package com.novelstudio.feature.swipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelstudio.core.data.SwipeImageRepository
import com.novelstudio.core.model.ImageRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 滑动喜欢/不喜欢卡片流 ViewModel（只依赖 ImageRepository 抽象） */
class SwipeViewModel(private val imageRepository: SwipeImageRepository) : ViewModel() {
    private val actionGate = SwipeActionGate()

    /** 仅保持当前首张和计数；万级图库不会在筛选页被整体物化。 */
    val deck: StateFlow<SwipeDeckState> = combine(
        imageRepository.observeNextUnreviewed(),
        imageRepository.observeUnreviewedCount(),
    ) { top, remainingCount -> SwipeDeckState(top, remainingCount) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SwipeDeckState())

    /** 最近操作记录，支持撤销（最多 5 条）。 */
    private val _recentDecisions = MutableStateFlow<List<Pair<String, Boolean>>>(emptyList())
    val canUndo: StateFlow<Boolean> = _recentDecisions
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun swipeLike(record: ImageRecord) {
        decide(record.id, liked = true)
    }

    fun swipeDislike(record: ImageRecord) {
        decide(record.id, liked = false)
    }

    /** 撤销最近一次决定：将图片从归档/垃圾箱恢复到未审阅状态。 */
    fun undoLast() {
        val last = _recentDecisions.value.lastOrNull() ?: return
        _recentDecisions.update { it.dropLast(1) }
        viewModelScope.launch {
            runCatching {
                imageRepository.restoreFromTrash(last.first)
            }
        }
    }

    private fun decide(id: String, liked: Boolean) {
        viewModelScope.launch {
            val accepted = actionGate.tryAcquire(id)
            if (!accepted) return@launch
            runCatching {
                if (liked) imageRepository.archive(id, null) else imageRepository.moveToTrash(id)
            }
                .onSuccess {
                    _recentDecisions.update { history ->
                        (history + (id to liked)).takeLast(MAX_UNDO_HISTORY)
                    }
                }
                .onFailure { actionGate.release(id) }
        }
    }

    private companion object {
        const val MAX_UNDO_HISTORY = 5
    }
}

data class SwipeDeckState(
    val top: ImageRecord? = null,
    val remainingCount: Int = 0,
)
