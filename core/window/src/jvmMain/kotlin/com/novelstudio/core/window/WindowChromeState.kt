package com.novelstudio.core.window

import androidx.compose.runtime.Immutable

/**
 * Observable state of a decorated window, refreshed on every composition.
 *
 * @property isActive `true` while the window holds the focus, so the title bar can dim itself when it does
 *   not.
 * @property isMaximized `true` while the window is maximized, which decides between the maximize and the
 *   restore glyph.
 * @property strategy identifier of the chrome strategy in use, mirrored here so a title bar can adapt
 *   without reaching for the diagnostics report.
 */
@Immutable
data class WindowChromeState(
    val isActive: Boolean,
    val isMaximized: Boolean,
    val strategy: String,
)
