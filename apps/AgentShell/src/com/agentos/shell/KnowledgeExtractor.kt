package com.agentos.shell

import org.json.JSONObject

internal object LocalKnowledgeExtractor {
    fun extract(text: String): List<KnowledgeCandidate> = buildList {
        matchOne(text, Regex("我(?:的名字)?叫\\s*([^，。,.!?！？]{1,40})")) { value, evidence ->
            fact("用户", "PERSON", "name", value, "PERSON", evidence)
        }
        matchOne(text, Regex("我不喜欢\\s*([^，。,.!?！？]{1,80})")) { value, evidence ->
            fact("用户", "PERSON", "dislikes", value, "PREFERENCE", evidence)
        }
        matchOne(text, Regex("我喜欢\\s*([^，。,.!?！？]{1,80})")) { value, evidence ->
            fact("用户", "PERSON", "likes", value, "PREFERENCE", evidence)
        }
        matchOne(text, Regex("我住在\\s*([^，。,.!?！？]{1,80})")) { value, evidence ->
            fact("用户", "PERSON", "lives_in", value, "PLACE", evidence)
        }
        matchOne(text, Regex("我在\\s*([^，。,.!?！？]{1,80})工作")) { value, evidence ->
            fact("用户", "PERSON", "works_at", value, "ORGANIZATION", evidence)
        }
        matchGroups(text, Regex("([^，。,.!?！？]{1,40})是我的([^，。,.!?！？]{1,30})")) { groups, evidence ->
            fact("用户", "PERSON", groups[2], groups[1], "PERSON", evidence)
        }
        matchOne(text, Regex("我(?:正在)?(?:开发|维护)\\s*([^，。,.!?！？]{1,100})")) { value, evidence ->
            fact("用户", "PERSON", "develops", value, "PROJECT", evidence)
        }
    }.distinctBy { listOf(it.sourceName, it.predicate, it.targetName, it.evidence) }

    private fun MutableList<KnowledgeCandidate>.fact(
        source: String, sourceType: String, predicate: String,
        target: String, targetType: String, evidence: String,
    ) = add(KnowledgeCandidate(source, sourceType, predicate, target.trim(), targetType,
        evidence, confidence = 1.0, explicit = true))

    private fun MutableList<KnowledgeCandidate>.matchOne(
        text: String,
        regex: Regex,
        block: MutableList<KnowledgeCandidate>.(String, String) -> Unit,
    ) {
        regex.findAll(text).forEach { result -> block(this, result.groupValues[1], result.value) }
    }

    private fun MutableList<KnowledgeCandidate>.matchGroups(
        text: String,
        regex: Regex,
        block: MutableList<KnowledgeCandidate>.(List<String>, String) -> Unit,
    ) {
        regex.findAll(text).forEach { result -> block(this, result.groupValues, result.value) }
    }
}

internal class ModelKnowledgeExtractor(private val config: ModelConfig) {
    suspend fun extract(text: String): List<KnowledgeCandidate> =
        parse(openAiJson(config, SYSTEM_PROMPT, text), text)

    internal fun parse(json: JSONObject, sourceText: String): List<KnowledgeCandidate> {
        val relations = json.optJSONArray("relations") ?: return emptyList()
        return buildList {
            for (index in 0 until minOf(relations.length(), MAX_RELATIONS)) {
                val item = relations.optJSONObject(index) ?: continue
                val evidence = item.optString("evidence").trim()
                if (evidence.isEmpty() || !sourceText.contains(evidence)) continue
                add(KnowledgeCandidate(
                    item.optString("source"), item.optString("source_type"),
                    item.optString("predicate"), item.optString("target"),
                    item.optString("target_type"), evidence,
                    item.optDouble("confidence", 0.0), explicit = false,
                ))
            }
        }
    }

    private companion object {
        const val MAX_RELATIONS = 50
        val SYSTEM_PROMPT = """
            Extract a personal semantic knowledge graph from exactly the user text.
            Return JSON: {"relations":[{"source":"用户 or named entity","source_type":"PERSON|ORGANIZATION|PLACE|PROJECT|PREFERENCE|FACT","predicate":"short_snake_case_relation","target":"entity or fact","target_type":"same enum","evidence":"exact substring from user text","confidence":0.0}]}.
            Extract people, relationships, stable preferences, projects, places, and long-term facts.
            Do not infer unstated facts. Evidence must be copied exactly from the input. Use an empty array when nothing durable is present. Maximum 50 relations.
        """.trimIndent()
    }
}
