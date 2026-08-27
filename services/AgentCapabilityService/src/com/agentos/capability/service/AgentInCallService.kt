package com.agentos.capability.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import com.agentos.capability.api.CommunicationContract as C
import com.agentos.capability.api.CommunicationReply
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

internal data class ActiveAgentCall(val id: String, val label: String, val state: Int, val canHold: Boolean)

class AgentInCallService : InCallService() {
    private val live = linkedMapOf<String, Call>()
    private val callbacks = mutableMapOf<Call, Call.Callback>()
    override fun onCreate() { super.onCreate(); instance = this }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        live[UUID.randomUUID().toString()] = call
        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) = publish()
            override fun onDetailsChanged(call: Call, details: Call.Details) = publish()
        }
        callbacks[call] = callback
        call.registerCallback(callback, main)
        publish()
    }

    override fun onCallRemoved(call: Call) {
        callbacks.remove(call)?.let(call::unregisterCallback)
        live.entries.removeAll { it.value == call }
        publish()
        super.onCallRemoved(call)
    }

    override fun onDestroy() {
        callbacks.forEach { (call, callback) -> call.unregisterCallback(callback) }
        callbacks.clear(); live.clear(); publish()
        if (instance === this) instance = null
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun publish() {
        mutableCalls.value = live.map { (id, call) ->
            ActiveAgentCall(id, call.details.callerDisplayName?.toString()?.takeIf { it.isNotBlank() }?.take(80)
                ?: call.details.handle?.schemeSpecificPart?.takeIf { it.isNotBlank() }?.take(80) ?: "未知来电", call.state,
                call.details.can(Call.Details.CAPABILITY_HOLD))
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        val selected = mutableCalls.value.firstOrNull { it.state == Call.STATE_RINGING }
            ?: mutableCalls.value.firstOrNull { it.state != Call.STATE_DISCONNECTED }
        if (selected == null) { notificationManager.cancel(NOTIFICATION_ID); return }
        notificationManager.createNotificationChannel(NotificationChannel(CHANNEL, "电话呼入与通话", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, CommunicationActivity::class.java).setAction(SHOW_CALL)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val ringing = selected.state == Call.STATE_RINGING
        val builder = Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(if (ringing) "电话来电" else "通话进行中")
            .setContentText("打开原生通话控制").setContentIntent(open)
            .setCategory(Notification.CATEGORY_CALL).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOngoing(true).setOnlyAlertOnce(true)
        if (Build.VERSION.SDK_INT >= 31 && ringing) {
            val person = Person.Builder().setName("AgentOS 电话").setImportant(true).build()
            builder.setStyle(Notification.CallStyle.forIncomingCall(person,
                action(this, selected.id, C.REJECT), action(this, selected.id, C.ANSWER)))
        } else {
            // Telecom binds this service; it is not a started foreground service.
            // Android rejects ongoing CallStyle without FGS/FSI. A normal ongoing
            // notification retains native hang-up controls without opening new UI.
            if (ringing) builder.addAction(Notification.Action.Builder(null, "接听", action(this, selected.id, C.ANSWER)).build())
            builder.addAction(Notification.Action.Builder(null, if (ringing) "拒接" else "挂断",
                action(this, selected.id, if (ringing) C.REJECT else C.HANG_UP)).build())
        }
        if (ringing) builder.setFullScreenIntent(open, true)
        runCatching { notificationManager.notify(NOTIFICATION_ID, builder.build()) }
            .onFailure { CommunicationController.get(this).notice("系统未允许通话通知，请打开原生通信页面控制当前通话。") }
    }

    @Suppress("DEPRECATION")
    private fun perform(id: String, action: Int) {
        val call = live[id] ?: return
        if (!CommunicationPolicy.canControl(call.state, action)) return
        when (action) {
            C.ANSWER -> call.answer(VideoProfile.STATE_AUDIO_ONLY)
            C.REJECT -> call.reject(false, null)
            C.HANG_UP -> call.disconnect()
        }
    }

    companion object {
        const val SHOW_CALL = "com.agentos.communication.SHOW_CALL"
        private const val CHANNEL = "agentos-calls"
        private const val NOTIFICATION_ID = 4101
        private val main = Handler(Looper.getMainLooper())
        @Volatile private var instance: AgentInCallService? = null
        private val mutableCalls = MutableStateFlow<List<ActiveAgentCall>>(emptyList())
        internal val calls = mutableCalls.asStateFlow()

        internal fun control(id: String, action: Int): CommunicationReply {
            val call = calls.value.singleOrNull { it.id == id }
            if (call == null || !CommunicationPolicy.canControl(call.state, action)) return CommunicationReply(C.DENIED, "通话状态已改变，没有执行")
            main.post { runCatching { instance?.perform(id, action) } }
            return CommunicationReply(C.SUBMITTED, "已提交通话控制，实际状态以系统回调为准")
        }

        @Suppress("DEPRECATION")
        internal fun mute(value: Boolean) { instance?.setMuted(value) }
        @Suppress("DEPRECATION")
        internal fun audio(route: Int) {
            val service = instance ?: return
            if (((service.callAudioState?.supportedRouteMask ?: 0) and route) != 0) runCatching { service.setAudioRoute(route) }
        }
        internal fun hold(id: String, held: Boolean) {
            val call = instance?.live?.get(id) ?: return
            if (held) { if (call.details.can(Call.Details.CAPABILITY_HOLD)) call.hold() } else call.unhold()
        }
        internal fun dtmf(id: String, digit: Char) {
            if (digit !in "0123456789*#") return
            val call = instance?.live?.get(id) ?: return
            call.playDtmfTone(digit)
            main.postDelayed({ call.stopDtmfTone() }, 150)
        }

        private fun action(context: Context, id: String, action: Int): PendingIntent = PendingIntent.getBroadcast(context, action,
            Intent(context, CallActionReceiver::class.java).setAction("com.agentos.call.$id.$action")
                .putExtra("callId", id).putExtra("control", action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AgentInCallService.control(intent.getStringExtra("callId").orEmpty(), intent.getIntExtra("control", -1))
    }
}
