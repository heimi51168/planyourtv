package app.orchard.tv.ui.policy

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orchard.tv.R
import app.orchard.tv.policy.PolicyModeCoordinator
import app.orchard.tv.system.Sounds
import app.orchard.tv.ui.components.consumeTaps
import app.orchard.tv.ui.components.dismissOnTap
import app.orchard.tv.ui.components.liquidGlass
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type
import com.planyourtv.policy.Activation
import com.planyourtv.policy.LocalRecovery
import com.planyourtv.policy.PinResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DIALOG_PIN_LENGTH = 6

/** All PIN prompts share one frame so step changes cannot resize or move the glass panel. */
private val PIN_DIALOG_WIDTH = 400.dp
private val PIN_DIALOG_MIN_HEIGHT = 400.dp

/** Pad slots: 0-8 are "1".."9"; the bottom row is close, 0 and delete under them. */
private const val PAD_ZERO = 10
private const val PAD_DELETE = 11
private const val PAD_CLOSE = 12

private val CONFIRM_KEYS = setOf(
    NativeKeyEvent.KEYCODE_DPAD_CENTER,
    NativeKeyEvent.KEYCODE_ENTER,
    NativeKeyEvent.KEYCODE_NUMPAD_ENTER,
    NativeKeyEvent.KEYCODE_BUTTON_A,
    NativeKeyEvent.KEYCODE_BUTTON_SELECT,
)

private val DIALOG_KEYS = CONFIRM_KEYS + setOf(
    NativeKeyEvent.KEYCODE_DPAD_UP,
    NativeKeyEvent.KEYCODE_DPAD_DOWN,
    NativeKeyEvent.KEYCODE_DPAD_LEFT,
    NativeKeyEvent.KEYCODE_DPAD_RIGHT,
)

/**
 * Where a d-pad move lands on the PIN pad. The bottom row mirrors the columns above it: close
 * under 7, 0 under 8 and delete under 9. Every move that leaves the pad is clamped, so no
 * direction can strand focus on a hole.
 */
private fun movePadSelection(index: Int, keyCode: Int): Int = when (keyCode) {
    NativeKeyEvent.KEYCODE_DPAD_UP -> when {
        index in 3..8 -> index - 3
        index in 0..2 -> index
        index == PAD_CLOSE -> 6
        index == PAD_ZERO -> 7
        index == PAD_DELETE -> 8
        else -> index
    }
    NativeKeyEvent.KEYCODE_DPAD_DOWN -> when {
        index in 0..5 -> index + 3
        index == 6 -> PAD_CLOSE
        index == 7 -> PAD_ZERO
        index == 8 -> PAD_DELETE
        else -> index
    }
    NativeKeyEvent.KEYCODE_DPAD_LEFT -> when {
        index in 0..8 && index % 3 > 0 -> index - 1
        index == PAD_DELETE -> PAD_ZERO
        index == PAD_ZERO -> PAD_CLOSE
        else -> index
    }
    NativeKeyEvent.KEYCODE_DPAD_RIGHT -> when {
        index in 0..8 && index % 3 < 2 -> index + 1
        index == PAD_CLOSE -> PAD_ZERO
        index == PAD_ZERO -> PAD_DELETE
        else -> index
    }
    else -> index
}

/**
 * Shared modal frame, drawn inside the parent center's own window like Aerial's PinPad instead
 * of a separate Dialog window. A Dialog gets its own window, and the first remote presses after
 * it opened were sometimes delivered before that window settled, so the keypad felt dead for a
 * moment. Keys now travel through the parent center's always-focused root, which routes them
 * here; the PIN keys are drawn from a selection index, so the first highlight is part of the
 * very first frame and no focus traversal is involved.
 */
