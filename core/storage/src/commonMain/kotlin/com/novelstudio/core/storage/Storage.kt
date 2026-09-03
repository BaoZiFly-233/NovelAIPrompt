package com.novelstudio.core.storage

/**
 * 图片文件存储管道契约（Task 2.4 落地）：
 * 原图 PNG 落盘 + WebP 缩略图（256px）+ BlurHash 主色占位串一次生成。
 */
data class StoredImage(
    val imagePath: String,
    val thumbnailPath: String,
    val blurHash: String,
)

interface ImageFileStorage {

    /** 保存原图并生成缩略图与 BlurHash，全部成功才返回，任一失败抛异常 */
    suspend fun saveImage(id: String, pngBytes: ByteArray): StoredImage

    /** 删除原图与缩略图（幂等） */
    suspend fun deleteImage(id: String)
}

/** 平台差异收口：Android 使用应用内部存储，桌面使用 ~/.novelai-studio */
expect fun imageFileStorage(context: Any?): ImageFileStorage
