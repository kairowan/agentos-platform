package com.agentos.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun AgentHomeScreen(
    state: AgentUiState,
    onPromptChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onSuggestion: (String) -> Unit,
    onToggleModelSettings: () -> Unit,
    onRemoteModelEnabled: (Boolean) -> Unit,
    onModelEndpointChanged: (String) -> Unit,
    onModelNameChanged: (String) -> Unit,
    onModelApiKeyChanged: (String) -> Unit,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onVoiceSettings: () -> Unit,
    onHistory: () -> Unit,
    onOpenMedia: (MediaWorkspaceMode) -> Unit,
    onOpenApps: () -> Unit,
    onNotificationAccess: () -> Unit,
) {
    AgentBackdrop {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp, bottom = 150.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { HomeHeader(state.useRemoteModel) }
            item { VoiceHero(state, onVoiceSettings) }
            item {
                QuickActions(
                    onCamera = { onOpenMedia(MediaWorkspaceMode.CAMERA) },
                    onGallery = { onOpenMedia(MediaWorkspaceMode.GALLERY) },
                    onApps = onOpenApps,
                    onHistory = onHistory,
                )
            }
            item {
                ModelSettings(state, onToggleModelSettings, onRemoteModelEnabled,
                    onModelEndpointChanged, onModelNameChanged, onModelApiKeyChanged)
            }
            state.notice?.let { item { NoticeCard(it) } }
            state.approval?.let { item { ApprovalCard(it, onApprove, onDeny) } }
            item { GeneratedScreenView(state.screen, onSuggestion) }
            item { NotificationInbox(state, onNotificationAccess) }
        }
        CommandComposer(
            prompt = state.prompt,
            working = state.isWorking,
            enabled = state.approval == null,
            onPromptChanged = onPromptChanged,
            onSubmit = onSubmit,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun HomeHeader(remote: Boolean) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Column {
            Text("AgentOS", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("你的系统，只保留意图与能力", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AgentPill(if (remote) "云端模型" else "本地模式", if (remote) AgentBlue else AgentMint)
    }
}

@Composable
private fun VoiceHero(state: AgentUiState, onSettings: () -> Unit) {
    AgentPanel(Modifier.fillMaxWidth(), accent = if (state.isWorking) AgentBlue else AgentMint) {
        Row(Modifier.fillMaxWidth().padding(20.dp), Arrangement.spacedBy(18.dp), Alignment.CenterVertically) {
            Box(
                Modifier.size(72.dp).background(
                    if (state.isWorking) AgentBlue.copy(alpha = 0.2f) else AgentMint.copy(alpha = 0.16f),
                    CircleShape,
                ),
                contentAlignment = Alignment.Center,
            ) {
                if (state.isWorking) CircularProgressIndicator(Modifier.size(36.dp), strokeWidth = 3.dp)
                else Text("A", color = AgentMint, style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AgentPill(if (state.isWorking) "正在思考" else "随时可唤醒")
                Text(if (state.isWorking) "我正在把目标拆成安全的系统动作" else "说“Hey AgentOS”开始",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(state.voiceStatus, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("设置", color = AgentBlue, modifier = Modifier.clickable(onClick = onSettings).padding(8.dp))
        }
    }
}

@Composable
private fun QuickActions(
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onApps: () -> Unit,
    onHistory: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("常用能力", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
            QuickAction("相机", "拍摄与录像", "CAM", AgentMint, onCamera, Modifier.weight(1f))
            QuickAction("图库", "照片与录音", "LIB", AgentBlue, onGallery, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
            QuickAction("应用", "调用已安装服务", "APP", AgentAmber, onApps, Modifier.weight(1f))
            QuickAction("记忆", "历史与知识图", "MEM", Color(0xFFC79BFF), onHistory, Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuickAction(
    title: String,
    subtitle: String,
    mark: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    AgentPanel(modifier.clickable(onClick = onClick), accent) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            AgentPill(mark, accent)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CommandComposer(
    prompt: String,
    working: Boolean,
    enabled: Boolean,
    onPromptChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier.navigationBarsPadding().imePadding().padding(12.dp),
        color = Color(0xF2112026),
        shape = RoundedCornerShape(26.dp),
        tonalElevation = 12.dp,
        shadowElevation = 18.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), Arrangement.spacedBy(10.dp), Alignment.CenterVertically) {
            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChanged,
                modifier = Modifier.weight(1f),
                placeholder = { Text("告诉我你想完成什么…") },
                minLines = 1,
                maxLines = 3,
                enabled = enabled && !working,
                shape = RoundedCornerShape(20.dp),
            )
            Button(
                onClick = onSubmit,
                enabled = prompt.isNotBlank() && enabled && !working,
                shape = CircleShape,
                modifier = Modifier.size(54.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) { Text(if (working) "…" else "↑", style = MaterialTheme.typography.titleLarge) }
        }
    }
}
