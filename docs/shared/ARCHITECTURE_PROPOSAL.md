# Lucento Shared — Architecture Proposal

Status: **implemented** on branch `feature/lucento-shared` (2026-11-05). This proposal is kept as the design record.
Where the build differs, §0 lists the differences, and [SECURITY_MODEL.md](SECURITY_MODEL.md) describes the database
exactly as built. The migrations are on `lucento-test` (owner, 2026-11-05), not on production.

## 0. As built — differences from this proposal

| Proposal | Built | Why |
|---|---|---|
| Membership row holds `owner_label`; space implied by the first invitation | Separate `shared_spaces` table (PK `owner_id`, `owner_label`); the Owner creates a space explicitly, then invites | One home for the Owner's label; "is an Owner" becomes a row the role trigger and RPCs can check |
| `lucento_is_member_of(env)` helper in `public` | `lucento_private.partner_spaces()` returning `uuid[]`, in a schema PostgREST doesn't expose | Callers can't invoke it as an RPC; one evaluation per statement (`user_id = any(...)`) |
| Owner policies rewritten with a membership clause | Separate permissive `*_partner` policies beside untouched owner policies | Strictly additive; owner behaviour provably unchanged |
| Direct RLS writes on `space_members` / `space_invites` (owner deletes, partner deletes, label updates) | No write policy or grant on any new table; every write is an RPC (`remove_space_partner`, `leave_shared_space`, `set_my_space_label`, `revoke_space_invite`, `decline_space_invite`, `close_shared_space`) | One code path per rule, under advisory locks |
| One pending invitation per space | One pending invitation per (space, email); **members + pending invitations ≤ `max_partners`** | Same guarantee at limit 1; correct at higher limits |
| Invitation statuses pending / accepted / revoked; expiry derived | Adds `declined` and `expired` (expired written when the Owner next invites), `inviter_label`, `inviter_email`, `responded_at` | The invitee can decline; the Owner sees a history |
| `created_by` / `updated_by` FK to `auth.users` on delete set null | Plain uuids, no FK | Deleting an account must not rewrite rows in someone else's ledger; history keeps the id |
| v12 clients always send `classification` (null included) | Sent only when a wallet in that batch is classified; uniform key set per batch | A solo v12 phone sends exactly what v11 sent; supabase-kt's bulk upsert nulls keys missing from some rows |
| `SyncPreferences.environmentId` + `role` | `SyncPreferences.sharedSpaceOwnerId` (null = solo or Owner); the Owner role comes from the cached snapshot | The binding is all the sync engine needs; the role for the UI is derived |
| Join = existing wipe + restore | `LocalLedgerReset.switchToSharedSpace` (same marker and order, **no income-category reseed**) + first sync; leave or removal = `returnToOwnLedger` → onboarding → own-account restore | A Partner's phone must never hold seeds it couldn't upload |
| Backup format bump for the new fields | Not done: backups don't carry classification or attribution yet | See RISKS_AND_DECISIONS.md §G |
| Onboarding finds the invitation | Onboarding also **adopts an existing membership** (a new phone, a reinstall, an interrupted join) before any restore, and shows why a space left the phone | Covers every way a Partner reaches onboarding |
| Membership discovered on sign-in only | Settings › Lucento Shared and Home also handle "joined on another phone": this phone can switch to the space | A Partner signing in through Settings got their own (parked) ledger otherwise |

Original revision notes (discovery, verified against docs/SYNC.md, `supabase/migrations/`, `data/sync/`,
`data/local/`):

Revision summary vs the original proposal: Partner gains full transaction UPDATE/DELETE (so the insert-only /
ignore-duplicates push strategy is **withdrawn** — §3.6), wallets gain a classification column (§3.4), and all
proposed identifiers are renamed (`couple_*` → `space_*`; roles OWNER/PARTNER). The environment-id model is
re-assessed under the new requirements and **stands** (§2).

Second revision (2026-11-05, owner request): the membership schema is made **structurally multi-Partner**
(one row per member, limit enforced procedurally at 1) so a future Partner-limit increase is an entitlement
change, not a schema redesign — see §2.1 and §3.1. The shipped product remains one Owner + one Partner.

## 1. Audit: what the current architecture already gives us

The single most important finding: **Lucento already has an environment id — it just calls it `user_id`.**

