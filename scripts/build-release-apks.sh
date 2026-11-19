#!/usr/bin/env bash
set -euo pipefail

prefix="$RUNNER_TEMP/ak-vod-hub-$RELEASE_NAME"
# Amprenta SHA-256 a certificatului de semnare (cel din v0.1.14, DD:DD:81:5F…). Un APK semnat cu altă cheie
# nu s-ar mai putea instala peste versiunea existentă, deci release-ul se oprește.
expected_cert=$(tr -d '[:space:]' < scripts/release-cert.sha256)
# apksigner vine cu Android SDK-ul runner-ului: nu depinde de cache-ul Gradle sau de versiunea AGP.
build_tools=$(ls -d "${ANDROID_HOME:?Lipsește ANDROID_HOME}"/build-tools/*/ | sort -V | tail -1)
apksigner="${build_tools}apksigner"

certificate() {
  local output
  output=$("$apksigner" verify --print-certs "$1")
  if [[ $(grep -c '^Signer #[0-9]* certificate SHA-256 digest:' <<<"$output") -ne 1 ]]; then
    echo "$1: se așteaptă un singur certificat de semnare." >&2
    return 1
  fi
  sed -n 's/^Signer #1 certificate SHA-256 digest: //p' <<<"$output" | tr -d ':[:space:]' | tr 'A-F' 'a-f'
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
  local apk_cert
  apk_cert=$(certificate "$apk")
  if [[ "$apk_cert" != "$expected_cert" ]]; then
    echo "Certificatul APK-ului $abi ($apk_cert) diferă de cel așteptat ($expected_cert)." >&2
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
