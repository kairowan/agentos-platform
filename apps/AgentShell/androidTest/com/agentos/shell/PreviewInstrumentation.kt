package com.agentos.shell

import android.app.Activity
import android.app.Instrumentation
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import androidx.room.Room
import java.util.UUID

/** No extra test framework. Every check uses an isolated database, never user history. */
class PreviewInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        val result = Bundle()
        try {
            checkMainThreadConstruction()
            checkHistory()
            checkTasks()
            checkMigration(3)
            checkMigration(4)
            result.putString("stream", "PASS: Room full history, task recovery/no replay, late result rejection, memory corrections, migration 3→5 and 4→5\n")
            finish(Activity.RESULT_OK, result)
        } catch (error: Throwable) {
            result.putString("stream", "FAIL: ${error.stackTraceToString()}\n")
            finish(Activity.RESULT_CANCELED, result)
        }
    }

    private fun checkMainThreadConstruction() {
        val database = Room.inMemoryDatabaseBuilder(context, AgentKnowledgeDatabase::class.java).build()
        lateinit var store: LocalConversationHistory
        runOnMainSync { store = LocalConversationHistory(context, database) }
        try { check(store.load().isEmpty()) } finally { store.close() }
    }

    private fun checkHistory() {
        val database = Room.inMemoryDatabaseBuilder(targetContext, AgentKnowledgeDatabase::class.java).build()
        val store = LocalConversationHistory(targetContext, database, migrateLegacy = false)
        try {
            val prompt = "我喜欢咖啡"
            val facts = LocalKnowledgeExtractor.extract(prompt)
            val first = store.append(prompt, "记录", facts)
            check(first.graph.relations.size == 1)
            store.removeRelation(first.graph.relations.single().id)
            check(store.load().single().memoryExcluded)
            check(store.merge(first.turnId, facts).relations.isEmpty())
            check(store.append(prompt, "再次提取", facts).graph.relations.isEmpty())
            store.clear()
            val edited = store.append(prompt, "记录", facts)
            store.append(prompt, "重复来源", facts)
            store.editRelation(edited.graph.relations.single().id, "likes", "茶", "PREFERENCE")
            check(store.load().all { it.memoryExcluded })
            val recalled = MemoryRecall.select("我的偏好", store.load(), store.loadGraph())
            check(recalled.recentTurns.isEmpty())
            check(recalled.facts.single().userCorrected)
            check(!recalled.asUntrustedJson().contains("咖啡"))
            check(store.merge(edited.turnId, facts).relations.single().target.name == "茶")
            store.clear()
            val collision = store.append(prompt, "重复目标", facts + facts.map { it.copy(targetName = "茶") })
            val coffee = collision.graph.relations.single { it.target.name == "咖啡" }
            val merged = store.editRelation(coffee.id, "likes", "茶", "PREFERENCE")
            check(merged.relations.single().id == coffee.id && merged.relations.single().confirmed)
            store.clear()
            check(store.merge(edited.turnId, facts).relations.isEmpty())
            check(store.load().isEmpty())
            val candidate = store.append(prompt, "候选", emptyList())
            check(!store.merge(candidate.turnId, facts).relations.single().confirmed)
            store.clear()
            check(store.append(prompt, "伪造来源", facts.map { it.copy(evidence = "不存在的原文") }).graph.relations.isEmpty())
        } finally { store.close() }
    }

    private fun checkTasks() {
        val name = "preview-tasks-${UUID.randomUUID()}.db"
        fun open() = LocalConversationHistory(targetContext,
            Room.databaseBuilder(targetContext, AgentKnowledgeDatabase::class.java, name).build(), migrateLegacy = false)
        try {
            val first = open()
            val planning: String
            val waiting: String
            val executing: String
            val finished: String
            try {
                planning = first.beginTask("还在理解").id
                waiting = first.beginTask("等待确认").id
                check(first.setTaskState(waiting, TaskState.EXECUTING))
                check(first.recordResult(waiting, "等待确认", "确认", "确认正文", TaskState.WAITING_CONFIRMATION, emptyList()) != null)
                executing = first.beginTask("执行中").id
                check(first.setTaskState(executing, TaskState.EXECUTING, "system.time.read"))
                finished = first.beginTask("完成").id
                check(first.recordResult(finished, "完成", "结果", "完整回复\n第 30 段", TaskState.SUCCEEDED, emptyList()) != null)
                val cancelled = first.beginTask("取消").id
                first.interruptTask(cancelled)
                check(first.recordResult(cancelled, "迟到", "迟到", "不能写入", TaskState.SUCCEEDED, emptyList()) == null)
                check(first.recoverTasks().single { it.id == planning }.state == TaskState.PLANNING.name)
            } finally { first.close() }
            val second = open()
            try {
                val recovered = second.recoverTasks().associateBy { it.id }
                check(recovered.getValue(planning).state == TaskState.CANCELLED.name)
                check(recovered.getValue(waiting).state == TaskState.CANCELLED.name)
                check(recovered.getValue(executing).state == TaskState.UNKNOWN.name)
                check(recovered.getValue(finished).state == TaskState.SUCCEEDED.name)
                check(second.load().last().responseBody == "完整回复\n第 30 段")
                check(second.recoverTasks() == recovered.values.toList())
                check(!second.setTaskState(executing, TaskState.EXECUTING))
                val newTask = second.beginTask("清空期间").id
                second.clear()
                check(second.recordResult(newTask, "迟到", "迟到", "不能复活", TaskState.SUCCEEDED, emptyList()) == null)
                check(second.load().isEmpty() && second.loadTasks().isEmpty())
            } finally { second.close() }
        } finally { targetContext.deleteDatabase(name) }
    }

    private fun checkMigration(version: Int) {
        val name = "preview-migration-${UUID.randomUUID()}.db"
        try {
            // Genuine legacy schema, not a current DB with its version number changed.
            SQLiteDatabase.openOrCreateDatabase(targetContext.getDatabasePath(name), null).use {
                it.execSQL("CREATE TABLE turns (id TEXT NOT NULL PRIMARY KEY, created_at INTEGER NOT NULL, prompt TEXT NOT NULL, response_title TEXT NOT NULL)")
                it.execSQL("CREATE TABLE entities (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL)")
                it.execSQL("CREATE TABLE relations (id TEXT NOT NULL PRIMARY KEY, source_id TEXT NOT NULL, predicate TEXT NOT NULL, target_id TEXT NOT NULL, evidence TEXT NOT NULL, turn_id TEXT NOT NULL, confidence REAL NOT NULL, confirmed INTEGER NOT NULL, FOREIGN KEY(source_id) REFERENCES entities(id) ON UPDATE CASCADE ON DELETE CASCADE, FOREIGN KEY(target_id) REFERENCES entities(id) ON UPDATE CASCADE ON DELETE CASCADE, FOREIGN KEY(turn_id) REFERENCES turns(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                listOf("source_id", "target_id", "turn_id").forEach { column ->
                    it.execSQL("CREATE INDEX index_relations_$column ON relations($column)")
                }
                it.execSQL("CREATE UNIQUE INDEX index_relations_source_id_predicate_target_id_evidence_turn_id ON relations(source_id,predicate,target_id,evidence,turn_id)")
                it.execSQL("CREATE TABLE node_positions (entity_id TEXT NOT NULL PRIMARY KEY, x REAL NOT NULL, y REAL NOT NULL, FOREIGN KEY(entity_id) REFERENCES entities(id) ON UPDATE CASCADE ON DELETE CASCADE)")
                it.execSQL("INSERT INTO turns VALUES ('keep',1,'保留历史','原始记录')")
                it.execSQL("INSERT INTO entities VALUES ('me','我','PERSON'),('tea','茶','PREFERENCE')")
                it.execSQL("INSERT INTO relations VALUES ('fact','me','likes','tea','保留历史','keep',1,1)")
                if (version == 4) {
                    it.execSQL("CREATE TABLE suppressed_relations (`key` TEXT NOT NULL PRIMARY KEY)")
                    it.execSQL("INSERT INTO suppressed_relations VALUES ('old-correction')")
                }
                it.version = version
            }
            val after = Room.databaseBuilder(targetContext, AgentKnowledgeDatabase::class.java, name)
                .addMigrations(AgentKnowledgeDatabase.MIGRATION_3_4, AgentKnowledgeDatabase.MIGRATION_4_5).build()
            try {
                val turn = after.knowledgeDao().turns().single()
                check(turn.id == "keep" && turn.responseBody.isEmpty() && turn.taskState == "LEGACY")
                check(turn.memoryExcluded == (version == 4))
                check(!after.knowledgeDao().relations().single().userCorrected)
                check(after.knowledgeDao().tasks().isEmpty())
                after.knowledgeDao().suppress(SuppressedRelation("test"))
                check(after.knowledgeDao().isSuppressed("test"))
            } finally { after.close() }
        } finally { targetContext.deleteDatabase(name) }
    }
}
