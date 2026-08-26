package com.agentos.shell

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import com.agentos.capability.core.ApprovalRequest
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    private var voiceOutput: VoiceOutputController? = null
    private val viewModel by lazy {
        ViewModelProvider(this, AgentShellViewModel.factory(applicationContext))[AgentShellViewModel::class.java]
    }
    private val mediaViewModel by lazy {
        ViewModelProvider(this, MediaWorkspaceViewModel.factory(applicationContext))[MediaWorkspaceViewModel::class.java]
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AgentOsTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val mediaState by mediaViewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.voiceReply) {
                    state.voiceReply?.let {
                        (voiceOutput ?: VoiceOutputController(applicationContext, ::rearmHotword)
                            .also { voiceOutput = it }).speak(it)
                        viewModel.consumeVoiceReply()
                    }
                }
                if (mediaState.mode != MediaWorkspaceMode.CLOSED) {
                    MediaWorkspace(
                        state = mediaState,
                        onClose = mediaViewModel::closeWorkspace,
                        onAttachSurface = mediaViewModel::attachCameraSurface,
                        onDetachSurface = mediaViewModel::detachCameraSurface,
                        onSwitchCamera = mediaViewModel::switchCamera,
                        onZoom = mediaViewModel::setZoom,
                        onFocus = mediaViewModel::focusCamera,
                        onCapturePhoto = mediaViewModel::capturePhoto,
                        onToggleVideo = mediaViewModel::toggleVideo,
                        onToggleAudio = mediaViewModel::toggleAudio,
                        onToggleAudioPause = mediaViewModel::toggleAudioPause,
                        onRefreshGallery = mediaViewModel::refreshGallery,
                        onOpenItem = { item ->
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.uri)).apply {
                                setDataAndType(Uri.parse(item.uri), item.mimeType)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            })
                        },
                    )
                } else AgentShellContent(
                    state = state,
                    onPromptChanged = viewModel::updatePrompt,
                    onSubmit = {
                        if (handleMediaCommand(state.prompt)) viewModel.updatePrompt("") else viewModel.submit()
                    },
                    onSuggestion = { command ->
                        if (!handleMediaCommand(command)) viewModel.submit(command)
                    },
                    onToggleModelSettings = viewModel::toggleModelSettings,
                    onRemoteModelEnabled = viewModel::setRemoteModelEnabled,
                    onModelEndpointChanged = viewModel::updateModelEndpoint,
                    onModelNameChanged = viewModel::updateModelName,
                    onModelApiKeyChanged = viewModel::updateModelApiKey,
                    onApprove = viewModel::approve,
                    onDeny = viewModel::deny,
                    onVoiceSettings = {
                        startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                    },
                    onToggleHistory = viewModel::toggleHistory,
                    onClearHistory = viewModel::clearHistory,
                    onRenameKnowledgeEntity = viewModel::renameKnowledgeEntity,
                    onMoveKnowledgeEntity = viewModel::moveKnowledgeEntity,
                    onEditKnowledgeRelation = viewModel::editKnowledgeRelation,
                    onRemoveKnowledgeRelation = viewModel::removeKnowledgeRelation,
                    onOpenMedia = mediaViewModel::open,
                    onNotificationAccess = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                )
            }
        }
        handleVoiceCommand(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleVoiceCommand(intent)
    }

    private fun handleVoiceCommand(intent: Intent?) {
        val action = intent?.action ?: return
        val token = intent.getStringExtra(VoiceCommandReceiver.EXTRA_TOKEN)
        intent.action = null
        when (action) {
            VoiceCommandReceiver.ACTION_RUN_COMMAND ->
                VoiceCommandInbox.take(token)?.let { command ->
                    if (!handleMediaCommand(command)) viewModel.submitVoice(command)
                }
            VoiceCommandReceiver.ACTION_INTERRUPT -> if (VoiceInterruptInbox.take(token)) {
                voiceOutput?.stop()
                viewModel.interruptVoiceTurn()
            }
        }
    }

    private fun handleMediaCommand(raw: String): Boolean {
        val command = raw.trim().replace("。", "").replace("！", "")
        return when (command) {
            "打开相机", "开启相机" -> true.also { mediaViewModel.open(MediaWorkspaceMode.CAMERA) }
            "拍照", "帮我拍照", "拍一张照片" -> true.also { mediaViewModel.requestPhoto() }
            "开始录像", "录像" -> true.also { mediaViewModel.requestVideo() }
            "停止录像" -> true.also { if (mediaViewModel.state.value.videoRecording) mediaViewModel.toggleVideo() }
            "打开图库", "打开相册", "查看照片" -> true.also { mediaViewModel.open(MediaWorkspaceMode.GALLERY) }
            "打开录音机" -> true.also { mediaViewModel.open(MediaWorkspaceMode.RECORDER) }
            "开始录音", "录音" -> true.also { mediaViewModel.requestAudioRecording() }
            "暂停录音", "继续录音" -> true.also { if (mediaViewModel.state.value.audioRecording) mediaViewModel.toggleAudioPause() }
            "停止录音" -> true.also { if (mediaViewModel.state.value.audioRecording) mediaViewModel.toggleAudio() }
            else -> false
        }
    }

    private fun rearmHotword() {
        sendBroadcast(
            Intent(ACTION_REARM_HOTWORD).setComponent(
                ComponentName(VOICE_PACKAGE, VOICE_REARM_RECEIVER),
            ),
        )
    }

    override fun onDestroy() {
        voiceOutput?.close()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_REARM_HOTWORD = "com.agentos.voice.action.REARM"
        private const val VOICE_PACKAGE = "com.agentos.voice"
        private const val VOICE_REARM_RECEIVER = "com.agentos.voice.AgentVoiceRearmReceiver"
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
    onVoiceSettings: () -> Unit,
    onToggleHistory: () -> Unit,
    onClearHistory: () -> Unit,
    onRenameKnowledgeEntity: (String, String, String) -> Unit,
    onMoveKnowledgeEntity: (String, Float, Float) -> Unit,
    onEditKnowledgeRelation: (String, String, String, String) -> Unit,
    onRemoveKnowledgeRelation: (String) -> Unit,
    onOpenMedia: (MediaWorkspaceMode) -> Unit,
    onNotificationAccess: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (state.showHistory) {
            KnowledgeScreen(state, onToggleHistory, onClearHistory,
                onRenameKnowledgeEntity, onMoveKnowledgeEntity,
                onEditKnowledgeRelation, onRemoveKnowledgeRelation)
        } else Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
            Header(state.useRemoteModel)
            ModelSettings(state, onToggleModelSettings, onRemoteModelEnabled,
                onModelEndpointChanged, onModelNameChanged, onModelApiKeyChanged)
            VoiceCard(state, onVoiceSettings)
            HistoryMindMap(state, onToggleHistory)
            MediaCard(onOpenMedia)

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
private fun VoiceCard(state: AgentUiState, onVoiceSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (state.isWorking) Color(0xFF17332D) else MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("语音智能体", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                if (state.isWorking) "正在处理语音目标" else state.voiceStatus,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onVoiceSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("配置系统语音助手")
            }
        }
    }
}

