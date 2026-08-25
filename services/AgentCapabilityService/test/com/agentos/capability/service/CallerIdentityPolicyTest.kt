package com.agentos.capability.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallerIdentityPolicyTest {
    private val policy = CallerIdentityPolicy("com.agentos.shell")

    @Test
    fun allowsOnlyTheSingleSignedShellPackage() {
        assertTrue(policy.isAuthorized(listOf("com.agentos.shell"), signatureMatches = true))
        assertFalse(policy.isAuthorized(listOf("com.attacker"), signatureMatches = true))
        assertFalse(policy.isAuthorized(listOf("com.agentos.shell"), signatureMatches = false))
        assertFalse(
            policy.isAuthorized(
                listOf("com.agentos.shell", "com.shared.uid.attacker"),
                signatureMatches = true,
            ),
        )
    }
}
