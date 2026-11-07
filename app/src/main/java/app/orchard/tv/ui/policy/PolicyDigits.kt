package app.orchard.tv.ui.policy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orchard.tv.R
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type

/** Shared six-digit PIN dots for setup and parent authentication. */
@Composable
internal fun PolicyPinDots(enteredLength: Int, length: Int = 6) {
    Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        repeat(length) { index ->
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (index < enteredLength) Palette.Label else Color.Transparent)
                    .border(1.5.dp, Palette.Label, CircleShape),
            )
        }
    }
}

/**
 * A D-pad-friendly digit pad; no soft keyboard is ever needed on the TV.
 *
 * The bottom row is three keys of the same width as the digits above, so 0 sits exactly under
 * 2/5/8 and the outer keys line up with the 1/4/7 and 3/6/9 columns. [onLeftKey] fills the
 * left slot (recovery code, back) instead of leaving it empty; the 0 key never moves.
 */
@Composable
internal fun PolicyDigitPad(
    onDigit: (Char) -> Unit,
    onErase: () -> Unit,
    firstFocus: FocusRequester? = null,
    leftKeyLabel: String? = null,
    onLeftKey: (() -> Unit)? = null,
) {
    val rows = listOf("123", "456", "789")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { digit ->
                    LockButton(
                        label = digit.toString(),
                        onClick = { onDigit(digit) },
                        focusRequester = if (digit == '1') firstFocus else null,
                        width = 74.dp,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (onLeftKey != null) {
                LockButton(
                    label = leftKeyLabel.orEmpty(),
                    onClick = onLeftKey,
                    width = 74.dp,
                    labelStyle = Type.Headline.copy(fontSize = 13.sp),
                )
            } else {
                Spacer(Modifier.width(74.dp).height(48.dp))
            }
            LockButton(
                label = "0",
                onClick = { onDigit('0') },
                width = 74.dp,
            )
            LockButton(
                label = stringResource(R.string.policy_delete),
                onClick = onErase,
                width = 74.dp,
            )
        }
    }
}

