package com.agentos.shell

import org.junit.Assert.assertEquals
import org.junit.Test

class ThoughtFieldUniformStateTest {
    @Test
    fun speakingAndListeningCondenseTheFaceWithoutTrustingUnboundedMotion() {
        val speaking = ThoughtFieldUniformState.from(
            AvatarExpression.SPEAKING,
            AvatarPerformance(gesture = AvatarGesture.TALK, intensity = 4f, gazeX = -3f, gazeY = 2f),
        )
        val resting = ThoughtFieldUniformState.from(AvatarExpression.SLEEPY, AvatarPerformance())

        assertEquals(1f, speaking.speaking)
        assertEquals(0.96f, speaking.facePresence)
        assertEquals(1f, speaking.intensity)
        assertEquals(-1f, speaking.gazeX)
        assertEquals(1f, speaking.gazeY)
        assertEquals(0.08f, resting.facePresence)
    }
}
