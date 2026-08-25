# AgentOS Platform

[![CI](https://github.com/kairowan/agentos-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/kairowan/agentos-platform/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/kairowan/agentos-platform?include_prereleases)](https://github.com/kairowan/agentos-platform/releases)

Buildable AgentOS product code for AOSP 17. The
[`agentos`](https://github.com/kairowan/agentos) bootstrap checks this repository
out at `vendor/agentos` inside the AOSP source tree.

## v0.2 baseline

- Kotlin and Jetpack Compose HOME activity
- Validated generated-interface data model and JSON Schema
- Capability Broker with risk classes, one-time confirmation, and bounded audit log
- Time, device, private-storage, and confirmed Wi-Fi settings capabilities
- Strict generated-UI JSON parser with size, field, type, and capability allowlists
- Optional OpenAI-compatible planner with HTTPS policy and in-memory credentials
- Deterministic local planner and automatic fail-safe model fallback
- Lifecycle-aware state holder and JVM unit tests
- Gradle build for daily development and Soong build for AOSP integration

The model remains an unprivileged planner. It can select only registered capability
IDs; the Broker independently decides whether the operation executes, is denied, or
requires a trusted system confirmation.

## Standalone build

Install JDK 17 and Android SDK 35, then use Gradle 8.12:

```bash
gradle testDebugUnitTest assembleDebug
```

The APK is written to:

```text
apps/AgentShell/build/outputs/apk/debug/AgentShell-debug.apk
```

GitHub Actions runs the same test and build for every commit and pull request.
Current APKs are available from
[GitHub Releases](https://github.com/kairowan/agentos-platform/releases).

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
a registered capability. The current capabilities are read-only and unknown goals
are rejected. Privileged and write capabilities require the future Broker service,
system-owned confirmation UI, auditing, quotas, and revocation.

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
