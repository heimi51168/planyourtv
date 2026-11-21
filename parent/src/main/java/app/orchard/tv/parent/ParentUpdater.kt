package app.orchard.tv.parent

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Checks the public release metadata and downloads the optional parent APK.
 *
 * The parent app is not tied to the TV app's version. The metadata lives in the
 * top-level `parent` object of the release `notes.json`; the TV parser only reads
 * `releases`, so the extra object is harmless for older TV builds. The download
 * is verified against that metadata, the expected package and the installed
 * signer before the system installer is asked to show its confirmation.
 */
internal class ParentUpdater(private val context: Context) {

    data class Release(
        val versionCode: Int,
        val versionName: String,
        val sha256: String,
        val sizeBytes: Long,
        /**
         * Gitee mirrors assets under a version tag and has no
         * `/releases/latest/download` alias. Null when the cached metadata was
         * written before this field existed; downloads then skip Gitee.
         */
        val releaseTag: String? = null,
    )

    sealed class CheckResult {
        data class Available(val release: Release) : CheckResult()

        /**
         * The installed build is current. [latestName]/[latestCode] describe the
         * newest release the metadata advertises, so the parent can compare the
         * two version numbers instead of only seeing "up to date". They are null
         * when the answer came from the once-a-day cache and no fetch was made.
         */
        data class UpToDate(
            val current: String,
            val latestName: String? = null,
            val latestCode: Int = 0,
        ) : CheckResult()

        object Failed : CheckResult()
    }

    sealed class DownloadResult {
        data class Ok(val file: File, val release: Release) : DownloadResult()
        data class Failed(val reason: Failure) : DownloadResult()
        object Cancelled : DownloadResult()
    }

