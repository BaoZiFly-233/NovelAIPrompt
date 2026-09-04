package com.novelstudio.core.window.platform

/**
 * Features the window chrome can rely on for the current host.
 *
 * Every flag answers a question the user experience depends on, so the set doubles as the payload of the
 * diagnostics report.
 *
 * @property nativeTitleBar a JetBrains Runtime `CustomTitleBar` is installed and the operating system owns
 *   the caption band, including its hit testing.
 * @property systemWindowDrag dragging the caption is performed by the window manager or the operating
 *   system, which is what makes edge snapping and half tiling work.
 * @property systemInteractiveResize resizing is performed by the window manager through a real resize grab
 *   rather than by repeatedly resizing the window from the client side.
 * @property systemDoubleClickMaximize double clicking the caption is turned into a maximize toggle by the
 *   platform; when `false` the application implements the gesture itself.
 * @property windowManagerSnapping dragging or resizing triggers the desktop snapping and tiling heuristics.
 * @property composeClientSideResize the Compose Multiplatform undecorated window resizer is active, which
 *   moves and resizes the window from the client side and cannot snap.
 * @property programmaticPositioning `Window.setLocation` actually moves the top level window, which saved
 *   window geometry needs in order to be restored.
 * @property roundedCorners the platform can round the window corners on request.
 */
internal data class WindowCapabilities(
    val nativeTitleBar: Boolean,
    val systemWindowDrag: Boolean,
    val systemInteractiveResize: Boolean,
    val systemDoubleClickMaximize: Boolean,
    val windowManagerSnapping: Boolean,
    val composeClientSideResize: Boolean,
    val programmaticPositioning: Boolean,
    val roundedCorners: Boolean,
) {
    /**
     * Flattens the capabilities into labelled pairs for the diagnostics report.
     *
     * @return one pair per capability, in a stable order suitable for direct rendering.
     */
    fun asPairs(): List<Pair<String, Boolean>> = listOf(
        "Native custom title bar" to nativeTitleBar,
        "System window drag" to systemWindowDrag,
        "System interactive resize" to systemInteractiveResize,
        "System double-click maximize" to systemDoubleClickMaximize,
        "Window manager snapping" to windowManagerSnapping,
        "Compose client-side resize" to composeClientSideResize,
        "Programmatic positioning" to programmaticPositioning,
        "Rounded corners" to roundedCorners,
    )
}
