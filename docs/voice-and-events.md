# Voice and message events

Speech is the primary AgentOS task input; text remains a fallback. Development on
`main` moves microphone ownership out of the HOME application and into Android's
system voice-assistant boundary:

```text
SoundTrigger HAL / low-power DSP
  -> AlwaysOnHotwordDetector ("Hey AgentOS")
  -> isolated HotwordDetectionService
  -> VoiceInteractionSession
  -> one on-device SpeechRecognizer command turn
  -> signature-protected receiver
  -> AgentRuntime -> Capability Broker
  -> platform TextToSpeech -> re-arm hotword detector
```

The Shell has no microphone permission. The selected `VoiceInteractionService` is
kept lightweight and owns detector lifecycle. A DSP match opens one command turn;
Android's recognizer endpointer closes it after 1.2 seconds of complete silence
(700 ms when speech is probably complete). Because recognizer implementations may
ignore those hints, the session also enforces a 10-second hard cutoff. Detection is
not re-armed while AgentOS speaks, preventing its own TTS response from becoming a
new command.

The service uses only on-device speech recognition. If an image has no on-device
recognition provider, the voice turn fails closed and re-arms the keyphrase; text
input remains usable. It deliberately does not fall back to a continuously open
application microphone.

## Hardware boundary

The repository supplies the framework service, permissions, product overlay, and
detector lifecycle. A real always-on phrase still requires all of the following
from the target device integration:

- a SoundTrigger HAL and low-power DSP implementation;
- an enrolled `Hey AgentOS` keyphrase model for the selected locale;
- audio policy and vendor SELinux rules for that implementation;
- calibration for noise, false accepts, false rejects, power, and microphone mute.

Cuttlefish can validate service selection, command routing, permission rejection,
and session behavior. It is not evidence that a physical device's DSP hotword path
works. Until a device model is enrolled, the detector remains unavailable rather
than switching to power-hungry background recording.

## Command boundary

Only `AgentVoiceService` may send `DELIVER_VOICE_COMMAND`; Android enforces a
signature permission on the exported receiver. Commands are trimmed and capped at
8,000 characters. The receiver exchanges the command for a random, one-time,
in-memory ticket before launching the exported HOME activity, so another app cannot
inject a command by imitating the internal activity intent. A process death loses
the pending command by design instead of persisting sensitive speech.

## Message notifications

Message notifications follow a separate trusted path:

```text
Android NotificationManager -> AgentCapabilityService
-> local category/size/privacy filter -> one-way AIDL event -> AgentShell UI
```

Enable access from **消息事件 / 授权**. Only message-category notifications with a
non-empty sender and body are surfaced. Ongoing notifications and group summaries
are rejected. At most five recent events remain in Shell process memory.

No notification content is automatically sent to a remote model, persisted, spoken
on a locked device, or answered. Those behaviors require separate user-facing
controls and Broker capabilities.
