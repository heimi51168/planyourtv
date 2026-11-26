package app.orchard.tv.policy.media

import android.content.Context
import android.net.Uri
import app.orchard.tv.quiz.QuizIntroVideo
import app.orchard.tv.quiz.QuizIntroVideoEntry
import com.planyourtv.log.TvLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A playable file or a still picture, plus the cover the child surfaces show before a video frame. */
data class PolicyVideoSource(
    val uri: Uri,
    val official: Boolean,
    val poster: File?,
    val kind: PolicyMediaKind = PolicyMediaKind.VIDEO,
    /** Whether this film has an audio track the TV can decode; the sound switch checks it. */
    val hasAudio: Boolean = false,
    /**
     * The item's own duration. A carousel image stays for the configured interval; a video
     * plays to its end and the rotation only uses this as an upper bound when the decoder
     * never reports one.
     */
    val durationMs: Long = 0L,
)

/** One playable wallpaper in the active child's rotation, kept with the id the page edits. */
data class PolicyWallpaperItem(val id: String, val source: PolicyVideoSource)

/** The child wallpaper rotation. One item keeps the old single-wallpaper behaviour. */
data class PolicyWallpaperSelection(
    val items: List<PolicyWallpaperItem>,
    val intervalMs: Long,
)

/**
 * The quiz start backdrop: one picture or film, optionally accompanied by the member's own
 * audio. [audio] is resolved from the local library only; [audioEnabled] is the stored switch,
 * so a missing file falls back to the visual's original audio instead of silently failing.
 *
 * A video is muted while [audioEnabled] has a usable track, and an image is silent otherwise.
 * Hosted/cloud media stays video-only and never carries custom audio.
 */
data class PolicyQuizIntro(
    val visual: PolicyVideoSource?,
    val audio: PolicyAudioEntry? = null,
    val audioEnabled: Boolean = false,
) {
    val customAudio: Boolean get() = audioEnabled && audio != null
}

/**
 * The one read/write surface for policy films: official downloads, local
 * uploads and the two role slots.
 *
 * Role preferences live in the existing `quiz-intro` SharedPreferences, so an
 * upgrade keeps a previously chosen local upload. `builtin:*` values are
 * removed once; they are never accepted again. A role only resolves to a file
 * that is actually present and complete, and a missing file quietly falls back
 * to the Aerial default wallpaper at the call site.
 *
 * The two role slots and the wallpaper sound belong to the active child-mode
 * member: the parent mode and child mode are already separate Aerial surfaces,
 * and two children must not inherit each other's wallpaper or quiz intro. The
 * library of files itself stays shared; only the selections are scoped.
 */
object PolicyVideoLibrary {
    private const val PREFS = "quiz-intro"
    private const val SCHEMA_KEY = "media_schema"
    private const val SCHEMA = 1
    private const val MEMBER_SCHEMA_KEY = "media_member_schema"
    private const val MEMBER_SCHEMA = 1
    private const val LOCK_SOUND_KEY = "lock_sound"
    private const val LOCK_PLAYLIST_KEY = "lock_playlist"
    private const val LOCK_AUDIO_KEY = "lock_audio"
    private const val QUIZ_AUDIO_KEY = "quiz_audio"
    private const val QUIZ_AUDIO_ENABLED_KEY = "quiz_audio_enabled"

    /** The page offers these bounds; the API rejects anything outside them. */
    const val WALLPAPER_MAX_ITEMS = 20
    const val WALLPAPER_MIN_INTERVAL_MS = 15_000L
    const val WALLPAPER_MAX_INTERVAL_MS = 300_000L
    const val WALLPAPER_DEFAULT_INTERVAL_MS = 30_000L

    private val localIdPattern = Regex("[a-f0-9]{32}")
    private val officialIdPattern = Regex("official:[a-z0-9-]{1,48}")

    /**
     * The active child member. The product wiring installs this after FamilyHub initializes;
     * until then (or in unit tests) selections keep using the legacy device-wide keys.
     */
    @Volatile
    private var memberScope: () -> String? = { null }

    internal fun installMemberScope(provider: () -> String?) {
        memberScope = provider
    }

    private fun activeMemberId(): String? =
        memberScope()?.trim()?.takeIf { it.isNotEmpty() }

    /** The preference key for one member; the unsuffixed key is the pre-V41 device scope. */
    private fun memberKey(base: String, memberId: String?): String =
        if (memberId.isNullOrEmpty()) base else "$base:$memberId"

    private fun isRoleKey(key: String, role: PolicyVideoRole): Boolean =
        key == role.prefKey || key.startsWith(role.prefKey + ":")

    private fun isLockSoundKey(key: String): Boolean =
        key == LOCK_SOUND_KEY || key.startsWith(LOCK_SOUND_KEY + ":")

    private fun isLockPlaylistKey(key: String): Boolean =
        key == LOCK_PLAYLIST_KEY || key.startsWith(LOCK_PLAYLIST_KEY + ":")

    private fun isLockAudioKey(key: String): Boolean =
        key == LOCK_AUDIO_KEY || key.startsWith(LOCK_AUDIO_KEY + ":")

    private fun isQuizAudioKey(key: String): Boolean =
        key == QUIZ_AUDIO_KEY || key.startsWith(QUIZ_AUDIO_KEY + ":")

    private fun isQuizAudioEnabledKey(key: String): Boolean =
        key == QUIZ_AUDIO_ENABLED_KEY || key.startsWith(QUIZ_AUDIO_ENABLED_KEY + ":")

    /** A music selection in either consumer: the wallpaper rotation or the quiz intro. */
    private fun isAudioSelectionKey(key: String): Boolean =
        isLockAudioKey(key) || isQuizAudioKey(key)

    /**
     * Child surfaces listen for role/sound/rotation edits, including another member's change.
     * Music selection is deliberately absent: the music player has its own listener, and a
     * track change must not reload the rotation and restart the item on screen.
     */
    internal fun isMediaPreferenceKey(key: String?): Boolean =
        key != null && (PolicyVideoRole.entries.any { isRoleKey(key, it) } ||
            isLockSoundKey(key) || isLockPlaylistKey(key))

    /** The music player only needs the selection keys, not wallpaper or sound edits. */
    internal fun isAudioPreferenceKey(key: String?): Boolean = key != null && isLockAudioKey(key)

    sealed class EditResult {
        data object Ok : EditResult()
        data object NotFound : EditResult()
        data object NotReady : EditResult()
        data object Busy : EditResult()
        data object RoleInvalid : EditResult()
        data object PlaylistTooLong : EditResult()
        data object PlaylistInvalid : EditResult()
        data object DeleteFailed : EditResult()
    }

    data class LibraryItem(
        val id: String,
        val source: String,
        val kind: PolicyMediaKind,
        val name: String,
        val nameEn: String,
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val fps: Int,
        val sizeBytes: Long,
        val sha256: String,
        val posterUrl: String?,
        val state: String,
        val roles: Set<PolicyVideoRole>,
        val downloadedAtMs: Long,
        val catalog: OfficialMediaItem?,
        val local: OfficialMediaLocal?,
        val upload: QuizIntroVideoEntry?,
        val capabilityRoles: Set<PolicyVideoRole>,
        val hasAudio: Boolean,
    )

