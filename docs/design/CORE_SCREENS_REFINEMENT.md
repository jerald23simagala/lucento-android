# Core screens refinement (November 2026)

Visual refinement of Dashboard, Transactions, Budget and Wallets. High-fidelity designs live on the canvas
**Lucento Core Screens** (https://claude.ai/artifact/MocjXVBtQG5uW38yUQm9kE): seven artboards — the four screens by
day, the Wallets card view, the Dashboard at night, and a shared-patterns sheet. This document is the implementation
companion: the rationale, the reusable patterns, and where each change lands in the code. Nothing here changes a
financial definition; every figure maps to an existing calculation in `docs/CALCULATIONS.md`.

## 1. Rationale

| Change | Problem it fixes |
|---|---|
| Card headers become 12sp SemiBold, +0.06em, uppercase, muted ink (`Monthly budget`, `Upcoming`, `Net worth · USD`). | Card titles competed with amounts and section titles. A quiet overline leaves the figure as the loudest thing on the card. |
| Section titles become 18sp Bold (`Budgeted`, `Not budgeted`, `Assets`, `Liabilities`) with a caption and the per-currency subtotal on the same line. | Tone chips as section titles read as filters, not structure. |
| Every amount is one size and weight, whole units and decimals alike; income green, expense red, transfer muted ink, always signed. | Mixed decimal scaling and ink-coloured expenses made the Transactions list slower to scan. |
| One icon-container family: 28dp (card header), 30dp (stat tile), 36dp (row), 40dp (hero figure), 54dp (shortcut); tonal tint + saturated glyph. | Four screens used five tile sizes and two glyph treatments. |
| Dashboard: "Overview" label removed, the month pill moves left and sits 10dp above the balance card; the header-to-content gap drops from 28 to 10dp. | The pinned row spent 60dp on a label that said nothing and a right-aligned control the thumb could not reach. |
| Monthly Budget card: donut (used %) with Budget, Used and Safe to spend on the left; the three categories closest to their limit with compact bars on the right; no divider. | The old card answered "how much" but not "where" without a second tap. |
| Switched-off modules: a dashed, muted shortcut tile with an add badge instead of the "Off: Scheduled · Turn on · Hide" line. | The text line looked like an error and pushed content down. |
| Transactions summary: three stat cards (Income, Expenses, Net inflow / Net outflow) with filled icon containers and a scope line underneath. | "In / Out / Net" with arrows were not self-explanatory and the arrows pointed the wrong way for a ledger. |
| Day groups: 16sp Bold day name, weekday-date caption, day net on the right, then the rows in one card. | 14sp headers on the background disappeared between hairline rows. |
| Budget hero: remaining amount is the largest element; "54% spent" top-right; utilisation bar with the today marker and both percentages in words; pace chip; two sub-cards (Spent, Safe daily spend). | The hero led with the left-over figure but hid the pace and the daily allowance in pills of equal weight. |
| Wallets hero: net worth and its month-on-month change on the left, a six-month trend on the right; Assets and Liabilities as two stat cards; a segmented distribution bar. | Net worth had no history and the asset/liability split needed a sentence to read. |
| Wallets filters: All / Assets / Liabilities segmented control, wallet-type chips, a row/card view toggle. Card view shows two wallet faces per row using the existing type and institution faces. | A long single list mixed six wallet types; the faces that already exist on the detail page never appeared where wallets are chosen. |

Not changed: the Net Wallet Balance card (it already behaves like a physical card and is the strongest brand moment on
Home; the heading simply moves to the caps style), the navigation dock, the composer, and every calculation.

## 2. Reusable patterns

- **CardOverline** — 28dp tonal icon container (radius 8) + caps title + trailing `StatusPill` or text action.
  Replaces `CardHeader` on these four screens; `CardHeader` stays for sheets and forms.
- **StatTile** — icon container (30dp / radius 9), caps label (10–11sp), figure (15–18sp Bold, tabular). Three
  variants: white card (Transactions, 3-up), white card with 40dp tile (Wallets, 2-up), translucent sub-card on a hero
  (Budget: white at 14 % alpha, radius 14).
- **UtilisationBar** — `ConsumptionBar` with `markerFraction` (today) plus an indicator row: elapsed % · spent % ·
  pace chip. One family with the 6dp compact bar (`ShareBar`) and the 84dp donut (`ProgressRing`, 9dp stroke).
- **DistributionBar** — `CompositionBar` with two segments (assets `success`, liabilities `negativeIndicator`), 2dp
  gap, legend with percentages, and the formula as a caption.
- **TrendLine** — new: a 150×60dp polyline + 18 % area fill, end dot, month initials. Sunlight on the blue hero,
  primary on a white card. Spoken description carries first and last values.
- **ViewToggle** — two icon buttons in one outlined pill (row / grid). Selected state = pale-sky fill, on-primary-container glyph.
- **OffModuleTile** — shortcut tile in `surfaceContainerLow` with a 1dp dashed `#C7CFDD` border, muted glyph and a
  20dp primary add badge; tapping opens a two-action sheet (Turn on · Hide).
- **WalletFace (compact)** — 118dp fixed height, radius 16, top row chip/plate + type caps chip, name (1 line,
  ellipsis), balance (never wraps; `MoneyText` shrinks). Liability: dashed chip, hatched trailing 14dp, `−` and "owed".

## 3. Typography and colour recommendations

Typography (all Plus Jakarta Sans, sp):
- Screen title 28 Bold · section title 18 Bold (new `Hierarchy.sectionTitle` weight) · card overline 12 SemiBold
  +0.06em uppercase (new `Hierarchy.cardOverline`) · hero amount 36 Bold (32 on Wallets where the trend shares the row)
  · figure 18 Bold · row amount 15–16 SemiBold · row title 14–15 SemiBold · caption 12 Regular · tiny caps 10–11.
- `MoneyText(fractionScale = 1f)` everywhere on these screens: decimals are never smaller than the integer part.
- Day labels: "Today" / "Yesterday" / weekday name, with the date as a 12sp caption beside it.

Colour (tokens already in `Color.kt`; no new hues):
- Status: positive `#137A53` text on `#E3F5EC`, warning `#9A5806` on `#FFEFD9`, negative `#B83838` on `#FDE7E7`,
  info `#2447C7` on `#EAF1FF`. Indicators (bars, rings, glyphs): `#16865C` / `#B86A08` / `#D64545`.
- Transfers are neutral: `onSurfaceVariant` amount, pale-sky tile with a primary swap glyph.
- Heroes stay `hero` blue (`#2F56E8`) by day and elevated navy at night; the Net Wallet Balance card is theme
  independent. Sunlight is the chart colour on blue; green/red are reserved for financial status.
- Night pairs follow `DarkExtended`: positive `#3DD68C` / `#123B2A`, warning `#FFB547` / `#3D2C10`, negative
  `#FF8A8A` / `#4A1A1F`, info `#7C9AFF` / `#26397A`, track `#26365C`.
- Google sign-in: use the official multi-colour "G" mark on a white button with a hairline, per Google's branding
  guidelines, in `ConnectAccount.kt` ("Continue with Google"). Add the mark as a drawable; never retint it.

## 4. Component-level implementation guidance

Design system (`core/designsystem`):
- `Type.kt`: add `Hierarchy.cardOverline` (12/16 SemiBold, 0.06em) and make `Hierarchy.sectionTitle` Bold.
- `Surfaces.kt`: add `CardOverline(icon, title, trailing)`; keep `CardHeader`.
- `Charts.kt`: add `TrendLine(points: List<Long>, labels, color, description)` drawn with `Canvas`; add
  `StatTile`. `Progress.kt`: allow `ProgressRing(size = 84.dp, strokeWidth = 9.dp)` with centre content.
- `FinanceVisuals.kt`: `CompositionBar` gains a 2dp segment gap; `ConsumptionBar` already supports the marker.
- `Inputs.kt`: add `ViewToggle(mode, onChange)`.

Dashboard (`feature/home/HomeScreen.kt`):
- Pinned row (~line 264): drop the "Overview" text; `MonthPill` left-aligned; row-to-hero gap 10dp.
- `WalletBalanceCard` (~828): heading in `cardOverline` style; otherwise unchanged.
- `MonthlyBudgetCard` (~1060): two halves in a `Row` (left 150dp, right `weight(1f)`), no divider. Left: `ProgressRing`
  with `burn.consumedPercent`; `plan.total`, `plan.spent`, and safe to spend from `CashflowCalculator.safeToSpend`
  (exists in `domain/Cashflow.kt` but has no caller today: compute it in `HomeState.derive` from the wallets, budgets,
  schedules and categories already in scope and add `safeToSpend: Money?` to `HomeUiState`); negative remaining shows
  "Overspending −$x" in negative text in the same slot.
  Right: `state.topCategories` (raise `spendingBreakdown(top = 3)` in `HomeState.kt`) with `ShareBar`, colour by
  `BudgetProgress.status`. Under 360dp width or font scale ≥ 1.3, the halves stack.
- `ShortcutRow` (~1212): replace the "Off: …" row with `OffModuleTile`s inside the row; the tap opens a small
  `LucentoBottomSheet` offering Turn on / Hide (same callbacks `onTurnOn`, `onHideOffers`).
- `UpcomingScheduledCard` / `RecentTransactionsCard`: `CardOverline` with "See all".

Transactions (`feature/transactions`):
- `ShownSummary` (~516): three `LucentoCard`s in a `Row` (gap 8dp); `SummaryFigure` becomes `StatTile`. Labels
  "Income", "Expenses", and `if (net.amountMinor >= 0) "Net inflow" else "Net outflow"`. Scope line under the row.
- `DayHeader` (~483): `titleMedium` Bold 16sp day label + 12sp date caption + day net; group rows in a
  `LucentoCard(contentPadding = 0)` per `DaySection` with hairlines inset 64dp.
- `TransactionRow.kt` (~134–142): expense amounts `extendedColors.negative`, income `positive`, transfer
  `onSurfaceVariant`. Signs are unchanged (`MoneyFormatter.formatSigned`).
- Filter pills: the active range pill takes the pale-sky selected treatment.

Budget (`feature/budget/BudgetScreen.kt`):
- `MonthSwitcher` (~315): compact white pill with the chevrons inside; the currency pill moves to the right of it.
- `BudgetSummaryCard` (~337): top row overlines "<Month> budget" / "<n>% spent" (`plan.percentUsed`); hero figure
  `plan.remaining` (over budget: "Over by $x" in the same slot, warning tone); utilisation `ConsumptionBar` with
  `markerFraction = burn.elapsedPercent / 100`; indicator row with `burn.elapsedPercent`, `plan.percentUsed` and
  the pace chip from `BudgetBurn.pace` (AHEAD → "Ahead of pace", ALIGNED → "On track", BEHIND → "Behind pace");
  two sub-cards: `plan.spent` and `burn.dailyAllowance` with `burn.daysLeft`.
- Section headers (~232, ~258): `Hierarchy.sectionTitle` Bold with the caption and subtotal inline.
- `AllocationRow` (~419): name 15 SemiBold, "spent of amount" caption, "x left" 14 Bold + `StatusPill`, 6dp bar inset 48dp.

Wallets (`feature/wallets`):
- `WalletsViewModel`: add `history = BalanceHistory.lastMonths(wallets, transactions, currency, month, today, count = 6)`
  and `trend = BalanceHistory.trendPercent(history)` (pure, already tested); add `viewMode` and `typeFilter` /
  `sideFilter` state. Persist `viewMode` in `UserPreferencesRepository` (DataStore key `wallets_view_mode`).
- `NetWorthCard` (~197): left column (overline, `AmountStyles.hero` 32sp, change pill, "You owe n%"), right
  `TrendLine` 150×60dp. Hide the trend when fewer than two months exist.
- New `AssetsLiabilitiesRow` (two `StatTile`s) and `DistributionBar` under the hero (skip the bar when either side is zero).
- Filters: the generic `SegmentedToggle` from `Inputs.kt` (as Transactions uses for its type tabs, `compact = true`)
  for All · Assets · Liabilities, plus `ViewToggle`; a horizontally scrolling `ChoicePill` row for types (`WalletType`
  labels). Filtering is client-side on `uiState.wallets`.
- `walletSection` (~249): header in `Hierarchy.sectionTitle`; row view unchanged (`WalletRow`); card view renders the
  section as rows of two `CompactWalletFace`s (`FlowRow(maxItemsInEachRow = 2)` inside the `LazyColumn` item, or
  `LazyVerticalGrid` when the screen is only wallets). Faces reuse `walletCard(type)` and the institution registry.

Accessibility: every tile and chart keeps a `contentDescription` with the figures; status pills carry words; the
day-group net and the distribution percentages are in text; all tap targets ≥ 48dp (the 36dp view-toggle buttons sit
in a 48dp-tall container with 6dp extra touch padding).

## 5. Responsive considerations (Android phones)

- **Widths 320–360dp**: Monthly Budget halves stack (donut row above the watch list); the three stat cards keep a row
  but amounts shrink through `MoneyText`'s autosize before wrapping; Wallet card grid falls back to one column under
  340dp; the Budget indicator row wraps the pace chip to a second line.
- **Widths 360–430dp**: layouts as drawn (390dp artboards).
- **≥ 600dp (large phones in landscape, foldables)**: content column max 560dp centred; Wallets card view uses three
  columns; the Dashboard keeps one column.
- **Font scale 1.3–2.0**: hero amounts cap at 32sp (`largeText()` already exists in HomeScreen); captions never drop
  below 12sp; overlines never below 11sp; stat tiles switch to a vertical stack (icon over label over figure) and the
  three-up row becomes two-up plus one.
- **Night theme**: tokens swap as on the night artboard; the Net Wallet Balance card and wallet faces stay as drawn;
  heroes use elevated navy; shadows off except the add button.
- **Reduced motion**: rings and bars render at their final state; trend lines do not draw in.

## 6. As built (2026-11-03, branch `feature/core-screens-refinement`)

Owner decisions: **D1** Budget's per-day figure is "Safe per day" (`CashflowCalculator.safeToSpend.perDay`, the
documented calculation), not `burn.dailyAllowance`, so Budget always agrees with Home's "Safe to spend". **D2** Bold
section titles app-wide (`Hierarchy.sectionTitle`). **D3** the Google "G" button is in scope as an exception.

