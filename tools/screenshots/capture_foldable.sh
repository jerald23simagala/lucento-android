#!/usr/bin/env bash
# Captures Lucento on a foldable's screens for Google Play's "Foldable" screenshots (docs/QUALITY.md › "Foldable
# screenshots"), in US dollars and pesos, at the three shapes Play asks for (Play Console Help, November 2026):
#
#   square  2106 × 2106 at 390 dpi =  864 ×  864 dp  a square inner screen (Pixel 9 Pro Fold class): the split
#   wide    2688 × 2016 at 420 dpi = 1024 ×  768 dp  a 4:3 inner screen: the split
#   outer   1260 × 2016 at 420 dpi =  480 ×  768 dp  a 4:3 foldable's 10:16 cover screen: the phone layout
#
# The Pixel Tablet AVD's screen is set to each shape (wm size / wm density), so every capture already has Play's exact
# size, and the app lays itself out for that window as it does on a foldable held open flat (it follows the window's width
# in dp: navigation/NavLayout.kt).
#
#   tools/screenshots/capture_foldable.sh <out dir> [USD] [PHP]
#
# For each currency (default both): the screen is made phone-sized and the tour seeds the demo data exactly as for the phone
# shots (capture_marketing.sh tour-usd / tour-php); the debug plan simulator is set to Pro Monthly (the tour ends on the
# free plan) and gesture navigation goes on; then each shape's screens go to <out dir>/fold-<currency>/<shape>-*.png, and
# <shape>.txt records "<width> <height> <status bar px> <gesture bar px> <time>" for the renderer (lucento-studio
# lab/play-graphics). SHAPES="square wide outer" (the default) picks the shapes; SEED=0 skips the tour when the demo data
# is already there, to redo a shape. Before running: AVD Lucento_Tablet_API35 (Pixel Tablet, API 35 google_apis_playstore)
# is booted and prepared by setup_marketing_emulator.sh, and the debug and androidTest APKs are installed.
set -euo pipefail
export MSYS_NO_PATHCONV=1
ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}"
[ -x "$ADB" ] || ADB=adb
OUT="${1:?usage: capture_foldable.sh <out dir> [USD] [PHP]}"
shift
CURRENCIES=("$@")
[ ${#CURRENCIES[@]} -gt 0 ] || CURRENCIES=(USD PHP)
SHAPES="${SHAPES:-square wide outer}"
SEED="${SEED:-1}"
HERE="$(cd "$(dirname "$0")" && pwd)"
APP=com.lucento.app.debug
W=1080 H=2400 DPI=420 W_DP=411 H_DP=914 # the current shape (shape() sets them)

# dp → px on the current shape (rounded).
dp() { awk -v d="$1" -v k="$DPI" 'BEGIN { printf "%d", d * k / 160 + 0.5 }'; }
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
# Drags slowly (no fling) at x from y1 to y2 (px), so a pane scrolls by about y1 - y2, less the touch slop.
drag() { "$ADB" shell input swipe "$1" "$2" "$1" "$3" 1500; sleep 2; }
# Scrolls the pane at x by about $2 dp (negative: back up), from 70 % down the screen (inside the list, above any footer).
scroll_by() {
  local from; from=$(dp $(( H_DP * 7 / 10 )))
  if [ "$2" -gt 0 ]; then drag "$1" "$from" $(( from - $(dp "$2") )); else drag "$1" $(( from - $(dp $(( -$2 ))) )) "$from"; fi
}
# Back to the top of the pane at x.
to_top() { for _ in 1 2 3; do drag "$1" $(dp 180) $(( H - $(dp 140) )); done; }
# Taps $2, scrolling the pane at x down 250 dp at a time (up to four times) until it shows.
tap_scrolling() {
  local i
  for i in 1 2 3 4 5; do
    [ -n "$(dump | grep -oE "(text|content-desc)=\"$2\"")" ] && { tap "$2"; return 0; }
    [ "$i" -lt 5 ] && scroll_by "$1" 250
  done
  echo "  not found after scrolling: $2" >&2; return 1
}
# Scrolls the pane at x until the node whose text starts with $2 has its top within 10 dp of y = $3 dp (up to three
# drags, either way, after up to four 300 dp drags down to find it: a lazy list leaves cards off the screen out of the
# dump), dragging around y = $4 dp (default 70 % down the screen; it must be inside the scrolling list, not on a footer),
# to within $5 dp (default 10). The stops below put a card's top right under a pane's header, so nothing shows sliced there.
bring_to() {
  local x="$1" text="$2" target from b top by slop tol most looked=0
  target=$(dp "$3"); from=$(dp "${4:-$(( H_DP * 7 / 10 ))}")
  slop=$(dp 9); tol=$(dp "${5:-10}"); most=$(( from - $(dp 160) ))
  until [ -n "$(bounds_of "$text")" ]; do
    [ "$looked" -lt 4 ] || { echo "  not found: $text" >&2; return 1; }
    drag "$x" "$from" $(( from - $(dp 300) )); looked=$(( looked + 1 ))
  done
  for _ in 1 2 3; do
    b=$(bounds_of "$text"); [ -n "$b" ] || { echo "  lost: $text" >&2; return 1; }
    set -- $b; top=$2; by=$(( top - target ))
    [ ${by#-} -le "$tol" ] && return 0
    if [ "$by" -gt 0 ]; then
      [ "$by" -gt "$most" ] && by=$most
      drag "$x" "$from" $(( from - by - slop ))
    else
      by=$(( -by )); [ "$by" -gt "$most" ] && by=$most
      drag "$x" $(( from - most )) $(( from - most + by + slop ))
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
# The window hierarchy of the screen just shot, for the renderer's focus and lift regions.
keep_dump() { dump > "$DIR/$1.xml"; }
back() { "$ADB" shell input keyevent KEYCODE_BACK; sleep 2; }
launch() {
  "$ADB" shell am force-stop "$APP"
  "$ADB" shell am start -W -n "$APP/com.lucento.app.MainActivity" >/dev/null
  waitfor 'text="Dashboard"' 30; sleep 3
}
rotate() { "$ADB" shell settings put system accelerometer_rotation 0; "$ADB" shell settings put system user_rotation "$1"; sleep 4; }
# Waits (up to 10 minutes) until the guest's load average is under 3: a launch on a busy emulator can die with an ANR
# ("Process crashed.", "failed to complete startup"); the average lags, hence the margin.
settle() {
  local load
  for _ in $(seq 1 60); do
    load="$("$ADB" shell cat /proc/loadavg | cut -d' ' -f1)"
    awk "BEGIN{exit !($load < 3)}" && return 0
    sleep 10
  done
  echo "  still busy (load $load), going on" >&2
}
# Sets the screen to one shape (name, width and height in px, dpi) and works out where the app's panes are, as
# LucentoRoot lays them out: the 84 dp rail, the list pane (half of what's beside the rail, 360–480 dp), a 1 dp divider,
# then the detail pane. Below 600 dp wide it's the phone layout: one pane, the dock along the bottom.
shape() {
  SHAPE="$1" W="$2" H="$3" DPI="$4"
  W_DP=$(( W * 160 / DPI )); H_DP=$(( H * 160 / DPI ))
  "$ADB" shell wm size "${W}x${H}"
  "$ADB" shell wm density "$DPI"
  sleep 20 # the switch redraws System UI and the launcher
  settle
  if [ "$W_DP" -ge 804 ]; then # NavLayout.SPLIT_MIN_WIDTH_DP
    local beside=$(( W_DP - 84 )) list
    list=$(( beside / 2 )); [ "$list" -gt 480 ] && list=480
    LIST_L=$(dp 84); LIST_R=$(dp $(( 84 + list )))
    LEFT_X=$(dp $(( 84 + list / 2 ))); RIGHT_X=$(dp $(( 84 + list + 1 + (beside - list - 1) / 2 )))
  else
    LIST_L=0; LIST_R=$W; LEFT_X=$(( W / 2 )); RIGHT_X=$LEFT_X
  fi
  echo "== $cur: $SHAPE, ${W}×${H} px at $DPI dpi = ${W_DP}×${H_DP} dp (panes at x $LEFT_X / $RIGHT_X)"
}
# Status bar and gesture bar heights (px) from the window manager's insets, then the time: the renderer crops both bars
# and draws its own status bar with this time (after every timestamp the screens show).
record_shape() {
  local d sb nb
  d="$("$ADB" shell dumpsys window | tr -d '\r')"
  sb=$(echo "$d" | grep -oE "type=statusBars frame=\[0,0\]\[$W,[0-9]+\]" | head -1 | grep -oE '[0-9]+\]$' | tr -d ']')
  nb=$(echo "$d" | grep -oE "type=navigationBars frame=\[0,[0-9]+\]\[$W,$H\]" | head -1 | grep -oE 'frame=\[0,[0-9]+' | grep -oE '[0-9]+$')
  [ -n "$sb" ] && [ -n "$nb" ] || { echo "  insets not found (status '$sb', navigation '$nb')" >&2; return 1; }
  echo "$W $H $sb $(( H - nb )) $("$ADB" shell date +%H:%M | tr -d '\r' | sed 's/^0//')" > "$DIR/$SHAPE.txt"
  echo "  $SHAPE.txt: $(cat "$DIR/$SHAPE.txt")"
}
# The tour's last steps add a $260 "Celebration dinner" to put Restaurants over budget; the phone set's screens are taken
# before it, so it goes here too (swipe the row in the list, Delete), and the month reads as the phone set's does.
drop_celebration_dinner() {
  local b
  tap "Transactions"; sleep 4
  b=$(bounds_of "Celebration dinner")
  [ -n "$b" ] || return 0
  set -- $b
  "$ADB" shell input swipe $(( LIST_R - $(dp 30) )) $(( ($2 + $4) / 2 )) $(( LIST_L + $(dp 60) )) $(( ($2 + $4) / 2 )) 500; sleep 2
  tap "Delete"; sleep 2
  tap "Delete permanently"; sleep 4 # "Delete this transaction?"
  waitfor 'text="Transactions"' 10 # the list is back (a dialog would hide the row and fool the check below)
  [ -z "$(bounds_of "Celebration dinner")" ] || { echo "Celebration dinner is still there" >&2; exit 1; }
}
# The composer with "$message" read into three rows, its review scrolled so Lunch and groceries show first; then discarded.
composer() {
  local name="$1" b field
  tap "Add transaction"; waitfor "Tell me about your transaction"; sleep 1
  b=$(dump | grep -oE 'class="android.widget.EditText"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1 |
    grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]')
  field=$(echo "$b" | tr '[],' '   ')
  set -- $field; "$ADB" shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 1
  "$ADB" shell input text "$(echo "$message" | sed 's/ /%s/g')"; sleep 1
  tap "Send"; waitfor "transaction is incomplete" 20; sleep 2
  back # hides the keyboard, and with it the keyboard's strip along the bottom (the field keeps its caret)
  # Drag from just above the footer ("1 transaction is incomplete", the save button, the field): a drag that starts on
  # it scrolls nothing.
  b=$(bounds_of "1 transaction is incomplete"); [ -n "$b" ] || { echo "  no footer" >&2; return 1; }
  set -- $b
  # The review's rows, Lunch and groceries first. Its header between 153 and 174 dp keeps the conversation unsliced: the
  # mascot clears the "Into" bar above it, and the "Edit message" chip before it stays out of view.
  bring_to "$RIGHT_X" "Here are 3 transactions" 163 $(( $2 * 160 / DPI - 40 )) 6
  shot_without_caret "$name" $field
  keep_dump "$name"
  for _ in 1 2 3; do # the composer asks before discarding its rows
    back
    if dump | grep -q 'text="Discard"'; then tap "Discard"; sleep 2; break; fi
  done
}
# Scheduled's filter row (This month, All wallets, All categories) is wider than a 390 dp pane, whose edge cut "All
# categories" down to "Al", which reads as "AI": drag the row along in short, slow steps until that chip's text ends
# inside the pane, so every chip that shows is whole. A short drag at the row's end can't flick the pane's tabs over.
chips_to_end() {
  local b i
  for i in 1 2 3 4 5; do
    b=$(bounds_of "All categories"); [ -n "$b" ] || { echo "  no filter row" >&2; return 0; }
    set -- $b
    [ $(( W - $3 )) -ge "$(dp 30)" ] && { echo "  filter row at its end (All categories at x $1-$3)"; return 0; }
    "$ADB" shell input swipe $(( $1 - $(dp 10) )) $(( ($2 + $4) / 2 )) $(( $1 - $(dp 70) )) $(( ($2 + $4) / 2 )) 600
    sleep 2
  done
  echo "  filter row: All categories still cut" >&2
}
# The split (square and wide): each tab with what it opens beside it, as on the tablet (capture_tablet.sh).
split_set() {
  local p="$SHAPE"
  tap "Home"; sleep 4
  # Insights beside the dashboard, scrolled to its income and spending on the free plan's three months.
  scroll_by "$RIGHT_X" 350
  tap_scrolling "$RIGHT_X" "3 months" || true; sleep 2
  bring_to "$RIGHT_X" "Income and expenses" 112 # its card's top right under the pane's header
  shot "$p-home-insights"
  tap_scrolling "$LEFT_X" "Scheduled transactions"; sleep 4; shot "$p-home-scheduled"; back
  to_top "$LEFT_X"
  # Goals and Milestones each with the dashboard scrolled to a different part, so no two screens repeat the left half.
  tap_scrolling "$LEFT_X" "Savings goals"; sleep 4
  bring_to "$LEFT_X" "To watch" 165 "" 4
  shot "$p-home-goals"; back
  to_top "$LEFT_X"
  tap_scrolling "$LEFT_X" "Milestones"; sleep 4
  bring_to "$LEFT_X" "Monthly budget" 172 "" 4 # within 4 dp: nothing of the quick links peeks under the header
  shot "$p-home-milestones"; back
  to_top "$LEFT_X"
  composer "$p-composer"
  tap "Transactions"; sleep 4
  tap "Market"; sleep 3; shot "$p-transactions" # one transaction opened beside the list, its row marked
  tap "Budget"; sleep 4; chips_to_end; shot "$p-budget"; keep_dump "$p-budget"
  tap "Wallets"; sleep 4; shot "$p-wallets"
}
# The cover screen: the phone layout, one screen at a time.
phone_set() {
  local p="$SHAPE"
  tap "Home"; sleep 4; shot "$p-home"
  tap_scrolling "$LEFT_X" "Savings goals"; sleep 4; shot "$p-goals"; keep_dump "$p-goals"; back
  tap "Home"; sleep 2; to_top "$LEFT_X"
  tap_scrolling "$LEFT_X" "Milestones"; sleep 4; shot "$p-milestones"; back
  tap "Home"; sleep 2; to_top "$LEFT_X"
  composer "$p-composer"
  tap "Transactions"; sleep 4; shot "$p-transactions"
  tap "Budget"; sleep 4; shot "$p-budget"; keep_dump "$p-budget"
  tap "Wallets"; sleep 4; shot "$p-wallets"
}

for cur in "${CURRENCIES[@]}"; do
  case "$cur" in
    USD) run=tour-usd; message="Lunch 45, groceries 120, and paid the electricity bill" ;;
    PHP) run=tour-php; message="Lunch 150, groceries 1200, and paid the electricity bill" ;;
    *) echo "unknown currency: $cur" >&2; exit 2 ;;
  esac
  DIR="$OUT/fold-$(echo "$cur" | tr 'A-Z' 'a-z')"
  mkdir -p "$DIR"
  rotate 0

  if [ "$SEED" = 1 ]; then
    echo "== $cur: seeding at phone size"
    "$ADB" shell wm size 1080x2400
    "$ADB" shell wm density 420
    "$ADB" shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.threebutton
    sleep 30 # the screen-size switch redraws System UI and the launcher
    settle
    bash "$HERE/capture_marketing.sh" "$OUT/seed" "$run"

    echo "== $cur: Pro plan, gesture navigation"
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
  fi

  first=1
  for s in $SHAPES; do
    case "$s" in
      square) shape square 2106 2106 390 ;;
      wide) shape wide 2688 2016 420 ;;
      outer) shape outer 1260 2016 420 ;;
      *) echo "unknown shape: $s" >&2; exit 2 ;;
    esac
    launch
    if [ "$first" = 1 ]; then drop_celebration_dinner; first=0; fi
    if [ "$SHAPE" = outer ]; then phone_set; else split_set; fi
    record_shape
  done

  "$ADB" shell wm size reset
  "$ADB" shell wm density reset
  anr="$("$ADB" shell dumpsys window windows | grep -c "Application Not Responding" || true)"
  echo "== $cur: $(ls "$DIR"/*.png | wc -l) shots in $DIR (ANR windows: $anr)"
done
