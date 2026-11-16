package app.orchard.tv.policy.media

import android.content.Context
import android.util.Log
import app.orchard.tv.BuildConfig
import app.orchard.tv.update.LegacyUpdateTls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.time.OffsetDateTime

data class PolicyVideoPoster(
    val url: String,
    val sha256: String,
    val bytes: Long,
    val atMs: Long,
)

data class OfficialMediaItem(
    val id: String,
    val name: String,
    val nameEn: String,
    val roles: Set<PolicyVideoRole>,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fps: Int,
    val bytes: Long,
    val sha256: String,
    val hasAudio: Boolean,
    val url: String,
    val poster: PolicyVideoPoster,
    val kind: PolicyMediaKind = PolicyMediaKind.VIDEO,
)

data class OfficialMediaCatalogData(
    val manifestUrl: String,
    val pack: String,
    val updatedAtMs: Long,
    val items: List<OfficialMediaItem>,
    val fetchedAtMs: Long,
    val checkedAtMs: Long,
    val etag: String?,
)

/**
 * The tiny, read-only catalogue of official policy films.
 *
 * The manifest is fetched only when the parent center's media page asks for it.
 * There is no startup fetch, no background poll and no video traffic here: the
 * catalogue carries covers and metadata, and the downloader fetches a film only
 * after the parent taps it.
 *
 * Everything in the manifest is treated as untrusted input. The manifest must
 * come from the compile-time HTTPS host, every object URL must be HTTPS on that
 * same host under `/media/policy-videos/`, and every byte count and SHA-256 is
 * re-checked by the downloader before a file becomes visible.
 */
object OfficialMediaCatalog {
    private const val TAG = "PolicyMediaCatalog"
    private const val MAX_MANIFEST_BYTES = 64 * 1024
    private const val MAX_ITEMS = 12
    private const val MAX_COMBINED_ITEMS = 24
    private const val MAX_NAME_LENGTH = 80
    private const val REFRESH_MS = 10 * 60 * 1000L
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 25_000
    private const val STALL_MS = 30_000L
    private const val STALL_BYTES = 64 * 1024L
    private const val PREFIX = "/media/policy-videos/"
    private val idPattern = Regex("official:[a-z0-9-]{1,48}")
    private val packPattern = Regex("[A-Za-z0-9._-]{1,64}")
    private val shaPattern = Regex("[a-f0-9]{64}")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    @Volatile private var cache: OfficialMediaCatalogData? = null
    @Volatile private var loaded = false
    @Volatile private var refreshing = false
    @Volatile private var lastError: String? = null

    data class ParseResult(
        val catalog: OfficialMediaCatalogData? = null,
        val error: String? = null,
    )

    private fun manifestUrl(): String = BuildConfig.TV_MEDIA_CATALOG_URL.trim()

    fun isConfigured(): Boolean = manifestUrl().startsWith("https://")

    /** Current catalogue, or null when nothing valid has been loaded yet. */
    fun cached(context: Context): OfficialMediaCatalogData? {
        ensureLoaded(context)
        return cache
    }

    fun find(context: Context, id: String): OfficialMediaItem? =
        cached(context)?.items?.firstOrNull { it.id == id }

    /**
     * The parent page calls this on every media snapshot. A fresh cache is used
     * as-is; an old one starts one background refresh and is returned at once.
     */
    fun maybeRefresh(context: Context) {
        if (!isConfigured()) return
        ensureLoaded(context)
        val now = System.currentTimeMillis()
        val current = cache
        if (!refreshing && current != null && now - current.checkedAtMs < REFRESH_MS) return
        requestRefresh(context, force = false)
    }

    fun requestRefresh(context: Context, force: Boolean): Boolean {
        if (!isConfigured()) return false
        val app = context.applicationContext
        ensureLoaded(app)
        if (!force) {
            val current = cache
            if (current != null && System.currentTimeMillis() - current.checkedAtMs < REFRESH_MS) {
                return false
            }
        }
        synchronized(lock) {
            if (refreshing) return false
            refreshing = true
        }
        scope.launch {
            try {
                refreshNow(app)
            } finally {
                synchronized(lock) { refreshing = false }
            }
        }
        return true
    }

