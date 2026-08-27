#!/usr/bin/env python3
"""Record the real offline confirmation flow on an explicitly selected emulator."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--output", type=Path, default=Path("artifacts/preview-recording"))
    parser.add_argument("--check-communications", action="store_true", help="Also inject an emulator-only call/SMS; requires explicit default roles and permissions first")
    args = parser.parse_args()
    if not re.fullmatch(r"emulator-[0-9]+", args.serial):
        parser.error("This script only operates on a disposable emulator, never a physical phone")
    base = [args.adb, "-s", args.serial]

    def adb(*command, binary=False):
        result = subprocess.check_output([*base, *command], timeout=25)
        return result if binary else result.decode().strip()

    if adb("shell", "getprop", "ro.kernel.qemu") != "1":
        raise RuntimeError("Not a verified emulator")
    args.output.mkdir(parents=True, exist_ok=True)
    size = adb("shell", "wm", "size") # Calibrate before any tap.
    installed = {}
    for package in ("com.agentos.shell", "com.agentos.capability", "com.agentos.media"):
        apk = adb("shell", "pm", "path", package).removeprefix("package:")
        if not re.fullmatch(r"/data/app/[a-zA-Z0-9_/=+.~-]+\.apk", apk):
            raise RuntimeError(f"Expected exactly one installed base APK for {package}")
        details = adb("shell", "dumpsys", "package", package)
        installed[package] = {"sha256": adb("shell", "sha256sum", apk).split()[0],
                              "version": re.search(r"versionName=(\S+)", details).group(1),
                              "uid": re.search(rf"^package:{re.escape(package)} uid:(\d+)$", adb("shell", "pm", "list", "packages", "-U", package), re.M).group(1)}
    assert len({item["uid"] for item in installed.values()}) == 3, "Components must use separate UIDs"
    assert len({item["version"] for item in installed.values()}) == 1, "Mixed installed versions"
    record_path = f"/sdcard/agentos-preview-{uuid.uuid4().hex}.mp4"
    recorder = subprocess.Popen([*base, "shell", "screenrecord", "--size", "720x1280", "--bit-rate", "2500000", "--time-limit", "180", record_path], stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    checks = []

    def dump():
        for attempt in range(3):
            path = f"/data/local/tmp/agentos-ui-{uuid.uuid4().hex}.xml"
            result = adb("shell", "uiautomator", "dump", path)
            if "dumped to" in result:
                break
            time.sleep(0.5)
        else:
            raise AssertionError("Android UI hierarchy unavailable; refusing stale tap coordinates")
        xml = adb("exec-out", "cat", path, binary=True)
        (args.output / "last-window.xml").write_bytes(xml)
        return ET.fromstring(xml)

    def find(label, kind="text", contains=False):
        for _ in range(8):
            tree = dump()
            for node in tree.iter("node"):
                value = node.get(kind, "")
                if label in value if contains else label == value:
                    return node
            time.sleep(0.5)
        raise AssertionError(f"UI element not found: {kind}={label}")

    def tap(label, kind="text", contains=False):
        node = find(label, kind, contains)
        bounds = [int(value) for value in re.findall(r"\d+", node.get("bounds", ""))]
        if len(bounds) != 4 or bounds[2] <= bounds[0] or bounds[3] <= bounds[1]:
            raise AssertionError(f"Invalid tap bounds: {label}")
        adb("shell", "input", "tap", str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2))
        time.sleep(0.5)

    def submit(text):
        tap("android.widget.EditText", "class")
        adb("shell", "input", "text", text)
        adb("shell", "input", "keyevent", "4") # Hide IME, retaining native draft.
        tap("↑")

    def screenshot(name):
        (args.output / f"{name}.png").write_bytes(adb("exec-out", "screencap", "-p", binary=True))

    failure = None
    renderer = "unobserved"
    try:
        adb("shell", "am", "force-stop", "com.agentos.shell") # Disposable emulator only; no data deletion.
        adb("shell", "am", "start", "-W", "-n", "com.agentos.shell/.MainActivity")
        find("键盘")
        renderer = "WebGL ready signal observed" if any(n.get("text") == "AGENTOS_AVATAR_READY" for n in dump().iter("node")) else "WebGL not verified; native fallback may be active"
        tap("键盘")
        submit("time")
        find("本地时间", contains=True)
        checks.append("offline time capability across Binder")
        screenshot("01-offline-result")
        submit("wifi")
        find("系统确认")
        screenshot("02-trusted-confirmation")
        tap("取消")
        find("用户已取消", contains=True)
        resumed = "\n".join(line for line in adb("shell", "dumpsys", "activity", "activities").splitlines() if "ResumedActivity" in line)
        assert "com.android.settings" not in resumed, "Cancellation opened Settings"
        checks.append("cancel does not launch settings")
        submit("wifi")
        tap("允许一次")
        for _ in range(12):
            resumed = "\n".join(line for line in adb("shell", "dumpsys", "activity", "activities").splitlines() if "ResumedActivity" in line)
            if "com.android.settings" in resumed:
                break
            time.sleep(0.5)
        assert "com.android.settings" in resumed, "Approval did not open Settings"
        checks.append("one-time approval opens real Android Settings")
        screenshot("03-system-settings")
        adb("shell", "input", "keyevent", "4")
        tap("打开记忆", "content-desc")
        find("确认：打开 Wi-Fi 设置", contains=True)
        checks.append("approval result persisted in visible history")
        tap("完整回复")
        find("system.settings.wifi.open", contains=True)
        checks.append("full saved reply expands in history")
        tap("查看任务记录", contains=True)
        find("任务：", contains=True)
        checks.append("durable task journal is accessible from memory")
        tap("收起任务记录", contains=True)
        screenshot("04-history")
        adb("shell", "am", "start", "-W", "-n", "com.agentos.capability/.service.CommunicationActivity")
        find("原生电话与文本短信", contains=True)
        screenshot("05-communication")
        checks.append("native communication fallback renders without a model")
        if args.check_communications:
            for role in ("android.app.role.DIALER", "android.app.role.SMS"):
                assert adb("shell", "cmd", "role", "get-role-holders", "--user", "0", role) == "com.agentos.capability", "Select the test communication roles first"
            # These are Android Emulator console events, never real network calls/SMS.
            adb("emu", "gsm", "call", "15555215556")
            find("接听")
            screenshot("06-simulated-incoming-call")
            tap("接听")
            find("通话中")
            screenshot("07-simulated-active-call")
            tap("挂断")
            for _ in range(8):
                labels = {node.get("text") for node in dump().iter("node")}
                if "挂断" not in labels and "通话中" not in labels:
                    break
                time.sleep(0.5)
            else:
                raise AssertionError("Native hang-up did not update call state")
            checks.append("emulated incoming call answered and hung up through native controls")
            message = f"AgentOS_preview_{uuid.uuid4().hex[:10]}"
            adb("emu", "sms", "send", "15555215556", message)
            for _ in range(12):
                inbox = adb("shell", "content", "query", "--uri", "content://sms/inbox", "--projection", "_id:body:type:sub_id")
                if message in inbox:
                    break
                time.sleep(0.5)
            else:
                raise AssertionError("Emulated SMS was not persisted in the system SMS Provider")
            for _ in range(6):
                tree = dump()
                if any(node.get("text") == message for node in tree.iter("node")):
                    screenshot("08-simulated-sms-inbox")
                    break
                node = next(n for n in tree.iter("node") if n.get("scrollable") == "true")
                x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
                adb("shell", "input", "swipe", str((x1 + x2) // 2), str(y2 - 50), str((x1 + x2) // 2), str(y1 + 50), "400")
            else:
                raise AssertionError("Persisted SMS not shown in native inbox")
            checks.append("emulated SMS stored in system Provider and visible in native inbox")
        if recorder.poll() is not None:
            raise AssertionError("Recording ended before the workflow; partial video is not complete evidence")
    except Exception as error:
        failure = str(error)
    finally:
        if args.check_communications:
            try:
                adb("emu", "gsm", "cancel", "15555215556")
            except Exception:
                pass # Never hide the actual failed check while cleaning up our simulated call.
        # Stop only our recorder on this dedicated emulator; never pkill unrelated processes.
        listing = adb("shell", "ps", "-A", "-o", "PID,ARGS")
        for line in listing.splitlines():
            if record_path in line and "screenrecord" in line:
                pid = line.strip().split()[0]
                if pid.isdigit():
                    adb("shell", "kill", "-2", pid)
        try:
            recorder.wait(timeout=10)
        except subprocess.TimeoutExpired:
            recorder.terminate()
        try:
            adb("pull", record_path, str(args.output / "offline-flow.mp4"))
        except Exception as error:
            failure = f"{failure or 'Recording failed'}; video unavailable: {error}"
        recording_errors = recorder.stderr.read().decode(errors="replace") if recorder.poll() is not None else ""
        report = {"kind": "stock Android emulator / installed components, NOT an AOSP image", "serial": args.serial,
                  "screen": size, "installed": installed, "checks": checks, "passed": failure is None, "failure": failure,
                  "renderer": renderer, "recorderErrors": recording_errors}
        (args.output / "verification.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if failure:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
