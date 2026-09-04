package com.novelstudio.core.window

/**
 * Early process configuration of the AWT toolkit, to be called before AWT initialises.
 *
 * The toolkit is chosen twice: once by the native launcher of the JetBrains Runtime, which parses
 * `-Dawt.toolkit.name` from the command line and injects the resolved value, and once by AWT itself, which
 * reads the resulting `awt.toolkit.name` system property. The launcher defaults to `XToolkit` and only
 * probes for a Wayland compositor when the value is the literal `auto`, which is why a Compose Desktop
 * application launched without arguments runs on XWayland inside a Wayland session.
 *
 * The command line flag is therefore the real switch, and it belongs in the JVM arguments of the application
 * and of the packaged distribution. The helper below only covers the second decision: setting the property
 * from Java code works as long as AWT has not been touched yet, which makes it a useful belt and braces for
 * the very first statement of a main function.
 *
 * Two commonly cited alternatives do not do what they promise. `GDK_BACKEND=x11` never reaches the toolkit
 * selection, because AWT does not use GDK to choose a backend; it only influences the GTK based pieces of
 * the runtime. `_JAVA_AWT_WM_NONREPARENTING` is unrelated to Wayland and only fixes the inset arithmetic
 * under non reparenting window managers such as i3 or xmonad.
 */
object AwtToolkitBootstrap {
    /** System property AWT reads to decide which toolkit to instantiate. */
    const val TOOLKIT_NAME_PROPERTY: String = "awt.toolkit.name"

    /** Property value selecting the X11 toolkit, which also covers XWayland. */
    const val X_TOOLKIT: String = "XToolkit"

    /** Property value selecting the native Wayland toolkit. */
    const val WAYLAND_TOOLKIT: String = "WLToolkit"

    /**
     * Requests the X11 toolkit, unless a toolkit was already chosen.
     *
     * An explicit choice always wins, whether it came from the command line, from the launcher or from an
     * earlier call, so a user who asked for the Wayland toolkit keeps it. The call must happen before any
     * AWT class is touched; afterwards the property is simply ignored.
     *
     * @return `true` when the property was set by this call, `false` when a value was already present.
     */
    fun forceX11(): Boolean {
        if (!configuredToolkitName().isNullOrBlank()) return false
        System.setProperty(TOOLKIT_NAME_PROPERTY, X_TOOLKIT)
        return true
    }

    /**
     * Reads the toolkit currently requested by the process.
     *
     * @return the raw property value, which the launcher normally resolves to [X_TOOLKIT] or
     *   [WAYLAND_TOOLKIT], or `null` when nothing was requested.
     */
    fun configuredToolkitName(): String? = System.getProperty(TOOLKIT_NAME_PROPERTY)

    /**
     * Reports whether the process asked for the native Wayland toolkit.
     *
     * Compose for Desktop cannot render there: Skiko binds its hardware layer to an AWT drawing surface that
     * only the X11 toolkit provides, so the first window fails with `Can't lock DrawingSurface` whatever the
     * window chrome decides. Verified against Skiko 0.144.6 and Compose Multiplatform 1.11.1. The window layer
     * still resolves a system-decorated strategy for that toolkit, so the day Skiko supports it nothing else
     * has to change.
     *
     * @return `true` when [WAYLAND_TOOLKIT] was requested, which means the process is expected to fail while
     *   creating its first window.
     */
    fun isNativeWaylandToolkitRequested(): Boolean = configuredToolkitName() == WAYLAND_TOOLKIT
}
