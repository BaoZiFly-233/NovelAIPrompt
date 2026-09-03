package com.novelstudio

import java.nio.file.Files
import kotlin.io.path.absolutePathString

actual fun settingsDirPath(context: Any?): String {
    val dir = java.nio.file.Path.of(System.getProperty("user.home"), ".novelai-studio")
    Files.createDirectories(dir)
    return dir.absolutePathString()
}
