package com.novelstudio.feature.gallery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.JFileChooser

@Composable
internal actual fun rememberBatchImageExporter(
    onResult: (GalleryExportResult) -> Unit,
    onError: (String) -> Unit,
): (List<GalleryExportItem>) -> Unit {
    val currentOnResult = rememberUpdatedState(onResult)
    val currentOnError = rememberUpdatedState(onError)
    val scope = rememberCoroutineScope()

    return remember(scope) {
        { items ->
            if (items.isEmpty()) {
                currentOnError.value("请先选择要导出的图片")
            } else {
                val chooser = JFileChooser().apply {
                    dialogTitle = "选择原图导出目录"
                    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                    isAcceptAllFileFilterUsed = false
                }
                if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                    val destination = chooser.selectedFile
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                exportImagesToDirectory(items, destination)
                            }
                        }.onSuccess(currentOnResult.value)
                            .onFailure { throwable ->
                                if (throwable is CancellationException) throw throwable
                                currentOnError.value(throwable.message ?: "无法导出所选图片")
                            }
                    }
                }
            }
        }
    }
}

internal fun exportImagesToDirectory(
    items: List<GalleryExportItem>,
    destination: File,
): GalleryExportResult {
    Files.createDirectories(destination.toPath())
    require(destination.isDirectory) { "导出目标不是目录" }

    val destinationPath = destination.toPath().toAbsolutePath().normalize()
    val occupiedNames = destination.list()?.toMutableSet() ?: mutableSetOf()
    val failures = mutableListOf<GalleryExportFailure>()
    var exportedCount = 0

    items.forEachIndexed { index, item ->
        val source = File(item.sourcePath)
        if (!source.isFile) {
            failures += GalleryExportFailure(item.id, "原图文件不存在或不是普通文件")
            return@forEachIndexed
        }

        var targetName = nextAvailableExportName(exportFileName(item.id, index), occupiedNames)
        var copied = false
        while (!copied) {
            val target = destinationPath.resolve(targetName).normalize()
            if (target.parent != destinationPath) {
                failures += GalleryExportFailure(item.id, "导出文件名越过目标目录")
                break
            }
            try {
                Files.copy(source.toPath(), target)
                occupiedNames += targetName
                exportedCount++
                copied = true
            } catch (_: FileAlreadyExistsException) {
                occupiedNames += targetName
                targetName = nextAvailableExportName(exportFileName(item.id, index), occupiedNames)
            } catch (throwable: Exception) {
                val cleanupFailure = runCatching { Files.deleteIfExists(target) }.exceptionOrNull()
                val reason = buildString {
                    append(throwable.message ?: "复制失败")
                    if (cleanupFailure != null) append("；未能清理不完整目标：${cleanupFailure.message ?: "未知错误"}")
                }
                failures += GalleryExportFailure(item.id, reason)
                break
            }
        }
    }

    return GalleryExportResult(
        exportedCount = exportedCount,
        failures = failures,
        destinationLabel = destination.absolutePath,
    )
}
