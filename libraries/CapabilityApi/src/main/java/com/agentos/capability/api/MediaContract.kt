package com.agentos.capability.api

object MediaContract {
    const val SERVICE_PACKAGE = "com.agentos.media"
    const val SERVICE_CLASS = "com.agentos.media.AgentMediaService"
    const val USE_MEDIA_PERMISSION = "com.agentos.permission.USE_MEDIA_SERVICE"

    const val LENS_BACK = 0
    const val LENS_FRONT = 1

    const val CAMERA_READY = 1
    const val PHOTO_SAVED = 2
    const val VIDEO_STARTED = 3
    const val VIDEO_SAVED = 4
    const val AUDIO_STARTED = 5
    const val AUDIO_PAUSED = 6
    const val AUDIO_RESUMED = 7
    const val AUDIO_SAVED = 8
    const val IDLE = 9
    const val ERROR = 10
}
