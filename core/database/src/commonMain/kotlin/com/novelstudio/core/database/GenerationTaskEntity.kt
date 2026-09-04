package com.novelstudio.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 生成任务持久化快照。parametersJson 由上层使用 kotlinx.serialization 生成。 */
@Entity(
    tableName = "generation_tasks",
    indices = [Index(value = ["status", "createdAt"])],
)
data class GenerationTaskEntity(
    @PrimaryKey val id: String,
    val parametersJson: String,
    val status: String,
    val decision: String?,
    val errorMessage: String?,
    val resultImageId: String?,
    val createdAt: Long,
    val completedAt: Long?,
    val updatedAt: Long,
    /** JSON 字符串数组；resultImageId 保留首图兼容。 */
    val resultImageIdsJson: String = "[]",
)
