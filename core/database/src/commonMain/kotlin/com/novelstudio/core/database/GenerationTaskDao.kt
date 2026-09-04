package com.novelstudio.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GenerationTaskDao {
    @Query("SELECT * FROM generation_tasks ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<GenerationTaskEntity>>

    @Query("SELECT * FROM generation_tasks WHERE id = :id")
    suspend fun findById(id: String): GenerationTaskEntity?

    @Query("SELECT * FROM generation_tasks WHERE status IN ('QUEUED', 'WAITING_ANLAS_CONFIRMATION') ORDER BY createdAt ASC, id ASC")
    suspend fun findPending(): List<GenerationTaskEntity>

    @Query("SELECT * FROM generation_tasks WHERE status = 'QUEUED' ORDER BY createdAt ASC, id ASC LIMIT 1")
    suspend fun findNextQueued(): GenerationTaskEntity?

    @Query("SELECT MAX(createdAt) FROM generation_tasks")
    suspend fun maxCreatedAt(): Long?

    @Upsert
    suspend fun upsert(task: GenerationTaskEntity)

    @Query("UPDATE generation_tasks SET status = 'RUNNING', updatedAt = :updatedAt WHERE id = :id AND status = 'QUEUED'")
    suspend fun claimQueued(id: String, updatedAt: Long): Int

    @Query("""
        UPDATE generation_tasks
        SET status = 'WAITING_ANLAS_CONFIRMATION', decision = 'CONFIRM_ANLAS', updatedAt = :updatedAt
        WHERE id = :id AND status = 'RUNNING'
    """)
    suspend fun markWaitingForAnlas(id: String, updatedAt: Long): Int

    @Query("""
        UPDATE generation_tasks
        SET status = 'QUEUED', decision = 'REQUIRES_CONFIRMATION_CONFIRMED', updatedAt = :updatedAt
        WHERE id = :id AND status = 'WAITING_ANLAS_CONFIRMATION'
    """)
    suspend fun confirmWaitingAnlas(id: String, updatedAt: Long): Int

    @Query("""
        UPDATE generation_tasks
        SET status = 'CANCELLED', errorMessage = :message, completedAt = :completedAt, updatedAt = :completedAt
        WHERE id = :id AND status IN ('QUEUED', 'WAITING_ANLAS_CONFIRMATION')
    """)
    suspend fun cancelPending(id: String, message: String, completedAt: Long): Int

    @Query("""
        UPDATE generation_tasks
        SET status = 'FAILED', errorMessage = :message, completedAt = :completedAt, updatedAt = :completedAt
        WHERE id = :id AND status IN ('QUEUED', 'WAITING_ANLAS_CONFIRMATION')
    """)
    suspend fun failPending(id: String, message: String, completedAt: Long): Int

    @Query("""
        UPDATE generation_tasks
        SET status = :status, errorMessage = :errorMessage, resultImageId = :resultImageId,
            resultImageIdsJson = :resultImageIdsJson, completedAt = :completedAt, updatedAt = :completedAt
        WHERE id = :id AND status = 'RUNNING'
    """)
    suspend fun finishRunning(
        id: String,
        status: String,
        errorMessage: String?,
        resultImageId: String?,
        resultImageIdsJson: String,
        completedAt: Long,
    ): Int

    @Query("UPDATE generation_tasks SET status = 'FAILED_UNKNOWN', errorMessage = :message, completedAt = :completedAt, updatedAt = :completedAt WHERE status = 'RUNNING'")
    suspend fun markInterruptedRunning(message: String, completedAt: Long)

    @Query("DELETE FROM generation_tasks WHERE id = :id")
    suspend fun delete(id: String)
}
