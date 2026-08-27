# Android-component demo (not a complete AOSP image)

The downloadable baseline is **v0.4.0**, containing Shell and Capability Service
APKs. Install both from the same release, service first, then select AgentOS as HOME:

```bash
adb install -r AgentCapabilityService-debug.apk
adb install -r AgentShell-debug.apk
adb shell cmd package set-home-activity com.agentos.shell/.MainActivity
```

Current `main` CI artifacts include a third APK, `AgentMediaService-debug.apk`.
To test media or newer UI features, use all three APKs from that same CI run or
local build, installing both services before Shell. Do not combine a current
Shell with services from an older tag. This demo changes the selected HOME app;
use a test emulator/device and restore your previous HOME afterward in Settings.

To test installed-app semantic snapshots, open Android Accessibility settings from
the **应用能力桥** card and explicitly enable **AgentOS 应用语义桥**. It is not
enabled automatically by the build or demo workflow.

## Offline path

1. Choose **查看设备状态** and verify the typed facts returned by the Broker.
2. Choose **打开 Wi-Fi 设置** and verify no Intent launches before confirmation.
3. Press **取消** and verify the operation is not executed.
4. Repeat, press **允许一次**, and verify Android Wi-Fi settings opens.

## Model path

1. Open **模型连接** and configure an OpenAI-compatible endpoint.
2. Ask for an interface that explains the current device.
3. Verify only paragraph, fact, and action blocks render.
4. Disconnect the endpoint and repeat a supported goal.
5. Verify the warning and automatic offline result.

The current `Verify Developer Preview` workflow checks a matching build on a stock
Android 35 emulator. It uploads APKs and written verification results, not runtime
screenshots or recordings, and does not attach images to a GitHub release. Public
3D images are limited to the two approved design concepts. See the
[current preview guide](developer-preview.md) for its actual commands and limits.
This validates component paths, **not** a custom AOSP 17 image, a system-installed
voice service, or SELinux product integration. The v0.4.0 instructions above and
current `main` do not describe the same UI.

Full-image build and boot evidence must come from the separate
[AOSP runbook](https://github.com/kairowan/agentos/blob/main/docs/aosp-build.md).
