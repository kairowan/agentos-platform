# Local preview verification — 2026-08-27

## Current: 0.5.0-preview.2 (8)

Local working-tree build; **not published as a GitHub release**. Source publication
does not turn this local run into a GitHub Actions run. The three matched APKs are
packaged in ignored `artifacts/AgentOS-0.5.0-preview.2.zip`. Same emulator environment
as below, upgraded in place without deleting the existing app history or changing
HOME/DIALER/SMS defaults.

- **69 JVM tests**, zero failures/errors; all three APKs and the Room test APK built.
- Real Android Room checks passed: full reply persistence, old/new-session recovery,
  cancelled/terminal task protection, clear-then-late-result rejection, corrected
  memory exclusion, and genuine legacy schema migrations **3→5 and 4→5**.
- Actual offline UI flow passed: Binder time, cancellation without opening Settings,
  one-time confirmation opening real Wi-Fi Settings, visible saved history, and
  native communication fallback without a model.
- The expanded follow-up observed **完整回复** and **查看任务记录**, then failed
  finding the collapse control after an unrelated adb/shell launch brought
  `com.palmzem.voicerecorder` to the foreground (17:10:52 local time). Its report
  remains `passed: false`; it is **not** a passed end-to-end run. The earlier
  five-check pass is in `artifacts/preview-recording-0.5.2/verification.json`;
  the interrupted follow-up is in `artifacts/preview-recording-0.5.2-final/verification.json`.
  Both recorded APK hashes match this preview bundle. Re-run the expanded script
  with exclusive access to the emulator before claiming its complete UI coverage.
- App Bridge's all-click confirmation policy, sensitive flags and delayed single-use
  gate have JVM coverage. **Third-party app execution, OEM accessibility behavior
  and cancellation timing on real devices have not been validated in this run.**
- Memory opt-in/filtering and local recall have deterministic tests. No real cloud
  model endpoint was contacted; bounded lexical recall is not full semantic memory.
- Preview integrity, explicit-device safeguards and public-image allowlist checks
  passed. Runtime captures remain local. No physical phone was targeted.

See [the preview guide](developer-preview.md) for new controls, migration limits
and privacy behavior. The earlier call/SMS findings below belong to preview.1;
they were **not rerun as carrier or telephony evidence for preview.2**.

## Earlier baseline: 0.5.0-preview.1 (7)

Version: **0.5.0-preview.1 (7)**. This is a local working-tree build based on
`c76948df1ebe7f28ce579f8907afbfe3ae4e6e86`, including the maintainer's renderer
changes. It is **not a new GitHub release or a full AOSP image**. Reproducing this
exact build requires the complete updated source, not just that base commit.

## Observed results

| Check | Result and boundary |
| --- | --- |
| Gradle build | Three APKs and the isolated Room test APK built together |
| JVM tests | 61 tests, zero failures/errors |
| Room on Android | Main-thread-safe construction; deletion/correction, duplicate correction, late extraction, evidence validation and migration 3→4 passed |
| Offline flow | Time via Binder; cancel Wi-Fi request without launching; confirm once; real Settings opens; history contains result |
| Process separation | Shell, capability and media APKs have distinct Android UIDs |
| Unauthorized entry | adb shell starting the communication service is rejected for missing signature permission; same-signature/wrong-package policy is unit-tested |
| Default roles | Android accepted the capability APK as DIALER and SMS on the test emulator |
| Restore defaults | Helper restored the original Google dialer and messaging app; original launcher and all test app data were retained |
| Incoming call | Emulator console call reaches native UI; answer → active → hang-up verified |
| Incoming text SMS | Emulator-injected text appears in Android's SMS Provider and native inbox |
| Preview tooling | APK integrity, explicit-device requirement and uninstall acknowledgement checks passed |
| Entry-repository build tooling | Manifest/evidence self-checks passed; **simulated checks, no AOSP build** |

Environment: Android 15 / API 35 arm64 Google APIs Play image; Android Emulator
36.2.12; 720×1280 override at 280 dpi; host graphics on an Apple M4. Wi-Fi and
mobile data were disabled for the final offline workflow. Test phone numbers and
messages are synthetic; no physical phone or real carrier call/SMS was used.

The video is a real `screenrecord`, not a concept animation. Its accompanying
`verification.json` records installed APK hashes, version, UIDs, check results and
renderer observation. Compare those hashes with the zip's `manifest.json`; do not
mix a video and APKs from different builds. Re-run with the
[preview instructions](developer-preview.md). Local outputs live in ignored
`artifacts/`; the manual demo workflow uploads only APKs, text logs and the JSON
verification report, never screenshots or video.
This recording is approximately 71 seconds. The updated GitHub workflows have
been checked locally but have not been dispatched on GitHub.

## Approved 3D design references / 对外展示

**设计效果图，非当前代码运行截图。** Only these two maintainer-selected images
represent the project's public 3D direction. They do not illustrate the executed
test results above. Runtime screenshots and the recording remain local and are
not included in public documentation or uploaded artifacts.

| 全屏智能体 · Home concept | 3D 角色工作室 · Studio concept |
| --- | --- |
| ![AgentOS home design concept, not a runtime screenshot](images/ui-v2/thought-field-home-concept.png) | ![AgentOS studio design concept, not a runtime screenshot](images/ui-v2/thought-field-studio-concept.png) |

## What remains unverified

- Physical SIM/IMS/carrier calls, outgoing carrier SMS/delivery reports, dual-SIM
  switching, Bluetooth/earpiece audio, locked-device/full-screen behavior, and OEMs.
- AOSP 17 full image, Soong/SELinux integration, boot, DSP hotword, echo cancellation
  and in-call AI audio. None is proven by the component APK build.
- Production-scale memory data, semantic paraphrase equivalence and complete
  accessibility. The graph suppresses exact corrected/deleted triples only.
- MMS/RCS, AI replacement of the caller, call recording and complete dialer features
  are **not implemented**, not merely awaiting verification. Use a test device only.
- GPU portability and stable frame rate: software graphics produced severe latency;
  the host-graphics run did not independently establish the WebGL path rather than
  its native fallback. A desktop shader capture is a separate renderer check, not
  phone performance evidence or parity with the concept images.

Real execution exposed and led to fixes for Room startup on the UI thread, dark
theme text contrast, WebGL cross-pass buffer state, bloom size arithmetic, the SMS
default-role check and Android 15's ongoing CallStyle restriction. The callable
emulator workflow includes the call/SMS paths so these are repeatable checks rather
than undocumented manual claims. The software-GPU limitations remain visible.
