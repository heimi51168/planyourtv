package app.orchard.tv.ui.control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.orchard.tv.R
import app.orchard.tv.ui.components.liquidGlass
import app.orchard.tv.ui.policy.LockButton
import app.orchard.tv.ui.theme.Glyph
import app.orchard.tv.ui.theme.GlyphIcon
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type

/**
 * The one notice a fresh installation gets when Home Guard is not ready. It points to the
 * permanent status row instead of sending the viewer straight into a system settings page.
 */
@Composable
fun GuardStatusPrompt(onOpenStatus: () -> Unit, onDismiss: () -> Unit) {
    val openFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { openFocus.requestFocus() }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .liquidGlass(24.dp)
                .padding(horizontal = 30.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GlyphIcon(Glyph.Shield, Palette.Orange, 26.dp)
                BasicText(
                    stringResource(R.string.cc_status_prompt_title),
                    style = Type.Title,
                )
            }
            Spacer(Modifier.height(12.dp))
            BasicText(
                stringResource(R.string.cc_status_prompt_body),
                style = Type.Body.copy(color = Palette.SecondaryLabel),
            )
            Spacer(Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LockButton(
                    label = stringResource(R.string.cc_status_prompt_later),
                    onClick = onDismiss,
                    width = 150.dp,
                )
                LockButton(
                    label = stringResource(R.string.cc_status_prompt_open),
                    onClick = onOpenStatus,
                    width = 250.dp,
                    focusRequester = openFocus,
                    primary = true,
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
