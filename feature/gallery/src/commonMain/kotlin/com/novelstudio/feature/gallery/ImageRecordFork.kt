package com.novelstudio.feature.gallery

import com.novelstudio.core.model.ImageRecord
import com.novelstudio.core.model.NaiModel
import com.novelstudio.core.model.WorkbenchDraft
import com.novelstudio.core.model.Sampler

/** PNG Info 回填只复制可复现生成的字段，不携带图库状态或文件路径。 */
internal fun ImageRecord.toWorkbenchDraft(updatedAt: Long): WorkbenchDraft = WorkbenchDraft(
    freePrompt = prompt,
    negativePrompt = uc,
    model = NaiModel.fromId(model),
    width = width,
    height = height,
    seed = seed,
    steps = steps,
    scale = scale,
    sampler = Sampler.fromId(sampler),
    updatedAt = updatedAt,
)
