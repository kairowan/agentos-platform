package com.agentos.shell

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import com.agentos.capability.api.AppBridgeContract
import com.agentos.capability.api.AppBridgeReply
import com.agentos.capability.api.AppDescriptor
import com.agentos.capability.api.IAgentAppBridgeService
import com.agentos.capability.api.SemanticSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal class AgentAppBridgeClient(context: Context) : AutoCloseable {
    private val applicationContext = context.applicationContext
    private val remote = CompletableDeferred<IAgentAppBridgeService>()
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            remote.complete(IAgentAppBridgeService.Stub.asInterface(binder))
        }
        override fun onServiceDisconnected(name: ComponentName) = Unit
    }
    private val isBound = applicationContext.bindService(
        Intent().setComponent(ComponentName(AppBridgeContract.SERVICE_PACKAGE, AppBridgeContract.SERVICE_CLASS)),
        connection,
        Context.BIND_AUTO_CREATE or if (Build.VERSION.SDK_INT >= 34) Context.BIND_ALLOW_ACTIVITY_STARTS else 0,
    ).also { if (!it) remote.completeExceptionally(IllegalStateException("App Bridge unavailable")) }

    suspend fun apps(): List<AppDescriptor> = call { it.listLaunchableApps() }
    suspend fun requestLaunch(packageName: String): AppBridgeReply = call { it.requestLaunch(packageName) }
    suspend fun snapshot(): SemanticSnapshot = call { it.semanticSnapshot }
    suspend fun requestAction(packageName: String, path: String, action: Int, value: String = ""): AppBridgeReply =
        call { it.requestNodeAction(packageName, path, action, value) }
    suspend fun approve(token: String): AppBridgeReply = call { it.approve(token) }
    suspend fun deny(token: String): AppBridgeReply = call { it.deny(token) }

    private suspend fun <T> call(block: (IAgentAppBridgeService) -> T): T = withContext(Dispatchers.IO) {
        block(withTimeout(CONNECT_TIMEOUT_MILLIS) { remote.await() })
    }

    override fun close() { if (isBound) runCatching { applicationContext.unbindService(connection) } }

    private companion object { const val CONNECT_TIMEOUT_MILLIS = 5_000L }
}
