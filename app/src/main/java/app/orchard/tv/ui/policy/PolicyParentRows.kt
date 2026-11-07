package app.orchard.tv.ui.policy

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orchard.tv.ui.components.Pressable
import app.orchard.tv.ui.theme.Motion
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type

/**
 * One focusable row. Up/down/left/right are handled by the caller instead of Compose's
 * geometric focus search; that is what keeps the parent center from jumping between columns.
 * The focused row is a whole-row plate, not a scaled card, so the highlight never moves.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PolicyFocusRow(
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    bringIntoViewRequester: BringIntoViewRequester? = null,
    normalBackground: Color = Palette.Fill.copy(alpha = 0.55f),
    highlighted: Boolean = false,
    armed: Boolean = false,
    enabled: Boolean = true,
    content: @Composable RowScope.(highlighted: Boolean) -> Unit,
) {
    fun handleKey(event: KeyEvent): Boolean {
        val code = event.nativeKeyEvent.keyCode
        val handled = when (code) {
            NativeKeyEvent.KEYCODE_DPAD_UP,
            NativeKeyEvent.KEYCODE_DPAD_DOWN,
            NativeKeyEvent.KEYCODE_DPAD_LEFT,
            NativeKeyEvent.KEYCODE_DPAD_RIGHT,
            -> true
            else -> false
        }
        if (!handled) return false
        // Consume key-up too. The actual move happens on key-down so a held d-pad repeats.
        if (event.type == KeyEventType.KeyUp) return true
        when (code) {
            NativeKeyEvent.KEYCODE_DPAD_UP -> onUp?.invoke()
            NativeKeyEvent.KEYCODE_DPAD_DOWN -> onDown?.invoke()
            NativeKeyEvent.KEYCODE_DPAD_LEFT -> onLeft?.invoke()
            NativeKeyEvent.KEYCODE_DPAD_RIGHT -> onRight?.invoke()
        }
        return true
    }

    val rowModifier = modifier
        .fillMaxWidth()
        .then(
            if (bringIntoViewRequester != null) {
                Modifier.bringIntoViewRequester(bringIntoViewRequester)
            } else {
                Modifier
            },
        )

    Pressable(
        onClick = onClick,
        enabled = enabled,
        focusRequester = focusRequester,
        focusedScale = 1f,
        onKey = ::handleKey,
        modifier = rowModifier,
    ) { press ->
        val plate = press.focused || highlighted || armed
        // Same 140 ms plate fade as Aerial's menu rows: a fast d-pad walk reads as one
        // highlight moving down the column instead of hard color cuts.
        val background by animateColorAsState(
            when {
                plate && armed -> Palette.Orange
                plate -> Palette.Focus
                else -> normalBackground
            },
            tween(140, easing = Motion.EaseOut),
            label = "policy-row-bg",
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(background)
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content(plate)
        }
    }
}

@Composable
internal fun PolicySectionHeader(text: String) {
    BasicText(
        text,
        style = Type.Caption.copy(
            fontSize = 13.sp,
            color = Palette.SecondaryLabel,
        ),
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
    )
}

/** A whole-row value control. Left/right adjust the value and never move focus. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PolicyValueRow(
    label: String,
    value: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    focusRequester: FocusRequester,
    bringIntoViewRequester: BringIntoViewRequester?,
    onUp: (() -> Unit)?,
    onDown: (() -> Unit)?,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    PolicyFocusRow(
        focusRequester = focusRequester,
        onClick = {},
        onUp = onUp,
        onDown = onDown,
        onLeft = { if (canDecrease) onDecrease() },
        onRight = { if (canIncrease) onIncrease() },
        modifier = modifier,
        bringIntoViewRequester = bringIntoViewRequester,
        highlighted = highlighted,
    ) { highlighted ->
        val labelColor = if (highlighted) Palette.OnFocus.copy(alpha = 0.72f) else Palette.SecondaryLabel
        val valueColor = if (highlighted) Palette.OnFocus else Palette.Label
        BasicText(
            label,
            style = Type.Body.copy(fontSize = 15.sp, color = labelColor),
            modifier = Modifier.weight(1f),
        )
        BasicText(
            text = when {
                highlighted && canDecrease && canIncrease -> "‹ $value ›"
                highlighted && canDecrease -> "‹ $value"
                highlighted && canIncrease -> "$value ›"
                else -> value
            },
            style = Type.Headline.copy(
                fontSize = 16.sp,
                color = valueColor,
                textAlign = TextAlign.End,
            ),
            maxLines = 1,
            modifier = Modifier.width(190.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PolicyActionRow(
    label: String,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    bringIntoViewRequester: BringIntoViewRequester? = null,
    primary: Boolean = false,
    armed: Boolean = false,
    enabled: Boolean = true,
    trailing: String? = null,
    centered: Boolean = false,
    fontSize: TextUnit = 16.sp,
    highlighted: Boolean = false,
) {
    PolicyFocusRow(
        focusRequester = focusRequester,
        onClick = onClick,
        onUp = onUp,
        onDown = onDown,
        onLeft = onLeft,
        onRight = onRight,
        modifier = modifier,
        bringIntoViewRequester = bringIntoViewRequester,
        normalBackground = if (primary) {
            Palette.Green.copy(alpha = 0.18f)
        } else {
            Palette.Fill.copy(alpha = 0.45f)
        },
        armed = armed,
        enabled = enabled,
        highlighted = highlighted,
    ) { highlighted ->
        val color = if (highlighted) Palette.OnFocus else Palette.Label
        if (centered) {
            BasicText(
                label,
                style = Type.Headline.copy(
                    fontSize = fontSize,
                    color = color,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            BasicText(
                label,
                style = Type.Headline.copy(fontSize = fontSize, color = color),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                BasicText(
                    trailing,
                    style = Type.Headline.copy(fontSize = fontSize, color = color.copy(alpha = 0.7f)),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PolicySwitchRow(
    title: String,
    status: String,
    on: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    armed: Boolean = false,
    highlighted: Boolean = false,
) {
    PolicyFocusRow(
        focusRequester = focusRequester,
        onClick = onClick,
        onUp = onUp,
        onDown = onDown,
        onLeft = onLeft,
        onRight = onRight,
        normalBackground = Palette.Fill.copy(alpha = 0.65f),
        armed = armed,
        highlighted = highlighted,
    ) { highlighted ->
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                title,
                style = Type.Headline.copy(
                    fontSize = 17.sp,
                    color = if (highlighted) Palette.OnFocus else Palette.Label,
                ),
            )
            Spacer(Modifier.size(2.dp))
            BasicText(
                status,
                style = Type.Caption.copy(
                    fontSize = 12.sp,
                    color = if (highlighted) Palette.OnFocus.copy(alpha = 0.72f) else Palette.SecondaryLabel,
                ),
            )
        }
        Box(
            modifier = Modifier
                .width(52.dp)
                .heightIn(min = 28.dp)
                .clip(CircleShape)
                .background(if (on) Palette.Green else Palette.FillStrong),
        ) {
            Box(
                modifier = Modifier
                    .padding(3.dp)
                    .offset(x = if (on) 24.dp else 0.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PolicyModeRow(
    title: String,
    subtitle: String,
    badge: String,
    active: Boolean,
    selected: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    highlighted: Boolean = false,
) {
    val normal = when {
        active -> Palette.Green.copy(alpha = 0.14f)
        selected -> Palette.FillStrong
        else -> Palette.Fill.copy(alpha = 0.4f)
    }
    PolicyFocusRow(
        focusRequester = focusRequester,
        onClick = onClick,
        onUp = onUp,
        onDown = onDown,
        onLeft = onLeft,
        onRight = onRight,
        normalBackground = normal,
        highlighted = highlighted,
    ) { highlighted ->
        if (active) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (highlighted) Palette.OnFocus else Palette.Green),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                title,
                style = Type.Headline.copy(
                    fontSize = 15.sp,
                    color = if (highlighted) Palette.OnFocus else Palette.Label,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(2.dp))
            BasicText(
                subtitle,
                style = Type.Caption.copy(
                    fontSize = 12.sp,
                    color = if (highlighted) Palette.OnFocus.copy(alpha = 0.72f) else Palette.SecondaryLabel,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(
                    if (highlighted) Palette.OnFocus.copy(alpha = 0.08f)
                    else if (active) Palette.Green.copy(alpha = 0.18f)
                    else Palette.Fill.copy(alpha = 0.7f),
                )
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            BasicText(
                badge,
                style = Type.Caption.copy(
                    fontSize = 11.sp,
                    color = if (highlighted) Palette.OnFocus else if (active) Palette.Green else Palette.SecondaryLabel,
                ),
            )
        }
    }
}

@Composable
internal fun PolicyStatusChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        BasicText(text, style = Type.Caption.copy(fontSize = 12.sp, color = color))
    }
}