Corrections to sections 1–5 (verified against the code before implementing):
- Home "To watch" reads `plan.allocatedLines` (already sorted by % used) with `CategoryBudgetLine.health` colours, not
  `spendingBreakdown` (ranks by amount, no limits) or the legacy `BudgetProgress`. Untouched categories are left out.
- The Wallets change pill is month-on-month via `BalanceHistory.comparableTrendPercent` on the last two months (the
  canvas says "vs Oct"; plain `trendPercent` would reintroduce RD-QA-5).
- Safe to spend exists for the current month only (it derives the month from today); past months show "Left" or
  "Over budget". Bills are not subtracted while Recurring is off.
- Pace words come from one shared `paceVerdict` (Over budget · Ahead of pace · On track · Under budget) on Home and
  Budget; the spec's "Behind pace" for `BEHIND` would read as a warning for spending slower than the month.
- Amount colour is by type (`amountInk`): a "+" balance adjustment is never painted red.
- `ProgressRing` (size, stroke, centre content) and the `CompositionBar` 2dp gap already existed; no change.
- `CardOverline` draws caps but keeps the sentence-case text for TalkBack and tests.
- docs/CALCULATIONS.md said safe to spend subtracts *budgeted* spending; the code (and decision D1) subtracts every
  expense. The docs were corrected, the code unchanged, and a test now pins it.

