package com.novelstudio.core.window.modifier

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import com.novelstudio.core.window.jbr.JbrWindowMove
import java.awt.MouseInfo
import java.awt.Window

private const val DOUBLE_CLICK_MIN_MILLIS = 40L
private const val DOUBLE_CLICK_MAX_MILLIS = 500L

/**
 * Turns the caption band of an undecorated window into a real title bar.
 *
 * An undecorated window has no platform caption, so the two gestures a caption normally provides have to be
 * rebuilt. Dragging is handed to the window manager whenever a service answers, which is what brings edge
 * snapping and half tiling back; the client side fallback that repositions the window itself is only used
 * when no service answers, and it deliberately reproduces what the Compose draggable area does.
 *
 * Double clicking is detected before any drag is started, so a maximize toggle never leaves a stray move
 * grab behind. Presses whose change was already consumed are ignored: on the main pass a control inside the
 * title bar has consumed its press before the caption sees it, which is what keeps a click on the close
 * button from dragging the window.
 *
 * Once the window manager owns the gesture no further pointer events arrive for it, so no state may be kept
 * across a release that will never come; the loop is stateless apart from the timestamp of the last press.
 *
 * @param window window the caption belongs to.
 * @param preferSystemWindowMove `true` to try the window manager assisted move before the client side drag.
 * @param onToggleMaximize invoked when the caption is double clicked.
 * @return a modifier to apply to the caption area of the title bar.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.captionDrag(
    window: Window,
    preferSystemWindowMove: Boolean,
    onToggleMaximize: () -> Unit,
): Modifier = pointerInput(window, preferSystemWindowMove) {
    awaitPointerEventScope {
        var lastPressAt = 0L
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            if (event.type != PointerEventType.Press) continue
            if (event.button != PointerButton.Primary) continue
            if (event.changes.any { it.isConsumed }) continue

            val pressedAt = System.currentTimeMillis()
            val elapsed = pressedAt - lastPressAt
            lastPressAt = pressedAt
            if (elapsed in DOUBLE_CLICK_MIN_MILLIS..DOUBLE_CLICK_MAX_MILLIS) {
                lastPressAt = 0L
                onToggleMaximize()
                continue
            }
            if (preferSystemWindowMove && JbrWindowMove.start(window)) continue
            dragFromClientSide(window)
        }
    }
}

private suspend fun AwaitPointerEventScope.dragFromClientSide(window: Window) {
    val pressLocation = MouseInfo.getPointerInfo()?.location ?: return
    val windowOrigin = window.location
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        if (event.type == PointerEventType.Release || event.changes.none { it.pressed }) return
        if (event.type != PointerEventType.Move) continue
        val current = MouseInfo.getPointerInfo()?.location ?: return
        window.setLocation(
            windowOrigin.x + current.x - pressLocation.x,
            windowOrigin.y + current.y - pressLocation.y,
        )
    }
}
