package com.novelstudio.core.data

import com.novelstudio.core.database.ImageDao
import com.novelstudio.core.database.ImageEntity
import com.novelstudio.core.model.ImageRecord
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
    starRating = starRating,
    isFavorite = isFavorite,
    hasTransparency = hasTransparency,
    rawMetadataJson = rawMetadataJson,
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
    starRating = starRating,
    isFavorite = isFavorite,
    hasTransparency = hasTransparency,
    rawMetadataJson = rawMetadataJson,
    createdAt = createdAt,
)

/** 图库仓储抽象：ViewModel 只依赖此接口（DIP），不直接触碰 DAO 与文件系统 */
interface ImageRepository {
    fun observeAll(): Flow<List<ImageRecord>>
    suspend fun getById(id: String): ImageRecord?
    suspend fun updateStarRating(id: String, rating: Int)
    suspend fun updateFavorite(id: String, favorite: Boolean)
    suspend fun delete(id: String)
}

class ImageRepositoryImpl(
    private val imageDao: ImageDao,
    private val fileStorage: ImageFileStorage,
) : ImageRepository {

    override fun observeAll(): Flow<List<ImageRecord>> =
        imageDao.observeAll().map { entities -> entities.map { it.toRecord() } }

    override suspend fun getById(id: String): ImageRecord? =
        imageDao.findById(id)?.toRecord()

    override suspend fun updateStarRating(id: String, rating: Int) =
        imageDao.updateStarRating(id, rating)

    override suspend fun updateFavorite(id: String, favorite: Boolean) =
        imageDao.updateFavorite(id, favorite)

    /** 数据库记录与磁盘文件（原图+缩略图）一并清理 */
    override suspend fun delete(id: String) {
        fileStorage.deleteImage(id)
        imageDao.delete(id)
    }
}
