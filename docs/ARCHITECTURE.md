# Architecture and maintenance guide

Nest Climate Monitor is a single-module Android/Kotlin application. It reads thermostat state,
stores history locally and compares it with outdoor weather. It does not control thermostats,
run a backend or subscribe to Pub/Sub. UI is built with Android Views; scheduling uses
WorkManager; persistence uses SQLiteOpenHelper and private SharedPreferences.

## Start here

Shared production Kotlin sources are under `app/src/main/java/ca/humiditylogger/`.
The real `HomeReader` SDK adapter is under `app/src/googleHome/java/ca/humiditylogger/`;
the SDK-free verification adapter is under `app/src/noGoogleHome/java/ca/humiditylogger/`.
Gradle includes exactly one, with the real adapter as default. `HomeAccess` defines app-owned
permission/result contracts, so SDK types do not leak into shared UI/worker code. The verification
adapter reports `UNAVAILABLE`; it never simulates permission or climate readings. This path has
a separate application ID and cannot be signed as an official release.

| Concern | Entry points | Responsibility |
| --- | --- | --- |
| Dashboard and settings | `MainActivity`, `DeviceAccessSettingsActivity` | Display cached data, manage explicit setup/refresh and CSV workflows |
| Scheduling | `LoggerScheduler`, `SamplingWorker` | Preserve periodic cadence, collect selected sources, record results |
| Nest SDM | `DeviceAccessClient`, `DeviceAccessParser`, `DeviceAccessStore` | Consent, token refresh, read-only device discovery, encrypted grants |
| Google Home | `HomeReader` | SDK permission state and bounded climate-trait discovery |
| Weather | `WeatherClient`, `WeatherLocationStore` | Saved location and direct Open-Meteo current-weather request |
| History and backup | `Reading`, `ReadingStore`, `CsvExporter`, `CsvImporter`, `ReadingImporter`, `DataRetention` | Canonical units, migrations, interchange validation and pruning |
| Selection | `IndoorSourcePreference`, `ThermostatSelection`, `WidgetReadingSelection` | Consistent provider/device/location filtering |
| Presentation | `ReadingChartView`, `ClimateMetrics`, widget providers/helpers | Charts, derived estimates, cached widget rendering |
| Notifications | `HumidityAlerts` | Sustained selected-device humidity thresholds and local notification state |

## Collection lifecycle

```text
Activity ensures schedule ──> unique periodic request (KEEP, initial delay 15 min)
Settings Refresh now ──────> independent unique one-time request (KEEP)
                                      │
                               SamplingWorker mutex
                                      │
                         selected indoor adapter + weather
                                      │
                       ReadingStore + sampling-health state
                                      │
                         alerts / retention / widgets
```

Both requests require network connectivity. `LoggerScheduler.start` leaves existing work intact,
so returning to the app does not restart the interval or sample immediately. Disabling logging
persists across restarts and cancels periodic work; explicit refresh still works while disabled.
Repeated refresh clicks are coalesced while manual work is unfinished. A manual and a periodic
request remain distinct and serialize in the same process; neither suppresses the other.

The interval is a request, not an exact wall-clock promise. Doze, network constraints and Android
scheduling can delay execution. The process mutex does not coordinate multiple processes; this
app uses its default single process. Expected collection failures are recorded and return
WorkManager success, avoiding retry-driven samples between intervals. Coroutine cancellation
is rethrown through `runCatchingCancellable` and the worker's cancellation handler. Blocking
HTTP calls have socket timeouts but are not instantly interrupted by coroutine cancellation.

Indoor and outdoor collection are independent. Indoor failure can still store weather, but that
must not advance indoor success/freshness. The current tiles use the newest selected indoor row
with temperature or humidity, not merely the newest database row. A setpoint-only snapshot is
not an ambient measurement. Runtime counters use elapsed realtime milliseconds; they are not
energy readings. See [battery testing](BATTERY_TESTING.md) for external measurement procedures.

## Provider and device identity

`IndoorSourcePreference` honors an explicit saved provider, including when it is unavailable.
Only an absent/unrecognized choice defaults to Device Access when locally connected, otherwise
Google Home. There is no automatic fallback on a failed request.

