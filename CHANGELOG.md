# Changelog

## Unreleased

- Show the installed app version at the bottom of the dashboard, identifying development builds.

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
