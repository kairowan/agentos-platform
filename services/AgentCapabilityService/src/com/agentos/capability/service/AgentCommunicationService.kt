package com.agentos.capability.service

import android.Manifest
import android.app.KeyguardManager
import android.app.Service
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.agentos.capability.api.CommunicationContract as C
import com.agentos.capability.api.CommunicationReply
import com.agentos.capability.api.CommunicationRequest
import com.agentos.capability.api.IAgentCommunicationService
import com.agentos.capability.core.PendingApproval
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class CommunicationSim(val id: Int, val label: String)
internal data class PreparedCommunication(
    val token: String, val request: CommunicationRequest, val label: String,
    val simLabel: String, val account: PhoneAccountHandle?,
)
internal data class CommunicationUiState(
    val draft: CommunicationRequest? = null,
    val prepared: PreparedCommunication? = null,
    val notice: String = "电话与文本短信预览。请只在测试设备启用默认角色。",
    val sims: List<CommunicationSim> = emptyList(),
)

class AgentCommunicationService : Service() {
    private val controller by lazy { CommunicationController.get(this) }
    private val binder = object : IAgentCommunicationService.Stub() {
        override fun prepare(request: CommunicationRequest): CommunicationReply = authorized { controller.prepare(request) }
        override fun cancelPending() = authorized { controller.cancel() }
        override fun activeCallIds(): List<String> = authorized { AgentInCallService.calls.value.map { it.id } }
        override fun controlCall(callId: String, action: Int): CommunicationReply = authorized {
            if (callId.length > 100) CommunicationReply(C.DENIED, "通话标识无效")
            else AgentInCallService.control(callId, action)
        }
    }

    private fun <T> authorized(block: () -> T): T {
        val uid = Binder.getCallingUid()
        val packages = packageManager.getPackagesForUid(uid)?.toList().orEmpty()
        val signed = packages.singleOrNull()?.let { packageManager.checkSignatures(it, packageName) == PackageManager.SIGNATURE_MATCH } == true
        if (!CallerIdentityPolicy("com.agentos.shell").isAuthorized(packages, signed)) throw SecurityException("Unauthorized communication caller")
        val identity = Binder.clearCallingIdentity()
        return try { block() } finally { Binder.restoreCallingIdentity(identity) }
    }

    override fun onBind(intent: Intent) = binder
}

internal class CommunicationController private constructor(private val app: Context) {
    private val approval = PendingApproval<PreparedCommunication>()
    private val mutableState = MutableStateFlow(CommunicationUiState())
    val state = mutableState.asStateFlow()
    private val telecom get() = app.getSystemService(TelecomManager::class.java)
    private val telephony get() = app.getSystemService(TelephonyManager::class.java)

    fun hasPermission(permission: String) = app.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    fun holdsRole(role: String) = app.getSystemService(RoleManager::class.java).let { it.isRoleAvailable(role) && it.isRoleHeld(role) }
    fun locked() = app.getSystemService(KeyguardManager::class.java).isDeviceLocked

    fun refreshSims() {
        val choices = if (!hasPermission(Manifest.permission.READ_PHONE_STATE)) emptyList() else runCatching {
            app.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty()
                .map { CommunicationSim(it.subscriptionId, "SIM ${it.simSlotIndex + 1} · ${it.displayName}") }
        }.getOrDefault(emptyList())
        mutableState.update { it.copy(sims = choices) }
    }

    @Synchronized fun cancel() {
        approval.clear()
        mutableState.update { it.copy(prepared = null, notice = if (it.prepared != null) "已取消；没有发起新的通信操作。" else it.notice) }
    }

    @Synchronized fun externalDraft(request: CommunicationRequest) {
        cancel()
        mutableState.update { it.copy(draft = request) }
    }

