package com.novelstudio.core.window.x11

import java.awt.Window

/**
 * Identifier of the X11 shell window backing an AWT window.
 *
 * The shell window is the top level the window manager manages and reparents, which is the one every EWMH
 * client message must address. The content window of the same peer is a different resource and addressing it
 * makes the window manager silently ignore the request.
 *
 * @property id non-zero X window identifier.
 */
@JvmInline
internal value class X11WindowHandle private constructor(val id: Long) {
    /** Resolves handles from AWT windows, returning `null` whenever the window has no X11 peer. */
    companion object {
        /**
         * Resolves the shell window identifier of an AWT window.
         *
         * @param window window to resolve; it must be displayable, because a window that was never shown has
         *   no peer and therefore no X resource.
         * @return the handle, or `null` when the runtime is not on X11 or the reflective bridge is disabled.
         */
        fun of(window: Window): X11WindowHandle? = X11Reflection.windowId(window)?.let { X11WindowHandle(it) }
    }
}
