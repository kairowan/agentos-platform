package com.agentos.shell

import org.junit.Assert.*
import org.junit.Test

class UtteranceGateTest {
    @Test fun staleCompletionCannotFinishNewSpeech() {
        val gate = UtteranceGate()
        val first = gate.start()
        val second = gate.start()
        assertFalse(gate.finish(first))
        assertTrue(gate.finish(second))
        assertFalse(gate.finish(second))
    }
    @Test fun interruptionInvalidatesCallbacks() {
        val gate = UtteranceGate()
        val id = gate.start()
        assertTrue(gate.cancel())
        assertFalse(gate.finish(id))
        assertFalse(gate.cancel())
    }
}
