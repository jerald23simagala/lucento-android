# Lucento Shared — Product Specification

Status: **approved and implemented** on branch `feature/lucento-shared` (2026-11-05), awaiting product testing. The
two Supabase migrations are on `lucento-test` (owner, 2026-11-05); production waits for the two-device test and the
owner's approval (docs/handover/NEXT_TASKS.md). How it was built: [SECURITY_MODEL.md](SECURITY_MODEL.md) and
[ARCHITECTURE_PROPOSAL.md](ARCHITECTURE_PROPOSAL.md) §0.
This revision (2026-11-05) supersedes the "Lucento for Couples" discovery: the feature is relationship-agnostic,
the Partner is a full transaction collaborator (not add-only), and wallets gain a Shared/Personal classification.
Companion documents: [ARCHITECTURE_PROPOSAL.md](ARCHITECTURE_PROPOSAL.md), [PERMISSION_MATRIX.md](PERMISSION_MATRIX.md),
[UX_FLOW.md](UX_FLOW.md), [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md), [RISKS_AND_DECISIONS.md](RISKS_AND_DECISIONS.md).

## 1. Vision

**Two independent Lucento accounts sharing one financial environment.**

Lucento Shared lets the owner of a Lucento ledger (the **Owner**) invite one other person (the **Partner**)
into a **Shared Space**. It works the same for married couples, boyfriends/girlfriends, friends, roommates,
siblings, parents and adult children — Lucento never asks for, stores, or displays a relationship category.

Both people keep their own Lucento account, credentials and devices. Both see the same wallets, transactions,
budgets, goals, debts and schedules. Both record **and maintain** the shared transaction history — either can
add, edit or delete any transaction. The Owner alone administers the financial structure (wallets, budgets,
goals, categories, schedules, debts) and the membership.

Deliberately not a family-finance platform: one Owner, one Partner, one Shared Space. The membership
*architecture* is deliberately built multi-Partner-capable (one row per member; the limit of one enforced in a
single server function — ARCHITECTURE_PROPOSAL.md §2.1/§3.1) so a future Partner-limit increase is an
entitlement change, not a redesign — but nothing beyond the two-person product is built, shown, or enforceable
now.

## 2. Terminology

| Concept | User-facing | Internal (code/schema, proposed) |
|---|---|---|
| Feature | **Lucento Shared** | `feature/sharedspace`, `SharedSpaceRepository` |
| Environment | **Shared Space** | environment id = Owner's `auth.users.id` (see architecture) |
| Creator | **Owner** — but screens prefer the person's name/label | role `OWNER` |
| Invited person | **Partner** — but screens prefer the person's name/label | role `PARTNER` |
| Invitation | **Invitation** | `space_invites` row |
| Membership | — | `space_members` row |

Rules:

- The UI uses people's names/labels wherever natural ("Shared with Alex", "Alex joined your Shared Space",
  "Added by Alex"); Owner/Partner appear only where a generic role word is unavoidable.
- Never used anywhere: Manager, Companion, Administrator, Member, Household Owner/Member, Couples/Couples Mode.
- **"Lucento Companion" is reserved** for a possible future web experience and must not name any role, table,
  class or screen in Lucento Shared. (No website work is part of this feature.)
- Nothing from the previous discovery was ever implemented, so the proposed internal identifiers are renamed
  freely now (`couple_*` → `space_*`) with zero migration cost. Once anything ships, internal identifiers are
  frozen and only user-facing wording may evolve.

## 3. Scope

In scope (initial release):

- One Owner + one Partner per Shared Space; independent authentication (existing Google / email sign-in).
- Partner sees everything and has **full transaction collaboration**: add, edit and delete any transaction,
  against any wallet, regardless of who created it.
- Wallet classification: **Shared · Personal (Owner's) · Personal (Partner's)** — organizational labels only
  (§5). Visibility and access are never restricted by classification.
- Owner invites (in-app discovery, no email delivery), sees invitation status, removes the Partner; the
  Partner can leave voluntarily.
- Server-authoritative attribution on transactions (`created_by` / `updated_by`), shown unobtrusively.
- Offline-first preserved for both people; both converge through the existing sync engine.
- Every Partner restriction enforced server-side (Supabase RLS + triggers), never by UI hiding alone.

Out of scope (explicitly, this release):

- Subscriptions, billing, paywalls, entitlement flags of any kind (hooks documented only, §7).
- More than one Partner; configurable roles or permissions; relationship categories.
- Multiple Shared Spaces per user, environment switching, ownership transfer.
- Separate personal vs shared *ledgers* (there is one ledger; classification is a label).
- Wallet-level privacy, access control, or RLS.
- Transaction approval workflows; household dashboards.
- Email delivery of invitations; any website functionality.
- Merging the Partner's own pre-existing cloud ledger into the Shared Space (§6.3).

## 4. Roles

### Owner

The Shared Space **is** the Owner's existing cloud account. The Owner keeps every capability they have today,
plus: invite a Partner, revoke a pending invitation, remove the Partner.

### Partner

A genuine financial collaborator with their own Lucento account:

