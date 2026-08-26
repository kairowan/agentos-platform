package com.agentos.shell

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.view.Surface
import com.agentos.capability.api.IAgentMediaListener
import com.agentos.capability.api.IAgentMediaService
import com.agentos.capability.api.MediaContract
import com.agentos.capability.api.MediaEvent
import com.agentos.capability.api.MediaItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal class AgentMediaClient(context: Context) : AutoCloseable {
    private val applicationContext = context.applicationContext
    private val remote = CompletableDeferred<IAgentMediaService>()
    private val mutableEvents = MutableSharedFlow<MediaEvent>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: Flow<MediaEvent> = mutableEvents
    @Volatile private var connected: IAgentMediaService? = null

    private val listener = object : IAgentMediaListener.Stub() {
        override fun onMediaEvent(event: MediaEvent) { mutableEvents.tryEmit(event) }
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val service = IAgentMediaService.Stub.asInterface(binder)
            runCatching { service.registerListener(listener) }
                .onSuccess { connected = service; remote.complete(service) }
                .onFailure(remote::completeExceptionally)
        }
        override fun onServiceDisconnected(name: ComponentName) { connected = null }
    }
    private val isBound = applicationContext.bindService(
        Intent().setComponent(ComponentName(MediaContract.SERVICE_PACKAGE, MediaContract.SERVICE_CLASS)),
        connection,
        Context.BIND_AUTO_CREATE,
    ).also { if (!it) remote.completeExceptionally(IllegalStateException("Media Service unavailable")) }

    suspend fun openCamera(surface: Surface, width: Int, height: Int, lens: Int) =
        call { it.openCamera(surface, width, height, lens) }
    suspend fun closeCamera() = call { it.closeCamera() }
    suspend fun setZoom(ratio: Float) = call { it.setZoom(ratio) }
    suspend fun capturePhoto() = call { it.capturePhoto() }
    suspend fun startVideo(withAudio: Boolean = true) = call { it.startVideo(withAudio) }
    suspend fun stopVideo() = call { it.stopVideo() }
    suspend fun startAudio() = call { it.startAudioRecording() }
    suspend fun pauseAudio() = call { it.pauseAudioRecording() }
    suspend fun resumeAudio() = call { it.resumeAudioRecording() }
    suspend fun stopAudio() = call { it.stopAudioRecording() }
    suspend fun amplitude(): Int = withService { it.audioAmplitude }
    suspend fun recentMedia(limit: Int = 200): List<MediaItem> = withService { it.queryRecentMedia(limit) }

    private suspend fun call(block: (IAgentMediaService) -> Unit) = withService { service -> block(service) }
    private suspend fun <T> withService(block: (IAgentMediaService) -> T): T = withContext(Dispatchers.IO) {
        block(withTimeout(CONNECT_TIMEOUT_MILLIS) { remote.await() })
    }

    override fun close() {
        connected?.let { runCatching { it.unregisterListener(listener) } }
        if (isBound) runCatching { applicationContext.unbindService(connection) }
    }

    private companion object { const val CONNECT_TIMEOUT_MILLIS = 5_000L }
}
