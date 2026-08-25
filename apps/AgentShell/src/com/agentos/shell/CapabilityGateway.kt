package com.agentos.shell

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.agentos.capability.api.CapabilityContract
import com.agentos.capability.api.CapabilityReply
import com.agentos.capability.api.IAgentCapabilityService
import com.agentos.capability.core.ApprovalRequest
import com.agentos.capability.core.BrokerOutcome
import com.agentos.capability.core.CapabilityBroker
import com.agentos.capability.core.CapabilityId
import com.agentos.capability.core.CapabilityResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

interface CapabilityGateway {
    suspend fun request(id: CapabilityId): BrokerOutcome
    suspend fun approve(token: String): BrokerOutcome
    suspend fun deny(token: String): BrokerOutcome
}

class LocalCapabilityGateway(private val broker: CapabilityBroker) : CapabilityGateway {
    override suspend fun request(id: CapabilityId) = broker.request(id)
    override suspend fun approve(token: String) = broker.approve(token)
    override suspend fun deny(token: String) = broker.deny(token)
}

class AgentCapabilityClient(context: Context) : CapabilityGateway {
    private val applicationContext = context.applicationContext
    private val remote = CompletableDeferred<IAgentCapabilityService>()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            remote.complete(IAgentCapabilityService.Stub.asInterface(binder))
        }

        override fun onServiceDisconnected(name: ComponentName) = Unit
    }

    init {
        // ponytail: One process-lifetime binding is enough for the single HOME
        // client. Add reconnect/backoff when more clients or service upgrades exist.
        val intent = Intent().setComponent(
            ComponentName(CapabilityContract.SERVICE_PACKAGE, CapabilityContract.SERVICE_CLASS),
        )
        if (!applicationContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
            remote.completeExceptionally(IllegalStateException("Capability Service is unavailable"))
        }
    }

    override suspend fun request(id: CapabilityId): BrokerOutcome =
        call { it.requestCapability(id.value) }

    override suspend fun approve(token: String): BrokerOutcome = call { it.approve(token) }

    override suspend fun deny(token: String): BrokerOutcome = call { it.deny(token) }

    private suspend fun call(block: (IAgentCapabilityService) -> CapabilityReply): BrokerOutcome =
        try {
            withContext(Dispatchers.IO) {
                block(withTimeout(CONNECT_TIMEOUT_MILLIS) { remote.await() }).toOutcome()
            }
        } catch (_: SecurityException) {
            BrokerOutcome.Denied("调用方未获 Capability Broker 授权")
        } catch (_: TimeoutCancellationException) {
            BrokerOutcome.Failed("连接 Capability Service 超时")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            BrokerOutcome.Failed("Capability Service 不可用")
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
