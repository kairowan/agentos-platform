# v0.3 demo

Install both APKs from GitHub Releases, service first, then select AgentOS as HOME.

```bash
adb install -r AgentCapabilityService-debug.apk
adb install -r AgentMediaService-debug.apk
adb install -r AgentShell-debug.apk
adb shell cmd package set-home-activity com.agentos.shell/.MainActivity
```

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

This script is the source for the first public screen recording once an emulator or
Cuttlefish host is available. The `Capture Demo` workflow boots a real Android 35
emulator, installs both tagged APKs, invokes the time capability across Binder,
captures the result, and attaches the PNG to the corresponding GitHub release.
