package app.orchard.tv.policy

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Device-wide switch that controls the extra parent-PIN prompts inside the web parent center's
 * "user configuration" tab.
 *
 * ON is the default for fresh and upgraded devices: entering Time & Apps, saving its rules,
 * saving the unlimited-app picker, and starting the out-of-plan timer all ask for the parent
 * PIN again. OFF skips exactly those prompts; turning the switch off itself needs a one-time
 * USER_CONFIG proof. System configuration keeps every existing PIN gate either way.
 */
object UserConfigPinSettings {
    private const val PREFS = "policy_user_config_pin"
    private const val KEY_REQUIRED = "required"

    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null

    private val _required = MutableStateFlow(true)
    val required: StateFlow<Boolean> = _required.asStateFlow()

    fun install(context: Context) {
        if (prefs != null) return
        val application = context.applicationContext
        appContext = application
        val loaded = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = loaded
        _required.value = loaded.getBoolean(KEY_REQUIRED, true)
    }

    fun isRequired(): Boolean = _required.value

    fun setRequired(required: Boolean): Boolean {
        val loaded = prefs ?: return false
        val stored = runCatching {
            loaded.edit().putBoolean(KEY_REQUIRED, required).commit()
        }.getOrDefault(false)
        if (!stored) return false
        _required.value = required
        return true
    }

    /**
     * Drops the in-process handle so the next [install] reads storage again. Tests use the
     * default to clear the file, or `clearStorage = false` to verify persistence.
     */
    internal fun debugResetForTest(clearStorage: Boolean = true) {
        if (clearStorage) {
            appContext
                ?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                ?.edit()
                ?.clear()
                ?.commit()
        }
        prefs = null
        _required.value = true
    }
}