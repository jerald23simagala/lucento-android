# Lucento Design System

Philosophy: **financial clarity without the complexity.** The identity comes from a bright, restrained palette, clear
type and calm composition — not effects. No gradients in UI chrome, no glassmorphism, no glows, no charts that answer
no question. Feeling: *a little sunlight, a lot of clarity, and confidence about tomorrow.*

## Identity: Sunrise Energy
**Gold and orange are Lucento's light and optimism. Blue is trust, clarity and interaction. Navy is structure.**
Distribution (guidance, not maths): 60–70 % neutral surfaces, 15–20 % navy text and structure, 10–15 % blue
interaction, small deliberate touches of sunlight and tangerine. Most cards stay neutral.

Canonical palette: Sunlight Yellow `#FFC928` · Bright Tangerine `#FF8A24` · Electric Blue `#3563F5` ·
Soft Ivory `#FFFDF5` · Midnight Navy `#17233F` · Pale Sky `#EAF1FF`.

| Role | Light | Dark | Use |
|---|---|---|---|
| Background / surface | `#FFFDF5` ivory | `#10182B` | Screens sit on it. |
| Card | `#FFFFFF` + `#E2E8F3` hairline | `#17233F` + `#26365C` hairline | `LucentoCard`; white on ivory by day. |
| Elevated / selected | `#EAF1FF` pale sky (`surfaceContainer`) | `#202E4D` (`surfaceContainerHigh`) | Info notes, search, segmented controls, selected cards. |
| Brand highlight surface | `#FFF4D1` (`highlight`, navy text) | `#3D3214` (`#FFE9A8` text) | Rare brand moments (milestones). Never whole screens. |
| Ink (onSurface) | `#17233F` midnight navy | `#F8FAFF` | Body text and amounts (15.3:1 / 14.9:1 on cards). |
| Muted ink | `#5E687D` | `#B8C5DC` | Labels, captions (5.5:1, 4.9:1 on pale sky / 8.9:1). Derived from the brief's `#667085` (4.4:1 on pale sky). |
| Primary — Electric Blue | `#3563F5`, white label 4.9:1 | `#7C9AFF`, navy label 6.7:1 | Primary actions, selection, the Add tab, links. |
| On primary container | `#2447C7` on `#EAF1FF` | `#E4EBFF` on `#26397A` | Tonal actions (ActionTile, IconBadge), selected text. |
| Statement panel (hero) | `#2F56E8` Electric Blue, white text, `#DCE5FF` muted | `#202E4D` elevated navy, `#F8FAFF` / `#B8C5DC` text | Budget hero. At night blue stays for interaction only. |
| Accent — sunlight | `#FFC928` (decorative only), `#9A5806` (text) | `#FFC928` (text-safe 11.5:1) | The leaf, the mascot, hero rule, milestones. Sparingly. |
| Warm accent — tangerine | `#FF8A24` (decorative only) | `#FF8A24` | Logo warmth, secondary chart series. Never a status. |

The night-theme primary is `#7C9AFF` rather than the proposed `#5278FF`: white on `#5278FF` is 3.8:1 and
`#5278FF` text on night cards is 4.1:1, both under AA; `#7C9AFF` with a navy label passes everywhere.

### Palette in components
- **Buttons:** primary = Electric Blue fill, white label; secondary = pale-sky fill (night: `#202E4D`) with ink label,
  no outline; text buttons in primary; destructive confirmations use `error`.
- **Chips / pills** (`ChoicePill`): selected = solid primary with `onPrimary`; unselected = white (night: lowest
  surface) with a hairline and ink label.
- **Navigation dock:** selected tab in primary tones on a pale-sky indicator; others muted ink.
- **Icon tiles:** tonal pairs (category, wallet, brand tint for transfers). Never a saturated tile with a white glyph.
- **Progress bars and rings:** `success` green on track, `warningIndicator` approaching, `negativeIndicator` over;
  the status always also appears in words.