| Fact (verified) | Where | Consequence for Lucento Shared |
|---|---|---|
| Every cloud table (`wallets`, `categories`, `transactions`, `budgets`, `goals`, `goal_contributions`, `recurring`) has `user_id uuid references auth.users` and RLS `auth.uid() = user_id` on all four verbs | `20261024085632_create_sync_tables.sql`, `20261024085649_enable_rls_policies.sql`, later migrations | Sharing = letting a second uid through the policies for an environment keyed by the first uid. No table rename, no data migration. |
| Composite FKs `(user_id, parent_id)` keep rows from referencing another user's parents | same | Still correct when `user_id` means "Shared Space": all rows of one space share it. |
| The client uses **one** `userId` for every push DTO and every pull filter (`eq("user_id", userId)`), threaded through `SyncRepository.sync()` | `SyncModels.kt` (all 7 DTOs), `SyncRemote.kt:74-118`, `SyncRepository.kt:118-190` | A Partner device only needs to substitute the Owner's uid at this one seam. |
| Room has no user/owner column anywhere; a device holds exactly one ledger bound to one account (`synced_user_id`, `AccountMismatch`) | `LucentoDatabase.kt` (v11), `SyncPreferences.kt`, `AccountBoundary.kt` | The Partner device holds the shared ledger as *the* ledger. No multi-tenant Room; the binding gains an environment dimension. |
| Conflict resolution is per-row revisions + causal rule, agreed server and client; idempotent retries (a replayed push is refused as stale and resolved from the identical server copy); keyset pull; tombstones pushed as **soft deletes** (UPDATE setting `deleted_at`), never hard deletes; delete-vs-edit rules incl. `localWinsOverRemoteDelete`; losing side notified (P2-6) | docs/SYNC.md §Change tracking, §Sync cycle, §Conflict resolution | Two *people* are indistinguishable from two *devices* of one account, which the engine fully supports — **including edits and deletes**, since both were already multi-device operations. The engine does not change. |
| The server's stale-edit guard is a BEFORE UPDATE trigger (`lucento_reject_stale_update`) that *skips* the row (no error), and the client re-fetches rows missing from a push's reply | `20261024101635_enforce_last_write_wins.sql`, docs/SYNC.md | A bulk upsert stays batch-safe for any writer **whose UPDATE passes RLS** — the key fact behind §3.6. |
| Join/leave machinery exists: "Restore on a new phone" (pull-first bootstrap), `AccountBoundary` + `LocalLedgerReset` (durable wipe marker, confirm-if-unsynced, resume after kill), `SessionRevocation` | docs/SYNC.md, `AccountBoundary.kt`, `SessionRevocation.kt` | Partner join = restore with a different environment id. Leave/removal = the existing wipe, minus the sign-out. |
| `WalletEntity` fields: name, `type` (financial kind: cash/bank/card/loan/receivable…), currency, opening balance, sort order, archived, timestamps, sync bookkeeping, `institution_id` (nullable, display-only, v10) | `WalletEntity.kt`, `20261102120000_budget_lifecycle_and_institutions.sql` | **No existing field can carry Shared/Personal classification** (`type` is semantic, `institution_id` is a bank id). But `institution_id` is the exact precedent for the smallest additive change: a nullable, display-only, always-sent column. |
| Security-definer RPC precedent: `consume_ai_quota()` (`security definer`, `set search_path = ''`, explicit grants, caller-scoped) | `20261027090000_ai_usage_quota.sql` | Invitation RPCs follow the same pattern. |
| RLS uses `(select auth.uid())` initplan form; migrations are append-only; rollout order is migration-before-build (v9/v10/v11 precedents); no edge functions exist | `20261028190000_…`, docs/SYNC.md | New policies/migrations follow all three conventions; invitations stay in-app (no email infra). |
| Fresh-install seed categories get deterministic ids `seed-<user>-<kind>-<name>` | `SyncRepository.assignMissingCategoryIds` | Must use the environment id on a Partner device; covered by the single-seam substitution. |

