package com.agentos.voice;

import static android.service.voice.AlwaysOnHotwordDetector.STATE_KEYPHRASE_ENROLLED;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.HotwordDetector;
import android.service.voice.HotwordRejectedResult;
import android.service.voice.VoiceInteractionService;

import java.util.Locale;

public final class AgentVoiceInteractionService extends VoiceInteractionService {
    private HotwordDetector detector;
    private boolean enrolled;

    private final BroadcastReceiver rearmReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (VoiceContract.ACTION_REARM.equals(intent.getAction())) arm();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        registerReceiver(
                rearmReceiver,
                new IntentFilter(VoiceContract.ACTION_REARM),
                Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onReady() {
        super.onReady();
        detector = createAlwaysOnHotwordDetector(
                VoiceContract.KEYPHRASE,
                Locale.US,
                null,
                null,
                new DetectorCallback());
    }

    @Override
    public void onLaunchVoiceAssistFromKeyguard() {
        showSession(new Bundle(), 0);
    }

    @Override
    public void onShutdown() {
        if (detector != null) detector.destroy();
        detector = null;
        enrolled = false;
        super.onShutdown();
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(rearmReceiver);
        super.onDestroy();
    }

    private void arm() {
        if (!enrolled || detector == null) return;
        try {
            detector.startRecognition();
        } catch (IllegalStateException | UnsupportedOperationException ignored) {
            // Hardware/service teardown can race a callback. A later availability
            // callback or service restart is the recovery boundary.
        }
    }

    private final class DetectorCallback extends AlwaysOnHotwordDetector.Callback {
        @Override
        public void onAvailabilityChanged(int status) {
            enrolled = status == STATE_KEYPHRASE_ENROLLED;
            arm();
        }

        @Override
        public void onDetected(AlwaysOnHotwordDetector.EventPayload eventPayload) {
            showSession(new Bundle(), 0);
        }

        @Override
        public void onRejected(HotwordRejectedResult result) {
            arm();
        }

        @Override
        public void onError() {
            arm();
        }

        @Override
        public void onRecognitionPaused() {}

        @Override
        public void onRecognitionResumed() {}

        @Override
        public void onHotwordDetectionServiceInitialized(int status) {
            arm();
        }
    }
}
