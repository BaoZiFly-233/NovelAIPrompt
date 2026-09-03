package com.novelstudio.core.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

actual fun imageFileStorage(context: Any?): ImageFileStorage {
    val appContext = context as? Context
        ?: error("Android 平台必须传入 ApplicationContext 构建 ImageFileStorage")
    return AndroidImageFileStorage(appContext)
}

/** Android 实现：原图落盘 filesDir/images，WebP 缩略图 256px，BlurHash 取自解码位图 */
private class AndroidImageFileStorage(private val context: Context) : ImageFileStorage {

    private val imageDir = File(context.filesDir, "images").apply { mkdirs() }
    private val thumbDir = File(context.filesDir, "thumbs").apply { mkdirs() }

    override suspend fun saveImage(id: String, pngBytes: ByteArray): StoredImage =
        withContext(Dispatchers.IO) {
            val original = File(imageDir, "$id.png")
            original.writeBytes(pngBytes)

            val bitmap = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)
                ?: error("PNG 解码失败，无法生成缩略图")
            try {
                val thumbSize = 256
                val scale = minOf(1f, thumbSize.toFloat() / maxOf(bitmap.width, bitmap.height))
                val thumbWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
                val thumbHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(bitmap, thumbWidth, thumbHeight, true)
                val thumbnail = File(thumbDir, "$id.webp")
                FileOutputStream(thumbnail).use { out ->
                    scaled.compress(Bitmap.CompressFormat.WEBP, 80, out)
                }

                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                StoredImage(
                    imagePath = original.absolutePath,
                    thumbnailPath = thumbnail.absolutePath,
                    blurHash = BlurhashEncoder.encode(pixels, bitmap.width, bitmap.height),
                )
            } finally {
                bitmap.recycle()
            }
        }

    override suspend fun deleteImage(id: String): Unit = withContext(Dispatchers.IO) {
        File(imageDir, "$id.png").delete()
        File(thumbDir, "$id.webp").delete()
    }
}
