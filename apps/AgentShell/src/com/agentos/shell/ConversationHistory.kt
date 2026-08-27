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
    @ColumnInfo(name = "response_body", defaultValue = "''") val responseBody: String = "",
    @ColumnInfo(name = "task_id") val taskId: String? = null,
    @ColumnInfo(name = "task_state", defaultValue = "'LEGACY'") val taskState: String = TaskState.LEGACY.name,
    @ColumnInfo(name = "memory_excluded", defaultValue = "0") val memoryExcluded: Boolean = false,
)

@Entity(tableName = "tasks")
data class TaskRecord(
    @PrimaryKey val id: String,
    val session: String,
    val prompt: String,
    val state: String,
    val capability: String,
    val detail: String,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
)

@Entity(tableName = "entities")
data class KnowledgeEntity(@PrimaryKey val id: String, val name: String, val type: String)

@Entity(tableName = "suppressed_relations")
internal data class SuppressedRelation(@PrimaryKey val key: String)

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
    @ColumnInfo(name = "user_corrected", defaultValue = "0") val userCorrected: Boolean = false,
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
    val userCorrected: Boolean = false,
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
    @Query("SELECT * FROM tasks ORDER BY created_at, rowid") fun tasks(): List<TaskRecord>
    @Query("SELECT * FROM tasks WHERE id = :id") fun task(id: String): TaskRecord?
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertTask(task: TaskRecord)
    @Query("UPDATE tasks SET state = :state, capability = :capability, detail = :detail, updated_at = :now WHERE id = :id")
    fun updateTask(id: String, state: String, capability: String, detail: String, now: Long)
    @Query("DELETE FROM tasks") fun deleteTasks()
    @Query("UPDATE turns SET memory_excluded = 1 WHERE id IN (SELECT turn_id FROM relations WHERE source_id = :source AND predicate = :predicate AND target_id = :target)")
    fun excludeRelationSources(source: String, predicate: String, target: String)
    @Query("UPDATE turns SET memory_excluded = 1 WHERE id = :id") fun excludeMemory(id: String)
    @Query("UPDATE relations SET user_corrected = 1 WHERE source_id = :id OR target_id = :id") fun markEntityCorrected(id: String)
    @Query("SELECT * FROM turns WHERE id = :id") fun turn(id: String): ConversationEntry?
    @Query("SELECT * FROM relations WHERE id = :id") fun relation(id: String): RelationRow?
    @Query("SELECT EXISTS(SELECT 1 FROM suppressed_relations WHERE `key` = :key)") fun isSuppressed(key: String): Boolean
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun suppress(value: SuppressedRelation)
    @Query("DELETE FROM suppressed_relations") fun clearSuppressions()
    @Query("DELETE FROM relations WHERE source_id = :source AND predicate = :predicate AND target_id = :target")
    fun deleteMatchingRelations(source: String, predicate: String, target: String)
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
    @Query("UPDATE relations SET predicate = :predicate, target_id = :targetId, confirmed = 1, confidence = 1, user_corrected = 1 WHERE id = :id")
    fun updateRelation(id: String, predicate: String, targetId: String)
    @Query("DELETE FROM relations WHERE id != :id AND source_id = :source AND predicate = :predicate AND target_id = :target AND evidence = :evidence AND turn_id = :turn")
    fun deleteEditDuplicate(id: String, source: String, predicate: String, target: String, evidence: String, turn: String)
    @Query("DELETE FROM relations WHERE id = :id") fun deleteRelation(id: String)
    @Query("DELETE FROM entities WHERE id NOT IN (SELECT source_id FROM relations UNION SELECT target_id FROM relations)")
    fun deleteOrphanEntities()
}

@Database(
    entities = [ConversationEntry::class, TaskRecord::class, KnowledgeEntity::class, RelationRow::class, StoredNodePosition::class, SuppressedRelation::class],
    version = 5,
    exportSchema = false,
)
internal abstract class AgentKnowledgeDatabase : RoomDatabase() {
    abstract fun knowledgeDao(): KnowledgeDao

