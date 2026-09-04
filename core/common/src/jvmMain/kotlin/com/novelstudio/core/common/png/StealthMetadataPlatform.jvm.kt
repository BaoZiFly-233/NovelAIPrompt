package com.novelstudio.core.common.png

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import javax.imageio.ImageIO

internal actual fun extractPackedAlphaLsb(pngBytes: ByteArray): ByteArray? {
    val image = ImageIO.read(ByteArrayInputStream(pngBytes)) ?: return null
    if (!image.colorModel.hasAlpha()) return null
    val bitCount = image.width.toLong() * image.height
    if (bitCount > MAX_STEALTH_PIXELS) return null
    val output = ByteArray((bitCount / 8L).toInt())
    var outputIndex = 0
    var current = 0
    var used = 0
    for (x in 0 until image.width) {
        for (y in 0 until image.height) {
            val alpha = image.getRGB(x, y) ushr 24 and 0xFF
            current = (current shl 1) or (alpha and 1)
            used++
            if (used == 8) {
                output[outputIndex++] = current.toByte()
                current = 0
                used = 0
                if (outputIndex == output.size) return output
            }
        }
    }
    return output
}

internal actual fun gunzipLimited(bytes: ByteArray, maxOutputBytes: Int): ByteArray {
    require(maxOutputBytes > 0)
    return GZIPInputStream(ByteArrayInputStream(bytes)).use { gzip ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = gzip.read(buffer)
            if (count < 0) break
            require(count <= maxOutputBytes - total) { "stealth metadata 解压后超过大小上限" }
            output.write(buffer, 0, count)
            total += count
        }
        output.toByteArray()
    }
}

private const val MAX_STEALTH_PIXELS = 64L * 1024 * 1024
