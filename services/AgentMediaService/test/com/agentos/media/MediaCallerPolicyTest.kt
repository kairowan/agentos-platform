package com.agentos.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCallerPolicyTest {
    private val policy = MediaCallerPolicy("com.agentos.shell")

    @Test fun acceptsOnlyTheSingleSignedShellPackage() {
        assertTrue(policy.isAuthorized(listOf("com.agentos.shell"), signatureMatches = true))
        assertFalse(policy.isAuthorized(listOf("com.agentos.shell"), signatureMatches = false))
        assertFalse(policy.isAuthorized(listOf("com.agentos.shell", "shared.uid.app"), signatureMatches = true))
        assertFalse(policy.isAuthorized(listOf("attacker"), signatureMatches = true))
    }
}
