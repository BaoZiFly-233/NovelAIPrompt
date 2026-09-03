package com.novelstudio.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 图库记录 Room 实体（ARCHITECTURE.md §4 数据库 Schema） */
@Entity(tableName = "images")
data class ImageEntity(
    @PrimaryKey val id: String,               // UUID / SHA256
    val filePath: String,                     // 磁盘绝对路径（应用隔离目录）
    val thumbnailPath: String,                // WebP 缩略图路径 (256x256)
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
    val starRating: Int,                      // 1=不喜欢/垃圾箱, 3=普通, 5=收藏/喜欢
    val isFavorite: Boolean,                  // 是否加入喜欢卡片库
    val hasTransparency: Boolean,             // 是否包含 V5 透明背景
    val rawMetadataJson: String,              // 完整的 NAI 官方元数据 JSON 字符串
    val createdAt: Long,                      // 毫秒时间戳
)
