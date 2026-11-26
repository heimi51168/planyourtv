package app.orchard.tv.policy.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hosted cache schema this build understands. V56 writes 5 (image wallpapers and quiz intros,
 * plus an optional quiz-intro audio track), also reads 4 (V54), 3 (V53), 2 (V51) and 1 (V50
 * caches, which carry only custom videos). A newer cache is treated as unreadable and the
 * previous file list is kept; a downgraded APK cannot read schema 5, so schema-5-only
 * configuration must be cleared before a rollback (see docs).
 */
const val HOSTED_MEDIA_SCHEMA = 5
const val HOSTED_MEDIA_SCHEMA_MIN = 1
/** The backend schema gate field that enables the V56 media-center flow. */
const val WALLPAPER_MEDIA_SCHEMA = 5

/**
 * V50/V51: the rotation the TV executes for a cloud child wallpaper when the backend does not
 * send an explicit playlist interval. Every mode uses the same short interval the local uploader
 * uses for pictures; a video still plays to its natural end and only uses this value as the image
 * hold / unknown-duration cap.
 *
 * [DAILY] selects exactly one item per Asia/Shanghai day (see `HostedMediaStore.snapshotOf`).
 */
const val HOSTED_WALLPAPER_INTERVAL_MS = 15_000L

/**
 * V53 playlist bounds, mirroring `WallpaperCapabilityService` on the backend. A payload outside
 * them is rejected, never clamped: the parent page shows the same limits, so an out-of-range
 * value means the two sides disagree and the TV must say so.
 */
const val HOSTED_PLAYLIST_MAX_ITEMS = 20
const val HOSTED_PLAYLIST_MIN_INTERVAL_MS = 15_000L
const val HOSTED_PLAYLIST_MAX_INTERVAL_MS = 300_000L

enum class HostedWallpaperMode(val wire: String) {
    FIXED("FIXED"),
    CAROUSEL("CAROUSEL"),
    DAILY("DAILY");

    companion object {
        fun fromWire(value: String?): HostedWallpaperMode? =
            entries.firstOrNull { it.wire == value?.trim()?.uppercase() }
    }
}

/**
 * The single data source a hosted plan was built from. Not a fallback chain.
 *
 * Named "origin" because [HostedMediaSource] is already the manifest's diagnostics block.
 */
enum class HostedMediaOrigin(val wire: String) {
    CUSTOM("CUSTOM"),
    THEME("THEME"),
    /** V53: the parent picked an explicit playlist (possibly mixing catalog and custom videos). */
    PLAYLIST("PLAYLIST"),
    NONE("NONE");

    companion object {
        fun fromWire(value: String?): HostedMediaOrigin? =
            entries.firstOrNull { it.wire == value?.trim()?.uppercase() }
    }
}

/** Failure causes of one sync attempt; they are written to `status.json` and logcat only. */
enum class HostedMediaError(val wire: String) {
    NO_MEMBER("NO_MEMBER"),
    BAD_PAYLOAD("BAD_PAYLOAD"),
    QUIZ_TOO_LARGE("QUIZ_TOO_LARGE"),
    QUIZ_TOO_LONG("QUIZ_TOO_LONG"),
    BAD_MODE("BAD_MODE"),
    ITEM_INVALID("ITEM_INVALID"),
    NO_SPACE("NO_SPACE"),
    NETWORK("NETWORK"),
    WRITE_FAILED("WRITE_FAILED"),
    THEME_UNSUPPORTED("THEME_UNSUPPORTED"),
}

/** One downloadable item of a cloud plan. The URL is only used for the fetch, never stored. */
data class HostedMediaPlanItem(
    val key: String,
    /** Custom: `custom:<long id>`; theme: the backend media id (`media:<file>`). */
    val id: String,
    val kind: PolicyMediaKind,
    val version: Long,
    val url: String,
    val ext: String,
    /** Index in the backend catalog; keeps daily rotation stable against reordering bugs. */
    val order: Int,
    /** Null when the parent backend did not probe the file; the TV fills it after download. */
    val sizeBytes: Long?,
    /** Null when the parent backend did not probe the file; the TV fills it after download. */
    val durationMs: Long?,
    /** V56: `official:<slug>` for catalog items; null for custom uploads and legacy entries. */
    val officialId: String? = null,
    /** V56: null = the parent backend never probed the audio track (treated as "may have"). */
    val hasAudio: Boolean? = null,
)

/**
 * V54/V56: one audio track selected for this child × device (background music or quiz intro).
 *
 * The track is not a wallpaper item: it has no poster, no 16:9 frame and no rotation role, so
 * it keeps its own model instead of widening [PolicyMediaKind]. The URL is used for the fetch
 * only and is never written to the manifest.
 */
data class HostedAudioPlan(
    val key: String,
    /** Stable parent selection: `official:<slug>` or `custom:<long id>`. */
    val id: String,
    /** `official` (public catalogue) or `custom` (private upload). */
    val source: String,
    val name: String,
    val nameEn: String?,
    val artist: String?,
    val license: String?,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val ext: String,
    val version: Long,
)

