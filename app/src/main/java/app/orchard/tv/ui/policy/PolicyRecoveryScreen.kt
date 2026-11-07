package app.orchard.tv.ui.policy

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.orchard.tv.R
import app.orchard.tv.policy.LocalPolicyRuntime
import app.orchard.tv.ui.components.liquidGlass
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type
import com.planyourtv.policy.PinResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val RECOVERY_CODE_LENGTH = 12
private const val RECOVERY_PIN_LENGTH = 6

/**
 * Offline recovery-code reset, reachable from the lock screen and the parent-center PIN gate
 * without a working PIN. The recovery code is fixed for the install; only its hash is stored.
 */
@Composable
fun PolicyRecoveryScreen(onCancel: () -> Unit, onDone: () -> Unit) {
    var step by remember { mutableStateOf(0) } // 0 code, 1 new PIN, 2 confirm, 3 done
    var code by remember { mutableStateOf("") }
    var entered by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(step) {
        withFrameNanos { }
        if (step < 3) runCatching { firstFocus.requestFocus() }
    }

    fun submitCode() {
        if (busy || entered.length != RECOVERY_CODE_LENGTH) return
        val candidate = entered
        busy = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                LocalPolicyRuntime.verifyRecovery(candidate)
            }
            busy = false
            if (result == PinResult.OK) {
                code = candidate
                entered = ""
                step = 1
            } else {
                error = when (result) {
                    PinResult.LOCKED_OUT -> R.string.policy_locked_out
                    PinResult.NOT_SET -> R.string.policy_recovery_not_set
                    else -> R.string.policy_wrong_pin
                }
                entered = ""
            }
        }
    }

    fun submitPin() {
        if (entered.length != RECOVERY_PIN_LENGTH) return
        if (step == 1) {
            newPin = entered
            entered = ""
            error = null
            step = 2
            return
        }
        if (entered != newPin) {
            error = R.string.policy_setup_pin_mismatch
            entered = ""
            return
        }
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.Default) {
                LocalPolicyRuntime.resetWithRecovery(code, newPin)
            }
            busy = false
            if (!ok) {
                error = R.string.policy_wrong_pin
            } else {
                step = 3
            }
        }
    }

    fun type(digit: Char) {
        if (busy || step >= 3) return
        val max = if (step == 0) RECOVERY_CODE_LENGTH else RECOVERY_PIN_LENGTH
        if (entered.length >= max) return
        entered += digit
        error = null
        if (entered.length == max) {
            if (step == 0) submitCode() else submitPin()
        }
    }

    fun erase() {
        if (!busy && step < 3 && entered.isNotEmpty()) {
            entered = entered.dropLast(1)
            error = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (busy || step >= 3 || event.type != KeyEventType.KeyDown ||
                    event.nativeKeyEvent.repeatCount != 0
                ) {
                    return@onPreviewKeyEvent false
                }
                when (event.nativeKeyEvent.keyCode) {
                    in NativeKeyEvent.KEYCODE_0..NativeKeyEvent.KEYCODE_9 -> {
                        type('0' + (event.nativeKeyEvent.keyCode - NativeKeyEvent.KEYCODE_0))
                        true
                    }
                    NativeKeyEvent.KEYCODE_DEL -> {
                        erase()
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        PolicyParentBackdrop()
        // The recovery flow shares the Aerial frosted backdrop and glass card with the parent
        // center, so a parent who lands here does not drop back into the old dark gradient.
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .liquidGlass(26.dp)
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                stringResource(
                    when (step) {
                        0 -> R.string.policy_parent_recovery
                        1, 2 -> R.string.policy_parent_recovery_new
                        else -> R.string.policy_recovery_done_title
                    },
                ),
                style = Type.LargeTitle,
                modifier = Modifier.widthIn(max = 480.dp),
            )
            Spacer(Modifier.height(6.dp))
            BasicText(
                when (step) {
                    0 -> stringResource(R.string.policy_parent_recovery_hint)
                    3 -> stringResource(R.string.policy_recovery_done_body)
                    else -> stringResource(R.string.policy_setup_recovery_body)
                },
                style = Type.Caption.copy(textAlign = TextAlign.Center),
                modifier = Modifier.widthIn(max = 460.dp),
            )
            Spacer(Modifier.height(12.dp))
            if (step == 3) {
                LockButton(
                    label = stringResource(R.string.policy_recovery_done),
                    onClick = onDone,
                    width = 240.dp,
                    primary = true,
                )
            } else {
                PolicyPinDots(
                    enteredLength = entered.length,
                    length = if (step == 0) RECOVERY_CODE_LENGTH else RECOVERY_PIN_LENGTH,
                )
                Spacer(Modifier.height(6.dp))
                BasicText(
                    error?.let { stringResource(it) } ?: " ",
                    style = Type.Caption.copy(color = Palette.Red),
                )
                Spacer(Modifier.height(8.dp))
                // Back sits in the slot left of 0 so the bottom row fills all three columns
                // instead of leaving a gap that pushes 0 and delete under the wrong digits.
                PolicyDigitPad(
                    onDigit = ::type,
                    onErase = ::erase,
                    firstFocus = firstFocus,
                    leftKeyLabel = stringResource(R.string.policy_back),
                    onLeftKey = onCancel,
                )
            }
        }
    }
}
