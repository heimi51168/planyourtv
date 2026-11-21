@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.orchard.tv.ui.policy

import android.view.KeyEvent as NativeKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orchard.tv.R
import app.orchard.tv.policy.LocalPolicyRuntime
import app.orchard.tv.policy.PolicyInstalledApp
import app.orchard.tv.policy.PolicyInstalledApps
import app.orchard.tv.policy.PolicyModeCoordinator
import app.orchard.tv.policy.PolicyProduct
import app.orchard.tv.policy.SourceEntrySettings
import app.orchard.tv.quiz.QuizSettings
import app.orchard.tv.system.Sounds
import app.orchard.tv.ui.components.liquidGlass
import app.orchard.tv.ui.theme.Glyph
import app.orchard.tv.ui.theme.GlyphIcon
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type
import com.planyourtv.policy.ALL_VALID_APP
import com.planyourtv.policy.GuardStatus
import com.planyourtv.policy.LocalRules
import com.planyourtv.policy.LocalRulesError
import com.planyourtv.policy.LocalTimeWindow
import com.planyourtv.policy.PinResult
import com.planyourtv.policy.PolicyConsentStore
import com.planyourtv.policy.PolicyMode
import com.planyourtv.policy.PolicyRuntime
import com.planyourtv.policy.PolicyUiState
import com.planyourtv.policy.RemoteHandoffState
import com.planyourtv.policy.RemotePolicy
import com.planyourtv.policy.RemotePolicyMode
import com.planyourtv.policy.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PARENT_PIN_LENGTH = 6
private const val CONFIRM_TIMEOUT_MS = 6_000L
private const val REMOVE_TIMEOUT_MS = 3_000L

// 2026-11-12: 远程管理随扫码绑定一起放出。绑定后答题配置完全以云端为准
// （题库孩子级配置 + TV 关卡），本机只保留断网时的 active 文档执行能力。
// 2026-11-15: 远程功能尚未就绪，临时隐藏「远程管理」入口。
// 2026-11-19: 恢复入口，继续扫码绑定对接；绑定、迁移和摘要页代码保持完整。
// 2026-11-21: 家长中心保留，仅隐藏其中的「远程管理」入口；绑定、迁移和摘要页代码保持完整。
// 测试通过后把这里改回 true 即可放出。
private const val REMOTE_MANAGEMENT_ENABLED = false

// The sheet's inner width is 864 dp: the rail, its gap and the pane must add up to it.
private val ParentRailWidth = 280.dp
private val ParentColumnGap = 20.dp
private val ParentPaneWidth = 564.dp

// Parent-center editor range. The rule engine accepts wider ranges so that cloud rules stay
// valid, but the TV only edits the rest interval within these steps.
internal const val MAX_EDIT_REST_MINUTES = 60

private sealed interface ArmedAction {
    data object Disable : ArmedAction
    data object EndSession : ArmedAction
}

private class VerifyRequest(
    val message: String,
    val returnTarget: ParentFocusTarget,
    val onVerified: (String) -> Unit,
)

/**
 * V6 parent center.
 *
 * The left rail is the only place that owns the child-mode switch. The right pane is a
 * read-only cloud summary once the phone is bound; before binding it is the local rule editor.
 * All focus movement is logical, so the d-pad can never jump between columns on its own.
 */