SDM identity is the full `enterprises/<project>/devices/<device>` resource name. Matching requires
the configured project and an exact selected resource. Discovery auto-selects only a single
available device; multiple devices require user selection. If the selected device disappears,
the worker clears it. Google Home selection uses stable device ID when available and display-name
fallback for older rows; its unset selection can include multiple climate devices.

Outdoor rows are identified by `WeatherClient.SOURCE_PREFIX`; the current outdoor series matches
the exact saved location label. Previous locations remain stored but are excluded from current
graphs/scales. Coordinates are not embedded in each reading. Renaming a location changes its
history label; changing coordinates with the same label does not create a distinct series.

## Authorization and security boundaries

Device Access uses the user's Web OAuth client and Partner Connections browser flow. The
registered redirect is `https://www.google.com`; the user copies the complete redirect URL back
to the app. A random 256-bit state nonce binds that URL to the pending attempt. It expires after
ten minutes (or clock rollback) and is consumed before code exchange. Bare codes are not accepted
by the linking entry point. Clipboard import is restricted to a consent attempt initiated by that
screen and validates the URL/state before populating the field.

Access tokens are reused until within one minute of expiry, then refreshed. Refresh responses
without a replacement refresh token preserve the existing grant. A locally decryptable refresh
token makes `isConnected` true; this is not a server-side validity check. Google can revoke or
expire it. Testing-mode grants commonly need weekly reconnection; see the [setup guide](../README.md).

Secrets/tokens are AES-GCM encrypted in private preferences with an Android Keystore key. The
serialized value is Base64 of the IV followed by authenticated ciphertext, not a portable backup.
The Device Access project/client IDs, device selection and pending nonce are private preference
values but not encrypted. The app excludes all app data from backup/transfer, and the credential
screen uses FLAG_SECURE and retains unsaved drafts only in memory across configuration changes.
Disconnect retains setup credentials/history; erase removes Device Access preferences and attempts
key deletion, but does not delete climate history. Credential changes invalidate the connection.

Never log tokens, client secrets, authorization URLs, request bodies or decrypted token objects.
The bounded error history redacts recognized credential assignments and caps entries/text length;
it is not a general-purpose secret scrubber. Latest status and diagnostics have no blanket
redaction and can contain home/device names and identifiers. Treat them as private. Review before
sharing logs/screenshots, even from a private repository. See [privacy](../PRIVACY.md).

Google Home permission belongs to the app, not the installed Nest/Home apps. The worker attempts
it only with the screen on and phone unlocked; the app need not be visible. `HomeReader` is an
application-context singleton. Permission has a 15-second timeout; discovery uses 30-second
per-flow bounds and a 45-second total sample deadline. An overall timeout returns an empty result
with diagnostics. Missing exposed traits are not inferred from another device.

## Data contracts and storage

- `Reading.timestampMs` is Unix epoch milliseconds at collection, not a provider event timestamp.
- Ambient temperatures and setpoints remain Celsius in adapters, SQLite and CSV. Fahrenheit is
  presentation-only. Humidity is percent, not a fraction; Home SDK hundredths are divided by 100.
- Null means absent/unsupported, never zero. Status strings are provider-specific snapshots.
- Schema version 3 uses additive migrations: version 2 added thermostat metadata, version 3 added
  device identity. Future migrations must preserve existing history; bump the version and test
  upgrading old schemas instead of dropping/recreating tables.
- `recent` selects newest N rows and returns them oldest first; `all` and `between` also return
  chronological rows. `between` uses a half-open interval: start inclusive, end exclusive.
- The repository is synchronous. Worker work and bulk CSV operations are off the UI thread;
  existing bounded dashboard/widget reads are synchronous. Do not add network or large-history
  work to UI callbacks. Short-lived consumers use `use`; the activity closes its store on destroy.
- Inserts append without database uniqueness constraints. `insertAll` is transactional. CSV
  duplicate detection happens during preview, not inside the database.
- Unlimited retention is default; optional pruning uses 365 elapsed days, not a calendar year.
  Pruning runs after collection/import or when explicitly applied by settings.

