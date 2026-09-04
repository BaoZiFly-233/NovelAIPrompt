package com.novelstudio.core.window.strategy

/**
 * Undecorated window driven by the window manager, used on Linux with the X11 toolkit.
 *
 * The JetBrains Runtime implements its custom title bar only for Windows and macOS, so a Linux window that
 * wants a Compose caption has to drop its decorations. An undecorated X11 window is still a normal managed
 * top level, because AWT clears the Motif decoration hints rather than making the window override redirect:
 * extended state changes, keyboard tiling and the whole EWMH surface keep working. What is lost is the
 * frame, and with it the three gestures the window manager used to run from it.
 *
 * All three are given back explicitly. Dragging is handed to the window manager, which restores edge
 * snapping and half tiling. Edge resizing is handed to the window manager through the EWMH
 * `_NET_WM_MOVERESIZE` client message, which is a real resize grab rather than the client side loop the
 * Compose resizer performs. The double click to maximize gesture is implemented by the application because
 * no caption exists to produce it.
 */
internal object X11UndecoratedStrategy : WindowChromeStrategy(
    id = "x11-undecorated",
    undecorated = true,
    usesSystemWindowControls = false,
    installsNativeTitleBar = false,
    rendersTitleBar = true,
    usesSystemWindowMove = true,
    usesX11InteractiveResize = true,
    usesComposeResizer = false,
    requiresManualMaximizeGesture = true,
)
