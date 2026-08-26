package com.agentos.shell

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal data class GraphNodePosition(
    val entity: KnowledgeEntity,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) {
    fun contains(point: Offset) = point.x in x..(x + width) && point.y in y..(y + height)
    val center get() = Offset(x + width / 2, y + height / 2)
}

internal fun layoutKnowledgeGraph(
    entities: List<KnowledgeEntity>,
    nodeWidth: Float = 220f,
    nodeHeight: Float = 84f,
    columnGap: Float = 100f,
    rowGap: Float = 42f,
): List<GraphNodePosition> {
    val typeOrder = listOf("PERSON", "ORGANIZATION", "PROJECT", "PLACE", "PREFERENCE", "FACT")
    val grouped = entities.groupBy(KnowledgeEntity::type)
    return buildList {
        typeOrder.filter(grouped::containsKey).forEachIndexed { column, type ->
            grouped.getValue(type).forEachIndexed { row, entity ->
                add(GraphNodePosition(entity, column * (nodeWidth + columnGap),
                    row * (nodeHeight + rowGap), nodeWidth, nodeHeight))
            }
        }
    }
}

@Composable
internal fun InteractiveKnowledgeGraph(
    graph: KnowledgeGraph,
    onRenameEntity: (String, String, String) -> Unit,
) {
    val density = LocalDensity.current.density
    val nodes = remember(graph.entities) {
        layoutKnowledgeGraph(graph.entities, 190f * density, 76f * density,
            90f * density, 38f * density)
    }
    val nodeById = remember(nodes) { nodes.associateBy { it.entity.id } }
    var zoomScale by remember { mutableFloatStateOf(0.85f) }
    var offset by remember { mutableStateOf(Offset(32f, 32f)) }
    var selected by remember { mutableStateOf<KnowledgeEntity?>(null) }
    val transformState = rememberTransformableState { zoom, pan, _ ->
        zoomScale = (zoomScale * zoom).coerceIn(0.35f, 4f)
        offset += pan
    }
    val titlePaint = remember(density) { Paint().apply {
        color = android.graphics.Color.WHITE; textSize = 15f * density; isAntiAlias = true
    } }
    val metaPaint = remember(density) { Paint().apply {
        color = android.graphics.Color.LTGRAY; textSize = 10f * density; isAntiAlias = true
    } }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { zoomScale = (zoomScale / 1.25f).coerceAtLeast(0.35f) }) { Text("缩小") }
            Button(onClick = { zoomScale = (zoomScale * 1.25f).coerceAtMost(4f) }) { Text("放大") }
            TextButton(onClick = { zoomScale = 0.85f; offset = Offset(32f, 32f) }) { Text("复位") }
        }
        Canvas(
            Modifier.fillMaxWidth().height(520.dp)
                .background(Color(0xFF0D171A), RoundedCornerShape(20.dp))
                .semantics { contentDescription = "可缩放知识图谱，包含 ${nodes.size} 个实体" }
                .transformable(transformState)
                .pointerInput(nodes, zoomScale, offset) {
                    detectTapGestures { tap ->
                        val world = Offset(
                            (tap.x - offset.x) / zoomScale,
                            (tap.y - offset.y) / zoomScale,
                        )
                        selected = nodes.lastOrNull { it.contains(world) }?.entity
                    }
                },
        ) {
            withTransform({ translate(offset.x, offset.y); scale(zoomScale, zoomScale, Offset.Zero) }) {
                graph.relations.forEach { relation ->
                    val source = nodeById[relation.source.id]?.center ?: return@forEach
                    val target = nodeById[relation.target.id]?.center ?: return@forEach
                    drawLine(Color(0xFF55716C), source, target, strokeWidth = 2f / zoomScale)
                    drawContext.canvas.nativeCanvas.drawText(
                        relation.predicate.take(24),
                        (source.x + target.x) / 2,
                        (source.y + target.y) / 2 - 6f,
                        metaPaint,
                    )
                }
                nodes.forEach { node ->
                    drawRoundRect(
                        color = typeColor(node.entity.type),
                        topLeft = Offset(node.x, node.y),
                        size = androidx.compose.ui.geometry.Size(node.width, node.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f),
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        node.entity.name.take(24), node.x + 16f, node.y + node.height / 2,
                        titlePaint,
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        node.entity.type, node.x + 16f, node.y + node.height - 12f,
                        metaPaint,
                    )
                }
            }
        }
        Text("双指缩放、拖动查看；点击节点可修改。关系详情和来源位于画布下方。",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    selected?.let { entity ->
        EntityEditor(entity, onDismiss = { selected = null }) { name, type ->
            onRenameEntity(entity.id, name, type)
            selected = null
        }
    }
}

@Composable
private fun EntityEditor(entity: KnowledgeEntity, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember(entity.id) { mutableStateOf(entity.name) }
    var type by remember(entity.id) { mutableStateOf(entity.type) }
    val valid = name.isNotBlank() && type.uppercase() in KNOWLEDGE_ENTITY_TYPES
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改知识节点") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(100) }, label = { Text("名称") })
                OutlinedTextField(type, { type = it.take(30) }, label = { Text("类型") },
                    supportingText = { Text(KNOWLEDGE_ENTITY_TYPES.joinToString()) })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim(), type.uppercase()) }, enabled = valid) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun typeColor(type: String) = when (type) {
    "PERSON" -> Color(0xFF285F56)
    "ORGANIZATION" -> Color(0xFF375A83)
    "PROJECT" -> Color(0xFF66522E)
    "PLACE" -> Color(0xFF4D5E36)
    "PREFERENCE" -> Color(0xFF704A66)
    else -> Color(0xFF4C5558)
}