Canvas details implemented beyond the spec: Budgeted list folds after 5 rows ("Show n more"); every allocation row shows
its status word, "On track" included; Home card titles "Upcoming" / "Recent transactions".

Deviations from the canvas, and why:
- Home: no "Open budget" link under To watch — the whole card already opens Budget; each watch row is a 48dp target
  that opens that category's transactions for the month (kept from E2).
- Budget: no "Categorise" link in the outside-budget note — Transactions has no "uncategorised" filter, so it would land
  on an unfiltered list. A line under the sub-cards says how much Safe per day sets aside for bills due.
- Budget: the pace chip uses the verdict's own tone pair rather than a white pill, so it keeps contrast at night.
- Budget: a past month's second sub-card is "Daily average" (`BudgetBurn.dailyAverage`); with nothing left this month it
  shows "Safe to spend" (zero or negative, signed).
- Net Wallet Balance card: kept as the owner's E3 physical card (spec §1), not the canvas's flat redraw; only its label
  moved to the caps overline.
- Transactions: day-card hairlines inset 58dp, aligned with the existing row text (the canvas's 64dp assumed 16dp row
  padding); at font scale ≥ 1.3 the stat cards scroll with the history instead of staying pinned.
- Stat-tile labels use `OverlineStyle` (11sp) rather than the canvas's 10px, for legibility.
- Off-module "Hide" hides all switched-off tiles (the one existing preference); the sheet says so.
- Google button: Google's colours and system font (Roboto on most phones) at 14sp Medium, as its guidelines ask.
- Wide windows: `screenGutter()` holds the four screens to a 560dp centred column from 600dp.