- Views everything: dashboard, wallets and balances (all classifications), transactions, budgets, goals,
  assets / net worth, debts and loans, schedules, insights and reports.
- Adds, **edits and deletes** transactions — any transaction, any wallet, whoever created it.
- Cannot create/edit/delete wallets, budgets, goals, categories, schedules, debts, or wallet classifications.
- Cannot invite anyone, administer membership, or act on any other account's data.
- Can leave the Shared Space at any time.

Complete matrix with enforcing mechanisms: [PERMISSION_MATRIX.md](PERMISSION_MATRIX.md).

## 5. Wallet classification

People sharing finances still keep separate wallets ("Household checking" vs "Alex's card"). Wallets carry one
of three organizational labels: **Shared**, **Personal (Owner's)**, **Personal (Partner's)**.

- **Personal does not mean private.** Both people see all wallets, all balances, all transactions, and can
  record transactions against any wallet. The label only organizes and explains.
- Audit result: no existing wallet field can carry this (verified: `WalletEntity` has `type` = financial kind,
  `institution_id` = display-only bank id, nothing else semantic). Smallest additive change: one nullable
  column following the `institution_id` precedent — see ARCHITECTURE_PROPOSAL.md §3.4.
- Unclassified (null, incl. every pre-existing wallet) displays as Shared in a Shared Space; solo users never
  see classification UI at all.
- The Owner sets classifications (wallet editing is Owner-only). No RLS, no behavior change anywhere else —
  totals, budgets, net worth are unaffected by the label (future filtered views are a separate product idea).

## 6. Functional requirements

### 6.1 Shared environment

Both users read and write the same cloud rows through the existing push/pull engine — no second sync system.
Existing solo users are untouched: no data migration, no behavior change, pre-feature app versions keep syncing.

### 6.2 Attribution

- `created_by` / `updated_by` on transactions, **stamped by the server** from the authenticated session;
  client-supplied values are never trusted; `created_by` is immutable across edits.
- UI shows "Added by <name>" (and "Edited by <name>" when it differs) unobtrusively in transaction details,
  and only in environments that have (or have had) a Partner; solo users see nothing.
- This is the whole of modification history — no audit-log infrastructure.

### 6.3 Partner's own data

Joining does **not** merge the Partner's own cloud ledger into the Shared Space; it stays safe in their own
account and returns (existing restore flow) if they leave. On the Partner's device, joining replaces the local
ledger with the shared one (existing wipe + restore machinery), with the standing unsynced-changes confirmation.

### 6.4 Invitations

One active (pending or accepted) invitation per Owner; invitations expire (7 days, enforced at acceptance);
addressed to a verified email, discovered in-app (the Owner can pass the word via the system share sheet);
acceptance is server-validated (identity, email match, expiry, status, both sides free of other memberships);
revoked/expired invitations cannot be accepted. The Owner's credentials are never shared; no bearer token exists.

### 6.5 Enforcement and revocation

All Partner limits hold against direct API calls with the Partner's own JWT (RLS + triggers). Removing the
Partner (or leaving) ends server-side access immediately; the Partner's device detects it at the next
connection and restores their own environment via the existing account-boundary/local-reset mechanisms.
Honest disclosures (no remote-wipe promises): the device ledger switches on joining; the Partner's own cloud
data remains safe; unsynced local changes may be lost on leave/removal; previously downloaded shared data may
remain on an offline device after removal; previously exported backups cannot be revoked.

## 7. Future commercial model (documentation only — nothing implemented)

Potential future capacity tiers (commercial possibilities, **not** finalized pricing and not product behavior):

| Plan | `max_partners` |
|---|---|
| Free | 0 (Owner only) |
| Pro | 1 |
| Pro Plus | 2 |
| Family / Premium | 4 |

The whole future entitlement surface is **one server function**: `lucento_max_partners(owner uuid)`, hardcoded
to return 1 this release and called by both `create_space_invite` and `accept_space_invite`
(ARCHITECTURE_PROPOSAL.md §3.3). A future tier changes that function's answer; the membership schema, RLS
policies, attribution and sync already accommodate N members (all Partners identical — no new roles). The
Settings screen is where an entitlement state would eventually render. No billing system, subscription tables,
entitlement flags, plan columns, or Google Play Billing exist or are added now; `max_partners` is documented
capacity, not an active feature.

## 8. Success criteria

- Owner and Partner on two phones each add, edit and delete transactions (including offline, including edits
  of each other's entries) and both ledgers converge with correct attribution and no duplicates — verified by
  extended JVM sync tests (`FakeSyncServer` taught membership and the widened policies) and a two-account
  end-to-end on `lucento-test`.
- A Partner's JWT, driven directly against PostgREST, can do exactly: select environment rows, insert/update
  transactions in the environment (soft deletes included), write their own membership label, leave — and
  nothing else; all access ends the moment membership is removed. Verified with the project's
  rolled-back-transaction RLS method.
- Existing solo accounts and pre-feature builds sync unchanged against the migrated schema.
- Concurrent edit, edit-vs-delete and delete-vs-delete between the two accounts resolve by the existing
  revision/causal rules with the losing side notified (P2-6), demonstrated in tests.
