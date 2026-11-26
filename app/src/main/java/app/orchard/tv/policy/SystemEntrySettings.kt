package app.orchard.tv.policy

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether child mode releases the maker's system entries (Settings, security/app manager and the
 * app market) together with the desktop app menu's "App info" and "Uninstall" rows.
 *
 * Default off. It is a LOCAL-mode device setting: the cloud projection has no field for it yet,
 * so a bound TV keeps its existing behaviour. It only ever hides entries or lets them through;
 * it never disables a system app, so a child can still reach an uninstall surface through the
 * firmware's own shortcuts or a voice assistant. The parent-center confirmation says so.
 */
object SystemEntrySettings {
    private const val PREFS = "policy_system_entries"
    private const val KEY_VISIBLE = "visible"

    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null

    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    /** Called from PolicyProduct before Home builds its first list. */
    fun install(context: Context) {
        if (prefs != null) return
        val app = context.applicationContext
        appContext = app
        val loaded = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = loaded
        _visible.value = loaded.getBoolean(KEY_VISIBLE, false)
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

    internal fun debugResetForTest() {
        appContext
            ?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()
            ?.clear()
            ?.commit()
        appContext = null
        prefs = null
        _visible.value = false
    }
}