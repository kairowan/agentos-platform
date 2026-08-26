package com.agentos.shell

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal data class ThoughtFieldSurfaceData(
    val parameters: FloatArray,
    val indices: ShortArray,
)

/** Parameter mesh; the vertex shader turns each ring into the animated thought-field surface. */
internal object ThoughtFieldSurfaceGeometry {
    const val DEFAULT_RINGS = 64
    const val DEFAULT_SEGMENTS = 28

    fun create(
        rings: Int = DEFAULT_RINGS,
        segments: Int = DEFAULT_SEGMENTS,
    ): ThoughtFieldSurfaceData {
        require(rings >= 2) { "At least two longitudinal rings are required" }
        require(segments >= 3) { "At least three radial segments are required" }
        val vertexCount = (rings + 1) * (segments + 1)
        require(vertexCount <= UShort.MAX_VALUE.toInt()) { "Mesh exceeds GLES2 unsigned-short indices" }

        val parameters = FloatArray(vertexCount * 3)
        var vertexOffset = 0
        for (ring in 0..rings) {
            val progress = ring.toFloat() / rings
            for (segment in 0..segments) {
                val angle = 2.0 * PI * segment / segments
                parameters[vertexOffset++] = progress
                parameters[vertexOffset++] = cos(angle).toFloat()
                parameters[vertexOffset++] = sin(angle).toFloat()
            }
        }

        val indices = ShortArray(rings * segments * 6)
        var indexOffset = 0
        for (ring in 0 until rings) {
            for (segment in 0 until segments) {
                val first = ring * (segments + 1) + segment
                val second = first + segments + 1
                indices[indexOffset++] = first.toShort()
                indices[indexOffset++] = second.toShort()
                indices[indexOffset++] = (first + 1).toShort()
                indices[indexOffset++] = second.toShort()
                indices[indexOffset++] = (second + 1).toShort()
                indices[indexOffset++] = (first + 1).toShort()
            }
        }
        return ThoughtFieldSurfaceData(parameters, indices)
    }
}
