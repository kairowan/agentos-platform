# Small, complete contributions

Do not start by compiling all of AOSP. The standalone Gradle build and a stock
Android emulator are enough for the following tasks. Open an issue to coordinate;
the examples below are a work queue, not fabricated external bug reports.

## 1. English SMS draft command — good first issue candidate

- Files: `apps/AgentShell/src/com/agentos/shell/CommunicationCommands.kt` and its
  existing `CommunicationCommandsTest.kt`.
- Reproduce: `sms` opens communication, but `sms 15555215556: arriving soon` does
  not currently create a draft.
- Expected: add this one exact grammar, respecting existing recipient/message
  bounds. It must produce a draft only, never confirm or send.
- Acceptance: tests for a valid draft, empty body, overlong body and ordinary text
  mentioning a command. Existing Chinese commands remain unchanged.
- Check: `gradle :apps:AgentShell:testDebugUnitTest`.

## 2. Device compatibility report — no code required

- Files: `docs/developer-preview.md`; use the compatibility issue form.
- Run the documented offline time/deny/approve/history flow on a disposable
  emulator or a consented test device. Do not use real emergency numbers.
- Report revision, Android API, emulator/physical distinction, relevant defaults,
  pass/fail for each step, and redacted diagnostics.
- Acceptance: another developer can reproduce the result without access to your
  private account, SMS, phone number or API key. A failure report is valuable.
- Check: the documented flow; record omissions instead of guessing a pass.

## 3. Native communication accessibility audit — help wanted

- Files: `services/AgentCapabilityService/src/com/agentos/capability/service/CommunicationActivity.kt`.
- Reproduce with TalkBack and 200% font size on a stock emulator.
- Expected: recipient/SIM selection and confirmation contents are distinguishable;
  call controls and emergency fallback remain reachable without the avatar renderer.
- Acceptance: describe exact focus/navigation problem, add a minimal fix, include
  before/after reproduction and verify that no confirmation or lock-screen policy
  was weakened. Do not label the entire audit a beginner task.
- Check: `gradle testDebugUnitTest assembleDebug`, then repeat the accessibility steps.

## 4. Renderer performance measurement — help wanted

- Files: `apps/AgentShell/assets/avatar/runtime.js`, `AgentAvatarWebView.kt`,
  `docs/avatar-system.md`.
- Compare idle/listening/speaking in the actual APK. Report device, WebView version,
  frame timing, memory and thermal conditions. A desktop concept screenshot is not
  evidence of phone frame rate.
- Acceptance: a repeatable measurement and a focused change preserving the bounded
  one-way render protocol and offline asset restrictions. No remote executable shaders.
- Check: existing renderer checks and APK build, plus the same device measurement.

Maintainer process: discuss the smallest acceptance criterion publicly, give a clear
review response, and credit testing/docs as well as code. Apply `good first issue`
only after verifying the task is genuinely approachable. Invite real testers with
the preview link and limitations; do not manufacture issues, stars or endorsements.
