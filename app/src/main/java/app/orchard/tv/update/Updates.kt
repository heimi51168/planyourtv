package app.orchard.tv.update

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.core.content.edit
import app.orchard.tv.BuildConfig
import app.orchard.tv.guard.GuardSetup
import app.orchard.tv.system.DiagLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Whether a newer release is out (issues #24, #76). Most owners stay on the version they installed
 * and report bugs fixed long ago. PlanYourTV reads the latest community release from GitHub;
 * Control Center then names the new version and says how to update. Nothing is downloaded or
 * installed in the background: only when the owner picks "Download and Install" does PlanYourTV
 * fetch the new APK and hand it to the system installer ([install]).
 *
 * Home and network events check at most every three hours; opening Control Center shortens that
 * to ten minutes. Failed checks retry while Home is in front (#912).
 */
object Updates {
    private const val TAG = "PlanYourTVUpdates"
    private const val PREFS = "updates"
    // Community release channel. The website is tried first because GitHub is unreliable in
    // mainland China; GitHub Releases remain the fallback and the authoritative source. Publish
    // version.txt, notes.json and per-flavor APKs in both places, or override these constants in
    // your own fork.
    private const val SITE_BASE = "https://planyourtv.me/download"
    private const val GITHUB_BASE = "https://github.com/heimi51168/planyourtv/releases/latest/download"
    private val VERSION_URLS = listOf("$SITE_BASE/version.txt", "$GITHUB_BASE/version.txt")
    private val NOTES_URLS = listOf("$SITE_BASE/notes.json", "$GITHUB_BASE/notes.json")

    /**
     * A source that delivers less than [STALL_BYTES] in [STALL_MS] is given up for the next one
     * (issue #390). A slow but moving download (about 8 KB/s and up) still finishes.
     */
    private const val STALL_MS = 30_000L
    private const val STALL_BYTES = 256 * 1024L
    const val CONTROL_CENTER_MAX_AGE_MS = UpdateCheck.PANEL_MAX_AGE_MS

    /** This build's version, e.g. "1.17" (the guard build's "-guard" left off). */
    val current: String = BuildConfig.VERSION_NAME.substringBefore('-')

    /** The newer release's version, or null when this is the latest (or nothing is known yet). */
    var available by mutableStateOf<String?>(null); private set

    /**
     * What the update brings: each release after this one up to [available], newest first. Empty
     * until read, and when the website has no notes (sites deployed before notes.json).
     */
    var notes by mutableStateOf<List<ReleaseNotes>>(emptyList()); private set
    private var readingNotes = false

    /** Where an install started from Control Center has got to. */
    var install by mutableStateOf<Install>(Install.Idle); private set

    /**
     * The parent center's auto-update switch. Device-level on purpose: it does not depend on the
     * cloud binding, on child mode or on whether the password row is shown. Default off.
     */
    var auto by mutableStateOf(false); private set

    /** The version whose APK has been downloaded and waits for an idle TV, or null. */
    var autoReady by mutableStateOf<String?>(null); private set

    /** A silent auto-update download is in flight; Control Center must not start a second one. */
    var autoBusy by mutableStateOf(false); private set

    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false
    private var checker: UpdateCheck? = null

    private const val KEY_AUTO = "auto"
    private const val KEY_AUTO_CHOICE = "auto_choice"
    private const val KEY_AUTO_READY = "auto_ready"
    private const val KEY_AUTO_TRIED = "auto_tried"
    /** Another app must stay in front this long before Aerial replaces itself unseen. */
    private const val AUTO_INSTALL_DELAY_MS = 20_000L
    /** A failed background download waits this long before Home resumes retry it. */
    private const val AUTO_RETRY_MS = 30 * 60 * 1000L
    private var autoDownloading = false
    private var autoJob: Job? = null
    private var autoRetryAt = 0L
    private var autoArmedFor: String? = null
    private val autoInstall = Runnable { installAuto() }

    fun init(context: Context) {
        if (started) return
        started = true
        appContext = context.applicationContext
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        available = prefs.getString("latest", null)?.takeIf { newer(it, current) }
        auto = prefs.getBoolean(KEY_AUTO, false)
        autoReady = prefs.getString(KEY_AUTO_READY, null)?.takeIf { newer(it, current) }
        checker = UpdateCheck(
            scope = scope,
            lastSuccess = { prefs.getLong("checked_at", 0) },
            read = { withContext(Dispatchers.IO) { readAny(VERSION_URLS) } },
            save = { latest, checkedAt ->
                prefs.edit { putString("latest", latest); putLong("checked_at", checkedAt) }
                available = latest.takeIf { newer(it, current) }
                dropStaleAutoReady()
                loadNotes()
                scheduleAuto()
            },
            log = { Log.i(TAG, it); DiagLog.i("update check: $it") },
        )
        loadNotes()
        scheduleAuto()
        // A download left from an install that has since finished or been given up.
        scope.launch(Dispatchers.IO) {
            apkFile(context).takeIf { System.currentTimeMillis() - it.lastModified() > 60 * 60 * 1000L }?.delete()
        }
    }

    /**
     * [force]: now, however recently it last looked (ConfigReceiver's check_update). Opening Control
     * Center asks with a shorter [maxAgeMs]: that is where the viewer looks for a new version, and a
     * release out an hour ago was not shown there for hours more (issue #301).
     */
    fun checkIfStale(
        force: Boolean = false,
        maxAgeMs: Long = UpdateCheck.MAX_AGE_MS,
        networkCameUp: Boolean = false,
    ) {
        checker?.check(force, maxAgeMs, networkCameUp, when {
            force -> "forced"
            networkCameUp -> "network"
            maxAgeMs == CONTROL_CENTER_MAX_AGE_MS -> "control center"
            else -> "home"
        })
    }

    fun diagnostics(): String = if (!started) "not initialized" else
        "known=${prefs.getString("latest", null) ?: "unknown"}; notes=${notes.size}; " +
            "auto=$auto; ${checker?.diagnostics}"

    /** Whether this install may be replaced with no confirmation page at all. */
    fun silentInstallSupported(context: Context): Boolean =
        canInstall(context) && mayInstallSilently(context.applicationContext)

    /** SelfCompile must not reinstall the running APK while an automatic update is in flight. */
    internal fun autoUpdateBusy(): Boolean =
        autoDownloading || autoArmedFor != null || awaiting?.auto == true

    /** Whether the parent has already chosen auto update on or off (the first-enable notice is one-time). */
    fun autoChoiceMade(): Boolean = started && prefs.getBoolean(KEY_AUTO_CHOICE, false)

    /**
     * The parent center's auto-update switch. The choice belongs to this TV, not to a cloud
     * binding or to child mode; it is remembered across restarts. Turning it on only downloads
     * and installs when the system allows a silent replace ([silentInstallSupported]), and only
     * while another app is in front; everywhere else the update stays in Control Center.
     */
    fun setAuto(context: Context, enabled: Boolean) {
        if (!started) init(context)
        prefs.edit {
            putBoolean(KEY_AUTO, enabled)
            putBoolean(KEY_AUTO_CHOICE, true)
        }
        auto = enabled
        if (enabled) {
            autoRetryAt = 0
            scheduleAuto()
        } else {
            autoJob?.cancel()
            autoJob = null
            autoArmedFor = null
            main.removeCallbacks(autoInstall)
            // A blocking download notices cancellation only at the next suspension. Keep
            // autoDownloading set so a manual install cannot start a second download into the
            // same cached APK; the job's finally clears it.
        }
    }

    /** A Control Center update is downloading, waiting for its session, or showing a page. */
    private fun manualInstallBusy(): Boolean = when (install) {
        Install.Idle, is Install.Failed -> false
        else -> true
    }

    /** Forgets the downloaded copy of a version the latest release no longer is. */
    private fun dropStaleAutoReady() {
        val ready = prefs.getString(KEY_AUTO_READY, null)
        if (ready != null && ready != available) prefs.edit { remove(KEY_AUTO_READY) }
        autoReady = ready?.takeIf { it == available }
    }

    /**
     * Starts the background download for [available] when auto update is on and this install can
     * put it in place silently. Runs on the main thread, from an update check, Home resume and
     * the switch itself; a failed download waits [AUTO_RETRY_MS] before another try.
     */
    private fun scheduleAuto() {
        if (!started || !auto) return
        val latest = available ?: return
        if (latest == prefs.getString(KEY_AUTO_TRIED, null)) return
        if (autoDownloading || awaiting != null || manualInstallBusy()) return
        if (autoReady == latest) {
            armAutoInstall()
            return
        }
        if (autoReady != null) dropStaleAutoReady()
        if (SystemClock.elapsedRealtime() < autoRetryAt) return
        val app = appContext.applicationContext
        // Download only what can be installed without a page. An APK left for the owner to
        // confirm would expire in the cache and would not make the switch automatic anyway.
        if (!silentInstallSupported(app)) return
        if (Build.VERSION.SDK_INT >= 26 && !app.packageManager.canRequestPackageInstalls()) return
        autoDownloading = true
        autoBusy = true
        autoJob = scope.launch {
            try {
                val file = apkFile(app)
                val ready = withContext(Dispatchers.IO) {
                    verify(app, file)?.takeIf { it.versionName?.substringBefore('-') == latest }
                        ?: downloadFrom(app, file, report = false)
                }
                val ok = ready != null && sameSigner(app, ready) &&
                    versionCode(ready) >= BuildConfig.VERSION_CODE
                if (!ok) {
                    autoRetryAt = SystemClock.elapsedRealtime() + AUTO_RETRY_MS
                    Log.w(TAG, "auto update: download failed")
                    DiagLog.i("auto update: download failed")
                    return@launch
                }
                prefs.edit { putString(KEY_AUTO_READY, latest) }
                autoReady = latest
                DiagLog.i("auto update: $latest downloaded")
                armAutoInstall()
            } finally {
                autoDownloading = false
                autoBusy = false
                autoJob = null
            }
        }
    }

    /** Waits until another app has stayed in front for a while, then installs unseen. */
    private fun armAutoInstall() {
        if (!started || !auto) return
        val version = autoReady ?: return
        if (version != available) return
        if (version == prefs.getString(KEY_AUTO_TRIED, null)) return
        if (!apkFile(appContext).isFile) {
            prefs.edit { remove(KEY_AUTO_READY) }
            autoReady = null
            scheduleAuto()
            return
        }
        autoArmedFor = version
        main.removeCallbacks(autoInstall)
        if (!homeInFront) main.postDelayed(autoInstall, AUTO_INSTALL_DELAY_MS)
    }

    private fun installAuto() {
        if (Build.VERSION.SDK_INT < 28) return
        if (!started || !auto) return
        val version = autoArmedFor ?: return
        autoArmedFor = null
        if (homeInFront || version != autoReady || version != available) return
        if (version == prefs.getString(KEY_AUTO_TRIED, null)) return
        // A manual update from Control Center, or the self-compile reinstall, may hold the
        // installer: never put a second session beside it.
        if (awaiting != null || manualInstallBusy()) return
        val app = appContext.applicationContext
        if (!silentInstallSupported(app)) return
        val file = apkFile(app)
        if (!file.isFile) {
            prefs.edit { remove(KEY_AUTO_READY) }
            autoReady = null
            return
        }
        // One try per version: a firmware that refuses the session must not be retried in a
        // loop. The APK stays for Control Center's "Download and Install".
        prefs.edit { putString(KEY_AUTO_TRIED, version) }
        DiagLog.i("auto update: installing $version while Home is away")
        if (!installBySession(app, file, withDm = true, auto = true)) {
            Log.w(TAG, "auto update: the system would not take the update")
            DiagLog.i("auto update: the system would not take the update")
            install = Install.Idle
            autoReady = version
        }
    }

    /**
     * The release notes for [available], from the copy kept with the version it was read for, else
     * from the website (once per new version; the update page asks again while it has none). The
     * file is about 30 KB. A missing or bad file leaves the page as it was, without notes.
     */
    fun loadNotes() {
        if (!started) return
        val latest = available ?: run { notes = emptyList(); return }
        val zh = Locale.getDefault().language == "zh"
        if (prefs.getString("notes_for", null) == latest) {
            prefs.getString("notes", null)?.let { json ->
                runCatching { ReleaseNotesParser.parse(json, current, latest, zh) }.getOrNull()?.let { notes = it; return }
            }
        }
        if (readingNotes) return
        readingNotes = true
        scope.launch {
            val json = runCatching { withContext(Dispatchers.IO) { readAny(NOTES_URLS) } }
                .onFailure { Log.i(TAG, "release notes: ${it.javaClass.simpleName}") }.getOrNull()
            readingNotes = false
            val parsed = json?.let { runCatching { ReleaseNotesParser.parse(it, current, latest, zh) }.getOrNull() }
            // Only notes that already have this release: the site's file may be a deploy behind.
            if (parsed.isNullOrEmpty() || parsed.first().version != latest || available != latest) return@launch
            prefs.edit { putString("notes", json); putString("notes_for", latest) }
            notes = parsed
        }
    }

    /** Whether version [a] comes after [b], comparing dotted numbers ("1.10" after "1.9"). */
    fun newer(a: String, b: String): Boolean {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    /** First source that answers wins; failures fall through in order. */
    private fun readAny(urls: List<String>): String {
        var last: Exception? = null
        for (url in urls) {
            try {
                return read(url)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("no update source configured")
    }

    private fun read(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        LegacyUpdateTls.configure(appContext, c)
        c.connectTimeout = 8_000
        c.readTimeout = 10_000
        return try {
            c.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            c.disconnect()
        }
    }

    // ---- Installing the new version from Control Center ----

    sealed interface Install {
        data object Idle : Install
        data class Downloading(val fraction: Float) : Install
        /** A session was submitted; neither a result nor a visible confirmation is known yet. */
        data object Waiting : Install
        /** The system installer is open; it asks the owner and replaces Aerial, which restarts. */
        data object Opened : Install
        /** Aerial may not install apps yet; the TV's setting for it is open. */
        data object NeedsPermission : Install
        /** Android 6 firmware requires storage access before the APK can be saved. */
        data object NeedsStoragePermission : Install
        data class Failed(val reason: Failure) : Install
    }

    enum class Failure {
        /** Neither the website nor GitHub gave a complete APK. */
        Download,
        /** The TV lets no app hand an APK to its installer, or has no installer to hand it to. */
        Blocked,
        /** This copy is not the official package or not signed with the official key. */
        NotOfficial,
        /** The downloaded APK is no newer than this one (the website not yet updated). */
        NotNewer,
    }

    /** This build's installed identity, independent of Aerial's source namespace. */
    private val PACKAGE = BuildConfig.APPLICATION_ID

    /** Whether this copy can update itself from the release at all. */
    fun canInstall(context: Context): Boolean = context.packageName == PACKAGE

    /**
     * Downloads this flavor's APK of the latest release and installs it. Android 9 and later, a
     * PackageInstaller session first (installBySession), as only a session can carry the dex
     * metadata that has the system compile Aerial as it installs. Where the session fails, the system
     * installer by ACTION_VIEW, as before Android 9: TCL's verifier (docs/devices/tcl-mt5879-cn.md)
     * passes only installs that go through com.android.packageinstaller, as Dangbei's do, and an
     * ACTION_VIEW on the APK makes the installer itself the one installing. The APK must be this package, signed
     * with this copy's key and no older, or the installer would only fail with a less clear message.
     */
    fun install(context: Context) {
        if (install is Install.Downloading || install == Install.Opened) return
        // A second press while the first session's confirmation is awaited (#1004: two sessions).
        if (awaiting != null) return
        // The background download writes the same cached APK; let it finish rather than race it.
        if (autoDownloading) return
        val app = context.applicationContext
        if (!canInstall(app)) { install = Install.Failed(Failure.NotOfficial); return }
        if (LegacyUpdateStorage.needsPermission(app)) {
            install = Install.NeedsStoragePermission
            LegacyUpdateStorage.requestPermission(context)
            return
        }
        if (Build.VERSION.SDK_INT >= 26 && !app.packageManager.canRequestPackageInstalls()) {
            // Xiaomi MiTV-MFTR0 has this page; TCL MT5879 does not (tools/install.sh grants it there).
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(settings) }.isSuccess) {
                install = Install.NeedsPermission
                return
            }
            // No page to allow it on (Xiaomi MiTV-MFTP0, Android 9, has only Xiaomi's own settings:
            // "this TV does not let apps install apps", issue #284). Firmware installers often let
            // it through or ask themselves, so the download goes ahead and the installer decides.
            Log.i(TAG, "not allowed to install and no page to allow it on; trying the installer anyway")
        }
        install = Install.Downloading(0f)
        scope.launch {
            val file = apkFile(app)
            val ready = withContext(Dispatchers.IO) {
                // A download kept from a moment ago, when the owner backed out of the installer.
                verify(app, file)?.takeIf { testApk == null && it.versionName?.substringBefore('-') == available }
                    ?: downloadFrom(app, file)
            }
            val failure = when {
                ready == null -> Failure.Download
                !sameSigner(app, ready) -> Failure.NotOfficial
                versionCode(ready) < BuildConfig.VERSION_CODE -> Failure.NotNewer
                else -> null
            }
            if (failure != null) {
                Log.w(TAG, "not installing: $failure")
                if (failure != Failure.Download) file.delete()
                install = Install.Failed(failure)
                return@launch
            }
            val opened = if (Build.VERSION.SDK_INT >= 28) installBySession(app, file) else openInstaller(context, app, file)
            if (!opened) install = Install.Failed(Failure.Blocked)
        }
    }

    /**
     * From Android 13 the installer's ACTION_VIEW records the APK as a local file (packageSource 3),
     * and Android then restricts the app's settings (ACCESS_RESTRICTED_SETTINGS deny; seen on the
     * Android 13 TV emulator). On TCL T966D5 (Android 14) Home Guard was off after every update made
     * this way, and stayed on after one pushed by 多屏互动 (issue #723). A session that says where
     * the APK came from is not restricted; the system still asks the owner to confirm. Where the
     * firmware turns the session down, the installer's ACTION_VIEW as before (UpdateResult).
     *
     * The session also carries the new APK's dex metadata (assets/aerial-dm, see app/build.gradle.kts)
     * as aerial.dm, from Android 9: the system then compiles Aerial with its Baseline Profile as it
     * installs (Xiaomi MiTV-MFTR0: status speed-profile, reason install-dm). Without it the update ran
     * uncompiled until the next one, its main thread about twice as slow (docs/PERF-2026-10-30.md).
     * Should a firmware refuse the metadata, the APK alone once more ([withDm]).
     */
    @RequiresApi(28)
    private fun installBySession(app: Context, file: File, withDm: Boolean = true): Boolean =
        installBySession(app, file, withDm, auto = false)

    /**
     * [auto]: the switch started this, not the owner. A confirmation page is never put in front
     * of whatever is playing, and a failure never opens the installer: the APK stays for Control
     * Center's "Download and Install" instead.
     */
    @RequiresApi(28)
    private fun installBySession(app: Context, file: File, withDm: Boolean, auto: Boolean): Boolean = runCatching {
        val dm = if (withDm) dexMetadata(file) else null
        val result = Intent(app, UpdateResult::class.java)
            .putExtra(EXTRA_WITH_DM, dm != null)
            .putExtra(EXTRA_AUTO, auto)
        val id = commitSession(app, file, dm, result)
        Log.i(TAG, "install session $id committed for ${file.length()} bytes, dex metadata ${dm?.size ?: "none"}")
        DiagLog.i("update: session $id, dex metadata ${dm?.size ?: "none"}")
        // Hisense VIDAA_TV / MT9655 (Android 14, #1162) also accepts sessions without answering.
        // Silent updates need time to install and compile, not the short confirmation deadline.
        val timeout = if (mayInstallSilently(app)) SILENT_INSTALL_TIMEOUT_MS else ANSWER_TIMEOUT_MS
        awaitSession(app, id, timeout, "no answer from session $id", auto = auto)
        true
    }.getOrElse {
        if (withDm) {
            Log.w(TAG, "install session with dex metadata failed; without it", it)
            installBySession(app, file, withDm = false, auto = auto)
        } else if (auto) {
            Log.w(TAG, "auto update: no install session", it)
            DiagLog.i("auto update: no install session")
            install = Install.Idle
            false
        } else {
            Log.w(TAG, "no install session; opening the installer", it)
            openInstaller(app, app, file)
        }
    }

    /**
     * The running APK over itself with its own dex metadata (SelfCompile): no download, the same
     * version. Without the metadata it would be pointless, so no fallback: a failure is only logged.
     * [auto]: nobody asked, so a confirmation page is not put in front of the app being watched.
     */
    @RequiresApi(28)
    internal fun reinstallSelf(app: Context, auto: Boolean) {
        val apk = File(app.applicationInfo.sourceDir)
        val dm = dexMetadata(apk) ?: return
        runCatching {
            val result = Intent(app, UpdateResult::class.java).putExtra(EXTRA_WITH_DM, true)
                .putExtra(EXTRA_SELF, if (auto) SELF_AUTO else SELF_ASKED)
            val id = commitSession(app, apk, dm, result)
            Log.i(TAG, "self compile: session $id committed, dex metadata ${dm.size}")
        }.onFailure {
            Log.w(TAG, "self compile: no session", it)
            DiagLog.i("self compile: no session (${it.javaClass.simpleName}: ${it.message})")
        }
    }

    /** Writes [file] (and [dm] beside it) into a new install session and commits it; returns its id. */
    @RequiresApi(28)
    private fun commitSession(app: Context, file: File, dm: ByteArray?, result: Intent): Int {
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.packageName)
            if (Build.VERSION.SDK_INT >= 33) setPackageSource(PackageInstaller.PACKAGE_SOURCE_OTHER)
            // Android 12 and later let an app update itself without the confirmation page, which some
            // firmwares do not have (Xiaomi MiTV-MFTR0: no CONFIRM_INSTALL activity, so the session
            // gave way to the installer's ACTION_VIEW and its dex metadata was lost). The owner has
            // just picked "Download and Install". Where the system still wants a confirmation, it
            // answers STATUS_PENDING_USER_ACTION as before.
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("aerial.apk", 0, file.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                if (dm != null) session.openWrite("aerial.dm", 0, dm.size.toLong()).use { out ->
                    out.write(dm)
                    session.fsync(out)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                GuardSetup.noteInstallerOpened(app)
                session.commit(PendingIntent.getBroadcast(app, id, result, flags).intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(id) }
            throw e
        }
        return id
    }

    /** This APK's own dex metadata for this Android, or null (SelfCompile). */
    internal fun ownDexMetadata(context: Context): ByteArray? = dexMetadata(File(context.applicationInfo.sourceDir))

    /**
     * The dex metadata the build put in [apk] for this Android (28.dm for 9 to 11, 31.dm from 12),
     * or null: an APK from before 1.72, or one that cannot be read.
     */
    private fun dexMetadata(apk: File): ByteArray? = runCatching {
        val name = if (Build.VERSION.SDK_INT >= 31) "assets/aerial-dm/31.dm" else "assets/aerial-dm/28.dm"
        java.util.zip.ZipFile(apk).use { zip -> zip.getEntry(name)?.let { e -> zip.getInputStream(e).use { it.readBytes() } } }
    }.getOrNull()

    private const val EXTRA_WITH_DM = "app.orchard.tv.update.WITH_DM"
    private const val EXTRA_AUTO = "app.orchard.tv.update.AUTO"
    private const val EXTRA_SELF = "app.orchard.tv.update.SELF"
    private const val SELF_ASKED = 1
    private const val SELF_AUTO = 2

    /**
     * The update's session while its answer or confirmation page is awaited, else null. Hisense VIDAA_TV
     * (Android 9, Amlogic T963, issue #1004) took the session and then showed nothing: no answer
     * in the log, no confirmation page, Home never paused. Before 1.72 the installer's ACTION_VIEW
     * worked there. So a session whose page does not come up in time is abandoned for it.
     */
    private var awaiting: Awaiting? = null
    private class Awaiting(
        val id: Int,
        val confirmation: Boolean,
        val showConfirmation: Runnable?,
        val auto: Boolean = false,
    ) {
        var homeLeft = false
        var timedOut = false
        lateinit var check: Runnable
    }
    private var homeInFront = false
    private val main by lazy { Handler(Looper.getMainLooper()) }
    /** Sessions given up on: their answers, should they still come, are ignored. */
    private val abandoned = mutableSetOf<Int>()

    /** From the commit to the system's answer; it comes within a second on the TVs at hand. */
    private const val ANSWER_TIMEOUT_MS = 8_000L
    /** Android 12+ may compile and replace the app without any intermediate answer or page. */
    private const val SILENT_INSTALL_TIMEOUT_MS = 120_000L
    /**
     * Whether Android may install the update without asking: from 12, and only for the app that
     * installed Aerial last (USER_ACTION_NOT_REQUIRED). Installed by adb (#1162's Hisense: none) or
     * by the system installer, it always asks, and an answer is due as soon as before 12.
     */
    private fun mayInstallSilently(app: Context): Boolean = Build.VERSION.SDK_INT >= 31 && runCatching {
        app.packageManager.getInstallSourceInfo(app.packageName).installingPackageName == app.packageName
    }.getOrDefault(true)

    /** From starting the confirmation page to Home pausing beneath it. */
    private const val CONFIRM_TIMEOUT_MS = 4_000L

    /** Wait for an answer, or for a requested confirmation page to pause Home. */
    private fun awaitSession(
        app: Context, id: Int, ms: Long, why: String,
        confirmation: Boolean = false, showConfirmation: Runnable? = null,
        auto: Boolean = false,
    ) {
        awaiting?.let { main.removeCallbacks(it.check) }
        val a = Awaiting(id, confirmation, showConfirmation, auto)
        a.check = Runnable {
            if (awaiting !== a || (a.confirmation && a.homeLeft)) return@Runnable
            a.timedOut = true
            // Do not open an installer over another app or while the TV is asleep.
            if (homeInFront) giveUpOnSession(app, id, why, a.auto)
        }
        awaiting = a
        install = Install.Waiting
        // A confirmation requested while away is opened on resume; its timer starts then.
        if (showConfirmation == null) main.postDelayed(a.check, ms)
    }

    private fun stopAwaiting(id: Int) {
        val a = awaiting?.takeIf { it.id == id } ?: return
        main.removeCallbacks(a.check)
        awaiting = null
    }

    private fun giveUpOnSession(app: Context, id: Int, why: String, auto: Boolean = false) {
        stopAwaiting(id)
        abandoned += id
        runCatching { app.packageManager.packageInstaller.abandonSession(id) }
        if (auto) {
            // Nobody asked: never put an installer in front of the app being watched.
            Log.w(TAG, "$why; auto update left for Control Center")
            DiagLog.i("auto update: $why; left for Control Center")
            install = Install.Idle
            autoReady = available
            return
        }
        Log.w(TAG, "$why; abandoned, opening the installer")
        DiagLog.i("update: $why; abandoned, installer")
        val file = apkFile(app)
        if (!file.isFile || !openInstaller(app, app, file)) install = Install.Failed(Failure.Blocked)
    }

    /** The session's answer (UpdateResult): the owner's confirmation, or the installer instead. */
    internal fun onSessionResult(context: Context, intent: Intent) {
        val app = context.applicationContext
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val self = intent.getIntExtra(EXTRA_SELF, 0)
        if (self != 0) return onSelfResult(app, intent, status, auto = self == SELF_AUTO)
        val id = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val auto = intent.getBooleanExtra(EXTRA_AUTO, false)
        if (id in abandoned) {
            Log.i(TAG, "session $id answered ($status) after it was given up")
            return
        }
        stopAwaiting(id)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                if (auto) {
                    // A silent session that still wants a page means this firmware will not
                    // replace Aerial unseen. Abandon it; Control Center keeps the row.
                    abandoned += id
                    runCatching { app.packageManager.packageInstaller.abandonSession(id) }
                    Log.w(TAG, "auto update: session $id asks for a confirmation; left for Control Center")
                    DiagLog.i("auto update: confirmation needed; left for Control Center")
                    install = Install.Idle
                    autoReady = available
                    return
                }
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm == null) return giveUpOnSession(app, id, "session $id asks to confirm but has no page")
                if (homeInFront) openConfirmation(app, id, confirm)
                else awaitSession(
                    app, id, CONFIRM_TIMEOUT_MS, "confirmation page of session $id did not come up",
                    confirmation = true, showConfirmation = Runnable { openConfirmation(app, id, confirm) },
                )
            }
            PackageInstaller.STATUS_SUCCESS -> install = Install.Idle // normally this process is replaced
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                Log.i(TAG, "update not confirmed")
                install = Install.Idle
                if (auto) autoReady = available
            }
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                val file = apkFile(app)
                // A firmware that turns the dex metadata down (INSTALL_FAILED_BAD_DEX_METADATA):
                // the APK alone in a new session.
                if (intent.getBooleanExtra(EXTRA_WITH_DM, false) && message.orEmpty().contains("DEX_METADATA", ignoreCase = true) &&
                    Build.VERSION.SDK_INT >= 28 && file.isFile
                ) {
                    Log.w(TAG, "install session refused the dex metadata ($message); without it")
                    if (installBySession(app, file, withDm = false, auto = auto)) return
                }
                if (auto) {
                    Log.w(TAG, "auto update: session failed ($status, $message); left for Control Center")
                    DiagLog.i("auto update: session failed ($status); left for Control Center")
                    install = Install.Idle
                    autoReady = available
                    return
                }
                Log.w(TAG, "install session failed ($status, $message); opening the installer")
                DiagLog.i("update: session failed ($status, $message); installer")
                if (!file.isFile || !openInstaller(app, app, file)) install = Install.Failed(Failure.Blocked)
            }
        }
    }

    private fun openConfirmation(app: Context, id: Int, confirm: Intent) {
        runCatching { app.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onSuccess {
            DiagLog.i("update: session $id asks to confirm; page started")
            awaitSession(app, id, CONFIRM_TIMEOUT_MS, "confirmation page of session $id did not come up", confirmation = true)
        }.onFailure {
            // Some Android 13+ firmware has no CONFIRM_INSTALL page but accepts ACTION_VIEW (#827).
            Log.w(TAG, "could not ask to confirm the update; opening the installer", it)
            giveUpOnSession(app, id, "session $id has no confirmation page (${it.javaClass.simpleName})")
        }
    }

    /** The answer to [reinstallSelf]: never the installer by ACTION_VIEW, which would carry no metadata. */
    private fun onSelfResult(app: Context, intent: Intent, status: Int, auto: Boolean) {
        val id = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        // Arrives in the new process, once the replace is done.
        if (status == PackageInstaller.STATUS_SUCCESS) {
            DiagLog.i("self compile: installed, odex ${SelfCompile.odexBytes(app)?.div(1024)} KB")
            return
        }
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION && !auto) {
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            if (confirm != null && runCatching { app.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        }
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION && id >= 0) runCatching { app.packageManager.packageInstaller.abandonSession(id) }
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        Log.w(TAG, "self compile: not installed ($status, $message)")
        DiagLog.i("self compile: not installed ($status, $message)")
    }

    /** Resume failed checks; if the installer closed without replacing Aerial, reset its button. */
    fun onHomeResumed() {
        homeInFront = true
        checker?.setActive(true)
        if (install == Install.Opened) install = Install.Idle
        main.removeCallbacks(autoInstall)
        autoArmedFor = null
        awaiting?.let {
            // Only returning from a requested confirmation releases the session's button.
            // Visiting another app while awaiting an answer must not forget the update.
            if (it.showConfirmation != null) it.showConfirmation.run()
            else if (it.confirmation && it.homeLeft) stopAwaiting(it.id)
            else if (it.timedOut) it.check.run()
        }
        // A failed background download is retried here, after the network has likely settled.
        scheduleAuto()
    }

    fun onHomePaused() {
        homeInFront = false
        checker?.setActive(false)
        awaiting?.takeIf { it.confirmation }?.let {
            it.homeLeft = true
            install = Install.Opened
        }
        // Another app is in front: the one moment Aerial may replace itself unseen.
        armAutoInstall()
    }

    /** Back to the button, when the update page is left. */
    fun resetInstall() {
        if (awaiting == null && install !is Install.Downloading) install = Install.Idle
    }

    private fun apkFile(context: Context): File {
        // Android 5 and 6 installers read a file:// URI, so there the APK sits where they can read it.
        val dir = if (Build.VERSION.SDK_INT >= 24) context.cacheDir else context.externalCacheDir ?: context.cacheDir
        return File(File(dir, "updates"), "aerial.apk")
    }

    /**
     * For testing the whole update on a TV before a release (ConfigReceiver's update_test): the APK
     * pushed to Android/data/com.planyourtv.tv/files/update-test.apk stands in for the download.
     */
    @Volatile var testApk: File? = null

    /** The website first (reachable in China), then GitHub. Returns the APK's info once it parses. */
    private suspend fun downloadFrom(context: Context, file: File, report: Boolean = true): PackageInfo? {
        testApk?.takeIf { it.isFile }?.let { test ->
            file.parentFile?.mkdirs()
            test.copyTo(file, overwrite = true)
            if (Build.VERSION.SDK_INT < 24) file.setReadable(true, false)
            return verify(context, file)
        }
        val name = "planyourtv-${BuildConfig.FLAVOR}.apk"
        val sources = listOf(
            "$SITE_BASE/$name",
            "$GITHUB_BASE/$name",
        )
        for (url in sources) {
            val ok = runCatching { download(url, file, report) }
                .onFailure { Log.w(TAG, "could not download $url", it) }
                .isSuccess
            if (ok) verify(context, file)?.let { return it }
            file.delete()
        }
        return null
    }

    private suspend fun download(url: String, file: File, report: Boolean = true) {
        val directory = checkNotNull(file.parentFile)
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create update directory: $directory" }
        check(directory.canWrite()) { "Cannot write update directory: $directory" }
        val part = File(directory, file.name + ".part")
        val c = URL(url).openConnection() as HttpURLConnection
        LegacyUpdateTls.configure(appContext, c)
        c.connectTimeout = 10_000
        c.readTimeout = 20_000
        // A throttled route can keep the TLS handshake up and deliver a byte now and then, so no
        // read() ever blocks long enough to time out and the progress bar sits at 0% for good
        // (issue #390, FFALCON/TCL on China Unicom). So the bytes are counted, not each read timed.
        var windowAt = SystemClock.elapsedRealtime()
        var windowFrom = 0L
        try {
            if (c.responseCode != HttpURLConnection.HTTP_OK) error("HTTP ${c.responseCode}")
            val total = c.contentLength.toLong()
            var done = 0L
            var shown = -1
            c.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val now = SystemClock.elapsedRealtime()
                        if (now - windowAt >= STALL_MS) {
                            if (done - windowFrom < STALL_BYTES) error("stalled: ${done - windowFrom} bytes in ${(now - windowAt) / 1000} s")
                            windowAt = now
                            windowFrom = done
                        }
                        val percent = if (total > 0) (done * 100 / total).toInt() else 0
                        if (report && percent != shown) {
                            shown = percent
                            withContext(Dispatchers.Main) { install = Install.Downloading(percent / 100f) }
                        }
                    }
                }
            }
            if (total > 0 && done != total) error("got $done of $total bytes")
            if (!part.renameTo(file)) error("could not keep the download")
            // The installer on Android 5 and 6 opens the file itself.
            if (Build.VERSION.SDK_INT < 24) file.setReadable(true, false)
        } finally {
            c.disconnect()
            part.delete()
        }
    }

    /** The APK's package info when it parses as this package, else null. */
    @Suppress("DEPRECATION")
    private fun verify(context: Context, file: File): PackageInfo? {
        if (!file.isFile) return null
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val info = runCatching { context.packageManager.getPackageArchiveInfo(file.path, flags) }.getOrNull() ?: return null
        return info.takeIf { it.packageName == context.packageName }
    }

    /** Whether [apk] is signed like this copy. If the archive's signers cannot be read, the installer decides. */
    @Suppress("DEPRECATION")
    private fun sameSigner(context: Context, apk: PackageInfo): Boolean {
        val pm = context.packageManager
        fun signers(info: PackageInfo?): Set<String>? {
            val sigs = if (Build.VERSION.SDK_INT >= 28) info?.signingInfo?.apkContentsSigners else info?.signatures
            return sigs?.takeIf { it.isNotEmpty() }?.map { it.toCharsString() }?.toSet()
        }
        val theirs = signers(apk) ?: return true
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val ours = signers(runCatching { pm.getPackageInfo(context.packageName, flags) }.getOrNull()) ?: return true
        return theirs == ours
    }

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    private fun openInstaller(context: Context, app: Context, file: File): Boolean {
        val uri = if (Build.VERSION.SDK_INT >= 24) FileProvider.getUriForFile(app, "${app.packageName}.files", file)
        else Uri.fromFile(file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            GuardSetup.noteInstallerOpened(app)
            context.startActivity(intent)
            install = Install.Opened
            Log.i(TAG, "installer opened for ${file.length()} bytes")
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "no installer", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "installer refused", e)
            false
        }
    }
}