    /** Snapshot for the parent page; never contains a URL or a filesystem path. */
    fun snapshot(context: Context, autoRefresh: Boolean = true): JSONObject {
        if (!isConfigured()) {
            return JSONObject()
                .put("state", "EMPTY")
                .put("configured", false)
                .put("refreshing", false)
                .put("pack", "")
                .put("updatedAt", 0L)
                .put("checkedAt", 0L)
                .put("error", JSONObject.NULL)
        }
        if (autoRefresh) maybeRefresh(context)
        val current = cached(context)
        val state = when {
            current != null -> "READY"
            lastError != null -> "ERROR"
            else -> "EMPTY"
        }
        return JSONObject()
            .put("state", state)
            .put("configured", true)
            .put("refreshing", refreshing)
            .put("pack", current?.pack.orEmpty())
            .put("updatedAt", (current?.updatedAtMs ?: 0L) / 1000L)
            .put("checkedAt", (current?.checkedAtMs ?: 0L) / 1000L)
            .put("error", lastError ?: JSONObject.NULL)
    }

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            loaded = true
            cache = runCatching { readCache(context.applicationContext) }
                .onFailure { Log.w(TAG, "could not read the media catalogue cache", it) }
                .getOrNull()
        }
    }

    private suspend fun refreshNow(context: Context) {
        val url = manifestUrl()
        if (!isConfigured()) return
        val previous = cache
        val connection = try {
            open(context, url)
        } catch (error: Exception) {
            lastError = "NETWORK"
            Log.w(TAG, "catalogue connection could not be opened", error)
            return
        }
        try {
            previous?.etag?.takeIf { it.isNotBlank() }?.let {
                connection.setRequestProperty("If-None-Match", it)
            }
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED && previous != null) {
                val checked = previous.copy(checkedAtMs = System.currentTimeMillis())
                cache = checked
                writeCache(context, checked)
                lastError = null
                return
            }
            if (code != HttpURLConnection.HTTP_OK) {
                lastError = "NETWORK"
                return
            }
            val raw = readLimited(connection, MAX_MANIFEST_BYTES) ?: run {
                lastError = "CATALOG_INVALID"
                return
            }
            val parsed = parse(raw, url, previous)
            if (parsed.catalog == null) {
                // Keep the last known-good cache; a bad publish must not empty the library.
                lastError = parsed.error ?: "CATALOG_INVALID"
                return
            }
            val responseEtag = connection.getHeaderField("ETag")
                ?.takeIf { it.isNotBlank() }
            val catalog = parsed.catalog.copy(
                etag = responseEtag ?: previous?.etag,
            )
            cache = catalog
            lastError = null
            runCatching { writeCache(context, catalog) }
                .onFailure { Log.w(TAG, "could not keep the media catalogue", it) }
        } catch (error: Exception) {
            lastError = "NETWORK"
            Log.w(TAG, "media catalogue refresh failed", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(context: Context, url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        LegacyUpdateTls.configure(context, connection)
        connection.instanceFollowRedirects = false
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Accept-Encoding", "identity")
        return connection
    }

    private fun readLimited(connection: HttpURLConnection, limit: Int): String? {
        val declared = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
        if (declared > limit) return null
        val bytes = ByteArray(limit + 1)
        var size = 0
        var windowAt = System.currentTimeMillis()
        var windowFrom = 0
        connection.inputStream.use { input ->
            while (true) {
                if (size == bytes.size) return null
                val read = input.read(bytes, size, bytes.size - size)
                if (read < 0) break
                if (read == 0) continue
                size += read
                val now = System.currentTimeMillis()
                if (now - windowAt >= STALL_MS) {
                    if (size - windowFrom < STALL_BYTES) return null
                    windowAt = now
                    windowFrom = size
                }
            }
        }
        if (size > limit) return null
        return String(bytes, 0, size, Charsets.UTF_8)
    }

    /** Strict manifest parser. Exposed for tests; never trusts a type coercion. */
    fun parse(
        raw: String,
        manifestUrl: String,
        previous: OfficialMediaCatalogData? = null,
        now: Long = System.currentTimeMillis(),
    ): ParseResult {
        if (raw.toByteArray(Charsets.UTF_8).size > MAX_MANIFEST_BYTES) {
            return ParseResult(error = "CATALOG_INVALID")
        }
        val base = runCatching { URI(manifestUrl) }.getOrNull()
            ?: return ParseResult(error = "CATALOG_INVALID")
        if (base.scheme != "https" || base.host.isNullOrBlank() ||
            (base.port != -1 && base.port != 443) || base.rawUserInfo != null ||
            base.rawQuery != null || base.rawFragment != null ||
            base.rawPath?.startsWith(PREFIX) != true ||
            !base.rawPath.endsWith("/manifest.json")
        ) {
            return ParseResult(error = "CATALOG_INVALID")
        }
        val root = runCatching { JSONObject(raw) }.getOrNull()
            ?: return ParseResult(error = "CATALOG_INVALID")
        if (root.requiredLong("schema") != 1L) return ParseResult(error = "CATALOG_INVALID")
        val pack = root.requiredString("pack")?.takeIf { packPattern.matches(it) }
            ?: return ParseResult(error = "CATALOG_INVALID")
        val updatedAt = root.requiredString("updatedAt")?.let(::parseIso) ?: 0L
        val videos = root.optJSONArray("videos") ?: return ParseResult(error = "CATALOG_INVALID")
        val images = root.optJSONArray("images")
        if (videos.length() > MAX_ITEMS || (images?.length() ?: 0) > MAX_ITEMS) {
            return ParseResult(error = "CATALOG_INVALID")
        }
        if (videos.length() + (images?.length() ?: 0) > MAX_COMBINED_ITEMS) {
            return ParseResult(error = "CATALOG_INVALID")
        }

        val seen = HashSet<String>()
        val items = ArrayList<OfficialMediaItem>(videos.length() + (images?.length() ?: 0))
        for (index in 0 until videos.length()) {
            val item = parseItem(videos.optJSONObject(index), base, PolicyMediaKind.VIDEO)
                ?: return ParseResult(error = "CATALOG_INVALID")
            if (!seen.add(item.id)) return ParseResult(error = "CATALOG_INVALID")
            items += item
        }
        if (images != null) {
            for (index in 0 until images.length()) {
                val item = parseItem(images.optJSONObject(index), base, PolicyMediaKind.IMAGE)
                    ?: return ParseResult(error = "CATALOG_INVALID")
                if (!seen.add(item.id)) return ParseResult(error = "CATALOG_INVALID")
                items += item
            }
        }
        val etag = previous?.etag
        return ParseResult(
            catalog = OfficialMediaCatalogData(
                manifestUrl = manifestUrl,
                pack = pack,
                updatedAtMs = updatedAt,
                items = items,
                fetchedAtMs = now,
                checkedAtMs = now,
                etag = etag,
            ),
        )
    }

    private fun parseItem(
        root: JSONObject?,
        base: URI,
        kind: PolicyMediaKind,
    ): OfficialMediaItem? {
        if (root == null) return null
        val id = root.requiredString("id")?.takeIf { idPattern.matches(it) } ?: return null
        val name = root.requiredString("name")?.trim()?.takeIf { validName(it) } ?: return null
        val nameEn = root.requiredString("nameEn")?.trim()?.takeIf { validName(it) } ?: name
        val roleArray = root.optJSONArray("roles") ?: return null
        if (roleArray.length() !in 1..2) return null
        val roles = LinkedHashSet<PolicyVideoRole>()
        for (index in 0 until roleArray.length()) {
            val value = roleArray.opt(index) as? String ?: return null
            val role = PolicyVideoRole.fromWire(value) ?: return null
            if (!roles.add(role)) return null
        }
        val url = root.requiredString("url")?.let { officialUrl(it, base) } ?: return null
        val sha = root.requiredString("sha256")?.takeIf { shaPattern.matches(it) } ?: return null
        if (kind == PolicyMediaKind.IMAGE) {
            // A still picture is only ever the child wallpaper; it has no playback semantics.
            if (roles.size != 1 || roles.first() != PolicyVideoRole.LOCK_WALLPAPER) return null
            val width = root.requiredInt("width")
                ?.takeIf { it in 1..PolicyImageValidation.WIDTH } ?: return null
            val height = root.requiredInt("height")
                ?.takeIf { it in 1..PolicyImageValidation.HEIGHT } ?: return null
            if (kotlin.math.abs(width.toDouble() / height - 16.0 / 9.0) > 0.03) return null
            val bytes = root.requiredLong("bytes")
                ?.takeIf { it in 1..PolicyImageValidation.MAX_STORED_BYTES } ?: return null
            return OfficialMediaItem(
                id = id,
                name = name,
                nameEn = nameEn,
                roles = roles,
                durationMs = 0L,
                width = width,
                height = height,
                fps = 0,
                bytes = bytes,
                sha256 = sha,
                hasAudio = false,
                url = url,
                // The image is its own cover; no separate poster object is fetched.
                poster = PolicyVideoPoster(url, sha, bytes, 0L),
                kind = PolicyMediaKind.IMAGE,
            )
        }
        val duration = root.requiredLong("durationMs")?.takeIf { it in 1..PolicyVideoValidation.MAX_DURATION_MS }
            ?: return null
        val width = root.requiredInt("width")?.takeIf { it in 1..PolicyVideoValidation.MAX_WIDTH }
            ?: return null
        val height = root.requiredInt("height")?.takeIf { it in 1..PolicyVideoValidation.MAX_HEIGHT }
            ?: return null
        if (kotlin.math.abs(width.toDouble() / height - 16.0 / 9.0) > 0.03) return null
        val fps = root.requiredInt("fps")?.takeIf { it in 1..PolicyVideoValidation.MAX_FPS } ?: 0
        val bytes = root.requiredLong("bytes")
            ?.takeIf { it in 1..PolicyVideoValidation.MAX_OFFICIAL_FILE_BYTES } ?: return null
        val hasAudio = root.requiredBoolean("hasAudio") ?: return null
        val posterObject = root.optJSONObject("poster") ?: return null
        val posterUrl = posterObject.requiredString("url")?.let { officialUrl(it, base) } ?: return null
        val posterSha = posterObject.requiredString("sha256")?.takeIf { shaPattern.matches(it) } ?: return null
        val posterBytes = posterObject.requiredLong("bytes")
            ?.takeIf { it in 1..PolicyVideoValidation.MAX_POSTER_BYTES } ?: return null
        val atMs = posterObject.requiredLong("atMs")?.takeIf { it in 0..duration } ?: return null
        return OfficialMediaItem(
            id = id,
            name = name,
            nameEn = nameEn,
            roles = roles,
            durationMs = duration,
            width = width,
            height = height,
            fps = fps,
            bytes = bytes,
            sha256 = sha,
            hasAudio = hasAudio,
            url = url,
            poster = PolicyVideoPoster(posterUrl, posterSha, posterBytes, atMs),
            kind = PolicyMediaKind.VIDEO,
        )
    }

    /** HTTPS, same host, same official prefix, no credentials/query/fragment/escapes. */
    private fun officialUrl(raw: String, base: URI): String? {
        if (raw.length > 2048 || raw.any { it.isWhitespace() || it.isISOControl() }) return null
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (uri.scheme != "https") return null
        if (!uri.host.equals(base.host, ignoreCase = true)) return null
        if (uri.port != -1 && uri.port != 443) return null
        if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
        val path = uri.rawPath ?: return null
        if (!path.startsWith(PREFIX) || path.contains('\\') || path.contains('%')) return null
        if (path.split('/').any { it == "." || it == ".." }) return null
        return uri.toString()
    }

    private fun validName(value: String): Boolean =
        value.isNotEmpty() && value.length <= MAX_NAME_LENGTH &&
            value.none { it.isISOControl() }

    private fun parseIso(raw: String): Long? = runCatching {
        OffsetDateTime.parse(raw).toInstant().toEpochMilli()
    }.getOrNull()

    private fun cacheFile(context: Context): File =
        File(context.filesDir, "policy-videos/catalog.json")

    private fun readCache(context: Context): OfficialMediaCatalogData? {
        val file = cacheFile(context)
        if (!file.isFile) return null
        val root = JSONObject(file.readText())
        if (root.optString("url") != manifestUrl()) return null
        val etag = root.optString("etag", "").takeIf { it.isNotBlank() }
        val fetchedAt = root.optLong("fetchedAt", 0L)
        val checkedAt = root.optLong("checkedAt", 0L)
        val manifest = root.optJSONObject("manifest")?.toString() ?: return null
        val parsed = parse(manifest, manifestUrl())
        val catalog = parsed.catalog ?: return null
        return catalog.copy(
            fetchedAtMs = fetchedAt,
            checkedAtMs = checkedAt,
            etag = etag,
        )
    }

    private fun writeCache(context: Context, catalog: OfficialMediaCatalogData) {
        val file = cacheFile(context)
        file.parentFile?.mkdirs()
        val body = JSONObject()
            .put("url", catalog.manifestUrl)
            .put("etag", catalog.etag ?: "")
            .put("fetchedAt", catalog.fetchedAtMs)
            .put("checkedAt", catalog.checkedAtMs)
            .put("manifest", manifestJson(catalog))
        val part = File(file.parentFile, file.name + ".part")
        part.writeText(body.toString())
        if (!part.renameTo(file)) {
            part.copyTo(file, overwrite = true)
            part.delete()
        }
    }

    /** The cache keeps the manifest in its own shape so a future parser can reread it. */
    private fun manifestJson(catalog: OfficialMediaCatalogData): JSONObject = JSONObject()
        .put("schema", 1)
        .put("pack", catalog.pack)
        .put("updatedAt", iso(catalog.updatedAtMs))
        .put(
            "videos",
            JSONArray().apply {
                catalog.items.filter { it.kind == PolicyMediaKind.VIDEO }.forEach { item ->
                    put(
                        JSONObject()
                            .put("id", item.id)
                            .put("name", item.name)
                            .put("nameEn", item.nameEn)
                            .put("roles", JSONArray(item.roles.map { it.wire }))
                            .put("durationMs", item.durationMs)
                            .put("width", item.width)
                            .put("height", item.height)
                            .put("fps", item.fps)
                            .put("bytes", item.bytes)
                            .put("sha256", item.sha256)
                            .put("hasAudio", item.hasAudio)
                            .put("url", item.url)
                            .put(
                                "poster",
                                JSONObject()
                                    .put("url", item.poster.url)
                                    .put("sha256", item.poster.sha256)
                                    .put("bytes", item.poster.bytes)
                                    .put("atMs", item.poster.atMs),
                            ),
                    )
                }
            },
        )
        .put(
            "images",
            JSONArray().apply {
                catalog.items.filter { it.kind == PolicyMediaKind.IMAGE }.forEach { item ->
                    put(
                        JSONObject()
                            .put("id", item.id)
                            .put("name", item.name)
                            .put("nameEn", item.nameEn)
                            .put("roles", JSONArray(item.roles.map { it.wire }))
                            .put("width", item.width)
                            .put("height", item.height)
                            .put("bytes", item.bytes)
                            .put("sha256", item.sha256)
                            .put("url", item.url),
                    )
                }
            },
        )

    private fun iso(ms: Long): String =
        if (ms <= 0L) "" else OffsetDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(ms),
            java.time.ZoneOffset.UTC,
        ).toString()

    private fun JSONObject.requiredString(key: String): String? =
        (opt(key) as? String)?.takeIf { it.isNotEmpty() }

    private fun JSONObject.requiredLong(key: String): Long? {
        val value = opt(key) as? Number ?: return null
        val long = value.toLong()
        return long.takeIf { value.toDouble() == long.toDouble() }
    }

    private fun JSONObject.requiredInt(key: String): Int? {
        val value = requiredLong(key) ?: return null
        if (value !in Int.MIN_VALUE..Int.MAX_VALUE) return null
        return value.toInt()
    }

    private fun JSONObject.requiredBoolean(key: String): Boolean? = opt(key) as? Boolean
}
