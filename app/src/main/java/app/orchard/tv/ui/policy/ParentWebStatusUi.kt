package app.orchard.tv.ui.policy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orchard.tv.R
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type
import app.orchard.tv.wallpaper.PhoneWebState
import app.orchard.tv.wallpaper.PhoneWebStatus

/** The status under the large parent-center QR: one state line, one action, and one hint. */
@Composable
internal fun ParentWebStatusDetail(
    status: PhoneWebStatus,
    modifier: Modifier = Modifier,
) {
    val color = statusColor(status.state)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatusDot(color, 6.dp)
            BasicText(
                stringResource(statusTitle(status.state)),
                style = Type.Headline.copy(fontSize = 14.sp, color = color, textAlign = TextAlign.Center),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        statusAction(status)?.let { action ->
            Spacer(Modifier.height(3.dp))
            BasicText(
                action,
                style = Type.Caption.copy(fontSize = 11.sp, color = Palette.SecondaryLabel, textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (status.state == PhoneWebState.OK) {
            Spacer(Modifier.height(6.dp))
            BasicText(
                stringResource(R.string.policy_parent_web_hint),
                style = Type.Caption.copy(fontSize = 10.sp, color = Palette.SecondaryLabel, textAlign = TextAlign.Center),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 500.dp),
            )
        }
    }
}

/** One line in the compact Control Center / lock-screen QR card; never shows the address. */
@Composable
internal fun ParentWebStatusCompact(
    status: PhoneWebStatus,
    modifier: Modifier = Modifier,
) {
    val color = statusColor(status.state)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StatusDot(color, 4.dp)
        BasicText(
            stringResource(statusShortTitle(status.state)),
            style = Type.Caption.copy(fontSize = 10.sp, color = color),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusDot(color: Color, size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun statusAction(status: PhoneWebStatus): String? = when (status.state) {
    PhoneWebState.OK -> {
        val access = lastAccessLabel(status.lastAccessAtMs)
        status.address?.let {
            stringResource(R.string.policy_parent_web_address_access, it, access)
        } ?: access
    }
    PhoneWebState.OFFLINE -> stringResource(R.string.policy_parent_web_action_network)
    PhoneWebState.NO_ADDRESS -> stringResource(R.string.policy_parent_web_action_proxy)
    PhoneWebState.NOT_STARTED,
    PhoneWebState.UNRESPONSIVE -> stringResource(R.string.policy_parent_web_action_restart)
    PhoneWebState.CHECKING -> null
}

@Composable
private fun lastAccessLabel(lastAccessAtMs: Long): String {
    if (lastAccessAtMs <= 0L) return stringResource(R.string.policy_parent_web_waiting)
    val minutes = ((System.currentTimeMillis() - lastAccessAtMs).coerceAtLeast(0L) / 60_000L).toInt()
    return when {
        minutes < 1 -> stringResource(R.string.policy_parent_web_just_now)
        minutes < 60 -> stringResource(R.string.policy_parent_web_minutes, minutes)
        else -> stringResource(R.string.policy_parent_web_hours, minutes / 60)
    }
}

private fun statusColor(state: PhoneWebState): Color = when (state) {
    PhoneWebState.OK -> Palette.Green
    PhoneWebState.OFFLINE, PhoneWebState.NO_ADDRESS -> Palette.Orange
    PhoneWebState.NOT_STARTED, PhoneWebState.UNRESPONSIVE -> Palette.Red
    PhoneWebState.CHECKING -> Palette.SecondaryLabel
}

private fun statusTitle(state: PhoneWebState): Int = when (state) {
    PhoneWebState.CHECKING -> R.string.policy_parent_web_checking
    PhoneWebState.OK -> R.string.policy_parent_web_ok
    PhoneWebState.OFFLINE -> R.string.policy_parent_web_offline
    PhoneWebState.NO_ADDRESS -> R.string.policy_parent_web_no_address
    PhoneWebState.NOT_STARTED -> R.string.policy_parent_web_not_started
    PhoneWebState.UNRESPONSIVE -> R.string.policy_parent_web_unresponsive
}

private fun statusShortTitle(state: PhoneWebState): Int = when (state) {
    PhoneWebState.CHECKING -> R.string.policy_parent_web_short_checking
    PhoneWebState.OK -> R.string.policy_parent_web_short_ok
    PhoneWebState.OFFLINE -> R.string.policy_parent_web_short_offline
    PhoneWebState.NO_ADDRESS -> R.string.policy_parent_web_short_no_address
    PhoneWebState.NOT_STARTED -> R.string.policy_parent_web_short_not_started
    PhoneWebState.UNRESPONSIVE -> R.string.policy_parent_web_short_unresponsive
}