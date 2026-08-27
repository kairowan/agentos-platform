package com.agentos.shell

import com.agentos.capability.core.AuditDecision
import com.agentos.capability.core.BrokerOutcome
import com.agentos.capability.core.CapabilityBroker
import com.agentos.capability.core.CapabilityId
import com.agentos.capability.core.CapabilityRegistry
import com.agentos.capability.core.CapabilityRisk
import com.agentos.capability.core.InMemoryCapabilityAuditLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityBrokerTest {
    @Test fun deniedOrCancelledTokensCannotExecuteLater() {
        var executions = 0
        val broker = CapabilityBroker(CapabilityRegistry(listOf(
            fakeCapability(CapabilityId.WIFI_SETTINGS, CapabilityRisk.REQUIRES_CONFIRMATION) { executions++ },
        )))
        val first = (broker.request(CapabilityId.WIFI_SETTINGS) as BrokerOutcome.ApprovalRequired).request.token
        broker.deny(first)
        assertTrue(broker.approve(first) is BrokerOutcome.Denied)
        val second = (broker.request(CapabilityId.WIFI_SETTINGS) as BrokerOutcome.ApprovalRequired).request.token
        broker.cancelPending()
        assertTrue(broker.approve(second) is BrokerOutcome.Denied)
        assertEquals(0, executions)
    }

    @Test
    fun executesReadOnlyCapabilityWithoutConfirmation() {
        var executions = 0
        val audit = InMemoryCapabilityAuditLog()
        val broker = CapabilityBroker(
            CapabilityRegistry(
                listOf(fakeCapability(CapabilityId.TIME, CapabilityRisk.READ_ONLY) { executions++ }),
            ),
            audit,
        )

        assertTrue(broker.request(CapabilityId.TIME) is BrokerOutcome.Success)
        assertEquals(1, executions)
        assertEquals(AuditDecision.EXECUTED, audit.snapshot().single().decision)
    }

    @Test
    fun confirmationCapabilityExecutesOnlyAfterOneTimeApproval() {
        var executions = 0
        val broker = CapabilityBroker(
            CapabilityRegistry(
                listOf(
                    fakeCapability(CapabilityId.WIFI_SETTINGS, CapabilityRisk.REQUIRES_CONFIRMATION) {
                        executions++
                    },
                ),
            ),
        )

        val pending = broker.request(CapabilityId.WIFI_SETTINGS) as BrokerOutcome.ApprovalRequired
        assertEquals(0, executions)
        assertTrue(broker.approve(pending.request.token) is BrokerOutcome.Success)
        assertEquals(1, executions)
        assertTrue(broker.approve(pending.request.token) is BrokerOutcome.Denied)
        assertEquals(1, executions)
    }

    @Test
    fun blockedCapabilityFailsClosed() {
        var executions = 0
        val broker = CapabilityBroker(
            CapabilityRegistry(
                listOf(fakeCapability(CapabilityId.TIME, CapabilityRisk.BLOCKED) { executions++ }),
            ),
        )

        assertTrue(broker.request(CapabilityId.TIME) is BrokerOutcome.Denied)
        assertEquals(0, executions)
    }
}