Responsive behaviour as built and checked on an API 28 emulator (411dp, 360dp at font 1.3, 320dp at font 2.0, day and
night; screenshots in `artifacts/core-screens/`):
- Font ≥ 1.3: Transactions' stat cards go two-up plus one and scroll with the history instead of staying pinned; the
  Budget hero's sub-cards and Wallets' Assets / Liabilities cards use the stacked tile layout; Home's budget card stacks
  (ring beside the figures, To watch below).
- Font ≥ 1.5: every stat card takes its own row; Transactions' type tabs and Wallets' All / Assets / Liabilities become
  a scrolling row of pills (four or three equal segments cannot hold "Transfers" / "Liabilities"); Home's ring grows
  with its text (up to 1.6×) above full-width figures, each label over its amount; the verdict chip drops under the title.
- Narrow screens: Wallets' side switch moves to its own row (view toggle below it, right-aligned) whenever all three
  labels would not fit beside the toggle; the "Net worth = assets − liabilities" formula flows onto its own line.
- Compact wallet faces use one-word type names ("Bank", "Card"); stat labels wrap to a second line rather than cut.
- `ScreenshotTourTest` was repaired while validating (it had been failing since the Part B onboarding): the Part B
  onboarding walk, every module switched on before seeding, a search before the swipe so the row is never under the
  dock, a keyboard-proof composer wait, and a Wallets card-view shot. It passes end to end on the API 28 emulator.