@Composable
private fun PolicyDialogRoot(
    maxWidth: Dp,
    keyRouter: PolicyDialogKeyRouter,
    onDismiss: () -> Unit,
    dismissible: () -> Boolean,
    onKey: (NativeKeyEvent) -> Boolean,
    fixedWidth: Boolean = false,
    minHeight: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val current by rememberUpdatedState(onKey)
    DisposableEffect(keyRouter) {
        val registration: (NativeKeyEvent) -> Boolean = { current(it) }
        keyRouter.attach(registration)
        // Only clear our own slot: a prompt that replaces another in the same frame must not
        // have its registration removed by the one that left.
        onDispose { keyRouter.detach(registration) }
    }
    val widthModifier =
        if (fixedWidth) Modifier.width(maxWidth) else Modifier.widthIn(max = maxWidth)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .dismissOnTap { if (dismissible()) onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .then(widthModifier)
                .heightIn(min = minHeight)
                .consumeTaps()
                .liquidGlass(24.dp)
                .padding(horizontal = 28.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

/**
 * Fixed frame shared by every PIN prompt. The dialog used to size itself to its content, so
 * hiding a description or switching to a shorter step made the glass panel shrink and re-center
 * between steps. Keep the outer frame stable; only the content inside changes.
 */
@Composable
private fun PolicyPinDialogRoot(
    keyRouter: PolicyDialogKeyRouter,
    onDismiss: () -> Unit,
    dismissible: () -> Boolean,
    onKey: (NativeKeyEvent) -> Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    PolicyDialogRoot(
        maxWidth = PIN_DIALOG_WIDTH,
        keyRouter = keyRouter,
        onDismiss = onDismiss,
        dismissible = dismissible,
        onKey = onKey,
        fixedWidth = true,
        minHeight = PIN_DIALOG_MIN_HEIGHT,
        content = content,
    )
}

/** Back closes a dialog unless a save is in flight. */
private fun handleBackKey(e: NativeKeyEvent, enabled: Boolean, onDismiss: () -> Unit): Boolean {
    if (e.keyCode != NativeKeyEvent.KEYCODE_BACK) return false
    if (e.action == NativeKeyEvent.ACTION_DOWN && e.repeatCount == 0 && enabled) onDismiss()
    return true
}

/**
 * Carries remote keys from the parent center's key catcher to the PIN prompt drawn in that same
 * window. The prompt has no focusable of its own, so the key catcher stays focused. Presses that
 * arrive between the state change that opened the prompt and its first composition (normally
 * under a frame, longer while the TV is busy) are kept and replayed, so a fast second press is
 * not swallowed.
 */
internal class PolicyDialogKeyRouter {
    private var handler: ((NativeKeyEvent) -> Boolean)? = null
    private val pending = mutableListOf<NativeKeyEvent>()

    fun dispatch(event: NativeKeyEvent): Boolean {
        val target = handler
        if (target == null) {
            pending += event
            return true
        }
        return target(event)
    }

    fun attach(target: (NativeKeyEvent) -> Boolean) {
        handler = target
        if (pending.isEmpty()) return
        val replay = pending.toList()
        pending.clear()
        replay.forEach { target(it) }
    }

    fun detach(target: (NativeKeyEvent) -> Boolean) {
        if (handler === target) handler = null
    }

    fun clear() {
        handler = null
        pending.clear()
    }
}

/** A stateless key/button that shows the selection from the index instead of the focus state. */
@Composable
private fun DialogTile(
    label: String,
    focused: Boolean,
    width: Dp,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val background = when {
        focused -> Palette.Focus
        primary -> Palette.FillStrong
        else -> Palette.Fill
    }
    Box(
        modifier = Modifier
            .width(width)
            .height(48.dp)
            .graphicsLayer {
                scaleX = if (focused) 1.05f else 1f
                scaleY = scaleX
            }
            .clip(RoundedCornerShape(13.dp))
            .background(background)
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            label,
            style = Type.Headline.copy(
                color = if (focused) Palette.OnFocus else Palette.Label,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

/**
 * Reserves a stable subtitle slot so switching steps or languages cannot move the PIN dots and
 * keypad. Two lines is the common height; longer translations may still grow the slot.
 */
@Composable
private fun DialogSubtitle(text: String, style: TextStyle, minHeight: Dp = 32.dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight),
        contentAlignment = Alignment.TopCenter,
    ) {
        BasicText(text, style = style)
    }
}

@Composable
private fun DialogPinPad(
    selected: Int,
    onDigit: (Char) -> Unit,
    onErase: () -> Unit,
    onClose: () -> Unit,
) {
    val rows = listOf("123", "456", "789")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { colIndex, digit ->
                    DialogTile(
                        label = digit.toString(),
                        focused = selected == rowIndex * 3 + colIndex,
                        width = 74.dp,
                        onClick = { onDigit(digit) },
                    )
                }
            }
        }
        // The bottom row fills every column of the grid: close under 7, 0 under 8 and delete
        // under 9. Nothing hangs below the pad, so the keys read as one aligned block.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DialogTile(
                label = stringResource(R.string.policy_parent_close),
                focused = selected == PAD_CLOSE,
                width = 74.dp,
                onClick = onClose,
            )
            DialogTile(
                label = "0",
                focused = selected == PAD_ZERO,
                width = 74.dp,
                onClick = { onDigit('0') },
            )
            DialogTile(
                label = stringResource(R.string.policy_delete),
                focused = selected == PAD_DELETE,
                width = 74.dp,
                onClick = onErase,
            )
        }
    }
}

