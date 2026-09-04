package com.novelstudio.feature.swipe

import kotlin.math.abs

internal enum class SwipeDecision { LIKE, DISLIKE, NONE }

internal fun swipeDecision(offset: Float, width: Float): SwipeDecision {
    if (width <= 0f) return SwipeDecision.NONE
    val threshold = width * 0.35f
    return when {
        offset >= threshold -> SwipeDecision.LIKE
        offset <= -threshold -> SwipeDecision.DISLIKE
        else -> SwipeDecision.NONE
    }
}

internal fun swipeProgress(offset: Float, width: Float): Float =
    if (width <= 0f) 0f else (offset / (width * 0.75f)).coerceIn(-1f, 1f)

internal fun swipeRotation(offset: Float): Float = (offset / 1200f * 15f).coerceIn(-15f, 15f)
