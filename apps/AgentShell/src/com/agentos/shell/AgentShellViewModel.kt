package com.agentos.shell

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.agentos.capability.api.AgentNotificationEvent
import com.agentos.capability.core.ApprovalRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

data class AgentUiState(
    val prompt: String = "",
    val screen: GeneratedScreen = GeneratedScreen.welcome(),
    val approval: ApprovalRequest? = null,
    val notice: String? = null,
    val isWorking: Boolean = false,
    val showModelSettings: Boolean = false,
    val useRemoteModel: Boolean = false,
    val shareMemoryWithModel: Boolean = false,
    val modelEndpoint: String = "",
    val modelName: String = "",
    val modelApiKey: String = "",
    val voiceStatus: String = "组件预览：使用键盘输入",
    val voiceReply: String? = null,
    val isSpeaking: Boolean = false,
    val notifications: List<AgentNotificationEvent> = emptyList(),
    val history: List<ConversationEntry> = emptyList(),
    val tasks: List<TaskRecord> = emptyList(),
    val knowledgeGraph: KnowledgeGraph = KnowledgeGraph(),
    val showHistory: Boolean = false,
    val avatar: AgentAvatar = AgentAvatar(),
    val showAvatarStudio: Boolean = false,
    val generatedAvatarDraft: AgentAvatar? = null,
    val avatarStyleWorking: Boolean = false,
    val avatarStyleError: String? = null,
    val performance: AvatarPerformance = AvatarPerformance(),
) {
    override fun toString(): String =
        "AgentUiState(promptLength=${prompt.length}, screen=${screen.title}, " +
            "approval=${approval != null}, isWorking=$isWorking, useRemoteModel=$useRemoteModel, " +
            "modelEndpoint=$modelEndpoint, modelName=$modelName, modelApiKey=<redacted>)"
}
class AgentShellViewModel internal constructor(
    private val gateway: CapabilityGateway,
    private val historyStore: ConversationHistory = EmptyConversationHistory,
    private val avatarStore: AgentAvatarStore = EmptyAgentAvatarStore,
) : ViewModel() {
    private val runtime = AgentRuntime(gateway)
    private val mutableUiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = mutableUiState.asStateFlow()
    private var activeTurn: Job? = null
    private var activeTaskId: String? = null
    private var cancellation: Job? = null
    private var performanceReset: Job? = null
    private val historyMutex = Mutex()
    private val startup: Job
    private var storageReady = false

    init {
        mutableUiState.update { it.copy(avatar = avatarStore.load()) }
        startup = viewModelScope.launch {
            try {
                val (history, graph, tasks) = withContext(Dispatchers.IO) {
                    historyMutex.withLock {
                        val tasks = historyStore.recoverTasks()
                        Triple(historyStore.load(), historyStore.loadGraph(), tasks)
                    }
                }
                storageReady = true
                mutableUiState.update { it.copy(history = history, knowledgeGraph = graph, tasks = tasks,
                    notice = if (tasks.any { task -> task.state == TaskState.UNKNOWN.name })
                        "有中断任务的结果未知，请到记忆页面核对；不会自动重试。" else it.notice) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutableUiState.update { it.copy(notice = "本地记录无法打开，已停止执行新任务，避免丢失结果。") }
            }
        }
        viewModelScope.launch {
            gateway.notificationEvents.collect { event ->
                mutableUiState.update {
                    it.copy(notifications = (listOf(event) + it.notifications).take(MAX_NOTIFICATIONS))
                }
            }
        }
    }

    fun updatePrompt(value: String) {
        mutableUiState.update { it.copy(prompt = value.take(8_000)) }
    }

    fun showLocalNotice(message: String) {
        mutableUiState.update { it.copy(notice = message.take(300), isWorking = false) }
    }

    fun toggleModelSettings() {
        mutableUiState.update { it.copy(showModelSettings = !it.showModelSettings) }
    }

    fun toggleHistory() {
        mutableUiState.update { it.copy(showHistory = !it.showHistory) }
    }

    fun openAvatarStudio() {
        mutableUiState.update {
            it.copy(showAvatarStudio = true, generatedAvatarDraft = null, avatarStyleError = null)
        }
    }

    fun closeAvatarStudio() {
        mutableUiState.update {
            it.copy(showAvatarStudio = false, generatedAvatarDraft = null,
                avatarStyleWorking = false, avatarStyleError = null)
        }
    }

    fun saveAvatar(avatar: AgentAvatar) {
        val value = avatar.normalized()
        avatarStore.save(value)
        mutableUiState.update {
            it.copy(avatar = value, showAvatarStudio = false, generatedAvatarDraft = null,
                avatarStyleWorking = false, avatarStyleError = null)
        }
    }

    fun generateAvatarStyle(prompt: String, current: AgentAvatar) {
        if (prompt.isBlank() || prompt.length > 1_000) {
            mutableUiState.update { it.copy(avatarStyleError = "请输入 1–1000 字的角色风格描述") }
            return
        }
        val config = try {
            currentModelConfig()
        } catch (_: IllegalArgumentException) {
            null
        }
        if (config == null) {
            mutableUiState.update { it.copy(avatarStyleError = "请先在主页配置并启用大模型") }
            return
        }
        viewModelScope.launch {
            mutableUiState.update { it.copy(avatarStyleWorking = true, avatarStyleError = null) }
            try {
                val generated = AvatarStyleGenerator(config).generate(prompt, current)
                mutableUiState.update {
                    it.copy(generatedAvatarDraft = generated, avatarStyleWorking = false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableUiState.update {
                    it.copy(avatarStyleWorking = false,
                        avatarStyleError = "模型返回的角色配置无效或网络不可用")
                }
            }
        }
    }

    fun clearHistory() {
        val cleanup = cancelCurrentTurn()
        viewModelScope.launch {
            cleanup.join()
            try {
                startup.join()
                withContext(Dispatchers.IO) { historyMutex.withLock { historyStore.clear() } }
                mutableUiState.update { it.copy(history = emptyList(), tasks = emptyList(), knowledgeGraph = KnowledgeGraph()) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableUiState.update { it.copy(notice = "清理失败，记录未确认删除；请检查本地存储。") } }
        }
    }

    fun renameKnowledgeEntity(id: String, name: String, type: String) {
        updateKnowledgeGraph { historyStore.renameEntity(id, name, type) }
    }

    fun editKnowledgeRelation(id: String, predicate: String, targetName: String, targetType: String) {
        updateKnowledgeGraph { historyStore.editRelation(id, predicate, targetName, targetType) }
    }

    fun removeKnowledgeRelation(id: String) {
        updateKnowledgeGraph { historyStore.removeRelation(id) }
    }

    fun moveKnowledgeEntity(id: String, x: Float, y: Float) {
        updateKnowledgeGraph { historyStore.moveEntity(id, x, y) }
    }

    private fun updateKnowledgeGraph(change: () -> KnowledgeGraph) {
        viewModelScope.launch {
            val graph = try {
                withContext(Dispatchers.IO) { historyMutex.withLock { change() } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutableUiState.update { it.copy(notice = "知识修改未保存，请检查内容和本地存储。") }
                return@launch
            }
            val history = withContext(Dispatchers.IO) { historyMutex.withLock { historyStore.load() } }
            mutableUiState.update { it.copy(knowledgeGraph = graph, history = history) }
        }
    }

    fun setRemoteModelEnabled(enabled: Boolean) {
        mutableUiState.update { it.copy(useRemoteModel = enabled) }
    }

    fun setMemorySharing(enabled: Boolean) {
        if (!enabled) cancelCurrentTurn()
        mutableUiState.update { it.copy(shareMemoryWithModel = enabled,
            notice = if (enabled) "允许向当前端点发送最多 6 条近期对话摘录和 12 条已确认记忆；本次会话有效。"
                else "已关闭后续记忆共享；之前已发送的内容无法撤回。") }
    }

    fun updateModelEndpoint(value: String) {
        mutableUiState.update { it.copy(modelEndpoint = value.take(2_000), shareMemoryWithModel = false) }
    }

    fun updateModelName(value: String) {
        mutableUiState.update { it.copy(modelName = value.take(200)) }
    }

    fun updateModelApiKey(value: String) {
        mutableUiState.update { it.copy(modelApiKey = value.take(4_096)) }
    }

    fun submit() {
        submit(mutableUiState.value.prompt)
    }

    fun submit(prompt: String) = submit(prompt, fromVoice = false)

    fun submitVoice(prompt: String) {
        mutableUiState.update { it.copy(voiceStatus = "已识别：${prompt.take(80)}") }
        submit(prompt, fromVoice = true)
    }

    fun markVoiceOutputStarted() {
        mutableUiState.update { it.copy(voiceReply = null, isSpeaking = true) }
    }

    fun finishVoiceOutput() {
        mutableUiState.update { it.copy(isSpeaking = false) }
    }

    fun interruptVoiceTurn() {
        cancelCurrentTurn()
        performanceReset?.cancel()
        mutableUiState.update {
            it.copy(isWorking = false, approval = null, voiceReply = null, isSpeaking = false,
                voiceStatus = "已打断，等待新指令")
        }
    }

    private fun submit(prompt: String, fromVoice: Boolean) {
        if (prompt.isBlank() || prompt.length > 8_000) return
        val cleanup = cancelCurrentTurn()
        val taskId = UUID.randomUUID().toString()
        activeTaskId = taskId
        activeTurn = viewModelScope.launch {
            cleanup.join()
            startup.join()
            if (!storageReady) return@launch
            mutableUiState.update {
                it.copy(prompt = "", approval = null, notice = null, isWorking = true)
            }
            try {
                val sourceText = prompt.trim()
                val memory = withContext(Dispatchers.IO) {
                    historyMutex.withLock {
                        historyStore.beginTask(sourceText, taskId)
                        MemoryRecall.select(sourceText, historyStore.load(), historyStore.loadGraph())
                    }
                }
                val config = currentModelConfig()
                val turn = runtime.handle(sourceText, config, memory) { id ->
                    val recorded = withContext(Dispatchers.IO) {
                        historyMutex.withLock { historyStore.setTaskState(taskId, TaskState.EXECUTING, id.value) }
                    }
                    if (!recorded) throw CancellationException("Task no longer authorized to dispatch")
                }
                val snapshot = withContext(Dispatchers.IO) {
                    historyMutex.withLock {
                        historyStore.recordResult(taskId, sourceText, turn.screen.title, turn.historyText(),
                            turn.taskState, LocalKnowledgeExtractor.extract(sourceText))
                    }
                } ?: return@launch
                val tasks = withContext(Dispatchers.IO) { historyMutex.withLock { historyStore.loadTasks() } }
                mutableUiState.update {
                    it.copy(
                        screen = turn.screen,
                        approval = turn.approval,
                        notice = turn.notice,
                        isWorking = false,
                        voiceReply = if (fromVoice) turn.toSpeechText() else null,
                        history = snapshot.entries,
                        tasks = tasks,
                        knowledgeGraph = snapshot.graph,
                        performance = turn.performance,
                    )
                }
                schedulePerformanceReset(turn.performance)
                if (config != null) {
                    try {
                        val inferred = ModelKnowledgeExtractor(config).extract(sourceText)
                        val graph = withContext(Dispatchers.IO) {
                            historyMutex.withLock { historyStore.merge(snapshot.turnId, inferred) }
                        }
                        mutableUiState.update { it.copy(knowledgeGraph = graph) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Knowledge extraction is supplementary; task completion survives failure.
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                recordFailure(taskId, prompt.trim())
            }
        }
    }

    fun approve() {
        finishApproval(accepted = true)
    }

    fun deny() {
        finishApproval(accepted = false)
    }

    private fun finishApproval(accepted: Boolean) {
        val request = mutableUiState.value.approval ?: return
        val taskId = activeTaskId ?: return
        val previous = activeTurn
        previous?.cancel()
        mutableUiState.update { it.copy(approval = null, isWorking = true) }
        activeTurn = viewModelScope.launch {
            previous?.join()
            val prompt = "${if (accepted) "确认" else "取消"}：${request.title}"
            try {
                if (accepted) {
                    val recorded = withContext(Dispatchers.IO) {
                        historyMutex.withLock { historyStore.setTaskState(taskId, TaskState.EXECUTING, request.capability.value) }
                    }
                    if (!recorded) throw CancellationException("Approval task is no longer pending")
                }
                val turn = if (accepted) runtime.approve(request.token) else runtime.deny(request.token)
                val snapshot = withContext(Dispatchers.IO) {
                    historyMutex.withLock { historyStore.recordResult(taskId, prompt, turn.screen.title,
                        turn.historyText(), turn.taskState, emptyList()) }
                } ?: return@launch
                val tasks = withContext(Dispatchers.IO) { historyMutex.withLock { historyStore.loadTasks() } }
                mutableUiState.update {
                    it.copy(screen = turn.screen, approval = null, notice = turn.notice, isWorking = false,
                        performance = turn.performance, history = snapshot.entries, tasks = tasks, knowledgeGraph = snapshot.graph)
                }
                schedulePerformanceReset(turn.performance)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { recordFailure(taskId, prompt) }
        }
    }

    private suspend fun recordFailure(taskId: String, prompt: String) {
        try {
            val (snapshot, tasks) = withContext(Dispatchers.IO) {
                historyMutex.withLock {
                    val executing = historyStore.task(taskId)?.state == TaskState.EXECUTING.name
                    val state = if (executing) TaskState.UNKNOWN else TaskState.FAILED
                    val snapshot = historyStore.recordResult(taskId, prompt, state.label,
                        "请求中断或服务不可用，请核对实际结果；不会自动重试。", state, emptyList())
                    snapshot to historyStore.loadTasks()
                }
            }
            mutableUiState.update {
                it.copy(isWorking = false, approval = null, tasks = tasks,
                    history = snapshot?.entries ?: it.history, knowledgeGraph = snapshot?.graph ?: it.knowledgeGraph,
                    notice = "任务未正常完成，请核对任务记录；不会自动重试。")
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            storageReady = false
            mutableUiState.update { it.copy(isWorking = false, approval = null,
                notice = "无法保存结果，已停止执行新任务。请核对系统实际状态，不要重复提交。") }
        }
    }

    private fun cancelCurrentTurn(): Job {
        val previous = activeTurn
        val taskId = activeTaskId
        val priorCleanup = cancellation
        previous?.cancel()
        activeTurn = null
        activeTaskId = null
        mutableUiState.update { it.copy(approval = null, isWorking = false, voiceReply = null, isSpeaking = false) }
        return viewModelScope.launch {
            priorCleanup?.join()
            previous?.join()
            gateway.cancelPending()
            if (taskId != null) {
                try {
                    val tasks = withContext(Dispatchers.IO) {
                        historyMutex.withLock { historyStore.interruptTask(taskId); historyStore.loadTasks() }
                    }
                    mutableUiState.update { it.copy(tasks = tasks, notice = if (tasks.any { task ->
                        task.id == taskId && task.state == TaskState.UNKNOWN.name
                    }) "任务执行期间被打断，结果未知；请核对实际状态，不会自动重试。" else it.notice) }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    storageReady = false
                    mutableUiState.update { it.copy(notice = "取消状态无法保存，已停止执行新任务。") }
                }
            }
        }.also { cancellation = it }
    }

    private fun currentModelConfig(): ModelConfig? {
        val state = mutableUiState.value
        if (!state.useRemoteModel) return null
        return ModelConfig(state.modelEndpoint.trim(), state.modelName.trim(), state.modelApiKey, state.shareMemoryWithModel)
    }

    private fun schedulePerformanceReset(value: AvatarPerformance) {
        performanceReset?.cancel()
        if (value.gesture == AvatarGesture.IDLE) return
        performanceReset = viewModelScope.launch {
            delay(4_500)
            mutableUiState.update { state ->
                if (state.performance == value) state.copy(
                    performance = value.copy(gesture = AvatarGesture.IDLE, intensity = 0.35f),
                ) else state
            }
        }
    }

    override fun onCleared() {
        performanceReset?.cancel()
        historyStore.close()
        gateway.close()
    }

    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == AgentShellViewModel::class.java)
                return AgentShellViewModel(
                    AgentCapabilityClient(context),
                    LocalConversationHistory(context),
                    LocalAgentAvatarStore(context),
                ) as T
            }
        }

        private const val MAX_NOTIFICATIONS = 5
    }
}

private fun AgentTurn.toSpeechText(): String = buildString {
    append(screen.title)
    screen.blocks.take(4).forEach { block ->
        when (block) {
            is UiBlock.Fact -> append("。${block.label}，${block.value}")
            is UiBlock.Paragraph -> append("。${block.text}")
            is UiBlock.Action -> Unit
        }
    }
}.take(500)
