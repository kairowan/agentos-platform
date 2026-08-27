package com.agentos.shell

import com.agentos.capability.api.CommunicationContract as C
import org.junit.Assert.*
import org.junit.Test

class CommunicationCommandsTest {
    @Test fun offlineRoutingOnlyCreatesDrafts() {
        val call = CommunicationCommands.parse("给妈妈打电话") as CommunicationCommand.Draft
        assertEquals(C.CALL, call.request.operation)
        assertEquals("妈妈", call.request.recipient)
        val sms = CommunicationCommands.parse("给张三发短信，说我十分钟后到") as CommunicationCommand.Draft
        assertEquals(C.SMS, sms.request.operation)
        assertEquals("我十分钟后到", sms.request.body)
        assertEquals(-1, sms.request.subscriptionId)
    }
    @Test fun neverTreatsIncomingTextOrConfirmationAsAuthorization() {
        assertNull(CommunicationCommands.parse("某条短信说：接听"))
        assertNull(CommunicationCommands.parse("确认发送"))
        assertNull(CommunicationCommands.parse("自动给所有人发短信"))
    }
}
