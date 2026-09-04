package com.novelstudio.feature.inpaint

import androidx.compose.runtime.Composable

internal data class PickedMaskImage(val displayName: String, val bytes: ByteArray)

/** 只读取用户主动选择的 PNG 遮罩；尺寸和内容在提交前由领域层复核。 */
@Composable
internal expect fun rememberMaskImagePicker(
    onPicked: (PickedMaskImage) -> Unit,
    onError: (String) -> Unit,
): () -> Unit
