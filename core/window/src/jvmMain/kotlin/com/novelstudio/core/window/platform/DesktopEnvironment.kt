package com.novelstudio.core.window.platform

/**
 * Immutable snapshot of everything the window layer needs to know about the host.
 *
 * The snapshot is plain data on purpose: it is produced once by [DesktopEnvironmentDetector], then consumed
 * by pure resolvers ([WindowCapabilitiesResolver] and the strategy selector) that can be unit tested without
 * AWT, without a display server and without a JetBrains Runtime.
 *
 * @property hostOs operating system family.
 * @property operatingSystem human-readable operating system name and version, for diagnostics.
 * @property javaVendor vendor of the running Java runtime.
 * @property javaVersion version of the running Java runtime.
 * @property toolkit AWT toolkit AWT actually bound to.
 * @property toolkitClassName raw toolkit class name, kept verbatim for diagnostics.
 * @property sessionType display session advertised by the desktop, meaningful on Linux only.
 * @property jbrAvailable `true` when the JetBrains Runtime API answers, that is when the JVM is a JBR build.
 * @property nativeTitleBarSupported `true` when `WindowDecorations.CustomTitleBar` can be installed, which is
 *   Windows and macOS only; the JetBrains Runtime has no Unix implementation of the service.
 * @property nativeWindowMoveSupported `true` when `WindowMove.startMovingTogetherWithMouse` is available,
 *   which the JetBrains Runtime provides on both the X11 and the Wayland toolkits.
 * @property roundedCornersSupported `true` when `RoundedCornersManager` is available.
 * @property x11ReflectionAvailable `true` when `sun.awt.X11` internals could be reflected on, which requires
 *   `--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED` and `--add-opens=java.desktop/sun.awt=ALL-UNNAMED`.
 */
internal data class DesktopEnvironment(
    val hostOs: HostOs,
    val operatingSystem: String,
    val javaVendor: String,
    val javaVersion: String,
    val toolkit: AwtToolkitKind,
    val toolkitClassName: String,
    val sessionType: LinuxSessionType,
    val jbrAvailable: Boolean,
    val nativeTitleBarSupported: Boolean,
    val nativeWindowMoveSupported: Boolean,
    val roundedCornersSupported: Boolean,
    val x11ReflectionAvailable: Boolean,
) {
    /**
     * `true` when an X11 toolkit is talking to a Wayland compositor through XWayland.
     *
     * This is the default situation for a Compose Desktop application launched in a Wayland session, because
     * the JetBrains Runtime launcher resolves `awt.toolkit.name` to `XToolkit` unless told otherwise.
     */
    val isXWayland: Boolean
        get() = toolkit.isX11 && sessionType == LinuxSessionType.WAYLAND

    /** `true` when AWT is driving a Wayland compositor natively, without any X11 protocol in between. */
    val isNativeWayland: Boolean
        get() = toolkit.isWayland

    /** `true` when the EWMH `_NET_WM_MOVERESIZE` client message can be used for interactive resizing. */
    val supportsNetWmMoveResize: Boolean
        get() = hostOs.isLinux && toolkit.isX11 && x11ReflectionAvailable
}
