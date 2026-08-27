package com.agentos.capability.service

import android.Manifest
import android.app.Application
import android.app.KeyguardManager
import android.app.role.RoleManager
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Settings
import android.provider.Telephony
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.agentos.capability.api.CommunicationContract as C
import com.agentos.capability.api.CommunicationRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class SmsPreview(val id: Long, val address: String, val body: String, val status: String)

internal class CommunicationViewModel(app: Application) : AndroidViewModel(app) {
    val controller = CommunicationController.get(app)
    private val mutableMessages = MutableStateFlow<List<SmsPreview>>(emptyList())
    val messages = mutableMessages.asStateFlow()
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }
    init { runCatching { app.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer) } }

    fun refresh() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                controller.refreshSims()
                mutableMessages.value = if (controller.locked() || !controller.hasPermission(Manifest.permission.READ_SMS)) emptyList() else runCatching {
                    val app = getApplication<Application>()
                    buildList {
                        app.contentResolver.query(Telephony.Sms.CONTENT_URI.buildUpon().appendQueryParameter("limit", "100").build(),
                            arrayOf("_id", "address", "body", "type", "status"), null, null, "date DESC")?.use { cursor ->
                            while (size < 100 && cursor.moveToNext()) add(SmsPreview(cursor.getLong(0), cursor.getString(1).orEmpty(), cursor.getString(2).orEmpty(),
                                when (cursor.getInt(3)) {
                                    Telephony.Sms.MESSAGE_TYPE_INBOX -> "收到"
                                    Telephony.Sms.MESSAGE_TYPE_SENT -> if (cursor.getInt(4) == Telephony.Sms.STATUS_COMPLETE) "已送达（不是已读）" else "已发送，送达未确认"
                                    Telephony.Sms.MESSAGE_TYPE_FAILED -> "发送失败／可能部分发送"
                                    else -> "等待结果／结果未知；不会自动重发"
                                }))
                        }
                    }
                }.getOrDefault(emptyList())
            }
        }
    }
    fun prepare(request: CommunicationRequest) { viewModelScope.launch(Dispatchers.IO) { controller.prepare(request) } }
    fun confirm(token: String) { viewModelScope.launch(Dispatchers.IO) { controller.confirm(token); refresh() } }
    override fun onCleared() { runCatching { getApplication<Application>().contentResolver.unregisterContentObserver(observer) } }
}

class CommunicationActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[CommunicationViewModel::class.java] }
    private var draftNumber by mutableStateOf("")
    private var draftBody by mutableStateOf("")
    private var locked by mutableStateOf(true)
    private val roleResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { model.refresh() }
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { model.refresh() }
    private val contact = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.data?.let { uri -> runCatching {
            contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use {
                if (it.moveToFirst()) { draftNumber = it.getString(0).orEmpty().take(100); model.controller.cancel() }
            }
        } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 31) window.setHideOverlayWindows(true)
        readDraft(intent)
        setContent {
            val state by model.controller.state.collectAsStateWithLifecycle()
            val calls by AgentInCallService.calls.collectAsStateWithLifecycle()
            val messages by model.messages.collectAsStateWithLifecycle()
            var smsWarning by remember { mutableStateOf(false) }
            var selectedSim by rememberSaveable { mutableIntStateOf(-1) }
            LaunchedEffect(state.draft) {
                state.draft?.let { draftNumber = it.recipient; draftBody = it.body; selectedSim = it.subscriptionId }
            }
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF49E4CE), background = Color(0xFF071216), surface = Color(0xFF112126))) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                    LazyColumn(Modifier.weight(1f).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("通信", style = MaterialTheme.typography.headlineMedium)
                                TextButton(onClick = { model.controller.cancel(); finish() }) { Text("返回智能体") }
                            }
                            Text("原生电话与文本短信 · 不依赖云端模型", color = MaterialTheme.colorScheme.primary)
                        }
                        items(calls, key = { "call-${it.id}" }) { call -> CallCard(call, locked) }
                        if (locked) item {
                            Text("设备已锁定，短信、联系人与发送内容已隐藏。")
                            Button(onClick = { getSystemService(KeyguardManager::class.java).requestDismissKeyguard(this@CommunicationActivity, null) }) { Text("解锁查看") }
                        } else {
                            item {
                                Text(state.notice)
                                Text("实验性预览：彩信、RCS、AI 代接和通话录音未实现，请勿替换日常主力短信应用。", style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { requestRole(RoleManager.ROLE_DIALER) }) { Text(if (model.controller.holdsRole(RoleManager.ROLE_DIALER)) "已是默认电话" else "设置默认电话") }
                                    OutlinedButton(onClick = { smsWarning = true }) { Text(if (model.controller.holdsRole(RoleManager.ROLE_SMS)) "已是默认短信" else "设置默认短信") }
                                }
                                TextButton(onClick = ::requestCommunicationPermissions) { Text("授权通信权限") }
                            }
                            item {
                                OutlinedTextField(draftNumber, { draftNumber = it.take(100); model.controller.cancel() }, label = { Text("完整号码或联系人姓名") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                TextButton(onClick = { runCatching { contact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }
                                    .onFailure { model.controller.notice("没有联系人选择器，请输入完整号码") } }) { Text("选择真实联系人") }
                                Text("发送／拨号 SIM（双卡必须明确选择）")
                                state.sims.forEach { sim ->
                                    Row {
                                        RadioButton(selected = selectedSim == sim.id, onClick = { selectedSim = sim.id; model.controller.cancel() })
                                        TextButton(onClick = { selectedSim = sim.id; model.controller.cancel() }) { Text(sim.label) }
                                    }
                                }
                                Button(onClick = { model.prepare(CommunicationRequest(C.CALL, draftNumber, "", selectedSim)) }, enabled = draftNumber.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("核对并拨号") }
                            }
                            item {
                                OutlinedTextField(draftBody, { draftBody = it.take(CommunicationPolicy.MAX_MESSAGE); model.controller.cancel() }, label = { Text("短信内容") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                                Button(onClick = { model.prepare(CommunicationRequest(C.SMS, draftNumber, draftBody, selectedSim)) }, enabled = draftNumber.isNotBlank() && draftBody.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("核对并发送短信") }
                            }
                            item {
                                Text("短信记录 · 最近 100 条", style = MaterialTheme.typography.titleLarge)
                                TextButton(onClick = model::refresh) { Text("刷新短信记录") }
                            }
                            items(messages, key = { "sms-${it.id}" }) { message ->
                                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(message.address, fontWeight = FontWeight.Bold)
                                    Text(message.body)
                                    Text(message.status, color = MaterialTheme.colorScheme.primary)
                                    TextButton(onClick = { draftNumber = message.address; draftBody = ""; model.controller.cancel() }) { Text("准备回复（不会自动发送）") }
                                } }
                            }
                        }
                        item {
                            TextButton(onClick = { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }) { Text("恢复原电话／短信／桌面应用") }
                            if (Build.VERSION.SDK_INT >= 34) TextButton(onClick = { startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))) }) { Text("来电全屏显示设置") }
                        }
                    }
                    OutlinedButton(onClick = ::openEmergencyDialer, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) { Text("系统紧急拨号") }
                    }
                }
                state.prepared?.takeUnless { locked }?.let { pending ->
                    AlertDialog(onDismissRequest = model.controller::cancel,
                        title = { Text(if (pending.request.operation == C.CALL) "确认拨打电话" else "确认发送短信") },
                        text = { Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("${pending.label}\n${pending.request.recipient}\n${pending.simLabel}")
                            if (pending.request.operation == C.SMS) Text(pending.request.body)
                            Text("可能产生运营商费用。60 秒内有效，仅执行一次。离开页面即取消。")
                        } },
                        confirmButton = { Button(onClick = { model.confirm(pending.token) }) { Text("确认执行一次") } },
                        dismissButton = { TextButton(onClick = model.controller::cancel) { Text("取消") } })
                }
                if (smsWarning) AlertDialog(onDismissRequest = { smsWarning = false }, title = { Text("仅用于测试设备") },
                    text = { Text("本预览仅实现文本短信；彩信只保留通知，不能下载内容，也不支持 RCS。请勿在承载重要通信的主力设备启用。退出测试前恢复原短信应用。") },
                    confirmButton = { TextButton(onClick = { smsWarning = false; requestRole(RoleManager.ROLE_SMS) }) { Text("了解限制，选择默认短信") } },
                    dismissButton = { TextButton(onClick = { smsWarning = false }) { Text("取消") } })
            }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); readDraft(intent) }
    override fun onResume() { super.onResume(); locked = model.controller.locked(); model.refresh() }
    override fun onStop() { model.controller.cancel(); super.onStop() }

    private fun readDraft(intent: Intent) {
        setShowWhenLocked(intent.action == AgentInCallService.SHOW_CALL)
        setTurnScreenOn(intent.action == AgentInCallService.SHOW_CALL)
        if (intent.action in setOf(Intent.ACTION_DIAL, Intent.ACTION_SENDTO)) {
            model.controller.cancel()
            draftNumber = intent.data?.schemeSpecificPart?.substringBefore('?').orEmpty().take(100)
            draftBody = intent.getStringExtra("sms_body").orEmpty().take(CommunicationPolicy.MAX_MESSAGE)
            model.controller.externalDraft(CommunicationRequest(if (intent.action == Intent.ACTION_DIAL) C.CALL else C.SMS, draftNumber, draftBody))
            if (intent.data?.scheme in setOf("mms", "mmsto")) model.controller.notice("彩信不受支持，此处只能准备文本短信。")
        }
    }

    private fun requestRole(role: String) {
        val manager = getSystemService(RoleManager::class.java)
        if (!manager.isRoleAvailable(role)) { model.controller.notice("此设备不提供该默认角色"); return }
        if (!manager.isRoleHeld(role)) roleResult.launch(manager.createRequestRoleIntent(role))
    }

    private fun requestCommunicationPermissions() {
        permissions.launch(buildList {
            add(Manifest.permission.READ_PHONE_STATE); add(Manifest.permission.CALL_PHONE); add(Manifest.permission.READ_CONTACTS)
            if (model.controller.holdsRole(RoleManager.ROLE_SMS)) {
                add(Manifest.permission.READ_SMS); add(Manifest.permission.SEND_SMS); add(Manifest.permission.RECEIVE_SMS)
                add(Manifest.permission.RECEIVE_MMS); add(Manifest.permission.RECEIVE_WAP_PUSH)
            }
            if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray())
    }

    private fun openEmergencyDialer() {
        model.controller.cancel()
        // No model, generated number, or confirmation token is in this path.
        // createLaunchEmergencyDialerIntent is SystemApi, not available to the
        // ordinary APK. Resolve the platform intent to a preinstalled component only.
        val intent = Intent("android.intent.action.DIAL_EMERGENCY")
        val target = packageManager.queryIntentActivities(intent, 0).map { it.activityInfo }
            .firstOrNull { it.packageName != packageName &&
                it.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0 }
        if (target == null) { model.controller.notice("无法打开系统紧急拨号器；请使用锁屏紧急呼叫入口。"); return }
        runCatching { startActivity(intent.setClassName(target.packageName, target.name)) }
            .onFailure { model.controller.notice("请使用锁屏紧急呼叫入口。") }
    }
}

