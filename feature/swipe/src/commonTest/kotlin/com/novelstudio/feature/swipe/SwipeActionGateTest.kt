package com.novelstudio.feature.swipe

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.coroutineScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SwipeActionGateTest {
    @Test
    fun `same card is single flight and can retry after failure`() = runTest {
        val gate = SwipeActionGate()
        val accepted = coroutineScope { (1..20).map { async { gate.tryAcquire("same") } }.awaitAll() }
        assertEquals(1, accepted.count { it })
        gate.release("same")
        assertTrue(gate.tryAcquire("same"))
    }
}
