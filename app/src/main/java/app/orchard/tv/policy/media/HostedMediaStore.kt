package app.orchard.tv.policy.media

import android.content.Context
import android.net.Uri
import com.planyourtv.log.TvLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId

/** One immutable file in the hosted cache, stored as a path relative to the member directory. */
data class HostedMediaItem(
    val key: String,
    val file: String,
    val poster: String?,
    val kind: PolicyMediaKind,
    val sizeBytes: Long,
    val durationMs: Long,
    /** Null = unknown (old manifest or unprobed upload); treat as "may have audio". */
    val hasAudio: Boolean?,
)

/**
 * V54: the background track persisted next to the wallpaper items. It has no poster and no
 * rotation role, so it stays outside `wallpaper.items` and its IMAGE/VIDEO kinds.
 */
data class HostedAudio(
    val key: String,
    val id: String,
    val source: String,
    val name: String,
    val nameEn: String?,
    val artist: String?,
    val license: String?,
    val file: String,
    val sha256: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val ext: String,
    val version: Long,
    val createdAtMs: Long,
)

data class HostedWallpaper(
    val mode: HostedWallpaperMode,
    val activeKey: String?,
    val items: List<HostedMediaItem>,
    /** Only meaningful for DAILY; 1 for every other mode (schema 1 manifests carry no value). */
    val rotateDays: Int = 1,
    val source: HostedMediaOrigin = HostedMediaOrigin.CUSTOM,
    /** V53 image hold; schema 1/2 manifests carry no value and keep the V51 constant. */
    val intervalMs: Long = HOSTED_WALLPAPER_INTERVAL_MS,
)

data class HostedMediaSource(
    val learnerId: Long,
    val itemCount: Int,
    val quizIntroItemId: Long?,
)

/**
 * The complete cache manifest of one member. It never contains signed URLs, tokens or account
 * data; those exist only in the in-memory sync plan.
 */
data class HostedManifest(
    val memberId: Long,
    val publishedAtMs: Long,
    val sound: Boolean,
    val wallpaper: HostedWallpaper,
    val quizIntro: HostedMediaItem?,
    val source: HostedMediaSource,
    /** V54 background music; schema 1–3 manifests read as null. */
    val audio: HostedAudio? = null,
    /** V56 quiz-intro audio; schema < 5 manifests read as null/false. */
    val quizAudio: HostedAudio? = null,
    val quizAudioEnabled: Boolean = false,
    val schema: Int = HOSTED_MEDIA_SCHEMA,
)

/** Diagnostics only; execution never reads this file. */
data class HostedMediaStatus(
    val result: String,
    val code: String?,
    val memberId: Long?,
    val reason: String,
    val atMs: Long,
    val items: Int,
    val quizIntroId: Long?,
    val bytes: Long,
)

/**
 * Content-addressed hosted cache. A member directory is the unit of publication: a new manifest
 * is written to a temporary file, fsynced and renamed, so execution sees either the previous
 * complete manifest or the new complete one. A failed sync never publishes partial state.
 *
 * The directory is physically separate from `quiz-intro/`, `policy-videos/` and `policy-audio/`.
 * Nothing here reads or writes a local upload, and no local store reads this directory.
 */
