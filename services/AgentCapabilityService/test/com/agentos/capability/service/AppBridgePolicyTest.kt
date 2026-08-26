package com.agentos.capability.service

import com.agentos.capability.api.AppBridgeContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBridgePolicyTest {
    @Test fun classifiesKnownDomainsWithoutBindingToOneVendor() {
        assertEquals("视频", AppCategoryPolicy.classify("tv.danmaku.bili", "哔哩哔哩视频"))
        assertEquals("外卖", AppCategoryPolicy.classify("com.sankuai.meituan", "美团"))
        assertEquals("其他", AppCategoryPolicy.classify("dev.example.notes", "Notes"))
    }

    @Test fun confirmsInputsAndTransactionLikeClicks() {
        assertTrue(AppActionPolicy.requiresApproval(AppBridgeContract.ACTION_SET_TEXT, "搜索"))
        assertTrue(AppActionPolicy.requiresApproval(AppBridgeContract.ACTION_CLICK, "确认支付"))
        assertFalse(AppActionPolicy.requiresApproval(AppBridgeContract.ACTION_CLICK, "下一集"))
        assertFalse(AppActionPolicy.requiresApproval(AppBridgeContract.ACTION_SCROLL_FORWARD, "视频列表"))
    }
}
