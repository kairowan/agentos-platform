package com.agentos.shell

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

data class ConversationEntry(
    val id: String,
    val createdAtMillis: Long,
    val prompt: String,
    val responseTitle: String,
)

data class KnowledgeEntity(val id: String, val name: String, val type: String)

data class KnowledgeRelation(
    val source: KnowledgeEntity,
    val predicate: String,
    val target: KnowledgeEntity,
    val evidence: String,
    val sourceTurnId: String,
    val confidence: Double,
    val confirmed: Boolean,
)

data class KnowledgeCandidate(
    val sourceName: String,
    val sourceType: String,
    val predicate: String,
    val targetName: String,
    val targetType: String,
    val evidence: String,
    val confidence: Double,
    val explicit: Boolean,
)

data class KnowledgeGraph(
    val entities: List<KnowledgeEntity> = emptyList(),
    val relations: List<KnowledgeRelation> = emptyList(),
)

internal data class HistorySnapshot(
    val turnId: String,
    val entries: List<ConversationEntry>,
    val graph: KnowledgeGraph,
)

internal interface ConversationHistory {
    fun load(): List<ConversationEntry>
    fun loadGraph(): KnowledgeGraph
    fun append(prompt: String, responseTitle: String, facts: List<KnowledgeCandidate>): HistorySnapshot
    fun merge(turnId: String, facts: List<KnowledgeCandidate>): KnowledgeGraph
    fun clear()
    fun close()
}

internal class LocalConversationHistory(private val context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION), ConversationHistory {

    init {
        writableDatabase
        migrateLegacyHistory()
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE turns (id TEXT PRIMARY KEY, created_at INTEGER NOT NULL, prompt TEXT NOT NULL, response_title TEXT NOT NULL)")
        db.execSQL("CREATE TABLE entities (id TEXT PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL)")
        db.execSQL("CREATE TABLE relations (id TEXT PRIMARY KEY, source_id TEXT NOT NULL REFERENCES entities(id) ON DELETE CASCADE, predicate TEXT NOT NULL, target_id TEXT NOT NULL REFERENCES entities(id) ON DELETE CASCADE, evidence TEXT NOT NULL, turn_id TEXT NOT NULL REFERENCES turns(id) ON DELETE CASCADE, confidence REAL NOT NULL, confirmed INTEGER NOT NULL, UNIQUE(source_id, predicate, target_id, evidence, turn_id))")
        db.execSQL("CREATE INDEX relation_turn_idx ON relations(turn_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    override fun load(): List<ConversationEntry> = readableDatabase.rawQuery(
        "SELECT id, created_at, prompt, response_title FROM turns ORDER BY created_at, rowid",
        null,
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(ConversationEntry(
                cursor.getString(0), cursor.getLong(1), cursor.getString(2), cursor.getString(3),
            ))
        }
    }

