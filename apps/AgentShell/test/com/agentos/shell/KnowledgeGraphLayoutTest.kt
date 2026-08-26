package com.agentos.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class KnowledgeGraphLayoutTest {
    @Test
    fun laysOutEveryEntityWithoutOverlapWithinAType() {
        val entities = listOf(
            KnowledgeEntity("1", "用户", "PERSON"),
            KnowledgeEntity("2", "小明", "PERSON"),
            KnowledgeEntity("3", "AgentOS", "PROJECT"),
        )
        val nodes = layoutKnowledgeGraph(entities)

        assertEquals(entities.toSet(), nodes.map { it.entity }.toSet())
        assertFalse(nodes[0].contains(nodes[1].center))
    }
}
