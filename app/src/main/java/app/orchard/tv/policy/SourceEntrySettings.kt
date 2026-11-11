package app.orchard.tv.policy

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether Aerial shows its own source entries while a child mode is active.
 *
 * This is a presentation switch, not a hard input lock: the firmware's Source key, vendor input
 * panel and HDMI-CEC are outside what a normal app can control. The parent-center hint says so.
 */
object SourceEntrySettings {
    private const val PREFS = "policy_source_entries"
    private const val KEY_VISIBLE = "visible"

    private var prefs: SharedPreferences? = null

    private val _visible = MutableStateFlow(true)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    /** Called from PolicyProduct before Home builds its first source list. */
    fun install(context: Context) {
        if (prefs != null) return
        val loaded = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = loaded
        _visible.value = loaded.getBoolean(KEY_VISIBLE, true)
    }

    fun isVisible(): Boolean = _visible.value

    fun setVisible(visible: Boolean): Boolean {
        val loaded = prefs ?: return false
        val stored = runCatching {
            loaded.edit().putBoolean(KEY_VISIBLE, visible).commit()
        }.getOrDefault(false)
        if (!stored) return false
        _visible.value = visible
        return true
    }
}
