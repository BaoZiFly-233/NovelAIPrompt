package com.novelstudio.core.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

actual fun imageFileStorage(context: Any?): ImageFileStorage = JvmImageFileStorage()

/** 桌面 JVM 实现：原图落盘 ~/.novelai-studio/images，PNG 缩略图 256px（ImageIO），BlurHash 取自解码位图 */
private class JvmImageFileStorage : ImageFileStorage {

    private val baseDir = Path(System.getProperty("user.home"), ".novelai-studio")

    override suspend fun saveImage(id: String, pngBytes: ByteArray): StoredImage =
        withContext(Dispatchers.IO) {
            val imageDir = baseDir.resolve("images").also { Files.createDirectories(it) }
            val thumbDir = baseDir.resolve("thumbs").also { Files.createDirectories(it) }

            val original = imageDir.resolve("$id.png")
            Files.write(original, pngBytes)

            val source = ImageIO.read(ByteArrayInputStream(pngBytes))
                ?: error("PNG 解码失败，无法生成缩略图")

            val thumbSize = 256
            val scale = minOf(1.0, thumbSize.toDouble() / maxOf(source.width, source.height))
            val thumbWidth = (source.width * scale).toInt().coerceAtLeast(1)
            val thumbHeight = (source.height * scale).toInt().coerceAtLeast(1)
            val scaled = BufferedImage(thumbWidth, thumbHeight, BufferedImage.TYPE_INT_ARGB)
            val graphics = scaled.createGraphics()
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            graphics.drawImage(source, 0, 0, thumbWidth, thumbHeight, null)
            graphics.dispose()

            val thumbnail = thumbDir.resolve("$id.png")
            ImageIO.write(scaled, "png", thumbnail.toFile())

            val pixels = IntArray(source.width * source.height)
            source.getRGB(0, 0, source.width, source.height, pixels, 0, source.width)
            StoredImage(
                imagePath = original.absolutePathString(),
                thumbnailPath = thumbnail.absolutePathString(),
                blurHash = BlurhashEncoder.encode(pixels, source.width, source.height),
            )
        }

    override suspend fun deleteImage(id: String): Unit = withContext(Dispatchers.IO) {
        baseDir.resolve("images/$id.png").toFile().delete()
        baseDir.resolve("thumbs/$id.png").toFile().delete()
    }
}
