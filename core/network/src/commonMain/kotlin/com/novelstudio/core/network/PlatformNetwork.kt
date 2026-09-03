package com.novelstudio.core.network

import io.ktor.client.engine.HttpClientEngineFactory

/** 平台 HTTP 引擎与 ZIP 解包的平台差异收口 */
expect fun platformHttpEngine(): HttpClientEngineFactory<*>

/** 从 ZIP 响应中提取第一个 PNG 条目（NAI 以 ZIP 打包返回图像） */
expect fun extractFirstPngFromZip(zipBytes: ByteArray): ByteArray?
