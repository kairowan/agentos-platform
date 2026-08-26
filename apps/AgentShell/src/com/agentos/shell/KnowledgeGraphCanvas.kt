package com.agentos.shell

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

internal data class GraphNodePosition(
    val entity: KnowledgeEntity,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) {
    fun contains(point: Offset) = point.x in x..(x + width) && point.y in y..(y + height)
    fun intersects(left: Float, top: Float, right: Float, bottom: Float) =
        x <= right && x + width >= left && y <= bottom && y + height >= top
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
    onMoveEntity: (String, Float, Float) -> Unit,
) {
    val density = LocalDensity.current.density
    var localPositions by remember(graph.positions, density) {
        mutableStateOf(graph.positions.mapValues { Offset(it.value.x * density, it.value.y * density) })
    }
    val nodes = remember(graph.entities, localPositions, density) {
        layoutKnowledgeGraph(graph.entities, 190f * density, 76f * density,
            90f * density, 38f * density).map { node ->
            localPositions[node.entity.id]?.let { node.copy(x = it.x, y = it.y) } ?: node
        }
    }
    val nodeById = remember(nodes) { nodes.associateBy { it.entity.id } }
    var zoomScale by remember { mutableFloatStateOf(0.85f) }
    var offset by remember { mutableStateOf(Offset(32f, 32f)) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var selected by remember { mutableStateOf<KnowledgeEntity?>(null) }
    var query by remember { mutableStateOf("") }
    var matchCursor by remember { mutableStateOf(0) }
    val normalizedQuery = query.trim()
    val matches = remember(nodes, graph.relations, normalizedQuery) {
        if (normalizedQuery.isEmpty()) emptyList() else {
            val relatedIds = graph.relations.asSequence().filter {
                it.predicate.contains(normalizedQuery, ignoreCase = true) ||
                    it.evidence.contains(normalizedQuery, ignoreCase = true)
            }.flatMap { sequenceOf(it.source.id, it.target.id) }.toSet()
            nodes.filter {
                it.entity.name.contains(normalizedQuery, ignoreCase = true) ||
                    it.entity.type.contains(normalizedQuery, ignoreCase = true) || it.entity.id in relatedIds
            }
        }
    }
    val matchIds = remember(matches) { matches.mapTo(hashSetOf()) { it.entity.id } }
    val latestNodes by rememberUpdatedState(nodes)
    val latestZoom by rememberUpdatedState(zoomScale)
    val latestOffset by rememberUpdatedState(offset)
    val transformState = rememberTransformableState { zoom, pan, _ ->
        zoomScale = (zoomScale * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
        offset += pan
    }
    val titlePaint = remember(density) { Paint().apply {
        color = android.graphics.Color.WHITE; textSize = 15f * density; isAntiAlias = true
    } }
    val metaPaint = remember(density) { Paint().apply {
        color = android.graphics.Color.LTGRAY; textSize = 10f * density; isAntiAlias = true
    } }

    fun focus(node: GraphNodePosition) {
        if (canvasSize == IntSize.Zero) return
        offset = Offset(canvasSize.width / 2f, canvasSize.height / 2f) - node.center * zoomScale
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(100); matchCursor = 0 },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("搜索人物、项目、偏好或事实") },
            supportingText = { if (normalizedQuery.isNotEmpty()) Text("匹配 ${matches.size} 个节点") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { zoomScale = (zoomScale / 1.25f).coerceAtLeast(MIN_ZOOM) }) { Text("缩小") }
            Button(onClick = { zoomScale = (zoomScale * 1.25f).coerceAtMost(MAX_ZOOM) }) { Text("放大") }
            Button(onClick = {
                if (matches.isNotEmpty()) {
                    focus(matches[matchCursor % matches.size])
                    matchCursor++
                }
            }, enabled = matches.isNotEmpty()) { Text("下一个") }
            TextButton(onClick = { zoomScale = 0.85f; offset = Offset(32f, 32f) }) { Text("复位") }
        }
        Canvas(
            Modifier.fillMaxWidth().height(520.dp)
                .background(Color(0xFF0D171A), RoundedCornerShape(20.dp))
                .onSizeChanged { canvasSize = it }
                .semantics { contentDescription = "可缩放知识图谱，包含 ${nodes.size} 个实体，匹配 ${matches.size} 个" }
                .transformable(transformState)
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val world = tap.toWorld(latestOffset, latestZoom)
                        selected = latestNodes.lastOrNull { it.contains(world) }?.entity
                    }
                }
                .pointerInput(Unit) {
                    var draggedId: String? = null
                    var draggedPosition = Offset.Zero
                    detectDragGesturesAfterLongPress(
                        onDragStart = { start ->
                            val node = latestNodes.lastOrNull {
                                it.contains(start.toWorld(latestOffset, latestZoom))
                            }
                            draggedId = node?.entity?.id
                            draggedPosition = node?.let { Offset(it.x, it.y) } ?: Offset.Zero
                        },
                        onDrag = { change, dragAmount ->
                            val id = draggedId ?: return@detectDragGesturesAfterLongPress
                            change.consume()
                            draggedPosition += dragAmount / latestZoom
                            localPositions = localPositions + (id to draggedPosition)
                        },
                        onDragEnd = {
                            draggedId?.let { onMoveEntity(it, draggedPosition.x / density, draggedPosition.y / density) }
                            draggedId = null
                        },
                        onDragCancel = { draggedId = null },
                    )
                },
        ) {
            val left = -offset.x / zoomScale
            val top = -offset.y / zoomScale
            val right = (size.width - offset.x) / zoomScale
            val bottom = (size.height - offset.y) / zoomScale
            val visibleIds = nodes.asSequence().filter { it.intersects(left, top, right, bottom) }
                .mapTo(hashSetOf()) { it.entity.id }
            withTransform({ translate(offset.x, offset.y); scale(zoomScale, zoomScale, Offset.Zero) }) {
                graph.relations.forEach { relation ->
                    val sourceNode = nodeById[relation.source.id] ?: return@forEach
                    val targetNode = nodeById[relation.target.id] ?: return@forEach
                    if (sourceNode.entity.id !in visibleIds && targetNode.entity.id !in visibleIds &&
                        !lineMayIntersectViewport(sourceNode.center, targetNode.center, left, top, right, bottom)) return@forEach
                    val source = sourceNode.center
                    val target = targetNode.center
                    drawLine(Color(0xFF55716C), source, target, strokeWidth = 2f / zoomScale)
                    drawContext.canvas.nativeCanvas.drawText(relation.predicate.take(24),
                        (source.x + target.x) / 2, (source.y + target.y) / 2 - 6f, metaPaint)
                }
                nodes.filter { it.entity.id in visibleIds }.forEach { node ->
                    drawRoundRect(typeColor(node.entity.type), Offset(node.x, node.y),
                        Size(node.width, node.height), CornerRadius(18f, 18f))
                    if (node.entity.id in matchIds) {
                        drawRoundRect(Color(0xFFFFD166), Offset(node.x, node.y),
                            Size(node.width, node.height), CornerRadius(18f, 18f),
                            style = Stroke(4f / zoomScale))
                    }
                    drawContext.canvas.nativeCanvas.drawText(node.entity.name.take(24),
                        node.x + 16f, node.y + node.height / 2, titlePaint)
                    drawContext.canvas.nativeCanvas.drawText(node.entity.type,
                        node.x + 16f, node.y + node.height - 12f, metaPaint)
                }
            }
        }
        Text("双指缩放、单指拖动画布；长按节点后拖动可永久保存位置，点击节点可修改。",
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
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it.take(100) }, label = { Text("名称") })
            OutlinedTextField(type, { type = it.take(30) }, label = { Text("类型") },
                supportingText = { Text(KNOWLEDGE_ENTITY_TYPES.joinToString()) })
        } },
        confirmButton = { TextButton(onClick = { onSave(name.trim(), type.uppercase()) }, enabled = valid) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun Offset.toWorld(offset: Offset, zoom: Float) = Offset((x - offset.x) / zoom, (y - offset.y) / zoom)

// ponytail: Bounding-box culling may draw a few off-screen edges; use exact segment clipping only if profiling requires it.
internal fun lineMayIntersectViewport(a: Offset, b: Offset, left: Float, top: Float, right: Float, bottom: Float) =
    maxOf(a.x, b.x) >= left && minOf(a.x, b.x) <= right &&
        maxOf(a.y, b.y) >= top && minOf(a.y, b.y) <= bottom

private fun typeColor(type: String) = when (type) {
    "PERSON" -> Color(0xFF285F56)
    "ORGANIZATION" -> Color(0xFF375A83)
    "PROJECT" -> Color(0xFF66522E)
    "PLACE" -> Color(0xFF4D5E36)
    "PREFERENCE" -> Color(0xFF704A66)
    else -> Color(0xFF4C5558)
}

private const val MIN_ZOOM = 0.35f
private const val MAX_ZOOM = 4f
