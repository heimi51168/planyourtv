package com.planyourtv.policy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Debug-only broadcast bridge for the TV test harness.
 *
 * This receiver is declared only in app/src/debug/AndroidManifest.xml. It is
 * intentionally not part of the release manifest: it can change the session and
 * unlock the screen without the parent PIN.
 */
class PolicyDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Never re-enable the cloud engine while the product is in LOCAL mode.
        PolicyRuntime.start(
            context,
            devPin = DEV_PIN,
            active = PolicyModeStore.load() == PolicyMode.CLOUD,
        )
        when (intent.action) {
            ACTION_PENDING -> PolicyRuntime.debugPending(
                seconds = intent.getLongExtra(EXTRA_SECONDS, 30 * 60L),
                userName = intent.getStringExtra(EXTRA_USER).orEmpty().ifBlank { "小明" },
            )
            ACTION_ACTIVE -> PolicyRuntime.debugActive(
                seconds = intent.getLongExtra(EXTRA_SECONDS, 30 * 60L),
                userName = intent.getStringExtra(EXTRA_USER).orEmpty().ifBlank { "小明" },
            )
            ACTION_LOCKED -> PolicyRuntime.debugLocked()
            ACTION_SCREEN_OFF -> PolicyRuntime.debugScreenOff()
            ACTION_UNBIND -> PolicyRuntime.debugUnbind()
            ACTION_SET_PIN -> intent.getStringExtra(EXTRA_PIN)?.let(PolicyRuntime::debugSetPin)
            ACTION_STATUS -> writeStatus(context)
        }
    }

    private fun writeStatus(context: Context) {
        val state = PolicyRuntime.state.value
        val text = buildString {
            appendLine("storage=${TvStore.backend}")
            appendLine("deviceNo=${state.deviceNo}")
            appendLine("bound=${state.bound}")
            appendLine("sessionState=${state.sessionState}")
            appendLine("guardState=${state.guardState}")
            appendLine("remainingSeconds=${state.remainingSeconds}")
            appendLine("pinConfigured=${state.pinConfigured}")
        }
        runCatching {
            context.openFileOutput(STATUS_FILE, Context.MODE_PRIVATE).use { it.write(text.toByteArray()) }
        }
    }

    companion object {
        const val DEV_PIN = "123456"
        const val STATUS_FILE = "policy-status.txt"
        const val ACTION_PENDING = "com.planyourtv.policy.DEBUG_PENDING"
        const val ACTION_ACTIVE = "com.planyourtv.policy.DEBUG_ACTIVE"
        const val ACTION_LOCKED = "com.planyourtv.policy.DEBUG_LOCKED"
        const val ACTION_SCREEN_OFF = "com.planyourtv.policy.DEBUG_SCREEN_OFF"
        const val ACTION_UNBIND = "com.planyourtv.policy.DEBUG_UNBIND"
        const val ACTION_SET_PIN = "com.planyourtv.policy.DEBUG_SET_PIN"
        const val ACTION_STATUS = "com.planyourtv.policy.DEBUG_STATUS"
        const val EXTRA_SECONDS = "seconds"
        const val EXTRA_USER = "user"
        const val EXTRA_PIN = "pin"
    }
}
