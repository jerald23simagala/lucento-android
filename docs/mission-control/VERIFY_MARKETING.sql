-- Mission Control, Marketing approvals: read-only checks after APPLY_MARKETING.sql (DEPLOYMENT.md part I, step 1).
-- Changes nothing. Paste into the Supabase SQL editor and Run: every column should say true.

select
  (select count(*) from pg_class c join pg_namespace n on n.oid = c.relnamespace
     where n.nspname = 'mc' and c.relkind = 'r'
       and c.relname in ('marketing_packs', 'marketing_cards', 'marketing_versions', 'marketing_files', 'marketing_media',
                         'marketing_log', 'marketing_launch', 'marketing_numbers')) = 8                        as tables_8,
  exists (select 1 from mc.marketing_launch where id)                                                              as launch_row_seeded,
  (select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'mc_api' and p.proname like 'marketing\_%') = 10                                          as page_functions_10,
  (select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'mc_api' and p.proname like 'ingest\_marketing\_%') = 4                                   as ingest_functions_4,
  -- The Marketing page's functions (and the badges): signed-in callers only, each checking the admin itself.
  not exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
              where n.nspname = 'mc_api' and (p.proname like 'marketing\_%' or p.proname = 'nav_badges')
                and (not has_function_privilege('authenticated', p.oid, 'execute')
                     or has_function_privilege('anon', p.oid, 'execute')))                                         as page_functions_signed_in_only,
  -- Besides Mission Control's five, anon runs exactly the four ingest_marketing_* functions (each checks its secret).
  (select coalesce(array_agg(p.proname::text order by p.proname), '{}') from pg_proc p join pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'mc_api' and has_function_privilege('anon', p.oid, 'execute')
       and p.proname not in ('ingest_email_events', 'play_orders_wanted', 'ingest_play_orders', 'ingest_job_run', 'ingest_service_checks'))
    = array['ingest_marketing_feedback', 'ingest_marketing_media_ready', 'ingest_marketing_pack', 'ingest_marketing_pending']
                                                                                                                   as anon_marketing_exactly_4,
  not exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
              where n.nspname = 'mc_api' and p.proname like 'ingest\_marketing\_%'
                and has_function_privilege('authenticated', p.oid, 'execute'))                                     as ingest_not_for_signed_in,
  not exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
              where n.nspname in ('mc', 'mc_api') and p.proname like '%marketing%'
                and exists (select 1 from aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) a
                            where a.grantee = 0 and a.privilege_type = 'EXECUTE'))                                as nothing_public,
  not exists (select 1 from pg_class c join pg_namespace n on n.oid = c.relnamespace
              where n.nspname = 'mc' and c.relkind = 'r' and c.relname like 'marketing\_%'
                and (has_table_privilege('anon', c.oid, 'select') or has_table_privilege('authenticated', c.oid, 'select')
                     or has_table_privilege('service_role', c.oid, 'select')))                                     as tables_private,
  -- Versions never change; the log takes no UPDATE (but removing an owner's note), DELETE (but cascades) or TRUNCATE.
  (select count(*) from pg_trigger t where not t.tgisinternal
     and t.tgname in ('marketing_versions_no_update', 'marketing_log_no_change', 'marketing_log_no_truncate')) = 3   as triggers_3,
  coalesce((select pg_get_constraintdef(c.oid) like '%''marketing''%' from pg_constraint c
            where c.conname = 'ingest_secret_purpose_check' and c.conrelid = 'mc.ingest_secret'::regclass), false) as marketing_secret_purpose,
  coalesce((select position('''marketing''' in p.prosrc) > 0 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
            where n.nspname = 'mc' and p.proname = 'rotate_ingest_secret'), false)                                as rotate_accepts_marketing,
  coalesce((select pg_get_constraintdef(c.oid) like '%marketing_retention%' from pg_constraint c
            where c.conname = 'job_runs_job_check' and c.conrelid = 'mc.job_runs'::regclass), false)              as retention_job_listed,
  coalesce((select position('''marketing''' in p.prosrc) > 0 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
            where n.nspname = 'mc_api' and p.proname = 'nav_badges'), false)                                      as nav_badge_marketing,
  -- The daily retention job, wherever pg_cron is installed (read through query_to_xml so this runs without it too).
  case when not exists (select 1 from pg_extension x where x.extname = 'pg_cron') then true
       else (xpath('/row/n/text()', query_to_xml(
               'select count(*) as n from cron.job where jobname = ''mc-marketing-retention''', false, true, '')))[1]::text = '1'
  end                                                                                                              as retention_scheduled;
