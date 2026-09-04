package com.novelstudio.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "artist_strings",
    indices = [Index(value = ["normalizedName"], unique = true), Index(value = ["updatedAt"])],
)
data class ArtistStringEntity(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val positivePrompt: String,
    val negativePrompt: String,
    val modelId: String?,
    val parameterOverridesJson: String?,
    val coverImageId: String?,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "prompt_assets",
    indices = [Index(value = ["normalizedName"], unique = true), Index(value = ["updatedAt"])],
)
data class PromptAssetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val positivePrompt: String,
    val negativePrompt: String,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "tags",
    indices = [Index(value = ["normalizedValue"], unique = true), Index(value = ["updatedAt"])],
)
data class TagEntity(
    @PrimaryKey val id: String,
    val normalizedValue: String,
    val displayValue: String,
    val groupName: String?,
    val notes: String,
    val isFavorite: Boolean,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "image_tags",
    primaryKeys = ["imageId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = ImageEntity::class,
            parentColumns = ["id"],
            childColumns = ["imageId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("imageId"), Index("tagId")],
)
data class ImageTagCrossRef(
    val imageId: String,
    val tagId: String,
    val position: Int,
    val createdAt: Long,
)

@Entity(
    tableName = "workbench_draft",
    foreignKeys = [
        ForeignKey(
            entity = ArtistStringEntity::class,
            parentColumns = ["id"],
            childColumns = ["artistStringId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = PromptAssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["promptAssetId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("artistStringId"), Index("promptAssetId")],
)
data class WorkbenchDraftEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val artistStringId: String?,
    val promptAssetId: String?,
    val artistPositive: String,
    val artistNegative: String,
    val promptPositive: String,
    val promptNegative: String,
    val orderedTagsJson: String,
    val freePrompt: String,
    val negativePrompt: String,
    val generationParametersJson: String,
    val updatedAt: Long,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

@Entity(
    tableName = "tag_suggestion_cache",
    primaryKeys = ["model", "language", "query"],
    indices = [Index("updatedAt")],
)
data class TagSuggestionCacheEntity(
    val model: String,
    val language: String,
    val query: String,
    val suggestionsJson: String,
    val updatedAt: Long,
)
