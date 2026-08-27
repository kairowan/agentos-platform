package com.agentos.shell

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.agentos.capability.api.AppBridgeContract
import com.agentos.capability.api.AppBridgeReply
import com.agentos.capability.api.AppDescriptor
import com.agentos.capability.api.SemanticNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

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
    private var operation: Job? = null
    private var cleanup: Job? = null
    private var generation = 0L

    fun open() {
        mutableState.update { it.copy(open = true, page = AppWorkspacePage.APPS) }
        loadApps()
    }

    fun close() { cancelPending(); mutableState.value = AppWorkspaceState() }

    fun cancelPending(): Job {
        val previous = operation
        val priorCleanup = cleanup
        previous?.cancel()
        operation = null
        val current = ++generation
        mutableState.update { it.copy(approvalToken = null, approvalMessage = "", loading = false) }
        return viewModelScope.launch {
            priorCleanup?.join()
            previous?.join()
            try {
                client.cancelPending()
                if (generation == current) mutableState.update { it.copy(message = "待执行操作已取消；已发生的操作不能撤回") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (generation == current) mutableState.update { it.copy(message = "取消未获服务确认，请核对目标应用；不会自动重试") }
            }
        }.also { cleanup = it }
    }

    fun showApps() { mutableState.update { it.copy(page = AppWorkspacePage.APPS) } }

    fun loadApps() {
        val current = generation
        mutableState.update { it.copy(loading = true) }
        viewModelScope.launch {
            val result = runCatching { client.apps() }
            coroutineContext.ensureActive()
            if (generation != current) return@launch
            mutableState.update { state -> state.copy(
                apps = result.getOrDefault(state.apps), loading = false,
                message = result.exceptionOrNull()?.message ?: "已发现 ${result.getOrDefault(state.apps).size} 个应用能力提供者",
            ) }
        }
    }

    fun requestLaunch(app: AppDescriptor) = bridgeCall { client.requestLaunch(app.packageName) }

    fun refreshSemantics() {
        val current = generation
        mutableState.update { it.copy(loading = true, page = AppWorkspacePage.SEMANTICS) }
        viewModelScope.launch {
            val result = runCatching { client.snapshot() }
            coroutineContext.ensureActive()
            if (generation != current) return@launch
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
        if (!mutableState.value.loading) bridgeCall(replace = false) { client.approve(token) }
    }

    fun deny() {
        val token = mutableState.value.approvalToken ?: return
        if (!mutableState.value.loading) bridgeCall(replace = false) { client.deny(token) }
    }

    private fun bridgeCall(replace: Boolean = true, block: suspend () -> AppBridgeReply) {
        val waitFor = if (replace) cancelPending() else cleanup
        val current = ++generation
        mutableState.update { it.copy(loading = true, approvalToken = null, approvalMessage = "") }
        operation = viewModelScope.launch {
            waitFor?.join()
            val result = runCatching { block() }
            coroutineContext.ensureActive()
            if (generation != current) return@launch
            mutableState.update { state ->
                val reply = result.getOrNull()
                state.copy(
                    loading = false,
                    message = reply?.message ?: "服务结果未知，请核对目标应用；不会自动重试",
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
