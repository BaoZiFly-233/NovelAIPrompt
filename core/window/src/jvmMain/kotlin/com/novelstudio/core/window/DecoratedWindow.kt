package com.novelstudio.core.window

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import com.novelstudio.core.window.diagnostics.currentDesktopEnvironment
import com.novelstudio.core.window.diagnostics.currentWindowChromeStrategy
import java.awt.Dimension
import kotlin.math.roundToInt

/**
 * A window whose chrome adapts to what the host platform can actually deliver.
 *
 * The window is created decorated or undecorated depending on the strategy selected for the host, and that
 * decision is taken before the window exists because it cannot be changed afterwards without recreating it:
 *
 * - On Windows and macOS with a JetBrains Runtime the window stays decorated and a native custom title bar
 *   is installed. The operating system keeps dragging, double click to maximize, snapping and edge resize,
 *   and the application only paints the caption pixels.
 * - On Linux with the X11 toolkit, including XWayland, the window is undecorated. Dragging goes to the
 *   window manager through the runtime move service, edge resize goes to the window manager through an EWMH
 *   client message, and the double click gesture is implemented here.
 * - On native Wayland the platform decorations are kept, because an undecorated Wayland top level has no
 *   resize edges, cannot be positioned and reports degraded pointer coordinates. The title bar composable is
 *   not rendered at all and the content fills the window.
 * - Anywhere else the window is undecorated and Compose provides its own client side resizer.
 *
 * The title bar is laid out at exactly [WindowChromeConfig.titleBarHeight] so it always matches the caption
 * band the operating system was told about; a mismatch would make the native hit test cover the wrong strip
 * of pixels. The clamp is two sided: the composable is given that height as a minimum as well as a maximum,
 * so a title bar that measures itself smaller still paints the whole band instead of leaving a dead strip
 * the system keeps hit testing as caption.
 *
 * @param onCloseRequest invoked when the window asks to close, either from the system or from
 *   [WindowChrome.close].
 * @param state window state driving the size, the position and the placement.
 * @param title window title, used by the task bar and the window switcher.
 * @param config chrome configuration.
 * @param minimumSize smallest size the window may be resized to.
 * @param titleBar title bar composable, given the chrome; it must apply [WindowChrome.captionModifier] to
 *   its caption area. It is not called when the platform draws its own title bar.
 * @param content window content, given the chrome.
 */
@Composable
fun ApplicationScope.DecoratedWindow(
    onCloseRequest: () -> Unit,
    state: WindowState,
    title: String,
    config: WindowChromeConfig = WindowChromeConfig(),
    minimumSize: DpSize = DpSize(560.dp, 400.dp),
    titleBar: @Composable (WindowChrome) -> Unit,
    content: @Composable (WindowChrome) -> Unit,
) {
    val strategy = remember(config) { currentWindowChromeStrategy(currentDesktopEnvironment(), config) }

    key(strategy) {
        Window(
            onCloseRequest = onCloseRequest,
            state = state,
            title = title,
            undecorated = strategy.undecorated,
            resizable = true,
        ) {
            LaunchedEffect(window, minimumSize) {
                window.minimumSize = Dimension(
                    minimumSize.width.value.roundToInt(),
                    minimumSize.height.value.roundToInt(),
                )
            }

            val chrome = rememberWindowChrome(
                strategy = strategy,
                config = config,
                state = state,
                onCloseRequest = onCloseRequest,
            )

            CompositionLocalProvider(LocalWindowChrome provides chrome) {
                Column(Modifier.fillMaxSize().then(chrome.resizeModifier)) {
                    if (strategy.rendersTitleBar) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(config.titleBarHeight),
                            propagateMinConstraints = true,
                        ) { titleBar(chrome) }
                    }
                    Box(Modifier.fillMaxWidth().weight(1f)) { content(chrome) }
                }
            }
        }
    }
}
