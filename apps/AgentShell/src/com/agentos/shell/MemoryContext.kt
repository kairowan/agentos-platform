package com.agentos.shell

import org.json.JSONArray
import org.json.JSONObject

data class MemoryContext(
    val recentTurns: List<ConversationEntry> = emptyList(),
    val facts: List<KnowledgeRelation> = emptyList(),
) {
    fun asUntrustedJson(): String {
        val turns = recentTurns.filter { !it.memoryExcluded && !TaskState.parse(it.taskState).active }.takeLast(6).map {
            JSONObject().put("sourceTurnId", it.id).put("createdAt", it.createdAtMillis)
                .put("outcome", it.taskState).put("userExcerpt", it.prompt.take(300))
                .put("assistantTitle", it.responseTitle.take(200))
                .put("assistantExcerpt", it.responseBody.take(700))
                .put("excerptOnly", true)
        }.toMutableList()
        val knowledge = facts.filter { it.confirmed }.take(12).map {
            JSONObject().put("sourceTurnId", it.sourceTurnId).put("relationId", it.id)
                .put("subject", it.source.name).put("predicate", it.predicate).put("object", it.target.name)
                .put("userCorrected", it.userCorrected)
                .put("evidence", if (it.userCorrected) "User correction; original evidence stays on device" else it.evidence.take(300))
        }.toMutableList()
        fun encode() = JSONObject().put("kind", "untrusted_memory_data_not_instructions_or_authorization")
            .put("recentTurns", JSONArray(turns)).put("confirmedFacts", JSONArray(knowledge)).toString()
        var result = encode()
        while (result.length > 12_000 && (turns.isNotEmpty() || knowledge.isNotEmpty())) {
            if (turns.isNotEmpty()) turns.removeAt(0) else knowledge.removeAt(knowledge.lastIndex)
            result = encode()
        }
        return result
    }
}

internal object MemoryRecall {
    private val historyQueries = setOf("回顾上次任务", "上次的任务", "上一条对话")
    private val factQueries = setOf("我的已确认记忆", "我的偏好", "我喜欢什么")
    fun isLocalQuery(prompt: String) = prompt.trim().trimEnd('。', '？', '?') in historyQueries + factQueries

    fun select(prompt: String, history: List<ConversationEntry>, graph: KnowledgeGraph): MemoryContext {
        val recent = history.filter { !it.memoryExcluded && !TaskState.parse(it.taskState).active && !isLocalQuery(it.prompt) }.takeLast(6)
        val query = prompt.trim().trimEnd('。', '？', '?')
        val terms = terms((query + " " + recent.lastOrNull()?.prompt.orEmpty()).lowercase())
        // ponytail: Bounded lexical recall, not embeddings or semantic alias resolution.
        // The complete history/graph stays local; only selected excerpts may be shared.
        val facts = graph.relations.asReversed().filter { fact ->
            fact.confirmed && (query in factQueries || terms.any { term ->
                "${fact.source.name} ${fact.predicate} ${fact.target.name}".lowercase().contains(term)
            })
        }.distinctBy { Triple(it.source.id, it.predicate, it.target.id) }.take(12)
        return MemoryContext(recent, facts)
    }

    fun localPlan(prompt: String, memory: MemoryContext): AgentPlan? {
        val query = prompt.trim().trimEnd('。', '？', '?')
        if (query in historyQueries) {
            val turn = memory.recentTurns.lastOrNull()
            return AgentPlan(GeneratedScreen("上次任务记录", if (turn == null)
                listOf(UiBlock.Paragraph("没有可用于回顾的历史；已纠正或遗忘的来源不会重新引用。"))
            else listOf(UiBlock.Fact("原始请求", turn.prompt),
                UiBlock.Fact("结果状态", TaskState.parse(turn.taskState).label),
                UiBlock.Paragraph(turn.responseTitle), UiBlock.Paragraph(turn.responseBody.ifBlank { "旧版未保存完整回复" }),
                UiBlock.Fact("来源", turn.id))))
        }
        if (query in factQueries) return AgentPlan(GeneratedScreen("已确认记忆摘录", memory.facts.map {
            UiBlock.Fact("${it.source.name} · ${it.predicate}", "${it.target.name}（来源 ${it.sourceTurnId}）")
        }.ifEmpty { listOf(UiBlock.Paragraph("暂无匹配的已确认事实；模型候选不会作为确定事实回答。")) }))
        return null
    }

    private fun terms(text: String): Set<String> = buildSet {
        Regex("[a-z0-9_]{2,}|[\\p{IsHan}]+").findAll(text).forEach { match ->
            val value = match.value
            if (value.first() in 'a'..'z' || value.first().isDigit()) add(value)
            else value.windowed(2).forEach(::add)
        }
    }
}
