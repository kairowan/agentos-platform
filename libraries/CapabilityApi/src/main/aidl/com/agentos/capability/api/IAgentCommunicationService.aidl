package com.agentos.capability.api;
import com.agentos.capability.api.CommunicationRequest;
import com.agentos.capability.api.CommunicationReply;

interface IAgentCommunicationService {
    CommunicationReply prepare(in CommunicationRequest request);
    void cancelPending();
    List<String> activeCallIds();
    CommunicationReply controlCall(String callId, int action);
    // Approval is deliberately NOT exposed to model/Shell IPC. Only the trusted
    // communication activity can consume a prepared call/SMS after user review.
}
