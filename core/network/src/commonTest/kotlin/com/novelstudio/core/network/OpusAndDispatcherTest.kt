package com.novelstudio.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpusFreeCalculatorTest {

    @Test
    fun `square ratio clamps to 1024x1024`() {
        assertEquals(1024 to 1024, OpusFreeCalculator.clampResolution(1, 1))
    }

    @Test
    fun `portrait 2_3 fills free quota within grid alignment`() {
        val (w, h) = OpusFreeCalculator.clampResolution(2, 3)
        assertTrue(w * h <= OpusFreeCalculator.MAX_FREE_PIXELS, "像素必须免费: $w×$h=${w * h}")
        assertEquals(0, w % OpusFreeCalculator.GRID_STEP, "宽度必须对齐 64 网格")
        assertEquals(0, h % OpusFreeCalculator.GRID_STEP, "高度必须对齐 64 网格")
        assertEquals(832 to 1216, w to h)
    }

    @Test
    fun `ultrawide ratio clamps down until free`() {
        val (w, h) = OpusFreeCalculator.clampResolution(21, 9)
        assertTrue(w * h <= OpusFreeCalculator.MAX_FREE_PIXELS)
        assertEquals(0, w % 64)
        assertEquals(0, h % 64)
    }

    @Test
    fun `free generation checks pixels and steps`() {
        assertTrue(OpusFreeCalculator.isFreeGeneration(1024, 1024, 28))
        assertTrue(!OpusFreeCalculator.isFreeGeneration(1088, 1024, 28))
        assertTrue(!OpusFreeCalculator.isFreeGeneration(1024, 1024, 29))
        assertTrue(OpusFreeCalculator.clampSteps(50) == 28)
    }
}
