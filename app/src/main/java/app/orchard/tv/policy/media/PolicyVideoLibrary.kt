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
 */
object PolicyVideoLibrary {
    private const val PREFS = "quiz-intro"
    private const val SCHEMA_KEY = "media_schema"
    private const val SCHEMA = 1
    private val localIdPattern = Regex("[a-f0-9]{32}")
    private val officialIdPattern = Regex("official:[a-z0-9-]{1,48}")

    sealed class EditResult {
        data object Ok : EditResult()
        data object NotFound : EditResult()
        data object NotReady : EditResult()
        data object Busy : EditResult()
        data object RoleInvalid : EditResult()
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

    fun quizSource(context: Context): PolicyVideoSource? = source(context, PolicyVideoRole.QUIZ_INTRO)

    fun lockSource(context: Context): PolicyVideoSource? = source(context, PolicyVideoRole.LOCK_WALLPAPER)

    /**
     * The stored lock role id, even when its file is not currently readable. Child surfaces use
     * this to decide whether they own the wallpaper; the picture then falls back to Aerial's
     * default rather than to the owner's Home wallpaper.
     */
    internal fun lockRoleId(context: Context): String? {
        ensureMigrated(context)
        return roleId(context, PolicyVideoRole.LOCK_WALLPAPER)
    }

    fun source(context: Context, role: PolicyVideoRole): PolicyVideoSource? {
        ensureMigrated(context)
        val id = roleId(context, role) ?: return null
        return when {
            officialIdPattern.matches(id) -> {
                val local = OfficialMediaStore.read(context, id) ?: return null
                PolicyVideoSource(
                    uri = Uri.fromFile(local.file),
                    official = true,
                    poster = local.poster,
                    kind = local.item.kind,
                )
            }
            localIdPattern.matches(id) -> {
                val upload = QuizIntroVideo.all(context).firstOrNull { it.id == id } ?: return null
                PolicyVideoSource(
                    uri = Uri.fromFile(upload.file),
                    official = false,
                    poster = QuizIntroVideo.posterOf(context, id),
                    kind = upload.kind,
                )
            }
            else -> null
        }
    }

    /**
     * Sets a role (or both) to [id]. An empty [id] clears only [roles], so
     * switching quiz back to the wallpaper never disturbs a lock choice.
     */
    fun select(context: Context, roles: Set<PolicyVideoRole>, id: String?): EditResult {
        if (roles.isEmpty()) return EditResult.RoleInvalid
        ensureMigrated(context)
        val target = id?.trim().orEmpty()
        if (target.isEmpty()) {
            val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            roles.forEach { editor.remove(it.prefKey) }
            return if (editor.commit()) EditResult.Ok else EditResult.DeleteFailed
        }
        if (!isPlayable(context, target)) return EditResult.NotFound
        val capability = capabilityRoles(context, target)
        if (!capability.containsAll(roles)) return EditResult.RoleInvalid
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        roles.forEach { editor.putString(it.prefKey, target) }
        return if (editor.commit()) EditResult.Ok else EditResult.DeleteFailed
    }

    /** Deletes a local copy/upload and clears every role that pointed at it. */
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
        val previous = PolicyVideoRole.entries
            .filter { prefs.getString(it.prefKey, null) == id }
            .associateWith { prefs.getString(it.prefKey, null) }
        val playable = isPlayable(context, id)
        if (previous.isEmpty() && !playable) return EditResult.NotFound
        val cleared = prefs.edit().apply {
            previous.keys.forEach { remove(it.prefKey) }
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
            previous.forEach { (role, value) -> if (value != null) restore.putString(role.prefKey, value) }
            restore.commit()
            return EditResult.DeleteFailed
        }
        OfficialMediaDownloader.forget(id)
        return EditResult.Ok
    }

    fun capabilityRoles(context: Context, id: String): Set<PolicyVideoRole> = when {
        localIdPattern.matches(id) -> {
            val upload = QuizIntroVideo.all(context).firstOrNull { it.id == id }
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
    fun snapshot(context: Context, autoRefreshCatalog: Boolean = true): JSONObject {
        ensureMigrated(context)
        val roles = PolicyVideoRole.entries.associateWith { roleId(context, it) }
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
                capabilityRoles = if (upload.kind == PolicyMediaKind.IMAGE) {
                    setOf(PolicyVideoRole.LOCK_WALLPAPER)
                } else {
                    PolicyVideoRole.entries.toSet()
                },
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

        return JSONObject()
            .put("catalog", OfficialMediaCatalog.snapshot(context, autoRefreshCatalog))
            .put(
                "roles",
                JSONObject()
                    .put("quiz", roles[PolicyVideoRole.QUIZ_INTRO] ?: JSONObject.NULL)
                    .put("lock", roles[PolicyVideoRole.LOCK_WALLPAPER] ?: JSONObject.NULL),
            )
            .put("items", itemArray)
            .put("downloads", OfficialMediaDownloader.snapshot())
            .put(
                "limits",
                JSONObject()
                    .put("maxOfficialItems", OfficialMediaDownloader.MAX_OFFICIAL_ITEMS)
                    .put("maxLocalItems", QuizIntroVideo.MAX_VIDEOS)
                    .put("maxLocalBytes", QuizIntroVideo.MAX_TOTAL_BYTES)
                    .put("keepFreeBytes", OfficialMediaDownloader.KEEP_FREE_BYTES)
                    .put("maxFileBytes", PolicyVideoValidation.MAX_FILE_BYTES)
                    .put("maxUploadBytes", QuizIntroVideo.MAX_UPLOAD_BYTES)
                    .put("maxImageBytes", QuizIntroVideo.MAX_IMAGE_BYTES)
                    .put("maxDurationMs", PolicyVideoValidation.MAX_DURATION_MS),
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

    private fun roleId(context: Context, role: PolicyVideoRole): String? {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(role.prefKey, null)
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
