package com.novelstudio.core.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** v1 中没有任务表；仅建表，不触碰已有图库数据。 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """CREATE TABLE IF NOT EXISTS generation_tasks (
                id TEXT NOT NULL PRIMARY KEY,
                parametersJson TEXT NOT NULL,
                status TEXT NOT NULL,
                decision TEXT,
                errorMessage TEXT,
                resultImageId TEXT,
                createdAt INTEGER NOT NULL,
                completedAt INTEGER,
                updatedAt INTEGER NOT NULL,
                resultImageIdsJson TEXT NOT NULL
            )""".trimIndent(),
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_generation_tasks_status_createdAt " +
                "ON generation_tasks (status, createdAt)",
        )
    }
}

/** 为万级图库的稳定分页排序补充复合索引，不改写现有图片记录。 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_images_createdAt_id ON images (createdAt, id)",
        )
    }
}

/**
 * 创作资产库迁移。先建立空资产表，再重建图片表补充可空来源与作品谱系；
 * 不从旧 Prompt 猜测或伪造任何资产。旧“不喜欢”记录转为归档，旧收藏原样保留。
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """CREATE TABLE IF NOT EXISTS artist_strings (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                normalizedName TEXT NOT NULL,
                positivePrompt TEXT NOT NULL,
                negativePrompt TEXT NOT NULL,
                modelId TEXT,
                parameterOverridesJson TEXT,
                coverImageId TEXT,
                notes TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )""".trimIndent(),
        )
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_artist_strings_normalizedName ON artist_strings (normalizedName)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_artist_strings_updatedAt ON artist_strings (updatedAt)")

        connection.execSQL(
            """CREATE TABLE IF NOT EXISTS prompt_assets (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                normalizedName TEXT NOT NULL,
                positivePrompt TEXT NOT NULL,
                negativePrompt TEXT NOT NULL,
                notes TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )""".trimIndent(),
        )
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_prompt_assets_normalizedName ON prompt_assets (normalizedName)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_prompt_assets_updatedAt ON prompt_assets (updatedAt)")

        connection.execSQL(
            """CREATE TABLE IF NOT EXISTS tags (
                id TEXT NOT NULL PRIMARY KEY,
                normalizedValue TEXT NOT NULL,
                displayValue TEXT NOT NULL,
                groupName TEXT,
                notes TEXT NOT NULL,
                isFavorite INTEGER NOT NULL,
                source TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )""".trimIndent(),
        )
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tags_normalizedValue ON tags (normalizedValue)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_tags_updatedAt ON tags (updatedAt)")

        connection.execSQL(
            """CREATE TABLE images_v4 (
                id TEXT NOT NULL PRIMARY KEY,
                filePath TEXT NOT NULL,
                thumbnailPath TEXT NOT NULL,
                blurHash TEXT NOT NULL,
                prompt TEXT NOT NULL,
                uc TEXT NOT NULL,
                model TEXT NOT NULL,
                seed INTEGER NOT NULL,
                steps INTEGER NOT NULL,
                scale REAL NOT NULL,
                sampler TEXT NOT NULL,
                width INTEGER NOT NULL,
                height INTEGER NOT NULL,
                starRating INTEGER NOT NULL,
                isFavorite INTEGER NOT NULL,
                hasTransparency INTEGER NOT NULL,
                rawMetadataJson TEXT NOT NULL,
                artistStringId TEXT,
                promptAssetId TEXT,
                parentImageId TEXT,
                operationType TEXT NOT NULL,
                generationSnapshotJson TEXT NOT NULL,
                mimeType TEXT NOT NULL,
                archivedAt INTEGER,
                trashedAt INTEGER,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(artistStringId) REFERENCES artist_strings(id) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(promptAssetId) REFERENCES prompt_assets(id) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(parentImageId) REFERENCES images_v4(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )""".trimIndent(),
        )
        connection.execSQL(
            """INSERT INTO images_v4 (
                id, filePath, thumbnailPath, blurHash, prompt, uc, model, seed, steps, scale,
                sampler, width, height, starRating, isFavorite, hasTransparency, rawMetadataJson,
                artistStringId, promptAssetId, parentImageId, operationType, generationSnapshotJson,
                mimeType, archivedAt, trashedAt, createdAt
            ) SELECT
                id, filePath, thumbnailPath, blurHash, prompt, uc, model, seed, steps, scale,
                sampler, width, height, CASE WHEN starRating = 1 THEN 3 ELSE starRating END,
                isFavorite, hasTransparency, rawMetadataJson,
                NULL, NULL, NULL, 'IMPORT', rawMetadataJson, 'image/png',
                CASE WHEN starRating = 1 THEN createdAt ELSE NULL END, NULL, createdAt
            FROM images""".trimIndent(),
        )
        connection.execSQL("DROP TABLE images")
        connection.execSQL("ALTER TABLE images_v4 RENAME TO images")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_images_createdAt_id ON images (createdAt, id)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_images_artistStringId ON images (artistStringId)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_images_promptAssetId ON images (promptAssetId)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_images_parentImageId ON images (parentImageId)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_images_archivedAt ON images (archivedAt)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_images_trashedAt ON images (trashedAt)")

        connection.execSQL(
            """CREATE TABLE IF NOT EXISTS image_tags (
                imageId TEXT NOT NULL,
                tagId TEXT NOT NULL,
                position INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                PRIMARY KEY(imageId, tagId),
                FOREIGN KEY(imageId) REFERENCES images(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(tagId) REFERENCES tags(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )""".trimIndent(),
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_image_tags_imageId ON image_tags (imageId)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_image_tags_tagId ON image_tags (tagId)")

        connection.execSQL(
            """CREATE TABLE IF NOT EXISTS workbench_draft (
                id INTEGER NOT NULL PRIMARY KEY,
                artistStringId TEXT,
                promptAssetId TEXT,
                artistPositive TEXT NOT NULL,
                artistNegative TEXT NOT NULL,
                promptPositive TEXT NOT NULL,
                promptNegative TEXT NOT NULL,
                orderedTagsJson TEXT NOT NULL,
                freePrompt TEXT NOT NULL,
                negativePrompt TEXT NOT NULL,
                generationParametersJson TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY(artistStringId) REFERENCES artist_strings(id) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(promptAssetId) REFERENCES prompt_assets(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )""".trimIndent(),
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_workbench_draft_artistStringId ON workbench_draft (artistStringId)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_workbench_draft_promptAssetId ON workbench_draft (promptAssetId)")

        connection.execSQL(
            """CREATE TABLE IF NOT EXISTS tag_suggestion_cache (
                model TEXT NOT NULL,
                language TEXT NOT NULL,
                query TEXT NOT NULL,
                suggestionsJson TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(model, language, query)
            )""".trimIndent(),
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_tag_suggestion_cache_updatedAt ON tag_suggestion_cache (updatedAt)")
    }
}