    companion object {
        fun open(context: Context): AgentKnowledgeDatabase = Room.databaseBuilder(
            context.applicationContext,
            AgentKnowledgeDatabase::class.java,
            "agent_knowledge.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE turns ADD COLUMN response_body TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE turns ADD COLUMN task_id TEXT")
                db.execSQL("ALTER TABLE turns ADD COLUMN task_state TEXT NOT NULL DEFAULT 'LEGACY'")
                db.execSQL("ALTER TABLE turns ADD COLUMN memory_excluded INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE relations ADD COLUMN user_corrected INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE tasks (id TEXT NOT NULL PRIMARY KEY, session TEXT NOT NULL, prompt TEXT NOT NULL, state TEXT NOT NULL, capability TEXT NOT NULL, detail TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
                // v4 tombstones lack source IDs: do not export old raw turns that
                // might restore something the user already corrected or forgot.
                db.execSQL("UPDATE turns SET memory_excluded = 1 WHERE EXISTS (SELECT 1 FROM suppressed_relations)")
            }
        }

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS suppressed_relations (`key` TEXT NOT NULL, PRIMARY KEY(`key`))")
            }
        }

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
    fun loadTasks(): List<TaskRecord>
    fun beginTask(prompt: String, id: String = UUID.randomUUID().toString()): TaskRecord
    fun task(id: String): TaskRecord?
    fun setTaskState(id: String, state: TaskState, capability: String = "", detail: String = ""): Boolean
    fun recordResult(id: String, prompt: String, title: String, body: String, state: TaskState, facts: List<KnowledgeCandidate>): HistorySnapshot?
    fun interruptTask(id: String): TaskRecord?
    fun recoverTasks(): List<TaskRecord>
    fun merge(turnId: String, facts: List<KnowledgeCandidate>): KnowledgeGraph
    fun renameEntity(id: String, name: String, type: String): KnowledgeGraph
    fun editRelation(id: String, predicate: String, targetName: String, targetType: String): KnowledgeGraph
    fun removeRelation(id: String): KnowledgeGraph
    fun moveEntity(id: String, x: Float, y: Float): KnowledgeGraph
    fun clear()
    fun close()
}

