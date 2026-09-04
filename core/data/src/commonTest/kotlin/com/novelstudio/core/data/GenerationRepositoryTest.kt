package com.novelstudio.core.data

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.novelstudio.core.database.AppDatabase
import com.novelstudio.core.database.ImageDao
import com.novelstudio.core.database.ImageEntity
import com.novelstudio.core.model.GenerationParameters
import com.novelstudio.core.model.GenerationPreflight
import com.novelstudio.core.model.NaiModel
import com.novelstudio.core.model.OpusBatteryState
import com.novelstudio.core.model.SubscriptionTier
import com.novelstudio.core.network.NaiApiException
import com.novelstudio.core.network.NovelAIApiService
import com.novelstudio.core.network.dto.SubscriptionDto
import com.novelstudio.core.storage.ImageFileStorage
import com.novelstudio.core.storage.StoredImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val PNG_BYTES = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

private class FakeDao : ImageDao {
    val store = MutableStateFlow<Map<String, ImageEntity>>(emptyMap())

    override fun observeAll(): Flow<List<ImageEntity>> =
        MutableStateFlow(store.value.values.toList())

    override fun pagingSource(): PagingSource<Int, ImageEntity> = object : PagingSource<Int, ImageEntity>() {
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ImageEntity> =
            LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)

        override fun getRefreshKey(state: PagingState<Int, ImageEntity>): Int? = null
    }

    override fun observeFirstUnreviewed(): Flow<ImageEntity?> = MutableStateFlow(
        store.value.values.filter { it.archivedAt == null && it.trashedAt == null }.maxByOrNull { it.createdAt },
    )

    override fun observeUnreviewedCount(): Flow<Int> = MutableStateFlow(
        store.value.values.count { it.archivedAt == null && it.trashedAt == null },
    )

    override suspend fun findById(id: String): ImageEntity? = store.value[id]

    override suspend fun findByIds(ids: List<String>): List<ImageEntity> =
        ids.mapNotNull(store.value::get)

    override suspend fun upsert(image: ImageEntity) {
        store.update { it + (image.id to image) }
    }

    override suspend fun upsertAll(images: List<ImageEntity>) {
        images.forEach { upsert(it) }
    }

    override suspend fun updateFavorite(id: String, favorite: Boolean): Int {
        val existed = store.value.containsKey(id)
        store.update { map ->
            map[id]?.let { map + (id to it.copy(isFavorite = favorite)) } ?: map
        }
        return if (existed) 1 else 0
    }

    override suspend fun updateFavorites(ids: List<String>, favorite: Boolean): Int {
        val existingIds = ids.filter(store.value::containsKey)
        store.update { map ->
            map.mapValues { (id, image) ->
                if (id in existingIds) image.copy(isFavorite = favorite) else image
            }
        }
        return existingIds.size
    }

    override suspend fun archive(id: String, archivedAt: Long, artistStringId: String?): Int = change(id) {
        it.copy(archivedAt = archivedAt, trashedAt = null, artistStringId = artistStringId ?: it.artistStringId)
    }

    override suspend fun archiveAll(ids: List<String>, archivedAt: Long): Int = ids.sumOf { id ->
        archive(id, archivedAt, null)
    }

    override suspend fun moveToTrash(id: String, trashedAt: Long): Int = change(id) {
        it.copy(trashedAt = trashedAt, archivedAt = null, isFavorite = false)
    }

    override suspend fun moveAllToTrash(ids: List<String>, trashedAt: Long): Int = ids.sumOf { id ->
        moveToTrash(id, trashedAt)
    }

    override suspend fun restoreFromTrash(id: String): Int = change(id) { it.copy(trashedAt = null) }

    override fun observeChildren(parentId: String): Flow<List<ImageEntity>> = MutableStateFlow(
        store.value.values.filter { it.parentImageId == parentId }.sortedBy { it.createdAt },
    )

    private fun change(id: String, transform: (ImageEntity) -> ImageEntity): Int {
        val value = store.value[id] ?: return 0
        store.value = store.value + (id to transform(value))
        return 1
    }

    override suspend fun delete(id: String) {
        store.update { it - id }
    }

    override suspend fun deleteAll(ids: List<String>) {
        ids.forEach { delete(it) }
    }

    override suspend fun count(): Int = store.value.size
}

private class FakeStorage : ImageFileStorage {
    val saved = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    var deletedCount = 0
    var saveAttempts = 0
    var failOnSaveAttempt: Int? = null
    var failDelete = false

