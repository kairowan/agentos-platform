package com.agentos.capability.service

import com.agentos.capability.api.AppBridgeContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBridgePolicyTest {
    @Test fun classifiesKnownDomainsWithoutBindingToOneVendor() {
        assertEquals("视频", AppAdapterCatalog.resolve("tv.danmaku.bili", "哔哩哔哩视频").category)
        assertEquals("外卖", AppAdapterCatalog.resolve("com.sankuai.meituan", "美团").category)
        assertEquals("其他", AppAdapterCatalog.resolve("dev.example.notes", "Notes").category)
        assertTrue(AppAdapterCatalog.resolve("com.ss.android.ugc.aweme", "抖音").capabilities.contains("上下切换"))
    }

    @Test fun confirmsInputsAndTransactionLikeClicks() {
        assertTrue(AppActionPolicy.requiresApproval(AppBridgeContract.ACTION_SET_TEXT, "搜索"))
        assertTrue(AppActionPolicy.requiresApproval(AppBridgeContract.ACTION_CLICK, "确认支付"))
        assertFalse(AppActionPolicy.requiresApproval(AppBridgeContract.ACTION_CLICK, "下一集"))
        assertFalse(AppActionPolicy.requiresApproval(AppBridgeContract.ACTION_SCROLL_FORWARD, "视频列表"))
    }
}
