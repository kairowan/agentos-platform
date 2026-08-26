package com.agentos.shell

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

@Entity(tableName = "turns")
data class ConversationEntry(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    val prompt: String,
    @ColumnInfo(name = "response_title") val responseTitle: String,
)

@Entity(tableName = "entities")
data class KnowledgeEntity(@PrimaryKey val id: String, val name: String, val type: String)

@Entity(
    tableName = "node_positions",
    foreignKeys = [ForeignKey(
        entity = KnowledgeEntity::class,
        parentColumns = ["id"],
        childColumns = ["entity_id"],
        onDelete = ForeignKey.CASCADE,
        onUpdate = ForeignKey.CASCADE,
    )],
)
data class StoredNodePosition(
    @PrimaryKey @ColumnInfo(name = "entity_id") val entityId: String,
    val x: Float,
    val y: Float,
)

@Entity(
    tableName = "relations",
    foreignKeys = [
        ForeignKey(entity = KnowledgeEntity::class, parentColumns = ["id"], childColumns = ["source_id"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE),
        ForeignKey(entity = KnowledgeEntity::class, parentColumns = ["id"], childColumns = ["target_id"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE),
        ForeignKey(entity = ConversationEntry::class, parentColumns = ["id"], childColumns = ["turn_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index(value = ["source_id"]), Index(value = ["target_id"]), Index(value = ["turn_id"]),
        Index(value = ["source_id", "predicate", "target_id", "evidence", "turn_id"], unique = true),
    ],
)
internal data class RelationRow(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    val predicate: String,
    @ColumnInfo(name = "target_id") val targetId: String,
    val evidence: String,
    @ColumnInfo(name = "turn_id") val turnId: String,
    val confidence: Double,
    val confirmed: Boolean,
)

data class KnowledgeRelation(
    val id: String,
    val source: KnowledgeEntity,
    val predicate: String,
    val target: KnowledgeEntity,
    val evidence: String,
    val sourceTurnId: String,
    val confidence: Double,
    val confirmed: Boolean,
)

data class KnowledgeCandidate(
    val sourceName: String, val sourceType: String, val predicate: String,
    val targetName: String, val targetType: String, val evidence: String,
    val confidence: Double, val explicit: Boolean,
)

data class KnowledgeGraph(
    val entities: List<KnowledgeEntity> = emptyList(),
    val relations: List<KnowledgeRelation> = emptyList(),
    val positions: Map<String, StoredNodePosition> = emptyMap(),
)

internal data class HistorySnapshot(
    val turnId: String,
    val entries: List<ConversationEntry>,
    val graph: KnowledgeGraph,
)

@Dao
internal interface KnowledgeDao {
    @Query("SELECT * FROM turns ORDER BY created_at, rowid") fun turns(): List<ConversationEntry>
    @Query("SELECT * FROM entities ORDER BY type, name") fun entities(): List<KnowledgeEntity>
    @Query("SELECT * FROM relations ORDER BY rowid") fun relations(): List<RelationRow>
    @Query("SELECT * FROM node_positions") fun positions(): List<StoredNodePosition>
    @Query("SELECT * FROM node_positions WHERE entity_id = :entityId") fun position(entityId: String): StoredNodePosition?
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertTurn(turn: ConversationEntry)
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun insertEntity(entity: KnowledgeEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun insertRelation(relation: RelationRow)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun savePosition(position: StoredNodePosition)
    @Query("DELETE FROM turns") fun deleteTurns()
    @Query("DELETE FROM entities") fun deleteEntities()
    @Query("UPDATE OR IGNORE relations SET source_id = :newId WHERE source_id = :oldId") fun moveSources(oldId: String, newId: String)
    @Query("UPDATE OR IGNORE relations SET target_id = :newId WHERE target_id = :oldId") fun moveTargets(oldId: String, newId: String)
    @Query("DELETE FROM entities WHERE id = :id") fun deleteEntity(id: String)
    @Query("UPDATE entities SET name = :name, type = :type WHERE id = :id") fun updateEntityLabel(id: String, name: String, type: String)
    @Query("UPDATE OR IGNORE relations SET predicate = :predicate, target_id = :targetId, confirmed = 1 WHERE id = :id")
    fun updateRelation(id: String, predicate: String, targetId: String)
    @Query("DELETE FROM relations WHERE id = :id") fun deleteRelation(id: String)
    @Query("DELETE FROM entities WHERE id NOT IN (SELECT source_id FROM relations UNION SELECT target_id FROM relations)")
    fun deleteOrphanEntities()
}

@Database(
    entities = [ConversationEntry::class, KnowledgeEntity::class, RelationRow::class, StoredNodePosition::class],
    version = 3,
    exportSchema = false,
)
internal abstract class AgentKnowledgeDatabase : RoomDatabase() {
    abstract fun knowledgeDao(): KnowledgeDao

    companion object {
        fun open(context: Context): AgentKnowledgeDatabase = Room.databaseBuilder(
            context.applicationContext,
            AgentKnowledgeDatabase::class.java,
            "agent_knowledge.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE turns_new (id TEXT NOT NULL, created_at INTEGER NOT NULL, prompt TEXT NOT NULL, response_title TEXT NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE TABLE entities_new (id TEXT NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE TEMP TABLE relations_backup AS SELECT id, source_id, predicate, target_id, evidence, turn_id, confidence, confirmed FROM relations")
                db.execSQL("INSERT INTO turns_new SELECT id, created_at, prompt, response_title FROM turns")
                db.execSQL("INSERT INTO entities_new SELECT id, name, type FROM entities")
                db.execSQL("DROP TABLE relations")
                db.execSQL("DROP TABLE entities")
                db.execSQL("DROP TABLE turns")
                db.execSQL("ALTER TABLE turns_new RENAME TO turns")
                db.execSQL("ALTER TABLE entities_new RENAME TO entities")
                db.execSQL("CREATE TABLE relations (id TEXT NOT NULL, source_id TEXT NOT NULL, predicate TEXT NOT NULL, target_id TEXT NOT NULL, evidence TEXT NOT NULL, turn_id TEXT NOT NULL, confidence REAL NOT NULL, confirmed INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(source_id) REFERENCES entities(id) ON UPDATE CASCADE ON DELETE CASCADE, FOREIGN KEY(target_id) REFERENCES entities(id) ON UPDATE CASCADE ON DELETE CASCADE, FOREIGN KEY(turn_id) REFERENCES turns(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("INSERT INTO relations SELECT id, source_id, predicate, target_id, evidence, turn_id, confidence, confirmed FROM relations_backup")
                db.execSQL("DROP TABLE relations_backup")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_relations_source_id ON relations(source_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_relations_target_id ON relations(target_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_relations_turn_id ON relations(turn_id)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_relations_source_id_predicate_target_id_evidence_turn_id ON relations(source_id, predicate, target_id, evidence, turn_id)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS node_positions (entity_id TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, PRIMARY KEY(entity_id), FOREIGN KEY(entity_id) REFERENCES entities(id) ON UPDATE CASCADE ON DELETE CASCADE)")
            }
        }
    }
}

internal interface ConversationHistory {
    fun load(): List<ConversationEntry>
    fun loadGraph(): KnowledgeGraph
    fun append(prompt: String, responseTitle: String, facts: List<KnowledgeCandidate>): HistorySnapshot
    fun merge(turnId: String, facts: List<KnowledgeCandidate>): KnowledgeGraph
    fun renameEntity(id: String, name: String, type: String): KnowledgeGraph
    fun editRelation(id: String, predicate: String, targetName: String, targetType: String): KnowledgeGraph
    fun removeRelation(id: String): KnowledgeGraph
    fun moveEntity(id: String, x: Float, y: Float): KnowledgeGraph
    fun clear()
    fun close()
}

internal class LocalConversationHistory(private val context: Context) : ConversationHistory {
    private val database = AgentKnowledgeDatabase.open(context)
    private val dao = database.knowledgeDao()

    init { migrateLegacyHistory() }

    override fun load() = dao.turns()

    override fun loadGraph(): KnowledgeGraph {
        val entities = dao.entities()
        val byId = entities.associateBy(KnowledgeEntity::id)
        return KnowledgeGraph(entities, dao.relations().mapNotNull { row ->
            KnowledgeRelation(row.id, byId[row.sourceId] ?: return@mapNotNull null,
                row.predicate, byId[row.targetId] ?: return@mapNotNull null,
                row.evidence, row.turnId, row.confidence, row.confirmed)
        }, dao.positions().associateBy(StoredNodePosition::entityId))
    }

    override fun append(prompt: String, responseTitle: String, facts: List<KnowledgeCandidate>): HistorySnapshot {
        val turnId = UUID.randomUUID().toString()
        database.runInTransaction {
            dao.insertTurn(ConversationEntry(turnId, System.currentTimeMillis(), prompt.take(8_000), responseTitle.take(200)))
            insertFacts(turnId, facts)
        }
        return HistorySnapshot(turnId, load(), loadGraph())
    }

    override fun merge(turnId: String, facts: List<KnowledgeCandidate>): KnowledgeGraph {
        database.runInTransaction { insertFacts(turnId, facts) }
        return loadGraph()
    }

    override fun renameEntity(id: String, name: String, type: String): KnowledgeGraph {
        val replacement = entity(name, type)
        database.runInTransaction {
            if (replacement.id == id) {
                dao.updateEntityLabel(id, replacement.name, replacement.type)
            } else {
                val oldPosition = dao.position(id)
                dao.insertEntity(replacement)
                dao.moveSources(id, replacement.id)
                dao.moveTargets(id, replacement.id)
                oldPosition?.let { dao.savePosition(it.copy(entityId = replacement.id)) }
                dao.deleteEntity(id)
            }
        }
        return loadGraph()
    }

    override fun editRelation(id: String, predicate: String, targetName: String, targetType: String): KnowledgeGraph {
        val target = entity(targetName, targetType)
        database.runInTransaction {
            dao.insertEntity(target)
            dao.updateRelation(id, predicate.validPredicate(), target.id)
            dao.deleteOrphanEntities()
        }
        return loadGraph()
    }

    override fun removeRelation(id: String): KnowledgeGraph {
        database.runInTransaction { dao.deleteRelation(id); dao.deleteOrphanEntities() }
        return loadGraph()
    }

    override fun moveEntity(id: String, x: Float, y: Float): KnowledgeGraph {
        require(x.isFinite() && y.isFinite())
        dao.savePosition(StoredNodePosition(id, x.coerceIn(-100_000f, 100_000f), y.coerceIn(-100_000f, 100_000f)))
        return loadGraph()
    }

    override fun clear() = database.runInTransaction { dao.deleteTurns(); dao.deleteEntities() }

    override fun close() = database.close()

    private fun insertFacts(turnId: String, facts: List<KnowledgeCandidate>) {
        facts.take(50).forEach { raw ->
            val fact = raw.validated() ?: return@forEach
            val source = entity(fact.sourceName, fact.sourceType)
            val target = entity(fact.targetName, fact.targetType)
            dao.insertEntity(source); dao.insertEntity(target)
            dao.insertRelation(RelationRow(UUID.randomUUID().toString(), source.id, fact.predicate,
                target.id, fact.evidence, turnId, fact.confidence, fact.explicit))
        }
    }

    private fun migrateLegacyHistory() {
        if (load().isNotEmpty()) return
        val preferences = context.getSharedPreferences("conversation_history", Context.MODE_PRIVATE)
        val entries = ConversationHistoryCodec.decode(preferences.getString("entries", null))
        if (entries.isEmpty()) return
        database.runInTransaction { entries.forEach { dao.insertTurn(it) } }
        preferences.edit().remove("entries").apply()
    }
}

internal object ConversationHistoryCodec {
    fun encode(entries: List<ConversationEntry>): String = JSONArray().apply {
        entries.forEach { put(JSONObject().put("id", it.id).put("createdAtMillis", it.createdAtMillis)
            .put("prompt", it.prompt).put("responseTitle", it.responseTitle)) }
    }.toString()

    fun decode(raw: String?): List<ConversationEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList { for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").takeIf(String::isNotBlank) ?: continue
                val prompt = item.optString("prompt").takeIf(String::isNotBlank) ?: continue
                val title = item.optString("responseTitle").takeIf(String::isNotBlank) ?: continue
                add(ConversationEntry(id.take(100), item.optLong("createdAtMillis").coerceAtLeast(0), prompt.take(8_000), title.take(200)))
            } }
        } catch (_: Exception) { emptyList() }
    }
}

internal object EmptyConversationHistory : ConversationHistory {
    override fun load() = emptyList<ConversationEntry>()
    override fun loadGraph() = KnowledgeGraph()
    override fun append(prompt: String, responseTitle: String, facts: List<KnowledgeCandidate>) = HistorySnapshot("", emptyList(), KnowledgeGraph())
    override fun merge(turnId: String, facts: List<KnowledgeCandidate>) = KnowledgeGraph()
    override fun renameEntity(id: String, name: String, type: String) = KnowledgeGraph()
    override fun editRelation(id: String, predicate: String, targetName: String, targetType: String) = KnowledgeGraph()
    override fun removeRelation(id: String) = KnowledgeGraph()
    override fun moveEntity(id: String, x: Float, y: Float) = KnowledgeGraph()
    override fun clear() = Unit
    override fun close() = Unit
}

internal val KNOWLEDGE_ENTITY_TYPES = setOf("PERSON", "ORGANIZATION", "PLACE", "PROJECT", "PREFERENCE", "FACT")

private fun KnowledgeCandidate.validated(): KnowledgeCandidate? {
    val normalizedPredicate = predicate.trim().lowercase().take(50)
    if (!normalizedPredicate.matches(Regex("[\\p{L}\\p{N}_-]{1,50}"))) return null
    val normalized = copy(sourceName = sourceName.trim().take(100), sourceType = sourceType.trim().uppercase().take(30),
        predicate = normalizedPredicate, targetName = targetName.trim().take(100),
        targetType = targetType.trim().uppercase().take(30), evidence = evidence.trim().take(300),
        confidence = if (confidence.isFinite()) confidence.coerceIn(0.0, 1.0) else 0.0)
    return normalized.takeIf { it.sourceName.isNotEmpty() && it.sourceType in KNOWLEDGE_ENTITY_TYPES &&
        it.targetName.isNotEmpty() && it.targetType in KNOWLEDGE_ENTITY_TYPES && it.evidence.isNotEmpty() }
}

private fun String.validPredicate(): String = trim().lowercase().take(50).also {
    require(it.matches(Regex("[\\p{L}\\p{N}_-]{1,50}"))) { "Invalid relation" }
}

private fun entity(name: String, type: String): KnowledgeEntity {
    val normalizedName = name.trim().take(100).also { require(it.isNotEmpty()) }
    val normalizedType = type.trim().uppercase().take(30).also { require(it in KNOWLEDGE_ENTITY_TYPES) }
    val digest = MessageDigest.getInstance("SHA-256").digest("$normalizedType\u0000${normalizedName.lowercase()}".toByteArray())
        .take(16).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    return KnowledgeEntity(digest, normalizedName, normalizedType)
}
