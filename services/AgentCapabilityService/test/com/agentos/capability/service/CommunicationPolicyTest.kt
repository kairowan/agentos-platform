package com.agentos.capability.service

import com.agentos.capability.api.CommunicationContract as C
import com.agentos.capability.api.CommunicationRequest
import org.junit.Assert.*
import org.junit.Test

class CommunicationPolicyTest {
    @Test fun rejectsCodeInjectionAndAmbiguousNumbers() {
        listOf("*#06#", "123;456", "123,456", "tel:12345", "12\n345", "１２３４５", "12+345").forEach {
            assertNull(it, CommunicationPolicy.number(it))
        }
        assertEquals("+8613800000000", CommunicationPolicy.number("+86 (138) 0000-0000"))
    }
    @Test fun validatesOperationAndBoundedPayload() {
        CommunicationPolicy.validate(CommunicationRequest(C.SMS, "12345", "hello", 1))
        listOf(CommunicationRequest(99, "12345", ""), CommunicationRequest(C.SMS, "12345", ""),
            CommunicationRequest(C.SMS, "12345", "a".repeat(2001)), CommunicationRequest(C.CALL, "12345", "hidden"),
            CommunicationRequest(C.CALL, "12345", "", -2)).forEach {
            assertTrue(runCatching { CommunicationPolicy.validate(it) }.isFailure)
        }
    }
    @Test fun staleOrEndedCallsCannotBeAnswered() {
        assertTrue(CommunicationPolicy.canControl(2, C.ANSWER))
        assertFalse(CommunicationPolicy.canControl(4, C.ANSWER))
        assertFalse(CommunicationPolicy.canControl(7, C.HANG_UP))
        assertFalse(CommunicationPolicy.canControl(2, 999))
    }
}
