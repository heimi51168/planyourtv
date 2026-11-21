package app.orchard.tv.policy.media

import android.content.Context
import android.net.Uri
import app.orchard.tv.quiz.QuizIntroVideo
import app.orchard.tv.quiz.QuizIntroVideoEntry
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

    fun quizSource(context: Context): PolicyVideoSource? =
        sourceFor(context, PolicyVideoRole.QUIZ_INTRO, memberScopeId(context))

    /** The first item is the film/shape shown until the rotation advances. */
    fun lockSource(context: Context): PolicyVideoSource? =
        lockSelection(context).items.firstOrNull()?.source

    /**
     * The active child's wallpaper rotation. Missing files are skipped here; an empty list means
     * the call site draws the bundled default. [intervalMs] is only meaningful with two or more
     * items.
     */
    fun lockSelection(context: Context): PolicyWallpaperSelection =
        lockSelectionFor(context, memberScopeId(context))

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
        lockPlaylistIdsFor(context, memberScopeId(context)).firstOrNull()

    fun source(context: Context, role: PolicyVideoRole): PolicyVideoSource? =
        sourceFor(context, role, memberScopeId(context))

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

    fun capabilityRoles(context: Context, id: String): Set<PolicyVideoRole> = when {
        localIdPattern.matches(id) -> {
            val upload = QuizIntroVideo.all(context).firstOrNull { it.id == id }
            // Every locally stored video can fill either role: the upload path already checked
            // it against this TV's decoders, and the quiz player streams from disk, so a long
            // film is no different from a short one. Images stay wallpaper-only because the
            // intro is played by ExoPlayer.
            when (upload?.kind) {
                PolicyMediaKind.IMAGE -> setOf(PolicyVideoRole.LOCK_WALLPAPER)
                PolicyMediaKind.VIDEO -> PolicyVideoRole.entries.toSet()
                null -> emptySet()
            }
        }
        officialIdPattern.matches(id) -> {
            val catalog = OfficialMediaCatalog.find(context, id)
            val installed = OfficialMediaStore.read(context, id)
            when {
                catalog != null -> catalog.roles
                installed != null && installed.item.roles.isNotEmpty() -> installed.item.roles
                installed != null -> PolicyVideoRole.entries.toSet()
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
                capabilityRoles = item.roles,
                hasAudio = item.hasAudio,
            )
        }

        // A film removed from the manifest stays playable if it is already on disk.
        downloaded.values.filter { local -> catalog?.items?.none { it.id == local.id } == true }
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
                    capabilityRoles = local.item.roles.ifEmpty { PolicyVideoRole.entries.toSet() },
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
                    .put("selected", entry.id == selectedAudio?.id),
            )
        }

        val cloudAudioArray = JSONArray()
        val audioCatalog = OfficialAudioCatalog.cached(context)
        val cloudSeen = HashSet<String>()
        audioCatalog?.tracks?.forEach { track ->
            cloudSeen += track.id
            cloudAudioArray.put(
                audioCloudJson(track, downloadedAudio[track.id], selectedAudio),
            )
        }
        // A downloaded track removed from the catalogue stays visible and playable offline.
        audioDownloads.filter { it.id !in cloudSeen }.forEach { local ->
            cloudAudioArray.put(audioCloudJson(local.track, local, selectedAudio))
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
        val memberId = memberScopeId(context)
        return lockSoundPreference(context, memberId) &&
            lockSelectionFor(context, memberId).items.any { it.source.hasAudio }
    }

    /** The member's stored switch, independent of whether the current item has audio. */
    fun lockSoundPreference(context: Context): Boolean =
        lockSoundPreference(context, memberScopeId(context))

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
    fun lockAudio(context: Context): PolicyAudioEntry? =
        lockAudioFor(context, memberScopeId(context))

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
        val editor = prefs.edit()
        prefs.all.forEach { (key, value) ->
            if (isLockAudioKey(key) && value == target) {
                selected[key] = target
                editor.remove(key)
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
            restore.commit()
            return EditResult.DeleteFailed
        }
        return EditResult.Ok
    }

    /** The picker's cloud rows, with the same download/verification state as a wallpaper. */
    private fun audioCloudJson(
        track: OfficialAudioTrack,
        local: OfficialAudioLocal?,
        selected: PolicyAudioEntry?,
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
