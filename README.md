# AgentOS Platform

Buildable AgentOS product code for AOSP 17. The
[`agentos`](https://github.com/kairowan/agentos) bootstrap checks this repository
out at `vendor/agentos` inside the AOSP source tree.

## v0.1 baseline

- Kotlin and Jetpack Compose HOME activity
- Validated generated-interface data model and JSON Schema
- Explicit capability registry with time, device, and private-storage readers
- Deterministic local agent router with safe handling of unknown goals
- Lifecycle-aware state holder and JVM unit tests
- Gradle build for daily development and Soong build for AOSP integration

The local router is intentionally not an LLM. A model planner will be added only
after the capability policy and confirmation boundary exists.

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

## License

Apache License 2.0.

