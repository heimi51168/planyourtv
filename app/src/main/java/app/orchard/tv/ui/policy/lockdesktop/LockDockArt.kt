package app.orchard.tv.ui.policy.lockdesktop

import android.content.ComponentName
import android.content.Context
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import app.orchard.tv.apps.AppEntry
import app.orchard.tv.apps.TileArt
import app.orchard.tv.apps.TileArtFactory

/**
 * Turns the finished 16:9 lock-screen cards into Aerial tile art. The cards are whole pictures
 * (background, icon and label), so they go through the same public banner bridge as a TV app
 * banner; no Aerial tile code is copied or changed.
 */
object LockDockArt {
    private val cache = HashMap<Int, TileArt>()

    fun entry(context: Context, slot: LockDockSlot): AppEntry = AppEntry(
        packageName = "lockdock:${slot.id.name.lowercase()}",
        label = slot.label,
        // Never launched: AppTile reads only the label and the art.
        component = ComponentName(context.packageName, "app.orchard.tv.MainActivity"),
        leanback = false,
        removable = false,
        art = art(context, slot.art),
    )

    fun art(context: Context, @DrawableRes res: Int): TileArt = synchronized(cache) {
        cache.getOrPut(res) {
            val drawable = ContextCompat.getDrawable(context, res)
            requireNotNull(drawable) { "lock dock art missing: $res" }
            TileArtFactory.fromBanner(drawable)
        }
    }
}
