package com.agentos.capability.api

object CapabilityContract {
    const val SERVICE_PACKAGE = "com.agentos.capability"
    const val SERVICE_CLASS = "com.agentos.capability.service.AgentCapabilityService"
    const val USE_BROKER_PERMISSION = "com.agentos.permission.USE_CAPABILITY_BROKER"

    const val STATUS_SUCCESS = 1
    const val STATUS_APPROVAL_REQUIRED = 2
    const val STATUS_DENIED = 3
    const val STATUS_FAILED = 4
}
