package app.orchard.tv.policy

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.util.Base64
import android.util.Log
import com.planyourtv.policy.PolicyRuntime
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Uploads the launchable-app inventory and square PNG icons to the parent backend.
 *
 * The list is uploaded first (a few KB); icons follow in the background by content hash. The
 * package watcher only marks the inventory dirty and debounces a full lightweight scan, so an
 * app store installing three apps in a row results in one upload, not three.
 */
object PolicyAppInventory {
    private const val TAG = "PolicyInventory"
    private const val ICON_SIZE = 144
    private const val ICON_MAX_BYTES = 64 * 1024
    private const val DEBOUNCE_MS = 5_000L
    private const val ICON_BATCH = 16
    private const val PREFS = "policy_inventory"
    private const val KEY_ICONS = "icons"
    private const val KEY_UPLOADED_FP = "uploaded_fingerprint"
    private const val KEY_PENDING_ICONS = "pending_icons"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val started = AtomicBoolean(false)

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var debounce: Job? = null

    @Volatile
    private var retryJob: Job? = null

    private var retryStep = 0

    @Volatile
    private var launcherCallback: LauncherApps.Callback? = null

    private data class Scanned(
        val apps: List<PolicyInstalledApp>,
        val hashes: Map<String, String>,
        val bytes: MutableMap<String, ByteArray>,
    )

    fun start(context: Context) {
        appContext = context.applicationContext
        if (!started.compareAndSet(false, true)) return
        registerPackageWatcher(context.applicationContext)
    }

    fun requestSync(reason: String, immediate: Boolean = false) {
        if (!PolicyModeCoordinator.isCloud()) return
        if (!PolicyRuntime.state.value.bound) return
        debounce?.cancel()
        debounce = scope.launch {
            if (!immediate) delay(DEBOUNCE_MS)
            sync(reason)
        }
    }

    private fun registerPackageWatcher(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            ?: return
        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String?, user: UserHandle?) =
                requestSync("package-added")

            override fun onPackageRemoved(packageName: String?, user: UserHandle?) =
                requestSync("package-removed")

            override fun onPackageChanged(packageName: String?, user: UserHandle?) =
                requestSync("package-changed")

            override fun onPackagesAvailable(
                packageNames: Array<out String>?,
                user: UserHandle?,
                replacing: Boolean,
            ) = requestSync("packages-available")

