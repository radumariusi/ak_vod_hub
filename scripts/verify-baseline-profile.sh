#!/usr/bin/env bash
set -euo pipefail

package=io.github.radumariusi.akvodhub
profiles=app/src/main/generated/baselineProfiles
if ! ./gradlew --no-daemon :app:generateBaselineProfile; then
  python3 - <<'PYTHON'
from pathlib import Path
from xml.etree import ElementTree

results = Path("baselineprofile/build/outputs/androidTest-results")
for report in sorted(results.rglob("TEST-*.xml")):
    for testcase in ElementTree.parse(report).iter("testcase"):
        for kind in ("failure", "error"):
            for issue in testcase.findall(kind):
                print(f"{testcase.get('classname')}.{testcase.get('name')}: {issue.get('message', '')}")
                print(issue.text or "")
PYTHON
  adb logcat -d -b crash -t 120 || true
  exit 1
fi
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
