-- Mission Control V1: read-only checks after APPLY_MISSION_CONTROL.sql (DEPLOYMENT.md step A3). Changes nothing.
-- Paste into the Supabase SQL editor and Run. Every column should say true (the counts are for information).

select
  (select count(*) from pg_namespace where nspname in ('mc', 'mc_api')) = 2                               as schemas_created,
  (select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'mc_api') = 41 as api_functions_41,
  (select count(*) from pg_class c join pg_namespace n on n.oid = c.relnamespace
     where n.nspname = 'mc' and c.relkind = 'r') = 19                                                         as tables_19,
  (select count(*) from mc.settings) >= 7                                                                      as settings_seeded,
  -- Nobody but the owner reaches the private schema; anon runs only the five ingest functions.
  not has_schema_privilege('authenticated', 'mc', 'usage') and not has_schema_privilege('anon', 'mc', 'usage') as mc_schema_private,
  not exists (
    select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'mc_api' and has_function_privilege('anon', p.oid, 'execute')
      and p.proname not in ('ingest_email_events', 'play_orders_wanted', 'ingest_play_orders', 'ingest_job_run', 'ingest_service_checks')
  )                                                                                                            as anon_ingest_only,
  has_function_privilege('anon', 'public.submit_support_request(text, text, text, uuid, uuid, jsonb)', 'execute') as support_form_open,
  not has_function_privilege('anon', 'public.report_client(text, integer, integer, text)', 'execute')          as report_client_signed_in_only,
  (select count(*) from cron.job where jobname in ('mc-activity', 'mc-grants', 'mc-deletions', 'mc-daily')) = 4 as cron_jobs_4,
  (select pg_get_constraintdef(c.oid) like '%lucento_pro_lifetime%' from pg_constraint c
     where c.conname = 'entitlements_grant_expires')                                                          as grant_constraint_updated,
  (select count(*) from mc.entitlement_grants where kind = 'imported')                                         as hand_written_grants_imported,
  exists (select 1 from mc.admins where active)                                                                as admin_added_step_a5,
  exists (select 1 from mc.ingest_secret)                                                                      as ingest_secret_created_step_a6;
