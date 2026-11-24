#!/usr/bin/env bash
# Captures the marketing screens (website, Google Play listing) from the current debug build on the API 35 shots
# emulator, in pesos and US dollars, with the demo month's history so Milestones has a year to show.
#
#   tools/screenshots/capture_marketing.sh <out dir> [run …]
#
# Runs (default: all four, in this order): tour-php, showcase-php, tour-usd, showcase-usd. Each run reinstalls the app
# fresh, runs one instrumented test with the demo arguments and pulls its shots into <out dir>/<run>/.
# Before running: the emulator is booted and set up as docs/QUALITY.md › "Marketing screenshots" describes (3-button
# navigation, animations off, no on-screen keyboard with the hardware one, clock on Sunday 2026-11-08 10:00), and the
# debug and androidTest APKs are built (./gradlew :app:assembleDebug :app:assembleDebugAndroidTest).
set -euo pipefail
export MSYS_NO_PATHCONV=1
ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}"
[ -x "$ADB" ] || ADB=adb
OUT="${1:?usage: capture_marketing.sh <out dir> [run …]}"
shift
RUNS=("$@")
[ ${#RUNS[@]} -gt 0 ] || RUNS=(tour-php showcase-php tour-usd showcase-usd)
# Windows' adb needs a Windows path: Git Bash's `pwd -W` gives one (C:/…); elsewhere plain `pwd`.
ROOT="$(cd "$(dirname "$0")/../.." && { pwd -W 2>/dev/null || pwd; })"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
RUNNER="com.lucento.app.debug.test/androidx.test.runner.AndroidJUnitRunner"

for run in "${RUNS[@]}"; do
  case "$run" in
    tour-php) args=(-e class com.lucento.app.ScreenshotTourTest -e demoCurrency PHP -e demoDark true -e demoHistory true) ;;
    tour-usd) args=(-e class com.lucento.app.ScreenshotTourTest -e demoCurrency USD -e demoDark true -e demoHistory true) ;;
    showcase-php) args=(-e class com.lucento.app.WebsiteShowcaseTest -e websiteShowcase true -e showcaseCurrency PHP -e demoHistory true) ;;
    showcase-usd) args=(-e class com.lucento.app.WebsiteShowcaseTest -e websiteShowcase true -e showcaseCurrency USD -e demoHistory true) ;;
    *) echo "unknown run: $run" >&2; exit 2 ;;
  esac
  echo "== $run: fresh install"
  "$ADB" uninstall com.lucento.app.debug >/dev/null 2>&1 || true
  "$ADB" uninstall com.lucento.app.debug.test >/dev/null 2>&1 || true
  "$ADB" install -r -t -g "$APK" >/dev/null
  "$ADB" install -r -t -g "$TEST_APK" >/dev/null
  "$ADB" shell rm -rf /data/local/tmp/lucento-shots
  "$ADB" shell settings put secure show_ime_with_hard_keyboard 0
  echo "== $run: $(date +%H:%M:%S) instrumenting"
  result="$("$ADB" shell am instrument -w -r "${args[@]}" "$RUNNER" | tr -d '\r')"
  echo "$result" | grep -E "INSTRUMENTATION_CODE|OK \(|FAILURES|Tests run|shortMsg|at com\.lucento" | head -12 || true
  mkdir -p "$OUT/$run"
  "$ADB" pull /data/local/tmp/lucento-shots/. "$OUT/$run/" >/dev/null
  echo "== $run: $(ls "$OUT/$run" | wc -l) shots in $OUT/$run"
  if ! echo "$result" | grep -q "^OK ("; then
    echo "== $run FAILED (shots pulled for diagnosis)" >&2
    exit 1
  fi
done
