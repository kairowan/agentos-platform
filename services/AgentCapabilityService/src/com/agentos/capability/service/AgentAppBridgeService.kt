package com.agentos.capability.service

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import com.agentos.capability.api.AppBridgeContract
import com.agentos.capability.api.AppBridgeReply
import com.agentos.capability.api.AppDescriptor
import com.agentos.capability.api.IAgentAppBridgeService
import com.agentos.capability.api.SemanticSnapshot
import java.util.UUID

class AgentAppBridgeService : Service() {
    private val callerPolicy = CallerIdentityPolicy(ALLOWED_CALLER_PACKAGE)
    private val pending = LinkedHashMap<String, PendingAction>()

    private val binder = object : IAgentAppBridgeService.Stub() {
        override fun listLaunchableApps(): MutableList<AppDescriptor> {
            enforceAuthorizedCaller()
            return launchableApps().toMutableList()
        }

        override fun requestLaunch(packageName: String): AppBridgeReply {
            enforceAuthorizedCaller()
            if (packageName.length !in 1..MAX_PACKAGE_LENGTH) return denied("应用标识无效")
            val app = launchableApps().firstOrNull { it.packageName == packageName }
                ?: return denied("应用不存在或不可启动")
            return requireApproval(PendingAction.Launch(packageName, app.label), "确认打开 ${app.label}")
        }

        override fun getSemanticSnapshot(): SemanticSnapshot {
            enforceAuthorizedCaller()
            return AgentAppAccessibilityService.instance?.snapshot()
                ?: SemanticSnapshot("", "", emptyList(), "应用语义桥尚未启用")
        }

        override fun requestNodeAction(
            expectedPackage: String,
            nodePath: String,
            action: Int,
            value: String,
        ): AppBridgeReply {
            enforceAuthorizedCaller()
            if (expectedPackage.length !in 1..MAX_PACKAGE_LENGTH || nodePath.length > MAX_PATH_LENGTH ||
                value.length > MAX_VALUE_LENGTH || action !in VALID_ACTIONS) return denied("页面操作参数无效")
            val bridge = AgentAppAccessibilityService.instance ?: return failed("应用语义桥尚未启用")
            val (text, className) = bridge.nodeDescriptor(expectedPackage, nodePath)
                ?: return failed("页面已经变化，请刷新后重试")
            val pendingAction = PendingAction.Node(expectedPackage, nodePath, action, value, text, className)
            return if (AppActionPolicy.requiresApproval(action, text)) {
                requireApproval(pendingAction, "确认对“${text.ifBlank { "当前输入框" }.take(80)}”执行操作")
            } else execute(pendingAction)
        }

        override fun approve(token: String): AppBridgeReply {
            enforceAuthorizedCaller()
            val action = synchronized(pending) {
                removeExpiredLocked()
                pending.remove(token)
            } ?: return denied("确认请求已失效")
            return execute(action)
        }

        override fun deny(token: String): AppBridgeReply {
            enforceAuthorizedCaller()
            val removed = synchronized(pending) { pending.remove(token) }
            return if (removed != null) denied("用户已取消操作") else denied("确认请求已失效")
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun launchableApps(): List<AppDescriptor> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(launcher, 0)
            .mapNotNull { info ->
                val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
                val label = info.loadLabel(packageManager)?.toString()?.trim().orEmpty()
                    .ifBlank { packageName }
                val profile = AppAdapterCatalog.resolve(packageName, label)
                AppDescriptor(packageName, label, profile.category, profile.capabilities)
            }
            .distinctBy(AppDescriptor::packageName)
            .sortedWith(compareBy(AppDescriptor::category, AppDescriptor::label))
            .take(MAX_APPS)
    }

    private fun requireApproval(action: PendingAction, message: String): AppBridgeReply {
        val token = UUID.randomUUID().toString()
        synchronized(pending) {
            removeExpiredLocked()
            if (pending.size >= MAX_PENDING) pending.remove(pending.keys.first())
            pending[token] = action
        }
        return AppBridgeReply(AppBridgeContract.STATUS_APPROVAL_REQUIRED, message, token)
    }

    private fun execute(action: PendingAction): AppBridgeReply = when (action) {
        is PendingAction.Launch -> {
            val intent = packageManager.getLaunchIntentForPackage(action.packageName)
                ?: return denied("应用已不可启动")
            runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .fold(
                    onSuccess = { success("已打开 ${action.label}") },
                    onFailure = { failed("无法打开应用") },
                )
        }
        is PendingAction.Node -> {
            val bridge = AgentAppAccessibilityService.instance ?: return failed("应用语义桥尚未启用")
            if (bridge.performVerified(action.packageName, action.path, action.action, action.value,
                    action.label, action.className)) {
                success("页面操作已执行")
            } else {
                val intent = packageManager.getLaunchIntentForPackage(action.packageName)
                    ?: return failed("目标应用已经不可启动")
                bridge.queue(action.packageName, action.path, action.action, action.value,
                    action.label, action.className)
                runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .fold(
                        onSuccess = { success("已切换到目标应用；仅在页面节点仍匹配时执行") },
                        onFailure = { failed("无法切换到目标应用") },
                    )
            }
        }
    }

    private fun removeExpiredLocked() {
        val now = System.currentTimeMillis()
        pending.entries.removeAll { now - it.value.createdAtMillis > APPROVAL_TTL_MILLIS }
    }

    private fun enforceAuthorizedCaller() {
        val packages = packageManager.getPackagesForUid(Binder.getCallingUid())?.toList().orEmpty()
        val signatureMatches = packages.singleOrNull()?.let {
            packageManager.checkSignatures(it, packageName) == PackageManager.SIGNATURE_MATCH
        } == true
        if (!callerPolicy.isAuthorized(packages, signatureMatches)) {
            throw SecurityException("Caller is not authorized to use AgentOS App Bridge")
        }
    }

    private fun success(message: String) = AppBridgeReply(AppBridgeContract.STATUS_SUCCESS, message, "")
    private fun denied(message: String) = AppBridgeReply(AppBridgeContract.STATUS_DENIED, message, "")
    private fun failed(message: String) = AppBridgeReply(AppBridgeContract.STATUS_FAILED, message, "")

    private sealed class PendingAction(val createdAtMillis: Long = System.currentTimeMillis()) {
        class Launch(val packageName: String, val label: String) : PendingAction()
        class Node(
            val packageName: String,
            val path: String,
            val action: Int,
            val value: String,
            val label: String,
            val className: String,
        ) : PendingAction()
    }

    companion object {
        private const val ALLOWED_CALLER_PACKAGE = "com.agentos.shell"
        private const val MAX_PACKAGE_LENGTH = 255
        private const val MAX_PATH_LENGTH = 512
        private const val MAX_VALUE_LENGTH = 500
        private const val MAX_APPS = 300
        private const val MAX_PENDING = 32
        private const val APPROVAL_TTL_MILLIS = 60_000L
        private val VALID_ACTIONS = setOf(
            AppBridgeContract.ACTION_CLICK,
            AppBridgeContract.ACTION_SCROLL_FORWARD,
            AppBridgeContract.ACTION_SCROLL_BACKWARD,
            AppBridgeContract.ACTION_SET_TEXT,
        )
    }
}
