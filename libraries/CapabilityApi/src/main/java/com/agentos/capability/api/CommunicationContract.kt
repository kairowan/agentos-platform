package com.agentos.capability.api

object CommunicationContract {
    const val PACKAGE = "com.agentos.capability"
    const val SERVICE = "com.agentos.capability.service.AgentCommunicationService"
    const val ACTIVITY = "com.agentos.capability.service.CommunicationActivity"
    const val REVIEW = "com.agentos.communication.REVIEW"
    const val CALL = 1
    const val SMS = 2
    const val ANSWER = 10
    const val REJECT = 11
    const val HANG_UP = 12
    const val READY = 1
    const val DENIED = 2
    const val SUBMITTED = 3
}
