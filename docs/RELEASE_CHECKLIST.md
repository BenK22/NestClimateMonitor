# Release checklist

Run this for each new release. Unchecked items are not implied by a passing CI build.
See [VALIDATION.md](VALIDATION.md) for the last recorded checks and remaining validation gaps.

## Source and distribution

- [ ] Review the diff and tracked files; publication/history and documentation checks pass.
- [ ] Run Python helper tests, full-SDK unit tests, Android lint and APK build.
- [ ] Build a fresh clone without developer `local.properties`, credentials or signing inputs;
      test SDK-free mode and the normal build using the checksum-verified SDK.
- [ ] Inspect generated licenses and the offline in-app screen; review updated SDK/data terms.
- [ ] Increment versionCode/versionName for a new APK, update CHANGELOG and add
      `docs/releases/v<version>.md`. Never replace an existing release's APK with different bytes.
- [ ] Confirm the permanent key/password configuration is excluded from Git and still available.
      Record the maintainer's storage policy: currently ignored local-only storage, with the
      accepted risk of losing compatible update signing if that storage is lost. If adopting an
      encrypted off-machine backup, store passwords separately and test recovery privately.
- [ ] Build/sign using that key; run `package-release.py` to verify signer, package, version,
      non-debuggable state and license resources. Tag the exact reviewed build commit.
- [ ] Publish APK and `.apk.sha256` with honest prerelease notes and known limitations.
- [ ] Download the published asset, verify checksum/certificate and test a same-key update.

## Human/device validation

- [ ] Follow README setup on a separate Google account with no saved developer configuration.
      Verify all three Device Access setup sections, grant renewal and manual/scheduled samples.
- [ ] Test screen-off/locked SDM collection, network failure/recovery, revoked grants and
      Android scheduling delays. Do not promise exact 15-minute wall-clock sampling.
- [ ] Test Google Home separately while unlocked; confirm the documented screen-off limitation.
- [ ] Check dashboard, landscape, widgets, unit selection, alerts and CSV import/export.
- [ ] Exercise the isolated `deviceTest` suite; never overwrite a user's installed package.
- [ ] Confirm privacy-reviewed screenshots, troubleshooting/support links and license attribution.

The maintainer must confirm account tests and key storage policy; CI cannot infer either.
See [releasing](RELEASING.md), [security](../SECURITY.md) and [troubleshooting](TROUBLESHOOTING.md).
