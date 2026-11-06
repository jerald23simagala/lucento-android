# Lucento Shared — Risks, Open Decisions, and Items Needing Owner Approval

Revised 2026-11-05. Decisions the owner has already made in the revision brief are recorded in §D as settled.

## A. Security concerns (addressed in the design; verify, don't assume)

1. **The membership clause widens RLS on all seven financial tables, and transactions now accept member
   writes.** A bug in `lucento_is_member_of()` (security definer) would be a cross-account leak; a bug in the
   transactions `with check` would let a Partner write outside the Shared Space. Mitigations: the function is
   four lines over one PK-indexed table; `with check` binds `user_id` on insert *and* update; membership
   constraints (unique `partner_id`, PK `owner_id`) make "Partner writing into their own environment"
   structurally impossible while membership exists; the PERMISSION_MATRIX.md §4 checklist runs as real roles
   before the migration reaches any project; security advisor must be clean. This remains the most
   security-sensitive change Lucento has made — an independent `lucento-architect` review of the migration is
   part of Phase 0.
2. **Attribution must be unforgeable and survive collaboration.** Trigger-stamped; `created_by` immutable on
   update regardless of payload; `updated_by` always the authenticated actor; explicitly tested with forged
   payloads from both roles.
3. **Full transaction collaboration raises the blast radius of a compromised Partner account** — it can now
   edit and delete history, not just add. Accepted deliberately (product decision §D.1): the two people share
   finances by consent, deletes are soft and propagate as tombstones, attribution records the actor, and
   structure (wallets, budgets, goals) stays Owner-only. No additional mitigation (e.g. recycle bin) this
   release; the existing 30-day local tombstone window is bookkeeping, not a user-facing undo.
4. **Invitation is addressed, not bearer.** No token exists to leak; acceptance requires a session whose
   verified email matches (the RPC also checks `email_confirmed_at`, so a future relaxation of the project's
   confirm-email setting cannot weaken it).
5. **Partner write surface** is exactly: insert/update (incl. soft delete) on `transactions` within the
   environment, own label on the membership row, deleting the membership row (leave), `accept_space_invite`.
   Everything else is absent by policy, not rejected by trigger.
6. **Offline cache on a removed device** — see C.3.

## B. Open decisions (recommendation first; owner sign-off requested)

1. **Unclassified wallets display as "Shared" inside a Shared Space.** Avoids a backfill and matches intuition
   (everything was implicitly shared before labels existed). Alternative: show no label until the Owner sets
   one. **Default: display as Shared.**
2. **Classification values are fixed (`SHARED`/`OWNER`/`PARTNER`), labels render with names.** A `PARTNER`
   label with no Partner (never invited, or after leaving) renders as "Personal" with the stored label/email;
   the value is kept so a returning Partner's wallets stay classified. Alternative: clear `PARTNER` values on
   membership end. **Default: keep.**
