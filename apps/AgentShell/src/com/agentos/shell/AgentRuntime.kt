package com.agentos.shell

import com.agentos.capability.core.ApprovalRequest
import com.agentos.capability.core.BrokerOutcome
import com.agentos.capability.core.CapabilityBroker
import com.agentos.capability.core.CapabilityId
import com.agentos.capability.core.CapabilityResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

data class AgentPlan(
    val screen: GeneratedScreen,
    val capability: CapabilityId? = null,
    val performance: AvatarPerformance = AvatarPerformance(),
    val taskState: TaskState = TaskState.SUCCEEDED,
)

data class AgentTurn(
    val screen: GeneratedScreen,
    val approval: ApprovalRequest? = null,
    val notice: String? = null,
    val performance: AvatarPerformance = AvatarPerformance(),
    val taskState: TaskState = TaskState.SUCCEEDED,
)

fun interface AgentPlanner {
    suspend fun plan(prompt: String): AgentPlan
    suspend fun planWithMemory(prompt: String, memory: MemoryContext): AgentPlan = plan(prompt)
}

class LocalAgentPlanner : AgentPlanner {
    override suspend fun planWithMemory(prompt: String, memory: MemoryContext): AgentPlan =
        MemoryRecall.localPlan(prompt, memory) ?: plan(prompt)
    override suspend fun plan(prompt: String): AgentPlan {
        // ponytail: This deterministic router is the offline recovery planner.
        // Add intents only while their capability policy remains explicit.
        val capability = when {
            prompt.containsAny("时间", "几点", "time") -> CapabilityId.TIME
            prompt.containsAny("设备", "型号", "系统", "device") -> CapabilityId.DEVICE
            prompt.containsAny("存储", "空间", "文件", "storage", "disk") -> CapabilityId.STORAGE
            prompt.containsAny("wifi", "wi-fi", "无线网络") -> CapabilityId.WIFI_SETTINGS
            else -> return AgentPlan(
                GeneratedScreen(
                    title = "需要更多能力",
                    blocks = listOf(
                        UiBlock.Paragraph("离线模式只开放时间、设备、存储和 Wi-Fi 设置。未知目标不会被执行。"),
                        UiBlock.Action("显示设备状态", "查看设备状态"),
                    ),
                ),
                taskState = TaskState.FAILED,
            )
        }
        return AgentPlan(
            screen = GeneratedScreen(
                title = "准备调用系统能力",
                blocks = listOf(UiBlock.Paragraph("请求 ${capability.value}")),
            ),
            capability = capability,
        )
    }
}

class AgentRuntime(
    private val broker: CapabilityGateway,
    private val localPlanner: AgentPlanner = LocalAgentPlanner(),
    private val remotePlannerFactory: (ModelConfig) -> AgentPlanner = ::OpenAiCompatiblePlanner,
) {
    constructor(
        broker: CapabilityBroker,
        localPlanner: AgentPlanner = LocalAgentPlanner(),
        remotePlannerFactory: (ModelConfig) -> AgentPlanner = ::OpenAiCompatiblePlanner,
    ) : this(LocalCapabilityGateway(broker), localPlanner, remotePlannerFactory)

    suspend fun handle(
        prompt: String,
        modelConfig: ModelConfig?,
        memory: MemoryContext = MemoryContext(),
        beforeDispatch: suspend (CapabilityId) -> Unit = {},
    ): AgentTurn {
        var notice: String? = null
        val plan = if (modelConfig == null || MemoryRecall.isLocalQuery(prompt)) {
            localPlanner.planWithMemory(prompt, memory)
        } else {
            try {
                remotePlannerFactory(modelConfig).planWithMemory(prompt, if (modelConfig.shareMemory) memory else MemoryContext())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                notice = "模型不可用或返回了无效内容，已切换到离线安全模式。"
                localPlanner.planWithMemory(prompt, memory)
            }
        }
        coroutineContext.ensureActive()
        plan.capability?.let { beforeDispatch(it) }
        coroutineContext.ensureActive()
        return execute(plan, notice)
    }

    suspend fun approve(token: String): AgentTurn = broker.approve(token).toTurn()

    suspend fun deny(token: String): AgentTurn = broker.deny(token).toTurn().copy(taskState = TaskState.CANCELLED)

    private suspend fun execute(plan: AgentPlan, notice: String?): AgentTurn {
        val id = plan.capability ?: return AgentTurn(plan.screen, notice = notice, performance = plan.performance, taskState = plan.taskState)
        return broker.request(id).toTurn(plan.screen, notice, plan.performance)
    }
}

private fun BrokerOutcome.toTurn(
    plannedScreen: GeneratedScreen? = null,
    notice: String? = null,
    performance: AvatarPerformance = AvatarPerformance(),
): AgentTurn = when (this) {
    is BrokerOutcome.Success -> AgentTurn(result.toScreen(), notice = notice, performance = performance)
    is BrokerOutcome.ApprovalRequired -> AgentTurn(
        screen = plannedScreen ?: GeneratedScreen("等待确认", emptyList()),
        approval = request,
        notice = notice,
        performance = performance,
        taskState = TaskState.WAITING_CONFIRMATION,
    )
    is BrokerOutcome.Denied -> AgentTurn(
        GeneratedScreen("操作未执行", listOf(UiBlock.Paragraph(reason))),
        notice = notice,
        performance = AvatarPerformance(AvatarEmotion.CONCERNED, AvatarGesture.COMFORT),
        taskState = TaskState.FAILED,
    )
    is BrokerOutcome.Failed -> AgentTurn(
        GeneratedScreen("能力执行失败", listOf(UiBlock.Paragraph(reason))),
        notice = notice,
        performance = AvatarPerformance(AvatarEmotion.CONCERNED, AvatarGesture.COMFORT),
        taskState = TaskState.FAILED,
    )
}

private fun CapabilityResult.toScreen() = GeneratedScreen(
    title = title,
    blocks = facts.map { UiBlock.Fact(it.first, it.second) } +
        UiBlock.Paragraph("由 ${capability.value} 通过 Capability Broker 提供。"),
)

private fun String.containsAny(vararg candidates: String): Boolean =
    candidates.any { contains(it, ignoreCase = true) }
