package com.novelstudio.core.network

import com.novelstudio.core.model.GenerationParameters
import com.novelstudio.core.model.GeneratedImageBytes
import com.novelstudio.core.model.GenerationAction
import com.novelstudio.core.model.GenerationEvent
import com.novelstudio.core.model.ImageToolRequest
import com.novelstudio.core.model.TagSuggestion
import com.novelstudio.core.model.OpusBatteryState
import com.novelstudio.core.model.SubscriptionTier
import com.novelstudio.core.model.V5Character
import com.novelstudio.core.network.dto.CharacterCaptionEntryPayload
import com.novelstudio.core.network.dto.CharacterCaptionPayload
import com.novelstudio.core.network.dto.CharacterCenterPayload
import com.novelstudio.core.network.dto.CharacterConditionPayload
import com.novelstudio.core.network.dto.ImageParametersPayload
import com.novelstudio.core.network.dto.ImageRequestPayload
import com.novelstudio.core.network.dto.Img2ImgParamsPayload
import com.novelstudio.core.network.dto.GenerateControlNetMaskRequestPayload
import com.novelstudio.core.network.dto.UpscaleRequestPayload
import com.novelstudio.core.network.dto.AugmentImageRequestPayload
import com.novelstudio.core.network.dto.ImageGenerationJsonResponsePayload
import com.novelstudio.core.network.dto.TagSuggestionPayload
import com.novelstudio.core.network.dto.VibeEncodeRequestPayload
import com.novelstudio.core.network.dto.SubscriptionDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.parameter
import io.ktor.client.request.accept
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * NovelAI 官方 API 服务（NOVELAI_V5_SPEC.md §1）：
 * - POST /ai/generate-image：图像生成（ZIP/PNG 二进制响应）
 * - POST /ai/encode-vibe：参考图编码（二进制响应，可能计费）
 * - GET  /user/subscription：订阅档位与 V5 Opus usage 状态
 */
interface NovelAIApiService {

    /** 生成并返回响应中的全部 PNG；单张兼容入口只允许响应恰好包含一张图。 */
    suspend fun generateImages(parameters: GenerationParameters): List<ByteArray> =
        listOf(generateImage(parameters))

    suspend fun generateImage(parameters: GenerationParameters): ByteArray =
        generateImages(parameters).singleOrNull() ?: error("生成响应包含的 PNG 数量不是 1")

    fun generateImageStream(parameters: GenerationParameters): Flow<GenerationEvent> = flow {
        val images = generateImages(parameters).map { bytes ->
            GeneratedImageBytes(bytes, requireNotNull(bytes.detectImageMimeType()) { "生成结果不是有效图像" })
        }
        emit(GenerationEvent.Final(images))
    }

    suspend fun suggestTags(model: String, prompt: String, language: String = "en"): List<TagSuggestion> =
        error("当前 API 实现不支持 Tag 建议")

    suspend fun upscale(request: ImageToolRequest.Upscale): List<GeneratedImageBytes> =
        error("当前 API 实现不支持 Upscale")

    suspend fun augment(request: ImageToolRequest.Director): List<GeneratedImageBytes> =
        error("当前 API 实现不支持 Director Tools")

    /** 生成 ControlNet 条件遮罩，结果以 base64 字符串传入 /ai/generate-image 的 controlnet_condition 字段。 */
    suspend fun generateControlNetMask(
        imageBytes: ByteArray,
        model: String,
        width: Int,
        height: Int,
    ): String = error("当前 API 实现不支持 ControlNet")

    suspend fun getSubscription(): SubscriptionDto

    suspend fun getOpusBatteryState(): OpusBatteryState

    suspend fun encodeVibe(imageBytes: ByteArray, model: String, informationExtracted: Float): ByteArray
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

    override suspend fun generateImages(parameters: GenerationParameters): List<ByteArray> {
        val token = tokenProvider() ?: error("未配置 NovelAI API Token")
        val payload = parameters.toImageRequestPayload()
        val response = client.post("$IMAGE_BASE_URL/ai/generate-image") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(json.encodeToString(ImageRequestPayload.serializer(), payload))
        }
        ensureSuccess(response, "/ai/generate-image")

