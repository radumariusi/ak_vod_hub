#!/usr/bin/env bash
set -euo pipefail

package=io.github.radumariusi.akvodhub
profiles=app/src/main/generated/baselineProfiles
./gradlew --no-daemon :app:generateBaselineProfile
test -s "$profiles/baseline-prof.txt"
grep -Fq "Lio/github/radumariusi/akvodhub/MainActivity;" "$profiles/baseline-prof.txt"
grep -Fq "/BrowseScreenKt;" "$profiles/baseline-prof.txt"

./gradlew --no-daemon :app:assembleBenchmarkRelease
apk=app/build/outputs/apk/benchmarkRelease/app-benchmarkRelease.apk
unzip -p "$apk" assets/dexopt/baseline.prof > "$RUNNER_TEMP/baseline.prof"
test -s "$RUNNER_TEMP/baseline.prof"

if adb shell pm path "$package" | grep -q "^package:"; then
  adb uninstall "$package"
fi
adb install "$apk"
result=$(adb shell am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE \
  -n "$package/androidx.profileinstaller.ProfileInstallReceiver")
printf '%s\n' "$result"
grep -Eq 'result=1([,[:space:]]|$)' <<< "$result"
