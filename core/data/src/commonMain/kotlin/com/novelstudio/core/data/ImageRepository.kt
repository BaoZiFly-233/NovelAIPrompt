package com.novelstudio.core.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.novelstudio.core.database.ImageDao
import com.novelstudio.core.database.ImageEntity
import com.novelstudio.core.model.ImageRecord
import com.novelstudio.core.model.ImageOperation
import com.novelstudio.core.database.LEGACY_NEUTRAL_RATING
import com.novelstudio.core.database.LEGACY_FAVORITE_RATING
import com.novelstudio.core.storage.ImageFileStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room 实体 ↔ 领域记录的双向映射 */
fun ImageEntity.toRecord(): ImageRecord = ImageRecord(
    id = id,
    filePath = filePath,
    thumbnailPath = thumbnailPath,
    blurHash = blurHash,
    prompt = prompt,
    uc = uc,
    model = model,
    seed = seed,
    steps = steps,
    scale = scale,
    sampler = sampler,
    width = width,
    height = height,
    isFavorite = isFavorite,
    hasTransparency = hasTransparency,
    rawMetadataJson = rawMetadataJson,
    artistStringId = artistStringId,
    promptAssetId = promptAssetId,
    parentImageId = parentImageId,
    operationType = runCatching { ImageOperation.valueOf(operationType) }.getOrDefault(ImageOperation.IMPORT),
    generationSnapshotJson = generationSnapshotJson,
    mimeType = mimeType,
    archivedAt = archivedAt,
    trashedAt = trashedAt,
    createdAt = createdAt,
)

fun ImageRecord.toEntity(): ImageEntity = ImageEntity(
    id = id,
    filePath = filePath,
    thumbnailPath = thumbnailPath,
    blurHash = blurHash,
    prompt = prompt,
    uc = uc,
    model = model,
    seed = seed,
    steps = steps,
    scale = scale,
    sampler = sampler,
    width = width,
    height = height,
    starRating = if (isFavorite) LEGACY_FAVORITE_RATING else LEGACY_NEUTRAL_RATING,
    isFavorite = isFavorite,
    hasTransparency = hasTransparency,
    rawMetadataJson = rawMetadataJson,
    artistStringId = artistStringId,
    promptAssetId = promptAssetId,
    parentImageId = parentImageId,
    operationType = operationType.name,
    generationSnapshotJson = generationSnapshotJson,
    mimeType = mimeType,
    archivedAt = archivedAt,
    trashedAt = trashedAt,
    createdAt = createdAt,
)

/** 图库仓储抽象：ViewModel 只依赖此接口（DIP），不直接触碰 DAO 与文件系统 */
interface ImageRepository {
    fun observeAll(): Flow<List<ImageRecord>>
    fun paged(): Flow<PagingData<ImageRecord>>
    suspend fun getById(id: String): ImageRecord?
    suspend fun getByIds(ids: Collection<String>): List<ImageRecord>
    suspend fun setFavorite(id: String, favorite: Boolean)
    suspend fun setFavorites(ids: Collection<String>, favorite: Boolean): Int
    suspend fun archive(id: String, artistStringId: String?)
    suspend fun archiveAll(ids: Collection<String>): Int
    suspend fun moveToTrash(id: String)
    suspend fun moveAllToTrash(ids: Collection<String>): Int
    suspend fun restoreFromTrash(id: String)
    fun observeChildren(parentId: String): Flow<List<ImageRecord>>
    suspend fun delete(id: String)
}

/** 滑动筛选流使用的窄接口：只观察首张普通图片和剩余数量。 */
interface SwipeImageRepository {
    fun observeNextUnreviewed(): Flow<ImageRecord?>
    fun observeUnreviewedCount(): Flow<Int>
    suspend fun archive(id: String, artistStringId: String?)
    suspend fun moveToTrash(id: String)
    suspend fun restoreFromTrash(id: String)
}

