package com.novelstudio

import java.io.File

actual fun settingsFilePath(context: Any?): String {
    val appContext = context as? android.content.Context
        ?: error("Android 平台必须传入 Context 提供设置目录")
    return File(appContext.filesDir, "settings.preferences_pb").absolutePath
}

actual fun crashLogFilePath(context: Any?): String {
    val appContext = context as? android.content.Context
        ?: error("Android 平台必须传入 Context 提供崩溃日志目录")
    return File(appContext.filesDir, "crash-latest.txt").absolutePath
}
