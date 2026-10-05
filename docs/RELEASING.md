# Releasing

## Local signed releases

The first release is built and signed locally, then uploaded as a GitHub prerelease together with its `.apk.sha256` checksum. Release APKs use a permanent private signing key; the keystore and its password configuration remain in ignored local storage and must be backed up securely outside the checkout. Never regenerate the signing key for an update.

Set `RELEASE_KEYSTORE_PATH`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD` in the build environment, then run:

```powershell
.\gradlew.bat testDebugUnitTest lintRelease assembleRelease --console=plain
```

Set `ANDROID_HOME` to the installed Android SDK, then run:

```powershell
python -B .github/scripts/package-release.py
```

The helper verifies the permanent official signing certificate, package, versionName/versionCode,
non-debuggable state and actual generated license resources (not the debug placeholder). It creates
`app/build/release-assets/NestClimateMonitor-v<version>.apk` and its `.apk.sha256` companion. Upload
those verified assets together; do not rename them inconsistently between local and automated builds.
Tag the exact reviewed source commit used for the build. Source archives do not include the
downloaded Google Home SDK or private signing inputs. Forks must establish their own identity,
signing policy and certificate validation; do not reuse the official release name/certificate claim.

Debug and release certificates differ. Existing debug users must export their readings before uninstalling that build, installing the release, restoring CSV history, and reconnecting Google; this process must never be performed automatically on a tester's phone.

## Optional GitHub Actions automation

### Public release certificate

Official APK updates use this certificate. These fingerprints are public identifiers, not signing secrets:

- SHA-1 (for the optional Google Home Android OAuth client): `FD:F6:4D:79:2D:B1:E6:80:E4:B9:10:AE:19:12:64:BE:DB:5E:E5:60`
- SHA-256: `1E:4D:88:6D:87:B1:7A:86:AD:08:6A:04:3C:49:74:25:D8:4A:2F:C7:82:D8:B3:CB:46:AA:EC:DF:7F:A0:4B:BC`

### Automation setup

Google distributes the Home APIs Android SDK as a signed-in download, so it must not be committed. The release workflow requires these repository secrets:

- `HOME_SDK_URL`: a private or expiring URL for the unmodified Home SDK ZIP;
- `HOME_SDK_TOKEN`: optional bearer token for that URL;
- `RELEASE_KEYSTORE_BASE64`: the release JKS encoded with base64;
- `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD`.

Keep the same release key permanently. The Android OAuth client must contain the SHA-1 fingerprint of that release certificate.

The SDK installer requires HTTPS, limits archive size, drops bearer authorization on cross-host
redirects and rejects unsafe archive paths. It verifies `gradle/home-sdk.sha256` before extracting
into `.home-sdk-repo`. This hash is the exact locally reviewed SDK 1.10.1 archive, not an independent
vendor signature. Review a new SDK's provenance and terms before changing the hash or Maven versions.
The Gradle distribution checksum and external action commit pins are also reviewed build inputs.
Do not publish the downloaded archive or configure signing/download secrets for pull-request jobs.

Before tagging, run the same local quality gates used during development:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug connectedDeviceTestAndroidTest --console=plain
```

The connected-device tests use the isolated `androidTestDeviceTest` package and do not overwrite the installed app's readings or credentials. They require an attached unlocked Android device; GitHub's release job runs the unit tests and lint gate without a device.

Update `versionCode`, `versionName`, and `CHANGELOG.md`, add `docs/releases/v<version>.md`, then push
a tag matching the Android version exactly. Do not retag/rebuild an existing published version or
replace its assets. Tag builds are skipped when automatic signing is unconfigured, so local signing
remains possible without putting private inputs in GitHub. When configured, validation requires
every signing input, matching version tag and release notes. Full-SDK tests and release lint run
before the key is restored. The signed APK must pass `package-release.py` before artifact upload.

A separate publish job has `contents: write` but no SDK/signing secrets; build jobs have only
read access. It rechecks artifact hashes and publishes a **prerelease** using the checked-in notes.
Manual signed workflow runs are restricted to `main` (or a matching tag), require all inputs and
produce verified Actions artifacts without publishing when run on `main`. PR checks are a separate
SDK-free, secret-free workflow, not proof of full Google Home integration.

Complete [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md), including off-machine key recovery and
independent-account onboarding, before making release-readiness claims. Current source changes
under **Unreleased** do not alter the already published v0.3.0 APK.
