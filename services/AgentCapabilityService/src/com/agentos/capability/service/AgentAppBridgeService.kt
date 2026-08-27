package com.agentos.capability.service

import android.app.Service
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import com.agentos.capability.api.AppBridgeContract
import com.agentos.capability.api.AppBridgeReply
import com.agentos.capability.api.AppDescriptor
import com.agentos.capability.api.IAgentAppBridgeService
import com.agentos.capability.api.SemanticSnapshot
import com.agentos.capability.core.PendingApproval

class AgentAppBridgeService : Service() {
    private val callerPolicy = CallerIdentityPolicy(ALLOWED_CALLER_PACKAGE)
    private val pending = PendingApproval<PendingAction>()
    private val requestLock = Any()

    private val binder = object : IAgentAppBridgeService.Stub() {
        override fun listLaunchableApps(): MutableList<AppDescriptor> {
            enforceAuthorizedCaller()
            return launchableApps().toMutableList()
        }

        override fun requestLaunch(packageName: String): AppBridgeReply = synchronized(requestLock) {
            enforceAuthorizedCaller()
            invalidatePending()
            if (locked()) return@synchronized denied("请先解锁设备")
            if (packageName.length !in 1..MAX_PACKAGE_LENGTH) return@synchronized denied("应用标识无效")
            val app = launchableApps().firstOrNull { it.packageName == packageName }
                ?: return@synchronized denied("应用不存在或不可启动")
            requireApproval(PendingAction.Launch(packageName, app.label), "确认打开 ${app.label}")
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
        ): AppBridgeReply = synchronized(requestLock) {
            enforceAuthorizedCaller()
            invalidatePending()
            if (locked()) return@synchronized denied("请先解锁设备")
            if (!AppActionPolicy.canAccessPackage(expectedPackage)) return@synchronized denied("不能自动操作系统权限或 AgentOS 自身的确认界面")
            if (expectedPackage.length !in 1..MAX_PACKAGE_LENGTH || nodePath.length > MAX_PATH_LENGTH ||
                value.length > MAX_VALUE_LENGTH || action !in VALID_ACTIONS ||
                (action != AppBridgeContract.ACTION_SET_TEXT && value.isNotEmpty())) return@synchronized denied("页面操作参数无效")
            val bridge = AgentAppAccessibilityService.instance ?: return@synchronized failed("应用语义桥尚未启用")
            val (text, className) = bridge.nodeDescriptor(expectedPackage, nodePath)
                ?: return@synchronized failed("页面已经变化或包含敏感输入，请刷新后重试")
            val pendingAction = PendingAction.Node(expectedPackage, nodePath, action, value, text, className)
            if (AppActionPolicy.requiresApproval(action)) {
                val detail = if (action == AppBridgeContract.ACTION_SET_TEXT) "输入：$value" else "点击"
                requireApproval(pendingAction, "$expectedPackage\n目标：${text.ifBlank { "无文字控件" }}\n$detail\n仅允许这一次，60 秒后失效")
            } else execute(pendingAction)
        }

        override fun approve(token: String): AppBridgeReply = synchronized(requestLock) {
            enforceAuthorizedCaller()
            val action = pending.take(token) ?: return@synchronized denied("确认请求已失效")
            execute(action)
        }

        override fun deny(token: String): AppBridgeReply = synchronized(requestLock) {
            enforceAuthorizedCaller()
            if (pending.take(token) != null) {
                AgentAppAccessibilityService.instance?.cancelPending()
                denied("用户已取消操作")
            } else denied("确认请求已失效")
        }

        override fun cancelPending() = synchronized(requestLock) {
            enforceAuthorizedCaller()
            invalidatePending()
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onUnbind(intent: Intent?): Boolean {
        synchronized(requestLock) { invalidatePending() }
        return false
    }

    override fun onDestroy() {
        synchronized(requestLock) { invalidatePending() }
        super.onDestroy()
    }

    private fun invalidatePending() {
        pending.clear()
        AgentAppAccessibilityService.instance?.cancelPending()
    }

    private fun locked() = getSystemService(KeyguardManager::class.java).isDeviceLocked

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
        return AppBridgeReply(AppBridgeContract.STATUS_APPROVAL_REQUIRED, message, pending.issue(action))
    }

    private fun execute(action: PendingAction): AppBridgeReply {
        if (locked()) return denied("设备已锁定，操作未执行")
        return when (action) {
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
                        onSuccess = { AppBridgeReply(AppBridgeContract.STATUS_QUEUED,
                            "已排队，尚未确认执行结果；5 秒内页面仍匹配才执行，不会自动重试", "") },
                        onFailure = { bridge.cancelPending(); failed("无法切换到目标应用") },
                    )
            }
        }
        }
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

    private sealed class PendingAction {
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
        private val VALID_ACTIONS = setOf(
            AppBridgeContract.ACTION_CLICK,
            AppBridgeContract.ACTION_SCROLL_FORWARD,
            AppBridgeContract.ACTION_SCROLL_BACKWARD,
            AppBridgeContract.ACTION_SET_TEXT,
        )
    }
}
