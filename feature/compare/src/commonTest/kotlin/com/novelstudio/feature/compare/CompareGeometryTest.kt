package com.novelstudio.feature.compare

import kotlin.test.Test
import kotlin.test.assertEquals

class CompareGeometryTest {
    @Test fun splitDragUsesActualWidth() {
        assertEquals(.25f, splitFromDrag(.5f, -250f, 1000f))
        assertEquals(.5f, splitFromDrag(.5f, 0f, 1000f))
        assertEquals(.75f, splitFromDrag(.5f, 250f, 1000f))
        assertEquals(.02f, splitFromDrag(.5f, -1000f, 1000f))
        assertEquals(.98f, splitFromDrag(.5f, 1000f, 1000f))
    }

    @Test fun paneOffsetClampsAtEachZoom() {
        assertEquals(0f, clampCompareOffset(100f, 1f, 500f))
        assertEquals(100f, clampCompareOffset(100f, 2f, 500f))
        assertEquals(1000f, clampCompareOffset(2000f, 5f, 500f))
        assertEquals(-1000f, clampCompareOffset(-2000f, 5f, 500f))
        assertEquals(0f, clampCompareOffset(Float.NaN, Float.NaN, 500f))
        assertEquals(0f, clampCompareOffset(100f, 2f, Float.NaN))
    }

    @Test fun zoomAndDoubleTapAreBounded() {
        assertEquals(1f, clampCompareScale(Float.NaN))
        assertEquals(1f, clampCompareScale(0f))
        assertEquals(5f, clampCompareScale(9f))
        assertEquals(2.5f, nextCompareDoubleTapScale(1f))
        assertEquals(1f, nextCompareDoubleTapScale(2.5f))
    }
}
