package com.agentos.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationHistoryTest {
    @Test
    fun roundTripsSourceBackedHistory() {
        val entries = listOf(ConversationEntry("turn-1", 42L, "打开设置", "准备调用系统能力"))

        assertEquals(entries, ConversationHistoryCodec.decode(ConversationHistoryCodec.encode(entries)))
    }

    @Test
    fun malformedOrIncompleteHistoryFailsClosed() {
        assertTrue(ConversationHistoryCodec.decode("not json").isEmpty())
        assertTrue(ConversationHistoryCodec.decode("[{}]").isEmpty())
    }
}
