package com.novelstudio.core.window.strategy

import com.novelstudio.core.window.platform.DesktopEnvironment

/**
 * Chooses the window chrome strategy for a host.
 *
 * The selector is a pure function over a [DesktopEnvironment] snapshot: no AWT, no Compose, no static probe.
 * It is the single decision point of the window layer and therefore the piece the test suite pins down
 * hardest, because every platform regression shows up here first.
 */
internal object WindowChromeStrategySelector {
    /**
     * Selects the strategy for a host.
     *
     * The order of the rules matters and encodes the platform reality:
     * 1. An explicit request for system decorations always wins, whatever the platform can do.
     * 2. The native Wayland toolkit is checked next, because an undecorated top level is unusable there even
     *    though the runtime advertises a working window move service.
     * 3. A JetBrains Runtime custom title bar is used on Windows and macOS. The check excludes Linux
     *    explicitly rather than trusting the capability flag, mirroring the way the runtime gates its own
     *    services on both the toolkit and the graphics environment.
     * 4. Linux on the X11 toolkit uses an undecorated window, but only when the reflective bridge to
     *    `sun.awt.X11` is usable; without it a real window manager resize is impossible.
     * 5. Everything else falls back to the Compose only chrome.
     *
     * @param environment snapshot of the host.
     * @param preferSystemDecorations `true` to keep the platform decorations regardless of what is possible.
     * @return the strategy to apply.
     */
    fun select(environment: DesktopEnvironment, preferSystemDecorations: Boolean): WindowChromeStrategy = when {
        preferSystemDecorations -> WaylandSystemDecoratedStrategy
        environment.isNativeWayland -> WaylandSystemDecoratedStrategy
        environment.nativeTitleBarSupported && !environment.hostOs.isLinux -> JbrCustomTitleBarStrategy
        environment.supportsNetWmMoveResize -> X11UndecoratedStrategy
        else -> ComposeFallbackStrategy
    }
}
