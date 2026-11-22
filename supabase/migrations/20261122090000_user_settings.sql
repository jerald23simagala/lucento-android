-- Settings that follow the account (docs/SYNC.md "Settings"). Tester feedback, 2026-11-22: sync brought the records back
-- but not the person's settings and customisations, "so restoring means setting up again".
--
-- public.user_settings — one row per account: a JSON document with one entry per setting and the time it was chosen,
--   {"theme": {"v": "grove", "at": 1763800000000}, "features": {"v": ["budgets", "insights"], "at": …}, …}
-- holding the theme and its mode, the modules switched on, the display name, Show cents, the Wallets view, hidden module
-- offers, exchange rates online, AI answers on or off, the default wallet and the budget moment. Phones merge it one
-- setting at a time (app: domain/settings/AccountSettings.kt). Consents to send something (crash reports, usage counts,
-- online voice, Smarter reading, the agreement to what AI answers send) and the app lock never come here.
--
-- Concurrency — compare-and-set on revision. A phone reads the row, merges, and writes the result back with
-- base_revision = the revision it read. lucento_user_settings_write keeps that write only while it is still the stored
-- revision (which then becomes revision + 1) and skips it otherwise: no row comes back, so the phone reads the newer
-- version, merges again and retries. No phone overwrites a version it hasn't seen. An account's first write is an insert;
-- of two first writes at once, the second becomes an update without a base, is skipped, and merges.
--
-- Security — RLS: an account reads and writes only its own row (a Partner's settings are their own, never the Owner's,
-- and the Owner never sees a Partner's). INSERT and UPDATE also need the CLOUD capability, like every synced table
-- (security audit F-01, 20261111090000_cloud_write_gate_and_quotas.sql); reading isn't gated, so a lapsed plan still
-- restores. No DELETE grant or policy: the row goes with the account (on delete cascade, public.delete_my_account). The
-- document must be a JSON object of at most 16 KB, so the row can't be used as storage. Grants to authenticated only.
--
-- Rollout — additive: one table, one function, one trigger. Builds without settings sync never touch it. A build with it,
-- against a project without this migration, skips the settings step (records sync as before) until this is applied.

create table if not exists public.user_settings (
  user_id uuid primary key references auth.users (id) on delete cascade,
  settings jsonb not null default '{}'::jsonb,
  revision bigint not null default 1,
  -- Sent by the phone with each write: the revision its merge was based on. Only carries the request (nulled on update).
  base_revision bigint,
  -- Server time of the last accepted write; for support and diagnosis only (phones compare revisions, never times).
  updated_at timestamptz not null default now(),
  constraint user_settings_document check (jsonb_typeof(settings) = 'object' and octet_length(settings::text) <= 16384)
);

comment on table public.user_settings is
  'Settings that follow the account: one JSON document per account, merged per setting by the app. docs/SYNC.md "Settings"';

create or replace function public.lucento_user_settings_write()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if tg_op = 'UPDATE' then
    if new.user_id is distinct from old.user_id then
      raise exception 'user_id cannot change';
    end if;
    -- Only on top of the version the phone merged with. Anything else is skipped: the phone reads, merges and retries.
    if new.base_revision is distinct from old.revision then
      return null;
    end if;
    new.revision := old.revision + 1;
    new.base_revision := null;
  else
    -- In INSERT … ON CONFLICT DO UPDATE this also runs on the proposed row, and EXCLUDED.base_revision (the phone's base)
    -- is read from it afterwards, so base_revision is left alone here (as in lucento_stamp_server_time).
    new.revision := 1;
  end if;
  new.updated_at := now();
  return new;
end;
$$;

drop trigger if exists trg_user_settings_write on public.user_settings;
create trigger trg_user_settings_write before insert or update on public.user_settings
  for each row execute function public.lucento_user_settings_write();

alter table public.user_settings enable row level security;

drop policy if exists "user_settings_select_own" on public.user_settings;
create policy "user_settings_select_own" on public.user_settings for select to authenticated
  using (user_id = (select auth.uid()));

drop policy if exists "user_settings_insert_own" on public.user_settings;
create policy "user_settings_insert_own" on public.user_settings for insert to authenticated
  with check (user_id = (select auth.uid()) and (select lucento_private.caller_has_cloud()));

drop policy if exists "user_settings_update_own" on public.user_settings;
create policy "user_settings_update_own" on public.user_settings for update to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()) and (select lucento_private.caller_has_cloud()));

-- Least privilege whatever the project's default privileges are: read, insert and update for signed-in users, nothing
-- else, nothing for anon. The trigger function needs no grant (triggers run it whatever the caller may execute).
revoke all on table public.user_settings from anon, authenticated;
grant select, insert, update on table public.user_settings to authenticated;
revoke all on function public.lucento_user_settings_write() from public, anon, authenticated;
