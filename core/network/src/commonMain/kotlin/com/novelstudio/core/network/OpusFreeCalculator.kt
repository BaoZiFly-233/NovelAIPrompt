package com.novelstudio.core.network

import kotlin.math.sqrt

/**
 * Opus 免费像素钳位算法（NOVELAI_V5_SPEC.md §3）。
 *
 * 规则：总像素 W × H ≤ 1,048,576（1024 × 1024）且必须为 64 的整数倍；
 * 步数 ≤ 28 时享受免费/电池额度。
 */
object OpusFreeCalculator {
    const val MAX_FREE_PIXELS = 1_048_576 // 1024 * 1024
    const val MAX_FREE_STEPS = 28
    const val GRID_STEP = 64
    const val MIN_SIZE = 64

    /**
     * 依据目标宽高比计算免费额度内的最大分辨率，
     * 返回 (width, height)，均已对齐 64 像素网格。
     */
    fun clampResolution(aspectRatioWidth: Int, aspectRatioHeight: Int): Pair<Int, Int> {
        require(aspectRatioWidth > 0 && aspectRatioHeight > 0) { "宽高比必须为正数" }
        val ratio = aspectRatioWidth.toDouble() / aspectRatioHeight.toDouble()
        var w = sqrt(MAX_FREE_PIXELS * ratio).toInt()
        var h = (w / ratio).toInt()

        // 对齐 64 倍数网格
        w = alignToGrid(w)
        h = alignToGrid(h)

        while (w * h > MAX_FREE_PIXELS) {
            if (w > h) w -= GRID_STEP else h -= GRID_STEP
        }
        return w.coerceAtLeast(MIN_SIZE) to h.coerceAtLeast(MIN_SIZE)
    }

    /** 步数钳位到免费上限 */
    fun clampSteps(steps: Int): Int = steps.coerceIn(1, MAX_FREE_STEPS)

    /** 是否命中免费/电池额度（像素与步数双重钳位判定） */
    fun isFreeGeneration(width: Int, height: Int, steps: Int): Boolean =
        width.toLong() * height <= MAX_FREE_PIXELS && steps <= MAX_FREE_STEPS

    private fun alignToGrid(value: Int): Int =
        (value / GRID_STEP) * GRID_STEP
}
