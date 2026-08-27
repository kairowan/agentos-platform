package com.agentos.shell

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.agentos.capability.api.AgentNotificationEvent
import com.agentos.capability.api.CapabilityContract
import com.agentos.capability.api.CapabilityReply
import com.agentos.capability.api.IAgentCapabilityService
import com.agentos.capability.api.IAgentEventListener
import com.agentos.capability.core.ApprovalRequest
import com.agentos.capability.core.BrokerOutcome
import com.agentos.capability.core.CapabilityBroker
import com.agentos.capability.core.CapabilityId
import com.agentos.capability.core.CapabilityResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

interface CapabilityGateway {
    val notificationEvents: Flow<AgentNotificationEvent>
    suspend fun request(id: CapabilityId): BrokerOutcome
    suspend fun approve(token: String): BrokerOutcome
    suspend fun deny(token: String): BrokerOutcome
    suspend fun cancelPending()
    fun close() = Unit
}

class LocalCapabilityGateway(private val broker: CapabilityBroker) : CapabilityGateway {
    override val notificationEvents: Flow<AgentNotificationEvent> = emptyFlow()
    override suspend fun request(id: CapabilityId) = broker.request(id)
    override suspend fun approve(token: String) = broker.approve(token)
    override suspend fun deny(token: String) = broker.deny(token)
    override suspend fun cancelPending() = broker.cancelPending()
}

class AgentCapabilityClient(context: Context) : CapabilityGateway {
    private val applicationContext = context.applicationContext
    @Volatile private var remote = CompletableDeferred<IAgentCapabilityService>()
    private val mutableNotificationEvents = MutableSharedFlow<AgentNotificationEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val notificationEvents: Flow<AgentNotificationEvent> = mutableNotificationEvents
    @Volatile private var connectedService: IAgentCapabilityService? = null

    private val eventListener = object : IAgentEventListener.Stub() {
        override fun onNotificationEvent(event: AgentNotificationEvent) {
            mutableNotificationEvents.tryEmit(event)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val service = IAgentCapabilityService.Stub.asInterface(binder)
            try {
                service.registerEventListener(eventListener)
                connectedService = service
                remote.complete(service)
            } catch (error: Exception) {
                remote.completeExceptionally(error)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            connectedService = null
            remote = CompletableDeferred()
        }
    }

    private val isBound = run {
        // Android reconnects this binding after process death; never replay an IPC.
        val intent = Intent().setComponent(
            ComponentName(CapabilityContract.SERVICE_PACKAGE, CapabilityContract.SERVICE_CLASS),
        )
        applicationContext.bindService(intent, connection, Context.BIND_AUTO_CREATE).also { bound ->
            if (!bound) {
                remote.completeExceptionally(IllegalStateException("Capability Service is unavailable"))
            }
        }
    }

    override suspend fun request(id: CapabilityId): BrokerOutcome =
        call { it.requestCapability(id.value) }

    override suspend fun approve(token: String): BrokerOutcome = call { it.approve(token) }

    override suspend fun deny(token: String): BrokerOutcome = call { it.deny(token) }

    override suspend fun cancelPending() {
        withContext(Dispatchers.IO) {
            try {
                withTimeout(CONNECT_TIMEOUT_MILLIS) { remote.await() }.cancelPending()
            } catch (_: TimeoutCancellationException) {
                // Unreachable service: expiry still applies; do not skip local invalidation.
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The service also expires approvals and clears them on the next request.
            }
        }
    }

    override fun close() {
        connectedService?.let { runCatching { it.unregisterEventListener(eventListener) } }
        if (isBound) runCatching { applicationContext.unbindService(connection) }
    }

    private suspend fun call(block: (IAgentCapabilityService) -> CapabilityReply): BrokerOutcome = withContext(Dispatchers.IO) {
        val service = try {
            withTimeout(CONNECT_TIMEOUT_MILLIS) { remote.await() }
        } catch (_: TimeoutCancellationException) {
            return@withContext BrokerOutcome.Failed("连接 Capability Service 超时，未提交")
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return@withContext BrokerOutcome.Failed("Capability Service 不可用，未提交") }
        // After dispatch, a broken Binder or malformed reply is an UNKNOWN outcome,
        // not proof of failure. The task journal handles it without retrying.
        try {
            coroutineContext.ensureActive()
            block(service).toOutcome()
        } catch (_: SecurityException) {
            BrokerOutcome.Denied("调用方未获 Capability Broker 授权")
        }
    }

    private fun CapabilityReply.toOutcome(): BrokerOutcome {
        require(factKeys.size == factValues.size && factKeys.size <= MAX_FACTS)
        require(listOf(title, message, token).all { it.length <= MAX_TEXT_LENGTH })
        val capability = capabilityId.takeIf(String::isNotEmpty)?.let(CapabilityId::fromWire)

        return when (status) {
            CapabilityContract.STATUS_SUCCESS -> BrokerOutcome.Success(
                CapabilityResult(
                    capability = requireNotNull(capability),
                    title = title,
                    facts = factKeys.zip(factValues),
                ),
            )
            CapabilityContract.STATUS_APPROVAL_REQUIRED -> BrokerOutcome.ApprovalRequired(
                ApprovalRequest(
                    token = token,
                    capability = requireNotNull(capability),
                    title = title,
                    explanation = message,
                ),
            )
            CapabilityContract.STATUS_DENIED -> BrokerOutcome.Denied(message)
            else -> BrokerOutcome.Failed(message.ifBlank { "Capability Service 返回无效结果" })
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MILLIS = 5_000L
        private const val MAX_FACTS = 32
        private const val MAX_TEXT_LENGTH = 4_096
    }
}
