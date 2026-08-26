package com.agentos.shell

import android.content.Context
import kotlin.random.Random

enum class AvatarFaceShape(val label: String) { ROUND("圆润"), OVAL("椭圆"), HEART("心形"), SQUARE("方形") }
enum class AvatarHairStyle(val label: String) { SHORT("短发"), WAVY("卷发"), BOB("波波头"), PONYTAIL("马尾"), BUZZ("寸头"), BALD("无发型") }
enum class AvatarEyeStyle(val label: String) { ROUND("圆眼"), CALM("柔和"), BRIGHT("明亮"), SHARP("锐利") }
enum class AvatarSkinTone(val label: String, val argb: Long) {
    LIGHT("浅色", 0xFFFFD8C2), WARM("暖色", 0xFFE8AC86), TAN("小麦", 0xFFC98663), DEEP("深色", 0xFF80513D),
}
enum class AvatarHairColor(val label: String, val argb: Long) {
    INK("墨黑", 0xFF172126), BROWN("棕色", 0xFF654536), COPPER("赤铜", 0xFF9A4E36), SILVER("银灰", 0xFFB8C6CC), MINT("薄荷", 0xFF55CDB1),
}
enum class AvatarOutfitColor(val label: String, val argb: Long) {
    MINT("薄荷", 0xFF36BFA0), BLUE("深蓝", 0xFF426DAE), AMBER("琥珀", 0xFFC68B32), VIOLET("紫罗兰", 0xFF7253A6), GRAPHITE("石墨", 0xFF33464E),
}
enum class AvatarExpression(val label: String) {
    NEUTRAL("自然"), HAPPY("开心"), LISTENING("聆听"), THINKING("思考"), SURPRISED("惊讶"),
    CONCERNED("关心"), SPEAKING("说话"), SLEEPY("休息"),
}

data class AgentAvatar(
    val name: String = "小 A",
    val faceShape: AvatarFaceShape = AvatarFaceShape.OVAL,
    val hairStyle: AvatarHairStyle = AvatarHairStyle.SHORT,
    val eyeStyle: AvatarEyeStyle = AvatarEyeStyle.BRIGHT,
    val skinTone: AvatarSkinTone = AvatarSkinTone.WARM,
    val hairColor: AvatarHairColor = AvatarHairColor.INK,
    val outfitColor: AvatarOutfitColor = AvatarOutfitColor.MINT,
    val faceWidth: Float = 0.5f,
    val eyeSize: Float = 0.55f,
    val eyeSpacing: Float = 0.5f,
    val mouthWidth: Float = 0.5f,
) {
    fun normalized() = copy(
        name = name.trim().take(MAX_NAME_LENGTH).ifBlank { "小 A" },
        faceWidth = faceWidth.coerceIn(0f, 1f),
        eyeSize = eyeSize.coerceIn(0f, 1f),
        eyeSpacing = eyeSpacing.coerceIn(0f, 1f),
        mouthWidth = mouthWidth.coerceIn(0f, 1f),
    )

    companion object { const val MAX_NAME_LENGTH = 24 }
}

internal fun AgentUiState.avatarExpression(): AvatarExpression = when {
    isWorking -> AvatarExpression.THINKING
    voiceReply != null || isSpeaking -> AvatarExpression.SPEAKING
    voiceStatus.contains("聆听") || voiceStatus.contains("已识别") -> AvatarExpression.LISTENING
    notice != null -> AvatarExpression.CONCERNED
    else -> AvatarExpression.NEUTRAL
}

internal fun randomAgentAvatar(random: Random = Random.Default) = AgentAvatar(
    faceShape = AvatarFaceShape.entries.random(random),
    hairStyle = AvatarHairStyle.entries.random(random),
    eyeStyle = AvatarEyeStyle.entries.random(random),
    skinTone = AvatarSkinTone.entries.random(random),
    hairColor = AvatarHairColor.entries.random(random),
    outfitColor = AvatarOutfitColor.entries.random(random),
    faceWidth = random.nextFloat(),
    eyeSize = random.nextFloat(),
    eyeSpacing = random.nextFloat(),
    mouthWidth = random.nextFloat(),
)

interface AgentAvatarStore {
    fun load(): AgentAvatar
    fun save(avatar: AgentAvatar)
}

object EmptyAgentAvatarStore : AgentAvatarStore {
    override fun load() = AgentAvatar()
    override fun save(avatar: AgentAvatar) = Unit
}

class LocalAgentAvatarStore(context: Context) : AgentAvatarStore {
    private val prefs = context.getSharedPreferences("agent_avatar", Context.MODE_PRIVATE)

    override fun load() = AgentAvatar(
        name = prefs.getString("name", null) ?: "小 A",
        faceShape = enumValue("face", AvatarFaceShape.OVAL),
        hairStyle = enumValue("hair", AvatarHairStyle.SHORT),
        eyeStyle = enumValue("eyes", AvatarEyeStyle.BRIGHT),
        skinTone = enumValue("skin", AvatarSkinTone.WARM),
        hairColor = enumValue("hair_color", AvatarHairColor.INK),
        outfitColor = enumValue("outfit", AvatarOutfitColor.MINT),
        faceWidth = prefs.getFloat("face_width", 0.5f),
        eyeSize = prefs.getFloat("eye_size", 0.55f),
        eyeSpacing = prefs.getFloat("eye_spacing", 0.5f),
        mouthWidth = prefs.getFloat("mouth_width", 0.5f),
    ).normalized()

    override fun save(avatar: AgentAvatar) {
        val value = avatar.normalized()
        prefs.edit()
            .putString("name", value.name)
            .putString("face", value.faceShape.name)
            .putString("hair", value.hairStyle.name)
            .putString("eyes", value.eyeStyle.name)
            .putString("skin", value.skinTone.name)
            .putString("hair_color", value.hairColor.name)
            .putString("outfit", value.outfitColor.name)
            .putFloat("face_width", value.faceWidth)
            .putFloat("eye_size", value.eyeSize)
            .putFloat("eye_spacing", value.eyeSpacing)
            .putFloat("mouth_width", value.mouthWidth)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumValue(key: String, fallback: T): T =
        prefs.getString(key, null)?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
}
