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
            """{"version":1,"title":"设备","blocks":[{"type":"paragraph","text":"准备读取"}],"capability":"system.device.read"}""",
        )

        assertEquals("设备", plan.screen.title)
        assertEquals(CapabilityId.DEVICE, plan.capability)
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