/** True when this key is a d-pad/OK key the dialogs consume, so key-up never leaks behind. */
private fun NativeKeyEvent.isDialogKey(): Boolean = keyCode in DIALOG_KEYS

/**
 * Parent-center change-password flow. When the parent center already holds a verified PIN
 * (temporary unlock or this screen's gate), [knownOldPin] skips the redundant repeat and the
 * dialog starts at the new PIN. Without it the old PIN is still required.
 */
@Composable
internal fun PolicyChangePinDialog(
    keyRouter: PolicyDialogKeyRouter,
    onDismiss: () -> Unit,
    knownOldPin: String? = null,
    onSave: (oldPin: String, newPin: String, onResult: (Boolean) -> Unit) -> Unit,
) {
    val trustedOldPin = knownOldPin?.takeIf {
        it.length == DIALOG_PIN_LENGTH && it.all { ch -> ch in '0'..'9' }
    }
    var step by remember { mutableStateOf(if (trustedOldPin != null) 1 else 0) }
    var entered by remember { mutableStateOf("") }
    var oldPin by remember { mutableStateOf(trustedOldPin.orEmpty()) }
    var firstNew by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    fun type(digit: Char) {
        if (busy || entered.length >= DIALOG_PIN_LENGTH) return
        entered += digit
        error = null
        if (entered.length == DIALOG_PIN_LENGTH) {
            val candidate = entered
            entered = ""
            when (step) {
                0 -> {
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.Default) {
                            PolicyModeCoordinator.verifyPin(candidate)
                        }
                        busy = false
                        if (result == PinResult.OK) {
                            oldPin = candidate
                            selected = 0
                            step = 1
                        } else {
                            error = R.string.policy_wrong_pin
                        }
                    }
                }
                1 -> {
                    firstNew = candidate
                    selected = 0
                    step = 2
                }
                2 -> {
                    if (candidate != firstNew) {
                        error = R.string.policy_setup_pin_mismatch
                    } else {
                        busy = true
                        onSave(oldPin, candidate) { ok ->
                            busy = false
                            if (ok) onDismiss() else error = R.string.policy_wrong_pin
                        }
                    }
                }
            }
        }
    }

    fun erase() {
        if (!busy && entered.isNotEmpty()) {
            entered = entered.dropLast(1)
            error = null
        }
    }

    fun activate() {
        if (busy) return
        Sounds.select()
        when (selected) {
            in 0..8 -> type('1' + selected)
            PAD_ZERO -> type('0')
            PAD_DELETE -> erase()
            PAD_CLOSE -> onDismiss()
        }
    }

    fun handleKey(e: NativeKeyEvent): Boolean {
        if (handleBackKey(e, !busy, onDismiss)) return true
        if (e.keyCode in NativeKeyEvent.KEYCODE_0..NativeKeyEvent.KEYCODE_9) {
            if (e.action == NativeKeyEvent.ACTION_DOWN && e.repeatCount == 0) {
                type('0' + (e.keyCode - NativeKeyEvent.KEYCODE_0))
            }
            return true
        }
        if (e.keyCode == NativeKeyEvent.KEYCODE_DEL) {
            if (e.action == NativeKeyEvent.ACTION_DOWN && e.repeatCount == 0) erase()
            return true
        }
        if (e.action != NativeKeyEvent.ACTION_DOWN) return e.isDialogKey()
        return when (e.keyCode) {
            NativeKeyEvent.KEYCODE_DPAD_UP,
            NativeKeyEvent.KEYCODE_DPAD_DOWN,
            NativeKeyEvent.KEYCODE_DPAD_LEFT,
            NativeKeyEvent.KEYCODE_DPAD_RIGHT,
            -> {
                // Repeats count too: holding a direction must keep moving like focus traversal.
                val next = movePadSelection(selected, e.keyCode)
                if (next != selected) {
                    selected = next
                    Sounds.focusMoved()
                }
                true
            }
            in CONFIRM_KEYS -> {
                if (e.repeatCount == 0) activate()
                true
            }
            else -> false
        }
    }

    PolicyPinDialogRoot(
        keyRouter = keyRouter,
        onDismiss = onDismiss,
        dismissible = { !busy },
        onKey = ::handleKey,
    ) {
        BasicText(stringResource(R.string.policy_parent_change_pin), style = Type.Title)
        Spacer(Modifier.height(6.dp))
        DialogSubtitle(
            text = stringResource(
                when (step) {
                    0 -> R.string.policy_parent_old_pin
                    1 -> R.string.policy_parent_new_pin
                    else -> R.string.policy_setup_pin_again
                },
            ),
            style = Type.Body,
        )
        Spacer(Modifier.height(12.dp))
        PolicyPinDots(entered.length, DIALOG_PIN_LENGTH)
        Spacer(Modifier.height(6.dp))
        BasicText(
            error?.let { stringResource(it) } ?: " ",
            style = Type.Caption.copy(color = Palette.Red),
        )
        Spacer(Modifier.height(8.dp))
        DialogPinPad(selected, ::type, ::erase, onClose = { if (!busy) onDismiss() })
    }
}