@Composable
fun PolicyParentCenter(onClose: () -> Unit) {
    val state by PolicyModeCoordinator.state.collectAsState()
    val cloudState by PolicyRuntime.state.collectAsState()
    val remoteState by PolicyRuntime.remotePolicy.collectAsState()
    val remote = remoteState

    val initialGuarded = remember { state.mode != PolicyMode.NONE }
    // The lock screen's temporary unlock already proved the parent's PIN; share that proof so
    // opening the parent center during the same unlock does not ask for it again. Screen-off
    // and guard resume clear the coordinator's flag, which re-enables the gate below.
    val sharedParentAuth by PolicyModeCoordinator.parentAuthenticated.collectAsState()
    var authenticated by remember { mutableStateOf(!initialGuarded || sharedParentAuth) }
    var authPin by remember { mutableStateOf("") }
    var pinVerified by remember { mutableStateOf(false) }
    LaunchedEffect(sharedParentAuth) {
        if (!sharedParentAuth) return@LaunchedEffect
        // The lock-screen unlock already proved this PIN. Seed the same state that
        // verifyGate() sets, so disable/handoff do not ask for the same PIN twice. When the
        // shared proof ends we keep this screen's own verification: making the parent type the
        // same PIN again while they are still in the parent center is exactly the interruption
        // this proof exists to avoid.
        authenticated = true
        val sharedPin = PolicyModeCoordinator.sharedParentPin()
        if (sharedPin != null) {
            authPin = sharedPin
            pinVerified = true
        }
    }
    var enteredPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<Int?>(null) }
    var verifyingPin by remember { mutableStateOf(false) }
    var recoveryMode by remember { mutableStateOf(false) }
    val pinGateFocus = remember { FocusRequester() }

    var rules by remember {
        mutableStateOf(LocalPolicyRuntime.currentRules ?: LocalRules.DEFAULTS)
    }
    var quizEntryVisible by remember { mutableStateOf(QuizSettings.isLockEntryVisible()) }
    var quizChanging by remember { mutableStateOf(false) }
    var sourceEntriesVisible by remember { mutableStateOf(SourceEntrySettings.isVisible()) }
    var savedNotice by remember { mutableStateOf<Int?>(null) }
    var actionError by remember { mutableStateOf<Int?>(null) }
    var handoffError by remember { mutableStateOf<Int?>(null) }
    var handoffBusy by remember { mutableStateOf(false) }
    // First local enable: choose the parent PIN before the guard is switched on.
    var showInitialPin by remember { mutableStateOf(false) }
    var cloudConsentAccepted by remember {
        mutableStateOf(PolicyConsentStore.cloudUploadAccepted())
    }
    var showCloudConsent by remember { mutableStateOf(false) }
    // The consent is shown before the QR for a new binding, but a TV bound before this build
    // can still have a pending upload. In that case the same notice gates the retry instead.
    var consentForHandoff by remember { mutableStateOf(false) }
    var verifyRequest by remember { mutableStateOf<VerifyRequest?>(null) }
    // The PIN prompt is drawn in this window and has no focusable of its own; the root key
    // catcher below routes every press to the open prompt through this router.
    val dialogKeyRouter = remember { PolicyDialogKeyRouter() }
    var armed by remember { mutableStateOf<ArmedAction?>(null) }
    var armedJob by remember { mutableStateOf<Job?>(null) }
    var armedRemoveKey by remember { mutableStateOf<String?>(null) }
    var armedRemoveJob by remember { mutableStateOf<Job?>(null) }
    var editingKey by remember { mutableStateOf<String?>(null) }
    var savedJob by remember { mutableStateOf<Job?>(null) }
    var switching by remember { mutableStateOf(false) }
    var retrying by remember { mutableStateOf(false) }
    var paneKind by remember { mutableStateOf(ParentPaneKind.OFF) }
    var activeZone by remember { mutableStateOf(ParentZone.RAIL) }
    var railOrigin by remember { mutableStateOf(ParentRailOrigin.SWITCH) }
    var focusedTarget by remember { mutableStateOf<ParentFocusTarget?>(ParentFocusTarget.Switch) }
    var showAppPicker by remember { mutableStateOf(false) }
    var pickerStudy by remember { mutableStateOf(false) }
    var pickerApps by remember { mutableStateOf<List<PolicyInstalledApp>?>(null) }
    var pickerLoading by remember { mutableStateOf(false) }
    var pickerLoadFailed by remember { mutableStateOf(false) }
    var pickerIndex by remember { mutableStateOf(0) }
    var pickerMessage by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val pickerEntries = remember(pickerApps, rules, pickerStudy) {
        buildAppPickerEntries(pickerApps.orEmpty(), rules, pickerStudy)
    }

    val rootFocus = remember { FocusRequester() }
    val railSwitchFocus = remember { FocusRequester() }
    val railRemoteFocus = remember { FocusRequester() }
    val migrationRetryFocus = remember { FocusRequester() }
    val waitRetryFocus = remember { FocusRequester() }
    val summaryReadFocus = remember { FocusRequester() }
    val localFocus = remember { mutableMapOf<String, FocusRequester>() }
    val localBring = remember { mutableMapOf<String, BringIntoViewRequester>() }

    val childOn = state.mode != PolicyMode.NONE
    // A community build has no backend, so a stale local binding flag must not make the local
    // pane read-only or expose a remote pane that can never connect.
    val bound = PolicyProduct.cloudEnabled && cloudState.bound
    val handoff = remote?.handoffState
    val handoffDone = bound && handoff == RemoteHandoffState.DONE
    val handoffPending = bound && handoff == RemoteHandoffState.PENDING
    // V29: remote management is a core row whenever the service is available. It leads to the
    // bind flow when unbound and to the read-only remote summary when bound.
    val remoteEntryVisible = REMOTE_MANAGEMENT_ENABLED && PolicyProduct.cloudEnabled
    val unboundCloud = PolicyProduct.cloudEnabled && !bound && state.mode == PolicyMode.CLOUD
    val localSessionActive = state.mode == PolicyMode.LOCAL && state.sessionState == SessionState.ACTIVE
    // Once the TV is bound, the local pane stays the familiar rule view but becomes read-only;
    // edits are made on the phone. After the handoff the pane mirrors the cloud copy so a phone
    // edit is visible without reopening the center.
    val readOnlyLocal = bound
    // A DONE handoff means the remote node exists, so its rule copy is what the pane mirrors.
    val displayRemoteRules = readOnlyLocal && handoff == RemoteHandoffState.DONE
    val displayedRules = if (displayRemoteRules) remote?.rules ?: rules else rules

    fun localRequester(key: String): FocusRequester =
        localFocus.getOrPut(key) { FocusRequester() }

    fun localBringRequester(key: String): BringIntoViewRequester =
        localBring.getOrPut(key) { BringIntoViewRequester() }

    fun cancelArmed() {
        armedJob?.cancel()
        armedJob = null
        armed = null
    }

    fun cancelRemoveConfirm() {
        armedRemoveJob?.cancel()
        armedRemoveJob = null
        armedRemoveKey = null
    }

    fun cancelInlineConfirm() {
        cancelArmed()
        cancelRemoveConfirm()
    }

    fun armDisable() {
        cancelArmed()
        armed = ArmedAction.Disable
        armedJob = scope.launch {
            delay(CONFIRM_TIMEOUT_MS)
            if (armed == ArmedAction.Disable) armed = null
        }
    }

    fun armEndSession() {
        cancelArmed()
        armed = ArmedAction.EndSession
        armedJob = scope.launch {
            delay(CONFIRM_TIMEOUT_MS)
            if (armed == ArmedAction.EndSession) armed = null
        }
    }

    fun armRemove(key: String) {
        cancelRemoveConfirm()
        armedRemoveKey = key
        armedRemoveJob = scope.launch {
            delay(REMOVE_TIMEOUT_MS)
            if (armedRemoveKey == key) armedRemoveKey = null
        }
    }

    fun focusNow(target: ParentFocusTarget) {
        cancelInlineConfirm()
        val previous = focusedTarget
        if (previous != target) Sounds.focusMoved()
        if (previous is ParentFocusTarget.LocalRow &&
            (target !is ParentFocusTarget.LocalRow || target.key != previous.key)
        ) {
            editingKey = null
        }
        focusedTarget = target
        activeZone = when (target) {
            ParentFocusTarget.Switch,
            ParentFocusTarget.Remote,
            -> ParentZone.RAIL
            else -> ParentZone.PANE
        }
        if (target is ParentFocusTarget.LocalRow) {
            scope.launch {
                runCatching { localBringRequester(target.key).bringIntoView() }
            }
        }
        // The root owns key input. Re-assert it after recomposition; a child row must never
        // steal focus from the overlay and send the next d-pad press behind it.
        scope.launch {
            withFrameNanos { }
            runCatching { rootFocus.requestFocus() }
        }
    }

    fun showSaved(message: Int) {
        savedJob?.cancel()
        savedNotice = message
        savedJob = scope.launch {
            delay(2_000)
            savedNotice = null
        }
    }

    fun paneTargetsFor(pane: ParentPaneKind): List<ParentFocusTarget> = when (pane) {
        ParentPaneKind.OFF -> emptyList()
        ParentPaneKind.LOCAL -> v6LocalFocusKeys(
            rules = displayedRules,
            sessionActive = localSessionActive,
            readOnly = readOnlyLocal,
        ).map { ParentFocusTarget.LocalRow(it) }
        // The bind and quiz pages are display-only and keep their rail row focused.
        ParentPaneKind.BIND -> emptyList()
        ParentPaneKind.PARENT_WEB_QR -> emptyList()
        ParentPaneKind.MIGRATION -> listOf(ParentFocusTarget.MigrationRetry)
        ParentPaneKind.WAIT_CONFIG -> listOf(ParentFocusTarget.WaitConfigRetry)
        ParentPaneKind.SUMMARY -> listOf(ParentFocusTarget.SummaryRead)
    }

    fun remotePaneKind(): ParentPaneKind = when {
        !bound -> ParentPaneKind.BIND
        remote == null -> ParentPaneKind.WAIT_CONFIG
        remote.handoffState == RemoteHandoffState.DONE -> ParentPaneKind.SUMMARY
        // 2026-11-19: binding no longer uploads local rules, so PENDING is not a migration
        // this TV can finish. Show the bound summary instead of the legacy upload page.
        remote.handoffState == RemoteHandoffState.PENDING &&
            PolicyModeCoordinator.LOCAL_RULES_UPLOAD_ENABLED -> ParentPaneKind.MIGRATION
        else -> ParentPaneKind.SUMMARY
    }

    fun defaultPaneKind(): ParentPaneKind = when {
        !childOn -> ParentPaneKind.OFF
        bound || state.mode == PolicyMode.CLOUD -> remotePaneKind()
        else -> ParentPaneKind.PARENT_WEB_QR
    }

    fun railTargetsNow(): List<ParentFocusTarget> = parentRailTargets(
        childOn = childOn,
        remoteVisible = remoteEntryVisible,
    )

    fun enterPane(pane: ParentPaneKind, origin: ParentRailOrigin) {
        if (!childOn || pane == ParentPaneKind.OFF) {
            focusNow(ParentFocusTarget.Switch)
            return
        }
        paneKind = pane
        railOrigin = origin
        actionError = null
        savedNotice = null
        editingKey = null
        val first = paneTargetsFor(pane).firstOrNull()
        if (first != null) {
            focusNow(first)
        } else {
            // A display-only pane (the bind or quiz QR page) has no row to focus. Keep the
            // rail row that opened it highlighted instead of jumping to the switch.
            focusNow(
                when (origin) {
                    ParentRailOrigin.REMOTE -> ParentFocusTarget.Remote
                    ParentRailOrigin.SWITCH -> ParentFocusTarget.Switch
                },
            )
        }
    }

    fun enterRemotePane() {
        // Kept wired for the release; with remote management hidden no row can reach it.
        if (!REMOTE_MANAGEMENT_ENABLED || !childOn || !PolicyProduct.cloudEnabled) {
            focusNow(ParentFocusTarget.Switch)
            return
        }
        if (bound) {
            // 2026-11-19: a bound TV never uploads local config, so there is no pending
            // handoff notice to show here. Open the phone-managed summary directly.
            enterPane(remotePaneKind(), ParentRailOrigin.REMOTE)
            return
        }
        // The consent is about remote management reports, so it is only needed before binding.
        if (!cloudConsentAccepted) {
            cancelInlineConfirm()
            editingKey = null
            consentForHandoff = false
            showCloudConsent = true
            return
        }
        enterPane(remotePaneKind(), ParentRailOrigin.REMOTE)
    }

    fun enterPaneFromSwitch() {
        if (!childOn) return
        enterPane(defaultPaneKind(), ParentRailOrigin.SWITCH)
    }

    fun enterParentWebPane() {
        if (!childOn) {
            focusNow(ParentFocusTarget.Switch)
            return
        }
        enterPane(ParentPaneKind.PARENT_WEB_QR, ParentRailOrigin.SWITCH)
    }

    fun focusRailFromPane() {
        val targets = railTargetsNow()
        val target = when {
            railOrigin == ParentRailOrigin.REMOTE && ParentFocusTarget.Remote in targets ->
                ParentFocusTarget.Remote
            else -> ParentFocusTarget.Switch
        }
        focusNow(target)
    }

    fun switchTo(target: PolicyMode, pin: String) {
        if (switching) return
        switching = true
        actionError = null
        savedNotice = null
        scope.launch {
            val result = PolicyModeCoordinator.requestModeSwitch(target, pin)
            switching = false
            when (result) {
                PolicyModeCoordinator.SwitchResult.Success -> {
                    handoffError = null
                    paneKind = when (target) {
                        PolicyMode.NONE -> ParentPaneKind.OFF
                        PolicyMode.LOCAL -> ParentPaneKind.PARENT_WEB_QR
                        PolicyMode.CLOUD -> if (REMOTE_MANAGEMENT_ENABLED) {
                            remotePaneKind()
                        } else {
                            ParentPaneKind.PARENT_WEB_QR
                        }
                    }
                    focusNow(ParentFocusTarget.Switch)
                }
                is PolicyModeCoordinator.SwitchResult.WrongPin -> {
                    pinVerified = false
                    authPin = ""
                    actionError = R.string.policy_wrong_pin
                    focusNow(ParentFocusTarget.Switch)
                }
                PolicyModeCoordinator.SwitchResult.PendingMustBeCanceled -> {
                    actionError = R.string.policy_switch_pending
                    focusNow(ParentFocusTarget.Switch)
                }
                is PolicyModeCoordinator.SwitchResult.CloudSettlementFailed -> {
                    actionError = R.string.policy_switch_need_network
                    focusNow(ParentFocusTarget.Switch)
                }
                PolicyModeCoordinator.SwitchResult.NotReady -> {
                    actionError = R.string.policy_switch_failed
                    focusNow(ParentFocusTarget.Switch)
                }
            }
        }
    }

    fun startChildMode() {
        if (switching) return
        val target = if (bound && PolicyProduct.cloudEnabled) PolicyMode.CLOUD else PolicyMode.LOCAL
        if (target != PolicyMode.LOCAL) {
            switchTo(target, "")
            return
        }
        // Local mode cannot be turned off without a parent PIN. Check for one before the switch
        // changes, so a fresh install never starts on a known default. The check verifies a
        // stored legacy default with PBKDF2, so keep it off the main thread.
        switching = true
        scope.launch {
            val needsPin = withContext(Dispatchers.Default) {
                runCatching { PolicyModeCoordinator.needsLocalPinSetup() }.getOrDefault(true)
            }
            switching = false
            if (needsPin) showInitialPin = true else switchTo(PolicyMode.LOCAL, "")
        }
    }

    fun enableChild() {
        if (switching) return
        startChildMode()
    }

    fun requestDisable() {
        if (switching) return
        // Only ask when this center has no verified PIN yet; a parent who just unlocked or
        // passed the gate can turn protection off directly. The upload warning only applies
        // when the legacy handoff path is enabled.
        if (!pinVerified || authPin.length != PARENT_PIN_LENGTH) {
            verifyRequest = VerifyRequest(
                message = context.getString(
                    if (handoffPending && PolicyModeCoordinator.LOCAL_RULES_UPLOAD_ENABLED) {
                        R.string.policy_v6_switch_off_migrating
                    } else {
                        R.string.policy_parent_pin_to_disable
                    },
                ),
                returnTarget = ParentFocusTarget.Switch,
            ) { pin ->
                authPin = pin
                pinVerified = true
                switchTo(PolicyMode.NONE, pin)
            }
            return
        }
        switchTo(PolicyMode.NONE, authPin)
    }

    // WAIT_CONFIG retry: report a failed fetch instead of leaving the screen unchanged, so a
    // second OK does not look like a no-op.
    fun retryRemoteConfig() {
        if (retrying) return
        retrying = true
        actionError = null
        savedNotice = null
        scope.launch {
            val loaded = PolicyModeCoordinator.refreshRemotePolicyNow()
            retrying = false
            if (!loaded) {
                actionError = R.string.policy_v6_retry_failed
            }
        }
    }

    fun applyRules(
        next: LocalRules,
        focusAfter: String? = null,
        appScopeChange: Boolean = false,
    ): Boolean {
        // A bound TV takes its rules from the cloud; nothing in the local pane may write a
        // second copy behind the phone's back.
        if (readOnlyLocal) return false
        val error = next.validationError()
        if (error != LocalRulesError.NONE) {
            actionError = when (error) {
                LocalRulesError.OVERLAPPING_WINDOWS -> R.string.policy_parent_rules_overlap
                else -> R.string.policy_parent_rules_invalid
            }
            return false
        }
        if (!LocalPolicyRuntime.setRules(next)) {
            actionError = R.string.policy_parent_rules_invalid
            return false
        }
        rules = next
        actionError = null
        showSaved(
            when {
                appScopeChange -> R.string.policy_parent_apps_saved
                state.mode != PolicyMode.LOCAL -> R.string.policy_parent_saved_inactive
                state.sessionState == SessionState.ACTIVE -> R.string.policy_parent_saved_next
                else -> R.string.policy_parent_saved
            },
        )
        if (focusAfter != null) {
            focusNow(ParentFocusTarget.LocalRow(focusAfter))
        }
        return true
    }

    fun adjustWindow(weekday: Boolean, index: Int, start: Boolean, delta: Int) {
        val windows = if (weekday) rules.weekdayWindows else rules.weekendWindows
        val window = windows.getOrNull(index) ?: return
        val changed = if (start) {
            val lower = windows.getOrNull(index - 1)?.endMinute ?: 0
            val upper = window.endMinute - 15
            if (lower > upper) return
            window.copy(startMinute = (window.startMinute + delta).coerceIn(lower, upper))
        } else {
            val lower = window.startMinute + 15
            val upper = windows.getOrNull(index + 1)?.startMinute ?: 1440
            if (lower > upper) return
            window.copy(endMinute = (window.endMinute + delta).coerceIn(lower, upper))
        }
        if (changed == window) return
        val nextWindows = windows.toMutableList().also { it[index] = changed }
        applyRules(rules.withEntertainmentWindowsForDay(if (weekday) 1 else 6, nextWindows))
    }

    fun removeWindow(weekday: Boolean, index: Int) {
        val windows = if (weekday) rules.weekdayWindows else rules.weekendWindows
        if (windows.size <= 1 || index !in windows.indices) return
        val next = windows.filterIndexed { i, _ -> i != index }
        editingKey = null
        applyRules(
            rules.withEntertainmentWindowsForDay(if (weekday) 1 else 6, next),
            focusAfter = "${if (weekday) "wd" else "we"}-0-start",
        )
    }

    fun adjustRest(delta: Int) {
        val current = rules.restMinutes
        val next = (current + delta).coerceIn(0, maxOf(MAX_EDIT_REST_MINUTES, current))
        if (next != current) {
            applyRules(rules.copy(restMinutes = next))
        }
    }

    fun adjustBuffer(delta: Int) {
        val current = rules.sessionScreenOffBufferMinutes
        val next = (current + delta).coerceIn(
            LocalRules.MIN_SESSION_BUFFER_MINUTES,
            LocalRules.MAX_SESSION_BUFFER_MINUTES,
        )
        if (next != current) {
            applyRules(rules.copy(sessionScreenOffBufferMinutes = next))
        }
    }

    fun adjustLocalByKey(key: String, delta: Int) {
        when {
            key.endsWith("-start") || key.endsWith("-end") -> {
                val parts = key.split("-")
                val weekday = parts.firstOrNull() == "wd"
                val index = parts.getOrNull(1)?.toIntOrNull() ?: return
                val start = parts.getOrNull(2) == "start"
                adjustWindow(weekday, index, start, delta)
            }
            key == "rest" -> adjustRest(delta)
            key == "buffer" -> adjustBuffer(delta)
        }
    }

    fun toggleSourceEntries() {
        // Presentation only, like the quiz switch: the read-only pane still changes on the phone.
        if (readOnlyLocal) return
        val next = !sourceEntriesVisible
        if (!SourceEntrySettings.setVisible(next)) {
            actionError = R.string.policy_v6_save_failed
            return
        }
        sourceEntriesVisible = next
        showSaved(R.string.policy_v6_source_entries_saved)
    }

    fun toggleQuizEntry() {
        // Display only: this never enables, disables or deletes a challenge. The read-only
        // pane keeps its promise that a bound TV changes settings on the phone.
        if (quizChanging || readOnlyLocal) return
        val next = !quizEntryVisible
        quizChanging = true
        actionError = null
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching { QuizSettings.setLockEntryVisible(next) }.isSuccess
            }
            quizChanging = false
            if (saved) {
                quizEntryVisible = next
                showSaved(R.string.policy_parent_saved)
            } else {
                actionError = R.string.policy_v6_save_failed
            }
        }
    }

    fun refreshPickerApps() {
        if (pickerLoading) return
        pickerLoading = true
        pickerLoadFailed = false
        scope.launch {
            val loaded = runCatching { PolicyInstalledApps.loadForPicker(context) }.getOrNull()
            pickerLoading = false
            if (loaded == null) {
                pickerLoadFailed = true
            } else {
                pickerApps = loaded
                pickerLoadFailed = false
            }
        }
    }

    fun openAppPicker(study: Boolean = false) {
        cancelInlineConfirm()
        editingKey = null
        actionError = null
        savedNotice = null
        pickerMessage = null
        pickerStudy = study
        showAppPicker = true
        pickerIndex = appPickerInitialIndex(
            buildAppPickerEntries(pickerApps.orEmpty(), rules, study),
            rules,
            study,
        )
        refreshPickerApps()
    }

    fun closeAppPicker() {
        if (!showAppPicker) return
        val returnKey = if (pickerStudy) "study" else "apps"
        showAppPicker = false
        pickerMessage = null
        pickerStudy = false
        focusNow(ParentFocusTarget.LocalRow(returnKey))
    }

    fun togglePickerEntry(index: Int) {
        val entry = pickerEntries.getOrNull(index) ?: return
        pickerIndex = index
        pickerMessage = null
        if (pickerStudy) {
            if (entry !is AppPickerEntry.App) return
            val current = rules.effectiveStudyPackages
            val adding = entry.packageName !in current
            val nextStudy = if (adding) {
                current + entry.packageName
            } else {
                current - entry.packageName
            }
            // Study wins over entertainment. Adding a study app removes it from the explicit
            // entertainment list; the all-apps sentinel stays, and the executor excludes study
            // packages from the timed scope anyway.
            val nextAllowed = if (adding && entry.packageName in rules.effectiveAllowedPackages) {
                rules.effectiveAllowedPackages - entry.packageName
            } else {
                rules.effectiveAllowedPackages
            }
            if (!applyRules(
                    rules.copy(studyPackages = nextStudy, allowedPackages = nextAllowed),
                    appScopeChange = true,
                )
            ) {
                pickerMessage = R.string.policy_parent_rules_invalid
            } else {
                showSaved(R.string.policy_v6_study_saved)
            }
            return
        }
        when (entry) {
            AppPickerEntry.AllApps -> {
                // Schema 2 reads an empty list as "no entertainment apps". Toggling the selected
                // All apps tile off is therefore an explicit way to reach zero; toggling it on
                // restores the sentinel.
                val next = if (!rules.usesAppSelection) emptySet() else setOf(ALL_VALID_APP)
                if (!applyRules(rules.copy(allowedPackages = next), appScopeChange = true)) {
                    pickerMessage = R.string.policy_parent_rules_invalid
                }
            }
            is AppPickerEntry.App -> {
                val current = if (rules.usesAppSelection) rules.effectiveAllowedPackages else emptySet()
                val adding = entry.packageName !in current
                if (adding && entry.packageName in rules.effectiveStudyPackages) {
                    pickerMessage = R.string.policy_v6_apps_in_study
                    return
                }
                val next = if (adding) {
                    current + entry.packageName
                } else {
                    current - entry.packageName
                }
                // An explicit empty entertainment list is valid in schema 2: the child keeps
                // only study apps and parent temporary unlock.
                if (!applyRules(rules.copy(allowedPackages = next), appScopeChange = true)) {
                    pickerMessage = R.string.policy_parent_rules_invalid
                }
            }
        }
    }

    fun activateLocalByKey(key: String) {
        if (readOnlyLocal) return
        when {
            key.endsWith("-remove") -> {
                val parts = key.split("-")
                val weekday = parts.firstOrNull() == "wd"
                val index = parts.getOrNull(1)?.toIntOrNull() ?: return
                if (armedRemoveKey == key) {
                    cancelRemoveConfirm()
                    removeWindow(weekday, index)
                } else {
                    armRemove(key)
                }
            }
            // The root key catcher routes OK here, so the row's own Pressable never sees it.
            key == "quiz" -> toggleQuizEntry()
            key == "sources" -> toggleSourceEntries()
            key == "apps" -> openAppPicker()
            key == "study" -> openAppPicker(study = true)
            key == "end-session" -> {
                if (armed == ArmedAction.EndSession) {
                    cancelArmed()
                    LocalPolicyRuntime.endSessionByParent()
                    focusNow(ParentFocusTarget.LocalRow("rest"))
                } else {
                    armEndSession()
                }
            }
            isV6ValueKey(key) -> {
                actionError = null
                editingKey = if (editingKey == key) null else key
            }
        }
    }

    fun verifyGate() {
        if (enteredPin.length != PARENT_PIN_LENGTH || verifyingPin) return
        val candidate = enteredPin
        verifyingPin = true
        pinError = null
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                PolicyModeCoordinator.verifyPin(candidate)
            }
            verifyingPin = false
            when (result) {
                PinResult.OK -> {
                    authenticated = true
                    authPin = candidate
                    pinVerified = true
                    enteredPin = ""
                    PolicyModeCoordinator.markParentVerified(candidate)
                }
                PinResult.LOCKED_OUT -> {
                    pinError = R.string.policy_locked_out
                    enteredPin = ""
                }
                else -> {
                    pinError = R.string.policy_wrong_pin
                    enteredPin = ""
                }
            }
        }
    }

    fun typePin(digit: Char) {
        if (verifyingPin || authenticated || enteredPin.length >= PARENT_PIN_LENGTH) return
        enteredPin += digit
        pinError = null
        if (enteredPin.length == PARENT_PIN_LENGTH) verifyGate()
    }

    fun completeHandoff(pin: String) {
        if (!PolicyModeCoordinator.LOCAL_RULES_UPLOAD_ENABLED) return
        if (handoffBusy) return
        handoffBusy = true
        handoffError = null
        scope.launch {
            val result = PolicyModeCoordinator.completeHandoff(pin)
            handoffBusy = false
            when (result) {
                PolicyModeCoordinator.HandoffResult.Success -> {
                    // Remote management now owns the policy. Land on its read-only summary.
                    handoffError = null
                    showSaved(R.string.policy_v6_upload_done)
                    paneKind = remotePaneKind()
                    railOrigin = ParentRailOrigin.REMOTE
                    focusNow(ParentFocusTarget.Remote)
                }
                PolicyModeCoordinator.HandoffResult.NotNeeded,
                PolicyModeCoordinator.HandoffResult.Busy,
                -> Unit
                else -> {
                    handoffError = handoffLabel(result)
                    if (paneKind == ParentPaneKind.MIGRATION) {
                        focusNow(ParentFocusTarget.MigrationRetry)
                    }
                }
            }
        }
    }

    fun requestHandoff() {
        if (!PolicyModeCoordinator.LOCAL_RULES_UPLOAD_ENABLED) return
        if (!handoffPending || handoffBusy) return
        handoffError = null
        // A TV bound before the consent existed can still be pending. Never upload its local
        // rules and password before the same one-time notice has been accepted.
        if (!cloudConsentAccepted) {
            consentForHandoff = true
            showCloudConsent = true
            return
        }
        // Remote management is gated by the privacy notice, not by the local parent PIN.
        // If this screen already has a verified PIN, hand it off so the phone keeps the same
        // password; otherwise upload the rules without touching the cloud password.
        completeHandoff(authPin)
    }

    // Read the prompt flags at event time, not from this composition's snapshot: the key that
    // opens a prompt sets the flag, and the next press can arrive before the recomposition that
    // composes it. A live read routes that press to the router, which buffers it for the prompt.
    fun anyDialogVisible(): Boolean =
        showInitialPin || showCloudConsent || verifyRequest != null
    val dialogOpen = anyDialogVisible()

    // Drop leftovers (and a stale handler) once every prompt is gone.
    LaunchedEffect(dialogOpen) {
        if (!dialogOpen) dialogKeyRouter.clear()
    }

    fun handleBackKey(): Boolean {
        if (showAppPicker) {
            closeAppPicker()
            return true
        }
        if (dialogOpen) return false
        when {
            armed != null -> cancelArmed()
            armedRemoveKey != null -> cancelRemoveConfirm()
            editingKey != null -> editingKey = null
            activeZone == ParentZone.PANE -> focusRailFromPane()
            // A QR page opened as a detour returns to the default pane first. When it already is
            // the default (the local-only parent center), Back leaves instead of re-selecting it,
            // so the key can always close the overlay.
            paneKind == ParentPaneKind.PARENT_WEB_QR && paneKind != defaultPaneKind() ->
                paneKind = defaultPaneKind()
            else -> onClose()
        }
        return true
    }

    if (!authenticated && recoveryMode) {
        BackHandler { recoveryMode = false }
        PolicyRecoveryScreen(
            onCancel = { recoveryMode = false },
            onDone = {
                recoveryMode = false
                enteredPin = ""
                pinError = null
            },
        )
        return
    }

    BackHandler(enabled = !authenticated) { onClose() }
    BackHandler(enabled = authenticated && !dialogOpen) { handleBackKey() }

    if (!authenticated) {
        ParentPinGate(
            entered = enteredPin,
            error = pinError,
            verifying = verifyingPin,
            firstFocus = pinGateFocus,
            onDigit = ::typePin,
            onErase = {
                if (enteredPin.isNotEmpty()) {
                    enteredPin = enteredPin.dropLast(1)
                    pinError = null
                }
            },
            onClose = onClose,
            onRecovery = { recoveryMode = true },
        )
        return
    }

    val cloudGuide = rememberCloudGuideState(
        deviceNo = cloudState.deviceNo,
        active = cloudConsentAccepted && childOn && paneKind == ParentPaneKind.BIND && !bound,
        onBound = {
            handoffError = null
            // Binding reports status only; wait for the first cloud snapshot (or its retry)
            // instead of showing the legacy local-upload pane.
            paneKind = if (PolicyModeCoordinator.LOCAL_RULES_UPLOAD_ENABLED) {
                ParentPaneKind.MIGRATION
            } else {
                ParentPaneKind.WAIT_CONFIG
            }
            railOrigin = ParentRailOrigin.REMOTE
            focusNow(ParentFocusTarget.Remote)
        },
    )

    // Rail focus follows the two core rows that actually exist.
    LaunchedEffect(
        authenticated,
        childOn,
        bound,
        handoffDone,
        focusedTarget,
        activeZone,
        paneKind,
        rules,
        displayedRules,
        readOnlyLocal,
        localSessionActive,
    ) {
        if (!authenticated) return@LaunchedEffect
        if (!childOn) {
            if (focusedTarget != ParentFocusTarget.Switch) {
                focusNow(ParentFocusTarget.Switch)
            }
            return@LaunchedEffect
        }
        val railTargets = railTargetsNow()
        val valid = if (activeZone == ParentZone.RAIL) {
            railTargets
        } else {
            paneTargetsFor(paneKind)
        }
        val target = focusedTarget
        if (target == null || target !in valid) {
            if (activeZone == ParentZone.PANE) {
                val first = paneTargetsFor(paneKind).firstOrNull()
                if (first != null) {
                    focusNow(first)
                } else {
                    focusNow(
                        if (railOrigin == ParentRailOrigin.REMOTE && ParentFocusTarget.Remote in railTargets) {
                            ParentFocusTarget.Remote
                        } else {
                            ParentFocusTarget.Switch
                        },
                    )
                }
            } else {
                focusNow(railTargets.firstOrNull() ?: ParentFocusTarget.Switch)
            }
        }
    }

    // One effect owns the right pane. The rail choice decides which view is shown, and the
    // remote context (remote-management row, or a remote pane such as bind/migration) keeps the
    // remote content; everywhere else the child-mode row shows the local rule view. That view
    // stays in place after binding and simply turns read-only -- it is not replaced by a second
    // summary page.
    LaunchedEffect(
        authenticated,
        activeZone,
        focusedTarget,
        childOn,
        bound,
        remote,
        state.mode,
        handoff,
        paneKind,
        railOrigin,
    ) {
        if (!authenticated) return@LaunchedEffect
        if (!childOn) {
            if (paneKind != ParentPaneKind.OFF) {
                paneKind = ParentPaneKind.OFF
                editingKey = null
                cancelInlineConfirm()
            }
            return@LaunchedEffect
        }
        val wantsRemote = (activeZone == ParentZone.RAIL &&
            focusedTarget == ParentFocusTarget.Remote) ||
            (activeZone == ParentZone.PANE && railOrigin == ParentRailOrigin.REMOTE)
        // Before consent there is nothing to remote-manage yet; merely moving the highlight
        // onto the remote row must not open the bind/QR pane behind the privacy notice.
        val remoteContext =
            REMOTE_MANAGEMENT_ENABLED && wantsRemote && (bound || cloudConsentAccepted)
        val next = when {
            remoteContext -> remotePaneKind()
            activeZone == ParentZone.RAIL && focusedTarget == ParentFocusTarget.Switch ->
                defaultPaneKind()
            bound || state.mode == PolicyMode.CLOUD -> remotePaneKind()
            else -> ParentPaneKind.PARENT_WEB_QR
        }
        if (next != paneKind) {
            paneKind = next
            editingKey = null
            cancelInlineConfirm()
        }
    }

    LaunchedEffect(authenticated) {
        if (authenticated) {
            focusNow(ParentFocusTarget.Switch)
        }
    }

    LaunchedEffect(showAppPicker, pickerEntries.size) {
        if (showAppPicker && pickerIndex !in pickerEntries.indices) {
            pickerIndex = pickerEntries.lastIndex.coerceAtLeast(0)
        }
    }

    // Legacy path only. 2026-11-19 binding reports device status without uploading local rules,