### Owner review round (2026-11-03, same branch)

- Home order: Net Wallet Balance → shortcuts → Monthly budget (or Cash flow) → getting started → Upcoming → Recent.
- Monthly budget card, side by side (card ≥ 286dp, font < 1.3): the left half is 54 % of the width (max 184dp); the
  ring is exactly that wide (12dp stroke, 30sp percentage), so it lines up with the formula under it. The formula reads
  Budget − Used (= Budgeted + Unbudgeted, both always shown) = Remaining − Bills due = **Safe to spend**, the last line
  on a green / red tint, signed; a past month ends at Remaining. Operators are visual only (TalkBack reads
  "label, amount"). Figures come from `HomeStateBuilder.budgetFormula` (tested to add up). "To watch" is a tinted panel
  stretched to the left half's height (`MatchedHeightPair`), its rows (now up to 4, with "$x left" / "$x over")
  spread evenly. Stacked (narrow or font ≥ 1.3): ring 140dp × font scale (≤ 1.4×) centred, formula and panel full width.
- Budget hero: the split line always shows, in the same words: "Spent = $x budgeted + $y unbudgeted".
- Guided setup, budget step: one card of category rows — category tile and name left, a 144dp right-aligned amount field
  right (under the name at font ≥ 1.5), errors under the row — closed by a live "Monthly budget" total
  (`GuidedSetup.draftTotal`, the sum of what parses) so the "budget = sum of categories" rule is visible while typing.
