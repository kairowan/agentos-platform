# Physical-device hotword calibration

Framework code cannot calibrate an unknown microphone, speaker, DSP, SoundTrigger
HAL, or keyphrase model. A device port is accepted only after testing the exact
hardware build with an enrolled `Hey AgentOS` model.

## Required test matrix

Run with the screen on, screen off, locked, charging, battery saver enabled, quiet
room, street noise, music playback, and AgentOS TTS playback:

- at least 100 intended wakes at 0.5 m, 1 m, 3 m, and multiple angles;
- at least 500 negative phrases from different speakers;
- at least 100 AgentOS spoken responses containing acoustically similar words;
- barge-in during the start, middle, and end of TTS at several volume levels;
- hardware microphone mute, calls, recording conflicts, suspend/resume, and reboot;
- 8-hour idle-power measurement with no software microphone fallback.

Record false rejects, false accepts, self-wakes, stop-to-listen latency, and idle
power. Initial acceptance targets are <5% false rejects in the supported acoustic
envelope, zero self-wakes in the TTS set, <1 false accept per 8-hour negative run,
and <500 ms from interrupt phrase detection to TTS stop. Final thresholds must be
tuned per product and threat model.

Use `scripts/capture-hotword-diagnostics.sh <output-directory>` immediately before
and after a test run. Vendor tuning typically changes the enrolled DSP model,
thresholds, microphone geometry/AEC parameters, audio policy, and vendor SELinux;
none of those values should be guessed from Cuttlefish.
