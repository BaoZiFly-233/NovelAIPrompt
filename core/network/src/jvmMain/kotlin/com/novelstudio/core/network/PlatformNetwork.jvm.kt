package com.novelstudio.core.network

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

actual fun platformHttpEngine(): HttpClientEngineFactory<*> = OkHttp

actual fun extractImagesFromZip(zipBytes: ByteArray, limits: PngArchiveLimits): List<ByteArray> {
    require(zipBytes.size.toLong() <= limits.maxTotalBytes) { "ZIP 响应超过总大小上限" }
    val images = ArrayList<ByteArray>()
    var entries = 0
    var total = 0L
    ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            require(++entries <= limits.maxEntries) { "ZIP 条目数量超过上限" }
            val isImageEntry = !entry.isDirectory &&
                (entry.name.endsWith(".png", ignoreCase = true) || entry.name.endsWith(".webp", ignoreCase = true))
            if (isImageEntry) {
                require(images.size < limits.maxImages) { "图像数量超过上限" }
            }
            val bytes = readLimited(zip, limits.maxEntryBytes, capture = isImageEntry)
            require(bytes.size <= limits.maxTotalBytes - total) { "ZIP 解压后总大小超过上限" }
            total += bytes.size
            if (isImageEntry) {
                val image = requireNotNull(bytes.content)
                require(image.detectImageMimeType() != null) { "ZIP 中图像条目签名无效: ${entry.name}" }
                images += image
            }
            entry = zip.nextEntry
        }
    }
    return images
}

private fun readLimited(zip: ZipInputStream, maxBytes: Long, capture: Boolean): ReadEntry {
    val output = if (capture) java.io.ByteArrayOutputStream() else null
    val buffer = ByteArray(8192)
    var total = 0L
    while (true) {
        val count = zip.read(buffer)
        if (count < 0) break
        require(count.toLong() <= maxBytes - total) { "ZIP 条目超过单项大小上限" }
        total += count.toLong()
        output?.write(buffer, 0, count)
    }
    return ReadEntry(total, output?.toByteArray())
}

private data class ReadEntry(val size: Long, val content: ByteArray?)

actual fun isTransportError(throwable: Throwable): Boolean = throwable is java.io.IOException
