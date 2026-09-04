package com.novelstudio.core.window.strategy

/**
 * Decorated window with a JetBrains Runtime custom title bar, used on Windows and macOS.
 *
 * The window is deliberately kept decorated. Removing the system decorations would also remove everything
 * the operating system hangs off them, and the runtime rebuilds none of it: dragging, double click to
 * maximize, Aero Snap, edge resize and the window menu all keep working precisely because the frame is still
 * there. The custom title bar only tells the operating system how tall the caption band is and lets the
 * application paint it.
 *
 * The application draws its own controls, so the runtime caption buttons are hidden. That trade is
 * intentional and has one cost, documented in the report: the Windows 11 Snap Layouts flyout is offered by
 * the runtime only while it owns the buttons, because the flyout requires the frame to answer the maximize
 * button hit test, and the public interface can only classify a point as caption or as client.
 */
internal object JbrCustomTitleBarStrategy : WindowChromeStrategy(
    id = "jbr-custom-title-bar",
    undecorated = false,
    usesSystemWindowControls = false,
    installsNativeTitleBar = true,
    rendersTitleBar = true,
    usesSystemWindowMove = false,
    usesX11InteractiveResize = false,
    usesComposeResizer = false,
    requiresManualMaximizeGesture = false,
)