        val responseBytes: ByteArray = response.body()
        return decodeImageResponse(responseBytes, response.headers[HttpHeaders.ContentType], json).map(GeneratedImageBytes::bytes)
    }

    override fun generateImageStream(parameters: GenerationParameters): Flow<GenerationEvent> = flow {
        val token = tokenProvider() ?: error("未配置 NovelAI API Token")
        val payload = parameters.toImageRequestPayload(stream = true)
        val response = client.post("$IMAGE_BASE_URL/ai/generate-image-stream") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Text.EventStream)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(json.encodeToString(ImageRequestPayload.serializer(), payload))
        }
        ensureSuccess(response, "/ai/generate-image-stream")
        val channel = response.bodyAsChannel()
        var eventName: String? = null
        val data = StringBuilder()
        suspend fun flush() {
            if (data.isEmpty()) return
            emit(parseSseGenerationEvent(eventName, data.toString(), json))
            eventName = null
            data.clear()
        }
        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: break
            when {
                line.isEmpty() -> flush()
                line.startsWith("event:") -> eventName = line.substringAfter(':').trim()
                line.startsWith("data:") -> {
                    if (data.isNotEmpty()) data.append('\n')
                    data.append(line.substringAfter(':').trimStart())
                }
            }
        }
        flush()
    }

    override suspend fun suggestTags(model: String, prompt: String, language: String): List<TagSuggestion> {
        val token = tokenProvider() ?: error("未配置 NovelAI API Token")
        val response = client.get("$IMAGE_BASE_URL/ai/generate-image/suggest-tags") {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("model", model)
            parameter("prompt", prompt)
            parameter("lang", language)
        }
        ensureSuccess(response, "/ai/generate-image/suggest-tags")
        val root = json.parseToJsonElement(response.body<String>())
        return root.extractSuggestionElements().map { element ->
            val dto = json.decodeFromJsonElement(TagSuggestionPayload.serializer(), element)
            TagSuggestion(dto.tag, dto.confidence, dto.count)
        }
    }

    override suspend fun upscale(request: ImageToolRequest.Upscale): List<GeneratedImageBytes> {
        val token = tokenProvider() ?: error("未配置 NovelAI API Token")
        val payload = UpscaleRequestPayload(
            image = Base64.encode(request.source.bytes),
            model = request.modelId,
            declaredBlurSigma = request.declaredBlurSigma,
        )
        val response = client.post("$IMAGE_BASE_URL/ai/upscale") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(json.encodeToString(UpscaleRequestPayload.serializer(), payload))
        }
        ensureSuccess(response, "/ai/upscale")
        return decodeImageResponse(response.body(), response.headers[HttpHeaders.ContentType], json)
    }

    override suspend fun augment(request: ImageToolRequest.Director): List<GeneratedImageBytes> {
        val token = tokenProvider() ?: error("未配置 NovelAI API Token")
        require(request.tool.name in setOf("COLORIZE", "EMOTION") || request.prompt == null) {
            "只有 Colorize 与 Emotion 可以携带 Prompt"
        }
        require(request.tool.name in setOf("COLORIZE", "EMOTION") || request.defry == null) {
            "只有 Colorize 与 Emotion 可以携带 Defry"
        }
        val payload = AugmentImageRequestPayload(
            image = Base64.encode(request.source.bytes),
            width = request.source.width,
            height = request.source.height,
            requestType = request.tool.requestType,
            prompt = request.prompt,
            defry = request.defry,
        )
        val response = client.post("$IMAGE_BASE_URL/ai/augment-image") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(json.encodeToString(AugmentImageRequestPayload.serializer(), payload))
        }
        ensureSuccess(response, "/ai/augment-image")
        return decodeImageResponse(response.body(), response.headers[HttpHeaders.ContentType], json)
    }

    override suspend fun getSubscription(): SubscriptionDto {
        val token = tokenProvider() ?: error("未配置 NovelAI API Token")
        val response = client.get("$IMAGE_BASE_URL/user/subscription") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        ensureSuccess(response, "/user/subscription")
        return response.body()
    }

    override suspend fun getOpusBatteryState(): OpusBatteryState {
        val subscription = getSubscription()
        return subscription.toOpusBatteryState()
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun generateControlNetMask(
        imageBytes: ByteArray,
        model: String,
        width: Int,
        height: Int,
    ): String {
        val token = tokenProvider() ?: error("未配置 NovelAI API Token")
        val payload = GenerateControlNetMaskRequestPayload(
            image = Base64.encode(imageBytes),
            model = model,
            parameters = com.novelstudio.core.network.dto.ControlNetMaskParametersPayload(width, height),
        )
        val response = client.post("$IMAGE_BASE_URL/ai/generate-controlnet-mask") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(json.encodeToString(GenerateControlNetMaskRequestPayload.serializer(), payload))
        }
        ensureSuccess(response, "/ai/generate-controlnet-mask")
        // 响应为二进制图像，转换为 base64 字符串供 controlnet_condition 字段使用
        val resultBytes: ByteArray = response.body()
        return Base64.encode(resultBytes)
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun encodeVibe(        imageBytes: ByteArray,
        model: String,
        informationExtracted: Float,
    ): ByteArray {
        val token = tokenProvider() ?: error("未配置 NovelAI API Token")
        val payload = imageBytes.toVibeEncodeRequestPayload(model, informationExtracted)
        val response = client.post("$IMAGE_BASE_URL/ai/encode-vibe") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(json.encodeToString(VibeEncodeRequestPayload.serializer(), payload))
        }
        ensureSuccess(response, "/ai/encode-vibe")
        return response.body()
    }

    private suspend fun ensureSuccess(response: HttpResponse, endpoint: String) {
        if (!response.status.isSuccess()) {
            throw NaiApiException(response.status.value, "HTTP ${response.status.value} from $endpoint")
        }
    }

    companion object {
        const val IMAGE_BASE_URL = "https://image.novelai.net"
        private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B)

        fun randomSeed(): Long = (0L..0xFFFFFFFFL).random()
    }
}