3. **Wallets list grouping by classification** (Shared first, then each person's Personal) vs. label chips
   only. Grouping aids comprehension but reorders a screen under active redesign elsewhere. **Default: chips
   in this release; grouping as a follow-up once the Wallets screen work settles.**
4. **"Deleted by" display.** `updated_by` on the soft delete makes it derivable; showing it (e.g. in a future
   trash/undo view) is deferred. **Default: store, don't display.**
5. **Conflict-notice copy naming the other person** ("a newer change from <label>'s phone") inside a Shared
   Space. Copy-only. **Default: yes, in Phase 4.**
6. **Structurally multi-Partner membership** (owner-requested evaluation, 2026-11-05): one row per member
   (PK `(owner_id, partner_id)`), the one-Partner limit enforced solely by `lucento_max_partners()` (hardcoded
   1) inside the accept RPC, with concurrency handled by an advisory lock + the one-pending-invite index.
   Trade-off analysis in ARCHITECTURE_PROPOSAL.md §2.1: near-zero cost now, removes the known redesign at a
   future limit increase; the cost is that the limit becomes procedural rather than declarative (risk C.11).
   **Recommended; needs your approval since it trades a schema guarantee for a hook.**
7. **`owner_label` placement under multi-Partner:** kept on each membership row (the owner updates all rows of
   their space in one statement; zero duplication at N = 1, trivial at N ≤ 4). Alternative: a one-row-per-space
   labels table — cleaner at larger N, one more table now. **Default: per-row, revisit if a tier beyond 4 ever
   exists.**

## C. Known risks and accepted limitations (documented behavior, not defects)

1. **A Partner's unsynced entries are lost on removal/leave** (they can no longer be pushed — RLS). The
   notices say so; same honesty as today's account-switch wipe.
2. **Two genuinely concurrent edits still tie-break by device clock** (existing engine limitation,
   docs/SYNC.md Known limitations) — and with two *people* editing the same history, concurrent edits of one
   row become materially more likely than with one person's two devices. Sequential edits never depend on
   clocks, and the losing side is notified (P2-6). If this bites in practice, the engine's documented next
   step (keeping the losing version recoverable) applies — out of scope now.
3. **A removed Partner's device keeps its downloaded copy until it next connects**, and an offline device can
   stay offline indefinitely; previously exported backups can never be revoked. Server-side access ends
   instantly; the local copy is data they were legitimately shown while trusted. **No remote wipe is promised
   anywhere in the UI** (the removal confirmation says what their phone already has can't be taken back).
   Settled framing per the revision brief.
4. **Owner edits of a Partner-created transaction** (and vice versa) replace content but never `created_by`;
   `updated_by` shows the last editor. Attribution stays truthful under full collaboration.
5. **Owner account inaccessible:** the Partner retains full transaction collaboration and can leave; nobody
   can administer structure or membership until Supabase account recovery succeeds. No in-app mitigation this
   release; ownership transfer is explicitly out of scope.
6. **Mixed app versions:** the Partner role requires a v12+ build by construction (acceptance is in-app). An
   Owner on an older build keeps syncing (policies only widen; old clients never send the new columns; the
   actor trigger stamps regardless) — they just don't *see* attribution or classification. Acceptable; no
   server guard.
7. **Partner batch purity is a correctness requirement:** a Partner push containing a non-transaction row
   fails that whole batch (42501), and the engine would retry it. Mitigations: the Partner cycle's hard table
   filter, role guards that prevent such dirty rows existing, a surfaced warning if any do, and a Phase 0
   spike pinning the exact failure mode. Residual risk: low, but this is the one place the widened model adds
   a new client obligation.
8. **Deleting the Shared Space** (server-side erase of all rows + membership + invites) stays **deferred**: it
   is really "delete my cloud data", a privacy feature with value for solo users too, and has no counterpart
   today (sign-out only wipes the device). Removal + existing sign-out cover this release.
9. **Performance:** the membership subquery joins every RLS check on hot tables (PK lookup, stable
   security-definer, initplan form). Re-run the performance advisor after Phase 0; regressions are blockers.
10. **Recurring auto-posting and reminders are Owner-device-only** this release (settled, §D.6). Cost: a bill
    due while only the Partner's phone is online posts when the Owner's phone next runs. Occurrence
    idempotency keys keep a future relaxation convergent.
11. **The Partner limit is procedural, not declarative** (if §B.6 is approved): no schema constraint makes a
    second member unrepresentable; the guarantee is `lucento_max_partners()` inside `accept_space_invite`,
    which is the only way a membership row can exist (no insert policy or grant anywhere). Mitigations: the
    advisory lock + one-pending-invite index close the count-then-insert race; PERMISSION_MATRIX.md §4.10
    tests refusal at capacity, serialization, and the slot-freeing on removal. Residual risk: low, and
    confined to one four-line function that is reviewed as part of the Phase 0 security pass. Note the client
    must also tolerate (not crash on, simply render first-row-wins and report) an impossible second member
    appearing, as plain defensive coding.
12. **Future multi-Partner remains unimplemented and unreachable**: no UI, no RPC path, no entitlement can
    admit a second member while `lucento_max_partners` returns 1. What ships N-ready is structure only
    (membership rows, policies written row-wise, attribution by uid, client member-list model). Raising the
    limit later still requires real work — multi-member UI, labels at N, invitation UX, QA of 3+-device
    convergence — none of which is designed yet; only the *schema redesign* has been removed from that bill.

## D. Settled by the product owner (revision brief, 2026-11-05 — recorded, not reopened)

1. The Partner has **full transaction collaboration** (add/edit/delete, any transaction, any wallet);
   structure management stays Owner-only. (Supersedes the add-only model and the insert-only/ignore-duplicates
   sync strategy, which is withdrawn — ARCHITECTURE_PROPOSAL.md §3.6.)
2. Relationship-agnostic positioning; no relationship categories; terminology = Lucento Shared / Shared Space /
   Owner / Partner, names preferred in UI; **"Companion" reserved** for a possible future web experience.
3. Wallet classification Shared / Owner Personal / Partner Personal as **organizational labels only** — no
   wallet-level privacy, visibility or access rules.
4. Environment id = Owner's uid (retained from the original discovery; reassessed and still viable).
5. Joining replaces the Partner device's ledger; the Partner's own cloud data is never merged and remains
   recoverable; honest disclosures, no remote-wipe promises.
6. Recurring auto-posting and reminders Owner-device-only initially; device preferences stay per-person;
   one Owner + one Partner; no ownership transfer; monetization deferred with hooks documented at
   `create_space_invite` / `accept_space_invite` only.
7. (2026-11-05 follow-up brief) Evaluate multi-Partner **structural readiness** while shipping one Partner:
   all Partners identical in permissions; potential future tiers Free 0 / Pro 1 / Pro Plus 2 / Family 4 as
   commercial possibilities only; `max_partners` documented as a potential future entitlement, not an active
   feature; no billing of any kind. (The *adoption* of the structural design is §B.6 — recommended, awaiting
   approval.)

## E. Migration risks

- The Supabase migration is additive (new tables/columns/functions, widened policies; CHECKs in the NOT VALID
  live-rows form per project convention). Old clients unaffected (v9–v11 column-addition precedents; re-verified
  by a smoke test in Phase 0). Rollback: policies can be narrowed and tables dropped by a follow-up migration
  without touching financial rows.
- Room v12 is a three-column add with a standard migration + test; destructive fallback stays disabled; backup
  format bump ships with a read-back test for the previous format.
- Rollout order (unchanged convention): migration → `lucento-test` → v12 builds against test → owner approves
  production migration → release build.

## F. Explicitly not built (re-affirmed)

Subscriptions/billing/entitlements (`lucento_max_partners`, hardcoded 1, is the one documented hook); a second
Partner (structurally representable if §B.6 is approved, but unreachable — C.12) or configurable
roles; relationship categories; multiple spaces / switching / ownership transfer; email delivery; wallet-level
access control; approval workflows; audit infrastructure beyond `created_by`/`updated_by`; remote device wipe;
Shared Space deletion (C.8); any website functionality; any change to the sync engine's conflict model.

## G. Decisions made during implementation (2026-11-05)

1. **Migrations not applied to `lucento-test` by this session.** The Supabase connector asks for interactive approval
   before DDL, which an unattended session can't give, so nothing was forced. The migrations were instead verified on
   real Postgres 17 (`tools/sql-verify`, 69/69), and applying them is an owner step. The earlier
   `20261105090000_receivable_wallets.sql` is also not on `lucento-test` yet, and must go first. **Resolved
   2026-11-05:** the owner applied all three to `lucento-test`, and a live check matched them to the repo
   (SECURITY_MODEL.md §8).
2. **Partner push uploads transactions only.** A stray dirty structure row on a Partner's phone is reverted from the
   server copy rather than left dirty. Echo tombstones from applied remote deletes are settled from the server copy,
   not uploaded.
3. **Joining doesn't reseed income categories.** Every category comes from the space; a Partner's phone could never
   upload a seed.
4. **No FK on `created_by`/`updated_by`.** Deleting an account must not rewrite rows in someone else's ledger.
5. **Classification is sent only when a wallet in the batch is classified.** A solo v12 phone sends exactly what v11
   sent, and the key set stays uniform within a batch.
6. **Backups don't carry classification or attribution yet.** A restored file reads every wallet as Shared and shows
   no attribution until sync brings the server's values back. The server keeps them: pushes never send attribution,
   and omit classification unless a wallet in the batch is classified. One edge remains: an Owner who labels a wallet
   on a restored phone *before* its first sync sends null labels for the other wallets in that upload. Partners can
   export a backup, but Restore is hidden on their phones. A backup-format bump is listed in NEXT_TASKS.
7. **The Owner's name comes from `shared_spaces.owner_label`, the Partner's from their membership row.** Either falls
   back to the first part of the email. Attribution shows "you" for the phone's own account and "someone else" for an
   account the phone never learned.
8. **Onboarding adopts an existing membership** (a new phone, a reinstall, an interrupted join) before any restore. It
   offers a waiting invitation only to an account with nothing of its own; an account with data restores first, and
   Home offers the invitation. If the server can't be asked, onboarding carries on as before.
9. **"Joined on another phone" is a state of its own.** Settings › Lucento Shared and Home offer to switch this phone.
   Leaving from such a phone ends the membership but keeps this phone's own ledger.
10. **Invitations are delivered by the system share sheet.** Lucento sends no email. The message tells the person to
    sign in with the invited address.
11. **Workspace model stays A (owner decision, 2026-11-05).** The Owner's whole ledger is the Shared Space; there is
    no separate shared ledger and no personal workspace beside it. The owner's reasons: for couples (later families)
    the point of sharing is one ledger with nothing hidden; the Owner sees everything as one; and commercially the
    space carries the Owner's plan — Partners get its features through the Owner, and a Partner who wants a personal
    Lucento as well needs their own plan. A Partner's own data waits in their account while they share and comes back
    if they leave. What was built instead: an indicator for both people once someone has joined (a small "Shared" pill
    on the profile button of every primary screen, and a Lucento Shared row in the profile menu). When gating arrives, entitlement checks on a Partner's phone should read the Owner's plan (the
    space id is the Owner's id), and a family tier is a `lucento_private.max_partners()` change.

## H. Open items

- Owner: the two-device test (docs/handover/NEXT_TASKS.md OT-8). The migrations have been on `lucento-test` since
  2026-11-05, and the advisors were run (SECURITY_MODEL.md §8). Production only on explicit approval.
- Not built yet: history-row attribution chip; a conflict-notice variant naming the other person; a "Shared with"
  line in the sync sheet; backup-format support for classification and attribution; `ScreenshotTourTest` coverage
  of the Lucento Shared states.
