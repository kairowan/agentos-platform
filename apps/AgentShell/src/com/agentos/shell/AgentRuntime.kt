package com.agentos.shell

interface AgentRuntime {
    fun handle(prompt: String): GeneratedScreen
}

class LocalAgentRuntime(
    private val capabilities: CapabilityRegistry,
) : AgentRuntime {
    override fun handle(prompt: String): GeneratedScreen {
        // ponytail: This deterministic router is intentionally limited to the v0.1
        // capability set. Replace it with the model planner after Broker policy exists.
        val capability = when {
            prompt.containsAny("时间", "几点", "time") -> CapabilityId.TIME
            prompt.containsAny("设备", "型号", "系统", "device") -> CapabilityId.DEVICE
            prompt.containsAny("存储", "空间", "文件", "storage", "disk") -> CapabilityId.STORAGE
            else -> return GeneratedScreen(
                title = "需要更多能力",
                blocks = listOf(
                    UiBlock.Paragraph("当前安全模式只开放时间、设备状态和存储状态。未知目标不会被执行。"),
                    UiBlock.Action("显示可用能力", "查看设备状态"),
                ),
            )
        }

        val result = capabilities.execute(capability)
        return GeneratedScreen(
            title = result.title,
            blocks = result.facts.map { UiBlock.Fact(it.first, it.second) } +
                UiBlock.Paragraph("由 ${result.capability.value} 能力提供；模型未获得直接系统权限。"),
        )
    }
}

private fun String.containsAny(vararg candidates: String): Boolean =
    candidates.any { contains(it, ignoreCase = true) }
