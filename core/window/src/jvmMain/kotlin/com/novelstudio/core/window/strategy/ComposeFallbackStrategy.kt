package com.novelstudio.core.window.strategy

/**
 * Undecorated window served entirely by Compose, used on any runtime that offers no platform assistance.
 *
 * This is the strategy for a stock JDK, for a Linux host whose `sun.awt` internals are not open, and for any
 * platform that could not be identified. The window drops its decorations, the application paints the title
 * bar and the controls, and edge resize is left to the Compose undecorated window resizer.
 *
 * The gestures are honest about their limits. The resizer moves and resizes the window from the client side,
 * so it never triggers snapping and shows a visible lag against a window manager resize. Dragging still
 * prefers the JetBrains Runtime window move service when one answers, exactly like the Compose draggable
 * area does, and only falls back to repositioning the window itself when it does not.
 */
internal object ComposeFallbackStrategy : WindowChromeStrategy(
    id = "compose-fallback",
    undecorated = true,
    usesSystemWindowControls = false,
    installsNativeTitleBar = false,
    rendersTitleBar = true,
    usesSystemWindowMove = true,
    usesX11InteractiveResize = false,
    usesComposeResizer = true,
    requiresManualMaximizeGesture = true,
)
