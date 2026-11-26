package app.orchard.tv.policy

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log

/**
 * The maker surfaces that can uninstall another app: Settings, the security/app manager and the
 * app market. Package names differ by brand, so [install] resolves the standard entry actions in
 * addition to the packages documented in docs/devices. No label matching: a translated or
 * rebranded launcher would silently escape it.
 *
 * This catalog only decides what child mode hides or lets through. It never disables a package.
 * [hideOnly] keeps a package off the desktop without foreground-blocking it: TCL's
 * `com.tcl.guard` is the installerPackageName for most apps there, so blocking its UI would
 * break app installs and updates, which are legitimate while the guard watches a session.
 */
object SystemEntryCatalog {
    private const val TAG = "SystemEntries"

    /**
     * Settings packages documented in docs/devices, plus the AOSP names. Hisense hides the
     * settings page inside `com.android.tv.settings` (`com.hisense.systemsettings.MainActivity`),
     * so the AOSP entry covers it; the Intent pass covers any brand not listed here.
     */
    private val SETTINGS_PACKAGES = setOf(
        "com.android.settings",
        "com.android.tv.settings",
        "com.xiaomi.mitv.settings",
        "com.tcl.settings",
        "com.helios.setting",
        "com.haier.tv.menusetting",
        "com.changhong.easysetting",
        "com.skyworth.setting",
        "com.tianci.setting",
    )

    /** App managers / security suites documented for Xiaomi and TCL. */
    private val SECURITY_PACKAGES = setOf(
        "com.xiaomi.mitv.tvmanager",
        "com.tcl.guard",
    )

    /** App markets documented for Xiaomi, TCL and Skyworth. */
    private val MARKET_PACKAGES = setOf(
        "com.xiaomi.mitv.appstore",
        "com.tcl.appmarket2",
        "com.mipt.store",
        "com.android.vending",
    )

    /** Hiding these is safe; foreground-blocking is not (see the class comment). */
    private val HIDE_ONLY = setOf("com.tcl.guard")

    @Volatile
    private var packages: Set<String> = emptySet()

    /** Called from PolicyProduct before Home builds its first list. */
    fun install(context: Context) {
        val app = context.applicationContext
        val pm = app.packageManager
        val resolved = HashSet<String>()
        resolved += SETTINGS_PACKAGES
        resolved += SECURITY_PACKAGES
        resolved += MARKET_PACKAGES
        entryIntents().forEach { resolved += resolve(pm, it) }
        resolved.remove(app.packageName)
        packages = resolved
        Log.i(TAG, "system entry packages: ${resolved.sorted()}")
    }

    private fun entryIntents(): List<Intent> = listOf(
        Intent(Settings.ACTION_SETTINGS),
        Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
        // API 26 and deprecated; the literals keep the minSdk 21 build free of API guards.
        Intent("android.settings.MANAGE_ALL_APPLICATIONS_SETTINGS"),
        Intent("android.settings.APPLICATION_SETTINGS"),
        Intent(Settings.ACTION_SECURITY_SETTINGS),
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MARKET),
        Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.APPSTORE_LAUNCHER"),
        Intent("android.intent.action.APPSTORE_LAUNCHER"),
    )

    private fun resolve(pm: PackageManager, intent: Intent): Set<String> = runCatching {
        val out = HashSet<String>()
        pm.queryIntentActivities(intent, 0)
            .mapNotNullTo(out) { it.activityInfo?.packageName }
        pm.resolveActivity(intent, 0)?.activityInfo?.packageName?.let(out::add)
        out
    }.getOrDefault(emptySet())

    fun isSystemEntry(packageName: String): Boolean =
        packageName.isNotEmpty() && packageName in packages

    /** Whether a foreground launch of this entry must be sent back to Home. */
    fun blocksLaunch(packageName: String): Boolean =
        isSystemEntry(packageName) && packageName !in HIDE_ONLY

    /**
     * Replace the resolved set in tests. Production code only gets it from [install], so a test
     * never needs Robolectric's package manager to carry every brand's components.
     */
    internal fun debugSetForTest(entries: Set<String>) {
        packages = entries
    }
}