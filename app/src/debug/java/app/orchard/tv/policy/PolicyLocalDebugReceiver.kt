package app.orchard.tv.policy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.planyourtv.policy.Activation
import com.planyourtv.policy.LocalPolicyStore
import com.planyourtv.policy.LocalRules
import com.planyourtv.policy.LocalTimeWindow
import com.planyourtv.policy.PolicyMode

/** Debug-only adb bridge for the local engine; not present in release builds. */
class PolicyLocalDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val state = PolicyModeCoordinator.state.value
        when (intent.action) {
            ACTION_FORCE_NONE -> PolicyModeCoordinator.debugForceMode(PolicyMode.NONE)
            ACTION_FORCE_LOCAL -> PolicyModeCoordinator.debugForceMode(PolicyMode.LOCAL)
            ACTION_FORCE_CLOUD -> PolicyModeCoordinator.debugForceMode(PolicyMode.CLOUD)
            ACTION_CLOUD_UNBOUND -> PolicyModeCoordinator.onCloudUnboundFromBackend()
            ACTION_SETUP -> {
                val start = intent.getIntExtra(EXTRA_START, 0)
                val end = intent.getIntExtra(EXTRA_END, 24 * 60)
                val rules = LocalRules(
                    weekdayWindows = listOf(LocalTimeWindow(start, end)),
                    weekendWindows = listOf(LocalTimeWindow(start, end)),
                    autoDailyLimitMinutes = intent.getIntExtra(EXTRA_DAILY, 60),
                    sessionLimitMinutes = intent.getIntExtra(EXTRA_SESSION, 20),
                    restMinutes = intent.getIntExtra(EXTRA_REST, 10),
                )
                val code = PolicyModeCoordinator.debugLocalSetup(
                    intent.getStringExtra(EXTRA_PIN).orEmpty().ifBlank { "123456" },
                    rules,
                )
                writeStatus(context, "setupRecovery=$code")
            }
            ACTION_RELOCK -> LocalPolicyRuntime.onScreenOff()
            ACTION_LOCAL_ACTIVE -> {
                if (PolicyModeCoordinator.mode == PolicyMode.LOCAL) {
                    LocalPolicyRuntime.debugStartSession(
                        intent.getLongExtra(EXTRA_SECONDS, 60L),
                    )
                }
            }
            ACTION_LOCAL_CLEAR_SESSION -> {
                if (PolicyModeCoordinator.mode == PolicyMode.LOCAL) {
                    LocalPolicyRuntime.debugClearSession()
                }
            }
            ACTION_LOCAL_END_USER -> {
                if (PolicyModeCoordinator.mode == PolicyMode.LOCAL) {
                    LocalPolicyRuntime.endSessionByUser()
                }
            }
            ACTION_LOCAL_EXPIRE_REST -> {
                if (PolicyModeCoordinator.mode == PolicyMode.LOCAL) {
                    LocalPolicyRuntime.debugExpireRest()
                }
            }
            ACTION_LOCAL_RESTORE_USAGE -> {
                if (PolicyModeCoordinator.mode == PolicyMode.LOCAL) {
                    LocalPolicyRuntime.debugRestoreUsage(
                        intent.getLongExtra(EXTRA_SECONDS, 0L),
                    )
                }
            }
            ACTION_LOCAL_ACTIVATION -> {
                val activation = runCatching {
                    Activation.valueOf(
                        intent.getStringExtra(EXTRA_ACTIVATION).orEmpty().uppercase(),
                    )
                }.getOrNull()
                if (activation != null) {
                    PolicyModeCoordinator.setLocalActivation(activation)
                    writeStatus(context, "activation=${activation.name}")
                }
            }
            ACTION_SETUP_DEFAULT -> {
                val code = PolicyModeCoordinator.debugLocalSetup("123456", LocalRules.DEFAULTS)
                writeStatus(context, "setupRecovery=$code")
            }
            ACTION_RULES -> {
                val start = intent.getIntExtra(EXTRA_START, 0)
                val end = intent.getIntExtra(EXTRA_END, 24 * 60)
                val rules = LocalRules(
                    weekdayWindows = listOf(LocalTimeWindow(start, end)),
                    weekendWindows = listOf(LocalTimeWindow(start, end)),
                    autoDailyLimitMinutes = intent.getIntExtra(EXTRA_DAILY, 60),
                    sessionLimitMinutes = intent.getIntExtra(EXTRA_SESSION, 20),
                    restMinutes = intent.getIntExtra(EXTRA_REST, 10),
                )
                PolicyModeCoordinator.debugSetLocalRules(rules)
            }
            ACTION_CLEAR -> LocalPolicyStore.resetLocalSetup()
            ACTION_OPEN_PARENT -> PolicyProduct.openParentSettings()
            ACTION_CLOSE_PARENT -> PolicyProduct.closeParentSettings()
            ACTION_STATUS -> writeStatus(context, "")
        }
    }

    private fun writeStatus(context: Context, extra: String) {
        val state = PolicyModeCoordinator.state.value
        val text = buildString {
            appendLine("mode=${state.mode}")
            appendLine("loaded=${state.loaded}")
            appendLine("sessionState=${state.sessionState}")
            appendLine("guardState=${state.guardState}")
            appendLine("remainingSeconds=${state.remainingSeconds}")
            appendLine("dailyUsedSeconds=${state.dailyUsedSeconds}")
            appendLine("dailyLimitSeconds=${state.dailyLimitSeconds}")
            appendLine("nextWindow=${state.nextWindowLabel}")
            appendLine("restRemainingSeconds=${state.restRemainingSeconds}")
            appendLine("lockReason=${state.lockReason}")
            appendLine("pinConfigured=${state.pinConfigured}")
            appendLine("localRulesConfigured=${state.localRulesConfigured}")
            appendLine("setupRequired=${state.setupRequired}")
            appendLine("recoveryConfigured=${LocalPolicyStore.recoveryConfigured}")
            if (extra.isNotBlank()) appendLine(extra)
        }
        runCatching {
            context.openFileOutput("policy-status.txt", Context.MODE_PRIVATE)
                .use { it.write(text.toByteArray()) }
        }
    }

    companion object {
        const val ACTION_FORCE_NONE = "app.orchard.tv.policy.DEBUG_FORCE_NONE"
        const val ACTION_FORCE_LOCAL = "app.orchard.tv.policy.DEBUG_FORCE_LOCAL"
        const val ACTION_FORCE_CLOUD = "app.orchard.tv.policy.DEBUG_FORCE_CLOUD"
        const val ACTION_CLOUD_UNBOUND = "app.orchard.tv.policy.DEBUG_CLOUD_UNBOUND"
        const val ACTION_SETUP = "app.orchard.tv.policy.DEBUG_LOCAL_SETUP"
        const val ACTION_RULES = "app.orchard.tv.policy.DEBUG_LOCAL_RULES"
        const val ACTION_RELOCK = "app.orchard.tv.policy.DEBUG_LOCAL_RELOCK"
        const val ACTION_LOCAL_ACTIVE = "app.orchard.tv.policy.DEBUG_LOCAL_ACTIVE"
        const val ACTION_LOCAL_CLEAR_SESSION = "app.orchard.tv.policy.DEBUG_LOCAL_CLEAR_SESSION"
        const val ACTION_LOCAL_END_USER = "app.orchard.tv.policy.DEBUG_LOCAL_END_USER"
        const val ACTION_LOCAL_RESTORE_USAGE = "app.orchard.tv.policy.DEBUG_LOCAL_RESTORE_USAGE"
        const val ACTION_LOCAL_EXPIRE_REST = "app.orchard.tv.policy.DEBUG_LOCAL_EXPIRE_REST"
        const val ACTION_LOCAL_ACTIVATION = "app.orchard.tv.policy.DEBUG_LOCAL_ACTIVATION"
        const val ACTION_SETUP_DEFAULT = "app.orchard.tv.policy.DEBUG_LOCAL_SETUP_DEFAULT"
        const val ACTION_CLEAR = "app.orchard.tv.policy.DEBUG_LOCAL_CLEAR"
        const val ACTION_OPEN_PARENT = "app.orchard.tv.policy.DEBUG_OPEN_PARENT"
        const val ACTION_CLOSE_PARENT = "app.orchard.tv.policy.DEBUG_CLOSE_PARENT"
        const val ACTION_STATUS = "app.orchard.tv.policy.DEBUG_LOCAL_STATUS"
        const val EXTRA_START = "start"
        const val EXTRA_END = "end"
        const val EXTRA_DAILY = "daily"
        const val EXTRA_SESSION = "session"
        const val EXTRA_REST = "rest"
        const val EXTRA_PIN = "pin"
        const val EXTRA_SECONDS = "seconds"
        const val EXTRA_ACTIVATION = "activation"
    }
}
