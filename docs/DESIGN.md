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
  `Curious`. A gentle 2.8s float plays only when animations are allowed.
- Usage: small and secondary on Home (beside the greeting, 60dp); larger in onboarding (140dp) and empty states (96dp).
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
| `NavRow` | Hub row: icon tile, title, real status line, chevron (More, Plan, Quick Add). |
| `SwitchRow` | Whole-row toggle with description and dependency note (Enabled features). |
| `InfoNote`, `StatusPill` | Calculation explanations; small status labels with text. |
| `LucentoTopBar`, `SectionHeader`, `Figure`, `Hairline`, `EmptyState` | Structure. |
| `IconTile`, `CategoryTile`, `WalletTypeTile`, `IconBadge` | Tinted icon tiles (transfers use a swap icon). |
| Buttons, `SearchField`, `SegmentedToggle`, `ChoicePill`, `LucentoBottomSheet`, `ConfirmDialog` | Inputs and containers. |
| `LucentoMark`, `LucentoMascot` | Brand. |

## Motion
Short and purposeful (120–800ms): fade-through between tabs, pushed screens slide from the trailing edge, the
mark's leaf grows once, rings/donuts/bars draw in once, amounts roll on change, the mascot floats gently. With the
system's "Remove animations" on (`LucentoTheme.reducedMotion`) decorative motion is skipped.
**Lottie is not integrated yet** (see roadmap): every current moment is served by Compose animation, and the
mascots are vector-drawn, so a Lottie dependency would add size without user value today.

## Navigation
Bottom bar: **Home · Transactions · Add · Insights · More** (Add is a filled blue circle). Insights leaves the bar
when the user turns it off. Everything secondary is pushed from More: Wallets → account detail, Plan → Budgets,
Savings goals, Debts & loans, Recurring payments; Settings; Enabled features. Scan receipt and Speak/type are pushed
from Home or Add. One app-wide transaction editor and one transfer sheet open from anywhere. Opening a module that is
switched off shows an explanation with "Turn on" instead of a broken screen.
