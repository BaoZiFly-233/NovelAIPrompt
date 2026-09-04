package com.novelstudio.core.window.jbr

import com.jetbrains.JBR
import java.awt.Window
import java.awt.event.MouseEvent

/**
 * Window manager assisted interactive move, provided by the JetBrains Runtime on both Unix toolkits.
 *
 * This is the correct way to drag an undecorated window on Linux. Repositioning the window from the client
 * side with `Window.setLocation` never enters the window manager move grab, so the desktop never runs its
 * snapping and tiling heuristics and the window is clamped to the work area. Handing the gesture over to the
 * compositor restores both: on X11 the runtime sends an EWMH `_NET_WM_MOVERESIZE` client message with the
 * move direction, and on Wayland it issues an `xdg_toplevel.move` request.
 *
 * The runtime documents three preconditions, none of which can be checked from here: the window manager must
 * support the protocol, the pointer must be inside the window bounds and the requested mouse button must
 * still be held down. Calling the service on a released button is harmless but does nothing, which is why
 * the caller must invoke it from the press event itself.
 *
 * There is no matching public resize service. Interactive resizing has to go through the X11 layer.
 */
internal object JbrWindowMove {
    /** `true` when the runtime can start a window manager assisted move on this platform. */
    val isSupported: Boolean get() = JbrAvailability.isWindowMoveSupported

    /**
     * Hands an in-progress mouse gesture over to the window manager as an interactive window move.
     *
     * @param window window to move; it must be displayable.
     * @param mouseButton AWT button constant of the button currently held down.
     * @return `true` when the request reached the service, `false` when the service is unavailable or
     *   refused it, in which case the caller must fall back to a client side drag.
     */
    fun start(window: Window, mouseButton: Int = MouseEvent.BUTTON1): Boolean {
        if (!isSupported) return false
        return try {
            val service = JBR.getWindowMove() ?: return false
            service.startMovingTogetherWithMouse(window, mouseButton)
            true
        } catch (runtimeError: RuntimeException) {
            false
        } catch (linkageError: LinkageError) {
            false
        }
    }
}
