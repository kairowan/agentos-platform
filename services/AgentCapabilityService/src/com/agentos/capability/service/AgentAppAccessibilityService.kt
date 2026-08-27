package com.agentos.capability.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.agentos.capability.api.AppBridgeContract
import com.agentos.capability.api.SemanticNode
import com.agentos.capability.api.SemanticSnapshot
import com.agentos.capability.core.PendingApproval
import java.util.ArrayDeque

class AgentAppAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val queuedApproval = PendingApproval<QueuedAction>(lifetimeMillis = 5_000)
    private var queuedAction: Pair<String, QueuedAction>? = null
    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (locked()) { cancelPending(); lastExternalSnapshot = null; return }
        val packageName = event?.packageName?.toString().orEmpty()
        val now = System.currentTimeMillis()
        if (AppActionPolicy.canAccessPackage(packageName) && now - lastSnapshotAt >= SNAPSHOT_INTERVAL_MILLIS) {
            rootInActiveWindow?.let { root ->
                lastExternalSnapshot = snapshotFrom(root, "已缓存最近的前台应用页面")
                lastSnapshotAt = now
            }
        }
        val scheduled = synchronized(this) {
            queuedAction?.takeIf { it.second.packageName == packageName }?.also { queuedAction = null }
        }
        scheduled?.let { (token, _) ->
            mainHandler.postDelayed({
                // Consume at execution time, not when an accessibility event arrives.
                // Cancellation, replacement, expiry and duplicate callbacks fail closed.
                val queued = queuedApproval.take(token) ?: return@postDelayed
                performVerified(queued.packageName, queued.path, queued.action, queued.value,
                    queued.expectedText, queued.expectedClass)
            }, ACTION_SETTLE_MILLIS)
        }
    }
    override fun onInterrupt() { cancelPending(); lastExternalSnapshot = null }
    override fun onDestroy() {
        cancelPending()
        mainHandler.removeCallbacksAndMessages(null)
        lastExternalSnapshot = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    @Synchronized internal fun cancelPending() { queuedAction = null; queuedApproval.clear() }

    private fun locked() = getSystemService(KeyguardManager::class.java).isDeviceLocked

    private fun AccessibilityNodeInfo.sensitive() = AppActionPolicy.isSensitive(
        isPassword, Build.VERSION.SDK_INT >= 34 && isAccessibilityDataSensitive,
    )

    private fun AccessibilityNodeInfo.safeText(): String =
        if (isEditable) "输入框（内容不读取）" else text?.toString()?.trim().orEmpty()
            .ifEmpty { contentDescription?.toString()?.trim().orEmpty() }.take(MAX_TEXT)

    internal fun snapshot(): SemanticSnapshot {
        if (locked()) { lastExternalSnapshot = null; return SemanticSnapshot("", "", emptyList(), "设备已锁定") }
        val root = rootInActiveWindow
        if (root == null) return lastExternalSnapshot
            ?: SemanticSnapshot("", "", emptyList(), "请启用语义桥并打开一个应用页面")
        if (!AppActionPolicy.canAccessPackage(root.packageName?.toString().orEmpty())) return lastExternalSnapshot
            ?: SemanticSnapshot("", "", emptyList(), "尚未缓存其他应用页面")
        return snapshotFrom(root, "已读取当前前台应用页面")
    }

    private fun snapshotFrom(root: AccessibilityNodeInfo, message: String): SemanticSnapshot {
        val packageName = root.packageName?.toString().orEmpty()
        if (!AppActionPolicy.canAccessPackage(packageName)) return SemanticSnapshot("", "", emptyList(), "受保护的系统界面")
        val nodes = ArrayList<SemanticNode>()
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, String>>()
        queue.add(root to "")
        while (queue.isNotEmpty() && nodes.size < MAX_NODES) {
            val (node, path) = queue.removeFirst()
            if (node.sensitive()) continue // Do not traverse a sensitive subtree either.
            val text = node.safeText()
            if (text.isNotEmpty() || node.isClickable || node.isEditable || node.isScrollable) {
                nodes += SemanticNode(path, text.take(MAX_TEXT), node.className?.toString().orEmpty(),
                    node.isClickable, node.isEditable, node.isScrollable)
            }
            if (path.count { it == '/' } < MAX_DEPTH) {
                for (index in 0 until node.childCount) {
                    node.getChild(index)?.let { queue.add(it to if (path.isEmpty()) "$index" else "$path/$index") }
                }
            }
        }
        val title = nodes.firstOrNull { it.text.isNotBlank() }?.text.orEmpty()
        return SemanticSnapshot(packageName, title, nodes, "$message：${nodes.size} 个可访问节点")
    }

    internal fun nodeDescriptor(expectedPackage: String, path: String): Pair<String, String>? =
        resolveNode(expectedPackage, path)?.let { node ->
            node.safeText() to node.className?.toString().orEmpty()
        } ?: lastExternalSnapshot?.takeIf { it.packageName == expectedPackage }
            ?.nodes?.firstOrNull { it.path == path }?.let { it.text to it.className }

    internal fun performVerified(
        expectedPackage: String,
        path: String,
        action: Int,
        value: String,
        expectedText: String,
        expectedClass: String,
    ): Boolean {
        val node = resolveNode(expectedPackage, path) ?: return false
        val currentText = node.safeText()
        if (currentText != expectedText || node.className?.toString().orEmpty() != expectedClass) return false
        return when (action) {
            AppBridgeContract.ACTION_CLICK -> node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            AppBridgeContract.ACTION_SCROLL_FORWARD -> node.isScrollable &&
                node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            AppBridgeContract.ACTION_SCROLL_BACKWARD -> node.isScrollable &&
                node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            AppBridgeContract.ACTION_SET_TEXT -> node.isEditable && node.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) },
            )
            else -> false
        }
    }

    @Synchronized internal fun queue(
        expectedPackage: String,
        path: String,
        action: Int,
        value: String,
        expectedText: String,
        expectedClass: String,
    ) {
        cancelPending()
        if (locked() || !AppActionPolicy.canAccessPackage(expectedPackage)) return
        val value = QueuedAction(expectedPackage, path, action, value, expectedText, expectedClass)
        queuedAction = queuedApproval.issue(value) to value
    }

    private fun resolveNode(expectedPackage: String, path: String): AccessibilityNodeInfo? {
        if (locked() || !AppActionPolicy.canAccessPackage(expectedPackage)) return null
        var node = rootInActiveWindow ?: return null
        if (node.packageName?.toString() != expectedPackage || node.sensitive()) return null
        if (path.isBlank()) return node
        for (part in path.split('/')) {
            val index = part.toIntOrNull() ?: return null
            if (index !in 0 until node.childCount) return null
            node = node.getChild(index) ?: return null
            if (node.sensitive()) return null
        }
        return node
    }

    companion object {
        @Volatile internal var instance: AgentAppAccessibilityService? = null
        @Volatile private var lastExternalSnapshot: SemanticSnapshot? = null
        private var lastSnapshotAt = 0L
        private const val MAX_NODES = 200
        private const val MAX_TEXT = 500
        private const val MAX_DEPTH = 12
        private const val SNAPSHOT_INTERVAL_MILLIS = 500L
        private const val ACTION_SETTLE_MILLIS = 350L
    }

    private data class QueuedAction(
        val packageName: String,
        val path: String,
        val action: Int,
        val value: String,
        val expectedText: String,
        val expectedClass: String,
    )
}
