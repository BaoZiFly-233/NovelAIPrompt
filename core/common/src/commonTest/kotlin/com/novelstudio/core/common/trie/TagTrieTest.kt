package com.novelstudio.core.common.trie

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TagTrieTest {

    @Test
    fun `completes by weight descending`() {
        val trie = TagTrie()
        trie.insert("1girl", 10)
        trie.insert("1girl, standing", 5)
        trie.insert("1boys", 1)
        trie.insert("silver hair", 3)

        val suggestions = trie.complete("1g", limit = 3)

        assertEquals(listOf("1girl", "1girl, standing"), suggestions.map { it.tag })
        assertEquals(10, suggestions.first().weight)
    }

    @Test
    fun `is case insensitive and trims`() {
        val trie = TagTrie()
        trie.insert("  Silver Hair ", 2)

        assertTrue(trie.contains("silver hair"))
        assertEquals(2, trie.weightOf("SILVER hair".lowercase().trim()))
        assertFalse(trie.contains("gold hair"))
    }

    @Test
    fun `deduplicates repeated inserts`() {
        val trie = TagTrie()
        trie.insert("blue eyes")
        trie.insert("blue eyes")

        assertEquals(1, trie.size)
        assertEquals(2, trie.weightOf("blue eyes"))
    }

    @Test
    fun `returns empty for unknown prefix`() {
        val trie = TagTrie()
        trie.insert("1girl")

        assertTrue(trie.complete("zzz").isEmpty())
        assertTrue(trie.complete("").isEmpty())
    }
}
