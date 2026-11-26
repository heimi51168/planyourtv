package app.orchard.tv.policy.media

import com.planyourtv.log.TvLog
import org.json.JSONObject

/**
 * Turns the backend's `configured-apps.result` into a hosted plan. Pure: it never touches disk,
 * the network or Android state, so the acceptance matrix can be exercised by unit tests.
 *
 * V51 reads two sources in a fixed product order (not a fallback chain): the child's own
 * landscape videos win; otherwise the saved theme decides. POEM / POEM_VIDEO are not renderable
 * on the TV and reject the whole publish, so the previous cache keeps playing.
 *
 * The planner is deliberately strict. A payload that does not carry the V50/V51 fields is
 * rejected instead of guessing defaults: an old backend must be visible as a failure, not
 * silently look like "the parent chose the default wallpaper".
 */
object HostedMediaPlanner {
    private const val TAG = "CloudMedia"
    private const val MAX_QUIZ_BYTES = PolicyVideoValidation.MAX_QUIZ_FILE_BYTES
    private const val MAX_QUIZ_DURATION_MS = PolicyVideoValidation.MAX_QUIZ_DURATION_MS
    private const val MAX_THEME_ROTATE_DAYS = 30
    private const val ITEM_ALL = "ALL"
    private const val THEME_NONE = "NONE"
    private const val THEME_MEDIA = "MEDIA"
    private const val THEME_POEM = "POEM"
    private const val THEME_POEM_VIDEO = "POEM_VIDEO"
    private const val CUSTOM_ID_PREFIX = "custom:"
    private const val THEME_KEY_PREFIX = "theme:"
    private const val PLAYLIST_WIRE = "wallpaperPlaylist"
    private const val PLAYLIST_SET_WIRE = "wallpaperPlaylistSet"
    private const val PLAYLIST_INTERVAL_WIRE = "wallpaperIntervalMs"
    private const val QUIZ_INTRO_WIRE = "wallpaperQuizIntro"
    private const val BGM_WIRE = "wallpaperBackgroundMusic"
    private const val BGM_ITEM_WIRE = "wallpaperBackgroundMusicItem"
    private const val QUIZ_AUDIO_WIRE = "wallpaperQuizAudio"
    private const val QUIZ_AUDIO_ENABLED_WIRE = "wallpaperQuizAudioEnabled"
    private const val QUIZ_AUDIO_ITEM_WIRE = "wallpaperQuizAudioItem"
    private const val MEDIA_SCHEMA_WIRE = "wallpaperMediaSchema"
    private const val AUDIO_ID_OFFICIAL = "official:"
    private const val AUDIO_ID_CUSTOM = "custom:"
    private const val AUDIO_SOURCE_OFFICIAL = "official"
    private const val AUDIO_SOURCE_CUSTOM = "custom"
    private const val AUDIO_KEY_PREFIX = "audio:"
    private const val MAX_AUDIO_NAME_LENGTH = 80
    private const val MAX_ATTRIBUTION_LENGTH = 80
    private val EXT_PATTERN = Regex("[a-z0-9]{1,8}")
    private val SHA256_PATTERN = Regex("[a-f0-9]{64}")
    private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "ogg", "oga")

    fun plan(result: JSONObject): HostedMediaPlanResult {
        val memberId = result.optLong("learnerId", 0L)
        if (memberId <= 0L) return HostedMediaPlanResult.Rejected(HostedMediaError.NO_MEMBER)
        val mediaSchema = result.optInt(MEDIA_SCHEMA_WIRE, 4)
        if (mediaSchema > WALLPAPER_MEDIA_SCHEMA) {
            return reject(HostedMediaError.BAD_PAYLOAD, "wallpaperMediaSchema unsupported $mediaSchema")
        }

        val customWallpaper = result.optJSONObject("customWallpaper")
            ?: return reject(HostedMediaError.BAD_PAYLOAD, "customWallpaper missing")
        // Always present (possibly JSON null) since V50. Its absence means an older backend.
        if (!customWallpaper.has("quizIntro")) {
            return reject(HostedMediaError.BAD_PAYLOAD, "customWallpaper.quizIntro missing")
        }
        val soundValue = result.opt("wallpaperSound")
        if (soundValue !is Boolean) {
            return reject(HostedMediaError.BAD_PAYLOAD, "wallpaperSound is not a boolean")
        }
        // V54: an old backend omits both fields; a present selection must be fully resolvable,
        // otherwise the whole sync fails instead of silently playing different music.
        val audio = when (val parsed = parseBackgroundMusic(result)) {
            is BackgroundMusic.Rejected -> return reject(parsed.error, parsed.detail)
            is BackgroundMusic.Parsed -> parsed.item
        }
        // Required even when a custom video wins: a missing field means this backend predates
        // V51, and the TV must show that as a failed sync rather than a silent theme change.
        val theme = result.optString("wallpaperTheme", "").trim().uppercase()
        if (theme.isEmpty()) return reject(HostedMediaError.BAD_PAYLOAD, "wallpaperTheme missing")
        val rotateWire = result.optString("wallpaperRotateMode", "").trim().uppercase()
        if (rotateWire.isEmpty()) {
            return reject(HostedMediaError.BAD_PAYLOAD, "wallpaperRotateMode missing")
        }

        val custom = parseCustomWallpaper(customWallpaper, mediaSchema)
        if (custom is CustomWallpaper.Rejected) {
            return HostedMediaPlanResult.Rejected(custom.error)
        }
        val customSelection = custom as CustomWallpaper.Parsed

        val themeItems = when (val parsed = themeItemsOf(result)) {
            is ThemeItems.Rejected -> return reject(parsed.error, parsed.detail)
            is ThemeItems.Parsed -> parsed.items
        }

        val quizIntro = parseQuizIntro(
            result = result,
            themeItems = themeItems,
            customVideos = customSelection.videos,
            customWallpaper = customWallpaper,
            mediaSchema = mediaSchema,
        )
        if (quizIntro is QuizIntro.Rejected) {
            return HostedMediaPlanResult.Rejected(quizIntro.error)
        }
        val quizItem = (quizIntro as QuizIntro.Parsed).item
        val quizAudio = when (val parsed = parseQuizAudio(result)) {
            is QuizAudio.Rejected -> return reject(parsed.error, parsed.detail)
            is QuizAudio.Parsed -> parsed
        }
        if (quizAudio.enabled) {
            if (mediaSchema < WALLPAPER_MEDIA_SCHEMA) {
                return reject(
                    HostedMediaError.BAD_PAYLOAD,
                    "quiz intro audio requires wallpaperMediaSchema 5",
                )
            }
            if (quizItem == null || quizAudio.item == null) {
                return reject(
                    HostedMediaError.ITEM_INVALID,
                    "quiz intro audio enabled without a visual or track",
                )
            }
        }
        // A disabled track stays in the backend row so the parent can re-enable it, but the TV
        // does not cache it: writing it into schema 5 would force a schema bump for no playback.
        val quizAudioTrack = if (quizAudio.enabled) quizAudio.item else null
        val quizAudioEnabled = quizAudio.enabled
        val quizIntroItemId = quizItem?.id
            ?.removePrefix(CUSTOM_ID_PREFIX)
            ?.toLongOrNull()
            ?: if (!result.has(QUIZ_INTRO_WIRE)) {
                // V50/V51 payload: the legacy numeric id stays for diagnostics only. A V53
                // payload with an explicit null intro must not resurrect a stale value.
                customWallpaper.optLong("quizIntroItemId", 0L).takeIf { it > 0L }
            } else {
                null
            }

        // V53: the flag separates "the parent made an explicit playlist decision" (including an
        // empty list = default wallpaper) from V50/V52 rows that only carry theme/item fields.
        // Without an explicit decision the TV keeps the V52 precedence untouched.
        when (val playlist = parsePlaylist(result, themeItems, customSelection.videos)) {
            is Playlist.Rejected -> return reject(playlist.error, playlist.detail)
            is Playlist.Parsed -> return HostedMediaPlanResult.Success(
                HostedMediaPlan(
                    memberId = memberId,
                    sound = soundValue,
                    source = if (playlist.items.isEmpty()) {
                        HostedMediaOrigin.NONE
                    } else {
                        HostedMediaOrigin.PLAYLIST
                    },
                    mode = if (playlist.items.size <= 1) {
                        HostedWallpaperMode.FIXED
                    } else {
                        HostedWallpaperMode.CAROUSEL
                    },
                    rotateDays = 1,
                    activeKey = playlist.items.singleOrNull()?.key,
                    wallpaperItems = playlist.items,
                    quizIntro = quizItem,
                    quizIntroItemId = quizIntroItemId,
                    sourceItemCount = playlist.items.size,
                    intervalMs = playlist.intervalMs,
                    audio = audio,
                    quizAudio = quizAudioTrack,
                    quizAudioEnabled = quizAudioEnabled,
                    mediaSchema = mediaSchema,
                ),
            )
            Playlist.Absent -> Unit
        }

        if (customSelection.videos.isNotEmpty()) {
            val wallpaperItems = when (customSelection.mode) {
                HostedWallpaperMode.FIXED -> {
                    val active = customSelection.videos.firstOrNull {
                        customNumericId(it) == customSelection.activeId
                    }
                    if (active == null && customSelection.activeId > 0L) {
                        TvLog.w(TAG, "FIXED active id=${customSelection.activeId} missing; using first")
                    }
                    listOfNotNull(active ?: customSelection.videos.firstOrNull())
                }
                // DAILY is not produced for custom; keep the remaining videos in catalog order.
                else -> customSelection.videos
            }
            return HostedMediaPlanResult.Success(
                HostedMediaPlan(
                    memberId = memberId,
                    sound = soundValue,
                    source = HostedMediaOrigin.CUSTOM,
                    mode = customSelection.mode,
                    rotateDays = 1,
                    activeKey = if (customSelection.mode == HostedWallpaperMode.FIXED) {
                        wallpaperItems.firstOrNull()?.key
                    } else {
                        null
                    },
                    wallpaperItems = wallpaperItems,
                    quizIntro = quizItem,
                    quizIntroItemId = quizIntroItemId,
                    sourceItemCount = customSelection.videos.size,
                    audio = audio,
                    quizAudio = quizAudioTrack,
                    quizAudioEnabled = quizAudioEnabled,
                    mediaSchema = mediaSchema,
                ),
            )
        }

        TvLog.w(
            TAG,
            "custom has no landscape video; use theme=$theme rotate=$rotateWire",
        )
        return when (theme) {
            THEME_NONE -> HostedMediaPlanResult.Success(
                HostedMediaPlan(
                    memberId = memberId,
                    sound = soundValue,
                    source = HostedMediaOrigin.NONE,
                    mode = HostedWallpaperMode.FIXED,
                    rotateDays = 1,
                    activeKey = null,
                    wallpaperItems = emptyList(),
                    quizIntro = quizItem,
                    quizIntroItemId = quizIntroItemId,
                    sourceItemCount = 0,
                    audio = audio,
                    quizAudio = quizAudioTrack,
                    quizAudioEnabled = quizAudioEnabled,
                    mediaSchema = mediaSchema,
                ),
            )
            THEME_MEDIA -> themePlan(
                result = result,
                items = themeItems,
                memberId = memberId,
                sound = soundValue,
                rotateWire = rotateWire,
                quizItem = quizItem,
                quizIntroItemId = quizIntroItemId,
                audio = audio,
                quizAudioTrack = quizAudioTrack,
                quizAudioEnabled = quizAudioEnabled,
                mediaSchema = mediaSchema,
            )
            THEME_POEM, THEME_POEM_VIDEO ->
                reject(HostedMediaError.THEME_UNSUPPORTED, "theme $theme is not renderable on TV")
            else -> reject(HostedMediaError.BAD_PAYLOAD, "wallpaperTheme unsupported: $theme")
        }
    }

    /**
     * V52 assets-first: parse every candidate the current payload can offer, without choosing a
     * selection. Strict for the same reason as [plan]: a malformed item fails the whole library,
     * so the TV reports that failure instead of silently preloading a subset.
     */
    fun library(result: JSONObject): HostedMediaLibraryResult {
        val memberId = result.optLong("learnerId", 0L)
        if (memberId <= 0L) {
            return libraryReject(HostedMediaError.NO_MEMBER, result, "learnerId missing")
        }
        val mediaSchema = result.optInt(MEDIA_SCHEMA_WIRE, 4)
        if (mediaSchema > WALLPAPER_MEDIA_SCHEMA) {
            return libraryReject(
                HostedMediaError.BAD_PAYLOAD,
                result,
                "wallpaperMediaSchema unsupported $mediaSchema",
            )
        }
        val customWallpaper = result.optJSONObject("customWallpaper")
            ?: return libraryReject(HostedMediaError.BAD_PAYLOAD, result, "customWallpaper missing")
        if (!customWallpaper.has("quizIntro")) {
            return libraryReject(
                HostedMediaError.BAD_PAYLOAD,
                result,
                "customWallpaper.quizIntro missing",
            )
        }
        val themeItems = when (val parsed = themeItemsOf(result)) {
            is ThemeItems.Rejected -> return libraryReject(parsed.error, result, parsed.detail)
            is ThemeItems.Parsed -> parsed.items
        }

        val custom = parseCustomWallpaper(customWallpaper, mediaSchema)
        if (custom is CustomWallpaper.Rejected) {
            return libraryReject(custom.error, result, custom.detail)
        }
        val customItems = (custom as CustomWallpaper.Parsed).videos
        val quizIntro = when (
            val parsed = parseQuizIntro(
                result = result,
                themeItems = themeItems,
                customVideos = customItems,
                customWallpaper = customWallpaper,
                mediaSchema = mediaSchema,
            )
        ) {
            is QuizIntro.Rejected -> return libraryReject(parsed.error, result, parsed.detail)
            is QuizIntro.Parsed -> parsed.item
        }
        return HostedMediaLibraryResult.Success(
            HostedMediaLibrary(
                memberId = memberId,
                customItems = customItems,
                themeItems = themeItems,
                quizIntro = quizIntro,
            ),
        )
    }

    private fun parseCustomWallpaper(
        customWallpaper: JSONObject,
        mediaSchema: Int = 4,
    ): CustomWallpaper {
        val mode = when (customWallpaper.optString("landscapeMode", "").trim().uppercase()) {
            "FIXED" -> HostedWallpaperMode.FIXED
            "CAROUSEL" -> HostedWallpaperMode.CAROUSEL
            "DAILY" -> return CustomWallpaper.Rejected(
                HostedMediaError.BAD_MODE,
                "custom landscapeMode DAILY",
            )
            else -> return CustomWallpaper.Rejected(
                HostedMediaError.BAD_MODE,
                "landscapeMode unsupported",
            )
        }
        val array = customWallpaper.optJSONArray("landscapeItems")
            ?: return CustomWallpaper.Rejected(
                HostedMediaError.BAD_PAYLOAD,
                "landscapeItems missing",
            )
        val videos = ArrayList<HostedMediaPlanItem>()
        for (index in 0 until array.length()) {
            val entry = array.optJSONObject(index) ?: continue
            val kind = when (entry.optString("kind", "").trim().uppercase()) {
                "VIDEO" -> PolicyMediaKind.VIDEO
                // V56: schema 5 renders custom landscape pictures too; older caches (and older
                // payloads) still skip them instead of turning one into a video plan item.
                "IMAGE" -> PolicyMediaKind.IMAGE
                else -> {
                    TvLog.w(TAG, "skip custom wallpaper kind=${entry.optString("kind", "")}")
                    continue
                }
            }
            if (kind == PolicyMediaKind.IMAGE && mediaSchema < WALLPAPER_MEDIA_SCHEMA) {
                TvLog.w(TAG, "skip custom image wallpaper on schema $mediaSchema")
                continue
            }
            val item = customItemOf(entry, videos.size, kind)
                ?: return CustomWallpaper.Rejected(
                    HostedMediaError.ITEM_INVALID,
                    "wallpaper item invalid",
                )
            videos += item
        }
        videos.sortBy { customNumericId(it) }
        if (videos.map { it.key }.distinct().size != videos.size) {
            return CustomWallpaper.Rejected(
                HostedMediaError.ITEM_INVALID,
                "duplicate wallpaper key",
            )
        }
        return CustomWallpaper.Parsed(
            mode = mode,
            activeId = customWallpaper.optLong("landscapeActiveId", 0L),
            videos = videos,
        )
    }

    private fun themeItemsOf(result: JSONObject): ThemeItems {
        val array = result.optJSONArray("wallpaperMedia")
            ?: return ThemeItems.Rejected(HostedMediaError.BAD_PAYLOAD, "wallpaperMedia missing")
        val items = ArrayList<HostedMediaPlanItem>()
        for (index in 0 until array.length()) {
            val entry = array.optJSONObject(index) ?: continue
            val kind = when (entry.optString("kind", "").trim().uppercase()) {
                "IMAGE" -> PolicyMediaKind.IMAGE
                "VIDEO" -> PolicyMediaKind.VIDEO
                else -> {
                    TvLog.w(TAG, "skip theme media kind=${entry.optString("kind", "")}")
                    continue
                }
            }
            val orientation = entry.optString("orientation", "").trim().lowercase()
            if (orientation != "landscape") {
                TvLog.w(TAG, "skip non-landscape theme media file=${entry.optString("file", "")}")
                continue
            }
            val item = themeItemOf(entry, kind, index)
                ?: return ThemeItems.Rejected(
                    HostedMediaError.ITEM_INVALID,
                    "theme media item invalid",
                )
            items += item
        }
        if (items.map { it.key }.distinct().size != items.size) {
            return ThemeItems.Rejected(HostedMediaError.ITEM_INVALID, "duplicate theme key")
        }
        return ThemeItems.Parsed(items)
    }

    /**
     * V53 playlist. `wallpaperPlaylistSet` is the discriminator the backend writes:
     * false/absent means the row was saved by V50/V52 and the legacy theme/custom precedence
     * still applies; true means the parent made an explicit playlist decision, and an empty
     * list is the deliberate "default wallpaper" choice (no fallback to custom videos).
     */
    private fun parsePlaylist(
        result: JSONObject,
        themeItems: List<HostedMediaPlanItem>,
        customVideos: List<HostedMediaPlanItem>,
    ): Playlist {
        if (!result.has(PLAYLIST_SET_WIRE)) return Playlist.Absent
        val flag = result.opt(PLAYLIST_SET_WIRE)
        if (flag !is Boolean) {
            return Playlist.Rejected(
                HostedMediaError.BAD_PAYLOAD,
                "wallpaperPlaylistSet is not a boolean",
            )
        }
        if (!flag) return Playlist.Absent
        val array = result.optJSONArray(PLAYLIST_WIRE)
            ?: return Playlist.Rejected(HostedMediaError.BAD_PAYLOAD, "wallpaperPlaylist missing")
        val interval = (result.opt(PLAYLIST_INTERVAL_WIRE) as? Number)?.toLong()
            ?: return Playlist.Rejected(
                HostedMediaError.BAD_PAYLOAD,
                "wallpaperIntervalMs missing",
            )
        if (interval !in HOSTED_PLAYLIST_MIN_INTERVAL_MS..HOSTED_PLAYLIST_MAX_INTERVAL_MS) {
            return Playlist.Rejected(
                HostedMediaError.BAD_PAYLOAD,
                "wallpaperIntervalMs out of range",
            )
        }
        if (array.length() > HOSTED_PLAYLIST_MAX_ITEMS) {
            return Playlist.Rejected(HostedMediaError.ITEM_INVALID, "playlist too long")
        }
        val items = ArrayList<HostedMediaPlanItem>(array.length())
        val seen = HashSet<String>()
        for (index in 0 until array.length()) {
            val raw = array.opt(index)
            if (raw !is String || raw.trim().isEmpty()) {
                return Playlist.Rejected(
                    HostedMediaError.ITEM_INVALID,
                    "playlist item is not a key",
                )
            }
            val key = raw.trim()
            if (!seen.add(key)) {
                return Playlist.Rejected(HostedMediaError.ITEM_INVALID, "duplicate playlist key")
            }
            val item = when {
                key.startsWith("media:") -> themeItems.firstOrNull { it.id == key }
                key.startsWith(CUSTOM_ID_PREFIX) ->
                    key.removePrefix(CUSTOM_ID_PREFIX).toLongOrNull()
                        ?.takeIf { it > 0L }
                        ?.let { id -> customVideos.firstOrNull { customNumericId(it) == id } }
                else -> null
            } ?: return Playlist.Rejected(
                HostedMediaError.ITEM_INVALID,
                "playlist item not in current library: $key",
            )
            items += item
        }
        return Playlist.Parsed(items, interval)
    }

    private fun themePlan(
        result: JSONObject,
        items: List<HostedMediaPlanItem>,
        memberId: Long,
        sound: Boolean,
        rotateWire: String,
        quizItem: HostedMediaPlanItem?,
        quizIntroItemId: Long?,
        audio: HostedAudioPlan?,
        quizAudioTrack: HostedAudioPlan?,
        quizAudioEnabled: Boolean,
        mediaSchema: Int,
    ): HostedMediaPlanResult {
        if (items.isEmpty()) {
            // The catalog has no TV-playable item. This is a published NONE, not a fallback to
            // the child's custom pictures or to local media.
            TvLog.w(TAG, "theme MEDIA has no TV-playable item; publish empty plan")
            return HostedMediaPlanResult.Success(
                HostedMediaPlan(
                    memberId = memberId,
                    sound = sound,
                    source = HostedMediaOrigin.NONE,
                    mode = HostedWallpaperMode.FIXED,
                    rotateDays = 1,
                    activeKey = null,
                    wallpaperItems = emptyList(),
                    quizIntro = quizItem,
                    quizIntroItemId = quizIntroItemId,
                    sourceItemCount = 0,
                    audio = audio,
                    quizAudio = quizAudioTrack,
                    quizAudioEnabled = quizAudioEnabled,
                    mediaSchema = mediaSchema,
                ),
            )
        }
        if (items.map { it.key }.distinct().size != items.size) {
            return reject(HostedMediaError.ITEM_INVALID, "duplicate theme key")
        }

        val mode: HostedWallpaperMode
        val rotateDays: Int
        when (rotateWire) {
            "FIXED" -> {
                mode = HostedWallpaperMode.FIXED
                rotateDays = 1
            }
            "DAILY" -> {
                mode = HostedWallpaperMode.DAILY
                rotateDays = 1
            }
            "INTERVAL" -> {
                val days = (result.opt("wallpaperRotateDays") as? Number)?.toInt()
                if (days == null || days !in 1..MAX_THEME_ROTATE_DAYS) {
                    return reject(
                        HostedMediaError.BAD_PAYLOAD,
                        "wallpaperRotateDays out of range",
                    )
                }
                mode = HostedWallpaperMode.DAILY
                rotateDays = days
            }
            "SCROLL" -> {
                mode = HostedWallpaperMode.CAROUSEL
                rotateDays = 1
            }
            else -> return reject(
                HostedMediaError.BAD_MODE,
                "wallpaperRotateMode unsupported: $rotateWire",
            )
        }

        val wallpaperItems: List<HostedMediaPlanItem>
        val activeKey: String?
        if (mode == HostedWallpaperMode.FIXED) {
            val wanted = result.optString("wallpaperItem", "").trim()
            val selected = if (wanted.isEmpty() || wanted == ITEM_ALL) {
                null
            } else {
                items.firstOrNull { it.id == wanted }
            }
            if (selected == null && wanted.isNotEmpty() && wanted != ITEM_ALL) {
                TvLog.w(TAG, "FIXED theme item=$wanted missing; using first")
            }
            val chosen = selected ?: items.first()
            wallpaperItems = listOf(chosen)
            activeKey = chosen.key
        } else {
            wallpaperItems = items
            activeKey = null
        }

        return HostedMediaPlanResult.Success(
            HostedMediaPlan(
                memberId = memberId,
                sound = sound,
                source = HostedMediaOrigin.THEME,
                mode = mode,
                rotateDays = rotateDays,
                activeKey = activeKey,
                wallpaperItems = wallpaperItems,
                quizIntro = quizItem,
                quizIntroItemId = quizIntroItemId,
                sourceItemCount = items.size,
                audio = audio,
                    quizAudio = quizAudioTrack,
                    quizAudioEnabled = quizAudioEnabled,
                    mediaSchema = mediaSchema,
            ),
        )
    }

    private fun parseQuizIntro(customWallpaper: JSONObject): QuizIntro {
        if (customWallpaper.isNull("quizIntro")) return QuizIntro.Parsed(null)
        val entry = customWallpaper.optJSONObject("quizIntro")
            ?: return QuizIntro.Rejected(
                HostedMediaError.BAD_PAYLOAD,
                "quizIntro is not an object",
            )
        // V50 bridges the child's landscape videos; the TV never renders a still here.
        if (entry.optString("kind", "").trim().uppercase() != "VIDEO") {
            return QuizIntro.Rejected(HostedMediaError.ITEM_INVALID, "quiz intro is not a video")
        }
        val item = customItemOf(entry, 0)
            ?: return QuizIntro.Rejected(HostedMediaError.ITEM_INVALID, "quiz intro item invalid")
        val size = item.sizeBytes
            ?: return QuizIntro.Rejected(
                HostedMediaError.BAD_PAYLOAD,
                "quiz intro size missing",
            )
        val duration = item.durationMs
            ?: return QuizIntro.Rejected(
                HostedMediaError.BAD_PAYLOAD,
                "quiz intro duration missing",
            )
        if (size <= 0L || duration <= 0L) {
            return QuizIntro.Rejected(
                HostedMediaError.BAD_PAYLOAD,
                "quiz intro metadata invalid",
            )
        }
        if (size > MAX_QUIZ_BYTES) {
            return QuizIntro.Rejected(HostedMediaError.QUIZ_TOO_LARGE, "quiz intro too large")
        }
        if (duration > MAX_QUIZ_DURATION_MS) {
            return QuizIntro.Rejected(HostedMediaError.QUIZ_TOO_LONG, "quiz intro too long")
        }
        return QuizIntro.Parsed(item)
    }

    /**
     * V53 unified quiz intro: `wallpaperQuizIntro` may point at either a built-in VIDEO item
     * (`media:` key, resolved against the current catalog) or one of the child's own landscape
     * videos (`custom:<id>`). A payload without the field predates V53 and keeps the V50 bridge
     * inside customWallpaper. A field that is present but unresolvable fails the sync — the TV
     * must not silently play a different intro than the parent picked.
     */
    private fun parseQuizIntro(
        result: JSONObject,
        themeItems: List<HostedMediaPlanItem>,
        customVideos: List<HostedMediaPlanItem>,
        customWallpaper: JSONObject,
        mediaSchema: Int = 4,
    ): QuizIntro {
        if (!result.has(QUIZ_INTRO_WIRE)) return parseQuizIntro(customWallpaper)
        if (result.isNull(QUIZ_INTRO_WIRE)) return QuizIntro.Parsed(null)
        val raw = result.opt(QUIZ_INTRO_WIRE)
        if (raw !is String) {
            return QuizIntro.Rejected(
                HostedMediaError.BAD_PAYLOAD,
                "wallpaperQuizIntro is not a string",
            )
        }
        val key = raw.trim()
        if (key.isEmpty()) {
            return QuizIntro.Rejected(HostedMediaError.BAD_PAYLOAD, "wallpaperQuizIntro empty")
        }
        val item = when {
            key.startsWith("media:") -> themeItems.firstOrNull { it.id == key }
            key.startsWith(CUSTOM_ID_PREFIX) ->
                key.removePrefix(CUSTOM_ID_PREFIX).toLongOrNull()
                    ?.takeIf { it > 0L }
                    ?.let { id -> customVideos.firstOrNull { customNumericId(it) == id } }
            else -> null
        } ?: return QuizIntro.Rejected(
            HostedMediaError.ITEM_INVALID,
            "quiz intro not in current library: $key",
        )
        if (item.kind == PolicyMediaKind.IMAGE) {
            // V56 stills are only renderable by a schema-5 cache; the old bridge stays video-only.
            if (mediaSchema < WALLPAPER_MEDIA_SCHEMA) {
                return QuizIntro.Rejected(
                    HostedMediaError.ITEM_INVALID,
                    "quiz intro image requires wallpaperMediaSchema 5",
                )
            }
            val size = item.sizeBytes
                ?: return QuizIntro.Rejected(HostedMediaError.BAD_PAYLOAD, "quiz intro size missing")
            if (size <= 0L) {
                return QuizIntro.Rejected(
                    HostedMediaError.BAD_PAYLOAD,
                    "quiz intro metadata invalid",
                )
            }
            return QuizIntro.Parsed(item)
        }
        if (item.kind != PolicyMediaKind.VIDEO) {
            return QuizIntro.Rejected(HostedMediaError.ITEM_INVALID, "quiz intro is not a video")
        }
        val size = item.sizeBytes
            ?: return QuizIntro.Rejected(HostedMediaError.BAD_PAYLOAD, "quiz intro size missing")
        val duration = item.durationMs
            ?: return QuizIntro.Rejected(
                HostedMediaError.BAD_PAYLOAD,
                "quiz intro duration missing",
            )
        if (size <= 0L || duration <= 0L) {
            return QuizIntro.Rejected(HostedMediaError.BAD_PAYLOAD, "quiz intro metadata invalid")
        }
        // V56 removed the legacy 16 MiB / 15 s caps; schema 4 keeps them so an older cache never
        // receives a file it would refuse to play.
        if (mediaSchema < WALLPAPER_MEDIA_SCHEMA) {
            if (size > MAX_QUIZ_BYTES) {
                return QuizIntro.Rejected(HostedMediaError.QUIZ_TOO_LARGE, "quiz intro too large")
            }
            if (duration > MAX_QUIZ_DURATION_MS) {
                return QuizIntro.Rejected(HostedMediaError.QUIZ_TOO_LONG, "quiz intro too long")
            }
        }
        return QuizIntro.Parsed(item)
    }

    /**
     * V54 background music. Both backend fields are written on every save (`item` may be JSON
     * null); a field pair that disagrees fails the sync rather than letting the TV play a track
     * the parent did not pick.
     */
    private fun parseBackgroundMusic(result: JSONObject): BackgroundMusic {
        return when (val parsed = parseAudioSelection(result, BGM_WIRE, BGM_ITEM_WIRE)) {
            is AudioSelection.Parsed -> BackgroundMusic.Parsed(parsed.item)
            is AudioSelection.Rejected -> BackgroundMusic.Rejected(parsed.error, parsed.detail)
        }
    }

    /**
     * V56 quiz-intro audio. Mirrors the background-music contract: the key and the resolved item
     * must agree, a partially written pair fails the sync, and an enabled switch without a track
     * or without a quiz visual is rejected here (the server enforces the same rule on save).
     */
    private fun parseQuizAudio(result: JSONObject): QuizAudio {
        if (!result.has(QUIZ_AUDIO_WIRE) && !result.has(QUIZ_AUDIO_ENABLED_WIRE)) {
            return QuizAudio.Parsed(null, false)
        }
        val enabledValue = result.opt(QUIZ_AUDIO_ENABLED_WIRE)
        if (enabledValue !is Boolean) {
            return QuizAudio.Rejected(
                HostedMediaError.BAD_PAYLOAD,
                "wallpaperQuizAudioEnabled is not a boolean",
            )
        }
        val item = when (
            val parsed = parseAudioSelection(result, QUIZ_AUDIO_WIRE, QUIZ_AUDIO_ITEM_WIRE)
        ) {
            is AudioSelection.Parsed -> parsed.item
            is AudioSelection.Rejected -> return QuizAudio.Rejected(parsed.error, parsed.detail)
        }
        if (enabledValue && item == null) {
            return QuizAudio.Rejected(
                HostedMediaError.ITEM_INVALID,
                "quiz audio enabled without a track",
            )
        }
        return QuizAudio.Parsed(item, enabledValue)
    }

    /**
     * Shared strict parser for one selected audio track. Both backend fields are written on every
     * save (`item` may be JSON null); a field pair that disagrees fails the sync rather than
     * letting the TV play a track the parent did not pick.
     */
    private fun parseAudioSelection(
        result: JSONObject,
        keyWire: String,
        itemWire: String,
    ): AudioSelection {
        if (!result.has(keyWire)) return AudioSelection.Parsed(null)
        val rawKey = result.opt(keyWire)
        if (rawKey == null || rawKey == JSONObject.NULL) {
            if (result.has(itemWire) && !result.isNull(itemWire)) {
                return AudioSelection.Rejected(
                    HostedMediaError.BAD_PAYLOAD,
                    "$keyWire item without a selection",
                )
            }
            return AudioSelection.Parsed(null)
        }
        if (rawKey !is String || rawKey.trim().isEmpty()) {
            return AudioSelection.Rejected(
                HostedMediaError.BAD_PAYLOAD,
                "$keyWire is not a key",
            )
        }
        val key = rawKey.trim()
        val source = when {
            key.startsWith(AUDIO_ID_OFFICIAL) -> AUDIO_SOURCE_OFFICIAL
            key.startsWith(AUDIO_ID_CUSTOM) -> AUDIO_SOURCE_CUSTOM
            else -> return AudioSelection.Rejected(
                HostedMediaError.BAD_PAYLOAD,
                "$keyWire key unsupported",
            )
        }
        if (source == AUDIO_SOURCE_OFFICIAL && key.length <= AUDIO_ID_OFFICIAL.length) {
            return AudioSelection.Rejected(HostedMediaError.BAD_PAYLOAD, "$keyWire id empty")
        }
        if (source == AUDIO_SOURCE_CUSTOM) {
            val id = key.removePrefix(AUDIO_ID_CUSTOM).toLongOrNull()
            if (id == null || id <= 0L) {
                return AudioSelection.Rejected(
                    HostedMediaError.BAD_PAYLOAD,
                    "custom audio id invalid",
                )
            }
        }
        if (!result.has(itemWire) || result.isNull(itemWire)) {
            return AudioSelection.Rejected(
                HostedMediaError.BAD_PAYLOAD,
                "$itemWire missing",
            )
        }
        val item = result.optJSONObject(itemWire)
            ?: return AudioSelection.Rejected(
                HostedMediaError.BAD_PAYLOAD,
                "$keyWire item is not an object",
            )
        if (item.optString("id", "").trim() != key) {
            return AudioSelection.Rejected(
                HostedMediaError.BAD_PAYLOAD,
                "$keyWire id does not match the selection",
            )
        }
        if (item.optString("source", "").trim() != source) {
            return AudioSelection.Rejected(
                HostedMediaError.BAD_PAYLOAD,
                "$keyWire source does not match the selection",
            )
        }
        val cacheKey = item.optString("key", "").trim()
        if (!cacheKey.startsWith(AUDIO_KEY_PREFIX) || cacheKey.length <= AUDIO_KEY_PREFIX.length) {
            return AudioSelection.Rejected(HostedMediaError.BAD_PAYLOAD, "audio cache key missing")
        }
        val name = item.optString("name", "").trim()
        if (name.isEmpty() || name.length > MAX_AUDIO_NAME_LENGTH) {
            return AudioSelection.Rejected(HostedMediaError.BAD_PAYLOAD, "audio name invalid")
        }
        val url = item.optString("url", "").trim()
        if (!url.startsWith("https://")) {
            return AudioSelection.Rejected(HostedMediaError.BAD_PAYLOAD, "audio url is not https")
        }
        val sha = item.optString("sha256", "").trim().lowercase()
        if (!SHA256_PATTERN.matches(sha)) {
            return AudioSelection.Rejected(HostedMediaError.BAD_PAYLOAD, "audio sha256 invalid")
        }
        val size = optionalLong(item, "sizeBytes")
        if (size == null || size <= 0L || size > PolicyAudioLibrary.MAX_AUDIO_BYTES) {
            return AudioSelection.Rejected(HostedMediaError.BAD_PAYLOAD, "audio size invalid")
        }
        val duration = optionalLong(item, "durationMs")
        if (duration == null || duration <= 0L ||
            duration > PolicyAudioLibrary.MAX_AUDIO_DURATION_MS
        ) {
            return AudioSelection.Rejected(HostedMediaError.BAD_PAYLOAD, "audio duration invalid")
        }
        val ext = item.optString("ext", "").trim().lowercase().removePrefix(".")
        if (ext !in AUDIO_EXTENSIONS) {
            return AudioSelection.Rejected(HostedMediaError.BAD_PAYLOAD, "audio ext invalid")
        }
        val version = item.optLong("version", 0L)
        if (version <= 0L) {
            return AudioSelection.Rejected(HostedMediaError.BAD_PAYLOAD, "audio version missing")
        }
        val nameEn = when {
            !item.has("nameEn") || item.isNull("nameEn") -> null
            item.opt("nameEn") !is String -> return AudioSelection.Rejected(
                HostedMediaError.BAD_PAYLOAD,
                "audio nameEn is not a string",
            )
            else -> item.optString("nameEn").trim().takeIf { it.isNotEmpty() }
        }
        val artist = when {
            !item.has("artist") || item.isNull("artist") -> null
            item.opt("artist") !is String -> return AudioSelection.Rejected(
                HostedMediaError.BAD_PAYLOAD,
                "audio artist is not a string",
            )
            else -> item.optString("artist").trim().takeIf { it.isNotEmpty() }
        }
        val license = when {
            !item.has("license") || item.isNull("license") -> null
            item.opt("license") !is String -> return AudioSelection.Rejected(
                HostedMediaError.BAD_PAYLOAD,
                "audio license is not a string",
            )
            else -> item.optString("license").trim().takeIf { it.isNotEmpty() }
        }
        if (listOfNotNull(nameEn, artist, license).any { it.length > MAX_ATTRIBUTION_LENGTH }) {
            return AudioSelection.Rejected(HostedMediaError.BAD_PAYLOAD, "audio attribution too long")
        }
        return AudioSelection.Parsed(
            HostedAudioPlan(
                key = cacheKey,
                id = key,
                source = source,
                name = name,
                nameEn = nameEn,
                artist = artist,
                license = license,
                url = url,
                sha256 = sha,
                sizeBytes = size,
                durationMs = duration,
                ext = ext,
                version = version,
            ),
        )
    }

    /** Null when id/version/url/ext cannot be used for a cache key and a safe file name. */
    private fun customItemOf(
        entry: JSONObject,
        order: Int,
        kind: PolicyMediaKind = PolicyMediaKind.VIDEO,
    ): HostedMediaPlanItem? {
        val numericId = entry.optLong("id", 0L)
        val version = entry.optLong("version", 0L)
        val url = entry.optString("url", "").trim()
        val ext = entry.optString("ext", "").trim().lowercase().removePrefix(".")
        if (numericId <= 0L || version <= 0L || url.isEmpty() || !EXT_PATTERN.matches(ext)) {
            return null
        }
        return HostedMediaPlanItem(
            key = "custom:$numericId:$version",
            id = "$CUSTOM_ID_PREFIX$numericId",
            kind = kind,
            version = version,
            url = url,
            ext = ext,
            order = order,
            sizeBytes = optionalLong(entry, "sizeBytes"),
            durationMs = optionalLong(entry, "durationMs"),
            hasAudio = optionalBoolean(entry, "hasAudio"),
        )
    }

    /**
     * The backend already filtered the catalog for TV, but the planner still checks the shape it
     * will write to the cache: a catalog bug must be visible rather than produce an unkeyed item.
     */
    private fun themeItemOf(
        entry: JSONObject,
        kind: PolicyMediaKind,
        order: Int,
    ): HostedMediaPlanItem? {
        val id = entry.optString("id", "").trim()
        val file = entry.optString("file", "").trim()
        val version = entry.optLong("version", 0L)
        val url = entry.optString("url", "").trim()
        val ext = entry.optString("ext", "").trim().lowercase().removePrefix(".")
        if (id.isEmpty() || file.isEmpty() || version <= 0L || url.isEmpty()) return null
        if (!EXT_PATTERN.matches(ext)) return null
        val officialId = when {
            !entry.has("officialId") || entry.isNull("officialId") -> null
            entry.opt("officialId") !is String -> return null
            else -> {
                // 旧版素材没有官方 ID：后端可能下发空串或 null，两者都按「无官方 ID」处理。
                val raw = entry.optString("officialId").trim()
                when {
                    raw.isEmpty() -> null
                    raw.startsWith(AUDIO_ID_OFFICIAL) && raw.length > AUDIO_ID_OFFICIAL.length -> raw
                    else -> return null
                }
            }
        }
        return HostedMediaPlanItem(
            key = "$THEME_KEY_PREFIX$file:$version",
            id = id,
            kind = kind,
            version = version,
            url = url,
            ext = ext,
            order = order,
            sizeBytes = optionalLong(entry, "sizeBytes"),
            durationMs = optionalLong(entry, "durationMs"),
            officialId = officialId,
            hasAudio = optionalBoolean(entry, "hasAudio"),
        )
    }

    private fun customNumericId(item: HostedMediaPlanItem): Long =
        item.id.removePrefix(CUSTOM_ID_PREFIX).toLongOrNull() ?: Long.MAX_VALUE

    private fun optionalLong(entry: JSONObject, name: String): Long? {
        if (!entry.has(name) || entry.isNull(name)) return null
        return (entry.opt(name) as? Number)?.toLong()
    }

    /** Tri-state: null means the catalogue never probed this file, treated as "may have audio". */
    private fun optionalBoolean(entry: JSONObject, name: String): Boolean? {
        if (!entry.has(name) || entry.isNull(name)) return null
        return entry.opt(name) as? Boolean
    }

    private fun reject(
        error: HostedMediaError,
        detail: String,
    ): HostedMediaPlanResult.Rejected {
        TvLog.w(TAG, "plan rejected code=${error.wire} detail=$detail")
        return HostedMediaPlanResult.Rejected(error)
    }

    private fun libraryReject(
        error: HostedMediaError,
        result: JSONObject,
        detail: String,
    ): HostedMediaLibraryResult.Rejected {
        val keys = libraryKeys(result)
        TvLog.w(
            TAG,
            "library rejected code=${error.wire} detail=$detail keys=${keys.size}",
        )
        return HostedMediaLibraryResult.Rejected(error, keys)
    }

    /**
     * Best-effort keys of a rejected payload. A key is only derived from fields that are safe
     * to combine; keys that cannot be built are left out (the backend keeps them preparing).
     */
    private fun libraryKeys(result: JSONObject): List<String> {
        val keys = LinkedHashSet<String>()
        result.optJSONArray("wallpaperMedia")?.let { array ->
            for (index in 0 until array.length()) {
                val entry = array.optJSONObject(index) ?: continue
                val file = entry.optString("file", "").trim()
                val version = entry.optLong("version", 0L)
                if (file.isNotEmpty() && version > 0L) {
                    keys += "${THEME_KEY_PREFIX}$file:$version"
                }
            }
        }
        result.optJSONObject("customWallpaper")?.let { custom ->
            custom.optJSONArray("landscapeItems")?.let { array ->
                for (index in 0 until array.length()) {
                    customKeyOf(array.optJSONObject(index))?.let { keys += it }
                }
            }
            if (custom.has("quizIntro") && !custom.isNull("quizIntro")) {
                customKeyOf(custom.optJSONObject("quizIntro"))?.let { keys += it }
            }
        }
        return keys.toList()
    }

    private fun customKeyOf(entry: JSONObject?): String? {
        if (entry == null) return null
        val id = entry.optLong("id", 0L)
        val version = entry.optLong("version", 0L)
        if (id <= 0L || version <= 0L) return null
        return "$CUSTOM_ID_PREFIX$id:$version"
    }

    private sealed class ThemeItems {
        data class Parsed(val items: List<HostedMediaPlanItem>) : ThemeItems()

        data class Rejected(
            val error: HostedMediaError,
            val detail: String,
        ) : ThemeItems() {
            init {
                TvLog.w(TAG, "plan rejected code=${error.wire} detail=$detail")
            }
        }
    }

    private sealed class Playlist {
        /** No explicit playlist decision: keep the V50/V52 theme/custom precedence. */
        object Absent : Playlist()

        data class Parsed(
            val items: List<HostedMediaPlanItem>,
            val intervalMs: Long,
        ) : Playlist()

        data class Rejected(
            val error: HostedMediaError,
            val detail: String,
        ) : Playlist() {
            init {
                TvLog.w(TAG, "plan rejected code=${error.wire} detail=$detail")
            }
        }
    }

    private sealed class CustomWallpaper {
        data class Parsed(
            val mode: HostedWallpaperMode,
            val activeId: Long,
            val videos: List<HostedMediaPlanItem>,
        ) : CustomWallpaper()

        data class Rejected(
            val error: HostedMediaError,
            val detail: String,
        ) : CustomWallpaper() {
            init {
                TvLog.w(TAG, "plan rejected code=${error.wire} detail=$detail")
            }
        }
    }

    private sealed class QuizIntro {
        data class Parsed(val item: HostedMediaPlanItem?) : QuizIntro()

        data class Rejected(
            val error: HostedMediaError,
            val detail: String,
        ) : QuizIntro() {
            init {
                TvLog.w(TAG, "plan rejected code=${error.wire} detail=$detail")
            }
        }
    }

    private sealed class BackgroundMusic {
        data class Parsed(val item: HostedAudioPlan?) : BackgroundMusic()

        data class Rejected(
            val error: HostedMediaError,
            val detail: String,
        ) : BackgroundMusic() {
            init {
                TvLog.w(TAG, "plan rejected code=${error.wire} detail=$detail")
            }
        }
    }

    private sealed class QuizAudio {
        data class Parsed(
            val item: HostedAudioPlan?,
            val enabled: Boolean,
        ) : QuizAudio()

        data class Rejected(
            val error: HostedMediaError,
            val detail: String,
        ) : QuizAudio() {
            init {
                TvLog.w(TAG, "plan rejected code=${error.wire} detail=$detail")
            }
        }
    }

    private sealed class AudioSelection {
        data class Parsed(val item: HostedAudioPlan?) : AudioSelection()

        data class Rejected(
            val error: HostedMediaError,
            val detail: String,
        ) : AudioSelection() {
            init {
                TvLog.w(TAG, "plan rejected code=${error.wire} detail=$detail")
            }
        }
    }
}