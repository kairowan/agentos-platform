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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import com.agentos.capability.api.CommunicationContract

class MainActivity : ComponentActivity() {
    private var voiceOutput: VoiceOutputController? = null
    private val viewModel by lazy {
        ViewModelProvider(this, AgentShellViewModel.factory(applicationContext))[AgentShellViewModel::class.java]
    }
    private val mediaViewModel by lazy {
        ViewModelProvider(this, MediaWorkspaceViewModel.factory(applicationContext))[MediaWorkspaceViewModel::class.java]
    }
    private val appViewModel by lazy {
        ViewModelProvider(this, AppWorkspaceViewModel.factory(applicationContext))[AppWorkspaceViewModel::class.java]
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AgentOsTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val mediaState by mediaViewModel.state.collectAsStateWithLifecycle()
                val appState by appViewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.voiceReply) {
                    state.voiceReply?.let {
                        viewModel.markVoiceOutputStarted()
                        (voiceOutput ?: VoiceOutputController(applicationContext, ::finishVoiceOutput)
                            .also { voiceOutput = it }).speak(it)
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
                } else if (appState.open) {
                    AppWorkspace(
                        state = appState,
                        onClose = appViewModel::close,
                        onShowApps = appViewModel::showApps,
                        onRefreshApps = appViewModel::loadApps,
                        onRefreshSemantics = appViewModel::refreshSemantics,
                        onLaunch = appViewModel::requestLaunch,
                        onClickNode = appViewModel::click,
                        onScrollNode = appViewModel::scroll,
                        onSetText = appViewModel::setText,
                        onAccessibilitySettings = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        onApprove = appViewModel::approve,
                        onDeny = appViewModel::deny,
                        onCancelPending = { appViewModel.cancelPending() },
                    )
                } else AgentShellContent(
                    state = state,
                    onPromptChanged = viewModel::updatePrompt,
                    onSubmit = {
                        appViewModel.cancelPending()
                        if (handleLocalCommand(state.prompt)) viewModel.updatePrompt("") else viewModel.submit()
                    },
                    onSuggestion = { command ->
                        appViewModel.cancelPending()
                        // Model-generated action text cannot directly control a live call.
                        if (CommunicationCommands.parse(command) != null) openCommunication() else if (!handleLocalCommand(command)) viewModel.submit(command)
                    },
                    onToggleModelSettings = viewModel::toggleModelSettings,
                    onRemoteModelEnabled = viewModel::setRemoteModelEnabled,
                    onModelEndpointChanged = viewModel::updateModelEndpoint,
                    onModelNameChanged = viewModel::updateModelName,
                    onModelApiKeyChanged = viewModel::updateModelApiKey,
                    onMemorySharing = viewModel::setMemorySharing,
                    onApprove = viewModel::approve,
                    onDeny = viewModel::deny,
                    onVoiceSettings = {
                        startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                    },
                    onToggleHistory = viewModel::toggleHistory,
                    onOpenAvatarStudio = viewModel::openAvatarStudio,
                    onOpenCommunication = ::openCommunication,
                    onInterrupt = { voiceOutput?.stop(); appViewModel.cancelPending(); viewModel.interruptVoiceTurn() },
                    onCloseAvatarStudio = viewModel::closeAvatarStudio,
                    onSaveAvatar = viewModel::saveAvatar,
                    onGenerateAvatarStyle = viewModel::generateAvatarStyle,
                    onClearHistory = viewModel::clearHistory,
                    onRenameKnowledgeEntity = viewModel::renameKnowledgeEntity,
                    onMoveKnowledgeEntity = viewModel::moveKnowledgeEntity,
                    onEditKnowledgeRelation = viewModel::editKnowledgeRelation,
                    onRemoveKnowledgeRelation = viewModel::removeKnowledgeRelation,
                    onOpenMedia = mediaViewModel::open,
                    onOpenApps = appViewModel::open,
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
                    voiceOutput?.stop()
                    appViewModel.cancelPending()
                    if (!handleLocalCommand(command)) viewModel.submitVoice(command)
                }
            VoiceCommandReceiver.ACTION_INTERRUPT -> if (VoiceInterruptInbox.take(token)) {
                voiceOutput?.stop()
                appViewModel.cancelPending()
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

    private fun handleLocalCommand(raw: String): Boolean {
        CommunicationCommands.parse(raw)?.let { command ->
            voiceOutput?.stop()
            viewModel.interruptVoiceTurn()
            if (command == CommunicationCommand.Open) openCommunication() else lifecycleScope.launch {
                try {
                    val reply = sendCommunicationCommand(applicationContext, command)
                    viewModel.showLocalNotice(reply.message)
                    if (command is CommunicationCommand.Draft) openCommunication()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { viewModel.showLocalNotice("通信组件不可用，请安装同一预览包内的全部 APK") }
            }
            return true
        }
        if (handleMediaCommand(raw)) return true
        val command = raw.trim().replace("。", "").replace("！", "")
        return when (command) {
            "打开应用能力", "打开应用中心", "应用中心", "应用能力" -> true.also { appViewModel.open() }
            "创建角色", "编辑角色", "打开角色工作室", "捏人" -> true.also { viewModel.openAvatarStudio() }
            "读取当前页面", "分析当前页面" -> true.also { appViewModel.open(); appViewModel.refreshSemantics() }
            else -> false
        }
    }

    private fun openCommunication() {
        voiceOutput?.stop()
        runCatching { startActivity(Intent(CommunicationContract.REVIEW).setComponent(
            ComponentName(CommunicationContract.PACKAGE, CommunicationContract.ACTIVITY))) }
            .onFailure { viewModel.showLocalNotice("通信组件未安装或版本不匹配") }
    }

    override fun onStop() {
        voiceOutput?.stop() // Telecom must never compete with a lingering agent response.
        if (appViewModel.state.value.approvalToken != null) appViewModel.cancelPending()
        super.onStop()
    }

    private fun rearmHotword() {
        sendBroadcast(
            Intent(ACTION_REARM_HOTWORD).setComponent(
                ComponentName(VOICE_PACKAGE, VOICE_REARM_RECEIVER),
            ),
        )
    }

    private fun finishVoiceOutput() {
        viewModel.finishVoiceOutput()
        rearmHotword()
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
    onMemorySharing: (Boolean) -> Unit,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onVoiceSettings: () -> Unit,
    onToggleHistory: () -> Unit,
    onOpenAvatarStudio: () -> Unit,
    onOpenCommunication: () -> Unit,
    onInterrupt: () -> Unit,
    onCloseAvatarStudio: () -> Unit,
    onSaveAvatar: (AgentAvatar) -> Unit,
    onGenerateAvatarStyle: (String, AgentAvatar) -> Unit,
    onClearHistory: () -> Unit,
    onRenameKnowledgeEntity: (String, String, String) -> Unit,
    onMoveKnowledgeEntity: (String, Float, Float) -> Unit,
    onEditKnowledgeRelation: (String, String, String, String) -> Unit,
    onRemoveKnowledgeRelation: (String) -> Unit,
    onOpenMedia: (MediaWorkspaceMode) -> Unit,
    onOpenApps: () -> Unit,
    onNotificationAccess: () -> Unit,
) {
    if (state.showAvatarStudio) {
        CharacterStudio(
            avatar = state.avatar,
            generatedAvatar = state.generatedAvatarDraft,
            styleWorking = state.avatarStyleWorking,
            styleError = state.avatarStyleError,
            onBack = onCloseAvatarStudio,
            onSave = onSaveAvatar,
            onGenerateStyle = onGenerateAvatarStyle,
        )
    } else if (state.showHistory) {
        KnowledgeScreen(state, onToggleHistory, onClearHistory,
            onRenameKnowledgeEntity, onMoveKnowledgeEntity,
            onEditKnowledgeRelation, onRemoveKnowledgeRelation)
    } else AgentHomeScreen(
        state, onPromptChanged, onSubmit, onToggleModelSettings,
        onRemoteModelEnabled, onModelEndpointChanged, onModelNameChanged,
        onModelApiKeyChanged, onMemorySharing, onApprove, onDeny, onVoiceSettings, onToggleHistory,
        onOpenAvatarStudio, onOpenCommunication, onInterrupt,
    )
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
    var showTasks by remember { mutableStateOf(false) }
    AgentBackdrop {
      LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        item {
            AgentTopBar("记忆与知识", "全部历史、人物、关系与长期事实", onBack,
                "清理", { if (state.history.isNotEmpty() || state.tasks.isNotEmpty()) confirmingClear = true })
        }
        item {
            AgentPanel(Modifier.fillMaxWidth(), AgentBlue) {
                Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceEvenly) {
                    KnowledgeMetric("历史", state.history.size)
                    KnowledgeMetric("实体", state.knowledgeGraph.entities.size)
                    KnowledgeMetric("关系", state.knowledgeGraph.relations.size)
                }
            }
        }
        if (state.knowledgeGraph.relations.isEmpty()) {
            item { Text("历史中暂未提取到人物、关系、偏好、项目或长期事实。") }
        } else {
            item { InteractiveKnowledgeGraph(state.knowledgeGraph, onRenameEntity, onMoveEntity) }
            item { Text("● 用户知识", color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold) }
            items(state.knowledgeGraph.relations, key = { "${it.sourceTurnId}:${it.source.id}:${it.predicate}:${it.target.id}:${it.evidence}" }) { relation ->
                AgentPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("${relation.source.name}  ─${relation.predicate}→  ${relation.target.name}",
                            fontWeight = FontWeight.SemiBold)
                        Text("${relation.source.type} → ${relation.target.type} · ${if (relation.userCorrected) "用户已修正" else if (relation.confirmed) "原文明示／用户已确认" else "模型候选"} · ${(relation.confidence * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                        Text("来源：${relation.evidence}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("来源记录：${relation.sourceTurnId}", style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { editingRelation = relation }) { Text("修改关系") }
                            TextButton(onClick = { deletingRelation = relation }) { Text("删除") }
                        }
                    }
                }
            }
        }
        item {
            TextButton(onClick = { showTasks = !showTasks }) {
                Text("${if (showTasks) "收起" else "查看"}任务记录（${state.tasks.size}）· 中断后不自动重试")
            }
        }
        if (showTasks) items(state.tasks.asReversed(), key = { "task:${it.id}" }) { task ->
            AgentPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(task.prompt, fontWeight = FontWeight.SemiBold)
                    Text(TaskState.parse(task.state).label, color = MaterialTheme.colorScheme.primary)
                    if (task.detail.isNotBlank()) Text(task.detail)
                    Text("任务：${task.id}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        item {
            Text("全部历史（${state.history.size}）", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        }
        items(state.history.asReversed(), key = ConversationEntry::id) { entry ->
            var expanded by remember(entry.id) { mutableStateOf(false) }
            AgentPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(entry.prompt, fontWeight = FontWeight.SemiBold)
                    Text("→ ${entry.responseTitle}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(TaskState.parse(entry.taskState).label, color = MaterialTheme.colorScheme.primary)
                    if (entry.responseBody.isNotEmpty()) {
                        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起回复" else "完整回复") }
                        if (expanded) Text(entry.responseBody)
                    } else Text("旧版仅保存标题，无法恢复原回复。", style = MaterialTheme.typography.bodySmall)
                    if (entry.memoryExcluded) Text("此记录仅保留在本机，不再作为模型记忆上下文。", style = MaterialTheme.typography.bodySmall)
                    Text("来源：${entry.id}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        item { Spacer(Modifier.size(18.dp)) }
      }
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
            text = { Text("这会删除所有对话、任务记录、实体、关系和手动布局，无法撤销。不会撤销已发生的系统操作。") },
            confirmButton = { TextButton(onClick = {
                onClear()
                confirmingClear = false
            }) { Text("全部清除") } },
            dismissButton = { TextButton(onClick = { confirmingClear = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun KnowledgeMetric(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall,
            color = AgentMint, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
internal fun NotificationInbox(state: AgentUiState, onNotificationAccess: () -> Unit) {
    AgentPanel(Modifier.fillMaxWidth()) {
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
internal fun ModelSettings(
    state: AgentUiState,
    onToggle: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onEndpoint: (String) -> Unit,
    onModel: (String) -> Unit,
    onApiKey: (String) -> Unit,
    onMemorySharing: (Boolean) -> Unit,
) {
    AgentPanel(Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)) {
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
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("向当前端点共享记忆", modifier = Modifier.weight(1f))
                    Switch(checked = state.shareMemoryWithModel, onCheckedChange = onMemorySharing,
                        enabled = state.useRemoteModel && state.modelEndpoint.isNotBlank())
                }
                Text("默认关闭。开启后发送最多 6 条近期对话摘录和 12 条已确认事实；仅本次会话有效，修改端点会关闭。关闭不能撤回已发送内容。",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun NoticeCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF2D2614),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(message, modifier = Modifier.padding(14.dp), color = Color(0xFFFFDFA0))
    }
}

@Composable
internal fun ApprovalCard(
    request: ApprovalRequest,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AgentPanel(modifier.fillMaxWidth(), AgentAmber) {
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
internal fun GeneratedScreenView(screen: GeneratedScreen, onAction: (String) -> Unit) {
    AgentPanel(Modifier.fillMaxWidth(), AgentBlue) {
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
