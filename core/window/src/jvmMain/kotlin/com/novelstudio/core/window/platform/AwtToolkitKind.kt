package com.novelstudio.core.window.platform

/**
 * Concrete AWT toolkit implementation backing the running application.
 *
 * This is the single most important detection signal on Linux. A JetBrains Runtime launched without
 * arguments resolves `awt.toolkit.name` to `XToolkit` by default, so a Compose Desktop application runs
 * on XWayland inside a Wayland session unless `-Dawt.toolkit.name=WLToolkit` or `=auto` is passed on the
 * command line. The environment variables of the session therefore describe the compositor, never the
 * toolkit; only the toolkit class name does.
 *
 * Detection mirrors the JetBrains Runtime service gates, which compare both the toolkit class name and the
 * `GraphicsEnvironment` class name before enabling a platform service.
 *
 * @property className fully qualified class name reported by `Toolkit.getDefaultToolkit()`, empty for [UNKNOWN].
 */
internal enum class AwtToolkitKind(val className: String) {
    /** `sun.awt.X11.XToolkit`: a real X server or XWayland; the EWMH `_NET_WM_MOVERESIZE` protocol applies. */
    X11("sun.awt.X11.XToolkit"),

    /** `sun.awt.wl.WLToolkit`: native Wayland, where undecorated windows lose resize edges and positioning. */
    WAYLAND("sun.awt.wl.WLToolkit"),

    /** `sun.awt.windows.WToolkit`: the Windows toolkit, which supports the JetBrains Runtime custom title bar. */
    WINDOWS("sun.awt.windows.WToolkit"),

    /** `sun.lwawt.macosx.LWCToolkit`: the macOS toolkit, which supports the custom title bar with native controls. */
    MAC_OS("sun.lwawt.macosx.LWCToolkit"),

    /** No toolkit could be identified, for instance in a headless JVM. */
    UNKNOWN(""),
    ;

    /** `true` when the toolkit talks the X11 protocol, either to a real X server or to XWayland. */
    val isX11: Boolean get() = this == X11

    /** `true` when the toolkit talks the Wayland protocol directly. */
    val isWayland: Boolean get() = this == WAYLAND

    /** Classifies a toolkit from plain class names so the mapping can be unit tested without AWT. */
    companion object {
        private const val X11_GRAPHICS_ENVIRONMENT = "sun.awt.X11GraphicsEnvironment"
        private const val WAYLAND_GRAPHICS_ENVIRONMENT = "sun.awt.wl.WLGraphicsEnvironment"
        private const val WINDOWS_GRAPHICS_ENVIRONMENT = "sun.awt.Win32GraphicsEnvironment"
        private const val MAC_OS_GRAPHICS_ENVIRONMENT = "sun.awt.CGraphicsEnvironment"

        /**
         * Classifies the toolkit from the runtime class names of the AWT toolkit and graphics environment.
         *
         * The toolkit class name wins when it is recognised; the graphics environment name is only consulted
         * as a fallback, which matters when the toolkit could not be instantiated but the graphics
         * environment could.
         *
         * @param toolkitClassName class name of `Toolkit.getDefaultToolkit()`, or `null` when unavailable.
         * @param graphicsEnvironmentClassName class name of the local `GraphicsEnvironment`, or `null`.
         * @return the identified toolkit, or [UNKNOWN] when neither name is recognised.
         */
        fun fromClassNames(toolkitClassName: String?, graphicsEnvironmentClassName: String?): AwtToolkitKind {
            entries.firstOrNull { it != UNKNOWN && it.className == toolkitClassName }?.let { return it }
            return when (graphicsEnvironmentClassName) {
                WAYLAND_GRAPHICS_ENVIRONMENT -> WAYLAND
                X11_GRAPHICS_ENVIRONMENT -> X11
                WINDOWS_GRAPHICS_ENVIRONMENT -> WINDOWS
                MAC_OS_GRAPHICS_ENVIRONMENT -> MAC_OS
                else -> UNKNOWN
            }
        }

        /**
         * Classifies the toolkit from the short name carried by the `awt.toolkit.name` system property.
         *
         * The property is the switch the JetBrains Runtime launcher itself resolves and injects, so it is a
         * reliable last resort when no class could be loaded.
         *
         * @param toolkitName value of `awt.toolkit.name`, typically `XToolkit` or `WLToolkit`.
         * @return the identified toolkit, or [UNKNOWN] when the name is absent or unrecognised.
         */
        fun fromToolkitName(toolkitName: String?): AwtToolkitKind = when (toolkitName) {
            "XToolkit" -> X11
            "WLToolkit" -> WAYLAND
            else -> UNKNOWN
        }
    }
}
