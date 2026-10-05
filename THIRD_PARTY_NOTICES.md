# Third-party software and data

The [MIT license](LICENSE) covers this project's original source and project-created assets.
It does not relicense Google SDKs, other libraries, trademarks or weather data.

## Notices shipped in the APK

Open **Settings → About → Open-source licenses** to read dependency notices offline. Google's
OSS Licenses Gradle plugin generates the library index, SDK-supplied full license texts and
POM-declared license links from resolved runtime dependencies. These notices are packaged in the
release APK; following an external license link can require internet access. The release packager
rejects APKs without both non-empty resources or containing the plugin's debug placeholder.
Debug variants show that placeholder because AGP supplies dependency reports only for release
variants. Generated artifacts are not checked into Git.

Direct runtime inputs (transitive dependencies are listed in the generated screen):

| Dependency | Role | License/terms source |
| --- | --- | --- |
| AndroidX Core, Activity, Lifecycle and WorkManager | Android integration and scheduling | [AndroidX licenses](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt) (Apache-2.0) |
| Kotlin runtime and kotlinx.coroutines | Kotlin language/runtime and asynchronous work | [Kotlin license](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt), [coroutines license](https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt) (Apache-2.0) |
| Google Play services Home / Home Types | Optional Google Home access | [Google Home SDK download and terms](https://developers.home.google.com/apis/android/sdk); SDK-provided embedded third-party notices |
| Google Play services OSS Licenses | Offline dependency-notice screen | [Official integration guide](https://developers.google.com/android/guides/opensource); SDK-provided embedded notices |

The generated index describes open-source components, not a blanket license for Google's SDKs.
Google Home SDK downloads require accepting Google's applicable terms; do not redistribute the
SDK ZIP or Maven repository. APK distribution must comply with those terms. This file is a notice
inventory, not a legal opinion or confirmation of commercial authorization. Review applicable
terms again before commercial use or changing distribution.

Gradle, Android build plugins, the OSS license-generation plugin, JUnit, JSON test fixtures and
Android instrumentation tools are development inputs, not all bundled runtime libraries. Consult
their upstream licenses if distributing those tools separately. Rebuild and inspect notices when
dependencies change; automated generation cannot resolve missing or incorrect upstream metadata.

## Outdoor data and attribution

Outdoor data is supplied by [Open-Meteo](https://open-meteo.com/). Its data uses
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/); attribution appears in the app and here.
The free hosted API is for non-commercial use under [Open-Meteo's terms](https://open-meteo.com/en/terms).
Review limits and licensing before commercial distribution; a software MIT license does not waive
API restrictions. Outdoor values are weather-model estimates, not readings from a sensor at the address.

Google and Nest names remain their owners' trademarks. This project is independent and not endorsed
by them. Do not imply an official Google product or certification.
