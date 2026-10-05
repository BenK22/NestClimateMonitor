# Contributing

Start with the [README](README.md) for setup and the [architecture guide](docs/ARCHITECTURE.md)
for component boundaries, data contracts and known limitations. This is a personal-use,
read-only Android client; changes must not add thermostat control or silently upload user data.

## Build and verify

Install the JDK/Android SDK versions listed in the README and download the Google Home SDK into
the ignored `.home-sdk-repo`. Configure `local.properties` locally. No Google credentials are
required for unit tests; connection testing uses your own credentials entered on a test phone.

From the repository root in PowerShell:

```powershell
python -B .github/scripts/check-documentation.py
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain
python -B .github/scripts/check-publication.py --history
git diff --check
```

For SQLite/Keystore changes, attach an unlocked Android test device and also run:

```powershell
.\gradlew.bat connectedDeviceTestAndroidTest --console=plain
```

The `deviceTest` variant has a separate application ID so instrumentation cannot replace a
normal installation. Never uninstall a user's app or install a differently signed APK over it
as part of a test. Use synthetic names/identifiers/tokens in fixtures; do not copy live responses.

## Code documentation standard

Use [KDoc](https://kotlinlang.org/docs/kotlin-doc.html) and
[Kotlin's documentation conventions](https://kotlinlang.org/docs/coding-conventions.html#documentation-comments):

- Give each production class/object/enum and public/internal non-override function a KDoc summary.
  Explain responsibility and caller-facing behavior, not the spelling of the declaration.
- Document units, null semantics, ordering, ownership/closing, thread expectations and side effects
  wherever they are material. Use `@param`, `@property`, `@return` and `@throws` when they clarify
  a contract; do not add redundant tags or imply Kotlin has checked exceptions.
- Use resolvable `[Symbol]` links for related Kotlin declarations and relative links in Markdown.
  Document constructor properties together with the data model when appropriate.
- Document meaningful private algorithms and security/lifecycle decisions with focused KDoc or
  ordinary comments. Overrides need extra documentation only for app-specific behavior beyond
  the Android contract. Tests should use descriptive names and comments for non-obvious setup.
- Keep comments accurate: distinguish requested intervals from guarantees, local connectivity
  from server acceptance and runtime duration from battery usage. Do not hide current limitations.
- Update architecture/setup/privacy/release documentation in the same change as affected behavior.
  Avoid stale version claims, copied credentials, personal paths and generated boilerplate.

`check-documentation.py` is a lightweight declaration-coverage guard for this project's source
layout, not a Kotlin parser or proof of documentation quality. Compiler/lint checks and review
remain required. KDoc is readable in Android Studio; a separate generated API site is not required.

## Behavioral invariants for review

Opening the app or changing source/device/location must not enqueue a logging sample. Explicit
device discovery may read provider state without saving it. Manual refresh must
not reset or suppress periodic work. Preserve cancellation; never convert it into a success/failure
notification. Keep canonical Celsius/percent/epoch-millisecond units and null traits throughout
storage/CSV. Apply the same identity filters to current values, history, alerts and widget scales.
Keep credentials out of logs, backups, screenshots, fixtures and Git history. Add regression tests
when changing these contracts.

## Publishing

Before committing, inspect the diff and staged filenames; ignore SDK downloads, build outputs,
signing material, local configuration and phone exports. The publication scan checks tracked files
and history for known risky content but is not an exhaustive secret detector. Screenshots need
visual privacy review. See [docs/PUBLISHING.md](docs/PUBLISHING.md) and
[docs/RELEASING.md](docs/RELEASING.md). Do not change repository visibility as part of code work.
