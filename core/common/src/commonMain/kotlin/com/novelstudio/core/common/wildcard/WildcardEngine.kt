package com.novelstudio.core.common.wildcard

import kotlin.random.Random

/**
 * Wildcard 展开引擎。
 *
 * 采用社区通用的 `__name__` 双下划线语法（避免与 NovelAI 官方 `{}`/`[]`
 * 权重语法冲突），将 Prompt 中的通配符随机展开为词库中的条目，
 * 支持递归展开与深度限制。
 */
class WildcardEngine(
    private val wildcards: Map<String, List<String>>,
    private val random: Random = Random.Default,
) {

    fun expand(prompt: String, maxDepth: Int = 5): String {
        if (wildcards.isEmpty()) return prompt
        var current = prompt
        repeat(maxDepth) {
            val next = WILDCARD_PATTERN.replace(current) { match ->
                val name = match.groupValues[1]
                pickEntry(name) ?: match.value
            }
            if (next == current) return next
            current = next
        }
        return current
    }

    /** 调试辅助：列出全部可用通配符名 */
    fun availableWildcards(): Set<String> = wildcards.keys

    private fun pickEntry(name: String): String? {
        val entries = wildcards[name.lowercase()]?.takeIf { it.isNotEmpty() } ?: return null
        return entries[random.nextInt(entries.size)]
    }

    companion object {
        /**
         * 通配符名允许 `hair_color` 这类下划线分段，
         * 但禁止连续/首尾下划线，避免与 `__` 定界符贪婪冲突。
         */
        private val WILDCARD_PATTERN = Regex("__([a-zA-Z0-9\\-]+(?:_[a-zA-Z0-9\\-]+)*)__")
    }
}
