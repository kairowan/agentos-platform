package com.agentos.shell

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentAvatarTest {
    @Test
    fun normalizesPersistedAndUserControlledValues() {
        val avatar = AgentAvatar(name = "   ", faceWidth = -1f, eyeSize = 2f, eyeSpacing = -3f, mouthWidth = 4f)
            .normalized()

        assertEquals("小 A", avatar.name)
        assertEquals(0f, avatar.faceWidth)
        assertEquals(1f, avatar.eyeSize)
        assertEquals(0f, avatar.eyeSpacing)
        assertEquals(1f, avatar.mouthWidth)
    }

    @Test
    fun randomPresetAlwaysStaysInsideEditorBounds() {
        val avatar = randomAgentAvatar(Random(17))

        assertTrue(avatar.faceWidth in 0f..1f)
        assertTrue(avatar.eyeSize in 0f..1f)
        assertTrue(avatar.eyeSpacing in 0f..1f)
        assertTrue(avatar.mouthWidth in 0f..1f)
    }

    @Test
    fun runtimeStateSelectsAUsefulExpression() {
        assertEquals(AvatarExpression.THINKING, AgentUiState(isWorking = true).avatarExpression())
        assertEquals(AvatarExpression.SPEAKING, AgentUiState(voiceReply = "你好").avatarExpression())
        assertEquals(AvatarExpression.SPEAKING, AgentUiState(isSpeaking = true).avatarExpression())
        assertEquals(AvatarExpression.LISTENING, AgentUiState(voiceStatus = "正在聆听").avatarExpression())
        assertEquals(AvatarExpression.CONCERNED, AgentUiState(notice = "需要确认").avatarExpression())
        assertEquals(AvatarExpression.NEUTRAL, AgentUiState().avatarExpression())
    }
}