    override suspend fun saveImage(id: String, pngBytes: ByteArray): StoredImage {
        saveAttempts++
        if (saveAttempts == failOnSaveAttempt) error("disk full")
        saved.update { it + (id to pngBytes) }
        return StoredImage("/fake/images/$id.png", "/fake/thumbs/$id.webp", "LEHV6n")
    }

    override suspend fun deleteImage(id: String) {
        deletedCount++
        if (failDelete) error("cannot delete")
    }
}

private class FakeApi(
    private val battery: OpusBatteryState,
    private val generateBehavior: () -> ByteArray,
) : NovelAIApiService {
    var generateAttempts = 0
    var batteryFailure: Throwable? = null
    var multipleImages: List<ByteArray>? = null

    override suspend fun generateImage(parameters: GenerationParameters): ByteArray {
        generateAttempts++
        return generateBehavior()
    }

    override suspend fun generateImages(parameters: GenerationParameters): List<ByteArray> {
        generateAttempts++
        return multipleImages ?: listOf(generateBehavior())
    }

    override suspend fun getSubscription(): SubscriptionDto =
        SubscriptionDto(tier = 3, active = true)

    override suspend fun getOpusBatteryState(): OpusBatteryState =
        batteryFailure?.let { throw it } ?: battery

    override suspend fun encodeVibe(imageBytes: ByteArray, model: String, informationExtracted: Float): ByteArray =
        imageBytes
}

class GenerationRepositoryTest {

    private fun repository(api: NovelAIApiService): Pair<GenerationRepositoryImpl, FakeDao> {
        val dao = FakeDao()
        val storage = FakeStorage()
        var idCounter = 0
        return GenerationRepositoryImpl(
            api = api,
            imageDao = dao,
            fileStorage = storage,
            idGenerator = { "id${idCounter++}" },
        ) to dao
    }

    @Test
    fun `success path saves file and upserts record`() = runTest {
        val opusHealthy = opusBattery(80)
        val (repo, dao) = repository(FakeApi(opusHealthy) { PNG_BYTES })

        val outcome = repo.generate(GenerationParameters(prompt = "1girl"))

        assertTrue(outcome is GenerationOutcome.Success)
        assertEquals("/fake/images/id0.png", outcome.record.filePath)
        assertEquals("LEHV6n", outcome.record.blurHash)
        assertEquals("1girl", outcome.record.prompt)
        assertEquals(1, dao.store.value.size)
        assertEquals(PNG_BYTES.toList(), (outcome.previewBytes).toList())
    }

    @Test
    fun `multi image response saves every PNG from one submission`() = runTest {
        val secondPng = PNG_BYTES + byteArrayOf(2)
        val api = FakeApi(opusBattery(80)) { PNG_BYTES }.apply {
            multipleImages = listOf(PNG_BYTES, secondPng)
        }
        val (repo, dao) = repository(api)

        val outcome = repo.generate(GenerationParameters(prompt = "batch", nSamples = 2))

        assertTrue(outcome is GenerationOutcome.NeedsAnlasConfirmation)
        assertEquals(0, api.generateAttempts, "未确认 Anlas 前不得提交批量请求")

        val paidOutcome = repo.generateWithAnlas(outcome.parameters)
        assertTrue(paidOutcome is GenerationOutcome.Success)
        assertEquals(2, paidOutcome.records.size)
        assertEquals(2, dao.store.value.size)
        assertEquals(1, api.generateAttempts, "n_samples 必须只产生一次 POST")
    }

    @Test
    fun `partial storage failure cleans files and database without resubmission`() = runTest {
        val api = FakeApi(opusBattery(80)) { PNG_BYTES }.apply {
            multipleImages = listOf(PNG_BYTES, PNG_BYTES + byteArrayOf(2))
        }
        val dao = FakeDao()
        val storage = FakeStorage().apply { failOnSaveAttempt = 2 }
        val repo = GenerationRepositoryImpl(api, dao, storage) { "id${storage.saveAttempts}" }

        val outcome = repo.generateWithAnlas(GenerationParameters(prompt = "batch", nSamples = 2))

        assertTrue(outcome is GenerationOutcome.Failure)
        assertTrue(outcome.submissionMayHaveCompleted)
        assertTrue(dao.store.value.isEmpty())
        assertEquals(1, storage.deletedCount)
        assertEquals(1, api.generateAttempts)
    }