@Composable
private fun CallCard(call: ActiveAgentCall, locked: Boolean) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (locked) "电话" else call.label, style = MaterialTheme.typography.titleLarge)
        Text(when (call.state) { Call.STATE_RINGING -> "来电"; Call.STATE_ACTIVE -> "通话中"; Call.STATE_HOLDING -> "已保持"; Call.STATE_DISCONNECTED -> "已结束"; else -> "连接中" })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (call.state == Call.STATE_RINGING) {
                Button(onClick = { AgentInCallService.control(call.id, C.ANSWER) }) { Text("接听") }
                OutlinedButton(onClick = { AgentInCallService.control(call.id, C.REJECT) }) { Text("拒接") }
            } else if (call.state != Call.STATE_DISCONNECTED) Button(onClick = { AgentInCallService.control(call.id, C.HANG_UP) }) { Text("挂断") }
        }
        if (call.state in setOf(Call.STATE_ACTIVE, Call.STATE_HOLDING)) {
            Row {
                TextButton(onClick = { AgentInCallService.mute(true) }) { Text("静音") }
                TextButton(onClick = { AgentInCallService.mute(false) }) { Text("取消静音") }
                if (call.canHold) TextButton(onClick = { AgentInCallService.hold(call.id, call.state != Call.STATE_HOLDING) }) { Text(if (call.state == Call.STATE_HOLDING) "恢复" else "保持") }
            }
            Row {
                TextButton(onClick = { AgentInCallService.audio(CallAudioState.ROUTE_SPEAKER) }) { Text("免提") }
                TextButton(onClick = { AgentInCallService.audio(CallAudioState.ROUTE_EARPIECE) }) { Text("听筒") }
                TextButton(onClick = { AgentInCallService.audio(CallAudioState.ROUTE_BLUETOOTH) }) { Text("蓝牙") }
            }
            listOf("123", "456", "789", "*0#").forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { digit -> OutlinedButton(onClick = { AgentInCallService.dtmf(call.id, digit) }) { Text(digit.toString()) } }
            } }
        }
    } }
}
