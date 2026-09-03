package com.novelstudio.feature.workbench

import androidx.compose.ui.graphics.ImageBitmap
import com.novelstudio.core.model.AspectPreset
import com.novelstudio.core.model.DispatchDecision
import com.novelstudio.core.model.GenerationParameters
import com.novelstudio.core.model.NaiModel
import com.novelstudio.core.model.OpusBatteryState

/** 工作台 UI 状态（StateFlow 单向数据流） */
data class WorkbenchUiState(
    val prompt: String = "",
    val negativePrompt: String = GenerationParameters.DEFAULT_NEGATIVE,
    val model: NaiModel = NaiModel.V5_FULL,
    val aspect: AspectPreset = AspectPreset.SQUARE,
    val width: Int = 1024,
    val height: Int = 1024,
    val steps: Int = 28,
    val scale: Float = 6f,
    val transparentBackground: Boolean = false,
    val explorationMode: Boolean = false,
    val isGenerating: Boolean = false,
    val battery: OpusBatteryState = OpusBatteryState(),
    val lastDecision: DispatchDecision? = null,
    val needsAnlasConfirmation: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null,
    val previewBitmap: ImageBitmap? = null,
) {
    val batteryLabel: String
        get() = if (battery.isOpus) "V5 电池 ${battery.batteryPercent.toInt()}%" else "非 Opus 订阅"

    fun parameters(seed: Long): GenerationParameters = GenerationParameters(
        prompt = prompt,
        negativePrompt = negativePrompt,
        model = model,
        width = width,
        height = height,
        steps = steps,
        scale = scale,
        transparentBackground = transparentBackground,
        seed = seed,
    )
}

/** PNG 字节解码为可预览位图（平台差异收口：Android=BitmapFactory，桌面=Skia） */
internal expect fun decodePngPreview(bytes: ByteArray): ImageBitmap?

/** 跨平台系统毫秒时钟（JVM / Android 同源实现） */
internal expect fun currentTimeMillis(): Long
