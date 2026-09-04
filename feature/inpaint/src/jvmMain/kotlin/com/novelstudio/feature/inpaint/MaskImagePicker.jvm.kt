package com.novelstudio.feature.inpaint

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
internal actual fun rememberMaskImagePicker(
    onPicked: (PickedMaskImage) -> Unit,
    onError: (String) -> Unit,
): () -> Unit {
    val currentOnPicked = rememberUpdatedState(onPicked)
    val currentOnError = rememberUpdatedState(onError)
    val scope = rememberCoroutineScope()
    return remember(scope) {
        {
            val chooser = JFileChooser().apply {
                dialogTitle = "选择与原图同尺寸的 PNG 遮罩"
                fileFilter = FileNameExtensionFilter("PNG 遮罩", "png")
                isAcceptAllFileFilterUsed = false
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                val file = chooser.selectedFile
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { file.readBytes() } }
                        .onSuccess { currentOnPicked.value(PickedMaskImage(file.name, it)) }
                        .onFailure { currentOnError.value(it.message ?: "无法读取遮罩") }
                }
            }
        }
    }
}
