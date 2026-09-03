package com.novelstudio.core.network

/** NovelAI API 带状态码的业务异常，供 UI 层做差异化提示与重试判定 */
class NaiApiException(val statusCode: Int, message: String) : Exception(message) {

    companion object {

        /** 把任意异常翻译成面向用户的中文提示（401/402/429/5xx/网络） */
        fun describe(throwable: Throwable): String = when {
            throwable is NaiApiException -> when (throwable.statusCode) {
                401 -> "API Token 无效或已过期，请到「设置」页重新配置"
                402 -> "Anlas 余额不足，无法完成本次生成"
                429 -> "请求过于频繁（限流），请稍后再试"
                in 500..599 -> "NovelAI 服务暂时不可用（HTTP ${throwable.statusCode}），已自动重试仍失败"
                else -> "请求失败（HTTP ${throwable.statusCode}）"
            }
            throwable.message?.contains("未配置", ignoreCase = false) == true -> throwable.message!!
            else -> "网络异常：${throwable.message ?: "未知错误"}"
        }

        /** 是否值得重试：限流与服务端错误重试，鉴权/余额等 4xx 不重试 */
        fun isRetryable(statusCode: Int): Boolean =
            statusCode == 429 || statusCode in 500..599
    }
}
