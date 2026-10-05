#!/usr/bin/env python3
"""Verify an official APK and produce consistent filename/checksum release assets."""

import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
CERTIFICATE_SHA256 = "1e4d886d87b17a86ad086a043c497425d84a2fc782d8b3cb46aaecdf7fa04bbc"


def notice_paths(resource_dump):
    """Resolve named raw resources after AAPT shortens release ZIP paths."""
    paths = []
    for name in ("third_party_licenses", "third_party_license_metadata"):
        match = re.search(
            rf'^\s*resource 0x[0-9a-f]+ ca\.humiditylogger:raw/{name}:[^\r\n]+\r?\n\s+\(string8\) "([^"]+)"',
            resource_dump, re.MULTILINE,
        )
        if not match:
            raise ValueError("APK raw license resources are missing from its resource table")
        paths.append(match[1])
    return paths


def verify_notices(apk, resource_dump=None):
    """Require non-empty generated license text and library index in the shipped APK."""
    with zipfile.ZipFile(apk) as zipped:
        paths = notice_paths(resource_dump) if resource_dump is not None else (
            "res/raw/third_party_licenses", "res/raw/third_party_license_metadata",
        )
        for name in paths:
            if not zipped.read(name).strip():
                raise ValueError("APK license resources are empty")
        text = zipped.read(paths[0])
        if b"Licenses are only provided in build variants" in text:
            raise ValueError("APK contains a debug placeholder instead of real license notices")
        metadata = zipped.read(paths[1]).decode("utf-8")
        for line in metadata.splitlines():
            match = re.fullmatch(r"(\d+):(\d+) (.+)", line)
            if not match or int(match[2]) == 0 or int(match[1]) + int(match[2]) > len(text):
                raise ValueError("APK license index has an invalid text range")


def main():
    """Check official signer and variant, then copy the APK and write its SHA-256 companion."""
    text = (ROOT / "app/build.gradle.kts").read_text()
    version = re.search(r'versionName = "([0-9]+\.[0-9]+\.[0-9]+)"', text).group(1)
    version_code = re.search(r"versionCode = ([0-9]+)", text).group(1)
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or "")
    if not sdk.is_dir() or not (sdk / "build-tools").is_dir():
        raise ValueError("Set ANDROID_HOME to the Android SDK directory")
    tool_dirs = sorted((sdk / "build-tools").iterdir(), key=lambda p: tuple(int(x) for x in re.findall(r"\d+", p.name)))
    tools = next((p for p in reversed(tool_dirs) if (p / ("apksigner.bat" if os.name == "nt" else "apksigner")).exists()), None)
    if tools is None:
        raise ValueError("Android apksigner is unavailable")
    apk = ROOT / "app/build/outputs/apk/release/app-release.apk"
    signer = tools / ("apksigner.bat" if os.name == "nt" else "apksigner")
    aapt = tools / ("aapt.exe" if os.name == "nt" else "aapt")
    signature = subprocess.check_output([str(signer), "verify", "--verbose", "--print-certs", str(apk)], text=True, encoding="utf-8")
    fingerprints = re.findall(r"certificate SHA-256 digest: ([0-9a-f]+)", signature)
    if fingerprints != [CERTIFICATE_SHA256]:
        raise ValueError("APK is not signed solely by the permanent official certificate")
    badging = subprocess.check_output([str(aapt), "dump", "badging", str(apk)], text=True, encoding="utf-8")
    if ("package: name='ca.humiditylogger'" not in badging or f"versionName='{version}'" not in badging
            or f"versionCode='{version_code}'" not in badging or "application-debuggable" in badging):
        raise ValueError("APK package/version/debug state does not match the official release")
    resource_dump = subprocess.check_output([str(aapt), "dump", "--values", "resources", str(apk)], text=True, encoding="utf-8")
    verify_notices(apk, resource_dump)
    destination = ROOT / "app/build/release-assets"
    destination.mkdir(parents=True, exist_ok=True)
    name = f"NestClimateMonitor-v{version}.apk"
    if any(p.name not in {name, name + ".sha256"} for p in destination.iterdir()):
        raise ValueError("Release asset directory contains unexpected files")
    shutil.copyfile(apk, destination / name)
    with apk.open("rb") as stream:
        checksum = hashlib.file_digest(stream, "sha256").hexdigest()
    (destination / (name + ".sha256")).write_text(checksum + "  " + name + "\n", encoding="ascii")
    print("Verified release signer/package/notices and packaged " + name + " with SHA-256 " + checksum)


if __name__ == "__main__":
    main()
