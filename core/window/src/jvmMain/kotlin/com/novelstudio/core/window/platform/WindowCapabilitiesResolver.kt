package com.novelstudio.core.window.platform

/**
 * Turns a [DesktopEnvironment] into the concrete set of [WindowCapabilities] the chrome can rely on.
 *
 * Both functions are pure: same input, same output, no AWT, no Compose, no static state. They carry the
 * behavioural knowledge of the whole window layer and are therefore the natural place for the test suite to
 * pin the platform matrix down.
 */
internal object WindowCapabilitiesResolver {
    /**
     * Resolves the capability matrix for a host.
     *
     * The rules follow what the platforms actually implement:
     * - Windows and macOS with a JetBrains Runtime install a native custom title bar, so the operating
     *   system keeps drag, double click maximize and edge resize. Windows 11 Snap Layouts are the single
     *   casualty, because they require the runtime to own the caption buttons and this product draws them
     *   in Compose.
     * - Linux on the X11 toolkit runs undecorated. Drag is delegated to the window manager through the
     *   JetBrains Runtime window move service and resize through the EWMH `_NET_WM_MOVERESIZE` client
     *   message, which is what brings snapping and tiling back; the double click gesture has to be
     *   implemented by the application because no window manager caption exists any more.
     * - Linux on the Wayland toolkit keeps system decorations, because an undecorated Wayland top level has
     *   no resize edges, cannot be positioned programmatically and reports degraded pointer coordinates.
     * - Anything else falls back to the Compose client side resizer.
     *
     * A window asked to keep the platform decorations short circuits all of it: the desktop then owns the
     * whole frame, exactly as it does on native Wayland, and the only difference between the two is that an
     * X11 or Windows desktop still honours a programmatic position.
     *
     * @param environment snapshot of the host.
     * @param preferSystemDecorations `true` when the window keeps the platform decorations regardless of what
     *   the host could otherwise offer.
     * @return the capabilities available on that host.
     */
    fun resolve(environment: DesktopEnvironment, preferSystemDecorations: Boolean = false): WindowCapabilities = when {
        preferSystemDecorations || environment.isNativeWayland -> systemDecoratedCapabilities(environment)
        environment.nativeTitleBarSupported && !environment.hostOs.isLinux -> nativeTitleBarCapabilities(environment)
        environment.supportsNetWmMoveResize -> x11Capabilities(environment)
        else -> fallbackCapabilities(environment)
    }

    /**
     * Describes, in plain English, every feature the current host forced the chrome to give up.
     *
     * The list is empty when nothing had to be sacrificed. It is surfaced by the diagnostics screen so a
     * user reporting a problem can say exactly which degraded path was taken.
     *
     * Everything the custom chrome gives up is only reported when the custom chrome is actually used. A window
     * that keeps the platform decorations never installs a native title bar, never sends an EWMH message and
     * never hides the runtime caption buttons, so those sentences would describe a code path it does not take.
     * What the desktop itself imposes, on native Wayland and on XWayland, is reported either way.
     *
     * @param environment snapshot of the host.
     * @param preferSystemDecorations `true` when the window keeps the platform decorations regardless of what
     *   the host could otherwise offer.
     * @return one sentence per degradation, in a stable order.
     */
    fun degradations(environment: DesktopEnvironment, preferSystemDecorations: Boolean = false): List<String> = buildList {
        if (!preferSystemDecorations && !environment.jbrAvailable) {
            add(
                "The JetBrains Runtime is not available: the native custom title bar and the window manager " +
                    "assisted window move are both disabled.",
            )
        }
        if (environment.isNativeWayland) {
            add(
                "Native Wayland (WLToolkit) cannot host an undecorated top level window: the compositor draws " +
                    "the title bar and the window controls, and the application renders content only.",
            )
            add(
                "Wayland does not allow a client to position its own top level windows, so a saved window " +
                    "position cannot be restored; only the size is honoured.",
            )
        }
        if (!preferSystemDecorations && environment.hostOs.isLinux && environment.toolkit.isX11) {
            if (!environment.x11ReflectionAvailable) {
                add(
                    "The sun.awt.X11 internals are not reachable, most likely because " +
                        "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED is missing: interactive edge resize " +
                        "falls back to the Compose client side resizer, which cannot snap.",
                )
            }
            if (!environment.nativeWindowMoveSupported) {
                add(
                    "The JetBrains Runtime window move service is unavailable: dragging the title bar " +
                        "repositions the window from the client side, so edge snapping and half tiling are lost.",
                )
            }
        }
        if (environment.isXWayland) {
            add(
                "Running on XWayland: the compositor may ignore programmatic window positioning, so a restored " +
                    "window position can be adjusted by the desktop.",
            )
        }
        if (!preferSystemDecorations && environment.nativeTitleBarSupported && environment.hostOs.isWindows) {
            add(
                "The application draws its own window controls, so the JetBrains Runtime caption buttons are " +
                    "hidden (controls.visible = false) and Windows 11 Snap Layouts are not offered.",
            )
        }
    }

    private fun nativeTitleBarCapabilities(environment: DesktopEnvironment) = WindowCapabilities(
        nativeTitleBar = true,
        systemWindowDrag = true,
        systemInteractiveResize = true,
        systemDoubleClickMaximize = true,
        windowManagerSnapping = true,
        composeClientSideResize = false,
        programmaticPositioning = true,
        roundedCorners = environment.roundedCornersSupported,
    )

    private fun x11Capabilities(environment: DesktopEnvironment) = WindowCapabilities(
        nativeTitleBar = false,
        systemWindowDrag = environment.nativeWindowMoveSupported,
        systemInteractiveResize = true,
        systemDoubleClickMaximize = false,
        windowManagerSnapping = environment.nativeWindowMoveSupported,
        composeClientSideResize = false,
        programmaticPositioning = true,
        roundedCorners = false,
    )

    private fun systemDecoratedCapabilities(environment: DesktopEnvironment) = WindowCapabilities(
        nativeTitleBar = false,
        systemWindowDrag = true,
        systemInteractiveResize = true,
        systemDoubleClickMaximize = true,
        windowManagerSnapping = true,
        composeClientSideResize = false,
        programmaticPositioning = !environment.isNativeWayland,
        roundedCorners = environment.roundedCornersSupported,
    )

    private fun fallbackCapabilities(environment: DesktopEnvironment) = WindowCapabilities(
        nativeTitleBar = false,
        systemWindowDrag = environment.nativeWindowMoveSupported,
        systemInteractiveResize = false,
        systemDoubleClickMaximize = false,
        windowManagerSnapping = environment.nativeWindowMoveSupported,
        composeClientSideResize = true,
        programmaticPositioning = true,
        roundedCorners = environment.roundedCornersSupported,
    )
}
