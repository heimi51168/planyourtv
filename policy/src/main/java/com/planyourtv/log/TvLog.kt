package com.planyourtv.log

import android.util.Log

/**
 * One logging entry point for product diagnostics.
 *
 * The app enables it only for debug/dev/preview builds; production leaves it off, so a disabled
 * call does not format fields, build strings or write to logcat. Structured events use
 * `key=value` fields so a whole flow can be filtered by tag and followed by stage:
 *
 *   adb logcat -s PolicyFlow
 *
 * Example:
 *   TvLog.event("PolicyFlow", "grant.arm", "recordId" to 12, "source" to "GRANT")
 *
 * Never pass tokens, PINs, passwords or full personal URLs as fields. Log identifiers and
 * booleans instead (for example `icon=true`, not the avatar URL).
 */
object TvLog {
    @Volatile
    private var enabled = false

    private val whitespace = Regex("\\s+")

    /** Called once from the app entry point; dev/preview only. */
    fun setEnabled(value: Boolean) {
        enabled = value
    }

    fun isEnabled(): Boolean = enabled

    fun event(tag: String, stage: String, vararg fields: Pair<String, Any?>) {
        if (!enabled) return
        if (fields.isEmpty()) {
            Log.i(tag, stage)
            return
        }
        val tail = fields.joinToString(separator = " ") { (key, value) ->
            val text = value?.toString().orEmpty()
                .replace(whitespace, "_")
                .ifEmpty { "-" }
            "$key=$text"
        }
        Log.i(tag, "$stage $tail")
    }

    fun d(tag: String, message: String) {
        if (enabled) Log.d(tag, message)
    }

    fun i(tag: String, message: String) {
        if (enabled) Log.i(tag, message)
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        if (!enabled) return
        if (error == null) Log.w(tag, message) else Log.w(tag, message, error)
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        if (!enabled) return
        if (error == null) Log.e(tag, message) else Log.e(tag, message, error)
    }
}
