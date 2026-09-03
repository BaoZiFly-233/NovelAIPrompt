package com.novelstudio.core.data

import com.novelstudio.core.common.png.PngMetadataParser
import com.novelstudio.core.database.ImageDao
import com.novelstudio.core.database.ImageEntity
import com.novelstudio.core.model.DispatchDecision
import com.novelstudio.core.model.GenerationParameters
import com.novelstudio.core.model.ImageRecord
import com.novelstudio.core.model.NaiImageMetadata
import com.novelstudio.core.model.OpusBatteryState
import com.novelstudio.core.network.NaiApiException
import com.novelstudio.core.network.NovelAIApiService
import com.novelstudio.core.network.Retry
import com.novelstudio.core.network.SmartDispatcher
import com.novelstudio.core.storage.ImageFileStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.Buffer
import kotlin.random.Random

/** 生成流程结果：成功（含即时预览字节）/ 需要用户确认扣 Anlas / 失败（已翻译为用户可读消息） */
sealed interface GenerationOutcome {
    data class Success(
        val record: ImageRecord,
        val previewBytes: ByteArray,
        val decision: DispatchDecision,
    ) : GenerationOutcome

    data class NeedsAnlasConfirmation(
        val parameters: GenerationParameters,
        val decision: DispatchDecision,
    ) : GenerationOutcome

    data class Failure(val message: String, val cause: Throwable? = null) : GenerationOutcome
}

/** 生成任务仓储抽象：双轨调度 → API → 文件落盘 → 元数据入库 的完整管道 */
interface GenerationRepository {
    suspend fun generate(parameters: GenerationParameters, explorationMode: Boolean): GenerationOutcome
    suspend fun generateWithAnlas(parameters: GenerationParameters): GenerationOutcome
}

class GenerationRepositoryImpl(
    private val api: NovelAIApiService,
    private val imageDao: ImageDao,
    private val fileStorage: ImageFileStorage,
    private val idGenerator: () -> String = { randomId() },
) : GenerationRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun generate(
        parameters: GenerationParameters,
        explorationMode: Boolean,
    ): GenerationOutcome {
        val battery = runCatching { api.getOpusBatteryState() }.getOrDefault(OpusBatteryState())
        return when (val decision = SmartDispatcher.decide(parameters, battery, explorationMode)) {
            DispatchDecision.USE_V5_BATTERY -> execute(parameters, decision)
            DispatchDecision.FALLBACK_V4_5 -> execute(SmartDispatcher.degradeToV4_5(parameters), decision)
            DispatchDecision.CONFIRM_ANLAS -> GenerationOutcome.NeedsAnlasConfirmation(parameters, decision)
        }
    }

    override suspend fun generateWithAnlas(parameters: GenerationParameters): GenerationOutcome =
        execute(parameters, DispatchDecision.CONFIRM_ANLAS)

    private suspend fun execute(parameters: GenerationParameters, decision: DispatchDecision): GenerationOutcome =
        withContext(Dispatchers.IO) {
            try {
                val bytes = Retry.withExponentialBackoff { api.generateImage(parameters) }
                val id = idGenerator()
                val stored = fileStorage.saveImage(id, bytes)

                val parsed = runCatching {
                    PngMetadataParser.parse(Buffer().apply { write(bytes) })
                }.getOrNull()
                val metadata = parsed?.naiMetadata ?: parameters.toMetadata()
                val entity = ImageEntity(
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
                    starRating = ImageRecord.STAR_NEUTRAL,
                    isFavorite = false,
                    hasTransparency = parameters.transparentBackground,
                    rawMetadataJson = json.encodeToString(NaiImageMetadata.serializer(), metadata),
                    createdAt = System.currentTimeMillis(),
                )
                imageDao.upsert(entity)
                GenerationOutcome.Success(entity.toRecord(), bytes, decision)
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                GenerationOutcome.Failure(NaiApiException.describe(throwable), throwable)
            }
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

    companion object {
        fun randomId(): String = Random.nextLong(0, Long.MAX_VALUE).toString(36)
    }
}
