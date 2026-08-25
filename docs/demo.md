# v0.2 demo

Install the APK from GitHub Releases, then select AgentOS as the HOME application.

```bash
adb install -r AgentShell-debug.apk
adb shell cmd package set-home-activity com.agentos.shell/.MainActivity
```

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
Cuttlefish host is available.
