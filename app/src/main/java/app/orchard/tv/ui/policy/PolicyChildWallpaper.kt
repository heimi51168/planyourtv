package app.orchard.tv.ui.policy

import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.orchard.tv.policy.FamilyHub
import app.orchard.tv.policy.media.PolicyMediaKind
import app.orchard.tv.policy.media.PolicyVideoLibrary
import app.orchard.tv.policy.media.PolicyVideoSource
import app.orchard.tv.policy.media.PolicyWallpaperSelection
import app.orchard.tv.ui.policy.lockdesktop.PolicyLockFilm
import app.orchard.tv.wallpaper.Images
import app.orchard.tv.wallpaper.Wallpapers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The self-discipline wallpaper, used by the lock screen and by the active child session's Home.
 * Quiz pages deliberately keep their plain dark background: a moving picture there would pull
 * attention away from the questions. The lock role owns the picture: its poster is the still
 * frame the lock video was taken from, so the child surfaces show the same wallpaper even while
 * the video is paused or not yet rendering.
 *
 * The parent center can give the child a rotation of pictures and videos. [lockId], [source],
 * [image] and [frosted] always describe the item on screen; [playlistSize], [onVideoEnded] and
 * [onVideoError] let the players know whether to loop and how to move on. An image holds for the
 * configured interval, a video plays to its end, and a single item keeps the old looping
 * behaviour.
 *
 * The fallback is [PolicyDefaultWallpaper], never the owner's Home wallpaper. A missing or
 * unreadable poster therefore leaves child mode on the bundled kids picture instead of leaking the
 * parent wallpaper into the child's screens.
 *
 * Posters are decoded and shaded on a small per-process cache. [PolicyChildWallpaper] can be
 * returned before the first one finishes ([loading]); the lock screen can draw the shared default
 * meanwhile, while [PolicyHomeGate] keeps Home back until the frame is ready. Later rotation
 * changes keep the previous frame until the next poster is decoded, so advanced navigation never
 * flashes the fallback.
 */
internal data class PolicyChildWallpaper(
    val lockId: String?,
    /** The loop the lock screen plays; null when the role's file is missing or unreadable. */
    val source: PolicyVideoSource?,
    val image: ImageBitmap?,
    val frosted: ImageBitmap?,
    val loading: Boolean,
    /** The parent-center sound preference; the player also requires [source] to have audio. */
    val soundEnabled: Boolean = false,
    /** Two or more items make the player advance; one keeps the old loop. */
    val playlistSize: Int = 1,
    val intervalMs: Long = PolicyVideoLibrary.WALLPAPER_DEFAULT_INTERVAL_MS,
    /** Called with the film that reached its natural end; null for a single looping item. */
    val onVideoEnded: ((PolicyVideoSource) -> Unit)? = null,
    /** Called with the film that cannot be decoded, so the rotation can skip it. */
    val onVideoError: ((PolicyVideoSource) -> Unit)? = null,
)

/**
 * Wallpaper for Home while a child session is active. Null means Aerial's own wallpaper.
 *
 * [PolicyHomeFilm] draws the lock role's video under Home; [cover] is how far its first frame
 * has replaced the poster.
 */
internal class PolicyHomeWallpaper(
    val image: ImageBitmap?,
    val frosted: ImageBitmap?,
    internal val source: PolicyVideoSource?,
    internal val filmCover: Animatable<Float, *>,
    internal val soundEnabled: Boolean = false,
    internal val playlistSize: Int = 1,
    internal val onVideoEnded: ((PolicyVideoSource) -> Unit)? = null,
    internal val onVideoError: ((PolicyVideoSource) -> Unit)? = null,
) {
    val cover: () -> Float = { filmCover.value }
}

/** The child wallpaper's film under Home. [active] false lets the screensaver's decoder have it. */
@Composable
internal fun PolicyHomeFilm(wallpaper: PolicyHomeWallpaper, active: Boolean) {
    // A picture is drawn by HomeBackground; only a video may reach the player.
    val source = wallpaper.source?.takeIf { it.kind == PolicyMediaKind.VIDEO } ?: return
    PolicyLockFilm(
        source = source,
        cover = wallpaper.filmCover,
        active = active,
        soundEnabled = wallpaper.soundEnabled && source.hasAudio,
        loop = wallpaper.playlistSize <= 1,
        onEnded = wallpaper.onVideoEnded,
        onError = wallpaper.onVideoError,
    )
}

internal val LocalPolicyHomeWallpaper = compositionLocalOf<PolicyHomeWallpaper?> { null }

private data class CachedWallpaperImage(
    val image: ImageBitmap?,
    val frosted: ImageBitmap?,
)

/**
 * Three entries (previous, showing, next) are enough for a rotation and keep the memory cost
 * bounded: every entry is a pair of full-screen pictures plus its shaded copy remains in the
 * bitmap itself. A fresh decode still uses the bundled default until the poster is ready.
 */
