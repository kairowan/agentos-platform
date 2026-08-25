package com.agentos.shell

import org.junit.Assert.assertThrows
import org.junit.Test

class ModelEndpointPolicyTest {
    @Test
    fun allowsHttpsAndLocalDevelopmentHttp() {
        validateModelEndpoint("https://models.example.com/v1/chat/completions")
        validateModelEndpoint("http://127.0.0.1:11434/v1/chat/completions")
        validateModelEndpoint("http://10.0.2.2:11434/v1/chat/completions")
    }

    @Test
    fun rejectsRemoteCleartextAndEmbeddedCredentials() {
        assertThrows(IllegalArgumentException::class.java) {
            validateModelEndpoint("http://models.example.com/v1/chat/completions")
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateModelEndpoint("https://secret@models.example.com/v1/chat/completions")
        }
    }

    @Test
    fun modelConfigDoesNotPrintApiKey() {
        val config = ModelConfig("https://models.example.com/v1/chat/completions", "model", "top-secret")

        require(!config.toString().contains("top-secret"))
    }
}

