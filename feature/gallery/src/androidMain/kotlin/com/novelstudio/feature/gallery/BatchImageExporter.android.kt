package com.novelstudio.feature.gallery

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberBatchImageExporter(
    onResult: (GalleryExportResult) -> Unit,
    onError: (String) -> Unit,
): (List<GalleryExportItem>) -> Unit {
    val context = LocalContext.current
    val currentOnResult = rememberUpdatedState(onResult)
    val currentOnError = rememberUpdatedState(onError)
    val scope = rememberCoroutineScope()
    var pendingItems by remember { mutableStateOf<List<GalleryExportItem>>(emptyList()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        val items = pendingItems
        pendingItems = emptyList()
        if (treeUri == null || items.isEmpty()) return@rememberLauncherForActivityResult

        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { exportImagesToTree(context, treeUri, items) }
            }.onSuccess(currentOnResult.value)
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    currentOnError.value(throwable.message ?: "无法导出所选图片")
                }
        }
    }

    return remember(launcher) {
        { items ->
            if (items.isEmpty()) {
                currentOnError.value("请先选择要导出的图片")
            } else {
                pendingItems = items
                launcher.launch(null)
            }
        }
    }
}

private fun exportImagesToTree(
    context: Context,
    treeUri: Uri,
    items: List<GalleryExportItem>,
): GalleryExportResult {
    val resolver = context.contentResolver
    val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
    val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId)
    val occupiedNames = runCatching { queryChildNames(resolver, treeUri, treeDocumentId) }
        .getOrDefault(mutableSetOf())
    val failures = mutableListOf<GalleryExportFailure>()
    var exportedCount = 0

    items.forEachIndexed { index, item ->
        val source = File(item.sourcePath)
        if (!source.isFile) {
            failures += GalleryExportFailure(item.id, "原图文件不存在或不是普通文件")
            return@forEachIndexed
        }
        val targetName = nextAvailableExportName(exportFileName(item.id, index), occupiedNames)
        var createdUri: Uri? = null
        runCatching {
            val documentUri = DocumentsContract.createDocument(resolver, parentUri, "image/png", targetName)
                ?: error("目标存储提供方拒绝创建文件")
            createdUri = documentUri
            resolver.openOutputStream(documentUri, "w")?.use { output ->
                source.inputStream().buffered().use { input -> input.copyTo(output) }
            } ?: error("无法打开导出目标")
        }.onSuccess {
            occupiedNames += targetName
            exportedCount++
        }.onFailure { throwable ->
            createdUri?.let { uri -> runCatching { DocumentsContract.deleteDocument(resolver, uri) } }
            failures += GalleryExportFailure(item.id, throwable.message ?: "复制失败")
        }
    }

    return GalleryExportResult(
        exportedCount = exportedCount,
        failures = failures,
        destinationLabel = "所选 Android 文档目录",
    )
}

private fun queryChildNames(
    resolver: ContentResolver,
    treeUri: Uri,
    treeDocumentId: String,
): MutableSet<String> {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId)
    val names = mutableSetOf<String>()
    resolver.query(
        childrenUri,
        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        while (nameColumn >= 0 && cursor.moveToNext()) {
            cursor.getString(nameColumn)?.let(names::add)
        }
    }
    return names
}
