package com.novelstudio.core.window.diagnostics

import androidx.compose.runtime.Immutable
import com.novelstudio.core.window.WindowChromeConfig
import com.novelstudio.core.window.jbr.JbrAvailability
import com.novelstudio.core.window.platform.DesktopEnvironment
import com.novelstudio.core.window.platform.DesktopEnvironmentDetector
import com.novelstudio.core.window.platform.EnvironmentSource
import com.novelstudio.core.window.platform.WindowCapabilitiesResolver
import com.novelstudio.core.window.strategy.WindowChromeStrategy
import com.novelstudio.core.window.strategy.WindowChromeStrategySelector
import com.novelstudio.core.window.x11.X11Reflection

/**
 * Diagnostic snapshot of the host and of the window chrome decisions taken for it.
 *
 * The report explains, in terms a user can paste into a bug report, which strategy was selected, what the
 * platform supports and what had to be given up. It is produced without any window, so a background layer
 * can read it long before a window exists.
 *
 * @property operatingSystem operating system name and version.
 * @property toolkit class name of the AWT toolkit actually in use, which is the only reliable way to tell a
 *   real X11 or XWayland session apart from a native Wayland one.
 * @property sessionType display session advertised by the desktop.
 * @property jbrAvailable `true` when the JVM is a JetBrains Runtime.
 * @property strategy identifier of the selected chrome strategy.
 * @property capabilities probed platform capabilities, in a stable order.
 * @property degradations plain English description of every feature the platform forced the chrome to give
 *   up; empty when nothing was sacrificed.
 */
@Immutable
data class WindowChromeReport(
    val operatingSystem: String,
    val toolkit: String,
    val sessionType: String,
    val jbrAvailable: Boolean,
    val strategy: String,
    val capabilities: List<Pair<String, Boolean>>,
    val degradations: List<String>,
)

/**
 * Builds the report for the running host, without requiring a window.
 *
 * The underlying environment snapshot is probed once and cached, because reading the AWT toolkit class name
 * initialises AWT and because none of the answers can change while the process lives.
 *
 * The strategy depends on the configuration as well as on the host, so pass the configuration the application
 * actually builds its windows with. Reporting the default one would show a strategy the running window does not
 * use as soon as the user asks for system decorations.
 *
 * @param config configuration the reported strategy is resolved for; defaults to the one an unconfigured window
 *   would use.
 * @return the diagnostic snapshot of the running host.
 */
fun currentWindowChromeReport(config: WindowChromeConfig = WindowChromeConfig()): WindowChromeReport {
    val environment = currentDesktopEnvironment()
    return windowChromeReport(environment, currentWindowChromeStrategy(environment, config), config)
}

private val cachedDesktopEnvironment: DesktopEnvironment by lazy {
    DesktopEnvironmentDetector.detect(
        environment = EnvironmentSource.Jvm,
        toolkitClassName = DesktopEnvironmentDetector.probeToolkitClassName(),
        graphicsEnvironmentClassName = DesktopEnvironmentDetector.probeGraphicsEnvironmentClassName(),
        jbrAvailable = JbrAvailability.isRuntimeAvailable,
        nativeTitleBarSupported = JbrAvailability.isCustomTitleBarSupported,
        nativeWindowMoveSupported = JbrAvailability.isWindowMoveSupported,
        roundedCornersSupported = JbrAvailability.isRoundedCornersSupported,
        x11ReflectionAvailable = X11Reflection.isAvailable,
    )
}

/**
 * Returns the cached snapshot of the running host.
 *
 * The first call initialises AWT, because identifying the toolkit requires instantiating it.
 *
 * @return the environment snapshot shared by every window of the process.
 */
internal fun currentDesktopEnvironment(): DesktopEnvironment = cachedDesktopEnvironment

/**
 * Selects the chrome strategy for a host and a configuration.
 *
 * @param environment snapshot of the host.
 * @param config chrome configuration, whose decoration preference can override the platform decision.
 * @return the strategy to apply.
 */
internal fun currentWindowChromeStrategy(
    environment: DesktopEnvironment,
    config: WindowChromeConfig,
): WindowChromeStrategy = WindowChromeStrategySelector.select(environment, config.preferSystemDecorations)

/**
 * Assembles the report for an already resolved environment and strategy.
 *
 * The capabilities and the degradations describe the window the configuration builds, not the one the host
 * would have offered by default: a window that keeps the platform decorations gives up neither the Snap
 * Layouts flyout nor the EWMH resize, because it never asked for them.
 *
 * @param environment snapshot of the host.
 * @param strategy strategy the window actually uses.
 * @param config configuration the window is built with.
 * @return the diagnostic snapshot.
 */
internal fun windowChromeReport(
    environment: DesktopEnvironment,
    strategy: WindowChromeStrategy,
    config: WindowChromeConfig = WindowChromeConfig(),
): WindowChromeReport = WindowChromeReport(
    operatingSystem = environment.operatingSystem,
    toolkit = environment.toolkitClassName,
    sessionType = environment.sessionType.id,
    jbrAvailable = environment.jbrAvailable,
    strategy = strategy.id,
    capabilities = WindowCapabilitiesResolver.resolve(environment, config.preferSystemDecorations).asPairs(),
    degradations = WindowCapabilitiesResolver.degradations(environment, config.preferSystemDecorations),
)
