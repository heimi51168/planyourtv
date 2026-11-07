package app.orchard.tv.ui.policy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.orchard.tv.R
import app.orchard.tv.ui.components.liquidGlass
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type

/**
 * Temporary stand-in for the redemption tutorial video. The row in the bound remote pane
 * opens this readable, full-screen step list; replacing it with an ExoPlayer surface later
 * does not change the parent-center focus model.
 */
@Composable
internal fun PolicyTutorialOverlay(onDismiss: () -> Unit) {
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { closeFocus.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Palette.OnFocus.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .liquidGlass(26.dp)
                    .padding(horizontal = 36.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BasicText(
                    stringResource(R.string.policy_parent_tutorial_title),
                    style = Type.LargeTitle.copy(fontSize = 24.sp),
                )
                BasicText(
                    stringResource(R.string.policy_parent_tutorial_placeholder),
                    style = Type.Caption.copy(fontSize = 13.sp, color = Palette.Orange),
                )
                Spacer(Modifier.height(2.dp))
                listOf(
                    R.string.policy_parent_redeem_step1,
                    R.string.policy_parent_redeem_step2,
                    R.string.policy_parent_redeem_step3,
                    R.string.policy_parent_redeem_step4,
                ).forEach { step ->
                    BasicText(
                        stringResource(step),
                        style = Type.Body.copy(fontSize = 16.sp, color = Palette.Label),
                    )
                }
                Spacer(Modifier.height(2.dp))
                BasicText(
                    stringResource(R.string.policy_parent_redeem_note),
                    style = Type.Caption.copy(fontSize = 13.sp, color = Palette.SecondaryLabel),
                )
                BasicText(
                    stringResource(R.string.policy_parent_phone_settings),
                    style = Type.Caption.copy(fontSize = 13.sp, color = Palette.SecondaryLabel),
                )
                Spacer(Modifier.height(8.dp))
                LockButton(
                    label = stringResource(R.string.policy_parent_close),
                    onClick = onDismiss,
                    width = 200.dp,
                    focusRequester = closeFocus,
                    primary = true,
                )
            }
        }
    }
}
