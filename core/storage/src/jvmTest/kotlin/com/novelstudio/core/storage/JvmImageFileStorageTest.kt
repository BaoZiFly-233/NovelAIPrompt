package com.novelstudio.core.storage

import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmImageFileStorageTest {

    @Test
    fun `save publishes both files and delete removes them`() = withTempStorage { root, storage ->
        val png = validPng()

        val stored = storage.saveImage("image-1", png)

        assertContentEquals(png, Path.of(stored.imagePath).readBytes())
        assertTrue(Files.isRegularFile(Path.of(stored.thumbnailPath)))
        assertTrue(stored.blurHash.isNotBlank())

        storage.deleteImage("image-1")
        assertFalse(Files.exists(Path.of(stored.imagePath)))
        assertFalse(Files.exists(Path.of(stored.thumbnailPath)))
        assertNoTemporaryFiles(root)
    }

    @Test
    fun `decode failure leaves no files behind`() = withTempStorage { root, storage ->
        assertFailsWith<IllegalStateException> {
            storage.saveImage("broken", byteArrayOf(1, 2, 3))
        }

        assertNoPublishedFiles(root)
        assertNoTemporaryFiles(root)
    }

    @Test
    fun `existing image id is never overwritten`() = withTempStorage { root, storage ->
        val png = validPng()
        val stored = storage.saveImage("same-id", png)

        assertFailsWith<IllegalArgumentException> {
            storage.saveImage("same-id", validPng(argb = 0xFF00FF00.toInt()))
        }

        assertContentEquals(png, Path.of(stored.imagePath).readBytes())
        assertNoTemporaryFiles(root)
    }

    @Test
    fun `unsafe or excessive id is rejected before writing`() = withTempStorage { root, storage ->
        assertFailsWith<IllegalArgumentException> { storage.saveImage("../escape", validPng()) }
        assertFailsWith<IllegalArgumentException> { storage.saveImage("x".repeat(121), validPng()) }
        assertNoPublishedFiles(root)
    }

    private fun withTempStorage(block: suspend (Path, JvmImageFileStorage) -> Unit) = runBlocking {
        val root = Files.createTempDirectory("novelstudio-storage-test-")
        try {
            block(root, JvmImageFileStorage(root))
        } finally {
            Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    private fun validPng(argb: Int = 0xFFFF00FF.toInt()): ByteArray {
        val image = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
        repeat(2) { y -> repeat(2) { x -> image.setRGB(x, y, argb) } }
        return ByteArrayOutputStream().use { output ->
            check(ImageIO.write(image, "png", output))
            output.toByteArray()
        }
    }

    private fun assertNoPublishedFiles(root: Path) {
        listOf(root.resolve("images"), root.resolve("thumbs")).forEach { dir ->
            if (Files.isDirectory(dir)) Files.list(dir).use { assertFalse(it.findAny().isPresent) }
        }
    }

    private fun assertNoTemporaryFiles(root: Path) {
        Files.walk(root).use { paths ->
            assertFalse(paths.anyMatch { it.fileName.toString().endsWith(".tmp") })
        }
    }
}
