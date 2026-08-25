package com.agentos.shell

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import com.agentos.capability.core.ApprovalRequest
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private var voiceInput: VoiceInputController? = null
    private var voiceOutput: VoiceOutputController? = null
    private val viewModel by lazy {
        ViewModelProvider(this, AgentShellViewModel.factory(applicationContext))[AgentShellViewModel::class.java]
    }
    private val requestMicrophone = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceInput() else viewModel.onVoiceError("需要麦克风权限才能使用语音")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AgentOsTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(state.voiceReply) {
                    state.voiceReply?.let {
                        (voiceOutput ?: VoiceOutputController(applicationContext).also { voiceOutput = it }).speak(it)
                        viewModel.consumeVoiceReply()
                    }
                }
                AgentShellContent(
                    state = state,
                    onPromptChanged = viewModel::updatePrompt,
                    onSubmit = viewModel::submit,
                    onSuggestion = viewModel::submit,
                    onToggleModelSettings = viewModel::toggleModelSettings,
                    onRemoteModelEnabled = viewModel::setRemoteModelEnabled,
                    onModelEndpointChanged = viewModel::updateModelEndpoint,
                    onModelNameChanged = viewModel::updateModelName,
                    onModelApiKeyChanged = viewModel::updateModelApiKey,
                    onApprove = viewModel::approve,
                    onDeny = viewModel::deny,
                    onVoice = ::toggleVoiceInput,
                    onNotificationAccess = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                )
            }
        }
    }

    private fun toggleVoiceInput() {
        if (viewModel.uiState.value.isListening) {
            voiceInput?.stop()
        } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startVoiceInput()
        } else {
            requestMicrophone.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startVoiceInput() {
        val controller = voiceInput ?: VoiceInputController(
            applicationContext,
            onResult = viewModel::submitVoice,
            onListening = viewModel::onVoiceListeningChanged,
            onError = viewModel::onVoiceError,
        ).also { voiceInput = it }
        controller.start()
    }

    override fun onDestroy() {
        voiceInput?.close()
        voiceOutput?.close()
        super.onDestroy()
    }
}

@Composable
internal fun AgentShellContent(
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
    onVoice: () -> Unit,
    onNotificationAccess: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Header(state.useRemoteModel)
            ModelSettings(state, onToggleModelSettings, onRemoteModelEnabled,
                onModelEndpointChanged, onModelNameChanged, onModelApiKeyChanged)
            VoiceCard(state, onVoice)

            state.notice?.let { NoticeCard(it) }
            NotificationInbox(state, onNotificationAccess)
            GeneratedScreenView(state.screen, onSuggestion)
            state.approval?.let { ApprovalCard(it, onApprove, onDeny) }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.prompt,
                    onValueChange = onPromptChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("输入目标") },
                    placeholder = { Text("例如：打开 Wi-Fi 设置") },
                    minLines = 2,
                    enabled = !state.isWorking && state.approval == null,
                )
                Button(
                    onClick = onSubmit,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.prompt.isNotBlank() && !state.isWorking && state.approval == null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    if (state.isWorking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("智能体正在规划")
                    } else {
                        Text("交给智能体")
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceCard(state: AgentUiState, onVoice: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (state.isListening) Color(0xFF17332D) else MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("语音智能体", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(state.voiceStatus, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = onVoice,
                enabled = !state.isWorking && state.approval == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isListening) "停止聆听" else "按住体验 · 点击说话")
            }
        }
    }
}

@Composable
private fun NotificationInbox(state: AgentUiState, onNotificationAccess: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("消息事件", fontWeight = FontWeight.Bold)
                    Text("由 Broker 本地过滤，不会自动上传", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onNotificationAccess) { Text("授权") }
            }
            if (state.notifications.isEmpty()) {
                Text("授权后，社交软件的消息类通知会推送到这里。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                state.notifications.forEach { event ->
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(event.sender, fontWeight = FontWeight.SemiBold)
                            Text(event.text, maxLines = 3)
                            Text(event.packageName, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(remoteModelEnabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "AGENT OS / TRUSTED SHELL",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(100.dp),
            ) {
                Text(
                    if (remoteModelEnabled) "MODEL + BROKER" else "OFFLINE + BROKER",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = "你想让系统完成什么？",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "模型只能提出计划；系统能力必须经过 Broker 策略和可信确认界面。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ModelSettings(
    state: AgentUiState,
    onToggle: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onEndpoint: (String) -> Unit,
    onModel: (String) -> Unit,
    onApiKey: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("模型连接", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (state.useRemoteModel) "已启用兼容模型" else "使用离线规划器",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = onToggle) {
                    Text(if (state.showModelSettings) "收起" else "配置")
                }
            }
            if (state.showModelSettings) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("启用 OpenAI-compatible 端点")
                    Switch(checked = state.useRemoteModel, onCheckedChange = onEnabled)
                }
                OutlinedTextField(
                    value = state.modelEndpoint,
                    onValueChange = onEndpoint,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("HTTPS endpoint") },
                    placeholder = { Text("https://example.com/v1/chat/completions") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(
                    value = state.modelName,
                    onValueChange = onModel,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("模型名称") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.modelApiKey,
                    onValueChange = onApiKey,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Key（仅保存在当前进程内存）") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        }
    }
}

@Composable
private fun NoticeCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF2D2614),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(message, modifier = Modifier.padding(14.dp), color = Color(0xFFFFDFA0))
    }
}

@Composable
private fun ApprovalCard(request: ApprovalRequest, onApprove: () -> Unit, onDeny: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF312517)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("系统确认", color = Color(0xFFFFC66D), fontWeight = FontWeight.Bold)
            Text(request.title, style = MaterialTheme.typography.titleMedium)
            Text(request.explanation, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(request.capability.value, style = MaterialTheme.typography.labelSmall)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDeny, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(onClick = onApprove, modifier = Modifier.weight(1f)) { Text("允许一次") }
            }
        }
    }
}

@Composable
private fun GeneratedScreenView(screen: GeneratedScreen, onAction: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(screen.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            screen.blocks.forEach { block ->
                when (block) {
                    is UiBlock.Paragraph -> Text(block.text, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    is UiBlock.Fact -> FactRow(block)
                    is UiBlock.Action -> Button(
                        onClick = { onAction(block.prompt) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(block.label) }
                }
            }
        }
    }
}

@Composable
private fun FactRow(block: UiBlock.Fact) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(block.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(block.value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AgentOsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF83D5C5),
            onPrimary = Color(0xFF06201B),
            background = Color(0xFF0A1114),
            onBackground = Color(0xFFE4F1EE),
            surface = Color(0xFF111B1F),
            onSurface = Color(0xFFE4F1EE),
            surfaceVariant = Color(0xFF1A292D),
            onSurfaceVariant = Color(0xFFB6C9C5),
        ),
        content = content,
    )
}
