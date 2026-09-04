package com.novelstudio.feature.workbench

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.novelstudio.core.designsystem.components.StudioIcons
import com.novelstudio.core.designsystem.components.StudioSpacing
import com.novelstudio.core.designsystem.components.StudioStatusChip
import com.novelstudio.core.designsystem.motion.MD3EMotion

private enum class PreviewState { EMPTY, GENERATING, HAS_RESULT }

/** 统一承载空态、生成中和结果，避免预览被埋在表单底部。 */
@Composable
internal fun PreviewStage(
    previewBitmap: ImageBitmap?,
    isGenerating: Boolean,
    resolutionLabel: String,
    modifier: Modifier = Modifier,
) {
    val state = when {
        previewBitmap != null -> PreviewState.HAS_RESULT
        isGenerating -> PreviewState.GENERATING
        else -> PreviewState.EMPTY
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 300.dp, max = 620.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f)),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    when (targetState) {
                        PreviewState.HAS_RESULT ->
                            (slideInVertically { it / 3 } + fadeIn(MD3EMotion.EmphasizedEasing)) togetherWith
                                fadeOut(MD3EMotion.StandardEasing)
                        else ->
                            fadeIn(MD3EMotion.StandardEasing) togetherWith fadeOut(MD3EMotion.StandardEasing)
                    }
                },
                label = "preview-state",
            ) { currentState ->
                when (currentState) {
                    PreviewState.HAS_RESULT -> Image(
                        bitmap = previewBitmap!!,
                        contentDescription = "生成预览",
                        modifier = Modifier.fillMaxSize().padding(StudioSpacing.Medium),
                        contentScale = ContentScale.Fit,
                    )
                    PreviewState.GENERATING -> Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(StudioSpacing.Medium),
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text("正在生成", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "完成后会在这里呈现，并自动写入图库。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    PreviewState.EMPTY -> Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(StudioSpacing.Small),
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                contentColor = MaterialTheme.colorScheme.primary,
                            ) {
                                Icon(
                                    StudioIcons.Brand,
                                    contentDescription = null,
                                    modifier = Modifier.padding(StudioSpacing.Large),
                                )
                            }
                            Text("画布已就绪", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "写下画面，再从生成按钮开始。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            StudioStatusChip(
                text = resolutionLabel,
                modifier = Modifier.align(Alignment.TopStart).padding(StudioSpacing.Medium),
                containerColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.46f),
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
    }
}
