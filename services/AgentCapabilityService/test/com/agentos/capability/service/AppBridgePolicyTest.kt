package com.agentos.capability.service

import com.agentos.capability.api.AppBridgeContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBridgePolicyTest {
    @org.junit.Test fun nativeConfirmationCannotBeClickedThroughAccessibilityBridge() {
        listOf("com.agentos.capability", "com.agentos.shell", "com.agentos.media",
            "com.android.settings", "com.android.systemui", "com.google.android.permissioncontroller",
            "com.android.permissioncontroller", "").forEach {
            org.junit.Assert.assertFalse(it, AppActionPolicy.canAccessPackage(it))
        }
        org.junit.Assert.assertTrue(AppActionPolicy.canAccessPackage("org.example.reader"))
    }
    @Test fun classifiesKnownDomainsWithoutBindingToOneVendor() {
        assertEquals("视频", AppAdapterCatalog.resolve("tv.danmaku.bili", "哔哩哔哩视频").category)
        assertEquals("外卖", AppAdapterCatalog.resolve("com.sankuai.meituan", "美团").category)
        assertEquals("其他", AppAdapterCatalog.resolve("dev.example.notes", "Notes").category)
        assertTrue(AppAdapterCatalog.resolve("com.ss.android.ugc.aweme", "抖音").capabilities.contains("上下切换"))
    }

    @Test fun allClicksAndEditsRequireApprovalRegardlessOfLabelOrLanguage() {
        assertTrue(AppActionPolicy.requiresApproval(AppBridgeContract.ACTION_SET_TEXT))
        assertTrue(AppActionPolicy.requiresApproval(AppBridgeContract.ACTION_CLICK))
        assertTrue(AppActionPolicy.requiresApproval(999))
        assertFalse(AppActionPolicy.requiresApproval(AppBridgeContract.ACTION_SCROLL_FORWARD))
        assertFalse(AppActionPolicy.requiresApproval(AppBridgeContract.ACTION_SCROLL_BACKWARD))
    }

    @Test fun sensitiveNodesCannotBeReadOrOperated() {
        assertTrue(AppActionPolicy.isSensitive(true, false))
        assertTrue(AppActionPolicy.isSensitive(false, true))
        assertTrue(AppActionPolicy.isSensitive(true, true))
        assertFalse(AppActionPolicy.isSensitive(false, false))
    }
}
