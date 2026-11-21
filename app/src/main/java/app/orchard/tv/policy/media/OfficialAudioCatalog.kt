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

/** One rights-cleared track in the read-only official music catalogue. */
data class OfficialAudioTrack(
    val id: String,
    val name: String,
    val nameEn: String,
    val artist: String?,
    val license: String,
    val sourceUrl: String?,
    val durationMs: Long,
    val bytes: Long,
    val sha256: String,
    val url: String,
)

data class OfficialAudioCatalogData(
    val manifestUrl: String,
    val pack: String,
    val updatedAtMs: Long,
    val tracks: List<OfficialAudioTrack>,
    val checkedAtMs: Long,
    val etag: String?,
)

/**
 * The official background-music catalogue, separate from the wallpaper-film catalogue.
 *
 * The two libraries share a transport style but not a model: a track has no wallpaper role,
 * poster or 16:9 frame, and the film catalogue's 12-item cap is a wallpaper limit rather than
 * a music limit. Keeping this parser separate avoids weakening either set of rules.
 *
 * The manifest is only fetched when the parent opens the music picker or asks to refresh.
 * Nothing is downloaded here; [OfficialAudioDownloader] fetches one track after an explicit
 * tap. Every value is untrusted: the manifest and every object URL must stay HTTPS on the
 * compile-time host under `/media/policy-audio/`, and each object name is its SHA-256.
 */
object OfficialAudioCatalog {
    private const val TAG = "PolicyAudioCatalog"
    private const val MAX_MANIFEST_BYTES = 64 * 1024
    const val MAX_TRACKS = 32
    private const val MAX_NAME_LENGTH = 80
    private const val MAX_ATTRIBUTION_LENGTH = 80
    private const val MAX_SOURCE_URL_LENGTH = 2048
    private const val REFRESH_MS = 10 * 60 * 1000L
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 25_000
    private const val STALL_MS = 30_000L
    private const val STALL_BYTES = 64 * 1024L
    private const val PREFIX = "/media/policy-audio/"
    private val idPattern = Regex("official:[a-z0-9-]{1,48}")
    private val packPattern = Regex("[A-Za-z0-9._-]{1,64}")
    private val shaPattern = Regex("[a-f0-9]{64}")
    private val objectNamePattern = Regex("[a-f0-9]{64}\\.mp3")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    @Volatile private var cache: OfficialAudioCatalogData? = null
    @Volatile private var loaded = false
    @Volatile private var refreshing = false
    @Volatile private var lastError: String? = null

    data class ParseResult(
        val catalog: OfficialAudioCatalogData? = null,
        val error: String? = null,
    )

    private fun manifestUrl(): String = BuildConfig.TV_AUDIO_CATALOG_URL.trim()

    fun isConfigured(): Boolean = manifestUrl().startsWith("https://")

    /** Current catalogue, or null when nothing valid has been loaded yet. */
    fun cached(context: Context): OfficialAudioCatalogData? {
        ensureLoaded(context)
        return cache
    }

    fun find(context: Context, id: String): OfficialAudioTrack? =
        cached(context)?.tracks?.firstOrNull { it.id == id }

