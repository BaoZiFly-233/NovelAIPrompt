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

/** V5 Opus 用量额度快照；percent 由官方接口定义为 [0, 100+]，不可反推剩余张数。 */
@Serializable
data class OpusBatteryState(
    val tier: SubscriptionTier = SubscriptionTier.UNKNOWN,
    val isSubscriptionActive: Boolean = false,
    val batteryPercent: Int? = null,
    /** 官方 usage.isNegative；true 表示当前用量额度不可用。 */
    val isUsageUnavailable: Boolean = true,
    val timeUntilNextPercentSeconds: Int? = null,
) {
    val isOpus: Boolean get() = isSubscriptionActive && tier == SubscriptionTier.OPUS
    val canUseV5Allowance: Boolean
        get() = isOpus && batteryPercent != null && !isUsageUnavailable

    val isLow: Boolean
        get() = batteryPercent?.let { it <= BATTERY_WARNING } ?: true

    companion object {
        const val BATTERY_WARNING = 10
        /** 客户端“探索省额度”模式的显式策略阈值，并非服务端可用性边界。 */
        const val BATTERY_EXPLORATION_LOW = 30
    }
}

/** 生成任务生命周期状态 */
@Serializable
enum class TaskStatus {
    QUEUED,
    WAITING_ANLAS_CONFIRMATION,
    RUNNING,
    SUCCEEDED,
    FAILED,
    /** 进程中断或网络结果不明；禁止按普通失败自动重试。 */
    FAILED_UNKNOWN,
    CANCELLED,
}

/** 一次生成任务的领域实体 */
@Serializable
data class GenerationTask(
    val id: String,
    val parameters: GenerationParameters,
    val status: TaskStatus = TaskStatus.QUEUED,
    val preflight: GenerationPreflight? = null,
    val errorMessage: String? = null,
    /** 首图兼容入口；批量结果请使用 resultImageIds。 */
    val resultImageId: String? = null,
    val resultImageIds: List<String> = resultImageId?.let(::listOf).orEmpty(),
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
    val isFavorite: Boolean = false,
    val hasTransparency: Boolean = false,
    val rawMetadataJson: String = "",
    val artistStringId: String? = null,
    val promptAssetId: String? = null,
    val parentImageId: String? = null,
    val operationType: ImageOperation = ImageOperation.IMPORT,
    val generationSnapshotJson: String = "",
    val mimeType: String = "image/png",
    val archivedAt: Long? = null,
    val trashedAt: Long? = null,
    val createdAt: Long = 0L,
) {
    val isArchived: Boolean get() = archivedAt != null
    val isTrashed: Boolean get() = trashedAt != null
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
