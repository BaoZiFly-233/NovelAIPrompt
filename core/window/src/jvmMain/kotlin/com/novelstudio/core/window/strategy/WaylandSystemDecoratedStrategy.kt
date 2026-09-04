package com.novelstudio.core.window.strategy

/**
 * System decorated window, used on native Wayland and whenever the caller asks for system decorations.
 *
 * An undecorated top level is unusable under the Wayland toolkit. The runtime gives such a window the
 * minimal frame decoration, which reports zero content insets, paints nothing and, decisively, exposes no
 * resize edges at all. On top of that Wayland forbids a client from positioning its own top levels, so
 * programmatic placement silently does nothing, and pointer coordinates are reported relative to a surface,
 * so the client side resizer of Compose has neither a way to move the window nor reliable global
 * coordinates.
 *
 * The only sound answer is to keep the decorations and let the compositor draw them, either server side when
 * the compositor offers that, or through the client side decorations the runtime paints itself. The
 * application then contributes content only and never renders a title bar, and the loss of programmatic
 * positioning is recorded in the report so a partially restored window geometry can be explained.
 *
 * The same strategy is selected on any platform when system decorations are requested explicitly, which is
 * the escape hatch for users who want their desktop theme to own the window frame.
 */
internal object WaylandSystemDecoratedStrategy : WindowChromeStrategy(
    id = "system-decorated",
    undecorated = false,
    usesSystemWindowControls = true,
    installsNativeTitleBar = false,
    rendersTitleBar = false,
    usesSystemWindowMove = false,
    usesX11InteractiveResize = false,
    usesComposeResizer = false,
    requiresManualMaximizeGesture = false,
)
