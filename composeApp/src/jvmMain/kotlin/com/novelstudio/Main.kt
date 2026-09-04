package com.novelstudio

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.novelstudio.core.window.DecoratedWindow
import com.novelstudio.core.window.WindowChrome
import com.novelstudio.core.window.WindowChromeConfig
import com.novelstudio.di.appModule
import org.koin.core.context.startKoin

fun main() {
    CrashReporter.install(null)

    startKoin {
        modules(appModule(null))
    }

    application {
        val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)
        var shortcutSequence by remember { mutableStateOf(0L) }
        var navigationShortcut by remember { mutableStateOf<NavigationShortcut?>(null) }

        DecoratedWindow(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "NovelAI Diffusion Studio",
            config = WindowChromeConfig(titleBarHeight = 44.dp),
            minimumSize = DpSize(960.dp, 600.dp),
            titleBar = { chrome ->
                DesktopTitleBar(
                    chrome = chrome,
                    onMinimize = chrome::minimize,
                    onToggleMaximize = chrome::toggleMaximize,
                    onClose = chrome::close,
                )
            },
        ) { chrome ->
            App(
                navigationShortcut = navigationShortcut,
                onPreviewKeyEvent = { event ->
                    if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed) {
                        false
                    } else {
                        destinationForShortcut(event.key)?.let { destination ->
                            shortcutSequence += 1
                            navigationShortcut = NavigationShortcut(shortcutSequence, destination)
                            true
                        } ?: false
                    }
                },
            )
        }
    }
}

@Composable
private fun DesktopTitleBar(
    chrome: WindowChrome,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .then(chrome.captionModifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = "NovelAI Studio",
                modifier = Modifier.padding(horizontal = 14.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TitleBarButton(WindowGlyph.Minimize, "最小化", onMinimize)
        TitleBarButton(
            if (chrome.state.isMaximized) WindowGlyph.Restore else WindowGlyph.Maximize,
            if (chrome.state.isMaximized) "还原" else "最大化",
            onToggleMaximize,
        )
        TitleBarButton(WindowGlyph.Close, "关闭", onClose)
    }
}

@Composable
private fun TitleBarButton(glyph: WindowGlyph, description: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val bgColor by animateColorAsState(
        targetValue = when {
            isPressed -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
            isHovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
            else -> androidx.compose.ui.graphics.Color.Transparent
        },
        animationSpec = tween(durationMillis = 120),
        label = "titlebar-btn-bg",
    )
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .width(48.dp)
            .fillMaxHeight()
            .background(bgColor)
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val stroke = Stroke(width = 1.5.dp.toPx())
            when (glyph) {
                WindowGlyph.Minimize -> drawLine(
                    color = tint,
                    start = Offset(size.width * 0.2f, size.height * 0.7f),
                    end = Offset(size.width * 0.8f, size.height * 0.7f),
                    strokeWidth = stroke.width,
                )
                WindowGlyph.Maximize -> drawRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.2f, size.height * 0.2f),
                    size = Size(size.width * 0.6f, size.height * 0.6f),
                    style = stroke,
                )
                WindowGlyph.Restore -> {
                    drawRect(
                        color = tint,
                        topLeft = Offset(size.width * 0.33f, size.height * 0.18f),
                        size = Size(size.width * 0.5f, size.height * 0.5f),
                        style = stroke,
                    )
                    drawRect(
                        color = tint,
                        topLeft = Offset(size.width * 0.18f, size.height * 0.33f),
                        size = Size(size.width * 0.5f, size.height * 0.5f),
                        style = stroke,
                    )
                }
                WindowGlyph.Close -> {
                    drawLine(tint, Offset(size.width * 0.22f, size.height * 0.22f), Offset(size.width * 0.78f, size.height * 0.78f), stroke.width)
                    drawLine(tint, Offset(size.width * 0.78f, size.height * 0.22f), Offset(size.width * 0.22f, size.height * 0.78f), stroke.width)
                }
            }
        }
    }
}

private enum class WindowGlyph { Minimize, Maximize, Restore, Close }
