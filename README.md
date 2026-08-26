# AgentOS Platform

[![CI](https://github.com/kairowan/agentos-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/kairowan/agentos-platform/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/kairowan/agentos-platform?include_prereleases)](https://github.com/kairowan/agentos-platform/releases)

Buildable AgentOS product code for AOSP 17. The
[`agentos`](https://github.com/kairowan/agentos) bootstrap checks this repository
out at `vendor/agentos` inside the AOSP source tree.

![AgentOS v0.4.0 using voice-first UI and the separate capability service](https://github.com/kairowan/agentos-platform/releases/download/v0.4.0/AgentShell-home.png)

## v0.4 baseline

- Voice-first Kotlin UI using the platform speech recognizer and text-to-speech
- Broker-owned notification listener with bounded message-event filtering
- One-way AIDL event delivery from the trusted service to the HOME shell
- Kotlin and Jetpack Compose HOME activity
- Validated generated-interface data model and JSON Schema
- Separate capability-service APK behind a typed AIDL interface
- Signature permission, Binder caller checks, and SELinux domain policy
- Capability Broker with risk classes, one-time confirmation, and bounded audit log
- Time, device, private-storage, and confirmed Wi-Fi settings capabilities
- Strict generated-UI JSON parser with size, field, type, and capability allowlists
- Optional OpenAI-compatible planner with HTTPS policy and in-memory credentials
- Deterministic local planner and automatic fail-safe model fallback
- Lifecycle-aware state holder and JVM unit tests
- Gradle build for daily development and Soong build for AOSP integration

## Current `main`: system hotword path

The platform moves audio capture out of AgentShell. A product-level
`VoiceInteractionService` now owns an always-on `Hey AgentOS` detector, delegates
DSP verification to an isolated `HotwordDetectionService`, opens one on-device
speech-recognition turn, and closes it automatically on silence. Delivery to the
Shell uses a signature permission plus a one-time command ticket.

Native media follows the same boundary. A separately sandboxed `AgentMediaService`
owns Camera2 and MediaRecorder sessions while AgentShell supplies a native
`SurfaceView` plus Kotlin Compose controls. The current vertical slice includes
front/back preview, zoom, JPEG photos, H.264/AAC video, pauseable M4A recording,
live amplitude, and a unified MediaStore gallery. See
[`docs/media-runtime.md`](docs/media-runtime.md) for hardware-dependent limits.

The same hotword can interrupt an active plan or spoken response. AgentShell keeps
all local task history through Room/SQLite and renders every conversation plus an automatically
extracted semantic graph of people, relationships, preferences, projects, places,
and long-term facts on a pannable, 0.35×–4× zoomable, editable mind-map canvas. Every relation retains source evidence and confidence; model
inferences are visibly marked as candidates and cannot authorize capabilities.

This source boundary is buildable only inside AOSP; the standalone Gradle build
continues to cover the Shell, Broker, and their unit tests. Real always-on wake-up
also requires a target device with a SoundTrigger DSP and an enrolled keyphrase
model. Cuttlefish does not supply proof of that hardware path.

The model and shell remain outside the capability-service process. They can select
only registered capability IDs; the Broker independently decides whether an operation
executes, is denied, or requires trusted confirmation.

## Standalone build

Install JDK 17 and Android SDK 35, then use Gradle 8.12:

```bash
gradle testDebugUnitTest assembleDebug
```

The three APKs are written to:

```text
apps/AgentShell/build/outputs/apk/debug/AgentShell-debug.apk
services/AgentCapabilityService/build/outputs/apk/debug/AgentCapabilityService-debug.apk
services/AgentMediaService/build/outputs/apk/debug/AgentMediaService-debug.apk
```

GitHub Actions runs the same test and build for every commit and pull request.
Current APKs are available from
[the v0.4.0 pre-release](https://github.com/kairowan/agentos-platform/releases/tag/v0.4.0).
Version tags are rebuilt by a separate release workflow, which publishes both the
APK and its SHA-256 checksum from the tagged commit.

## AOSP build

From the AOSP tree created by the main repository:

```bash
source build/envsetup.sh
lunch agentos_cf_x86_64-aosp_current-userdebug
m AgentShell AgentCapabilityService AgentMediaService AgentVoiceService
```

Use `m` instead of the two module targets to build the complete Cuttlefish image.

## Security boundary

Agent output cannot invoke Android APIs directly. Every operation must resolve to
a registered capability. Unknown goals are rejected. The current service implements
the process and caller-authentication boundary. Additional privileged or write
capabilities still require scoped grants, quotas, revocation, persistent encrypted
auditing, and complete AOSP SELinux validation.

Remote model credentials are not persisted. Non-local endpoints must use HTTPS;
cleartext HTTP is accepted only for `localhost`, `127.0.0.1`, and Android emulator
host address `10.0.2.2`. Prompts are sent to the configured provider when remote
mode is enabled.

Notification text is filtered inside the capability-service process and displayed
locally. Receiving an event does not send its contents to a configured model and
does not authorize an automatic reply. Notification access remains an explicit
user-controlled Android setting.

## Documentation

- [Capability Broker](docs/capability-broker.md)
- [Model provider](docs/model-provider.md)
- [Generated UI](docs/generated-ui.md)
- [Demo flow](docs/demo.md)
- [Voice and message events](docs/voice-and-events.md)
- [Conversation history and knowledge view](docs/history-and-knowledge.md)
- [Physical-device hotword calibration](docs/hotword-device-calibration.md)
- [ADR: system hotword boundary](docs/adr/0001-system-hotword-boundary.md)

## License

Apache License 2.0.
