package com.novelstudio.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ImageEntity::class,
        GenerationTaskEntity::class,
        ArtistStringEntity::class,
        PromptAssetEntity::class,
        TagEntity::class,
        ImageTagCrossRef::class,
        WorkbenchDraftEntity::class,
        TagSuggestionCacheEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun imageDao(): ImageDao
    abstract fun generationTaskDao(): GenerationTaskDao
    abstract fun artistStringDao(): ArtistStringDao
    abstract fun promptAssetDao(): PromptAssetDao
    abstract fun tagDao(): TagDao
    abstract fun imageTagDao(): ImageTagDao
    abstract fun workbenchDraftDao(): WorkbenchDraftDao
    abstract fun tagSuggestionCacheDao(): TagSuggestionCacheDao

    companion object {
        const val DATABASE_NAME = "novelai-studio.db"
    }
}
