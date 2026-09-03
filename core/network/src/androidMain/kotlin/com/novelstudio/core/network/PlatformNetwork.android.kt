package com.novelstudio.core.network

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

actual fun platformHttpEngine(): HttpClientEngineFactory<*> = OkHttp

actual fun extractFirstPngFromZip(zipBytes: ByteArray): ByteArray? {
    ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            if (entry.name.endsWith(".png", ignoreCase = true)) {
                return zip.readBytes()
            }
            entry = zip.nextEntry
        }
    }
    return null
}

actual fun isTransportError(throwable: Throwable): Boolean = throwable is java.io.IOException
