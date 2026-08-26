package com.agentos.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun AgentHomeScreen(
    state: AgentUiState,
    onPromptChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onToggleModelSettings: () -> Unit,
    onRemoteModelEnabled: (Boolean) -> Unit,
    onModelEndpointChanged: (String) -> Unit,
    onModelNameChanged: (String) -> Unit,
    onModelApiKeyChanged: (String) -> Unit,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onVoiceSettings: () -> Unit,
    onHistory: () -> Unit,
    onAvatar: () -> Unit,
) {
    var keyboardOpen by remember { mutableStateOf(false) }
    AgentBackdrop {
        AgentAvatarView(
            avatar = state.avatar,
            expression = state.avatarExpression(),
            performance = state.avatarPerformance(),
            modifier = Modifier.fillMaxSize(),
        )
        StageHeader(
            name = state.avatar.name,
            status = when {
                state.isSpeaking -> "正在和你说话"
                state.isWorking -> "正在思考"
                state.voiceStatus.contains("聆听") -> "正在聆听"
                else -> "随时可唤醒"
            },
            onHistory = onHistory,
            onAvatar = onAvatar,
            onSettings = onToggleModelSettings,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        StageCaption(state, Modifier.align(Alignment.BottomCenter)
            .padding(bottom = if (keyboardOpen) 126.dp else 102.dp))
        state.approval?.let {
            ApprovalCard(it, onApprove, onDeny,
                Modifier.align(Alignment.Center).padding(horizontal = 24.dp))
        }
        if (state.showModelSettings) {
            Box(Modifier.align(Alignment.Center).padding(horizontal = 20.dp)) {
                ModelSettings(
                    state, onToggleModelSettings, onRemoteModelEnabled,
                    onModelEndpointChanged, onModelNameChanged, onModelApiKeyChanged,
                )
            }
        }
        StageInput(
            state = state,
            keyboardOpen = keyboardOpen,
            onKeyboardToggle = { keyboardOpen = !keyboardOpen },
            onPromptChanged = onPromptChanged,
            onSubmit = onSubmit,
            onVoiceSettings = onVoiceSettings,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun StageHeader(
    name: String,
    status: String,
    onHistory: () -> Unit,
    onAvatar: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(color = Color(0x99111F25), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                Text(name, fontWeight = FontWeight.Bold)
                Text(status, style = MaterialTheme.typography.labelSmall, color = AgentMint)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StageButton("忆", "打开记忆", onHistory)
            StageButton("人", "编辑角色", onAvatar)
            StageButton("⋯", "打开设置", onSettings)
        }
    }
}

@Composable
private fun StageButton(mark: String, description: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(42.dp).semantics { contentDescription = description }.clickable(onClick = onClick),
        color = Color(0x99111F25),
        shape = CircleShape,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(mark, color = AgentMint, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StageCaption(state: AgentUiState, modifier: Modifier = Modifier) {
    val message = when {
        state.notice != null -> state.notice
        state.isWorking -> "我正在理解你的目标…"
        state.screen == GeneratedScreen.welcome() -> "说“Hey AgentOS”，我在听"
        else -> state.screen.stageText()
    }
    Surface(modifier.padding(horizontal = 28.dp), color = Color(0xB30B171C), shape = RoundedCornerShape(22.dp)) {
        Text(message, Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun StageInput(
    state: AgentUiState,
    keyboardOpen: Boolean,
    onKeyboardToggle: () -> Unit,
    onPromptChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onVoiceSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier.navigationBarsPadding().imePadding().padding(12.dp),
        color = Color(0xE6112026),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 10.dp,
    ) {
        if (keyboardOpen) {
            Row(Modifier.fillMaxWidth().padding(8.dp), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
                TextButton(onClick = onKeyboardToggle) { Text("语音") }
                OutlinedTextField(
                    value = state.prompt,
                    onValueChange = onPromptChanged,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("文字输入（备用）") },
                    enabled = state.approval == null && !state.isWorking,
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                )
                Button(onClick = onSubmit,
                    enabled = state.prompt.isNotBlank() && state.approval == null && !state.isWorking,
                    shape = CircleShape, modifier = Modifier.size(50.dp)) { Text("↑") }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                TextButton(onClick = onKeyboardToggle) { Text("键盘") }
                Surface(modifier = Modifier.clickable(onClick = onVoiceSettings),
                    color = AgentMint.copy(alpha = 0.16f), shape = RoundedCornerShape(20.dp)) {
                    Text("  ◉  ${state.voiceStatus.take(32)}  ", Modifier.padding(vertical = 10.dp), color = AgentMint)
                }
                TextButton(onClick = onVoiceSettings) { Text("语音") }
            }
        }
    }
}

private fun GeneratedScreen.stageText(): String = buildString {
    append(title)
    blocks.take(3).forEach { block ->
        when (block) {
            is UiBlock.Paragraph -> append("\n${block.text}")
            is UiBlock.Fact -> append("\n${block.label}：${block.value}")
            is UiBlock.Action -> Unit
        }
    }
}.take(360)
