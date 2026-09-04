package com.novelstudio.core.common.png

import com.novelstudio.core.model.NaiImageMetadata
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.BufferedSource
import okio.ByteString

/**
 * Okio 零拷贝 PNG tEXt Chunk 元数据提取器。
 *
 * 契约（NOVELAI_V5_SPEC.md §4）：
 * 1. 校验 PNG 文件头 `89 50 4E 47 0D 0A 1A 0A`；
 * 2. 循环遍历 Chunk（Length 4B + Type 4B + Data NB + CRC 4B）；
 * 3. 收集全部 `tEXt` 键值对，关键字命中 `Comment` / `Description` 时解析 NAI 元数据 JSON；
 * 4. 同时读取 IHDR 中的原始宽高与 Alpha 信息，供图库布局及透明标记使用。
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
        val hasTransparency: Boolean = false,
        val textChunks: Map<String, String> = emptyMap(),
        val stealthMetadataJson: String? = null,
    ) {
        /** Alpha stealth 元数据优先，其次读取 Comment/Description。 */
        val naiMetadata: NaiImageMetadata?
            get() = stealthMetadataJson?.let(::decodeNaiMetadata)
                ?: (textChunks[NAI_COMMENT_KEY] ?: textChunks[NAI_DESCRIPTION_KEY])?.let(::decodeNaiMetadata)
    }

    fun parse(source: BufferedSource): PngInfo {
        val originalBytes = source.readByteArray()
        val pngSource = okio.Buffer().apply { write(originalBytes) }
        if (pngSource.request(PNG_SIGNATURE.size.toLong()).not()) {
            throw IllegalArgumentException("不是有效的 PNG 文件（签名缺失）")
        }
        val signature = pngSource.readByteArray(PNG_SIGNATURE.size.toLong())
        if (!signature.contentEquals(PNG_SIGNATURE)) {
            throw IllegalArgumentException("PNG 签名不匹配")
        }

        var width: Int? = null
        var height: Int? = null
        var hasTransparency = false
        val textChunks = LinkedHashMap<String, String>()

        while (!pngSource.exhausted()) {
            if (pngSource.request(8).not()) break
            val length = pngSource.readInt()
            val type = pngSource.readByteString(4L).utf8()
            if (type == "IEND") break
            if (length <= 0 || length > MAX_CHUNK_SIZE) break

            if (!pngSource.request(length.toLong() + 4L)) break
            val data = pngSource.readByteString(length.toLong())
            pngSource.skip(4) // CRC

            when (type) {
                "IHDR" -> {
                    if (data.size >= 10) {
                        width = data.readIntAt(0)
                        height = data.readIntAt(4)
                        val colorType = data[9].toInt() and 0xFF
                        hasTransparency = colorType == PNG_COLOR_GRAYSCALE_ALPHA || colorType == PNG_COLOR_TRUECOLOR_ALPHA
                    }
                }
                "tRNS" -> hasTransparency = true
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

        val stealth = runCatching { extractStealthJson(originalBytes) }.getOrNull()
        return PngInfo(width, height, hasTransparency, textChunks, stealth)
    }

    private fun extractStealthJson(pngBytes: ByteArray): String? {
        val packed = extractPackedAlphaLsb(pngBytes) ?: return null
        val magic = STEALTH_MAGIC.encodeToByteArray()
        if (packed.size < magic.size + 4 || !packed.copyOfRange(0, magic.size).contentEquals(magic)) return null
        val bitLength = packed.readIntAt(magic.size)
        require(bitLength >= 0 && bitLength % 8 == 0) { "stealth metadata 长度无效" }
        val byteLength = bitLength / 8
        require(byteLength <= MAX_STEALTH_COMPRESSED_SIZE) { "stealth metadata 压缩载荷过大" }
        require(magic.size + 4 + byteLength <= packed.size) { "stealth metadata 载荷被截断" }
        val compressed = packed.copyOfRange(magic.size + 4, magic.size + 4 + byteLength)
        return gunzipLimited(compressed, MAX_STEALTH_DECOMPRESSED_SIZE).decodeToString()
    }

    private fun decodeNaiMetadata(raw: String): NaiImageMetadata? = runCatching {
        val element = json.parseToJsonElement(raw)
        val root = element as? JsonObject
        val comment = root?.get(NAI_COMMENT_KEY)
        when (comment) {
            is JsonObject -> json.decodeFromJsonElement(NaiImageMetadata.serializer(), comment)
            null -> json.decodeFromJsonElement(NaiImageMetadata.serializer(), element)
            else -> json.decodeFromString(NaiImageMetadata.serializer(), comment.jsonPrimitive.content)
        }
    }.getOrNull()

    private fun ByteString.readIntAt(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)

    private fun ByteArray.readIntAt(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)

    const val NAI_COMMENT_KEY = "Comment"
    const val NAI_DESCRIPTION_KEY = "Description"
    private const val STEALTH_MAGIC = "stealth_pngcomp"
    private const val MAX_STEALTH_COMPRESSED_SIZE = 8 * 1024 * 1024
    private const val MAX_STEALTH_DECOMPRESSED_SIZE = 16 * 1024 * 1024
    private const val PNG_COLOR_GRAYSCALE_ALPHA = 4
    private const val PNG_COLOR_TRUECOLOR_ALPHA = 6
}
