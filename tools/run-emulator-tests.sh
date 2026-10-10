#!/usr/bin/env bash
set -euo pipefail

serial=${1:-}
if [[ ! "$serial" =~ ^emulator-[0-9]+$ ]]; then
  echo 'Refusing to run tests: an explicit emulator serial is required.' >&2
  exit 2
fi
shift
adb=${ADB:-adb}
if [[ $("$adb" -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r') != 1 ]]; then
  echo 'Refusing to run tests: the selected device is not an emulator.' >&2
  exit 2
fi
cd "$(dirname "$0")/.."
./gradlew assembleDebug assembleDebugAndroidTest --console=plain
"$adb" -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk
"$adb" -s "$serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
result=$(mktemp)
trap 'rm -f "$result"' EXIT
"$adb" -s "$serial" shell am instrument -w -r "$@" \
  com.github.garynasser.correction_notebook.test/androidx.test.runner.AndroidJUnitRunner | tee "$result"
if ! grep -Eq '^OK \([1-9][0-9]* tests?\)' "$result"; then
  echo 'Instrumentation did not report a passing test run.' >&2
  exit 1
fi
