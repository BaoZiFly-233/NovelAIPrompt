package com.novelstudio.core.window.platform

/**
 * One of the eight interactive resize handles of a window.
 *
 * The enum is deliberately free of any protocol or toolkit detail: the X11 layer maps it to an EWMH
 * `_NET_WM_MOVERESIZE` direction and the Compose layer maps it to a pointer cursor, but the geometry itself
 * is decided by [ResizeEdgeResolver] alone.
 *
 * @property movesLeftBorder `true` when dragging the handle moves the left border.
 * @property movesTopBorder `true` when dragging the handle moves the top border.
 * @property movesRightBorder `true` when dragging the handle moves the right border.
 * @property movesBottomBorder `true` when dragging the handle moves the bottom border.
 */
internal enum class ResizeEdge(
    val movesLeftBorder: Boolean,
    val movesTopBorder: Boolean,
    val movesRightBorder: Boolean,
    val movesBottomBorder: Boolean,
) {
    /** Upper left corner handle. */
    TOP_LEFT(movesLeftBorder = true, movesTopBorder = true, movesRightBorder = false, movesBottomBorder = false),

    /** Top border handle. */
    TOP(movesLeftBorder = false, movesTopBorder = true, movesRightBorder = false, movesBottomBorder = false),

    /** Upper right corner handle. */
    TOP_RIGHT(movesLeftBorder = false, movesTopBorder = true, movesRightBorder = true, movesBottomBorder = false),

    /** Right border handle. */
    RIGHT(movesLeftBorder = false, movesTopBorder = false, movesRightBorder = true, movesBottomBorder = false),

    /** Lower right corner handle. */
    BOTTOM_RIGHT(movesLeftBorder = false, movesTopBorder = false, movesRightBorder = true, movesBottomBorder = true),

    /** Bottom border handle. */
    BOTTOM(movesLeftBorder = false, movesTopBorder = false, movesRightBorder = false, movesBottomBorder = true),

    /** Lower left corner handle. */
    BOTTOM_LEFT(movesLeftBorder = true, movesTopBorder = false, movesRightBorder = false, movesBottomBorder = true),

    /** Left border handle. */
    LEFT(movesLeftBorder = true, movesTopBorder = false, movesRightBorder = false, movesBottomBorder = false),
    ;

    /** `true` when the handle sits on a corner and therefore resizes along both axes. */
    val isCorner: Boolean
        get() = (movesLeftBorder || movesRightBorder) && (movesTopBorder || movesBottomBorder)
}
