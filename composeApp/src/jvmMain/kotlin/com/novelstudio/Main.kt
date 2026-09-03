package com.novelstudio

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.novelstudio.di.appModule
import org.koin.core.context.startKoin

fun main() {
    startKoin {
        modules(appModule(null))
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "NovelAI Diffusion Studio",
            state = rememberWindowState(width = 1280.dp, height = 800.dp),
        ) {
            App()
        }
    }
}
