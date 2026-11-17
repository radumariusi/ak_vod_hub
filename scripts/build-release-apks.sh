#!/usr/bin/env bash
set -euo pipefail

prefix="$RUNNER_TEMP/ak-vod-hub-$RELEASE_NAME"
build_tools=$(find "${ANDROID_HOME:?}/build-tools" -name apksigner -type f | sort -V | tail -n 1)
test -x "$build_tools"

gh release download v0.1.14 --pattern ak-vod-hub-0.1.14.apk --dir "$RUNNER_TEMP"

certificate() {
  local report
  report=$("$build_tools" verify --print-certs "$1")
  sed -n 's/^Signer #[0-9]* certificate SHA-256 digest: //p' <<< "$report"
}

base_cert=$(certificate "$RUNNER_TEMP/ak-vod-hub-0.1.14.apk")
test -n "$base_cert"

build_and_verify() {
  local abi=$1 asset=$2 expected_abis=$3
  shift 3
  local apk=app/build/outputs/apk/release/app-release.apk
  rm -f "$apk"
  ./gradlew --no-daemon --stacktrace :app:assembleRelease "-Pakvod.versionCode=$RELEASE_CODE" "$@"
  test -f "$apk"
  test "$(certificate "$apk")" = "$base_cert"
  local actual_abis
  actual_abis=$(unzip -Z1 "$apk" | awk -F/ '$1 == "lib" && $3 ~ /\.so$/ {print $2}' | sort -u | paste -sd ' ')
  test "$actual_abis" = "$expected_abis"
  cp "$apk" "$asset"
  printf '| %s | %s |\n' "$abi" "$(stat -c %s "$asset")" >> "$GITHUB_STEP_SUMMARY"
}

printf '| APK | Octeți |\n|---|---:|\n' >> "$GITHUB_STEP_SUMMARY"
build_and_verify universal "$prefix.apk" "arm64-v8a armeabi-v7a x86_64"
for abi in armeabi-v7a arm64-v8a x86_64; do
  build_and_verify "$abi" "$prefix-$abi.apk.bin" "$abi" "-Pakvod.releaseAbi=$abi"
done
