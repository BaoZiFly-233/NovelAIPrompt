package com.novelstudio

actual fun settingsDirPath(context: Any?): String =
    (context as? android.content.Context)?.filesDir?.absolutePath
        ?: error("Android 平台必须传入 Context 提供设置目录")