**Single-owner assumptions that must change** (complete list): the `userId` seam in `SyncRepository`/`SyncRemote`
(becomes environment id); `SyncPreferences` device→account binding (gains device→environment); RLS owner-only
policies (gain a membership clause: select everywhere, insert/update on transactions); UI's assumption that the
user may mutate everything (role gating on structure management); no authorship on rows (`created_by`/`updated_by`).
Nothing else in the audit (DAOs, repositories, domain calculators, money rules, composer, backup, Ask Lucento)
is owner-aware.

## 2. Ownership model (re-assessed under full transaction collaboration)

**Retained: the environment id is the Owner's `auth.users.id`, kept in the existing `user_id` columns**, with
a membership table mapping the Partner's uid to it.

Re-assessment against the revised requirements found no reason to abandon it — the widened Partner permissions
(transaction update/delete) change *policies*, not *identity*: rows still belong to one environment keyed by
one uid, and attribution (who acted) is carried by `created_by`/`updated_by`, not by row ownership. Wallet
classification is likewise a column on rows that already live in the environment. The alternative
(`household_id` everywhere) still costs a rewrite of 7 live tables, every composite FK, every policy and the
wire format, for no additional capability. Known trade-offs stand: no ownership transfer this release (explicit
product constraint) and the "Owner account inaccessible" story (RISKS_AND_DECISIONS.md).

**The Owner's uid remains the Shared Space id under multiple Partners too** (evaluated 2026-11-05): membership
cardinality never touches row identity — N Partners are N more uids admitted by the same policies against the
same environment id. Nothing about the environment-id choice depends on there being one Partner.

### 2.1 One Partner permanently vs. structurally multi-Partner (trade-off analysis)

An audit of the first proposal found the one-Partner assumption lived in exactly three places: the
`space_members` primary key (`owner_id`), two validations inside the RPCs ("owner already has a Partner"),
and the client's single-partner state model. Everything else was already N-safe: `lucento_is_member_of()` is
an existence check per (env, caller); every policy is row-scoped; attribution stores uids, not roles; and the
sync engine handles any number of devices of one environment, which is what N Partners are to it.

| | **A. One-Partner schema** (`primary key (owner_id)`) | **B. Structurally multi-Partner** (one row per member, limit = 1 by policy) — *chosen* |
|---|---|---|
| Limit guarantee | Declarative (the PK makes a second Partner unrepresentable) | Procedural (`accept_space_invite` checks `lucento_max_partners()`); needs explicit concurrency handling (§3.3) |
| Cost now | none | composite PK; the limit check + advisory lock in the RPC; client models members as a list of size ≤ 1; one labels decision (§3.1) |
| Cost at the future limit increase | New migration changing a PK **plus** reworking every RPC validation, policy test, client state model, and UI written against "the Partner" singular | Change one function's return value (the `max_partners` entitlement hook); UI work to *display* multiple partners remains either way |
| Risk | none new | the limit lives in code paths, not schema — mitigated because membership rows are **only** creatable by the RPC (no insert grants/policy anywhere) and invite acceptance is serialized by the one-pending-invite index + an advisory lock |

Honest assessment: migrating option A's schema later would itself be cheap (a tiny non-financial table) — the
real later-cost is everything *written against* the singular assumption. Choosing B now costs almost nothing,
keeps the strong parts declarative (`unique (partner_id)`, self-check, FK cascades), and concentrates the only
weakened guarantee (the count limit) in one four-line function that is also exactly where the future
entitlement belongs. **Recommendation: B**, with the shipped product still hard-limited to one Partner.

## 3. Supabase schema (one new migration, additive only)

New file `supabase/migrations/<ts>_shared_space.sql` (names now final-proposed):

### 3.1 Membership

```sql
create table public.space_members (
  owner_id      uuid not null references auth.users (id) on delete cascade,
  partner_id    uuid not null unique references auth.users (id) on delete cascade,
  partner_label text not null default '',   -- the member names themselves; email fallback in UI
  owner_label   text not null default '',   -- the owner's label, written by the owner (see note below)
  created_at    timestamptz not null default now(),
  primary key (owner_id, partner_id),
  check (owner_id <> partner_id)
);
```

**One row per member — structurally multi-Partner, procedurally limited to one** (owner request, 2026-11-05;
trade-offs in §2.1):

- `primary key (owner_id, partner_id)` lets a space hold N members without any future schema change.
  `unique (partner_id)` stays: a user is Partner in at most one space — a product rule we keep even with
  multiple Partners per space. No `role` column (all Partners are identical by product definition; a role
  column would have one value and invite configurable-permission creep the product explicitly rejects).
