package com.agentos.shell

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentAvatarTest {
    @Test
    fun defaultAvatarUsesAgentOsOriginalNonHumanStyle() {
        val avatar = AgentAvatar()

        assertEquals(AvatarStyleFamily.SYSTEM, avatar.styleFamily)
        assertEquals(AvatarHairStyle.BALD, avatar.hairStyle)
        assertEquals(AvatarMaterial.HOLOGRAM, avatar.material)
        assertEquals(AvatarOutfitColor.GRAPHITE, avatar.outfitColor)
    }

    @Test
    fun normalizesPersistedAndUserControlledValues() {
        val avatar = AgentAvatar(name = "   ", styleDescription = " ", faceWidth = -1f,
            eyeSize = 2f, eyeSpacing = -3f, mouthWidth = 4f, headScale = 2f, glow = -1f)
            .normalized()

        assertEquals("小 A", avatar.name)
        assertEquals(0f, avatar.faceWidth)
        assertEquals(1f, avatar.eyeSize)
        assertEquals(0f, avatar.eyeSpacing)
        assertEquals(1f, avatar.mouthWidth)
        assertEquals("自定义 3D 角色", avatar.styleDescription)
        assertEquals(1f, avatar.headScale)
        assertEquals(0f, avatar.glow)
    }

    @Test
    fun randomPresetAlwaysStaysInsideEditorBounds() {
        val avatar = randomAgentAvatar(Random(17))

        assertTrue(avatar.faceWidth in 0f..1f)
        assertTrue(avatar.eyeSize in 0f..1f)
        assertTrue(avatar.eyeSpacing in 0f..1f)
        assertTrue(avatar.mouthWidth in 0f..1f)
        assertTrue(avatar.headScale in 0f..1f)
        assertTrue(avatar.bodyHeight in 0f..1f)
        assertTrue(avatar.shoulderWidth in 0f..1f)
        assertTrue(avatar.glow in 0f..1f)
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
