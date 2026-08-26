package com.agentos.shell

import com.agentos.capability.core.CapabilityId
import org.json.JSONObject
import org.json.JSONTokener

class GeneratedUiParser {
    fun parse(payload: String): AgentPlan {
        require(payload.toByteArray().size <= MAX_PAYLOAD_BYTES) { "Generated UI payload is too large" }
        val tokener = JSONTokener(payload)
        val root = tokener.nextValue() as? JSONObject
            ?: throw IllegalArgumentException("Generated UI root must be an object")
        require(tokener.nextClean() == 0.toChar()) { "Generated UI contains trailing content" }
        root.requireOnly(ROOT_KEYS)
        require(root.getInt("version") == 1) { "Unsupported generated UI version" }

        val title = root.requiredBoundedString("title", 120)
        val rawBlocks = root.getJSONArray("blocks")
        require(rawBlocks.length() <= 100) { "Too many generated UI blocks" }
        val blocks = buildList {
            for (index in 0 until rawBlocks.length()) {
                add(parseBlock(rawBlocks.getJSONObject(index)))
            }
        }

        val capability = if (root.has("capability") && !root.isNull("capability")) {
            val wireId = root.requiredBoundedString("capability", 100)
            requireNotNull(CapabilityId.fromWire(wireId)) { "Unknown capability: $wireId" }
        } else {
            null
        }
        val performance = if (root.has("performance")) parsePerformance(root.getJSONObject("performance"))
        else AvatarPerformance()
        return AgentPlan(GeneratedScreen(title, blocks), capability, performance)
    }

    private fun parsePerformance(value: JSONObject): AvatarPerformance {
        value.requireOnly(PERFORMANCE_KEYS)
        return AvatarPerformance(
            emotion = enumValueOf<AvatarEmotion>(value.requiredBoundedString("emotion", 20).uppercase()),
            gesture = enumValueOf<AvatarGesture>(value.requiredBoundedString("gesture", 20).uppercase()),
            intensity = value.boundedFloat("intensity", 0f, 1f),
            tempo = value.boundedFloat("tempo", 0.5f, 1.8f),
            gazeX = value.boundedFloat("gazeX", -1f, 1f),
            gazeY = value.boundedFloat("gazeY", -1f, 1f),
        )
    }

    private fun parseBlock(block: JSONObject): UiBlock = when (block.getString("type")) {
        "paragraph" -> {
            block.requireOnly(setOf("type", "text"))
            UiBlock.Paragraph(block.requiredBoundedString("text", 4_000))
        }
        "fact" -> {
            block.requireOnly(setOf("type", "label", "value"))
            UiBlock.Fact(
                block.requiredBoundedString("label", 80),
                block.requiredBoundedString("value", 500),
            )
        }
        "action" -> {
            block.requireOnly(setOf("type", "label", "prompt"))
            UiBlock.Action(
                block.requiredBoundedString("label", 80),
                block.requiredBoundedString("prompt", 1_000),
            )
        }
        else -> throw IllegalArgumentException("Unsupported generated UI block")
    }

    private fun JSONObject.requireOnly(allowed: Set<String>) {
        val names = keys()
        while (names.hasNext()) {
            require(names.next() in allowed) { "Generated UI contains unknown fields" }
        }
    }

    private fun JSONObject.requiredBoundedString(name: String, maxLength: Int): String =
        getString(name).also {
            require(it.isNotBlank() && it.length <= maxLength) { "Invalid $name" }
        }

    private fun JSONObject.boundedFloat(name: String, min: Float, max: Float): Float =
        getDouble(name).toFloat().also {
            require(it.isFinite() && it in min..max) { "Invalid $name" }
        }

    private companion object {
        const val MAX_PAYLOAD_BYTES = 1_048_576
        val ROOT_KEYS = setOf("version", "title", "blocks", "capability", "performance")
        val PERFORMANCE_KEYS = setOf("emotion", "gesture", "intensity", "tempo", "gazeX", "gazeY")
    }
}
