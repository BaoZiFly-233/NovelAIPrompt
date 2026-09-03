package com.novelstudio.core.common.wildcard

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WildcardEngineTest {

    private val engine = WildcardEngine(
        wildcards = mapOf(
            "hair_color" to listOf("silver hair", "blue hair", "black hair"),
            "expression" to listOf("smiling", "crying"),
        ),
        random = Random(42),
    )

    @Test
    fun `expands wildcard deterministically with fixed seed`() {
        val wildcards = mapOf(
            "hair_color" to listOf("silver hair", "blue hair", "black hair"),
            "expression" to listOf("smiling", "crying"),
        )
        val resultA = WildcardEngine(wildcards, Random(42)).expand("1girl, __hair_color__")
        val resultB = WildcardEngine(wildcards, Random(42)).expand("1girl, __hair_color__")

        assertTrue(
            resultA in setOf("1girl, silver hair", "1girl, blue hair", "1girl, black hair"),
        )
        assertEquals(resultA, resultB)
    }

    @Test
    fun `leaves unknown wildcards untouched`() {
        val result = engine.expand("1girl, __missing_wildcard__")

        assertEquals("1girl, __missing_wildcard__", result)
    }

    @Test
    fun `expands recursively up to depth limit`() {
        val nested = WildcardEngine(
            wildcards = mapOf(
                "outer" to listOf("__inner__"),
                "inner" to listOf("deep"),
            ),
            random = Random(1),
        )

        assertEquals("deep", nested.expand("__outer__"))
    }

    @Test
    fun `respects novelai weight syntax without touching it`() {
        val result = engine.expand("{{1girl}}, [__expression__]")

        assertTrue(result.startsWith("{{1girl}}, ["))
        assertTrue(result.endsWith("]"))
    }

    @Test
    fun `available wildcards are listed`() {
        assertEquals(setOf("hair_color", "expression"), engine.availableWildcards())
    }
}
