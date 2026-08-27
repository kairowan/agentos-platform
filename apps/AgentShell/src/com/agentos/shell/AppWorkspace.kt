package com.agentos.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    onCancelPending: () -> Unit,
) {
    AgentBackdrop {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AgentTopBar(
                title = "应用能力",
                subtitle = if (state.page == AppWorkspacePage.APPS) "让已安装应用成为 Agent 的服务层" else "最近的可访问页面语义",
                onBack = onClose,
                actionLabel = "刷新",
                onAction = if (state.page == AppWorkspacePage.APPS) onRefreshApps else onRefreshSemantics,
            )
            WorkspaceTabs(state.page, onShowApps, onRefreshSemantics)
            if (state.loading) CircularProgressIndicator()
            if (state.message.isNotBlank()) AgentPill(state.message.take(80), AgentBlue)
            TextButton(onClick = onCancelPending) { Text("停止待执行操作") }
            if (state.page == AppWorkspacePage.APPS) AppList(state.apps, onLaunch)
            else SemanticList(state.activePackage, state.activeTitle, state.nodes,
                onClickNode, onScrollNode, onSetText, onAccessibilitySettings)
        }
    }
    if (state.approvalToken != null) {
        AlertDialog(
            onDismissRequest = onDeny,
            title = { Text("仅允许这一次？") },
            text = { Text(state.approvalMessage) },
            confirmButton = { Button(onClick = onApprove) { Text("允许一次") } },
            dismissButton = { TextButton(onClick = onDeny) { Text("取消") } },
        )
    }
}

@Composable
private fun WorkspaceTabs(page: AppWorkspacePage, onApps: () -> Unit, onSemantics: () -> Unit) {
    Surface(color = AgentSurfaceHigh, shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(4.dp)) {
            Tab("能力提供者", page == AppWorkspacePage.APPS, onApps, Modifier.weight(1f))
            Tab("页面语义", page == AppWorkspacePage.SEMANTICS, onSemantics, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Tab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        modifier.clickable(onClick = onClick),
        color = if (selected) AgentMint.copy(alpha = 0.16f) else Color.Transparent,
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(label, color = if (selected) AgentMint else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(vertical = 11.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun AppList(apps: List<AppDescriptor>, onLaunch: (AppDescriptor) -> Unit) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("全部") }
    val categories = remember(apps) { listOf("全部") + apps.map(AppDescriptor::category).distinct() }
    val filtered = remember(apps, query, category) {
        apps.filter { app ->
            (category == "全部" || app.category == category) &&
                (query.isBlank() || app.label.contains(query, true) || app.packageName.contains(query, true))
        }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(100) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索应用或包名") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp)) {
            categories.forEach { item ->
                Surface(
                    Modifier.clickable { category = item },
                    color = if (category == item) AgentBlue.copy(alpha = 0.18f) else AgentSurface,
                    shape = RoundedCornerShape(100.dp),
                ) {
                    Text(item, color = if (category == item) AgentBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered, key = AppDescriptor::packageName) { app -> AppProviderCard(app, onLaunch) }
        }
    }
}

@Composable
private fun AppProviderCard(app: AppDescriptor, onLaunch: (AppDescriptor) -> Unit) {
    val accent = categoryAccent(app.category)
    AgentPanel(Modifier.fillMaxWidth(), accent) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(app.packageName, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AgentPill(app.category, accent)
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), Arrangement.spacedBy(6.dp)) {
                app.capabilities.forEach { capability -> AgentPill(capability, accent) }
            }
            Button(onClick = { onLaunch(app) }, modifier = Modifier.fillMaxWidth()) { Text("经确认后打开") }
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
    onAccessibilitySettings: () -> Unit,
) {
    var editingNode by remember { mutableStateOf<SemanticNode?>(null) }
    var input by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            AgentPanel(Modifier.fillMaxWidth(), AgentBlue) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title.ifBlank { "还没有页面快照" }, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Text(packageName.ifBlank { "访问另一个应用后返回，再读取它公开的可访问语义。" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onAccessibilitySettings) { Text("配置语义桥权限") }
                }
            }
        }
        items(nodes, key = SemanticNode::path) { node ->
            AgentPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(node.text.ifBlank { node.className.substringAfterLast('.') }, fontWeight = FontWeight.SemiBold)
                    Text(node.path.ifBlank { "root" }, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (node.clickable) TextButton(onClick = { onClick(node) }) { Text("点击") }
                        if (node.scrollable) {
                            TextButton(onClick = { onScroll(node, false) }) { Text("上一屏") }
                            TextButton(onClick = { onScroll(node, true) }) { Text("下一屏") }
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
            text = { OutlinedTextField(input, { input = it.take(500) },
                label = { Text(node.text.ifBlank { "当前输入框" }) }) },
            confirmButton = { Button(onClick = {
                onSetText(node, input); input = ""; editingNode = null
            }, enabled = input.isNotBlank()) { Text("进入安全确认") } },
            dismissButton = { TextButton(onClick = { editingNode = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun categoryAccent(category: String) = when (category) {
    "短视频", "视频" -> AgentBlue
    "阅读", "新闻" -> Color(0xFFC79BFF)
    "外卖", "购物" -> AgentAmber
    "社交" -> AgentMint
    "音乐" -> Color(0xFFFF8ED4)
    "出行" -> Color(0xFF73D6FF)
    else -> MaterialTheme.colorScheme.outline
}
