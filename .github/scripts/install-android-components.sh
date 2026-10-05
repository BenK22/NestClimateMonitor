#!/usr/bin/env bash
# Hosted runners have an Android SDK but do not necessarily put sdkmanager in PATH.
set -euo pipefail

android_sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[[ -n "$android_sdk_dir" && -d "$android_sdk_dir" ]] || {
  echo "Set ANDROID_HOME to the installed Android SDK" >&2
  exit 1
}
if [[ -d "$android_sdk_dir/platforms/android-36" && -x "$android_sdk_dir/build-tools/35.0.0/aapt" ]]; then
  echo "Required Android SDK components are already installed"
  exit 0
fi

mapfile -t sdk_managers < <(find "$android_sdk_dir/cmdline-tools" -maxdepth 3 -type f -name sdkmanager | sort -V)
[[ "${#sdk_managers[@]}" -gt 0 ]] || { echo "Android command-line tools are unavailable" >&2; exit 1; }
sdk_manager="${sdk_managers[-1]}"
"$sdk_manager" --sdk_root="$android_sdk_dir" "platforms;android-36" "build-tools;35.0.0" < /dev/null
