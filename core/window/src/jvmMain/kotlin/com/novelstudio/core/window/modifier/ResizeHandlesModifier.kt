package com.novelstudio.core.window.modifier

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import com.novelstudio.core.window.platform.ResizeEdge
import com.novelstudio.core.window.platform.ResizeEdgeResolver
import com.novelstudio.core.window.x11.NetWmMoveResize
import java.awt.Cursor
import java.awt.Window

private val NorthWestResize = PointerIcon(Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR))
private val NorthResize = PointerIcon(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR))
private val NorthEastResize = PointerIcon(Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR))
private val EastResize = PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR))
private val SouthEastResize = PointerIcon(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR))
private val SouthResize = PointerIcon(Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR))
private val SouthWestResize = PointerIcon(Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR))
private val WestResize = PointerIcon(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR))

/**
 * Places the eight interactive resize handles of an undecorated window on the window root.
 *
 * The handles do not resize anything themselves. They only decide which border the pointer is on and hand
 * the gesture to the window manager, which then performs a real resize grab with all the desktop behaviour
 * that implies. This is the piece the Compose undecorated window resizer does not provide: that one loops on
 * the client side, calling `setLocation` and `setSize` while reading global pointer coordinates, so it can
 * neither snap nor keep up with the compositor.
 *
 * Events are observed on the initial pass, which runs from the root inwards, so the border always wins over
 * whatever content happens to be painted underneath it, and the press is consumed once the gesture has been
 * handed over. Resizing is refused while the window is not floating, because window managers ignore resize
 * directions on a maximized window and the resulting flicker is a known Compose defect.
 *
 * @param window window to resize.
 * @param borderThickness reach of the border handles.
 * @param onHoverEdgeChange notified with the handle under the pointer, or `null` when the pointer is not on
 *   a handle, so the caller can update the cursor.
 * @param canResize consulted before every gesture; return `false` while the window is not floating.
 * @return a modifier to apply to the window root.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.windowResizeHandles(
    window: Window,
    borderThickness: Dp,
    onHoverEdgeChange: (ResizeEdge?) -> Unit,
    canResize: () -> Boolean,
): Modifier = pointerInput(window, borderThickness) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type == PointerEventType.Exit || event.type == PointerEventType.Scroll) {
                onHoverEdgeChange(null)
                continue
            }
            val position = event.changes.lastOrNull()?.position
            val edge = when {
                position == null || !canResize() -> null
                else -> ResizeEdgeResolver.resolve(
                    x = position.x,
                    y = position.y,
                    width = size.width.toFloat(),
                    height = size.height.toFloat(),
                    borderThickness = borderThickness.toPx(),
                )
            }
            onHoverEdgeChange(edge)
            if (event.type != PointerEventType.Press || edge == null) continue
            if (event.button != PointerButton.Primary || event.changes.any { it.isConsumed }) continue

            if (NetWmMoveResize.startResize(window, edge)) {
                event.changes.forEach { it.consume() }
                onHoverEdgeChange(null)
            }
        }
    }
}

/**
 * Maps a resize handle to the cursor that announces it.
 *
 * @param edge handle under the pointer, or `null` when the pointer is in the window interior.
 * @return the matching pointer icon, or the default icon when no handle is hovered.
 */
internal fun resizePointerIcon(edge: ResizeEdge?): PointerIcon = when (edge) {
    null -> PointerIcon.Default
    ResizeEdge.TOP_LEFT -> NorthWestResize
    ResizeEdge.TOP -> NorthResize
    ResizeEdge.TOP_RIGHT -> NorthEastResize
    ResizeEdge.RIGHT -> EastResize
    ResizeEdge.BOTTOM_RIGHT -> SouthEastResize
    ResizeEdge.BOTTOM -> SouthResize
    ResizeEdge.BOTTOM_LEFT -> SouthWestResize
    ResizeEdge.LEFT -> WestResize
}
