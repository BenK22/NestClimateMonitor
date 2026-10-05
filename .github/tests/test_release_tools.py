"""Offline regression tests for release inputs, notices and workflow safeguards."""

import contextlib
import hashlib
import importlib.util
import io
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import tempfile
import unittest
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def load_script(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / ".github/scripts" / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


sdk = load_script("install-home-sdk")
release = load_script("package-release")
publication = load_script("check-publication")


class ArchiveTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.archive = self.directory / "sdk.zip"

    def make_archive(self, extra=None, missing=False):
        with zipfile.ZipFile(self.archive, "w") as zipped:
            if not missing:
                for artifact in ("play-services-home", "play-services-home-types"):
                    prefix = f"com/google/android/gms/{artifact}/17.1.0/{artifact}-17.1.0"
                    for suffix in (".aar", ".pom"):
                        zipped.writestr(prefix + suffix, "synthetic test artifact")
            if extra is not None:
                # zipfile normalizes Windows backslashes in ZipInfo's constructor.
                # Assign afterward to emulate a malicious ZIP produced on another platform.
                item = extra if isinstance(extra, zipfile.ZipInfo) else zipfile.ZipInfo(extra)
                if isinstance(extra, str):
                    item.filename = extra
                zipped.writestr(item, "not an SDK")
        return hashlib.sha256(self.archive.read_bytes()).hexdigest()

    def test_verified_archive_installs_expected_artifacts(self):
        checksum = self.make_archive()
        target = self.directory / "maven"
        with contextlib.redirect_stdout(io.StringIO()):
            sdk.install(self.archive, target, checksum)
        self.assertEqual(4, sum(p.is_file() for p in target.rglob("*")))

    def test_checksum_mismatch_does_not_create_destination(self):
        self.make_archive()
        target = self.directory / "maven"
        with self.assertRaisesRegex(ValueError, "checksum mismatch"):
            sdk.install(self.archive, target, "0" * 64)
        self.assertFalse(target.exists())

    def test_invalid_checksum_is_rejected(self):
        self.make_archive()
        for value in ("", "0" * 63, "g" * 64):
            with self.subTest(value=value), self.assertRaises(ValueError):
                sdk.verify_archive(self.archive, value)

    def test_missing_maven_artifacts_are_rejected(self):
        checksum = self.make_archive(missing=True)
        with self.assertRaisesRegex(ValueError, "required Maven artifacts"):
            sdk.verify_archive(self.archive, checksum)

    def test_unsafe_paths_rejected_before_any_files_written(self):
        for path in ("../escape", "/absolute", "C:/drive", "folder\\escape", "folder/../../escape"):
            with self.subTest(path=path):
                checksum = self.make_archive(extra=path)
                target = self.directory / "maven"
                with self.assertRaisesRegex(ValueError, "unsafe path"):
                    sdk.install(self.archive, target, checksum)
                self.assertFalse(target.exists())

    def test_symlink_rejected(self):
        link = zipfile.ZipInfo("link")
        link.create_system = 3
        link.external_attr = (stat.S_IFLNK | 0o777) << 16
        checksum = self.make_archive(extra=link)
        with self.assertRaisesRegex(ValueError, "unsafe path"):
            sdk.verify_archive(self.archive, checksum)

    def test_archive_size_limit_is_enforced(self):
        checksum = self.make_archive()
        original = sdk.MAX_ARCHIVE_BYTES
        try:
            sdk.MAX_ARCHIVE_BYTES = 1
            with self.assertRaisesRegex(ValueError, "size limit"):
                sdk.verify_archive(self.archive, checksum)
        finally:
            sdk.MAX_ARCHIVE_BYTES = original


class RedirectTests(unittest.TestCase):
    def request(self):
        return urllib.request.Request("https://sdk.example/archive", headers={"Authorization": "Bearer synthetic"})

    def redirect(self, url):
        return sdk.SafeRedirect().redirect_request(self.request(), None, 302, "Found", {}, url)

    def test_same_host_keeps_authorization(self):
        self.assertEqual("Bearer synthetic", self.redirect("https://sdk.example/next").get_header("Authorization"))

    def test_other_host_drops_authorization(self):
        self.assertIsNone(self.redirect("https://other.example/next").get_header("Authorization"))

    def test_http_redirect_is_rejected(self):
        with self.assertRaises(ValueError):
            self.redirect("http://sdk.example/next")


class NoticeTests(unittest.TestCase):
    def test_release_paths_resolved_from_resource_table(self):
        dump = (' resource 0x7f000001 ca.humiditylogger:raw/third_party_licenses: t=0x03\n'
                '   (string8) "res/a"\n'
                ' resource 0x7f000002 ca.humiditylogger:raw/third_party_license_metadata: t=0x03\n'
                '   (string8) "res/b"\n')
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "fixture.apk"
            with zipfile.ZipFile(apk, "w") as zipped:
                zipped.writestr("res/a", b"license text")
                zipped.writestr("res/b", b"0:12 Library")
            release.verify_notices(apk, dump)
        with self.assertRaises(ValueError):
            release.notice_paths("")

    def test_debug_placeholder_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "fixture.apk"
            with zipfile.ZipFile(apk, "w") as zipped:
                zipped.writestr("res/raw/third_party_licenses", b"Licenses are only provided in build variants")
                zipped.writestr("res/raw/third_party_license_metadata", b"0:12 Placeholder")
            with self.assertRaisesRegex(ValueError, "placeholder"):
                release.verify_notices(apk)

    def test_invalid_license_range_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "fixture.apk"
            for metadata in ("bad record", "0:0 Empty", "1:100 Out of bounds"):
                with zipfile.ZipFile(apk, "w") as zipped:
                    zipped.writestr("res/raw/third_party_licenses", b"license")
                    zipped.writestr("res/raw/third_party_license_metadata", metadata)
                with self.subTest(metadata=metadata), self.assertRaises(ValueError):
                    release.verify_notices(apk)

    def test_both_notice_resources_must_be_nonempty(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "fixture.apk"
            for value in (b"license text", b"", b" \n"):
                with zipfile.ZipFile(apk, "w") as zipped:
                    zipped.writestr("res/raw/third_party_licenses", value)
                    zipped.writestr("res/raw/third_party_license_metadata", b"0:12 Library")
                if value.strip():
                    release.verify_notices(apk)
                else:
                    with self.assertRaises(ValueError):
                        release.verify_notices(apk)

    def test_missing_notices_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "fixture.apk"
            with zipfile.ZipFile(apk, "w"):
                pass
            with self.assertRaises(KeyError):
                release.verify_notices(apk)


class WorkflowTests(unittest.TestCase):
    def test_release_smoke_package_is_isolated_and_not_instrumented(self):
        text = (ROOT / "app/build.gradle.kts").read_text()
        block = re.search(r'create\("releaseSmoke"\)\s*\{([^}]+)\}', text)
        self.assertIsNotNone(block)
        self.assertIn('initWith(getByName("release"))', block[1])
        self.assertIn('applicationIdSuffix = ".releasesmoke"', block[1])
        self.assertIn('versionNameSuffix = "-release-smoke"', block[1])
        self.assertIn('variantBuilder.buildType == "deviceTest"', text)

    def test_apk_metadata_opt_out_keeps_license_inventory_enabled(self):
        text = (ROOT / "app/build.gradle.kts").read_text()
        block = re.search(r"dependenciesInfo\s*\{([^}]+)\}", text)
        self.assertIsNotNone(block)
        self.assertRegex(block[1], r"includeInApk\s*=\s*false")
        self.assertRegex(block[1], r"includeInBundle\s*=\s*true")
        self.assertIn('id("com.google.android.gms.oss-licenses-plugin")', text)

    def test_workflows_use_sdk_helper_not_path_assumption(self):
        for name in ("android-check.yml", "android-release.yml"):
            text = (ROOT / ".github/workflows" / name).read_text()
            self.assertIn("bash .github/scripts/install-android-components.sh", text)
            self.assertNotIn('run: sdkmanager ', text)

    def test_all_external_actions_use_full_commit_pins(self):
        for path in (ROOT / ".github/workflows").glob("*.yml"):
            for action in re.findall(r"uses:\s*([^\s#]+)", path.read_text()):
                with self.subTest(workflow=path.name, action=action):
                    self.assertRegex(action, r"^[\w/-]+@[0-9a-f]{40}$")

    def test_pr_workflows_are_secret_free_and_read_only(self):
        for name in ("android-check.yml", "publication-check.yml"):
            text = (ROOT / ".github/workflows" / name).read_text()
            self.assertNotIn("secrets.", text)
            self.assertNotIn("pull_request_target", text)
            self.assertIn("contents: read", text)
            self.assertNotIn("contents: write", text)
            self.assertIn("persist-credentials: false", text)
        self.assertIn("-PincludeGoogleHome=false", (ROOT / ".github/workflows/android-check.yml").read_text())

    def test_signing_job_has_no_publish_permission(self):
        text = (ROOT / ".github/workflows/android-release.yml").read_text()
        build, publish = text.split("\n  publish:", 1)
        self.assertNotIn("contents: write", build)
        self.assertIn("contents: write", publish)
        self.assertNotIn("secrets.", publish)
        self.assertIn("--prerelease", publish)

    def test_sdk_and_wrapper_checksums_are_hex_sha256(self):
        self.assertRegex((ROOT / "gradle/home-sdk.sha256").read_text().strip(), r"^[0-9a-f]{64}$")
        wrapper = (ROOT / "gradle/wrapper/gradle-wrapper.properties").read_text()
        self.assertRegex(wrapper, r"distributionSha256Sum=[0-9a-f]{64}")
        self.assertIn("validateDistributionUrl=true", wrapper)


class PublicationBotTests(unittest.TestCase):
    def test_public_dependabot_trailer_is_allowed(self):
        findings = set()
        publication.check_content("fixture", b"Signed-off-by: dependabot[bot] <support@github.com>", findings)
        self.assertEqual(set(), findings)

    def test_allowlist_does_not_allow_other_github_or_personal_addresses(self):
        for domain in (b"github.com", b"private.invalid"):
            # Construct synthetic rejected addresses without publishing an email literal.
            email = b"synthetic-person" + bytes([64]) + domain
            findings = set()
            publication.check_content("fixture", email, findings)
            self.assertIn(("fixture", "personal email address"), findings)


class ReleaseInputTests(unittest.TestCase):
    def run_validation(self, ref, missing=None):
        bash = (Path("C:/Program Files/Git/bin/bash.exe") if os.name == "nt"
                else Path(shutil.which("bash") or "/unavailable"))
        if not bash.is_file():
            self.skipTest("Bash unavailable")
        env = dict(os.environ, GITHUB_REF=ref)
        for name in ("HOME_SDK_URL", "RELEASE_KEYSTORE_BASE64", "RELEASE_STORE_PASSWORD",
                     "RELEASE_KEY_ALIAS", "RELEASE_KEY_PASSWORD"):
            env[name] = "synthetic-private-input"
        if missing:
            env.pop(missing)
        # read_text normalizes CRLF in Windows working copies; Git attributes keep
        # committed shell scripts LF for Linux runners.
        script = (ROOT / ".github/scripts/validate-release.sh").read_text()
        result = subprocess.run([str(bash), "-s"], input=script, text=True, encoding="utf-8",
                                cwd=ROOT, env=env, capture_output=True, timeout=20)
        self.assertNotIn("synthetic-private-input", result.stdout + result.stderr)
        return result

    def test_manual_signing_accepts_main_only(self):
        self.assertEqual(0, self.run_validation("refs/heads/main").returncode)
        self.assertNotEqual(0, self.run_validation("refs/heads/contributor").returncode)

    def test_missing_input_rejected_without_echoing_values(self):
        result = self.run_validation("refs/heads/main", missing="RELEASE_KEY_PASSWORD")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Missing RELEASE_KEY_PASSWORD", result.stdout)

    def test_tag_must_match_version_and_have_release_notes(self):
        text = (ROOT / "app/build.gradle.kts").read_text()
        version = re.search(r'versionName = "([^"]+)"', text)[1]
        self.assertEqual(0, self.run_validation("refs/tags/v" + version).returncode)
        self.assertNotEqual(0, self.run_validation("refs/tags/v0.0.0-test").returncode)


if __name__ == "__main__":
    unittest.main()