    fun ensureMigrated(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(SCHEMA_KEY, 0) >= SCHEMA) return
        synchronized(this) {
            if (prefs.getInt(SCHEMA_KEY, 0) >= SCHEMA) return
            val editor = prefs.edit()
            PolicyVideoRole.entries.forEach { role ->
                val id = prefs.getString(role.prefKey, null)
                if (id != null && !isPlayable(context, id)) {
                    editor.remove(role.prefKey)
                }
            }
            editor.putInt(SCHEMA_KEY, SCHEMA)
            editor.commit()
        }
    }

    /** Runs once when the product policy starts. */
    fun install(context: Context) {
        ensureMigrated(context)
        OfficialMediaStore.cleanupStale(context)
    }

    /**
     * Moves the pre-V41 device-wide selection to the member that was active at the upgrade,
     * once. Later members start from the bundled default instead of inheriting a sibling's
     * wallpaper. The migration waits until the family roster can name that member, so until
     * then the legacy keys stay authoritative and nothing is lost.
     */
    @Synchronized
    private fun ensureMemberMigrated(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(MEMBER_SCHEMA_KEY, 0) >= MEMBER_SCHEMA) return
        migrateMemberPreferences(context, activeMemberId())
    }

    @Synchronized
    internal fun migrateMemberPreferences(context: Context, memberId: String?) {
        if (memberId.isNullOrEmpty()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(MEMBER_SCHEMA_KEY, 0) >= MEMBER_SCHEMA) return
        val editor = prefs.edit()
        PolicyVideoRole.entries.forEach { role ->
            val legacy = prefs.getString(role.prefKey, null)
            val scoped = memberKey(role.prefKey, memberId)
            if (legacy != null && !prefs.contains(scoped)) editor.putString(scoped, legacy)
        }
        val soundKey = memberKey(LOCK_SOUND_KEY, memberId)
        if (prefs.contains(LOCK_SOUND_KEY) && !prefs.contains(soundKey)) {
            editor.putBoolean(soundKey, prefs.getBoolean(LOCK_SOUND_KEY, true))
        }
        // A pre-V41 TV could already have a rotation? It could not, but copying the key keeps
        // the one-shot migration complete for any intermediate build that wrote it unscoped.
        val playlistKey = memberKey(LOCK_PLAYLIST_KEY, memberId)
        val legacyPlaylist = prefs.getString(LOCK_PLAYLIST_KEY, null)
        if (legacyPlaylist != null && !prefs.contains(playlistKey)) {
            editor.putString(playlistKey, legacyPlaylist)
        }
        val audioKey = memberKey(LOCK_AUDIO_KEY, memberId)
        val legacyAudio = prefs.getString(LOCK_AUDIO_KEY, null)
        if (legacyAudio != null && !prefs.contains(audioKey)) {
            editor.putString(audioKey, legacyAudio)
        }
        editor.putInt(MEMBER_SCHEMA_KEY, MEMBER_SCHEMA)
        editor.commit()
    }

    private fun memberScopeId(context: Context): String? {
        ensureMemberMigrated(context)
        return activeMemberId()
    }

    /**
     * In CLOUD the execution path reads the hosted snapshot only. Local edits still succeed
     * (the parent page must not error against a stale tab), but they are not what the child
     * sees; say so once per write instead of silently pretending the change took effect.
     */
    private fun warnLocalWriteInCloud(operation: String) {
        if (HostedMediaRoute.isCloud()) {
            TvLog.w("CloudMedia", "local media write in CLOUD op=$operation; execution keeps hosted cache")
        }
    }

    /**
     * The quiz backdrop for the active child. In CLOUD the hosted snapshot is the only source:
     * it carries the visual plus the optional V56 quiz-intro audio, so the local member's custom
     * audio is never mixed into a hosted plan.
     */
    fun quizIntro(context: Context): PolicyQuizIntro =
        if (HostedMediaRoute.isCloud()) {
            val snapshot = HostedMediaRoute.snapshot()
            val audio = snapshot?.quizAudio
            val enabled = snapshot?.quizAudioEnabled == true && audio != null
            PolicyQuizIntro(visual = snapshot?.quizSource, audio = audio, audioEnabled = enabled)
        } else {
            quizIntroFor(context, memberScopeId(context))
        }

    /** The visual alone, kept for callers that only need the URI/kind. */
    fun quizSource(context: Context): PolicyVideoSource? = quizIntro(context).visual

    internal fun quizIntroFor(context: Context, memberId: String?): PolicyQuizIntro {
        ensureMigrated(context)
        val visual = roleId(context, PolicyVideoRole.QUIZ_INTRO, memberId)
            ?.let { sourceById(context, it) }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val audio = prefs.getString(memberKey(QUIZ_AUDIO_KEY, memberId), null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { PolicyAudioLibrary.entry(context, it) }
        val enabled = prefs.getBoolean(memberKey(QUIZ_AUDIO_ENABLED_KEY, memberId), false)
        return PolicyQuizIntro(visual = visual, audio = audio, audioEnabled = enabled && audio != null)
    }

    /** The first item is the film/shape shown until the rotation advances. */
    fun lockSource(context: Context): PolicyVideoSource? =
        lockSelection(context).items.firstOrNull()?.source

    /**
     * The active child's wallpaper rotation. Missing files are skipped here; an empty list means
     * the call site draws the bundled default. [intervalMs] is only meaningful with two or more
     * items.
     */
    fun lockSelection(context: Context): PolicyWallpaperSelection =
        if (HostedMediaRoute.isCloud()) {
            HostedMediaRoute.snapshot()?.selection
                ?: PolicyWallpaperSelection(emptyList(), HOSTED_WALLPAPER_INTERVAL_MS)
        } else {
            lockSelectionFor(context, memberScopeId(context))
        }

    internal fun lockSelectionFor(
        context: Context,
        memberId: String?,
    ): PolicyWallpaperSelection {
        ensureMigrated(context)
        val ids = lockPlaylistIdsFor(context, memberId)
        // Resolve the local uploads once; a 20-item rotation must not re-list the library per id.
        val local = if (ids.any { localIdPattern.matches(it) }) {
            QuizIntroVideo.all(context).associateBy { it.id }
        } else {
            emptyMap()
        }
        val items = ids.mapNotNull { id ->
            val source = when {
                officialIdPattern.matches(id) -> sourceById(context, id)
                localIdPattern.matches(id) -> local[id]?.let { localSource(context, it) }
                else -> null
            }
            source?.let { PolicyWallpaperItem(id, it) }
        }
        return PolicyWallpaperSelection(items, lockIntervalFor(context, memberId))
    }

    /**
     * The stored lock role id, even when its file is not currently readable. Child surfaces use
     * this to decide whether they own the wallpaper; the picture then falls back to Aerial's
     * default rather than to the owner's Home wallpaper.
     */
    internal fun lockRoleId(context: Context): String? =
        if (HostedMediaRoute.isCloud()) {
            HostedMediaRoute.snapshot()?.lockRoleId ?: HostedMediaRoute.NONE_ID
        } else {
            lockPlaylistIdsFor(context, memberScopeId(context)).firstOrNull()
        }

    fun source(context: Context, role: PolicyVideoRole): PolicyVideoSource? =
        if (HostedMediaRoute.isCloud()) {
            if (role == PolicyVideoRole.LOCK_WALLPAPER) {
                lockSelection(context).items.firstOrNull()?.source
            } else {
                quizSource(context)
            }
        } else {
            sourceFor(context, role, memberScopeId(context))
        }

    private fun sourceFor(
        context: Context,
        role: PolicyVideoRole,
        memberId: String?,
    ): PolicyVideoSource? {
        ensureMigrated(context)
        if (role == PolicyVideoRole.LOCK_WALLPAPER) {
            // Match the rotation: skip an unreadable first item instead of falling back.
            return lockSelectionFor(context, memberId).items.firstOrNull()?.source
        }
        val id = roleId(context, role, memberId) ?: return null
        return sourceById(context, id)
    }

    private fun sourceById(context: Context, id: String): PolicyVideoSource? = when {
        officialIdPattern.matches(id) -> {
            val local = OfficialMediaStore.read(context, id) ?: return null
            PolicyVideoSource(
                uri = Uri.fromFile(local.file),
                official = true,
                poster = local.poster,
                kind = local.item.kind,
                hasAudio = local.item.hasAudio,
                durationMs = local.item.durationMs,
            )
        }
        localIdPattern.matches(id) -> {
            val upload = QuizIntroVideo.all(context).firstOrNull { it.id == id } ?: return null
            localSource(context, upload)
        }
        else -> null
    }

    private fun localSource(context: Context, upload: QuizIntroVideoEntry): PolicyVideoSource =
        PolicyVideoSource(
            uri = Uri.fromFile(upload.file),
            official = false,
            poster = QuizIntroVideo.posterOf(context, upload.id),
            kind = upload.kind,
            hasAudio = upload.hasAudio,
            durationMs = upload.durationMs,
        )

    /**
     * Sets a role (or both) to [id]. An empty [id] clears only [roles], so
     * switching quiz back to the wallpaper never disturbs a lock choice.
     *
     * Selecting a single lock item replaces any rotation: the old page's picker and the TV's
     * quick choice both mean "show this one wallpaper".
     */
    fun select(context: Context, roles: Set<PolicyVideoRole>, id: String?): EditResult =
        selectFor(context, roles, id, memberScopeId(context))

    internal fun selectFor(
        context: Context,
        roles: Set<PolicyVideoRole>,
        id: String?,
        memberId: String?,
    ): EditResult {
        if (roles.isEmpty()) return EditResult.RoleInvalid
        warnLocalWriteInCloud("select")
        ensureMigrated(context)
        val target = id?.trim().orEmpty()
        if (target.isEmpty()) {
            val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            roles.forEach {
                editor.remove(memberKey(it.prefKey, memberId))
                if (it == PolicyVideoRole.LOCK_WALLPAPER) {
                    editor.remove(memberKey(LOCK_PLAYLIST_KEY, memberId))
                }
            }
            val saved = editor.commit()
            return if (saved) EditResult.Ok else EditResult.DeleteFailed
        }
        if (!isPlayable(context, target)) return EditResult.NotFound
        val capability = capabilityRoles(context, target)
        if (!capability.containsAll(roles)) return EditResult.RoleInvalid
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        roles.forEach {
            editor.putString(memberKey(it.prefKey, memberId), target)
            // A single pick is the whole rotation; the lock id above remains its legacy alias.
            if (it == PolicyVideoRole.LOCK_WALLPAPER) {
                editor.remove(memberKey(LOCK_PLAYLIST_KEY, memberId))
            }
        }
        if (!editor.commit()) return EditResult.DeleteFailed
        // The sound preference is independent of which item is selected: a picture or a silent
        // video simply cannot resolve it to "on", and a later audio video keeps the default.
        return EditResult.Ok
    }

    /**
     * Replaces the active child's wallpaper rotation. The order is the order the child sees;
     * an empty [ids] clears it back to the bundled default. The first item also becomes the
     * legacy `lock_id`, so builds from before the rotation still show one of the chosen pictures.
     */
    fun saveLockPlaylist(context: Context, ids: List<String>, intervalMs: Long): EditResult =
        saveLockPlaylistFor(context, ids, intervalMs, memberScopeId(context))

    internal fun saveLockPlaylistFor(
        context: Context,
        ids: List<String>,
        intervalMs: Long,
        memberId: String?,
    ): EditResult {
        warnLocalWriteInCloud("saveLockPlaylist")
        ensureMigrated(context)
        val normalized = ids.map { it.trim() }
        if (normalized.size > WALLPAPER_MAX_ITEMS) return EditResult.PlaylistTooLong
        if (normalized.distinct().size != normalized.size) return EditResult.PlaylistInvalid
        if (normalized.any { !officialIdPattern.matches(it) && !localIdPattern.matches(it) }) {
            return EditResult.PlaylistInvalid
        }
        if (normalized.isNotEmpty() &&
            intervalMs !in WALLPAPER_MIN_INTERVAL_MS..WALLPAPER_MAX_INTERVAL_MS
        ) {
            return EditResult.PlaylistInvalid
        }
        normalized.forEach { id ->
            if (!isPlayable(context, id)) return EditResult.NotFound
            if (!capabilityRoles(context, id).contains(PolicyVideoRole.LOCK_WALLPAPER)) {
                return EditResult.RoleInvalid
            }
        }
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        val playlistKey = memberKey(LOCK_PLAYLIST_KEY, memberId)
        val lockKey = memberKey(PolicyVideoRole.LOCK_WALLPAPER.prefKey, memberId)
        if (normalized.isEmpty()) {
            editor.remove(playlistKey).remove(lockKey)
        } else {
            editor.putString(playlistKey, encodePlaylist(normalized, intervalMs))
            editor.putString(lockKey, normalized.first())
        }
        return if (editor.commit()) EditResult.Ok else EditResult.DeleteFailed
    }

    /**
     * One-save wallpaper configuration: rotation, interval, master sound switch and background
     * music. Every field is validated before the single commit, so the child never sees a
     * rotation updated with an old sound choice or a half-applied audio selection.
     */
    fun saveLockConfig(
        context: Context,
        ids: List<String>,
        intervalMs: Long,
        sound: Boolean,
        audioId: String?,
    ): EditResult = saveLockConfigFor(
        context,
        ids,
        intervalMs,
        sound,
        audioId,
        memberScopeId(context),
    )

    internal fun saveLockConfigFor(
        context: Context,
        ids: List<String>,
        intervalMs: Long,
        sound: Boolean,
        audioId: String?,
        memberId: String?,
    ): EditResult {
        warnLocalWriteInCloud("saveLockConfig")
        ensureMigrated(context)
        val normalized = ids.map { it.trim() }
        if (normalized.size > WALLPAPER_MAX_ITEMS) return EditResult.PlaylistTooLong
        if (normalized.distinct().size != normalized.size) return EditResult.PlaylistInvalid
        if (normalized.any { !officialIdPattern.matches(it) && !localIdPattern.matches(it) }) {
            return EditResult.PlaylistInvalid
        }
        if (normalized.isNotEmpty() &&
            intervalMs !in WALLPAPER_MIN_INTERVAL_MS..WALLPAPER_MAX_INTERVAL_MS
        ) {
            return EditResult.PlaylistInvalid
        }
        val sources = ArrayList<PolicyVideoSource>(normalized.size)
        normalized.forEach { id ->
            if (!isPlayable(context, id)) return EditResult.NotFound
            if (!capabilityRoles(context, id).contains(PolicyVideoRole.LOCK_WALLPAPER)) {
                return EditResult.RoleInvalid
            }
            sources += sourceById(context, id) ?: return EditResult.NotFound
        }
        val audioKey = audioId?.trim().orEmpty()
        val audio = if (audioKey.isEmpty()) {
            null
        } else {
            if (!PolicyAudioLibrary.isAudioId(audioKey)) return EditResult.NotFound
            PolicyAudioLibrary.entry(context, audioKey) ?: return EditResult.NotFound
        }
        if (sound && sources.none { it.hasAudio } && audio == null) {
            return EditResult.RoleInvalid
        }
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        val playlistKey = memberKey(LOCK_PLAYLIST_KEY, memberId)
        val lockKey = memberKey(PolicyVideoRole.LOCK_WALLPAPER.prefKey, memberId)
        if (normalized.isEmpty()) {
            editor.remove(playlistKey).remove(lockKey)
        } else {
            editor.putString(playlistKey, encodePlaylist(normalized, intervalMs))
            editor.putString(lockKey, normalized.first())
        }
        editor.putBoolean(memberKey(LOCK_SOUND_KEY, memberId), sound)
        if (audio == null) {
            editor.remove(memberKey(LOCK_AUDIO_KEY, memberId))
        } else {
            editor.putString(memberKey(LOCK_AUDIO_KEY, memberId), audio.id)
        }
        return if (editor.commit()) EditResult.Ok else EditResult.DeleteFailed
    }

    /**
     * One-save quiz-intro configuration. The visual may be a video or a local picture; the
     * optional audio is a complete local/downloaded track. Enabling audio without a track (or
     * without a visual) is rejected instead of producing a silently broken intro.
     */
    fun saveQuizIntro(
        context: Context,
        id: String?,
        audioEnabled: Boolean,
        audioId: String?,
    ): EditResult = saveQuizIntroFor(context, id, audioEnabled, audioId, memberScopeId(context))

    internal fun saveQuizIntroFor(
        context: Context,
        id: String?,
        audioEnabled: Boolean,
        audioId: String?,
        memberId: String?,
    ): EditResult {
        warnLocalWriteInCloud("saveQuizIntro")
        ensureMigrated(context)
        val visualId = id?.trim().orEmpty()
        if (visualId.isNotEmpty()) {
            if (!isPlayable(context, visualId)) return EditResult.NotFound
            if (!capabilityRoles(context, visualId).contains(PolicyVideoRole.QUIZ_INTRO)) {
                return EditResult.RoleInvalid
            }
        }
        val audioKey = audioId?.trim().orEmpty()
        val audio = if (audioKey.isEmpty()) {
            null
        } else {
            if (!PolicyAudioLibrary.isAudioId(audioKey)) return EditResult.NotFound
            PolicyAudioLibrary.entry(context, audioKey) ?: return EditResult.NotFound
        }
        if (audioEnabled && (visualId.isEmpty() || audio == null)) {
            return EditResult.RoleInvalid
        }
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        val roleKey = memberKey(PolicyVideoRole.QUIZ_INTRO.prefKey, memberId)
        if (visualId.isEmpty()) editor.remove(roleKey) else editor.putString(roleKey, visualId)
        editor.putBoolean(memberKey(QUIZ_AUDIO_ENABLED_KEY, memberId), audioEnabled)
        if (audio == null) {
            editor.remove(memberKey(QUIZ_AUDIO_KEY, memberId))
        } else {
            editor.putString(memberKey(QUIZ_AUDIO_KEY, memberId), audio.id)
        }
        return if (editor.commit()) EditResult.Ok else EditResult.DeleteFailed
    }

    private data class StoredPlaylist(val ids: List<String>, val intervalMs: Long)

    private fun encodePlaylist(ids: List<String>, intervalMs: Long): String {
        val array = JSONArray()
        ids.forEach { array.put(it) }
        return JSONObject()
            .put("v", 1)
            .put("intervalMs", intervalMs)
            .put("ids", array)
            .toString()
    }

    private fun readPlaylist(raw: String?): StoredPlaylist? {
        if (raw.isNullOrBlank()) return null
        return runCatching<StoredPlaylist?> {
            val json = JSONObject(raw)
            val array = json.optJSONArray("ids") ?: return@runCatching null
            val seen = linkedSetOf<String>()
            for (index in 0 until array.length()) {
                val id = array.optString(index, "").trim()
                if (officialIdPattern.matches(id) || localIdPattern.matches(id)) seen += id
                if (seen.size == WALLPAPER_MAX_ITEMS) break
            }
            val interval = json.optLong("intervalMs", WALLPAPER_DEFAULT_INTERVAL_MS)
                .coerceIn(WALLPAPER_MIN_INTERVAL_MS, WALLPAPER_MAX_INTERVAL_MS)
            StoredPlaylist(seen.toList(), interval)
        }.getOrNull()
    }

    private fun lockPlaylistIdsFor(context: Context, memberId: String?): List<String> {
        ensureMigrated(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = readPlaylist(prefs.getString(memberKey(LOCK_PLAYLIST_KEY, memberId), null))
        if (stored != null && stored.ids.isNotEmpty()) return stored.ids
        // No rotation yet: the single legacy slot is a one-item rotation.
        return listOfNotNull(roleId(context, PolicyVideoRole.LOCK_WALLPAPER, memberId))
    }

    private fun lockIntervalFor(context: Context, memberId: String?): Long {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return readPlaylist(prefs.getString(memberKey(LOCK_PLAYLIST_KEY, memberId), null))
            ?.intervalMs
            ?: WALLPAPER_DEFAULT_INTERVAL_MS
    }

    /** Deletes a local copy/upload and clears every role that pointed at it, for every member. */
    fun delete(context: Context, id: String): EditResult {
        warnLocalWriteInCloud("deleteLocal")
        if (!officialIdPattern.matches(id) && !localIdPattern.matches(id)) {
            return EditResult.NotFound
        }
        val active = OfficialMediaDownloader.progress(id)
        if (active?.state == OfficialMediaDownloader.STATE_DOWNLOADING ||
            active?.state == OfficialMediaDownloader.STATE_VERIFYING
        ) {
            return EditResult.Busy
        }
        ensureMigrated(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val all = prefs.all
        // A shared library file can be one member's wallpaper and another member's intro, and it
        // can sit anywhere in a rotation. Clear every slot and strip it from every rotation.
        val before = mutableMapOf<String, Any?>()
        val removals = mutableSetOf<String>()
        val replacements = mutableMapOf<String, String>()
        all.forEach { (key, value) ->
            if (isLockPlaylistKey(key) && value is String) {
                val stored = readPlaylist(value) ?: return@forEach
                if (id !in stored.ids) return@forEach
                before[key] = value
                val remaining = stored.ids.filterNot { it == id }
                if (remaining.isEmpty()) {
                    removals += key
                } else {
                    replacements[key] = encodePlaylist(remaining, stored.intervalMs)
                    // The legacy alias stays a valid first item for a downgraded build.
                    val suffix = key.removePrefix(LOCK_PLAYLIST_KEY)
                    val lockKey = PolicyVideoRole.LOCK_WALLPAPER.prefKey + suffix
                    before.putIfAbsent(lockKey, all[lockKey])
                    replacements[lockKey] = remaining.first()
                }
            } else if (value == id && PolicyVideoRole.entries.any { isRoleKey(key, it) }) {
                before[key] = value
                removals += key
            }
        }
        val playable = isPlayable(context, id)
        if (before.isEmpty() && !playable) return EditResult.NotFound
        val cleared = prefs.edit().apply {
            removals.forEach { key -> if (key !in replacements) remove(key) }
            replacements.forEach { (key, value) -> putString(key, value) }
        }.commit()
        if (!cleared) return EditResult.DeleteFailed
        if (!playable) {
            OfficialMediaDownloader.forget(id)
            return EditResult.Ok
        }

        val deleted = when {
            officialIdPattern.matches(id) -> OfficialMediaStore.delete(context, id)
            else -> QuizIntroVideo.delete(context, id)
        }
        if (!deleted) {
            val restore = prefs.edit()
            before.forEach { (key, value) ->
                when (value) {
                    null -> restore.remove(key)
                    is String -> restore.putString(key, value)
                    is Boolean -> restore.putBoolean(key, value)
                    is Int -> restore.putInt(key, value)
                    is Long -> restore.putLong(key, value)
                    is Float -> restore.putFloat(key, value)
                    is Set<*> -> restore.putStringSet(
                        key,
                        value.filterIsInstance<String>().toSet(),
                    )
                    else -> Unit
                }
            }
            restore.commit()
            return EditResult.DeleteFailed
        }
        OfficialMediaDownloader.forget(id)
        return EditResult.Ok
    }

    /**
     * A downloaded official picture is a complete local file, so local mode can use it in
     * either role: the manifest offers cloud pictures as wallpapers only, but the quiz intro
     * renders a still locally. Hosted plans remain video-only and never read this capability.
     */
    private fun downloadedCapabilityRoles(local: OfficialMediaLocal): Set<PolicyVideoRole> =
        when {
            local.item.kind == PolicyMediaKind.IMAGE -> PolicyVideoRole.entries.toSet()
            local.item.roles.isNotEmpty() -> local.item.roles
            else -> PolicyVideoRole.entries.toSet()
        }

    fun capabilityRoles(context: Context, id: String): Set<PolicyVideoRole> = when {
        localIdPattern.matches(id) -> {
            val upload = QuizIntroVideo.all(context).firstOrNull { it.id == id }
            // Every locally stored item can fill either role: the upload path already checked
            // it against this TV's decoders, and the quiz player renders both films and the
            // still picture. Hosted/cloud media keeps its own video-only contract.
            when (upload?.kind) {
                PolicyMediaKind.IMAGE, PolicyMediaKind.VIDEO -> PolicyVideoRole.entries.toSet()
                null -> emptySet()
            }
        }
        officialIdPattern.matches(id) -> {
            val catalog = OfficialMediaCatalog.find(context, id)
            val installed = OfficialMediaStore.read(context, id)
            when {
                // Check the downloaded file first: the manifest's image roles are lock-only.
                installed?.item?.kind == PolicyMediaKind.IMAGE ->
                    downloadedCapabilityRoles(installed)
                catalog != null -> catalog.roles
                installed != null -> downloadedCapabilityRoles(installed)
                else -> emptySet()
            }
        }
        else -> emptySet()
    }

    /** Snapshot for the parent center; call off the main thread. */
    fun snapshot(
        context: Context,
        autoRefreshCatalog: Boolean = true,
        // The main parent page and the wallpaper page already have an explicit catalogue
        // request. Music is fetched only when the BGM picker asks, so merely opening the
        // parent center never contacts the audio host.
        autoRefreshAudioCatalog: Boolean = false,
    ): JSONObject {
        ensureMigrated(context)
        val memberId = memberScopeId(context)
        val lockIds = lockPlaylistIdsFor(context, memberId)
        val roles = PolicyVideoRole.entries.associateWith { role ->
            if (role == PolicyVideoRole.LOCK_WALLPAPER) lockIds.firstOrNull()
            else roleId(context, role, memberId)
        }
        val catalog = OfficialMediaCatalog.cached(context)
        val downloaded = OfficialMediaStore.list(context).associateBy { it.id }
        val uploads = QuizIntroVideo.all(context)
        val items = ArrayList<LibraryItem>()

        catalog?.items?.forEach { item ->
            val local = downloaded[item.id]
            val progress = OfficialMediaDownloader.progress(item.id)
            val state = when {
                progress?.state == OfficialMediaDownloader.STATE_DOWNLOADING -> "DOWNLOADING"
                progress?.state == OfficialMediaDownloader.STATE_VERIFYING -> "VERIFYING"
                progress?.state == OfficialMediaDownloader.STATE_FAILED -> "FAILED"
                local == null -> "NOT_DOWNLOADED"
                local.item.sha256 == item.sha256 -> "READY"
                else -> "UPDATE_AVAILABLE"
            }
            items += LibraryItem(
                id = item.id,
                source = "official",
                kind = item.kind,
                name = item.name,
                nameEn = item.nameEn,
                durationMs = item.durationMs,
                width = item.width,
                height = item.height,
                fps = item.fps,
                sizeBytes = item.bytes,
                sha256 = item.sha256,
                // A downloaded cover comes from the TV first, so the page still shows it
                // offline and a cloud picture (its own cover) never leaves the device again.
                posterUrl = posterRoute(item.id, local?.poster)
                    ?: item.poster.url.takeIf { it.isNotBlank() },
                state = state,
                roles = roles.filter { it.value == item.id }.keys,
                downloadedAtMs = local?.downloadedAtMs ?: 0L,
                catalog = item,
                local = local,
                upload = null,
                // The catalogue's still pictures are cloud wallpapers only. Once downloaded,
                // the local file can also open the quiz, so tell the parent page that.
                capabilityRoles = if (local != null && local.item.kind == PolicyMediaKind.IMAGE) {
                    downloadedCapabilityRoles(local)
                } else {
                    item.roles
                },
                hasAudio = item.hasAudio,
            )
        }

        // A film removed from the manifest stays playable if it is already on disk. This
        // also keeps downloaded media visible before the catalogue has ever loaded.
        downloaded.values.filter { local ->
            catalog?.items?.any { it.id == local.id } != true
        }
            .forEach { local ->
                items += LibraryItem(
                    id = local.id,
                    source = "official",
                    kind = local.item.kind,
                    name = local.item.name,
                    nameEn = local.item.nameEn,
                    durationMs = local.item.durationMs,
                    width = local.item.width,
                    height = local.item.height,
                    fps = local.item.fps,
                    sizeBytes = local.item.bytes,
                    sha256 = local.item.sha256,
                    posterUrl = posterRoute(local.id, local.poster),
                    state = "READY",
                    roles = roles.filter { it.value == local.id }.keys,
                    downloadedAtMs = local.downloadedAtMs,
                    catalog = null,
                    local = local,
                    upload = null,
                    capabilityRoles = downloadedCapabilityRoles(local),
                    hasAudio = local.item.hasAudio,
                )
            }

        uploads.forEach { upload ->
            items += LibraryItem(
                id = upload.id,
                source = "local",
                kind = upload.kind,
                name = upload.name.substringBeforeLast('.', upload.name),
                nameEn = upload.name.substringBeforeLast('.', upload.name),
                durationMs = upload.durationMs,
                width = upload.width,
                height = upload.height,
                fps = 0,
                sizeBytes = upload.sizeBytes,
                sha256 = "",
                posterUrl = posterRoute(upload.id, QuizIntroVideo.posterOf(context, upload.id)),
                state = "READY",
                roles = roles.filter { it.value == upload.id }.keys,
                downloadedAtMs = upload.createdAt,
                catalog = null,
                local = null,
                upload = upload,
                capabilityRoles = capabilityRoles(context, upload.id),
                hasAudio = upload.hasAudio,
            )
        }

        val itemArray = JSONArray()
        items.forEach { item ->
            val progress = OfficialMediaDownloader.progress(item.id)
            itemArray.put(
                JSONObject()
                    .put("id", item.id)
                    .put("source", item.source)
                    .put("kind", item.kind.wire)
                    .put("name", item.name)
                    .put("nameEn", item.nameEn)
                    .put("durationMs", item.durationMs)
                    .put("width", item.width)
                    .put("height", item.height)
                    .put("fps", item.fps)
                    .put("sizeBytes", item.sizeBytes)
                    .put("sha256", item.sha256)
                    .put("posterUrl", item.posterUrl ?: JSONObject.NULL)
                    // Preview-only cloud URL for the parent page's <video>. The TV execution
                    // paths never read this field and keep playing only the local copy.
                    .put("playUrl", previewPlayUrl(item) ?: JSONObject.NULL)
                    .put("state", item.state)
                    .put("roles", JSONArray(item.roles.map { it.wire }))
                    .put("capabilityRoles", JSONArray(item.capabilityRoles.map { it.wire }))
                    .put("hasAudio", item.hasAudio)
                    .put("downloadedAt", item.downloadedAtMs / 1000L)
                    .put(
                        "download",
                        progress?.toJson() ?: JSONObject()
                            .put("state", item.state)
                            .put(
                                "bytes",
                                if (item.state == "READY" || item.state == "UPDATE_AVAILABLE") item.sizeBytes else 0L,
                            )
                            .put("totalBytes", item.sizeBytes)
                            .put(
                                "percent",
                                if (item.state == "READY" || item.state == "UPDATE_AVAILABLE") 100 else 0,
                            )
                            .put("error", JSONObject.NULL),
                    ),
            )
        }

        val lockId = roles[PolicyVideoRole.LOCK_WALLPAPER]
        val lockHasAudio = lockSelectionFor(context, memberId).items.any { it.source.hasAudio }
        val audioEntries = PolicyAudioLibrary.all(context)
        val audioDownloads = OfficialAudioStore.list(context)
        val selectedAudioId = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(memberKey(LOCK_AUDIO_KEY, memberId), null)
            ?.trim()
            .orEmpty()
        // Resolving through the library means a cloud id counts only after its bytes are here.
        val selectedAudio = audioEntries.firstOrNull { it.id == selectedAudioId }
            ?: audioDownloads.firstOrNull { it.id == selectedAudioId }
                ?.let { PolicyAudioLibrary.entry(context, it.id) }
        val quizAudioId = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(memberKey(QUIZ_AUDIO_KEY, memberId), null)
            ?.trim()
            .orEmpty()
        val quizAudio = audioEntries.firstOrNull { it.id == quizAudioId }
            ?: audioDownloads.firstOrNull { it.id == quizAudioId }
                ?.let { PolicyAudioLibrary.entry(context, it.id) }
        val quizAudioEnabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(memberKey(QUIZ_AUDIO_ENABLED_KEY, memberId), false) &&
            quizAudio != null
        val downloadedAudio = audioDownloads.associateBy { it.id }
        val audioArray = JSONArray()
        audioEntries.forEach { entry ->
            audioArray.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("source", entry.source)
                    .put("name", PolicyAudioLibrary.displayName(entry.name))
                    .put("nameEn", PolicyAudioLibrary.displayName(entry.name))
                    .put("sizeBytes", entry.sizeBytes)
                    .put("durationMs", entry.durationMs)
                    .put("createdAt", entry.createdAt)
                    .put("selected", entry.id == selectedAudio?.id)
                    .put("quizSelected", entry.id == quizAudio?.id)
                    .put("roles", audioRolesJson(entry.id, selectedAudio, quizAudio)),
            )
        }

        val cloudAudioArray = JSONArray()
        val audioCatalog = OfficialAudioCatalog.cached(context)
        val cloudSeen = HashSet<String>()
        audioCatalog?.tracks?.forEach { track ->
            cloudSeen += track.id
            cloudAudioArray.put(
                audioCloudJson(track, downloadedAudio[track.id], selectedAudio, quizAudio),
            )
        }
        // A downloaded track removed from the catalogue stays visible and playable offline.
        audioDownloads.filter { it.id !in cloudSeen }.forEach { local ->
            cloudAudioArray.put(audioCloudJson(local.track, local, selectedAudio, quizAudio))
        }
        return JSONObject()
            .put("catalog", OfficialMediaCatalog.snapshot(context, autoRefreshCatalog))
            .put(
                "roles",
                JSONObject()
                    .put("quiz", roles[PolicyVideoRole.QUIZ_INTRO] ?: JSONObject.NULL)
                    .put("lock", lockId ?: JSONObject.NULL),
            )
            .put(
                "lockPlaylist",
                JSONObject()
                    .put("intervalMs", lockIntervalFor(context, memberId))
                    .put("ids", JSONArray(lockIds)),
            )
            // The master switch is usable with either a video soundtrack or background music.
            .put(
                "lockSound",
                lockSoundPreference(context, memberId) && (lockHasAudio || selectedAudio != null),
            )
            .put(
                "quizAudio",
                JSONObject()
                    .put("enabled", quizAudioEnabled)
                    .put("selectedId", quizAudio?.id ?: JSONObject.NULL)
                    .put(
                        "selectedName",
                        quizAudio?.let { PolicyAudioLibrary.displayName(it.name) }
                            ?: JSONObject.NULL,
                    ),
            )
            .put(
                "audio",
                JSONObject()
                    .put("selectedId", selectedAudio?.id ?: JSONObject.NULL)
                    .put(
                        "selectedName",
                        selectedAudio?.let { PolicyAudioLibrary.displayName(it.name) }
                            ?: JSONObject.NULL,
                    )
                    .put("items", audioArray)
                    .put("cloudItems", cloudAudioArray)
                    .put("catalog", OfficialAudioCatalog.snapshot(context, autoRefreshAudioCatalog))
                    .put("downloads", OfficialAudioDownloader.snapshot())
                    .put("maxBytes", PolicyAudioLibrary.MAX_AUDIO_BYTES)
                    .put("maxDurationMs", PolicyAudioLibrary.MAX_AUDIO_DURATION_MS)
                    .put("keepFreeBytes", OfficialAudioDownloader.KEEP_FREE_BYTES),
            )
            .put("items", itemArray)
            .put("downloads", OfficialMediaDownloader.snapshot())
            .put(
                "limits",
                JSONObject()
                    // Neither library has a count or total-byte quota; the page only needs the
                    // per-file limits and the free-space guard below.
                    .put("localKeepFreeBytes", QuizIntroVideo.KEEP_FREE_BYTES)
                    .put("maxWallpaperDurationMs", 0L)
                    .put("maxWallpaperFileBytes", PolicyVideoValidation.MAX_WALLPAPER_FILE_BYTES)
                    .put("maxQuizDurationMs", PolicyVideoValidation.MAX_QUIZ_DURATION_MS)
                    .put("maxQuizFileBytes", PolicyVideoValidation.MAX_QUIZ_FILE_BYTES)
                    .put("maxImageUploadBytes", QuizIntroVideo.MAX_UPLOAD_BYTES)
                    .put("maxImageStoredBytes", QuizIntroVideo.MAX_IMAGE_BYTES)
                    .put("maxRequestBytes", PolicyVideoValidation.MAX_REQUEST_BYTES)
                    .put("maxWallpaperItems", WALLPAPER_MAX_ITEMS)
                    .put("minWallpaperIntervalMs", WALLPAPER_MIN_INTERVAL_MS)
                    .put("maxWallpaperIntervalMs", WALLPAPER_MAX_INTERVAL_MS)
                    .put("defaultWallpaperIntervalMs", WALLPAPER_DEFAULT_INTERVAL_MS)
                    // Legacy page aliases. Never widen these to the wallpaper limits.
                    .put("keepFreeBytes", OfficialMediaDownloader.KEEP_FREE_BYTES)
                    .put("maxFileBytes", PolicyVideoValidation.MAX_FILE_BYTES)
                    .put("maxUploadBytes", QuizIntroVideo.MAX_UPLOAD_BYTES)
                    .put("maxImageBytes", QuizIntroVideo.MAX_IMAGE_BYTES)
                    .put("maxDurationMs", PolicyVideoValidation.MAX_DURATION_MS),
            )
    }

    /** Whether video sound is enabled and some item in the active member's rotation can supply it. */
    fun lockSound(context: Context): Boolean {
        if (HostedMediaRoute.isCloud()) {
            val snapshot = HostedMediaRoute.snapshot() ?: return false
            return snapshot.sound && snapshot.selection.items.any { it.source.hasAudio }
        }
        val memberId = memberScopeId(context)
        return lockSoundPreference(context, memberId) &&
            lockSelectionFor(context, memberId).items.any { it.source.hasAudio }
    }

    /** The member's stored switch, independent of whether the current item has audio. */
    fun lockSoundPreference(context: Context): Boolean =
        if (HostedMediaRoute.isCloud()) {
            HostedMediaRoute.snapshot()?.sound ?: false
        } else {
            lockSoundPreference(context, memberScopeId(context))
        }

    internal fun lockSoundPreference(context: Context, memberId: String?): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(memberKey(LOCK_SOUND_KEY, memberId), true)

    /**
     * The parent page's master wallpaper-sound switch. It enables both the video's own
     * soundtrack and the background music. Turning it on requires either a selected video with
     * a decodable audio track or a selected background track; the caller maps `false` to
     * `MEDIA_SOUND_UNAVAILABLE`.
     */
    fun setLockSound(context: Context, enabled: Boolean): Boolean =
        setLockSound(context, enabled, memberScopeId(context))

    internal fun setLockSound(context: Context, enabled: Boolean, memberId: String?): Boolean {
        warnLocalWriteInCloud("setLockSound")
        if (enabled &&
            lockSelectionFor(context, memberId).items.none { it.source.hasAudio } &&
            lockAudioFor(context, memberId) == null
        ) {
            return false
        }
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(memberKey(LOCK_SOUND_KEY, memberId), enabled)
            .commit()
    }

    /**
     * The active member's background-music track. It plays under the child wallpaper rotation
     * while the master wallpaper-sound preference is on; [lockSound] only reports the video
     * soundtrack's own availability.
     */
    fun lockAudio(context: Context): PolicyAudioEntry? {
        if (!HostedMediaRoute.isCloud()) return lockAudioFor(context, memberScopeId(context))
        // V54: the cloud selection is part of the hosted manifest. A missing or truncated
        // file is reported and produces no music; it is never replaced by a local track.
        val audio = HostedMediaRoute.snapshot()?.audio ?: return null
        if (!audio.file.isFile || audio.file.length() <= 0L) {
            TvLog.e("CloudMedia", "background music file missing id=${audio.id}")
            return null
        }
        if (audio.sizeBytes > 0L && audio.file.length() != audio.sizeBytes) {
            TvLog.e("CloudMedia", "background music size mismatch id=${audio.id}")
            return null
        }
        return audio
    }

    internal fun lockAudioFor(context: Context, memberId: String?): PolicyAudioEntry? {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(memberKey(LOCK_AUDIO_KEY, memberId), null)
            ?.trim()
            .orEmpty()
        if (id.isEmpty()) return null
        return PolicyAudioLibrary.entry(context, id)
    }

    /** Selects one local track for the active member; an empty id removes the background music. */
    fun selectLockAudio(context: Context, id: String?): EditResult =
        selectLockAudioFor(context, id, memberScopeId(context))

    internal fun selectLockAudioFor(
        context: Context,
        id: String?,
        memberId: String?,
    ): EditResult {
        warnLocalWriteInCloud("selectLockAudio")
        val target = id?.trim().orEmpty()
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        val key = memberKey(LOCK_AUDIO_KEY, memberId)
        if (target.isEmpty()) {
            editor.remove(key)
        } else {
            if (!PolicyAudioLibrary.isAudioId(target)) return EditResult.NotFound
            if (PolicyAudioLibrary.entry(context, target) == null) return EditResult.NotFound
            editor.putString(key, target)
        }
        return if (editor.commit()) EditResult.Ok else EditResult.DeleteFailed
    }

    /**
     * Deletes a track from the shared library and clears every member's selection that points
     * at it. The selection is cleared first so a failed file delete can be rolled back without
     * leaving a role pointing at a half-deleted directory.
     */
    fun deleteLockAudio(context: Context, id: String): EditResult {
        val target = id.trim()
        if (!PolicyAudioLibrary.isAudioId(target)) return EditResult.NotFound
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val selected = mutableMapOf<String, String>()
        val quizAudioEnabled = mutableMapOf<String, Boolean>()
        val editor = prefs.edit()
        prefs.all.forEach { (key, value) ->
            if (isAudioSelectionKey(key) && value == target) {
                selected[key] = target
                editor.remove(key)
                // A quiz intro whose custom audio was deleted must come back with the switch
                // off; the visual then falls back to its own soundtrack or silence.
                if (isQuizAudioKey(key)) {
                    val suffix = key.removePrefix(QUIZ_AUDIO_KEY)
                    val enabledKey = QUIZ_AUDIO_ENABLED_KEY + suffix
                    if (prefs.contains(enabledKey)) {
                        quizAudioEnabled[enabledKey] = prefs.getBoolean(enabledKey, false)
                    }
                    editor.remove(enabledKey)
                }
            }
        }
        val exists = PolicyAudioLibrary.entry(context, target) != null
        if (!exists && selected.isEmpty()) return EditResult.NotFound
        if (!editor.commit()) return EditResult.DeleteFailed
        val deleted = if (!exists) {
            true
        } else if (PolicyAudioLibrary.isOfficialId(target)) {
            // Delete only the downloaded copy; the cloud catalogue is read-only and stays
            // available to another member or a later re-download.
            OfficialAudioDownloader.forget(target)
            OfficialAudioStore.delete(context, target)
        } else {
            PolicyAudioLibrary.delete(context, target)
        }
        if (!deleted) {
            val restore = prefs.edit()
            selected.keys.forEach { restore.putString(it, target) }
            quizAudioEnabled.forEach { (key, value) -> restore.putBoolean(key, value) }
            restore.commit()
            return EditResult.DeleteFailed
        }
        return EditResult.Ok
    }

    /** The picker's cloud rows, with the same download/verification state as a wallpaper. */
    private fun audioRolesJson(
        id: String,
        background: PolicyAudioEntry?,
        quiz: PolicyAudioEntry?,
    ): JSONArray = JSONArray().apply {
        if (id == background?.id) put("lock")
        if (id == quiz?.id) put("quiz")
    }

    private fun audioCloudJson(
        track: OfficialAudioTrack,
        local: OfficialAudioLocal?,
        selected: PolicyAudioEntry?,
        quizSelected: PolicyAudioEntry?,
    ): JSONObject {
        val progress = OfficialAudioDownloader.progress(track.id)
        val state = when {
            progress?.state == OfficialAudioDownloader.STATE_DOWNLOADING -> "DOWNLOADING"
            progress?.state == OfficialAudioDownloader.STATE_VERIFYING -> "VERIFYING"
            progress?.state == OfficialAudioDownloader.STATE_FAILED -> "FAILED"
            local == null -> "NOT_DOWNLOADED"
            local.track.sha256 == track.sha256 -> "READY"
            else -> "UPDATE_AVAILABLE"
        }
        val ready = state == "READY"
        val bytes = local?.track?.bytes ?: track.bytes
        return JSONObject()
            .put("id", track.id)
            .put("source", PolicyAudioEntry.SOURCE_OFFICIAL)
            .put("name", track.name)
            .put("nameEn", track.nameEn)
            .put("artist", track.artist ?: JSONObject.NULL)
            .put("license", track.license)
            .put("sourceUrl", track.sourceUrl ?: JSONObject.NULL)
            .put("sizeBytes", bytes)
            .put("durationMs", local?.track?.durationMs ?: track.durationMs)
            .put("downloaded", ready)
            .put("downloadedAt", (local?.downloadedAtMs ?: 0L) / 1000L)
            .put("state", state)
            .put("selected", track.id == selected?.id)
            .put("quizSelected", track.id == quizSelected?.id)
            .put("roles", audioRolesJson(track.id, selected, quizSelected))
            .put(
                "download",
                progress?.toJson() ?: JSONObject()
                    .put("state", state)
                    .put("bytes", if (ready) bytes else 0L)
                    .put("totalBytes", track.bytes)
                    .put("percent", if (ready) 100 else 0)
                    .put("error", JSONObject.NULL),
            )
    }

    /**
     * The cloud address of an official film, exposed only so the parent page can preview it
     * before downloading. The TV's players never consume this; they resolve [PolicyVideoSource]
     * from the local library instead.
     */
    private fun previewPlayUrl(item: LibraryItem): String? {
        if (item.kind != PolicyMediaKind.VIDEO) return null
        return item.catalog?.url ?: item.local?.item?.url
    }

    /** A complete local file the parent page may stream, with the type its bytes have. */
    data class PlaybackFile(val file: File, val mime: String)

    /**
     * Resolves a parent-page media id to a published file on this TV. Download directories are
     * only handed out by [OfficialMediaStore] once complete, and a local upload goes through the
     * same info.json check, so a half-written file is never served.
     */
    fun playbackFile(context: Context, id: String): PlaybackFile? = when {
        officialIdPattern.matches(id) -> OfficialMediaStore.read(context, id)?.let { local ->
            PlaybackFile(local.file, mimeOf(local.item.kind))
        }
        localIdPattern.matches(id) -> QuizIntroVideo.find(context, id)?.let { entry ->
            PlaybackFile(entry.file, mimeOf(entry.kind))
        }
        else -> null
    }?.takeIf { it.file.isFile && it.file.length() > 0L }

    private fun mimeOf(kind: PolicyMediaKind): String =
        if (kind == PolicyMediaKind.IMAGE) "image/jpeg" else "video/mp4"

    /** The cached cover of a local upload or downloaded official film, for the poster route. */
    fun poster(context: Context, id: String): ByteArray? {
        val file = when {
            officialIdPattern.matches(id) -> OfficialMediaStore.read(context, id)?.poster
            localIdPattern.matches(id) -> QuizIntroVideo.posterOf(context, id)
            else -> null
        } ?: return null
        // A picture is its own cover, so this route serves 1920x1080 JPEGs as well as the much
        // smaller video poster. The page still fetches it with the editor header and blobs it.
        if (file.length() !in 1..PolicyImageValidation.MAX_STORED_BYTES) return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        return bytes.takeIf(PolicyVideoValidation::isJpeg)
    }

    internal fun roleId(context: Context, role: PolicyVideoRole, memberId: String?): String? {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(memberKey(role.prefKey, memberId), null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        return id.takeIf {
            officialIdPattern.matches(it) || localIdPattern.matches(it)
        }
    }

    private fun isPlayable(context: Context, id: String): Boolean = when {
        officialIdPattern.matches(id) -> OfficialMediaStore.read(context, id) != null
        localIdPattern.matches(id) -> QuizIntroVideo.all(context).any { it.id == id }
        else -> false
    }

    /**
     * Local covers are served by the same-origin poster endpoint, not embedded in every
     * snapshot: a 50 KB JPEG becomes ~67 KB of base64, and the page polls during a download.
     * The page fetches the image with the editor header and turns it into a blob URL.
     */
    private fun posterRoute(id: String, file: File?): String? {
        if (file == null || !file.isFile) return null
        if (file.length() !in 1..PolicyImageValidation.MAX_STORED_BYTES) return null
        return "/api/parent/media/poster?id=" + Uri.encode(id)
    }
}
