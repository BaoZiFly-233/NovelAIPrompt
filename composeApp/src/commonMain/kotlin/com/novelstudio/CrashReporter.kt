package com.novelstudio

/**
 * 崩溃日志收集器：
 * 捕获未捕获异常并落盘到应用沙盒，供「设置」页读取展示——
 * 即使没有 USB 调试也能把闪退堆栈带回给开发者。
 */
expect object CrashReporter {
    fun install(context: Any?)
    fun latestCrash(): String?
}
