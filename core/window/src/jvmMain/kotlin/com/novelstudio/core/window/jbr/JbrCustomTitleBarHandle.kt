package com.novelstudio.core.window.jbr

import com.jetbrains.JBR
import com.jetbrains.WindowDecorations
import java.awt.Frame

/**
 * Live handle on a JetBrains Runtime native custom title bar installed on a frame.
 *
 * A custom title bar keeps the window decorated. The operating system therefore keeps ownership of the
 * caption band and of everything that hangs off it: dragging, double click to maximize, Aero Snap and
 * interactive edge resize all stay native, while the pixels of the band are painted by Compose.
 *
 * The runtime asks the application to classify every pointer event over the band as either non client, so
 * the system performs its caption actions, or client, so the application receives the event. That
 * classification is not sticky and must be pushed on every event, which is the job of the caption modifier
 * built on top of this handle.
 */
internal class JbrCustomTitleBarHandle private constructor(
    private val frame: Frame,
    private val titleBar: WindowDecorations.CustomTitleBar,
) {
    /**
     * Width reserved by the runtime on the leading edge of the caption for its own controls.
     *
     * The value collapses to zero when the controls are hidden, which is the configuration this product
     * uses, and is expressed in logical units so it converts to `Dp` directly.
     */
    val leadingInset: Float get() = titleBar.leftInset

    /** Width reserved by the runtime on the trailing edge of the caption, in logical units. */
    val trailingInset: Float get() = titleBar.rightInset

    /**
     * Classifies the pointer position for the next native hit test.
     *
     * @param overClientArea `true` when the pointer is over an interactive control, which suppresses the
     *   native caption actions; `false` when it is over empty caption pixels, which enables dragging,
     *   double click to maximize and snapping.
     */
    fun forceHitTest(overClientArea: Boolean) {
        titleBar.forceHitTest(overClientArea)
    }

    /**
     * Detaches the custom title bar and restores the system title bar of the frame.
     *
     * @return `true` when the runtime accepted the reset, `false` when the service became unavailable.
     */
    fun remove(): Boolean {
        val decorations = JBR.getWindowDecorations() ?: return false
        return try {
            decorations.setCustomTitleBar(frame, null)
            true
        } catch (runtimeError: RuntimeException) {
            false
        } catch (linkageError: LinkageError) {
            false
        }
    }

    /** Installs custom title bars, returning `null` on any platform that cannot host one. */
    companion object {
        private const val CONTROLS_VISIBLE = "controls.visible"

        /**
         * Installs a native custom title bar on [frame].
         *
         * The height must be expressed in logical units, that is `Dp.value` and never `Dp.toPx()`: the
         * runtime multiplies the value by the per monitor scale factor of the screen the window is on, so a
         * pre-scaled value is scaled a second time and the caption band ends up twice too tall on a high
         * density display. The height must also be strictly positive and set before the bar is attached,
         * which the runtime enforces.
         *
         * The runtime caption buttons are hidden because this product draws its own controls in Compose.
         * That choice costs the Windows 11 Snap Layouts flyout, which the runtime only offers while it owns
         * the buttons, and it collapses both insets to zero.
         *
         * @param frame frame to decorate; it must have been created decorated.
         * @param titleBarHeight caption height in logical units, strictly positive.
         * @param controlsVisible `true` to let the runtime paint its own caption buttons.
         * @return the handle, or `null` when the platform has no custom title bar implementation or when the
         *   runtime refused the installation.
         * @throws IllegalArgumentException when [titleBarHeight] is not strictly positive.
         */
        fun install(frame: Frame, titleBarHeight: Float, controlsVisible: Boolean = false): JbrCustomTitleBarHandle? {
            require(titleBarHeight > 0f) { "Custom title bar height must be strictly positive, was $titleBarHeight" }
            if (!JbrAvailability.isCustomTitleBarSupported) return null
            val decorations = JBR.getWindowDecorations() ?: return null
            return try {
                val titleBar = decorations.createCustomTitleBar()
                titleBar.height = titleBarHeight
                titleBar.putProperty(CONTROLS_VISIBLE, controlsVisible)
                decorations.setCustomTitleBar(frame, titleBar)
                JbrCustomTitleBarHandle(frame, titleBar)
            } catch (runtimeError: RuntimeException) {
                null
            } catch (linkageError: LinkageError) {
                null
            }
        }
    }
}
