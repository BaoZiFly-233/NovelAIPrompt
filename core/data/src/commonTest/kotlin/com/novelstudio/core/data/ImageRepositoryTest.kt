package com.novelstudio.core.data

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.novelstudio.core.database.ImageDao
import com.novelstudio.core.database.ImageEntity
import com.novelstudio.core.model.ImageRecord
import com.novelstudio.core.storage.ImageFileStorage
import com.novelstudio.core.storage.StoredImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImageRepositoryTest {

    @Test
    fun batchFavoriteDeduplicatesIdsAndArchiveUsesExplicitState() = runTest {
        val dao = RecordingImageDao(listOf(entity("a"), entity("b")))
        val repository = ImageRepositoryImpl(dao, NoOpStorage)

        val likedCount = repository.setFavorites(listOf("a", "b", "a", ""), true)
        repository.archive("a", null)

        assertEquals(2, likedCount)
        assertEquals(1, dao.batchUpdateCalls)
        assertTrue(dao.entities.getValue("a").archivedAt != null)
        assertTrue(dao.entities.getValue("b").isFavorite)
    }

    @Test
    fun emptyBatchesAreNoOps() = runTest {
        val dao = RecordingImageDao(listOf(entity("a")))
        val repository = ImageRepositoryImpl(dao, NoOpStorage)

        assertEquals(0, repository.setFavorites(emptyList(), true))
        assertEquals(0, repository.moveAllToTrash(emptyList()))
        assertEquals(0, dao.batchUpdateCalls)
    }

    @Test
    fun getByIdsPreservesRequestedOrderAndSkipsMissingRecords() = runTest {
        val dao = RecordingImageDao(listOf(entity("first"), entity("second")))
        val repository = ImageRepositoryImpl(dao, NoOpStorage)

        val records = repository.getByIds(listOf("second", "missing", "first", "second"))

        assertEquals(listOf("second", "first"), records.map(ImageRecord::id))
    }

    @Test
    fun swipeSourceKeepsOnlyUnarchivedAndUntrashedRecord() = runTest {
        val dao = RecordingImageDao(
            listOf(
                entity("neutral"),
                entity("archived").copy(archivedAt = 2L),
                entity("trashed").copy(trashedAt = 3L),
            ),
        )
        val repository = ImageRepositoryImpl(dao, NoOpStorage)

        assertEquals("neutral", repository.observeNextUnreviewed().first()?.id)
        assertEquals(1, repository.observeUnreviewedCount().first())
    }
}

private class RecordingImageDao(initial: List<ImageEntity>) : ImageDao {
    val entities = initial.associateBy(ImageEntity::id).toMutableMap()
    var batchUpdateCalls = 0

    override fun observeAll(): Flow<List<ImageEntity>> = MutableStateFlow(entities.values.toList())

    override fun pagingSource(): PagingSource<Int, ImageEntity> = object : PagingSource<Int, ImageEntity>() {
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ImageEntity> =
            LoadResult.Page(emptyList(), null, null)

        override fun getRefreshKey(state: PagingState<Int, ImageEntity>): Int? = null
    }

    override fun observeFirstUnreviewed(): Flow<ImageEntity?> =
        MutableStateFlow(entities.values.firstOrNull { it.archivedAt == null && it.trashedAt == null })

    override fun observeUnreviewedCount(): Flow<Int> =
        MutableStateFlow(entities.values.count { it.archivedAt == null && it.trashedAt == null })

    override suspend fun findById(id: String): ImageEntity? = entities[id]

    override suspend fun findByIds(ids: List<String>): List<ImageEntity> =
        ids.mapNotNull(entities::get).reversed()

    override suspend fun upsert(image: ImageEntity) {
        entities[image.id] = image
    }

    override suspend fun upsertAll(images: List<ImageEntity>) = images.forEach { upsert(it) }

    override suspend fun updateFavorite(id: String, favorite: Boolean): Int {
        val image = entities[id] ?: return 0
        entities[id] = image.copy(isFavorite = favorite)
        return 1
    }

    override suspend fun updateFavorites(ids: List<String>, favorite: Boolean): Int {
        batchUpdateCalls++
        val existingIds = ids.filter(entities::containsKey)
        existingIds.forEach { id ->
            entities[id] = entities.getValue(id).copy(isFavorite = favorite)
        }
        return existingIds.size
    }

    override suspend fun archive(id: String, archivedAt: Long, artistStringId: String?): Int = change(id) {
        it.copy(archivedAt = archivedAt, trashedAt = null, artistStringId = artistStringId ?: it.artistStringId)
    }

    override suspend fun archiveAll(ids: List<String>, archivedAt: Long): Int = ids.sumOf { archive(it, archivedAt, null) }

    override suspend fun moveToTrash(id: String, trashedAt: Long): Int = change(id) {
        it.copy(trashedAt = trashedAt, archivedAt = null, isFavorite = false)
    }

    override suspend fun moveAllToTrash(ids: List<String>, trashedAt: Long): Int = ids.sumOf { moveToTrash(it, trashedAt) }

    override suspend fun restoreFromTrash(id: String): Int = change(id) { it.copy(trashedAt = null) }

    override fun observeChildren(parentId: String): Flow<List<ImageEntity>> =
        MutableStateFlow(entities.values.filter { it.parentImageId == parentId })

    private fun change(id: String, transform: (ImageEntity) -> ImageEntity): Int {
        val image = entities[id] ?: return 0
        entities[id] = transform(image)
        return 1
    }

    override suspend fun delete(id: String) {
        entities.remove(id)
    }

    override suspend fun deleteAll(ids: List<String>) {
        ids.forEach(entities::remove)
    }

    override suspend fun count(): Int = entities.size
}

private object NoOpStorage : ImageFileStorage {
    override suspend fun saveImage(id: String, pngBytes: ByteArray): StoredImage = error("unused")
    override suspend fun deleteImage(id: String) = Unit
}

private fun entity(id: String) = ImageEntity(
    id = id,
    filePath = "/images/$id.png",
    thumbnailPath = "/thumbs/$id.png",
    blurHash = "",
    prompt = id,
    uc = "",
    model = "nai-diffusion-5-full",
    seed = 1,
    steps = 28,
    scale = 6f,
    sampler = "k_euler",
    width = 1024,
    height = 1024,
    starRating = 3,
    isFavorite = false,
    hasTransparency = false,
    rawMetadataJson = "",
    createdAt = 1,
)
