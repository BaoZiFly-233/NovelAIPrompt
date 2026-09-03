package com.novelstudio.core.network

import com.novelstudio.core.model.DispatchDecision
import com.novelstudio.core.model.GenerationParameters
import com.novelstudio.core.model.NaiModel
import com.novelstudio.core.model.OpusBatteryState
import com.novelstudio.core.model.SubscriptionTier
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

class SmartDispatcherTest {

    private val params = GenerationParameters(model = NaiModel.V5_FULL)
    private val opusFull = OpusBatteryState(SubscriptionTier.OPUS, 0, 80f)

    @Test
    fun `explicit V5 with healthy battery uses V5 pool`() {
        assertEquals(DispatchDecision.USE_V5_BATTERY, SmartDispatcher.decide(params, opusFull))
    }

    @Test
    fun `explicit V5 with low battery asks anlas confirmation`() {
        val low = OpusBatteryState(SubscriptionTier.OPUS, 500, 5f)
        assertEquals(DispatchDecision.CONFIRM_ANLAS, SmartDispatcher.decide(params, low))
    }

    @Test
    fun `exploration with low battery falls back to V4_5`() {
        val medium = OpusBatteryState(SubscriptionTier.OPUS, 0, 20f)
        assertEquals(DispatchDecision.FALLBACK_V4_5, SmartDispatcher.decide(params, medium, explorationMode = true))
        val degraded = SmartDispatcher.degradeToV4_5(params)
        assertEquals(NaiModel.V4_5_FULL, degraded.model)
    }

    @Test
    fun `exploration with healthy battery stays on V5`() {
        assertEquals(
            DispatchDecision.USE_V5_BATTERY,
            SmartDispatcher.decide(params, opusFull, explorationMode = true),
        )
    }

    @Test
    fun `V4_5 model never consumes V5 battery`() {
        val v45 = params.copy(model = NaiModel.V4_5_FULL)
        assertEquals(
            DispatchDecision.USE_V5_BATTERY,
            SmartDispatcher.decide(v45, OpusBatteryState(SubscriptionTier.OPUS, 0, 0f)),
        )
    }

    @Test
    fun `oversized parameters require anlas confirmation`() {
        val huge = params.copy(width = 1536, height = 1536, steps = 50)
        assertEquals(DispatchDecision.CONFIRM_ANLAS, SmartDispatcher.decide(huge, opusFull))
    }

    @Test
    fun `non opus tiers always require confirmation for V5`() {
        val tablet = OpusBatteryState(SubscriptionTier.TABLET, 1000, 90f)
        assertEquals(DispatchDecision.CONFIRM_ANLAS, SmartDispatcher.decide(params, tablet))
    }
}
