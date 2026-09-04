package com.novelstudio.feature.gallery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class GallerySelectionTest {

    @Test
    fun selectionIsOrderedDeduplicatedAndToggleable() {
        val selection = GallerySelection.from(listOf("second", "first", "second", ""))

        assertEquals(listOf("second", "first"), selection.ids)
        assertEquals(listOf("first"), selection.toggle("second").ids)
        assertEquals(listOf("second", "first", "third"), selection.toggle("third").ids)
    }

    @Test
    fun blankToggleIsNoOp() {
        val selection = GallerySelection.from(listOf("one"))

        assertSame(selection, selection.toggle(" "))
    }
}
