package com.agentos.shell

import android.content.Context
import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.agentos.capability.api.MediaContract
import com.agentos.capability.api.MediaItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class MediaWorkspaceMode { CLOSED, CAMERA, GALLERY, RECORDER }

internal data class MediaWorkspaceState(
    val mode: MediaWorkspaceMode = MediaWorkspaceMode.CLOSED,
    val message: String = "",
    val lensFacing: Int = MediaContract.LENS_BACK,
    val zoom: Float = 1f,
    val cameraReady: Boolean = false,
    val videoRecording: Boolean = false,
    val audioRecording: Boolean = false,
    val audioPaused: Boolean = false,
    val elapsedMillis: Long = 0,
    val amplitudes: List<Float> = emptyList(),
    val media: List<MediaItem> = emptyList(),
    val loading: Boolean = false,
)

internal class MediaWorkspaceViewModel(private val client: AgentMediaClient) : ViewModel() {
    private val mutableState = MutableStateFlow(MediaWorkspaceState())
    val state: StateFlow<MediaWorkspaceState> = mutableState.asStateFlow()
    private var surface: Surface? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var meterJob: Job? = null
    private var recordingStartedAt = 0L

    init {
        viewModelScope.launch {
            client.events.collect { event ->
                mutableState.update { current -> when (event.state) {
                    MediaContract.CAMERA_READY -> current.copy(cameraReady = true, message = event.message)
                    MediaContract.PHOTO_SAVED -> current.copy(message = event.message)
                    MediaContract.VIDEO_STARTED -> current.copy(videoRecording = true, message = event.message)
                    MediaContract.VIDEO_SAVED -> current.copy(videoRecording = false, message = event.message)
                    MediaContract.AUDIO_STARTED -> current.copy(audioRecording = true, audioPaused = false, message = event.message)
                    MediaContract.AUDIO_PAUSED -> current.copy(audioPaused = true, message = event.message)
                    MediaContract.AUDIO_RESUMED -> current.copy(audioPaused = false, message = event.message)
                    MediaContract.AUDIO_SAVED -> current.copy(audioRecording = false, audioPaused = false,
                        elapsedMillis = event.durationMillis, message = event.message)
                    MediaContract.IDLE -> current.copy(cameraReady = false, videoRecording = false, message = event.message)
                    else -> current.copy(message = event.message, videoRecording = false)
                } }
                if (event.state == MediaContract.AUDIO_STARTED) startMeter()
                if (event.state == MediaContract.AUDIO_SAVED || event.state == MediaContract.ERROR) stopMeter()
                if (event.state in setOf(MediaContract.PHOTO_SAVED, MediaContract.VIDEO_SAVED, MediaContract.AUDIO_SAVED)) {
                    refreshGallery()
                }
            }
        }
    }

    fun open(mode: MediaWorkspaceMode) {
        mutableState.update { it.copy(mode = mode, message = "") }
        if (mode == MediaWorkspaceMode.GALLERY) refreshGallery()
    }

    fun closeWorkspace() {
        val current = mutableState.value
        viewModelScope.launch {
            runCatching {
                when {
                    current.videoRecording -> client.stopVideo()
                    current.audioRecording -> client.stopAudio()
                    current.mode == MediaWorkspaceMode.CAMERA -> client.closeCamera()
                }
            }
        }
        stopMeter(); surface = null
        mutableState.value = MediaWorkspaceState()
    }

    fun attachCameraSurface(value: Surface, width: Int, height: Int) {
        surface = value; surfaceWidth = width; surfaceHeight = height
        mutableState.update { it.copy(cameraReady = false, message = "正在启动相机…") }
        viewModelScope.launch { mediaCall { client.openCamera(value, width, height, mutableState.value.lensFacing) } }
    }

    fun detachCameraSurface() {
        surface = null
        viewModelScope.launch { runCatching { client.closeCamera() } }
        mutableState.update { it.copy(cameraReady = false) }
    }

    fun switchCamera() {
        val next = if (mutableState.value.lensFacing == MediaContract.LENS_BACK) MediaContract.LENS_FRONT else MediaContract.LENS_BACK
        mutableState.update { it.copy(lensFacing = next, cameraReady = false) }
        val target = surface ?: return
        viewModelScope.launch { mediaCall { client.openCamera(target, surfaceWidth, surfaceHeight, next) } }
    }

    fun setZoom(zoom: Float) {
        mutableState.update { it.copy(zoom = zoom) }
        viewModelScope.launch { mediaCall { client.setZoom(zoom) } }
    }

    fun capturePhoto() { viewModelScope.launch { mediaCall { client.capturePhoto() } } }
    fun toggleVideo() {
        viewModelScope.launch { mediaCall {
            if (mutableState.value.videoRecording) client.stopVideo() else client.startVideo(true)
        } }
    }

    fun toggleAudio() {
        viewModelScope.launch { mediaCall {
            if (mutableState.value.audioRecording) client.stopAudio() else client.startAudio()
        } }
    }

    fun toggleAudioPause() {
        viewModelScope.launch { mediaCall {
            if (mutableState.value.audioPaused) client.resumeAudio() else client.pauseAudio()
        } }
    }

    fun refreshGallery() {
        mutableState.update { it.copy(loading = true) }
        viewModelScope.launch {
            val result = runCatching { client.recentMedia() }
            mutableState.update { it.copy(
                media = result.getOrDefault(it.media),
                loading = false,
                message = result.exceptionOrNull()?.let { "读取媒体失败" } ?: it.message,
            ) }
        }
    }

    private fun startMeter() {
        meterJob?.cancel(); recordingStartedAt = System.currentTimeMillis()
        meterJob = viewModelScope.launch {
            while (true) {
                val amplitude = runCatching { client.amplitude() }.getOrDefault(0) / 32767f
                mutableState.update { it.copy(
                    elapsedMillis = System.currentTimeMillis() - recordingStartedAt,
                    amplitudes = (it.amplitudes + amplitude.coerceIn(0f, 1f)).takeLast(80),
                ) }
                delay(100)
            }
        }
    }

    private fun stopMeter() { meterJob?.cancel(); meterJob = null }

    private suspend fun mediaCall(block: suspend () -> Unit) {
        runCatching { block() }.onFailure { error ->
            mutableState.update { it.copy(message = error.message ?: "媒体服务不可用") }
        }
    }

    override fun onCleared() { stopMeter(); client.close() }

    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == MediaWorkspaceViewModel::class.java)
                return MediaWorkspaceViewModel(AgentMediaClient(context)) as T
            }
        }
    }
}
