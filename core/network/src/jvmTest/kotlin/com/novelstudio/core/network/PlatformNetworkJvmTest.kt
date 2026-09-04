package com.novelstudio.core.network

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlatformNetworkJvmTest {

    private val pngA = PNG_SIGNATURE + byteArrayOf(1, 2, 3)
    private val pngB = PNG_SIGNATURE + byteArrayOf(4, 5)

    @Test
    fun `extracts png entries in archive order and ignores other entries`() {
        val archive = zipOf(
            "cover.png" to pngA,
            "notes.txt" to "not an image".encodeToByteArray(),
            "second.PNG" to pngB,
        )

        val images = extractPngImagesFromZip(archive)

        assertEquals(2, images.size)
        assertContentEquals(pngA, images[0])
        assertContentEquals(pngB, images[1])
    }

    @Test
    fun `rejects png entry without a real png signature`() {
        val archive = zipOf("fake.png" to "PNG but not a signature".encodeToByteArray())

        assertFailsWith<IllegalArgumentException> {
            extractPngImagesFromZip(archive)
        }
    }

    @Test
    fun `rejects a single entry over maxEntryBytes`() {
        val archive = zipOf("large.png" to pngA)

        assertFailsWith<IllegalArgumentException> {
            extractPngImagesFromZip(archive, PngArchiveLimits(maxEntryBytes = pngA.size.toLong() - 1))
        }
    }

    @Test
    fun `rejects total extracted png bytes over maxTotalBytes`() {
        val archive = zipOf("a.png" to pngA, "b.png" to pngB)

        assertFailsWith<IllegalArgumentException> {
            extractPngImagesFromZip(
                archive,
                PngArchiveLimits(maxTotalBytes = (pngA.size + pngB.size).toLong() - 1),
            )
        }
    }

    @Test
    fun `non png entries also consume the decompression budget`() {
        val archive = zipOf(
            "large.txt" to ByteArray(64) { 1 },
            "image.png" to pngA,
        )

        assertFailsWith<IllegalArgumentException> {
            extractPngImagesFromZip(
                archive,
                PngArchiveLimits(maxEntryBytes = 64, maxTotalBytes = 64),
            )
        }
    }

    @Test
    fun `rejects archive over maxEntries`() {
        val archive = zipOf("a.png" to pngA, "ignored.txt" to byteArrayOf())

        assertFailsWith<IllegalArgumentException> {
            extractPngImagesFromZip(archive, PngArchiveLimits(maxEntries = 1))
        }
    }

    @Test
    fun `rejects archive over maxImages instead of truncating`() {
        val archive = zipOf("a.png" to pngA, "b.png" to pngB)

        assertFailsWith<IllegalArgumentException> {
            extractPngImagesFromZip(archive, PngArchiveLimits(maxImages = 1))
        }
    }

    @Test
    fun `empty archive returns no images`() {
        assertEquals(emptyList(), extractPngImagesFromZip(zipOf()))
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }
}
