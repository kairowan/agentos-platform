package com.agentos.shell

enum class TaskState(val label: String) {
    PLANNING("正在理解"), WAITING_CONFIRMATION("等待确认"), EXECUTING("已提交执行"),
    SUCCEEDED("已完成"), FAILED("失败"), CANCELLED("已取消"), UNKNOWN("结果未知"),
    LEGACY("旧版记录，结果未核验");

    val active: Boolean get() = this in setOf(PLANNING, WAITING_CONFIRMATION, EXECUTING)

    fun interrupted(): TaskState = when (this) {
        EXECUTING -> UNKNOWN
        PLANNING, WAITING_CONFIRMATION -> CANCELLED
        else -> this
    }

    fun canTransitionTo(next: TaskState): Boolean = active && when (this) {
        PLANNING -> next in setOf(EXECUTING, SUCCEEDED, FAILED, CANCELLED)
        WAITING_CONFIRMATION -> next in setOf(EXECUTING, CANCELLED, FAILED)
        EXECUTING -> next in setOf(WAITING_CONFIRMATION, SUCCEEDED, FAILED, UNKNOWN)
        else -> false
    }

    companion object {
        fun parse(value: String): TaskState = entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}

internal fun AgentTurn.historyText(): String = buildString {
    screen.blocks.forEach { block ->
        appendLine(when (block) {
            is UiBlock.Paragraph -> block.text
            is UiBlock.Fact -> "${block.label}：${block.value}"
            is UiBlock.Action -> "[可选操作，未执行] ${block.label}：${block.prompt}"
        })
    }
    notice?.let { appendLine("提示：$it") }
}.trimEnd()