@OptIn(ExperimentalEncodingApi::class)
internal fun ByteArray.toVibeEncodeRequestPayload(
    model: String,
    informationExtracted: Float,
): VibeEncodeRequestPayload {
    require(isNotEmpty()) { "Vibe 图片不能为空" }
    require(model.isNotBlank()) { "Vibe 编码模型不能为空" }
    require(informationExtracted in 0f..1f) { "informationExtracted 必须位于 [0, 1]" }
    return VibeEncodeRequestPayload(
        image = Base64.encode(this),
        model = model,
        informationExtracted = informationExtracted,
    )
}

internal fun SubscriptionDto.toOpusBatteryState(): OpusBatteryState {
    val tier = toSubscriptionTier()
    if (!active || tier != SubscriptionTier.OPUS) {
        return OpusBatteryState(tier = tier, isSubscriptionActive = active)
    }

    val currentUsage = requireNotNull(usage) { "Opus 订阅响应缺少 usage 状态" }
    require(currentUsage.percent >= 0) { "usage.percent 不能为负数" }
    require(currentUsage.timeUntilNextPercent >= 0) { "usage.timeUntilNextPercent 不能为负数" }
    return OpusBatteryState(
        tier = tier,
        isSubscriptionActive = true,
        batteryPercent = currentUsage.percent,
        isUsageUnavailable = currentUsage.isNegative,
        timeUntilNextPercentSeconds = currentUsage.timeUntilNextPercent,
    )
}

