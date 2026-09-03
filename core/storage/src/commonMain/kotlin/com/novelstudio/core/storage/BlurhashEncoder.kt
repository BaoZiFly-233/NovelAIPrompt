package com.novelstudio.core.storage

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign

/**
 * BlurHash 编解码器（纯 Kotlin 实现标准算法，components 4x3）。
 *
 * 输入为 ARGB 像素数组（int，高字节 Alpha），输出为标准 BlurHash 串；
 * 同时提供解码用于往返测试与未来的瀑布流占位渲染。
 */
object BlurhashEncoder {

    private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~"

    private val SRGB_TO_LINEAR = DoubleArray(256) { i ->
        val v = i / 255.0
        if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    fun encode(argbPixels: IntArray, width: Int, height: Int, xComponents: Int = 4, yComponents: Int = 3): String {
        require(width > 0 && height > 0) { "图像尺寸必须为正" }
        require(argbPixels.size >= width * height) { "像素数组尺寸不足" }
        require(xComponents in 1..9 && yComponents in 1..9) { "分量数必须在 1..9" }

        val factors = Array(xComponents * yComponents) { DoubleArray(3) }
        for (cy in 0 until yComponents) {
            for (cx in 0 until xComponents) {
                val normalisation = if (cx == 0 && cy == 0) 1.0 else 2.0
                var r = 0.0
                var g = 0.0
                var b = 0.0
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val basis = cos(PI * cx * x / width) * cos(PI * cy * y / height)
                        val argb = argbPixels[y * width + x]
                        r += basis * SRGB_TO_LINEAR[(argb shr 16) and 0xFF]
                        g += basis * SRGB_TO_LINEAR[(argb shr 8) and 0xFF]
                        b += basis * SRGB_TO_LINEAR[argb and 0xFF]
                    }
                }
                val scale = normalisation / (width * height)
                val index = cy * xComponents + cx
                factors[index][0] = r * scale
                factors[index][1] = g * scale
                factors[index][2] = b * scale
            }
        }

        var maximumValue = 1.0
        if (xComponents * yComponents > 1) {
            for (i in 1 until factors.size) {
                maximumValue = max(maximumValue, abs(factors[i][0]))
                maximumValue = max(maximumValue, abs(factors[i][1]))
                maximumValue = max(maximumValue, abs(factors[i][2]))
            }
        }

        val dc = factors[0]
        val quantisedMaximumValue = max(0, min(82, floor(maximumValue * 166 - 0.5).toInt()))
        val actualMaximumValue = (quantisedMaximumValue + 1) / 166.0

        // 标准布局：size(1) + quant(1) + DC(4) + AC(2*(n-1))
        var hash = encode83((xComponents - 1) + (yComponents - 1) * 9, 1)
        hash += encode83(quantisedMaximumValue, 1)
        hash += encode83(
            (linearToSrgb(dc[0]) shl 16) + (linearToSrgb(dc[1]) shl 8) + linearToSrgb(dc[2]),
            4,
        )

        for (i in 1 until factors.size) {
            val q = IntArray(3)
            for (c in 0 until 3) {
                q[c] = max(0, min(18, floor(signPow(factors[i][c] / actualMaximumValue, 0.5) * 9.0 + 9.5).toInt()))
            }
            hash += encode83(q[0] * 19 * 19 + q[1] * 19 + q[2], 2)
        }
        return hash
    }

    /** 解码为 ARGB 像素（用于往返验证与占位渲染） */
    fun decodeToArgb(hash: String, width: Int, height: Int): IntArray {
        val linear = decodeToLinear(hash, width, height)
        return IntArray(width * height) { i ->
            (0xFF shl 24) or
                (linearToSrgb(linear[i * 3].toDouble()) shl 16) or
                (linearToSrgb(linear[i * 3 + 1].toDouble()) shl 8) or
                linearToSrgb(linear[i * 3 + 2].toDouble())
        }
    }

    private fun decodeToLinear(hash: String, width: Int, height: Int): FloatArray {
        require(hash.length >= 6) { "BlurHash 串过短" }
        val sizeFlag = decode83(hash, 0, 1)
        val yComponents = sizeFlag / 9 + 1
        val xComponents = sizeFlag % 9 + 1
        require(hash.length == 6 + 2 * (xComponents * yComponents - 1)) { "BlurHash 分量数与长度不符" }

        val quantisedMaximumValue = decode83(hash, 1, 2)
        val maximumValue = (quantisedMaximumValue + 1) / 166.0

        val dcValue = decode83(hash, 2, 6)
        val colors = ArrayList<DoubleArray>(xComponents * yComponents).also {
            it.add(
                doubleArrayOf(
                    srgbToLinear((dcValue shr 16) and 0xFF),
                    srgbToLinear((dcValue shr 8) and 0xFF),
                    srgbToLinear(dcValue and 0xFF),
                ),
            )
        }
        var offset = 6
        repeat(xComponents * yComponents - 1) {
            val quantised = decode83(hash, offset, offset + 2)
            offset += 2
            val q = intArrayOf(
                quantised / (19 * 19),
                (quantised / 19) % 19,
                quantised % 19,
            )
            colors.add(
                DoubleArray(3) { c -> signPow((q[c] - 9) / 9.0, 2.0) * maximumValue },
            )
        }

        val output = FloatArray(width * height * 3)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var r = 0.0
                var g = 0.0
                var b = 0.0
                for (cy in 0 until yComponents) {
                    for (cx in 0 until xComponents) {
                        val basis = cos(PI * cx * x / width) * cos(PI * cy * y / height)
                        val color = colors[cy * xComponents + cx]
                        r += color[0] * basis
                        g += color[1] * basis
                        b += color[2] * basis
                    }
                }
                val index = (y * width + x) * 3
                output[index] = r.toFloat()
                output[index + 1] = g.toFloat()
                output[index + 2] = b.toFloat()
            }
        }
        return output
    }

    private fun signPow(value: Double, exp: Double): Double = sign(value) * abs(value).pow(exp)

    private fun srgbToLinear(value: Int): Double = SRGB_TO_LINEAR[value.coerceIn(0, 255)]

    private fun linearToSrgb(value: Double): Int {
        val v = value.coerceIn(0.0, 1.0)
        val s = if (v <= 0.0031308) v * 12.92 else 1.055 * v.pow(1.0 / 2.4) - 0.055
        return (s * 255.0 + 0.5).toInt().coerceIn(0, 255)
    }

    private fun encode83(value: Int, length: Int): String {
        val result = CharArray(length)
        for (i in 1..length) {
            val digit = (value / pow83(length - i).toInt()) % 83
            result[i - 1] = ALPHABET[digit]
        }
        return String(result)
    }

    private fun pow83(exponent: Int): Double = 83.0.pow(exponent)

    private fun decode83(hash: String, from: Int, to: Int): Int {
        var value = 0
        for (i in from until to) {
            val index = ALPHABET.indexOf(hash[i])
            require(index >= 0) { "BlurHash 含非法字符: ${hash[i]}" }
            value = value * 83 + index
        }
        return value
    }
}
