package com.novelstudio.core.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.imageio.ImageIO
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

actual fun imageFileStorage(context: Any?): ImageFileStorage = JvmImageFileStorage()

/** 桌面 JVM 实现：临时写入并逐文件原子发布，失败时回滚本次产生的文件。 */
internal class JvmImageFileStorage(
    private val baseDir: java.nio.file.Path = Path(System.getProperty("user.home"), ".novelai-studio"),
) : ImageFileStorage {

    override suspend fun saveImage(id: String, pngBytes: ByteArray): StoredImage =
        saveImage(id, pngBytes, "image/png")

    override suspend fun saveImage(id: String, imageBytes: ByteArray, mimeType: String): StoredImage =
        withContext(Dispatchers.IO) {
            WRITE_MUTEX.withLock { saveImageLocked(id, imageBytes, mimeType) }
        }

    private fun saveImageLocked(id: String, imageBytes: ByteArray, mimeType: String): StoredImage {
        validateId(id)
        require(imageBytes.isNotEmpty()) { "图像数据不能为空" }
        val extension = extensionFor(mimeType)
        val imageDir = baseDir.resolve("images").also { Files.createDirectories(it) }
        val thumbDir = baseDir.resolve("thumbs").also { Files.createDirectories(it) }
        check(Files.isDirectory(imageDir) && Files.isDirectory(thumbDir)) { "图片存储目录创建失败" }

        val original = imageDir.resolve("$id.$extension")
        val thumbnail = thumbDir.resolve("$id.png")
        val originalTemp = imageDir.resolve(".$id.$extension.tmp")
        val thumbnailTemp = thumbDir.resolve(".$id.png.tmp")
        cleanup(originalTemp, thumbnailTemp)
        require(!Files.exists(original) && !Files.exists(thumbnail)) { "图片 ID 已存在，拒绝覆盖" }

        var originalPublished = false
        var thumbnailPublished = false
        try {
            val source = ImageIO.read(ByteArrayInputStream(imageBytes))
                ?: error("图像解码失败，无法生成缩略图")
            require(source.width.toLong() * source.height <= MAX_PIXELS) { "图像像素数量超过安全上限" }

            val thumbSize = 256
            val scale = minOf(1.0, thumbSize.toDouble() / maxOf(source.width, source.height))
            val thumbWidth = (source.width * scale).toInt().coerceAtLeast(1)
            val thumbHeight = (source.height * scale).toInt().coerceAtLeast(1)
            val scaled = BufferedImage(thumbWidth, thumbHeight, BufferedImage.TYPE_INT_ARGB)
            val graphics = scaled.createGraphics()
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                graphics.drawImage(source, 0, 0, thumbWidth, thumbHeight, null)
            } finally {
                graphics.dispose()
            }

            val pixels = IntArray(scaled.width * scaled.height)
            scaled.getRGB(0, 0, scaled.width, scaled.height, pixels, 0, scaled.width)
            val blurHash = BlurhashEncoder.encode(pixels, scaled.width, scaled.height)

            Files.write(originalTemp, imageBytes)
            require(ImageIO.write(scaled, "png", thumbnailTemp.toFile())) { "PNG 缩略图编码失败" }
            moveAtomically(thumbnailTemp, thumbnail)
            thumbnailPublished = true
            moveAtomically(originalTemp, original)
            originalPublished = true

            return StoredImage(
                imagePath = original.absolutePathString(),
                thumbnailPath = thumbnail.absolutePathString(),
                blurHash = blurHash,
            )
        } catch (throwable: Throwable) {
            cleanup(originalTemp, thumbnailTemp)
            if (originalPublished) cleanup(original)
            if (thumbnailPublished) cleanup(thumbnail)
            throw throwable
        }
    }

    override suspend fun deleteImage(id: String): Unit = withContext(Dispatchers.IO) {
        WRITE_MUTEX.withLock {
            validateId(id)
            Files.deleteIfExists(baseDir.resolve("images/$id.png"))
            Files.deleteIfExists(baseDir.resolve("images/$id.webp"))
            Files.deleteIfExists(baseDir.resolve("thumbs/$id.png"))
        }
    }

    override suspend fun readImage(imagePath: String): ByteArray = withContext(Dispatchers.IO) {
        val root = baseDir.resolve("images").toAbsolutePath().normalize()
        val requested = java.nio.file.Path.of(imagePath).toAbsolutePath().normalize()
        require(requested.startsWith(root)) { "拒绝读取图库目录之外的文件" }
        Files.readAllBytes(requested)
    }

    private fun validateId(id: String) {
        require(id.length in 1..MAX_ID_LENGTH && id.matches(SAFE_ID)) {
            "图片 ID 只能包含不超过 $MAX_ID_LENGTH 个字母、数字、点、下划线和连字符"
        }
    }

    private fun extensionFor(mimeType: String): String = when (mimeType) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> error("不支持的图像类型：$mimeType")
    }

    private fun moveAtomically(source: java.nio.file.Path, target: java.nio.file.Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(source, target)
        }
    }

    private fun cleanup(vararg paths: java.nio.file.Path) {
        paths.forEach { runCatching { Files.deleteIfExists(it) } }
    }

    private companion object {
        val SAFE_ID = Regex("[A-Za-z0-9._-]+")
        val WRITE_MUTEX = Mutex()
        const val MAX_ID_LENGTH = 120
        const val MAX_PIXELS = 16L * 1024 * 1024
    }
}
