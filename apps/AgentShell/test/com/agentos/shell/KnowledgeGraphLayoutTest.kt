package com.agentos.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun cullsOnlyEdgesWhoseBoundsMissTheViewport() {
        assertTrue(lineMayIntersectViewport(
            androidx.compose.ui.geometry.Offset(-10f, 50f),
            androidx.compose.ui.geometry.Offset(110f, 50f),
            0f, 0f, 100f, 100f,
        ))
        assertFalse(lineMayIntersectViewport(
            androidx.compose.ui.geometry.Offset(-20f, -20f),
            androidx.compose.ui.geometry.Offset(-10f, -10f),
            0f, 0f, 100f, 100f,
        ))
    }
}