internal class LocalConversationHistory(
    private val context: Context,
    private val database: AgentKnowledgeDatabase = AgentKnowledgeDatabase.open(context),
    migrateLegacy: Boolean = true,
    private val session: String = UUID.randomUUID().toString(),
) : ConversationHistory {
    // Construction happens in the main-thread ViewModel factory; opening/migrating
    // belongs to the first history operation, whose caller already uses Dispatchers.IO.
    private val dao by lazy { database.knowledgeDao().also { if (migrateLegacy) migrateLegacyHistory(it) } }

    override fun load() = dao.turns()
    override fun loadTasks() = dao.tasks()

    override fun task(id: String) = dao.task(id)

    override fun beginTask(prompt: String, id: String): TaskRecord {
        require(prompt.isNotBlank() && prompt.length <= 8_000)
        val now = System.currentTimeMillis()
        return TaskRecord(id, session, prompt, TaskState.PLANNING.name,
            "", "", now, now).also(dao::insertTask)
    }

    override fun setTaskState(id: String, state: TaskState, capability: String, detail: String): Boolean {
        var changed = false
        database.runInTransaction {
            val old = dao.task(id) ?: return@runInTransaction
            if (old.session != session || !TaskState.parse(old.state).canTransitionTo(state)) return@runInTransaction
            dao.updateTask(id, state.name, capability.ifEmpty { old.capability }, detail.take(2_000), System.currentTimeMillis())
            changed = true
        }
        return changed
    }

    override fun recordResult(id: String, prompt: String, title: String, body: String, state: TaskState, facts: List<KnowledgeCandidate>): HistorySnapshot? {
        var turnId: String? = null
        database.runInTransaction {
            if (!setTaskState(id, state, detail = title)) return@runInTransaction
            val value = ConversationEntry(UUID.randomUUID().toString(), System.currentTimeMillis(),
                prompt.take(8_000), title, body, id, state.name)
            dao.insertTurn(value)
            insertFacts(value.id, facts)
            turnId = value.id
        }
        return turnId?.let { HistorySnapshot(it, load(), loadGraph()) }
    }

    override fun interruptTask(id: String): TaskRecord? {
        database.runInTransaction {
            val old = dao.task(id) ?: return@runInTransaction
            if (old.session == session) interrupt(old)
        }
        return dao.task(id)
    }

    private fun interrupt(old: TaskRecord) {
        val previous = TaskState.parse(old.state)
        if (!previous.active) return
        val next = previous.interrupted()
        dao.updateTask(old.id, next.name, old.capability,
            if (next == TaskState.UNKNOWN) "执行期间中断，请核对实际结果；不会自动重试" else "未完成的请求已作废，需要重新发起并确认",
            System.currentTimeMillis())
    }

    override fun recoverTasks(): List<TaskRecord> {
        database.runInTransaction { dao.tasks().filter { it.session != session }.forEach(::interrupt) }
        return loadTasks() // Never resume a task or restore an approval token.
    }

    override fun loadGraph(): KnowledgeGraph {
        val entities = dao.entities()
        val byId = entities.associateBy(KnowledgeEntity::id)
        return KnowledgeGraph(entities, dao.relations().mapNotNull { row ->
            KnowledgeRelation(row.id, byId[row.sourceId] ?: return@mapNotNull null,
                row.predicate, byId[row.targetId] ?: return@mapNotNull null,
                row.evidence, row.turnId, row.confidence, row.confirmed, row.userCorrected)
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
        database.runInTransaction { insertFacts(turnId, facts.map { it.copy(explicit = false) }) }
        return loadGraph()
    }

    override fun renameEntity(id: String, name: String, type: String): KnowledgeGraph {
        val replacement = entity(name, type)
        database.runInTransaction {
            if (replacement.id == id) {
                dao.updateEntityLabel(id, replacement.name, replacement.type)
            } else {
                dao.relations().filter { it.sourceId == id || it.targetId == id }.forEach(::suppress)
                dao.markEntityCorrected(id)
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
        val validPredicate = predicate.validPredicate()
        database.runInTransaction {
            val old = dao.relation(id) ?: return@runInTransaction
            // Keep the original evidence locally, but prevent old raw conversation
            // from being reintroduced to a model after a user correction.
            dao.excludeRelationSources(old.sourceId, old.predicate, old.targetId)
            dao.insertEntity(target)
            dao.deleteEditDuplicate(id, old.sourceId, validPredicate, target.id, old.evidence, old.turnId)
            dao.updateRelation(id, validPredicate, target.id)
            if (old.predicate != validPredicate || old.targetId != target.id) {
                suppress(old)
                dao.deleteMatchingRelations(old.sourceId, old.predicate, old.targetId)
            }
            dao.deleteOrphanEntities()
        }
        return loadGraph()
    }

    override fun removeRelation(id: String): KnowledgeGraph {
        database.runInTransaction {
            val old = dao.relation(id) ?: return@runInTransaction
            suppress(old)
            dao.deleteMatchingRelations(old.sourceId, old.predicate, old.targetId)
            dao.deleteOrphanEntities()
        }
        return loadGraph()
    }

    override fun moveEntity(id: String, x: Float, y: Float): KnowledgeGraph {
        require(x.isFinite() && y.isFinite())
        dao.savePosition(StoredNodePosition(id, x.coerceIn(-100_000f, 100_000f), y.coerceIn(-100_000f, 100_000f)))
        return loadGraph()
    }

    override fun clear() = database.runInTransaction {
        dao.deleteTurns(); dao.deleteEntities(); dao.clearSuppressions(); dao.deleteTasks()
    }

    override fun close() = database.close()

    private fun insertFacts(turnId: String, facts: List<KnowledgeCandidate>) {
        val turn = dao.turn(turnId) ?: return // A late extraction cannot resurrect a deleted turn.
        facts.take(50).forEach { raw ->
            val fact = raw.validated() ?: return@forEach
            if (!turn.prompt.contains(fact.evidence)) return@forEach
            val source = entity(fact.sourceName, fact.sourceType)
            val target = entity(fact.targetName, fact.targetType)
            if (dao.isSuppressed(relationKey(source.id, fact.predicate, target.id))) {
                dao.excludeMemory(turnId)
                return@forEach
            }
            dao.insertEntity(source); dao.insertEntity(target)
            dao.insertRelation(RelationRow(UUID.randomUUID().toString(), source.id, fact.predicate,
                target.id, fact.evidence, turnId, fact.confidence, fact.explicit))
        }
    }

    private fun suppress(row: RelationRow) {
        dao.excludeRelationSources(row.sourceId, row.predicate, row.targetId)
        dao.suppress(SuppressedRelation(relationKey(row.sourceId, row.predicate, row.targetId)))
    }

    // ponytail: Exact semantic triples are suppressed across turns. Paraphrase-equivalent
    // facts need an explicit review/alias model; do not claim semantic deduplication here.
    private fun relationKey(source: String, predicate: String, target: String) = "$source\u0000$predicate\u0000$target"

    private fun migrateLegacyHistory(legacyDao: KnowledgeDao) {
        if (legacyDao.turns().isNotEmpty()) return
        val preferences = context.getSharedPreferences("conversation_history", Context.MODE_PRIVATE)
        val entries = ConversationHistoryCodec.decode(preferences.getString("entries", null))
        if (entries.isEmpty()) return
        database.runInTransaction { entries.forEach { legacyDao.insertTurn(it) } }
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
    override fun loadTasks() = emptyList<TaskRecord>()
    override fun beginTask(prompt: String, id: String) = TaskRecord(id, "", prompt, TaskState.PLANNING.name, "", "", 0, 0)
    override fun task(id: String): TaskRecord? = null
    override fun setTaskState(id: String, state: TaskState, capability: String, detail: String) = true
    override fun recordResult(id: String, prompt: String, title: String, body: String, state: TaskState, facts: List<KnowledgeCandidate>) = append(prompt, title, facts)
    override fun interruptTask(id: String): TaskRecord? = null
    override fun recoverTasks() = loadTasks()
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
