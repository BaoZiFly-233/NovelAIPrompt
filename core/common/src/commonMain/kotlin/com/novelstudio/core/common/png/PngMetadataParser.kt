package com.novelstudio.core.common.png

import com.novelstudio.core.model.NaiImageMetadata
import kotlinx.serialization.json.Json
import okio.BufferedSource
import okio.ByteString

/**
 * Okio 零拷贝 PNG tEXt Chunk 元数据提取器。
 *
 * 契约（NOVELAI_V5_SPEC.md §4）：
 * 1. 校验 PNG 文件头 `89 50 4E 47 0D 0A 1A 0A`；
 * 2. 循环遍历 Chunk（Length 4B + Type 4B + Data NB + CRC 4B）；
 * 3. 收集全部 `tEXt` 键值对，关键字命中 `Comment` / `Description` 时解析 NAI 元数据 JSON；
 * 4. 同时读取 IHDR 中的原始宽高，供图库瀑布流布局使用。
 */
object PngMetadataParser {

    private val PNG_SIGNATURE: ByteArray =
        byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )

    /** 单个 Chunk 的防御性上限，防止损坏文件导致 OOM */
    private const val MAX_CHUNK_SIZE = 16 * 1024 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    data class PngInfo(
        val width: Int? = null,
        val height: Int? = null,
        val textChunks: Map<String, String> = emptyMap(),
    ) {
        /** NAI 官方元数据（Comment/Description 键），解析失败返回 null 而非抛异常 */
        val naiMetadata: NaiImageMetadata?
            get() = (textChunks[NAI_COMMENT_KEY] ?: textChunks[NAI_DESCRIPTION_KEY])
                ?.let { raw -> runCatching { json.decodeFromString<NaiImageMetadata>(raw) }.getOrNull() }
    }

    fun parse(source: BufferedSource): PngInfo {
        if (source.request(PNG_SIGNATURE.size.toLong()).not()) {
            throw IllegalArgumentException("不是有效的 PNG 文件（签名缺失）")
        }
        val signature = source.readByteArray(PNG_SIGNATURE.size.toLong())
        if (!signature.contentEquals(PNG_SIGNATURE)) {
            throw IllegalArgumentException("PNG 签名不匹配")
        }

        var width: Int? = null
        var height: Int? = null
        val textChunks = LinkedHashMap<String, String>()

        while (!source.exhausted()) {
            if (source.request(8).not()) break
            val length = source.readInt()
            val type = source.readByteString(4L).utf8()
            if (type == "IEND") break
            if (length <= 0 || length > MAX_CHUNK_SIZE) break

            val data = source.readByteString(length.toLong())
            source.skip(4) // CRC，非零拷贝校验场景下直接跳过

            when (type) {
                "IHDR" -> {
                    if (data.size >= 8) {
                        width = data.readIntAt(0)
                        height = data.readIntAt(4)
                    }
                }
                "tEXt" -> {
                    val nul = (0 until data.size).firstOrNull { data[it] == 0.toByte() } ?: -1
                    if (nul > 0) {
                        val keyword = data.substring(0, nul).utf8()
                        val value = data.substring(nul + 1).utf8()
                        textChunks[keyword] = value
                    }
                }
            }
        }

        return PngInfo(width, height, textChunks)
    }

    private fun ByteString.readIntAt(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)

    const val NAI_COMMENT_KEY = "Comment"
    const val NAI_DESCRIPTION_KEY = "Description"
}
