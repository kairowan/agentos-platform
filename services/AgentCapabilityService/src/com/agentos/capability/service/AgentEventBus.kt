package com.agentos.capability.service

import android.os.RemoteCallbackList
import com.agentos.capability.api.AgentNotificationEvent
import com.agentos.capability.api.IAgentEventListener

internal object AgentEventBus {
    private val listeners = RemoteCallbackList<IAgentEventListener>()

    fun register(listener: IAgentEventListener) {
        listeners.register(listener)
    }

    fun unregister(listener: IAgentEventListener) {
        listeners.unregister(listener)
    }

    @Synchronized
    fun publish(event: AgentNotificationEvent) {
        val count = listeners.beginBroadcast()
        try {
            repeat(count) { runCatching { listeners.getBroadcastItem(it).onNotificationEvent(event) } }
        } finally {
            listeners.finishBroadcast()
        }
    }
}
