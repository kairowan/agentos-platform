package com.agentos.shell

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarRenderCommandTest {
    @Test
    fun emitsBoundedVersionedRendererState() {
        val command = AvatarRenderCommand.from(
            AgentAvatar(glow = Float.NaN, shoulderWidth = Float.POSITIVE_INFINITY),
            AvatarExpression.SPEAKING,
            AvatarPerformance(intensity = 4f, tempo = -2f, gazeX = Float.NaN),
        )
        val json = JSONObject(command.toJson())

        assertEquals(AvatarRenderCommand.PROTOCOL_VERSION, json.getInt("protocol"))
        assertEquals(1.0, json.getDouble("intensity"), 0.0)
        assertEquals(0.5, json.getDouble("tempo"), 0.0)
        assertEquals(0.0, json.getDouble("gazeX"), 0.0)
        assertEquals(0.2, json.getDouble("glow"), 0.0001)
        assertEquals(0.5, json.getDouble("shoulderWidth"), 0.0)
        assertEquals(1.0, json.getDouble("speaking"), 0.0)
    }

    @Test
    fun javascriptTreatsProtocolAsQuotedData() {
        val script = AvatarRenderCommand.from(
            AgentAvatar(),
            AvatarExpression.NEUTRAL,
            AvatarPerformance(),
        ).toJavascript()

        assertTrue(script.startsWith("window.AgentOSAvatar.applyState(JSON.parse(\""))
        assertFalse(script.contains("eval("))
        assertFalse(script.contains("addJavascriptInterface"))
    }
}
