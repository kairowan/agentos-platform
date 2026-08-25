# AgentOS Platform

[![CI](https://github.com/kairowan/agentos-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/kairowan/agentos-platform/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/kairowan/agentos-platform?include_prereleases)](https://github.com/kairowan/agentos-platform/releases)

Buildable AgentOS product code for AOSP 17. The
[`agentos`](https://github.com/kairowan/agentos) bootstrap checks this repository
out at `vendor/agentos` inside the AOSP source tree.

![AgentOS v0.2.1 running on an Android emulator](https://github.com/kairowan/agentos-platform/releases/download/v0.2.1/AgentShell-home.png)

## v0.3 baseline

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

The model and shell remain outside the capability-service process. They can select
only registered capability IDs; the Broker independently decides whether an operation
executes, is denied, or requires trusted confirmation.

## Standalone build

Install JDK 17 and Android SDK 35, then use Gradle 8.12:

```bash
gradle testDebugUnitTest assembleDebug
```

The two APKs are written to:

```text
apps/AgentShell/build/outputs/apk/debug/AgentShell-debug.apk
services/AgentCapabilityService/build/outputs/apk/debug/AgentCapabilityService-debug.apk
```

GitHub Actions runs the same test and build for every commit and pull request.
Current APKs are available from
[the v0.2.1 pre-release](https://github.com/kairowan/agentos-platform/releases/tag/v0.2.1).
Version tags are rebuilt by a separate release workflow, which publishes both the
APK and its SHA-256 checksum from the tagged commit.

## AOSP build

From the AOSP tree created by the main repository:

```bash
source build/envsetup.sh
lunch agentos_cf_x86_64-aosp_current-userdebug
m AgentShell
```

Use `m` instead of `m AgentShell` to build the complete Cuttlefish image.

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

## Documentation

- [Capability Broker](docs/capability-broker.md)
- [Model provider](docs/model-provider.md)
- [Generated UI](docs/generated-ui.md)
- [Demo flow](docs/demo.md)

## License

Apache License 2.0.
