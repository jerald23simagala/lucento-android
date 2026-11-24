-- Mission Control V1: read-only checks after APPLY_MISSION_CONTROL.sql (DEPLOYMENT.md step A3). Changes nothing.
-- Paste into the Supabase SQL editor and Run. Every column should say true (the counts are for information).
-- Marketing approvals (APPLY_MARKETING.sql), auto-posting (APPLY_AUTOPOST.sql) and Tasks (APPLY_TASKS.sql) stay out of
-- these counts, before and after them: VERIFY_MARKETING.sql, VERIFY_AUTOPOST.sql and VERIFY_TASKS.sql check them.

select
  (select count(*) from pg_namespace where nspname in ('mc', 'mc_api')) = 2                               as schemas_created,
  (select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'mc_api' and p.proname not like '%marketing%' and p.proname not like '%autopost%' and p.proname not like '%task%') = 43                                  as api_functions_43,
  (select count(*) from pg_class c join pg_namespace n on n.oid = c.relnamespace
     where n.nspname = 'mc' and c.relkind = 'r' and c.relname not like 'marketing\_%' and c.relname not like 'autopost\_%' and c.relname not like 'task%') = 19                  as tables_19,
  (select count(*) from mc.settings) >= 7                                                                      as settings_seeded,
  -- Nobody but the owner reaches the private schema; anon runs only the five ingest functions (and Marketing's four).
  not has_schema_privilege('authenticated', 'mc', 'usage') and not has_schema_privilege('anon', 'mc', 'usage') as mc_schema_private,
  not exists (
    select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'mc_api' and has_function_privilege('anon', p.oid, 'execute')
      and p.proname not in ('ingest_email_events', 'play_orders_wanted', 'ingest_play_orders', 'ingest_job_run', 'ingest_service_checks',
                            'ingest_marketing_pack', 'ingest_marketing_pending', 'ingest_marketing_media_ready',
                            'ingest_marketing_feedback', 'ingest_autopost_claim', 'ingest_autopost_result',
                            'ingest_autopost_token', 'ingest_tasks_push', 'ingest_tasks_feed', 'ingest_tasks_get',
                            'ingest_tasks_update')
  )                                                                                                            as anon_ingest_only,
  has_function_privilege('anon', 'public.submit_support_request(text, text, text, uuid, uuid, jsonb)', 'execute') as support_form_open,
  not has_function_privilege('anon', 'public.report_client(text, integer, integer, text)', 'execute')          as report_client_signed_in_only,
  (select count(*) from cron.job where jobname in ('mc-activity', 'mc-grants', 'mc-deletions', 'mc-daily')) = 4 as cron_jobs_4,
  (select pg_get_constraintdef(c.oid) like '%lucento_pro_lifetime%' from pg_constraint c
     where c.conname = 'entitlements_grant_expires')                                                          as grant_constraint_updated,
  exists (select 1 from pg_trigger where tgname = 'mc_forget_account_emails')                                  as deletion_trigger_on_auth_users,
  -- Both privacy switches stay off until the release with the updated privacy policy is live (DEPLOYMENT.md part H).
  not mc.setting_on('records_view_enabled') and not mc.setting_on('extended_data_enabled')                     as privacy_switches_off,
  (select count(*) from mc.entitlement_grants where kind = 'imported')                                         as hand_written_grants_imported,
  exists (select 1 from mc.admins where active)                                                                as admin_added_step_a5,
  (select count(*) from mc.ingest_secret where purpose in ('app', 'backup')) = 2                               as both_secrets_created_step_a6;
