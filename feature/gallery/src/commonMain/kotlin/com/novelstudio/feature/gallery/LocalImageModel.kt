package com.novelstudio.feature.gallery

import com.novelstudio.core.common.platform.localFileModel

/** 将平台本地路径转换成 Coil 可识别的模型，避免手工拼接 file:// URI。 */
internal fun localImageModel(path: String): Any = localFileModel(path)
