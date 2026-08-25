package com.agentos.voice;

import android.os.PersistableBundle;
import android.os.SharedMemory;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.HotwordDetectedResult;
import android.service.voice.HotwordDetectionService;
import android.service.voice.HotwordRejectedResult;

import java.util.function.IntConsumer;

public final class AgentHotwordDetectionService extends HotwordDetectionService {
    @Override
    public void onUpdateState(
            PersistableBundle options,
            SharedMemory sharedMemory,
            long callbackTimeoutMillis,
            IntConsumer statusCallback) {
        if (statusCallback != null) statusCallback.accept(0);
    }

    @Override
    public void onDetect(
            AlwaysOnHotwordDetector.EventPayload payload,
            long timeoutMillis,
            Callback callback) {
        // The enrolled SoundTrigger/DSP model already matched the keyphrase. The
        // isolated service is the boundary where a second-stage model can be added.
        callback.onDetected(new HotwordDetectedResult.Builder().setHotwordPhraseId(0).build());
    }

    @Override
    public void onDetect(Callback callback) {
        // Never turn the sandbox into a continuous software microphone detector.
        callback.onRejected(new HotwordRejectedResult.Builder().build());
    }
}
