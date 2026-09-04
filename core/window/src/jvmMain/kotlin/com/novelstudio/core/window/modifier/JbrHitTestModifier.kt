package com.novelstudio.core.window.modifier

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import com.novelstudio.core.window.jbr.JbrCustomTitleBarHandle

/**
 * Bridges Compose pointer events to the hit test of a JetBrains Runtime custom title bar.
 *
 * The runtime answers the operating system hit test for the caption band from a flag the application must
 * refresh, and that flag is not sticky: it describes the very next hit test and nothing more. It therefore
 * has to be pushed on every pointer event except the two that carry no meaningful position for the caption,
 * a pointer exit and a scroll.
 *
 * Classification uses consumption as its signal. The main pass runs from the innermost node outwards, so a
 * control that handled the event has already consumed its change by the time the caption sees it: an
 * unconsumed change means empty caption pixels, which must be reported as non client so the operating system
 * performs the drag, the double click maximize and the snapping itself.
 *
 * A press that landed on a control latches the classification until the matching release. Without that latch
 * the pointer leaving the control while the button is down would suddenly report empty caption and the
 * operating system would hijack the gesture into a window drag.
 *
 * @param handle handle on the installed custom title bar.
 * @return a modifier to apply to the caption area of the title bar.
 */
internal fun Modifier.jbrCaptionHitTest(handle: JbrCustomTitleBarHandle): Modifier = pointerInput(handle) {
    awaitPointerEventScope {
        var pressedOnControl = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            when (event.type) {
                PointerEventType.Exit, PointerEventType.Scroll -> continue
                PointerEventType.Press -> pressedOnControl = event.changes.any { it.isConsumed }
                PointerEventType.Release -> pressedOnControl = false
                else -> Unit
            }
            handle.forceHitTest(pressedOnControl || event.changes.any { it.isConsumed })
        }
    }
}