### Financial status (separate from the brand palette)
| Status | Light indicator / text / container | Dark text / container | Meaning |
|---|---|---|---|
| Positive | `#16865C` / `#137A53` / `#E3F5EC` | `#3DD68C` / `#123B2A` | Income (with "+"), on track, goals. |
| Warning | `#B86A08` / `#9A5806` / `#FFEFD9` | `#FFB547` / `#3D2C10` | Approaching a limit. |
| Negative | `#D64545` / `#B83838` / `#FDE7E7` | `#FF8A8A` / `#4A1A1F` | Over budget, money owed, errors, destructive. |
| Info | `#3563F5` / `#EAF1FF` | `#7C9AFF` / `#1C2B55` | Guidance and neutral notes. |

The suggested `#16865C`, `#B86A08` and `#D64545` are 4.5, 4.0 and 4.3:1 on ivory, so they are used for rings, bars
and icons; text uses the darker variants above. Warnings inform without shaming: soft containers, words, never a
red screen.

### Charts
Series order: Electric Blue → Tangerine → Sunlight → Emerald `#16865C` → blue-gray `#8A97B0` → sky `#38A3E8` →
violet `#8B7CF6` (night: `#7C9AFF`, `#FF9F4D`, `#FFC928`, `#3DD68C`, `#8A97B0`, `#6CC4F5`, `#A99BFF`). Sunlight
and tangerine are under 3:1 on ivory, so every series keeps its legend label and exact figure beside the chart.
Bars: `chartBar` blue, `chartBarMuted` `#C7D5FC` / `#2C3D66`, `track` `#E8EDF7` / `#26365C`.

Every text/background pair is checked for WCAG AA (≥ 4.5:1 body, ≥ 3:1 large text, boundaries and marks) in both
themes by `ThemeContrastTest` (unit test). All tokens live in `core/designsystem/theme/Color.kt`
(`MaterialTheme.colorScheme` + `LucentoTheme.extendedColors`); features never hardcode colours (the literal
theme-preview swatches in Settings are the single, intentional exception). Lottie files carry the same palette.

**Colour never carries meaning alone:** amounts use `MoneyFormatter.formatSigned` (typographic `−` / `+`), owed
balances say "owed", budget states say "Over" / a percentage, and transfers are labelled "From → To".

## Brand mark and mascots
- **Mark** (`LucentoMark`, launcher icon, splash, onboarding animation): a rounded blue **L** (`#2563EB`, night
  `#8AB0FF`) with a yellow **leaf** (`#FBBF24`) — clarity plus growth. Drawn on a 100-unit grid; the launcher/splash
  vectors are generated from the same geometry. **Approved artwork: never redrawn, retyped or re-tinted** to the UI
  palette (`brandMark` / `brandLeaf` keep the artwork colours).
- **Mascot** (`LucentoMascot`): **Lucento**, a golden orb with a tousled hair crest, cream scarf, brown satchel and a
  crystal torch whose light reflects financial wellness (thriving → steady → getting tight → concerned → over plan).
  The canonical spec, tiers, poses and masters live in `docs/mascot/` (`MASCOT_DESIGN_SPEC.md`). In the app,
  `LucentoMascot(mood, tier)` shows the approved art as 512px WebP sprites (`res/drawable-nodpi/mascot_<tier>_<pose>`:
  the 2048² masters cropped only of transparent margin, x 298–1778 / y 272–1752, so every pose shares one frame) with
  the torch glow drawn behind at 100/80/60/42/28 %. The art is never re-tinted, redrawn or regenerated.
- Tier (`domain/WellnessTier`): Budget hero only — on track at pace → thriving; ahead of pace or no budget → steady;
  ≥ 80 % used → getting tight; ≤ 10 % left or used exactly → concerned; overspent → over plan. Elsewhere the default
  is thriving (the everyday look). Moods → poses: Happy hero_neutral · Greeting greeting_happy · Curious thinking ·
  Success/Celebrate success · Concerned caution (pointing and sleeping ship for later use).
- Motion: arrives with a small hop (6 % of its height, 4 % below 96dp, +4 % scale, soft spring); Greeting adds two
  ±4° rocks (≥ 96dp); then a float of 4.5 % (3 % below 96dp) for a few 3.2 s cycles and rest; glow breathes 6 % with
  it; pose and tier changes cross-fade (300 ms). Amplitudes are ~3× the original guide so the motion reads on a
  phone. Reduced motion: still, instant swaps.
