package com.novelstudio.core.model

import kotlinx.serialization.Serializable

/** NovelAI 扩散模型标识（V5 / V4.5 双轨算力体系） */
@Serializable
enum class NaiModel(val id: String, val displayName: String, val supportsTransparency: Boolean) {
    V5_FULL("nai-diffusion-5-full", "V5 全能", true),
    V5_CURATED("nai-diffusion-5-curated", "V5 精选", true),
    V4_5_FULL("nai-diffusion-4-5-full", "V4.5 全能", false),
    V4_5_CURATED("nai-diffusion-4-5-curated", "V4.5 精选", false);

    companion object {
        fun fromId(id: String): NaiModel = entries.firstOrNull { it.id == id } ?: V5_FULL
    }
}

/** 采样器算法枚举 */
@Serializable
enum class Sampler(val id: String) {
    K_EULER("k_euler"),
    K_EULER_ANCESTRAL("k_euler_ancestral"),
    K_DPMPP_2S_ANCESTRAL("k_dpmpp_2s_ancestral"),
    K_DPMPP_2M("k_dpmpp_2m"),
    K_DPMPP_SDE("k_dpmpp_sde"),
    K_DPMPP_3M_SDE("k_dpmpp_3m_sde");

    companion object {
        fun fromId(id: String): Sampler = entries.firstOrNull { it.id == id } ?: K_EULER
    }
}

/** 噪声调度器 */
@Serializable
enum class NoiseSchedule(val id: String) {
    NATIVE("native"),
    KARRAS("karras"),
    EXPONENTIAL("exponential"),
    POLYEXPONENTIAL("polyexponential");

    companion object {
        fun fromId(id: String): NoiseSchedule = entries.firstOrNull { it.id == id } ?: NATIVE
    }
}

/** 常用画面比例预设（宽:高），分辨率由 OpusFreeCalculator 钳位到免费额度 */
@Serializable
enum class AspectPreset(val ratioWidth: Int, val ratioHeight: Int, val label: String) {
    SQUARE(1, 1, "1:1 方形"),
    PORTRAIT_2_3(2, 3, "2:3 竖屏"),
    PORTRAIT_3_4(3, 4, "3:4 竖屏"),
    LANDSCAPE_3_2(3, 2, "3:2 横屏"),
    LANDSCAPE_4_3(4, 3, "4:3 横屏"),
    LANDSCAPE_16_9(16, 9, "16:9 宽幕"),
    ULTRAWIDE_21_9(21, 9, "21:9 超宽");
}
