package com.novelstudio.core.data

import com.novelstudio.core.database.ArtistStringDao
import com.novelstudio.core.database.ArtistStringEntity
import com.novelstudio.core.database.ImageTagCrossRef
import com.novelstudio.core.database.ImageTagDao
import com.novelstudio.core.database.PromptAssetDao
import com.novelstudio.core.database.PromptAssetEntity
import com.novelstudio.core.database.TagDao
import com.novelstudio.core.database.TagEntity
import com.novelstudio.core.database.TagSuggestionCacheDao
import com.novelstudio.core.database.TagSuggestionCacheEntity
import com.novelstudio.core.database.WorkbenchDraftDao
import com.novelstudio.core.database.WorkbenchDraftEntity
import com.novelstudio.core.model.ArtistString
import com.novelstudio.core.model.PersonalTag
import com.novelstudio.core.model.PromptAsset
import com.novelstudio.core.model.TagSource
import com.novelstudio.core.model.TagSuggestion
import com.novelstudio.core.model.WorkbenchDraft
import com.novelstudio.core.model.normalizeTag
import com.novelstudio.core.network.NovelAIApiService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

interface ArtistStringRepository {
    fun observeAll(): Flow<List<ArtistString>>
    suspend fun get(id: String): ArtistString?
    suspend fun save(value: ArtistString)
    suspend fun delete(id: String)
}

interface PromptAssetRepository {
    fun observeAll(): Flow<List<PromptAsset>>
    suspend fun get(id: String): PromptAsset?
    suspend fun save(value: PromptAsset)
    suspend fun delete(id: String)
}

interface TagRepository {
    fun observeAll(): Flow<List<PersonalTag>>
    suspend fun save(value: PersonalTag)
    suspend fun getOrCreate(displayValue: String, source: TagSource = TagSource.PERSONAL): PersonalTag
    suspend fun delete(id: String)
    suspend fun bindToImage(imageId: String, orderedTagIds: List<String>)
}

interface WorkbenchDraftRepository {
    fun observe(): Flow<WorkbenchDraft?>
    suspend fun get(): WorkbenchDraft?
    suspend fun save(value: WorkbenchDraft)
}

data class TagSuggestionResult(
    val suggestions: List<TagSuggestion>,
    val isOffline: Boolean,
)

interface TagSuggestionRepository {
    suspend fun suggest(model: String, prompt: String, language: String = "en"): TagSuggestionResult
}

class RoomArtistStringRepository(private val dao: ArtistStringDao) : ArtistStringRepository {
    override fun observeAll(): Flow<List<ArtistString>> = dao.observeAll().map { rows -> rows.map(ArtistStringEntity::toModel) }
    override suspend fun get(id: String): ArtistString? = dao.findById(id)?.toModel()
    override suspend fun save(value: ArtistString) {
        require(value.name.isNotBlank()) { "画师串名称不能为空" }
        require(value.positivePrompt.isNotBlank()) { "画师串正向内容不能为空" }
        dao.upsert(value.toEntity())
    }
    override suspend fun delete(id: String) = dao.delete(id)
}

class RoomPromptAssetRepository(private val dao: PromptAssetDao) : PromptAssetRepository {
    override fun observeAll(): Flow<List<PromptAsset>> = dao.observeAll().map { rows -> rows.map(PromptAssetEntity::toModel) }
    override suspend fun get(id: String): PromptAsset? = dao.findById(id)?.toModel()
    override suspend fun save(value: PromptAsset) {
        require(value.name.isNotBlank()) { "Prompt 名称不能为空" }
        require(value.positivePrompt.isNotBlank()) { "Prompt 正向内容不能为空" }
        dao.upsert(value.toEntity())
    }
    override suspend fun delete(id: String) = dao.delete(id)
}

class RoomTagRepository(
    private val tagDao: TagDao,
    private val imageTagDao: ImageTagDao,
    private val idGenerator: () -> String = { GenerationRepositoryImpl.randomId() },
    private val clock: () -> Long = { System.currentTimeMillis() },
) : TagRepository {
    override fun observeAll(): Flow<List<PersonalTag>> = tagDao.observeAll().map { rows -> rows.map(TagEntity::toModel) }

    override suspend fun save(value: PersonalTag) {
        require(value.normalizedValue == normalizeTag(value.normalizedValue)) { "Tag 必须使用规范化值" }
        require(value.normalizedValue.isNotBlank()) { "Tag 不能为空" }
        tagDao.upsert(value.toEntity())
    }

    override suspend fun getOrCreate(displayValue: String, source: TagSource): PersonalTag {
        val normalized = normalizeTag(displayValue)
        require(normalized.isNotBlank()) { "Tag 不能为空" }
        tagDao.findByNormalizedValue(normalized)?.let { return it.toModel() }
        val now = clock()
        return PersonalTag(
            id = idGenerator(),
            normalizedValue = normalized,
            displayValue = displayValue.trim().ifBlank { normalized },
            source = source,
            createdAt = now,
            updatedAt = now,
        ).also { tagDao.upsert(it.toEntity()) }
    }

    override suspend fun delete(id: String) = tagDao.delete(id)

    override suspend fun bindToImage(imageId: String, orderedTagIds: List<String>) {
        val ids = orderedTagIds.asSequence().filter(String::isNotBlank).distinct().toList()
        val existing = tagDao.findByIds(ids).map(TagEntity::id).toSet()
        require(existing.size == ids.size) { "存在已删除或无效的 Tag" }
        imageTagDao.clearForImage(imageId)
        val now = clock()
        imageTagDao.upsertAll(ids.mapIndexed { index, id -> ImageTagCrossRef(imageId, id, index, now) })
    }
}

