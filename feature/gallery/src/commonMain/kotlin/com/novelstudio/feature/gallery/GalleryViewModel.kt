package com.novelstudio.feature.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelstudio.core.database.ImageDao
import com.novelstudio.core.database.ImageEntity
import com.novelstudio.core.model.ImageRecord
import com.novelstudio.core.model.PromptDraft
import com.novelstudio.core.model.PromptDraftStore
import com.novelstudio.core.model.Sampler
import com.novelstudio.core.model.NaiModel
import com.novelstudio.core.model.NaiImageMetadata
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * 万级虚拟瀑布流图库 ViewModel：
 * Room Flow 响应式数据源 + 收藏打标 + 「一键回填到工作台」。
 */
class GalleryViewModel(
    private val imageDao: ImageDao,
    private val draftStore: PromptDraftStore,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    val records: StateFlow<List<ImageEntity>> = imageDao.observeAll()
        .map { list -> list }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 星标循环：普通(3) → 喜欢(5) → 普通(3) */
    fun toggleLike(entity: ImageEntity) {
        viewModelScope.launch {
            val next = if (entity.starRating >= ImageRecord.STAR_LIKE) {
                ImageRecord.STAR_NEUTRAL
            } else {
                ImageRecord.STAR_LIKE
            }
            imageDao.updateStarRating(entity.id, next)
            if (next == ImageRecord.STAR_LIKE) {
                imageDao.updateFavorite(entity.id, true)
            }
        }
    }

    /** 不喜欢：归档到垃圾箱档位（滑动筛选流共享同一档位语义） */
    fun markDislike(entity: ImageEntity) {
        viewModelScope.launch {
            imageDao.updateStarRating(entity.id, ImageRecord.STAR_DISLIKE)
        }
    }

    fun delete(entity: ImageEntity) {
        viewModelScope.launch { imageDao.delete(entity.id) }
    }

    /** 将记录回填为工作台草稿（Fork） */
    fun forkToWorkbench(entity: ImageEntity): PromptDraft {
        val metadata = runCatching {
            json.decodeFromString<NaiImageMetadata>(entity.rawMetadataJson)
        }.getOrNull()
        val draft = PromptDraft(
            prompt = metadata?.prompt ?: entity.prompt,
            uc = metadata?.uc ?: entity.uc,
            model = NaiModel.fromId(metadata?.model ?: entity.model),
            width = entity.width,
            height = entity.height,
            seed = entity.seed,
            steps = entity.steps,
            scale = entity.scale,
            sampler = Sampler.fromId(metadata?.sampler ?: entity.sampler),
        )
        draftStore.push(draft)
        return draft
    }
}
