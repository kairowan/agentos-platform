package com.agentos.shell

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ConversationEntry(
    val id: String,
    val createdAtMillis: Long,
    val prompt: String,
    val responseTitle: String,
)

internal interface ConversationHistory {
    fun load(): List<ConversationEntry>
    fun append(prompt: String, responseTitle: String): List<ConversationEntry>
    fun clear()
}

internal class LocalConversationHistory(context: Context) : ConversationHistory {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun load(): List<ConversationEntry> =
        ConversationHistoryCodec.decode(preferences.getString(KEY_ENTRIES, null))

    override fun append(prompt: String, responseTitle: String): List<ConversationEntry> {
        val updated = (load() + ConversationEntry(
            id = UUID.randomUUID().toString(),
            createdAtMillis = System.currentTimeMillis(),
            prompt = prompt.take(MAX_PROMPT_LENGTH),
            responseTitle = responseTitle.take(MAX_TITLE_LENGTH),
        )).takeLast(MAX_ENTRIES)
        preferences.edit().putString(KEY_ENTRIES, ConversationHistoryCodec.encode(updated)).apply()
        return updated
    }

    override fun clear() {
        preferences.edit().remove(KEY_ENTRIES).apply()
    }

    companion object {
        private const val FILE_NAME = "conversation_history"
        private const val KEY_ENTRIES = "entries"
        private const val MAX_ENTRIES = 100
        private const val MAX_PROMPT_LENGTH = 8_000
        private const val MAX_TITLE_LENGTH = 200
    }
}

internal object ConversationHistoryCodec {
    fun encode(entries: List<ConversationEntry>): String = JSONArray().apply {
        entries.takeLast(MAX_ENTRIES).forEach { entry ->
            put(JSONObject()
                .put("id", entry.id)
                .put("createdAtMillis", entry.createdAtMillis)
                .put("prompt", entry.prompt)
                .put("responseTitle", entry.responseTitle))
        }
    }.toString()

    fun decode(raw: String?): List<ConversationEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in maxOf(0, array.length() - MAX_ENTRIES) until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id").takeIf(String::isNotBlank) ?: continue
                    val prompt = item.optString("prompt").takeIf(String::isNotBlank) ?: continue
                    val title = item.optString("responseTitle").takeIf(String::isNotBlank) ?: continue
                    add(ConversationEntry(
                        id.take(MAX_ID_LENGTH),
                        item.optLong("createdAtMillis").coerceAtLeast(0L),
                        prompt.take(MAX_PROMPT_LENGTH),
                        title.take(MAX_TITLE_LENGTH),
                    ))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private const val MAX_ENTRIES = 100
    private const val MAX_ID_LENGTH = 100
    private const val MAX_PROMPT_LENGTH = 8_000
    private const val MAX_TITLE_LENGTH = 200
}

internal object EmptyConversationHistory : ConversationHistory {
    override fun load() = emptyList<ConversationEntry>()
    override fun append(prompt: String, responseTitle: String) = emptyList<ConversationEntry>()
    override fun clear() = Unit
}
