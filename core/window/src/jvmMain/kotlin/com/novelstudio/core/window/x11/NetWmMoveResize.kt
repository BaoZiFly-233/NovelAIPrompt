package com.novelstudio.core.window.x11

import com.novelstudio.core.window.platform.ResizeEdge
import java.awt.Window

/**
 * Window manager assisted move and resize through the EWMH `_NET_WM_MOVERESIZE` client message.
 *
 * This is what an undecorated window is missing on X11 and what the Compose undecorated window resizer does
 * not do. The Compose resizer repeatedly calls `setLocation` and `setSize` from the client side, so the
 * window manager never enters a resize grab: there is no snapping while resizing, no interaction with tiling
 * and a visible lag against a real window manager resize. Sending the client message instead hands the whole
 * gesture to the window manager, which then behaves exactly as if the user had grabbed a real decoration.
 *
 * The payload is fixed by the specification and carries, in this order, the pointer position in root
 * coordinates, the direction, the pressed button number and a source indication. Two details decide whether
 * it works at all:
 * - the coordinates must be root coordinates in device pixels, so they are read from the X server itself
 *   rather than converted from the user space pixels AWT and Compose report; the conversion is anchored on
 *   the origin of the screen the pointer is on, which no scale factor alone can express;
 * - every grab must be released before sending, otherwise the implicit pointer grab installed by the button
 *   press keeps the window manager from taking over on Xorg.
 *
 * Two consequences are worth remembering at the call site. A window manager ignores resize directions while
 * the window is maximized, so the caller must disable its handles unless the window is floating. And once
 * the window manager owns the pointer the application receives no further events for that gesture, so any
 * drag state must be reset on the next press rather than on a release that will never arrive.
 */
internal object NetWmMoveResize {
    /** X11 button number of the primary mouse button. */
    const val BUTTON_PRIMARY: Int = 1

    private const val SOURCE_INDICATION_APPLICATION = 1L
    private const val NO_COORDINATE = 0L

    /**
     * Asks the window manager to perform an interactive move of a window.
     *
     * Prefer the JetBrains Runtime window move service when it is available: it covers Wayland as well and
     * needs no reflection. This entry point exists for runtimes that do not ship the service.
     *
     * The gesture is anchored on the pointer position read from the X server, so it must be sent while the
     * button that started it is still held down.
     *
     * @param window window to move; it must be displayable.
     * @param button X11 number of the button currently held down.
     * @return `true` when the request was sent to the window manager.
     */
    fun startMove(window: Window, button: Int = BUTTON_PRIMARY): Boolean =
        send(window, NetWmMoveResizeDirection.MOVE, button, releaseGrabs = true)

    /**
     * Asks the window manager to perform an interactive resize of a window.
     *
     * The gesture is anchored on the pointer position read from the X server, so it must be sent while the
     * button that started it is still held down.
     *
     * @param window window to resize; it must be displayable and floating, because window managers ignore
     *   resize directions on a maximized window.
     * @param edge handle the user grabbed.
     * @param button X11 number of the button currently held down.
     * @return `true` when the request was sent to the window manager.
     */
    fun startResize(window: Window, edge: ResizeEdge, button: Int = BUTTON_PRIMARY): Boolean = send(
        window = window,
        direction = NetWmMoveResizeDirection.forEdge(edge),
        button = button,
        releaseGrabs = true,
    )

    /**
     * Cancels a move or resize the window manager is still performing.
     *
     * The specification sanctions this message explicitly, because there is a race between the client asking
     * for a gesture and the user releasing the button. It is the only message of the family sent while the
     * grabs are kept, and the only one that carries no pointer position.
     *
     * @param window window whose gesture must be cancelled.
     * @return `true` when the request was sent to the window manager.
     */
    fun cancel(window: Window): Boolean = send(
        window = window,
        direction = NetWmMoveResizeDirection.CANCEL,
        button = BUTTON_PRIMARY,
        releaseGrabs = false,
    )

    private fun send(
        window: Window,
        direction: NetWmMoveResizeDirection,
        button: Int,
        releaseGrabs: Boolean,
    ): Boolean {
        val handle = X11WindowHandle.of(window) ?: return false
        val atom = X11Atoms.netWmMoveResize ?: return false
        val (deviceX, deviceY) = when (direction) {
            NetWmMoveResizeDirection.CANCEL -> NO_COORDINATE to NO_COORDINATE
            else -> X11Reflection.pointerRootLocation() ?: return false
        }
        return X11Reflection.sendClientMessageToRoot(
            targetWindowId = handle.id,
            messageType = atom,
            data = longArrayOf(
                deviceX,
                deviceY,
                direction.value.toLong(),
                button.toLong(),
                SOURCE_INDICATION_APPLICATION,
            ),
            releaseGrabs = releaseGrabs,
        )
    }
}
