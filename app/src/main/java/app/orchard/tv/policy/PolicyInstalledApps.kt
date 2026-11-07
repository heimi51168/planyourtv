package app.orchard.tv.policy

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.orchard.tv.guard.HomeGuardService
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One launchable TV app, shared by the cloud inventory uploader and the local rule editor. */
internal data class PolicyInstalledApp(
    val packageName: String,
    val label: String,
    /** Last-update time xor versionCode; used by the uploader's icon cache. */
    val stamp: Long,
    val icon: ImageBitmap? = null,
)

/**
 * The single launchable-app scan. Both the local picker and the cloud uploader use it so a
 * package excluded from one is excluded from the other, especially stock launchers that must
 * never appear in a child's app list.
 */
internal object PolicyInstalledApps {
    private const val UI_ICON_SIZE = 144

    fun scan(context: Context): List<PolicyInstalledApp> {
        val pm = context.packageManager
        val found = LinkedHashMap<String, ResolveInfo>()
        listOf(
            Intent.CATEGORY_LEANBACK_LAUNCHER,
            Intent.CATEGORY_LAUNCHER,
            "android.intent.category.APPSTORE_LAUNCHER",
        ).forEach { category ->
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
                .forEach { found.putIfAbsent(it.activityInfo.packageName, it) }
        }

        val guardedHomes = HomeGuardService.stockHomes()?.keys.orEmpty()
        val stockHomes = if (guardedHomes.isNotEmpty()) {
            guardedHomes
        } else {
            pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                0,
            ).mapTo(HashSet()) { it.activityInfo.packageName }
        }
        val apps = ArrayList<PolicyInstalledApp>(found.size)
        for ((pkg, info) in found) {
            if (pkg == context.packageName || pkg in stockHomes) continue
            if (pm.getLaunchIntentForPackage(pkg) == null &&
                pm.getLeanbackLaunchIntentForPackage(pkg) == null
            ) continue
            val pkgInfo = runCatching { pm.getPackageInfo(pkg, 0) }.getOrNull() ?: continue
            val label = runCatching { info.activityInfo.loadLabel(pm).toString().trim() }
                .getOrDefault("").ifBlank { pkg }
            val version = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            }
            apps.add(PolicyInstalledApp(pkg, label, pkgInfo.lastUpdateTime xor version))
        }
        apps.sortWith(compareBy { it.label.lowercase() })
        return apps
    }

    /** App list plus icons for the parent-center picker; never uploads anything. */
    suspend fun loadForPicker(context: Context): List<PolicyInstalledApp> =
        withContext(Dispatchers.IO) {
            scan(context).map { app ->
                app.copy(icon = iconBitmap(context, app.packageName, UI_ICON_SIZE))
            }
        }

    /**
     * Icons only for the packages a launcher will show. The study launcher must not pay for
     * loading every installed app's icon just to keep a handful of study apps.
     */
    suspend fun loadPackagesForPicker(
        context: Context,
        packages: Set<String>,
    ): List<PolicyInstalledApp> = withContext(Dispatchers.IO) {
        scan(context)
            .filter { it.packageName in packages }
            .map { app ->
                app.copy(icon = iconBitmap(context, app.packageName, UI_ICON_SIZE))
            }
    }

    fun iconBytes(
        context: Context,
        packageName: String,
        size: Int,
        maxBytes: Int,
    ): ByteArray? = runCatching {
        val bitmap = drawIcon(context, packageName, size) ?: return@runCatching null
        try {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray().takeIf { it.isNotEmpty() && it.size <= maxBytes }
        } finally {
            bitmap.recycle()
        }
    }.getOrNull()

    private fun iconBitmap(context: Context, packageName: String, size: Int): ImageBitmap? =
        runCatching {
            drawIcon(context, packageName, size)?.asImageBitmap()
        }.getOrNull()

    private fun drawIcon(context: Context, packageName: String, size: Int): Bitmap? =
        runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, size, size)
                drawable.draw(canvas)
                bitmap
            } catch (error: Throwable) {
                bitmap.recycle()
                throw error
            }
        }.getOrNull()
}
