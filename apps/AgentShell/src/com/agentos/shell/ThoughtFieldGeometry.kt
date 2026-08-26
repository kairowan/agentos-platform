package com.agentos.shell

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Deterministic animated volume sampled by the GLES thought-field renderer. */
internal object ThoughtFieldGeometry {
    const val POINT_COUNT = 144
    private const val GOLDEN_ANGLE = 2.3999632f

    fun writePosition(index: Int, seconds: Float, intensity: Float, target: FloatArray, offset: Int) {
        require(index in 0 until POINT_COUNT)
        require(target.size >= offset + 3)
        val progress = (index + 0.5f) / POINT_COUNT
        val latitude = progress * PI.toFloat()
        val angle = index * GOLDEN_ANGLE + seconds * (0.08f + intensity * 0.08f)
        val envelope = 0.24f + sin(latitude) * 0.72f
        val phase = index * 0.71f
        target[offset] = cos(angle) * envelope + sin(seconds * 0.7f + phase) * 0.045f
        target[offset + 1] = 1.62f - progress * 3.18f + sin(seconds * 0.52f + phase) * 0.055f
        target[offset + 2] = sin(angle) * envelope * 0.62f + cos(seconds * 0.43f + phase) * 0.035f
    }
}
