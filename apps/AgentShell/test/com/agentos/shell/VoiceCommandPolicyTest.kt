package com.agentos.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCommandPolicyTest {
    @Test
    fun rejectsMissingOrBlankCommands() {
        assertNull(VoiceCommandPolicy.sanitize(null))
        assertNull(VoiceCommandPolicy.sanitize("  \n "))
    }

    @Test
    fun trimsAndBoundsCommandsAtTheProcessBoundary() {
        assertEquals("打开设置", VoiceCommandPolicy.sanitize("  打开设置  "))
        assertEquals(8_000, VoiceCommandPolicy.sanitize("a".repeat(8_001))?.length)
    }

    @Test
    fun commandTicketIsUnforgeableAndOneTime() {
        val token = VoiceCommandInbox.offer("打开设置")

        assertNull(VoiceCommandInbox.take("wrong-token"))
        assertEquals("打开设置", VoiceCommandInbox.take(token))
        assertNull(VoiceCommandInbox.take(token))
    }

    @Test
    fun interruptTicketIsUnforgeableAndOneTime() {
        val token = VoiceInterruptInbox.offer()

        assertEquals(false, VoiceInterruptInbox.take("wrong-token"))
        assertEquals(true, VoiceInterruptInbox.take(token))
        assertEquals(false, VoiceInterruptInbox.take(token))
    }
}
