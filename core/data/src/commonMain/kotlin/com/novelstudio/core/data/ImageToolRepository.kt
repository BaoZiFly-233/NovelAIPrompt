package com.novelstudio.core.data

import com.novelstudio.core.common.png.PngMetadataParser
import com.novelstudio.core.database.ImageDao
import com.novelstudio.core.database.ImageEntity
import com.novelstudio.core.model.GenerationPreflight
import com.novelstudio.core.model.ImageOperation
import com.novelstudio.core.model.ImageRecord
import com.novelstudio.core.model.ImageToolRequest
import com.novelstudio.core.model.SubscriptionTier
import com.novelstudio.core.network.NaiApiException
import com.novelstudio.core.network.NovelAIApiService
import com.novelstudio.core.storage.ImageFileStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.Buffer

sealed interface ImageToolOutcome {
    data class Success(val records: List<ImageRecord>) : ImageToolOutcome
    data class Failure(
        val message: String,
        val cause: Throwable? = null,
        val submissionMayHaveCompleted: Boolean = false,
    ) : ImageToolOutcome
}

interface ImageToolRepository {
    suspend fun preflight(request: ImageToolRequest): GenerationPreflight
    suspend fun executeConfirmed(request: ImageToolRequest): ImageToolOutcome
}

class ImageToolRepositoryImpl(
    private val api: NovelAIApiService,
    private val imageDao: ImageDao,
    private val storage: ImageFileStorage,
    private val idGenerator: () -> String = { GenerationRepositoryImpl.randomId() },
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ImageToolRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun preflight(request: ImageToolRequest): GenerationPreflight {
        val parent = imageDao.findById(request.parentImageId)
            ?: return GenerationPreflight.Blocked("父作品不存在，不能创建派生结果")
        if (parent.trashedAt != null) return GenerationPreflight.Blocked("父作品位于垃圾箱，不能创建派生结果")
        val battery = try {
            api.getOpusBatteryState()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return GenerationPreflight.Blocked("无法确认订阅与计费状态，已停止且未提交图像工具请求")
        }
        if (battery.tier == SubscriptionTier.UNKNOWN) {
            return GenerationPreflight.Blocked("订阅档位未知，无法判断图像工具计费")
        }
        val operation = request.operation()
        return GenerationPreflight.RequiresConfirmation(
            "将对作品 ${parent.id} 执行 ${operation.name} 并创建新作品；原图不会被覆盖，继续可能消耗 ImageAnlas。",
        )
    }

    override suspend fun executeConfirmed(request: ImageToolRequest): ImageToolOutcome = withContext(Dispatchers.IO) {
        val parent = imageDao.findById(request.parentImageId)
            ?: return@withContext ImageToolOutcome.Failure("父作品不存在，未提交请求")
        val persistedIds = mutableListOf<String>()
        try {
            val results = when (request) {
                is ImageToolRequest.Upscale -> api.upscale(request)
                is ImageToolRequest.Director -> api.augment(request)
            }
            require(results.isNotEmpty()) { "图像工具响应中没有有效图像" }
            val snapshot = json.encodeToString(ImageToolRequest.serializer(), request)
            val entities = results.map { result ->
                val id = idGenerator()
                val stored = storage.saveImage(id, result.bytes, result.mimeType)
                persistedIds += id
                val png = if (result.mimeType == "image/png") runCatching {
                    PngMetadataParser.parse(Buffer().apply { write(result.bytes) })
                }.getOrNull() else null
                parent.copy(
                    id = id,
                    filePath = stored.imagePath,
                    thumbnailPath = stored.thumbnailPath,
                    blurHash = stored.blurHash,
                    seed = result.seed ?: parent.seed,
                    width = png?.width ?: request.source.width,
                    height = png?.height ?: request.source.height,
                    starRating = LEGACY_NEUTRAL_RATING,
                    isFavorite = false,
                    hasTransparency = png?.hasTransparency ?: parent.hasTransparency,
                    parentImageId = parent.id,
                    operationType = request.operation().name,
                    generationSnapshotJson = snapshot,
                    mimeType = result.mimeType,
                    archivedAt = null,
                    trashedAt = null,
                    createdAt = clock(),
                )
            }
            imageDao.upsertAll(entities)
            ImageToolOutcome.Success(entities.map(ImageEntity::toRecord))
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) { cleanup(persistedIds) }
            throw cancellation
        } catch (throwable: Throwable) {
            cleanup(persistedIds)
            ImageToolOutcome.Failure(
                message = NaiApiException.describe(throwable),
                cause = throwable,
                submissionMayHaveCompleted = true,
            )
        }
    }

    private suspend fun cleanup(ids: List<String>) {
        if (ids.isEmpty()) return
        runCatching { imageDao.deleteAll(ids) }
        ids.forEach { id -> runCatching { storage.deleteImage(id) } }
    }

    private fun ImageToolRequest.operation(): ImageOperation = when (this) {
        is ImageToolRequest.Upscale -> ImageOperation.UPSCALE
        is ImageToolRequest.Director -> tool.operation
    }

    private companion object {
        const val LEGACY_NEUTRAL_RATING = 3
    }
}
