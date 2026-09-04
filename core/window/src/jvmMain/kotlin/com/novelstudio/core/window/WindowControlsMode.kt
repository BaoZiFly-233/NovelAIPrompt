package com.novelstudio.core.window

/**
 * Tells the application who paints the minimize, maximize and close buttons of the window.
 */
enum class WindowControlsMode {
    /**
     * The application draws the window controls itself, in Compose.
     *
     * This is the mode of every strategy that gives the caption pixels to the application, whether the
     * window is decorated with a native custom title bar or undecorated.
     */
    COMPOSE_DRAWN,

    /**
     * The platform draws the window controls as part of its own decorations.
     *
     * The title bar composable must not render controls in this mode, and usually must not render at all,
     * because the platform already draws a full title bar above the application content.
     */
    SYSTEM,
}
