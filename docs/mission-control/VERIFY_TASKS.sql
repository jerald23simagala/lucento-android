-- Mission Control, Tasks: read-only checks after APPLY_TASKS.sql (DEPLOYMENT.md part K, step 1). Changes nothing.
-- Paste into the Supabase SQL editor and Run: every column should say true (tasks_key_made turns true after step 2,
-- select mc.rotate_ingest_secret('tasks');).

select
  (select count(*) from pg_class c join pg_namespace n on n.oid = c.relnamespace
     where n.nspname = 'mc' and c.relkind = 'r'
       and c.relname in ('tasks', 'task_versions', 'task_events', 'tasks_state')) = 4                              as tables_4,
  exists (select 1 from mc.tasks_state where id)                                                                  as state_row_seeded,
  (select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'mc_api' and p.proname in ('tasks_board', 'task_get', 'task_act', 'tasks_summary')) = 4     as page_functions_4,
  (select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'mc_api' and p.proname like 'ingest\_tasks\_%') = 4                                        as claude_functions_4,
  -- The page's functions: signed-in callers only, each checking the admin itself.
  not exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
              where n.nspname = 'mc_api' and p.proname in ('tasks_board', 'task_get', 'task_act', 'tasks_summary', 'nav_badges')
                and (not has_function_privilege('authenticated', p.oid, 'execute')
                     or has_function_privilege('anon', p.oid, 'execute')))                                         as page_functions_signed_in_only,
  -- Claude's four: anon only (each checks the tasks key first), never signed-in callers.
  not exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
              where n.nspname = 'mc_api' and p.proname like 'ingest\_tasks\_%'
                and (has_function_privilege('authenticated', p.oid, 'execute')
                     or not has_function_privilege('anon', p.oid, 'execute')))                                     as claude_functions_anon_only,
  not exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
              where n.nspname in ('mc', 'mc_api') and (p.proname like 'task%' or p.proname like 'ingest\_tasks%')
                and exists (select 1 from aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) a
                            where a.grantee = 0 and a.privilege_type = 'EXECUTE'))                                as nothing_public,
  not exists (select 1 from pg_class c join pg_namespace n on n.oid = c.relnamespace
              where n.nspname = 'mc' and c.relkind = 'r' and c.relname like 'task%'
                and (has_table_privilege('anon', c.oid, 'select') or has_table_privilege('authenticated', c.oid, 'select')
                     or has_table_privilege('service_role', c.oid, 'select')))                                     as tables_private,
  (select count(*) from pg_trigger t
     where t.tgname in ('task_events_no_change', 'task_events_no_truncate', 'task_versions_no_change', 'task_versions_no_truncate')
       and not t.tgisinternal) = 4                                                                                  as history_append_only,
  coalesce((select pg_get_constraintdef(c.oid) like '%''tasks''%' from pg_constraint c
            where c.conname = 'ingest_secret_purpose_check' and c.conrelid = 'mc.ingest_secret'::regclass), false)  as tasks_key_purpose,
  coalesce((select pg_get_functiondef(p.oid) like '%''tasks''%' from pg_proc p join pg_namespace n on n.oid = p.pronamespace
            where n.nspname = 'mc_api' and p.proname = 'nav_badges'), false)                                     as nav_badge_counts_tasks,
  -- Auto-posting's database part came with it (APPLY_TASKS.sql carries it); auto-posting itself stays off.
  (select count(*) from pg_class c join pg_namespace n on n.oid = c.relnamespace
     where n.nspname = 'mc' and c.relkind = 'r' and c.relname like 'autopost\_%') = 5                           as autopost_database_part,
  -- After step 2 only.
  exists (select 1 from mc.ingest_secret s where s.purpose = 'tasks')                                            as tasks_key_made;
