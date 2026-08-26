package com.agentos.shell

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.agentos.capability.api.AppBridgeContract
import com.agentos.capability.api.AppBridgeReply
import com.agentos.capability.api.AppDescriptor
import com.agentos.capability.api.SemanticNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class AppWorkspacePage { APPS, SEMANTICS }

internal data class AppWorkspaceState(
    val open: Boolean = false,
    val page: AppWorkspacePage = AppWorkspacePage.APPS,
    val loading: Boolean = false,
    val apps: List<AppDescriptor> = emptyList(),
    val activePackage: String = "",
    val activeTitle: String = "",
    val nodes: List<SemanticNode> = emptyList(),
    val message: String = "",
    val approvalToken: String? = null,
    val approvalMessage: String = "",
)

internal class AppWorkspaceViewModel(private val client: AgentAppBridgeClient) : ViewModel() {
    private val mutableState = MutableStateFlow(AppWorkspaceState())
    val state: StateFlow<AppWorkspaceState> = mutableState.asStateFlow()

    fun open() {
        mutableState.update { it.copy(open = true, page = AppWorkspacePage.APPS) }
        loadApps()
    }

    fun close() { mutableState.value = AppWorkspaceState() }

    fun showApps() { mutableState.update { it.copy(page = AppWorkspacePage.APPS) } }

    fun loadApps() {
        mutableState.update { it.copy(loading = true) }
        viewModelScope.launch {
            val result = runCatching { client.apps() }
            mutableState.update { state -> state.copy(
                apps = result.getOrDefault(state.apps), loading = false,
                message = result.exceptionOrNull()?.message ?: "已发现 ${result.getOrDefault(state.apps).size} 个应用能力提供者",
            ) }
        }
    }

    fun requestLaunch(app: AppDescriptor) = bridgeCall { client.requestLaunch(app.packageName) }

    fun refreshSemantics() {
        mutableState.update { it.copy(loading = true, page = AppWorkspacePage.SEMANTICS) }
        viewModelScope.launch {
            val result = runCatching { client.snapshot() }
            mutableState.update { state ->
                val snapshot = result.getOrNull()
                state.copy(
                    loading = false,
                    activePackage = snapshot?.packageName.orEmpty(),
                    activeTitle = snapshot?.title.orEmpty(),
                    nodes = snapshot?.nodes.orEmpty(),
                    message = snapshot?.message ?: result.exceptionOrNull()?.message.orEmpty(),
                )
            }
        }
    }

    fun click(node: SemanticNode) {
        val packageName = mutableState.value.activePackage
        if (packageName.isNotBlank()) bridgeCall {
            client.requestAction(packageName, node.path, AppBridgeContract.ACTION_CLICK)
        }
    }

    fun scroll(node: SemanticNode, forward: Boolean) {
        val packageName = mutableState.value.activePackage
        if (packageName.isNotBlank()) bridgeCall {
            client.requestAction(packageName, node.path, if (forward) {
                AppBridgeContract.ACTION_SCROLL_FORWARD
            } else AppBridgeContract.ACTION_SCROLL_BACKWARD)
        }
    }

    fun setText(node: SemanticNode, value: String) {
        val packageName = mutableState.value.activePackage
        if (packageName.isNotBlank() && value.isNotBlank()) bridgeCall {
            client.requestAction(packageName, node.path, AppBridgeContract.ACTION_SET_TEXT, value.take(500))
        }
    }

    fun approve() {
        val token = mutableState.value.approvalToken ?: return
        bridgeCall { client.approve(token) }
    }

    fun deny() {
        val token = mutableState.value.approvalToken ?: return
        bridgeCall { client.deny(token) }
    }

    private fun bridgeCall(block: suspend () -> AppBridgeReply) {
        viewModelScope.launch {
            val result = runCatching { block() }
            mutableState.update { state ->
                val reply = result.getOrNull()
                state.copy(
                    message = reply?.message ?: result.exceptionOrNull()?.message.orEmpty(),
                    approvalToken = reply?.token?.takeIf { reply.status == AppBridgeContract.STATUS_APPROVAL_REQUIRED },
                    approvalMessage = reply?.message.takeIf { reply?.status == AppBridgeContract.STATUS_APPROVAL_REQUIRED }.orEmpty(),
                )
            }
        }
    }

    override fun onCleared() { client.close() }

    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == AppWorkspaceViewModel::class.java)
                return AppWorkspaceViewModel(AgentAppBridgeClient(context)) as T
            }
        }
    }
}
