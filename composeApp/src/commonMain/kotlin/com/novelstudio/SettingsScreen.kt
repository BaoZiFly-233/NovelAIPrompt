package com.novelstudio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.novelstudio.core.designsystem.components.ChipSemantic
import com.novelstudio.core.designsystem.components.StudioConfirmDialog
import com.novelstudio.core.designsystem.components.StudioPageHeader
import com.novelstudio.core.designsystem.components.StudioSection
import com.novelstudio.core.designsystem.components.StudioSkeleton
import com.novelstudio.core.designsystem.components.StudioSpacing
import com.novelstudio.core.designsystem.components.StudioStatusChip
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** 设置页：NovelAI API Token 安全存取（DataStore Preferences） */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val settings = koinInject<com.novelstudio.di.SettingsStore>()
    val scope = rememberCoroutineScope()

    var token by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var tokenVisible by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<Pair<String, ChipSemantic>?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    // token 格式校验：pst- 前缀 + 非空
    val tokenError: String? = when {
        !loaded -> null
        token.isBlank() -> null
        !token.trimStart().startsWith("pst-") -> "Token 应以 pst- 开头"
        token.trim().length < 20 -> "Token 长度似乎太短"
        else -> null
    }

    LaunchedEffect(Unit) {
        if (!loaded) {
            token = settings.readToken().orEmpty()
            loaded = true
        }
    }

    if (showClearConfirm) {
        StudioConfirmDialog(
            title = "清除 Token",
            body = "确认后将删除本地保存的 API Token，需要重新输入才能继续使用生成功能。",
            confirmLabel = "清除",
            confirmIsDestructive = true,
            onConfirm = {
                showClearConfirm = false
                scope.launch {
                    settings.writeToken("")
                    token = ""
                    saveMessage = "Token 已清除" to ChipSemantic.WARNING
                }
            },
            onDismiss = { showClearConfirm = false },
        )
    }

    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(StudioSpacing.XLarge),
            verticalArrangement = Arrangement.spacedBy(StudioSpacing.Large),
        ) {
            StudioPageHeader(
                eyebrow = "PREFERENCES",
                title = "设置",
            )

            StudioSection(
                title = "NovelAI 连接",
            ) {
                if (!loaded) {
                    // 加载中显示骨架屏，避免空串闪烁
                    StudioSkeleton(modifier = Modifier.fillMaxWidth().height(56.dp))
                    StudioSkeleton(modifier = Modifier.fillMaxWidth(0.5f).height(40.dp))
                } else {
                    OutlinedTextField(
                        value = token,
                        onValueChange = {
                            token = it
                            saveMessage = null  // 内容变动后清除旧反馈
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("NovelAI API Token") },
                        placeholder = { Text("pst-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx") },
                        shape = MaterialTheme.shapes.medium,
                        singleLine = true,
                        isError = tokenError != null,
                        supportingText = tokenError?.let { err -> { Text(err) } },
                        visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { tokenVisible = !tokenVisible }) {
                                Icon(
                                    if (tokenVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (tokenVisible) "隐藏 Token" else "显示 Token",
                                )
                            }
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(StudioSpacing.Small)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        settings.writeToken(token.trim())
                                        saveMessage = "Token 已保存" to ChipSemantic.SUCCESS
                                    } catch (e: Exception) {
                                        saveMessage = "保存失败：${e.message}" to ChipSemantic.ERROR
                                    }
                                }
                            },
                            enabled = tokenError == null,
                            shape = MaterialTheme.shapes.medium,
                        ) { Text("保存 Token") }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val saved = settings.readToken()
                                    saveMessage = if (saved.isNullOrBlank()) {
                                        "当前未配置 Token" to ChipSemantic.WARNING
                                    } else {
                                        "已配置 Token（${saved.take(8)}…）" to ChipSemantic.INFO
                                    }
                                }
                            },
                            shape = MaterialTheme.shapes.medium,
                        ) { Text("检查状态") }

                        OutlinedButton(
                            onClick = { showClearConfirm = true },
                            enabled = token.isNotBlank(),
                            shape = MaterialTheme.shapes.medium,
                        ) { Text("清除") }
                    }
                    saveMessage?.let { (msg, semantic) ->
                        StudioStatusChip(text = msg, semantic = semantic)
                    }
                }
            }

            StudioSection(
                title = "隐私说明",
            ) {
                Text(
                    "客户端直接连接 NovelAI，不经过中转服务器。生成参数和图片记录保存在本地。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 崩溃日志回溯：无需 USB 调试即可把闪退堆栈带回来
            val crash = CrashReporter.latestCrash()
            if (crash != null) StudioSection(title = "诊断信息") {
                Text("检测到上次异常退出", color = MaterialTheme.colorScheme.error)
                Text(crash, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 8)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
