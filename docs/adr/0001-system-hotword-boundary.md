# ADR 0001: Use Android's system voice-assistant boundary

- Status: accepted
- Date: 2026-08-25

## Decision

AgentOS uses `VoiceInteractionService`, `AlwaysOnHotwordDetector`, an isolated
`HotwordDetectionService`, and a short on-device `SpeechRecognizer` session. The
HOME shell does not record audio and does not run a continuous recognizer.

The product overlay selects `com.agentos.voice` as the default and forced voice
interaction package. A hardware DSP match is the only always-on trigger. After the
spoken command ends, the service closes recognition and sends bounded text across a
signature-protected boundary. Detection is then re-armed during planning and TTS so
a fresh keyphrase can interrupt the active turn; physical-device acceptance
therefore includes speaker echo and false-wake tests.

## Consequences

- Android owns microphone attribution, assistant lifecycle, and isolated hotword
  execution instead of application code.
- Idle power can remain compatible with a phone only when the target device offers
  a working SoundTrigger DSP model.
- Cuttlefish and standalone Gradle builds cannot prove hardware hotword behavior.
- Each physical-device port must provide and calibrate its keyphrase enrollment and
  vendor audio policy; unsupported hardware fails closed.
