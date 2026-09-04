package com.novelstudio.core.data

import com.novelstudio.core.common.png.PngMetadataParser
import com.novelstudio.core.database.ImageDao
import com.novelstudio.core.database.ImageEntity
import com.novelstudio.core.model.GenerationParameters
import com.novelstudio.core.model.GeneratedImageBytes
import com.novelstudio.core.model.GenerationEvent
import com.novelstudio.core.model.GenerationPreflight
import com.novelstudio.core.model.ImageRecord
import com.novelstudio.core.model.NaiImageMetadata
import com.novelstudio.core.model.NaiModel
import com.novelstudio.core.model.OpusBatteryState
import com.novelstudio.core.model.VibeReference
import com.novelstudio.core.network.NaiApiException
import com.novelstudio.core.network.NovelAIApiService
import com.novelstudio.core.storage.ImageFileStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.Json
import okio.Buffer
import kotlin.random.Random

/** 生成流程结果：成功（含即时预览字节）/ 需要用户确认扣 Anlas / 失败（已翻译为用户可读消息） */
sealed interface GenerationOutcome {
    data class Success(
        val record: ImageRecord,
        val previewBytes: ByteArray,
        val preflight: GenerationPreflight,
        val additionalRecords: List<ImageRecord> = emptyList(),
    ) : GenerationOutcome {
        /** 同一次 n_samples 请求产生的完整结果；record 保留首图兼容入口。 */
        val records: List<ImageRecord> get() = listOf(record) + additionalRecords
    }

    data class NeedsAnlasConfirmation(
        val parameters: GenerationParameters,
        val preflight: GenerationPreflight.RequiresConfirmation,
    ) : GenerationOutcome

    data class Failure(
        val message: String,
        val cause: Throwable? = null,
        /** true 表示计费 POST 已开始，客户端不得把该失败自动重新入队。 */
        val submissionMayHaveCompleted: Boolean = false,
    ) : GenerationOutcome
}

/** 生成任务仓储抽象：双轨调度 → API → 文件落盘 → 元数据入库 的完整管道 */
interface GenerationRepository {
    suspend fun getBatteryState(): OpusBatteryState
    suspend fun encodeVibe(
        imageBytes: ByteArray,
        displayName: String,
        model: NaiModel,
        informationExtracted: Float,
    ): VibeReference
    suspend fun preflight(parameters: GenerationParameters): GenerationPreflight
    suspend fun generate(parameters: GenerationParameters): GenerationOutcome
    suspend fun generateWithAnlas(parameters: GenerationParameters): GenerationOutcome
}

