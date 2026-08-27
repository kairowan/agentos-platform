package com.agentos.shell

import com.agentos.capability.core.PendingApproval
import org.junit.Assert.*
import org.junit.Test

class PendingApprovalTest {
    @Test fun delayedActionRechecksAuthorizationAtExecutionTime() {
        var now = 0L
        val gate = PendingApproval<String>({ now }, 5_000)
        var executions = 0
        val token = gate.issue("queued click")
        val callback = { if (gate.take(token) != null) executions++ }
        gate.clear() // cancellation after the accessibility event, before its delayed callback
        callback()
        assertEquals(0, executions)
        val expired = gate.issue("queued edit")
        now = 5_000
        assertNull(gate.take(expired))
        val valid = gate.issue("fresh")
        assertEquals("fresh", gate.take(valid))
        assertNull(gate.take(valid))
    }
    @Test fun bindsPayloadAndConsumesOnce() {
        val gate = PendingApproval<Pair<String, String>>()
        val token = gate.issue("12345" to "original message")
        assertNull(gate.take("forged"))
        assertEquals("12345" to "original message", gate.take(token))
        assertNull(gate.take(token))
    }

    @Test fun cancellationReplacementAndExpiryFailClosed() {
        var now = 0L
        val gate = PendingApproval<String>({ now }, 100)
        val old = gate.issue("old")
        val next = gate.issue("next")
        assertNull(gate.take(old))
        gate.clear()
        assertNull(gate.take(next))
        val expired = gate.issue("expired")
        now = 100
        assertNull(gate.take(expired))
    }
}
