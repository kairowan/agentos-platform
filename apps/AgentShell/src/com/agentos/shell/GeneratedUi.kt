package com.agentos.shell

import androidx.compose.runtime.Immutable

@Immutable
data class GeneratedScreen(
    val title: String,
    val blocks: List<UiBlock>,
) {
    companion object {
        fun welcome() = GeneratedScreen(
            title = "系统已就绪",
            blocks = listOf(
                UiBlock.Paragraph("当前是 AgentOS v0.3 的本地安全模式。系统能力由独立 Broker 服务执行。"),
                UiBlock.Action("查看当前时间", "现在几点"),
                UiBlock.Action("查看设备状态", "查看设备状态"),
                UiBlock.Action("查看存储状态", "查看存储状态"),
                UiBlock.Action("打开 Wi-Fi 设置", "打开 Wi-Fi 设置"),
            ),
        )
    }
}

@Immutable
sealed interface UiBlock {
    data class Paragraph(val text: String) : UiBlock
    data class Fact(val label: String, val value: String) : UiBlock
    data class Action(val label: String, val prompt: String) : UiBlock
}
