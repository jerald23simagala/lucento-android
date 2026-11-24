#!/usr/bin/env bash
# Prepares a booted Lucento_Shots_API35 emulator for tools/screenshots/capture_marketing.sh (docs/QUALITY.md ›
# "Marketing screenshots"): 3-button navigation, animations off, no on-screen keyboard with the hardware one, no system
# crash dialogs, Manila time on the demo month's day (Sunday 2026-11-08 10:00), then waits until the guest has settled.
# Boot the AVD first, for example: emulator -avd Lucento_Shots_API35 -read-only -no-snapshot-save -no-boot-anim
# -gpu host -no-audio -memory 2048 (read-only keeps the AVD's saved data as it was).
set -euo pipefail
export MSYS_NO_PATHCONV=1
ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}"
[ -x "$ADB" ] || ADB=adb
"$ADB" wait-for-device
until [ "$("$ADB" shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 5; done
"$ADB" shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.threebutton
for k in window_animation_scale transition_animation_scale animator_duration_scale; do "$ADB" shell settings put global "$k" 0; done
"$ADB" shell settings put secure show_ime_with_hard_keyboard 0
"$ADB" shell settings put global hide_error_dialogs 1
"$ADB" shell settings put global auto_time 0
"$ADB" shell settings put global auto_time_zone 0
"$ADB" shell service call alarm 3 s16 Asia/Manila >/dev/null
"$ADB" shell cmd alarm set-time 1794103200000 # 2026-11-08 10:00 in Manila
"$ADB" shell settings put system font_scale 1.0
echo "clock: $("$ADB" shell date | tr -d '\r')"
for _ in $(seq 1 90); do
  load="$("$ADB" shell cat /proc/loadavg | cut -d' ' -f1)"
  awk "BEGIN{exit !($load < 5)}" && { echo "settled (load $load)"; exit 0; }
  sleep 10
done
echo "still busy after 15 minutes (load $load)" >&2
exit 1