/** What the TV will cache for one member. Produced only by [HostedMediaPlanner]. */
data class HostedMediaPlan(
    val memberId: Long,
    val sound: Boolean,
    val source: HostedMediaOrigin,
    val mode: HostedWallpaperMode,
    /** Only meaningful for [HostedWallpaperMode.DAILY]; 1 for every other mode. */
    val rotateDays: Int,
    val activeKey: String?,
    val wallpaperItems: List<HostedMediaPlanItem>,
    val quizIntro: HostedMediaPlanItem?,
    val quizIntroItemId: Long?,
    val sourceItemCount: Int,
    /** V53: image hold of the playlist; [HOSTED_WALLPAPER_INTERVAL_MS] for V50/V51 plans. */
    val intervalMs: Long = HOSTED_WALLPAPER_INTERVAL_MS,
    /** V54: the one background track to cache; null when the parent chose none. */
    val audio: HostedAudioPlan? = null,
    /** V56: optional track that replaces the quiz intro's own audio. */
    val quizAudio: HostedAudioPlan? = null,
    val quizAudioEnabled: Boolean = false,
    /** Payload schema (`wallpaperMediaSchema`); 4 for every backend before V56. */
    val mediaSchema: Int = 4,
)

sealed class HostedMediaPlanResult {
    data class Success(val plan: HostedMediaPlan) : HostedMediaPlanResult()
    data class Rejected(val error: HostedMediaError) : HostedMediaPlanResult()
}

/**
 * V52 assets-first: every candidate the save page can choose, not the current selection.
 * The backend builds the same key set, so a ready library means a later save needs no download.
 */
data class HostedMediaLibrary(
    val memberId: Long,
    val customItems: List<HostedMediaPlanItem>,
    val themeItems: List<HostedMediaPlanItem>,
    val quizIntro: HostedMediaPlanItem?,
) {
    /** Deduplicated by cache key; the quiz intro may also be one of the custom videos. */
    fun allItems(): List<HostedMediaPlanItem> {
        val seen = LinkedHashSet<String>()
        val out = ArrayList<HostedMediaPlanItem>()
        (themeItems + customItems + listOfNotNull(quizIntro)).forEach { item ->
            if (seen.add(item.key)) out += item
        }
        return out
    }
}

sealed class HostedMediaLibraryResult {
    data class Success(val library: HostedMediaLibrary) : HostedMediaLibraryResult()

    /**
     * [keys] are the keys that could be derived from the rejected payload. Reporting them as
     * failed lets the page offer a retry instead of polling a preparing state forever.
     */
    data class Rejected(
        val error: HostedMediaError,
        val keys: List<String>,
    ) : HostedMediaLibraryResult()
}

/** V52 preload wire constants; the backend accepts exactly these item states. */
const val PRELOAD_SCHEMA = 1
const val PRELOAD_STATE_PREPARING = "preparing"
const val PRELOAD_STATE_READY = "ready"
const val PRELOAD_STATE_FAILED = "failed"

/**
 * The assets-first index persisted next to the manifest. It is a download cache only: execution
 * never reads it, and publishing keeps its files alive so a save does not re-download them.
 */
data class HostedPreloadIndex(
    val memberId: Long,
    val requestId: String,
    val libraryVersion: String,
    val updatedAtMs: Long,
    val items: List<HostedMediaItem>,
    val schema: Int = PRELOAD_SCHEMA,
)

/**
 * The execution view built from one complete manifest. Only complete files are referenced, so the
 * child surfaces never fall back from a missing hosted file to a local upload or local BGM.
 */
data class HostedMediaSnapshot(
    val memberId: Long,
    val sound: Boolean,
    val selection: PolicyWallpaperSelection,
    val lockRoleId: String,
    val quizSource: PolicyVideoSource?,
    /** V54: background music resolved to a complete local file; null when none is selected. */
    val audio: PolicyAudioEntry? = null,
    /** V56: quiz-intro audio; only executed when [quizAudioEnabled] is true. */
    val quizAudio: PolicyAudioEntry? = null,
    val quizAudioEnabled: Boolean = false,
)

/**
 * The single switch between the local media source and the cloud-hosted cache. Execution reads
 * through this route; writes never go through it. A community build never sets [cloud], so every
 * read entry in [PolicyVideoLibrary] keeps its original local semantics.
 *
 * [NONE_ID] is deliberately not null: while CLOUD has no snapshot the child desktop must show the
 * bundled default, not the owner's Home wallpaper. Returning null here used to mean "not owned".
 */
object HostedMediaRoute {
    const val NONE_ID = "hosted:none"

    @Volatile
    private var cloud: Boolean = false

    @Volatile
    private var snapshot: HostedMediaSnapshot? = null

    private val _revision = MutableStateFlow(0L)
    /** Compose surfaces collect this to re-read a data source; it is not a playback signal. */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    @Synchronized
    fun setCloud(value: Boolean) {
        if (cloud == value) return
        cloud = value
        if (!value) snapshot = null
        _revision.value = _revision.value + 1L
    }

    @Synchronized
    fun setSnapshot(value: HostedMediaSnapshot?) {
        if (snapshot == value) return
        snapshot = value
        _revision.value = _revision.value + 1L
    }

    fun isCloud(): Boolean = cloud

    fun snapshot(): HostedMediaSnapshot? = snapshot
}