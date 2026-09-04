package com.novelstudio.core.window.jbr

import com.jetbrains.JBR
import java.awt.Window

/**
 * Optional rounded window corners, provided by the JetBrains Runtime rounded corners service.
 *
 * The service covers macOS, Windows 11 and the Wayland toolkit. It is never available on X11, where corner
 * rounding is a decision of the window manager and cannot be requested by a client, so every call on that
 * platform returns `false` and the window simply keeps square corners.
 */
internal object JbrRoundedCorners {
    /** Platform default corner treatment, which is what a well behaved application should ask for. */
    const val STYLE_DEFAULT: String = "default"

    /** Square corners, explicitly opting out of any platform rounding. */
    const val STYLE_NONE: String = "none"

    /** Large corner radius. */
    const val STYLE_FULL: String = "full"

    /** Small corner radius. */
    const val STYLE_SMALL: String = "small"

    /** `true` when the platform can round window corners on request. */
    val isSupported: Boolean get() = JbrAvailability.isRoundedCornersSupported

    /**
     * Requests a corner treatment for a window.
     *
     * @param window window to restyle; it must be displayable.
     * @param style one of the `STYLE_` constants, a `Float` radius, or an array holding a radius, a border
     *   width and a border colour, depending on what the platform accepts.
     * @return `true` when the request reached the service, `false` when the platform does not support it.
     */
    fun apply(window: Window, style: Any = STYLE_DEFAULT): Boolean {
        if (!isSupported) return false
        return try {
            val service = JBR.getRoundedCornersManager() ?: return false
            service.setRoundedCorners(window, style)
            true
        } catch (runtimeError: RuntimeException) {
            false
        } catch (linkageError: LinkageError) {
            false
        }
    }
}
