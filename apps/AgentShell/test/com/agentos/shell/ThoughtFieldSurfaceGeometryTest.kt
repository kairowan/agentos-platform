package com.agentos.shell

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThoughtFieldSurfaceGeometryTest {
    @Test
    fun createsClosedIndexedParameterSurface() {
        val rings = 4
        val segments = 6
        val mesh = ThoughtFieldSurfaceGeometry.create(rings, segments)

        assertEquals((rings + 1) * (segments + 1) * 3, mesh.parameters.size)
        assertEquals(rings * segments * 6, mesh.indices.size)
        assertTrue(mesh.parameters.all { it.isFinite() })
        assertEquals(0f, mesh.parameters.first())
        assertEquals(1f, mesh.parameters[(rings * (segments + 1)) * 3])

        val first = mesh.parameters.sliceArray(0..2)
        val seam = mesh.parameters.sliceArray((segments * 3)..(segments * 3 + 2))
        assertArrayEquals(first, seam, 0.00001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSurfaceWithoutEnoughSegments() {
        ThoughtFieldSurfaceGeometry.create(rings = 4, segments = 2)
    }
}
