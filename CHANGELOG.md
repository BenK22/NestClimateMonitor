# Changelog

## Unreleased

- Mark actual indoor temperature samples with reported Heat, Cool, Off or unknown
  activity on daily and widget graphs; show sample HVAC state/time when inspecting daily history.
- Retain existing running-state storage and CSV backups; markers do not infer cycle transitions
  or runtime between samples, and unsupported/older missing state stays unknown.

## 0.4.3

- Fix app freezes, most noticeable on low battery: indoor-device filtering no longer decrypts the
  Nest token through Android Keystore once per stored reading; dashboard loading, settings counts,
  delete-all and widget rendering run off the UI thread; bursts of refreshes are coalesced.
- Share one SQLite connection pool with write-ahead logging so background sampling no longer
  blocks dashboard and widget reads.
- Fix Settings appearing stuck open and repeated taps queuing behind a frozen UI.
- Let vertical drags that start on the daily chart scroll the page; horizontal drags and taps
  still move the crosshair.
- Widget and humidity-alert taps resume the existing app like the launcher icon, instead of
  stacking another dashboard on each tap.
- Hardening: the exported widget-configuration screen only accepts this app's widgets, the
  clipboard is cleared after importing the Google authorization URL, links no longer crash
  without a browser, and new CSV exports remove older cached exports.

## 0.4.2

- Fit graph widgets to launcher-reported sizes and preserve bitmap aspect ratio at large sizes.
- Fill the graph widget's content box on third-party launchers rather than letterboxing a
  mismatched orientation bitmap into a skinny strip.
- Let the widget host choose responsive graph sizes independently of the calling app's
  orientation, with tolerance for launcher padding/size-estimate differences.
- Query the complete six-hour graph window instead of truncating it by total row count;
  show a dot when only one measurement exists.
- Add sample-data widget picker previews without using personal history or settings.
- Match the dashboard's solid indoor/dashed outdoor lines in widget plots and legends.
- Keep the last earlier measurement per stream/trait so widget lines crossing the six-hour
  boundary reach the left edge; do not extrapolate history without bracketing saved samples.

## 0.4.1

- Show the installed app version at the bottom of the dashboard, identifying development builds.
- Record the maintainer's waived separate-account and separate official-update checks without
  claiming those tests passed; retain overnight sampling and recovery validation as follow-up.

## 0.4.0

- Added an offline open-source license screen and dependency/data notice inventory.
- Added secret-free Android pull-request checks with an explicitly SDK-free verification package.
- Pinned workflow actions, Gradle distribution and the reviewed Google Home SDK archive.
- Hardened SDK installation and official APK validation; separated signing from release publishing.
- Added security/support policies, issue templates, troubleshooting and a release checklist.
- Disabled unused Google Play encrypted SDK metadata in GitHub APKs while retaining the
  dependency report and generated open-source notices; avoids the build-tool Tink warning.

## 0.3.0

- Renamed the app to Nest Climate Monitor and attributed the MIT license to Benjamin Kar.
- Added weekly Nest authorization renewal guidance for Google OAuth apps in Testing mode.
- Added direct Nest Device Access/SDM authorization and thermostat sampling.
- Added Android Keystore-backed credential and token storage excluded from backup and screenshots.
- Added selectable Nest Device Access and Google Home indoor sources, defaulting to Device Access when connected.
- Added thermostat discovery/selection and a dedicated Device Access settings screen.
- Clarified that Google Home requires a screen-on/unlocked phone, while the one-time US$5 Device Access registration enables screen-off background readings.
- Made freshness indicators track the selected indoor thermostat instead of newer outdoor-only updates.
- Required an explicit thermostat selection when Device Access returns multiple devices.
- Improved widget graph sharpness and scaling, chart day-navigation state, and exact CSV backup round trips.
- Fixed Linux release execution by tracking the Gradle wrapper as executable.
- Added a bounded, credential-redacted sampling failure history to Sampling Health.
- Stopped app launch, source changes, and Device Access setup from implicitly sampling; manual refresh no longer alters or suppresses the periodic cadence.

## 0.2.0

- Added responsive current-reading and graph-only home-screen widgets.
- Added six-hour widget graphs, appearance controls, dual axes, legends, and freshness states.
- Added CSV export, humidity alerts, stale-data reporting, dew point, comfort status, daily summaries, manual refresh, thermostat selection, and retention controls.
- Added privacy and release documentation plus signed GitHub Actions builds.

## 0.1.0

- Initial Google Home temperature and humidity logger with outdoor comparison and daily charts.
