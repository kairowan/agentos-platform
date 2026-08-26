package com.agentos.media

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Rect
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.MeteringRectangle
import android.media.ImageReader
import android.media.MediaRecorder
import android.net.Uri
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteCallbackList
import android.provider.MediaStore
import android.util.Size
import android.view.Surface
import com.agentos.capability.api.IAgentMediaListener
import com.agentos.capability.api.IAgentMediaService
import com.agentos.capability.api.MediaContract
import com.agentos.capability.api.MediaEvent
import com.agentos.capability.api.MediaItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

class AgentMediaService : Service() {
    private val callerPolicy = MediaCallerPolicy(ALLOWED_CALLER_PACKAGE)
    private val listeners = RemoteCallbackList<IAgentMediaListener>()
    private val cameraManager by lazy { getSystemService(CameraManager::class.java) }
    private val cameraThread = HandlerThread("AgentMediaCamera").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewSurface: Surface? = null
    private var previewRequest: CaptureRequest.Builder? = null
    private var imageReader: ImageReader? = null
    private var cameraId: String? = null
    private var lensFacing = MediaContract.LENS_BACK
    private var sensorOrientation = 0
    private var displayRotation = Surface.ROTATION_0
    private var cameraGeneration = 0
    private var maximumZoom = 1f
    private var currentZoom = 1f
    private var activeArray: Rect? = null
    private var maximumAfRegions = 0
    private var maximumAeRegions = 0
    private var videoSize = Size(1920, 1080)

    private var recorder: MediaRecorder? = null
    private var recorderFile: ParcelFileDescriptor? = null
    private var pendingPhotoUri: Uri? = null
    private var pendingVideoUri: Uri? = null
    private var pendingAudioUri: Uri? = null
    private var recordingStartedAt = 0L
    private var isVideoRecording = false
    private var isAudioRecording = false

