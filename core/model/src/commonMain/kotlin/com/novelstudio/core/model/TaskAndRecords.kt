package com.novelstudio.core.model

import kotlinx.serialization.Serializable

/** 订阅档位（Anlas 计费与 Opus 充能电池规则的依据） */
@Serializable
enum class SubscriptionTier(val displayName: String) {
    OPUS("Opus"),
    SCROLL("Scroll"),
    TABLET("Tablet"),
    PAPER("Paper"),
    UNKNOWN("未知");
}

/** V5 Opus 充能电池状态快照 */
@Serializable
data class OpusBatteryState(
    val tier: SubscriptionTier = SubscriptionTier.UNKNOWN,
    val anlas: Int = 0,
    /** 充能电池剩余百分比 [0, 100] */
    val batteryPercent: Float = 0f,
) {
    val isOpus: Boolean get() = tier == SubscriptionTier.OPUS
    val isDepleted: Boolean get() = batteryPercent <= BATTERY_EMPTY
    val isLow: Boolean get() = batteryPercent <= BATTERY_LOW

    companion object {
        const val BATTERY_EMPTY = 0f
        const val BATTERY_LOW = 10f
        const val BATTERY_EXPLORATION_LOW = 30f
    }
}

/** 智能双轨路由状态机的输出决策 */
@Serializable
enum class DispatchDecision(val description: String) {
    /** 直接扣除 V5 电池，0 Anlas */
    USE_V5_BATTERY("扣除 V5 电池（0 Anlas）"),

    /** V5 电量不足，需要用户确认扣 Anlas */
    CONFIRM_ANLAS("V5 电量不足，需确认扣 Anlas"),

    /** 探索抽卡模式下电量过低，自动切 V4.5 无限池 */
    FALLBACK_V4_5("自动切换 V4.5 无限池（免费）"),
}

/** 生成任务生命周期状态 */
@Serializable
enum class TaskStatus { QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED }

/** 一次生成任务的领域实体 */
@Serializable
data class GenerationTask(
    val id: String,
    val parameters: GenerationParameters,
    val status: TaskStatus = TaskStatus.QUEUED,
    val decision: DispatchDecision? = null,
    val errorMessage: String? = null,
    val resultImageId: String? = null,
    val createdAt: Long = 0L,
    val completedAt: Long? = null,
)

/** 图库记录的领域实体（与 Room ImageEntity 字段一一对应） */
@Serializable
data class ImageRecord(
    val id: String,
    val filePath: String,
    val thumbnailPath: String = "",
    val blurHash: String = "",
    val prompt: String,
    val uc: String = "",
    val model: String,
    val seed: Long = -1L,
    val steps: Int = 28,
    val scale: Float = 6f,
    val sampler: String = Sampler.K_EULER.id,
    val width: Int = 1024,
    val height: Int = 1024,
    val starRating: Int = 3,
    val isFavorite: Boolean = false,
    val hasTransparency: Boolean = false,
    val rawMetadataJson: String = "",
    val createdAt: Long = 0L,
) {
    val isDisliked: Boolean get() = starRating == STAR_DISLIKE
    val isLiked: Boolean get() = starRating >= STAR_LIKE

    companion object {
        const val STAR_DISLIKE = 1
        const val STAR_NEUTRAL = 3
        const val STAR_LIKE = 5
    }
}

/** PNG tEXt:Comment 内嵌的 NAI 官方生成元数据（用于图库回填工作台） */
@Serializable
data class NaiImageMetadata(
    val prompt: String = "",
    val uc: String = "",
    val seed: Long = -1L,
    val steps: Int = 28,
    val scale: Float = 6f,
    val sampler: String = Sampler.K_EULER.id,
    val width: Int = 1024,
    val height: Int = 1024,
    val model: String = NaiModel.V5_FULL.id,
    val noise_schedule: String? = null,
    val cfg_rescale: Float? = null,
    val uncond_scale: Float? = null,
)