class RoomWorkbenchDraftRepository(private val dao: WorkbenchDraftDao) : WorkbenchDraftRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun observe(): Flow<WorkbenchDraft?> = dao.observe().map { it?.toModel() }
    override suspend fun get(): WorkbenchDraft? = dao.get()?.toModel()
    override suspend fun save(value: WorkbenchDraft) = dao.upsert(value.toEntity())

    private fun WorkbenchDraftEntity.toModel(): WorkbenchDraft {
        val snapshot = runCatching { json.decodeFromString(WorkbenchDraft.serializer(), generationParametersJson) }
            .getOrDefault(WorkbenchDraft())
        val tags = runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), orderedTagsJson)
        }.getOrDefault(emptyList())
        return snapshot.copy(
            artistStringId = artistStringId,
            promptAssetId = promptAssetId,
            artistPositive = artistPositive,
            artistNegative = artistNegative,
            promptPositive = promptPositive,
            promptNegative = promptNegative,
            orderedTags = tags,
            freePrompt = freePrompt,
            negativePrompt = negativePrompt,
            updatedAt = updatedAt,
        )
    }

    private fun WorkbenchDraft.toEntity() = WorkbenchDraftEntity(
        artistStringId = artistStringId,
        promptAssetId = promptAssetId,
        artistPositive = artistPositive,
        artistNegative = artistNegative,
        promptPositive = promptPositive,
        promptNegative = promptNegative,
        orderedTagsJson = json.encodeToString(ListSerializer(String.serializer()), orderedTags),
        freePrompt = freePrompt,
        negativePrompt = negativePrompt,
        generationParametersJson = json.encodeToString(WorkbenchDraft.serializer(), this),
        updatedAt = updatedAt,
    )
}

class CachedTagSuggestionRepository(
    private val api: NovelAIApiService,
    private val cacheDao: TagSuggestionCacheDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : TagSuggestionRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(TagSuggestion.serializer())

    override suspend fun suggest(model: String, prompt: String, language: String): TagSuggestionResult {
        val query = normalizeTag(prompt)
        require(model.isNotBlank() && query.isNotBlank()) { "模型和 Tag 查询词不能为空" }
        require(language == "en" || language == "jp") { "Tag 建议语言只支持 en 或 jp" }
        try {
            val suggestions = api.suggestTags(model, query, language)
            cacheDao.upsert(
                TagSuggestionCacheEntity(model, language, query, json.encodeToString(serializer, suggestions), clock()),
            )
            return TagSuggestionResult(suggestions, isOffline = false)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            val cached = cacheDao.find(model, language, query) ?: throw throwable
            return TagSuggestionResult(
                suggestions = json.decodeFromString(serializer, cached.suggestionsJson),
                isOffline = true,
            )
        }
    }
}

private fun normalizeName(value: String): String = value.trim().split(Regex("\\s+")).joinToString(" ").lowercase()

private fun ArtistStringEntity.toModel() = ArtistString(
    id, name, positivePrompt, negativePrompt, modelId, parameterOverridesJson, coverImageId, notes, createdAt, updatedAt,
)
private fun ArtistString.toEntity() = ArtistStringEntity(
    id, name.trim(), normalizeName(name), positivePrompt.trim(), negativePrompt.trim(), modelId,
    parameterOverridesJson, coverImageId, notes.trim(), createdAt, updatedAt,
)
private fun PromptAssetEntity.toModel() = PromptAsset(id, name, positivePrompt, negativePrompt, notes, createdAt, updatedAt)
private fun PromptAsset.toEntity() = PromptAssetEntity(
    id, name.trim(), normalizeName(name), positivePrompt.trim(), negativePrompt.trim(), notes.trim(), createdAt, updatedAt,
)
private fun TagEntity.toModel() = PersonalTag(
    id, normalizedValue, displayValue, groupName, notes, isFavorite,
    runCatching { TagSource.valueOf(source) }.getOrDefault(TagSource.PERSONAL), createdAt, updatedAt,
)
private fun PersonalTag.toEntity() = TagEntity(
    id, normalizedValue, displayValue.trim(), groupName?.trim()?.takeIf(String::isNotEmpty), notes.trim(),
    isFavorite, source.name, createdAt, updatedAt,
)
