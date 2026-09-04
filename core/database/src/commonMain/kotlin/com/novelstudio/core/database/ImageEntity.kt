package com.novelstudio.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** v1-v3 兼容的星级评分魔法数字，统一定义于此避免散落重复。新代码只写不读。 */
const val LEGACY_NEUTRAL_RATING = 3
const val LEGACY_FAVORITE_RATING = 5

/** 图库记录 Room 实体（ARCHITECTURE.md §4 数据库 Schema） */
@Entity(
    tableName = "images",
    foreignKeys = [
        ForeignKey(
            entity = ArtistStringEntity::class,
            parentColumns = ["id"],
            childColumns = ["artistStringId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = PromptAssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["promptAssetId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ImageEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentImageId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["createdAt", "id"]),
        Index("artistStringId"),
        Index("promptAssetId"),
        Index("parentImageId"),
        Index("archivedAt"),
        Index("trashedAt"),
    ],
)
data class ImageEntity(
    @PrimaryKey val id: String,               // UUID / SHA256
    val filePath: String,                     // 磁盘绝对路径（应用隔离目录）
    val thumbnailPath: String,                // 平台缩略图路径（最长边 256px）
    val blurHash: String,                     // 占位模糊字符串
    val prompt: String,                       // 提示词
    val uc: String,                           // 负面提示词
    val model: String,                        // 如 nai-diffusion-4-5-full, nai-diffusion-5
    val seed: Long,                           // 随机种子
    val steps: Int,                           // 采样步数
    val scale: Float,                         // CFG Scale
    val sampler: String,                      // 采样器算法
    val width: Int,                           // 原始宽度
    val height: Int,                          // 原始高度
    val starRating: Int,                      // v1-v3 兼容列；新逻辑不再读取该魔法评分
    val isFavorite: Boolean,                  // 显式喜欢状态
    val hasTransparency: Boolean,             // 是否包含 V5 透明背景
    val rawMetadataJson: String,              // 完整的 NAI 官方元数据 JSON 字符串
    val artistStringId: String? = null,
    val promptAssetId: String? = null,
    val parentImageId: String? = null,
    val operationType: String = "IMPORT",
    val generationSnapshotJson: String = "",
    val mimeType: String = "image/png",
    val archivedAt: Long? = null,
    val trashedAt: Long? = null,
    val createdAt: Long,                      // 毫秒时间戳
)
