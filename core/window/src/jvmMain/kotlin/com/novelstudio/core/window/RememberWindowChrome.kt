package com.novelstudio.core.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.novelstudio.core.window.diagnostics.currentDesktopEnvironment
import com.novelstudio.core.window.diagnostics.currentWindowChromeStrategy
import com.novelstudio.core.window.diagnostics.windowChromeReport
import com.novelstudio.core.window.jbr.JbrCustomTitleBarHandle
import com.novelstudio.core.window.modifier.captionDrag
import com.novelstudio.core.window.modifier.jbrCaptionHitTest
import com.novelstudio.core.window.modifier.windowResizeHandles
import com.novelstudio.core.window.strategy.WindowChromeStrategy

/**
 * Creates and wires the [WindowChrome] of the window currently being composed.
 *
 * The lifecycle is split between three effects, one per kind of concern. The chrome object itself is
 * remembered for the life of the window. A side effect pushes the focus and placement of the window into the
 * observable state on every composition. A disposable effect owns everything that touches the platform: it
 * installs the native custom title bar when the strategy asks for one, builds the caption and resize
 * modifiers, and undoes all of it when the window goes away.
 *
 * One detail deserves attention. Compose enables its own undecorated window resizer purely on the fact that
 * the window is undecorated and resizable, so it would fight the window manager assisted handles. Its
 * thickness is therefore zeroed while those handles are installed and restored on disposal.
 *
 * @param strategy chrome strategy selected for this host.
 * @param config chrome configuration, whose title bar height is also the height of the native caption band.
 * @param state window state driving the placement.
 * @param onCloseRequest close handler of the application, invoked by the close action.
 * @return the chrome bound to the window being composed.
 */
@Composable
internal fun FrameWindowScope.rememberWindowChrome(
    strategy: WindowChromeStrategy,
    config: WindowChromeConfig,
    state: WindowState,
    onCloseRequest: () -> Unit,
): WindowChrome {
    val environment = remember { currentDesktopEnvironment() }
    val report = remember(environment, strategy, config) { windowChromeReport(environment, strategy, config) }
    val closeRequest = rememberUpdatedState(onCloseRequest)

    val chrome = remember(window, strategy, state) {
        WindowChrome(
            controlsMode = when {
                strategy.usesSystemWindowControls -> WindowControlsMode.SYSTEM
                else -> WindowControlsMode.COMPOSE_DRAWN
            },
            report = report,
            initialState = WindowChromeState(
                isActive = window.isActive,
                isMaximized = state.placement == WindowPlacement.Maximized,
                strategy = strategy.id,
            ),
            onMinimize = { window.isMinimized = true },
            onToggleMaximize = {
                state.placement = when (state.placement) {
                    WindowPlacement.Maximized -> WindowPlacement.Floating
                    else -> WindowPlacement.Maximized
                }
            },
            onClose = { closeRequest.value() },
        )
    }

    val isActive = LocalWindowInfo.current.isWindowFocused
    val isMaximized = state.placement == WindowPlacement.Maximized
    SideEffect { chrome.updateState(isActive = isActive, isMaximized = isMaximized) }

    DisposableEffect(chrome, config) {
        val titleBarHandle = when {
            strategy.installsNativeTitleBar -> JbrCustomTitleBarHandle.install(window, config.titleBarHeight.value)
            else -> null
        }
        chrome.updateControlsInsets(start = 0.dp, end = 0.dp)
        chrome.updateCaptionModifier(
            when {
                titleBarHandle != null -> Modifier.jbrCaptionHitTest(titleBarHandle)
                strategy.rendersTitleBar -> Modifier.captionDrag(
                    window = window,
                    preferSystemWindowMove = strategy.usesSystemWindowMove,
                    onToggleMaximize = chrome::toggleMaximize,
                )

                else -> Modifier
            },
        )

        val previousResizerThickness = window.undecoratedResizerThickness
        if (strategy.usesX11InteractiveResize) {
            window.undecoratedResizerThickness = 0.dp
            chrome.updateResizeHandlesModifier(
                Modifier.windowResizeHandles(
                    window = window,
                    borderThickness = config.resizeBorderThickness,
                    onHoverEdgeChange = chrome::updateHoveredResizeEdge,
                    canResize = { state.placement == WindowPlacement.Floating },
                ),
            )
        }

        onDispose {
            titleBarHandle?.remove()
            window.undecoratedResizerThickness = previousResizerThickness
            chrome.updateHoveredResizeEdge(null)
            chrome.updateResizeHandlesModifier(Modifier)
            chrome.updateCaptionModifier(Modifier)
        }
    }

    return chrome
}