- Onboarding: a 48dp icon tile heads each step title (focus, payment, categories, review, currency and look, backups);
  the theme is three icon tiles (System / Light / Dark; icons step aside at font ≥ 1.3); the summary is one card of
  icon-tile rows. The shared currency list (onboarding, Settings, wallet sheet) groups Popular / All currencies under
  icon headings, and each row shows the symbol on a tile, the name, "code · symbol", and a radio mark; the chosen row is
  tinted and outlined. It is now one scrolling list: the step's title and appearance scroll away above the search, which
  stays pinned once reached (at 360dp / font 1.5 the old fixed header left no room for a single currency row).

### Owner review round 2 (2026-11-03, same branch)

- Monthly budget formula: no operator column (more room for the figures). It reads Budget, Used (Budgeted, Unbudgeted
  indented), then **Remaining** in green, or **Overspent** in red with its minus sign; **Bills due** in red as a
  deduction (−); **Safe to spend** on its green line only while above zero, otherwise left out. "To watch" lists up to
  5 categories and ends on "See all" in its bottom-right corner (the Budget tab).
- Inline empty states share one component, `MascotMessage` (curious Lucento + one line): Home's cards, the Scheduled
  screen's empty tabs and the bell's "Upcoming payments" menu.
- Wallet page: cash, bank, e-wallet, savings and credit cards drop Expense / Income / Purchase / Interest (the dock's +
  records those) and show **Transfer** (or **Payment** for a card) and **Adjust balance**, which left the Edit wallet
  sheet. Loans keep Repay loan, Adjust loan, Interest/fee.
- Wallet card: the yearly rate joins the title, "Car loan (24% / yr)" (the name ellipsizes, never the rate). An
  instalment loan shows Remaining over a segmented bar (one segment per instalment, paid ones green — mint on dark
  faces), "3 of 24 installments paid", and Principal · Paid · Monthly along the bottom. A credit card shows Credit
  limit · Available · Amount due ("by Nov 5" / "Overdue · Nov 5").
- Credit card editor: no One time / Monthly and no reminder date; Credit limit (optional) and Statement day + Due day
  (1–31, side by side, stacked at font ≥ 1.5). The due day becomes a derived payment reminder — Home's Upcoming card and
  the bell list "<card> payment" with the amount due and "not in Safe to spend"; a tap opens the card. Rules and
  formulas: docs/CALCULATIONS.md "Credit cards (v11)" and "Instalment loan progress". Schema v11 adds the three
  columns to `debt_terms`; sync, backup (format 3) and the Supabase migration carry them.

## Owner review round 3 (2026-11-03)

Owner comments, as built:

