package com.novelstudio.core.model

import kotlinx.serialization.Serializable

/**
 * ControlNet 条件引导模型（/ai/generate-controlnet-mask 的 model 参数）。
 * 生成 mask 后以 controlnet_condition 字段传入 /ai/generate-image。
 */
@Serializable
enum class ControlNetModel(val id: String, val displayName: String, val description: String) {
    HED("hed", "Palette Swap", "颜色/色调引导，保留构图的同时改变配色"),
    MIDAS("midas", "Form Lock", "深度图引导，锁定物体形态与空间位置"),
    FAKE_SCRIBBLE("fake_scribble", "Scribbler", "涂鸦/草图引导，从粗线稿生成精细图像"),
    MLSD("mlsd", "Building Control", "建筑线条引导，强化直线结构与建筑透视"),
    UNIFORMER("uniformer", "Landscaper", "语义分割引导，保留景观区域布局");

    companion object {
        fun fromId(id: String): ControlNetModel? = entries.firstOrNull { it.id == id }
    }
}
