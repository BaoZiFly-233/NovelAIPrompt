package com.novelstudio.feature.gallery

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class BatchImageExporterJvmTest {

    @Test
    fun exportCopiesSourcesAndNeverOverwritesConflictingFile() {
        val root = createTempDirectory("gallery-export-test")
        try {
            val source = root.resolve("source.png")
            Files.write(source, byteArrayOf(1, 2, 3))
            val destination = Files.createDirectory(root.resolve("out"))
            Files.write(destination.resolve("novelai-same.png"), byteArrayOf(9))

            val result = exportImagesToDirectory(
                listOf(GalleryExportItem("same", source.toString())),
                destination.toFile(),
            )

            assertEquals(1, result.exportedCount)
            assertEquals(emptyList(), result.failures)
            assertContentEquals(byteArrayOf(9), destination.resolve("novelai-same.png").readBytes())
            assertContentEquals(byteArrayOf(1, 2, 3), destination.resolve("novelai-same (1).png").readBytes())
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun exportReportsMissingSourceWithoutDiscardingSuccessfulCopies() {
        val root = createTempDirectory("gallery-export-partial-test")
        try {
            val source = root.resolve("source.png")
            Files.write(source, byteArrayOf(4, 5, 6))
            val destination = root.resolve("new-out").toFile()

            val result = exportImagesToDirectory(
                listOf(
                    GalleryExportItem("ok", source.toString()),
                    GalleryExportItem("missing", root.resolve("missing.png").toString()),
                ),
                destination,
            )

            assertEquals(1, result.exportedCount)
            assertEquals(listOf("missing"), result.failures.map { it.id })
            assertContentEquals(byteArrayOf(4, 5, 6), destination.resolve("novelai-ok.png").readBytes())
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
