package com.novelstudio.feature.workbench

import androidx.compose.runtime.Composable

internal data class PickedVibeImage(
    val displayName: String,
    val bytes: ByteArray,
)

/** 平台图片选择器；只读取用户主动选择的本地文件，不在此处发起网络请求。 */
@Composable
internal expect fun rememberVibeImagePicker(
    onPicked: (PickedVibeImage) -> Unit,
    onError: (String) -> Unit,
): () -> Unit
