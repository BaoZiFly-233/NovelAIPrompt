package com.novelstudio.feature.gallery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class BatchImageExporterTest {

    @Test
    fun exportNameCannotEscapeDestinationDirectory() {
        val name = exportFileName("../../bad\\name:with*chars", 0)

        assertFalse('/' in name)
        assertFalse('\\' in name)
        assertFalse(".." in name)
        assertEquals("novelai-bad_name_with_chars.png", name)
    }

    @Test
    fun blankIdGetsStableFallbackName() {
        assertEquals("novelai-image-3.png", exportFileName("...", 2))
    }

    @Test
    fun conflictsUseCaseInsensitiveIncrementingSuffix() {
        val occupied = setOf("NOVELAI-ID.PNG", "novelai-id (1).png")

        assertEquals("novelai-id (2).png", nextAvailableExportName("novelai-id.png", occupied))
    }
}
