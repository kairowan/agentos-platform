package com.agentos.capability.service

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import com.agentos.capability.api.CapabilityContract
import com.agentos.capability.api.CapabilityReply
import com.agentos.capability.api.IAgentCapabilityService
import com.agentos.capability.api.IAgentEventListener
import com.agentos.capability.core.ApprovalRequest
import com.agentos.capability.core.BrokerOutcome
import com.agentos.capability.core.CapabilityBroker
import com.agentos.capability.core.CapabilityId
import com.agentos.capability.core.CapabilityRegistry
import com.agentos.capability.core.DeviceCapability
import com.agentos.capability.core.OpenWifiSettingsCapability
import com.agentos.capability.core.StorageCapability
import com.agentos.capability.core.TimeCapability

class AgentCapabilityService : Service() {
    private val callerPolicy = CallerIdentityPolicy(ALLOWED_CALLER_PACKAGE)
    private val broker by lazy {
        CapabilityBroker(
            CapabilityRegistry(
                listOf(
                    TimeCapability(),
                    DeviceCapability(),
                    StorageCapability(this),
                    OpenWifiSettingsCapability(this),
                ),
            ),
        )
    }

    private val binder = object : IAgentCapabilityService.Stub() {
        override fun cancelPending() {
            enforceAuthorizedCaller()
            broker.cancelPending()
        }

        override fun requestCapability(capabilityId: String): CapabilityReply {
            enforceAuthorizedCaller()
            val id = capabilityId.takeIf { it.length <= MAX_INPUT_LENGTH }
                ?.let(CapabilityId::fromWire)
                ?: return denied("未知或无效的能力标识")
            return broker.request(id).toReply()
        }

        override fun approve(token: String): CapabilityReply {
            enforceAuthorizedCaller()
            return if (token.length in 1..MAX_INPUT_LENGTH) {
                broker.approve(token).toReply()
            } else {
                denied("确认请求已失效")
            }
        }

        override fun deny(token: String): CapabilityReply {
            enforceAuthorizedCaller()
            return if (token.length in 1..MAX_INPUT_LENGTH) {
                broker.deny(token).toReply()
            } else {
                denied("确认请求已失效")
            }
        }

        override fun registerEventListener(listener: IAgentEventListener) {
            enforceAuthorizedCaller()
            AgentEventBus.register(listener)
        }

        override fun unregisterEventListener(listener: IAgentEventListener) {
            enforceAuthorizedCaller()
            AgentEventBus.unregister(listener)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun enforceAuthorizedCaller() {
        val callingUid = Binder.getCallingUid()
        val packages = packageManager.getPackagesForUid(callingUid)?.toList().orEmpty()
        val signatureMatches = packages.singleOrNull()?.let {
            packageManager.checkSignatures(it, packageName) == PackageManager.SIGNATURE_MATCH
        } == true
        if (!callerPolicy.isAuthorized(packages, signatureMatches)) {
            throw SecurityException("Caller is not authorized to use the AgentOS Capability Broker")
        }
    }

    private fun BrokerOutcome.toReply(): CapabilityReply = when (this) {
        is BrokerOutcome.Success -> CapabilityReply(
            status = CapabilityContract.STATUS_SUCCESS,
            capabilityId = result.capability.value,
            title = result.title,
            message = "",
            token = "",
            factKeys = result.facts.map { it.first },
            factValues = result.facts.map { it.second },
        )
        is BrokerOutcome.ApprovalRequired -> request.toReply()
        is BrokerOutcome.Denied -> denied(reason)
        is BrokerOutcome.Failed -> CapabilityReply(
            CapabilityContract.STATUS_FAILED, "", "", reason, "", emptyList(), emptyList(),
        )
    }

    private fun ApprovalRequest.toReply() = CapabilityReply(
        status = CapabilityContract.STATUS_APPROVAL_REQUIRED,
        capabilityId = capability.value,
        title = title,
        message = explanation,
        token = token,
        factKeys = emptyList(),
        factValues = emptyList(),
    )

    private fun denied(reason: String) = CapabilityReply(
        CapabilityContract.STATUS_DENIED, "", "", reason, "", emptyList(), emptyList(),
    )

    companion object {
        private const val ALLOWED_CALLER_PACKAGE = "com.agentos.shell"
        private const val MAX_INPUT_LENGTH = 128
    }
}
