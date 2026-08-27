package com.agentos.capability.api;

import com.agentos.capability.api.AppBridgeReply;
import com.agentos.capability.api.AppDescriptor;
import com.agentos.capability.api.SemanticSnapshot;

interface IAgentAppBridgeService {
    List<AppDescriptor> listLaunchableApps();
    AppBridgeReply requestLaunch(String packageName);
    SemanticSnapshot getSemanticSnapshot();
    AppBridgeReply requestNodeAction(String expectedPackage, String nodePath, int action, String value);
    AppBridgeReply approve(String token);
    AppBridgeReply deny(String token);
    void cancelPending();
}
