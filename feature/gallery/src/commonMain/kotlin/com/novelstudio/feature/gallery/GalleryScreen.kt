package com.novelstudio.feature.gallery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.automirrored.rounded.List as GalleryList
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import com.novelstudio.core.designsystem.components.StudioEmptyState
import com.novelstudio.core.designsystem.components.StudioPageHeader
import com.novelstudio.core.designsystem.components.StudioSpacing
import com.novelstudio.core.designsystem.components.StudioStatusChip
import com.novelstudio.core.model.ImageRecord
import kotlinx.coroutines.launch

/**
 * 万级虚拟瀑布流图库（MD3E 交互标准 §4-2）：
 * 自适应多列 StaggeredGrid、稳定元数据区、收藏/选择状态、灯箱与一键回填。
 */
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    modifier: Modifier = Modifier,
    onOpenCompare: () -> Unit = {},
    onOpenWorkbench: () -> Unit = {},
    onOpenOrganize: () -> Unit = {},
    onOpenImageTools: (String) -> Unit = {},
) {
    val records = viewModel.records.collectAsLazyPagingItems()
    var selected by remember { mutableStateOf<ImageRecord?>(null) }
    var multiSelect by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(GallerySelection.empty()) }
    var tagBindingTarget by remember { mutableStateOf<ImageRecord?>(null) }
    var tagBindingText by remember { mutableStateOf("") }
    val loadedIds = records.itemSnapshotList.items.map(ImageRecord::id)
    val snackbarHostState = remember { SnackbarHostState() }
    val launchExporter = rememberBatchImageExporter(
        onResult = viewModel::onExportResult,
        onError = viewModel::onExportError,
    )
    val currentLaunchExporter by rememberUpdatedState(launchExporter)
    val currentOnOpenCompare by rememberUpdatedState(onOpenCompare)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is GalleryEvent.Message -> {
                    if (event.clearSelection) selection = GallerySelection.empty()
                    launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(event.text)
                    }
                }
                is GalleryEvent.StartExport -> currentLaunchExporter(event.items)
                GalleryEvent.OpenCompare -> {
                    selection = GallerySelection.empty()
                    multiSelect = false
                    currentOnOpenCompare()
                }
                GalleryEvent.OpenWorkbench -> onOpenWorkbench()
                is GalleryEvent.Trashed -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "作品已移入 30 天垃圾箱",
                        actionLabel = "撤销",
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.restoreFromTrash(event.imageId)
                }
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val pagePadding = if (maxWidth < 600.dp) StudioSpacing.Large else StudioSpacing.XLarge
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1440.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = pagePadding),
            verticalArrangement = Arrangement.spacedBy(StudioSpacing.Medium),
        ) {
            StudioPageHeader(
                eyebrow = "LIBRARY",
                title = "万级图库",
                description = if (records.itemCount == 0) {
                    "让作品成为主角，生成结果与导入图片都会汇聚在这里。"
                } else {
                    "当前已加载 ${records.itemCount} 张作品，可批量整理、导出或送入对比。"
                },
                modifier = Modifier.padding(top = StudioSpacing.Large),
                actions = {
                    TextButton(onClick = onOpenOrganize) { Text("快速整理") }
                    FilledTonalButton(
                        onClick = {
                            multiSelect = !multiSelect
                            if (!multiSelect) selection = GallerySelection.empty()
                        },
                    ) {
                        Text(if (multiSelect) "完成" else "选择")
                    }
                },
            )

            AnimatedVisibility(visible = multiSelect) {
                GallerySelectionToolbar(
                    selection = selection,
                    loadedIds = loadedIds,
                    onToggleAll = {
                        selection = if (selection.containsAll(loadedIds)) {
                            GallerySelection.empty()
                        } else {
                            GallerySelection.from(loadedIds)
                        }
                    },
                    onLike = { viewModel.batchFavorite(selection.ids, true) },
                    onNeutral = { viewModel.batchArchive(selection.ids) },
                    onDislike = { viewModel.batchTrash(selection.ids) },
                    onExport = { viewModel.requestExport(selection.ids) },
                    onCompare = { viewModel.requestCompare(selection.ids) },
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    records.loadState.refresh is LoadState.Loading -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(StudioSpacing.Medium),
                        ) {
                            CircularProgressIndicator()
                            Text("正在整理图库…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    records.loadState.refresh is LoadState.Error -> {
                        StudioEmptyState(
                            icon = Icons.Rounded.Warning,
                            title = "图库暂时无法打开",
                            description = "本地索引读取失败，重试不会修改任何图片。",
                            actionLabel = "重新加载",
                            onAction = records::retry,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    records.loadState.refresh is LoadState.NotLoading && records.itemCount == 0 -> {
                        StudioEmptyState(
                            icon = Icons.AutoMirrored.Rounded.GalleryList,
                            title = "第一张作品在等你",
                            description = "生成或导入 PNG 后，图片和参数会自动出现在图库中。",
                            actionLabel = "开始创作",
                            onAction = onOpenWorkbench,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    else -> {
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Adaptive(200.dp),
                            horizontalArrangement = Arrangement.spacedBy(StudioSpacing.Medium),
                            verticalItemSpacing = StudioSpacing.Medium,
                            contentPadding = PaddingValues(bottom = StudioSpacing.XLarge),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(
                                count = records.itemCount,
                                key = records.itemKey { it.id },
                                contentType = records.itemContentType { "gallery-card" },
                            ) { index ->
                                val record = records[index] ?: return@items
                                GalleryCard(
                                    record = record,
                                    selected = selection.contains(record.id),
                                    multiSelect = multiSelect,
                                    onClick = {
                                        if (multiSelect) {
                                            selection = selection.toggle(record.id)
                                        } else {
                                            selected = record
                                        }
                                    },
                                    onToggleStar = { viewModel.toggleLike(record) },
                                )
                            }
                            when (records.loadState.append) {
                                is LoadState.Loading -> item { Text("加载更多…", modifier = Modifier.padding(16.dp)) }
                                is LoadState.Error -> item { TextButton(onClick = records::retry) { Text("加载失败，重试") } }
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    selected?.let { record ->
        LightboxDialog(
            record = record,
            onDismiss = { selected = null },
            onFork = {
                viewModel.forkToWorkbench(record)
                selected = null
            },
            onToggleStar = { viewModel.toggleLike(record) },
            onDelete = {
                viewModel.markDislike(record)
                selected = null
            },
            onSaveArtist = { viewModel.saveAsArtistString(record) },
            onSavePrompt = { viewModel.saveAsPrompt(record) },
            onBindTags = {
                tagBindingTarget = record
                tagBindingText = ""
            },
            onImageTools = {
                selected = null
                onOpenImageTools(record.id)
            },
        )
    }

    tagBindingTarget?.let { record ->
        AlertDialog(
            onDismissRequest = { tagBindingTarget = null },
            title = { Text("绑定 Tag") },
            text = {
                OutlinedTextField(
                    value = tagBindingText,
                    onValueChange = { tagBindingText = it },
                    label = { Text("用英文逗号分隔，顺序会被保留") },
                    minLines = 3,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.bindTags(record, tagBindingText)
                        tagBindingTarget = null
                    },
                    enabled = tagBindingText.isNotBlank(),
                ) { Text("绑定") }
            },
            dismissButton = { TextButton(onClick = { tagBindingTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun GallerySelectionToolbar(
    selection: GallerySelection,
    loadedIds: List<String>,
    onToggleAll: () -> Unit,
    onLike: () -> Unit,
    onNeutral: () -> Unit,
    onDislike: () -> Unit,
    onExport: () -> Unit,
    onCompare: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = StudioSpacing.Medium, vertical = StudioSpacing.Small),
            horizontalArrangement = Arrangement.spacedBy(StudioSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StudioStatusChip(
                text = "已选 ${selection.size}",
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
            TextButton(onClick = onToggleAll, enabled = loadedIds.isNotEmpty()) {
                Text(if (loadedIds.isNotEmpty() && selection.containsAll(loadedIds)) "取消全选" else "全选已加载")
            }
            if (!selection.isEmpty) {
                TextButton(onClick = onLike) { Text("喜欢") }
                TextButton(onClick = onNeutral) { Text("普通") }
                TextButton(onClick = onDislike) { Text("不喜欢") }
                TextButton(onClick = onExport) { Text("导出") }
                Button(onClick = onCompare) { Text("加入对比") }
            }
        }
    }
}

/** 图库作品卡：图片优先，元数据收进固定信息区；网格只加载平台缩略图避免 OOM。 */
@Composable
private fun GalleryCard(record: ImageRecord, selected: Boolean = false, multiSelect: Boolean = false, onClick: () -> Unit, onToggleStar: () -> Unit) {
    val liked = record.isFavorite
    val aspectRatio = safeThumbnailAspectRatio(record)
    val shape = MaterialTheme.shapes.medium
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource),
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = when {
                selected -> MaterialTheme.colorScheme.primary
                hovered -> MaterialTheme.colorScheme.outline
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            },
        ),
        tonalElevation = if (hovered) 3.dp else 0.dp,
    ) {
        Column(
            modifier = Modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        ) {
            // 网格永远只读取缩略图；原图仅在灯箱中按需打开，避免万级图库占用过多内存。
            val model = record.thumbnailPath
                .takeIf { it.isNotBlank() && !it.startsWith("pending") }
                ?.let(::localImageModel)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                if (model == null) {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(StudioSpacing.Large),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(StudioSpacing.Small),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.GalleryList,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "图片正在落盘",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    AsyncImage(
                        model = model,
                        contentDescription = record.prompt,
                        contentScale = ContentScale.Crop,
                        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceContainerHigh),
                        error = ColorPainter(MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                if (multiSelect) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(StudioSpacing.Small).size(32.dp),
                        shape = CircleShape,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.scrim.copy(alpha = 0.48f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = if (selected) "已选择" else "未选择",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(StudioSpacing.Small).size(36.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.52f),
                ) {
                    IconButton(onClick = onToggleStar) {
                        Icon(
                            imageVector = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = if (liked) "取消喜欢" else "设为喜欢",
                            tint = if (liked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(StudioSpacing.Medium),
                verticalArrangement = Arrangement.spacedBy(StudioSpacing.Small),
            ) {
                Text(
                    text = record.prompt.ifBlank { "无提示词" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StudioStatusChip(
                        text = record.model.removePrefix("nai-diffusion-").ifBlank { "未知模型" },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Seed ${record.seed}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 灯箱：大图 + PNG Info 侧栏 + 「一键回填到工作台」 */
@Composable
private fun LightboxDialog(
    record: ImageRecord,
    onDismiss: () -> Unit,
    onFork: () -> Unit,
    onToggleStar: () -> Unit,
    onDelete: () -> Unit,
    onSaveArtist: () -> Unit,
    onSavePrompt: () -> Unit,
    onBindTags: () -> Unit,
    onImageTools: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            val compact = isCompactLightbox(maxWidth)
            val contentPadding = if (compact) 12.dp else 24.dp
            if (compact) {
                Column(Modifier.fillMaxSize().padding(contentPadding)) {
                    LightboxImage(record = record, modifier = Modifier.weight(1f).fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    ImageInfoPanel(
                        record = record,
                        onDismiss = onDismiss,
                        onFork = onFork,
                        onToggleStar = onToggleStar,
                        onDelete = onDelete,
                        onSaveArtist = onSaveArtist,
                        onSavePrompt = onSavePrompt,
                        onBindTags = onBindTags,
                        onImageTools = onImageTools,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 320.dp),
                    )
                }
            } else {
                Row(Modifier.fillMaxSize().padding(contentPadding)) {
                    LightboxImage(record = record, modifier = Modifier.weight(1f).fillMaxSize())
                    Spacer(Modifier.width(24.dp))
                    ImageInfoPanel(
                        record = record,
                        onDismiss = onDismiss,
                        onFork = onFork,
                        onToggleStar = onToggleStar,
                        onDelete = onDelete,
                        onSaveArtist = onSaveArtist,
                        onSavePrompt = onSavePrompt,
                        onBindTags = onBindTags,
                        onImageTools = onImageTools,
                        modifier = Modifier.width(320.dp).fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun LightboxImage(record: ImageRecord, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        val path = record.filePath.takeIf { it.isNotBlank() && !it.startsWith("pending") }
        if (path == null) {
            Text("原图文件不可用", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            ZoomableImage(
                path = path,
                contentDescription = record.prompt,
                contentScale = ContentScale.Fit,
                error = ColorPainter(MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxSize(),
            )
            Text(
                "双击缩放 · 放大后拖拽平移",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            )
        }
    }
}

@Composable
private fun ImageInfoPanel(
    record: ImageRecord,
    onDismiss: () -> Unit,
    onFork: () -> Unit,
    onToggleStar: () -> Unit,
    onDelete: () -> Unit,
    onSaveArtist: () -> Unit,
    onSavePrompt: () -> Unit,
    onBindTags: () -> Unit,
    onImageTools: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("PNG Info", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "关闭") }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetadataRow("Prompt", record.prompt)
            MetadataRow("Negative", record.uc)
            MetadataRow("Model", record.model)
            MetadataRow("Seed", record.seed.toString())
            MetadataRow("Steps", record.steps.toString())
            MetadataRow("Scale", record.scale.toString())
            MetadataRow("Sampler", record.sampler)
            MetadataRow("尺寸", "${record.width} × ${record.height}")
            if (record.rawMetadataJson.isNotBlank()) MetadataRow("Raw metadata", record.rawMetadataJson)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        ) {
            Button(onClick = onFork, shape = MaterialTheme.shapes.medium) {
                Icon(Icons.Rounded.Star, contentDescription = null)
                Text("回填到工作台", modifier = Modifier.padding(start = 8.dp))
            }
            TextButton(onClick = onToggleStar) {
                Icon(
                    if (record.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = null,
                )
                Text(if (record.isFavorite) "取消喜欢" else "标记喜欢", modifier = Modifier.padding(start = 6.dp))
            }
            TextButton(onClick = onSaveArtist) { Text("保存为画师串") }
            TextButton(onClick = onSavePrompt) { Text("保存为 Prompt") }
            TextButton(onClick = onBindTags) { Text("绑定 Tag") }
            TextButton(onClick = onImageTools) { Text("图像工具") }
            TextButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text("移入垃圾箱", modifier = Modifier.padding(start = 6.dp), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

internal fun safeThumbnailAspectRatio(record: ImageRecord): Float =
    (record.width.coerceAtLeast(1).toFloat() / record.height.coerceAtLeast(1))
        .coerceIn(MIN_CARD_ASPECT_RATIO, MAX_CARD_ASPECT_RATIO)

internal fun isCompactLightbox(width: Dp): Boolean = width < LIGHTBOX_COMPACT_BREAKPOINT

private const val MIN_CARD_ASPECT_RATIO = 0.25f
private const val MAX_CARD_ASPECT_RATIO = 4f
private val LIGHTBOX_COMPACT_BREAKPOINT = 720.dp

@Composable
private fun MetadataRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}