// so a freshly bound TV must not start the old migration here.
    LaunchedEffect(authenticated, bound, handoff, authPin, cloudConsentAccepted) {
        if (!PolicyModeCoordinator.LOCAL_RULES_UPLOAD_ENABLED) return@LaunchedEffect
        if (authenticated && bound && handoff == RemoteHandoffState.PENDING &&
            cloudConsentAccepted && !handoffBusy
        ) {
            completeHandoff(authPin)
        }
    }

    LaunchedEffect(authenticated, dialogOpen) {
        if (authenticated && !dialogOpen) {
            repeat(3) {
                withFrameNanos { }
                if (runCatching { rootFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            }
        }
    }

    // The Home/Control Center layer can request focus after the overlay opens. Keep the key
    // catcher focused so a d-pad press can never land behind the parent center, including while
    // a PIN prompt is up: that prompt is in this window and reads keys through this catcher.
    LaunchedEffect(authenticated) {
        if (!authenticated) return@LaunchedEffect
        while (true) {
            delay(500)
            runCatching { rootFocus.requestFocus() }
        }
    }

    fun activateFocused() {
        if (switching) return
        Sounds.select()
        when (val target = focusedTarget) {
            ParentFocusTarget.Switch -> {
                if (!childOn) {
                    enableChild()
                } else if (armed == ArmedAction.Disable) {
                    cancelArmed()
                    requestDisable()
                } else {
                    armDisable()
                }
            }
            ParentFocusTarget.Remote -> enterRemotePane()
            ParentFocusTarget.MigrationRetry -> requestHandoff()
            ParentFocusTarget.WaitConfigRetry -> retryRemoteConfig()
            ParentFocusTarget.SummaryRead -> Unit
            is ParentFocusTarget.LocalRow -> activateLocalByKey(target.key)
            null -> Unit
        }
    }

    fun handleRootKey(event: KeyEvent): Boolean {
        if (anyDialogVisible()) {
            // A modal prompt owns the remote. Consume everything here so a press can never fall
            // through to the controls behind the scrim, even on the frame the prompt appears.
            dialogKeyRouter.dispatch(event.nativeKeyEvent)
            return true
        }
        val code = event.nativeKeyEvent.keyCode
        val isUp = code == NativeKeyEvent.KEYCODE_DPAD_UP
        val isDown = code == NativeKeyEvent.KEYCODE_DPAD_DOWN
        val isLeft = code == NativeKeyEvent.KEYCODE_DPAD_LEFT
        val isRight = code == NativeKeyEvent.KEYCODE_DPAD_RIGHT
        val isSelect = code == NativeKeyEvent.KEYCODE_DPAD_CENTER ||
            code == NativeKeyEvent.KEYCODE_ENTER ||
            code == NativeKeyEvent.KEYCODE_NUMPAD_ENTER ||
            code == NativeKeyEvent.KEYCODE_BUTTON_A ||
            code == NativeKeyEvent.KEYCODE_BUTTON_SELECT
        val isBack = code == NativeKeyEvent.KEYCODE_BACK
        if (!(isUp || isDown || isLeft || isRight || isSelect || isBack)) return false
        if (event.type == KeyEventType.KeyUp) return true
        if (showAppPicker) {
            when {
                isBack -> closeAppPicker()
                isSelect -> if (event.nativeKeyEvent.repeatCount == 0) {
                    togglePickerEntry(pickerIndex)
                }
                isUp -> pickerIndex = moveAppPickerIndex(pickerIndex, pickerEntries.size, 0, -1)
                isDown -> pickerIndex = moveAppPickerIndex(pickerIndex, pickerEntries.size, 0, 1)
                isLeft -> pickerIndex = moveAppPickerIndex(pickerIndex, pickerEntries.size, -1, 0)
                isRight -> pickerIndex = moveAppPickerIndex(pickerIndex, pickerEntries.size, 1, 0)
            }
            return true
        }
        if (isBack) {
            handleBackKey()
            return true
        }
        if (isSelect) {
            if (event.nativeKeyEvent.repeatCount == 0) activateFocused()
            return true
        }

        // A remove/end-session row is an inline confirmation. While it is armed the d-pad does
        // not move; OK confirms and Left/Back cancels. The timeout clears it as well.
        if (armedRemoveKey != null || armed == ArmedAction.EndSession) {
            if (isLeft) cancelInlineConfirm()
            return true
        }

        val targets = if (activeZone == ParentZone.RAIL) {
            railTargetsNow()
        } else {
            paneTargetsFor(paneKind)
        }
        val target = focusedTarget
        if (isUp || isDown) {
            if (editingKey != null) editingKey = null
            if (targets.isEmpty()) return true
            val current = targets.indexOf(focusedTarget)
            when {
                current < 0 -> focusNow(targets[0])
                isUp && current > 0 -> focusNow(targets[current - 1])
                isDown && current < targets.lastIndex -> focusNow(targets[current + 1])
            }
            return true
        }

        if (isLeft) {
            if (activeZone == ParentZone.RAIL) return true
            if (editingKey != null && target is ParentFocusTarget.LocalRow) {
                if (editingKey == target.key) {
                    val step = v6ValueStep(target.key)
                    adjustLocalByKey(target.key, -step)
                }
                return true
            }
            focusRailFromPane()
            return true
        }

        // Right on the rail only opens the switch/remote/quiz panes. Inside a value row it
        // adjusts only while that row is in the explicit edit state.
        if (activeZone == ParentZone.RAIL) {
            when (target) {
                ParentFocusTarget.Switch -> enterPaneFromSwitch()
                ParentFocusTarget.Remote -> enterRemotePane()
                else -> Unit
            }
            return true
        }
        if (editingKey != null && target is ParentFocusTarget.LocalRow &&
            editingKey == target.key
        ) {
            adjustLocalByKey(target.key, v6ValueStep(target.key))
        }
        return true
    }

    val railTargets = railTargetsNow()
    fun railUp(target: ParentFocusTarget): (() -> Unit)? {
        val previous = railTargets.getOrNull(railTargets.indexOf(target) - 1) ?: return null
        return { focusNow(previous) }
    }
    fun railDown(target: ParentFocusTarget): (() -> Unit)? {
        val next = railTargets.getOrNull(railTargets.indexOf(target) + 1) ?: return null
        return { focusNow(next) }
    }

    val switchStatus = when {
        armed == ArmedAction.Disable -> stringResource(R.string.policy_v6_switch_off_confirm)
        state.guardState == GuardStatus.PARENT_OVERRIDE ->
            stringResource(R.string.policy_parent_child_mode_paused)
        !childOn -> stringResource(R.string.policy_v6_status_off)
        unboundCloud -> stringResource(R.string.policy_parent_child_mode_pending)
        !bound -> stringResource(R.string.policy_v6_status_local)
        handoff == RemoteHandoffState.PENDING -> stringResource(R.string.policy_v6_status_pending)
        remote == null -> stringResource(R.string.policy_v6_status_wait_config)
        handoff == RemoteHandoffState.DONE && remote.mode == RemotePolicyMode.RULE ->
            stringResource(R.string.policy_v6_status_rule)
        handoff == RemoteHandoffState.DONE && remote.mode == RemotePolicyMode.REDEEM ->
            stringResource(R.string.policy_v6_status_redeem)
        else -> stringResource(R.string.policy_v6_status_wait_config)
    }
    val remoteSubtitle = when {
        !bound -> stringResource(R.string.policy_v6_remote_unbound)
        handoff == RemoteHandoffState.PENDING ->
            if (PolicyModeCoordinator.LOCAL_RULES_UPLOAD_ENABLED) {
                stringResource(R.string.policy_v6_remote_pending)
            } else {
                // No local upload is pending; the phone already manages the cloud copy.
                stringResource(R.string.policy_v6_remote_done)
            }
        handoff == RemoteHandoffState.DONE -> stringResource(R.string.policy_v6_remote_done)
        else -> stringResource(R.string.policy_v6_status_wait_config)
    }
    val notice = actionError ?: savedNotice
    val noticeText = notice?.let { stringResource(it) }
    val noticeColor = if (actionError != null) Palette.Red else Palette.Green

    val paneTitle: String
    val paneStatus: String
    val paneStatusColor: Color
    val paneSummary: String
    val paneSummaryColor: Color
    when (paneKind) {
        ParentPaneKind.OFF -> {
            paneTitle = stringResource(R.string.policy_v6_off_title)
            paneStatus = stringResource(R.string.policy_v6_status_off)
            paneStatusColor = Palette.SecondaryLabel
            paneSummary = stringResource(R.string.policy_v6_off_body)
            paneSummaryColor = Palette.SecondaryLabel
        }
        ParentPaneKind.LOCAL -> {
            paneTitle = stringResource(R.string.policy_v6_local_title)
            paneStatus = when {
                readOnlyLocal && savedNotice != null ->
                    noticeText ?: stringResource(R.string.policy_v6_status_readonly)
                readOnlyLocal -> stringResource(R.string.policy_v6_status_readonly)
                actionError != null -> noticeText ?: stringResource(R.string.policy_v6_save_failed)
                savedNotice != null -> noticeText ?: stringResource(R.string.policy_parent_saved)
                else -> stringResource(R.string.policy_v6_status_local)
            }
            paneStatusColor = when {
                readOnlyLocal && savedNotice != null -> Palette.Green
                readOnlyLocal -> Palette.SecondaryLabel
                actionError != null -> Palette.Red
                else -> Palette.Green
            }
            paneSummary = when {
                readOnlyLocal -> stringResource(R.string.policy_v6_readonly_hint)
                editingKey != null -> stringResource(R.string.policy_v6_edit_hint)
                else -> stringResource(R.string.policy_v6_local_note)
            }
            paneSummaryColor = when {
                readOnlyLocal -> Palette.SecondaryLabel
                editingKey != null -> Palette.Orange
                else -> Palette.SecondaryLabel
            }
        }
        ParentPaneKind.BIND -> {
            paneTitle = stringResource(R.string.policy_bind_title)
            paneStatus = stringResource(R.string.policy_parent_unbound)
            paneStatusColor = Palette.Orange
            paneSummary = noticeText ?: ""
            paneSummaryColor = noticeColor
        }
        ParentPaneKind.MIGRATION -> {
            paneTitle = stringResource(R.string.policy_v6_migrate_title)
            paneStatus = stringResource(R.string.policy_v6_status_pending)
            paneStatusColor = Palette.Orange
            paneSummary = noticeText ?: ""
            paneSummaryColor = noticeColor
        }
        ParentPaneKind.WAIT_CONFIG -> {
            paneTitle = stringResource(R.string.policy_v6_wait_config)
            paneStatus = stringResource(R.string.policy_v6_status_wait_config)
            paneStatusColor = Palette.Orange
            paneSummary = noticeText ?: ""
            paneSummaryColor = noticeColor
        }
        ParentPaneKind.SUMMARY -> {
            val summaryRemote = remote
            paneTitle = stringResource(R.string.policy_v6_summary_title)
            val waiting = summaryRemote == null || summaryRemote.pendingApply
            paneStatus = if (waiting) {
                stringResource(R.string.policy_v6_wait_apply)
            } else {
                stringResource(R.string.policy_v6_summary_managed)
            }
            paneStatusColor = if (waiting) Palette.Orange else Palette.Green
            paneSummary = when {
                noticeText != null -> noticeText
                waiting -> stringResource(R.string.policy_v6_wait_apply_body)
                else -> ""
            }
            paneSummaryColor = if (notice != null) noticeColor else Palette.SecondaryLabel
        }
        ParentPaneKind.PARENT_WEB_QR -> {
            paneTitle = stringResource(R.string.policy_v29_qr_pane_title)
            paneStatus = stringResource(R.string.policy_v29_qr_pane_status)
            paneStatusColor = Palette.Green
            paneSummary = stringResource(R.string.policy_v29_qr_pane_summary)
            paneSummaryColor = Palette.SecondaryLabel
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event -> handleRootKey(event) },
        contentAlignment = Alignment.Center,
    ) {
        PolicyParentBackdrop()
        // The parent center is an overlay, so it wears the same Aerial glass sheet as the lock
        // screen instead of floating its rows directly on the backdrop. The taller local editor
        // scrolls inside the sheet as before.
        Row(
            modifier = Modifier
                .width(ParentSheetWidth)
                .height(ParentSheetHeight)
                .liquidGlass(ParentSheetCorner)
                .padding(horizontal = ParentSheetPaddingH, vertical = ParentSheetPaddingV),
            horizontalArrangement = Arrangement.spacedBy(ParentColumnGap),
        ) {
            Column(modifier = Modifier.width(ParentRailWidth).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(Glyph.Shield, Palette.Label, 24.dp)
                    Spacer(Modifier.width(10.dp))
                    BasicText(
                        stringResource(R.string.policy_parent_brand_title),
                        style = Type.Headline.copy(fontSize = 18.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(12.dp))

                PolicySwitchRow(
                    title = stringResource(R.string.policy_parent_child_mode),
                    status = switchStatus,
                    on = childOn,
                    focusRequester = railSwitchFocus,
                    armed = armed == ArmedAction.Disable,
                    highlighted = focusedTarget == ParentFocusTarget.Switch,
                    onClick = {
                        if (!childOn) {
                            enableChild()
                        } else if (armed == ArmedAction.Disable) {
                            cancelArmed()
                            requestDisable()
                        } else {
                            armDisable()
                        }
                    },
                    onUp = railUp(ParentFocusTarget.Switch),
                    onDown = railDown(ParentFocusTarget.Switch),
                    onRight = { enterPaneFromSwitch() },
                )

                if (childOn && remoteEntryVisible) {
                    Spacer(Modifier.height(12.dp))
                    V6RailRow(
                        title = stringResource(R.string.policy_v6_remote_manage),
                        subtitle = remoteSubtitle,
                        focusRequester = railRemoteFocus,
                        highlighted = focusedTarget == ParentFocusTarget.Remote,
                        onClick = ::enterRemotePane,
                        onUp = railUp(ParentFocusTarget.Remote),
                        onDown = railDown(ParentFocusTarget.Remote),
                        onRight = ::enterRemotePane,
                    )
                }

                Spacer(Modifier.weight(1f))
                BasicText(
                    stringResource(R.string.policy_v6_rail_hint),
                    style = Type.Caption.copy(fontSize = 11.sp, color = Palette.SecondaryLabel),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(4.dp))
            }

            Column(modifier = Modifier.width(ParentPaneWidth).fillMaxHeight()) {
                PaneHeader(
                    title = paneTitle,
                    status = paneStatus,
                    statusColor = paneStatusColor,
                    summary = paneSummary,
                    summaryColor = paneSummaryColor,
                )

                Spacer(Modifier.height(10.dp))

                Box(modifier = Modifier.weight(1f)) {
                    when (paneKind) {
                        ParentPaneKind.OFF -> Unit
                        ParentPaneKind.LOCAL -> V6LocalPane(
                            rules = displayedRules,
                            quizEntryVisible = quizEntryVisible,
                            sourceEntriesVisible = sourceEntriesVisible,
                            sessionActive = localSessionActive,
                            readOnly = readOnlyLocal,
                            editingKey = editingKey,
                            armedRemoveKey = armedRemoveKey,
                            endArmed = armed == ArmedAction.EndSession,
                            focusedTarget = focusedTarget,
                            focusFor = ::localRequester,
                            bringFor = ::localBringRequester,
                            onFocusRow = { key ->
                                focusNow(ParentFocusTarget.LocalRow(key))
                            },
                            onFocusRail = ::focusRailFromPane,
                            onActivateValue = { key -> activateLocalByKey(key) },
                            onAdjust = ::adjustLocalByKey,
                            onToggleQuizEntry = ::toggleQuizEntry,
                            onToggleSourceEntries = ::toggleSourceEntries,
                            onRemoveWindow = { weekday, index ->
                                val key = "${if (weekday) "wd" else "we"}-$index-remove"
                                if (armedRemoveKey == key) {
                                    cancelRemoveConfirm()
                                    removeWindow(weekday, index)
                                } else {
                                    armRemove(key)
                                }
                            },
                            onOpenApps = { openAppPicker(study = false) },
                            onOpenStudyApps = { openAppPicker(study = true) },
                            onEndSession = {
                                if (armed == ArmedAction.EndSession) {
                                    cancelArmed()
                                    LocalPolicyRuntime.endSessionByParent()
                                    focusNow(ParentFocusTarget.LocalRow("rest"))
                                } else {
                                    armEndSession()
                                }
                            },
                        )
                        ParentPaneKind.BIND -> V6BindPane(
                            deviceNo = cloudState.deviceNo,
                            guide = cloudGuide,
                        )
                        ParentPaneKind.PARENT_WEB_QR -> V6ParentWebQrPane()
                        ParentPaneKind.MIGRATION -> V6MigrationPane(
                            busy = handoffBusy,
                            error = handoffError,
                            focused = focusedTarget,
                            retryFocus = migrationRetryFocus,
                            onRetry = ::requestHandoff,
                            onFocusRail = ::focusRailFromPane,
                        )
                        ParentPaneKind.WAIT_CONFIG -> V6WaitConfigPane(
                            focused = focusedTarget,
                            retryFocus = waitRetryFocus,
                            onRetry = ::retryRemoteConfig,
                            onFocusRail = ::focusRailFromPane,
                        )
                        ParentPaneKind.SUMMARY -> {
                            val summaryRemote = remote
                            if (summaryRemote == null) {
                                V6WaitConfigPane(
                                    focused = focusedTarget,
                                    retryFocus = waitRetryFocus,
                                    onRetry = ::retryRemoteConfig,
                                    onFocusRail = ::focusRailFromPane,
                                )
                            } else {
                                V6SummaryPane(
                                    state = state,
                                    remote = summaryRemote,
                                    focused = focusedTarget,
                                    readFocus = summaryReadFocus,
                                    onFocusRail = ::focusRailFromPane,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showAppPicker) {
            V6AppPickerOverlay(
                rules = rules,
                entries = pickerEntries,
                loading = pickerLoading,
                loadFailed = pickerLoadFailed,
                focusedIndex = pickerIndex,
                message = pickerMessage,
                study = pickerStudy,
                onActivate = ::togglePickerEntry,
            )
        }

        if (showCloudConsent) {
            PolicyCloudConsentDialog(
                keyRouter = dialogKeyRouter,
                onAccept = {
                    PolicyConsentStore.acceptCloudUpload()
                    cloudConsentAccepted = true
                    showCloudConsent = false
                    if (consentForHandoff) {
                        consentForHandoff = false
                        requestHandoff()
                    } else {
                        enterRemotePane()
                    }
                },
                onDismiss = {
                    showCloudConsent = false
                    if (consentForHandoff) {
                        consentForHandoff = false
                        focusNow(
                            if (handoffPending) {
                                ParentFocusTarget.MigrationRetry
                            } else {
                                ParentFocusTarget.Switch
                            },
                        )
                    } else {
                        // Do not leave the remote row focused: the pane effect would open the
                        // bind/QR pane even though the parent just declined the upload.
                        focusNow(ParentFocusTarget.Switch)
                    }
                },
            )
        }
        if (showInitialPin) {
            PolicyInitialPinDialog(
                keyRouter = dialogKeyRouter,
                onDismiss = {
                    showInitialPin = false
                    focusNow(ParentFocusTarget.Switch)
                },
                onSave = { pin, recovery, result ->
                    scope.launch {
                        val code = withContext(Dispatchers.Default) {
                            PolicyModeCoordinator.completeLocalSetup(pin, recovery)
                        }
                        if (code == null) {
                            result(false)
                        } else {
                            result(true)
                            authPin = pin
                            pinVerified = true
                            showInitialPin = false
                            switchTo(PolicyMode.LOCAL, "")
                        }
                    }
                },
            )
        }
        verifyRequest?.let { request ->
            PolicyVerifyPinDialog(
                keyRouter = dialogKeyRouter,
                message = request.message,
                onDismiss = {
                    if (verifyRequest != null) {
                        verifyRequest = null
                        focusNow(request.returnTarget)
                    }
                },
                onVerified = { pin ->
                    verifyRequest = null
                    // Share this verification with every other parent action until the guard
                    // resumes or the TTL ends; do not ask for the same PIN again.
                    PolicyModeCoordinator.markParentVerified(pin)
                    request.onVerified(pin)
                },
            )
        }
    }
}

/** Left-rail row with a secondary line; the rail has no section tabs in V6. */
@Composable
private fun V6RailRow(
    title: String,
    subtitle: String,
    focusRequester: FocusRequester,
    highlighted: Boolean,
    onClick: () -> Unit,
    onUp: (() -> Unit)?,
    onDown: (() -> Unit)?,
    onRight: (() -> Unit)? = null,
) {
    PolicyFocusRow(
        focusRequester = focusRequester,
        onClick = onClick,
        onUp = onUp,
        onDown = onDown,
        onRight = onRight,
        normalBackground = Palette.Fill.copy(alpha = 0.55f),
        highlighted = highlighted,
    ) { plate ->
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                title,
                style = Type.Headline.copy(
                    fontSize = 15.sp,
                    color = if (plate) Palette.OnFocus else Palette.Label,
                ),
            )
            Spacer(Modifier.height(2.dp))
            BasicText(
                subtitle,
                style = Type.Caption.copy(
                    fontSize = 12.sp,
                    color = if (plate) {
                        Palette.OnFocus.copy(alpha = 0.72f)
                    } else {
                        Palette.SecondaryLabel
                    },
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicText(
            "›",
            style = Type.Headline.copy(
                fontSize = 16.sp,
                color = if (plate) Palette.OnFocus else Palette.SecondaryLabel,
            ),
        )
    }
}

@Composable
private fun PaneHeader(
    title: String,
    status: String,
    statusColor: Color,
    summary: String,
    summaryColor: Color,
) {
    Column(modifier = Modifier.heightIn(min = 54.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            BasicText(
                title,
                style = Type.Title.copy(fontSize = 21.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            PolicyStatusChip(status, statusColor)
        }
        Spacer(Modifier.height(4.dp))
        BasicText(
            summary,
            style = Type.Body.copy(fontSize = 14.sp, color = summaryColor),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ParentPinGate(
    entered: String,
    error: Int?,
    verifying: Boolean,
    firstFocus: FocusRequester,
    onDigit: (Char) -> Unit,
    onErase: () -> Unit,
    onClose: () -> Unit,
    onRecovery: () -> Unit,
) {
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (verifying || event.type != KeyEventType.KeyDown ||
                    event.nativeKeyEvent.repeatCount != 0
                ) {
                    return@onPreviewKeyEvent false
                }
                when (event.nativeKeyEvent.keyCode) {
                    in NativeKeyEvent.KEYCODE_0..NativeKeyEvent.KEYCODE_9 -> {
                        onDigit('0' + (event.nativeKeyEvent.keyCode - NativeKeyEvent.KEYCODE_0))
                        true
                    }
                    NativeKeyEvent.KEYCODE_DEL -> {
                        onErase()
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        PolicyParentBackdrop()
        // The gate is a modal, so it sits on a piece of Aerial glass like the other PIN prompts
        // rather than floating directly on the wallpaper.
        Column(
            modifier = Modifier
                .liquidGlass(ParentSheetCorner)
                .padding(horizontal = 36.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                stringResource(R.string.policy_parent_brand_title),
                style = Type.Caption.copy(color = Palette.SecondaryLabel),
            )
            Spacer(Modifier.height(8.dp))
            BasicText(stringResource(R.string.policy_parent_pin_prompt), style = Type.LargeTitle)
            Spacer(Modifier.height(12.dp))
            PolicyPinDots(entered.length, PARENT_PIN_LENGTH)
            Spacer(Modifier.height(8.dp))
            BasicText(
                error?.let { stringResource(it) } ?: " ",
                style = Type.Caption.copy(color = Palette.Red),
            )
            Spacer(Modifier.height(8.dp))
            PolicyDigitPad(
                onDigit = onDigit,
                onErase = onErase,
                firstFocus = firstFocus,
                leftKeyLabel = stringResource(R.string.policy_parent_close),
                onLeftKey = onClose,
            )
            // Close fills the slot left of 0 so the key block stays aligned; the recovery
            // code is the rarer escape and sits below the pad instead.
            Spacer(Modifier.height(10.dp))
            LockButton(
                label = stringResource(R.string.policy_recovery_key),
                onClick = onRecovery,
                width = 180.dp,
            )
        }
    }
}
