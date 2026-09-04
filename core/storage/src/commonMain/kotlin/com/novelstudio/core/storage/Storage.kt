package com.novelstudio.core.storage

/**
 * 图片文件存储管道契约：
 * 原图 PNG 落盘 + 平台缩略图（最长边 256px）+ BlurHash 主色占位串一次生成。
 * Android 使用 WebP 缩略图，桌面 JVM 使用 PNG 缩略图。
 */
data class StoredImage(
    val imagePath: String,
    val thumbnailPath: String,
    val blurHash: String,
)

interface ImageFileStorage {

    /**
     * 事务式保存原图、缩略图与 BlurHash：全部成功才发布正式文件；
     * 任一步失败必须清理本次写入的临时文件和正式文件。
     */
    suspend fun saveImage(id: String, pngBytes: ByteArray): StoredImage

    /** PNG/WebP 统一入口；旧实现默认只接受 PNG。 */
    suspend fun saveImage(id: String, imageBytes: ByteArray, mimeType: String): StoredImage {
        require(mimeType == "image/png") { "当前存储实现不支持 $mimeType" }
        return saveImage(id, imageBytes)
    }

    /** 只读取本存储拥有的原图路径，供上下文图像工具使用。 */
    suspend fun readImage(imagePath: String): ByteArray = error("当前存储实现不支持读取原图")

    /** 删除原图与缩略图（幂等） */
    suspend fun deleteImage(id: String)
}

/** 平台差异收口：Android 使用应用内部存储，桌面使用 ~/.novelai-studio */
expect fun imageFileStorage(context: Any?): ImageFileStorage