- **The Partner limit is not a schema constraint.** It lives in one place: `lucento_max_partners(owner uuid)
  returns int` — hardcoded `1` this release, and the documented **future entitlement hook** (`max_partners`:
  Free 0 / Pro 1 / Pro Plus 2 / Family 4 are commercial possibilities, not implemented). Both RPCs call it
  (§3.3). This is safe because membership rows are **only** creatable by `accept_space_invite`: no insert
  policy and no insert grant exists for the table, so there is no path around the check.
- RLS, written N-ready from the start (equivalent to the two-person form while one row exists):
  `select` where `owner_id = auth.uid() or (select public.lucento_is_member_of(owner_id))` — the owner sees
  all member rows of their space, and each member sees their fellow members' rows (needed to render
  attribution labels once N > 1); `delete` where `owner_id = auth.uid()` (Owner removes any member) `or
  partner_id = auth.uid()` (a member removes themselves — leaving); `update` of one's own `partner_label`
  only; **no insert policy**.
- Labels: each member's `partner_label` sits on their own row. The owner has no row, so `owner_label` is kept
  per member row and the owner's label update writes all rows of their space in one statement — zero
  duplication while N = 1, trivial while N ≤ 4; if a later tier makes this ugly, a one-row-per-space labels
  home is a small additive migration then. (Recorded as a decision — RISKS_AND_DECISIONS.md §B.)

### 3.2 Invitations

```sql
create table public.space_invites (
  id            uuid primary key default gen_random_uuid(),
  owner_id      uuid not null references auth.users (id) on delete cascade,
  invitee_email text not null,              -- lowercased by the create RPC
  status        text not null default 'pending' check (status in ('pending','accepted','revoked')),
  created_at    timestamptz not null default now(),
  expires_at    timestamptz not null
);
create unique index space_invites_one_pending on public.space_invites (owner_id) where status = 'pending';
```

"Expired" is derived (`pending and expires_at < now()`), enforced at acceptance — no cron. RLS: owner
select/update(→revoked)/delete of own invites; invitee select where pending and
`invitee_email = lower(coalesce(auth.jwt() ->> 'email',''))`. No direct insert for anyone. The invite is
*addressed*, not bearer — no token leaves Supabase; only a session authenticated (and verified — the RPC also
checks `email_confirmed_at`) as the invited email can see or accept it.

The one-pending-invite index is **kept under multi-Partner**: invitations stay one-at-a-time per space
(invite → accept → invite again), which keeps onboarding serial and doubles as part of the capacity-race
defense (§3.3). Multiple *members* never require multiple *concurrent invitations*.

### 3.3 RPCs (`security definer`, `set search_path = ''`, explicit grants/revokes to `authenticated` only —
the `consume_ai_quota` pattern; atomic validation inside)

- `lucento_max_partners(owner uuid) returns int` — **returns `1`, hardcoded.** The single home of the Partner
  limit and the documented future `max_partners` entitlement hook (a future tier changes this function's
  answer; nothing else moves). Not exposed as product behavior — no billing, plans or entitlement storage
  exist or are added.
- `create_space_invite(invitee_email text) returns uuid` — caller becomes owner; rejects: member count at
  `lucento_max_partners(owner)` capacity, an existing pending invite, self-invitation.
- `accept_space_invite(invite_id uuid) returns void` — validates pending + unexpired + email match (verified)
  + member count still below `lucento_max_partners(owner)` + caller not a Partner elsewhere and not owning a
  space that has members; inserts the membership row and marks the invite accepted atomically.
- **Capacity race:** a count-then-insert check could over-admit under concurrency, so `accept_space_invite`
  takes `pg_advisory_xact_lock(hashtext('space:' || owner_id))` before validating. The one-pending-invite
  index already makes two simultaneous acceptances of *different* invites for one space impossible, and the
  `update … where status = 'pending'` on the invite makes a double-accept of the *same* invite a no-op for
  the loser; the advisory lock is the belt to those braces and keeps the guarantee local to the RPC.

### 3.4 Wallet classification

```sql
alter table public.wallets add column classification text
  check (classification in ('SHARED','OWNER','PARTNER'));  -- NOT VALID, live-rows form, per project convention
```