/** 将领域参数映射为官方 OpenAPI 的 v4_prompt / v4_negative_prompt 多角色结构。 */
@OptIn(ExperimentalEncodingApi::class)
internal fun GenerationParameters.toImageRequestPayload(stream: Boolean = false): ImageRequestPayload {
    val transparencyEnabled = transparentBackground && model.supportsTransparency
    val resolvedPrompt = if (transparencyEnabled) prompt.withTransparentBackgroundTag() else prompt
    val positiveCondition = characterPrompts.toCharacterCondition(resolvedPrompt) { it.prompt }
    val negativeCondition = characterPrompts.toCharacterCondition(negativePrompt) { it.uc }
    return ImageRequestPayload(
        input = resolvedPrompt,
        model = if (action == GenerationAction.INFILL) requireNotNull(model.inpaintingModelId) else model.id,
        action = action.wireValue,
        parameters = ImageParametersPayload(
            width = width,
            height = height,
            scale = scale,
            sampler = sampler.id,
            steps = steps,
            nSamples = nSamples,
            seed = seed.takeIf { it >= 0 } ?: NovelAIApiServiceImpl.randomSeed(),
            ucPreset = ucPreset,
            qualityToggle = qualityToggle,
            negativePrompt = negativePrompt,
            cfgRescale = cfgRescale,
            noiseSchedule = noiseSchedule.id,
            straightAlpha = transparencyEnabled,
            tagHintTransparentBackground = transparencyEnabled,
            v4Prompt = positiveCondition,
            v4NegativePrompt = negativeCondition,
            referenceImageMultiple = vibeReferences.map { Base64.encode(it.encoding) }.takeIf { it.isNotEmpty() },
            referenceStrengthMultiple = vibeReferences.map { it.referenceStrength }.takeIf { it.isNotEmpty() },
            referenceInformationExtractedMultiple = vibeReferences.map { it.informationExtracted }.takeIf { it.isNotEmpty() },
            image = sourceImage?.let { Base64.encode(it.bytes) },
            mask = maskImage?.let { Base64.encode(it.bytes) },
            strength = strength.takeIf { action == GenerationAction.IMG2IMG },
            noise = noise.takeIf { action == GenerationAction.IMG2IMG },
            img2img = if (action == GenerationAction.INFILL) Img2ImgParamsPayload(strength, noise) else null,
            stream = "sse".takeIf { stream },
            upscaledEnhance = operation == com.novelstudio.core.model.ImageOperation.ENHANCE && outputScale > 1f,
            smea = smea,
            smeaDyn = smeaDyn && smea,  // sm_dyn 要求 sm=true 才有效
            skipCfgAboveSigma = if (varietyPlus) 19.0f else null,
            dynamicThresholding = decrisper,
            uncondScale = uncondScale,
            normalizeReferenceStrengthMultiple = normalizeReferenceStrengthMultiple,
            inpaintImg2ImgStrength = if (action == GenerationAction.INFILL) inpaintImg2ImgStrength else null,
            controlnetCondition = controlnetCondition,
            controlnetModel = controlnetModel?.id,
        ),
    )
}

@OptIn(ExperimentalEncodingApi::class)
internal fun decodeImageResponse(bytes: ByteArray, contentType: String?, json: Json): List<GeneratedImageBytes> {
    require(bytes.isNotEmpty()) { "图像响应为空" }
    if (bytes.size >= 2 && bytes[0] == ZIP_MAGIC_BYTES[0] && bytes[1] == ZIP_MAGIC_BYTES[1]) {
        return extractImagesFromZip(bytes).mapIndexed { index, image ->
            GeneratedImageBytes(image, requireNotNull(image.detectImageMimeType()), index)
        }.also { require(it.isNotEmpty()) { "ZIP 响应中未找到有效图像" } }
    }
    bytes.detectImageMimeType()?.let { return listOf(GeneratedImageBytes(bytes, it)) }
    if (contentType?.contains("json", ignoreCase = true) == true || bytes.firstOrNull()?.toInt()?.toChar() == '{') {
        val payload = json.decodeFromString(ImageGenerationJsonResponsePayload.serializer(), bytes.decodeToString())
        return payload.images.map { image ->
            val decoded = Base64.decode(image.image.substringAfter("base64,", image.image))
            GeneratedImageBytes(
                bytes = decoded,
                mimeType = requireNotNull(decoded.detectImageMimeType()) { "JSON 图像签名无效" },
                index = image.index,
                seed = image.seed,
            )
        }.also { require(it.isNotEmpty()) { "JSON 响应中没有图像" } }
    }
    error("响应既不是 ZIP、JSON，也不是有效 PNG/WebP")
}

