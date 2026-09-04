package com.novelstudio.feature.swipe

import kotlin.test.Test
import kotlin.test.assertEquals

class SwipeDecisionTest {
    @Test fun `threshold is 35 percent and direction is stable`() {
        assertEquals(SwipeDecision.NONE, swipeDecision(34f, 100f))
        assertEquals(SwipeDecision.LIKE, swipeDecision(35f, 100f))
        assertEquals(SwipeDecision.DISLIKE, swipeDecision(-35f, 100f))
    }

    @Test fun `invalid width never decides`() {
        assertEquals(SwipeDecision.NONE, swipeDecision(100f, 0f))
    }
}
