package com.agentos.shell

import com.agentos.capability.core.CapabilityId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GeneratedUiParserTest {
    private val parser = GeneratedUiParser()

    @Test
    fun parsesBoundedScreenAndKnownCapability() {
        val plan = parser.parse(
            """{"version":1,"title":"设备","blocks":[{"type":"paragraph","text":"准备读取"}],"capability":"system.device.read","performance":{"emotion":"FOCUSED","gesture":"EXPLAIN","intensity":0.7,"tempo":1.1,"gazeX":0.2,"gazeY":-0.1}}""",
        )

        assertEquals("设备", plan.screen.title)
        assertEquals(CapabilityId.DEVICE, plan.capability)
        assertEquals(AvatarEmotion.FOCUSED, plan.performance.emotion)
        assertEquals(AvatarGesture.EXPLAIN, plan.performance.gesture)
    }

    @Test
    fun rejectsArbitraryAnimationAndOutOfRangeMotion() {
        assertThrows(IllegalArgumentException::class.java) {
            parser.parse("""{"version":1,"title":"x","blocks":[],"performance":{"emotion":"HAPPY","gesture":"RUN_SCRIPT","intensity":0.5,"tempo":1,"gazeX":0,"gazeY":0}}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            parser.parse("""{"version":1,"title":"x","blocks":[],"performance":{"emotion":"HAPPY","gesture":"WAVE","intensity":4,"tempo":1,"gazeX":0,"gazeY":0}}""")
        }
    }

    @Test
    fun rejectsUnknownRootField() {
        assertThrows(IllegalArgumentException::class.java) {
            parser.parse("""{"version":1,"title":"x","blocks":[],"intent":"android.settings.SETTINGS"}""")
        }
    }

    @Test
    fun rejectsUnknownCapability() {
        assertThrows(IllegalArgumentException::class.java) {
            parser.parse("""{"version":1,"title":"x","blocks":[],"capability":"shell.exec"}""")
        }
    }

    @Test
    fun rejectsTrailingContent() {
        assertThrows(IllegalArgumentException::class.java) {
            parser.parse("""{"version":1,"title":"x","blocks":[]} trailing""")
        }
    }

    @Test
    fun rejectsOversizedText() {
        val text = "x".repeat(4_001)
        assertThrows(IllegalArgumentException::class.java) {
            parser.parse("""{"version":1,"title":"x","blocks":[{"type":"paragraph","text":"$text"}]}""")
        }
    }
}
