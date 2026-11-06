# Lucento Shared — Permission Matrix

Roles: **Owner** (creates the Shared Space) and **Partner** (joins it). The UI prefers people's names; these
role words are the internal model. Legend for **Enforced by** (first mechanism is the real boundary):

- **RLS** — Supabase Row Level Security (server; holds against direct API calls with the user's JWT).
- **TRG** — database trigger (server; actor stamping, stale-edit skip).
- **RPC** — validation inside a `security definer` function (server).
- **REPO** — repository-level role guard on the device (defense in depth).
- **UI** — control hidden (experience only, never the boundary).

## 1. Financial data

| Capability | Owner | Partner | Enforced by |
|---|:---:|:---:|---|
| View dashboard / Home | ✔ | ✔ | RLS (select: owner or partner) |
| View all wallets, balances & classifications | ✔ | ✔ | RLS — classification is a label, never an access rule |
| View transactions, budgets, goals, assets/net worth, debts & loans, schedules, insights | ✔ | ✔ | RLS |
| Add transaction — against **any** wallet | ✔ | ✔ | RLS (insert on `transactions`: owner or partner, `with check` on `user_id`), TRG (`created_by` = actor, client value ignored) |
| **Edit any transaction** (whoever created it) | ✔ | ✔ | RLS (update on `transactions`: owner or partner), TRG (`created_by` immutable, `updated_by` = actor; stale edits skipped) |
| **Delete any transaction** (soft delete via sync) | ✔ | ✔ | RLS (the soft delete is an UPDATE, admitted as above); hard DELETE verb stays owner-only and is unused by sync |
| Transfers between wallets (two linked legs) | ✔ | ✔ | Same as add/edit/delete; atomicity is local + existing sync invariants |
| Create / edit / archive / delete wallets | ✔ | ✘ | RLS (wallets insert/update/delete: owner only), REPO, UI |
| Set wallet classification (Shared / Personal) | ✔ | ✘ | RLS (a wallets column ⇒ owner-only automatically), REPO, UI |
| Manage assets & liabilities (wallet types, debt terms, credit-card billing) | ✔ | ✘ | RLS (wallet columns), REPO, UI |
| Create / edit / delete budgets | ✔ | ✘ | RLS, REPO, UI |
| Create / edit / archive goals; add goal contributions | ✔ | ✘ | RLS, REPO, UI |
| Manage debts & loans | ✔ | ✘ | RLS, REPO, UI |
| Manage categories | ✔ | ✘ | RLS, REPO, UI |
| Create / edit / delete scheduled transactions; mark occurrences paid | ✔ | ✘ | RLS (`recurring` owner-only; posting also writes schedule bookkeeping), REPO, UI |
| Import / batch capture (creates transactions only) | ✔ | ✔ | RLS (same as Add transaction; idempotency keys unchanged) |

## 2. Shared Space administration

| Capability | Owner | Partner | Enforced by |
|---|:---:|:---:|---|
| Create a Shared Space | ✔ | ✘ | RPC `create_shared_space` (refused for a Partner), role trigger |
| Invite a Partner | ✔ | ✘ | RPC `create_space_invite` (caller owns a space; members + pending invitations below `max_partners` — hardcoded 1, the future entitlement hook) |
| Withdraw a pending invitation | ✔ | ✘ | RPC `revoke_space_invite` (own pending invitation) |
| See invitation status | ✔ | own invite only | RLS select (owner sees own invites; invitee sees pending invites addressed to their verified email) |
| Accept or decline an invitation | — | ✔ (when invited) | RPC `accept_space_invite` / `decline_space_invite` (verified email match, unexpired, pending, both sides free) |
| Remove the Partner | ✔ | ✘ | RPC `remove_space_partner` |
| Leave the Shared Space | ✘ | ✔ | RPC `leave_shared_space` |
| Close an empty Shared Space | ✔ | ✘ | RPC `close_shared_space` (refused while a Partner remains) |
| Set own display label | ✔ | ✔ | RPC `set_my_space_label` (own label only) |
| Export a backup file | ✔ | ✔ | Device only — an exported file can't be revoked later |
| Restore a backup file | ✔ | ✘ | UI (hidden on a Partner's phone: it would replace the shared ledger) |
| Device preferences (theme, currency, modules…) | own device | own device | DataStore preferences are never synced — each person styles their own device |
| Delete the Shared Space (server-side erase) | deferred | ✘ | Not in this release — RISKS_AND_DECISIONS.md §C.8 |
| Transfer ownership | ✘ | ✘ | Explicitly out of scope this release |

## 3. Accounts & identity

| Capability | Owner | Partner | Enforced by |
|---|:---:|:---:|---|
| Own credentials, sessions, password change | ✔ | ✔ | Supabase Auth (unchanged, fully independent) |
| See the other person's credentials / session | ✘ | ✘ | Never shared by design |
| Attribution visible to both ("Added/Edited by <name>") | ✔ | ✔ | TRG-stamped `created_by`/`updated_by`, RLS-readable |
| Forge attribution or change `created_by` via edit | ✘ | ✘ | TRG overrides/preserves regardless of payload |
| Write into their own or a third account's environment while a Partner | ✘ | ✘ | `with check` on `user_id` + membership constraints + composite FKs |
| Read any third account's data | ✘ | ✘ | RLS unchanged for non-members |
| Ask Lucento (AI) quota | own | own | `ai_usage` keyed by the caller's own uid (unchanged) |

## 4. Server-side verification checklist

**As built:** no table on the Lucento Shared side has a direct write policy; every administration row in §2 is an
RPC (SECURITY_MODEL.md §4). The checklist below is implemented as `tools/sql-verify/shared_space_suite.sql`. It
passes 69/69 on Postgres 17 (PGlite) under both lucento-test's and Supabase's stock default privileges. The
migrations have been on `lucento-test` since 2026-11-05, and a live catalog check found the deployed functions identical
to the files the suite verified (SECURITY_MODEL.md §8). The suite creates users and rows, so it isn't run against the
project itself; the two-device test covers the rules end to end.

Original checklist (to run against `lucento-test` as real `authenticated` users inside rolled-back transactions, plus
as `anon`):

1. Partner selects environment rows in all seven tables → sees them; a third user → 0 rows.
2. Partner inserts a transaction with `user_id` = environment → accepted; `created_by` = partner uid even when
   the payload claims otherwise.
3. Partner inserts a transaction with `user_id` = their own uid or a third account → refused.
4. Partner **updates** an environment transaction (owner-created) → accepted; `created_by` unchanged,
   `updated_by` = partner, revision bumped; a stale update (old base) → skipped, no error, batch unaffected.
5. Partner soft-deletes an environment transaction (UPDATE setting `deleted_at`) → accepted; a stale delete
   that lost to a newer edit → skipped per the existing rule.
6. Partner update/delete/insert on wallets, categories, budgets, goals, goal_contributions, recurring →
   refused / 0 rows; a bulk statement mixing such a row fails as expected (documents the client-filter
   requirement).
7. Partner hard DELETE on transactions → 0 rows (owner-only verb).
8. Wallet `classification`: owner sets/clears it; partner's attempt → 0 rows; invalid value refused by CHECK;
   null accepted.
9. Invitee with matching verified email sees the pending invite; a different email sees nothing;
   `accept_space_invite` refuses: wrong email, unverified email, expired, revoked, space at
   `lucento_max_partners` capacity (= 1), caller already a Partner elsewhere or owning a space that has
   members; happy path creates the membership row + marks accepted atomically.
10. Capacity mechanics: a second accept while at capacity → refused; two concurrent accepts serialize
    (advisory lock + one-pending-invite index — no over-admission); removing the Partner frees the slot for a
    new invite. With `lucento_max_partners` temporarily returning 2 inside a rolled-back transaction: two
    members coexist, each sees the data and the other's membership row, and removing one never affects the
    other (proves the N-ready structure without shipping it).
11. After deleting a membership row: every select/insert/update by that ex-member → 0 rows / refused, same
    cycle.
12. `space_members` / `space_invites`: no direct insert by anyone; label updates limited to own label; `anon`
    sees and does nothing anywhere.
13. Owner behavior is byte-identical to today on all seven tables (regression), including with a Partner
    attached.
