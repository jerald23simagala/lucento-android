-- Anonymous usage counts. No account column and no link to auth.users.
-- The device sends a random install id. Opt-out calls delete_usage_events(install id) before
-- the device forgets that id. Clients may insert, and may execute that one function. They cannot
-- DELETE from the table.
-- This file is not applied by the app or by CI; apply it in the Supabase project when the
-- usage-counts backend is turned on.

create table if not exists public.usage_events (
  id uuid primary key default gen_random_uuid(),
  install_id uuid not null,
  event text not null,
  props jsonb not null default '{}'::jsonb,
  app_version text,
  created_at timestamptz not null default now(),
  constraint usage_events_event_allowlist check (
    event in (
      'onboarding_started',
      'onboarding_step_viewed',
      'first_entry_saved',
      'd1_return',
      'd7_return',
      'first_week_card_step_done',
      'budget_suggestion_shown',
      'budget_suggestion_accepted'
    )
  ),
  constraint usage_events_event_length check (char_length(event) <= 40),
  constraint usage_events_app_version_length check (app_version is null or char_length(app_version) <= 32),
  constraint usage_events_props_size check (octet_length(props::text) <= 512)
);

alter table public.usage_events enable row level security;

revoke all on table public.usage_events from public, anon, authenticated;
grant insert on table public.usage_events to anon, authenticated;

create policy "usage_events: insert"
  on public.usage_events
  for insert
  to anon, authenticated
  with check (true);

-- Rows older than 90 days. Client roles cannot call this. The table owner (and pg_cron, when it
-- runs as a role that bypasses RLS) can.
create or replace function public.purge_usage_events()
returns void
language sql
as $$
  delete from public.usage_events
  where created_at < now() - interval '90 days';
$$;

revoke all on function public.purge_usage_events() from public, anon, authenticated;

-- Opt-out. Deletes only the rows for the install id the device still holds. SECURITY DEFINER so
-- it can delete without a table DELETE grant. search_path is fixed and empty; the table is schema-qualified.
create or replace function public.delete_usage_events(p_install_id uuid)
returns void
language sql
security definer
set search_path = ''
as $$
  delete from public.usage_events
  where install_id = p_install_id;
$$;

revoke all on function public.delete_usage_events(uuid) from public;
grant execute on function public.delete_usage_events(uuid) to anon, authenticated;

-- pg_cron is optional. When the extension is already installed, schedule a daily purge.
-- When it is not, run `select public.purge_usage_events();` daily from the Supabase dashboard
-- or enable pg_cron and schedule: cron.schedule('purge-usage-events', '15 3 * * *', 'select public.purge_usage_events()');
do $$
begin
  if exists (select 1 from pg_extension where extname = 'pg_cron') then
    if not exists (select 1 from cron.job where jobname = 'purge-usage-events') then
      perform cron.schedule(
        'purge-usage-events',
        '15 3 * * *',
        'select public.purge_usage_events()'
      );
    end if;
  end if;
end
$$;
