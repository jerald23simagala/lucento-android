# Lucento Shared — Security Model (as built)

Status, 2026-11-05: implemented on branch `feature/lucento-shared` as two additive migrations:

- `supabase/migrations/20261105120000_lucento_shared.sql`
- `supabase/migrations/20261105120100_lucento_shared_privileges.sql`

Both pass the 69-check suite in `tools/sql-verify` on real Postgres 17 (PGlite). They pass under lucento-test's default
privileges and under Supabase's stock defaults. **The owner applied both to `lucento-test` on 2026-11-05** (§8 has the
live check). Production doesn't have them yet (`docs/handover/NEXT_TASKS.md` OT-9).

Where this document and ARCHITECTURE_PROPOSAL.md disagree, this one describes the code.

## 1. Identity

- **Shared Space id = the Owner's `auth.users.id`.** That is the `user_id` every synced row already carries, so no
  financial row is rewritten. A solo account is a space of one, with no membership rows.
- A Partner is admitted by one row in `space_members`. The Partner's own environment (rows with
  `user_id = partner`) is untouched while they share, and comes back when they leave.

## 2. Tables (new)

| Table | Purpose | Key constraints |
|---|---|---|
| `shared_spaces` | One row per Owner who created a space; holds `owner_label`. | PK `owner_id` → `auth.users` (cascade). Label ≤ 60. |
| `space_members` | One row per Partner. | PK `(owner_id, partner_id)`; **`unique (partner_id)`**: a person is a Partner in at most one space; `check (owner_id <> partner_id)`; FK to `shared_spaces` (cascade). |
| `space_invites` | Addressed invitations: `pending` / `accepted` / `declined` / `revoked` / `expired`, valid 7 days. | Partial unique index: one pending invitation per (owner, email). |

A trigger on both `shared_spaces` and `space_members` refuses any row that would make an Owner a Partner elsewhere, or
a Partner an Owner. It applies whatever writes the row, as defence in depth beside the RPC checks.

**No INSERT, UPDATE or DELETE policy or grant exists on these tables.** Signed-in users may only `SELECT`, scoped by
RLS:

- `shared_spaces` and `space_members`: your own space, or a space you're a Partner in.
- `space_invites`: invitations you sent, or pending invitations addressed to your *verified* email.

Every write goes through the RPCs in §4.

## 3. Financial tables

**Partner policies are added beside the existing owner policies; the owner policies are untouched.** Permissive
policies are OR-ed, so an owner sees and writes exactly what they did before.

| Table | Partner SELECT | Partner INSERT | Partner UPDATE | Hard DELETE |
|---|---|---|---|---|
| `transactions` | ✔ | ✔ | ✔ (including the soft delete: an UPDATE setting `deleted_at`) | owner only (sync never hard-deletes) |
| `wallets`, `categories`, `budgets`, `goals`, `goal_contributions`, `recurring` | ✔ | ✘ | ✘ | owner only |

- Every Partner predicate is `user_id = any ((select lucento_private.partner_spaces())::uuid[])`.
  `partner_spaces()` is a `security definer` helper in a schema PostgREST doesn't expose. It returns only the
  caller's own memberships, and is evaluated once per statement (initplan form).
- `with check` on Partner INSERT and UPDATE pins `user_id` to the space, so a Partner can't write into a third
  account or into their own parked environment. The existing `lucento_reject_stale_update` trigger already refuses
  any change of `user_id`, so a row can't be moved out of the space.
- **A Partner's upsert into a structure table fails as a whole statement** (Postgres raises 42501 for the conflict
  arm). The app therefore pushes only transactions and transaction tombstones from a Partner's phone (§6).

### Attribution

`transactions.created_by` and `transactions.updated_by` are plain uuids (no FK, so history stays truthful after an
account is deleted). Trigger `trg_transactions_stamp_actor` sets them from the session on every write:

- insert: `created_by = auth.uid()`, `updated_by = null`.
- update: `created_by` keeps its old value; `updated_by = auth.uid()`.

Client values are always overwritten, so attribution can't be forged. The trigger runs after the last-write-wins
trigger (alphabetical order of BEFORE triggers), so an update the conflict rule skips is never stamped. Rows written
before the migration keep null and read as the Owner's.

### Wallet classification

`wallets.classification` is `SHARED`, `OWNER`, `PARTNER` or null (check `wallets_classification_known`). It is an
organizational label only: **no policy reads it**. Wallets are owner-only to write, so only the Owner can set it.
Builds that don't know the column never send it, and an upsert that omits a column leaves it as it is.

## 4. RPCs

All are `security definer` with `set search_path = ''`. Each takes the caller from `auth.uid()` (never from an
argument) and runs everything in one transaction. Refusals are raised as `lucento_space:<code>`, and the app maps
every code to one sentence (`sharedSpaceMessage`).

| RPC | Who | What it enforces |
|---|---|---|
| `get_my_shared_space()` | anyone signed in | Returns role (`OWNER`/`PARTNER`/null), the space, members, the Owner's invitations, and pending unexpired invitations to the caller's verified email. |
| `create_shared_space(label)` | not a Partner | Creates or renames your space (idempotent). |
| `set_my_space_label(label)` | Owner or Partner | Your own label only. |
| `create_space_invite(email)` | Owner | Valid email, not yourself, not already a member or invited. **members + pending invitations < `max_partners`**, so invitations can never outnumber free places. Expired invitations are marked expired first. |
| `revoke_space_invite(id)` | Owner | Your own pending invitation. |
| `accept_space_invite(id, label)` | invitee | Verified email equal to the invitation's; pending; unexpired; not your own space; not a Partner already. If you own a space, it must be empty with nothing pending, and is then closed. Capacity is checked again. The membership row and `accepted` are written in one transaction. |
| `decline_space_invite(id)` | invitee | Your pending invitation, matched by verified email. |
| `remove_space_partner(id)` | Owner | Deletes the membership row: access ends with that statement. |
| `leave_shared_space()` | Partner | Same, for yourself. Returns the Owner id. |
| `close_shared_space()` | Owner | Only when no Partner remains; pending invitations go with it. Your ledger is untouched. |