/**
 * First-run local enable. The switch checks for a parent PIN first; this dialog asks for one
 * twice and shows the one-time recovery code before child mode is switched on. The interaction
 * mirrors [app.orchard.tv.ui.policy.PolicySetupScreen] so the PIN UI is the same on both paths.
 */
@Composable
internal fun PolicyInitialPinDialog(
    keyRouter: PolicyDialogKeyRouter,
    onDismiss: () -> Unit,
    onSave: (pin: String, recoveryCode: String, onResult: (Boolean) -> Unit) -> Unit,
) {
    var step by remember { mutableStateOf(0) } // 0 PIN, 1 confirm, 2 recovery
    var entered by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf("") }
    var recoveryCode by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(0) }

    fun advance(candidate: String) {
        when (step) {
            0 -> {
                if (PolicyModeCoordinator.isDefaultPinValue(candidate)) {
                    entered = ""
                    error = R.string.policy_setup_pin_default
                } else {
                    firstPin = candidate
                    entered = ""
                    error = null
                    selected = 0
                    step = 1
                }
            }
            1 -> {
                if (candidate != firstPin) {
                    entered = ""
                    error = R.string.policy_setup_pin_mismatch
                } else {
                    entered = ""
                    error = null
                    recoveryCode = LocalRecovery.newCode()
                    selected = PAD_CLOSE
                    step = 2
                }
            }
        }
    }

    fun type(digit: Char) {
        if (busy || step >= 2 || entered.length >= DIALOG_PIN_LENGTH) return
        entered += digit
        error = null
        if (entered.length == DIALOG_PIN_LENGTH) advance(entered)
    }

    fun erase() {
        if (!busy && step < 2 && entered.isNotEmpty()) {
            entered = entered.dropLast(1)
            error = null
        }
    }

    fun save() {
        if (busy) return
        busy = true
        error = null
        onSave(firstPin, recoveryCode) { ok ->
            busy = false
            if (!ok) error = R.string.policy_v6_save_failed
        }
    }

    fun activate() {
        if (busy) return
        Sounds.select()
        if (step >= 2) {
            save()
            return
        }
        when (selected) {
            in 0..8 -> type('1' + selected)
            PAD_ZERO -> type('0')
            PAD_DELETE -> erase()
            PAD_CLOSE -> onDismiss()
        }
    }

    fun handleKey(e: NativeKeyEvent): Boolean {
        if (handleBackKey(e, !busy, onDismiss)) return true
        if (step >= 2) {
            if (e.action != NativeKeyEvent.ACTION_DOWN || e.repeatCount != 0) {
                return e.isDialogKey()
            }
            if (e.keyCode in CONFIRM_KEYS) {
                activate()
                return true
            }
            return e.isDialogKey()
        }
        if (e.keyCode in NativeKeyEvent.KEYCODE_0..NativeKeyEvent.KEYCODE_9) {
            if (e.action == NativeKeyEvent.ACTION_DOWN && e.repeatCount == 0) {
                type('0' + (e.keyCode - NativeKeyEvent.KEYCODE_0))
            }
            return true
        }
        if (e.keyCode == NativeKeyEvent.KEYCODE_DEL) {
            if (e.action == NativeKeyEvent.ACTION_DOWN && e.repeatCount == 0) erase()
            return true
        }
        if (e.action != NativeKeyEvent.ACTION_DOWN) return e.isDialogKey()
        return when (e.keyCode) {
            NativeKeyEvent.KEYCODE_DPAD_UP,
            NativeKeyEvent.KEYCODE_DPAD_DOWN,
            NativeKeyEvent.KEYCODE_DPAD_LEFT,
            NativeKeyEvent.KEYCODE_DPAD_RIGHT,
            -> {
                // Repeats count too: holding a direction must keep moving like focus traversal.
                val next = movePadSelection(selected, e.keyCode)
                if (next != selected) {
                    selected = next
                    Sounds.focusMoved()
                }
                true
            }
            in CONFIRM_KEYS -> {
                if (e.repeatCount == 0) activate()
                true
            }
            else -> false
        }
    }

    PolicyPinDialogRoot(
        keyRouter = keyRouter,
        onDismiss = onDismiss,
        dismissible = { !busy },
        onKey = ::handleKey,
    ) {
        BasicText(
            stringResource(
                when (step) {
                    0 -> R.string.policy_setup_pin_title
                    1 -> R.string.policy_setup_pin_again
                    else -> R.string.policy_setup_recovery_title
                },
            ),
            style = Type.Title,
        )
        Spacer(Modifier.height(6.dp))
        DialogSubtitle(
            text = stringResource(
                when (step) {
                    0 -> R.string.policy_setup_body
                    1 -> R.string.policy_setup_pin_again_hint
                    else -> R.string.policy_setup_recovery_body
                },
            ),
            style = Type.Caption.copy(textAlign = TextAlign.Center),
        )
        Spacer(Modifier.height(12.dp))
        if (step < 2) {
            PolicyPinDots(entered.length, DIALOG_PIN_LENGTH)
            Spacer(Modifier.height(6.dp))
            BasicText(
                error?.let { stringResource(it) } ?: " ",
                style = Type.Caption.copy(color = Palette.Red),
            )
            Spacer(Modifier.height(8.dp))
            DialogPinPad(selected, ::type, ::erase, onClose = { if (!busy) onDismiss() })
        } else {
            BasicText(recoveryCode, style = Type.LargeTitle.copy(color = Palette.Focus))
            Spacer(Modifier.height(12.dp))
            BasicText(
                error?.let { stringResource(it) } ?: " ",
                style = Type.Caption.copy(color = Palette.Red),
            )
            Spacer(Modifier.height(8.dp))
            DialogTile(
                label = stringResource(R.string.policy_setup_recovery_saved),
                focused = selected == PAD_CLOSE,
                width = 280.dp,
                primary = true,
                onClick = ::save,
            )
        }
    }
}

