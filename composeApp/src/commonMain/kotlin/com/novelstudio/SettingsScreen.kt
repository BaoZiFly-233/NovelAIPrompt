package com.novelstudio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.novelstudio.core.designsystem.theme.MD3EPillShape
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.core.qualifier.named

/** 设置页：NovelAI API Token 安全存取（DataStore Preferences） */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val settings = koinInject<com.novelstudio.di.SettingsStore>()
    val scope = rememberCoroutineScope()

    var token by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!loaded) {
            token = settings.readToken().orEmpty()
            loaded = true
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("NovelAI API Token") },
            placeholder = { Text("pst-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx") },
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
        )

        Text(
            "Token 仅保存在本机应用隔离目录（DataStore Preferences），请求时以 Bearer 方式直连 NovelAI API，不经过任何第三方服务器。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        settings.writeToken(token.trim())
                        message = "已保存 Token"
                    }
                },
                shape = MD3EPillShape,
            ) { Text("保存 Token") }

            Button(
                onClick = {
                    scope.launch {
                        val saved = settings.readToken()
                        message = if (saved.isNullOrBlank()) "当前未配置 Token" else "已配置 Token（前 8 位：${saved.take(8)}…）"
                    }
                },
                shape = MD3EPillShape,
            ) { Text("读取状态") }
        }

        message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }

        // 崩溃日志回溯：无需 USB 调试即可把闪退堆栈带回来
        val crash = CrashReporter.latestCrash()
        if (crash != null) {
            Text(
                "检测到上次异常退出（请把以下内容反馈给开发者）",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                crash,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}
