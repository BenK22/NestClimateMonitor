#!/usr/bin/env bash
set -euo pipefail

required=(
  HOME_SDK_URL
  RELEASE_KEYSTORE_BASE64
  RELEASE_STORE_PASSWORD
  RELEASE_KEY_ALIAS
  RELEASE_KEY_PASSWORD
)

missing=0
for name in "${required[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    echo "Missing $name"
    missing=1
  fi
done
[[ "$missing" == 0 ]] || exit 1

app_version="$(sed -n 's/^[[:space:]]*versionName = "\([^"]*\)"/\1/p' app/build.gradle.kts)"
[[ -n "$app_version" ]] || { echo "Could not read versionName from app/build.gradle.kts"; exit 1; }

if [[ "${GITHUB_REF:-}" == refs/tags/* ]]; then
  expected="refs/tags/v$app_version"
  [[ "$GITHUB_REF" == "$expected" ]] || {
    echo "Tag ${GITHUB_REF#refs/tags/} does not match Android versionName $app_version (expected v$app_version)"
    exit 1
  }
fi