/** One-time sync of a locally changed PIN to the cloud; the TV never stores the plaintext. */
@Composable
internal fun PolicySyncPinDialog(
    keyRouter: PolicyDialogKeyRouter,
    onDismiss: () -> Unit,
    onSync: (pin: String, onResult: (Boolean) -> Unit) -> Unit,
) {
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    fun submit(candidate: String) {
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                PolicyModeCoordinator.verifyPin(candidate)
            }
            if (result != PinResult.OK) {
                busy = false
                error = R.string.policy_wrong_pin
                return@launch
            }
            onSync(candidate) { ok ->
                busy = false
                if (ok) onDismiss() else error = R.string.policy_parent_pin_sync_failed
            }
        }
    }

    fun type(digit: Char) {
        if (busy || entered.length >= DIALOG_PIN_LENGTH) return
        entered += digit
        error = null
        if (entered.length == DIALOG_PIN_LENGTH) {
            val candidate = entered
            entered = ""
            submit(candidate)
        }
    }

    fun erase() {
        if (!busy && entered.isNotEmpty()) {
            entered = entered.dropLast(1)
            error = null
        }
    }

    fun activate() {
        if (busy) return
        Sounds.select()
        when (selected) {
            in 0..8 -> type('1' + selected)
            PAD_ZERO -> type('0')
            PAD_DELETE -> erase()
            PAD_CLOSE -> onDismiss()
        }
    }

    fun handleKey(e: NativeKeyEvent): Boolean {
        if (handleBackKey(e, !busy, onDismiss)) return true
        if (e.keyCode in NativeKeyEvent.KEYCODE_0..NativeKeyEvent.KEYCODE_9) {
            if (e.action == NativeKeyEvent.ACTION_DOWN && e.repeatCount == 0) {
                type('0' + (e.keyCode - NativeKeyEvent.KEYCODE_0))
            }
            return true
        }
        if (e.keyCode == NativeKeyEvent.KEYCODE_DEL) {
            if (e.action == NativeKeyEvent.ACTION_DOWN && e.repeatCount == 0) erase()
            return true
        }
        if (e.action != NativeKeyEvent.ACTION_DOWN) return e.isDialogKey()
        return when (e.keyCode) {
            NativeKeyEvent.KEYCODE_DPAD_UP,
            NativeKeyEvent.KEYCODE_DPAD_DOWN,
            NativeKeyEvent.KEYCODE_DPAD_LEFT,
            NativeKeyEvent.KEYCODE_DPAD_RIGHT,
            -> {
                // Repeats count too: holding a direction must keep moving like focus traversal.
                val next = movePadSelection(selected, e.keyCode)
                if (next != selected) {
                    selected = next
                    Sounds.focusMoved()
                }
                true
            }
            in CONFIRM_KEYS -> {
                if (e.repeatCount == 0) activate()
                true
            }
            else -> false
        }
    }

    PolicyPinDialogRoot(
        keyRouter = keyRouter,
        onDismiss = onDismiss,
        dismissible = { !busy },
        onKey = ::handleKey,
    ) {
        BasicText(stringResource(R.string.policy_parent_pin_sync), style = Type.Title)
        Spacer(Modifier.height(6.dp))
        BasicText(
            stringResource(R.string.policy_parent_pin_sync_hint),
            style = Type.Body,
        )
        Spacer(Modifier.height(4.dp))
        BasicText(stringResource(R.string.policy_parent_old_pin), style = Type.Caption)
        Spacer(Modifier.height(10.dp))
        PolicyPinDots(entered.length, DIALOG_PIN_LENGTH)
        Spacer(Modifier.height(6.dp))
        BasicText(
            error?.let { stringResource(it) } ?: " ",
            style = Type.Caption.copy(color = Palette.Red),
        )
        Spacer(Modifier.height(8.dp))
        DialogPinPad(selected, ::type, ::erase, onClose = { if (!busy) onDismiss() })
    }
}

