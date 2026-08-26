package com.agentos.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agentos.capability.api.AppDescriptor
import com.agentos.capability.api.SemanticNode

@Composable
internal fun AppWorkspace(
    state: AppWorkspaceState,
    onClose: () -> Unit,
    onShowApps: () -> Unit,
    onRefreshApps: () -> Unit,
    onRefreshSemantics: () -> Unit,
    onLaunch: (AppDescriptor) -> Unit,
    onClickNode: (SemanticNode) -> Unit,
    onScrollNode: (SemanticNode, Boolean) -> Unit,
    onSetText: (SemanticNode, String) -> Unit,
    onAccessibilitySettings: () -> Unit,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("返回") }
            Text("应用能力桥", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = if (state.page == AppWorkspacePage.APPS) onRefreshApps else onRefreshSemantics) {
                Text("刷新")
            }
        }
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
            Button(onClick = onShowApps, modifier = Modifier.weight(1f)) { Text("应用") }
            Button(onClick = onRefreshSemantics, modifier = Modifier.weight(1f)) { Text("当前页面") }
        }
        if (state.page == AppWorkspacePage.SEMANTICS) {
            TextButton(onClick = onAccessibilitySettings) { Text("配置应用语义桥") }
        }
        if (state.loading) CircularProgressIndicator()
        if (state.message.isNotBlank()) Text(state.message, color = MaterialTheme.colorScheme.primary)
        if (state.page == AppWorkspacePage.APPS) {
            AppList(state.apps, onLaunch)
        } else {
            SemanticList(state.activePackage, state.activeTitle, state.nodes, onClickNode, onScrollNode, onSetText)
        }
    }
    if (state.approvalToken != null) {
        AlertDialog(
            onDismissRequest = onDeny,
            title = { Text("需要确认") },
            text = { Text(state.approvalMessage) },
            confirmButton = { TextButton(onClick = onApprove) { Text("确认") } },
            dismissButton = { TextButton(onClick = onDeny) { Text("取消") } },
        )
    }
}

@Composable
private fun AppList(apps: List<AppDescriptor>, onLaunch: (AppDescriptor) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(apps, key = AppDescriptor::packageName) { app ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(14.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(app.label, fontWeight = FontWeight.SemiBold)
                        Text("${app.category} · ${app.packageName}", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { onLaunch(app) }) { Text("打开") }
                }
            }
        }
    }
}

@Composable
private fun SemanticList(
    packageName: String,
    title: String,
    nodes: List<SemanticNode>,
    onClick: (SemanticNode) -> Unit,
    onScroll: (SemanticNode, Boolean) -> Unit,
    onSetText: (SemanticNode, String) -> Unit,
) {
    var editingNode by remember { mutableStateOf<SemanticNode?>(null) }
    var input by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(title.ifBlank { "当前页面" }, fontWeight = FontWeight.Bold)
            Text(packageName.ifBlank { "尚未读取应用页面" }, style = MaterialTheme.typography.bodySmall)
        }
        items(nodes, key = SemanticNode::path) { node ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(node.text.ifBlank { node.className.substringAfterLast('.') })
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (node.clickable) TextButton(onClick = { onClick(node) }) { Text("点击") }
                        if (node.scrollable) {
                            TextButton(onClick = { onScroll(node, false) }) { Text("向前") }
                            TextButton(onClick = { onScroll(node, true) }) { Text("向后") }
                        }
                        if (node.editable) TextButton(onClick = { editingNode = node }) { Text("输入") }
                    }
                }
            }
        }
    }
    editingNode?.let { node ->
        AlertDialog(
            onDismissRequest = { editingNode = null },
            title = { Text("输入内容") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(500) },
                    label = { Text(node.text.ifBlank { "当前输入框" }) },
                )
            },
            confirmButton = { TextButton(onClick = {
                onSetText(node, input); input = ""; editingNode = null
            }, enabled = input.isNotBlank()) { Text("继续确认") } },
            dismissButton = { TextButton(onClick = { editingNode = null }) { Text("取消") } },
        )
    }
}
