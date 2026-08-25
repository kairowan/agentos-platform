package com.agentos.capability.api;

import com.agentos.capability.api.CapabilityReply;

interface IAgentCapabilityService {
    CapabilityReply requestCapability(String capabilityId);
    CapabilityReply approve(String token);
    CapabilityReply deny(String token);
}
