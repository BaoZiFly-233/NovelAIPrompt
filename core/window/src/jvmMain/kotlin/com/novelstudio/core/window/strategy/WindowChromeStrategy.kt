package com.novelstudio.core.window.strategy

/**
 * Declarative description of how a window is decorated and who owns each chrome gesture.
 *
 * A strategy is plain data: it holds no behaviour and no platform handle, so the selector that produces it
 * stays a pure function and the whole platform matrix can be pinned down by unit tests. The composable layer
 * reads these flags and wires the matching modifiers.
 *
 * @property id stable identifier surfaced in the diagnostics report and in the chrome state.
 * @property undecorated `true` when the window must be created without system decorations.
 * @property usesSystemWindowControls `true` when the minimize, maximize and close buttons are drawn by the
 *   platform rather than by the application.
 * @property installsNativeTitleBar `true` when a JetBrains Runtime custom title bar must be attached to the
 *   frame, which keeps the window decorated while the caption pixels are painted by the application.
 * @property rendersTitleBar `true` when the application paints its own title bar; `false` means the platform
 *   already draws one and the application contributes content only.
 * @property usesSystemWindowMove `true` when dragging the caption must be handed to the window manager,
 *   falling back to a client side drag when no such service answers.
 * @property usesX11InteractiveResize `true` when edge resize must be handed to the window manager through
 *   the EWMH `_NET_WM_MOVERESIZE` client message.
 * @property usesComposeResizer `true` when the Compose undecorated window resizer stays responsible for edge
 *   resize, which is a purely client side fallback.
 * @property requiresManualMaximizeGesture `true` when no platform caption exists any more, so double
 *   clicking the title bar must be turned into a maximize toggle by the application.
 */
internal sealed class WindowChromeStrategy(
    val id: String,
    val undecorated: Boolean,
    val usesSystemWindowControls: Boolean,
    val installsNativeTitleBar: Boolean,
    val rendersTitleBar: Boolean,
    val usesSystemWindowMove: Boolean,
    val usesX11InteractiveResize: Boolean,
    val usesComposeResizer: Boolean,
    val requiresManualMaximizeGesture: Boolean,
)
