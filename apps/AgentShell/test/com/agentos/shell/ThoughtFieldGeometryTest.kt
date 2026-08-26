package com.agentos.shell

import kotlin.math.abs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThoughtFieldGeometryTest {
    @Test
    fun generatedFieldIsFiniteBoundedAndAlive() {
        val start = FloatArray(ThoughtFieldGeometry.POINT_COUNT * 3)
        val later = FloatArray(start.size)

        repeat(ThoughtFieldGeometry.POINT_COUNT) { index ->
            ThoughtFieldGeometry.writePosition(index, 0f, 0.55f, start, index * 3)
            ThoughtFieldGeometry.writePosition(index, 1f, 0.55f, later, index * 3)
        }

        assertTrue(start.all { it.isFinite() })
        assertTrue(start.indices.step(3).all { abs(start[it]) <= 1.1f })
        assertTrue((1 until start.size step 3).all { start[it] in -1.7f..1.7f })
        assertTrue((2 until start.size step 3).all { abs(start[it]) <= 0.7f })
        assertFalse(start.contentEquals(later))
    }
}
