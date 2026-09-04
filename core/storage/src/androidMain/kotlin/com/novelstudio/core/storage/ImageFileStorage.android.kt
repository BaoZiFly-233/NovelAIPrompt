package com.novelstudio.core.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

actual fun imageFileStorage(context: Any?): ImageFileStorage {
    val appContext = context as? Context
        ?: error("Android 平台必须传入 ApplicationContext 构建 ImageFileStorage")
    return AndroidImageFileStorage(appContext)
}

/** Android 实现：临时写入并用 POSIX rename 原子发布，失败时回滚本次产生的文件。 */
private class AndroidImageFileStorage(context: Context) : ImageFileStorage {

    private val imageDir = File(context.filesDir, "images").also {
        check(it.mkdirs() || it.isDirectory) { "原图存储目录创建失败" }
    }
    private val thumbDir = File(context.filesDir, "thumbs").also {
        check(it.mkdirs() || it.isDirectory) { "缩略图存储目录创建失败" }
    }

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
        val original = File(imageDir, "$id.$extension")
        val thumbnail = File(thumbDir, "$id.webp")
        val originalTemp = File(imageDir, ".$id.$extension.tmp")
        val thumbnailTemp = File(thumbDir, ".$id.webp.tmp")
        cleanup(originalTemp, thumbnailTemp)
        require(!original.exists() && !thumbnail.exists()) { "图片 ID 已存在，拒绝覆盖" }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "图像解码失败，无法读取尺寸" }
        require(bounds.outWidth.toLong() * bounds.outHeight <= MAX_PIXELS) { "图像像素数量超过安全上限" }

        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: error("图像解码失败，无法生成缩略图")
        var originalPublished = false
        var thumbnailPublished = false
        try {
            val thumbSize = 256
            val scale = minOf(1f, thumbSize.toFloat() / maxOf(bitmap.width, bitmap.height))
            val thumbWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val thumbHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bitmap, thumbWidth, thumbHeight, true)
            try {
                FileOutputStream(originalTemp).use { out ->
                    out.write(imageBytes)
                    out.fd.sync()
                }
                FileOutputStream(thumbnailTemp).use { out ->
                    require(scaled.compress(Bitmap.CompressFormat.WEBP, 80, out)) { "WebP 缩略图编码失败" }
                    out.fd.sync()
                }

                val pixels = IntArray(scaled.width * scaled.height)
                scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
                val blurHash = BlurhashEncoder.encode(pixels, scaled.width, scaled.height)

                publishAtomically(thumbnailTemp, thumbnail)
                thumbnailPublished = true
                publishAtomically(originalTemp, original)
                originalPublished = true
                return StoredImage(
                    imagePath = original.absolutePath,
                    thumbnailPath = thumbnail.absolutePath,
                    blurHash = blurHash,
                )
            } finally {
                if (scaled !== bitmap) scaled.recycle()
            }
        } catch (throwable: Throwable) {
            cleanup(originalTemp, thumbnailTemp)
            if (originalPublished) cleanup(original)
            if (thumbnailPublished) cleanup(thumbnail)
            throw throwable
        } finally {
            bitmap.recycle()
        }
    }

    override suspend fun deleteImage(id: String): Unit = withContext(Dispatchers.IO) {
        WRITE_MUTEX.withLock {
            validateId(id)
            deleteIfExists(File(imageDir, "$id.png"))
            deleteIfExists(File(imageDir, "$id.webp"))
            deleteIfExists(File(thumbDir, "$id.webp"))
        }
    }

    override suspend fun readImage(imagePath: String): ByteArray = withContext(Dispatchers.IO) {
        val requested = File(imagePath).canonicalFile
        val root = imageDir.canonicalFile
        require(requested.path.startsWith(root.path + File.separator)) { "拒绝读取图库目录之外的文件" }
        requested.readBytes()
    }

    private fun deleteIfExists(file: File) {
        check(!file.exists() || file.delete()) { "无法删除图片文件：${file.name}" }
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

    private fun publishAtomically(source: File, target: File) {
        check(!target.exists()) { "目标图片已存在，拒绝覆盖" }
        Os.rename(source.absolutePath, target.absolutePath)
    }

    private fun cleanup(vararg files: File) {
        files.forEach { runCatching { it.delete() } }
    }

    private companion object {
        val SAFE_ID = Regex("[A-Za-z0-9._-]+")
        val WRITE_MUTEX = Mutex()
        const val MAX_ID_LENGTH = 120
        const val MAX_PIXELS = 16L * 1024 * 1024
    }
}
