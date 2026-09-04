package com.novelstudio.feature.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.novelstudio.core.data.CompareSelectionStore
import com.novelstudio.core.data.ImageRepository
import com.novelstudio.core.data.ArtistStringRepository
import com.novelstudio.core.data.PromptAssetRepository
import com.novelstudio.core.data.TagRepository
import com.novelstudio.core.data.WorkbenchDraftRepository
import com.novelstudio.core.data.GenerationRepositoryImpl
import com.novelstudio.core.model.ImageRecord
import com.novelstudio.core.model.ArtistString
import com.novelstudio.core.model.PromptAsset
import com.novelstudio.core.model.TagSource
import com.novelstudio.core.model.WorkbenchDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * 万级虚拟瀑布流图库 ViewModel（只依赖 ImageRepository 抽象）：
 * Room Flow 响应式数据源 + 收藏打标 + 「一键回填到工作台」。
 */
class GalleryViewModel(
    private val imageRepository: ImageRepository,
    private val draftRepository: WorkbenchDraftRepository,
    private val compareSelectionStore: CompareSelectionStore,
    private val artistRepository: ArtistStringRepository,
    private val promptRepository: PromptAssetRepository,
    private val tagRepository: TagRepository,
) : ViewModel() {

    private val eventChannel = Channel<GalleryEvent>(Channel.BUFFERED)
    internal val events: Flow<GalleryEvent> = eventChannel.receiveAsFlow()

    val records: Flow<PagingData<ImageRecord>> = imageRepository.paged()
        .cachedIn(viewModelScope)

    fun toggleLike(record: ImageRecord) {
        viewModelScope.launch {
            imageRepository.setFavorite(record.id, !record.isFavorite)
        }
    }

    fun markDislike(record: ImageRecord) {
        viewModelScope.launch {
            imageRepository.moveToTrash(record.id)
            eventChannel.send(GalleryEvent.Trashed(record.id))
        }
    }

    fun restoreFromTrash(id: String) { viewModelScope.launch { imageRepository.restoreFromTrash(id) } }

    fun delete(record: ImageRecord) {
        viewModelScope.launch { imageRepository.delete(record.id) }
    }

    fun batchFavorite(ids: Collection<String>, favorite: Boolean) {
        val selectedIds = ids.asSequence().filter(String::isNotBlank).distinct().toList()
        if (selectedIds.isEmpty()) return
        viewModelScope.launch {
            runCatching { imageRepository.setFavorites(selectedIds, favorite) }
                .onSuccess { updated ->
                    eventChannel.send(GalleryEvent.Message("已更新 $updated 张图片的标签", clearSelection = true))
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    eventChannel.send(GalleryEvent.Message(throwable.message ?: "批量打标失败"))
                }
        }
    }

    fun batchArchive(ids: Collection<String>) = batchStateChange(ids, "归档") { imageRepository.archiveAll(it) }

    fun batchTrash(ids: Collection<String>) = batchStateChange(ids, "移入垃圾箱") { imageRepository.moveAllToTrash(it) }

    private fun batchStateChange(ids: Collection<String>, label: String, action: suspend (List<String>) -> Int) {
        val selected = ids.asSequence().filter(String::isNotBlank).distinct().toList()
        if (selected.isEmpty()) return
        viewModelScope.launch {
            runCatching { action(selected) }
                .onSuccess { eventChannel.send(GalleryEvent.Message("已将 $it 张作品$label", clearSelection = true)) }
                .onFailure { eventChannel.send(GalleryEvent.Message(it.message ?: "$label 失败")) }
        }
    }

    fun requestExport(ids: Collection<String>) {
        val selectedIds = ids.asSequence().filter(String::isNotBlank).distinct().toList()
        if (selectedIds.isEmpty()) return
        viewModelScope.launch {
            runCatching { imageRepository.getByIds(selectedIds) }
                .onSuccess { records ->
                    if (records.isEmpty()) {
                        eventChannel.send(GalleryEvent.Message("所选图片记录已不存在"))
                    } else {
                        val missingCount = selectedIds.size - records.size
                        if (missingCount > 0) {
                            eventChannel.send(GalleryEvent.Message("$missingCount 条记录已不存在，已跳过"))
                        }
                        eventChannel.send(
                            GalleryEvent.StartExport(
                                records.map { GalleryExportItem(id = it.id, sourcePath = it.filePath) },
                            ),
                        )
                    }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    eventChannel.send(GalleryEvent.Message(throwable.message ?: "读取导出记录失败"))
                }
        }
    }

    fun requestCompare(ids: Collection<String>) {
        val selectedIds = ids.asSequence().filter(String::isNotBlank).distinct().toList()
        if (selectedIds.isEmpty()) return
        if (selectedIds.size > CompareSelectionStore.MAX_COMPARE_IMAGES) {
            viewModelScope.launch {
                eventChannel.send(
                    GalleryEvent.Message(
                        "对比实验室最多接收 ${CompareSelectionStore.MAX_COMPARE_IMAGES} 张图片",
                    ),
                )
            }
            return
        }
        viewModelScope.launch {
            runCatching { imageRepository.getByIds(selectedIds) }
                .onSuccess { records ->
                    if (records.size != selectedIds.size) {
                        eventChannel.send(GalleryEvent.Message("部分所选记录已不存在，请重新选择"))
                    } else {
                        val result = compareSelectionStore.replace(records.map(ImageRecord::id))
                        if (result.accepted) {
                            eventChannel.send(GalleryEvent.OpenCompare)
                        } else {
                            eventChannel.send(GalleryEvent.Message(result.message))
                        }
                    }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    eventChannel.send(GalleryEvent.Message(throwable.message ?: "加入对比失败"))
                }
        }
    }

    internal fun onExportResult(result: GalleryExportResult) {
        val message = when {
            result.failures.isEmpty() -> "已导出 ${result.exportedCount} 张原图到 ${result.destinationLabel}"
            result.exportedCount == 0 -> "导出失败：${result.failures.first().reason}"
            else -> "已导出 ${result.exportedCount} 张，${result.failures.size} 张失败"
        }
        viewModelScope.launch {
            eventChannel.send(GalleryEvent.Message(message, clearSelection = result.exportedCount > 0))
        }
    }

    internal fun onExportError(message: String) {
        viewModelScope.launch { eventChannel.send(GalleryEvent.Message(message)) }
    }

    fun forkToWorkbench(record: ImageRecord) {
        viewModelScope.launch {
            draftRepository.save(record.toWorkbenchDraft(System.currentTimeMillis()))
            eventChannel.send(GalleryEvent.OpenWorkbench)
        }
    }

    fun saveAsArtistString(record: ImageRecord) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = GenerationRepositoryImpl.randomId()
            runCatching {
                artistRepository.save(
                    ArtistString(
                        id = id,
                        name = "作品 ${record.id.take(8)}",
                        positivePrompt = record.prompt,
                        negativePrompt = record.uc,
                        modelId = record.model,
                        coverImageId = record.id,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }.onSuccess {
                eventChannel.send(GalleryEvent.Message("已保存为画师串"))
            }.onFailure { eventChannel.send(GalleryEvent.Message(it.message ?: "保存画师串失败")) }
        }
    }

    fun saveAsPrompt(record: ImageRecord) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            runCatching {
                promptRepository.save(
                    PromptAsset(
                        id = GenerationRepositoryImpl.randomId(),
                        name = "作品 ${record.id.take(8)}",
                        positivePrompt = record.prompt,
                        negativePrompt = record.uc,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }.onSuccess {
                eventChannel.send(GalleryEvent.Message("已保存为 Prompt"))
            }.onFailure { eventChannel.send(GalleryEvent.Message(it.message ?: "保存 Prompt 失败")) }
        }
    }

    fun bindTags(record: ImageRecord, rawTags: String) {
        viewModelScope.launch {
            val values = rawTags.split(',').map(String::trim).filter(String::isNotEmpty).distinct()
            runCatching {
                val tags = values.map { tagRepository.getOrCreate(it, TagSource.PERSONAL) }
                tagRepository.bindToImage(record.id, tags.map { it.id })
            }.onSuccess {
                eventChannel.send(GalleryEvent.Message("已绑定 ${values.size} 个 Tag"))
            }.onFailure { eventChannel.send(GalleryEvent.Message(it.message ?: "绑定 Tag 失败")) }
        }
    }
}

internal sealed interface GalleryEvent {
    data class Message(val text: String, val clearSelection: Boolean = false) : GalleryEvent
    data class StartExport(val items: List<GalleryExportItem>) : GalleryEvent
    data object OpenCompare : GalleryEvent
    data object OpenWorkbench : GalleryEvent
    data class Trashed(val imageId: String) : GalleryEvent
}
