package com.novelstudio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.novelstudio.core.designsystem.components.StudioSpacing
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * 首次启动引导页：Token 为空时展示，完成配置后由 App 弹出此页并进入 Gallery。
 * 复用 SettingsStore，不引入新 ViewModel，保持 DRY。
 */
@Composable
fun OnboardingScreen(onComplete: () -> Unit, modifier: Modifier = Modifier) {
    val settings = koinInject<com.novelstudio.di.SettingsStore>()
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .padding(StudioSpacing.XLarge),
            verticalArrangement = Arrangement.spacedBy(StudioSpacing.Large),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                modifier = Modifier.padding(bottom = StudioSpacing.Small),
                tint = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = "NovelAI Diffusion Studio",
                style = MaterialTheme.typography.headlineMedium,
            )

            Text(
                text = "本客户端直接连接 NovelAI，不经过中转服务器。" +
                    "凭证只保存在这台设备上，不会写入图库或诊断日志。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("NovelAI API Token") },
                placeholder = { Text("pst-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx") },
                shape = MaterialTheme.shapes.medium,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )

            Button(
                onClick = {
                    val trimmed = token.trim()
                    if (trimmed.isBlank()) return@Button
                    scope.launch {
                        settings.writeToken(trimmed)
                        onComplete()
                    }
                },
                enabled = token.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text("开始使用")
            }
        }
    }
}
