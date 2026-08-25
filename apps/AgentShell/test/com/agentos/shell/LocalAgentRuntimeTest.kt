package com.agentos.shell

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAgentRuntimeTest {
    private val registry = CapabilityRegistry(
        CapabilityId.entries.map { id ->
            fakeCapability(
                id = id,
                risk = if (id == CapabilityId.WIFI_SETTINGS) {
                    CapabilityRisk.REQUIRES_CONFIRMATION
                } else {
                    CapabilityRisk.READ_ONLY
                },
            )
        },
    )
    private val runtime = AgentRuntime(CapabilityBroker(registry))

    @Test
    fun routesChinesePromptsThroughBroker() = runBlocking {
        assertEquals("TIME", runtime.handle("现在几点", null).screen.factValue())
        assertEquals("DEVICE", runtime.handle("查看设备状态", null).screen.factValue())
        assertEquals("STORAGE", runtime.handle("还有多少存储空间", null).screen.factValue())
    }

    @Test
    fun requestsTrustedConfirmationForWifiSettings() = runBlocking {
        val turn = runtime.handle("打开 Wi-Fi 设置", null)

        assertNotNull(turn.approval)
        assertEquals(CapabilityId.WIFI_SETTINGS, turn.approval?.capability)
    }

    @Test
    fun rejectsUnknownGoalsWithoutExecutingCapability() = runBlocking {
        val turn = runtime.handle("替我购买一台电脑", null)

        assertNull(turn.approval)
        assertEquals("需要更多能力", turn.screen.title)
        assertTrue(turn.screen.paragraph().contains("不会被执行"))
    }

    @Test
    fun fallsBackWhenRemotePlannerFails() = runBlocking {
        val failingRuntime = AgentRuntime(
            broker = CapabilityBroker(registry),
            remotePlannerFactory = { AgentPlanner { error("offline") } },
        )
        val config = ModelConfig("https://example.com/v1/chat/completions", "test", "secret")

        val turn = failingRuntime.handle("查看设备状态", config)

        assertEquals("DEVICE", turn.screen.factValue())
        assertTrue(turn.notice.orEmpty().contains("离线安全模式"))
    }

    private fun GeneratedScreen.factValue(): String =
        blocks.filterIsInstance<UiBlock.Fact>().single().value

    private fun GeneratedScreen.paragraph(): String =
        blocks.filterIsInstance<UiBlock.Paragraph>().first().text
}

internal fun fakeCapability(
    id: CapabilityId,
    risk: CapabilityRisk,
    onExecute: () -> Unit = {},
): SystemCapability = object : SystemCapability {
    override val descriptor = CapabilityDescriptor(id, id.value, risk)

    override fun execute(): CapabilityResult {
        onExecute()
        return CapabilityResult(id, id.value, listOf("result" to id.name))
    }
}