- Usage: small and secondary on Home (beside the greeting, 72dp); larger in onboarding (140dp) and empty states.
  Never on every screen; never competing with a financial figure.

## Illustrations — "A Brighter Little World"
Three landscape backgrounds cropped from one painterly sunrise panorama (supplied by the owner) so the style is
always consistent: `illustration_sunrise_landscape`, `illustration_soft_mountains`, `illustration_japan_fuji`
(`res/drawable-nodpi`, WebP, 46–55 KB). Source and crop boxes: `docs/design/illustrations/`. No text, figures,
logos or UI in the art.
- `IllustratedSurface(illustration, overlay, artworkAlignment, contentPadding, onClick…)` puts the art behind content,
  cropped and clipped to the shape; the content sets the height. Scrims (`IllustrationScrims`) keep every figure on
  solid colour: `trailingWindow` (art only in the last 120dp), `topWindow` (art in a top band that fades into the card).
- Used on: the Home budget hero (soft mountains behind Lucento; the hero stays electric blue / navy under the text),
  goal cards (`GoalScene`: the picture chosen in the goal form — Automatic, Mount Fuji, Sunrise, Mountains or No
  picture — where Automatic means Japan → Fuji, other trips → sunrise, others plain), and the
  onboarding welcome (Lucento in the sunrise landscape). Lists, forms and settings stay clean.
- Decorative: no content descriptions. Night: art dimmed by the scrim / a background wash.

- New-goal form: optional suggestions (Trip to Japan, Holiday, Emergency fund, New laptop) fill in the name and
  picture only; nothing is created until the user saves.

## Typography
- **Plus Jakarta Sans** for the whole UI, bundled as static 400/500/600/700 TTFs (OFL, `docs/licenses`). Manrope is
  for marketing material only. The logo is artwork and is never typeset.
- Scale (sp): display 36 Bold · screen title 28 Bold (`headlineMedium`) · section 20 SemiBold (`titleLarge`) ·
  card heading / buttons 16 SemiBold (`titleMedium`) · body 14–16 · form labels 14 Medium (`labelLarge`) ·
  supporting 12 (`bodySmall`) · financial metadata 12 Medium (`labelMedium`). Hero amounts top out at 40sp.
