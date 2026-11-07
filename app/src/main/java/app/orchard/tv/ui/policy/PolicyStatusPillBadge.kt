package app.orchard.tv.ui.policy

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orchard.tv.R
import app.orchard.tv.policy.PolicyModeCoordinator
import app.orchard.tv.ui.theme.Glyph
import app.orchard.tv.ui.theme.GlyphIcon
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type
import com.planyourtv.policy.GuardStatus
import com.planyourtv.policy.PolicyMode
import com.planyourtv.policy.PolicyUiState
import com.planyourtv.policy.SessionState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val RingSize = 30.dp
private val RingStroke = 2.5.dp

// The status pill turns near-white while focused, so semantic colors need a
// darker variant there. These match Apple's accessible dark variants.
private val GreenOnLight = Color(0xFF248A3D)
private val OrangeOnLight = Color(0xFFC93400)
private val RedOnLight = Color(0xFFD70015)
private val GrayOnDark = Color(0xFFAEAEB2)
private val GrayOnLight = Color(0xFF6E6E73)

private data class BadgeState(
    val mode: PolicyMode,
    val guard: GuardStatus,
    /** Only an ACTIVE session counts down; PENDING has not started. */
    val counting: Boolean,
    /** Rounded up to a 10-second bucket, for the tiny label. */
    val seconds: Long,
    /** Exact remaining time, for color thresholds and ring progress. */
    val remainingSeconds: Long,
    /** The session total, for the progress ring before the last minute. */
    val totalSeconds: Long,
)

private fun badgeStateOf(state: PolicyUiState): BadgeState {
    // A temporary unlock and study mode never show a child session countdown.
    val active = state.sessionState == SessionState.ACTIVE &&
        state.guardState == GuardStatus.ENFORCING
    val remaining = if (active) state.effectiveRemainingSeconds.coerceAtLeast(0L) else 0L
    return BadgeState(
        mode = state.mode,
        guard = state.guardState,
        counting = active && remaining > 0 && state.totalSeconds > 0,
        seconds = if (active) roundUpToTen(remaining) else 0L,
        remainingSeconds = remaining,
        totalSeconds = if (active) state.totalSeconds.coerceAtLeast(0L) else 0L,
    )
}

private fun roundUpToTen(seconds: Long): Long =
    ((seconds.coerceAtLeast(0L) + 9L) / 10L) * 10L

/**
 * The lock screen and the desktop pill must use the same shape for each policy source:
 * local shielding is the shield, cloud policy is the cloud. Color is separate and carries
 * the guard state, so both sources can share the same green while guarding.
 */
internal fun policyModeGlyph(mode: PolicyMode): Glyph = when (mode) {
    PolicyMode.CLOUD -> Glyph.Cloud
    PolicyMode.NONE, PolicyMode.LOCAL -> Glyph.Shield
}

/**
 * Appended to Aerial's StatusPill through the generic product hook. Aerial only supplies the
 * foreground color; this product-owned composable renders the mode icon and its session ring.
 */
@Composable
fun PolicyStatusPillBadge(foreground: Color) {
    val badgeFlow = remember {
        PolicyModeCoordinator.state
            .map(::badgeStateOf)
            .distinctUntilChanged()
    }
    val badge by badgeFlow.collectAsState(
        initial = badgeStateOf(PolicyModeCoordinator.state.value),
    )
    val modeLabel = stringResource(
        when (badge.mode) {
            PolicyMode.NONE -> R.string.policy_mode_none
            PolicyMode.LOCAL -> R.string.policy_mode_local
            PolicyMode.CLOUD -> R.string.policy_mode_cloud
        },
    )
    val studyLabel = stringResource(R.string.policy_study_title)
    val parentUnlockLabel = stringResource(R.string.policy_parent_unlock)
    // Shape keeps the source (local shield / cloud) distinct; color carries the
    // guard state. Both sources are green while guarding, so neither looks more
    // trusted than the other.
    val lightBackground = foreground.luminance() < 0.5f
    val green = if (lightBackground) GreenOnLight else Palette.Green
    val orange = if (lightBackground) OrangeOnLight else Palette.Orange
    val red = if (lightBackground) RedOnLight else Palette.Red
    val gray = if (lightBackground) GrayOnLight else GrayOnDark
    val accent = when {
        badge.mode == PolicyMode.NONE -> foreground.copy(alpha = 0.55f)
        badge.guard == GuardStatus.PARENT_OVERRIDE -> gray
        else -> green
    }
    val animatedAccent by animateColorAsState(
        accent,
        tween(200),
        label = "guard-accent",
    )
    val glyph = policyModeGlyph(badge.mode)
    val ringColor = when (countdownTone(badge.remainingSeconds)) {
        CountdownTone.GREEN -> green
        CountdownTone.ORANGE -> orange
        CountdownTone.RED -> red
    }
    val animatedRingColor by animateColorAsState(
        ringColor,
        tween(200),
        label = "ring-color",
    )
    val guardLabel = when (badge.guard) {
        GuardStatus.STUDY_MODE -> studyLabel
        GuardStatus.PARENT_OVERRIDE -> parentUnlockLabel
        GuardStatus.ENFORCING -> null
    }
    val semanticsLabel = listOfNotNull(
        modeLabel,
        guardLabel,
        if (badge.counting) ringLabel(badge.seconds) else null,
    ).joinToString(" ")

    Row(
        modifier = Modifier.semantics { contentDescription = semanticsLabel },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // A hairline separates the guard from the settings icon without another pill.
        Box(
            Modifier
                .width(1.dp)
                .height(14.dp)
                .background(foreground.copy(alpha = 0.25f)),
        )
        GlyphIcon(glyph, animatedAccent, 14.dp)
        if (badge.counting) {
            CountdownRing(
                seconds = badge.seconds,
                progress = countdownProgress(badge.remainingSeconds, badge.totalSeconds),
                color = animatedRingColor,
                foreground = foreground,
            )
        }
    }
}

@Composable
private fun CountdownRing(
    seconds: Long,
    progress: Float,
    color: Color,
    foreground: Color,
) {
    Box(
        modifier = Modifier.size(RingSize),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = RingStroke.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = foreground.copy(alpha = 0.18f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (progress > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        BasicText(
            ringLabel(seconds),
            style = Type.Caption.copy(
                color = foreground,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
    }
}

private fun ringLabel(seconds: Long): String = when {
    seconds >= 3600L -> "${(seconds + 3599L) / 3600L}h"
    seconds >= 60L -> "${(seconds + 59L) / 60L}m"
    else -> "${seconds.coerceAtLeast(0L)}s"
}