private val wallpaperCache = object : LinkedHashMap<String, CachedWallpaperImage>(4, 0.75f, true) {
    override fun removeEldestEntry(
        eldest: MutableMap.MutableEntry<String, CachedWallpaperImage>,
    ): Boolean = size > 3
}

private val loadLock = Mutex()

/**
 * Loads and advances the active child's wallpaper rotation.
 *
 * An image holds for the configured interval; a video is never cut at that interval but plays
 * to its natural end (with a duration-based watchdog for decoders that never report one). An
 * item that fails to decode is skipped. [playing] is false while the screensaver hides Home: the
 * frame stays, but neither the interval nor a carousel video's end moves the rotation on. The
 * timer also waits while the screen is not STARTED, so a paused app does not consume intervals.
 */
@Composable
internal fun rememberPolicyChildWallpaperState(playing: Boolean = true): PolicyChildWallpaper {
    val context = LocalContext.current.applicationContext
    // Each child-mode member owns a rotation. Switching the TV's member changes this id, so the
    // rotation must be resolved again even though no preference key was touched.
    val family by FamilyHub.state.collectAsState()
    // The parent center is an overlay above the lock screen: choosing a wallpaper there must
    // update the picture underneath it without waiting for the next lock entry.
    var roleRevision by remember(context) { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val prefs = context.getSharedPreferences("quiz-intro", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (PolicyVideoLibrary.isMediaPreferenceKey(key)) roleRevision++
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val activeUserId = family.activeUserId
    var selection by remember(context, activeUserId) {
        mutableStateOf<PolicyWallpaperSelection?>(null)
    }
    var activeIndex by remember(context, activeUserId) { mutableIntStateOf(0) }
    var firstReady by remember(context, activeUserId) { mutableStateOf(false) }
    var images by remember(context, activeUserId) {
        mutableStateOf<Map<String, CachedWallpaperImage>>(emptyMap())
    }
    val sound = remember(context, activeUserId, roleRevision) {
        // Preferences only. The player checks the item's audio capability separately.
        runCatching { PolicyVideoLibrary.lockSoundPreference(context) }.getOrDefault(true)
    }

    LaunchedEffect(context, activeUserId, roleRevision) {
        // Keep the current picture when an unrelated preference (e.g. sound) changes; only
        // restart at the first item when the active one was removed or replaced.
        val previousId = selection?.items?.getOrNull(activeIndex)?.id
        // Read preferences and resolve the playable files off the main thread; changing the
        // wallpaper must not spend a frame on the library's filesystem checks.
        val loaded = withContext(Dispatchers.IO) {
            runCatching { PolicyVideoLibrary.lockSelection(context) }
                .getOrElse { PolicyWallpaperSelection(emptyList(), 0L) }
        }
        selection = loaded
        val retained = loaded.items.indexOfFirst { it.id == previousId }
        activeIndex = if (retained >= 0) retained else 0
    }

    val items = selection?.items.orEmpty()
    val active = items.getOrNull(activeIndex)
    val fallback = cachedPolicyDefaultWallpaper()
    val shown = active?.let { images[it.id] }
        ?: CachedWallpaperImage(fallback?.image, fallback?.frosted)

    LaunchedEffect(selection, activeIndex) {
        if (selection == null) return@LaunchedEffect
        if (active == null) {
            loadPolicyDefaultWallpaper(context)
            firstReady = true
            return@LaunchedEffect
        }
        val loadedImages = mutableMapOf<String, CachedWallpaperImage>()
        loadedImages[active.id] = images[active.id]
            ?: loadWallpaperImage(context, active.source)
        firstReady = true
        // Decode the next poster ahead of the timer. It is the only preload: a 20-item rotation
        // must not keep 20 full-screen pictures in memory, so this map holds just these two.
        if (items.size > 1) {
            val next = items[(activeIndex + 1) % items.size]
            if (next.id != active.id) {
                loadedImages[next.id] = images[next.id]
                    ?: loadWallpaperImage(context, next.source)
            }
        }
        images = loadedImages
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> foreground = true
                Lifecycle.Event.ON_STOP -> foreground = false
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // A carousel callback can arrive while playback is paused (screensaver, parent center, or a
    // stopped activity). Keep that event instead of dropping it, and apply it when playback
    // resumes. The URI identifies the item, so a late callback from a released player cannot
    // skip the item that replaced it.
    var pendingAdvance by remember(context, activeUserId) { mutableStateOf<String?>(null) }
    // Reads the live index, so a natural end and a timer cannot advance the same item twice.
    val advance: (Int) -> Unit = { expected ->
        if (activeIndex == expected && items.size > 1) {
            activeIndex = (expected + 1) % items.size
        }
    }
    val requestAdvance: (PolicyVideoSource) -> Unit = { ended ->
        if (items.size > 1 && ended.uri == active?.source?.uri) {
            if (playing && foreground) {
                activeIndex = (activeIndex + 1) % items.size
            } else {
                pendingAdvance = ended.uri.toString()
            }
        }
    }
    LaunchedEffect(selection, activeIndex, playing, foreground, pendingAdvance) {
        if (!playing || !foreground || items.size < 2) return@LaunchedEffect
        val pending = pendingAdvance
        if (pending != null) {
            pendingAdvance = null
            if (pending == active?.source?.uri?.toString()) {
                activeIndex = (activeIndex + 1) % items.size
            }
            return@LaunchedEffect
        }
        val expected = activeIndex
        val source = items.getOrNull(expected)?.source ?: return@LaunchedEffect
        when {
            // A still picture has no natural end; the configured interval is its hold.
            source.kind == PolicyMediaKind.IMAGE -> {
                delay(selection?.intervalMs ?: PolicyVideoLibrary.WALLPAPER_DEFAULT_INTERVAL_MS)
                advance(expected)
            }
            // The player normally reports its end. If a decoder stalls without an error, the
            // film's own duration plus a grace period still releases the rotation.
            source.durationMs > 0L -> {
                delay(source.durationMs + ROTATION_VIDEO_GRACE_MS)
                advance(expected)
            }
            // A video without duration metadata may also report no end on some decoders; a
            // generous cap keeps the rotation from parking on it forever.
            else -> {
                delay(ROTATION_VIDEO_UNKNOWN_DURATION_MS)
                advance(expected)
            }
        }
    }

    return PolicyChildWallpaper(
        lockId = active?.id,
        source = active?.source,
        image = shown.image,
        frosted = shown.frosted,
        loading = selection == null || !firstReady,
        soundEnabled = sound,
        playlistSize = items.size,
        intervalMs = selection?.intervalMs ?: PolicyVideoLibrary.WALLPAPER_DEFAULT_INTERVAL_MS,
        onVideoEnded = if (items.size > 1) requestAdvance else null,
        onVideoError = if (items.size > 1) requestAdvance else null,
    )
}

/**
 * How long after a film's own duration the carousel waits before treating the decoder as
 * stalled. Long enough for buffering a local file, short enough that a broken item does not
 * hold the desktop forever.
 */
private const val ROTATION_VIDEO_GRACE_MS = 4_000L

/**
 * Last-resort hold for a film whose metadata and player both fail to report an end. Wallpaper
 * uploads normally carry a duration; this only keeps a broken item from freezing the rotation
 * until the next key press.
 */
private const val ROTATION_VIDEO_UNKNOWN_DURATION_MS = 10L * 60 * 1000

private suspend fun loadWallpaperImage(
    context: Context,
    source: PolicyVideoSource,
): CachedWallpaperImage = loadLock.withLock {
    wallpaperCache[source.uri.toString()]?.let { return@withLock it }
    val poster = source.poster?.takeIf { it.isFile && it.length() > 0 }
    val decoded = poster?.let {
        withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull()
        }
    }
    if (decoded == null) {
        // The lock role can be selected while its file is still unavailable (deleted outside
        // the app). In that case the default wallpaper is the child backdrop, not Home's.
        val fallback = loadPolicyDefaultWallpaper(context)
        return@withLock CachedWallpaperImage(fallback?.image, fallback?.frosted)
            .also { wallpaperCache[source.uri.toString()] = it }
    }
    val loaded = withContext(Dispatchers.Default) {
        val frosted = runCatching { Images.frost(decoded) }.getOrNull()
        // Shade after frosting, as the shared default does; Home expects the same shading on
        // this picture as on any other static wallpaper.
        val shaded = runCatching { Images.shadeForHome(decoded) }.getOrNull() ?: decoded
        CachedWallpaperImage(shaded.asImageBitmap(), frosted?.asImageBitmap())
    }
    wallpaperCache[source.uri.toString()] = loaded
    loaded
}

/**
 * The background for the short "preparing desktop" wait. A child transition keeps the child
 * wallpaper; a parent unlock shows the owner's wallpaper, so leaving the session does not flash
 * black or the child's picture.
 */
@Composable
internal fun PolicyPreparingBackdrop(
    wallpaper: PolicyChildWallpaper?,
    childActive: Boolean,
    modifier: Modifier = Modifier,
) {
    if (childActive) {
        val fallback = rememberPolicyDefaultWallpaperState()
        PreparingBackdrop(wallpaper?.image ?: fallback?.image, modifier)
        return
    }
    PreparingBackdrop(Wallpapers.backdrop, modifier)
}

@Composable
private fun PreparingBackdrop(image: ImageBitmap?, modifier: Modifier) {
    Box(modifier.fillMaxSize().background(PolicyBackdropBase)) {
        image?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(Modifier.fillMaxSize().background(PolicyPreparingScrim))
    }
}

private val PolicyBackdropBase = Color(0xFF03060C)

private val PolicyPreparingScrim = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.26f),
    0.5f to Color.Black.copy(alpha = 0.16f),
    1f to Color.Black.copy(alpha = 0.42f),
)
