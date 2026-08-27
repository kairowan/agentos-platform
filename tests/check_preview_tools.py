#!/usr/bin/env python3
"""Small stdlib regression check for bundle integrity and device safety."""
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("preview_device", ROOT / "scripts/preview-device.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

with tempfile.TemporaryDirectory(prefix="agentos-preview-check-") as temporary:
    bundle = Path(temporary)
    apks = []
    for index, package in enumerate(module.PACKAGES):
        name = f"part-{index}.apk"
        (bundle / name).write_bytes(b"fixture")
        apks.append({"file": name, "package": package, "sha256": hashlib.sha256(b"fixture").hexdigest()})
    (bundle / "manifest.json").write_text(json.dumps({"version": "test", "apks": apks}))
    assert module.verify(bundle)["version"] == "test"
    (bundle / apks[0]["file"]).write_bytes(b"tampered")
    try:
        module.verify(bundle)
        raise AssertionError("Tampered APK accepted")
    except ValueError:
        pass
    (bundle / apks[0]["file"]).write_bytes(b"fixture")
    run = subprocess.run(["python3", str(ROOT / "scripts/preview-device.py"), "install", "--bundle", str(bundle)], capture_output=True, text=True)
    assert run.returncode != 0 and "--serial" in run.stderr
    run = subprocess.run(["python3", str(ROOT / "scripts/preview-device.py"), "uninstall", "--bundle", str(bundle)], capture_output=True, text=True)
    assert run.returncode != 0 and "--erase-local-data" in run.stderr

approved_images = {
    "thought-field-home-concept.png", "thought-field-studio-concept.png",
    "app-bridge-v2.png", "camera-v2.png", "knowledge-v2.png",
}
status_badges = {
    "https://github.com/kairowan/agentos-platform/actions/workflows/ci.yml/badge.svg",
    "https://img.shields.io/github/v/release/kairowan/agentos-platform?include_prereleases",
}
public_media = {path.relative_to(ROOT / "docs/images").as_posix()
                for path in (ROOT / "docs/images").rglob("*")
                if path.suffix.lower() in {".png", ".jpg", ".jpeg", ".webp", ".gif", ".svg", ".mp4", ".webm"}}
assert public_media == {f"ui-v2/{name}" for name in approved_images}, "Unapproved public visual; keep runtime media in artifacts/"
for document in [ROOT / "README.md", *(ROOT / "docs").rglob("*.md")]:
    for target in re.findall(r"!\[[^\]]*\]\(([^)]+)\)", document.read_text()):
        assert target in status_badges or target.rsplit("/", 1)[-1] in approved_images, f"Unapproved documentation image: {document}: {target}"
workflow = (ROOT / ".github/workflows/demo.yml").read_text()
upload = re.search(r"(?m)^          path: \|\n((?: {12}[^\n]+\n)+)", workflow)
assert upload, "Preview artifact upload list not found"
assert set(upload.group(1).split()) == {
    "artifacts/AgentOS-*.zip", "artifacts/preview-recording/verification.json",
    "artifacts/room-check.txt", "artifacts/emulator.log",
}, "Preview upload must not include runtime images, recordings or whole directories"
assert "${project_root}/artifacts/renderer-check/thought-field.png" in (ROOT / "scripts/capture-thought-field-preview.sh").read_text()

print("Preview integrity, device safety and public visual publication checks passed")
