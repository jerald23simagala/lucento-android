# Production migration manifest — 2026-11-10

Security audit **F-14**: production's migration history (`supabase_migrations.schema_migrations`) doesn't record what is
deployed, because most migrations were applied as pasted SQL. This manifest establishes, from the catalog, which
repository migrations are present in production, and gives a safe way to make the history match. **Nothing here has been
run against production.** The owner runs §3 when they choose.

- **Project:** `lucento` (`hmtcuttadbgbldvzsfhz`), Postgres 17.11, region ap-northeast-2.
- **How it was read:** Supabase MCP `list_migrations` and one read-only catalog query (`to_regclass`, `pg_constraint`,
  `pg_policies`, `pg_proc`, `information_schema.columns`, `has_function_privilege`). No user row was read and nothing was
  written. Run by Claude on 2026-11-10.

## 1. What the history table says

11 rows. Three carry a **different version** from the repository file of the same name (`KNOWN_ISSUES` N-07): they were
applied under the time they ran, not the file's timestamp.

| Recorded version | Name | Repository file |
|---|---|---|
| 20261024085632 | create_sync_tables | same |
| 20261024085649 | enable_rls_policies | same |
| 20261024101635 | enforce_last_write_wins | same |
| 20261024101725 | grant_authenticated_table_access | same |
| 20261025145902 | server_clock_sync_cursor | same |
| 20261026120000 | recurring_and_debt_terms_sync | same |
| 20261027090000 | ai_usage_quota | same |
| 20261028050634 | revoke_extra_table_privileges | same |
| **20261029030807** | usage_events | `20261028180000_usage_events.sql` |
| **20261029030814** | rls_initplan_and_fk_indexes | `20261028190000_rls_initplan_and_fk_indexes.sql` |
| **20261029030820** | usage_events_purge_schedule | `20261029010000_usage_events_purge_schedule.sql` |

## 2. What the catalog says (marker objects)

One object each migration creates and nothing else does. **All 15 later migrations are present; none is in the history.**

| Repository migration | Marker checked | Present |
|---|---|---|
| 20261028180000_usage_events | table `public.usage_events` | yes |
| 20261028190000_rls_initplan_and_fk_indexes | `wallets_select_own` uses `(SELECT auth.uid())` | yes |
| 20261029010000_usage_events_purge_schedule | `pg_cron` installed (job verified in the audit) | yes |
| 20261101120000_financial_check_constraints | constraint `transactions_amount_range` | yes |
| 20261101180000_check_constraints_live_rows | that constraint exempts soft-deleted rows | yes |
| 20261102120000_budget_lifecycle_and_institutions | column `budgets.month` | yes |
| 20261103180000_credit_card_billing | column `wallets.credit_limit_minor` | yes |
| 20261105090000_receivable_wallets | `wallets_type_known` allows `RECEIVABLE` | yes |
| 20261105120000_lucento_shared | table `public.shared_spaces` | yes |
| 20261105120100_lucento_shared_privileges | anon can't execute `get_my_shared_space()` | yes |
| 20261106090000_multicurrency | table `public.ledger_settings` | yes |
| 20261107090000_entitlements | table `public.entitlements` | yes |
| 20261108090000_play_purchases | function `public.apply_play_purchase` | yes |
| 20261109090000_account_deletion | function `public.delete_my_account` | yes |
| 20261110090000_account_emails | table `public.account_emails` | yes |
| **20261111090000_cloud_write_gate_and_quotas** (new) | `lucento_private.caller_has_cloud` | **no — not applied** |
| **20261111100000_play_purchase_integrity** (new) | `lucento_private.revoked_purchases` | **no — not applied** |
| 20261111110000_seed_ids_and_default_privileges (new) | — | not applied |
| 20261111120000_request_quotas (new) | — | not applied |

The audit (2026-11-09) also compared the security-relevant catalog — policies, grants, function ACLs, `search_path`,
triggers, default ACLs, cron — with the repository by hand and found it matching. A marker shows a migration ran; it
doesn't prove every statement in it did. The four new migrations will be applied with the Supabase CLI or the MCP
`apply_migration` tool, which record their own history rows, so the gap stops growing.

## 3. Reconciliation (owner, optional, after reading §2)

Back-filling only adds bookkeeping rows; it changes no schema. It makes `supabase migration list` and `list_migrations`
agree with the repository, so a future `supabase db push` won't try to re-run old files. Two choices:

**A. Record the 15 missing versions (recommended).** In Supabase → SQL Editor, run once:

```sql
-- Bookkeeping only: the objects already exist (docs/security/MIGRATION_MANIFEST_2026-11-10.md §2).
insert into supabase_migrations.schema_migrations (version, name, statements)
values
  ('20261028180000', 'usage_events', '{}'),
  ('20261028190000', 'rls_initplan_and_fk_indexes', '{}'),
  ('20261029010000', 'usage_events_purge_schedule', '{}'),
  ('20261101120000', 'financial_check_constraints', '{}'),
  ('20261101180000', 'check_constraints_live_rows', '{}'),
  ('20261102120000', 'budget_lifecycle_and_institutions', '{}'),
  ('20261103180000', 'credit_card_billing', '{}'),
  ('20261105090000', 'receivable_wallets', '{}'),
  ('20261105120000', 'lucento_shared', '{}'),
  ('20261105120100', 'lucento_shared_privileges', '{}'),
  ('20261106090000', 'multicurrency', '{}'),
  ('20261107090000', 'entitlements', '{}'),
  ('20261108090000', 'play_purchases', '{}'),
  ('20261109090000', 'account_deletion', '{}'),
  ('20261110090000', 'account_emails', '{}')
on conflict (version) do nothing;
```

The three rows recorded under their run time (`20261029030807/14/20`) stay as they are: deleting history rows is never
needed, and the repository's versions for them are now recorded too. Check afterwards: `list_migrations` shows 26 rows.

**B. Keep this manifest as the record.** Leave the history table alone and add a dated section here each time SQL is
applied by hand. Choose A unless there is a reason not to.

Before either: re-run §2's query (Claude can run it read-only) to confirm nothing changed since 2026-11-10.
