package com.agentos.capability.service

import com.agentos.capability.api.AppBridgeContract

internal data class AppAdapterProfile(val category: String, val capabilities: List<String>)

internal object AppAdapterCatalog {
    private data class Rule(val signals: List<String>, val profile: AppAdapterProfile)

    private val rules = listOf(
        Rule(listOf("douyin", "tiktok", "抖音", "kuaishou", "快手"),
            AppAdapterProfile("短视频", listOf("播放控制", "上下切换", "页面语义", "搜索"))),
        Rule(listOf("bilibili", "danmaku", "youtube", "netflix", "iqiyi", "youku", "腾讯视频", "视频"),
            AppAdapterProfile("视频", listOf("播放控制", "选集", "页面语义", "搜索"))),
        Rule(listOf("reader", "kindle", "qidian", "fanqie", "weread", "阅读", "小说", "读书"),
            AppAdapterProfile("阅读", listOf("章节导航", "阅读进度", "页面语义", "朗读"))),
        Rule(listOf("news", "toutiao", "zhihu", "新闻", "头条", "资讯"),
            AppAdapterProfile("新闻", listOf("信息流", "文章语义", "搜索", "摘要"))),
        Rule(listOf("meituan", "sankuai", "eleme", "food", "外卖", "美团", "饿了么"),
            AppAdapterProfile("外卖", listOf("商家浏览", "菜单语义", "购物车", "确认下单"))),
        Rule(listOf("wechat", "weixin", "qq", "telegram", "whatsapp", "微信", "聊天"),
            AppAdapterProfile("社交", listOf("通知摘要", "会话导航", "确认输入", "确认发送"))),
        Rule(listOf("taobao", "tmall", "jingdong", "jd.", "pinduoduo", "淘宝", "京东", "拼多多"),
            AppAdapterProfile("购物", listOf("商品搜索", "页面语义", "购物车", "确认购买"))),
        Rule(listOf("spotify", "music", "netease", "kugou", "音乐", "云音乐", "酷狗"),
            AppAdapterProfile("音乐", listOf("播放控制", "搜索", "歌单导航"))),
        Rule(listOf("maps", "map", "amap", "baidu", "地图", "高德"),
            AppAdapterProfile("出行", listOf("地点搜索", "路线导航", "页面语义"))),
    )

    fun resolve(packageName: String, label: String): AppAdapterProfile {
        val value = "$packageName $label".lowercase()
        return rules.firstOrNull { rule -> rule.signals.any(value::contains) }?.profile
            ?: AppAdapterProfile("其他", listOf("应用启动", "页面语义", "受控点击", "受控输入"))
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
