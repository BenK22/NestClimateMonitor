# Home Climate Monitor

A deliberately small Android logger for Nest Device Access and Google Home APIs. It:

- connects directly to Google's Smart Device Management API for reliable screen-off Nest readings;
- keeps Google Home structure access as a foreground fallback;
- samples in the background every 15 minutes using Android WorkManager, enabled by default with a persistent on/off setting;
- records outdoor temperature and humidity from the Open-Meteo grid for a user-selected location;
- discovers temperature and relative-humidity traits;
- stores readings in an on-device SQLite database;
- graphs stored temperature and humidity readings;
- provides resizable current-reading and six-hour graph widgets;
- exports selected-day or complete history as CSV;
- supports optional sustained high/low humidity notifications;
- calculates dew point, comfort state, and daily min/average/max summaries;
- reports delayed or stale samples and supports a unique manual refresh;
- lets users select a specific compatible indoor device; and
- shows every returned device type and trait to diagnose whether a Nest thermostat exposes humidity.

There is no foreground service, developer-operated cloud upload, analytics, or app-server dependency. Android may defer individual runs during Doze or other battery-saving modes, so 15 minutes is the requested interval rather than a wall-clock guarantee. Nest Device Access works while the display is off; the Google Home Android API fallback is limited to foreground/unlocked reads.

The daily chart spans midnight through 11:59 PM in the Toronto time zone. Solid lines are indoor Google Home readings and dashed lines are outdoor weather-model readings. Previous and Next navigate among calendar days; Next is hidden on the current day. The outdoor location is selectable in the app, with St. Catharines, Ontario as the non-personal default. Android geocodes the entered place, and only the resulting coordinates are sent to Open-Meteo; Google Home data is never transmitted.

Outdoor weather data is provided by [Open-Meteo](https://open-meteo.com/) under CC BY 4.0. The free endpoint is intended for non-commercial use; review Open-Meteo's current terms before distributing a commercial build.

## Nest Device Access setup

Device Access is the recommended indoor source. Each user supplies their own Google credentials directly on the phone; no credentials are compiled into the APK or repository.

1. Enable the Smart Device Management API in a Google Cloud project.
2. Create a **Web application** OAuth client with `https://www.google.com` as an authorized redirect URI.
3. Register for [Nest Device Access](https://developers.google.com/nest/device-access/registration), create a Device Access project with Events disabled, and associate the Web OAuth Client ID with it.
4. For long-lived refresh tokens, move the Google Auth Platform audience from Testing to Production. Personal use does not require OAuth verification, but Google may show an unverified-app warning.
5. In the app, open **Settings → Nest Device Access**, enter the Device Access Project ID, Web Client ID, and rotated Client Secret, then save.
6. Tap **Open Google authorization**, grant access, and copy the final redirected URL (or its `code` value) from the browser into the app.
7. Complete the connection and select a thermostat. The next manual or scheduled sample will use SDM.

The client secret, access token, and refresh token are encrypted using Android Keystore, excluded from Android backup/device transfer, hidden from screenshots, and erased through the Device Access screen. They are still credentials held by a native client; this direct-phone design is intended for personal sideloaded use.

## Google Home fallback setup

1. Sign in at the [Home APIs SDK setup page](https://developers.home.google.com/apis/android/sdk) and download the current Android SDK ZIP. This checkout uses SDK 1.10.1 extracted into `.home-sdk-repo`; the required Maven artifacts are `play-services-home` and `play-services-home-types` version `17.1.0`.
2. Create a Google Cloud project and configure its OAuth consent screen.
3. Add the Google account that owns/administers the Google Home as an OAuth test user.
4. Create an **Android** OAuth client for package `ca.humiditylogger` using the SHA-1 of the key used to sign the app.
5. Keep the app unverified for personal testing. Google Home API app registration is not required for testing.

The phone needs current Google Play services. Current Home APIs also expect a supported physical Google Home hub in the structure.

## Build

The project requires JDK 17, Android SDK 36, and Android Studio/Gradle capable of Android Gradle Plugin 8.9.3. Open this folder in a current Android Studio, let it install missing SDK components, then run the `app` configuration on the phone.

This workstation has Android SDK Platform 36 and Build Tools 35 installed. The signed-in Home APIs SDK is stored in the ignored `.home-sdk-repo` directory.

Once those artifacts are installed, build the debug APK with:

```powershell
.\gradlew.bat assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Enable installation from your browser or file manager, transfer the APK to the phone, and open it to sideload. Builds signed with a different certificate require a matching Android OAuth client and cannot update an installed build signed by another key.

## First run

1. Configure Nest Device Access using the steps above.
2. Optionally grant Google Home access as a foreground fallback.
3. Choose the outdoor location and temperature units.
4. Leave 15-minute logging enabled. Android may delay individual WorkManager runs.
5. Optionally configure humidity alerts, retention, and home-screen widgets.

All history remains on the phone unless the user explicitly shares a CSV. See [PRIVACY.md](PRIVACY.md).

## Publishing this repository

Do not commit the downloaded Home APIs SDK ZIP or its extracted Maven repository. They are ignored as `home.android.sdk_*.zip` and `.home-sdk-repo/`; each developer must download the SDK while signed in to Google Home Developers. Local Android SDK paths, Gradle/build output, APK/AAB files, signing keys, environment files, and common secret-property files are also ignored.

The Google Home Android OAuth client ID is associated with the application ID and signing-certificate SHA-1 in Google Cloud; it is not embedded in this project. Device Access project/client identifiers are entered at runtime, and its client secret and tokens must never be added to source, build configuration, screenshots, issues, or documentation.

Release signing and automation are documented in [docs/RELEASING.md](docs/RELEASING.md). Screenshot guidance is in [docs/screenshots/README.md](docs/screenshots/README.md). The project is available under the [MIT License](LICENSE), and user-facing changes are tracked in [CHANGELOG.md](CHANGELOG.md).

Background-worker timing and a repeatable Android battery-accounting procedure are documented in [docs/BATTERY_TESTING.md](docs/BATTERY_TESTING.md).

## Testing safety

`testDebugUnitTest` runs the local test suite. On-device database tests exist only in the isolated `deviceTest` build and run with `connectedDeviceTestAndroidTest`. That build installs as `ca.humiditylogger.devicetest`, so it cannot replace `ca.humiditylogger` or access its readings and preferences. Android tests are disabled for the normal debug and release variants.

## What success looks like

After Device Access authorization, a refresh should show the selected Nest thermostat's temperature and humidity and Sampling Health should identify `Nest Device Access (Google SDM)`. With only the Google Home fallback connected, diagnostics should contain `RelativeHumidityMeasurement` with a humidity value.
