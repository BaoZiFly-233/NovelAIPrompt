package com.novelstudio.core.network

import com.novelstudio.core.model.GenerationParameters
import com.novelstudio.core.model.GenerationAction
import com.novelstudio.core.model.GenerationInputImage
import com.novelstudio.core.model.ImageOperation
import com.novelstudio.core.model.SubscriptionTier
import com.novelstudio.core.model.NaiModel
import com.novelstudio.core.model.V5Character
import com.novelstudio.core.model.VibeReference
import com.novelstudio.core.network.dto.ImageRequestPayload
import com.novelstudio.core.network.dto.SubscriptionDto
import com.novelstudio.core.network.dto.UsageLimitDto
import com.novelstudio.core.network.dto.VibeEncodeRequestPayload
import com.novelstudio.core.network.dto.UpscaleRequestPayload
import com.novelstudio.core.network.dto.AugmentImageRequestPayload
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NovelAIApiPayloadTest {

    private val json = Json { encodeDefaults = true; explicitNulls = false }

    @Test
    fun `multi character request uses official v4 caption and center schema`() {
        val request = GenerationParameters(
            prompt = "2girls, outdoors",
            negativePrompt = "lowres",
            characterPrompts = listOf(
                V5Character("a", prompt = "girl, silver hair", uc = "blue hair", centerX = 0.25f, centerY = 0.5f),
                V5Character("b", prompt = "girl, black hair", uc = "red hair", centerX = 0.75f, centerY = 0.5f),
            ),
        ).toImageRequestPayload()

        val parameters = json.encodeToJsonElement(ImageRequestPayload.serializer(), request)
            .jsonObject.getValue("parameters").jsonObject
        assertFalse("characterPrompts" in parameters)

        val positive = parameters.getValue("v4_prompt").jsonObject
        assertEquals(true, positive.getValue("use_coords").jsonPrimitive.content.toBoolean())
        val positiveCaption = positive.getValue("caption").jsonObject
        assertEquals("2girls, outdoors", positiveCaption.getValue("base_caption").jsonPrimitive.content)
        val firstCharacter = positiveCaption.getValue("char_captions").jsonArray.first().jsonObject
        assertEquals("girl, silver hair", firstCharacter.getValue("char_caption").jsonPrimitive.content)
        val firstCenter = firstCharacter.getValue("centers").jsonArray.single().jsonObject
        assertEquals("0.25", firstCenter.getValue("x").jsonPrimitive.content)
        assertEquals("0.5", firstCenter.getValue("y").jsonPrimitive.content)
        assertFalse("width" in firstCenter)
        assertFalse("height" in firstCenter)

        val negativeCaption = parameters.getValue("v4_negative_prompt").jsonObject
            .getValue("caption").jsonObject
        assertEquals("blue hair", negativeCaption.getValue("char_captions").jsonArray.first()
            .jsonObject.getValue("char_caption").jsonPrimitive.content)
    }

    @Test
    fun `single prompt request omits multi character conditions`() {
        val request = GenerationParameters(prompt = "solo").toImageRequestPayload()
        val parameters = json.encodeToJsonElement(ImageRequestPayload.serializer(), request)
            .jsonObject.getValue("parameters").jsonObject

        assertFalse("v4_prompt" in parameters)
        assertFalse("v4_negative_prompt" in parameters)
    }

    @Test
    fun `vibe references map to official parallel arrays and empty arrays are omitted`() {
        val request = GenerationParameters(
            prompt = "vibe",
            vibeReferences = listOf(
                VibeReference(
                    id = "v1",
                    displayName = "portrait",
                    encoding = byteArrayOf(1, 2, 3),
                    referenceStrength = 0.7f,
                    informationExtracted = 0.8f,
                ),
            ),
        ).toImageRequestPayload()
        val parameters = json.encodeToJsonElement(ImageRequestPayload.serializer(), request)
            .jsonObject.getValue("parameters").jsonObject
        assertEquals("AQID", parameters.getValue("reference_image_multiple").jsonArray.single().jsonPrimitive.content)
        assertEquals("0.7", parameters.getValue("reference_strength_multiple").jsonArray.single().jsonPrimitive.content)
        assertEquals("0.8", parameters.getValue("reference_information_extracted_multiple").jsonArray.single().jsonPrimitive.content)

        val empty = json.encodeToJsonElement(ImageRequestPayload.serializer(), GenerationParameters().toImageRequestPayload())
            .jsonObject.getValue("parameters").jsonObject
        assertFalse("reference_image_multiple" in empty)
        assertFalse("reference_strength_multiple" in empty)
        assertFalse("reference_information_extracted_multiple" in empty)
    }

    @Test
    fun `vibe encoder request uses base64 image and official field name`() {
        val payload = byteArrayOf(1, 2, 3).toVibeEncodeRequestPayload(
            model = NaiModel.V5_FULL.id,
            informationExtracted = 0.75f,
        )
        val encoded = json.encodeToJsonElement(VibeEncodeRequestPayload.serializer(), payload).jsonObject

        assertEquals("AQID", encoded.getValue("image").jsonPrimitive.content)
        assertEquals(NaiModel.V5_FULL.id, encoded.getValue("model").jsonPrimitive.content)
        assertEquals("0.75", encoded.getValue("information_extracted").jsonPrimitive.content)
        assertFailsWith<IllegalArgumentException> {
            byteArrayOf().toVibeEncodeRequestPayload(NaiModel.V5_FULL.id, 1f)
        }
    }

    @Test
    fun `opus usage mapping preserves percent above one hundred`() {
        val state = SubscriptionDto(
            tier = 3,
            active = true,
            usage = UsageLimitDto(isNegative = false, percent = 127, timeUntilNextPercent = 42),
        ).toOpusBatteryState()

        assertEquals(SubscriptionTier.OPUS, state.tier)
        assertTrue(state.isOpus)
        assertTrue(state.canUseV5Allowance)
        assertEquals(127, state.batteryPercent)
        assertEquals(42, state.timeUntilNextPercentSeconds)
    }

    @Test
    fun `active opus without usage fails closed`() {
        assertFailsWith<IllegalArgumentException> {
            SubscriptionDto(tier = 3, active = true).toOpusBatteryState()
        }
    }

    @Test
    fun `negative usage is represented as unavailable`() {
        val state = SubscriptionDto(
            tier = 3,
            active = true,
            usage = UsageLimitDto(isNegative = true, percent = 0, timeUntilNextPercent = 300),
        ).toOpusBatteryState()

        assertFalse(state.canUseV5Allowance)
        assertTrue(state.isUsageUnavailable)
    }

    @Test
    fun `V5 transparency uses official alpha hints and prompt tag`() {
        val request = GenerationParameters(
            prompt = "1girl",
            model = NaiModel.V5_FULL,
            transparentBackground = true,
        ).toImageRequestPayload()
        val root = json.encodeToJsonElement(ImageRequestPayload.serializer(), request).jsonObject
        val parameters = root.getValue("parameters").jsonObject

        assertEquals("1girl, transparent background", root.getValue("input").jsonPrimitive.content)
        assertEquals("true", parameters.getValue("straight_alpha").jsonPrimitive.content)
        assertEquals("true", parameters.getValue("tag_hint_transparent_background").jsonPrimitive.content)
        assertEquals("png", parameters.getValue("image_format").jsonPrimitive.content)
        assertFalse("transparent_background" in parameters)
        assertFalse("uncond_scale" in parameters)
    }

    @Test
    fun `unsupported model never sends transparency hints`() {
        val request = GenerationParameters(
            prompt = "1girl",
            model = NaiModel.V4_5_FULL,
            transparentBackground = true,
        ).toImageRequestPayload()
        val root = json.encodeToJsonElement(ImageRequestPayload.serializer(), request).jsonObject
        val parameters = root.getValue("parameters").jsonObject

        assertEquals("1girl", root.getValue("input").jsonPrimitive.content)
        assertEquals("false", parameters.getValue("straight_alpha").jsonPrimitive.content)
        assertEquals("false", parameters.getValue("tag_hint_transparent_background").jsonPrimitive.content)
    }

    @Test
    fun `img2img and infill actions map source mask and streaming fields exactly`() {
        val source = GenerationInputImage(byteArrayOf(1, 2), 1024, 1024, "image/png")
        val mask = GenerationInputImage(byteArrayOf(3, 4), 1024, 1024, "image/png")
        val img2img = GenerationParameters(
            model = NaiModel.V4_5_FULL,
            action = GenerationAction.IMG2IMG,
            operation = ImageOperation.IMG2IMG,
            parentImageId = "parent",
            sourceImage = source,
            strength = 0.65f,
            noise = 0.2f,
        ).toImageRequestPayload(stream = true)
        val imgRoot = json.encodeToJsonElement(ImageRequestPayload.serializer(), img2img).jsonObject
        val imgParameters = imgRoot.getValue("parameters").jsonObject
        assertEquals("img2img", imgRoot.getValue("action").jsonPrimitive.content)
        assertEquals("AQI=", imgParameters.getValue("image").jsonPrimitive.content)
        assertEquals("0.65", imgParameters.getValue("strength").jsonPrimitive.content)
        assertEquals("0.2", imgParameters.getValue("noise").jsonPrimitive.content)
        assertEquals("sse", imgParameters.getValue("stream").jsonPrimitive.content)
        assertFalse("mask" in imgParameters)

        val infill = GenerationParameters(
            model = NaiModel.V4_5_FULL,
            action = GenerationAction.INFILL,
            operation = ImageOperation.INPAINT,
            parentImageId = "parent",
            sourceImage = source,
            maskImage = mask,
            strength = 0.8f,
            noise = 0.1f,
        ).toImageRequestPayload(stream = true)
        val infillRoot = json.encodeToJsonElement(ImageRequestPayload.serializer(), infill).jsonObject
        val infillParameters = infillRoot.getValue("parameters").jsonObject
        assertEquals("infill", infillRoot.getValue("action").jsonPrimitive.content)
        assertEquals("nai-diffusion-4-5-full-inpainting", infillRoot.getValue("model").jsonPrimitive.content)
        assertEquals("AwQ=", infillParameters.getValue("mask").jsonPrimitive.content)
        assertTrue("img2img" in infillParameters)
        assertFalse("strength" in infillParameters)
    }

    @Test
    fun `upscale and augment dto contain only swagger fields`() {
        val upscale = json.encodeToJsonElement(
            UpscaleRequestPayload.serializer(),
            UpscaleRequestPayload("AQI=", NaiModel.V4_5_FULL.id, 0.35f),
        ).jsonObject
        assertEquals(setOf("image", "model", "declared_blur_sigma"), upscale.keys)

        val augment = json.encodeToJsonElement(
            AugmentImageRequestPayload.serializer(),
            AugmentImageRequestPayload("AQI=", 1024, 1024, "emotion", "happy", 2),
        ).jsonObject
        assertEquals(setOf("image", "width", "height", "req_type", "prompt", "defry"), augment.keys)
    }
}
