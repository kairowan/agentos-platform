package com.agentos.shell

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.agentos.capability.api.AgentNotificationEvent
import com.agentos.capability.core.ApprovalRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val notifications: List<AgentNotificationEvent> = emptyList(),
    val history: List<ConversationEntry> = emptyList(),
    val showHistory: Boolean = false,
) {
    override fun toString(): String =
        "AgentUiState(promptLength=${prompt.length}, screen=${screen.title}, " +
            "approval=${approval != null}, isWorking=$isWorking, useRemoteModel=$useRemoteModel, " +
            "modelEndpoint=$modelEndpoint, modelName=$modelName, modelApiKey=<redacted>)"
}
class AgentShellViewModel internal constructor(
    private val gateway: CapabilityGateway,
    private val historyStore: ConversationHistory = EmptyConversationHistory,
) : ViewModel() {
    private val runtime = AgentRuntime(gateway)
    private val mutableUiState = MutableStateFlow(AgentUiState(history = historyStore.load()))
    val uiState: StateFlow<AgentUiState> = mutableUiState.asStateFlow()
    private var activeTurn: Job? = null

    init {
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

    fun clearHistory() {
        historyStore.clear()
        mutableUiState.update { it.copy(history = emptyList()) }
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

    fun consumeVoiceReply() {
        mutableUiState.update { it.copy(voiceReply = null) }
    }

    fun interruptVoiceTurn() {
        activeTurn?.cancel()
        activeTurn = null
        mutableUiState.update {
            it.copy(isWorking = false, voiceReply = null, voiceStatus = "已打断，正在聆听新指令")
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
                val history = historyStore.append(prompt.trim(), turn.screen.title)
                mutableUiState.update {
                    it.copy(
                        screen = turn.screen,
                        approval = turn.approval,
                        notice = turn.notice,
                        isWorking = false,
                        voiceReply = if (fromVoice) turn.toSpeechText() else null,
                        history = history,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                val history = historyStore.append(prompt.trim(), "任务未执行")
                mutableUiState.update {
                    it.copy(
                        screen = GeneratedScreen(
                            "任务未执行",
                            listOf(UiBlock.Paragraph("配置无效或系统内部发生错误。")),
                        ),
                        notice = "未向任何系统能力发出请求。",
                        isWorking = false,
                        voiceReply = if (fromVoice) "任务未执行。系统内部发生错误。" else null,
                        history = history,
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