**Concurrency.** Every membership change takes `pg_advisory_xact_lock` on each person involved, keyed by
`lucento_private.space_lock(uuid)` and always in the same order (`least`, then `greatest`). Acceptance re-reads the
invitation `FOR UPDATE` under the locks. Concurrent acceptances therefore serialize and can't exceed the limit. That
covers the same invitation twice, two invitations to one space, and crossed invitations between two Owners.

**The Partner limit** lives only in `lucento_private.max_partners(owner)`, which returns `1`. This is the future
`max_partners` entitlement hook; no billing, plan or entitlement storage exists. The suite proves the multi-Partner
structure with a limit of 2 inside a rolled-back transaction (checks N2–N4).

## 5. Privileges (`…_privileges.sql`)

- The three tables: everything revoked from `anon` and `authenticated`, then `SELECT` granted to `authenticated`.
  This removes stock-default TRUNCATE, which RLS doesn't cover.
- `lucento_private`: no access for `public`/`anon`. `authenticated` keeps `USAGE` and `EXECUTE` on the two helpers
  the policies call. `max_partners`, `space_lock` and the role trigger are executable by their owner only.
- The actor-stamp trigger function: executable by nobody (triggers don't check EXECUTE).
- The RPCs: revoked from `public` and `anon`, granted to `authenticated`.

## 6. The app's side (defence in depth; the database is the boundary)

- **Binding.** A Partner's phone stores the Owner id (`SyncPreferences.sharedSpaceOwnerId`). Every sync seam (push
  rows, pull filters, fetch-by-ids, probes) uses `env = owner ?: me`.
- **Membership check every sync.** Before pushing, the cycle reads `space_members` for the caller. If the phone is
  bound but the membership is gone, the result is `MembershipRevoked`. After the sync lock is released, the shared
  ledger is wiped (marker-protected, like sign-out) and the person returns to onboarding and their own account.
- **Partner push.** Only `transactions` and transaction tombstones are uploaded. Any dirty structure row (none should
  exist; repositories refuse those writes) is reverted from the server copy rather than left dirty forever.
- **Repository guards.** `StructureAccess` makes wallet, category, budget, goal and schedule writes return
  `NotPermitted` on a Partner's phone, and the UI hides those controls.
- **Wire compatibility.** A solo v12 phone sends exactly what v11 sent: no attribution keys, and no classification
  key unless a wallet in that batch is classified. The key set is uniform within a batch, because supabase-kt's bulk
  upsert nulls keys missing from some rows.

## 7. What is not protected (documented limitations)

- A removed Partner's phone keeps its downloaded copy until it next connects. Lucento then removes it, but never
  promises remote wiping. Exported backups and screenshots can't be revoked.
- Changes a Partner made offline after being removed can't reach the space and are lost. The notice says so when it
  happens.
- The Owner can read the Partner's email (shown on their screen). The Partner can read the Owner's email and label.
  Nobody else can read either.

## 8. Live on `lucento-test` (2026-11-05)

The owner applied the receivables migration, then these two, in the SQL editor. The SQL editor doesn't record
migrations, so `list_migrations` doesn't show them. A read-only catalog check confirmed the result instead:

- **Functions.** All 16 have a body identical to the repo's (md5 of `prosrc` against the file), no overloads, and
  `search_path=""`. The ten RPCs and the two policy helpers are executable by `authenticated`. `anon` can execute
  none of them. `max_partners`, `space_lock`, the role trigger and the actor-stamp function are executable by their
  owner only.
- **New tables.** RLS is on, with one SELECT policy each. `authenticated` holds SELECT only, and `anon` holds nothing.
  `lucento_private` grants USAGE to `authenticated` only.
- **Financial tables.** There are nine `*_partner` policies, all permissive and `to authenticated`, with the
  predicates in §3. The columns, constraints (`wallets_type_known` now accepts `RECEIVABLE`), indexes and triggers are
  all present. BEFORE triggers on `transactions` run in the order `trg_transactions_lww` → `trg_transactions_stamp` →
  `trg_transactions_stamp_actor`.
- **Server.** Postgres 17.11, the same major version the suite ran on.

Advisors after applying:

| Advisor | Finding | Verdict |
|---|---|---|
| Security | `authenticated_security_definer_function_executable` on the ten RPCs | Intended. They are the only write path, and each takes the caller from `auth.uid()` |
| Security | The same lint on `consume_ai_quota` and `delete_usage_events` (the latter also callable by `anon`); leaked-password protection off | Pre-existing and documented (N-08, F-21) |
| Performance | `multiple_permissive_policies` ×9, one for each Partner policy | Accepted: the cost of leaving the owner policies untouched (KNOWN_ISSUES LS-K9) |
| Performance | `unused_index` (INFO), including the two new `space_invites` indexes | Expected: the new tables are empty |

The behavioural suite hasn't been run against the project, because it creates users and rows. The two-device test
(NEXT_TASKS OT-8) exercises the rules end to end.
