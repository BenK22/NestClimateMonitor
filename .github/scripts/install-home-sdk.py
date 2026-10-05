#!/usr/bin/env python3
"""Verify the reviewed SDK archive before installing its Maven repository.

Downloads use environment-only URL/token inputs so expiring URLs and credentials
are never stored in Git or printed. The expected archive checksum is public.
"""

import argparse
import hashlib
import os
from pathlib import Path, PurePosixPath
import shutil
import stat
import sys
import tempfile
import urllib.parse
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[2]
MAX_ARCHIVE_BYTES = 256 * 1024 * 1024


class SafeRedirect(urllib.request.HTTPRedirectHandler):
    """Reject insecure redirects and avoid forwarding bearer tokens to another host."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        if urllib.parse.urlparse(newurl).scheme != "https":
            raise ValueError("SDK download redirect must use HTTPS")
        redirected = super().redirect_request(req, fp, code, msg, headers, newurl)
        if redirected and urllib.parse.urlparse(newurl).netloc != urllib.parse.urlparse(req.full_url).netloc:
            redirected.remove_header("Authorization")
        return redirected


def verify_archive(archive, expected):
    """Validate SHA-256 and portable safe archive paths; return the normalized checksum."""
    expected = expected.strip().lower()
    if len(expected) != 64 or any(c not in "0123456789abcdef" for c in expected):
        raise ValueError("SDK checksum must be a SHA-256 hex value")
    if archive.stat().st_size > MAX_ARCHIVE_BYTES:
        raise ValueError("SDK archive exceeds the size limit")
    with archive.open("rb") as stream:
        actual = hashlib.file_digest(stream, "sha256").hexdigest()
    if actual != expected:
        raise ValueError("SDK checksum mismatch; refusing installation")
    with zipfile.ZipFile(archive) as zipped:
        if sum(item.file_size for item in zipped.infolist()) > MAX_ARCHIVE_BYTES * 4:
            raise ValueError("SDK expanded archive exceeds the size limit")
        for item in zipped.infolist():
            # Windows zipfile normalizes backslashes in filename; orig_filename
            # retains the actual archive header and must also be checked.
            raw_name = item.orig_filename
            path = PurePosixPath(raw_name)
            if (path.is_absolute() or ".." in path.parts or "\\" in raw_name
                    or ":" in raw_name or "\x00" in raw_name or stat.S_ISLNK(item.external_attr >> 16)):
                raise ValueError("SDK archive contains an unsafe path")
        for artifact in ("play-services-home", "play-services-home-types"):
            prefix = f"com/google/android/gms/{artifact}/17.1.0/{artifact}-17.1.0"
            if any(prefix + suffix not in zipped.namelist() for suffix in (".aar", ".pom")):
                raise ValueError("SDK archive does not contain the required Maven artifacts")
    return actual


def download(destination):
    """Fetch an HTTPS SDK ZIP with a bounded response and without printing URL/token values."""
    url = os.environ.get("HOME_SDK_URL", "")
    if urllib.parse.urlparse(url).scheme != "https":
        raise ValueError("HOME_SDK_URL must be an HTTPS URL")
    headers = {"User-Agent": "NestClimateMonitor-SDK-install"}
    if os.environ.get("HOME_SDK_TOKEN"):
        headers["Authorization"] = "Bearer " + os.environ["HOME_SDK_TOKEN"]
    opener = urllib.request.build_opener(SafeRedirect())
    with opener.open(urllib.request.Request(url, headers=headers), timeout=60) as response:
        with destination.open("wb") as output:
            total = 0
            while chunk := response.read(1024 * 1024):
                total += len(chunk)
                if total > MAX_ARCHIVE_BYTES:
                    raise ValueError("SDK download exceeds the size limit")
                output.write(chunk)


def install(archive, destination, expected):
    """Verify first, then copy the reviewed archive files into the ignored target."""
    checksum = verify_archive(archive, expected)
    destination.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive) as zipped:
        for item in zipped.infolist():
            target = destination / item.filename
            if item.is_dir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with zipped.open(item) as source, target.open("wb") as output:
                    shutil.copyfileobj(source, output)
    print("Home SDK archive verified and installed (SHA-256 " + checksum + ").")


def main():
    """Install a local archive, or download one using private environment inputs."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", nargs="?", type=Path)
    args = parser.parse_args()
    checksum = (ROOT / "gradle/home-sdk.sha256").read_text()
    if args.archive:
        install(args.archive, ROOT / ".home-sdk-repo", checksum)
    else:
        with tempfile.TemporaryDirectory(prefix="nest-home-sdk-") as directory:
            archive = Path(directory) / "sdk.zip"
            download(archive)
            install(archive, ROOT / ".home-sdk-repo", checksum)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        # Exception strings can contain expiring download URLs; do not echo them.
        print("SDK installation failed (" + type(error).__name__ + "). Check archive checksum, version and private download inputs.", file=sys.stderr)
        sys.exit(1)
