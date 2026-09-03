package com.novelstudio.feature.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelstudio.core.data.ImageRepository
import com.novelstudio.core.model.ImageRecord
import com.novelstudio.core.model.NaiModel
import com.novelstudio.core.model.PromptDraft
import com.novelstudio.core.model.PromptDraftStore
import com.novelstudio.core.model.Sampler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 万级虚拟瀑布流图库 ViewModel（只依赖 ImageRepository 抽象）：
 * Room Flow 响应式数据源 + 收藏打标 + 「一键回填到工作台」。
 */
class GalleryViewModel(
    private val imageRepository: ImageRepository,
    private val draftStore: PromptDraftStore,
) : ViewModel() {

    val records: StateFlow<List<ImageRecord>> = imageRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 星标循环：普通(3) → 喜欢(5) → 普通(3) */
    fun toggleLike(record: ImageRecord) {
        viewModelScope.launch {
            val next = if (record.starRating >= ImageRecord.STAR_LIKE) {
                ImageRecord.STAR_NEUTRAL
            } else {
                ImageRecord.STAR_LIKE
            }
            imageRepository.updateStarRating(record.id, next)
            if (next == ImageRecord.STAR_LIKE) {
                imageRepository.updateFavorite(record.id, true)
            }
        }
    }

    /** 不喜欢：归档到垃圾箱档位（滑动筛选流共享同一档位语义） */
    fun markDislike(record: ImageRecord) {
        viewModelScope.launch {
            imageRepository.updateStarRating(record.id, ImageRecord.STAR_DISLIKE)
        }
    }

    fun delete(record: ImageRecord) {
        viewModelScope.launch { imageRepository.delete(record.id) }
    }

    /** 将记录回填为工作台草稿（Fork） */
    fun forkToWorkbench(record: ImageRecord): PromptDraft {
        val draft = PromptDraft(
            prompt = record.prompt,
            uc = record.uc,
            model = NaiModel.fromId(record.model),
            width = record.width,
            height = record.height,
            seed = record.seed,
            steps = record.steps,
            scale = record.scale,
            sampler = Sampler.fromId(record.sampler),
        )
        draftStore.push(draft)
        return draft
    }
}