    @Test
    fun `rollback failure is surfaced without resubmitting generation`() = runTest {
        val api = FakeApi(opusBattery(80)) { PNG_BYTES }.apply {
            multipleImages = listOf(PNG_BYTES, PNG_BYTES)
        }
        val dao = FakeDao()
        val storage = FakeStorage().apply {
            failOnSaveAttempt = 2
            failDelete = true
        }
        val repo = GenerationRepositoryImpl(api, dao, storage) { "id${storage.saveAttempts}" }

        val outcome = repo.generateWithAnlas(GenerationParameters(prompt = "batch", nSamples = 2))

        assertTrue(outcome is GenerationOutcome.Failure)
        assertTrue(outcome.message.contains("本地回滚未完整"))
        assertEquals(1, api.generateAttempts)
    }

    @Test
    fun `unavailable usage asks explicit anlas confirmation without model switching`() = runTest {
        val opusLow = opusBattery(0, unavailable = true)
        val (repo, _) = repository(FakeApi(opusLow) { PNG_BYTES })

        val parameters = GenerationParameters(model = NaiModel.V5_FULL)
        val outcome = repo.generate(parameters)

        assertTrue(outcome is GenerationOutcome.NeedsAnlasConfirmation)
        assertTrue(outcome.preflight is GenerationPreflight.RequiresConfirmation)
        assertEquals(NaiModel.V5_FULL, outcome.parameters.model)
    }

    @Test
    fun `auth failure fails immediately without retry`() = runTest {
        val opusHealthy = opusBattery(80)
        val api = FakeApi(opusHealthy) { throw NaiApiException(401, "HTTP 401") }
        val (repo, dao) = repository(api)

        val outcome = repo.generate(GenerationParameters())

        assertTrue(outcome is GenerationOutcome.Failure)
        assertTrue(outcome.message.contains("Token"))
        assertEquals(1, api.generateAttempts, "401 不应重试")
        assertTrue(dao.store.value.isEmpty())
    }

    @Test
    fun `server error never retries a potentially charged generation`() = runTest {
        val opusHealthy = opusBattery(80)
        val api = FakeApi(opusHealthy) { throw NaiApiException(503, "HTTP 503") }
        val (repo, _) = repository(api)

        val outcome = repo.generate(GenerationParameters())

        assertTrue(outcome is GenerationOutcome.Failure)
        assertEquals(1, api.generateAttempts, "付费生成结果不明时禁止自动重试")
    }

    @Test
    fun `image repository delete cleans database and disk`() = runTest {
        val opusHealthy = opusBattery(80)
        val dao = FakeDao()
        val storage = FakeStorage()
        val imageRepo = ImageRepositoryImpl(dao, storage)
        dao.upsert(ImageEntity(id = "a", filePath = "/x.png", thumbnailPath = "", blurHash = "", prompt = "p", uc = "", model = "m", seed = 1, steps = 1, scale = 1f, sampler = "k_euler", width = 1, height = 1, starRating = 3, isFavorite = false, hasTransparency = false, rawMetadataJson = "", createdAt = 0))

        imageRepo.delete("a")

        assertTrue(dao.store.value.isEmpty())
        assertEquals(1, storage.deletedCount)
    }

    @Test
    fun `database name constant sanity`() {
        assertEquals("novelai-studio.db", AppDatabase.DATABASE_NAME)
    }

    @Test
    fun `usage lookup failure stops before generation submission`() = runTest {
        val api = FakeApi(opusBattery(80)) { PNG_BYTES }.apply {
            batteryFailure = NaiApiException(503, "HTTP 503")
        }
        val (repo, dao) = repository(api)

        val outcome = repo.generate(GenerationParameters())

        assertTrue(outcome is GenerationOutcome.Failure)
        assertTrue(outcome.message.contains("未提交生成"))
        assertEquals(0, api.generateAttempts)
        assertTrue(dao.store.value.isEmpty())
    }

    @Test
    fun `vibe encoding result is wrapped with stable domain metadata`() = runTest {
        val (repo, _) = repository(FakeApi(opusBattery(80)) { PNG_BYTES })

        val vibe = repo.encodeVibe(
            imageBytes = byteArrayOf(4, 5, 6),
            displayName = "reference.png",
            model = NaiModel.V5_CURATED,
            informationExtracted = 0.75f,
        )

        assertEquals("id0", vibe.id)
        assertEquals("reference.png", vibe.displayName)
        assertEquals(NaiModel.V5_CURATED, vibe.model)
        assertEquals(0.75f, vibe.informationExtracted)
        assertEquals(listOf<Byte>(4, 5, 6), vibe.encoding.toList())
    }

    private fun opusBattery(percent: Int, unavailable: Boolean = false) = OpusBatteryState(
        tier = SubscriptionTier.OPUS,
        isSubscriptionActive = true,
        batteryPercent = percent,
        isUsageUnavailable = unavailable,
        timeUntilNextPercentSeconds = 60,
    )
}
