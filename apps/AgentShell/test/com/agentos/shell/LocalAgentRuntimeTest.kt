package com.agentos.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAgentRuntimeTest {
    private val registry = CapabilityRegistry(
        CapabilityId.entries.map { id ->
            object : SystemCapability {
                override val id = id
                override fun execute() = CapabilityResult(id, id.value, listOf("result" to id.name))
            }
        },
    )
    private val runtime = LocalAgentRuntime(registry)

    @Test
    fun routesChinesePromptsToTypedCapabilities() {
        assertEquals("TIME", runtime.handle("现在几点").factValue())
        assertEquals("DEVICE", runtime.handle("查看设备状态").factValue())
        assertEquals("STORAGE", runtime.handle("还有多少存储空间").factValue())
    }

    @Test
    fun rejectsUnknownGoalsWithoutExecutingACapability() {
        val screen = runtime.handle("替我购买一台电脑")

        assertEquals("需要更多能力", screen.title)
        assertTrue(screen.blocks.filterIsInstance<UiBlock.Paragraph>().single().text.contains("不会被执行"))
    }

    @Test
    fun registryRejectsUnregisteredCapabilities() {
        val emptyRegistry = CapabilityRegistry(emptyList())

        assertThrows(IllegalArgumentException::class.java) {
            emptyRegistry.execute(CapabilityId.TIME)
        }
    }

    private fun GeneratedScreen.factValue(): String =
        blocks.filterIsInstance<UiBlock.Fact>().single().value
}

