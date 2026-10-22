# Lucento Design System

Philosophy: **financial clarity without the complexity.** The identity comes from composition, type and colour
relationships — not effects. No gradients, glassmorphism, glows, decorative blobs or charts that answer no question.

## Identity: pine & brass on ivory
| Role | Light | Dark | Use |
|---|---|---|---|
| Background / surface | `#F6F3EB` ivory | `#0D1211` night | Screens sit directly on it; most lists are not boxed. |
| Ink (onSurface) | `#161A19` | `#E8E5DD` | Body text and amounts (15.8:1 / 15.0:1). |
| Muted ink | `#565D5A` | `#A5ADA9` | Labels, captions (≥ 6:1). |
| Primary — pine | `#1C4A42` | `#8FCBBB` | Primary actions, selection, focus. |
| Statement panel | `#163E37` + ivory `#F6F1E5` | `#163A33` + `#F2EEE4` | The one hero figure per screen (net worth). |
| Accent — brass | `#D9B36C` (decorative), `#85672A` (text) | `#D9B36C` / `#E2C07E` | Statement rule, current-month bar, top category, the mark's point of light. Sparingly. |
| Positive | `#1F6E4B` | `#7FD1A6` | Income — always with a "+" sign. |
| Negative | `#A23B2C` | `#F0A193` | Debt, overspending, destructive actions — always with "−" or words. |

Wallet kinds have restrained tone pairs (cash = brass, bank = pine, e-wallet = slate, savings = olive,
credit = clay) used only on their icon tiles. All tokens live in `core/designsystem/theme/Color.kt`
(`MaterialTheme.colorScheme` + `LucentoTheme.extendedColors`); features never hardcode colours (the theme preview
swatches in Settings are the single, intentional exception). Contrast pairs were checked for WCAG AA in both themes.

**Colour never carries meaning alone:** amounts use `MoneyFormatter.formatSigned` (typographic `−` / `+`), owed
balances say "owed", and over-budget ratios are spelled out in words.

## Typography
- Inter Display for headings and hero figures, Inter for UI text (bundled, OFL).
- Screen titles are modest (`headlineMedium`, 26sp) — money, not chrome, carries the visual weight.
- Section labels use the editorial overline (`OverlineStyle`, 11sp, tracked, uppercase).
- Amounts always use tabular lining figures (`AmountStyles`: display 44, entry 40, hero 34, large 22, medium 16,
  row 15, small 12). `MoneyText` sets minor units smaller (e.g. cents at 62%), shrinks to fit narrow screens instead
  of wrapping, and rolls value changes up/down.

## Space & shape
4dp grid (`Spacing`), 20dp gutter, 28dp between major sections. Radii: 10 (controls/tiles), 14 (fields, buttons),
20 (statement panel), 28 (sheets). Hairlines (`Hairline`, inset to text) separate rows; cards are rare.

## Components (`core/designsystem/component`)
| Component | Purpose |
|---|---|
| `StatementPanel` | Pine hero panel with brass rule; optional click target with label. |
| `MoneyText` / `AmountText` | Prominent animated, shrink-to-fit figure / compact tabular figure; both signed. |
| `LucentoTopBar` | Compact title row with optional back and actions. |
| `SectionHeader` | Overline label + trailing action; heading semantics. |
| `Figure` | Muted label above a value. |
| `IconTile`, `CategoryTile`, `WalletTypeTile`, `IconBadge` | Tinted rounded icon tiles (decorative; text carries meaning). |
| `LucentoPrimaryButton`, `LucentoSecondaryButton`, `LucentoTextButton`, `QuickActionButton` | Buttons with haptics and 48dp+ targets. |
| `SearchField`, `SegmentedToggle`, `ChoicePill` | Filled search, radio-group toggle, selectable pill. |
| `LucentoBottomSheet`, `ConfirmDialog` | Sheet and dialog wrappers in Lucento surfaces. |
| `MonthlyBarChart`, `ShareBar` | Data visualisation that always has a text equivalent. |
| `EmptyState`, `LucentoCard`, `Hairline`, `LucentoMark` | Supporting pieces. |

## Motion
Short and purposeful (120–600ms): fade-through between tabs, Settings slides from the trailing edge, sheet
transitions, amounts roll on change, bars grow once, selection colours cross-fade. When the system's
"Remove animations" setting is on (`LucentoTheme.reducedMotion`), decorative motion is skipped.
Lottie is intentionally not used yet: no current moment needs more than Compose animation, and it would add a
dependency without user value (see roadmap M11).

## Navigation
Bottom navigation holds only real destinations: **Home, Transactions, Wallets**. Budgets and Goals join as tabs when
they ship (five fit comfortably). Settings is a secondary screen behind the gear on Home and hides the bottom bar.
One app-wide transaction editor opens from Home's quick actions, recent rows and the Transactions list.
