package com.novelstudio.core.window

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.novelstudio.core.window.diagnostics.WindowChromeReport
import com.novelstudio.core.window.modifier.resizePointerIcon
import com.novelstudio.core.window.platform.ResizeEdge

/**
 * Everything a title bar and a window content need in order to behave like a real window.
 *
 * An instance is created by [DecoratedWindow] and handed to both composables. It exposes the observable
 * window state, the two modifiers that carry the platform gestures and the three window actions, so a title
 * bar never has to know which platform it is running on.
 *
 * The modifiers are the important part of the contract. [captionModifier] must be applied to the caption
 * area of the title bar, because that is where the platform gesture bridge lives; forgetting it costs
 * dragging and the maximize gesture. [resizeModifier] must be applied to the window root, because the resize
 * handles measure themselves against the full window.
 *
 * @property controlsMode who paints the window controls on this platform.
 * @property report diagnostic snapshot explaining the selected strategy and its trade-offs.
 * @param initialState state the chrome starts with, before the first composition refreshes it.
 * @param onMinimize action iconifying the window.
 * @param onToggleMaximize action toggling between the maximized and the floating placement.
 * @param onClose action requesting the window to close.
 */
@Stable
class WindowChrome internal constructor(
    val controlsMode: WindowControlsMode,
    val report: WindowChromeReport,
    initialState: WindowChromeState,
    private val onMinimize: () -> Unit,
    private val onToggleMaximize: () -> Unit,
    private val onClose: () -> Unit,
) {
    private var currentState by mutableStateOf(initialState)
    private var currentControlsStartInset by mutableStateOf(0.dp)
    private var currentControlsEndInset by mutableStateOf(0.dp)
    private var currentCaptionModifier by mutableStateOf<Modifier>(Modifier)
    private var resizeHandlesModifier by mutableStateOf<Modifier>(Modifier)
    private var hoveredResizeEdge by mutableStateOf<ResizeEdge?>(null)

    /** Observable state of the window: focus, placement and the identifier of the active strategy. */
    val state: WindowChromeState get() = currentState

    /**
     * Space reserved on the leading edge of the caption by platform drawn controls.
     *
     * It is zero on every strategy of this product, because the application always draws its own controls
     * and therefore asks the platform to hide them, which collapses the reserved insets.
     */
    val controlsStartInset: Dp get() = currentControlsStartInset

    /** Space reserved on the trailing edge of the caption by platform drawn controls. */
    val controlsEndInset: Dp get() = currentControlsEndInset

    /**
     * Modifier carrying the caption gestures, to apply to the caption area of the title bar.
     *
     * Depending on the platform it either feeds the native hit test of a JetBrains Runtime custom title bar
     * or implements dragging and the double click maximize gesture directly.
     */
    val captionModifier: Modifier get() = currentCaptionModifier

    /**
     * Modifier carrying the resize handles and the resize cursors, to apply to the window root.
     *
     * It is empty when the platform or Compose already handles resizing.
     */
    val resizeModifier: Modifier
        get() = when (val edge = hoveredResizeEdge) {
            null -> resizeHandlesModifier
            else -> resizeHandlesModifier.pointerHoverIcon(resizePointerIcon(edge))
        }

    /** Iconifies the window. */
    fun minimize() {
        onMinimize()
    }

    /** Toggles the window between the maximized and the floating placement. */
    fun toggleMaximize() {
        onToggleMaximize()
    }

    /** Requests the window to close, going through the close handler of the application. */
    fun close() {
        onClose()
    }

    /**
     * Refreshes the observable window state.
     *
     * @param isActive `true` while the window holds the focus.
     * @param isMaximized `true` while the window is maximized.
     */
    internal fun updateState(isActive: Boolean, isMaximized: Boolean) {
        currentState = currentState.copy(isActive = isActive, isMaximized = isMaximized)
    }

    /**
     * Refreshes the insets reserved by platform drawn window controls.
     *
     * @param start space reserved on the leading edge of the caption.
     * @param end space reserved on the trailing edge of the caption.
     */
    internal fun updateControlsInsets(start: Dp, end: Dp) {
        currentControlsStartInset = start
        currentControlsEndInset = end
    }

    /**
     * Installs the modifier that carries the caption gestures.
     *
     * @param modifier gesture modifier, or [Modifier] when the platform owns the caption entirely.
     */
    internal fun updateCaptionModifier(modifier: Modifier) {
        currentCaptionModifier = modifier
    }

    /**
     * Installs the modifier that carries the resize handles.
     *
     * @param modifier handles modifier, or [Modifier] when resizing is not handled by this layer.
     */
    internal fun updateResizeHandlesModifier(modifier: Modifier) {
        resizeHandlesModifier = modifier
    }

    /**
     * Records which resize handle the pointer currently hovers, so the cursor can announce it.
     *
     * @param edge hovered handle, or `null` when the pointer is in the window interior.
     */
    internal fun updateHoveredResizeEdge(edge: ResizeEdge?) {
        hoveredResizeEdge = edge
    }
}