/**
 * One-time privacy/authorization notice shown before the TV uploads local rules and the parent
 * password for remote management. It is deliberately shown before the QR code so scanning does
 * not trigger a second confirmation on the phone or the TV.
 */
@Composable
internal fun PolicyCloudConsentDialog(
    keyRouter: PolicyDialogKeyRouter,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(1) } // 1 = agree

    fun activate() {
        Sounds.select()
        if (selected == 0) onDismiss() else onAccept()
    }

    fun handleKey(e: NativeKeyEvent): Boolean {
        if (handleBackKey(e, true, onDismiss)) return true
        if (e.action != NativeKeyEvent.ACTION_DOWN) return e.isDialogKey()
        return when (e.keyCode) {
            NativeKeyEvent.KEYCODE_DPAD_LEFT -> {
                if (selected != 0) {
                    selected = 0
                    Sounds.focusMoved()
                }
                true
            }
            NativeKeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (selected != 1) {
                    selected = 1
                    Sounds.focusMoved()
                }
                true
            }
            NativeKeyEvent.KEYCODE_DPAD_UP,
            NativeKeyEvent.KEYCODE_DPAD_DOWN,
            -> true
            in CONFIRM_KEYS -> {
                if (e.repeatCount == 0) activate()
                true
            }
            else -> false
        }
    }

    PolicyDialogRoot(
        maxWidth = 680.dp,
        keyRouter = keyRouter,
        onDismiss = onDismiss,
        dismissible = { true },
        onKey = ::handleKey,
    ) {
        BasicText(stringResource(R.string.policy_v6_consent_title), style = Type.Title)
        Spacer(Modifier.height(10.dp))
        BasicText(
            stringResource(R.string.policy_v6_consent_body),
            style = Type.Body.copy(fontSize = 14.sp),
        )
        Spacer(Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DialogTile(
                label = stringResource(R.string.policy_v6_consent_cancel),
                focused = selected == 0,
                width = 160.dp,
                onClick = onDismiss,
            )
            DialogTile(
                label = stringResource(R.string.policy_v6_consent_agree),
                focused = selected == 1,
                width = 280.dp,
                primary = true,
                onClick = onAccept,
            )
        }
    }
}

/**
 * Confirms a timing-mode switch before the rule set is replaced. Switching to automatic also
 * explains that the TV quiz entry is manual-only and shows the automatic values that will take
 * effect. The safe choice is focused first, so a second OK never commits the switch.
 */
