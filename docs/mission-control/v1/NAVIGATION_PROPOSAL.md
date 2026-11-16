# Navigation Proposal — V1

## Sidebar (8 items, no submenus)

```
LUCENTO · Mission Control          [PRODUCTION]
  Overview
  Users                (profile → Overview · Entitlements · Records · Activity tabs)
  Support              (badge: open tickets)
  Revenue
  Infrastructure       (badge: services not OK)
  Emails
  Audit Logs
  ──────────
  Settings
```

- Investigation isn't a destination; it's the **Records** tab of a user's profile. That keeps "browse customers'
  money" out of the navigation, and every record view starts from a person and a reason.
- The profile is reached from Users, from a ticket's customer panel, and from search.
- The top bar holds global search (Ctrl/⌘ K): email, user id, ticket number, order id.

## Page patterns

| Page | Layout |
|---|---|
| Overview | Date filter → user numbers band → revenue band → two charts side by side → distribution + operational summary |
| Users | Search and filters → dense table → click opens the profile (full page) |
| Profile | Identity header with plan chip and Actions menu → tabs |
| Support | Summary strip → two panes: ticket list (search, status filter) and ticket (conversation, composer, status) with a customer side panel; stacks on narrow screens |
| Revenue | Four labelled groups (one-time · recurring · complimentary · refunds) → monthly chart → purchases and refunds tables |
| Infrastructure | One section per service, each with status, last check, the few numbers that matter, and a link to the service's console |
| Emails | Quota meters → events table with filters → actions (test email, send transactional) |
| Audit Logs | Search and filters → table → detail panel |
| Settings | Short sections, one per concern |

## Key journeys

1. **Comp a tester:** search the email → profile → Grant complimentary Pro → 90 days, reason "Tester" → confirm.
   A toast links to the audit entry; the phone shows Pro at its next refresh.
2. **"I bought Lifetime but it says Free":** Support → ticket → customer panel shows "purchase linked to another
   account" → Resolve purchase link → evidence + step-up → reply with a saved reply → Resolved.
3. **"My transactions are missing"** (sent from the app): the ticket shows the app version and a stale
   `transactions` sync → View records (reason = the ticket, authenticator code) → read → reply by email.
4. **Morning check:** Overview (7 days) → the Needs attention strip at the top (only what asks for action, each a link;
   "All clear" otherwise) → Support, Infrastructure or Emails.

## Visual system

- Lucento's palette roots set in an operational register: navy-tinted neutrals, Electric Blue as the one interactive
  accent, Sunlight Yellow only for "careful" (the production badge, the open-records banner).
- IBM Plex Sans for the interface and IBM Plex Mono for ids and order numbers, with tabular figures everywhere (owner,
  2026-11-15, after the UI pass: clear figures; Atkinson Hyperlegible's slashed zero read as a struck-out amount). Plus
  Jakarta Sans stays the Android app's face.
- Tables over cards. Status by shape and word, not colour alone. Money always signed and with its currency.
- Light and dark from one token set. Desktop first; at phone width the sidebar becomes a scrolling top bar, panes
  stack, and destructive actions remain available but with the same confirmations.
