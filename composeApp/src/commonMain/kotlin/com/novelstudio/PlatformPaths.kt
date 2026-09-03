package com.novelstudio

/** 平台差异收口：设置文件（Token）与崩溃日志的完整文件路径 */
expect fun settingsFilePath(context: Any?): String

expect fun crashLogFilePath(context: Any?): String