    @Synchronized fun prepare(raw: CommunicationRequest): CommunicationReply {
        approval.clear()
        mutableState.update { it.copy(prepared = null) }
        return try {
            CommunicationPolicy.validate(raw)
            mutableState.update { it.copy(draft = raw) }
            require(!locked()) { "请先解锁设备，再确认号码和内容" }
            require(app.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) { "此设备不支持电话／短信" }
            val role = if (raw.operation == C.CALL) RoleManager.ROLE_DIALER else RoleManager.ROLE_SMS
            require(holdsRole(role)) { "请先在通信页面选择对应的默认角色" }
            require(hasPermission(Manifest.permission.READ_PHONE_STATE)) { "请先授权电话状态，以明确选择 SIM" }
            val (number, label) = resolveRecipient(raw.recipient)
            require(!telephony.isEmergencyNumber(number)) { "紧急号码请使用下方独立的系统紧急拨号入口" }
            refreshSims()
            val sim = if (raw.subscriptionId >= 0) state.value.sims.singleOrNull { it.id == raw.subscriptionId }
                else state.value.sims.singleOrNull()
            requireNotNull(sim) { "请选择一张当前有效的 SIM；系统不会猜测或改用另一张卡" }
            val account = if (raw.operation == C.CALL) {
                require(hasPermission(Manifest.permission.CALL_PHONE)) { "请授权拨打电话" }
                require(Build.VERSION.SDK_INT >= 30) { "此拨号预览需要 Android 11 或更高版本" }
                telecom.callCapablePhoneAccounts.filter {
                    telecom.getPhoneAccount(it)?.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION) == true &&
                        telephony.getSubscriptionId(it) == sim.id
                }.singleOrNull().also { requireNotNull(it) { "所选 SIM 没有可用的电话账户" } }
            } else {
                require(hasPermission(Manifest.permission.SEND_SMS) && hasPermission(Manifest.permission.READ_SMS) &&
                    hasPermission(Manifest.permission.RECEIVE_SMS)) { "请先授权文本短信收发和存储" }
                null
            }
            val prepared = PreparedCommunication("", raw.copy(recipient = number, subscriptionId = sim.id), label, sim.label, account)
            val token = approval.issue(prepared)
            mutableState.update { it.copy(prepared = prepared.copy(token = token), notice = "核对号码、SIM 和内容。60 秒内有效，仅执行一次。") }
            CommunicationReply(C.READY, "请在原生通信页面核对并确认；尚未执行")
        } catch (error: IllegalArgumentException) {
            notice(error.message ?: "通信参数无效")
            CommunicationReply(C.DENIED, state.value.notice)
        } catch (_: Exception) {
            notice("通信服务不可用或权限不足；未执行，请检查默认角色和 SIM")
            CommunicationReply(C.DENIED, state.value.notice)
        }
    }

    // Only in-process trusted UI calls this. No approve method exists on the AIDL boundary.
    @Synchronized fun confirm(token: String) {
        val pending = approval.take(token)
        mutableState.update { it.copy(prepared = null) }
        if (pending == null) { notice("确认已过期、已取消或已使用，请重新核对"); return }
        try {
            require(!locked()) { "设备已锁定，请重新解锁确认" }
            val request = pending.request
            val role = if (request.operation == C.CALL) RoleManager.ROLE_DIALER else RoleManager.ROLE_SMS
            require(holdsRole(role)) { "默认角色已改变，操作已取消" }
            refreshSims()
            require(state.value.sims.any { it.id == request.subscriptionId }) { "SIM 已改变，请重新确认" }
            if (request.operation == C.CALL) {
                require(telecom.callCapablePhoneAccounts.contains(pending.account)) { "电话账户已改变" }
                require(!telephony.isEmergencyNumber(request.recipient)) { "请使用系统紧急拨号" }
                telecom.placeCall(Uri.fromParts("tel", request.recipient, null), Bundle().apply {
                    putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, pending.account)
                })
                notice("拨号请求已提交，连接状态以系统通话页面为准；不会自动重拨。")
            } else {
                AgentSmsReceiver.send(app, request)
                notice("短信已提交，尚未确认发送／送达。请查看短信记录；不会自动重发。")
            }
        } catch (error: IllegalArgumentException) {
            notice(error.message ?: "通信条件已变化，请重新确认")
        } catch (_: Exception) {
            notice("通信请求失败或结果未知，请核对通话／短信记录；不会自动重试。")
        }
    }

    fun notice(message: String) { mutableState.update { it.copy(notice = message.take(300)) } }

    private fun resolveRecipient(raw: String): Pair<String, String> {
        CommunicationPolicy.number(raw)?.let { return it to it }
        require(hasPermission(Manifest.permission.READ_CONTACTS)) { "请输入完整号码，或授权后使用真实联系人" }
        val matches = linkedSetOf<String>()
        app.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ?", arrayOf(raw.trim()), null)?.use { cursor ->
            while (cursor.moveToNext()) CommunicationPolicy.number(cursor.getString(0))?.let(matches::add)
        }
        require(matches.size == 1) { "联系人不存在或有多个号码，请通过联系人选择器明确选择" }
        return matches.single() to raw.trim()
    }

    companion object {
        @Volatile private var instance: CommunicationController? = null
        fun get(context: Context): CommunicationController = instance ?: synchronized(this) {
            instance ?: CommunicationController(context.applicationContext).also { instance = it }
        }
    }
}
