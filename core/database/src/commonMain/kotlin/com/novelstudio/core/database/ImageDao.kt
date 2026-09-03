package com.novelstudio.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** 图库 DAO：响应式查询 + 偏好打标 + 批量操作 */
@Dao
interface ImageDao {

    @Query("SELECT * FROM images ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ImageEntity>>

    @Query("SELECT * FROM images WHERE starRating >= :minStar ORDER BY createdAt DESC")
    fun observeByStar(minStar: Int): Flow<List<ImageEntity>>

    @Query("SELECT * FROM images WHERE id = :id")
    suspend fun findById(id: String): ImageEntity?

    @Upsert
    suspend fun upsert(image: ImageEntity)

    @Upsert
    suspend fun upsertAll(images: List<ImageEntity>)

    @Query("UPDATE images SET starRating = :rating WHERE id = :id")
    suspend fun updateStarRating(id: String, rating: Int)

    @Query("UPDATE images SET isFavorite = :favorite WHERE id = :id")
    suspend fun updateFavorite(id: String, favorite: Boolean)

    @Query("DELETE FROM images WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM images WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<String>)

    @Query("SELECT COUNT(*) FROM images")
    suspend fun count(): Int
}
