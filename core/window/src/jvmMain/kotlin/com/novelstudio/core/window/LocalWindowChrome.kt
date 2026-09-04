package com.novelstudio.core.window

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Gives any composable inside a [DecoratedWindow] access to the window chrome.
 *
 * It saves deep composables from threading the chrome through every layer when they need the window actions
 * or the chrome state. Reading it outside a decorated window is a programming error and fails loudly, which
 * is what a static composition local should do.
 */
val LocalWindowChrome: ProvidableCompositionLocal<WindowChrome> = staticCompositionLocalOf {
    error("LocalWindowChrome was read outside of a DecoratedWindow content lambda.")
}
