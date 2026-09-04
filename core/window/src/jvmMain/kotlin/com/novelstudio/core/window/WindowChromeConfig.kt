package com.novelstudio.core.window

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tuning knobs of the window chrome.
 *
 * @property titleBarHeight height of the caption band. It must match the height the title bar composable
 *   actually renders, because on Windows and macOS the same value is handed to the JetBrains Runtime as the
 *   height of the native caption band: a mismatch makes the operating system hit test a strip of pixels that
 *   is not the title bar. The window layer knows nothing of the design system, so a caller that paints its
 *   title bar with the design-system metrics must pass the same metric here rather than rely on this
 *   default, which exists only so a window can be built without a theme.
 * @property resizeBorderThickness reach of the interactive resize handles around an undecorated window.
 * @property preferSystemDecorations `true` to keep the platform decorations on every host, letting the
 *   desktop theme own the window frame and reducing the application to its content.
 */
@Immutable
data class WindowChromeConfig(
    val titleBarHeight: Dp = 40.dp,
    val resizeBorderThickness: Dp = 6.dp,
    val preferSystemDecorations: Boolean = false,
) {
    init {
        require(titleBarHeight.value > 0f) { "titleBarHeight must be strictly positive, was $titleBarHeight" }
        require(resizeBorderThickness.value >= 0f) {
            "resizeBorderThickness must not be negative, was $resizeBorderThickness"
        }
    }
}