- Overline labels (`OverlineStyle`, 11sp, tracked, uppercase) name every card ("SAFE TO SPEND", "NET FLOW").
- Amounts always use tabular figures (`AmountStyles`, `tnum`; Plus Jakarta's figures are lining, `−` matches `+`). `MoneyText` sets minor units smaller, shrinks to fit
  narrow screens instead of wrapping, and rolls value changes.

## Space & shape
4dp grid (`Spacing`: xs 4 · sm 8 · md 12 · lg 16 · gutter 20 · xl 24 · xxl 32 · xxxl 48 = space-1 … space-12),
28dp between sections.
Radii (`Radii` / `LucentoShapes`): xs 6 badges · sm 10 inputs, icon tiles · md 14 buttons, dropdowns, chips, dock ·
lg 20 cards, panels · xl 28 sheets, feature cards; `CircleShape` only for circular controls and thin tracks.
Borders: `outlineVariant` `#E8EAF0` (subtle: cards, separators), `borderDefault` `#D8DDE7` (stronger, decorative),
`outline` `#7A869F` for input/control boundaries (the brief's `#D8DDE7` is 1.4:1, below the 3:1 a control needs),
focus = 2dp primary. Elevation (`Elevation` xs 1 · sm 2 · md 4 · lg 8 · xl 16dp, navy-tinted `shadow`): day cards
xs with their hairline, FAB sm→md, toast md, dialogs lg; the night theme uses tonal surfaces instead.
Hairlines separate rows inside cards.

## Components (`core/designsystem/component`)
| Component | Purpose |
|---|---|
| `LucentoCard`, `StatementPanel` | Card with hairline; solid blue statement panel for first-run setup. |
| `MoneyText` / `AmountText` | Prominent animated figure / compact tabular figure; both signed. |
| `ProgressRing`, `DonutChart`, `ShareBar`, `MonthlyBarChart` | Data visuals; each has a spoken description and the exact figures beside it. |
| `ActionTile` | Round quick action with label (Home, account screens). |
| `NavRow` | Menu row: icon tile, title, real status line, chevron (More, Profile, Add sheet). |
| `ConsumptionBar`, `IncomeExpenseChart`, `CompositionBar`, `LegendKey`, `NetFlowIndicator` | v0.5 visuals: budget/goal consumption with a today marker; income vs expenses by month; stacked net-worth composition; net-flow Lottie glyph. |
| `ProfileButton`, `ProfileAvatar` | Top-bar avatar (initials or a person glyph) that opens the profile menu. |
| `SwitchRow` | Whole-row toggle with description and dependency note (Enabled features). |
| `InfoNote`, `StatusPill` | Calculation explanations; small status labels with text. |
| `LucentoTopBar`, `SectionHeader`, `Figure`, `Hairline`, `EmptyState` | Structure. |
| `IconTile`, `CategoryTile`, `WalletTypeTile`, `IconBadge` | Tinted icon tiles (transfers use a swap icon). |
| Buttons, `SearchField`, `SegmentedToggle`, `ChoicePill`, `LucentoBottomSheet`, `ConfirmDialog` | Inputs and containers. |
| `LucentoMark`, `LucentoMascot` | Brand. |
| `LucentoLottieAnimation`, `EmptyIllustration`, `LucentoEmptyState`, `ConfirmationHost`, `LucentoWorkingIndicator` | Motion (see below). |

## Motion and Lottie (v0.4)
Short and purposeful (120–800ms) Compose motion remains for structure: fade-through between tabs, pushed screens slide
from the trailing edge, rings/bars draw once, amounts roll on change. Character and moments use **original Lottie**
files (`res/raw`, register in `docs/design/ANIMATION_ASSETS.md`) through two components:

| Component | Behaviour |
|---|---|
| `LucentoLottieAnimation(animation, marker, iterations, playing, onFinished, fallback)` | Day/night variant by theme; one-shots hold the final frame; loops stop when disposed; reduced motion shows a still (final frame for one-shots, first for loops); parse failure → fallback. |
| `LucentoMascot(mood, tier)` | Mascot with moods: `Happy` (idle ×3, then rests), `Curious` (empty states), `Success` (onboarding done), `Celebrate`, `Concerned` (v0.5: budget nearly or fully used — supportive, never a frown). See `docs/mascot/` for the character. |
| `EmptyIllustration` / `LucentoEmptyState` | Empty-state illustration (200×166dp) that drops out on narrow/short layouts. |
| `ConfirmationHost(Confirmation)` | Saved check or calm milestone with one line of text, shown **after** a committed write; does not block touches; announced as a live region. |
| `LucentoWorkingIndicator` | Brand dots + label for genuine background work. |

Moments: onboarding mark assembles; empty Transactions/Budgets/Goals/Recurring; receipt scan beam while ML Kit runs
(replaced by results or a clear error); saved transaction/transfer/recurring payment; goal halfway/reached; a month
kept within budget (once). With "Remove animations" on (`LucentoTheme.reducedMotion`) nothing moves.

## Reference mockup (v0.4)
`docs/design-reference/lucento-mock.png` is the visual source of truth. Implemented from it:
- **Category tiles**: tonal — pale tint with a saturated glyph by day, deep tint with a light glyph at night (≥ 3:1) — one hue per spending family (food coral, transport green, shopping
  rose, bills amber, housing blue, health teal, education/subscriptions indigo, entertainment violet, travel sky,
  income green) — `LucentoExtendedColors.categoryTones`, used by `CategoryTile` in every list.
- **Home**: greeting with a larger mascot (72dp); quick actions as one filled **Add** circle + tonal Transfer / Scan /
  Speak / **More** (opens the Add hub); Net flow card split — net figure left, Income/Expenses stacked right with a
  hairline divider; change vs last month coloured by direction.
- **Transactions**: All · Income · Expenses · Transfers tabs above search (type pill removed from the filter row);
  empty state with the ledger illustration, Add transaction + outlined Scan receipt / Speak transaction.
- **Plan**: mascot enlarged beside the tagline; **Budgets/Goals/Recurring** empty states with their illustrations.
- **Onboarding**: animated mark above the wordmark; "You're all set" mascot plays its success expression.
- **Receipt scan**: scanning illustration while reading, "Couldn't read this receipt" state with the curious mascot.
Not taken from the mockup on purpose: the user name/avatar (Lucento has no account), "I already have an account"
(no sign-in exists), notification settings (no reminders yet), and emoji in the greeting.
Visual parity has **not** been verified on a device: no emulator or Android build was available in the authoring
environment (see `docs/QUALITY.md`).

## Navigation (v0.5)
- **Dock** (`navigation/NavigationDock.kt`, persistent): **Home · Transactions · Wallets · Insights · Budget**, then a
  **More** button. The selected tab shows its label under a tinted indicator; the others are icon-only (each keeps its
  label as the accessibility name). Insights/Budget leave the dock when switched off. The dock stays on secondary
  screens (goals, recurring, debts, account detail, categories — the tab they were opened from stays highlighted)
  and steps aside for focused workflows: the transaction composer, category setup, Settings, Enabled features.
  Tabs keep their state (save/restore on switch).
- **+ Add transaction** (floating, every primary screen) opens the **transaction composer**: a compact top bar, the
  conversation (the user's message on a pale-sky bubble, Lucento's one-line reply, then the rows on the background
  separated by hairlines), a summary with one primary "Save N transactions" button, and the input with scan and
  microphone actions (each shown only when its module is on). Rows: category tile, description and signed amount (both
  edit in place), then compact selectors for type, category or transfer accounts, date and account. Fields needing
  attention are amber-tinted and explained in words underneath. Lucento's reply never exceeds one line ("Found 4
  transactions. Review anything that looks wrong."). The mascot appears once, 72dp, in the empty intro. Spec:
  docs/CAPTURE.md.
- **More** (sheet): Savings goals, Recurring payments, Debts & loans, Categories — each with a real status line.
- **Profile** (avatar in every primary screen's top bar): optional name (device-only), appearance, **Settings**,
  Enabled features, version. Lucento has no account; nothing here signs in.
- One app-wide transaction editor and one transfer sheet. Opening a switched-off module explains and offers "Turn on".

## Home dashboard (v0.5)
Top to bottom, nothing duplicated from the dock or the + button:
1. **Budget hero** — solid brand panel (the most prominent element, ≈30 % shorter than v0.4's card): budget left
   (or over) this month, "of total · spent", one supportive sentence, and the **mascot** — happy while
   comfortably within budget, *supportively concerned* when ≤ 10 % is left or the budget is used up, curious before a
   budget exists. Taps through to Budget (or category setup when none is chosen).
2. **Net flow** — one compact row: Lottie trend glyph (green up / red down / neutral level line at exactly zero), the
   signed net, income and expenses in small type.
3. **Burn rate** — consumption bar with a "today" marker (share of the month gone), level colours normal / approaching
   / exhausted from the same thresholds as the hero, day N of M, daily average and the daily allowance that keeps the
   month on budget. No percentage when there is no budget.
4. **Savings goals** — overall % saved across goals (capped per goal), progress bar, closest goal and what's left.
5. **Upcoming payments** — next recurring items within 30 days in date order (overdue flagged in red with words),
   wallet and amount; tapping opens that payment's review sheet where it can be marked paid. Never auto-recorded.

## Budget tab (v0.5)
The total is always the **sum of category amounts**. Month switcher, currency pills (never summed across currencies),
a summary card (left of total, status pill, consumption bar with today marker, daily allowance), spending outside the
budget explained (uncategorised / unallocated), **Budgeted** rows (spent of amount, left/over, status, bar) and **Not
budgeted yet** rows ("Set amount"). Tapping a row opens an amount sheet that previews the new total. Manage
categories: add, rename, reorder, deactivate (archive) and restore. Existing users get a one-time "Make your budget
yours" card (choose from presets or keep their categories); a pre-0.5 overall limit is explained once and can be
removed. Users with no categories see "Finish setting up your budget".

## Insights (v0.5)
Two questions only: **income and expenses over time** (3/6/12 months; grouped bars, legend, totals and net, plain
summary; transfers excluded) and **what net worth is made of** (net worth figure; assets and liabilities as two
stacked bars on one shared scale, grouped by account type with counts, shares and amounts; negative net worth shown
honestly). Category donuts and budget performance moved out (Budget tab owns budgets).
