package com.agentos.capability.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SmsMessage
import com.agentos.capability.api.CommunicationRequest
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors

/** SMS persistence is independent of the Shell, model, and network connectivity. */
open class AgentSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        worker.execute {
            try {
                if (!CommunicationController.get(context).holdsRole(android.app.role.RoleManager.ROLE_SMS)) return@execute
                when (intent.action) {
                    Telephony.Sms.Intents.SMS_DELIVER_ACTION -> receiveText(context, intent)
                    Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION -> {
                        // ponytail: Text-SMS preview only. Preserve the WAP notification; a
                        // tested MMS stack is required before this can replace a daily-driver app.
                        val raw = intent.getByteArrayExtra("data") ?: return@execute
                        require(raw.size <= 128 * 1024)
                        context.openFileOutput("pending-mms-${UUID.randomUUID()}.wap", Context.MODE_PRIVATE).use { it.write(raw) }
                        notify(context, "收到彩信通知，预览版不能下载彩信。请使用完整短信应用。")
                    }
                }
            } catch (error: Exception) {
                android.util.Log.w("AgentOSCommunication", "SMS persistence failed: ${error.javaClass.simpleName}")
                notify(context, "短信存储失败，请检查存储空间和默认短信角色。")
            } finally { pending.finish() }
        }
    }

    companion object {
        internal val worker = Executors.newSingleThreadExecutor()
        private const val CHANNEL = "agentos-sms"
        private const val RECEIPTS = "sms-receipts"

        @Suppress("DEPRECATION")
        internal fun send(context: Context, request: CommunicationRequest) {
            val manager = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java).createForSubscriptionId(request.subscriptionId)
                else SmsManager.getSmsManagerForSubscriptionId(request.subscriptionId)
            val parts = manager.divideMessage(request.body)
            require(parts.size in 1..30) { "短信分段过多，请缩短内容" }
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, request.recipient); put(Telephony.Sms.BODY, request.body)
                put(Telephony.Sms.DATE, System.currentTimeMillis()); put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1); put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
                put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_PENDING)
                put(Telephony.Sms.SUBSCRIPTION_ID, request.subscriptionId)
            }
            val uri = requireNotNull(context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)) { "无法保存待发送短信" }
            val id = ContentUris.parseId(uri)
            val nonce = UUID.randomUUID().toString()
            val record = JSONObject().put("nonce", nonce)
                .put("sent", JSONArray(List(parts.size) { SmsReceiptPolicy.PENDING }))
                .put("delivered", JSONArray(List(parts.size) { SmsReceiptPolicy.PENDING }))
            check(context.getSharedPreferences(RECEIPTS, Context.MODE_PRIVATE).edit().putString(id.toString(), record.toString()).commit())
            val sent = ArrayList(parts.indices.map { receipt(context, id, nonce, "sent", it) })
            val delivered = ArrayList(parts.indices.map { receipt(context, id, nonce, "delivered", it) })
            try {
                manager.sendMultipartTextMessage(request.recipient, null, parts, sent, delivered)
            } catch (error: Exception) {
                context.contentResolver.update(uri, ContentValues().apply {
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED)
                }, null, null)
                throw error // Never retry an uncertain/partially completed send.
            }
        }

        private fun receipt(context: Context, id: Long, nonce: String, kind: String, part: Int): PendingIntent {
            val intent = Intent(context, SmsStatusReceiver::class.java)
                .setData(Uri.parse("agentos-sms://receipt/$id/$nonce/$kind/$part"))
            // A mutable, explicit, non-exported receiver is needed for the framework's
            // delivery PDU. Identity is verified against the persisted nonce below.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            return PendingIntent.getBroadcast(context, 0, intent, flags)
        }

        internal fun recordStatus(context: Context, intent: Intent, result: Int) {
            val uri = intent.data ?: return
            if (uri.scheme != "agentos-sms" || uri.host != "receipt") return
            val path = uri.pathSegments
            if (path.size != 4) return
            val id = path[0].toLongOrNull()?.takeIf { it > 0 } ?: return
            val prefs = context.getSharedPreferences(RECEIPTS, Context.MODE_PRIVATE)
            val record = prefs.getString(id.toString(), null)?.let(::JSONObject) ?: return
            if (record.optString("nonce") != path[1] || path[2] !in setOf("sent", "delivered")) return
            val part = path[3].toIntOrNull() ?: return
            val states = record.getJSONArray(path[2])
            if (part !in 0 until states.length() || states.getInt(part) != SmsReceiptPolicy.PENDING) return
            val value = if (path[2] == "sent") result else {
                val pdu = intent.getByteArrayExtra("pdu") ?: return
                val message = SmsMessage.createFromPdu(pdu, intent.getStringExtra("format") ?: "3gpp") ?: return
                when (message.status) { in 0..31 -> SmsReceiptPolicy.OK; in 64..255 -> 1; else -> return }
            }
            states.put(part, value)
            check(prefs.edit().putString(id.toString(), record.toString()).commit())
            val sent = record.getJSONArray("sent").let { array -> List(array.length()) { array.getInt(it) } }
            val delivered = record.getJSONArray("delivered").let { array -> List(array.length()) { array.getInt(it) } }
            val values = ContentValues()
            if (SmsReceiptPolicy.terminal(sent)) values.put(Telephony.Sms.TYPE,
                if (sent.all { it == SmsReceiptPolicy.OK }) Telephony.Sms.MESSAGE_TYPE_SENT else Telephony.Sms.MESSAGE_TYPE_FAILED)
            if (SmsReceiptPolicy.terminal(delivered)) values.put(Telephony.Sms.STATUS,
                if (delivered.all { it == SmsReceiptPolicy.OK }) Telephony.Sms.STATUS_COMPLETE else Telephony.Sms.STATUS_FAILED)
            if (values.size() > 0) context.contentResolver.update(ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id), values, null, null)
            CommunicationController.get(context).notice(SmsReceiptPolicy.sentState(sent))
            // Remove only terminal metadata; uncertain sends stay pending and are never replayed.
            if (SmsReceiptPolicy.terminal(sent) && SmsReceiptPolicy.terminal(delivered)) prefs.edit().remove(id.toString()).apply()
        }

        private fun receiveText(context: Context, intent: Intent) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isEmpty()) return
            val address = messages.first().originatingAddress ?: "未知发送方"
            require(messages.all { it.originatingAddress == messages.first().originatingAddress })
            val body = messages.joinToString("") { it.messageBody.orEmpty() }
            require(body.length <= 64_000)
            val sentAt = messages.first().timestampMillis
            val sub = intent.getIntExtra("subscription", -1)
            val duplicate = context.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms._ID), "address = ? AND body = ? AND date_sent = ? AND sub_id = ?",
                arrayOf(address, body, sentAt.toString(), sub.toString()), null)?.use { it.moveToFirst() } == true
            if (duplicate) return
            checkNotNull(context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address); put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis()); put(Telephony.Sms.DATE_SENT, sentAt)
                put(Telephony.Sms.READ, 0); put(Telephony.Sms.SEEN, 0); put(Telephony.Sms.SUBSCRIPTION_ID, sub)
            }))
            notify(context, "收到文本短信，解锁后查看。内容不会自动朗读或上传模型。")
        }

        internal fun notify(context: Context, text: String) {
            CommunicationController.get(context).notice(text)
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "文本短信", NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(context, 0, Intent(context, CommunicationActivity::class.java)
                .setAction("com.agentos.communication.SMS"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            runCatching { manager.notify(4102, Notification.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.sym_action_chat).setContentTitle("AgentOS 短信")
                .setContentText(text).setContentIntent(open).setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE).build()) }
        }
    }
}

class AgentMmsReceiver : AgentSmsReceiver()

class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = resultCode
        val pending = goAsync()
        AgentSmsReceiver.worker.execute {
            try { AgentSmsReceiver.recordStatus(context, intent, result) }
            catch (_: Exception) { CommunicationController.get(context).notice("短信回执暂未保存；结果未知，请核对短信记录，勿自动重发。") }
            finally { pending.finish() }
        }
    }
}

/** Android's reject-with-message entry point must still pass native review. */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AgentSmsReceiver.notify(this, "请打开通信页面，核对收件人与短信后发送；未自动回复。")
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
