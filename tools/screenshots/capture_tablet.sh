#!/usr/bin/env bash
# Captures Lucento's wide-window split (rail, list on the left, what it opened on the right) on a tablet emulator, for the
# Google Play tablet screenshots, in US dollars and pesos (docs/QUALITY.md › "Tablet screenshots").
#
#   tools/screenshots/capture_tablet.sh <out dir> [USD] [PHP]
#
# For each currency (default both): the screen is made phone-sized, the tour seeds the demo data exactly as for the phone
# shots (capture_marketing.sh tour-usd / tour-php), the screen goes back to tablet size with gesture navigation (no
# taskbar), the debug plan simulator is set to Pro Monthly (the tour ends on the free plan), and the split screens are
# captured in landscape (t01…t08) plus three portrait ones (p01…p03) into <out dir>/tablet-<currency>/.
# Before running: AVD Lucento_Tablet_API35 (Pixel Tablet, 2560 × 1600 at 320 dpi, API 35 google_apis_playstore) is
# booted and prepared by setup_marketing_emulator.sh, and the debug and androidTest APKs are built.
set -euo pipefail
export MSYS_NO_PATHCONV=1
ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}"
[ -x "$ADB" ] || ADB=adb
OUT="${1:?usage: capture_tablet.sh <out dir> [USD] [PHP]}"
shift
CURRENCIES=("$@")
[ ${#CURRENCIES[@]} -gt 0 ] || CURRENCIES=(USD PHP)
HERE="$(cd "$(dirname "$0")" && pwd)"
APP=com.lucento.app.debug

dump() { "$ADB" shell uiautomator dump /data/local/tmp/ui.xml >/dev/null 2>&1; "$ADB" shell cat /data/local/tmp/ui.xml; }
# Taps the centre of the Nth node (default 1st) whose text or content description is exactly $1.
tap() {
  local b
  b=$(dump | grep -oE "(text|content-desc)=\"$1\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" | sed -n "${2:-1}p" |
    grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]') || true
  [ -n "$b" ] || { echo "  not found: $1" >&2; return 1; }
  set -- $(echo "$b" | tr '[],' '   ')
  "$ADB" shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}
waitfor() { for _ in $(seq 1 "${2:-15}"); do dump | grep -q "$1" && return 0; sleep 1; done; echo "  never saw: $1" >&2; return 1; }
# Bounds "x1 y1 x2 y2" of the first node whose text starts with $1 (empty when there is none).
bounds_of() {
  dump | grep -oE "text=\"$1[^\"]*\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" | head -1 |
    grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' | tr '[],' '   ' || true
}
# Drags slowly (no fling) at x from y1 to y2, so a pane scrolls by about y1 - y2 pixels, less the touch slop.
SLOP=18
drag() { "$ADB" shell input swipe "$1" "$2" "$1" "$3" 1500; sleep 2; }
# Scrolls the pane at x until the node whose text starts with $2 has its top within 20 px of y=$3 (up to three drags,
# either way), dragging around y=$4 (default 1300; it must be inside the scrolling list, not on a footer). The stops
# below put a card's top right under a pane's header, so nothing shows sliced there.
bring_to() {
  local x="$1" text="$2" target="$3" from="${4:-1300}" b top by
  for _ in 1 2 3; do
    b=$(bounds_of "$text"); [ -n "$b" ] || { echo "  not found: $text" >&2; return 1; }
    set -- $b; top=$2; by=$(( top - target ))
    [ ${by#-} -le 20 ] && return 0
    if [ "$by" -gt 0 ]; then
      [ "$by" -gt $(( from - 300 )) ] && by=$(( from - 300 ))
      drag "$x" "$from" $(( from - by - SLOP ))
    else
      by=$(( -by )); [ "$by" -gt 900 ] && by=900
      drag "$x" $(( from - 900 )) $(( from - 900 + by + SLOP ))
    fi
  done
}
# A shot of a screen with a focused text field: the caret blinks, so take a few frames and keep the one where the
# field's start (bounds $2…$5) has the fewest caret-blue pixels.
shot_without_caret() {
  local name="$1" x1="$2" y1="$3" x2="$4" y2="$5" best="" fewest=999999 n i
  for i in 1 2 3 4 5; do
    shot "$name-$i" >/dev/null
    n=$(python -I -c "
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert('RGB'); x1, y1, y2 = int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4])
print(sum(1 for x in range(x1 + 10, x1 + 70) for y in range(y1 + 10, y2 - 10)
          if (lambda p: p[2] > 180 and p[0] < 150)(im.getpixel((x, y)))))" "$DIR/$name-$i.png" "$x1" "$y1" "$y2")
    if [ "$n" -lt "$fewest" ]; then fewest=$n; best=$i; fi
    sleep 0.3
  done
  mv "$DIR/$name-$best.png" "$DIR/$name.png"; rm -f "$DIR/$name"-[0-9].png
  echo "  $name (caret pixels: $fewest)"
}
# The emulator's screencap can start with a "[Warning] Multiple displays" line: keep the bytes from the PNG header on.
shot() {
  "$ADB" exec-out screencap -p > "$DIR/$1.raw"
  python -I -c "import sys;b=open(sys.argv[1],'rb').read();open(sys.argv[2],'wb').write(b[b.find(b'\x89PNG'):])" "$DIR/$1.raw" "$DIR/$1.png"
  rm -f "$DIR/$1.raw"; echo "  $1"
}
back() { "$ADB" shell input keyevent KEYCODE_BACK; sleep 2; }
launch() {
  "$ADB" shell am force-stop "$APP"
  "$ADB" shell am start -W -n "$APP/com.lucento.app.MainActivity" >/dev/null
  waitfor 'text="Dashboard"' 30; sleep 3
}
rotate() { "$ADB" shell settings put system accelerometer_rotation 0; "$ADB" shell settings put system user_rotation "$1"; sleep 4; }
# Waits (up to 10 minutes) until the guest's load average is under 3: a tour started on a busy emulator can die at
# launch with an ANR ("Process crashed.", "failed to complete startup"); the average lags, hence the margin.
settle() {
  local load
  for _ in $(seq 1 60); do
    load="$("$ADB" shell cat /proc/loadavg | cut -d' ' -f1)"
    awk "BEGIN{exit !($load < 3)}" && return 0
    sleep 10
  done
  echo "  still busy (load $load), going on" >&2
}

for cur in "${CURRENCIES[@]}"; do
  case "$cur" in
    USD) run=tour-usd; message="Lunch 45, groceries 120, and paid the electricity bill" ;;
    PHP) run=tour-php; message="Lunch 150, groceries 1200, and paid the electricity bill" ;;
    *) echo "unknown currency: $cur" >&2; exit 2 ;;
  esac
  DIR="$OUT/tablet-$(echo "$cur" | tr 'A-Z' 'a-z')"
  mkdir -p "$DIR"

  echo "== $cur: seeding at phone size"
  rotate 0
  "$ADB" shell wm size 1080x2400
  "$ADB" shell wm density 420
  "$ADB" shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.threebutton
  sleep 30 # the screen-size switch redraws System UI and the launcher
  settle
  bash "$HERE/capture_marketing.sh" "$OUT/seed" "$run"

  echo "== $cur: tablet size, Pro plan"
  "$ADB" shell wm size reset
  "$ADB" shell wm density reset
  "$ADB" shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.gestural
  "$ADB" shell am force-stop "$APP"
  plan="$DIR/plan.xml" # PlanSimulator's store (debug builds only): shared_prefs/lucento_debug_plan.xml
  printf '%s\n' "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>" \
    '<map><string name="simulated_plan">PRO_MONTHLY</string></map>' > "$plan"
  "$ADB" push "$(cd "$DIR" && { pwd -W 2>/dev/null || pwd; })/plan.xml" /data/local/tmp/plan.xml >/dev/null
  rm -f "$plan"
  "$ADB" shell "run-as $APP sh -c 'cat /data/local/tmp/plan.xml > shared_prefs/lucento_debug_plan.xml'"
  "$ADB" shell run-as "$APP" cat shared_prefs/lucento_debug_plan.xml | grep -q PRO_MONTHLY || { echo "plan not set" >&2; exit 1; }
  sleep 5

  echo "== $cur: landscape"
  launch
  # The tour's last steps add a $260 "Celebration dinner" to put Restaurants over budget; the phone set's screens are
  # taken before it, so it goes here too (swipe the row, Delete), and the month reads as the phone set's does.
  tap "Transactions"; sleep 4
  b=$(bounds_of "Celebration dinner")
  if [ -n "$b" ]; then
    set -- $b
    "$ADB" shell input swipe 1050 $(( ($2 + $4) / 2 )) 450 $(( ($2 + $4) / 2 )) 500; sleep 2
    tap "Delete"; sleep 2
    tap "Delete permanently"; sleep 4 # "Delete this transaction?"
    waitfor 'text="Transactions"' 10 # the list is back (a dialog would hide the row and fool the check below)
    [ -z "$(bounds_of "Celebration dinner")" ] || { echo "Celebration dinner is still there" >&2; exit 1; }
  fi
  tap "Home"; sleep 4

  # Insights beside the dashboard, scrolled to its income and spending on the free plan's three months.
  drag 1800 1300 500
  tap "3 months" || true; sleep 2
  bring_to 1800 "Income and expenses" 225 # its whole card shows; the next heading starts below the screen's edge
  shot t01-home-insights
  tap "Scheduled transactions"; sleep 4; shot t04-home-scheduled; back
  # Goals and Milestones each with the dashboard scrolled to a different part, so no two screens repeat the left half,
  # and each stop puts a card's top right under the dashboard's header.
  tap "Savings goals"; sleep 4
  drag 600 1300 600
  bring_to 600 "To watch" 330
  shot t02-home-goals; back
  drag 600 500 1300; drag 600 500 1300; drag 600 500 1300 # back to the top, where the quick links are
  tap "Milestones"; sleep 4
  drag 600 1300 900
  bring_to 600 "Monthly budget" 345
  shot t03-home-milestones; back
  drag 600 500 1300; drag 600 500 1300; drag 600 500 1300

  tap "Add transaction"; waitfor "Tell me about your transaction"; sleep 1
  b=$(dump | grep -oE 'class="android.widget.EditText"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1 |
    grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]')
  field=$(echo "$b" | tr '[],' '   ')
  set -- $field; "$ADB" shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 1
  "$ADB" shell input text "$(echo "$message" | sed 's/ /%s/g')"; sleep 1
  tap "Send"; waitfor "transaction is incomplete" 20; sleep 2
  back # hides the keyboard, and with it the keyboard's strip along the bottom (the field keeps its caret)
  bring_to 1800 "Here are 3 transactions" 300 1100 # the review's rows, Lunch and groceries first (above its footer)
  shot_without_caret t05-composer $field
  for _ in 1 2 3; do # the composer asks before discarding its rows
    back
    if dump | grep -q 'text="Discard"'; then tap "Discard"; sleep 2; break; fi
  done
  tap "Transactions"; sleep 4
  tap "Market"; sleep 3 # one transaction opened beside the list, its row marked
  shot t06-transactions-newest
  tap "Budget"; sleep 4; shot t07-budget
  tap "Wallets"; sleep 4; shot t08-wallets
  # The time on the tablet now, for the generated status bar (after every timestamp the screens show).
  "$ADB" shell date +%H:%M | tr -d '\r' | sed 's/^0//' > "$DIR/clock.txt"

  echo "== $cur: portrait (one pane beside the rail: 800 dp is below the split's 804)"
  rotate 1
  launch
  shot p01-home
  tap "Transactions"; sleep 4; shot p02-transactions
  tap "Budget"; sleep 4; shot p03-budget
  rotate 0
  anr="$("$ADB" shell dumpsys window windows | grep -c "Application Not Responding" || true)"
  echo "== $cur: $(ls "$DIR" | wc -l) shots in $DIR (ANR windows: $anr)"
done
