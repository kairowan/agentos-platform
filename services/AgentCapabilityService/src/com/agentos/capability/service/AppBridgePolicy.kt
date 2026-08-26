package com.agentos.capability.service

import com.agentos.capability.api.AppBridgeContract

internal object AppCategoryPolicy {
    fun classify(packageName: String, label: String): String {
        val value = "$packageName $label".lowercase()
        return when {
            listOf("douyin", "tiktok", "bilibili", "video", "视频", "抖音").any(value::contains) -> "视频"
            listOf("reader", "book", "novel", "阅读", "小说", "书").any(value::contains) -> "阅读"
            listOf("news", "toutiao", "新闻", "头条").any(value::contains) -> "新闻"
            listOf("meituan", "eleme", "food", "外卖", "美团", "饿了么").any(value::contains) -> "外卖"
            listOf("wechat", "qq", "message", "微信", "聊天").any(value::contains) -> "社交"
            else -> "其他"
        }
    }
}

internal object AppActionPolicy {
    private val riskyWords = listOf(
        "支付", "付款", "购买", "下单", "提交订单", "发送", "发布", "删除", "转账",
        "pay", "buy", "order", "send", "publish", "delete", "transfer",
    )

    fun requiresApproval(action: Int, nodeText: String): Boolean =
        action == AppBridgeContract.ACTION_SET_TEXT ||
            action == AppBridgeContract.ACTION_CLICK && riskyWords.any(nodeText.lowercase()::contains)
}
