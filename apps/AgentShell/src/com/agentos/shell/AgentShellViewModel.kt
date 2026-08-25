package com.agentos.shell

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AgentUiState(
    val prompt: String = "",
    val screen: GeneratedScreen = GeneratedScreen.welcome(),
)

class AgentShellViewModel internal constructor(
    private val runtime: AgentRuntime,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = mutableUiState.asStateFlow()

    fun updatePrompt(value: String) {
        mutableUiState.update { it.copy(prompt = value) }
    }

    fun submit() {
        submit(mutableUiState.value.prompt)
    }

    fun submit(prompt: String) {
        if (prompt.isBlank()) return
        mutableUiState.value = AgentUiState(screen = runtime.handle(prompt.trim()))
    }

    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == AgentShellViewModel::class.java)
                val capabilities = CapabilityRegistry(
                    listOf(TimeCapability(), DeviceCapability(), StorageCapability(context)),
                )
                return AgentShellViewModel(LocalAgentRuntime(capabilities)) as T
            }
        }
    }
}
