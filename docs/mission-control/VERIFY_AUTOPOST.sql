-- Mission Control, auto-posting: read-only checks after APPLY_AUTOPOST.sql (DEPLOYMENT.md part J, step 1). Changes
-- nothing. Paste into the Supabase SQL editor and Run: every column should say true (clock_scheduled turns true after
-- step 2, `select mc.autopost_setup(...)`).

select
  (select count(*) from pg_class c join pg_namespace n on n.oid = c.relnamespace
     where n.nspname = 'mc' and c.relkind = 'r'
       and c.relname in ('autopost_connections', 'autopost_destinations', 'autopost_jobs', 'autopost_events',
                         'autopost_state')) = 5                                                                   as tables_5,
  exists (select 1 from mc.autopost_state where id)                                                              as state_row_seeded,
  exists (select 1 from information_schema.columns
          where table_schema = 'mc' and table_name = 'marketing_cards' and column_name = 'autopost_off')         as card_column_added,
  (select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'mc_api' and p.proname like 'autopost\_%') = 9                                           as page_functions_9,
  (select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'mc_api' and p.proname like 'ingest\_autopost\_%') = 3                                   as ingest_functions_3,
  -- The page's functions: signed-in callers only, each checking the admin itself.
  not exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
              where n.nspname = 'mc_api' and p.proname like 'autopost\_%'
                and (not has_function_privilege('authenticated', p.oid, 'execute')
                     or has_function_privilege('anon', p.oid, 'execute')))                                         as page_functions_signed_in_only,
  -- The console's three: anon only (each checks its secrets first), never signed-in callers.
  not exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
              where n.nspname = 'mc_api' and p.proname like 'ingest\_autopost\_%'
                and (has_function_privilege('authenticated', p.oid, 'execute')
                     or not has_function_privilege('anon', p.oid, 'execute')))                                     as ingest_anon_only,
  not exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
              where n.nspname in ('mc', 'mc_api') and p.proname like '%autopost%'
                and exists (select 1 from aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) a
                            where a.grantee = 0 and a.privilege_type = 'EXECUTE'))                                as nothing_public,
  -- Tokens live only here, encrypted, and no API role can read these tables.
  not exists (select 1 from pg_class c join pg_namespace n on n.oid = c.relnamespace
              where n.nspname = 'mc' and c.relkind = 'r' and c.relname like 'autopost\_%'
                and (has_table_privilege('anon', c.oid, 'select') or has_table_privilege('authenticated', c.oid, 'select')
                     or has_table_privilege('service_role', c.oid, 'select')))                                     as tables_private,
  coalesce((select pg_get_constraintdef(c.oid) like '%''autopost''%' from pg_constraint c
            where c.conname = 'ingest_secret_purpose_check' and c.conrelid = 'mc.ingest_secret'::regclass), false)  as autopost_secret_purpose,
  coalesce((select pg_get_constraintdef(c.oid) like '%autopost_enabled%' from pg_constraint c
            where c.conname = 'settings_key_check' and c.conrelid = 'mc.settings'::regclass), false)                as settings_keys_added,
  (select value from mc.settings where key = 'autopost_enabled') is not null                                     as enabled_setting_seeded,
  coalesce((select pg_get_constraintdef(c.oid) like '%''autopost''%' from pg_constraint c
            where c.conname = 'job_runs_job_check' and c.conrelid = 'mc.job_runs'::regclass), false)              as job_listed,
  exists (select 1 from pg_extension x where x.extname = 'pg_net')                                               as pg_net_on,
  -- After step 2 only (read through query_to_xml so this runs without pg_cron too).
  case when not exists (select 1 from pg_extension x where x.extname = 'pg_cron') then false
       else (xpath('/row/n/text()', query_to_xml(
               'select count(*) as n from cron.job where jobname = ''mc-autopost''', false, true, '')))[1]::text = '1'
  end                                                                                                              as clock_scheduled;
