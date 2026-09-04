package com.novelstudio.feature.gallery

import androidx.compose.runtime.Composable

internal data class GalleryExportItem(
    val id: String,
    val sourcePath: String,
)

internal data class GalleryExportFailure(
    val id: String,
    val reason: String,
)

internal data class GalleryExportResult(
    val exportedCount: Int,
    val failures: List<GalleryExportFailure>,
    val destinationLabel: String,
)

/** 用户主动选择目标目录后复制原图；导出绝不移动或删除图库源文件。 */
@Composable
internal expect fun rememberBatchImageExporter(
    onResult: (GalleryExportResult) -> Unit,
    onError: (String) -> Unit,
): (List<GalleryExportItem>) -> Unit

internal fun exportFileName(id: String, index: Int): String {
    val safeId = id
        .map { character ->
            when (character) {
                in 'a'..'z', in 'A'..'Z', in '0'..'9', '-', '_' -> character
                else -> '_'
            }
        }
        .joinToString("")
        .trim('_', '.')
        .take(MAX_EXPORT_ID_LENGTH)
        .ifBlank { "image-${index + 1}" }
    return "novelai-$safeId.png"
}

internal fun nextAvailableExportName(baseName: String, occupiedNames: Set<String>): String {
    val occupied = occupiedNames.mapTo(hashSetOf()) { it.lowercase() }
    if (baseName.lowercase() !in occupied) return baseName
    val stem = baseName.removeSuffix(".png")
    var suffix = 1
    while (true) {
        val candidate = "$stem ($suffix).png"
        if (candidate.lowercase() !in occupied) return candidate
        suffix++
    }
}

private const val MAX_EXPORT_ID_LENGTH = 64
