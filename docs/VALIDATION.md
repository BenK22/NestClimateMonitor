# Release-readiness validation record

This records checks performed on 2026-10-05, not a guarantee about every device or future version.
Initial app-source/build-input checks used committed source `df88e09`; subsequent changes
added helper regression tests and fixed CI setup/privacy handling without changing app behavior.
The later APK metadata opt-out was checked separately as recorded below.

## Local checks completed

- A fresh checkout contained no `local.properties`, downloaded SDK, credentials or signing keys.
  It used the installed Android SDK through `ANDROID_HOME` and the machine's existing Gradle
  dependency cache. This was a clean **project checkout**, not a fresh operating system/cache.
- SDK-free mode passed `testDebugUnitTest lintDebug assembleDebug`: 53 JVM tests, no failures.
  APK inspection confirmed `ca.humiditylogger.verification` and the no-Google-Home version suffix.
- The documented installer verified the reviewed SDK ZIP against `gradle/home-sdk.sha256` before
  extracting it into that checkout. Normal mode then passed `testDebugUnitTest lintDebug lintRelease
  assembleDebug assembleRelease`, with 53 JVM tests and no lint errors. Local builds used JDK 21.0.1
  with Java/Kotlin bytecode target 17 and Android SDK 36; CI is configured to use JDK 17.
- Eight isolated SQLite/Keystore instrumentation tests passed on the connected Pixel 9a, under
  `ca.humiditylogger.devicetest`. The normal installed app was not replaced or uninstalled. An initial
  Gradle run timed out writing its optional problems report after the tests passed; rerunning with
  `--no-problems-report` completed successfully. This does not disable Android lint or test assertions.
- A locally signed full APK passed `package-release.py`: permanent certificate, package,
  versionCode/versionName, non-debuggable state, resource-table resolution and valid license index.
  It contained 149 notice entries and 265,264 bytes of generated notice text. This is an **unreleased
  test artifact**; the existing v0.3.0 release and its assets were not replaced.
- 25 Python helper regression tests passed, covering archive hashes/unsafe paths, redirect token
  handling, notice resources/optimized paths, signing-input gates, action pins, workflow permissions
  and the narrow Dependabot public-identity exception. Documentation coverage checked 32 production
  Kotlin files; publication scans covered tracked files and reachable history. YAML and local Markdown
  links were also checked.
- Linux/JDK 17 SDK-free Android CI passed on source/helper commit `d762432`:
  [Android quality gate](https://github.com/BenK22/NestClimateMonitor/actions/runs/37349879009).
  Its matching [privacy/documentation/helper gate](https://github.com/BenK22/NestClimateMonitor/actions/runs/37349878619)
  also passed. The later validation-record edits are documentation-only.

## Follow-up APK packaging checks (2026-10-05)

- After disabling Play-only APK dependency metadata, the full-SDK build again passed 53 JVM
  tests, debug/release lint and debug/release assembly. The signed test APK passed the permanent
  signer, package/version, non-debuggable and license-resource checks. No APK was published or
  installed, and the published v0.3.0 assets were not replaced.
- SDK-free contributor mode also passed `testDebugUnitTest lintDebug assembleDebug` with the
  new metadata setting, including 53 JVM tests and no lint errors.
- All 26 helper regression tests passed, including the new guard that APK metadata is disabled
  while bundle dependency inventory and the OSS plugin remain enabled. Documentation, tracked-file
  privacy and reachable-history checks passed. Other deprecation warnings remain.

## Remaining human checks and known warnings

- Separate-account onboarding and weekly grant renewal still require maintainer confirmation.
  The maintainer explicitly left the separate-account test open on 2026-10-05.
- The existing permanent signing key and password configuration were verified present in ignored
  `.private-backups/release-signing`, with neither file tracked. The maintainer chose local-only
  storage on 2026-10-05; no off-machine backup or recovery test was performed. Losing this local
  storage would prevent signing compatible updates. Git ignore rules are not encryption or backup.
- Actual offline license-screen navigation, all supported thermostat models, every launcher and
  exact overnight sampling behavior were not established by these tests. Use the
  [release checklist](RELEASE_CHECKLIST.md) and [troubleshooting](TROUBLESHOOTING.md).
- Existing Android/Kotlin deprecation/lint warnings remain. Builds are not claimed warning-free.
- The original APK build invoked AGP 8.9.3's `sdkReleaseDependencyData` task, which reported old
  Tink protobuf-generated code. The GitHub APK build now uses the supported `includeInApk = false`
  setting, removing that encryption task from the APK task graph without filtering diagnostics.
  `includeInBundle = true` retains the dependency report used for OSS notices. A forced regeneration
  preserved the license text/index byte-for-byte against the prior full-SDK checkout (149 entries,
  265,264 bytes). See [the build setting and distribution scope](RELEASING.md#apk-dependency-metadata-and-licenses).
- This does **not** patch upstream Tink. Published Google Maven POMs reviewed for AGP 8.9.3,
  8.10.1, 8.13.2, 9.0.0 and 9.4.1 all use Tink 1.7.0. It is absent from the external app runtime
  inventory, and the previously inspected APK has no unshaded `com.google.crypto.tink.proto`
  descriptors. Bundle/Play distribution needs separate review; no bundle validation or blanket
  vulnerability audit is claimed. See the
  [upstream protobuf advisory](https://github.com/protocolbuffers/protobuf/security/advisories/GHSA-h4h5-3hr4-j3g2).

GitHub workflows are the authoritative record of each pushed revision's CI outcome. Passing
SDK-free CI does not validate the real Home SDK or a user's Google account setup. Signing/publishing
automation was validated locally but not dispatched with repository signing secrets during this work.
