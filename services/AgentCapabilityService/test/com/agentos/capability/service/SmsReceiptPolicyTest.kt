package com.agentos.capability.service

import org.junit.Assert.*
import org.junit.Test

class SmsReceiptPolicyTest {
    @Test fun partialFailureIsNeverReportedAsSuccessOrRetried() {
        assertTrue(SmsReceiptPolicy.sentState(listOf(-1, 1)).contains("失败"))
        assertFalse(SmsReceiptPolicy.terminal(listOf(-1, SmsReceiptPolicy.PENDING)))
        assertTrue(SmsReceiptPolicy.terminal(listOf(-1, -1)))
        assertTrue(SmsReceiptPolicy.sentState(listOf(-1, -1)).contains("不代表送达或已读"))
    }
}
