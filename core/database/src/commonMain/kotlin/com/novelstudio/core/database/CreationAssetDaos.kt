package com.novelstudio.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistStringDao {
    @Query("SELECT * FROM artist_strings ORDER BY updatedAt DESC, id DESC")
    fun observeAll(): Flow<List<ArtistStringEntity>>

    @Query("SELECT * FROM artist_strings WHERE id = :id")
    suspend fun findById(id: String): ArtistStringEntity?

    @Upsert
    suspend fun upsert(value: ArtistStringEntity)

    @Query("DELETE FROM artist_strings WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface PromptAssetDao {
    @Query("SELECT * FROM prompt_assets ORDER BY updatedAt DESC, id DESC")
    fun observeAll(): Flow<List<PromptAssetEntity>>

    @Query("SELECT * FROM prompt_assets WHERE id = :id")
    suspend fun findById(id: String): PromptAssetEntity?

    @Upsert
    suspend fun upsert(value: PromptAssetEntity)

    @Query("DELETE FROM prompt_assets WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY isFavorite DESC, updatedAt DESC, id DESC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE normalizedValue = :normalizedValue LIMIT 1")
    suspend fun findByNormalizedValue(normalizedValue: String): TagEntity?

    @Query("SELECT * FROM tags WHERE id IN (:ids)")
    suspend fun findByIds(ids: List<String>): List<TagEntity>

    @Upsert
    suspend fun upsert(value: TagEntity)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ImageTagDao {
    @Query("SELECT * FROM image_tags WHERE imageId = :imageId ORDER BY position ASC, createdAt ASC")
    fun observeForImage(imageId: String): Flow<List<ImageTagCrossRef>>

    @Query("DELETE FROM image_tags WHERE imageId = :imageId")
    suspend fun clearForImage(imageId: String)

    @Upsert
    suspend fun upsertAll(values: List<ImageTagCrossRef>)
}

@Dao
interface WorkbenchDraftDao {
    @Query("SELECT * FROM workbench_draft WHERE id = 1")
    fun observe(): Flow<WorkbenchDraftEntity?>

    @Query("SELECT * FROM workbench_draft WHERE id = 1")
    suspend fun get(): WorkbenchDraftEntity?

    @Upsert
    suspend fun upsert(value: WorkbenchDraftEntity)
}

@Dao
interface TagSuggestionCacheDao {
    @Query(
        "SELECT * FROM tag_suggestion_cache " +
            "WHERE model = :model AND language = :language AND query = :query",
    )
    suspend fun find(model: String, language: String, query: String): TagSuggestionCacheEntity?

    @Upsert
    suspend fun upsert(value: TagSuggestionCacheEntity)
}