    /** Starts one background refresh when the cache is stale; the caller never waits. */
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
                .onFailure { Log.w(TAG, "could not read the audio catalogue cache", it) }
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
            lastError = "AUDIO_NETWORK"
            Log.w(TAG, "audio catalogue connection could not be opened", error)
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
                lastError = "AUDIO_NETWORK"
                return
            }
            val raw = readLimited(connection, MAX_MANIFEST_BYTES) ?: run {
                lastError = "AUDIO_CATALOG_INVALID"
                return
            }
            val parsed = parse(raw, url, previous)
            if (parsed.catalog == null) {
                // Keep the last known-good cache; a bad publish must not empty the picker.
                lastError = parsed.error ?: "AUDIO_CATALOG_INVALID"
                return
            }
            val responseEtag = connection.getHeaderField("ETag")
                ?.takeIf { it.isNotBlank() }
            val catalog = parsed.catalog.copy(etag = responseEtag ?: previous?.etag)
            cache = catalog
            lastError = null
            runCatching { writeCache(context, catalog) }
                .onFailure { Log.w(TAG, "could not keep the audio catalogue", it) }
        } catch (error: Exception) {
            lastError = "AUDIO_NETWORK"
            Log.w(TAG, "audio catalogue refresh failed", error)
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
        previous: OfficialAudioCatalogData? = null,
        now: Long = System.currentTimeMillis(),
    ): ParseResult {
        if (raw.toByteArray(Charsets.UTF_8).size > MAX_MANIFEST_BYTES) {
            return ParseResult(error = "AUDIO_CATALOG_INVALID")
        }
        val base = runCatching { URI(manifestUrl) }.getOrNull()
            ?: return ParseResult(error = "AUDIO_CATALOG_INVALID")
        if (base.scheme != "https" || base.host.isNullOrBlank() ||
            (base.port != -1 && base.port != 443) || base.rawUserInfo != null ||
            base.rawQuery != null || base.rawFragment != null ||
            base.rawPath?.startsWith(PREFIX) != true ||
            !base.rawPath.endsWith("/manifest.json")
        ) {
            return ParseResult(error = "AUDIO_CATALOG_INVALID")
        }
        val root = runCatching { JSONObject(raw) }.getOrNull()
            ?: return ParseResult(error = "AUDIO_CATALOG_INVALID")
        if (root.requiredLong("schema") != 1L) return ParseResult(error = "AUDIO_CATALOG_INVALID")
        val pack = root.requiredString("pack")?.takeIf { packPattern.matches(it) }
            ?: return ParseResult(error = "AUDIO_CATALOG_INVALID")
        val updatedAt = root.requiredString("updatedAt")?.let(::parseIso)
            ?: return ParseResult(error = "AUDIO_CATALOG_INVALID")
        val tracks = root.optJSONArray("tracks") ?: return ParseResult(error = "AUDIO_CATALOG_INVALID")
        if (tracks.length() > MAX_TRACKS) return ParseResult(error = "AUDIO_CATALOG_INVALID")

        val seen = HashSet<String>()
        val items = ArrayList<OfficialAudioTrack>(tracks.length())
        for (index in 0 until tracks.length()) {
            val item = parseTrack(tracks.optJSONObject(index), base, pack)
                ?: return ParseResult(error = "AUDIO_CATALOG_INVALID")
            if (!seen.add(item.id)) return ParseResult(error = "AUDIO_CATALOG_INVALID")
            items += item
        }
        return ParseResult(
            catalog = OfficialAudioCatalogData(
                manifestUrl = manifestUrl,
                pack = pack,
                updatedAtMs = updatedAt,
                tracks = items,
                checkedAtMs = now,
                etag = previous?.etag,
            ),
        )
    }

    private fun parseTrack(root: JSONObject?, base: URI, pack: String): OfficialAudioTrack? {
        if (root == null) return null
        val id = root.requiredString("id")?.takeIf { idPattern.matches(it) } ?: return null
        val name = root.requiredString("name")?.trim()?.takeIf { validText(it, MAX_NAME_LENGTH) }
            ?: return null
        val nameEn = when {
            !root.has("nameEn") || root.opt("nameEn") == JSONObject.NULL -> name
            else -> root.requiredString("nameEn")?.trim()
                ?.takeIf { validText(it, MAX_NAME_LENGTH) } ?: return null
        }
        val artist = when {
            !root.has("artist") || root.opt("artist") == JSONObject.NULL -> null
            else -> root.optionalText("artist", MAX_ATTRIBUTION_LENGTH) ?: return null
        }
        val license = root.requiredString("license")?.trim()
            ?.takeIf { validText(it, MAX_ATTRIBUTION_LENGTH) }
            ?: return null
        val sourceUrl = when {
            !root.has("sourceUrl") || root.opt("sourceUrl") == JSONObject.NULL -> null
            else -> root.optionalText("sourceUrl", MAX_SOURCE_URL_LENGTH)
                ?.let(::sourcePage) ?: return null
        }
        val duration = root.requiredLong("durationMs")
            ?.takeIf { it in 1..PolicyAudioLibrary.MAX_AUDIO_DURATION_MS } ?: return null
        val bytes = root.requiredLong("bytes")
            ?.takeIf { it in 1..PolicyAudioLibrary.MAX_AUDIO_BYTES } ?: return null
        val sha = root.requiredString("sha256")?.takeIf { shaPattern.matches(it) } ?: return null
        val url = root.requiredString("url")?.let { officialObjectUrl(it, base, sha) }
            ?: return null
        return OfficialAudioTrack(
            id = id,
            name = name,
            nameEn = nameEn,
            artist = artist,
            license = license,
            sourceUrl = sourceUrl,
            durationMs = duration,
            bytes = bytes,
            sha256 = sha,
            url = url,
        )
    }

    /** HTTPS, same host, exactly `policy-audio/<pack>/<sha256>.mp3`, no redirects or escapes. */
    private fun officialObjectUrl(raw: String, base: URI, sha: String): String? {
        if (raw.length > 2048 || raw.any { it.isWhitespace() || it.isISOControl() }) return null
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (uri.scheme != "https") return null
        if (!uri.host.equals(base.host, ignoreCase = true)) return null
        if (uri.port != -1 && uri.port != 443) return null
        if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
        val path = uri.rawPath ?: return null
        if (!path.startsWith(PREFIX) || path.contains('\\') || path.contains('%')) return null
        if (path.split('/').any { it == "." || it == ".." }) return null
        val parts = path.removePrefix(PREFIX).split('/')
        // The manifest's pack names the current publishing batch, but a track added in an
        // earlier month stays valid; only the content-addressed object name is binding.
        if (parts.size != 2 || !packPattern.matches(parts[0])) return null
        if (parts[1] != "$sha.mp3" || !objectNamePattern.matches(parts[1])) return null
        return uri.toString()
    }

    /** A rights page may have a query string, but never credentials or a fragment. */
    private fun sourcePage(raw: String): String? {
        if (raw.length > MAX_SOURCE_URL_LENGTH ||
            raw.any { it.isWhitespace() || it.isISOControl() }
        ) {
            return null
        }
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host.isNullOrBlank()) return null
        if (uri.rawUserInfo != null || uri.rawFragment != null) return null
        return uri.toString()
    }

    private fun validText(value: String, max: Int): Boolean =
        value.isNotEmpty() && value.length <= max && value.none { it.isISOControl() }

    private fun parseIso(raw: String): Long? = runCatching {
        OffsetDateTime.parse(raw).toInstant().toEpochMilli()
    }.getOrNull()

    private fun cacheFile(context: Context): File =
        File(context.filesDir, "policy-audio/catalog.json")

    private fun readCache(context: Context): OfficialAudioCatalogData? {
        val file = cacheFile(context)
        if (!file.isFile) return null
        val root = JSONObject(file.readText())
        if (root.optString("url") != manifestUrl()) return null
        val etag = root.optString("etag", "").takeIf { it.isNotBlank() }
        val checkedAt = root.optLong("checkedAt", 0L)
        val manifest = root.optJSONObject("manifest")?.toString() ?: return null
        val catalog = parse(manifest, manifestUrl()).catalog ?: return null
        return catalog.copy(checkedAtMs = checkedAt, etag = etag)
    }

    private fun writeCache(context: Context, catalog: OfficialAudioCatalogData) {
        val file = cacheFile(context)
        file.parentFile?.mkdirs()
        val body = JSONObject()
            .put("url", catalog.manifestUrl)
            .put("etag", catalog.etag ?: "")
            .put("checkedAt", catalog.checkedAtMs)
            .put("manifest", manifestJson(catalog))
        val part = File(file.parentFile, file.name + ".part")
        part.writeText(body.toString())
        if (!part.renameTo(file)) {
            part.copyTo(file, overwrite = true)
            part.delete()
        }
    }

    private fun manifestJson(catalog: OfficialAudioCatalogData): JSONObject = JSONObject()
        .put("schema", 1)
        .put("pack", catalog.pack)
        .put("updatedAt", iso(catalog.updatedAtMs))
        .put(
            "tracks",
            JSONArray().apply {
                catalog.tracks.forEach { item ->
                    put(
                        JSONObject()
                            .put("id", item.id)
                            .put("name", item.name)
                            .put("nameEn", item.nameEn)
                            .put("artist", item.artist ?: JSONObject.NULL)
                            .put("license", item.license)
                            .put("sourceUrl", item.sourceUrl ?: JSONObject.NULL)
                            .put("durationMs", item.durationMs)
                            .put("bytes", item.bytes)
                            .put("sha256", item.sha256)
                            .put("url", item.url),
                    )
                }
            },
        )

    private fun iso(ms: Long): String = OffsetDateTime.ofInstant(
        java.time.Instant.ofEpochMilli(ms),
        java.time.ZoneOffset.UTC,
    ).toString()

    private fun JSONObject.requiredString(key: String): String? =
        (opt(key) as? String)?.takeIf { it.isNotEmpty() }

    private fun JSONObject.optionalText(key: String, max: Int): String? {
        if (!has(key) || opt(key) == JSONObject.NULL) return null
        val value = opt(key) as? String ?: return null
        val trimmed = value.trim()
        return trimmed.takeIf { validText(it, max) }
    }

    private fun JSONObject.requiredLong(key: String): Long? {
        val value = opt(key) as? Number ?: return null
        val long = value.toLong()
        return long.takeIf { value.toDouble() == long.toDouble() }
    }
}