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

## v0.4.0 phone/release checks (2026-10-05)

- Full-SDK source `f3cb633` passed 53 JVM tests, debug/release/release-smoke lint, and assembly of
  all three APK variants. The official v0.4.0/versionCode 4 APK passed the packaging helper's
  permanent-signer, package/version, non-debuggable and generated-notice checks (149 entries).
- The connected phone had a developer-signed v0.3.0, not the official release certificate.
  After verifying matching developer certificates, `adb install -r` updated it to developer-signed
  v0.4.0 without uninstalling. The existing reading count and all five saved preference files were
  unchanged, including the encrypted Nest connection. This is a tested **development-to-development**
  update, not a claim that the official APK was installed over that differently signed app.
- A later dashboard check showed a new successful indoor/outdoor sample after the update;
  history grew and the saved Nest connection remained unchanged. Logger status naturally changed
  as sampling resumed. This checks sampling on this phone, not universal overnight timing.
- A separately signed `ca.humiditylogger.releasesmoke` installation exercised actual release
  notices without accessing the normal app's data. Settings opened the populated license menu;
  AndroidX's link-only notice and Dagger's embedded text rendered. Network access was not disabled
  and external license URLs were not followed. The temporary test app was removed afterward.
- 27 helper regression tests passed, including smoke-package isolation. Publication/history and
  production Kotlin documentation scans passed. Official v0.3.0 and v0.4.0 certificates match;
  the public release's in-place upgrade has not been tested on a separate official installation.
- Independent-account onboarding remains untested and open; v0.4.0 retains prerelease status
  with this limitation documented. These checks do not establish every launcher/device or future
  overnight schedule. No signing material or personal data is included in the repository.
