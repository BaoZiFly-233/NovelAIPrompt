package com.novelstudio.feature.workbench

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelstudio.core.data.ArtistStringRepository
import com.novelstudio.core.data.GenerationQueue
import com.novelstudio.core.data.GenerationRepository
import com.novelstudio.core.data.PromptAssetRepository
import com.novelstudio.core.data.TagRepository
import com.novelstudio.core.data.TagSuggestionRepository
import com.novelstudio.core.data.WorkbenchDraftRepository
import com.novelstudio.core.model.AspectPreset
import com.novelstudio.core.model.GenerationParameters
import com.novelstudio.core.model.NaiModel
import com.novelstudio.core.model.NoiseSchedule
import com.novelstudio.core.model.Sampler
import com.novelstudio.core.model.TaskStatus
import com.novelstudio.core.model.V5Character
import com.novelstudio.core.model.WorkbenchDraft
import com.novelstudio.core.model.normalizeTag
import com.novelstudio.core.network.NaiApiException
import com.novelstudio.core.network.OpusFreeCalculator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

class WorkbenchViewModel(
    private val generationRepository: GenerationRepository,
    private val generationQueue: GenerationQueue,
    private val draftRepository: WorkbenchDraftRepository,
    private val artistRepository: ArtistStringRepository,
    private val promptRepository: PromptAssetRepository,
    private val tagRepository: TagRepository,
    private val suggestionRepository: TagSuggestionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WorkbenchUiState())
    val uiState: StateFlow<WorkbenchUiState> = _uiState.asStateFlow()
    private var pendingVibeImageBytes: ByteArray? = null
    private var nextCharacterId = 1
    private var suggestionJob: Job? = null
    private var latestDraftTimestamp = Long.MIN_VALUE

    init {
        observeAssetsAndDraft()
        observeGenerationQueue()
        refreshBattery()
    }

    private fun observeAssetsAndDraft() {
        viewModelScope.launch { artistRepository.observeAll().collect { setTransient { copy(artistStrings = it) } } }
        viewModelScope.launch { promptRepository.observeAll().collect { setTransient { copy(promptAssets = it) } } }
        viewModelScope.launch { tagRepository.observeAll().collect { setTransient { copy(availableTags = it) } } }
        viewModelScope.launch {
            draftRepository.observe().collect { draft ->
                if (draft != null && draft.updatedAt > latestDraftTimestamp) {
                    latestDraftTimestamp = draft.updatedAt
                    setTransient { applyDraft(draft) }
                }
            }
        }
    }

    private fun observeGenerationQueue() {
        viewModelScope.launch {
            generationQueue.state.collect { items ->
                val waiting = items.firstOrNull { it.status == TaskStatus.WAITING_ANLAS_CONFIRMATION }
                setTransient {
                    copy(
                        queueItems = items,
                        isGenerating = items.any { it.status == TaskStatus.RUNNING },
                        pendingAnlasTaskId = waiting?.id,
                        needsAnlasConfirmation = waiting != null,
                        anlasConfirmationMessage = waiting?.parameters?.let(::anlasSummary),
                    )
                }
            }
        }
        viewModelScope.launch {
            generationQueue.success.collect { event ->
                setTransient {
                    copy(
                        message = "生成成功，${event.outcome.records.size} 张图片已保存到作品库",
                        errorMessage = null,
                        lastPreflight = event.outcome.preflight,
                        previewBitmap = decodePngPreview(event.outcome.previewBytes),
                    )
                }
                refreshBattery()
            }
        }
        viewModelScope.launch {
            generationQueue.failure.collect { event ->
                val suffix = if (event.outcome.submissionMayHaveCompleted) "；为避免重复扣费不会自动重试" else ""
                setTransient { copy(errorMessage = event.outcome.message + suffix) }
            }
        }
    }

    fun refreshBattery() = viewModelScope.launch {
        setTransient { copy(isBatteryLoading = true, batteryErrorMessage = null) }
        try {
            val battery = generationRepository.getBatteryState()
            setTransient { copy(battery = battery, isBatteryLoading = false) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            setTransient { copy(battery = null, isBatteryLoading = false, batteryErrorMessage = throwable.message ?: "V5 用量读取失败") }
        }
    }

    fun selectArtist(id: String?) = edit {
        val asset = artistStrings.firstOrNull { it.id == id }
        copy(selectedArtistStringId = asset?.id, artistPositive = asset?.positivePrompt.orEmpty(), artistNegative = asset?.negativePrompt.orEmpty())
    }
    fun selectPromptAsset(id: String?) = edit {
        val asset = promptAssets.firstOrNull { it.id == id }
        copy(selectedPromptAssetId = asset?.id, promptPositive = asset?.positivePrompt.orEmpty(), promptNegative = asset?.negativePrompt.orEmpty())
    }
    fun updateFreePrompt(value: String) = edit { copy(freePrompt = value) }
    fun updateNegativePrompt(value: String) = edit { copy(negativePrompt = value) }
    fun toggleTag(value: String) = edit {
        val normalized = normalizeTag(value)
        copy(orderedTags = if (normalized in orderedTags) orderedTags - normalized else orderedTags + normalized)
    }
    fun moveTag(from: Int, to: Int) = edit {
        if (from !in orderedTags.indices || to !in orderedTags.indices || from == to) return@edit this
        val mutable = orderedTags.toMutableList()
        val value = mutable.removeAt(from)
        mutable.add(to, value)
        copy(orderedTags = mutable)
    }

    fun requestTagSuggestions(query: String) {
        val normalized = normalizeTag(query)
        suggestionJob?.cancel()
        if (normalized.isBlank()) {
            setTransient { copy(tagSuggestions = emptyList(), isSuggestionsLoading = false, suggestionsOffline = false) }
            return
        }
        suggestionJob = viewModelScope.launch {
            setTransient { copy(isSuggestionsLoading = true) }
            try {
                val result = suggestionRepository.suggest(_uiState.value.model.id, normalized)
                setTransient { copy(tagSuggestions = result.suggestions, suggestionsOffline = result.isOffline, isSuggestionsLoading = false) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                setTransient { copy(tagSuggestions = emptyList(), isSuggestionsLoading = false) }
            }
        }
    }
    fun acceptTagSuggestion(tag: String) {
        if (normalizeTag(tag).isBlank()) return
        viewModelScope.launch { runCatching { tagRepository.getOrCreate(tag) } }
    }

    fun selectGenerationMode(mode: GenerationMode) = edit {
        copy(generationMode = mode, nSamples = if (mode == GenerationMode.BATCH_REVIEW) nSamples.coerceAtLeast(2) else 1)
    }
    fun selectModel(model: NaiModel) = edit {
        copy(model = model, transparentBackground = transparentBackground && model.supportsTransparency, errorMessage = characterLimitMessage(model, characterPrompts.size))
    }
    fun selectAspect(preset: AspectPreset) {
        val (width, height) = OpusFreeCalculator.clampResolution(preset.ratioWidth, preset.ratioHeight)
        edit { copy(aspect = preset, width = width, height = height) }
    }
    fun updateSteps(value: Int) = edit { copy(steps = value.coerceIn(1, 50)) }
    fun updateScale(value: Float) = edit { copy(scale = value.coerceIn(0f, 10f)) }
    fun updateCfgRescale(value: Float) = edit { copy(cfgRescale = value.coerceIn(0f, 1f)) }
    fun selectSampler(value: Sampler) = edit { copy(sampler = value) }
    fun selectNoiseSchedule(value: NoiseSchedule) = edit { copy(noiseSchedule = value) }
    fun updateSamples(value: Int) = edit { copy(nSamples = value.coerceIn(2, GenerationParameters.MAX_SAMPLES)) }
    fun toggleQualityTags(value: Boolean) = edit { copy(qualityToggle = value) }
    fun updateSeed(value: String) = edit { if (value.isEmpty() || value.all(Char::isDigit)) copy(seedText = value.take(10)) else this }
    fun toggleTransparent(value: Boolean) = edit { copy(transparentBackground = value && model.supportsTransparency) }
    fun toggleSmea(value: Boolean) = edit { copy(smea = value, smeaDyn = smeaDyn && value) }
    fun toggleSmeaDyn(value: Boolean) = edit { copy(smeaDyn = value && smea) }
    fun toggleVarietyPlus(value: Boolean) = edit { copy(varietyPlus = value) }
    fun toggleDecrisper(value: Boolean) = edit { copy(decrisper = value) }

    fun requestVibeEncoding(displayName: String, imageBytes: ByteArray) {
        val state = _uiState.value
        if (state.isEncodingVibe || state.needsVibeEncodingConfirmation) return
        if (imageBytes.isEmpty() || imageBytes.size > MAX_VIBE_SOURCE_BYTES) {
            setTransient { copy(errorMessage = "Vibe 图片为空或超过 32 MiB") }
            return
        }
        pendingVibeImageBytes = imageBytes.copyOf()
        setTransient { copy(needsVibeEncodingConfirmation = true, pendingVibeDisplayName = displayName.ifBlank { "未命名图片" }) }
    }
    fun updatePendingVibeInformation(value: Float) = setTransient { copy(pendingVibeInformationExtracted = value.coerceIn(0f, 1f)) }
    fun confirmVibeEncoding() {
        val bytes = pendingVibeImageBytes ?: return
        val state = _uiState.value
        val name = state.pendingVibeDisplayName ?: return
        pendingVibeImageBytes = null
        viewModelScope.launch {
            setTransient { copy(isEncodingVibe = true, needsVibeEncodingConfirmation = false) }
            try {
                val vibe = generationRepository.encodeVibe(bytes, name, state.model, state.pendingVibeInformationExtracted)
                edit { copy(vibeReferences = vibeReferences + vibe, isEncodingVibe = false, pendingVibeDisplayName = null) }
                refreshBattery()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                setTransient { copy(isEncodingVibe = false, errorMessage = NaiApiException.describe(throwable)) }
            }
        }
    }
    fun dismissVibeEncodingConfirmation() {
        pendingVibeImageBytes = null
        setTransient { copy(needsVibeEncodingConfirmation = false, pendingVibeDisplayName = null) }
    }
    fun updateVibeStrength(id: String, strength: Float) = edit {
        copy(vibeReferences = vibeReferences.map { if (it.id == id) it.copy(referenceStrength = strength.coerceIn(0f, 1f)) else it })
    }
    fun removeVibe(id: String) = edit { copy(vibeReferences = vibeReferences.filterNot { it.id == id }) }

    fun addCharacter() {
        val state = _uiState.value
        if (state.characterPrompts.size >= state.model.maxCharacterPrompts) return
        val index = state.characterPrompts.size
        val character = V5Character(
            id = "character-${nextCharacterId++}",
            centerX = ((index % 5) + 0.5f) / 5f,
            centerY = ((index / 5) + 0.5f) / 5f,
        )
        edit { copy(characterPrompts = characterPrompts + character, selectedCharacterId = character.id) }
    }
    fun selectCharacter(id: String) = setTransient { if (characterPrompts.none { it.id == id }) this else copy(selectedCharacterId = id) }
    fun moveCharacter(id: String, x: Float, y: Float) = updateCharacter(id) { copy(centerX = x.coerceIn(0f, 1f), centerY = y.coerceIn(0f, 1f)) }
    fun updateCharacterPrompt(id: String, prompt: String) = updateCharacter(id) { copy(prompt = prompt) }
    fun updateCharacterUc(id: String, uc: String) = updateCharacter(id) { copy(uc = uc) }
    fun removeCharacter(id: String) = edit {
        val remaining = characterPrompts.filterNot { it.id == id }
        copy(characterPrompts = remaining, selectedCharacterId = remaining.firstOrNull()?.id)
    }

    fun generate() {
        val state = _uiState.value
        if (!state.canGenerate) {
            setTransient { copy(errorMessage = "请完成 Prompt，并成功读取订阅与计费状态") }
            return
        }
        val parameters = state.parameters(Random.nextLong(0L, 0xFFFFFFFFL))
        viewModelScope.launch {
            try {
                generationQueue.enqueue(parameters)
                setTransient { copy(message = "已加入生成队列", errorMessage = null) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                setTransient { copy(errorMessage = throwable.message ?: "生成任务入队失败") }
            }
        }
    }
    fun rejectAndGenerateNext() { if (_uiState.value.generationMode == GenerationMode.DRAW_UNTIL_LIKED) generate() }
    fun finishDrawMode() = edit { copy(generationMode = GenerationMode.SINGLE, nSamples = 1, message = "已结束抽取") }
    fun confirmAnlas() { _uiState.value.pendingAnlasTaskId?.let { viewModelScope.launch { generationQueue.confirmAnlas(it) } } }
    fun dismissAnlasConfirmation() { _uiState.value.pendingAnlasTaskId?.let { viewModelScope.launch { generationQueue.cancel(it) } } }
    fun cancelQueueTask(id: String) { viewModelScope.launch { generationQueue.cancel(id) } }

    private fun edit(reducer: WorkbenchUiState.() -> WorkbenchUiState) {
        _uiState.value = _uiState.value.reducer()
        val timestamp = maxOf(currentTimeMillis(), latestDraftTimestamp + 1L)
        latestDraftTimestamp = timestamp
        val draft = _uiState.value.toDraft(timestamp)
        viewModelScope.launch { draftRepository.save(draft) }
    }
    private fun setTransient(reducer: WorkbenchUiState.() -> WorkbenchUiState) { _uiState.value = _uiState.value.reducer() }
    private fun updateCharacter(id: String, transform: V5Character.() -> V5Character) = edit {
        copy(characterPrompts = characterPrompts.map { if (it.id == id) it.transform() else it })
    }
    private fun WorkbenchUiState.applyDraft(draft: WorkbenchDraft) = copy(
        selectedArtistStringId = draft.artistStringId,
        selectedPromptAssetId = draft.promptAssetId,
        artistPositive = draft.artistPositive,
        artistNegative = draft.artistNegative,
        promptPositive = draft.promptPositive,
        promptNegative = draft.promptNegative,
        orderedTags = draft.orderedTags,
        freePrompt = draft.freePrompt,
        negativePrompt = draft.negativePrompt,
        model = draft.model,
        width = draft.width,
        height = draft.height,
        steps = draft.steps,
        scale = draft.scale,
        cfgRescale = draft.cfgRescale,
        sampler = draft.sampler,
        noiseSchedule = draft.noiseSchedule,
        nSamples = draft.nSamples,
        qualityToggle = draft.qualityToggle,
        seedText = draft.seed.takeIf { it >= 0 }?.toString().orEmpty(),
        transparentBackground = draft.transparentBackground,
    )
    private fun characterLimitMessage(model: NaiModel, count: Int): String? =
        if (count > model.maxCharacterPrompts) "${model.displayName} 最多支持 ${model.maxCharacterPrompts} 个角色" else null
    private fun anlasSummary(parameters: GenerationParameters): String =
        "${parameters.model.displayName} · ${parameters.width}×${parameters.height} · ${parameters.steps} 步 · ${parameters.nSamples} 张；继续可能消耗 ImageAnlas。"

    private companion object { const val MAX_VIBE_SOURCE_BYTES = 32 * 1024 * 1024 }
}
