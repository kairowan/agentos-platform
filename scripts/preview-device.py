#!/usr/bin/env python3
"""Explicit-device installation and restoration. Never grants roles or erases data silently."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

PACKAGES = ("com.agentos.capability", "com.agentos.media", "com.agentos.shell")
ROLES = ("android.app.role.HOME", "android.app.role.DIALER", "android.app.role.SMS")


def verify(bundle):
    manifest = json.loads((bundle / "manifest.json").read_text())
    if tuple(apk["package"] for apk in manifest["apks"]) != PACKAGES:
        raise ValueError("Unexpected packages or package order")
    for apk in manifest["apks"]:
        if not re.fullmatch(r"[a-zA-Z0-9_.-]+\.apk", apk["file"]):
            raise ValueError("Unsafe APK filename")
        digest = hashlib.sha256((bundle / apk["file"]).read_bytes()).hexdigest()
        if digest != apk["sha256"]:
            raise ValueError(f"Checksum mismatch: {apk['file']}")
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("verify", "install", "launch", "restore", "uninstall"))
    parser.add_argument("--serial", help="Explicit adb serial; required for every device action")
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--bundle", type=Path, default=Path(__file__).resolve().parent)
    parser.add_argument("--state", type=Path, help="Path to saved original defaults; required for install/restore/uninstall")
    parser.add_argument("--erase-local-data", action="store_true", help="Acknowledge uninstall removes AgentOS history/settings")
    args = parser.parse_args()
    if args.action == "uninstall" and not args.erase_local_data:
        parser.error("Uninstall needs --erase-local-data. Back up AgentOS local history/settings first")
    manifest = verify(args.bundle)
    if args.action == "verify":
        print(f"Verified all three APK hashes: {manifest['version']}")
        return
    if not args.serial or not re.fullmatch(r"[a-zA-Z0-9_.:-]+", args.serial):
        parser.error("Specify a valid --serial; there is deliberately no default device")

    def adb(*command):
        return subprocess.check_output([args.adb, "-s", args.serial, *command], text=True, stderr=subprocess.STDOUT, timeout=180).strip()

    if adb("get-state") != "device":
        raise RuntimeError("Device is not connected and authorized")
    user = adb("shell", "am", "get-current-user")
    if not user.isdigit():
        raise RuntimeError("Cannot determine Android user")

    def holders(role):
        result = adb("shell", "cmd", "role", "get-role-holders", "--user", user, role)
        packages = result.splitlines() if result else []
        if any(not re.fullmatch(r"[a-zA-Z0-9_.]+", package) for package in packages):
            raise RuntimeError("Unable to read default roles; refusing to guess")
        return packages

    if args.action == "launch":
        print(adb("shell", "am", "start", "-W", "-n", "com.agentos.shell/.MainActivity"))
        return
    if args.state is None:
        parser.error("--state is required to preserve/restore original defaults")
    if args.action == "install":
        if args.state.exists():
            state = json.loads(args.state.read_text())
            if state["serial"] != args.serial or state["user"] != user:
                raise RuntimeError("State file belongs to a different device/user")
        else:
            state = {"serial": args.serial, "user": user, "roles": {role: holders(role) for role in ROLES}}
            args.state.parent.mkdir(parents=True, exist_ok=True)
            with args.state.open("x") as stream:
                json.dump(state, stream, indent=2)
        print(adb("install-multi-package", "-r", "--user", user, *[str(args.bundle / apk["file"]) for apk in manifest["apks"]]))
        print("Installed atomically. No role, default desktop, or runtime permission was changed. Use 'launch' next.")
        return
    state = json.loads(args.state.read_text())
    if state["serial"] != args.serial or state["user"] != user:
        raise RuntimeError("Saved defaults do not match this device/user")
    for role in ROLES:
        current = holders(role)
        if not any(package in PACKAGES for package in current):
            continue # Do not overwrite a choice the user made after installation.
        original = state["roles"].get(role, [])
        if len(original) != 1 or original[0] in PACKAGES or not re.fullmatch(r"[a-zA-Z0-9_.]+", original[0]):
            raise RuntimeError("No unambiguous original default. Restore manually in Android Settings before uninstalling")
        adb("shell", "cmd", "role", "add-role-holder", "--user", user, role, original[0], "0")
        if holders(role) != original:
            raise RuntimeError("Role restoration failed; nothing will be uninstalled")
    if args.action == "restore":
        print("Original roles restored where AgentOS was still selected. Apps and data retained.")
        return
    for package in reversed(PACKAGES):
        print(adb("shell", "pm", "uninstall", "--user", user, package))
    print("Removed the three AgentOS APKs and their private data. This script cannot undo that deletion; system SMS records were not explicitly deleted.")


if __name__ == "__main__":
    main()
