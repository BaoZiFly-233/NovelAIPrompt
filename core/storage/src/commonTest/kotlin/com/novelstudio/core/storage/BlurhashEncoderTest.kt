package com.novelstudio.core.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlurhashEncoderTest {

    @Test
    fun `pure white image encodes to valid hash and roundtrips`() {
        val white = IntArray(64 * 64) { 0xFFFFFFFF.toInt() }
        val hash = BlurhashEncoder.encode(white, 64, 64)

        // sizeFlag: 4x3 → (4-1)+(3-1)*9 = 21 → 首字符 'L'（与官方示例 LEHV6n... 一致）
        assertEquals('L', hash[0])
        // 总长 = 1(size) + 4(DC) + 1(quant) + 2*(12-1)(AC) = 28
        assertEquals(28, hash.length)

        val decoded = BlurhashEncoder.decodeToArgb(hash, 8, 8)
        // 白色图往返后所有像素应接近纯白（允许量化误差）
        assertTrue(decoded.all { (it shr 16) and 0xFF >= 245 }, "R 通道应接近 255: ${decoded.first()}")
        assertTrue(decoded.all { (it shr 8) and 0xFF >= 245 }, "G 通道应接近 255")
        assertTrue(decoded.all { it and 0xFF >= 245 }, "B 通道应接近 255")
    }

    @Test
    fun `pure black image encodes to dark hash`() {
        val black = IntArray(32 * 32) { 0xFF000000.toInt() }
        val hash = BlurhashEncoder.encode(black, 32, 32)

        val decoded = BlurhashEncoder.decodeToArgb(hash, 4, 4)
        assertTrue(decoded.all { (it shr 16) and 0xFF <= 10 }, "黑色图 R 通道应接近 0")
    }

    @Test
    fun `encoding is deterministic`() {
        val gradient = IntArray(48 * 48) { i ->
            val x = i % 48
            (0xFF shl 24) or (x * 5 shl 16) or (x * 3 shl 8) or x
        }
        assertEquals(
            BlurhashEncoder.encode(gradient, 48, 48),
            BlurhashEncoder.encode(gradient, 48, 48),
        )
    }

    @Test
    fun `hash format follows component count`() {
        val gradient = IntArray(16 * 16) { 0xFF808080.toInt() }
        val hash4x3 = BlurhashEncoder.encode(gradient, 16, 16, xComponents = 4, yComponents = 3)
        val hash1x1 = BlurhashEncoder.encode(gradient, 16, 16, xComponents = 1, yComponents = 1)

        // 4x3 → 1(size) + 4(DC) + 1(quant) + 2*(12-1)(AC) = 28；1x1 → 1+4+1+0 = 6
        assertEquals(28, hash4x3.length)
        assertEquals(6, hash1x1.length)
    }
}
