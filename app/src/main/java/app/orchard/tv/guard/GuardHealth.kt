package app.orchard.tv.guard

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Whether Home can come back after standby or a restart. Read-only: the status page and the
 * first-install prompt only report what they find, and the system settings pages do the changing.
 *
 * The guard being listed in accessibility is not proof that Android bound it: after an update
 * Android keeps the list entry but may never bind the new version. [guardRunning] is the authority
 * for that, and the Home status alone is not enough either: Aerial can be the default home while
 * the guard is dead.
 */
data class GuardHealth(
    /** The system resolves Home to PlanYourTV. Null when PackageManager could not answer. */
    val homeDefault: Boolean?,
    /** Accessibility master switch, as far as the app may read it. */
    val accessibilityOn: Boolean,
    /** Home Guard is in the enabled accessibility services list. */
    val guardListed: Boolean,
    /** Home Guard is actually bound. */
    val guardRunning: Boolean,
    /** WRITE_SECURE_SETTINGS: the guard can put itself back after the system turns it off. */
    val selfHeal: Boolean,
    /** The revival notification listener is enabled, by exact component. Null when unreadable. */
    val revival: Boolean?,
    /** Usage access: used to tell which app is in front. Null when unreadable. */
    val usageAccess: Boolean?,
    /** Overlay permission, for the policy countdown window. Null when unreadable. */
    val overlay: Boolean?,
) {
    /** Home is either the default launcher or covered by the running guard. */
    val homeReady: Boolean get() = homeDefault == true || guardRunning

    /** Failures that stop the guard from bringing Home back now, or after the system resets it. */
    val coreIssues: Int get() = listOf(!homeReady, !guardRunning, !selfHeal).count { it }

    /** Failures that do not stop the guard now but make later recovery less reliable. */
    val warningIssues: Int get() =
        listOf(revival == false, usageAccess == false, overlay == false).count { it }

    val issues: Int get() = coreIssues + warningIssues

    val coreBroken: Boolean get() = coreIssues > 0

    /** Every required item was read and is on. Unknown states are never green. */
    val healthy: Boolean get() =
        !coreBroken && revival == true && usageAccess == true && overlay != false

    companion object {
        fun read(context: Context): GuardHealth {
            val pm = context.packageManager
            val homeDefault = runCatching {
                val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == context.packageName
            }.getOrNull()

            val guard = ComponentName(context, HomeGuardService::class.java)
            val accessibility = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            )
            val accessibilityOn = runCatching {
                Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
            }.getOrDefault(false)
            val guardListed = listed(accessibility, guard)
            val selfHeal = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_SECURE_SETTINGS,
            ) == PackageManager.PERMISSION_GRANTED
            val revival = runCatching {
                val listeners = Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners",
                )
                listed(listeners, ComponentName(context, RevivalListener::class.java))
            }.getOrNull()
            val usageAccess = runCatching { HomeGuardService.usageStatsAllowed(context) }.getOrNull()
            val overlay = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                true
            } else {
                runCatching { Settings.canDrawOverlays(context) }.getOrNull()
            }

            return GuardHealth(
                homeDefault = homeDefault,
                accessibilityOn = accessibilityOn,
                guardListed = guardListed,
                guardRunning = HomeGuardService.isRunning,
                selfHeal = selfHeal,
                revival = revival,
                usageAccess = usageAccess,
                overlay = overlay,
            )
        }

        /** Usage access settings, where the TV has a page for them. */
        fun usageAccessSettingsIntent(context: Context): Intent? =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) null
            else resolvable(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

        /** This app's overlay permission page, where the TV has one. */
        fun overlaySettingsIntent(context: Context): Intent? =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) null
            else resolvable(
                context,
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ),
            )

        private fun resolvable(context: Context, intent: Intent): Intent? =
            intent.takeIf { context.packageManager.resolveActivity(it, 0) != null }
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        private fun listed(value: String?, component: ComponentName): Boolean =
            value.orEmpty()
                .split(':')
                .any { it.isNotBlank() && ComponentName.unflattenFromString(it) == component }
    }
}