@Composable
internal fun PolicyActivationConfirmDialog(
    keyRouter: PolicyDialogKeyRouter,
    target: Activation,
    autoDailyMinutes: Int,
    autoSessionMinutes: Int,
    autoRestMinutes: Int,
    sessionActive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(target) { mutableStateOf(0) } // 0 = cancel, 1 = switch

    fun activate() {
        Sounds.select()
        if (selected == 0) onDismiss() else onConfirm()
    }

    fun handleKey(e: NativeKeyEvent): Boolean {
        if (handleBackKey(e, true, onDismiss)) return true
        if (e.action != NativeKeyEvent.ACTION_DOWN) return e.isDialogKey()
        return when (e.keyCode) {
            NativeKeyEvent.KEYCODE_DPAD_LEFT -> {
                if (selected != 0) {
                    selected = 0
                    Sounds.focusMoved()
                }
                true
            }
            NativeKeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (selected != 1) {
                    selected = 1
                    Sounds.focusMoved()
                }
                true
            }
            NativeKeyEvent.KEYCODE_DPAD_UP,
            NativeKeyEvent.KEYCODE_DPAD_DOWN,
            -> true
            in CONFIRM_KEYS -> {
                if (e.repeatCount == 0) activate()
                true
            }
            else -> false
        }
    }

    PolicyDialogRoot(
        maxWidth = 620.dp,
        keyRouter = keyRouter,
        onDismiss = onDismiss,
        dismissible = { true },
        onKey = ::handleKey,
    ) {
        BasicText(
            stringResource(
                if (target == Activation.AUTO) {
                    R.string.policy_v6_activation_confirm_auto_title
                } else {
                    R.string.policy_v6_activation_confirm_manual_title
                },
            ),
            style = Type.Title,
        )
        Spacer(Modifier.height(10.dp))
        BasicText(
            if (target == Activation.AUTO) {
                stringResource(
                    R.string.policy_v6_activation_confirm_auto_body,
                    autoDailyMinutes,
                    autoSessionMinutes,
                    autoRestMinutes,
                )
            } else {
                stringResource(R.string.policy_v6_activation_confirm_manual_body)
            },
            style = Type.Body.copy(fontSize = 14.sp),
        )
        if (sessionActive) {
            Spacer(Modifier.height(8.dp))
            BasicText(
                stringResource(R.string.policy_v6_activation_confirm_end_session),
                style = Type.Body.copy(fontSize = 14.sp, color = Palette.Orange),
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DialogTile(
                label = stringResource(R.string.policy_v6_activation_confirm_cancel),
                focused = selected == 0,
                width = 160.dp,
                onClick = onDismiss,
            )
            DialogTile(
                label = stringResource(R.string.policy_v6_activation_confirm_switch),
                focused = selected == 1,
                width = 240.dp,
                primary = true,
                onClick = onConfirm,
            )
        }
    }
}

/**
 * The first child-mode enable explains that it also turns auto update on. The choice is
 * device-level and one-time: after this, the parent center's own switch is the only place it
 * changes, so a parent who turned auto update off is not asked again by child mode.
 */
@Composable
internal fun PolicyAutoUpdateConsentDialog(
    keyRouter: PolicyDialogKeyRouter,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(1) } // 1 = confirm

    fun activate() {
        Sounds.select()
        if (selected == 0) onDismiss() else onConfirm()
    }

    fun handleKey(e: NativeKeyEvent): Boolean {
        if (handleBackKey(e, true, onDismiss)) return true
        if (e.action != NativeKeyEvent.ACTION_DOWN) return e.isDialogKey()
        return when (e.keyCode) {
            NativeKeyEvent.KEYCODE_DPAD_LEFT -> {
                if (selected != 0) {
                    selected = 0
                    Sounds.focusMoved()
                }
                true
            }
            NativeKeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (selected != 1) {
                    selected = 1
                    Sounds.focusMoved()
                }
                true
            }
            NativeKeyEvent.KEYCODE_DPAD_UP,
            NativeKeyEvent.KEYCODE_DPAD_DOWN,
            -> true
            in CONFIRM_KEYS -> {
                if (e.repeatCount == 0) activate()
                true
            }
            else -> false
        }
    }

    PolicyDialogRoot(
        maxWidth = 680.dp,
        keyRouter = keyRouter,
        onDismiss = onDismiss,
        dismissible = { true },
        onKey = ::handleKey,
    ) {
        BasicText(stringResource(R.string.policy_v6_auto_consent_title), style = Type.Title)
        Spacer(Modifier.height(10.dp))
        // Say what the mode is for before explaining the auto-update side effect.
        BasicText(
            stringResource(R.string.kids_position),
            style = Type.Body.copy(fontSize = 14.sp),
        )
        Spacer(Modifier.height(10.dp))
        BasicText(
            stringResource(R.string.policy_v6_auto_consent_body),
            style = Type.Body.copy(fontSize = 14.sp),
        )
        Spacer(Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DialogTile(
                label = stringResource(R.string.policy_v6_auto_consent_cancel),
                focused = selected == 0,
                width = 160.dp,
                onClick = onDismiss,
            )
            DialogTile(
                label = stringResource(R.string.policy_v6_auto_consent_confirm),
                focused = selected == 1,
                width = 280.dp,
                primary = true,
                onClick = onConfirm,
            )
        }
    }
}

