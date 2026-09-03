package com.novelstudio.core.network

import com.novelstudio.core.model.GenerationParameters
import com.novelstudio.core.model.OpusBatteryState
import com.novelstudio.core.network.dto.ImageParametersPayload
import com.novelstudio.core.network.dto.ImageRequestPayload
import com.novelstudio.core.network.dto.CharacterPromptPayload
import com.novelstudio.core.network.dto.SubscriptionDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * NovelAI 官方 API 服务（NOVELAI_V5_SPEC.md §1）：
 * - POST /ai/generate-image：图像生成（ZIP/PNG 二进制响应）
 * - GET  /user/subscription：订阅档位与 Anlas 余额
 * - GET  /user/data：V5 Opus 充能电池百分比
 */
interface NovelAIApiService {

    /** 生成一张图，返回原始 PNG 字节（若服务端返回 ZIP 则自动提取首个 PNG） */
    suspend fun generateImage(parameters: GenerationParameters): ByteArray

    suspend fun getSubscription(): SubscriptionDto

    suspend fun getOpusBatteryState(): OpusBatteryState
}

class NovelAIApiServiceImpl(
    private val client: HttpClient,
    private val tokenProvider: suspend () -> String?,
) : NovelAIApiService {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    override suspend fun generateImage(parameters: GenerationParameters): ByteArray {
        val token = tokenProvider() ?: error("未配置 NovelAI API Token")
        val payload = parameters.toPayload()
        val responseBytes: ByteArray = client.post("$IMAGE_BASE_URL/ai/generate-image") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(json.encodeToString(ImageRequestPayload.serializer(), payload))
        }.body()

        return if (responseBytes.size >= 2 && responseBytes[0] == ZIP_MAGIC[0] && responseBytes[1] == ZIP_MAGIC[1]) {
            extractFirstPngFromZip(responseBytes) ?: error("ZIP 响应中未找到 PNG 图像")
        } else {
            responseBytes
        }
    }

    override suspend fun getSubscription(): SubscriptionDto {
        val token = tokenProvider() ?: error("未配置 NovelAI API Token")
        return client.get("$API_BASE_URL/user/subscription") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.body()
    }

    override suspend fun getOpusBatteryState(): OpusBatteryState {
        val subscription = getSubscription()
        val token = tokenProvider() ?: error("未配置 NovelAI API Token")
        val userData: JsonObject = client.get("$API_BASE_URL/user/data") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.body()

        val batteryPercent = userData[KEY_BATTERY_PERCENT]?.jsonPrimitive?.floatOrNull
            ?: (userData[KEY_BATTERY] as? JsonObject)
                ?.get(KEY_PERCENT)?.jsonPrimitive?.floatOrNull
            ?: 0f

        return OpusBatteryState(
            tier = subscription.toSubscriptionTier(),
            anlas = subscription.anlas ?: 0,
            batteryPercent = batteryPercent,
        )
    }

    private fun GenerationParameters.toPayload(): ImageRequestPayload = ImageRequestPayload(
        input = prompt,
        model = model.id,
        parameters = ImageParametersPayload(
            width = width,
            height = height,
            scale = scale,
            sampler = sampler.id,
            steps = steps,
            nSamples = nSamples,
            seed = seed.takeIf { it >= 0 } ?: randomSeed(),
            ucPreset = ucPreset,
            qualityToggle = qualityToggle,
            negativePrompt = negativePrompt,
            transparentBackground = transparentBackground && model.supportsTransparency,
            cfgRescale = cfgRescale,
            uncondScale = uncondScale,
            noiseSchedule = noiseSchedule.id,
            characterPrompts = characterPrompts.map { char ->
                CharacterPromptPayload(
                    prompt = char.prompt,
                    uc = char.uc,
                    centerX = char.centerX,
                    centerY = char.centerY,
                    width = char.width,
                    height = char.height,
                )
            },
        ),
    )

    companion object {
        const val API_BASE_URL = "https://api.novelai.net"
        const val IMAGE_BASE_URL = "https://image.novelai.net"
        private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B)
        private const val KEY_BATTERY_PERCENT = "batteryPercent"
        private const val KEY_BATTERY = "battery"
        private const val KEY_PERCENT = "percent"

        fun randomSeed(): Long = (0L..0xFFFFFFFFL).random()
    }
}
