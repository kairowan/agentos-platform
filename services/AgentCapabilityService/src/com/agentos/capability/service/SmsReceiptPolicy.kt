package com.agentos.capability.service

internal object SmsReceiptPolicy {
    const val PENDING = -999
    const val OK = -1 // Activity.RESULT_OK from the send PendingIntent, not an API return value.
    fun sentState(results: List<Int>): String = when {
        results.isEmpty() -> "结果未知"
        results.any { it != PENDING && it != OK } -> "发送失败或部分发送；请核对，勿自动重发"
        results.all { it == OK } -> "已发送（不代表送达或已读）"
        else -> "等待发送结果"
    }
    fun terminal(results: List<Int>) = results.isNotEmpty() && results.none { it == PENDING }
}
