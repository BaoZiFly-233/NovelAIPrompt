package com.novelstudio.core.data

import com.novelstudio.core.database.AppDatabase
import com.novelstudio.core.database.ImageDao
import com.novelstudio.core.database.ImageEntity
import com.novelstudio.core.model.DispatchDecision
import com.novelstudio.core.model.GenerationParameters
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

    override fun observeByStar(minStar: Int): Flow<List<ImageEntity>> = observeAll()

    override suspend fun findById(id: String): ImageEntity? = store.value[id]

    override suspend fun upsert(image: ImageEntity) {
        store.update { it + (image.id to image) }
    }

    override suspend fun upsertAll(images: List<ImageEntity>) {
        images.forEach { upsert(it) }
    }

    override suspend fun updateStarRating(id: String, rating: Int) {
        store.update { map -> map[id]?.let { map + (id to it.copy(starRating = rating)) } ?: map }
    }

    override suspend fun updateFavorite(id: String, favorite: Boolean) {
        store.update { map -> map[id]?.let { map + (id to it.copy(isFavorite = favorite)) } ?: map }
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

    override suspend fun saveImage(id: String, pngBytes: ByteArray): StoredImage {
        saved.update { it + (id to pngBytes) }
        return StoredImage("/fake/images/$id.png", "/fake/thumbs/$id.webp", "LEHV6n")
    }

    override suspend fun deleteImage(id: String) {
        deletedCount++
    }
}

private class FakeApi(
    private val battery: OpusBatteryState,
    private val generateBehavior: () -> ByteArray,
) : NovelAIApiService {
    var generateAttempts = 0

    override suspend fun generateImage(parameters: GenerationParameters): ByteArray {
        generateAttempts++
        return generateBehavior()
    }

    override suspend fun getSubscription(): SubscriptionDto =
        SubscriptionDto(tier = 3, active = true)

    override suspend fun getOpusBatteryState(): OpusBatteryState = battery
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
        val opusHealthy = OpusBatteryState(SubscriptionTier.OPUS, 0, 80f)
        val (repo, dao) = repository(FakeApi(opusHealthy) { PNG_BYTES })

        val outcome = repo.generate(GenerationParameters(prompt = "1girl"), explorationMode = false)

        assertTrue(outcome is GenerationOutcome.Success)
        assertEquals("/fake/images/id0.png", outcome.record.filePath)
        assertEquals("LEHV6n", outcome.record.blurHash)
        assertEquals("1girl", outcome.record.prompt)
        assertEquals(1, dao.store.value.size)
        assertEquals(PNG_BYTES.toList(), (outcome.previewBytes).toList())
    }

    @Test
    fun `exploration with low battery degrades to V4_5`() = runTest {
        val opusLow = OpusBatteryState(SubscriptionTier.OPUS, 0, 20f)
        val (repo, dao) = repository(FakeApi(opusLow) { PNG_BYTES })

        val outcome = repo.generate(GenerationParameters(model = NaiModel.V5_FULL), explorationMode = true)

        assertTrue(outcome is GenerationOutcome.Success)
        assertEquals(DispatchDecision.FALLBACK_V4_5, outcome.decision)
        assertEquals(NaiModel.V4_5_FULL.id, dao.store.value.values.first().model)
    }

    @Test
    fun `low battery without exploration asks anlas confirmation`() = runTest {
        val opusLow = OpusBatteryState(SubscriptionTier.OPUS, 500, 5f)
        val (repo, _) = repository(FakeApi(opusLow) { PNG_BYTES })

        val outcome = repo.generate(GenerationParameters(), explorationMode = false)

        assertTrue(outcome is GenerationOutcome.NeedsAnlasConfirmation)
        assertEquals(DispatchDecision.CONFIRM_ANLAS, outcome.decision)
    }

    @Test
    fun `auth failure fails immediately without retry`() = runTest {
        val opusHealthy = OpusBatteryState(SubscriptionTier.OPUS, 0, 80f)
        val api = FakeApi(opusHealthy) { throw NaiApiException(401, "HTTP 401") }
        val (repo, dao) = repository(api)

        val outcome = repo.generate(GenerationParameters(), explorationMode = false)

        assertTrue(outcome is GenerationOutcome.Failure)
        assertTrue(outcome.message.contains("Token"))
        assertEquals(1, api.generateAttempts, "401 不应重试")
        assertTrue(dao.store.value.isEmpty())
    }

    @Test
    fun `server error retries three times then fails`() = runTest {
        val opusHealthy = OpusBatteryState(SubscriptionTier.OPUS, 0, 80f)
        val api = FakeApi(opusHealthy) { throw NaiApiException(503, "HTTP 503") }
        val (repo, _) = repository(api)

        val outcome = repo.generate(GenerationParameters(), explorationMode = false)

        assertTrue(outcome is GenerationOutcome.Failure)
        assertEquals(3, api.generateAttempts, "503 应重试至多 3 次")
    }

    @Test
    fun `image repository delete cleans database and disk`() = runTest {
        val opusHealthy = OpusBatteryState(SubscriptionTier.OPUS, 0, 80f)
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
}
