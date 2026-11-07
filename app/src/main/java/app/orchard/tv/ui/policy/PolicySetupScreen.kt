package app.orchard.tv.ui.policy

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.orchard.tv.R
import app.orchard.tv.policy.LocalPolicyRuntime
import app.orchard.tv.ui.components.liquidGlass
import app.orchard.tv.ui.theme.Glyph
import app.orchard.tv.ui.theme.GlyphIcon
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type
import com.planyourtv.policy.LocalRecovery
import com.planyourtv.policy.LocalRules
import com.planyourtv.policy.PolicyUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SETUP_PIN_LENGTH = 6

/** First-run LOCAL setup. It never locks the TV before a PIN and rules exist. */
@Composable
fun PolicySetupScreen(state: PolicyUiState) {
    var step by remember { mutableStateOf(0) } // 0 PIN, 1 confirm, 2 recovery
    var entered by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf("") }
    var recoveryCode by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(step) {
        withFrameNanos { }
        if (step < 2) runCatching { firstFocus.requestFocus() }
    }

    fun submit() {
        if (entered.length != SETUP_PIN_LENGTH) return
        when (step) {
            0 -> {
                firstPin = entered
                entered = ""
                error = null
                step = 1
            }
            1 -> {
                if (entered != firstPin) {
                    error = R.string.policy_setup_pin_mismatch
                    entered = ""
                } else {
                    recoveryCode = LocalRecovery.newCode()
                    entered = ""
                    error = null
                    step = 2
                }
            }
        }
    }

    fun type(digit: Char) {
        if (saving || step >= 2 || entered.length >= SETUP_PIN_LENGTH) return
        entered += digit
        error = null
        if (entered.length == SETUP_PIN_LENGTH) submit()
    }

    fun erase() {
        if (!saving && step < 2 && entered.isNotEmpty()) {
            entered = entered.dropLast(1)
            error = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF101827), Color(0xFF03060C))),
            )
            .onPreviewKeyEvent { event ->
                if (saving || step >= 2 || event.type != KeyEventType.KeyDown ||
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
        Row(
            modifier = Modifier
                .widthIn(max = 820.dp)
                .liquidGlass(28.dp)
                .padding(horizontal = 40.dp, vertical = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 340.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GlyphIcon(Glyph.Shield, Palette.Label, 30.dp)
                Spacer(Modifier.height(8.dp))
                BasicText(
                    stringResource(if (step == 2) R.string.policy_setup_recovery_title else R.string.policy_setup_title),
                    style = Type.LargeTitle,
                    modifier = Modifier.widthIn(max = 340.dp),
                )
                Spacer(Modifier.height(8.dp))
                BasicText(
                    stringResource(if (step == 2) R.string.policy_setup_recovery_body else R.string.policy_setup_body),
                    style = Type.Caption.copy(textAlign = TextAlign.Center),
                )
                Spacer(Modifier.height(8.dp))
                BasicText(
                    stringResource(
                        if (state.mode == com.planyourtv.policy.PolicyMode.LOCAL) R.string.policy_mode_local
                        else R.string.policy_mode_cloud,
                    ),
                    style = Type.Caption.copy(color = Palette.SecondaryLabel),
                )
                if (step == 2) {
                    Spacer(Modifier.height(18.dp))
                    BasicText(recoveryCode, style = Type.LargeTitle.copy(color = Palette.Focus))
                    Spacer(Modifier.height(18.dp))
                    LockButton(
                        label = stringResource(R.string.policy_setup_recovery_saved),
                        onClick = {
                            if (saving) return@LockButton
                            saving = true
                            scope.launch {
                                val result = withContext(Dispatchers.Default) {
                                    LocalPolicyRuntime.completeSetup(
                                        firstPin,
                                        LocalRules.defaultsFor(state.activation),
                                        recoveryCode,
                                    )
                                }
                                saving = false
                                if (result == null) error = R.string.policy_wrong_pin
                            }
                        },
                        primary = true,
                        width = 280.dp,
                    )
                }
            }

            if (step < 2) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BasicText(
                        stringResource(if (step == 0) R.string.policy_setup_pin_title else R.string.policy_setup_pin_again),
                        style = Type.Title,
                    )
                    Spacer(Modifier.height(12.dp))
                    PolicyPinDots(entered.length, SETUP_PIN_LENGTH)
                    Spacer(Modifier.height(6.dp))
                    BasicText(
                        error?.let { stringResource(it) } ?: " ",
                        style = Type.Caption.copy(color = Palette.Red, textAlign = TextAlign.Center),
                    )
                    Spacer(Modifier.height(8.dp))
                    PolicyDigitPad(::type, ::erase, firstFocus)
                }
            }
        }
    }
}