class GenerationRepositoryImpl(
    private val api: NovelAIApiService,
    private val imageDao: ImageDao,
    private val fileStorage: ImageFileStorage,
    private val idGenerator: () -> String = { randomId() },
) : GenerationRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun getBatteryState(): OpusBatteryState = api.getOpusBatteryState()

    override suspend fun encodeVibe(
        imageBytes: ByteArray,
        displayName: String,
        model: NaiModel,
        informationExtracted: Float,
    ): VibeReference {
        require(displayName.isNotBlank()) { "Vibe 名称不能为空" }
        val encoding = api.encodeVibe(imageBytes, model.id, informationExtracted)
        return VibeReference(
            id = idGenerator(),
            displayName = displayName,
            encoding = encoding,
            model = model,
            informationExtracted = informationExtracted,
        )
    }

    override suspend fun preflight(parameters: GenerationParameters): GenerationPreflight {
        val battery = try {
            getBatteryState()
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            return GenerationPreflight.Blocked("无法确认订阅与计费状态，已停止且未提交生成")
        }
        if (battery.tier == com.novelstudio.core.model.SubscriptionTier.UNKNOWN) {
            return GenerationPreflight.Blocked("订阅档位未知，无法判断是否会计费")
        }
        return if (battery.canUseV5Allowance && parameters.isWithinFreeQuota) {
            GenerationPreflight.Free
        } else {
            GenerationPreflight.RequiresConfirmation(parameters.anlasSummary())
        }
    }

    override suspend fun generate(parameters: GenerationParameters): GenerationOutcome {
        return when (val result = preflight(parameters)) {
            GenerationPreflight.Free -> execute(parameters, result)
            is GenerationPreflight.RequiresConfirmation -> GenerationOutcome.NeedsAnlasConfirmation(parameters, result)
            is GenerationPreflight.Blocked -> GenerationOutcome.Failure(result.reason)
        }
    }

    override suspend fun generateWithAnlas(parameters: GenerationParameters): GenerationOutcome =
        execute(parameters, GenerationPreflight.RequiresConfirmation(parameters.anlasSummary()))

    private suspend fun execute(parameters: GenerationParameters, preflight: GenerationPreflight): GenerationOutcome =
        withContext(Dispatchers.IO) {
            val persistedIds = mutableListOf<String>()
            try {
                // 图像生成可能计费且服务端结果在传输失败时不可判定；一次人类操作只提交一次，禁止自动重试。
                val images = mutableListOf<GeneratedImageBytes>()
                var intermediate: GeneratedImageBytes? = null
                api.generateImageStream(parameters).collect { event ->
                    when (event) {
                        is GenerationEvent.Intermediate -> {
                            intermediate = GeneratedImageBytes(event.imageBytes, event.mimeType, event.index)
                        }
                        is GenerationEvent.Final -> images += event.images
                        is GenerationEvent.Failure -> error(event.message)
                    }
                }
                require(images.isNotEmpty()) { "生成响应中没有有效 PNG 图像" }
                val entities = images.map { generated ->
                    val bytes = generated.bytes
                    val id = idGenerator()
                    val stored = fileStorage.saveImage(id, bytes, generated.mimeType)
                    persistedIds += id

                    val parsed = if (generated.mimeType == "image/png") runCatching {
                        PngMetadataParser.parse(Buffer().apply { write(bytes) })
                    }.getOrNull() else null
                    val metadata = parsed?.naiMetadata ?: parameters.toMetadata()
                    ImageEntity(
                        id = id,
                        filePath = stored.imagePath,
                        thumbnailPath = stored.thumbnailPath,
                        blurHash = stored.blurHash,
                        prompt = metadata.prompt,
                        uc = metadata.uc,
                        model = metadata.model,
                        seed = metadata.seed,
                        steps = metadata.steps,
                        scale = metadata.scale,
                        sampler = metadata.sampler,
                        width = parsed?.width ?: parameters.width,
                        height = parsed?.height ?: parameters.height,
                        starRating = LEGACY_NEUTRAL_RATING,
                        isFavorite = false,
                        hasTransparency = parsed?.hasTransparency ?: false,
                        rawMetadataJson = json.encodeToString(NaiImageMetadata.serializer(), metadata),
                        artistStringId = parameters.artistStringId,
                        promptAssetId = parameters.promptAssetId,
                        parentImageId = parameters.parentImageId,
                        operationType = parameters.operation.name,
                        generationSnapshotJson = json.encodeToString(GenerationParameters.serializer(), parameters),
                        mimeType = generated.mimeType,
                        archivedAt = null,
                        trashedAt = null,
                        createdAt = System.currentTimeMillis(),
                    )
                }
                imageDao.upsertAll(entities)
                val records = entities.map(ImageEntity::toRecord)
                GenerationOutcome.Success(
                    record = records.first(),
                    previewBytes = intermediate?.bytes ?: images.first().bytes,
                    preflight = preflight,
                    additionalRecords = records.drop(1),
                )
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                withContext(NonCancellable) {
                    cleanupPersistedImages(persistedIds)
                }
                throw cancellation
            } catch (throwable: Throwable) {
                val cleanupFailures = cleanupPersistedImages(persistedIds)
                val cleanupSuffix = if (cleanupFailures.isEmpty()) {
                    ""
                } else {
                    "；本地回滚未完整（${cleanupFailures.joinToString()}），请检查图库存储"
                }
                GenerationOutcome.Failure(
                    message = NaiApiException.describe(throwable) + cleanupSuffix,
                    cause = throwable,
                    submissionMayHaveCompleted = true,
                )
            }
        }

    private suspend fun cleanupPersistedImages(ids: List<String>): List<String> {
        if (ids.isEmpty()) return emptyList()
        val failures = mutableListOf<String>()
        runCatching { imageDao.deleteAll(ids) }
            .onFailure { failures += "数据库记录" }
        ids.forEach { id ->
            runCatching { fileStorage.deleteImage(id) }
                .onFailure { failures += "图片 $id" }
        }
        return failures
    }

    private fun GenerationParameters.toMetadata(): NaiImageMetadata = NaiImageMetadata(
        prompt = prompt,
        uc = negativePrompt,
        seed = seed,
        steps = steps,
        scale = scale,
        sampler = sampler.id,
        width = width,
        height = height,
        model = model.id,
    )

    private fun GenerationParameters.anlasSummary(): String = buildString {
        append("${model.displayName} · ${width}×${height} · ${steps} 步 · $nSamples 张")
        if (operation != com.novelstudio.core.model.ImageOperation.GENERATE) append(" · ${operation.name}")
        append("。继续提交可能消耗 ImageAnlas。")
    }

    companion object {
        private const val LEGACY_NEUTRAL_RATING = 3
        fun randomId(): String = Random.nextLong(0, Long.MAX_VALUE).toString(36)
    }
}
