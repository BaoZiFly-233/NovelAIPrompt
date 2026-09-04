package com.novelstudio.core.network

import io.ktor.client.engine.HttpClientEngineFactory

/** 平台 HTTP 引擎与 ZIP 解包的平台差异收口 */
expect fun platformHttpEngine(): HttpClientEngineFactory<*>

/** 从 ZIP 响应中提取全部有效 PNG/WebP 条目，并限制不可信压缩包的资源消耗。 */
expect fun extractImagesFromZip(zipBytes: ByteArray, limits: PngArchiveLimits = PngArchiveLimits()): List<ByteArray>

/** 兼容旧测试入口；新代码统一走 extractImagesFromZip。 */
fun extractPngImagesFromZip(zipBytes: ByteArray, limits: PngArchiveLimits = PngArchiveLimits()): List<ByteArray> =
    extractImagesFromZip(zipBytes, limits).filter(ByteArray::isPngSignature)

data class PngArchiveLimits(
    val maxImages: Int = 16,
    val maxEntries: Int = 64,
    val maxEntryBytes: Long = 32L * 1024 * 1024,
    val maxTotalBytes: Long = 128L * 1024 * 1024,
) {
    init {
        require(maxImages > 0 && maxEntries > 0 && maxEntryBytes > 0 && maxTotalBytes > 0)
        require(maxEntryBytes <= maxTotalBytes)
    }
}

internal val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

internal fun ByteArray.isPngSignature(): Boolean = size >= PNG_SIGNATURE.size &&
    PNG_SIGNATURE.indices.all { this[it] == PNG_SIGNATURE[it] }

internal fun ByteArray.isWebpSignature(): Boolean = size >= 12 &&
    copyOfRange(0, 4).decodeToString() == "RIFF" && copyOfRange(8, 12).decodeToString() == "WEBP"

internal fun ByteArray.detectImageMimeType(): String? = when {
    isPngSignature() -> "image/png"
    isWebpSignature() -> "image/webp"
    else -> null
}

/** 判定是否为可重试的传输层错误（连接失败/超时等） */
expect fun isTransportError(throwable: Throwable): Boolean
