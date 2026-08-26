package com.agentos.capability.api;

import com.agentos.capability.api.MediaEvent;

oneway interface IAgentMediaListener {
    void onMediaEvent(in MediaEvent event);
}