@OptIn(ExperimentalEncodingApi::class)
internal fun parseSseGenerationEvent(eventName: String?, data: String, json: Json): GenerationEvent {
    val normalizedType = eventName?.lowercase().orEmpty()
    val element = runCatching { json.parseToJsonElement(data) }.getOrNull()
    val payloadType = (element as? JsonObject)?.stringAt("type", "event", "event_type")?.lowercase().orEmpty()
    val type = normalizedType.ifBlank { payloadType }
    if (type.contains("error") || type.contains("fail")) {
        val message = (element as? JsonObject)?.stringAt("message", "error", "detail") ?: data
        return GenerationEvent.Failure(message)
    }
    val images = when (element) {
        is JsonObject -> element.extractImageElements().mapNotNull { image -> image.toGeneratedImageOrNull() }
        else -> emptyList()
    }.ifEmpty {
        runCatching {
            val decoded = Base64.decode(data.substringAfter("base64,", data))
            listOf(GeneratedImageBytes(decoded, requireNotNull(decoded.detectImageMimeType())))
        }.getOrDefault(emptyList())
    }
    if (images.isEmpty()) {
        // 服务端返回了帧但帧内容无效（区别于网络中断），submissionMayHaveCompleted=false 允许上层判断
        return GenerationEvent.Failure("SSE 图像帧没有有效 PNG/WebP 内容")
    }
    return if (type.contains("intermediate") || type.contains("preview")) {
        val first = images.first()
        GenerationEvent.Intermediate(first.bytes, first.mimeType, first.index)
    } else {
        GenerationEvent.Final(images)
    }
}

private fun JsonElement.extractSuggestionElements(): List<JsonElement> = when (this) {
    is JsonArray -> this
    is JsonObject -> when (val tags = this["tags"]) {
        is JsonArray -> tags
        is JsonObject -> listOf(tags)
        else -> emptyList()
    }
    else -> emptyList()
}

private fun JsonObject.extractImageElements(): List<JsonObject> {
    val nested = this["images"]
    if (nested is JsonArray) return nested.mapNotNull { it as? JsonObject }
    if (this["image"] != null || this["data"] != null) return listOf(this)
    val data = this["data"] as? JsonObject ?: return emptyList()
    return data.extractImageElements()
}

@OptIn(ExperimentalEncodingApi::class)
private fun JsonObject.toGeneratedImageOrNull(): GeneratedImageBytes? {
    val encoded = stringAt("image", "data", "base64") ?: return null
    val bytes = runCatching { Base64.decode(encoded.substringAfter("base64,", encoded)) }.getOrNull() ?: return null
    val mime = bytes.detectImageMimeType() ?: return null
    val index = this["index"]?.jsonPrimitive?.content?.toIntOrNull()
    val seed = this["seed"]?.jsonPrimitive?.content?.toLongOrNull()
    return GeneratedImageBytes(bytes, mime, index, seed)
}

private fun JsonObject.stringAt(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
    this[key]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
}

private val ZIP_MAGIC_BYTES = byteArrayOf(0x50, 0x4B)

private fun String.withTransparentBackgroundTag(): String = when {
    contains(TRANSPARENT_BACKGROUND_TAG, ignoreCase = true) -> this
    isBlank() -> TRANSPARENT_BACKGROUND_TAG
    else -> "$this, $TRANSPARENT_BACKGROUND_TAG"
}

private const val TRANSPARENT_BACKGROUND_TAG = "transparent background"

private fun List<V5Character>.toCharacterCondition(
    baseCaption: String,
    caption: (V5Character) -> String,
): CharacterConditionPayload? {
    if (isEmpty()) return null
    return CharacterConditionPayload(
        caption = CharacterCaptionPayload(
            baseCaption = baseCaption,
            characterCaptions = map { character ->
                CharacterCaptionEntryPayload(
                    characterCaption = caption(character),
                    centers = listOf(CharacterCenterPayload(character.centerX, character.centerY)),
                )
            },
        ),
        useCoords = true,
        useOrder = true,
    )
}
