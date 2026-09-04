package com.novelstudio.core.common.platform

import java.io.File

actual fun localFileModel(path: String): Any = File(path)
