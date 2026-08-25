package com.agentos.shell

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
) {
    override fun toString(): String =
        "AgentUiState(promptLength=${prompt.length}, screen=${screen.title}, " +
            "approval=${approval != null}, isWorking=$isWorking, useRemoteModel=$useRemoteModel, " +
            "modelEndpoint=$modelEndpoint, modelName=$modelName, modelApiKey=<redacted>)"
}
class AgentShellViewModel internal constructor(
    private val runtime: AgentRuntime,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = mutableUiState.asStateFlow()
    private var activeTurn: Job? = null

    fun updatePrompt(value: String) {
        mutableUiState.update { it.copy(prompt = value.take(8_000)) }
    }

    fun toggleModelSettings() {
        mutableUiState.update { it.copy(showModelSettings = !it.showModelSettings) }
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

    fun submit(prompt: String) {
        if (prompt.isBlank()) return
        activeTurn?.cancel()
        activeTurn = viewModelScope.launch {
            mutableUiState.update {
                it.copy(prompt = "", approval = null, notice = null, isWorking = true)
            }
            try {
                val config = currentModelConfig()
                val turn = runtime.handle(prompt.trim(), config)
                mutableUiState.update {
                    it.copy(
                        screen = turn.screen,
                        approval = turn.approval,
                        notice = turn.notice,
                        isWorking = false,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableUiState.update {
                    it.copy(
                        screen = GeneratedScreen(
                            "任务未执行",
                            listOf(UiBlock.Paragraph("配置无效或系统内部发生错误。")),
                        ),
                        notice = "未向任何系统能力发出请求。",
                        isWorking = false,
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

    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == AgentShellViewModel::class.java)
                return AgentShellViewModel(AgentRuntime(AgentCapabilityClient(context))) as T
            }
        }
    }
}
