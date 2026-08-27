package com.agentos.capability.core

import java.time.Instant

data class ApprovalRequest(
    val token: String,
    val capability: CapabilityId,
    val title: String,
    val explanation: String,
)

sealed interface BrokerOutcome {
    data class Success(val result: CapabilityResult) : BrokerOutcome
    data class ApprovalRequired(val request: ApprovalRequest) : BrokerOutcome
    data class Denied(val reason: String) : BrokerOutcome
    data class Failed(val reason: String) : BrokerOutcome
}

enum class AuditDecision {
    EXECUTED,
    APPROVAL_REQUIRED,
    USER_DENIED,
    POLICY_DENIED,
    FAILED,
}

data class CapabilityAuditEvent(
    val timestamp: Instant,
    val capability: CapabilityId,
    val decision: AuditDecision,
)

class InMemoryCapabilityAuditLog(private val capacity: Int = 200) {
    init {
        require(capacity > 0)
    }

    // ponytail: The prototype keeps metadata only and bounds memory. Move this
    // behind encrypted, user-scoped storage before handling personal data.
    private val events = ArrayDeque<CapabilityAuditEvent>()

    @Synchronized
    fun append(event: CapabilityAuditEvent) {
        if (events.size == capacity) events.removeFirst()
        events.addLast(event)
    }

    @Synchronized
    fun snapshot(): List<CapabilityAuditEvent> = events.toList()
}

class CapabilityBroker(
    private val registry: CapabilityRegistry,
    private val auditLog: InMemoryCapabilityAuditLog = InMemoryCapabilityAuditLog(),
) {
    private val pending = PendingApproval<SystemCapability>()

    @Synchronized
    fun request(id: CapabilityId): BrokerOutcome {
        pending.clear()
        val capability = registry.find(id)
            ?: return BrokerOutcome.Denied("能力未注册").also {
                audit(id, AuditDecision.POLICY_DENIED)
            }

        return when (capability.descriptor.risk) {
            CapabilityRisk.READ_ONLY -> execute(capability)
            CapabilityRisk.BLOCKED -> BrokerOutcome.Denied("该能力被系统策略禁止").also {
                audit(id, AuditDecision.POLICY_DENIED)
            }
            CapabilityRisk.REQUIRES_CONFIRMATION -> {
                val token = pending.issue(capability)
                audit(id, AuditDecision.APPROVAL_REQUIRED)
                BrokerOutcome.ApprovalRequired(
                    ApprovalRequest(
                        token = token,
                        capability = id,
                        title = capability.descriptor.displayName,
                        explanation = "此操作将离开 AgentOS 当前界面，必须由你明确确认。",
                    ),
                )
            }
        }
    }

    @Synchronized
    fun approve(token: String): BrokerOutcome {
        val capability = pending.take(token)
            ?: return BrokerOutcome.Denied("确认请求已失效")
        return execute(capability)
    }

    @Synchronized
    fun deny(token: String): BrokerOutcome {
        val capability = pending.take(token)
            ?: return BrokerOutcome.Denied("确认请求已失效")
        audit(capability.descriptor.id, AuditDecision.USER_DENIED)
        return BrokerOutcome.Denied("用户已取消操作")
    }

    fun auditSnapshot(): List<CapabilityAuditEvent> = auditLog.snapshot()

    @Synchronized
    fun cancelPending() = pending.clear()

    private fun execute(capability: SystemCapability): BrokerOutcome = try {
        BrokerOutcome.Success(capability.execute()).also {
            audit(capability.descriptor.id, AuditDecision.EXECUTED)
        }
    } catch (_: Exception) {
        BrokerOutcome.Failed("能力执行失败").also {
            audit(capability.descriptor.id, AuditDecision.FAILED)
        }
    }

    private fun audit(id: CapabilityId, decision: AuditDecision) {
        auditLog.append(CapabilityAuditEvent(Instant.now(), id, decision))
    }
}