    override fun loadGraph(): KnowledgeGraph {
        val entities = readableDatabase.rawQuery(
            "SELECT id, name, type FROM entities ORDER BY type, name", null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(KnowledgeEntity(
                    cursor.getString(0), cursor.getString(1), cursor.getString(2),
                ))
            }
        }
        val byId = entities.associateBy(KnowledgeEntity::id)
        val relations = readableDatabase.rawQuery(
            "SELECT source_id, predicate, target_id, evidence, turn_id, confidence, confirmed FROM relations ORDER BY rowid",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val source = byId[cursor.getString(0)] ?: continue
                    val target = byId[cursor.getString(2)] ?: continue
                    add(KnowledgeRelation(source, cursor.getString(1), target,
                        cursor.getString(3), cursor.getString(4), cursor.getDouble(5), cursor.getInt(6) == 1))
                }
            }
        }
        return KnowledgeGraph(entities, relations)
    }

    override fun append(
        prompt: String,
        responseTitle: String,
        facts: List<KnowledgeCandidate>,
    ): HistorySnapshot {
        val turnId = UUID.randomUUID().toString()
        writableDatabase.inTransaction {
            insertOrThrow("turns", null, ContentValues().apply {
                put("id", turnId)
                put("created_at", System.currentTimeMillis())
                put("prompt", prompt.take(MAX_PROMPT_LENGTH))
                put("response_title", responseTitle.take(MAX_TITLE_LENGTH))
            })
            insertFacts(this, turnId, facts)
        }
        return HistorySnapshot(turnId, load(), loadGraph())
    }

    override fun merge(turnId: String, facts: List<KnowledgeCandidate>): KnowledgeGraph {
        writableDatabase.inTransaction { insertFacts(this, turnId, facts) }
        return loadGraph()
    }

    override fun clear() {
        writableDatabase.inTransaction {
            delete("turns", null, null)
            delete("entities", null, null)
        }
    }

    private fun insertFacts(db: SQLiteDatabase, turnId: String, facts: List<KnowledgeCandidate>) {
        facts.take(MAX_FACTS_PER_TURN).forEach { raw ->
            val fact = raw.validated() ?: return@forEach
            val source = entity(fact.sourceName, fact.sourceType)
            val target = entity(fact.targetName, fact.targetType)
            listOf(source, target).forEach { item ->
                db.insertWithOnConflict("entities", null, ContentValues().apply {
                    put("id", item.id); put("name", item.name); put("type", item.type)
                }, SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.insertWithOnConflict("relations", null, ContentValues().apply {
                put("id", UUID.randomUUID().toString())
                put("source_id", source.id)
                put("predicate", fact.predicate)
                put("target_id", target.id)
                put("evidence", fact.evidence)
                put("turn_id", turnId)
                put("confidence", fact.confidence)
                put("confirmed", if (fact.explicit) 1 else 0)
            }, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    private fun migrateLegacyHistory() {
        if (load().isNotEmpty()) return
        val preferences = context.getSharedPreferences(LEGACY_FILE_NAME, Context.MODE_PRIVATE)
        val entries = ConversationHistoryCodec.decode(preferences.getString(LEGACY_KEY_ENTRIES, null))
        if (entries.isEmpty()) return
        writableDatabase.inTransaction {
            entries.forEach { entry -> insertWithOnConflict("turns", null, ContentValues().apply {
                put("id", entry.id); put("created_at", entry.createdAtMillis)
                put("prompt", entry.prompt); put("response_title", entry.responseTitle)
            }, SQLiteDatabase.CONFLICT_IGNORE) }
        }
        preferences.edit().remove(LEGACY_KEY_ENTRIES).apply()
    }

    private companion object {
        const val DATABASE_NAME = "agent_knowledge.db"
        const val DATABASE_VERSION = 1
        const val LEGACY_FILE_NAME = "conversation_history"
        const val LEGACY_KEY_ENTRIES = "entries"
        const val MAX_PROMPT_LENGTH = 8_000
        const val MAX_TITLE_LENGTH = 200
        const val MAX_FACTS_PER_TURN = 50
    }
}

internal object ConversationHistoryCodec {
    fun encode(entries: List<ConversationEntry>): String = JSONArray().apply {
        entries.forEach { entry -> put(JSONObject()
            .put("id", entry.id).put("createdAtMillis", entry.createdAtMillis)
            .put("prompt", entry.prompt).put("responseTitle", entry.responseTitle)) }
    }.toString()

    fun decode(raw: String?): List<ConversationEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id").takeIf(String::isNotBlank) ?: continue
                    val prompt = item.optString("prompt").takeIf(String::isNotBlank) ?: continue
                    val title = item.optString("responseTitle").takeIf(String::isNotBlank) ?: continue
                    add(ConversationEntry(id.take(100), item.optLong("createdAtMillis").coerceAtLeast(0),
                        prompt.take(8_000), title.take(200)))
                }
            }
        } catch (_: Exception) { emptyList() }
    }
}

internal object EmptyConversationHistory : ConversationHistory {
    override fun load() = emptyList<ConversationEntry>()
    override fun loadGraph() = KnowledgeGraph()
    override fun append(prompt: String, responseTitle: String, facts: List<KnowledgeCandidate>) =
        HistorySnapshot("", emptyList(), KnowledgeGraph())
    override fun merge(turnId: String, facts: List<KnowledgeCandidate>) = KnowledgeGraph()
    override fun clear() = Unit
    override fun close() = Unit
}

private fun SQLiteDatabase.inTransaction(block: SQLiteDatabase.() -> Unit) {
    beginTransaction()
    try { block(); setTransactionSuccessful() } finally { endTransaction() }
}

private fun KnowledgeCandidate.validated(): KnowledgeCandidate? {
    val normalized = copy(
        sourceName = sourceName.trim().take(100), sourceType = sourceType.trim().uppercase().take(30),
        predicate = predicate.trim().lowercase().take(50), targetName = targetName.trim().take(100),
        targetType = targetType.trim().uppercase().take(30), evidence = evidence.trim().take(300),
        confidence = if (confidence.isFinite()) confidence.coerceIn(0.0, 1.0) else 0.0,
    )
    return normalized.takeIf {
        it.sourceName.isNotEmpty() && it.sourceType in KNOWLEDGE_TYPES &&
            it.predicate.matches(Regex("[\\p{L}\\p{N}_-]{1,50}")) && it.targetName.isNotEmpty() &&
            it.targetType in KNOWLEDGE_TYPES && it.evidence.isNotEmpty()
    }
}

private val KNOWLEDGE_TYPES = setOf(
    "PERSON", "ORGANIZATION", "PLACE", "PROJECT", "PREFERENCE", "FACT",
)

private fun entity(name: String, type: String): KnowledgeEntity {
    val normalizedName = name.trim().take(100)
    val normalizedType = type.trim().uppercase().take(30)
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$normalizedType\u0000${normalizedName.lowercase()}".toByteArray())
        .take(16).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    return KnowledgeEntity(digest, normalizedName, normalizedType)
}