## CSV interchange

Exports are UTF-8 and keep canonical Celsius columns plus an ISO UTC display timestamp. Null
traits are blank. Formula-triggering text gets a leading apostrophe for spreadsheet safety;
`*_b64` companion columns preserve the original text for exact re-import. Base64 is not encryption.
Cached export files become backups only when the user saves/shares them elsewhere through
FileProvider. Credentials are not exported, but device names, IDs and climate history are private.

Import is preview-first and requires user confirmation. Limits are 10 million decoded characters
(not bytes), 100,000 parsed rows and bounded text fields. Validation accepts timestamps from
2000-01-01 through five minutes beyond collection time, finite temperatures in −100–100°C and
humidity in 0–100%. Missing required headers/invalid rows are counted; acceptable rows remain
importable. Duplicate identity is timestamp + device ID + source, both against existing history
and earlier incoming rows. Older exports without encoded columns remain accepted. Preview and
insertion are separate operations, not a concurrent uniqueness guarantee.

## Charts, widgets and alerts

The dashboard uses local calendar-day start and next-midnight boundaries, so queries handle
23/25-hour DST days. The daily chart positions points by elapsed fraction; its fixed clock labels
are approximate on DST transitions. It uses an adaptive temperature axis and fixed 0–100% humidity.
Daily averages are arithmetic sample means, not time-weighted averages; explicit manual samples
have the same weight as scheduled samples. Missing values are excluded from summaries.
Null values break a daily path; a long sampling gap alone does not. Touch shows nearest stored
readings and never triggers a refresh.

Widgets display cached history only and re-render on provider callbacks or explicit app updates.
They do not own a sampling schedule. Both providers use the newest 400 stored rows; the renderer
then filters a rolling six-hour window and current device/location. Rendering uses density-scaled
ARGB bitmaps capped at 1200 × 750 pixels, with adaptive temperature/humidity scales. Widget paths
omit null points rather than breaking on them. Transparency/border preferences are per widget ID.
Clicking launches the dashboard only. Freshness is based on selected indoor ambient history:
disabled takes precedence, no reading is waiting, under 30 minutes is fresh, under 60 delayed,
otherwise stale. Re-rendering, not a live timer, recomputes those labels.

Humidity alerts consume selected-device humidity samples and require an inclusive high/low
threshold for the configured duration. A gap exceeding 25 minutes breaks continuity; this is
sample-based evidence, not proof of the value between samples. Notifications are deduplicated by
state and cleared on normal/disabled state. Android 13+ notification permission is checked at
delivery. Comfort/dew-point values are estimates for display, not health/building advice.

## Tests and releases

See [CONTRIBUTING.md](../CONTRIBUTING.md) for build commands and documentation conventions.
JVM tests cover parser/selection/CSV, scheduling policies, cancellation, alert continuity,
retention, chart state and widget sizing/freshness. Instrumentation tests cover SQLite transactions
and Android Keystore storage using the isolated `deviceTest` application ID, not the user's app.
No test establishes exact overnight timing on every Android device.

PR CI compiles/tests/lints SDK-free mode with read-only permissions and no private inputs. The
trusted release workflow verifies the SDK archive, runs full-SDK tests/lint before restoring
the key, checks the signed APK and generated notices, then hands only assets to a separate
publishing job. External actions use full commit pins. Python regression tests cover archive
validation, redirects, notice checks and workflow safeguards. See the [release checklist](RELEASE_CHECKLIST.md)
for human validation that these checks cannot establish.

Settings launches Google's v2 OSS license menu. The Gradle plugin generates release notices
from dependency metadata and bundled SDK notices; AGP debug variants supply only a placeholder.
Release validation rejects that placeholder. See [third-party notices](../THIRD_PARTY_NOTICES.md).

The Google Home SDK is a signed-in download in ignored `.home-sdk-repo`; release signing inputs
are environment-only and stay outside public Git. See [releasing](RELEASING.md) and
[publishing/privacy checks](PUBLISHING.md). A documentation-only source commit does not replace
the existing signed APK or its tag. Keep the permanent signing key for future compatible updates.
