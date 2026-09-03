package com.novelstudio

import java.io.File
import java.nio.file.Files
import kotlin.io.path.absolutePathString

actual fun settingsFilePath(context: Any?): String {
    val dir = File(System.getProperty("user.home"), ".novelai-studio")
    Files.createDirectories(dir.toPath())
    return File(dir, "settings.preferences_pb").absolutePath
}

actual fun crashLogFilePath(context: Any?): String {
    val dir = File(System.getProperty("user.home"), ".novelai-studio")
    Files.createDirectories(dir.toPath())
    return File(dir, "crash-latest.txt").absolutePath
}
