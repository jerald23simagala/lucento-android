-- Security audit F-07 and F-13 (docs/security/FINAL_SECURITY_AUDIT.md, docs/security/REMEDIATION_REPORT.md).
--
--   F-07  Seed category ids are predictable: a fresh install's income categories get "seed-<user id>-<kind>-<slug>"
--         (SyncRepository.assignMissingCategoryIds) and categories.id is a global primary key, so an account that learnt
--         another's user id (a Shared Space shows it) could create that id first and break the other's sync (probe X3).
--         A "seed-" id is now accepted only under the account it names. The app's ids are unchanged, so every existing
--         row, phone and migration keeps working; ids that aren't seed ids (random UUIDs) are not affected.
--         NOT VALID like the other checks: a squatted row already stored is left for the owner to review
--         (docs/security/OWNER_ACTIONS_REQUIRED.md §14 lists the read-only query).
--   F-13  Supabase's stock default privileges give anon and authenticated every privilege (TRUNCATE, REFERENCES,
--         TRIGGER, MAINTAIN included) on each new table, sequence and function that postgres creates in public. Every
--         migration so far revokes and grants explicitly; this makes the safe outcome the default, so a future migration
--         that forgets its revoke exposes nothing. Existing objects keep exactly the grants they have.
--         Objects created by other roles (supabase_admin) keep their own defaults; only postgres may change postgres's.

-- ------------------------------------------------------------------------------------------------ F-07
alter table public.categories drop constraint if exists categories_seed_id_owner;
alter table public.categories add constraint categories_seed_id_owner check (
  id not like 'seed-%' or starts_with(id, 'seed-' || user_id::text || '-')
) not valid;

-- ------------------------------------------------------------------------------------------------ F-13
alter default privileges for role postgres in schema public revoke all on tables from anon, authenticated;
alter default privileges for role postgres in schema public revoke all on sequences from anon, authenticated;
alter default privileges for role postgres in schema public revoke all on functions from public, anon, authenticated;
-- A per-schema entry can only add to the global defaults, and PostgreSQL's global default lets PUBLIC execute every new
-- function: that one must be revoked globally (every schema; each migration already grants EXECUTE explicitly).
alter default privileges for role postgres revoke execute on functions from public;