class ImageRepositoryImpl(
    private val imageDao: ImageDao,
    private val fileStorage: ImageFileStorage,
) : ImageRepository, SwipeImageRepository {

    override fun observeAll(): Flow<List<ImageRecord>> =
        imageDao.observeAll().map { entities -> entities.map { it.toRecord() } }

    override fun paged(): Flow<PagingData<ImageRecord>> = Pager(
        config = galleryPagingConfig(),
        pagingSourceFactory = imageDao::pagingSource,
    ).flow.map { page -> page.map(ImageEntity::toRecord) }

    override suspend fun getById(id: String): ImageRecord? =
        imageDao.findById(id)?.toRecord()

    override suspend fun getByIds(ids: Collection<String>): List<ImageRecord> {
        val orderedIds = ids.asSequence().filter(String::isNotBlank).distinct().toList()
        if (orderedIds.isEmpty()) return emptyList()
        val recordsById = imageDao.findByIds(orderedIds).associateBy(ImageEntity::id)
        return orderedIds.mapNotNull(recordsById::get).map(ImageEntity::toRecord)
    }

    override fun observeNextUnreviewed(): Flow<ImageRecord?> =
        imageDao.observeFirstUnreviewed().map { it?.toRecord() }

    override fun observeUnreviewedCount(): Flow<Int> = imageDao.observeUnreviewedCount()

    override suspend fun setFavorite(id: String, favorite: Boolean) {
        imageDao.updateFavorite(id, favorite)
    }

    override suspend fun setFavorites(ids: Collection<String>, favorite: Boolean): Int {
        val uniqueIds = ids.asSequence().filter(String::isNotBlank).distinct().toList()
        if (uniqueIds.isEmpty()) return 0
        return imageDao.updateFavorites(uniqueIds, favorite)
    }

    override suspend fun archive(id: String, artistStringId: String?) {
        imageDao.archive(id, System.currentTimeMillis(), artistStringId)
    }

    override suspend fun archiveAll(ids: Collection<String>): Int {
        val uniqueIds = ids.asSequence().filter(String::isNotBlank).distinct().toList()
        return if (uniqueIds.isEmpty()) 0 else imageDao.archiveAll(uniqueIds, System.currentTimeMillis())
    }

    override suspend fun moveToTrash(id: String) {
        imageDao.moveToTrash(id, System.currentTimeMillis())
    }

    override suspend fun moveAllToTrash(ids: Collection<String>): Int {
        val uniqueIds = ids.asSequence().filter(String::isNotBlank).distinct().toList()
        return if (uniqueIds.isEmpty()) 0 else imageDao.moveAllToTrash(uniqueIds, System.currentTimeMillis())
    }

    override suspend fun restoreFromTrash(id: String) {
        imageDao.restoreFromTrash(id)
    }

    override fun observeChildren(parentId: String): Flow<List<ImageRecord>> =
        imageDao.observeChildren(parentId).map { rows -> rows.map(ImageEntity::toRecord) }

    /** 数据库记录与磁盘文件（原图+缩略图）一并清理。
     *  先删 DB 记录：事务失败时文件保留，不产生悬挂记录；
     *  孤儿文件（DB 成功但文件删除失败）可由定期扫描修复。 */
    override suspend fun delete(id: String) {
        imageDao.delete(id)
        fileStorage.deleteImage(id)
    }

}

internal fun galleryPagingConfig(): PagingConfig = PagingConfig(
    pageSize = GALLERY_PAGE_SIZE,
    initialLoadSize = GALLERY_PAGE_SIZE,
    prefetchDistance = GALLERY_PREFETCH_DISTANCE,
    enablePlaceholders = false,
    maxSize = GALLERY_MAX_LOADED_ITEMS,
)

private const val GALLERY_PAGE_SIZE = 60
private const val GALLERY_PREFETCH_DISTANCE = 20
private const val GALLERY_MAX_LOADED_ITEMS = 300
