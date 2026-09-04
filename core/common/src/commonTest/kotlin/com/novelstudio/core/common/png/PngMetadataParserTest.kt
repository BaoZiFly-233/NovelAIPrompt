package com.novelstudio.core.common.png

import com.novelstudio.core.model.NaiImageMetadata
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PngMetadataParserTest {

    @Test
    fun `parses IHDR dimensions and tEXt Comment metadata`() {
        val metadataJson = """{"prompt":"1girl, silver hair","uc":"lowres","seed":123456,"steps":28,"scale":6.0,"sampler":"k_euler","width":1024,"height":1024,"model":"nai-diffusion-5-full","unknown_future_field":1}"""
        val png = Buffer()
            .writeSignature()
            .writeChunk("IHDR", ihdr(width = 320, height = 240))
            .writeChunk("tEXt", "Comment\u0000$metadataJson".encodeToByteArray())
            .writeChunk("tEXt", "Software\u0000NovelAI".encodeToByteArray())
            .writeChunk("IEND", ByteArray(0))
            .readByteArray()

        val info = PngMetadataParser.parse(Buffer().apply { write(png) })

        assertEquals(320, info.width)
        assertEquals(240, info.height)
        assertTrue(info.hasTransparency)
        assertEquals("NovelAI", info.textChunks["Software"])

        val metadata = assertNotNull(info.naiMetadata)
        assertEquals("1girl, silver hair", metadata.prompt)
        assertEquals(123456L, metadata.seed)
        assertEquals(28, metadata.steps)
        assertEquals(6.0f, metadata.scale)
        assertEquals("nai-diffusion-5-full", metadata.model)
    }

    @Test
    fun `returns null metadata when Comment is not JSON`() {
        val png = Buffer()
            .writeSignature()
            .writeChunk("tEXt", "Comment\u0000not a json".encodeToByteArray())
            .writeChunk("IEND", ByteArray(0))
            .readByteArray()

        val info = PngMetadataParser.parse(Buffer().apply { write(png) })

        assertNull(info.naiMetadata)
    }

    @Test
    fun `throws on invalid signature`() {
        val notPng = Buffer().writeUtf8("GIF89a not a png at all")

        assertFailsWith<IllegalArgumentException> {
            PngMetadataParser.parse(notPng)
        }
    }

    @Test
    fun `parses truncated file without IEND gracefully`() {
        val png = Buffer()
            .writeSignature()
            .writeChunk("IHDR", ihdr(width = 64, height = 64))
            .readByteArray() // 缺少 IEND 与 CRC 尾部

        val info = PngMetadataParser.parse(Buffer().apply { write(png) })

        assertEquals(64, info.width)
    }

    @Test
    fun `detects tRNS transparency on non alpha color type`() {
        val png = Buffer()
            .writeSignature()
            .writeChunk("IHDR", ihdr(width = 64, height = 64, colorType = 2))
            .writeChunk("tRNS", byteArrayOf(0, 0, 0, 0, 0, 0))
            .writeChunk("IEND", ByteArray(0))
            .readByteArray()

        assertTrue(PngMetadataParser.parse(Buffer().apply { write(png) }).hasTransparency)

        val opaque = Buffer()
            .writeSignature()
            .writeChunk("IHDR", ihdr(width = 64, height = 64, colorType = 2))
            .writeChunk("IEND", ByteArray(0))
            .readByteArray()
        assertFalse(PngMetadataParser.parse(Buffer().apply { write(opaque) }).hasTransparency)
    }

    // ---------- 测试用 PNG 构造工具 ----------

    private fun Buffer.writeSignature(): Buffer = write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))

    private fun Buffer.writeChunk(type: String, data: ByteArray): Buffer {
        writeInt(data.size)
        write(type.encodeToByteArray())
        write(data)
        writeInt(crc32(type.encodeToByteArray() + data))
        return this
    }

    private fun ihdr(width: Int, height: Int, colorType: Int = 6): ByteArray = Buffer()
        .apply {
            writeInt(width)
            writeInt(height)
            writeByte(8)  // bit depth
            writeByte(colorType)
            writeByte(0)  // compression
            writeByte(0)  // filter
            writeByte(0)  // interlace
        }
        .readByteArray()

    private fun crc32(data: ByteArray): Int {
        var crc = -1
        for (b in data) {
            crc = crc xor (b.toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 1 != 0) (crc ushr 1) xor -0x12477ce0 else crc ushr 1
            }
        }
        return crc.inv()
    }
}
