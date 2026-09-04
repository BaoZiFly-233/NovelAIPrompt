package com.novelstudio.core.common.platform

/** 将本地绝对路径转换成图像加载器可识别的平台文件模型，避免手工拼接 file:// URI。 */
expect fun localFileModel(path: String): Any
