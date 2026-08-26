package com.agentos.shell

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AvatarStyleParserTest {
    @Test
    fun acceptsACompleteBoundedStyleAndPreservesTheName() {
        val avatar = AvatarStyleParser().parse(validStyle(), AgentAvatar(name = "Nova"))

        assertEquals("Nova", avatar.name)
        assertEquals(AvatarStyleFamily.CYBER, avatar.styleFamily)
        assertEquals(AvatarMaterial.HOLOGRAM, avatar.material)
        assertEquals(AvatarOutfitStyle.ARMOR, avatar.outfitStyle)
        assertEquals(AvatarAccessory.VISOR, avatar.accessory)
        assertEquals(0.8f, avatar.glow)
    }

    @Test
    fun rejectsUnknownFieldsAndOutOfRangeGeometry() {
        assertThrows(IllegalArgumentException::class.java) {
            AvatarStyleParser().parse(validStyle().put("shaderCode", "void main() {}"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AvatarStyleParser().parse(validStyle().put("headScale", 1.2))
        }
    }

    private fun validStyle() = JSONObject(
        """{
          "version":1,
          "styleDescription":"赛博仙侠全息轻甲",
          "styleFamily":"CYBER",
          "material":"HOLOGRAM",
          "outfitStyle":"ARMOR",
          "accessory":"VISOR",
          "faceShape":"OVAL",
          "hairStyle":"SHORT",
          "eyeStyle":"BRIGHT",
          "skinTone":"WARM",
          "hairColor":"SILVER",
          "outfitColor":"MINT",
          "faceWidth":0.5,
          "eyeSize":0.6,
          "eyeSpacing":0.5,
          "mouthWidth":0.4,
          "headScale":0.6,
          "bodyHeight":0.7,
          "shoulderWidth":0.55,
          "glow":0.8
        }""",
    )
}