    enum class Failure { NETWORK, VERIFY, NOT_NEWER }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "parent-updater").apply { isDaemon = true }
    }
    private val checking = AtomicBoolean(false)
    private val downloading = AtomicBoolean(false)

    @Volatile
    private var cancelled = false

    /** The last release newer than this build, even if its banner was dismissed. */
    fun cachedRelease(): Release? {
        val code = prefs.getInt(KEY_VERSION_CODE, 0)
        val name = prefs.getString(KEY_VERSION_NAME, null) ?: return null
        val sha = prefs.getString(KEY_SHA256, null) ?: return null
        if (code <= BuildConfig.VERSION_CODE) return null
        return Release(
            code,
            name,
            sha,
            prefs.getLong(KEY_SIZE, 0L),
            prefs.getString(KEY_RELEASE_TAG, null),
        )
    }

    fun isDismissed(release: Release): Boolean =
        prefs.getInt(KEY_DISMISSED_CODE, 0) == release.versionCode

    fun dismiss(release: Release) {
        prefs.edit().putInt(KEY_DISMISSED_CODE, release.versionCode).apply()
    }

    /**
     * Fetches metadata at most once a day unless [force] is true. [done] runs on
     * the main thread. Returns false when a check is already in flight.
     */
    fun check(force: Boolean, done: (CheckResult) -> Unit): Boolean {
        if (!checking.compareAndSet(false, true)) return false

        if (!force) {
            val age = System.currentTimeMillis() - prefs.getLong(KEY_CHECKED_AT, 0L)
            if (age in 0 until CHECK_INTERVAL_MS) {
                checking.set(false)
                val cached = cachedRelease()
                val result = if (cached != null) {
                    CheckResult.Available(cached)
                } else {
                    CheckResult.UpToDate(BuildConfig.VERSION_NAME)
                }
                main.post { done(result) }
                return true
            }
        }

        worker.execute {
            val result = try {
                val release = fetchRelease()
                prefs.edit()
                    .putLong(KEY_CHECKED_AT, System.currentTimeMillis())
                    .apply()
                if (release.versionCode > BuildConfig.VERSION_CODE) {
                    saveRelease(release)
                    CheckResult.Available(release)
                } else {
                    clearRelease()
                    CheckResult.UpToDate(
                        current = BuildConfig.VERSION_NAME,
                        latestName = release.versionName,
                        latestCode = release.versionCode,
                    )
                }
            } catch (e: Exception) {
                CheckResult.Failed
            }
            checking.set(false)
            main.post { done(result) }
        }
        return true
    }

    /**
     * Downloads and verifies the APK. Progress and the result run on the main
     * thread. Returns false when another download is already in flight.
     */
    fun download(
        release: Release,
        onProgress: (Int) -> Unit,
        done: (DownloadResult) -> Unit,
    ): Boolean {
        if (!downloading.compareAndSet(false, true)) return false
        cancelled = false
        worker.execute {
            var result: DownloadResult = DownloadResult.Failed(Failure.NETWORK)
            var sawHashMismatch = false
            for (url in parentApkUrls(release.releaseTag)) {
                if (cancelled) {
                    result = DownloadResult.Cancelled
                    break
                }
                val file = try {
                    downloadOne(url, release) { percent -> main.post { onProgress(percent) } }
                } catch (e: IOException) {
                    continue
                } catch (e: Exception) {
                    continue
                }
                // An unreadable or malformed archive must fail the update, not
                // strand the worker with a progress dialog that never closes.
                val verify = try {
                    verify(file, release)
                } catch (e: Exception) {
                    VerifyError.VERIFY
                }
                when (verify) {
                    VerifyError.NONE -> {
                        result = DownloadResult.Ok(file, release)
                        break
                    }
                    VerifyError.HASH -> {
                        file.delete()
                        sawHashMismatch = true
                    }
                    VerifyError.NOT_NEWER -> {
                        file.delete()
                        result = DownloadResult.Failed(Failure.NOT_NEWER)
                        break
                    }
                    VerifyError.SIGNATURE, VerifyError.PACKAGE, VerifyError.VERSION,
                    VerifyError.VERIFY,
                    -> {
                        file.delete()
                        result = DownloadResult.Failed(Failure.VERIFY)
                        break
                    }
                }
            }
            if (cancelled) {
                result = DownloadResult.Cancelled
            } else if (result is DownloadResult.Failed &&
                result.reason == Failure.NETWORK && sawHashMismatch
            ) {
                result = DownloadResult.Failed(Failure.VERIFY)
            }
            downloading.set(false)
            main.post { done(result) }
        }
        return true
    }

    fun cancelDownload() {
        cancelled = true
    }

    fun close() {
        cancelled = true
        worker.shutdownNow()
    }

    // ------------------------------------------------------------- metadata

    private fun fetchRelease(): Release {
        var lastError: Exception? = null
        for (url in METADATA_URLS) {
            try {
                return parseRelease(readText(url))
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IOException("no metadata source")
    }

    private fun parseRelease(json: String): Release =
        parseParentReleaseMetadata(json) ?: throw IOException("invalid parent metadata")

    private fun readText(url: String): String {
        val connection = open(url)
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val text = StringBuilder()
            connection.inputStream.bufferedReader().use { reader ->
                val buffer = CharArray(4096)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    text.append(buffer, 0, count)
                    if (text.length > MAX_METADATA_CHARS) throw IOException("metadata too large")
                }
            }
            return text.toString()
        } finally {
            connection.disconnect()
        }
    }

    private fun saveRelease(release: Release) {
        prefs.edit()
            .putInt(KEY_VERSION_CODE, release.versionCode)
            .putString(KEY_VERSION_NAME, release.versionName)
            .putString(KEY_SHA256, release.sha256)
            .putLong(KEY_SIZE, release.sizeBytes)
            .putString(KEY_RELEASE_TAG, release.releaseTag ?: "")
            .apply()
    }

    private fun clearRelease() {
        prefs.edit()
            .remove(KEY_VERSION_CODE)
            .remove(KEY_VERSION_NAME)
            .remove(KEY_SHA256)
            .remove(KEY_SIZE)
            .remove(KEY_RELEASE_TAG)
            .apply()
    }

    // ------------------------------------------------------------- download

    private fun downloadOne(
        url: String,
        release: Release,
        onProgress: (Int) -> Unit,
    ): File {
        val directory = File(context.cacheDir, "parent-updates").apply { mkdirs() }
        val target = File(directory, APK_NAME)
        val part = File(directory, "$APK_NAME.part")
        part.delete()
        target.delete()

        val connection = open(url)
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val length = connection.contentLengthLong.takeIf { it > 0L }
                ?: release.sizeBytes.takeIf { it > 0L }
            var copied = 0L
            var lastPercent = -1
            connection.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        if (cancelled) throw IOException("cancelled")
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copied += count
                        if (length != null && length > 0L) {
                            val percent = ((copied * 100L) / length).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
            if (!part.renameTo(target)) throw IOException("cannot rename download")
            onProgress(100)
            return target
        } finally {
            connection.disconnect()
            if (!target.isFile) part.delete()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "PlanYourTVParent/${BuildConfig.VERSION_NAME}")
        }

    // ------------------------------------------------------------ verification

    private enum class VerifyError { NONE, HASH, PACKAGE, SIGNATURE, VERSION, NOT_NEWER, VERIFY }

    private fun verify(file: File, release: Release): VerifyError {
        if (!file.isFile || file.length() == 0L) return VerifyError.HASH
        // Some old metadata may omit sizeBytes; only a positive mismatch is
        // conclusive enough to reject before hashing.
        if (release.sizeBytes > 0L && file.length() != release.sizeBytes) return VerifyError.HASH
        if (sha256(file) != release.sha256) return VerifyError.HASH

        val info = packageInfo(file) ?: return VerifyError.PACKAGE
        if (info.packageName != context.packageName) return VerifyError.PACKAGE
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
        if (code != release.versionCode) return VerifyError.VERSION
        if (code <= BuildConfig.VERSION_CODE) return VerifyError.NOT_NEWER
        if (!sameSigner(info)) return VerifyError.SIGNATURE
        return VerifyError.NONE
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(file: File): android.content.pm.PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
    }

    @Suppress("DEPRECATION")
    private fun sameSigner(archive: android.content.pm.PackageInfo): Boolean {
        fun signers(info: android.content.pm.PackageInfo?): Set<String>? {
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info?.signingInfo?.apkContentsSigners
            } else {
                info?.signatures
            }
            return signatures?.takeIf { it.isNotEmpty() }?.map { it.toCharsString() }?.toSet()
        }

        val archiveSigners = signers(archive) ?: return false
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val current = runCatching {
            context.packageManager.getPackageInfo(context.packageName, flags)
        }.getOrNull()
        val currentSigners = signers(current) ?: return false
        return archiveSigners == currentSigners
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private companion object {
        const val PREFS = "parent_update"
        const val KEY_CHECKED_AT = "checked_at"
        const val KEY_VERSION_CODE = "version_code"
        const val KEY_VERSION_NAME = "version_name"
        const val KEY_SHA256 = "sha256"
        const val KEY_SIZE = "size"
        const val KEY_RELEASE_TAG = "release_tag"
        const val KEY_DISMISSED_CODE = "dismissed_code"
        const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L
        const val MAX_METADATA_CHARS = 256 * 1024

        val METADATA_URLS = listOf("$SITE_BASE/notes.json", "$GITHUB_BASE/notes.json")
    }
}

