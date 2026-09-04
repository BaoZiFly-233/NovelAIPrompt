package com.novelstudio.core.window.x11

import com.novelstudio.core.window.platform.ResizeEdge

/**
 * Direction values of the EWMH `_NET_WM_MOVERESIZE` client message.
 *
 * The values are fixed by the freedesktop window manager specification and are carried verbatim in
 * `data.l[2]` of the message. Move and resize differ by nothing but this value, which is why a single
 * dispatcher can serve both.
 *
 * @property value wire value written into the client message payload.
 */
internal enum class NetWmMoveResizeDirection(val value: Int) {
    /** Interactive resize anchored on the upper left corner. */
    SIZE_TOP_LEFT(0),

    /** Interactive resize anchored on the top border. */
    SIZE_TOP(1),

    /** Interactive resize anchored on the upper right corner. */
    SIZE_TOP_RIGHT(2),

    /** Interactive resize anchored on the right border. */
    SIZE_RIGHT(3),

    /** Interactive resize anchored on the lower right corner. */
    SIZE_BOTTOM_RIGHT(4),

    /** Interactive resize anchored on the bottom border. */
    SIZE_BOTTOM(5),

    /** Interactive resize anchored on the lower left corner. */
    SIZE_BOTTOM_LEFT(6),

    /** Interactive resize anchored on the left border. */
    SIZE_LEFT(7),

    /** Interactive move, the single direction the JetBrains Runtime window move service ever sends. */
    MOVE(8),

    /** Keyboard driven resize. */
    SIZE_KEYBOARD(9),

    /** Keyboard driven move. */
    MOVE_KEYBOARD(10),

    /**
     * Cancels an operation the window manager is still performing.
     *
     * It is the only message of the family that must be sent without releasing the grabs first.
     */
    CANCEL(11),
    ;

    /** Maps user interface resize handles to the wire directions of the protocol. */
    companion object {
        /**
         * Returns the direction that matches a resize handle.
         *
         * @param edge handle the user grabbed.
         * @return the corresponding resize direction.
         */
        fun forEdge(edge: ResizeEdge): NetWmMoveResizeDirection = when (edge) {
            ResizeEdge.TOP_LEFT -> SIZE_TOP_LEFT
            ResizeEdge.TOP -> SIZE_TOP
            ResizeEdge.TOP_RIGHT -> SIZE_TOP_RIGHT
            ResizeEdge.RIGHT -> SIZE_RIGHT
            ResizeEdge.BOTTOM_RIGHT -> SIZE_BOTTOM_RIGHT
            ResizeEdge.BOTTOM -> SIZE_BOTTOM
            ResizeEdge.BOTTOM_LEFT -> SIZE_BOTTOM_LEFT
            ResizeEdge.LEFT -> SIZE_LEFT
        }
    }
}