- The final official APK was rebuilt from tagged commit `155b4d0`, and its packaged version-control
  metadata was checked against that exact commit. Both GitHub gates passed:
  [Android/Linux JDK 17 verification](https://github.com/BenK22/NestClimateMonitor/actions/runs/37356530403)
  and [publication/documentation/helpers](https://github.com/BenK22/NestClimateMonitor/actions/runs/37356530338).
  The tag workflow skipped automatic signing because repository signing inputs are unconfigured;
  this release was signed locally, not built by that skipped job.
- [v0.4.0 was published as a prerelease](https://github.com/BenK22/NestClimateMonitor/releases/tag/v0.4.0).
  Both uploaded assets were downloaded again and matched the reviewed local APK/checksum bytes.
  APK SHA-256: `cf0d75130f08763de246e325d8c50a4af8cc32164c108aa62044877a3e92cb8e`.
  v0.3.0 assets and repository visibility were unchanged. This later publication-record edit does
  not change the tagged release source or published APK.

## v0.4.1 footer/release checks (2026-10-05)

- The version footer was visually verified at the bottom of the maintainer's dashboard.
  Nine isolated device tests passed on footer source `e802f32`, including checking the generated
  variant version, final dashboard position and activity recreation. The initial UI test could
  not resume with the phone locked; rerunning after unlocking passed all nine tests.
- Release source `3cc0555` changes the version to 0.4.1/versionCode 5 and records the maintainer's
  waived validation checks. Full-SDK 53 JVM tests, debug/release lint and debug/release assembly
  passed. All 27 helper tests and documentation/publication-history checks passed.
- The official APK passed permanent-certificate, package/version, non-debuggable and generated
  license-resource checks. Its packaged VCS commit matches tagged source `3cc0555`. Release
  configuration uses `DEBUG = false` and version 0.4.1; development builds identify themselves.
- Both source-commit GitHub gates passed:
  [Android verification](https://github.com/BenK22/NestClimateMonitor/actions/runs/37359955172)
  and [publication/documentation/helpers](https://github.com/BenK22/NestClimateMonitor/actions/runs/37359955253).
  This release was signed locally using the existing permanent key, not by automatic signing.
- [v0.4.1 was published as a prerelease](https://github.com/BenK22/NestClimateMonitor/releases/tag/v0.4.1).
  Both uploaded assets were downloaded and matched the reviewed local APK/checksum bytes.
  APK SHA-256: `23014e4a195dbc3c6598df583c1f0a82e2d333cea9813b16defb8c40d637eee0`.
  No prior release assets or repository visibility were changed. This publication record is
  subsequent documentation, not a change to the tagged APK source.

## v0.4.2 widget source checks (2026-10-07)

- Widget source `48ffbe2` passed 66 full-SDK JVM tests, 11 isolated SQLite/widget device tests,
  debug lint and debug assembly. Tests cover six-hour boundary clipping, source/device/trait
  predecessor selection, single-point drawing, outdoor dash styles and a real `AppWidgetHostView`
  choosing a portrait bitmap while its calling app is configured for landscape.
- The developer-signed update was installed in place on the maintainer's Pixel 9a. Saved connection,
  alert, setup and widget appearance settings were retained; history grew from 1,340 to 1,342 rows
  through regular sampling. Logger status changed naturally. No sampler/cadence logic changed.
- The unlocked Nova home screen was visually checked: the graph filled its box, all four paths
  reached the left edge where saved samples bracketed it, and outdoor paths/legend samples were
  dashed. This is a live widget check, not a completed Nova picker-preview check. Preview layouts
  were inflated in device tests, but actual Nova picker appearance remains a follow-up.
- Both widget-source GitHub gates passed:
  [Android verification](https://github.com/BenK22/NestClimateMonitor/actions/runs/37667711075)
  and [publication/documentation/helpers](https://github.com/BenK22/NestClimateMonitor/actions/runs/37667711045).
  Documentation covered 33 production Kotlin files; all 27 helper tests passed.
- Version 0.4.2/versionCode 6 changes the distribution version and documents these widget fixes.
  The prior fresh-checkout/SDK-installation checks above were performed on 2026-10-05, not repeated
  or represented as new clean-machine checks for this patch. Publication/package verification is
  recorded separately after release. Previous human-check waivers and limitations remain in effect.

## v0.4.2 publication checks (2026-10-07)

- After a system crash, the Git staging index and one damaged documentation file were recovered.
  Tracked text/source files were checked for null-byte corruption and Git object integrity was checked.
  Damaged build caches and interrupted outputs were preserved in ignored backups; the final APK was
  rebuilt with regenerated compiler transforms/output files, not taken from the interrupted build.
- Tagged release source `5889917` passed full-SDK `testDebugUnitTest lintDebug lintRelease
  assembleDebug assembleRelease`: 66 JVM tests, no failures, and no debug/release lint errors.
  Existing deprecation/native-symbol warnings remain. The source also passed all 27 helper tests,
  documentation coverage and publication/history scans.
- `package-release.py` verified the permanent certificate, `ca.humiditylogger` package,
  version 0.4.2/versionCode 6, non-debuggable state and generated license resources: 149 notice
  entries and 265,264 bytes of notice text. Packaged VCS metadata matches the exact tagged commit.
  The maintainer's differently signed working phone app was not replaced with the official APK.
- Both exact-source GitHub gates passed:
  [Android verification](https://github.com/BenK22/NestClimateMonitor/actions/runs/37671920081)
  and [publication/documentation/helpers](https://github.com/BenK22/NestClimateMonitor/actions/runs/37671920044).
  The [tag workflow](https://github.com/BenK22/NestClimateMonitor/actions/runs/37675112136) explicitly
  skipped automatic signing/publishing because those inputs are unconfigured. Signing was local.
- [v0.4.2 was published as a prerelease](https://github.com/BenK22/NestClimateMonitor/releases/tag/v0.4.2).
  Both uploaded assets were downloaded and matched the reviewed local APK/checksum bytes.
  APK SHA-256: `6f47c3b9026b03b52968228c64f3174f8a32966a7f265e0c4e4a491dcdebebb3`.
  Existing releases/assets and repository visibility were unchanged. This later record does not
  change the tagged release source or APK. Remaining human-check limitations above still apply.

## v0.4.3 stability source checks (2026-10-16)

- Fixes in `cce82c1` and `ea5e540` passed full-SDK `testDebugUnitTest`, `lintDebug`, `assembleDebug`
  and device-test compilation on this machine, plus the publication and documentation scans.
  No new automated tests were added for the threading, touch or launch-intent changes.
- The developer-signed debug build of `ea5e540` was installed in place on the maintainer's Pixel 9a
  (Android 17), replacing developer build 0.4.1. History (1,945 rows) and connection settings were
  retained. With Battery Saver forced on, power simulated as unplugged and the battery at 25%:
  cold start took 749 ms; a vertical swipe starting on the chart scrolled the page; a horizontal
  drag showed the crosshair without scrolling; Settings opened, two rapid unit toggles returned
  to Fahrenheit, the stored-data count loaded, and Done/Refresh now dismissed it; the manual
  sample completed while the page was scrolled; graph-widget taps reused a single `MainActivity`
  task (`sz=1`), Back returned to the launcher, and the page scrolled from the chart afterwards.
  Logcat showed no ANR, fatal exception, SQLite lock or skipped-frame entries for the app.
  Battery Saver and the simulated battery state were then restored.
- These were scripted `adb` input checks on one phone with Device Access selected, not a long
  soak test. Google Home source mode, overnight sampling and the official signed APK on a phone
  were not exercised in this pass.

## v0.4.3 publication checks (2026-10-16)

- Before signing, `keytool` confirmed the configured key's certificate SHA-256 matched the permanent
  `1E:4D:…:4B:BC` fingerprint; no key was generated. Signing inputs came from User-scoped variables
  loaded into the build process only.
- Tagged release source `3b21140` passed full-SDK `testDebugUnitTest lintDebug lintRelease
  assembleDebug assembleRelease`: 66 JVM tests with no failures or skips, and no debug/release lint
  errors. Existing deprecation warnings remain. Documentation (35 production Kotlin files),
  publication/history scans and all helper tests passed.
- `package-release.py` verified the permanent certificate, `ca.humiditylogger`, version
  0.4.3/versionCode 7, non-debuggable state and generated license resources. Independently,
  `apksigner` reported a single signer with the permanent SHA-256, `aapt` reported the expected
  package/version, and the packaged VCS metadata named `3b21140` with a `$PROJECT_DIR` root.
- Exact-source GitHub gates passed:
  [Android verification](https://github.com/BenK22/NestClimateMonitor/actions/runs/38926214082)
  and [publication privacy check](https://github.com/BenK22/NestClimateMonitor/actions/runs/38926213994).
  The [tag workflow](https://github.com/BenK22/NestClimateMonitor/actions/runs/38929096590) ran its
  configuration job and skipped build/publish because repository signing inputs are unconfigured.
  Signing was local.
- [v0.4.3 was published as a prerelease](https://github.com/BenK22/NestClimateMonitor/releases/tag/v0.4.3)
  with a separate v0.4.3 publisher. Server-side asset digests were checked on upload, and both
  assets were downloaded after publication and matched the reviewed local APK/checksum bytes.
  APK SHA-256: `87285e9de3b021bfc0e4fc1f3ce976604d902f8eac63f32dccba907ed7fb19eb`.
  Earlier tags, releases and assets were unchanged. The official APK was not installed on the
  maintainer's phone, which keeps its developer-signed build.

## Unreleased HVAC sample markers (2026-10-19)

- Full Google Home SDK mode passed `testDebugUnitTest lintDebug assembleDebug
  connectedDeviceTestAndroidTest`: 72 JVM tests, 20 isolated device tests on the Pixel 9a
  (Android 17), and no lint errors. All 27 Python helper tests and production Kotlin
  documentation coverage (37 files) also passed.
- Tests cover reported-state normalization, unknown states, raw-state database/CSV round trips,
  actual-sample-only markers, widget boundary clipping and tooltip timestamps. Two initial
  widget-test failures led to updating the single-point expectation and thickening the idle
  marker stroke; the complete device suite then passed. Synthetic chart output was visually
  reviewed, and the temporary test applications were removed.
- After verifying identical developer signing certificates, the tested development APK updated
  the maintainer's app in place. The complete reading count, all six saved preference files and
  encrypted Nest connection were unchanged. The live dashboard showed HVAC markers on saved
  indoor temperature samples. No manual refresh, uninstall or scheduling change was performed.
- These are sampled HVAC states, not continuous cycle measurements. No runtime estimate or
  new overnight timing validation is claimed. This feature has not been published as an APK
  release; the existing v0.4.3 release assets remain unchanged.

## Unreleased legend/mode shading follow-up (2026-10-19)

- The conditional unknown-activity legend passed 75 JVM and 22 isolated phone tests before
  selected-mode shading was added. The final combined full-SDK build passed 82 JVM tests,
  26 isolated Pixel 9a device tests, debug lint and debug assembly. All 27 helper tests and
  documentation coverage (39 production Kotlin files) passed as well.
- Regression tests cover conditional unknown keys in both renderers, mode normalization,
  distinction from running activity, raw database/CSV round trips, bounded last-observed bands,
  missing modes, duplicate timestamps, window clipping and metadata-only widget predecessors.
  Synthetic output for all four known mode colours plus unshaded unknown history was visually
  reviewed. The isolated preview-test packages were removed afterwards.
- The combined development update was installed in place after verifying its compatible signer.
  Reading count, all six preference files and the saved Nest connection were unchanged. Mode
  shading uses existing history without migration or new sampling. The 30-minute shading cap
  is a presentation rule, not a claim of exact mode-change times. No release assets were changed.

## Continuous mode shading follow-up (2026-10-19)

- At the maintainer's request, last-observed mode backgrounds now span sampling gaps until
  the next recorded mode change, replacing the initial 30-minute presentation cutoff above.
  Adjacent equal modes merge; explicit missing/unsupported modes still interrupt shading, and
  renderers still clip future time. Stored samples and equipment-activity markers are unchanged.
- Full-SDK debug tests, lint, assembly and isolated instrumentation passed: 84 JVM tests and
  27 Pixel device tests. Regression cases include multi-hour gaps, merging unchanged modes,
  a predecessor covering the widget window and interruptions by an explicit unknown snapshot.
  All 27 helper tests and publication/history/documentation checks passed.
- The compatible development update retained the complete reading count and all six preference
  files. The maintainer's October 18 chart was visually checked on the phone: its sampled Heat/Cool
  setting now forms one continuous background instead of expired strips. This is approximate
  last-observed-mode presentation, not exact switch-event history. No release assets were changed.

## Nest authorization recovery checks (2026-11-19)

- Full-SDK `testDebugUnitTest lintDebug assembleDebug` passed with 88 JVM tests and no lint
  errors. Both isolated app/test APKs were assembled explicitly. All 27 helper tests and the
  publication/history/documentation checks (40 production Kotlin files) passed.
- Direct instrumentation on the Pixel 9a passed 29 storage/rendering tests and one climate-widget
  recovery test, using synthetic grants in the separate device-test package. These cover persisted
  expiry state, legacy migration, preservation of credentials/selection, replacement-grant races,
  local key errors not being mislabeled as Google expiry, all tile sizes, clearing the warning after
  synthetic renewal, and suppression while Google Home is selected. No Google request was needed.
- Two earlier complete device runs could not pass their four visible-activity checks while the phone
  was locked. After unlocking, direct instrumentation ran the final complete 34-test suite and all
  tests passed, including the three renewal-screen/routing checks and existing version-footer check.
  For direct runs, build both `assembleDeviceTest` and `assembleDeviceTestAndroidTest`; assembling
  only the latter does not regenerate the app APK installed alongside it.
- The maintainer's installed development signer matched the update. An in-place update retained
  all readings, the saved connection, and configuration/display/alert/widget settings; the logger
  state changed as scheduled work continued. Temporary test packages were removed. The phone's
  existing saved error matches the narrow legacy expired-grant detector. On the unlocked phone,
  opening the installed app displayed the focused renewal screen with saved setup retained. The
  live Nova climate widget's red expiry warning was visually verified; tapping it returned to renewal.
  Nova did not expose its accessibility tree, so only the private screenshot was used for that visual
  check. Google consent itself remains the user's action and was not performed during testing.
  No release assets were published, and no credentials, tokens or personal screenshots were committed.

## Connection-screen close button (2026-11-19)

- Added an accessible 48dp top-right close button in a fixed header, outside the scrolling form.
  It finishes the connection activity like View history/Done, without modifying saved grants.
- Close-button source `ae7fc3b` passed GitHub's
  [shared Android unit/lint verification](https://github.com/BenK22/NestClimateMonitor/actions/runs/44592595970)
  and [publication/privacy gate](https://github.com/BenK22/NestClimateMonitor/actions/runs/44592595996).
  Local full-SDK JVM tests passed (88 tests). An unusually slow final full-SDK lint invocation was
  cancelled after the CI checks passed; it is not represented as a completed lint pass. Final
  full-SDK assembly of the normal and both isolated APKs completed successfully without rerunning lint.
- On the unlocked Pixel 9a, all 34 final instrumentation tests passed, including the updated
  assertions that X remains visible after scrolling, closes renewal, and does not reopen it after
  dashboard recreation. Temporary test apps were removed.
- The fixed-header APK was verified and installed in place with the matching developer certificate.
  The already-renewed connection and settings were unchanged and stored history did not shrink.
  No consent was initiated, sampling cadence changed, or release assets published.

## v0.4.4 signed prerelease publication (2026-11-19)

- Tagged and published exact source `7c4f38bdc31609dfaf9c2a70f45860bf881b920a` as
  [v0.4.4](https://github.com/BenK22/NestClimateMonitor/releases/tag/v0.4.4), version code 8.
  This includes the HVAC history, continuous mode shading, Nest authorization recovery and
  persistent connection-screen close button validated above.
- Full Google Home SDK mode passed `testDebugUnitTest assembleRelease lintRelease`:
  88 JVM tests, no failed/skipped tests, and no release lint errors (128 warnings remain).
  The existing local SDK repository was used; a fresh checkout and independent-account
  onboarding were not repeated. The earlier 34 isolated phone checks remain the device evidence.
- The permanent keystore was verified outside the repository. Signing inputs were loaded from
  Windows user environment variables into the build process. `package-release.py` verified
  the sole permanent signer, `ca.humiditylogger`, version 0.4.4/code 8, non-debuggable state
  and packaged license resources. The APK's source metadata matches the tagged commit.
- Exact-source GitHub gates passed:
  [Android verification](https://github.com/BenK22/NestClimateMonitor/actions/runs/44596617020)
  and [publication privacy check](https://github.com/BenK22/NestClimateMonitor/actions/runs/44596614451).
  The [tag workflow](https://github.com/BenK22/NestClimateMonitor/actions/runs/44600453023)
  passed configuration and skipped automatic build/publish because repository signing inputs
  are unconfigured. The release was signed locally and uploaded through the GitHub API.
- Published `NestClimateMonitor-v0.4.4.apk` and its `.apk.sha256` companion as a new prerelease.
  Both server-side asset digests/sizes were checked, then both published assets were downloaded
  and matched the reviewed local bytes. APK SHA-256:
  `48aadf32ba51e024bb3475ae91846fcc812d0a74099fe5fb37dbd86bb720f2ff`.
  Existing releases/tags were unchanged. The official APK was not installed over the phone's
  differently signed development build; no uninstall, data clearing or sampling change occurred.

## Remaining human checks and known warnings

- On 2026-10-05, the maintainer waived separate-account onboarding and a separate official
  APK-to-APK upgrade test for this personal-use prerelease. Setup worked on the maintainer's
  account and the developer-signed phone update was verified. These waivers close the tracking
  tasks; they do not turn unperformed independent-account or official-update tests into passes.
- Overnight locked-screen collection, battery observations and network/authorization recovery
  remain manual validation follow-ups. Weekly grant renewal still needs observation. Android
  may delay scheduled work; no exact 15-minute timing guarantee is made.
- The current permanent signing key is stored outside the repository, with signing inputs in
  Windows user environment variables. Earlier releases used ignored local signing storage.
  No off-machine backup or recovery test was performed. Losing the key or required credentials
  would prevent signing compatible updates. Git ignore rules are not encryption or backup.
- All supported thermostat models, every launcher and exact overnight sampling behavior were
  not established by these tests. A network-disabled license-screen check also remains unperformed. Use the
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
