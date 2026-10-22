# Lucento Design System

Philosophy: **financial clarity without the complexity.** The identity comes from a friendly palette, clear type and
calm composition — not effects. No gradients, glassmorphism, glows or charts that answer no question.

## Identity: daylight blue on warm cream, midnight navy at night
| Role | Light | Dark | Use |
|---|---|---|---|
| Background / surface | `#FFFDF8` cream | `#0B1226` midnight | Screens sit on it. |
| Card | `#FFFFFF` + hairline | `#16203B` + hairline | `LucentoCard`; lifts content off the canvas. |
| Tinted surface | `#F3F6FF` | `#16203B` | Info notes, search, segmented controls. |
| Ink (onSurface) | `#0F1B3D` deep navy | `#E8EDF8` | Body text and amounts (16.6:1 / 15.9:1). |
| Muted ink | `#5B6478` | `#A4AFC7` | Labels, captions (5.8:1 / 8.5:1). |
| Primary — blue | `#2563EB` | `#8AB0FF` | Primary actions, selection, the Add tab, the mark. |
| Secondary blue | `#60A5FA` family | — | Chart tints (`chartBarMuted`), never small text. |
| Accent — sun | `#FBBF24` (decorative), `#8A5A00` (text) | `#FBBF24` / `#FCD34D` | The leaf, the Sun mascot, onboarding highlights. Sparingly. |
| Highlight | `#FFE6C7` peach | — | Cash tone, warm containers. |
| Success | `#22C55E` (rings/bars), `#15803D` (text) | `#4ADE80` | Income (with "+"), budgets on track, goals. |
| Error / negative | `#F87171` (indicators), `#C53030` (text) | `#FC8A8A` | Over budget, money owed, destructive actions. |

The brief's `#FFFD F8` background is implemented as `#FFFDF8`. Every text/background pair was checked with a
script for WCAG AA (≥ 4.5:1 body, ≥ 3:1 large text and boundaries) in both themes. All tokens live in
`core/designsystem/theme/Color.kt` (`MaterialTheme.colorScheme` + `LucentoTheme.extendedColors`); features never
hardcode colours (the literal theme-preview swatches in Settings are the single, intentional exception).

**Colour never carries meaning alone:** amounts use `MoneyFormatter.formatSigned` (typographic `−` / `+`), owed
balances say "owed", budget states say "Over" / a percentage, and transfers are labelled "From → To".

## Brand mark and mascots
- **Mark** (`LucentoMark`, launcher icon, splash): a rounded blue **L** with a sun-yellow **leaf** — clarity plus
  growth. Drawn on a 100-unit grid; the launcher/splash vectors are generated from the same geometry.
- **Handsome Sun** (light theme) and **Handsome Moon** (dark theme), `LucentoMascot`: an original character pair
  drawn entirely in Compose vectors (no image assets, no downloads). They share one face — level, confident brows,
  bright eyes, cheeks and a restrained smile — so they read as the same character by day and night. Moods: `Happy`,
  `Curious`, `Success`, `Celebrate` — animated with the original Lottie files, still vector otherwise.
- Usage: small and secondary on Home (beside the greeting, 72dp); larger in onboarding (140dp) and empty states.
  Never on every screen; never competing with a financial figure.

## Typography
- Inter Display for headings and hero figures, Inter for UI text (bundled, OFL).
- Screen titles are modest (`headlineMedium`, 26sp) so the money carries the visual weight.
- Overline labels (`OverlineStyle`, 11sp, tracked, uppercase) name every card ("SAFE TO SPEND", "NET FLOW").
- Amounts always use tabular lining figures (`AmountStyles`). `MoneyText` sets minor units smaller, shrinks to fit
  narrow screens instead of wrapping, and rolls value changes.

## Space & shape
4dp grid (`Spacing`), 20dp gutter, 28dp between sections. Radii: 12 (controls), 16 (fields, tiles, buttons),
24 (cards, panels), 32 (sheets). Hairlines separate rows inside cards.

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
| `LucentoMascot(mood)` | Lottie Sun/Moon with markers: `Happy` (idle ×3, then rests), `Curious` (empty states), `Success` (onboarding done), `Celebrate`, `Concerned` (v0.5: budget nearly or fully used — supportive, never a frown). Vector drawing as still/fallback. |
| `EmptyIllustration` / `LucentoEmptyState` | Empty-state illustration (200×166dp) that drops out on narrow/short layouts. |
| `ConfirmationHost(Confirmation)` | Saved check or calm milestone with one line of text, shown **after** a committed write; does not block touches; announced as a live region. |
| `LucentoWorkingIndicator` | Brand dots + label for genuine background work. |

Moments: onboarding mark assembles; empty Transactions/Budgets/Goals/Recurring; receipt scan beam while ML Kit runs
(replaced by results or a clear error); saved transaction/transfer/recurring payment; goal halfway/reached; a month
kept within budget (once). With "Remove animations" on (`LucentoTheme.reducedMotion`) nothing moves.

## Reference mockup (v0.4)
`docs/design-reference/lucento-mock.png` is the visual source of truth. Implemented from it:
- **Category tiles**: saturated hue per spending family with a white glyph (food coral, transport green, shopping
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
  and steps aside for focused workflows: receipt scan, Speak / Type it, category setup, Settings, Enabled features.
  Tabs keep their state (save/restore on switch).
- **+ Add transaction** (floating, every primary screen) opens a sheet with four workflows — **Manual** (the editor),
  **Scan** (receipt OCR), **Speak** (starts listening on arrival), **Type it** (sentence → structured fields). Scanned,
  spoken and typed entries always open in the editor for review. A switched-off module still appears and offers to
  turn itself back on. Transfers are a secondary row in the same sheet.
- **More** (sheet): Savings goals, Recurring payments, Debts & loans, Categories — each with a real status line.
- **Profile** (avatar in every primary screen's top bar): optional name (device-only), appearance, **Settings**,
  Enabled features, version. Lucento has no account; nothing here signs in.
- One app-wide transaction editor and one transfer sheet. Opening a switched-off module explains and offers "Turn on".

## Home dashboard (v0.5)
Top to bottom, nothing duplicated from the dock or the + button:
1. **Budget hero** — solid brand panel (the most prominent element, ≈30 % shorter than v0.4's card): budget left
   (or over) this month, "of total · spent", one supportive sentence, and the **Sun** (Moon at night) — happy while
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
