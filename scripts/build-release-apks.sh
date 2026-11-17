#!/usr/bin/env bash
set -euo pipefail

prefix="$RUNNER_TEMP/ak-vod-hub-$RELEASE_NAME"
agp_version=$(sed -nE 's/^agp = "([^"]+)"$/\1/p' gradle/libs.versions.toml)
gradle_cache="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1"
apksig_dir="$gradle_cache/com.android.tools.build/apksig/$agp_version"
base_cert=''

gh release download v0.1.14 --pattern ak-vod-hub-0.1.14.apk --dir "$RUNNER_TEMP"

certificate() {
  java --class-path "$apksig_jar" scripts/ApkCertificate.java "$1"
}

build_and_verify() {
  local abi=$1 asset=$2 expected_abis=$3
  shift 3
  local apk=app/build/outputs/apk/release/app-release.apk
  rm -f "$apk"
  ./gradlew --no-daemon --stacktrace :app:assembleRelease "-Pakvod.versionCode=$RELEASE_CODE" "$@"
  if [[ ! -f "$apk" ]]; then
    echo "Lipsește APK-ul semnat pentru $abi." >&2
    return 1
  fi
  if [[ -z "$base_cert" ]]; then
    if [[ ! -d "$apksig_dir" ]]; then
      echo "Lipsește biblioteca apksig $agp_version din cache-ul Gradle." >&2
      return 1
    fi
    apksig_jar=$(find "$apksig_dir" -type f -name "apksig-$agp_version.jar" -print -quit)
    if [[ -z "$apksig_jar" ]]; then
      echo "Lipsește fișierul apksig-$agp_version.jar." >&2
      return 1
    fi
    base_cert=$(certificate "$RUNNER_TEMP/ak-vod-hub-0.1.14.apk")
  fi
  local apk_cert
  apk_cert=$(certificate "$apk")
  if [[ "$apk_cert" != "$base_cert" ]]; then
    echo "Certificatul APK-ului $abi diferă de certificatul v0.1.14." >&2
    return 1
  fi
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
