package com.novelstudio.feature.gallery

import kotlin.test.Test
import kotlin.test.assertEquals

class ZoomableImageTest {

    @Test
    fun zoomAndPanAreClampedToLegalBounds() {
        assertEquals(MIN_VIEWER_ZOOM, clampViewerZoom(0.2f))
        assertEquals(MAX_VIEWER_ZOOM, clampViewerZoom(99f))
        assertEquals(MIN_VIEWER_ZOOM, clampViewerZoom(Float.NaN))
        assertEquals(0f, clampViewerOffset(100f, 1f, 800f))
        assertEquals(400f, clampViewerOffset(900f, 2f, 800f))
        assertEquals(-400f, clampViewerOffset(-900f, 2f, 800f))
    }

    @Test
    fun viewportChangesAndInvalidNumbersCannotLeaveIllegalOffset() {
        assertEquals(100f, clampViewerOffset(400f, 2f, 200f))
        assertEquals(0f, clampViewerOffset(Float.NaN, 2f, 200f))
        assertEquals(0f, clampViewerOffset(10f, 2f, Float.POSITIVE_INFINITY))
    }

    @Test
    fun doubleTapZoomsInThenResets() {
        assertEquals(2.5f, nextViewerDoubleTapZoom(1f))
        assertEquals(1f, nextViewerDoubleTapZoom(2.5f))
    }
}
