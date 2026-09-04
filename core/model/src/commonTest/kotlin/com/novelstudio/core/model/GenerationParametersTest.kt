package com.novelstudio.core.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class GenerationParametersTest {

    @Test
    fun `task status keeps confirmation and interrupted unknown states distinct`() {
        assertEquals(TaskStatus.WAITING_ANLAS_CONFIRMATION.name, "WAITING_ANLAS_CONFIRMATION")
        assertEquals(TaskStatus.FAILED_UNKNOWN.name, "FAILED_UNKNOWN")
        assertTrue(TaskStatus.RUNNING != TaskStatus.FAILED_UNKNOWN)
    }

    @Test
    fun `character coordinates must stay normalized`() {
        assertFailsWith<IllegalArgumentException> { V5Character("x", centerX = -0.01f) }
        assertFailsWith<IllegalArgumentException> { V5Character("x", centerY = 1.01f) }
    }

    @Test
    fun `character limit follows selected model`() {
        val sevenCharacters = List(7) { V5Character("character-$it") }
        val twentyThreeCharacters = List(23) { V5Character("character-$it") }

        GenerationParameters(model = NaiModel.V5_FULL, characterPrompts = sevenCharacters)
        assertFailsWith<IllegalArgumentException> {
            GenerationParameters(model = NaiModel.V4_5_FULL, characterPrompts = sevenCharacters)
        }
        assertFailsWith<IllegalArgumentException> {
            GenerationParameters(model = NaiModel.V5_FULL, characterPrompts = twentyThreeCharacters)
        }
    }

    @Test
    fun `invalid dimensions and numeric ranges are rejected`() {
        assertFailsWith<IllegalArgumentException> { GenerationParameters(width = 1000) }
        assertFailsWith<IllegalArgumentException> { GenerationParameters(height = 0) }
        assertFailsWith<IllegalArgumentException> { GenerationParameters(steps = 51) }
        assertFailsWith<IllegalArgumentException> { GenerationParameters(scale = 10.1f) }
        assertFailsWith<IllegalArgumentException> { GenerationParameters(cfgRescale = -0.1f) }
        assertFailsWith<IllegalArgumentException> { GenerationParameters(seed = 0x1_0000_0000L) }
        assertFailsWith<IllegalArgumentException> { GenerationParameters(nSamples = 0) }
    }

    @Test
    fun `vibe limits and free quota extras are enforced`() {
        val vibes = List(16) { index ->
            VibeReference("v$index", "v$index", byteArrayOf(index.toByte()))
        }
        GenerationParameters(vibeReferences = vibes)
        assertFailsWith<IllegalArgumentException> {
            GenerationParameters(
                vibeReferences = vibes + VibeReference("overflow", "overflow", byteArrayOf(1)),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            VibeReference("empty", "empty", byteArrayOf())
        }
        assertFailsWith<IllegalArgumentException> {
            GenerationParameters(
                model = NaiModel.V4_5_FULL,
                vibeReferences = listOf(VibeReference("v5", "v5", byteArrayOf(1), model = NaiModel.V5_FULL)),
            )
        }
        assertTrue(GenerationParameters(vibeReferences = vibes.take(4)).isWithinFreeQuota)
        assertFalse(GenerationParameters(vibeReferences = vibes.take(5)).isWithinFreeQuota)
        assertFalse(GenerationParameters(nSamples = 2).isWithinFreeQuota)
    }

    @Test
    fun `workbench prompt composition has stable owned section order`() {
        val draft = WorkbenchDraft(
            artistPositive = "artist:a",
            artistNegative = "bad style",
            promptPositive = "1girl, portrait",
            promptNegative = "bad hands",
            orderedTags = listOf("blue eyes", "silver hair"),
            freePrompt = "moonlight",
            negativePrompt = "text",
        )
        assertEquals("artist:a, 1girl, portrait, blue eyes, silver hair, moonlight", draft.finalPositive)
        assertEquals("bad style, bad hands, text", draft.finalNegative)
        assertEquals("blue eyes", normalizeTag("  Blue_eyes  "))
    }

    @Test
    fun `inpaint requires source mask matching size and a confirmed model variant`() {
        val source = GenerationInputImage(byteArrayOf(1), 1024, 1024, "image/png")
        val mask = GenerationInputImage(byteArrayOf(2), 1024, 1024, "image/png")
        GenerationParameters(
            model = NaiModel.V4_5_FULL,
            action = GenerationAction.INFILL,
            operation = ImageOperation.INPAINT,
            parentImageId = "parent",
            sourceImage = source,
            maskImage = mask,
        )
        assertFailsWith<IllegalArgumentException> {
            GenerationParameters(
                model = NaiModel.V5_FULL,
                action = GenerationAction.INFILL,
                operation = ImageOperation.INPAINT,
                parentImageId = "parent",
                sourceImage = source,
                maskImage = mask,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GenerationParameters(
                model = NaiModel.V4_5_FULL,
                action = GenerationAction.INFILL,
                operation = ImageOperation.INPAINT,
                parentImageId = "parent",
                sourceImage = source,
                maskImage = GenerationInputImage(byteArrayOf(2), 832, 1216, "image/png"),
            )
        }
    }
}
