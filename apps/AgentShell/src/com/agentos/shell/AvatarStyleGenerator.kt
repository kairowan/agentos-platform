package com.agentos.shell

import org.json.JSONObject

class AvatarStyleGenerator(private val config: ModelConfig) {
    suspend fun generate(prompt: String, current: AgentAvatar): AgentAvatar {
        val request = prompt.trim()
        require(request.isNotBlank() && request.length <= MAX_PROMPT_LENGTH) { "Invalid avatar style prompt" }
        val payload = openAiJson(
            config,
            SYSTEM_PROMPT,
            "用户要求：$request\n当前角色：${current.toStyleJson()}",
        )
        return AvatarStyleParser().parse(payload, current)
    }

    private companion object {
        const val MAX_PROMPT_LENGTH = 1_000
        val SYSTEM_PROMPT = """
            You design a safe procedural 3D avatar for AgentOS. Return exactly one JSON object and
            no markdown. You may only choose the declared enum values and normalized numbers. Never
            output URLs, code, shaders, file paths, scripts, asset downloads, or extra fields.

            Required schema version 1 fields:
            version: 1
            styleDescription: Chinese string, 1..200 characters
            styleFamily: SYSTEM | SOFT | ANIME | CYBER | FANTASY | REALISTIC
            material: MATTE | GLOSS | METAL | HOLOGRAM
            outfitStyle: MINIMAL | SUIT | ARMOR | ROBE | STREET
            accessory: NONE | VISOR | HEADSET | HALO | HORNS
            faceShape: ROUND | OVAL | HEART | SQUARE
            hairStyle: SHORT | WAVY | BOB | PONYTAIL | BUZZ | BALD
            eyeStyle: ROUND | CALM | BRIGHT | SHARP
            skinTone: LIGHT | WARM | TAN | DEEP
            hairColor: INK | BROWN | COPPER | SILVER | MINT
            outfitColor: MINT | BLUE | AMBER | VIOLET | GRAPHITE
            faceWidth, eyeSize, eyeSpacing, mouthWidth, headScale, bodyHeight,
            shoulderWidth, glow: JSON numbers from 0 to 1 inclusive.
        """.trimIndent()
    }
}

class AvatarStyleParser {
    fun parse(root: JSONObject, current: AgentAvatar = AgentAvatar()): AgentAvatar {
        root.requireOnly(KEYS)
        require(root.getInt("version") == 1) { "Unsupported avatar style version" }
        return current.copy(
            styleDescription = root.boundedString("styleDescription", AgentAvatar.MAX_STYLE_LENGTH),
            styleFamily = root.enum("styleFamily"),
            material = root.enum("material"),
            outfitStyle = root.enum("outfitStyle"),
            accessory = root.enum("accessory"),
            faceShape = root.enum("faceShape"),
            hairStyle = root.enum("hairStyle"),
            eyeStyle = root.enum("eyeStyle"),
            skinTone = root.enum("skinTone"),
            hairColor = root.enum("hairColor"),
            outfitColor = root.enum("outfitColor"),
            faceWidth = root.unit("faceWidth"),
            eyeSize = root.unit("eyeSize"),
            eyeSpacing = root.unit("eyeSpacing"),
            mouthWidth = root.unit("mouthWidth"),
            headScale = root.unit("headScale"),
            bodyHeight = root.unit("bodyHeight"),
            shoulderWidth = root.unit("shoulderWidth"),
            glow = root.unit("glow"),
        ).normalized()
    }

    private fun JSONObject.requireOnly(allowed: Set<String>) {
        val names = keys()
        while (names.hasNext()) require(names.next() in allowed) { "Avatar style contains unknown fields" }
        require(allowed.all { has(it) }) { "Avatar style is missing fields" }
    }

    private fun JSONObject.boundedString(key: String, max: Int) = getString(key).also {
        require(it.isNotBlank() && it.length <= max) { "Invalid $key" }
    }

    private fun JSONObject.unit(key: String) = getDouble(key).also {
        require(it.isFinite() && it in 0.0..1.0) { "Invalid $key" }
    }.toFloat()

    private inline fun <reified T : Enum<T>> JSONObject.enum(key: String): T =
        runCatching { enumValueOf<T>(getString(key)) }
            .getOrElse { throw IllegalArgumentException("Invalid $key") }

    private companion object {
        val KEYS = setOf(
            "version", "styleDescription", "styleFamily", "material", "outfitStyle", "accessory",
            "faceShape", "hairStyle", "eyeStyle", "skinTone", "hairColor", "outfitColor",
            "faceWidth", "eyeSize", "eyeSpacing", "mouthWidth", "headScale", "bodyHeight",
            "shoulderWidth", "glow",
        )
    }
}

private fun AgentAvatar.toStyleJson() = JSONObject()
    .put("styleFamily", styleFamily.name)
    .put("material", material.name)
    .put("outfitStyle", outfitStyle.name)
    .put("accessory", accessory.name)
    .put("faceShape", faceShape.name)
    .put("hairStyle", hairStyle.name)
    .put("eyeStyle", eyeStyle.name)
    .put("skinTone", skinTone.name)
    .put("hairColor", hairColor.name)
    .put("outfitColor", outfitColor.name)
    .put("faceWidth", faceWidth)
    .put("eyeSize", eyeSize)
    .put("eyeSpacing", eyeSpacing)
    .put("mouthWidth", mouthWidth)
    .put("headScale", headScale)
    .put("bodyHeight", bodyHeight)
    .put("shoulderWidth", shoulderWidth)
    .put("glow", glow)