            override fun onPackagesUnavailable(
                packageNames: Array<out String>?,
                user: UserHandle?,
                replacing: Boolean,
            ) = requestSync("packages-unavailable")
        }
        runCatching { launcherApps.registerCallback(callback) }
            .onSuccess {
                launcherCallback = callback
                Log.i(TAG, "package watcher registered")
            }
            .onFailure { Log.w(TAG, "package watcher unavailable", it) }
    }

    private suspend fun sync(reason: String) = mutex.withLock {
        val context = appContext ?: return@withLock
        if (!PolicyModeCoordinator.isCloud()) return@withLock
        val state = PolicyRuntime.state.value
        if (!state.bound || state.deviceNo.isBlank()) return@withLock

        val scanned = scan(context)
        if (scanned.apps.isEmpty()) {
            Log.w(TAG, "no launchable apps found; keeping the previous cloud list")
            return@withLock
        }
        val fingerprint = fingerprint(scanned)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val same = fingerprint == prefs.getString(KEY_UPLOADED_FP, "")
        val uploads = scanned.apps.map { app ->
            PolicyAppUpload(
                appName = app.label,
                packageName = app.packageName,
                appIcon = scanned.hashes[app.packageName],
            )
        }

        // Always check the icon blobs, even when the metadata fingerprint is unchanged. A first
        // list upload can be followed by a process kill/restart before all icons finish; the
        // cloud list is correct but many icon hashes are still missing. Checking again is cheap
        // and makes icon upload converge.
        if (!same) {
            Log.i(TAG, "uploading ${uploads.size} apps ($reason)")
            val list = PolicyCloud.uploadApps(state.deviceNo, uploads)
            if (list.isFailure) {
                Log.w(TAG, "app list upload failed", list.exceptionOrNull())
                scheduleRetry()
                return@withLock
            }
            prefs.edit().putString(KEY_UPLOADED_FP, fingerprint).apply()
        } else {
            Log.i(TAG, "inventory unchanged ($reason); checking icons")
        }

        val hashes = uploads.mapNotNull { it.appIcon }.distinct()
        if (hashes.isEmpty()) {
            prefs.edit().putBoolean(KEY_PENDING_ICONS, true).apply()
            scheduleRetry()
            return@withLock
        }
        val missing = PolicyCloud.missingIcons(state.deviceNo, hashes).getOrElse {
            Log.w(TAG, "icon check failed", it)
            prefs.edit().putBoolean(KEY_PENDING_ICONS, true).apply()
            scheduleRetry()
            return@withLock
        }
        val emptyHashes = uploads.any { it.appIcon.isNullOrEmpty() }
        if (missing.isEmpty()) {
            if (!emptyHashes) {
                Log.i(TAG, "icons complete (${hashes.size})")
                prefs.edit().putBoolean(KEY_PENDING_ICONS, false).apply()
                resetRetry()
                return@withLock
            }
            prefs.edit().putBoolean(KEY_PENDING_ICONS, true).apply()
            Log.w(TAG, "some apps still have no icon hash; retry")
            scheduleRetry()
            return@withLock
        }
        prefs.edit().putBoolean(KEY_PENDING_ICONS, true).apply()
        Log.i(TAG, "icons missing=${missing.size} (of ${hashes.size})")

        val oldPriority = Process.getThreadPriority(Process.myTid())
        var failed = false
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            var count = 0
            for (hash in missing) {
                val app = scanned.apps.firstOrNull { scanned.hashes[it.packageName] == hash } ?: continue
                val bytes = scanned.bytes[hash] ?: PolicyInstalledApps.iconBytes(
                    context,
                    app.packageName,
                    ICON_SIZE,
                    ICON_MAX_BYTES,
                )
                if (bytes == null) {
                    failed = true
                    continue
                }
                val sent = PolicyCloud.uploadIcon(
                    state.deviceNo,
                    hash,
                    Base64.encodeToString(bytes, Base64.NO_WRAP),
                )
                if (sent.isFailure) {
                    failed = true
                    Log.w(TAG, "icon upload failed: ${app.packageName}", sent.exceptionOrNull())
                }
                count++
                if (count % ICON_BATCH == 0) delay(200)
            }
            Log.i(TAG, "icons uploaded: ${count}, failed=$failed")
        } finally {
            Process.setThreadPriority(oldPriority)
        }
        prefs.edit().putBoolean(KEY_PENDING_ICONS, failed || emptyHashes).apply()
        if (failed || emptyHashes) scheduleRetry() else resetRetry()
    }

    /** Retry a failed list/icon upload with a bounded exponential backoff while bound. */
    private fun scheduleRetry() {
        retryJob?.cancel()
        val delayMs = minOf(300_000L, 15_000L shl retryStep.coerceAtMost(4))
        retryStep++
        retryJob = scope.launch {
            delay(delayMs)
            requestSync("retry", immediate = true)
        }
    }

    private fun resetRetry() {
        retryStep = 0
        retryJob?.cancel()
        retryJob = null
    }

    private fun scan(context: Context): Scanned {
        val apps = PolicyInstalledApps.scan(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cached = readIconCache(prefs)
        val hashes = LinkedHashMap<String, String>()
        val bytes = HashMap<String, ByteArray>()
        for (app in apps) {
            val stampKey = app.stamp.toString()
            val hit = cached[app.packageName]
            if (hit != null && hit.first == stampKey) {
                hashes[app.packageName] = hit.second
                continue
            }
            val data = PolicyInstalledApps.iconBytes(
                context,
                app.packageName,
                ICON_SIZE,
                ICON_MAX_BYTES,
            )
            if (data == null) {
                hashes[app.packageName] = ""
                // Do not cache a failed draw: the next sync must retry it.
                cached.remove(app.packageName)
                continue
            }
            val hash = data.md5()
            hashes[app.packageName] = hash
            bytes[hash] = data
            cached[app.packageName] = stampKey to hash
        }
        writeIconCache(prefs, cached)
        return Scanned(apps, hashes, bytes)
    }

    private fun fingerprint(scanned: Scanned): String {
        val material = scanned.apps.joinToString("\n") { app ->
            "${app.packageName}|${app.label}|${app.stamp}|${scanned.hashes[app.packageName].orEmpty()}"
        }
        return material.sha256()
    }

    private fun readIconCache(prefs: android.content.SharedPreferences): MutableMap<String, Pair<String, String>> {
        val out = HashMap<String, Pair<String, String>>()
        val raw = prefs.getString(KEY_ICONS, null) ?: return out
        runCatching {
            val json = JSONObject(raw)
            for (pkg in json.keys()) {
                val value = json.optString(pkg, "")
                val separator = value.indexOf('|')
                if (separator > 0) {
                    out[pkg] = value.substring(0, separator) to value.substring(separator + 1)
                }
            }
        }
        return out
    }

    private fun writeIconCache(
        prefs: android.content.SharedPreferences,
        cache: Map<String, Pair<String, String>>,
    ) {
        val json = JSONObject()
        cache.forEach { (pkg, value) -> json.put(pkg, "${value.first}|${value.second}") }
        prefs.edit().putString(KEY_ICONS, json.toString()).apply()
    }

    private fun ByteArray.md5(): String =
        MessageDigest.getInstance("MD5").digest(this)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun String.sha256(): String =
        MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
