package com.novelstudio.core.common.trie

/**
 * Danbooru Tag 前缀联想 Trie 树。
 *
 * 以小写、去空白后的 Tag 为键，按历史使用频次（weight）排序输出 Top-K 候选，
 * 供工作台 PromptEditor 的联想输入使用。
 */
class TagTrie {

    data class TagSuggestion(val tag: String, val weight: Int)

    private class Node {
        val children = HashMap<Char, Node>()
        var weight = 0
        var isWord = false
    }

    private val root = Node()
    var size: Int = 0
        private set

    /** 归一化：小写 + 去首尾空白 */
    private fun normalize(tag: String): String = tag.trim().lowercase()

    fun insert(tag: String, weight: Int = 1) {
        val normalized = normalize(tag)
        if (normalized.isEmpty()) return
        var node = root
        for (ch in normalized) {
            node = node.children.getOrPut(ch) { Node() }
        }
        if (!node.isWord) size++
        node.isWord = true
        node.weight += weight
    }

    fun contains(tag: String): Boolean {
        val node = findNode(normalize(tag)) ?: return false
        return node.isWord
    }

    fun weightOf(tag: String): Int = findNode(normalize(tag))?.takeIf { it.isWord }?.weight ?: 0

    /** 返回以 prefix 开头、按 weight 降序的前 limit 个候选 */
    fun complete(prefix: String, limit: Int = 10): List<TagSuggestion> {
        val normalized = normalize(prefix)
        if (normalized.isEmpty() || limit <= 0) return emptyList()
        val start = findNode(normalized) ?: return emptyList()

        val results = ArrayList<TagSuggestion>()
        val stack = ArrayDeque<Pair<Node, String>>()
        stack.addLast(start to normalized)
        while (stack.isNotEmpty() && results.size < limit * 4) {
            val (node, text) = stack.removeLast()
            if (node.isWord && text.length >= normalized.length) {
                results.add(TagSuggestion(text, node.weight))
            }
            for ((ch, child) in node.children) {
                stack.addLast(child to text + ch)
            }
        }
        return results
            .asSequence()
            .filter { it.tag.startsWith(normalized) }
            .sortedWith(compareByDescending<TagSuggestion> { it.weight }.thenBy { it.tag })
            .take(limit)
            .toList()
    }

    private fun findNode(prefix: String): Node? {
        var node = root
        for (ch in prefix) {
            node = node.children[ch] ?: return null
        }
        return node
    }
}