| Comment | What changed |
|---|---|
| Safe to spend sticky, 0 and red when over | Always the last line of this month's formula. Above zero: green. At or below: **0** in red plus a caption saying why ("Bills due take what’s left" / "You’re over budget this month"). `BudgetFormula.nothingSafe`, `safeToSpendShown` |
| Bills due always zero | Root cause: only scheduled expenses in categories *with a budget amount* counted, so bills in unbudgeted categories (or none) were ignored; loan instalments count only their interest, which is entered at Mark paid. Now every scheduled bill counts whatever its category (`CashflowCalculator.safeToSpend`); transfers still don't |
| Assets % / Liabilities % / Net worth formula in one row | Wallets page legend is one line that shrinks to fit (`FittingText`, now internal). Exception: at 150 %+ system text it may wrap rather than shrink below the user's own text size |
| Upcoming coloured by kind | Income green, expenses red, transfers (loan instalments, card payments) neutral ink — Home's Upcoming, the bell, the Scheduled page and the schedule sheet (`scheduledFlowColor`). Loan instalments show the loan's tile and "Transfer to …" |
| Scheduled page refactor | New page: hero "<Month> · still scheduled" (net, then Income · Expenses · Transfers, overdue count); "Next 3 months" projection (bars per flow, net per month); Upcoming / All schedules; Transactions-style filters (kind, wallet, category) plus a date range (This month default, Next 30 days, Next 3 months, From–To picker); agenda grouped Overdue → months with calendar date badges; card payments listed and open the card. `ScheduleOutlook` |
| Mandatory category for scheduled transactions | Editor: category pills without "None", error "Choose a category."; repository refuses `CategoryRequired` for bills and income |
| Notification bell more alive | Rounded panel: Lucento (concerned / happy / all clear) with a one-line summary, a tinted "To pay by <date>" total, Overdue and This week sections with tiles and flow-coloured amounts, and a "See all scheduled" button |
| Dialogs aligned, less skeletal | Shared `SheetHeader` (tile, title, subtitle, actions) on every sheet — wallet editor, transfer, adjust balance, goal editor and goal, budget category, sync details, milestones, module offer, welcome back, bank picker, composer wallet choice, schedule sheet, profile; sheet content rises into place (skipped with reduced motion); `ConfirmDialog` has a tonal icon and a filled (red when destructive) confirm button; the remaining raw dialogs (category name, restore, onward budget change) match |
| Add Wallet: card = the wallet being built | The editor draws the wallet page's own `WalletCard` from the draft (`WalletDraft.previewWallet`): bank face, rate in the title, credit limit / available / amount due for a card, instalment bar for a loan; type or bank changes swap it in with a short fade. The old `WalletCardPreview` strip is gone |
| Profile picture from Google, or upload | Profile menu: tap the avatar → Choose from photos (system photo picker, no permission), Use my Google photo (only when signed in with Google), Remove photo. Setup's sync step offers "Use your Google photo?" after a Google sign-in. The Google picture is downloaded once, on that tap, from Google's HTTPS image host only (`ProfilePhotos`), kept as a private file, never synced or backed up, and removed with the ledger wipe on sign-out / account switch |

## Owner review round 4 (2026-11-03)

Owner comments, as built:

| Comment | What changed |
|---|---|
| Scheduled: remove the 3-month projection; remove the hero | Both gone (`ScheduleOutlook.projection` and `MonthOutlook` removed). The page is: top bar, Upcoming / All schedules, filters, then the range's summary |
| Scheduled: transfers apart from income, expenses and net | `FlowTotals.net` = income − expenses. Transfers sit on their own card ("Card and loan payments · not in net flow") under the summary. Month headers show net flow |
| Scheduled: card below the filter in Transactions' style | The summary is the Transactions page's own cards — Income / Expenses / Net inflow (outflow) — now a shared `FlowStatTiles` used by both pages, then a caption (items, dates, overdue included). Agenda headers match Transactions' day headers |
| Transaction view: icons for every field, overall style | A card washed in the transaction's tone (tile, title, signed amount, kind and day pills), a Route card for transfers (both wallets' own tiles), and a Details card with one line per field: category tile, wallet tile, date, currency, note, recorded |
| Edit transaction: wallet as a dropdown with the wallet's icon | `WalletDropdown` (like the category field): the wallet's own tile (bank mark or type icon), name and type; the menu lists every wallet the same way. Date and note fields got icons |
| New wallet: fewer paddings, fields in onboarding's style | Onboarding's filled, tinted fields (`LucentoFields`, `Modifier.filledField`), section labels with icons (`IconFieldHeader`, now shared with onboarding), leading icons, and a tighter rhythm (16 / 8 dp) |
| Wallet page: empty space by Transfer / Adjust balance | One row: Transfer (or Payment / Repay loan) on the left, and on the right a wider Adjust balance card that explains reconciling ("Bank says otherwise? Enter the real balance — the gap is recorded as an adjustment, not income or spending"). A loan's "Record interest or a fee" sits under it. History is in month cards of compact rows (as on Transactions) |
| Wallet page: header "View wallet" | Top bar title |
| Insights: remove "From your own records · USD"; follow the reference's card styling | Subtitle gone. Section intros (large title + one line), a "vs. prior N months" change chip on the hero, a chart with round dashed gridlines and labels, rounded bar tops and a bold current month, and the summary on a tinted panel with a "kept money in X of Y months" pill |
| Insights: new hero, mascot, "Ask Lucento" typing "What’s my top 5 spending categories?" | `AskLucentoHero`: mascot, title, and a chat box that holds the question, clears, and types it out again (still with reduced motion). Opens Ask Lucento |
| Ask Lucento: AI about the user's records, preset reports (net worth, cash flow) and PDFs, safeguards | New page (docs/ASK.md). The preset questions and four report tiles are calculated on the phone. Free-form questions go to Gemini through the existing proxy only with consent (summaries only), with figures filled in by the phone and anything else flagged. Reports open as full statements with a month stepper, Save as PDF and Share |
| Composer: chat styling after the reference; wallets with their real icons | Lucento's speech bubble at the top; "Into" with wallet chips showing each wallet's own tile and a star on the default; "Today" divider; pale-blue user bubbles with times; Lucento's reply card holding its rows as tinted panels; a raised input bar (scan, voice, field, send). Shared chat components in `Chat.kt`, also used by Ask Lucento |

