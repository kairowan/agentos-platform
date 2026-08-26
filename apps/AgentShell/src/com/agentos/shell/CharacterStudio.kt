package com.agentos.shell

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun CharacterStudio(
    avatar: AgentAvatar,
    onBack: () -> Unit,
    onSave: (AgentAvatar) -> Unit,
) {
    var draft by remember(avatar) { mutableStateOf(avatar) }
    var expression by remember { mutableStateOf(AvatarExpression.HAPPY) }
    AgentBackdrop {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { AgentTopBar("角色工作室", "捏出属于你的系统智能体", onBack, "保存") { onSave(draft) } }
            item {
                AgentPanel(Modifier.fillMaxWidth(), AgentMint) {
                    Column(
                        Modifier.fillMaxWidth().padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AgentAvatarView(draft, expression, Modifier.size(230.dp))
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Column {
                                Text(draft.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("表情会跟随聆听、思考和说话状态", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            AgentPill(expression.label)
                        }
                    }
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
                EditorSection("脸型与五官") {
                    Text("脸型", style = MaterialTheme.typography.labelLarge)
                    ChoiceRow(AvatarFaceShape.entries, draft.faceShape, { it.label }) { draft = draft.copy(faceShape = it) }
                    Text("眼型", style = MaterialTheme.typography.labelLarge)
                    ChoiceRow(AvatarEyeStyle.entries, draft.eyeStyle, { it.label }) { draft = draft.copy(eyeStyle = it) }
                    ShapeSlider("脸部宽度", draft.faceWidth) { draft = draft.copy(faceWidth = it) }
                    ShapeSlider("眼睛大小", draft.eyeSize) { draft = draft.copy(eyeSize = it) }
                    ShapeSlider("眼距", draft.eyeSpacing) { draft = draft.copy(eyeSpacing = it) }
                    ShapeSlider("嘴部宽度", draft.mouthWidth) { draft = draft.copy(mouthWidth = it) }
                }
            }
            item {
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
                        Text("外观参数只保存在设备内。未来接入照片建模或云端生成时，必须单独取得授权。",
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

@Composable
internal fun AgentAvatarView(
    avatar: AgentAvatar,
    expression: AvatarExpression,
    modifier: Modifier = Modifier,
) {
    val skin = Color(avatar.skinTone.argb)
    val hair = Color(avatar.hairColor.argb)
    val outfit = Color(avatar.outfitColor.argb)
    val ink = Color(0xFF18262A)
    Canvas(
        modifier.background(Color(0xFF0B1A1F), RoundedCornerShape(28.dp))
            .border(1.dp, AgentMint.copy(alpha = 0.28f), RoundedCornerShape(28.dp))
            .semantics { contentDescription = "${avatar.name}，${expression.label}表情" },
    ) {
        val cx = size.width / 2f
        val unit = size.minDimension
        val faceW = unit * (0.47f + avatar.faceWidth * 0.09f)
        val faceH = when (avatar.faceShape) {
            AvatarFaceShape.ROUND -> faceW
            AvatarFaceShape.OVAL -> faceW * 1.14f
            AvatarFaceShape.HEART -> faceW * 1.08f
            AvatarFaceShape.SQUARE -> faceW * 1.02f
        }
        val faceTop = unit * 0.18f
        val faceLeft = cx - faceW / 2f
        val hairPad = unit * 0.045f

        drawOval(outfit, Offset(cx - unit * 0.36f, unit * 0.70f), Size(unit * 0.72f, unit * 0.42f))
        drawRoundRect(skin, Offset(cx - unit * 0.075f, unit * 0.60f), Size(unit * 0.15f, unit * 0.20f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(unit * 0.04f))
        if (avatar.hairStyle == AvatarHairStyle.PONYTAIL) {
            drawCircle(hair, unit * 0.13f, Offset(cx + faceW * 0.54f, faceTop + faceH * 0.42f))
        }
        if (avatar.hairStyle == AvatarHairStyle.BOB || avatar.hairStyle == AvatarHairStyle.WAVY) {
            drawOval(hair, Offset(faceLeft - hairPad, faceTop - hairPad), Size(faceW + hairPad * 2f, faceH + hairPad * 1.8f))
        }
        val corner = if (avatar.faceShape == AvatarFaceShape.SQUARE) unit * 0.08f else faceW / 2f
        drawRoundRect(skin, Offset(faceLeft, faceTop), Size(faceW, faceH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner))
        drawCircle(skin, unit * 0.045f, Offset(faceLeft, faceTop + faceH * 0.5f))
        drawCircle(skin, unit * 0.045f, Offset(faceLeft + faceW, faceTop + faceH * 0.5f))

        if (avatar.hairStyle != AvatarHairStyle.BALD) {
            val hairHeight = when (avatar.hairStyle) {
                AvatarHairStyle.BUZZ -> faceH * 0.22f
                AvatarHairStyle.SHORT -> faceH * 0.30f
                else -> faceH * 0.36f
            }
            drawArc(hair, 180f, 180f, true, Offset(faceLeft, faceTop - hairPad), Size(faceW, hairHeight * 2f))
            if (avatar.hairStyle == AvatarHairStyle.WAVY) {
                repeat(5) { index ->
                    drawCircle(hair, unit * 0.045f, Offset(faceLeft + faceW * (0.15f + index * 0.17f), faceTop + hairHeight * 0.52f))
                }
            }
        }

        val eyeY = faceTop + faceH * 0.48f
        val eyeGap = faceW * (0.16f + avatar.eyeSpacing * 0.055f)
        val eyeRadius = unit * (0.018f + avatar.eyeSize * 0.018f)
        val leftEye = Offset(cx - eyeGap, eyeY)
        val rightEye = Offset(cx + eyeGap, eyeY)
        val stroke = unit * 0.014f

        if (expression == AvatarExpression.HAPPY || expression == AvatarExpression.SLEEPY) {
            drawArc(ink, 190f, 160f, false, leftEye - Offset(eyeRadius, eyeRadius), Size(eyeRadius * 2f, eyeRadius * 1.4f),
                style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(ink, 190f, 160f, false, rightEye - Offset(eyeRadius, eyeRadius), Size(eyeRadius * 2f, eyeRadius * 1.4f),
                style = Stroke(stroke, cap = StrokeCap.Round))
        } else {
            val scaleY = if (avatar.eyeStyle == AvatarEyeStyle.CALM || avatar.eyeStyle == AvatarEyeStyle.SHARP) 0.55f else 1f
            drawOval(ink, Offset(leftEye.x - eyeRadius, leftEye.y - eyeRadius * scaleY), Size(eyeRadius * 2f, eyeRadius * 2f * scaleY))
            drawOval(ink, Offset(rightEye.x - eyeRadius, rightEye.y - eyeRadius * scaleY), Size(eyeRadius * 2f, eyeRadius * 2f * scaleY))
            if (avatar.eyeStyle == AvatarEyeStyle.BRIGHT || expression == AvatarExpression.SURPRISED) {
                drawCircle(Color.White, eyeRadius * 0.34f, leftEye - Offset(eyeRadius * 0.25f, eyeRadius * 0.25f))
                drawCircle(Color.White, eyeRadius * 0.34f, rightEye - Offset(eyeRadius * 0.25f, eyeRadius * 0.25f))
            }
        }

        val browY = eyeY - eyeRadius * 2.1f
        val browTilt = if (expression == AvatarExpression.CONCERNED) eyeRadius else if (expression == AvatarExpression.THINKING) -eyeRadius else 0f
        drawLine(ink, Offset(leftEye.x - eyeRadius, browY + browTilt), Offset(leftEye.x + eyeRadius, browY - browTilt), stroke, StrokeCap.Round)
        drawLine(ink, Offset(rightEye.x - eyeRadius, browY - browTilt), Offset(rightEye.x + eyeRadius, browY + browTilt), stroke, StrokeCap.Round)

        val mouthY = faceTop + faceH * 0.73f
        val mouthW = faceW * (0.16f + avatar.mouthWidth * 0.12f)
        when (expression) {
            AvatarExpression.HAPPY, AvatarExpression.LISTENING -> drawArc(ink, 0f, 180f, false,
                Offset(cx - mouthW / 2f, mouthY - mouthW * 0.2f), Size(mouthW, mouthW * 0.55f),
                style = Stroke(stroke, cap = StrokeCap.Round))
            AvatarExpression.SPEAKING, AvatarExpression.SURPRISED -> drawOval(ink,
                Offset(cx - mouthW * 0.28f, mouthY - mouthW * 0.18f), Size(mouthW * 0.56f, mouthW * 0.58f),
                style = Stroke(stroke))
            AvatarExpression.CONCERNED -> drawArc(ink, 180f, 180f, false,
                Offset(cx - mouthW / 2f, mouthY), Size(mouthW, mouthW * 0.45f),
                style = Stroke(stroke, cap = StrokeCap.Round))
            AvatarExpression.THINKING -> drawLine(ink, Offset(cx - mouthW / 2f, mouthY), Offset(cx + mouthW / 2f, mouthY - stroke), stroke, StrokeCap.Round)
            AvatarExpression.SLEEPY -> drawLine(ink, Offset(cx - mouthW / 3f, mouthY), Offset(cx + mouthW / 3f, mouthY), stroke, StrokeCap.Round)
            AvatarExpression.NEUTRAL -> drawArc(ink, 12f, 156f, false,
                Offset(cx - mouthW / 2f, mouthY), Size(mouthW, mouthW * 0.25f),
                style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}
