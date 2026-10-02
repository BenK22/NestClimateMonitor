#!/usr/bin/env python3
"""Check publishable Git content without displaying sensitive matched values."""

import argparse
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GIT = ["git", "-c", f"safe.directory={ROOT.as_posix()}"]
PATTERNS = {
    "OAuth client secret": rb"\bGOCSPX-[A-Za-z0-9_-]{16,}",
    "personal Google OAuth client ID": rb"\b\d{6,}-[A-Za-z0-9_-]{10,}\.apps\.googleusercontent\.com",
    "API or access key": rb"\b(?:AIza[A-Za-z0-9_-]{30,}|gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[A-Z0-9]{16})\b",
    "private key": rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----",
    "private Windows user path": rb"(?i)[A-Z]:[\\/]+Users[\\/]+[^\\/\s\"']+",
}
EMAIL = re.compile(rb"[A-Za-z0-9._%+-]+@([A-Za-z0-9.-]+\.[A-Za-z]{2,})")
LOCAL_DIRS = {".beads", ".aws", ".ssh", ".codex", ".agents", ".private-backups", ".home-sdk-repo"}
LOCAL_SUFFIXES = {".apk", ".aab", ".jks", ".keystore", ".pem", ".key", ".p12", ".pfx", ".bundle", ".db", ".sqlite", ".sqlite3", ".csv", ".log"}


def git(*args):
    return subprocess.check_output(GIT + list(args), cwd=ROOT)


def public_email(domain):
    value = domain.decode("ascii").lower()
    return value in {"example.com", "example.org", "example.net", "example.invalid"} or value.endswith(".example.invalid") or value == "users.noreply.github.com"


def local_path(path):
    parts = Path(path).parts
    name = Path(path).name.lower()
    return (
        any(part in LOCAL_DIRS for part in parts)
        or Path(path).suffix.lower() in LOCAL_SUFFIXES
        or name in {"local.properties", "secrets.properties", "credentials.properties", "keystore.properties", "google-services.json"}
        or (name.startswith(".env") and name != ".env.example")
        or re.match(r"(?:credentials|oauth|token).*\.json$", name)
        or "service-account" in name
        or re.search(r"\.local\.(?:properties|json|ya?ml)$", name)
    )


def blobs(entries):
    """Read all object contents in one process; never print their contents."""
    request = b"".join(oid.encode("ascii") + b"\n" for oid, _ in entries)
    data = subprocess.check_output(GIT + ["cat-file", "--batch"], input=request, cwd=ROOT)
    offset = 0
    for _, path in entries:
        end = data.index(b"\n", offset)
        header = data[offset:end].split()
        size = int(header[2])
        content = data[end + 1:end + 1 + size]
        offset = end + 2 + size
        if header[1] == b"blob":
            yield path, content


def check_content(label, content, findings):
    # Compressed image data may resemble text; images need visual review.
    if b"\0" in content:
        return
    for category, pattern in PATTERNS.items():
        if re.search(pattern, content):
            findings.add((label, category))
    if any(not public_email(match[1]) for match in EMAIL.finditer(content)):
        findings.add((label, "personal email address"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--history", action="store_true", help="Also check all reachable commits and file versions")
    args = parser.parse_args()
    findings = set()
    entries = []
    for row in git("ls-files", "--stage", "-z").split(b"\0"):
        if not row:
            continue
        metadata, name = row.split(b"\t", 1)
        path = name.decode("utf-8", errors="replace")
        entries.append((metadata.split()[1].decode("ascii"), path))
    if args.history:
        for row in git("rev-list", "--objects", "--all").decode("utf-8", errors="replace").splitlines():
            oid, _, path = row.partition(" ")
            if path:
                entries.append((oid, path))
    path_input = b"".join(path.encode("utf-8") + b"\0" for path in sorted({path for _, path in entries}))
    ignored = subprocess.run(GIT + ["check-ignore", "--no-index", "-z", "--stdin"], input=path_input, stdout=subprocess.PIPE, check=False, cwd=ROOT)
    if ignored.returncode not in (0, 1):
        raise subprocess.CalledProcessError(ignored.returncode, ignored.args)
    ignored_paths = {path.decode("utf-8", errors="replace") for path in ignored.stdout.split(b"\0") if path}
    for path, content in blobs(sorted(set(entries))):
        if path in ignored_paths or local_path(path):
            findings.add((path, "local-only file in Git"))
        check_content(path, content, findings)
    if args.history:
        for row in git("log", "--all", "--format=%h%x09%an%x09%ae%x09%cn%x09%ce").decode("utf-8", errors="replace").splitlines():
            oid, author, author_email, committer, committer_email = row.split("\t")
            for name, email in [(author, author_email), (committer, committer_email)]:
                domain = email.rpartition("@")[2].encode("ascii", errors="replace")
                if not public_email(domain):
                    findings.add((f"commit {oid}", "personal commit email"))
                if domain != b"users.noreply.github.com" and name != "Home Climate Monitor contributors":
                    findings.add((f"commit {oid}", "review public contributor name"))
        messages = git("log", "--all", "--format=%h%x00%B%x00").split(b"\0")
        for index in range(0, len(messages) - 1, 2):
            label = "commit " + messages[index].strip().decode("ascii")
            check_content(label, messages[index + 1], findings)
    for path, category in sorted(findings):
        print(f"FAIL: {path}: {category}")
    if findings:
        print(f"Publication check failed: {len(findings)} finding(s). Matched values are hidden.")
        return 1
    print("Publication check passed: no flagged content in " + ("tracked files and reachable history." if args.history else "tracked files."))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (subprocess.CalledProcessError, ValueError) as error:
        print(f"Publication check could not complete ({type(error).__name__}).", file=sys.stderr)
        sys.exit(2)
