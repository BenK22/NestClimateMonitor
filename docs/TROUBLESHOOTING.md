# Troubleshooting and compatibility

## Supported environment

- Android 10/API 29 or newer, with internet access. Google Home additionally requires current
  Google Play services and a Home environment supported by Google's SDK.
- Device Access supports the [Nest thermostat models listed by Google](https://developers.google.com/nest/device-access/supported-devices).
  Separate Nest Temperature Sensors are not exposed by SDM. Other brands are not supported by SDM;
  Google Home can expose compatible climate traits, but this project has not tested every device.
- Real-device development testing has used a Pixel 9a and one Nest installation. This is not a
  compatibility guarantee for every thermostat, launcher or Android battery policy.
- Device Access requires a supported personal Google account, not a Google Workspace account,
  plus Google's one-time US$5 registration. See the [Google prerequisites](https://developers.google.com/nest/device-access/get-started).

## No overnight samples / stale widget

1. Check **Settings → Indoor data source**. Google Home is attempted only when the screen is on
   and unlocked. Use connected **Nest Device Access** for screen-off collection. There is no silent fallback.
2. Confirm **15-minute logging** is enabled and inspect **Sampling health**, its latest attempt,
   selected source, last indoor success and bounded failure history. Weather success is not indoor success.
3. Try **Refresh now** once. It does not reset the periodic interval. App opening and widget taps
   only display cached data; they do not sample.
4. Check internet access and Android battery restrictions. WorkManager requests a 15-minute
   interval, not exact wall-clock delivery; Doze and constraints may delay it. Force-stopping the
   app stops scheduled work until the app is opened again. Re-enable the saved schedule if disabled.
5. Widgets render cached history and refresh on callbacks/app updates. Their freshness label is
   recomputed when rendered, not by a live timer. Open the app to inspect actual stored timestamps.

Do not uninstall to troubleshoot: backup/device transfer is disabled. Export CSV first if a
reinstall or switch between differently signed builds is necessary. See [battery testing](BATTERY_TESTING.md).

## Nest authorization or 403 errors

- A saved connection is not proof that Google still accepts the refresh token. In OAuth **Testing**
  mode it commonly expires after seven days. Repeat **Nest Device Access → 3. Connect Nest → Complete
  connection**; saved project/client credentials do not need to be recreated.
- Enable **Smart Device Management API** in the same Cloud project as the Web OAuth client and
  allow propagation. Check the consent screen/test-user account and thermostat ownership/access.
- The **Device Access Project ID** comes from Device Access Console, not the Cloud project ID or
  project number. The **Web OAuth Client ID/Secret** come from Google Auth Platform. Associate that
  same Web client with the Device Access project; save each section in the app.
- The Web client's authorized redirect URI must be exactly `https://www.google.com`. Ending on
  google.com is expected: copy the complete URL from Chrome's address bar, including `code` and
  `state`, and return to the app to complete the pending attempt within ten minutes. Bare codes,
  an old attempt, a changed state or a plain google.com URL are rejected.
- If a client secret was rotated, save the new value and reconnect. Never share an authorization URL
  or screenshot of credentials. See [Google's authorization errors](https://developers.google.com/nest/device-access/reference/errors/authorization).

## No thermostat / missing traits

Check Google's supported device list and the account used for consent. Grant the appropriate home
and thermostat access. Choose a device explicitly if there are multiple; discovery can auto-select
one device. Google Home permissions belong to this app, not the installed Home/Nest apps. Register
its Android OAuth client using the actual APK signing certificate and package name.

Missing target, HVAC/Eco state or humidity is shown as unavailable, not zero. Devices/providers do
not expose every trait. User changes are sampled snapshots, not a complete audit trail of who changed what.

## Build and installation

Use JDK 17, Android SDK 36 and Python 3.11+ for maintenance helpers. Install the reviewed Home SDK
with `.github/scripts/install-home-sdk.py`; checksum mismatch means stop and review the archive,
not disable verification. A normal build needs this SDK even when the user chooses SDM at runtime.

For an SDK-free contributor build, pass `-PincludeGoogleHome=false`. It has the distinct package
`ca.humiditylogger.verification`, explicitly unavailable Home access and no official release signing.
It checks shared/SDM code, not the real Home SDK integration. Never present it as the official APK.

Android refuses updates signed with another certificate. Export data before manually migrating
between debug, fork and official release builds. An official update should preserve data when the
package/certificate match and versionCode increases. Never uninstall a tester's app automatically.

## Sharing a bug report

Include app/Android versions, indoor source, steps, expected/actual behavior and approximate timing.
Use redacted Sampling Health excerpts. Do not attach full databases, CSV history, personal addresses,
device identifiers, credentials or authorization URLs. Report vulnerabilities privately via
[SECURITY.md](../SECURITY.md), not a public issue. Support is best-effort for a volunteer prerelease.
