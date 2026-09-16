# Releasing

Google distributes the Home APIs Android SDK as a signed-in download, so it must not be committed. The release workflow requires these repository secrets:

- `HOME_SDK_URL`: a private or expiring URL for the unmodified Home SDK ZIP;
- `HOME_SDK_TOKEN`: optional bearer token for that URL;
- `RELEASE_KEYSTORE_BASE64`: the release JKS encoded with base64;
- `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD`.

Keep the same release key permanently. The Android OAuth client must contain the SHA-1 fingerprint of that release certificate.

Update `versionCode`, `versionName`, and `CHANGELOG.md`, then push a tag such as `v0.2.0`. The workflow builds a signed APK and creates the GitHub release. A manual workflow run builds the same APK as a downloadable Actions artifact without creating a release.
