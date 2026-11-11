# Lucento Design System

Philosophy: **financial clarity without the complexity.** The identity comes from a bright, restrained palette, clear
type and calm composition — not effects. No gradients in UI chrome (the Aurora theme's hero panel is the one
deliberate exception), no glassmorphism, no glows, no charts that answer no question. Feeling: *a little sunlight, a lot of clarity, and confidence about tomorrow.*

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
| Statement panel (hero) | `#2F56E8` Electric Blue, white text, `#DCE5FF` muted | `#202E4D` elevated navy, `#F8FAFF` / `#B8C5DC` text | Budget hero, Wallets net-worth hero. At night blue stays for interaction only. |
| Wallet card faces (`walletCard`) | Cash sunlight · Bank blue hero · E-wallet tangerine · Savings green · Credit card navy · Loan deep blue · Other asset pale sky; navy or white ink | Same (physical-card faces don't change at night) | Wallet page balance card without an institution. Decoration: two faint ink circles and a gold chip. Institution faces use the registry colours instead (see Wallet cards). |
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
Categorical series (`extendedColors.categorical`) are their own tokens, separate from the UI accents (2026-11-05;
the earlier blue → tangerine → sunlight… order failed colour-blind separation and was never wired to a chart). Each
theme has 7 slots generated in OKLCH and **ordered** so every gate of the dataviz palette check passes: lightness
inside the mode's band (light 0.43–0.77, dark 0.48–0.67), chroma ≥ 0.10, adjacent pairs ≥ 8 OKLab ΔE apart under
protanopia/deuteranopia (Machado 2009) and ≥ 15 for normal vision, ≥ 3:1 on the theme's cards. Sunrise light:
`#426EC2 #AB5803 #007CA5 #1C8742 #8A57AE #86700A #AB4A7D`; night: `#5D87D7 #C47131 #0396C6 #459F5D #A171C3 #A28700
#C26594`. The order is the safety mechanism — `DataSeriesTest` fails a reordered or edited series. Series 8+ fold into
"Other"; every series keeps its legend label and exact figure. Bars: `chartBar` = primary, `chartBarMuted`, `track`.

Every text/background pair is checked for WCAG AA (≥ 4.5:1 body, ≥ 3:1 large text, boundaries and marks) in every
theme and mode by `ThemeContrastTest` (unit test). All tokens live in `core/designsystem/theme/Color.kt` and
`Themes.kt` (`MaterialTheme.colorScheme` + `LucentoTheme.extendedColors`); features never hardcode colours (the theme
picker's previews are drawn from each `ThemeSpec`'s own tokens). Lottie files carry the Sunrise palette in every theme.

### Themes (2026-11-05)
Five curated themes, chosen in the profile menu › Appearance (`feature/profile/ThemePicker`): a row of previews,
each drawn from that theme's own tokens over its landscape, then System / Light / Dark. Picking applies at once — the
palette cross-fades for 250 ms (instant under reduced motion), nothing restarts.

| Theme | Character | Light / dark |
|---|---|---|
| **Sunrise** (default) | The signature palette above, unchanged. | Both |
| **Midnight** | Indigo black `#0A0E1A`, periwinkle `#8FA8FF` action, champagne `#E3C87E` accent. | Dark only — the mode is set aside (kept, not changed) and the picker says so |
| **Grove** | Sage paper `#F5F6EF`, forest green `#2E7D5B`, harvest gold `#D9A441`. | Both |
| **Mono** | Graphite, slate `#46557A` action; colour is left to the money. | Both |
| **Aurora** | Violet `#6C4DE0` with teal `#2BB8A8` and magenta accents; the hero panel carries the system's only gradient (`#4B37B8 → #2F4DB0`, AA at both ends). | Both |

- **What a theme may change** (`BrandColors`): surfaces, ink, primary and containers, outline, accent / accent text /
  warm accent, highlight, the hero panel (+ gradient end), chart bar/track tokens, the categorical series, and its
  landscape. **What it can't** (financial semantics, fixed per light/dark mode): positive / negative / warning and their
  containers and indicators, progress success, error roles, category tiles, wallet tones and wallet card faces.
  Themes are built as `LightExtended/DarkExtended.withBrand(…)`, and `ThemeInvarianceTest` checks it.
- The approved logo, wordmark and mascot art are never re-tinted; launcher icon and splash stay Sunrise. Lottie files
  keep their palette. Institution and wallet-type card faces are theme-invariant (they are the bank's or the type's).
- **Home net-balance card takes the theme** (owner, 2026-11-05; `ThemeSpec.balanceCard`, `BalanceCardFace`). Still a
  physical card — the same face in light and dark mode, same layout and type sizes — with per theme: a deep diagonal
  face, light ink and muted ink (labels, line icons, contactless mark), the two trend bars, and the chip plating.

  | Theme | Face (top left → bottom right) | Muted ink | Bars | Chip |
  |---|---|---|---|---|
  | Sunrise | `#245BD8 → #162D88` | `#DCE5FF` | sky blues | gold |
  | Midnight | `#2B3456 → #0C1020`, ink `#F4F1E8` | champagne `#D8C795` | champagne | gold |
  | Grove | `#2A6E4E → #123423` | `#D3EADC` | harvest gold | gold |
  | Mono | `#454B5A → #1B1E26` | `#CDD2DC` | silver | silver |
  | Aurora | `#5C43CF → #22308F` | `#E0D9FB` | teal | silver |

  The theme's landscape is printed on the face tone-on-tone (`silhouetteFilter`): scene pixels darker than the face's
  threshold darken it, sky / sun / moon / aurora leave it untouched — so the print can only raise the ink's contrast.
  Gain `#A7F3C5` / loss `#FFC2B8` stay the same in every theme, always beside a signed figure. `ThemeContrastTest`
  checks ink and muted ink at both ends and the middle of every face, and the trend text and bars where they sit.
  Sunrise's start moved from `#2768ED` and its muted ink from `#C9D7FF`: the small labels were 3.4:1 in the bright
  corner (AA needs 4.5).
- Persistence: DataStore `theme_id` beside `theme_mode`; unknown values fall back to Sunrise. A device choice (like the
  wallets view): kept across sign-outs, not in backups or sync (the backup settings format is strict; adding it would
  need a backup schema bump). Owner-confirmed 2026-11-05.
- Adding a theme: one `ThemeSpec` (two `ThemeColors` built from `BrandColors`, a `BalanceCardFace`), a landscape, then
  `ThemeContrastTest` and `DataSeriesTest` must pass. No screen changes.

**Colour never carries meaning alone:** amounts use `MoneyFormatter.formatSigned` (typographic `−` / `+`), owed
balances say "owed", budget states say "Over" / a percentage, and transfers are labelled "From → To".

## Brand mark and mascots
- **Logo — Rising Crest** (`LucentoMark` symbol, `LucentoWordmark`, launcher icon, splash, onboarding): three
  leaf-cut bars in `#154DFD`, `#61AEFD`, `#FECA45` and a `#FDA81B` dot; the "lucento" wordmark is outlined Manrope Bold,
  navy `#17233F` on light and white on dark. Source of truth: the `lucento-website` repo, `brand/logo/` (its README
  has usage rules). The vector drawables (`ic_launcher_*`, `ic_splash_mark`, `lucento_symbol`, `lucento_wordmark_*`)
  are lossless conversions of those SVGs. **Approved artwork: never redrawn, retyped or re-tinted** to the UI palette.
  Adaptive icon background `#FFFFFF`. Legacy density PNGs are not shipped: minSdk 26 always uses the adaptive icon.
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
- **Theme landscapes** (2026-11-05): each theme has one panorama (`scenery_<theme>_panorama`, 1200×600 WebP,
  11–24 KB; masters and provenance in `docs/design/illustrations/themes/`). All five share one composition (ridges in
  the lower third, a calm sky), so one framing works for every theme. Shown only where nothing needs reading:
  `HeroHorizon` — a 36dp strip of horizon and ridges along the bottom of every `HeroCard` (44dp on `StatementPanel`),
  below the content and fading up into the panel; `MascotScene` — Lucento standing in the landscape in empty states;
  the onboarding welcome (Sunrise keeps the approved "A Brighter Little World" crop); and the theme picker previews.
  Light-painted scenes are dimmed at night; night scenes (Midnight, Aurora) show as painted.

- New-goal form: optional suggestions (Trip to Japan, Holiday, Emergency fund, New laptop) fill in the name and
  picture only; nothing is created until the user saves.

## Typography
- **Plus Jakarta Sans** for the whole UI, bundled as static 400/500/600/700 TTFs (OFL, `docs/licenses`). Manrope is
  for marketing material only. The logo is artwork and is never typeset.
- Scale (sp): display 36 Bold · screen title 28 Bold (`headlineMedium`) · section 20 SemiBold (`titleLarge`) ·
  card heading / buttons 16 SemiBold (`titleMedium`) · body 14–16 · form labels 14 Medium (`labelLarge`) ·
  supporting 12 (`bodySmall`) · financial metadata 12 Medium (`labelMedium`). Hero amounts top out at 40sp.
- **Hierarchy** (`Hierarchy` in `Type.kt`, used the same way on every screen), sentence case throughout:
  section title 18 SemiBold (`SectionHeader`, optional supporting line) · card title 15 SemiBold with a 12 Medium
  context line such as "October · USD" (`CardHeader`) · primary value (`AmountStyles.hero` / `large`) · secondary
  values (`AmountStyles.medium` / `row`) · description (`bodySmall`, muted) · action (`LucentoTextButton`, trailing
  the title it acts on) · form labels inside sheets 14 SemiBold (`FieldHeader`).
- Overline labels (`OverlineStyle`, 11sp, tracked, uppercase) are only for tiny legend/figure captions, never titles.
- Amounts always use tabular figures (`AmountStyles`, `tnum`; Plus Jakarta's figures are lining, `−` matches `+`). `MoneyText` sets minor units smaller, shrinks to fit
  narrow screens instead of wrapping, and rolls value changes.

## Space & shape
4dp grid (`Spacing`: xs 4 · sm 8 · md 12 · lg 16 · gutter 20 · xl 24 · xxl 32 · xxxl 48 = space-1 … space-12),
28dp between sections.
Radii (`Radii` / `LucentoShapes`): xs 6 badges · sm 10 inputs, icon tiles · md 14 buttons, dropdowns, chips, dock ·
lg 20 cards, panels · xl 28 sheets, feature cards; `CircleShape` only for circular controls and thin tracks.
Borders: `borderDefault` `#D8DDE7` / night `#2D3D63` for card, panel and dock edges (`outlineVariant` `#E8EAF0` read
too faint on ivory), `outlineVariant` for separators and unselected pills,
`outline` `#7A869F` for input/control boundaries (the brief's `#D8DDE7` is 1.4:1, below the 3:1 a control needs),
focus = 2dp primary. Elevation (`Elevation` xs 1 · sm 2 · md 4 · lg 8 · xl 16dp, navy-tinted `shadow`): day cards
xs with their hairline, FAB sm→md, toast md, dialogs lg; the night theme uses tonal surfaces instead.
Hairlines separate rows inside cards.

## Components (`core/designsystem/component`)
| Component | Purpose |
|---|---|
| `LucentoCard`, `StatementPanel` | Card with hairline; solid blue statement panel for first-run setup. |
| `HeroCard`, `HeroPill`, `HeroTones` | A page's statement figure on brand blue (Wallets net worth, Recurring due soon): main figure left on a white pill, two stacked figures right on sunlight / tangerine pills with navy ink. |
| `MoneyText` / `AmountText` | Prominent animated figure / compact tabular figure; both signed. |
| `ProgressRing`, `DonutChart`, `ShareBar`, `MonthlyBarChart` | Data visuals; each has a spoken description and the exact figures beside it. |
| `ActionTile` | Round quick action with label (Home, account screens). |
| `NavRow` | Menu row: icon tile, title, real status line, chevron (Profile, Add sheet). |
| `ConsumptionBar`, `IncomeExpenseChart`, `CompositionBar`, `LegendKey`, `NetFlowIndicator` | v0.5 visuals: budget/goal consumption with a today marker; income vs expenses by month; stacked net-worth composition; net-flow Lottie glyph. |
| `ProfileButton`, `ProfileAvatar` | Top-bar avatar (initials or a person glyph) that opens the profile menu. |
| `SwitchRow` | Whole-row toggle with description and dependency note (Enabled features). |
| `InfoNote`, `StatusPill` | Calculation explanations; small status labels with text. |
| `LucentoTopBar`, `SectionHeader`, `Figure`, `Hairline`, `EmptyState` | Structure. |
| `IconTile`, `CategoryTile`, `WalletTypeTile`, `IconBadge` | Tinted icon tiles (transfers use a swap icon). |
| Buttons, `SearchField`, `SegmentedToggle`, `ChoicePill`, `LucentoBottomSheet`, `ConfirmDialog` | Inputs and containers. |
| `LucentoMark`, `LucentoWordmark`, `LucentoMascot` | Brand: symbol, wordmark, mascot. |
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
| `ConfirmationHost(Confirmation)` | Saved check or calm milestone with one line of text, shown **after** a committed write; does not block touches; announced as a live region. Goal halfway/reached and a kept month's budget use it. |
| `AwardMedal` / `AwardsSheet` / `AwardCeremony` (`feature/awards`) | Awards drawn as medals on neck ribbons in Canvas (struck gold, engraved emblem, ribbon dye per award from `AwardArtwork`; unearned = flat outline with a dashed rim and a progress arc for counted awards). The cabinet shows them two to a shelf; an earned medal replays its ceremony. The ceremony is a modal card: the mascot (Celebrate pose, open palm) presents the medal; complete from the first frame, then the medal pops and swings, a sunburst turns, confetti bursts once, a shine sweeps; confirm haptic; still under reduced motion. |
| `LucentoWorkingIndicator` | Brand dots + label for genuine background work. |

Moments: empty Transactions/Budgets/Goals/Recurring; receipt scan beam while ML Kit runs
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
- **Onboarding**: superseded by "Getting started (Sunrise Path)" below.
- **Receipt scan**: scanning illustration while reading, "Couldn't read this receipt" state with the curious mascot.
Not taken from the mockup on purpose: the user name/avatar (Lucento has no account), "I already have an account"
(no sign-in exists), notification settings (no reminders yet), and emoji in the greeting.
Visual parity has **not** been verified on a device: no emulator or Android build was available in the authoring
environment (see `docs/QUALITY.md`).

## Navigation (v0.5)
- **Dock** (`navigation/NavigationDock.kt`, persistent): **Home · Transactions · (+) · Budget · Wallets** on a floating
  surface (24dp corners, 12dp side inset, hairline border; a soft shadow by day). The **+** is a raised 60dp brand-blue
  circle in the centre, breaking the dock's top edge, with a brand-tinted shadow: the one transaction-entry action.
  Insights, savings goals, scheduled transactions and debts open from Home's **Quick links**; categories from the
  Budget tab. Every tab shows its label (the selected one in primary under a tinted indicator) unless the longest
  label no longer fits a tab (large font, narrow screen), when all are icon-only (each keeps its label as the
  accessibility name). Tab content fades into the background over the last 20dp above the dock instead of being cut
  by a hard line (`LucentoRoot`). Budget leaves the dock when switched off. The dock stays on secondary
  screens (goals, recurring, debts, account detail, categories — the tab they were opened from stays highlighted)
  and steps aside for focused workflows: the transaction composer, category setup, Settings, Enabled features.
  Tabs keep their state (save/restore on switch).
- **+ Add transaction** (the dock's centre button) opens the **transaction composer**: a compact top bar, the
  conversation (the user's message on a pale-sky bubble, Lucento's one-line reply, then the rows on the background
  separated by hairlines), a summary with one primary "Save N transactions" button, and the input with scan and
  microphone actions (each shown only when its module is on). Rows: category tile, description and signed amount (both
  edit in place), then compact selectors for type, category or transfer accounts, date and account. Fields needing
  attention are amber-tinted and explained in words underneath. Lucento's reply never exceeds one line ("Found 4
  transactions. Review anything that looks wrong."). The mascot appears once, 72dp, in the empty intro. Spec:
  docs/CAPTURE.md.
- **Profile** (avatar in every primary screen's top bar): optional name (device-only), appearance, **Settings**,
  Enabled features, version. Lucento has no account; nothing here signs in.
- One app-wide transaction editor and one transfer sheet. Opening a switched-off module explains and offers "Turn on".

## Home dashboard (redesign, v10)
Header "Dashboard" with the sync status chip (On device · Synced · Sync needed · Offline · Sync issue; opens Settings ›
Cloud sync) and the profile button. A pinned row stays visible while scrolling: "Overview" and the white **month pill**
(calendar icon, "November 2026", ▾; this month back to the first month with data). Top to bottom:
1. **First-run cards** (new users only): "Record your first transaction" spotlight (also rings the dock's + until the
   first entry; the pulse stops under reduced motion) and **Getting started** (x of 3 done). Both disappear once used.
2. **Net Wallet Balance card**: an ID-1-proportioned blue card (theme-independent, like a physical card) with a
   guilloché texture, the Lucento mark, a decorative gold chip and a contactless glyph; the signed balance (info
   tooltip explains assets − owed), "vs last month" mini-bars with a % only when comparable (docs/CALCULATIONS.md),
   then **Wallets** (assets) and **Debt & Loans** (signed, −) figures. Empty: mascot on the left with a short prompt.
3. **Monthly Budget**: spent (hero) "of <total> budget", % used, a 7dp bar, a verdict chip in words (Over budget ·
   Under budget · Ahead of pace · On track), and a subtle italic "Top 2 spending categories" note above two tiles that
   open Transactions filtered to that category and month.
4. **Shortcuts**: Goals · Insights · Awards · Debts · Scheduled (only modules that are on; switched-off ones offer
   "Turn on" or "Hide").
5. **Upcoming scheduled transactions** (≤ 3, two-line rows, never auto-recorded) and **Recent transactions** (≤ 5 in
   the shown month), each with "See all".
Amounts on cards use the same size for whole units and decimals. Awards (milestones, docs/CALCULATIONS.md) are
presented once, after the write that earned them, in the award ceremony on Home; several earned together share one
ceremony ("Also earned"), and none is presented over the open cabinet.

## Getting started (Sunrise Path, 2026-11-08)
Onboarding and the guided setup share one structure (owner-approved mockups:
`artifacts/onboarding-redesign/proposal-2026-11-08/`). Shared parts live in `core/designsystem/component/SetupKit.kt`.
- **Path rail** (`PathRail`): back, the three stages as thin segments, the current stage named and counted
  ("Personalize · 2 of 5", `SetupProgress.caption`; Set up counts the onboarding summary plus the guide pages this
  person's modules switch on, `GuidedSetup.screenOf`), and Skip on optional steps. No icon tile above step titles.
- **Titles**: `SetupTitle` — `Hierarchy.stepTitle` 28/34 Bold, `Hierarchy.lead` 15/22 muted; milestones 30/36.
- **Action dock** (`ActionDock`): every step ends in the same place, with a soft fade above; steps that collect
  choices show a running count beside the button (`DockCount`). Primary "go on" buttons carry a trailing arrow.
- **One selected state**: pale sky + blue edge + check for chips (`SelectChip`), rows (`OptionRow`), tiles
  (`OptionTile`) and radio cards (`ChoiceCard`); solid blue is only the primary action. Category groups and modules
  carry their existing tones (`categoryTone`, wallet-type tones) on spot icons and tiles — never as text colour.
- **Mascot moments** (always inside a scene): welcome (in the landscape), summary (on a sunrise banner), empty
  accounts (`MascotMood.Pointing` beside a card stack), optional goal (a small note), the composer example (reply
  avatar) and the finish line (theme panorama). Nowhere else in getting started.
- **Screens**: currency first on "Currency and look" (with its lock note) and appearance as three previews; the
  summary is a 2×2 of tiles that jump back to their step, a switched-on strip, the backup row and "Up next"; accounts
  start empty with Cash / Bank / E-wallet quick-add rows (editor preset to the type), then a starting-balance card
  (main currency only, never converted); the budget leads with its total, a "x of 2 set" pill and a split bar whose
  shares are named in words (`GuidedSetup.sharePercents`); goal ideas prefill only the goal's name.

## Wallet cards (redesign Part A)
- **Type face** (no institution): the `walletCard` colours below, a gold chip, the balance (signed for debts: −1,250.00
  with "Amount owed"), opening balance and movement count along the bottom.
- **Institution face** (a bank or provider picked in the wallet editor's searchable picker): the registry's
  contrast-checked gradient and template, its logo on a white plate (or monogram) top-left, the type chip top-right,
  a decorative chip, the wallet name and the signed balance. Logos come only from `app/src/main/assets/banks/` and are
  never baked into card artwork (docs/design/INSTITUTION_ASSETS.md). Not a payment card: no number, expiry or network
  mark. Debts read without colour: "Amount owed", the minus sign, a dashed type chip and a hatched trailing edge.
  TalkBack reads institution, type, name and the same signed amount.

## Budget tab (v0.5)
The total is always the **sum of category amounts**. Month switcher, currency pills (never summed across currencies),
a summary card (left of total, status pill, consumption bar with today marker, daily allowance), spending outside the
budget explained (uncategorised / unallocated), **Budgeted** rows (spent of amount, left/over, status, bar) and **Not
budgeted yet** rows ("Set amount"). Tapping a row opens an amount sheet that previews the new total. Manage
categories: add, rename, reorder, deactivate (archive) and restore. Existing users get a one-time "Make your budget
yours" card (choose from presets or keep their categories); a pre-0.5 overall limit is explained once and can be
removed. Users with no categories see "Finish setting up your budget".

## Insights (v0.5)
*Tester round 3, second pass (2026-11-11):* Ask Lucento stays on top; the built-in **reports** sit right under it as
one list (wallet statement first), each opening the shared report sheet (headline panel, group headings, section
cards with share bars and tinted totals, how it was calculated; PDF and Share). Ask Lucento keeps questions and the
reports an answer produces, but no longer lists reports. Income and expenses opens with a quiet **summary card**
(Money in / Money out side by side with bars on one scale, a "You kept" or "More went out" strip, the change against
the period before), deliberately unlike the brand heroes.
*Tester round 3 (2026-11-11):* the currency and period filters sit at the top and every section follows them; income
and expenses opened with a hero in the Budget page's form (replaced in the second pass, above); then **where your money went** (expense donut, top five + Other, total in
the middle, each slice's share and amount) and **net worth over time** (month-end line from `WalletsOverview.trend`).
What net worth is made of moved out: Wallets already shows assets and liabilities by kind. The paragraph below is the
earlier v0.5 description.

Two questions only: **income and expenses over time** (3/6/12 months; grouped bars, legend, totals and net, plain
summary; transfers excluded) and **what net worth is made of** (net worth figure; assets and liabilities as two
stacked bars on one shared scale, grouped by account type with counts, shares and amounts; negative net worth shown
honestly). Category donuts and budget performance moved out (Budget tab owns budgets).
