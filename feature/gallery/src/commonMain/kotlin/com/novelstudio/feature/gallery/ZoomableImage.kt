package com.novelstudio.feature.gallery

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.novelstudio.core.designsystem.theme.expressiveSlowSpatialSpec
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 独立图片查看器：双击 1x/2.5x，捏合 1x-5x，放大后可拖拽并始终钳位在视口内。 */
@Composable
fun ZoomableImage(
    path: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    error: Painter? = null,
) {
    BoxWithConstraints(modifier.clipToBounds()) {
        var zoom by remember(path) { mutableFloatStateOf(MIN_VIEWER_ZOOM) }
        var offsetX by remember(path) { mutableFloatStateOf(0f) }
        var offsetY by remember(path) { mutableFloatStateOf(0f) }
        var settleJob by remember(path) { mutableStateOf<Job?>(null) }
        val scope = rememberCoroutineScope()
        val settleSpec: AnimationSpec<Float> = expressiveSlowSpatialSpec()
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        fun settle(targetZoom: Float) {
            val safeZoom = clampViewerZoom(targetZoom)
            val startZoom = zoom
            val startX = offsetX
            val startY = offsetY
            val targetX = clampViewerOffset(startX, safeZoom, widthPx)
            val targetY = clampViewerOffset(startY, safeZoom, heightPx)
            settleJob?.cancel()
            settleJob = scope.launch {
                animate(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = settleSpec,
                ) { progress, _ ->
                    zoom = clampViewerZoom(startZoom + (safeZoom - startZoom) * progress)
                    offsetX = clampViewerOffset(startX + (targetX - startX) * progress, zoom, widthPx)
                    offsetY = clampViewerOffset(startY + (targetY - startY) * progress, zoom, heightPx)
                }
                zoom = safeZoom
                offsetX = targetX
                offsetY = targetY
            }
        }

        LaunchedEffect(widthPx, heightPx) {
            offsetX = clampViewerOffset(offsetX, zoom, widthPx)
            offsetY = clampViewerOffset(offsetY, zoom, heightPx)
        }
        val transformState = rememberTransformableState { centroid, gestureZoom, pan, _ ->
            if (gestureZoom.isFinite() && gestureZoom > 0f) {
                settleJob?.cancel()
                val nextZoom = clampViewerZoom(zoom * gestureZoom)
                val scaleRatio = nextZoom / zoom
                val focusX = centroid.x.takeIf { it.isFinite() }?.minus(widthPx / 2f) ?: 0f
                val focusY = centroid.y.takeIf { it.isFinite() }?.minus(heightPx / 2f) ?: 0f
                zoom = nextZoom
                offsetX = clampViewerOffset(
                    offsetX * scaleRatio + focusX * (1f - scaleRatio) + pan.x,
                    nextZoom,
                    widthPx,
                )
                offsetY = clampViewerOffset(
                    offsetY * scaleRatio + focusY * (1f - scaleRatio) + pan.y,
                    nextZoom,
                    heightPx,
                )
            }
        }

        AsyncImage(
            model = localImageModel(path),
            contentDescription = contentDescription,
            contentScale = contentScale,
            error = error,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = offsetX; translationY = offsetY }
                .pointerInput(widthPx, heightPx) {
                    detectTapGestures(onDoubleTap = { settle(nextViewerDoubleTapZoom(zoom)) })
                }
                .transformable(transformState),
        )
    }
}

internal fun clampViewerZoom(value: Float): Float =
    if (value.isFinite()) value.coerceIn(MIN_VIEWER_ZOOM, MAX_VIEWER_ZOOM) else MIN_VIEWER_ZOOM

internal fun clampViewerOffset(value: Float, zoom: Float, viewport: Float): Float {
    if (!value.isFinite() || !viewport.isFinite() || viewport <= 0f) return 0f
    val maxOffset = (clampViewerZoom(zoom) - MIN_VIEWER_ZOOM) * viewport / 2f
    return value.coerceIn(-maxOffset, maxOffset)
}

internal fun nextViewerDoubleTapZoom(currentZoom: Float): Float =
    if (clampViewerZoom(currentZoom) > RESET_ZOOM_THRESHOLD) MIN_VIEWER_ZOOM else DOUBLE_TAP_VIEWER_ZOOM

internal const val MIN_VIEWER_ZOOM = 1f
internal const val MAX_VIEWER_ZOOM = 5f
private const val DOUBLE_TAP_VIEWER_ZOOM = 2.5f
private const val RESET_ZOOM_THRESHOLD = 1.1f
