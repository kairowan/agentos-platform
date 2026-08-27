package com.agentos.shell

import org.json.JSONObject

/** Bounded, data-only message from the native agent state to the isolated renderer. */
internal data class AvatarRenderCommand(
    val mood: Float,
    val gesture: Float,
    val intensity: Float,
    val tempo: Float,
    val gazeX: Float,
    val gazeY: Float,
    val speaking: Float,
    val facePresence: Float,
    val faceWidth: Float,
    val eyeSize: Float,
    val eyeSpacing: Float,
    val mouthWidth: Float,
    val headScale: Float,
    val bodyHeight: Float,
    val shoulderWidth: Float,
    val glow: Float,
) {
    fun toJson(): String = JSONObject()
        .put("protocol", PROTOCOL_VERSION)
        .put("mood", mood.toDouble())
        .put("gesture", gesture.toDouble())
        .put("intensity", intensity.toDouble())
        .put("tempo", tempo.toDouble())
        .put("gazeX", gazeX.toDouble())
        .put("gazeY", gazeY.toDouble())
        .put("speaking", speaking.toDouble())
        .put("facePresence", facePresence.toDouble())
        .put("faceWidth", faceWidth.toDouble())
        .put("eyeSize", eyeSize.toDouble())
        .put("eyeSpacing", eyeSpacing.toDouble())
        .put("mouthWidth", mouthWidth.toDouble())
        .put("headScale", headScale.toDouble())
        .put("bodyHeight", bodyHeight.toDouble())
        .put("shoulderWidth", shoulderWidth.toDouble())
        .put("glow", glow.toDouble())
        .toString()

    /** JSON stays quoted data even if future protocol versions add user-visible strings. */
    fun toJavascript(): String =
        "window.AgentOSAvatar.applyState(JSON.parse(${JSONObject.quote(toJson())}))"

    companion object {
        const val PROTOCOL_VERSION = 1

        fun from(
            avatar: AgentAvatar,
            expression: AvatarExpression,
            performance: AvatarPerformance,
        ): AvatarRenderCommand {
            val motion = performance.normalized()
            val field = ThoughtFieldUniformState.from(expression, motion)
            return AvatarRenderCommand(
                mood = field.mood,
                gesture = field.gesture,
                intensity = field.intensity.finiteIn(0f, 1f, 0.55f),
                tempo = motion.tempo.finiteIn(0.5f, 1.8f, 1f),
                gazeX = field.gazeX.finiteIn(-1f, 1f, 0f),
                gazeY = field.gazeY.finiteIn(-1f, 1f, 0f),
                speaking = field.speaking,
                facePresence = field.facePresence,
                faceWidth = avatar.faceWidth.finiteIn(0f, 1f, 0.5f),
                eyeSize = avatar.eyeSize.finiteIn(0f, 1f, 0.55f),
                eyeSpacing = avatar.eyeSpacing.finiteIn(0f, 1f, 0.5f),
                mouthWidth = avatar.mouthWidth.finiteIn(0f, 1f, 0.5f),
                headScale = avatar.headScale.finiteIn(0f, 1f, 0.55f),
                bodyHeight = avatar.bodyHeight.finiteIn(0f, 1f, 0.5f),
                shoulderWidth = avatar.shoulderWidth.finiteIn(0f, 1f, 0.5f),
                glow = avatar.glow.finiteIn(0f, 1f, 0.2f),
            )
        }

        private fun Float.finiteIn(minimum: Float, maximum: Float, fallback: Float): Float =
            if (isFinite()) coerceIn(minimum, maximum) else fallback
    }
}
