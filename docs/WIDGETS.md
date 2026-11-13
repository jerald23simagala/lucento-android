# Home-screen widgets

Two widgets (owner decision, 2026-11-12: widgets only, both free). Design exploration: the private canvas "Lucento
Quick Add" (claude.ai) and studio `critic/reports/2026-11-12-quick-add-mockups-r1/r2*.md` (round 3 was stopped when the
owner narrowed the scope from widgets + notification + Share to widgets only).

| Widget | What it shows | Taps |
|---|---|---|
| **Quick add** (4×1, down to 2×1) | The composer's own bar: scanner, mic, and the "Add a transaction" field, in the composer's order. No amounts. | Field → composer (Type); mic → composer (Speak); scanner → composer (Scan) |
| **Safe to spend** (4×2, down to 3×2) | Home's Monthly budget card in brief: Safe to spend, "a day · days left", "After … bills due", the bar with "72% spent" (Home's ring) and Home's verdict chip — or Home's Net flow when the Budgets module is off | Body → Home; "+ Add" → composer; "Set amounts" → Budget; "Choose categories" → category setup |

## Rules

- **Widgets only open Lucento.** Nothing is read, typed or saved on the home screen; every transaction still goes
  through the composer (interpreter → review → one atomic save). A tap carries a target (`WidgetLaunch`) in its intent;
  `MainActivity` hands it to the main scaffold, which opens it once and never over the guided setup. Under App lock it
  lands behind the lock screen. Speak or Scan asked of a composer that is already open starts there, keeping what's
  written; the microphone and camera open only once the launch logo and the lock screen are gone.
- **The same figures and words as Home.** `SafeToSpendWidget` builds `HomeStateBuilder.build(...)` for today and
  `SafeToSpendWidgetModel.from(...)` reads it: the figure is `BudgetFormula.safeToSpendShown`, the per-day amount is
  `SafeToSpend.perDay` (rounded down), the bar is `BudgetBurn.consumedPercent` coloured by budget health, the chip is
  `paceVerdict(...)`. Zero states use Home's captions: "Bills due take what’s left", "You’re over budget this month".
  No budget yet and no categories yet use Home's own prompts ("Set amounts", "Choose categories"). Budgets switched
  off → Net flow, as Home's Cash flow card.
- **Privacy.** Hide amounts masks every amount ("₱••••"; percentages and words stay, as in the app). App lock shows
  nothing about the month (the data isn't even read). The Safe to spend widget is home-screen only
  (`widgetCategory="home_screen|not_keyguard"`, so Android 16's lock-screen widget pages don't offer it). The picker
  preview shows "••••", never a sample amount. No permission, no network.
- **Theme.** The chosen Lucento theme's colours (`WidgetColors`), never the wallpaper's: with light/dark on System the
  launcher switches them with the phone, a fixed mode stays fixed, Midnight is always dark. Widgets use the phone's own
  font (Glance can't load Plus Jakarta Sans).
- **Sizes and text size** (works to 200 %, polished to 130 %). Glance can't measure text, so `FigureLayout` and
  `MessageLayout` (`WidgetFit.kt`) plan every row from line boxes measured on a device (`LINE_EM`) and generous
  character widths, and each text is drawn with exactly the lines planned for it: nothing runs past the widget's edge.
  `WidgetLayoutTest` sweeps 6 widths × 7 heights × 7 text sizes (85–200 %) for every state. The full card stays while
  its figure fits beside "+ Add" at 20 sp or more and its essential line fits whole (Budget's reason); the bills line,
  then the per-day line, are shown whole or left out. Otherwise the compact card (label, figure, one status, round Add)
  shrinks the figure first (never below 16 dp on screen), then leaves out the status, then the label. The setup states
  leave out their detail, then the label, then message lines; their action wraps to two lines rather than being cut.
  Quick add shows "Add a transaction" from 344 dp and below 115 % text, then "Add", then one Add (below 180 dp or from
  150 %) with Speak and Scan left to the composer; its targets are 48 dp unless the row is shorter.
- **Freshness.** `WidgetRefresher` redraws both after any change to the tables they read (Room's change signal, so
  nothing is queried until a redraw; a Shared Space partner's synced entries included) and to the preferences they show
  (theme, mode, Hide amounts, App lock, modules, main currency), debounced by 750 ms; also when Lucento starts and when
  the phone's text size or dark mode changes. `WidgetMidnightWorker` redraws just after local midnight while a Safe to
  spend widget is placed. No `updatePeriodMillis` timer. Glance keeps a widget's session open for a while after each
  update, and an update meanwhile recomposes only what reads Glance's own state, so each widget collects its frames from
  `WidgetRefresher.frames`: loaded as the session opens, reloaded on every refresh while it stays open (text size and
  dark mode are part of a frame).
- **Adding them.** Settings › Home-screen widgets asks the home screen to place either one (Android's pin request); a
  home screen that can't is told the long-press way (Widgets › Lucento).

## Code

`feature/widget/`: `QuickAddWidget.kt`, `SafeToSpendWidget.kt` (Glance widgets and receivers), `WidgetModels.kt` (pure:
the model, `QuickAddLayout`), `WidgetFit.kt` (pure: `FigureLayout`, `MessageLayout`, `fitTextSp`), `WidgetLaunch.kt` +
`WidgetIntents.kt` (targets and intents), `WidgetColors.kt`, `WidgetParts.kt` (shared Glance parts), `WidgetRefresher.kt` (+ the midnight worker),
`WidgetsSheet.kt` (Settings). Resources: `res/xml/widget_*_info.xml`, `res/layout/widget_preview_*.xml`,
`res/drawable/ic_widget_*.xml`. Tests: `SafeToSpendWidgetModelTest`, `WidgetLayoutTest`.
Library: Jetpack Glance 1.2.0 (`gradle/libs.versions.toml`).