/**
 * Reads the top-level `parent` object from the release notes.json. Kept as a
 * pure function so the release metadata contract can be unit tested.
 */
internal fun parseParentReleaseMetadata(json: String): ParentUpdater.Release? {
    val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
    val parent = root.optJSONObject("parent") ?: return null
    val code = parent.optInt("versionCode", 0)
    val name = parent.optString("versionName").trim()
    val sha = parent.optString("sha256").trim().lowercase()
    if (code <= 0 || name.isEmpty() || !PARENT_SHA256.matches(sha)) return null
    return ParentUpdater.Release(
        versionCode = code,
        versionName = name,
        sha256 = sha,
        sizeBytes = parent.optLong("sizeBytes", 0L).coerceAtLeast(0L),
        releaseTag = parseReleaseTag(root),
    )
}

/**
 * notes.json lists releases newest first, and every TV release bundles the
 * current parent APK, so the newest tag is the one whose Gitee assets (if the
 * mirror is caught up) match this metadata. A missing or unsafe tag just means
 * the download skips Gitee.
 */
private fun parseReleaseTag(root: JSONObject): String? {
    val releases = root.optJSONArray("releases") ?: return null
    val first = releases.optJSONObject(0) ?: return null
    val version = first.optString("v").trim()
    if (version.isEmpty()) return null
    val tag = if (version.startsWith("v")) version else "v$version"
    return tag.takeIf { PARENT_RELEASE_TAG.matches(it) }
}

/**
 * Gitee is the fastest source in mainland China but has no `/latest` alias and
 * can lag the official release. Its versioned URL is tried first; a missing
 * asset or a checksum mismatch falls through to the official site and GitHub.
 */
internal fun parentApkUrls(releaseTag: String?): List<String> = buildList {
    if (!releaseTag.isNullOrEmpty()) {
        add("$GITEE_BASE/releases/download/$releaseTag/$APK_NAME")
    }
    add("$SITE_BASE/$APK_NAME")
    add("$GITHUB_BASE/$APK_NAME")
}

private const val SITE_BASE = "https://planyourtv.me/download"
private const val GITHUB_BASE =
    "https://github.com/heimi51168/planyourtv/releases/latest/download"
private const val GITEE_BASE = "https://gitee.com/liurumbo/planyourtv"
private const val APK_NAME = "planyourtv-parent.apk"

private val PARENT_SHA256 = Regex("[0-9a-f]{64}")
private val PARENT_RELEASE_TAG = Regex("v[0-9][A-Za-z0-9._-]{0,63}")