- Nullable; **null = unclassified** → displayed as Shared inside a Shared Space, invisible to solo users. No
  backfill, no behavior change to any calculation (an organizational label, exactly like `institution_id`).
- Room v12 adds `classification TEXT` to `wallets`; `WalletDto` carries it; v12 clients always send it (null
  included, the v10/v11 convention) so clearing syncs; `PulledRowRules` reads an unknown value as null (wallet
  still arrives). Older clients never send it → server keeps it. Backup format bumps (wallets gain a field),
  following the format-3 credit-card precedent.
- Written by the Owner only — enforced automatically, because wallet insert/update stay Owner-only (§3.6).
- **No wallet-level RLS, privacy or access control** — explicitly out of scope; the column has no policy effect.

### 3.5 Transaction attribution

```sql
alter table public.transactions
  add column created_by uuid references auth.users (id) on delete set null,
  add column updated_by uuid references auth.users (id) on delete set null;
```

Stamped server-side (BEFORE INSERT OR UPDATE trigger): insert → `created_by := auth.uid()`; update →
`created_by := old.created_by` (immutable, whatever the client sent), `updated_by := auth.uid()`. Client values
are never trusted; the soft delete (an UPDATE) stamps `updated_by` too, so "deleted by" is derivable later if
wanted. Nullable; existing rows stay null (shown as the Owner / "You"). Old clients neither send nor miss them.
Room v12 adds both columns to `transactions`; `TransactionDto` carries them; pulled values stored, pushed
values ignored by the trigger.

### 3.6 RLS policy changes — re-assessed for full transaction collaboration

Helper (initplan-friendly, used as `(select public.lucento_is_member_of(user_id))`):

```sql
create function public.lucento_is_member_of(env uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.space_members
                 where owner_id = env and partner_id = (select auth.uid()));
$$;
```

This helper — and with it every data-table policy below — is already **multi-Partner-safe**: it asks "is the
caller a member of this environment", never "is the caller *the* Partner". No data-table policy changes when
the Partner limit rises. The same holds for attribution (uids, not roles) and for the sync engine (N Partners
are N more devices of one environment, and the engine supports any device count today).

| Table(s) | select | insert | update | delete (hard) |
|---|---|---|---|---|
| all seven | owner **or partner** | — | — | — |
| `transactions` | ↑ | owner **or partner** (`with check` on `user_id`; trigger stamps actor) | owner **or partner** (`using` + `with check` on `user_id`) | owner only (unchanged) |
| other six | ↑ | owner only | owner only | owner only |

Why this shape:

- **The insert-only / ignore-duplicates strategy is withdrawn.** It existed solely because the Partner had no
  UPDATE policy, which made the update arm of a bulk upsert a batch-killing RLS error. With the Partner now
  editing and deleting transactions, they get a real UPDATE policy on `transactions` — and then a Partner
  device's bulk upsert behaves **exactly like the Owner's second device**: the update arm passes RLS, the
  existing `lucento_reject_stale_update` trigger silently skips stale rows, accepted rows return
  `{id, revision}`, and the rejected-row re-fetch recovers the rest. Lost-reply retries are refused as stale
  and resolved from the identical server copy — the engine's documented idempotency, unchanged.
