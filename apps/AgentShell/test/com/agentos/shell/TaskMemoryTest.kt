package com.agentos.shell

import com.agentos.capability.core.BrokerOutcome
import com.agentos.capability.core.CapabilityId
import com.agentos.capability.core.CapabilityResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.emptyFlow
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TaskMemoryTest {
    private fun gateway(onDispatch: () -> Unit) = object : CapabilityGateway {
        override val notificationEvents = emptyFlow<com.agentos.capability.api.AgentNotificationEvent>()
        override suspend fun request(id: CapabilityId): BrokerOutcome {
            onDispatch()
            return BrokerOutcome.Success(CapabilityResult(id, "ok", emptyList()))
        }
        override suspend fun approve(token: String): BrokerOutcome = error("unexpected approval")
        override suspend fun deny(token: String): BrokerOutcome = error("unexpected denial")
        override suspend fun cancelPending() = Unit
    }
    private fun turn(id: String = "source", state: TaskState = TaskState.SUCCEEDED) =
        ConversationEntry(id, 1, "我喜欢咖啡", "记录", "完整回复", taskState = state.name)
    private fun fact(confirmed: Boolean = true, corrected: Boolean = false) = KnowledgeRelation(
        "relation", KnowledgeEntity("me", "我", "PERSON"), "likes",
        KnowledgeEntity("tea", "茶", "PREFERENCE"), "我喜欢咖啡", "source", 1.0, confirmed, corrected)

    @Test fun interruptionsNeverBecomeSuccessfulOrReplayable() {
        assertEquals(TaskState.CANCELLED, TaskState.PLANNING.interrupted())
        assertEquals(TaskState.CANCELLED, TaskState.WAITING_CONFIRMATION.interrupted())
        assertEquals(TaskState.UNKNOWN, TaskState.EXECUTING.interrupted())
        TaskState.entries.filterNot { it.active }.forEach { terminal ->
            assertEquals(terminal, terminal.interrupted())
            TaskState.entries.forEach { assertFalse(terminal.canTransitionTo(it)) }
        }
        assertTrue(TaskState.EXECUTING.canTransitionTo(TaskState.WAITING_CONFIRMATION))
        assertFalse(TaskState.WAITING_CONFIRMATION.canTransitionTo(TaskState.SUCCEEDED))
        assertEquals(TaskState.UNKNOWN, TaskState.parse("invalid"))
    }

    @Test fun completeResponseIncludesAllBlocksAndDoesNotExecuteSuggestions() {
        val blocks = (1..30).map { UiBlock.Paragraph("第 $it 段") } + UiBlock.Action("打开", "打开 Wi-Fi")
        val text = AgentTurn(GeneratedScreen("全部", blocks), notice = "警告").historyText()
        assertTrue(text.contains("第 30 段"))
        assertTrue(text.contains("[可选操作，未执行]"))
        assertTrue(text.endsWith("提示：警告"))
    }

    @Test fun recallFiltersExcludedHistoryAndUnconfirmedFacts() {
        val memory = MemoryRecall.select("我的偏好", listOf(turn(), turn("excluded").copy(memoryExcluded = true),
            turn("pending", TaskState.WAITING_CONFIRMATION)), KnowledgeGraph(relations = listOf(fact(false))))
        assertEquals(listOf("source"), memory.recentTurns.map { it.id })
        assertTrue(memory.facts.isEmpty())
        val json = JSONObject(MemoryContext(listOf(turn().copy(memoryExcluded = true)), listOf(fact(false))).asUntrustedJson())
        assertEquals(0, json.getJSONArray("recentTurns").length())
        assertEquals(0, json.getJSONArray("confirmedFacts").length())
    }

    @Test fun correctedFactKeepsOldEvidenceOffModelAndSerializationIsBounded() {
        val encoded = MemoryContext(listOf(turn()), listOf(fact(corrected = true))).asUntrustedJson()
        val knowledge = JSONObject(encoded).getJSONArray("confirmedFacts").getJSONObject(0)
        assertEquals("茶", knowledge.getString("object"))
        assertFalse(knowledge.getString("evidence").contains("咖啡"))
        val huge = MemoryContext((1..30).map { turn("$it").copy(responseBody = "\n".repeat(8000)) },
            (1..30).map { fact().copy(target = KnowledgeEntity("$it", "x".repeat(8000), "PREFERENCE")) })
            .asUntrustedJson()
        assertTrue(huge.length <= 12_000)
        assertTrue(JSONObject(huge).getJSONArray("recentTurns").length() <= 6)
    }

    @Test fun contextRequiresConsentAndOfflineRecallNeverDispatches() = runBlocking {
        var calls = 0
        var received = MemoryContext()
        val broker = gateway { calls++ }
        val runtime = AgentRuntime(broker, remotePlannerFactory = {
            object : AgentPlanner {
                override suspend fun plan(prompt: String) = AgentPlan(GeneratedScreen("答复", emptyList()))
                override suspend fun planWithMemory(prompt: String, memory: MemoryContext): AgentPlan {
                    received = memory
                    return plan(prompt)
                }
            }
        })
        val config = ModelConfig("https://example.com/v1/chat/completions", "model", "")
        val memory = MemoryContext(listOf(turn()))
        runtime.handle("你好", config, memory)
        assertTrue(received.recentTurns.isEmpty())
        runtime.handle("你好", config.copy(shareMemory = true), memory)
        assertEquals(memory, received)
        val recall = runtime.handle("回顾上次任务", config, memory)
        assertTrue(recall.historyText().contains("完整回复"))
        assertEquals(0, calls)
        assertEquals(TaskState.FAILED, runtime.handle("未知目标", null).taskState)
    }

    @Test fun missingDurableDispatchRecordPreventsCapabilityCall() = runBlocking {
        var calls = 0
        val runtime = AgentRuntime(gateway { calls++ })
        try {
            runtime.handle("时间", null, beforeDispatch = { throw CancellationException("record unavailable") })
            fail("must stop")
        } catch (_: CancellationException) { }
        assertEquals(0, calls)
    }
}
