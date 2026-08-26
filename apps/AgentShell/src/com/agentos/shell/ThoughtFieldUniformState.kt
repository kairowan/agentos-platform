package com.agentos.shell

/** Pure state projection kept separate from GLES so interaction precedence is unit-testable. */
internal data class ThoughtFieldUniformState(
    val mood: Float,
    val gesture: Float,
    val intensity: Float,
    val gazeX: Float,
    val gazeY: Float,
    val speaking: Float,
    val facePresence: Float,
) {
    companion object {
        fun from(expression: AvatarExpression, performance: AvatarPerformance): ThoughtFieldUniformState {
            val value = performance.normalized()
            val communicating = expression == AvatarExpression.SPEAKING ||
                expression == AvatarExpression.LISTENING || expression == AvatarExpression.HAPPY ||
                expression == AvatarExpression.SURPRISED || value.gesture == AvatarGesture.TALK
            return ThoughtFieldUniformState(
                mood = expression.ordinal.toFloat(),
                gesture = value.gesture.ordinal.toFloat(),
                intensity = value.intensity,
                gazeX = value.gazeX,
                gazeY = value.gazeY,
                speaking = if (expression == AvatarExpression.SPEAKING ||
                    value.gesture == AvatarGesture.TALK) 1f else 0f,
                facePresence = when {
                    communicating -> 0.96f
                    expression == AvatarExpression.SLEEPY -> 0.08f
                    expression == AvatarExpression.THINKING -> 0.22f
                    else -> 0.38f
                },
            )
        }
    }
}
