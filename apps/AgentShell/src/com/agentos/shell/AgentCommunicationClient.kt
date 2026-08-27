package com.agentos.shell

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.agentos.capability.api.CommunicationContract as C
import com.agentos.capability.api.CommunicationReply
import com.agentos.capability.api.IAgentCommunicationService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal suspend fun sendCommunicationCommand(context: Context, command: CommunicationCommand): CommunicationReply = withContext(Dispatchers.IO) {
    val app = context.applicationContext
    val ready = CompletableDeferred<IAgentCommunicationService>()
    val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) { ready.complete(IAgentCommunicationService.Stub.asInterface(service)) }
        override fun onServiceDisconnected(name: ComponentName) = Unit
        override fun onNullBinding(name: ComponentName) { ready.completeExceptionally(IllegalStateException("通信组件不可用")) }
    }
    val bound = app.bindService(Intent().setComponent(ComponentName(C.PACKAGE, C.SERVICE)), connection, Context.BIND_AUTO_CREATE)
    check(bound) { "通信组件尚未安装" }
    try {
        val service = withTimeout(5_000) { ready.await() }
        when (command) {
            is CommunicationCommand.Draft -> service.prepare(command.request)
            is CommunicationCommand.Control -> {
                val id = service.activeCallIds().singleOrNull()
                if (id == null) CommunicationReply(C.DENIED, "没有唯一的当前通话，请在原生通话页面选择")
                else service.controlCall(id, command.action)
            }
            else -> { service.cancelPending(); CommunicationReply(C.DENIED, "待确认通信已取消") }
        }
    } finally { app.unbindService(connection) }
}
