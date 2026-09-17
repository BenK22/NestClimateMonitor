# Releasing

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

Update `versionCode`, `versionName`, and `CHANGELOG.md`, then push a tag such as `v0.2.0`. The workflow must pass unit tests and Android lint before it builds the signed APK and creates the GitHub release. A manual workflow run runs the same gates and builds the APK as a downloadable Actions artifact without creating a release.