object HostedMediaStore {
    private const val TAG = "CloudMedia"
    private const val ROOT_DIR = "hosted-media"
    private const val MANIFEST = "manifest.json"
    private const val LAST_MEMBER = "last_member"
    private const val LAST_USED = "last_used.json"
    private const val STATUS = "status.json"
    private const val FILES = "files"
    /** V52 assets-first index; a download cache next to the manifest, never read by execution. */
    private const val PRELOAD = "preload.json"
    private const val PRELOAD_TMP = "preload-tmp"
    private const val TEMP_PREFIX = ".part-"
    private const val MAX_DAILY_ROTATE_DAYS = 30
    private val TV_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
    private val SHA256_PATTERN = Regex("[a-f0-9]{64}")
    private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "ogg", "oga")

    /** Total hosted-media budget; the product maximum (9 x 128 MiB + intro) fits with covers. */
    const val MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024
    /** The rest of the filesystem must stay usable for local media and the app itself. */
    const val RESERVED_BYTES = 256L * 1024 * 1024

    fun install(context: Context) {
        val root = root(context)
        root.mkdirs()
        cleanTempFiles(root)
        cleanPreloadTempFiles(root)
        cleanMemberDirectoriesWithoutManifest(root)
        // The active child is still unknown before the first config poll; the last published
        // member is the cache the lock screen is about to show, so it must survive eviction.
        enforceTotalLimit(context, activeMemberId = lastMemberId(context))
    }

    fun root(context: Context): File = File(context.filesDir, ROOT_DIR)

    fun memberDir(context: Context, memberId: Long): File =
        File(root(context), memberId.toString())

    fun filesDir(context: Context, memberId: Long): File =
        File(memberDir(context, memberId), FILES).apply { mkdirs() }

    /** Null when [relative] is not exactly one safe file name below `files/`. */
    fun fileFor(context: Context, memberId: Long, relative: String?): File? {
        val safe = safeRelative(relative) ?: return null
        return File(memberDir(context, memberId), safe)
    }

    fun readManifest(context: Context, memberId: Long): HostedManifest? {
        val file = File(memberDir(context, memberId), MANIFEST)
        if (!file.isFile) return null
        return runCatching {
            val json = JSONObject(file.readText())
            val schema = json.optInt("schema", 0)
            // V51 reads V50 caches as well; a newer schema is never guessed at. A downgraded APK
            // cannot read schema 2, so clearing `hosted-media` is part of an APK rollback.
            if (schema < HOSTED_MEDIA_SCHEMA_MIN || schema > HOSTED_MEDIA_SCHEMA) {
                TvLog.w(TAG, "member=$memberId manifest schema unsupported schema=$schema")
                return null
            }
            if (json.optLong("memberId", 0L) != memberId) {
                TvLog.w(TAG, "member=$memberId manifest belongs to another member")
                return null
            }
            val wallpaper = json.optJSONObject("wallpaper")
                ?: throw IOException("wallpaper missing")
            val mode = HostedWallpaperMode.fromWire(wallpaper.optString("mode", ""))
                ?: throw IOException("wallpaper mode invalid")
            // Schema 3 always writes the playlist hold. A schema 3 manifest without a valid value
            // is corrupt; older schemas had a single constant hold and read it as absent.
            val intervalMs = if (schema >= 3) {
                val value = wallpaper.optLong("intervalMs", 0L)
                if (value !in HOSTED_PLAYLIST_MIN_INTERVAL_MS..HOSTED_PLAYLIST_MAX_INTERVAL_MS) {
                    throw IOException("wallpaper intervalMs invalid")
                }
                value
            } else {
                HOSTED_WALLPAPER_INTERVAL_MS
            }
            val items = parseItems(wallpaper.optJSONArray("items") ?: JSONArray())
            val quizIntro = if (json.isNull("quizIntro")) {
                null
            } else {
                json.optJSONObject("quizIntro")?.let { parseItem(it) }
                    ?: throw IOException("quizIntro invalid")
            }
            // V54 background music. Older schemas never carried it; a schema 4 manifest with a
            // malformed entry is corrupt and is rejected as a whole.
            val audio = if (schema >= 4 && json.has("audio") && !json.isNull("audio")) {
                val entry = json.optJSONObject("audio")
                    ?: throw IOException("audio invalid")
                parseAudio(entry) ?: throw IOException("audio invalid")
            } else {
                null
            }
            // V56 quiz-intro audio. A schema 5 manifest with a malformed entry is corrupt.
            val quizAudio = if (schema >= 5 && json.has("quizAudio") && !json.isNull("quizAudio")) {
                val entry = json.optJSONObject("quizAudio")
                    ?: throw IOException("quizAudio invalid")
                parseAudio(entry) ?: throw IOException("quizAudio invalid")
            } else {
                null
            }
            val quizAudioEnabled = schema >= 5 && json.optBoolean("quizAudioEnabled", false)
            val source = json.optJSONObject("source") ?: JSONObject()
            HostedManifest(
                memberId = memberId,
                publishedAtMs = json.optLong("publishedAtMs", 0L),
                sound = json.optBoolean("sound", false),
                wallpaper = HostedWallpaper(
                    mode = mode,
                    activeKey = wallpaper.optString("activeKey", "").takeIf { it.isNotEmpty() },
                    items = items,
                    // V50 manifests only carry custom videos.
                    rotateDays = wallpaper.optInt("rotateDays", 1).takeIf { it > 0 } ?: 1,
                    source = HostedMediaOrigin.fromWire(wallpaper.optString("source", ""))
                        ?: HostedMediaOrigin.CUSTOM,
                    intervalMs = intervalMs,
                ),
                quizIntro = quizIntro,
                source = HostedMediaSource(
                    learnerId = source.optLong("learnerId", memberId),
                    itemCount = source.optInt("itemCount", items.size),
                    quizIntroItemId = source.optLong("quizIntroItemId", 0L)
                        .takeIf { it > 0L },
                ),
                audio = audio,
                quizAudio = quizAudio,
                quizAudioEnabled = quizAudioEnabled && quizAudio != null,
                schema = schema,
            )
        }.onFailure { error ->
            TvLog.w(TAG, "member=$memberId manifest unreadable", error)
        }.getOrNull()
    }

    /** Atomic replace inside the member directory; returns false and keeps the old manifest. */
    fun writeManifest(context: Context, manifest: HostedManifest): Boolean {
        return runCatching {
            val dir = memberDir(context, manifest.memberId).apply { mkdirs() }
            filesDir(context, manifest.memberId)
            val temp = File(dir, "$MANIFEST.tmp")
            FileOutputStream(temp).use { output ->
                output.write(manifest.toJson().toString().toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            val target = File(dir, MANIFEST)
            if (!temp.renameTo(target)) {
                temp.delete()
                throw IOException("manifest rename failed")
            }
            true
        }.onFailure { error ->
            TvLog.e(TAG, "member=${manifest.memberId} manifest write failed", error)
        }.getOrDefault(false)
    }

    /**
     * V52 preload index of one member. Null when absent or unreadable; an unreadable index is
     * never half-applied, so its files simply become eligible for cleanup.
     */
    fun readPreloadIndex(context: Context, memberId: Long): HostedPreloadIndex? =
        readPreloadIndexFile(memberDir(context, memberId), memberId)

    /** Atomic replace; false keeps the previous index. */
    fun writePreloadIndex(context: Context, index: HostedPreloadIndex): Boolean {
        return runCatching {
            val dir = memberDir(context, index.memberId).apply { mkdirs() }
            filesDir(context, index.memberId)
            val temp = File(dir, "$PRELOAD.tmp")
            FileOutputStream(temp).use { output ->
                output.write(index.toJson().toString().toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            val target = File(dir, PRELOAD)
            if (!temp.renameTo(target)) {
                temp.delete()
                throw IOException("preload index rename failed")
            }
            true
        }.onFailure { error ->
            TvLog.e(TAG, "member=${index.memberId} preload index write failed", error)
        }.getOrDefault(false)
    }

    /** Files protected by the preload index; publishing must not delete them. */
    fun preloadReferenced(context: Context, memberId: Long): Set<String> {
        val index = readPreloadIndex(context, memberId) ?: return emptySet()
        val referenced = linkedSetOf<String>()
        index.items.forEach { entry ->
            safeRelative(entry.file)?.let { referenced += it }
            safeRelative(entry.poster)?.let { referenced += it }
        }
        return referenced
    }

    fun preloadTempDir(context: Context, memberId: Long): File =
        File(memberDir(context, memberId), PRELOAD_TMP).apply { mkdirs() }

    fun newPreloadTempFile(context: Context, memberId: Long): File =
        File.createTempFile(TEMP_PREFIX, ".tmp", preloadTempDir(context, memberId))

    fun readSnapshot(context: Context, memberId: Long): HostedMediaSnapshot? =
        readManifest(context, memberId)?.let { snapshotOf(context, it) }

    /**
     * Builds the execution view. A referenced file that is missing or empty is skipped; the
     * remaining rotation is still complete. It never substitutes a local file.
     *
     * [todayEpochDay] is injectable so the daily-rotation rule can be asserted in unit tests.
     */
    fun snapshotOf(
        context: Context,
        manifest: HostedManifest,
        todayEpochDay: Long = LocalDate.now(TV_ZONE).toEpochDay(),
    ): HostedMediaSnapshot {
        val memberId = manifest.memberId
        val dir = memberDir(context, memberId)
        val resolved = manifest.wallpaper.items.mapNotNull { entry ->
            val file = fileFor(context, memberId, entry.file)?.takeIf { it.isFile && it.length() > 0L }
            if (file == null) {
                TvLog.w(TAG, "member=$memberId hosted file missing key=${entry.key}")
                return@mapNotNull null
            }
            val item = PolicyWallpaperItem(
                id = hostedItemId(memberId, entry.key),
                source = sourceOf(context, memberId, entry, file),
            )
            entry.key to item
        }
        val items = when (manifest.wallpaper.mode) {
            HostedWallpaperMode.FIXED -> {
                val active = resolved.firstOrNull { it.first == manifest.wallpaper.activeKey }
                listOfNotNull(active ?: resolved.firstOrNull()).map { it.second }
            }
            HostedWallpaperMode.CAROUSEL -> resolved.map { it.second }
            HostedWallpaperMode.DAILY -> {
                val rotateDays = dailyRotateDays(memberId, manifest.wallpaper.rotateDays)
                if (resolved.isEmpty()) {
                    emptyList()
                } else {
                    val index = dailyIndex(todayEpochDay, rotateDays, resolved.size)
                    listOf(resolved[index].second)
                }
            }
        }
        val quizSource = manifest.quizIntro?.let { entry ->
            val file = fileFor(context, memberId, entry.file)?.takeIf { it.isFile && it.length() > 0L }
            if (file == null) {
                TvLog.w(TAG, "member=$memberId quiz intro file missing key=${entry.key}")
                null
            } else {
                sourceOf(context, memberId, entry, file)
            }
        }
        val audio = resolveAudio(context, memberId, manifest.audio, "background music")
        val quizAudio = resolveAudio(context, memberId, manifest.quizAudio, "quiz intro audio")
        return HostedMediaSnapshot(
            memberId = memberId,
            sound = manifest.sound,
            selection = PolicyWallpaperSelection(items, manifest.wallpaper.intervalMs),
            lockRoleId = items.firstOrNull()?.id ?: HostedMediaRoute.NONE_ID,
            quizSource = quizSource,
            audio = audio,
            quizAudio = quizAudio,
            quizAudioEnabled = manifest.quizAudioEnabled && quizAudio != null,
        )
    }

    private fun resolveAudio(
        context: Context,
        memberId: Long,
        entry: HostedAudio?,
        label: String,
    ): PolicyAudioEntry? {
        if (entry == null) return null
        val file = fileFor(context, memberId, entry.file)?.takeIf {
            it.isFile && it.length() > 0L &&
                (entry.sizeBytes <= 0L || it.length() == entry.sizeBytes)
        }
        if (file == null) {
            TvLog.w(TAG, "member=$memberId $label file missing key=${entry.key}")
            return null
        }
        return PolicyAudioEntry(
            id = entry.id,
            name = entry.name,
            file = file,
            sizeBytes = entry.sizeBytes,
            durationMs = entry.durationMs,
            createdAt = entry.createdAtMs,
            source = if (entry.source == "official") {
                PolicyAudioEntry.SOURCE_OFFICIAL
            } else {
                PolicyAudioEntry.SOURCE_LOCAL
            },
            nameEn = entry.nameEn,
            artist = entry.artist,
            license = entry.license,
        )
    }

    fun hostedItemId(memberId: Long, key: String): String = "hosted:$memberId:$key"

    /**
     * Same epoch-day rule as the parent pages: a positive integer division groups days, and the
     * item index is that group modulo the rotation size.
     */
    internal fun dailyIndex(epochDay: Long, rotateDays: Int, size: Int): Int {
        if (size <= 0) return -1
        val days = rotateDays.coerceAtLeast(1).toLong()
        val block = Math.floorDiv(epochDay, days)
        return Math.floorMod(block, size.toLong()).toInt()
    }

    /** Dirty cached data only: the planner never writes an out-of-range value. */
    private fun dailyRotateDays(memberId: Long, rotateDays: Int): Int {
        if (rotateDays in 1..MAX_DAILY_ROTATE_DAYS) return rotateDays
        TvLog.w(TAG, "member=$memberId manifest rotateDays=$rotateDays invalid; use 1")
        return 1
    }

    /** Records the last published member and the usage time used by eviction. */
    fun touch(context: Context, memberId: Long) {
        runCatching {
            val root = root(context).apply { mkdirs() }
            File(root, LAST_MEMBER).writeText(memberId.toString())
            File(memberDir(context, memberId), LAST_USED).writeText(
                JSONObject()
                    .put("atMs", System.currentTimeMillis())
                    .toString(),
            )
        }.onFailure { error ->
            TvLog.w(TAG, "member=$memberId touch failed", error)
        }
    }

    fun lastMemberId(context: Context): Long? =
        File(root(context), LAST_MEMBER)
            .takeIf { it.isFile }
            ?.readText()
            ?.trim()
            ?.toLongOrNull()
            ?.takeIf { it > 0L }

    fun newTempFile(context: Context, memberId: Long): File {
        val dir = filesDir(context, memberId)
        return File.createTempFile(TEMP_PREFIX, ".tmp", dir)
    }

    fun relativeFileName(file: File): String = "$FILES/${file.name}"

    /**
     * Deletes files that neither the manifest nor the preload index references. The preload
     * index is part of the union so publishing after an assets-first save reuses its files.
     */
    fun cleanupUnreferenced(context: Context, manifest: HostedManifest) {
        cleanupFiles(
            context,
            manifest.memberId,
            referencedOf(manifest) + preloadReferenced(context, manifest.memberId),
        )
    }

    /** Same union for a member that may not have a manifest yet (assets-first only). */
    fun cleanupPreloadUnreferenced(context: Context, memberId: Long) {
        val manifest = readManifest(context, memberId)
        cleanupFiles(context, memberId, referencedOf(manifest) + preloadReferenced(context, memberId))
    }

    private fun referencedOf(manifest: HostedManifest?): Set<String> {
        if (manifest == null) return emptySet()
        val referenced = linkedSetOf<String>()
        manifest.wallpaper.items.forEach { entry ->
            safeRelative(entry.file)?.let { referenced += it }
            safeRelative(entry.poster)?.let { referenced += it }
        }
        manifest.quizIntro?.let { entry ->
            safeRelative(entry.file)?.let { referenced += it }
            safeRelative(entry.poster)?.let { referenced += it }
        }
        manifest.audio?.let { entry ->
            safeRelative(entry.file)?.let { referenced += it }
        }
        manifest.quizAudio?.let { entry ->
            safeRelative(entry.file)?.let { referenced += it }
        }
        return referenced
    }

    private fun cleanupFiles(context: Context, memberId: Long, referenced: Set<String>) {
        val dir = filesDir(context, memberId)
        dir.listFiles()?.forEach { file ->
            val rel = relativeFileName(file)
            if (file.isFile && (rel !in referenced || file.name.startsWith(TEMP_PREFIX))) {
                if (!file.delete()) TvLog.w(TAG, "unable to delete stale hosted file name=${file.name}")
            }
        }
    }

    /** Published bytes of one member, for `status.json` and diagnostics. */
    fun memberBytes(context: Context, memberId: Long): Long =
        memberDir(context, memberId).walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }

    /**
     * Keeps the whole cache under [maxBytes]. Only non-active member directories are evicted,
     * oldest use first. The active member's files are never removed.
     */
    internal fun enforceTotalLimit(
        context: Context,
        activeMemberId: Long?,
        maxBytes: Long = MAX_TOTAL_BYTES,
        /** V52: the preload target may differ from the active child; never evict it. */
        protectedMemberIds: Set<Long> = emptySet(),
    ) {
        val members = root(context).listFiles()
            ?.filter { it.isDirectory }
            ?.map { it to directoryBytes(it) }
            ?: return
        var total = members.sumOf { it.second }
        if (total <= maxBytes) return
        val candidates = members
            .filter { (dir, _) ->
                val memberId = dir.name.toLongOrNull()
                memberId != activeMemberId && (memberId == null || memberId !in protectedMemberIds)
            }
            .sortedBy { (dir, _) -> lastUsedAt(dir) }
        for ((dir, bytes) in candidates) {
            if (total <= maxBytes) break
            if (dir.deleteRecursively()) {
                total -= bytes
                TvLog.i(TAG, "evicted hosted member dir name=${dir.name} bytes=$bytes")
            } else {
                TvLog.w(TAG, "unable to evict hosted member dir name=${dir.name}")
            }
        }
        if (total > maxBytes) {
            TvLog.w(TAG, "hosted cache still above limit bytes=$total max=$maxBytes")
        }
    }

    fun clearAll(context: Context) {
        val root = root(context)
        if (!root.exists()) return
        if (!root.deleteRecursively()) TvLog.w(TAG, "unable to clear hosted-media")
    }

    fun writeStatus(context: Context, status: HostedMediaStatus) {
        runCatching {
            val root = root(context).apply { mkdirs() }
            val temp = File(root, "$STATUS.tmp")
            FileOutputStream(temp).use { output ->
                output.write(status.toJson().toString().toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            val target = File(root, STATUS)
            if (!temp.renameTo(target)) {
                temp.delete()
                throw IOException("status rename failed")
            }
        }.onFailure { error ->
            TvLog.w(TAG, "status write failed", error)
        }
    }

    private fun sourceOf(
        context: Context,
        memberId: Long,
        entry: HostedMediaItem,
        file: File,
    ): PolicyVideoSource {
        // A still picture is decoded by `PolicyChildWallpaper` through `source.poster` alone, so
        // the image file is both the playable URI and its own poster. A video keeps its cover.
        val poster = when (entry.kind) {
            PolicyMediaKind.IMAGE -> file
            PolicyMediaKind.VIDEO -> entry.poster?.let { fileFor(context, memberId, it) }
        }
        return PolicyVideoSource(
            uri = Uri.fromFile(file),
            official = false,
            poster = poster,
            kind = entry.kind,
            // Unknown (old manifest / unprobed upload) is treated as "may have audio"; only an
            // explicit false proves the film is silent.
            hasAudio = entry.hasAudio != false,
            durationMs = entry.durationMs,
        )
    }

    private fun parseItems(array: JSONArray): List<HostedMediaItem> {
        val items = ArrayList<HostedMediaItem>()
        for (index in 0 until array.length()) {
            val entry = array.optJSONObject(index) ?: continue
            val item = parseItem(entry) ?: continue
            items += item
        }
        return items
    }

    private fun parseItem(json: JSONObject): HostedMediaItem? {
        val key = json.optString("key", "").trim()
        val file = safeRelative(json.optString("file", "")) ?: return null
        val poster = json.optString("poster", "")
            .takeIf { it.isNotBlank() }
            ?.let { safeRelative(it) }
        val kind = PolicyMediaKind.fromWire(json.optString("kind", "").lowercase())
        if (kind == null) {
            // Every V50 manifest already wrote a kind. An unknown value is a corrupt cache entry,
            // so drop it instead of guessing VIDEO.
            TvLog.w(TAG, "manifest item kind unknown key=$key")
            return null
        }
        if (key.isEmpty()) return null
        return HostedMediaItem(
            key = key,
            file = file,
            poster = poster,
            kind = kind,
            sizeBytes = json.optLong("sizeBytes", 0L),
            durationMs = json.optLong("durationMs", 0L),
            // Unknown is preserved as null; old manifests wrote nothing or false only when the
            // audio track had actually been probed. A schema < 5 manifest never carried it.
            hasAudio = if (json.has("hasAudio") && !json.isNull("hasAudio")) {
                json.optBoolean("hasAudio")
            } else {
                null
            },
        )
    }

    /**
     * A schema 4 background-music entry. Any field that cannot be used to find and play the
     * cached file invalidates the entry, and with it the whole manifest: execution must never
     * show a half-configured track.
     */
    private fun parseAudio(json: JSONObject): HostedAudio? {
        val key = json.optString("key", "").trim()
        val id = json.optString("id", "").trim()
        val source = json.optString("source", "").trim()
        val name = json.optString("name", "").trim()
        val file = safeRelative(json.optString("file", "")) ?: return null
        val sha = json.optString("sha256", "").trim().lowercase()
        val ext = json.optString("ext", "").trim().lowercase().removePrefix(".")
        val size = json.optLong("sizeBytes", 0L)
        val duration = json.optLong("durationMs", 0L)
        val version = json.optLong("version", 0L)
        if (key.isEmpty() || id.isEmpty() || name.isEmpty()) return null
        if (source != "official" && source != "custom") return null
        if (!SHA256_PATTERN.matches(sha)) return null
        if (ext !in AUDIO_EXTENSIONS) return null
        if (size <= 0L || size > PolicyAudioLibrary.MAX_AUDIO_BYTES) return null
        if (duration <= 0L || duration > PolicyAudioLibrary.MAX_AUDIO_DURATION_MS) return null
        if (version <= 0L) return null
        return HostedAudio(
            key = key,
            id = id,
            source = source,
            name = name,
            nameEn = optionalText(json, "nameEn"),
            artist = optionalText(json, "artist"),
            license = optionalText(json, "license"),
            file = file,
            sha256 = sha,
            sizeBytes = size,
            durationMs = duration,
            ext = ext,
            version = version,
            createdAtMs = json.optLong("createdAtMs", 0L),
        )
    }

    private fun optionalText(json: JSONObject, name: String): String? {
        if (!json.has(name) || json.isNull(name)) return null
        return json.optString(name, "").trim().takeIf { it.isNotEmpty() }
    }

    private fun HostedManifest.toJson(): JSONObject {
        val wallpaperJson = JSONObject()
            .put("mode", wallpaper.mode.wire)
            .put("activeKey", wallpaper.activeKey ?: JSONObject.NULL)
            .put("rotateDays", wallpaper.rotateDays)
            .put("source", wallpaper.source.wire)
            .put("intervalMs", wallpaper.intervalMs)
            .put("items", JSONArray(wallpaper.items.map { it.toJson() }))
        return JSONObject()
            .put("schema", schema)
            .put("memberId", memberId)
            .put("publishedAtMs", publishedAtMs)
            .put("sound", sound)
            .put("wallpaper", wallpaperJson)
            .put("quizIntro", quizIntro?.toJson() ?: JSONObject.NULL)
            .put("audio", audio?.toJson() ?: JSONObject.NULL)
            .apply {
                if (schema >= 5) {
                    put("quizAudio", quizAudio?.toJson() ?: JSONObject.NULL)
                    put("quizAudioEnabled", quizAudioEnabled)
                }
            }
            .put(
                "source",
                JSONObject()
                    .put("learnerId", source.learnerId)
                    .put("itemCount", source.itemCount)
                    .put("quizIntroItemId", source.quizIntroItemId ?: JSONObject.NULL),
            )
    }

    private fun HostedMediaItem.toJson(): JSONObject = JSONObject()
        .put("key", key)
        .put("file", file)
        .put("poster", poster ?: JSONObject.NULL)
        .put("kind", kind.wire.uppercase())
        .put("sizeBytes", sizeBytes)
        .put("durationMs", durationMs)
        // Unknown audio is written conservatively: a schema 4 downgrade must not treat a film
        // with sound as silent. An IMAGE is always silent.
        .put("hasAudio", hasAudio ?: (kind == PolicyMediaKind.VIDEO))

    private fun HostedAudio.toJson(): JSONObject = JSONObject()
        .put("key", key)
        .put("id", id)
        .put("source", source)
        .put("name", name)
        .put("nameEn", nameEn ?: JSONObject.NULL)
        .put("artist", artist ?: JSONObject.NULL)
        .put("license", license ?: JSONObject.NULL)
        .put("file", file)
        .put("sha256", sha256)
        .put("sizeBytes", sizeBytes)
        .put("durationMs", durationMs)
        .put("ext", ext)
        .put("version", version)
        .put("createdAtMs", createdAtMs)

    private fun HostedPreloadIndex.toJson(): JSONObject = JSONObject()
        .put("schema", schema)
        .put("memberId", memberId)
        .put("requestId", requestId)
        .put("libraryVersion", libraryVersion)
        .put("updatedAtMs", updatedAtMs)
        .put("items", JSONArray(items.map { it.toJson() }))

    private fun HostedMediaStatus.toJson(): JSONObject = JSONObject()
        .put("result", result)
        .put("code", code ?: JSONObject.NULL)
        .put("memberId", memberId ?: JSONObject.NULL)
        .put("reason", reason)
        .put("atMs", atMs)
        .put("items", items)
        .put("quizIntroId", quizIntroId ?: JSONObject.NULL)
        .put("bytes", bytes)

    private fun safeRelative(relative: String?): String? {
        val value = relative?.trim().orEmpty()
        if (!value.startsWith("$FILES/")) return null
        val name = value.removePrefix("$FILES/")
        if (name.isEmpty() || name.contains('/') || name.contains('\\')) return null
        if (name == "." || name == ".." || name.startsWith(TEMP_PREFIX)) return null
        return "$FILES/$name"
    }

    private fun cleanTempFiles(root: File) {
        root.listFiles()?.forEach { member ->
            if (!member.isDirectory) return@forEach
            File(member, FILES).listFiles()?.forEach { file ->
                if (file.name.startsWith(TEMP_PREFIX) && file.isFile && !file.delete()) {
                    TvLog.w(TAG, "unable to delete stale temp name=${file.name}")
                }
            }
        }
    }

    /** V52: a preload temp file from a killed process is never resumed. */
    private fun cleanPreloadTempFiles(root: File) {
        root.listFiles()?.forEach { member ->
            if (!member.isDirectory) return@forEach
            val dir = File(member, PRELOAD_TMP)
            dir.listFiles()?.forEach { file ->
                if (file.isFile && !file.delete()) {
                    TvLog.w(TAG, "unable to delete stale preload temp name=${file.name}")
                }
            }
            if (dir.isDirectory && dir.listFiles().isNullOrEmpty() && !dir.delete()) {
                TvLog.w(TAG, "unable to remove empty preload temp dir name=${member.name}")
            }
        }
    }

    /**
     * A member directory without a manifest is debris from an interrupted first sync, unless it
     * carries a readable assets-first index (V52): that directory is a complete download cache
     * that a later save will publish.
     */
    private fun cleanMemberDirectoriesWithoutManifest(root: File) {
        root.listFiles()?.forEach { member ->
            if (!member.isDirectory) return@forEach
            val memberId = member.name.toLongOrNull() ?: return@forEach
            if (File(member, MANIFEST).isFile) return@forEach
            if (readPreloadIndexFile(member, memberId) != null) return@forEach
            if (!member.deleteRecursively()) {
                TvLog.w(TAG, "unable to remove incomplete hosted dir name=${member.name}")
            }
        }
    }

    private fun readPreloadIndexFile(dir: File, memberId: Long): HostedPreloadIndex? {
        val file = File(dir, PRELOAD)
        if (!file.isFile) return null
        return runCatching { parsePreloadIndex(file.readText(), memberId) }
            .onFailure { error ->
                TvLog.w(TAG, "member=$memberId preload index unreadable", error)
            }
            .getOrNull()
    }

    private fun parsePreloadIndex(text: String, memberId: Long): HostedPreloadIndex {
        val json = JSONObject(text)
        if (json.optInt("schema", 0) != PRELOAD_SCHEMA) {
            throw IOException("preload schema unsupported")
        }
        if (json.optLong("memberId", 0L) != memberId) {
            throw IOException("preload index belongs to another member")
        }
        val requestId = json.optString("requestId", "").trim()
        val libraryVersion = json.optString("libraryVersion", "").trim()
        if (requestId.isEmpty() || libraryVersion.isEmpty()) {
            throw IOException("preload ids missing")
        }
        val array = json.optJSONArray("items") ?: JSONArray()
        val items = ArrayList<HostedMediaItem>()
        for (index in 0 until array.length()) {
            val entry = array.optJSONObject(index) ?: continue
            val item = parseItem(entry) ?: throw IOException("preload item invalid")
            items += item
        }
        return HostedPreloadIndex(
            memberId = memberId,
            requestId = requestId,
            libraryVersion = libraryVersion,
            updatedAtMs = json.optLong("updatedAtMs", 0L),
            items = items,
        )
    }

    private fun directoryBytes(dir: File): Long =
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun lastUsedAt(dir: File): Long {
        val json = File(dir, LAST_USED).takeIf { it.isFile }?.let { file ->
            runCatching { JSONObject(file.readText()).optLong("atMs", 0L) }.getOrDefault(0L)
        }
        return json?.takeIf { it > 0L } ?: dir.lastModified()
    }
}