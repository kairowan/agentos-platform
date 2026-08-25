package com.agentos.shell

import org.json.JSONObject

class GeneratedUiParser {
    fun parse(payload: String): AgentPlan {
        require(payload.toByteArray().size <= MAX_PAYLOAD_BYTES) { "Generated UI payload is too large" }
        val root = JSONObject(payload)
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
        return AgentPlan(GeneratedScreen(title, blocks), capability)
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
        require(keySet().all(allowed::contains)) { "Generated UI contains unknown fields" }
    }

    private fun JSONObject.requiredBoundedString(name: String, maxLength: Int): String =
        getString(name).also {
            require(it.isNotBlank() && it.length <= maxLength) { "Invalid $name" }
        }

    private companion object {
        const val MAX_PAYLOAD_BYTES = 1_048_576
        val ROOT_KEYS = setOf("version", "title", "blocks", "capability")
    }
}
