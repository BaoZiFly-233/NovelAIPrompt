package com.novelstudio.core.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalPathApi::class)
class DatabaseMigrationJvmTest {

    @Test
    fun migrationFromV1ToCurrentPreservesImagesAndCreatesTaskTable() = runBlocking {
        val directory = Files.createTempDirectory("novelstudio-room-migration-")
        val databasePath = directory.resolve("migration.db")

        try {
            createVersionOneDatabase(databasePath)

            val database = Room.databaseBuilder<AppDatabase>(databasePath.absolutePathString())
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()

            try {
                val legacyImage = assertNotNull(database.imageDao().findById(LEGACY_IMAGE_ID))
                assertEquals("legacy prompt", legacyImage.prompt)
                assertEquals(1234L, legacyImage.seed)
                assertEquals("{}", legacyImage.generationSnapshotJson)
                assertEquals("image/png", legacyImage.mimeType)
                assertNull(legacyImage.archivedAt)
                assertNull(legacyImage.trashedAt)

                val task = GenerationTaskEntity(
                    id = "task-after-migration",
                    parametersJson = "{}",
                    status = "QUEUED",
                    decision = null,
                    errorMessage = null,
                    resultImageId = null,
                    createdAt = 2L,
                    completedAt = null,
                    updatedAt = 2L,
                )
                database.generationTaskDao().upsert(task)

                assertEquals(task, database.generationTaskDao().findById(task.id))
                assertEquals(1, database.imageDao().count())
            } finally {
                database.close()
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun createVersionOneDatabase(path: Path) {
        BundledSQLiteDriver().open(path.absolutePathString()).use { connection ->
            connection.execSQL(
                """CREATE TABLE IF NOT EXISTS images (
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
                    createdAt INTEGER NOT NULL
                )""".trimIndent(),
            )
            connection.prepare(
                """INSERT INTO images (
                    id, filePath, thumbnailPath, blurHash, prompt, uc, model, seed, steps,
                    scale, sampler, width, height, starRating, isFavorite, hasTransparency,
                    rawMetadataJson, createdAt
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".trimIndent(),
            ).use { statement ->
                statement.bindText(1, LEGACY_IMAGE_ID)
                statement.bindText(2, "/legacy/original.png")
                statement.bindText(3, "/legacy/thumbnail.webp")
                statement.bindText(4, "legacy-blurhash")
                statement.bindText(5, "legacy prompt")
                statement.bindText(6, "legacy uc")
                statement.bindText(7, "nai-diffusion-4-5-full")
                statement.bindLong(8, 1234L)
                statement.bindLong(9, 28L)
                statement.bindDouble(10, 5.0)
                statement.bindText(11, "k_euler")
                statement.bindLong(12, 832L)
                statement.bindLong(13, 1216L)
                statement.bindLong(14, 3L)
                statement.bindLong(15, 0L)
                statement.bindLong(16, 0L)
                statement.bindText(17, "{}")
                statement.bindLong(18, 1L)
                statement.step()
            }
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table " +
                    "(id INTEGER PRIMARY KEY, identity_hash TEXT)",
            )
            connection.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) " +
                    "VALUES(42, '359c22747142133feaf18adc3c996cc1')",
            )
            connection.execSQL("PRAGMA user_version = 1")
        }
    }

    private companion object {
        const val LEGACY_IMAGE_ID = "legacy-image"
    }
}
