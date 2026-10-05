# Release-readiness validation record

This records checks performed on 2026-10-05, not a guarantee about every device or future version.
App-source/build-input changes were tested from committed source `df88e09`; subsequent changes
added helper regression tests and fixed CI setup/privacy handling without changing app behavior.

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

## Remaining human checks and known warnings

- Separate-account onboarding, weekly grant renewal and an encrypted recoverable off-machine
  signing-key backup still require maintainer confirmation. They are not marked complete by CI.
- Actual offline license-screen navigation, all supported thermostat models, every launcher and
  exact overnight sampling behavior were not established by these tests. Use the
  [release checklist](RELEASE_CHECKLIST.md) and [troubleshooting](TROUBLESHOOTING.md).
- Existing Android/Kotlin deprecation/lint warnings remain. Builds are not claimed warning-free.
- AGP 8.9.3's release dependency-metadata task reports old Tink protobuf-generated code. Its POM
  depends on Tink 1.7.0; that external dependency is absent from the app runtime inventory, and the
  inspected APK has no unshaded `com.google.crypto.tink.proto` descriptors. A supported build-tool
  upgrade needs review; warnings were not suppressed and this is not a blanket vulnerability audit.
  See the [upstream protobuf advisory](https://github.com/protocolbuffers/protobuf/security/advisories/GHSA-h4h5-3hr4-j3g2).

GitHub workflows are the authoritative record of each pushed revision's CI outcome. Passing
SDK-free CI does not validate the real Home SDK or a user's Google account setup. Signing/publishing
automation was validated locally but not dispatched with repository signing secrets during this work.
