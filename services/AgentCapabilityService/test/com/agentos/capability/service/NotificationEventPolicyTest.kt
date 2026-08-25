package com.agentos.capability.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationEventPolicyTest {
    @Test
    fun rejectsNonMessageAndBackgroundNotifications() {
        assertNull(candidate(isMessage = false))
        assertNull(candidate(isOngoing = true))
        assertNull(candidate(isGroupSummary = true))
    }

    @Test
    fun rejectsMessageWithoutSenderOrText() {
        assertNull(candidate(sender = ""))
        assertNull(candidate(text = "   "))
    }

    @Test
    fun boundsAndNormalizesUntrustedNotificationText() {
        val event = candidate(sender = " Alice\n ", text = "hello\n" + "x".repeat(2_000))!!

        assertEquals("Alice", event.sender)
        assertEquals(1_000, event.text.length)
    }

    private fun candidate(
        sender: String = "Alice",
        text: String = "Hello",
        isMessage: Boolean = true,
        isOngoing: Boolean = false,
        isGroupSummary: Boolean = false,
    ) = NotificationEventPolicy.create(
        packageName = "chat.example",
        sender = sender,
        text = text,
        postedAtMillis = 1L,
        isMessage = isMessage,
        isOngoing = isOngoing,
        isGroupSummary = isGroupSummary,
    )
}
