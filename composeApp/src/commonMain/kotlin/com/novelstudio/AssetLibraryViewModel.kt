package com.novelstudio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelstudio.core.data.ArtistStringRepository
import com.novelstudio.core.data.GenerationRepositoryImpl
import com.novelstudio.core.data.PromptAssetRepository
import com.novelstudio.core.data.TagRepository
import com.novelstudio.core.model.ArtistString
import com.novelstudio.core.model.PersonalTag
import com.novelstudio.core.model.PromptAsset
import com.novelstudio.core.model.TagSource
import com.novelstudio.core.model.normalizeTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AssetLibraryState(
    val artists: List<ArtistString> = emptyList(),
    val prompts: List<PromptAsset> = emptyList(),
    val tags: List<PersonalTag> = emptyList(),
    val message: String? = null,
)

class AssetLibraryViewModel(
    private val artistRepository: ArtistStringRepository,
    private val promptRepository: PromptAssetRepository,
    private val tagRepository: TagRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AssetLibraryState())
    val state: StateFlow<AssetLibraryState> = _state.asStateFlow()

    init {
        viewModelScope.launch { artistRepository.observeAll().collect { values -> _state.value = _state.value.copy(artists = values) } }
        viewModelScope.launch { promptRepository.observeAll().collect { values -> _state.value = _state.value.copy(prompts = values) } }
        viewModelScope.launch { tagRepository.observeAll().collect { values -> _state.value = _state.value.copy(tags = values) } }
    }

    fun saveArtist(existing: ArtistString?, name: String, positive: String, negative: String, notes: String) {
        val now = System.currentTimeMillis()
        launchMutation("画师串已保存") {
            artistRepository.save(
                ArtistString(
                    id = existing?.id ?: GenerationRepositoryImpl.randomId(),
                    name = name,
                    positivePrompt = positive,
                    negativePrompt = negative,
                    modelId = existing?.modelId,
                    parameterOverridesJson = existing?.parameterOverridesJson,
                    coverImageId = existing?.coverImageId,
                    notes = notes,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                ),
            )
        }
    }

    fun deleteArtist(id: String) = launchMutation("画师串已删除；历史作品快照未改变") { artistRepository.delete(id) }

    fun savePrompt(existing: PromptAsset?, name: String, positive: String, negative: String, notes: String) {
        val now = System.currentTimeMillis()
        launchMutation("Prompt 已保存") {
            promptRepository.save(
                PromptAsset(
                    id = existing?.id ?: GenerationRepositoryImpl.randomId(),
                    name = name,
                    positivePrompt = positive,
                    negativePrompt = negative,
                    notes = notes,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                ),
            )
        }
    }

    fun deletePrompt(id: String) = launchMutation("Prompt 已删除；历史作品快照未改变") { promptRepository.delete(id) }

    fun saveTag(existing: PersonalTag?, displayValue: String, group: String, notes: String, favorite: Boolean) {
        val normalized = normalizeTag(displayValue)
        val now = System.currentTimeMillis()
        launchMutation("Tag 已保存") {
            tagRepository.save(
                PersonalTag(
                    id = existing?.id ?: GenerationRepositoryImpl.randomId(),
                    normalizedValue = normalized,
                    displayValue = displayValue,
                    groupName = group.takeIf(String::isNotBlank),
                    notes = notes,
                    isFavorite = favorite,
                    source = existing?.source ?: TagSource.PERSONAL,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                ),
            )
        }
    }

    fun deleteTag(id: String) = launchMutation("Tag 已删除，图片绑定已自动清理") { tagRepository.delete(id) }

    fun clearMessage() { _state.value = _state.value.copy(message = null) }

    private fun launchMutation(success: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.value = try {
                block()
                _state.value.copy(message = success)
            } catch (throwable: Throwable) {
                _state.value.copy(message = throwable.message ?: "保存失败")
            }
        }
    }
}
