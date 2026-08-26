package com.agentos.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp

@Composable
internal fun CharacterStudio(
    avatar: AgentAvatar,
    generatedAvatar: AgentAvatar?,
    styleWorking: Boolean,
    styleError: String?,
    onBack: () -> Unit,
    onSave: (AgentAvatar) -> Unit,
    onGenerateStyle: (String, AgentAvatar) -> Unit,
) {
    var draft by remember(avatar) { mutableStateOf(avatar) }
    var expression by remember { mutableStateOf(AvatarExpression.HAPPY) }
    var stylePrompt by remember { mutableStateOf("") }
    LaunchedEffect(generatedAvatar) { generatedAvatar?.let { draft = it } }
    AgentBackdrop {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { AgentTopBar("3D 角色工作室", "旋转、缩放并塑造系统智能体", onBack, "保存") { onSave(draft) } }
            item {
                AgentPanel(Modifier.fillMaxWidth(), AgentMint) {
                    Column(
                        Modifier.fillMaxWidth().padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AgentAvatarView(draft, expression, Modifier.size(230.dp))
                        Text("拖动旋转 · 双指缩放", color = AgentMint,
                            style = MaterialTheme.typography.labelSmall)
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Column {
                                Text(draft.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(draft.styleDescription, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            AgentPill(expression.label)
                        }
                    }
                }
            }
            item {
                EditorSection("让大模型设计整个风格") {
                    OutlinedTextField(
                        value = stylePrompt,
                        onValueChange = { stylePrompt = it.take(1_000) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (draft.styleFamily == AvatarStyleFamily.SYSTEM)
                            "例如：更宁静的暗色思维场，琥珀核心，星图缓慢流动"
                        else "例如：赛博仙侠风，银发、全息面罩、轻型机甲") },
                        minLines = 2,
                        maxLines = 4,
                        enabled = !styleWorking,
                    )
                    styleError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(
                        onClick = { onGenerateStyle(stylePrompt, draft) },
                        enabled = stylePrompt.isNotBlank() && !styleWorking,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (styleWorking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("生成 3D 风格")
                    }
                    Text("模型只生成经过 Schema 校验的参数，不执行代码或下载未知资产。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { draft = randomAgentAvatar().copy(name = draft.name) }, Modifier.weight(1f)) {
                        Text("随机生成")
                    }
                    OutlinedButton(onClick = { draft = AgentAvatar() }, Modifier.weight(1f)) { Text("恢复默认") }
                }
            }
            item {
                EditorSection("角色信息") {
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { draft = draft.copy(name = it.take(AgentAvatar.MAX_NAME_LENGTH)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("角色名字") },
                        singleLine = true,
                    )
                }
            }
            item {
                EditorSection("预览表情") {
                    ChoiceRow(AvatarExpression.entries, expression, { it.label }) { expression = it }
                }
            }
            item {
                EditorSection(if (draft.styleFamily == AvatarStyleFamily.SYSTEM) "思维场塑形" else "脸型与五官") {
                    if (draft.styleFamily != AvatarStyleFamily.SYSTEM) {
                        Text("脸型", style = MaterialTheme.typography.labelLarge)
                        ChoiceRow(AvatarFaceShape.entries, draft.faceShape, { it.label }) { draft = draft.copy(faceShape = it) }
                        Text("眼型", style = MaterialTheme.typography.labelLarge)
                        ChoiceRow(AvatarEyeStyle.entries, draft.eyeStyle, { it.label }) { draft = draft.copy(eyeStyle = it) }
                    }
                    ShapeSlider(if (draft.styleFamily == AvatarStyleFamily.SYSTEM) "意识结宽度" else "脸部宽度",
                        draft.faceWidth) { draft = draft.copy(faceWidth = it) }
                    ShapeSlider(if (draft.styleFamily == AvatarStyleFamily.SYSTEM) "光学表情强度" else "眼睛大小",
                        draft.eyeSize) { draft = draft.copy(eyeSize = it) }
                    ShapeSlider(if (draft.styleFamily == AvatarStyleFamily.SYSTEM) "光点间距" else "眼距",
                        draft.eyeSpacing) { draft = draft.copy(eyeSpacing = it) }
                    ShapeSlider(if (draft.styleFamily == AvatarStyleFamily.SYSTEM) "声纹宽度" else "嘴部宽度",
                        draft.mouthWidth) { draft = draft.copy(mouthWidth = it) }
                }
            }
            item {
                EditorSection("3D 风格与材质") {
                    Text("整体风格", style = MaterialTheme.typography.labelLarge)
                    ChoiceRow(AvatarStyleFamily.entries, draft.styleFamily, { it.label }) { draft = draft.copy(styleFamily = it) }
                    if (draft.styleFamily == AvatarStyleFamily.SYSTEM) {
                        Text("暗色玻璃、琥珀核心和记忆星图是 AgentOS 的固定身份锚点；下面调整其生命感。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("材质", style = MaterialTheme.typography.labelLarge)
                        ChoiceRow(AvatarMaterial.entries, draft.material, { it.label }) { draft = draft.copy(material = it) }
                        Text("服装结构", style = MaterialTheme.typography.labelLarge)
                        ChoiceRow(AvatarOutfitStyle.entries, draft.outfitStyle, { it.label }) { draft = draft.copy(outfitStyle = it) }
                        Text("配件", style = MaterialTheme.typography.labelLarge)
                        ChoiceRow(AvatarAccessory.entries, draft.accessory, { it.label }) { draft = draft.copy(accessory = it) }
                    }
                    ShapeSlider(if (draft.styleFamily == AvatarStyleFamily.SYSTEM) "核心尺度" else "头部比例",
                        draft.headScale) { draft = draft.copy(headScale = it) }
                    ShapeSlider(if (draft.styleFamily == AvatarStyleFamily.SYSTEM) "星图高度" else "身高比例",
                        draft.bodyHeight) { draft = draft.copy(bodyHeight = it) }
                    ShapeSlider(if (draft.styleFamily == AvatarStyleFamily.SYSTEM) "星图宽度" else "肩部宽度",
                        draft.shoulderWidth) { draft = draft.copy(shoulderWidth = it) }
                    ShapeSlider("发光强度", draft.glow) { draft = draft.copy(glow = it) }
                }
            }
            if (draft.styleFamily != AvatarStyleFamily.SYSTEM) item {
                EditorSection("发型与配色") {
                    Text("发型", style = MaterialTheme.typography.labelLarge)
                    ChoiceRow(AvatarHairStyle.entries, draft.hairStyle, { it.label }) { draft = draft.copy(hairStyle = it) }
                    Text("肤色", style = MaterialTheme.typography.labelLarge)
                    ColorChoiceRow(AvatarSkinTone.entries, draft.skinTone, { it.argb }, { it.label }) {
                        draft = draft.copy(skinTone = it)
                    }
                    Text("发色", style = MaterialTheme.typography.labelLarge)
                    ColorChoiceRow(AvatarHairColor.entries, draft.hairColor, { it.argb }, { it.label }) {
                        draft = draft.copy(hairColor = it)
                    }
                    Text("服装色", style = MaterialTheme.typography.labelLarge)
                    ColorChoiceRow(AvatarOutfitColor.entries, draft.outfitColor, { it.argb }, { it.label }) {
                        draft = draft.copy(outfitColor = it)
                    }
                }
            }
            item {
                AgentPanel(Modifier.fillMaxWidth(), AgentBlue) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("本地角色", fontWeight = FontWeight.Bold)
                        Text("角色参数只保存在设备内。启用远程模型时，仅发送你的风格描述和当前参数，不发送照片。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { onSave(draft) }, modifier = Modifier.fillMaxWidth()) { Text("保存并使用") }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorSection(title: String, content: @Composable () -> Unit) {
    AgentPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun <T> ChoiceRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { option ->
            val active = option == selected
            Box(
                Modifier
                    .background(if (active) AgentMint.copy(alpha = 0.18f) else AgentSurfaceHigh, CircleShape)
                    .border(1.dp, if (active) AgentMint else MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) { Text(label(option), color = if (active) AgentMint else MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun <T> ColorChoiceRow(
    options: List<T>, selected: T, color: (T) -> Long, label: (T) -> String, onSelect: (T) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(options) { option ->
            val active = option == selected
            Column(
                Modifier.clickable { onSelect(option) }.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier.size(38.dp).background(Color(color(option)), CircleShape)
                        .border(if (active) 3.dp else 1.dp, if (active) AgentMint else MaterialTheme.colorScheme.outline, CircleShape),
                )
                Text(label(option), style = MaterialTheme.typography.labelSmall,
                    color = if (active) AgentMint else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ShapeSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text("${(value * 100).toInt()}%", color = AgentMint, style = MaterialTheme.typography.bodySmall)
        }
        Slider(value = value, onValueChange = onValueChange)
    }
}