/**
 * The parent-center confirmation path for an OFF-origin session. The start screen can be
 * opened and child mode enabled without a PIN; switching or turning protection off still
 * needs the stored PIN. The consequence text lives in this dialog so one modal covers both
 * authorization and confirmation.
 */
@Composable
internal fun PolicyVerifyPinDialog(
    keyRouter: PolicyDialogKeyRouter,
    message: String,
    onDismiss: () -> Unit,
    onVerified: (String) -> Unit,
) {
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    fun submit(candidate: String) {
        busy = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                PolicyModeCoordinator.verifyPin(candidate)
            }
            busy = false
            when (result) {
                PinResult.OK -> {
                    onVerified(candidate)
                    onDismiss()
                }
                PinResult.LOCKED_OUT -> error = R.string.policy_locked_out
                else -> error = R.string.policy_wrong_pin
            }
        }
    }

    fun type(digit: Char) {
        if (busy || entered.length >= DIALOG_PIN_LENGTH) return
        entered += digit
        error = null
        if (entered.length == DIALOG_PIN_LENGTH) {
            val candidate = entered
            entered = ""
            submit(candidate)
        }
    }

    fun erase() {
        if (!busy && entered.isNotEmpty()) {
            entered = entered.dropLast(1)
            error = null
        }
    }

    fun activate() {
        if (busy) return
        Sounds.select()
        when (selected) {
            in 0..8 -> type('1' + selected)
            PAD_ZERO -> type('0')
            PAD_DELETE -> erase()
            PAD_CLOSE -> onDismiss()
        }
    }

    fun handleKey(e: NativeKeyEvent): Boolean {
        if (handleBackKey(e, !busy, onDismiss)) return true
        if (e.keyCode in NativeKeyEvent.KEYCODE_0..NativeKeyEvent.KEYCODE_9) {
            if (e.action == NativeKeyEvent.ACTION_DOWN && e.repeatCount == 0) {
                type('0' + (e.keyCode - NativeKeyEvent.KEYCODE_0))
            }
            return true
        }
        if (e.keyCode == NativeKeyEvent.KEYCODE_DEL) {
            if (e.action == NativeKeyEvent.ACTION_DOWN && e.repeatCount == 0) erase()
            return true
        }
        if (e.action != NativeKeyEvent.ACTION_DOWN) return e.isDialogKey()
        return when (e.keyCode) {
            NativeKeyEvent.KEYCODE_DPAD_UP,
            NativeKeyEvent.KEYCODE_DPAD_DOWN,
            NativeKeyEvent.KEYCODE_DPAD_LEFT,
            NativeKeyEvent.KEYCODE_DPAD_RIGHT,
            -> {
                // Repeats count too: holding a direction must keep moving like focus traversal.
                val next = movePadSelection(selected, e.keyCode)
                if (next != selected) {
                    selected = next
                    Sounds.focusMoved()
                }
                true
            }
            in CONFIRM_KEYS -> {
                if (e.repeatCount == 0) activate()
                true
            }
            else -> false
        }
    }

    PolicyPinDialogRoot(
        keyRouter = keyRouter,
        onDismiss = onDismiss,
        dismissible = { !busy },
        onKey = ::handleKey,
    ) {
        BasicText(stringResource(R.string.policy_parent_verify_title), style = Type.Title)
        Spacer(Modifier.height(6.dp))
        DialogSubtitle(text = message, style = Type.Body.copy(fontSize = 15.sp))
        Spacer(Modifier.height(12.dp))
        PolicyPinDots(entered.length, DIALOG_PIN_LENGTH)
        Spacer(Modifier.height(6.dp))
        BasicText(
            error?.let { stringResource(it) } ?: " ",
            style = Type.Caption.copy(color = Palette.Red),
        )
        Spacer(Modifier.height(8.dp))
        DialogPinPad(selected, ::type, ::erase, onClose = { if (!busy) onDismiss() })
    }
}