    private val binder = object : IAgentMediaService.Stub() {
        override fun registerListener(listener: IAgentMediaListener) {
            enforceAuthorizedCaller(); listeners.register(listener)
        }
        override fun unregisterListener(listener: IAgentMediaListener) {
            enforceAuthorizedCaller(); listeners.unregister(listener)
        }
        override fun openCamera(surface: Surface, width: Int, height: Int, lens: Int, rotation: Int) {
            enforceAuthorizedCaller()
            require(width in 1..MAX_SURFACE_SIZE && height in 1..MAX_SURFACE_SIZE && surface.isValid)
            require(rotation in Surface.ROTATION_0..Surface.ROTATION_270)
            cameraHandler.post { openCameraInternal(surface, lens, rotation) }
        }
        override fun closeCamera() { enforceAuthorizedCaller(); cameraHandler.post(::closeCameraInternal) }
        override fun setZoom(ratio: Float) {
            enforceAuthorizedCaller(); cameraHandler.post { updateZoom(ratio) }
        }
        override fun focus(normalizedX: Float, normalizedY: Float) {
            enforceAuthorizedCaller()
            require(normalizedX.isFinite() && normalizedY.isFinite())
            require(normalizedX in 0f..1f && normalizedY in 0f..1f)
            cameraHandler.post { focusInternal(normalizedX, normalizedY) }
        }
        override fun capturePhoto() { enforceAuthorizedCaller(); cameraHandler.post(::capturePhotoInternal) }
        override fun startVideo(withAudio: Boolean) {
            enforceAuthorizedCaller(); cameraHandler.post { startVideoInternal(withAudio) }
        }
        override fun stopVideo() { enforceAuthorizedCaller(); cameraHandler.post(::stopVideoInternal) }
        override fun startAudioRecording() { enforceAuthorizedCaller(); cameraHandler.post(::startAudioInternal) }
        override fun pauseAudioRecording() { enforceAuthorizedCaller(); cameraHandler.post(::pauseAudioInternal) }
        override fun resumeAudioRecording() { enforceAuthorizedCaller(); cameraHandler.post(::resumeAudioInternal) }
        override fun stopAudioRecording() { enforceAuthorizedCaller(); cameraHandler.post(::stopAudioInternal) }
        override fun getAudioAmplitude(): Int {
            enforceAuthorizedCaller()
            return if (isAudioRecording) runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0) else 0
        }
        override fun queryRecentMedia(limit: Int): MutableList<MediaItem> {
            enforceAuthorizedCaller()
            return queryMedia(limit.coerceIn(1, MAX_QUERY_ITEMS)).toMutableList()
        }
    }

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "AgentOS 媒体", NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        cameraHandler.post {
            if (isVideoRecording) stopVideoInternal()
            if (isAudioRecording) stopAudioInternal()
            closeCameraInternal()
        }
        cameraThread.quitSafely()
        listeners.kill()
        super.onDestroy()
    }

    @SuppressLint("MissingPermission")
    private fun openCameraInternal(surface: Surface, requestedLens: Int, rotation: Int) {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            emitError("相机权限未授予"); return
        }
        if (isAudioRecording) { emitError("录音期间不能打开相机"); return }
        closeCameraResources()
        val generation = cameraGeneration
        val facing = if (requestedLens == MediaContract.LENS_FRONT) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else CameraCharacteristics.LENS_FACING_BACK
        val selected = cameraManager.cameraIdList.firstOrNull {
            cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == facing
        } ?: cameraManager.cameraIdList.firstOrNull()
        if (selected == null) { emitError("设备没有可用摄像头"); return }

        val characteristics = cameraManager.getCameraCharacteristics(selected)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val photoSize = map?.getOutputSizes(ImageFormat.JPEG)?.filter { it.width * it.height <= MAX_PHOTO_PIXELS }
            ?.maxByOrNull { it.width * it.height }
            ?: map?.getOutputSizes(ImageFormat.JPEG)?.maxByOrNull { it.width * it.height }
            ?: Size(1920, 1080)
        videoSize = map?.getOutputSizes(MediaRecorder::class.java)?.filter {
            it.width <= 1920 && it.height <= 1080
        }?.maxByOrNull { it.width * it.height } ?: Size(1280, 720)
        sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        activeArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        maximumAfRegions = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0
        maximumAeRegions = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) ?: 0
        displayRotation = rotation
        maximumZoom = (if (android.os.Build.VERSION.SDK_INT >= 30) {
            characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.upper
        } else null) ?: (characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f)
        currentZoom = 1f
        lensFacing = requestedLens
        cameraId = selected
        previewSurface = surface
        imageReader = ImageReader.newInstance(photoSize.width, photoSize.height, ImageFormat.JPEG, 2).apply {
            setOnImageAvailableListener({ reader -> savePendingPhoto(reader) }, cameraHandler)
        }
        startForegroundFor(ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA, "相机正在使用")
        try {
            cameraManager.openCamera(selected, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    if (generation != cameraGeneration) { camera.close(); return }
                    cameraDevice = camera
                    createPreviewSession()
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close(); cameraDevice = null; emitError("摄像头已断开")
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close(); cameraDevice = null; emitError("摄像头错误：$error")
                }
            }, cameraHandler)
        } catch (error: Exception) {
            emitError("无法打开摄像头：${error.message.orEmpty()}")
        }
    }

    private fun createPreviewSession() {
        val camera = cameraDevice ?: return
        val preview = previewSurface?.takeIf(Surface::isValid) ?: return
        val readerSurface = imageReader?.surface ?: return
        try {
            val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(preview)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }
            previewRequest = builder
            camera.createCaptureSession(listOf(preview, readerSurface), sessionCallback({ session ->
                captureSession = session
                updateZoom(currentZoom)
                emit(MediaEvent(MediaContract.CAMERA_READY, "相机已就绪"))
            }), cameraHandler)
        } catch (error: Exception) { emitError("无法创建相机预览：${error.message.orEmpty()}") }
    }

    private fun capturePhotoInternal() {
        val camera = cameraDevice ?: return emitError("相机尚未就绪")
        val session = captureSession ?: return emitError("相机预览尚未就绪")
        val target = imageReader?.surface ?: return emitError("照片输出不可用")
        if (isVideoRecording || pendingPhotoUri != null) return emitError("当前拍摄尚未结束")
        val uri = createPendingUri(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "IMG", "jpg", "image/jpeg", "Pictures/AgentOS")
            ?: return emitError("无法创建照片文件")
        pendingPhotoUri = uri
        try {
            val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(target)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.JPEG_ORIENTATION, outputOrientation())
                applyZoom(this, currentZoom)
            }.build()
            session.capture(request, object : CameraCaptureSession.CaptureCallback() {}, cameraHandler)
        } catch (error: Exception) {
            pendingPhotoUri = null; contentResolver.delete(uri, null, null)
            emitError("拍照失败：${error.message.orEmpty()}")
        }
    }

    private fun savePendingPhoto(reader: ImageReader) {
        val image = reader.acquireLatestImage() ?: return
        val uri = pendingPhotoUri
        try {
            if (uri == null) return
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining()).also(buffer::get)
            contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
                ?: error("输出流不可用")
            publish(uri)
            grantUriPermission(ALLOWED_CALLER_PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            emit(MediaEvent(MediaContract.PHOTO_SAVED, "照片已保存", uri.toString()))
        } catch (error: Exception) {
            uri?.let { contentResolver.delete(it, null, null) }
            emitError("保存照片失败：${error.message.orEmpty()}")
        } finally {
            pendingPhotoUri = null
            image.close()
        }
    }

    private fun startVideoInternal(withAudio: Boolean) {
        val camera = cameraDevice ?: return emitError("相机尚未就绪")
        val preview = previewSurface?.takeIf(Surface::isValid) ?: return emitError("预览不可用")
        if (isVideoRecording) return
        if (withAudio && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return emitError("麦克风权限未授予")
        }
        val uri = createPendingUri(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "VID", "mp4", "video/mp4", "Movies/AgentOS")
            ?: return emitError("无法创建视频文件")
        val file = contentResolver.openFileDescriptor(uri, "w")
            ?: return contentResolver.delete(uri, null, null).let { emitError("视频输出不可用") }
        val mediaRecorder = MediaRecorder()
        try {
            mediaRecorder.apply {
                if (withAudio) setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoEncodingBitRate(12_000_000)
                setVideoFrameRate(30)
                setVideoSize(videoSize.width, videoSize.height)
                if (withAudio) {
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(128_000)
                    setAudioSamplingRate(48_000)
                }
                setOrientationHint(outputOrientation())
                setOutputFile(file.fileDescriptor)
                prepare()
            }
        } catch (error: Exception) {
            runCatching { mediaRecorder.release() }
            file.close(); contentResolver.delete(uri, null, null)
            emitError("无法准备录像：${error.message.orEmpty()}")
            return
        }
        try {
            captureSession?.close()
            recorder = mediaRecorder
            recorderFile = file
            pendingVideoUri = uri
            val recordSurface = mediaRecorder.surface
            val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(preview); addTarget(recordSurface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                applyZoom(this, currentZoom)
            }
            camera.createCaptureSession(listOf(preview, recordSurface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    runCatching {
                        session.setRepeatingRequest(builder.build(), null, cameraHandler)
                        mediaRecorder.start()
                    }.onSuccess {
                        isVideoRecording = true
                        recordingStartedAt = System.currentTimeMillis()
                        startForegroundFor(ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                            (if (withAudio) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0), "正在录像")
                        emit(MediaEvent(MediaContract.VIDEO_STARTED, "正在录像"))
                    }.onFailure { failVideoStart(mediaRecorder, file, uri, it) }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    failVideoStart(mediaRecorder, file, uri, IllegalStateException("相机会话配置失败"))
                }
            }, cameraHandler)
        } catch (error: Exception) {
            failVideoStart(mediaRecorder, file, uri, error)
        }
    }

    private fun failVideoStart(
        mediaRecorder: MediaRecorder,
        file: ParcelFileDescriptor,
        uri: Uri,
        error: Throwable,
    ) {
        captureSession?.close(); captureSession = null
        runCatching { mediaRecorder.reset() }; runCatching { mediaRecorder.release() }
        runCatching { file.close() }; contentResolver.delete(uri, null, null)
        recorder = null; recorderFile = null; pendingVideoUri = null; isVideoRecording = false
        createPreviewSession()
        emitError("无法开始录像：${error.message.orEmpty()}")
    }

    private fun stopVideoInternal() {
        if (!isVideoRecording) return
        val uri = pendingVideoUri
        val duration = System.currentTimeMillis() - recordingStartedAt
        val success = runCatching { recorder?.stop() }.isSuccess
        recorder?.reset(); recorder?.release(); recorder = null
        recorderFile?.close(); recorderFile = null
        isVideoRecording = false; pendingVideoUri = null
        if (success && uri != null) {
            publish(uri); grantUriPermission(ALLOWED_CALLER_PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            emit(MediaEvent(MediaContract.VIDEO_SAVED, "视频已保存", uri.toString(), duration))
        } else {
            uri?.let { contentResolver.delete(it, null, null) }; emitError("录像时间过短或保存失败")
        }
        startForegroundFor(ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA, "相机正在使用")
        createPreviewSession()
    }

    private fun startAudioInternal() {
        if (isAudioRecording) return
        if (cameraDevice != null) return emitError("请先退出相机")
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return emitError("麦克风权限未授予")
        }
        val uri = createPendingUri(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "REC", "m4a", "audio/mp4", "Music/AgentOS")
            ?: return emitError("无法创建录音文件")
        val file = contentResolver.openFileDescriptor(uri, "w")
            ?: return contentResolver.delete(uri, null, null).let { emitError("录音输出不可用") }
        try {
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(48_000)
                setOutputFile(file.fileDescriptor)
                prepare(); start()
            }
            recorderFile = file; pendingAudioUri = uri; isAudioRecording = true
            recordingStartedAt = System.currentTimeMillis()
            startForegroundFor(ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE, "正在录音")
            emit(MediaEvent(MediaContract.AUDIO_STARTED, "正在录音"))
        } catch (error: Exception) {
            runCatching { recorder?.release() }; recorder = null; file.close(); contentResolver.delete(uri, null, null)
            emitError("无法开始录音：${error.message.orEmpty()}")
        }
    }

    private fun pauseAudioInternal() {
        if (!isAudioRecording) return
        runCatching { recorder?.pause() }.onSuccess {
            emit(MediaEvent(MediaContract.AUDIO_PAUSED, "录音已暂停"))
        }.onFailure { emitError("暂停录音失败") }
    }

    private fun resumeAudioInternal() {
        if (!isAudioRecording) return
        runCatching { recorder?.resume() }.onSuccess {
            emit(MediaEvent(MediaContract.AUDIO_RESUMED, "继续录音"))
        }.onFailure { emitError("继续录音失败") }
    }

    private fun stopAudioInternal() {
        if (!isAudioRecording) return
        val uri = pendingAudioUri
        val duration = System.currentTimeMillis() - recordingStartedAt
        val success = runCatching { recorder?.stop() }.isSuccess
        recorder?.reset(); recorder?.release(); recorder = null
        recorderFile?.close(); recorderFile = null
        isAudioRecording = false; pendingAudioUri = null
        if (success && uri != null) {
            publish(uri); grantUriPermission(ALLOWED_CALLER_PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            emit(MediaEvent(MediaContract.AUDIO_SAVED, "录音已保存", uri.toString(), duration))
        } else {
            uri?.let { contentResolver.delete(it, null, null) }; emitError("录音时间过短或保存失败")
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun updateZoom(requested: Float) {
        currentZoom = requested.coerceIn(1f, maximumZoom.coerceAtLeast(1f))
        val builder = previewRequest ?: return
        applyZoom(builder, currentZoom)
        runCatching { captureSession?.setRepeatingRequest(builder.build(), null, cameraHandler) }
    }

    private fun applyZoom(builder: CaptureRequest.Builder, zoom: Float) {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoom)
        } else {
            cropRegion(zoom)?.let { builder.set(CaptureRequest.SCALER_CROP_REGION, it) }
        }
    }

    private fun cropRegion(zoom: Float): Rect? {
        val sensor = activeArray ?: return null
        val bounds = centeredCrop(sensor.left, sensor.top, sensor.right, sensor.bottom, zoom)
        return Rect(bounds[0], bounds[1], bounds[2], bounds[3])
    }

    private fun focusInternal(normalizedX: Float, normalizedY: Float) {
        val session = captureSession ?: return
        val builder = previewRequest ?: return
        val sensor = cropRegion(currentZoom) ?: return
        // ponytail: preview coordinates ignore crop/rotation distortion; replace with a calibrated
        // sensor transform if target-device focus metrics show drift.
        val x = (sensor.left + normalizedX * sensor.width()).toInt()
        val y = (sensor.top + normalizedY * sensor.height()).toInt()
        val halfWidth = (sensor.width() / 20).coerceAtLeast(1)
        val halfHeight = (sensor.height() / 20).coerceAtLeast(1)
        val region = MeteringRectangle(
            Rect(
                (x - halfWidth).coerceAtLeast(sensor.left),
                (y - halfHeight).coerceAtLeast(sensor.top),
                (x + halfWidth).coerceAtMost(sensor.right),
                (y + halfHeight).coerceAtMost(sensor.bottom),
            ),
            MeteringRectangle.METERING_WEIGHT_MAX,
        )
        if (maximumAfRegions > 0) builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(region))
        if (maximumAeRegions > 0) builder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(region))
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
        runCatching { session.capture(builder.build(), null, cameraHandler) }
            .onSuccess {
                builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                runCatching { session.setRepeatingRequest(builder.build(), null, cameraHandler) }
            }
    }

    private fun closeCameraInternal() {
        if (isVideoRecording) stopVideoInternal()
        closeCameraResources()
        stopForeground(STOP_FOREGROUND_REMOVE)
        emit(MediaEvent(MediaContract.IDLE, "相机已关闭"))
    }

    private fun closeCameraResources() {
        cameraGeneration++
        captureSession?.close(); captureSession = null
        cameraDevice?.close(); cameraDevice = null
        imageReader?.close(); imageReader = null
        previewRequest = null; previewSurface = null; cameraId = null
        activeArray = null; maximumAfRegions = 0; maximumAeRegions = 0
        pendingPhotoUri?.let { contentResolver.delete(it, null, null) }; pendingPhotoUri = null
    }

    private fun outputOrientation(): Int {
        val degrees = when (displayRotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        return if (lensFacing == MediaContract.LENS_FRONT) {
            (sensorOrientation + degrees) % 360
        } else (sensorOrientation - degrees + 360) % 360
    }

    private fun sessionCallback(onConfigured: (CameraCaptureSession) -> Unit) =
        object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) = onConfigured(session)
            override fun onConfigureFailed(session: CameraCaptureSession) = emitError("相机会话配置失败")
        }

    private fun createPendingUri(collection: Uri, prefix: String, extension: String, mime: String, path: String): Uri? {
        val name = "$prefix-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.$extension"
        return contentResolver.insert(collection, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, path)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        })
    }

    private fun publish(uri: Uri) {
        contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }

    private fun queryMedia(limit: Int): List<MediaItem> = buildList {
        queryCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, limit, false)
        queryCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, limit, true)
        queryCollection(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, limit, true)
    }.sortedByDescending(MediaItem::createdAtMillis).take(limit).onEach {
        runCatching { grantUriPermission(ALLOWED_CALLER_PACKAGE, Uri.parse(it.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    private fun MutableList<MediaItem>.queryCollection(collection: Uri, limit: Int, hasDuration: Boolean) {
        val columns = buildList {
            add(MediaStore.MediaColumns._ID); add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE); add(MediaStore.MediaColumns.DATE_ADDED)
            if (hasDuration) add(MediaStore.MediaColumns.DURATION)
        }.toTypedArray()
        contentResolver.query(collection, columns, "${MediaStore.MediaColumns.IS_PENDING}=0", null,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val durationIndex = if (hasDuration) cursor.getColumnIndex(MediaStore.MediaColumns.DURATION) else -1
            while (cursor.moveToNext() && size < limit * MEDIA_TYPES) {
                add(MediaItem(ContentUris.withAppendedId(collection, cursor.getLong(idIndex)).toString(),
                    cursor.getString(nameIndex).orEmpty(), cursor.getString(mimeIndex).orEmpty(),
                    cursor.getLong(dateIndex) * 1_000L,
                    if (durationIndex >= 0) cursor.getLong(durationIndex) else 0L))
            }
        }
    }

    private fun startForegroundFor(type: Int, label: String) {
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("AgentOS Media")
            .setContentText(label)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification, type)
    }

    private fun emitError(message: String) = emit(MediaEvent(MediaContract.ERROR, message.take(MAX_EVENT_TEXT)))

    private fun emit(event: MediaEvent) {
        val count = listeners.beginBroadcast()
        try { for (index in 0 until count) runCatching { listeners.getBroadcastItem(index).onMediaEvent(event) } }
        finally { listeners.finishBroadcast() }
    }

    private fun enforceAuthorizedCaller() {
        val packages = packageManager.getPackagesForUid(Binder.getCallingUid())?.toList().orEmpty()
        val signatureMatches = packages.singleOrNull()?.let {
            packageManager.checkSignatures(it, packageName) == PackageManager.SIGNATURE_MATCH
        } == true
        if (!callerPolicy.isAuthorized(packages, signatureMatches)) {
            throw SecurityException("Caller is not authorized to use AgentOS Media")
        }
    }

    companion object {
        private const val ALLOWED_CALLER_PACKAGE = "com.agentos.shell"
        private const val CHANNEL_ID = "agentos_media_capture"
        private const val NOTIFICATION_ID = 2201
        private const val MAX_SURFACE_SIZE = 16_384
        private const val MAX_QUERY_ITEMS = 500
        private const val MAX_PHOTO_PIXELS = 12_000_000
        private const val MAX_EVENT_TEXT = 300
        private const val MEDIA_TYPES = 3
    }
}

internal class MediaCallerPolicy(private val allowedPackage: String) {
    fun isAuthorized(packages: List<String>, signatureMatches: Boolean) =
        signatureMatches && packages.singleOrNull() == allowedPackage
}

internal fun centeredCrop(left: Int, top: Int, right: Int, bottom: Int, zoom: Float): IntArray {
    val width = ((right - left) / zoom).toInt().coerceAtLeast(1)
    val height = ((bottom - top) / zoom).toInt().coerceAtLeast(1)
    val cropLeft = (left + right) / 2 - width / 2
    val cropTop = (top + bottom) / 2 - height / 2
    return intArrayOf(cropLeft, cropTop, cropLeft + width, cropTop + height)
}
