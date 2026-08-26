# Native media runtime

AgentOS does not render camera frames in WebView or ask a model to process the
preview loop. `AgentMediaService` is a platform-signed, separately sandboxed process
that owns Camera2 and MediaRecorder sessions. AgentShell passes a native
`SurfaceView` surface over signature-protected AIDL and renders only the Kotlin
Compose control layer.

The first end-to-end media slice provides:

- native front/back Camera2 preview, continuous/tap autofocus, zoom, JPEG capture, and
  MediaStore publication;
- H.264/AAC MP4 recording through the hardware media stack with a persistent
  foreground recording indicator;
- AAC/M4A microphone recording with pause/resume, elapsed time, and live amplitude
  visualization;
- a unified MediaStore gallery for images, video, and audio, including platform
  thumbnails and dispatch to the installed viewer;
- signature permission, exact UID-package/signature validation, bounded inputs,
  AppOps-backed camera/microphone indicators, and a dedicated SELinux domain.

The UI lifecycle owns the preview surface: leaving the camera closes the session,
and leaving an active recorder stops and publishes the current artifact. Partial
files use `IS_PENDING` and are deleted when capture fails.

## Hardware-dependent work

CI verifies AIDL generation, Kotlin compilation, unit tests, and all APKs. It cannot
validate image quality. Target-device bring-up must still calibrate sensor
orientation, supported stream combinations, bitrate, stabilization, preview-to-sensor
focus transforms, and vendor Camera Extensions such as Night, HDR, Bokeh, and face retouch.
Those capabilities must be queried at runtime and never assumed.
