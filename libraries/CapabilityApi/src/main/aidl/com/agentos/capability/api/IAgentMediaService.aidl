package com.agentos.capability.api;

import android.view.Surface;
import com.agentos.capability.api.IAgentMediaListener;
import com.agentos.capability.api.MediaItem;

interface IAgentMediaService {
    void registerListener(IAgentMediaListener listener);
    void unregisterListener(IAgentMediaListener listener);
    void openCamera(in Surface previewSurface, int width, int height, int lensFacing);
    void closeCamera();
    void setZoom(float ratio);
    void capturePhoto();
    void startVideo(boolean withAudio);
    void stopVideo();
    void startAudioRecording();
    void pauseAudioRecording();
    void resumeAudioRecording();
    void stopAudioRecording();
    int getAudioAmplitude();
    List<MediaItem> queryRecentMedia(int limit);
}
