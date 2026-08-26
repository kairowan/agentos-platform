package com.agentos.shell

enum class AvatarEmotion { NEUTRAL, HAPPY, EXCITED, FOCUSED, CONCERNED, SURPRISED, CALM }

enum class AvatarGesture { IDLE, LISTEN, THINK, TALK, NOD, WAVE, POINT, CELEBRATE, COMFORT, EXPLAIN }

/** A deliberately bounded direction from the untrusted model to the native avatar rig. */
data class AvatarPerformance(
    val emotion: AvatarEmotion = AvatarEmotion.NEUTRAL,
    val gesture: AvatarGesture = AvatarGesture.IDLE,
    val intensity: Float = 0.55f,
    val tempo: Float = 1f,
    val gazeX: Float = 0f,
    val gazeY: Float = 0f,
) {
    fun normalized() = copy(
        intensity = intensity.coerceIn(0f, 1f),
        tempo = tempo.coerceIn(0.5f, 1.8f),
        gazeX = gazeX.coerceIn(-1f, 1f),
        gazeY = gazeY.coerceIn(-1f, 1f),
    )
}

internal fun AgentUiState.avatarPerformance(): AvatarPerformance = when {
    isSpeaking || voiceReply != null -> performance.copy(gesture = AvatarGesture.TALK)
    isWorking -> AvatarPerformance(AvatarEmotion.FOCUSED, AvatarGesture.THINK, 0.58f, 0.85f)
    voiceStatus.contains("聆听") || voiceStatus.contains("已识别") ->
        AvatarPerformance(AvatarEmotion.CALM, AvatarGesture.LISTEN, 0.45f, 0.8f)
    notice != null -> AvatarPerformance(AvatarEmotion.CONCERNED, AvatarGesture.COMFORT, 0.42f, 0.75f)
    else -> performance
}

internal fun AvatarPerformance.expression(): AvatarExpression = when (emotion) {
    AvatarEmotion.HAPPY, AvatarEmotion.EXCITED -> AvatarExpression.HAPPY
    AvatarEmotion.FOCUSED -> AvatarExpression.THINKING
    AvatarEmotion.CONCERNED -> AvatarExpression.CONCERNED
    AvatarEmotion.SURPRISED -> AvatarExpression.SURPRISED
    AvatarEmotion.CALM, AvatarEmotion.NEUTRAL -> AvatarExpression.NEUTRAL
}
