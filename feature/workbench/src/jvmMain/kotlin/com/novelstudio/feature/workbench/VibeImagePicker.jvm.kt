package com.novelstudio.feature.workbench

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
internal actual fun rememberVibeImagePicker(
    onPicked: (PickedVibeImage) -> Unit,
    onError: (String) -> Unit,
): () -> Unit {
    val currentOnPicked = rememberUpdatedState(onPicked)
    val currentOnError = rememberUpdatedState(onError)
    val scope = rememberCoroutineScope()

    return remember(scope) {
        {
            val chooser = JFileChooser().apply {
                dialogTitle = "选择 Vibe Transfer 参考图"
                fileFilter = FileNameExtensionFilter("图片（PNG、JPEG、WebP）", "png", "jpg", "jpeg", "webp")
                isAcceptAllFileFilterUsed = false
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                val file = chooser.selectedFile
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) { file.readBytes() }
                    }.onSuccess { bytes ->
                        currentOnPicked.value(PickedVibeImage(file.name, bytes))
                    }.onFailure { throwable ->
                        currentOnError.value(throwable.message ?: "无法读取所选图片")
                    }
                }
            }
        }
    }
}
