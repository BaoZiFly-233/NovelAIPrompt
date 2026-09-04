package com.novelstudio.core.window.platform

/**
 * Maps a pointer position inside a window to the resize handle it belongs to.
 *
 * The resolver is a pure function over plain numbers: no AWT, no Compose, no density, no state. Callers work
 * in a single coordinate space of their choosing, usually device independent pixels converted once by the
 * pointer input scope, and the resolver never throws for out of range input so it can be called on every
 * pointer move.
 */
internal object ResizeEdgeResolver {
    /**
     * Multiplier applied to the border thickness to obtain the default corner reach.
     *
     * Corners are made deliberately larger than borders because they are the hardest handles to hit; the
     * same trick is used by window managers and by the Compose undecorated window resizer.
     */
    const val CORNER_MULTIPLIER: Float = 2f

    /**
     * Resolves the resize handle under a pointer position.
     *
     * A position is on a border when it lies within [borderThickness] of it, and on a corner when it lies on
     * one border and within [cornerThickness] of the perpendicular one. Corners therefore win over borders.
     *
     * Both reaches are clamped to half of the window on each axis independently, so a window narrower or
     * shorter than the configured reach never reports two opposite handles for the same position.
     *
     * @param x horizontal pointer position, relative to the window content, in the caller's unit.
     * @param y vertical pointer position, relative to the window content, in the caller's unit.
     * @param width window content width in the same unit.
     * @param height window content height in the same unit.
     * @param borderThickness reach of a border handle; a value of zero or less disables resizing entirely.
     * @param cornerThickness reach of a corner handle along the perpendicular axis; never smaller than
     *   [borderThickness].
     * @return the handle under the pointer, or `null` when the pointer is outside the window or in its
     *   interior.
     */
    fun resolve(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        borderThickness: Float,
        cornerThickness: Float = borderThickness * CORNER_MULTIPLIER,
    ): ResizeEdge? {
        if (borderThickness <= 0f || width <= 0f || height <= 0f) return null
        if (x < 0f || y < 0f || x > width || y > height) return null

        val border = minOf(borderThickness, width / 2f, height / 2f)
        val corner = cornerThickness.coerceAtLeast(border)
        val cornerX = minOf(corner, width / 2f)
        val cornerY = minOf(corner, height / 2f)
        val onLeft = x <= border
        val onTop = y <= border
        val onRight = x >= width - border
        val onBottom = y >= height - border
        val nearLeft = x <= cornerX
        val nearTop = y <= cornerY
        val nearRight = x >= width - cornerX
        val nearBottom = y >= height - cornerY

        return when {
            (onTop && nearLeft) || (onLeft && nearTop) -> ResizeEdge.TOP_LEFT
            (onTop && nearRight) || (onRight && nearTop) -> ResizeEdge.TOP_RIGHT
            (onBottom && nearLeft) || (onLeft && nearBottom) -> ResizeEdge.BOTTOM_LEFT
            (onBottom && nearRight) || (onRight && nearBottom) -> ResizeEdge.BOTTOM_RIGHT
            onTop -> ResizeEdge.TOP
            onBottom -> ResizeEdge.BOTTOM
            onLeft -> ResizeEdge.LEFT
            onRight -> ResizeEdge.RIGHT
            else -> null
        }
    }
}
