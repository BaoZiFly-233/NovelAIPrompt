package com.novelstudio.core.common.png

/** 从未经转码的 PNG 字节解码 RGBA，并按 NovelAI 列优先顺序打包 Alpha 通道最低位。 */
internal expect fun extractPackedAlphaLsb(pngBytes: ByteArray): ByteArray?

/** 解压 stealth_pngcomp 的 gzip 载荷，并强制限制输出大小。 */
internal expect fun gunzipLimited(bytes: ByteArray, maxOutputBytes: Int): ByteArray
