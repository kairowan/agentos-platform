# AgentOS developer preview / 开发者预览

This is an **ordinary Android component preview**, not an AOSP image, daily-driver
phone replacement, or proof of DSP hotword support. The selected concept images
are visual targets, not runtime evidence. No paid model is needed for the demo.

## One revision, three APKs

Build with JDK 17, SDK 35 and Gradle 8.12:

```bash
gradle testDebugUnitTest assembleDebug :apps:AgentShell:assembleDebugAndroidTest
python3 scripts/package-preview.py
```

All three APKs share the version in `gradle.properties`. The package command reads
the built APK metadata and refuses mixed versions. The zip includes hashes,
source commit, a dirty-worktree flag, this guide and the device helper. A dirty
bundle is a local snapshot, not a reproducible release tag. Debug builds are not
production signing or an OTA update. CI artifacts also use the same package step.

## Install without changing your normal desktop

Use Android 11+ for the current communication preview. Choose a disposable
emulator first. Do **not** enable the SMS role on a phone receiving important
messages: MMS/RCS are not implemented. Android 10 still supports the basic Shell
demo, but this preview's SIM-account mapping for calls requires Android 11+.

Extract the zip and run from its directory (Python 3 and `adb` are required):

```bash
python3 preview-device.py verify
adb devices -l
python3 preview-device.py install --serial emulator-5580 --state original-defaults.json
python3 preview-device.py launch --serial emulator-5580
```

Use your actual emulator serial. For a physical **test** device, explicitly supply
its serial. Installation checks hashes, records the original HOME/DIALER/SMS
roles, and atomically installs the three APKs. It does **not** change HOME,
default phone/SMS, or runtime permissions. Signature mismatch fails safely: never
uninstall an existing build just to bypass it without backing up its data.

Launch directly to try AgentOS; selecting it as default HOME is optional. Keep
`original-defaults.json` outside the repository; it identifies your device.

## Reproduce the offline experience

1. Open **键盘**, type `time`, submit. Verify actual local time from the Broker.
2. Type `wifi`. Verify **系统确认** appears and Settings has not opened.
3. Select **取消**. Verify the operation is not executed.
4. Repeat `wifi`, choose **允许一次**. Android Wi-Fi Settings must open.
5. Return and open **忆**. Both the cancellation and confirmed result are in history.
6. Open **话** for the native communication screen. Without roles/permissions,
   communication must fail with an explanation, not claim success.

### New in 0.5.0-preview.2

- **Full local replies**: history stores all generated blocks, not just the title.
  Select **完整回复** to expand. Old title-only records remain labeled; missing
  historical text cannot be reconstructed.
- **Local recall without a paid model**: `回顾上次任务` recalls the last eligible
  completed exchange; `我的偏好` / `我的已确认记忆` shows confirmed fact excerpts
  with source IDs. The full graph/history remains available in the memory page.
- **Explicit cloud-memory consent**: the model-settings switch defaults off and
  resets on endpoint changes or process restart. It permits at most six recent
  turn excerpts and twelve confirmed facts (12,000-character JSON budget).
  It does not add SMS, notifications or app snapshots to model context. Current
  prompts still go to the configured model when remote mode is enabled.
- **Corrections**: affected raw source turns stop participating in model recall;
  corrected facts omit contradictory original evidence from the outbound context.
  Evidence remains visible locally. Exact triples are suppressed, not all semantic
  paraphrases. With v4 suppression records, migration conservatively excludes all
  old raw turns from recall because those tombstones lack source IDs.
- **Task journal**: open **查看任务记录** in memory. Planning/confirmation work
  interrupted by a restart becomes cancelled; dispatched work without a result
  becomes unknown. Nothing is replayed automatically and tokens are not saved.
  This journal covers Shell→Capability Broker goals, not every media/communication
  subsystem or third-party app action. A completed answer is not proof that an
  arbitrary real-world operation occurred.
- **App Bridge**: every click/input now requires confirmation. Queued actions are
  cancellable and expire after five seconds; queued does not mean successful.

Room schema v5 migrates v3/v4 without destructive recreation. Do not downgrade the
APK against a v5 database; older builds cannot read the new schema. This is local
app-private storage, not an added encrypted-backup or cross-device sync feature.

For a real recording on a dedicated emulator, from the source checkout:

```bash
python3 scripts/capture-preview.py --serial emulator-5580
adb -s emulator-5580 install -r apps/AgentShell/build/outputs/apk/androidTest/debug/AgentShell-debug-androidTest.apk
adb -s emulator-5580 shell am instrument -w com.agentos.shell.test/com.agentos.shell.PreviewInstrumentation
```

The recording tool discovers controls from UI hierarchy bounds, captures the real
screen, and writes `artifacts/preview-recording/verification.json`. A video exists
even when a check fails: only `passed: true` is a successful flow. The separate
instrumentation checks real Room storage/migration using isolated test databases;
it does not delete user history. Model/network failure and authorization policy
are covered separately by JVM tests. No mock screen or concept image is substituted.