@Composable
private fun HistoryMindMap(
    state: AgentUiState,
    onToggle: () -> Unit,
) {
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
                    Text("历史知识图", fontWeight = FontWeight.Bold)
                    Text(
                        "本机私有 · ${state.history.size} 段历史 · ${state.knowledgeGraph.relations.size} 条关系",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onToggle) { Text(if (state.showHistory) "收起" else "查看") }
            }
        }
    }
}

@Composable
private fun MediaCard(onOpenMedia: (MediaWorkspaceMode) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("原生媒体工作区", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            Text("Camera HAL、MediaStore 与系统录音链路",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onOpenMedia(MediaWorkspaceMode.CAMERA) }) { Text("相机") }
                Button(onClick = { onOpenMedia(MediaWorkspaceMode.GALLERY) }) { Text("图库") }
                Button(onClick = { onOpenMedia(MediaWorkspaceMode.RECORDER) }) { Text("录音") }
            }
        }
    }
}

@Composable
private fun KnowledgeScreen(
    state: AgentUiState,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onRenameEntity: (String, String, String) -> Unit,
    onMoveEntity: (String, Float, Float) -> Unit,
    onEditRelation: (String, String, String, String) -> Unit,
    onRemoveRelation: (String) -> Unit,
) {
    var editingRelation by remember { mutableStateOf<KnowledgeRelation?>(null) }
    var deletingRelation by remember { mutableStateOf<KnowledgeRelation?>(null) }
    var confirmingClear by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("返回") }
                TextButton(onClick = { confirmingClear = true }, enabled = state.history.isNotEmpty()) { Text("清除全部") }
            }
        }
        item {
            Text("语义知识图谱", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)
            Text("${state.knowledgeGraph.entities.size} 个实体 · ${state.knowledgeGraph.relations.size} 条关系 · 全部带原文来源",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.knowledgeGraph.relations.isEmpty()) {
            item { Text("历史中暂未提取到人物、关系、偏好、项目或长期事实。") }
        } else {
            item { InteractiveKnowledgeGraph(state.knowledgeGraph, onRenameEntity, onMoveEntity) }
            item { Text("● 用户知识", color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold) }
            items(state.knowledgeGraph.relations, key = { "${it.sourceTurnId}:${it.source.id}:${it.predicate}:${it.target.id}:${it.evidence}" }) { relation ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("${relation.source.name}  ─${relation.predicate}→  ${relation.target.name}",
                            fontWeight = FontWeight.SemiBold)
                        Text("${relation.source.type} → ${relation.target.type} · ${if (relation.confirmed) "原文明示" else "模型候选"} · ${(relation.confidence * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                        Text("来源：${relation.evidence}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { editingRelation = relation }) { Text("修改关系") }
                            TextButton(onClick = { deletingRelation = relation }) { Text("删除") }
                        }
                    }
                }
            }
        }
        item {
            Text("全部历史（${state.history.size}）", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        }
        items(state.history.asReversed(), key = ConversationEntry::id) { entry ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(entry.prompt, fontWeight = FontWeight.SemiBold)
                    Text("→ ${entry.responseTitle}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Spacer(Modifier.size(18.dp)) }
    }
    editingRelation?.let { relation ->
        RelationEditor(relation, onDismiss = { editingRelation = null }) { predicate, target, type ->
            onEditRelation(relation.id, predicate, target, type)
            editingRelation = null
        }
    }
    deletingRelation?.let { relation ->
        AlertDialog(
            onDismissRequest = { deletingRelation = null },
            title = { Text("删除这条关系？") },
            text = { Text("${relation.source.name} ─${relation.predicate}→ ${relation.target.name}\n原始聊天历史不会被删除。") },
            confirmButton = { TextButton(onClick = {
                onRemoveRelation(relation.id)
                deletingRelation = null
            }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deletingRelation = null }) { Text("取消") } },
        )
    }
    if (confirmingClear) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            title = { Text("清除全部历史和知识？") },
            text = { Text("这会删除所有对话、实体、关系和手动布局，无法撤销。") },
            confirmButton = { TextButton(onClick = {
                onClear()
                confirmingClear = false
            }) { Text("全部清除") } },
            dismissButton = { TextButton(onClick = { confirmingClear = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun RelationEditor(
    relation: KnowledgeRelation,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var predicate by remember(relation.id) { mutableStateOf(relation.predicate) }
    var target by remember(relation.id) { mutableStateOf(relation.target.name) }
    var targetType by remember(relation.id) { mutableStateOf(relation.target.type) }
    val validPredicate = predicate.matches(Regex("[\\p{L}\\p{N}_-]{1,50}"))
    val valid = validPredicate && target.isNotBlank() && targetType.uppercase() in KNOWLEDGE_ENTITY_TYPES
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改知识关系") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(predicate, { predicate = it.take(50) }, label = { Text("关系") })
            OutlinedTextField(target, { target = it.take(100) }, label = { Text("目标节点") })
            OutlinedTextField(targetType, { targetType = it.take(30) }, label = { Text("目标类型") },
                supportingText = { Text(KNOWLEDGE_ENTITY_TYPES.joinToString()) })
            Text("原始来源保留：${relation.evidence}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } },
        confirmButton = { TextButton(onClick = { onSave(predicate, target, targetType.uppercase()) }, enabled = valid) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
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
