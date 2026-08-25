package com.agentos.voice;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.service.voice.VoiceInteractionSession;

import java.util.ArrayList;
import java.util.Locale;

final class AgentVoiceInteractionSession extends VoiceInteractionSession {
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeout = () -> finishSession(true);
    private SpeechRecognizer recognizer;
    private boolean finished;

    AgentVoiceInteractionSession(Context context) {
        super(context);
        this.context = context;
    }

    @Override
    public void onShow(Bundle args, int showFlags) {
        super.onShow(args, showFlags);
        startCommandCapture();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(timeout);
        if (recognizer != null) recognizer.destroy();
        recognizer = null;
        super.onDestroy();
    }

    private void startCommandCapture() {
        handler.removeCallbacks(timeout);
        if (recognizer != null) recognizer.destroy();
        recognizer = null;
        finished = false;
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            finishSession(true);
            return;
        }
        try {
            recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
            recognizer.setRecognitionListener(new CommandListener());
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
                    .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 700L);
            recognizer.startListening(intent);
            handler.postDelayed(timeout, MAX_LISTENING_MILLIS);
        } catch (RuntimeException unavailable) {
            finishSession(true);
        }
    }

    private void deliver(String command) {
        if (command.isBlank()) {
            finishSession(true);
            return;
        }
        Intent intent = new Intent(VoiceContract.ACTION_DELIVER_COMMAND)
                .setComponent(new ComponentName(
                        VoiceContract.SHELL_PACKAGE,
                        VoiceContract.COMMAND_RECEIVER))
                .putExtra(VoiceContract.EXTRA_COMMAND, command.substring(0, Math.min(command.length(), 8000)));
        context.sendBroadcast(intent);
        // AgentShell rearms detection after TTS completes, so the assistant never
        // interprets its own spoken response as a new wake phrase.
        finishSession(false);
    }

    private void finishSession(boolean rearm) {
        if (finished) return;
        finished = true;
        handler.removeCallbacks(timeout);
        SpeechRecognizer current = recognizer;
        recognizer = null;
        if (current != null) {
            try {
                current.cancel();
            } catch (RuntimeException ignored) {
                // The provider may have disappeared while the turn was active.
            }
            try {
                current.destroy();
            } catch (RuntimeException ignored) {
                // Session teardown must still re-arm the low-power detector.
            }
        }
        if (rearm) {
            context.sendBroadcast(
                    new Intent(VoiceContract.ACTION_REARM).setPackage(context.getPackageName()));
        }
        finish();
    }

    private final class CommandListener implements RecognitionListener {
        @Override
        public void onResults(Bundle results) {
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            deliver(matches == null || matches.isEmpty() ? "" : matches.get(0).trim());
        }

        @Override public void onError(int error) { finishSession(true); }
        @Override public void onReadyForSpeech(Bundle params) {}
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() {}
        @Override public void onPartialResults(Bundle partialResults) {}
        @Override public void onEvent(int eventType, Bundle params) {}
    }

    private static final long MAX_LISTENING_MILLIS = 10_000L;
}
