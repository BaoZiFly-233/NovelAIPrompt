package com.novelstudio.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 图库与对比实验室之间只传稳定图片 ID，避免跨页面持有过期的分页对象。 */
interface CompareSelectionStore {
    val selectedIds: StateFlow<List<String>>

    /** 以本次选择替换现有对比槽；最多接收 [MAX_COMPARE_IMAGES] 张。 */
    fun replace(ids: Collection<String>): CompareSelectionResult

    fun clear()

    companion object {
        const val MAX_COMPARE_IMAGES = 2
    }
}

data class CompareSelectionResult(
    val accepted: Boolean,
    val selectedIds: List<String>,
    val message: String,
)

class InMemoryCompareSelectionStore : CompareSelectionStore {
    private val mutableSelectedIds = MutableStateFlow<List<String>>(emptyList())
    override val selectedIds: StateFlow<List<String>> = mutableSelectedIds.asStateFlow()

    override fun replace(ids: Collection<String>): CompareSelectionResult {
        val normalized = ids.asSequence()
            .filter(String::isNotBlank)
            .distinct()
            .toList()
        if (normalized.isEmpty()) {
            return CompareSelectionResult(false, selectedIds.value, "请至少选择一张图片")
        }
        if (normalized.size > CompareSelectionStore.MAX_COMPARE_IMAGES) {
            return CompareSelectionResult(
                accepted = false,
                selectedIds = selectedIds.value,
                message = "对比实验室最多接收 ${CompareSelectionStore.MAX_COMPARE_IMAGES} 张图片",
            )
        }
        mutableSelectedIds.value = normalized
        return CompareSelectionResult(true, normalized, "已加入对比")
    }

    override fun clear() {
        mutableSelectedIds.value = emptyList()
    }
}