## Owner review round 5 (2026-11-04)

| Comment | What changed |
|---|---|
| New wallet: a fast scroll closes the page | Every sheet keeps a content fling's leftover speed (`LucentoBottomSheet`, `KeepFlingInSheet`): reaching the top mid-fling no longer dismisses it; dragging the handle or pulling down from the top still does. Checked on the emulator with fast flings both ways |
| Animate bars and charts | One helper, `animatedFraction`: bars and rings grow in from zero when first shown and follow changes smoothly (still with reduced motion). Applied to budget bars (`ConsumptionBar`), Home's budget bar, `ShareBar` (goals, top categories), `ProgressRing`, and a loan's instalment segments, which fill one after another; the charts and composition bars already grew in |
| Add transactions: no saving without a category | A missing category on an income or expense is now a blocking issue ("Choose a category"); the row menu has no "No category". Transfers have none |
| Dashboard: Ask Lucento beside the month filter? | Advised against; the owner then asked for none, so Home gets no Ask Lucento entry |
| Wallets: gallery view first | Cards are the default view (`WalletsViewMode`); a saved choice is kept |
| Budget: compact rows | Category rows 16dp side padding (was 24), 10dp vertical (was 16), 34dp tiles, tighter bar gap; unbudgeted rows 52dp |
| Goals page: enhance | Hero with an overall progress ring and Still to save / Reached tiles; "In progress" and "Reached" sections; cards with the goal's icon, saved of target, percentage, a thicker filling bar and fact chips (target date, a month's amount, the estimate at your pace, where it's kept); the goal sheet with a ring, Saved / Target / Still to save, fact chips and a history with direction tiles |
| New savings goal | Preset icons (`GoalIcon`, 12, each on a wallet-palette tone) replace the sunrise pictures; the photo option stays; square tiles. Suggestions wrap, each with its icon. "Kept in" lists only wallets money can sit in (cash, bank, e-wallet, savings) plus **Add wallet** (opens the wallet editor; the new wallet is chosen). Onboarding-style fields with icons; tighter spacing. Icons are stored in the existing scene key (`icon_travel`), so nothing in the database, sync or backup changes; older scene keys map to the closest icon |
| (Found on the way) Budget page Safe to spend showed a negative | Now $0.00 with "Nothing left this month", as on Home; the tile's caption is stacked so it always shows |
| Ask Lucento still "couldn't be reached" | Production logs: Google answered 503 for gemini-3.8-flash (no capacity). lucento-website PR #8 falls back to the next Flash model on 503 / 429 / 5xx within each endpoint's time budget; merged and deployed (`50c8379`) |
| Goals: archive a goal once it's reached ("a toggle on top") | A reached goal's card has an **Archive** pill and its sheet a "You reached this goal" note with **Archive goal**. Once anything is archived an **Active / Archived · n** toggle sits at the top of Goals; the archived list shows each goal greyed with "Archived", and its sheet is read-only (no money in or out, no edits) with **Restore goal**; delete stays. Archived goals keep their history and sync, but leave the hero, Home's savings card and the featured goal; they still count for Goal Setter. Uses the existing `archived` column (already synced and backed up), so no schema change. When every goal is archived, Active shows "Every goal reached" with Create a goal |

