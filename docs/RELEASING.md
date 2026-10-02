# Releasing

## Local signed releases

The first release is built and signed locally, then uploaded as a GitHub prerelease together with its `.apk.sha256` checksum. Release APKs use a permanent private signing key; the keystore and its password configuration remain in ignored local storage and must be backed up securely outside the checkout. Never regenerate the signing key for an update.

Set `RELEASE_KEYSTORE_PATH`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD` in the build environment, then run:

```powershell
.\gradlew.bat testDebugUnitTest lintRelease assembleRelease --console=plain
```

Verify `app/build/outputs/apk/release/app-release.apk` with Android's `apksigner verify --verbose --print-certs`, record its SHA-256 checksum, and upload the verified APK and checksum to the release. Tag the exact reviewed source commit used for the build. Source archives do not include the downloaded Google Home SDK or private signing inputs.

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

Before tagging, run the same local quality gates used during development:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug connectedDeviceTestAndroidTest --console=plain
```

The connected-device tests use the isolated `androidTestDeviceTest` package and do not overwrite the installed app's readings or credentials. They require an attached unlocked Android device; GitHub's release job runs the unit tests and lint gate without a device.

Update `versionCode`, `versionName`, and `CHANGELOG.md`, then push a tag matching the Android version exactly, such as `v0.3.0` for `versionName = "0.3.0"`. Tag builds are skipped when automatic signing has not been configured, allowing a locally signed release to be published without uploading private signing inputs. When configured, the workflow validates every signing secret and the tag/version agreement before downloading private inputs. It must then pass unit tests and Android lint before it builds the signed APK and creates the GitHub release. A manual workflow run still requires all signing inputs, runs the same validation and gates, and builds the APK as a downloadable Actions artifact without creating a release.
