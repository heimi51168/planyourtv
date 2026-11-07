package app.orchard.tv.ui.policy

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type

/**
 * One D-pad rule for grouped controls such as quiz buttons and confirmation
 * dialogs: Up and Left move focus to the previous control, Down and Right to
 * the next. A null neighbour clamps focus on the current control instead of
 * letting Compose's spatial search wander into non-interactive text or off
 * the visible surface.
 *
 * Arrow key-up events are consumed too, so no default focus search can run
 * after the move. Scrollable reading areas keep their own Up/Down scrolling;
 * only the controls in the chain use this rule.
 */
internal fun linearDpadFocus(
    previous: FocusRequester? = null,
    next: FocusRequester? = null,
): (KeyEvent) -> Boolean = { event ->
    when (event.nativeKeyEvent.keyCode) {
        NativeKeyEvent.KEYCODE_DPAD_UP,
        NativeKeyEvent.KEYCODE_DPAD_LEFT -> {
            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                previous?.let { runCatching { it.requestFocus() } }
            }
            true
        }

        NativeKeyEvent.KEYCODE_DPAD_DOWN,
        NativeKeyEvent.KEYCODE_DPAD_RIGHT -> {
            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                next?.let { runCatching { it.requestFocus() } }
            }
            true
        }

        else -> false
    }
}
