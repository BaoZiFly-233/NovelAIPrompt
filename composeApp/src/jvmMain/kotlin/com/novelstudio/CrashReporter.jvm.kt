package com.novelstudio

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

actual object CrashReporter {

    private var logFile: File? = null

    actual fun install(context: Any?) {
        logFile = File(crashLogFilePath(context))
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                logFile?.writeText(
                    buildString {
                        appendLine("时间: ${System.currentTimeMillis()}")
                        appendLine("线程: ${thread.name}")
                        appendLine(stackTraceOf(throwable))
                    },
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    actual fun latestCrash(): String? =
        logFile?.takeIf { it.exists() }?.readText()?.take(4000)

    private fun stackTraceOf(throwable: Throwable): String {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        return writer.toString()
    }
}