After explicitly selecting the phone/SMS roles and granting communication/notification
permissions **on a disposable emulator**, also run:

```bash
python3 scripts/capture-preview.py --serial emulator-5580 --check-communications
```

This injects a virtual incoming call, taps the native answer/hang-up controls, and
injects a test SMS which must appear in the system Provider and native inbox. It
never calls a real network; it refuses physical-device serials. It does not grant
roles automatically. Restore defaults with the helper when finished. Outgoing
carrier delivery, audio routing and lock-screen behavior still need separate tests.

The `Verify Developer Preview` GitHub workflow builds and checks one selected
revision. Its uploads are limited to APK bundles, the JSON verification report and
text logs; runtime screenshots and recordings are excluded. Local visual captures
stay in ignored `artifacts/`. Public 3D presentation uses only the two approved
design concepts, never presented as runtime evidence. The workflow does not overwrite
an older release's demonstration or claim a local run was executed in GitHub Actions.

## Communication preview

- Choose default phone/SMS roles **inside** the native screen, explicitly grant
  needed permissions, and select a SIM. No role is silently granted.
- `给妈妈打电话` resolves an exact real contact name; absent/ambiguous contacts
  require the contact picker or a complete number. The model never guesses a number.
- `给张三发短信，说我十分钟后到` prepares a draft. Final dialing/sending requires
  the native confirmation button, bound to number, text and SIM for 60 seconds.
  Cancellation invalidates a **pending** approval, not a call/SMS already submitted
  to Android. Sent messages cannot be recalled by cancelling an agent turn.
- Voice-originated `接听` / `拒接` / `挂断` use the typed control path only when
  there is one unambiguous current call. System wakeword transport still requires
  AOSP/hardware validation; the ordinary APK does not provide that microphone path.
- Incoming calls and SMS do not depend on the Shell or cloud model. Calls have
  native controls; SMS are stored in Android's SMS Provider, not sent to the model
  or automatically inserted into the knowledge graph. Locked UI hides message text.
- The app automation bridge cannot operate AgentOS confirmation surfaces or the
  standard Android/Google permission-controller, Settings and SystemUI packages.
  OEM-specific permission surfaces still need device-specific review.
- Long SMS use per-part send/delivery receipts. A timeout is not a success, send
  success is not delivery/read, and uncertain or partially sent messages are never
  automatically retried. Review records before a new manual send.
- **Not implemented:** MMS content download/sending, RCS, AI speaking into calls,
  call recording/transcription, voicemail/conference UI, complete accessibility,
  proximity-sensor behavior, and all OEM/carrier compatibility. MMS only preserves
  its incoming WAP notification and warns the user; this is not a usable MMS client.
- Emergency calling always has a separate preinstalled-system entry. Never dial
  real emergency numbers for testing; verify routing/UI in a controlled environment.

## Restore and uninstall safely

```bash
python3 preview-device.py restore --serial emulator-5580 --state original-defaults.json
```

This restores saved defaults only if AgentOS is still selected. If the original
choice is missing/ambiguous, the helper stops: use Android Settings → Apps →
Default apps to select your original desktop, phone and SMS apps. Also revoke
AgentOS notification/accessibility access if you enabled it. Do not remove your
only working dialer before selecting a replacement.

After backing up anything you need, uninstall explicitly:

```bash
python3 preview-device.py uninstall --serial emulator-5580 --state original-defaults.json --erase-local-data
```

This removes AgentOS for the current Android user and deletes its private history,
avatar settings and pending metadata. The helper cannot recover that private data.
It does not explicitly delete the system SMS database; verify your original SMS app
can read messages before removal. Do not use this flag merely to fix an APK signature.

## Verification boundaries

| Layer | Evidence required | What it does not prove |
| --- | --- | --- |
| JVM policy tests | `testDebugUnitTest` | Real Binder permission enforcement or modem behavior |
| APK build | all three APKs from one build | AOSP/SELinux product integration |
| Stock emulator demo | recording plus `passed: true` | SIM/IMS/real carrier service or DSP wakeword |
| Room instrumentation | `PASS` result | Large production databases or arbitrary semantic equivalence |
| Real telephone/SMS | device, SIM/carrier and explicit test cases | Other devices/carriers |
| AOSP image | full build, matching boot, logs and checksums | Real device DSP/audio/camera calibration |

Memory forgetting suppresses exact source/predicate/target triples, including late
extractions. It does not claim to recognize all paraphrases. Clearing all history
also clears suppression records; a new explicit conversation can then add new facts.

Report revision/version, device/API, exact reproduction, expected/actual results,
and redacted diagnostics. Never publish phone numbers, SMS contents, tokens or API keys.