- **Deletes need no new verb.** The engine never hard-deletes synced rows; tombstones push as soft deletes —
  an UPDATE setting `deleted_at` (`SyncRemote.softDelete` is an update with `eq("user_id", …)`) — which the
  Partner's transactions UPDATE policy admits. Hard DELETE stays owner-only everywhere (and is unused by sync).
  Delete-vs-edit and delete-vs-delete races resolve by the existing tombstone/causal rules
  (`deleted_at` as the delete's time, `localWinsOverRemoteDelete`, rejected-delete recovery).
- **The remaining batch hazard moves, it doesn't vanish:** a Partner push containing a **non-transaction** row
  would still 42501-fail its whole batch (no insert/update policy there). The Partner sync cycle therefore
  pushes only `transactions` and transaction tombstones (hard client-side filter); role gating plus repository
  guards should make other dirty rows impossible, and any that appear anyway stay dirty and are surfaced,
  never silently dropped. This filter is a correctness requirement, tested as such.
- `with check (user_id = auth.uid() or lucento_is_member_of(user_id))` on both insert and update means a
  Partner cannot write a transaction into their own environment (their uid is nobody's `owner_id` while they
  are a Partner — membership constraints in §3.1) or any third account; a forged `created_by`/`updated_by` is
  overwritten by the trigger; a forged `user_id` fails the check or the composite FKs.

### 3.7 Grants, validation, advisors

New tables follow the revoke-then-grant convention (`20261028050634_…`); nothing to `anon`. `PulledRowRules`
and server CHECKs: `created_by`/`updated_by` uuid-or-null; `classification` enum-or-null (unknown → null on
pull). Verification repeats the rolled-back-transaction method as real `authenticated`/`anon` roles — the
checklist in PERMISSION_MATRIX.md §4 — plus security and performance advisors after applying (the membership
subquery on hot-table policies is a PK lookup in a stable security-definer function with the initplan form;
advisor regressions are blockers). Rollout order (project convention): migration reaches a project before any
v12 build syncs against it; pre-feature builds are unaffected (columns unknown to them, policies only widen).
`lucento-test` first; production only on owner approval.

## 4. Android client changes

### 4.1 Environment binding and role (`data/sync`)

- `SyncPreferences` gains `environmentId` and `role` (`OWNER`/`PARTNER`) beside `syncedUserId`. For every
  existing install and solo user, `environmentId == syncedUserId` — behavioral no-op. Cleared by every path
  that clears `syncedUserId`.
- `SyncRepository.sync()` resolves membership once per cycle (one `space_members` select by `partner_id`):
  partner → envId = `owner_id`; previously bound but membership gone → **`MembershipRevoked`** result (sync
  stops before push/pull; UX_FLOW.md §5 takes over); otherwise envId = own uid (today's behavior). Offline:
  stored binding; RLS is the real boundary.
- All `userId` uses inside the cycle (push DTOs, pull filters, `fetchByIds`, seed ids `seed-<envId>-…`,
  `remoteHasData`, probe, cursors) switch to envId — mechanical, at the verified single seam.
- Partner cycles push `transactions` and transaction tombstones **only** (§3.6), with the normal upsert path —
  no ignore-duplicates variant, no special push code beyond the table filter.

### 4.2 Room (v11 → v12)

One migration: `transactions` + `created_by`/`updated_by`; `wallets` + `classification`. Explicit
`MIGRATION_11_12` + migration test + committed schema JSON. DTO fields per §3.4/§3.5. Backup format bump with
read-back compatibility for the previous format (established pattern).

### 4.3 New `data/repository/SharedSpaceRepository` + `feature/sharedspace`

Membership/invite state as a Flow (DataStore-cached so role gating works offline; refreshed each sync and on
screen open); actions: `invite(email)`, `revokeInvite()`, `acceptInvite(id)`, `removePartner(uid)`, `leave()`,
`setOwnLabel(text)` — thin supabase-kt calls (table ops + the RPCs). Wired in `AppContainer` (manual DI). One
Settings destination (SettingsHub row) + the acceptance surfaces (UX_FLOW.md). **Model the state as a list of
members (size ≤ 1 this release), keyed by uid with labels** — attribution display resolves any `created_by`/
`updated_by` uid against it, and a future limit increase changes what the UI *renders*, not the state model.
The UI itself stays single-Partner (UX_FLOW.md is unchanged by the multi-Partner readiness work).

### 4.4 Role gating (defense in depth; the server is the boundary)

Partner devices hide structure-management controls (wallet/budget/goal/category/schedule/debt editors and
classification editing) and keep the full transaction surface (composer, editor, delete). Repositories guard
the gated mutations with a typed refusal so no code path can strand permanently-dirty non-transaction rows.
Recurring auto-posting and reminders run only on the Owner's device this release (product constraint; the
occurrence idempotency keys keep a future relaxation convergent).

### 4.5 Join / leave / removal (existing machinery, no new wipe code)

Join = unsynced-changes confirm (`AccountBoundary` pattern) → `LocalLedgerReset` wipe → bind environment →
pull-first bootstrap (the "Restore on a new phone" path with envId = owner; seeds fold via envId-keyed ids).
Leave = delete membership row → notice → wipe + own-account probe/restore (user stays signed in). Removed =
`MembershipRevoked` detected at next connection → same path. Disclosures per PRODUCT_SPEC.md §6.5; unsynced
shared entries are lost and the copy says so; no remote wipe is promised and offline devices keep their cache
until they connect (RISKS_AND_DECISIONS.md §C.3).

### 4.6 What explicitly does not change

Sync engine (conflict rules, revisions, cursor, tombstone mechanics, triggers, workers, locks), Room as source
of truth, money rules, composer pipeline, solo onboarding, backup semantics (format bump aside), Ask Lucento
(local reads; `ai_usage` stays per-account), device preferences (theme, currency, modules — never synced,
each person styles their own device), and all privacy properties (nothing leaves the device without the
existing sync opt-in; Lucento Shared requires sync by definition and the UI says so).

## 5. Critical scenarios (expected behavior, revised)

| # | Scenario | Behavior |
|---|---|---|
| 1 | Owner invites a new Lucento user | Invite row; partner installs, signs up with that email, invite offered before solo setup. |
| 2 | Owner invites an existing user | Invite prompt on next sign-in/app-open; accepting warns the device switches to the Shared Space (own cloud data untouched). |
| 3 | Partner accepts | `accept_space_invite` (atomic, server-validated) → wipe → pull-first bootstrap → connected. |
| 4 | Partner creates/edits/deletes a transaction offline | Saved to Room, dirty (edits) / tombstoned (deletes) — identical to today's offline behavior. |
| 5 | Partner syncs later | Normal push with envId; server stamps actor; Owner pulls next cycle. |
| 6 | Both edit the same transaction concurrently | Existing causal rule: a causal successor wins outright; genuinely concurrent edits tie-break by edit time; the losing phone shows the overridden-change notice (P2-6). |
| 7 | One edits while the other deletes | Existing delete rules: delete time = `deleted_at`; a delete that lost to a newer concurrent edit is rejected and the row returns on the deleting device; a remote delete is skipped only against a newer unpushed local edit. |
| 8 | Both delete the same transaction | Both tombstones soft-delete the same row; idempotent; no error, no resurrection. |
| 9 | Owner edits a wallet while Partner offline | Partner pulls on reconnect; a Partner transaction against a wallet the Owner deleted follows the existing restore-parent-locally rule. |
| 10 | Owner removes Partner | Membership row deleted → RLS refuses everything immediately; Partner app notices at next connection, wipes, restores their own account; offline cache remains until then (documented). |
| 11 | Partner leaves | Same, self-initiated, with unsynced-changes confirmation first. |
| 12 | Either changes devices | Owner: existing restore. Partner: sign in → membership found → join flow on the new device. |
| 13 | Partner attempts structure changes or unauthorized DB ops | UI absent; repository guard refuses; server: no insert/update/delete on the six structure tables, select scoped to the environment, membership/invites writable only per §3.1–3.3, forged attribution overwritten, forged `user_id` fails `with check`/FKs. |
| 14 | Owner's account inaccessible | Partner retains full transaction collaboration and can leave; administration waits for Supabase account recovery. Documented, no special code. |
| 15 | Deleting the Shared Space | Deferred (RISKS_AND_DECISIONS.md §C.8): removal + existing local sign-out exist; a true server-side erase is its own future privacy feature. |

## 6. Testing strategy (summary — details in IMPLEMENTATION_PLAN.md)

`FakeSyncServer` taught membership, the widened transaction policies and actor stamping → the full two-user
matrix as JVM tests (concurrent edit / edit-vs-delete / delete-vs-delete / lost-reply retry as Partner /
revocation mid-cycle / batch with a stray non-transaction row / seed folding on join / per-currency total
equality after convergence). Capacity enforcement gets its own server-side cases: accept at capacity refused,
concurrent accepts serialize (advisory lock), removal frees a slot for a new invite, and removing one member
never affects another's access (run with `lucento_max_partners` temporarily returning 2 inside a rolled-back
transaction, so the N-ready structure is proven without shipping it). RLS checklist on `lucento-test` via
rolled-back transactions. Room
`MIGRATION_11_12` test; `PulledRowRules` tests; backup round-trip. One manual two-device end-to-end on
`lucento-test` mirroring docs/SYNC.md "First end-to-end test" plus the collaboration scenarios.
