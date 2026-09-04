package com.novelstudio.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow

/** 图库 DAO：响应式分页 + 显式归档/垃圾箱状态 + 作品谱系。 */
@Dao
interface ImageDao {

    @Query("SELECT * FROM images WHERE trashedAt IS NULL ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<ImageEntity>>

    /** 图库专用分页源，避免万级记录被 DAO 与 UI 同时全量物化。 */
    @Query("SELECT * FROM images WHERE trashedAt IS NULL ORDER BY createdAt DESC, id DESC")
    fun pagingSource(): PagingSource<Int, ImageEntity>

    /** 快速整理只观察未归档且未进垃圾箱的当前首张。 */
    @Query("SELECT * FROM images WHERE archivedAt IS NULL AND trashedAt IS NULL ORDER BY createdAt DESC, id DESC LIMIT 1")
    fun observeFirstUnreviewed(): Flow<ImageEntity?>

    @Query("SELECT COUNT(*) FROM images WHERE archivedAt IS NULL AND trashedAt IS NULL")
    fun observeUnreviewedCount(): Flow<Int>

    @Query("SELECT * FROM images WHERE id = :id")
    suspend fun findById(id: String): ImageEntity?

    @Query("SELECT * FROM images WHERE id IN (:ids)")
    suspend fun findByIds(ids: List<String>): List<ImageEntity>

    @Upsert
    suspend fun upsert(image: ImageEntity)

    @Upsert
    suspend fun upsertAll(images: List<ImageEntity>)

    @Query("UPDATE images SET isFavorite = :favorite WHERE id = :id")
    suspend fun updateFavorite(id: String, favorite: Boolean): Int

    @Query("UPDATE images SET isFavorite = :favorite WHERE id IN (:ids)")
    suspend fun updateFavorites(ids: List<String>, favorite: Boolean): Int

    @Query("UPDATE images SET archivedAt = :archivedAt, trashedAt = NULL, artistStringId = COALESCE(:artistStringId, artistStringId) WHERE id = :id")
    suspend fun archive(id: String, archivedAt: Long, artistStringId: String?): Int

    @Query("UPDATE images SET archivedAt = :archivedAt, trashedAt = NULL WHERE id IN (:ids)")
    suspend fun archiveAll(ids: List<String>, archivedAt: Long): Int

    @Query("UPDATE images SET trashedAt = :trashedAt, archivedAt = NULL, isFavorite = 0 WHERE id = :id")
    suspend fun moveToTrash(id: String, trashedAt: Long): Int

    @Query("UPDATE images SET trashedAt = :trashedAt, archivedAt = NULL, isFavorite = 0 WHERE id IN (:ids)")
    suspend fun moveAllToTrash(ids: List<String>, trashedAt: Long): Int

    @Query("UPDATE images SET trashedAt = NULL WHERE id = :id")
    suspend fun restoreFromTrash(id: String): Int

    @Query("SELECT * FROM images WHERE parentImageId = :parentId ORDER BY createdAt ASC, id ASC")
    fun observeChildren(parentId: String): Flow<List<ImageEntity>>

    @Query("DELETE FROM images WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM images WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<String>)

    @Query("SELECT COUNT(*) FROM images")
    suspend fun count(): Int
}
