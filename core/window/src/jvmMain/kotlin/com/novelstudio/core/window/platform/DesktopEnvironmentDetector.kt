package com.novelstudio.core.window.platform

import java.awt.AWTError
import java.awt.GraphicsEnvironment
import java.awt.Toolkit

/**
 * Builds a [DesktopEnvironment] from raw platform signals.
 *
 * The detector is split in two halves on purpose. [detect] is a pure function over plain values and carries
 * the whole decision logic, so it is unit testable without AWT. The `probe` helpers are the only impure
 * part: they read the actual AWT toolkit and graphics environment class names, mirroring the gates the
 * JetBrains Runtime itself uses before enabling a platform service. Probing the toolkit initialises AWT,
 * which is why the caller decides when it happens.
 */
internal object DesktopEnvironmentDetector {
    private const val OS_NAME = "os.name"
    private const val JAVA_VENDOR = "java.vendor"
    private const val JAVA_VERSION = "java.version"
    private const val TOOLKIT_NAME = "awt.toolkit.name"
    private const val UNKNOWN_VALUE = "unknown"

    /**
     * Assembles a [DesktopEnvironment] from already collected signals.
     *
     * The toolkit is resolved from the runtime class names first and only falls back to the
     * `awt.toolkit.name` system property when neither class could be identified, because the property may
     * still hold the unresolved `auto` value while the class names always describe what AWT bound to.
     *
     * @param environment source of the `os.name`, `java.vendor`, `java.version` and session variables.
     * @param toolkitClassName class name of the AWT toolkit, or `null` when it could not be read.
     * @param graphicsEnvironmentClassName class name of the local graphics environment, or `null`.
     * @param jbrAvailable `true` when the JetBrains Runtime API answers on this JVM.
     * @param nativeTitleBarSupported `true` when a JetBrains Runtime custom title bar can be installed.
     * @param nativeWindowMoveSupported `true` when the JetBrains Runtime window move service is available.
     * @param roundedCornersSupported `true` when the JetBrains Runtime rounded corners service is available.
     * @param x11ReflectionAvailable `true` when the `sun.awt.X11` internals could be reflected on.
     * @return the assembled snapshot.
     */
    fun detect(
        environment: EnvironmentSource,
        toolkitClassName: String?,
        graphicsEnvironmentClassName: String?,
        jbrAvailable: Boolean,
        nativeTitleBarSupported: Boolean,
        nativeWindowMoveSupported: Boolean,
        roundedCornersSupported: Boolean,
        x11ReflectionAvailable: Boolean,
    ): DesktopEnvironment {
        val fromClasses = AwtToolkitKind.fromClassNames(toolkitClassName, graphicsEnvironmentClassName)
        val toolkit = when (fromClasses) {
            AwtToolkitKind.UNKNOWN -> AwtToolkitKind.fromToolkitName(environment.property(TOOLKIT_NAME))
            else -> fromClasses
        }
        return DesktopEnvironment(
            hostOs = HostOs.from(environment),
            operatingSystem = environment.property(OS_NAME).orEmpty().ifBlank { UNKNOWN_VALUE },
            javaVendor = environment.property(JAVA_VENDOR).orEmpty().ifBlank { UNKNOWN_VALUE },
            javaVersion = environment.property(JAVA_VERSION).orEmpty().ifBlank { UNKNOWN_VALUE },
            toolkit = toolkit,
            toolkitClassName = toolkitClassName ?: toolkit.className.ifBlank { UNKNOWN_VALUE },
            sessionType = LinuxSessionType.from(environment),
            jbrAvailable = jbrAvailable,
            nativeTitleBarSupported = nativeTitleBarSupported,
            nativeWindowMoveSupported = nativeWindowMoveSupported,
            roundedCornersSupported = roundedCornersSupported,
            x11ReflectionAvailable = x11ReflectionAvailable,
        )
    }

    /**
     * Reads the class name of the AWT toolkit actually in use.
     *
     * This initialises AWT. It is the single reliable way to tell a real X11 or XWayland session apart from
     * a native Wayland one, because the session environment variables describe the compositor and not the
     * toolkit the launcher selected.
     *
     * @return the toolkit class name, or `null` when AWT refused to start, for instance in a headless JVM.
     */
    fun probeToolkitClassName(): String? = probeClassName { Toolkit.getDefaultToolkit().javaClass.name }

    /**
     * Reads the class name of the local graphics environment.
     *
     * @return the graphics environment class name, or `null` when it could not be obtained.
     */
    fun probeGraphicsEnvironmentClassName(): String? =
        probeClassName { GraphicsEnvironment.getLocalGraphicsEnvironment().javaClass.name }

    private fun probeClassName(probe: () -> String): String? = try {
        probe()
    } catch (awtError: AWTError) {
        null
    } catch (runtimeError: RuntimeException) {
        null
    } catch (linkageError: LinkageError) {
        null
    }
}
