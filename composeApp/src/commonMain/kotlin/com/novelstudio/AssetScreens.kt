package com.novelstudio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelstudio.core.model.ArtistString
import com.novelstudio.core.model.PersonalTag
import com.novelstudio.core.model.PromptAsset

@Composable
fun ArtistStringScreen(viewModel: AssetLibraryViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ArtistString?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    AssetPage(
        title = "画师串",
        description = "保存可复用的风格正/负串；一个画师串内部可以包含多个 artist token。",
        message = state.message,
        onAdd = { editing = null; showEditor = true },
        modifier = modifier,
    ) {
        items(state.artists, key = ArtistString::id) { asset ->
            AssetCard(asset.name, asset.positivePrompt, onEdit = { editing = asset; showEditor = true }, onDelete = { viewModel.deleteArtist(asset.id) })
        }
    }
    if (showEditor) {
        PromptLikeEditor(
            title = "画师串",
            initialName = editing?.name.orEmpty(),
            initialPositive = editing?.positivePrompt.orEmpty(),
            initialNegative = editing?.negativePrompt.orEmpty(),
            initialNotes = editing?.notes.orEmpty(),
            onDismiss = { showEditor = false },
            onSave = { name, positive, negative, notes ->
                viewModel.saveArtist(editing, name, positive, negative, notes)
                showEditor = false
            },
        )
    }
}

@Composable
fun PromptAssetScreen(viewModel: AssetLibraryViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<PromptAsset?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    AssetPage(
        title = "Prompt",
        description = "管理完整主 Prompt 模板；切换模板只替换工作台的主 Prompt 区段。",
        message = state.message,
        onAdd = { editing = null; showEditor = true },
        modifier = modifier,
    ) {
        items(state.prompts, key = PromptAsset::id) { asset ->
            AssetCard(asset.name, asset.positivePrompt, onEdit = { editing = asset; showEditor = true }, onDelete = { viewModel.deletePrompt(asset.id) })
        }
    }
    if (showEditor) {
        PromptLikeEditor(
            title = "Prompt",
            initialName = editing?.name.orEmpty(),
            initialPositive = editing?.positivePrompt.orEmpty(),
            initialNegative = editing?.negativePrompt.orEmpty(),
            initialNotes = editing?.notes.orEmpty(),
            onDismiss = { showEditor = false },
            onSave = { name, positive, negative, notes ->
                viewModel.savePrompt(editing, name, positive, negative, notes)
                showEditor = false
            },
        )
    }
}

@Composable
fun TagLibraryScreen(viewModel: AssetLibraryViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<PersonalTag?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    AssetPage(
        title = "Tag",
        description = "个人 Tag、官方建议缓存与最近使用统一按规范化值去重。",
        message = state.message,
        onAdd = { editing = null; showEditor = true },
        modifier = modifier,
    ) {
        items(state.tags, key = PersonalTag::id) { tag ->
            AssetCard(
                title = if (tag.isFavorite) "★ ${tag.displayValue}" else tag.displayValue,
                body = listOfNotNull(tag.groupName, tag.source.name).joinToString(" · "),
                onEdit = { editing = tag; showEditor = true },
                onDelete = { viewModel.deleteTag(tag.id) },
            )
        }
    }
    if (showEditor) {
        TagEditor(
            value = editing,
            onDismiss = { showEditor = false },
            onSave = { display, group, notes, favorite ->
                viewModel.saveTag(editing, display, group, notes, favorite)
                showEditor = false
            },
        )
    }
}

@Composable
private fun AssetPage(
    title: String,
    description: String,
    message: String?,
    onAdd: () -> Unit,
    modifier: Modifier,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().widthIn(max = 1000.dp).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onAdd) { Text("新建") }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize(), content = content)
    }
}

@Composable
private fun AssetCard(title: String, body: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onEdit) { Text("编辑") }
            TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun PromptLikeEditor(
    title: String,
    initialName: String,
    initialPositive: String,
    initialNegative: String,
    initialNotes: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var positive by remember(initialPositive) { mutableStateOf(initialPositive) }
    var negative by remember(initialNegative) { mutableStateOf(initialNegative) }
    var notes by remember(initialNotes) { mutableStateOf(initialNotes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑$title") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("名称") })
                OutlinedTextField(positive, { positive = it }, label = { Text("正向") }, minLines = 3)
                OutlinedTextField(negative, { negative = it }, label = { Text("负向") }, minLines = 2)
                OutlinedTextField(notes, { notes = it }, label = { Text("备注") })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, positive, negative, notes) }, enabled = name.isNotBlank() && positive.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun TagEditor(value: PersonalTag?, onDismiss: () -> Unit, onSave: (String, String, String, Boolean) -> Unit) {
    var display by remember(value) { mutableStateOf(value?.displayValue.orEmpty()) }
    var group by remember(value) { mutableStateOf(value?.groupName.orEmpty()) }
    var notes by remember(value) { mutableStateOf(value?.notes.orEmpty()) }
    var favorite by remember(value) { mutableStateOf(value?.isFavorite == true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑 Tag") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(display, { display = it }, label = { Text("Tag") })
                OutlinedTextField(group, { group = it }, label = { Text("分组（可选）") })
                OutlinedTextField(notes, { notes = it }, label = { Text("备注") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(favorite, { favorite = it })
                    Text("收藏")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(display, group, notes, favorite) }, enabled = display.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
