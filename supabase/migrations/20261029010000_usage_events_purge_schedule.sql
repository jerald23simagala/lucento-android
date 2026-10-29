-- The usage_events migration that already ran only scheduled a purge when pg_cron was
-- already installed, and left purge_usage_events on the default search_path.
-- This file is safe to run on a database that already has the job: it replaces the
-- function, creates the extension if needed, unschedules every job with this name,
-- then schedules one daily purge.

create or replace function public.purge_usage_events()
returns void
language sql
set search_path = ''
as $$
  delete from public.usage_events
  where created_at < pg_catalog.now() - interval '90 days';
$$;

revoke all on function public.purge_usage_events() from public, anon, authenticated;

create extension if not exists pg_cron with schema pg_catalog;

do $$
declare
  existing bigint;
begin
  for existing in select jobid from cron.job where jobname = 'purge-usage-events'
  loop
    perform cron.unschedule(existing);
  end loop;
  perform cron.schedule(
    'purge-usage-events',
    '15 3 * * *',
    'select public.purge_usage_events()'
  );
end
$$;
