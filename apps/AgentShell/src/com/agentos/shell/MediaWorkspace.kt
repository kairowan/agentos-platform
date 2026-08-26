package com.agentos.shell

import android.graphics.Bitmap
import android.net.Uri
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.agentos.capability.api.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

@Composable
internal fun MediaWorkspace(
    state: MediaWorkspaceState,
    onClose: () -> Unit,
    onAttachSurface: (android.view.Surface, Int, Int) -> Unit,
    onDetachSurface: () -> Unit,
    onSwitchCamera: () -> Unit,
    onZoom: (Float) -> Unit,
    onCapturePhoto: () -> Unit,
    onToggleVideo: () -> Unit,
    onToggleAudio: () -> Unit,
    onToggleAudioPause: () -> Unit,
    onRefreshGallery: () -> Unit,
    onOpenItem: (MediaItem) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("返回") }
            Text(when (state.mode) {
                MediaWorkspaceMode.CAMERA -> "AgentOS 相机"
                MediaWorkspaceMode.GALLERY -> "AgentOS 图库"
                MediaWorkspaceMode.RECORDER -> "AgentOS 录音机"
                else -> "媒体工作区"
            }, style = MaterialTheme.typography.titleLarge)
            if (state.mode == MediaWorkspaceMode.GALLERY) {
                TextButton(onClick = onRefreshGallery) { Text("刷新") }
            } else TextButton(onClick = {}) { Text("") }
        }
        when (state.mode) {
            MediaWorkspaceMode.CAMERA -> CameraWorkspace(state, onAttachSurface, onDetachSurface,
                onSwitchCamera, onZoom, onCapturePhoto, onToggleVideo)
            MediaWorkspaceMode.GALLERY -> GalleryWorkspace(state, onOpenItem)
            MediaWorkspaceMode.RECORDER -> RecorderWorkspace(state, onToggleAudio, onToggleAudioPause)
            MediaWorkspaceMode.CLOSED -> Unit
        }
        if (state.message.isNotBlank()) {
            Text(state.message, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CameraWorkspace(
    state: MediaWorkspaceState,
    onAttachSurface: (android.view.Surface, Int, Int) -> Unit,
    onDetachSurface: () -> Unit,
    onSwitchCamera: () -> Unit,
    onZoom: (Float) -> Unit,
    onCapturePhoto: () -> Unit,
    onToggleVideo: () -> Unit,
) {
    DisposableEffect(Unit) { onDispose(onDetachSurface) }
    Box(
        Modifier.fillMaxWidth().aspectRatio(3f / 4f)
            .background(Color.Black, RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { context -> SurfaceView(context).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) = Unit
                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                        if (holder.surface.isValid) onAttachSurface(holder.surface, width, height)
                    }
                    override fun surfaceDestroyed(holder: SurfaceHolder) = onDetachSurface()
                })
            } },
            modifier = Modifier.fillMaxSize(),
        )
        if (!state.cameraReady) CircularProgressIndicator()
        if (state.videoRecording) {
            Text("● REC", color = Color(0xFFFF4D4D),
                modifier = Modifier.align(Alignment.TopStart).padding(18.dp))
        }
    }
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp), Alignment.CenterVertically) {
        Text("${state.zoom.formatOne()}×")
        Slider(value = state.zoom, onValueChange = onZoom, valueRange = 1f..8f,
            modifier = Modifier.weight(1f))
        TextButton(onClick = onSwitchCamera, enabled = !state.videoRecording) { Text("切换镜头") }
    }
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
        Button(onClick = onCapturePhoto, enabled = state.cameraReady && !state.videoRecording) { Text("拍照") }
        Button(onClick = onToggleVideo, enabled = state.cameraReady) {
            Text(if (state.videoRecording) "停止录像" else "开始录像")
        }
    }
}

@Composable
private fun GalleryWorkspace(state: MediaWorkspaceState, onOpenItem: (MediaItem) -> Unit) {
    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        state.media.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("还没有照片、视频或录音")
        }
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.media, key = MediaItem::uri) { item -> MediaTile(item, onOpenItem) }
        }
    }
}

@Composable
private fun MediaTile(item: MediaItem, onOpenItem: (MediaItem) -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable { onOpenItem(item) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        if (item.mimeType.startsWith("image/") || item.mimeType.startsWith("video/")) {
            MediaThumbnail(item)
        } else {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Color(0xFF243238)),
                contentAlignment = Alignment.Center) { Text("录音", style = MaterialTheme.typography.titleLarge) }
        }
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.displayName, maxLines = 1)
            if (item.durationMillis > 0) Text(formatDuration(item.durationMillis),
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MediaThumbnail(item: MediaItem) {
    val resolver = LocalContext.current.contentResolver
    val bitmap by produceState<Bitmap?>(null, item.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { resolver.loadThumbnail(Uri.parse(item.uri), android.util.Size(320, 320), null) }.getOrNull()
        }
    }
    val image = bitmap
    if (image == null) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Color(0xFF243238)),
            contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else Image(image.asImageBitmap(), item.displayName,
        Modifier.fillMaxWidth().aspectRatio(1f), contentScale = ContentScale.Crop)
}

@Composable
private fun RecorderWorkspace(
    state: MediaWorkspaceState,
    onToggleAudio: () -> Unit,
    onTogglePause: () -> Unit,
) {
    val waveformColor = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
        Text(formatDuration(state.elapsedMillis), style = MaterialTheme.typography.displayMedium)
        Canvas(Modifier.fillMaxWidth().height(180.dp).padding(vertical = 28.dp)) {
            val values = state.amplitudes.ifEmpty { List(40) { 0.03f } }
            val step = size.width / max(values.size, 1)
            values.forEachIndexed { index, value ->
                val height = max(4f, value * size.height)
                drawLine(waveformColor,
                    Offset(index * step + step / 2, size.height / 2 - height / 2),
                    Offset(index * step + step / 2, size.height / 2 + height / 2),
                    strokeWidth = max(2f, step * 0.45f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (state.audioRecording) {
                Button(onClick = onTogglePause) { Text(if (state.audioPaused) "继续" else "暂停") }
            }
            Button(onClick = onToggleAudio) { Text(if (state.audioRecording) "停止并保存" else "开始录音") }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val seconds = millis / 1_000
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}

private fun Float.formatOne() = "%.1f".format(this)
