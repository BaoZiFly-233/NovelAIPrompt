package com.novelstudio.feature.gallery

import com.novelstudio.core.model.ImageRecord
import com.novelstudio.core.model.NaiModel
import com.novelstudio.core.model.Sampler
import kotlin.test.Test
import kotlin.test.assertEquals

class ImageRecordForkTest {

    @Test
    fun pngInfoFieldsAreCopiedIntoWorkbenchDraft() {
        val record = ImageRecord(
            id = "image",
            filePath = "/image.png",
            prompt = "1girl, blue hair",
            uc = "lowres",
            model = NaiModel.V4_5_FULL.id,
            seed = 42,
            steps = 31,
            scale = 5.5f,
            sampler = Sampler.K_DPMPP_2M.id,
            width = 832,
            height = 1216,
        )

        val draft = record.toWorkbenchDraft(updatedAt = 99L)

        assertEquals(record.prompt, draft.freePrompt)
        assertEquals(record.uc, draft.negativePrompt)
        assertEquals(NaiModel.V4_5_FULL, draft.model)
        assertEquals(record.width, draft.width)
        assertEquals(record.height, draft.height)
        assertEquals(record.seed, draft.seed)
        assertEquals(record.steps, draft.steps)
        assertEquals(record.scale, draft.scale)
        assertEquals(Sampler.K_DPMPP_2M, draft.sampler)
        assertEquals(99L, draft.updatedAt)
    }
}
