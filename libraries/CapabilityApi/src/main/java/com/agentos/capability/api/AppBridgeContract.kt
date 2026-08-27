package com.agentos.capability.api

object AppBridgeContract {
    const val SERVICE_PACKAGE = "com.agentos.capability"
    const val SERVICE_CLASS = "com.agentos.capability.service.AgentAppBridgeService"

    const val STATUS_SUCCESS = 1
    const val STATUS_APPROVAL_REQUIRED = 2
    const val STATUS_DENIED = 3
    const val STATUS_FAILED = 4
    const val STATUS_QUEUED = 5

    const val ACTION_CLICK = 1
    const val ACTION_SCROLL_FORWARD = 2
    const val ACTION_SCROLL_BACKWARD = 3
    const val ACTION_SET_TEXT = 4
}
