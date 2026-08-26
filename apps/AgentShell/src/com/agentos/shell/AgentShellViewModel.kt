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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class AgentUiState(
    val prompt: String = "",
    val screen: GeneratedScreen = GeneratedScreen.welcome(),
    val approval: ApprovalRequest? = null,
    val notice: String? = null,
    val isWorking: Boolean = false,
    val showModelSettings: Boolean = false,
    val useRemoteModel: Boolean = false,
    val modelEndpoint: String = "",
    val modelName: String = "",
    val modelApiKey: String = "",
    val voiceStatus: String = "等待“Hey AgentOS”唤醒",
    val voiceReply: String? = null,
    val isSpeaking: Boolean = false,
    val notifications: List<AgentNotificationEvent> = emptyList(),
    val history: List<ConversationEntry> = emptyList(),
    val knowledgeGraph: KnowledgeGraph = KnowledgeGraph(),
    val showHistory: Boolean = false,
    val avatar: AgentAvatar = AgentAvatar(),
    val showAvatarStudio: Boolean = false,
    val generatedAvatarDraft: AgentAvatar? = null,
    val avatarStyleWorking: Boolean = false,
    val avatarStyleError: String? = null,
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
    private val historyMutex = Mutex()

    init {
        mutableUiState.update { it.copy(avatar = avatarStore.load()) }
        viewModelScope.launch {
            val (history, graph) = withContext(Dispatchers.IO) {
                historyMutex.withLock { historyStore.load() to historyStore.loadGraph() }
            }
            mutableUiState.update { it.copy(history = history, knowledgeGraph = graph) }
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
        viewModelScope.launch {
            withContext(Dispatchers.IO) { historyMutex.withLock { historyStore.clear() } }
            mutableUiState.update { it.copy(history = emptyList(), knowledgeGraph = KnowledgeGraph()) }
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
            } catch (_: IllegalArgumentException) {
                return@launch
            }
            mutableUiState.update { it.copy(knowledgeGraph = graph) }
        }
    }

    fun setRemoteModelEnabled(enabled: Boolean) {
        mutableUiState.update { it.copy(useRemoteModel = enabled) }
    }

    fun updateModelEndpoint(value: String) {
        mutableUiState.update { it.copy(modelEndpoint = value.take(2_000)) }
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
        activeTurn?.cancel()
        activeTurn = null
        mutableUiState.update {
            it.copy(isWorking = false, voiceReply = null, isSpeaking = false,
                voiceStatus = "已打断，正在聆听新指令")
        }
    }

    private fun submit(prompt: String, fromVoice: Boolean) {
        if (prompt.isBlank()) return
        activeTurn?.cancel()
        activeTurn = viewModelScope.launch {
            mutableUiState.update {
                it.copy(prompt = "", approval = null, notice = null, isWorking = true)
            }
            try {
                val config = currentModelConfig()
                val turn = runtime.handle(prompt.trim(), config)
                val sourceText = prompt.trim()
                val snapshot = withContext(Dispatchers.IO) {
                    historyMutex.withLock {
                        historyStore.append(sourceText, turn.screen.title,
                            LocalKnowledgeExtractor.extract(sourceText))
                    }
                }
                mutableUiState.update {
                    it.copy(
                        screen = turn.screen,
                        approval = turn.approval,
                        notice = turn.notice,
                        isWorking = false,
                        voiceReply = if (fromVoice) turn.toSpeechText() else null,
                        history = snapshot.entries,
                        knowledgeGraph = snapshot.graph,
                    )
                }
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
                val snapshot = withContext(Dispatchers.IO) {
                    historyMutex.withLock {
                        historyStore.append(prompt.trim(), "任务未执行",
                            LocalKnowledgeExtractor.extract(prompt.trim()))
                    }
                }
                mutableUiState.update {
                    it.copy(
                        screen = GeneratedScreen(
                            "任务未执行",
                            listOf(UiBlock.Paragraph("配置无效或系统内部发生错误。")),
                        ),
                        notice = "未向任何系统能力发出请求。",
                        isWorking = false,
                        voiceReply = if (fromVoice) "任务未执行。系统内部发生错误。" else null,
                        history = snapshot.entries,
                        knowledgeGraph = snapshot.graph,
                    )
                }
            }
        }
    }

    fun approve() {
        val token = mutableUiState.value.approval?.token ?: return
        activeTurn?.cancel()
        activeTurn = viewModelScope.launch {
            val turn = runtime.approve(token)
            mutableUiState.update {
                it.copy(screen = turn.screen, approval = null, notice = turn.notice)
            }
        }
    }

    fun deny() {
        val token = mutableUiState.value.approval?.token ?: return
        activeTurn?.cancel()
        activeTurn = viewModelScope.launch {
            val turn = runtime.deny(token)
            mutableUiState.update {
                it.copy(screen = turn.screen, approval = null, notice = turn.notice)
            }
        }
    }

    private fun currentModelConfig(): ModelConfig? {
        val state = mutableUiState.value
        if (!state.useRemoteModel) return null
        return ModelConfig(state.modelEndpoint.trim(), state.modelName.trim(), state.modelApiKey)
    }

    override fun onCleared() {
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
