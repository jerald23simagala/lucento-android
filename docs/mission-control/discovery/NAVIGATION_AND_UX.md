# Navigation and UX

## 1. Information architecture

The brief lists nine modules. As navigation they'd become nine peers, and a founder would hop between them all day.
They're regrouped around the three jobs (Observe, Act, Understand), with one home that pulls from all of them.

```
TODAY                         the home: briefing · attention queue · health strip · key numbers
─ ACT ─────────────────────────────────────────────────────────────
Customers                     directory → profile → (investigation)
Support                       inbox → ticket (with customer side panel)
Messages                      announcements · campaigns · deliverability
─ OBSERVE ─────────────────────────────────────────────────────────
Operations                    health · incidents · crashes · deployments · jobs · costs
─ UNDERSTAND ──────────────────────────────────────────────────────
Revenue                       MRR & sales · reconciliation · refunds · complimentary
Product                       activation · adoption · versions · reviews
Intelligence                  briefings · anomalies · ask
─ GOVERNANCE (footer of the sidebar) ──────────────────────────────
Audit log · Team & access · Settings
```

Why this shape:
- **Customers and Support are separate destinations but always linked.** Support is a queue (work arrives); Customers
  is a directory (you go looking). Each opens the other in a side panel instead of navigating away, so ticket context
  is never lost.
- **Investigation isn't a destination.** It's a *mode* of a customer profile, entered from a ticket or a profile, shown
  with a persistent banner. Putting it in the sidebar would invite browsing customers' records.
- **Messages, not Marketing.** Announcements (in-app) come first; campaigns arrive later with consent. The name fits
  both.
- **Governance sits low but visible.** The audit log is a daily habit, not a buried setting.

## 2. Global elements

| Element | Behaviour |
|---|---|
| **Sidebar** (240 px, collapsible to 64 px icons) | Groups as above; counts on Support (unassigned) and Operations (open incidents); environment badge (PRODUCTION in amber, TEST in blue) at the top |
| **Command bar** (⌘K / Ctrl+K) | Jump anywhere; find a customer by email, id, order id or ticket number; run verbs ("grant pro to…", "open LC-1042"); every verb opens the normal confirmed flow |
| **Environment lock** | Production and test are different sessions; the header colour and the badge change; data can't be mixed in one view |
| **Investigation banner** | Full-width amber band while a session is active: customer, mode, reason, time left, End. Survives navigation |
| **Sample/estimate markers** | `EST` (estimated), `SAMPLE` (opted-in population), `DELAYED 2d` (source lag) chips beside numbers |
| **Source drawer** | Clicking any metric opens "How this is computed": definition, source, population, last computed, the query |
| **Toasts as receipts** | "Granted Pro for 90 days · Audit #4,812" with a link to the entry |

## 3. Primary workflows

### 3.1 Morning check (≤ 10 minutes)
1. Open **Today**. Health strip: all `ok`? Briefing: three to five cited sentences on what changed.
2. **Attention queue**, ranked: Sev incidents → tickets breaching SLA → pending approvals → expiring comps (7 days)
   → failed jobs → anomalies. Each row has one primary action.
3. Done, or click into the one thing that matters.

### 3.2 A tester needs Pro (the most frequent action at launch)
1. ⌘K → type the email → Enter opens the **profile**.
2. **Entitlements** card → *Grant complimentary Pro*.
3. Dialog: duration chips (30 d · 90 d · 1 yr · Lifetime · Custom), capability checkboxes (all Pro by default), reason
   code (required), note, optional ticket. A live preview shows the resulting entitlement and the expiry in the
   customer's terms ("Pro until 7 Feb 2027").
4. Confirm (step-up only for > 90 days or Lifetime). The toast links to the audit entry. The profile timeline shows
   "Complimentary Pro granted by Jerald · tester".
