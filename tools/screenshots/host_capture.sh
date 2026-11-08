#!/usr/bin/env bash
# Host side of ScreenshotTourTest's opt-in host-capture mode.
#
# Some API 36.1 emulator hosts can't read pixels back inside the guest: `screencap` and UiAutomation screenshots abort
# on `hasReadColorBufferDma`. Run the tour with `-Pandroid.testInstrumentationRunnerArguments.hostCapture=true` and
# keep this script running beside it: for every screen the test leaves `<name>.request` in /data/local/tmp/lucento-shots
# and waits; this script takes the frame from the emulator itself (`adb emu screenrecord screenshot`) and answers with
# `<name>.done`, so the screen can't change before the capture.
#
# Usage: tools/screenshots/host_capture.sh <output dir>   (stop it by creating <output dir>/.stop, or with Ctrl+C)
# Env:   ANDROID_SERIAL (default emulator-5554), ADB (default: adb on PATH)
#
# The same emulator also needs 3-button navigation, or SurfaceFlinger's region sampling for the gesture handle keeps
# aborting on the same assertion:
#   adb shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.threebutton
set -u
SERIAL="${ANDROID_SERIAL:-emulator-5554}"
ADB="${ADB:-adb}"
OUT="${1:-./lucento-shots}"
DIR=/data/local/tmp/lucento-shots
mkdir -p "$OUT"
rm -f "$OUT/.stop"
export MSYS_NO_PATHCONV=1 # Git Bash would rewrite /data/local/tmp paths

hostpath() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi; }

echo "host capture: watching $SERIAL, saving to $OUT"
while [ ! -f "$OUT/.stop" ]; do
  for req in $("$ADB" -s "$SERIAL" shell "ls $DIR/*.request 2>/dev/null" 2>/dev/null); do
    req="${req%$'\r'}"
    name="$(basename "$req" .request)"
    [ -n "$name" ] || continue
    "$ADB" -s "$SERIAL" emu screenrecord screenshot "$(hostpath "$OUT/$name.png")" >/dev/null 2>&1
    "$ADB" -s "$SERIAL" shell "touch $DIR/$name.done"
    echo "captured $name"
  done
  sleep 0.25
done
echo "host capture: stopped"
