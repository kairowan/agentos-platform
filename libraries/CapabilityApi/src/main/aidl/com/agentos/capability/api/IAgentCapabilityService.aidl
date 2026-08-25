package com.agentos.capability.api;

import com.agentos.capability.api.CapabilityReply;
import com.agentos.capability.api.IAgentEventListener;

interface IAgentCapabilityService {
    CapabilityReply requestCapability(String capabilityId);
    CapabilityReply approve(String token);
    CapabilityReply deny(String token);
    void registerEventListener(IAgentEventListener listener);
    void unregisterEventListener(IAgentEventListener listener);
}
