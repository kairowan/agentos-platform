package com.agentos.shell

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeExtractorTest {
    @Test
    fun extractsExplicitPeoplePreferencesAndProjectsLocally() {
        val facts = LocalKnowledgeExtractor.extract("我叫小南。我喜欢 Kotlin。小明是我的朋友。我正在开发 AgentOS。")

        assertTrue(facts.any { it.predicate == "name" && it.targetName == "小南" })
        assertTrue(facts.any { it.predicate == "likes" && it.targetName == "Kotlin" })
        assertTrue(facts.any { it.predicate == "朋友" && it.targetName == "小明" })
        assertTrue(facts.any { it.predicate == "develops" && it.targetName == "AgentOS" })
    }

    @Test
    fun modelFactsRequireExactSourceEvidence() {
        val extractor = ModelKnowledgeExtractor(ModelConfig("https://example.com", "model", ""))
        val json = JSONObject("""{"relations":[
          {"source":"用户","source_type":"PERSON","predicate":"likes","target":"Kotlin","target_type":"PREFERENCE","evidence":"我喜欢 Kotlin","confidence":0.9},
          {"source":"用户","source_type":"PERSON","predicate":"lives_in","target":"上海","target_type":"PLACE","evidence":"我住在上海","confidence":0.9}
        ]}""")

        val facts = extractor.parse(json, "我喜欢 Kotlin")
        assertEquals(1, facts.size)
        assertEquals("Kotlin", facts.single().targetName)
    }
}
