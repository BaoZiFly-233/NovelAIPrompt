package com.novelstudio.core.database

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalPathApi::class)
class ImagePagingJvmTest {

    @Test
    fun tenThousandImagesLoadInStableBoundedPages() = runBlocking {
        val directory = Files.createTempDirectory("novelstudio-room-paging-")
        val databasePath = directory.resolve("paging.db")
        val database = Room.databaseBuilder<AppDatabase>(databasePath.absolutePathString())
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()

        try {
            database.imageDao().upsertAll(
                List(IMAGE_COUNT) { index -> image(index) },
            )

            val source = database.imageDao().pagingSource()
            val first = assertIs<PagingSource.LoadResult.Page<Int, ImageEntity>>(
                source.load(
                    PagingSource.LoadParams.Refresh(
                        key = null,
                        loadSize = PAGE_SIZE,
                        placeholdersEnabled = false,
                    ),
                ),
            )
            val second = assertIs<PagingSource.LoadResult.Page<Int, ImageEntity>>(
                source.load(
                    PagingSource.LoadParams.Append(
                        key = assertNotNull(first.nextKey),
                        loadSize = PAGE_SIZE,
                        placeholdersEnabled = false,
                    ),
                ),
            )

            assertEquals(PAGE_SIZE, first.data.size)
            assertEquals(PAGE_SIZE, second.data.size)
            assertEquals("image-09999", first.data.first().id)
            assertTrue(first.data.map { it.id }.toSet().intersect(second.data.map { it.id }.toSet()).isEmpty())
            assertStableDescendingOrder(first.data + second.data)
            assertPagingIndexIsUsed(databasePath)
        } finally {
            database.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun batchFavoriteUpdatesOnlyExplicitFavoriteFlag() = runBlocking {
        val directory = Files.createTempDirectory("novelstudio-room-batch-")
        val databasePath = directory.resolve("batch.db")
        val database = Room.databaseBuilder<AppDatabase>(databasePath.absolutePathString())
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()

        try {
            database.imageDao().upsertAll(listOf(image(1), image(2), image(3)))

            val updated = database.imageDao().updateFavorites(
                ids = listOf("image-00001", "image-00003"),
                favorite = true,
            )

            assertEquals(2, updated)
            val selected = database.imageDao().findByIds(listOf("image-00001", "image-00003"))
            assertTrue(selected.all { it.isFavorite })
            assertTrue(database.imageDao().findById("image-00002")?.isFavorite == false)
        } finally {
            database.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun trashedImagesAreExcludedFromGalleryPaging() = runBlocking {
        val directory = Files.createTempDirectory("novelstudio-room-prompt-page-")
        val databasePath = directory.resolve("prompt-page.db")
        val database = Room.databaseBuilder<AppDatabase>(databasePath.absolutePathString())
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
        try {
            database.imageDao().upsertAll(
                listOf(
                    image(1),
                    image(2).copy(trashedAt = 10L),
                    image(3),
                ),
            )

            val page = assertIs<PagingSource.LoadResult.Page<Int, ImageEntity>>(
                database.imageDao().pagingSource().load(
                    PagingSource.LoadParams.Refresh(null, 20, false),
                ),
            )
            assertEquals(listOf("image-00003", "image-00001"), page.data.map { it.id })
        } finally {
            database.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun swipeQueriesExposeOnlyUnarchivedAndUntrashedImages() = runBlocking {
        val directory = Files.createTempDirectory("novelstudio-room-swipe-")
        val databasePath = directory.resolve("swipe.db")
        val database = Room.databaseBuilder<AppDatabase>(databasePath.absolutePathString())
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
        try {
            database.imageDao().upsertAll(
                listOf(
                    image(1),
                    image(2).copy(archivedAt = 8L),
                    image(3).copy(trashedAt = 9L),
                ),
            )

            assertEquals("image-00001", database.imageDao().observeFirstUnreviewed().first()?.id)
            assertEquals(1, database.imageDao().observeUnreviewedCount().first())
        } finally {
            database.close()
            directory.deleteRecursively()
        }
    }

    private fun assertPagingIndexIsUsed(path: Path) {
        val queryPlan = BundledSQLiteDriver().open(path.absolutePathString()).use { connection ->
            connection.prepare(
                "EXPLAIN QUERY PLAN SELECT * FROM images WHERE trashedAt IS NULL " +
                    "ORDER BY createdAt DESC, id DESC LIMIT 60",
            ).use { statement ->
                buildList {
                    while (statement.step()) add(statement.getText(3))
                }
            }
        }
        assertTrue(
            queryPlan.any { it.contains("index_images_createdAt_id") },
            "分页查询应使用 createdAt/id 索引，实际计划：${queryPlan.joinToString()}",
        )
    }

    private fun assertStableDescendingOrder(images: List<ImageEntity>) {
        images.zipWithNext().forEach { (left, right) ->
            assertTrue(
                left.createdAt > right.createdAt ||
                    left.createdAt == right.createdAt && left.id > right.id,
                "分页顺序必须稳定按 createdAt DESC, id DESC",
            )
        }
    }

    private fun image(index: Int): ImageEntity {
        val id = "image-${index.toString().padStart(5, '0')}"
        return ImageEntity(
            id = id,
            filePath = "/images/$id.png",
            thumbnailPath = "/thumbs/$id.png",
            blurHash = "blurhash",
            prompt = "prompt $index",
            uc = "",
            model = "nai-diffusion-5-full",
            seed = index.toLong(),
            steps = 28,
            scale = 6f,
            sampler = "k_euler",
            width = 1024,
            height = 1024,
            starRating = 3,
            isFavorite = false,
            hasTransparency = false,
            rawMetadataJson = "{}",
            createdAt = (index / 2).toLong(),
        )
    }

    private companion object {
        const val IMAGE_COUNT = 10_000
        const val PAGE_SIZE = 60
    }
}
