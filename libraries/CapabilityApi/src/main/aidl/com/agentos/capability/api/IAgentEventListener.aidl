package com.agentos.capability.api;

import com.agentos.capability.api.AgentNotificationEvent;

oneway interface IAgentEventListener {
    void onNotificationEvent(in AgentNotificationEvent event);
}
