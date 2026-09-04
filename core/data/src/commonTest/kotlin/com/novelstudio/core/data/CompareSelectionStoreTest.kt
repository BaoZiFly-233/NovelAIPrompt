package com.novelstudio.core.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompareSelectionStoreTest {

    @Test
    fun replaceDeduplicatesIdsAndPreservesOrder() {
        val store = InMemoryCompareSelectionStore()

        val result = store.replace(listOf("second", "first", "second"))

        assertTrue(result.accepted)
        assertEquals(listOf("second", "first"), store.selectedIds.value)
    }

    @Test
    fun rejectedReplacementDoesNotDestroyExistingSelection() {
        val store = InMemoryCompareSelectionStore()
        store.replace(listOf("left", "right"))

        val tooMany = store.replace(listOf("one", "two", "three"))
        val empty = store.replace(emptyList())

        assertFalse(tooMany.accepted)
        assertFalse(empty.accepted)
        assertEquals(listOf("left", "right"), store.selectedIds.value)
    }

    @Test
    fun clearRemovesBothComparisonSlots() {
        val store = InMemoryCompareSelectionStore()
        store.replace(listOf("left", "right"))

        store.clear()

        assertTrue(store.selectedIds.value.isEmpty())
    }
}
