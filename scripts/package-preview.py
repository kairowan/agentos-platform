#!/usr/bin/env python3
"""Package one already-built revision; never mix APKs from separate CI jobs."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MODULES = ("services/AgentCapabilityService", "services/AgentMediaService", "apps/AgentShell")


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def main():
    metadata = [json.loads((ROOT / module / "build/outputs/apk/debug/output-metadata.json").read_text()) for module in MODULES]
    versions = {(item["elements"][0]["versionCode"], item["elements"][0]["versionName"]) for item in metadata}
    if len(versions) != 1:
        raise SystemExit("Refusing mixed-version APKs; rebuild all modules together")
    version_code, version = versions.pop()
    output = ROOT / "artifacts" / f"AgentOS-{version}"
    output.mkdir(parents=True, exist_ok=True)
    apks = []
    for module, meta in zip(MODULES, metadata):
        name = meta["elements"][0]["outputFile"]
        source = ROOT / module / "build/outputs/apk/debug" / name
        shutil.copy2(source, output / name)
        apks.append({"file": name, "package": meta["applicationId"], "sha256": sha256(source)})
    for source, name in ((ROOT / "scripts/preview-device.py", "preview-device.py"), (ROOT / "docs/developer-preview.md", "README.md")):
        shutil.copy2(source, output / name)
    manifest = {
        "version": version, "versionCode": version_code,
        "commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
        "dirty": bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT, text=True).strip()),
        "kind": "Android component preview, NOT an AOSP system image", "apks": apks,
    }
    (output / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    files = [output / apk["file"] for apk in apks] + [output / "README.md", output / "preview-device.py", output / "manifest.json"]
    (output / "SHA256SUMS").write_text("".join(f"{sha256(path)}  {path.name}\n" for path in files))
    archive = output.parent / f"AgentOS-{version}.zip"
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as bundle:
        for path in files + [output / "SHA256SUMS"]:
            bundle.write(path, f"{output.name}/{path.name}")
    print(archive)


if __name__ == "__main__":
    main()