5. The phone picks it up at its next entitlement refresh (the app's existing path, no change needed).

### 3.3 A support email arrives: "I bought Lifetime but the app says Free"
1. **Support** inbox: the ticket is auto-categorized *Pro Access* (labelled AI, editable) and linked to the account by
   sender email.
2. The side panel shows the customer: plan **Free**; purchases: one `lucento_pro_lifetime`, link status
   `linked_elsewhere` (bought while signed in to another account 12 days ago).
3. The panel offers *Resolve purchase link*: it shows both accounts (the other masked to the last 4 characters of its
   email), the 30-day rule, and the options (move now with Owner approval, or wait).
4. Reply with the "Purchase on another account" template; resolve. Every step is audited and linked to the ticket.

### 3.4 Sync looks broken for a customer
1. In the ticket, *Investigate*. Without consent: metadata only (T1). Sync health shows `transactions` last server
   write 9 days ago while `wallets` updated today. The client-reported `last_sync_error_code` (once A ships) is
   `PGRST204`.
2. Ask for consent with the template ("Turn on Help › Let support see my records for 24 hours").
3. When the grant lands, the ticket shows "Support access granted until 10 Nov 14:32". *Investigate › Read* now shows
   records with notes masked.
4. A transfer leg is missing. *Assist* mode → **Repair transfer pair** → diff → reason → step-up → confirm. The
   customer's app later shows "Changed by Lucento support" on that row.

### 3.5 Month close (revenue)
1. Day 5+: **Revenue › Reconciliation** shows the earnings report for last month as `imported`.
2. Three columns per product: Lucento entitlements · Play orders · Earnings lines. Discrepancies are listed with a
   reason (refund after month end, FX timing, order not yet linked to an account).
3. The founder marks the month reconciled; it's locked (the audit log records it). Net revenue becomes `REPORTED`
   instead of `EST` for that month.

### 3.6 Incident
1. A health check flips to `down` → an alert email/push to the Owner + an incident draft in the attention queue.
2. **Operations › Incidents › Declare**: severity, affected (AI answers, sync, purchases), public message.
3. Optional: flip a kill switch (e.g. pause AI features → the app shows "AI answers are paused while we fix
   something").
4. Updates are posted; resolution reverses the switch, and linked tickets get a bulk reply.

### 3.7 Campaign (later phase)
Draft → audience (only consented; the count is shown with its definition) → desktop/mobile preview → test send to
self → schedule → approval (if not Owner) → final confirm with typed count → a 10-minute cancellable queue → sending →
results.

## 4. Screen inventory

| Screen | Purpose | Key components |
|---|---|---|
| Today | One look, know where to act | Briefing, attention queue, KPI row with sparklines, health strip, MRR vs Lifetime chart |
| Customers | Find anyone fast | Search, saved filters, dense table, column chooser, bulk select (capped) |
| Customer profile | Everything about one account, metadata first | Identity header, plan and capability chips, entitlements ledger, purchases, sync health table, usage counts, timeline (customer / admin / system), actions menu |
| Investigation | Consented look at records | Banner, scope tabs (Wallets, Transactions, Budgets, Goals, Debts, Recurring, Settings, Sync), masked fields, Assist actions with diff |
| Support inbox | Work the queue | Three panes: list (filters, SLA), conversation (messages, notes, templates), customer side panel |
| Revenue | Money, accountant-grade | MRR vs one-time split, source-labelled figures, monthly table, reconciliation, refunds, comp tracker |
| Messages | Talk to customers responsibly | Announcements list, campaign builder (editor, preview, audience, guardrail checklist), deliverability |
| Operations | Is it working and what does it cost | Service grid with checks, incidents, crashes (both sources), deployments, jobs, costs and quotas |
| Product | Is the product working for people | Funnel, adoption (synced), versions, reviews |
| Intelligence | Explain changes | Briefing archive, anomalies, ask box with cited answers |
| Audit log | Who did what | Filterable log, entry detail with before/after, chain verification |
| Team & access | Who can do what | Admins, roles, sessions, secrets inventory |

## 5. Interaction and visual principles

- **Density with rhythm.** 13 px base type in tables, 32 px rows, an 8 px grid; whitespace separates groups, not
  every row. Tables beat cards for anything you compare.
- **Status is shape and word, never colour alone.** `● ok`, `▲ degraded`, `■ down`, `○ unknown`, each with its label;
  money is signed (`+₱1,190`, `−₱3,490 refund`).
- **Numbers are tabular** (`tabular-nums`), right-aligned, with units and currency codes; PHP is the reporting
  currency, and USD orders show both values with the rate.
- **Destructive actions look different.** A red outline, a confirmation that names the customer, a typed phrase for
  irreversible ones, and an explicit "what stays" list.
- **Every empty state says why** ("No purchases yet. Lucento Pro isn't on sale until the Play listing goes live") and
  every error says what to do.
- **Keyboard first:** `J/K` to move in lists, `E` to resolve a ticket, `G` then `C` for Customers, `?` for the cheat
  sheet.
- **Motion is functional only:** panel slides of 150 ms, no decorative animation; respects reduced motion.
- **Light and dark** themes from one token set; dark is the default for long operating sessions; production amber is
  identical in both.

## 6. Brand

Mission Control borrows Lucento's palette roots (Midnight Navy `#17233F`, Electric Blue `#3563F5`, Sunlight Yellow
`#FFC928`) and Plus Jakarta Sans, but sets them in an instrument-panel register: navy-tinted neutrals, blue as the only
interactive accent, yellow reserved for the *production* environment marker and the investigation banner (both mean
"careful"), and a monospace face for ids, hashes and amounts in logs. No mascot inside the console. Lucento's warmth
belongs to customers; Mission Control's calm belongs to the operator.

## 7. Responsive behaviour

The desktop (≥ 1280 px) is the primary target. At tablet width the sidebar collapses to icons and the support customer
panel becomes a drawer. On phones, Mission Control offers a deliberately reduced set: Today, the attention queue,
incident updates, approvals and ticket replies. No investigation and no bulk actions on a phone.
