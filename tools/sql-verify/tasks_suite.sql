-- Mission Control: the owner's tasks (supabase/migrations/20261120090000_mission_control_tasks.sql,
-- docs/mission-control/v1/OWNER_TASKS.md). Run with tools/sql-verify (see README.md):
--   node run.mjs ../../supabase/migrations tasks_suite.sql
-- Plays the owner (an authenticator code an hour old), a customer, anon and Claude's tool (anon + the tasks secret)
-- against every rule: who may call what, the write-up's checks (all or nothing), versions and history, the owner's
-- actions with their row versions, Claude's actions, the read positions, Overview's summary, the badge and the audit.
-- Raises one exception listing every check; "VERIFY 0/N failed" is a pass.
do $verify$
declare
  r text[] := '{}';
  v_admin uuid := gen_random_uuid();
  v_cust uuid := gen_random_uuid();
  s_fresh uuid := gen_random_uuid();
  v_today date := (now() at time zone 'Asia/Manila')::date;
  v_tasks text; v_mkt text; v_t text; v_n integer; v_m integer; v_ok boolean;
  v_j jsonb; v_k jsonb; v_b jsonb; v_bad jsonb; v_fn record;
  v_rv bigint; v_id uuid; v_max bigint;
  v_long text := repeat('x', 1500);
begin
  -- ======================================================================== helpers
  create function pg_temp.as_owner() returns void language plpgsql as $f$
  begin
    perform set_config('request.jwt.claims', json_build_object('sub', current_setting('verify.admin'), 'role', 'authenticated',
      'aal', 'aal2', 'session_id', current_setting('verify.fresh'), 'amr', json_build_array(
        json_build_object('method', 'totp', 'timestamp', floor(extract(epoch from now() - interval '1 hour'))::bigint)))::text, true);
    perform set_config('role', 'authenticated', true);
  end $f$;
  create function pg_temp.as_user(p_uid uuid) returns void language plpgsql as $f$
  begin
    perform set_config('request.jwt.claims', json_build_object('sub', p_uid, 'role', 'authenticated', 'aal', 'aal1')::text, true);
    perform set_config('role', 'authenticated', true);
  end $f$;
  create function pg_temp.as_anon() returns void language plpgsql as $f$
  begin
    perform set_config('request.jwt.claims', json_build_object('role', 'anon')::text, true);
    perform set_config('role', 'anon', true);
  end $f$;
  create function pg_temp.owner(p_sql text) returns jsonb language plpgsql as $f$
  declare v jsonb;
  begin
    perform pg_temp.as_owner();
    execute p_sql into v;
    execute 'reset role';
    return v;
  end $f$;
  create function pg_temp.push(p_tasks jsonb, p_secret text default null) returns jsonb language plpgsql as $f$
  declare v jsonb;
  begin
    perform pg_temp.as_anon();
    v := mc_api.ingest_tasks_push(coalesce(p_secret, current_setting('verify.tasks')), p_tasks);
    execute 'reset role';
    return v;
  end $f$;
  create function pg_temp.upd(p_code text, p_action text, p_note text default null, p_wait text default null) returns jsonb language plpgsql as $f$
  declare v jsonb;
  begin
    perform pg_temp.as_anon();
    v := mc_api.ingest_tasks_update(current_setting('verify.tasks'), p_code, p_action, p_note, p_wait);
    execute 'reset role';
    return v;
  end $f$;
  create function pg_temp.feed(p_since bigint default null) returns jsonb language plpgsql as $f$
  declare v jsonb;
  begin
    perform pg_temp.as_anon();
    v := mc_api.ingest_tasks_feed(current_setting('verify.tasks'), p_since);
    execute 'reset role';
    return v;
  end $f$;
  create function pg_temp.act(p_code text, p_action text, p_note text default null, p_rv bigint default null) returns jsonb language plpgsql as $f$
  declare v jsonb; v_rv bigint;
  begin
    select row_version into v_rv from mc.tasks where code = p_code;
    perform pg_temp.as_owner();
    v := mc_api.task_act(p_code, coalesce(p_rv, v_rv), p_action, p_note);
    execute 'reset role';
    return v;
  end $f$;
  create function pg_temp.st(p_code text) returns text language sql as $f$
    select status from mc.tasks where code = p_code
  $f$;
  -- A valid task, with top-level fields from p_over.
  create function pg_temp.mk(p_code text, p_over jsonb default '{}'::jsonb) returns jsonb language sql as $f$
    select jsonb_build_object('code', p_code, 'title', 'Task ' || p_code, 'area', 'Database', 'priority', 'soon',
      'due_on', null, 'status', 'open', 'waiting_for', null,
      'content', jsonb_build_object('why', 'Because the test needs a real task to work with.', 'minutes', 5,
        'before', jsonb_build_array('A browser signed in to Supabase'),
        'parts', jsonb_build_array(jsonb_build_object('title', 'Do it', 'steps', jsonb_build_array(
          jsonb_build_object('text', 'Open **Supabase** and click `SQL Editor`.',
            'link', jsonb_build_object('url', 'https://supabase.com/dashboard', 'label', 'Supabase dashboard'),
            'copy', E'select\tmc.rotate_ingest_secret(''tasks'');', 'expect', 'A table with one row appears.'),
          jsonb_build_object('text', 'Click **Run**.')))),
        'done_when', 'The row says true.', 'if_stuck', 'Tap **Ask Claude** on this task.',
        'links', jsonb_build_array(jsonb_build_object('label', 'DEPLOYMENT.md',
          'url', 'https://github.com/jerald23simagala/lucento-mission-control/blob/main/DEPLOYMENT.md#k-tasks'))))
      || p_over
  $f$;
  -- The same with one path inside it replaced.
  create function pg_temp.mkset(p_code text, p_path text[], p_value jsonb) returns jsonb language sql as $f$
    select jsonb_set(pg_temp.mk(p_code), p_path, p_value, true)
  $f$;

  -- ======================================================================== fixtures
  insert into auth.users (id, email, email_confirmed_at, aud, role, raw_user_meta_data) values
    (v_admin, 'ops@lucento-test.invalid', now(), 'authenticated', 'authenticated', '{}'),
    (v_cust, 'customer@lucento-test.invalid', now(), 'authenticated', 'authenticated', '{}');
  insert into mc.admins (user_id, email, display_name, active) values (v_admin, 'ops@lucento-test.invalid', 'Ops', true);
  insert into auth.sessions (id, user_id, aal, created_at) values (s_fresh, v_admin, 'aal2', now());
  insert into auth.mfa_amr_claims (session_id, authentication_method, created_at, updated_at) values
    (s_fresh, 'totp', now() - interval '1 hour', now() - interval '1 hour');
  perform set_config('verify.admin', v_admin::text, true);
  perform set_config('verify.fresh', s_fresh::text, true);

  -- ======================================================================== A: the secret and who may call what
  v_tasks := mc.rotate_ingest_secret('tasks');
  v_mkt := mc.rotate_ingest_secret('marketing');
  perform set_config('verify.tasks', v_tasks, true);
  begin
    perform mc.rotate_ingest_secret('todo');
    v_t := 'accepted';
  exception when sqlstate '22023' then
    v_t := sqlerrm;
  end;
  r := r || case when v_tasks ~ '^[0-9a-f]{64}$' and v_tasks <> v_mkt and v_t like '%autopost or tasks%'
                 then 'ok  T1 a fifth secret purpose, tasks; other names refused'
                 else 'FAIL T1 ' || coalesce(v_t, 'null') end;

  v_j := pg_temp.push(jsonb_build_array(pg_temp.mk('OT-90')), repeat('a', 64));
  v_k := pg_temp.push(jsonb_build_array(pg_temp.mk('OT-90')), v_mkt);
  perform pg_temp.as_anon();
  v_b := jsonb_build_array(mc_api.ingest_tasks_feed(v_mkt, null), mc_api.ingest_tasks_get(v_mkt, 'OT-90'),
                           mc_api.ingest_tasks_update(v_mkt, 'OT-90', 'note', 'hello there', null));
  execute 'reset role';
  r := r || case when v_j ->> 'error' = 'bad_secret' and v_k ->> 'error' = 'bad_secret'
                      and (select bool_and(x ->> 'error' = 'bad_secret') from jsonb_array_elements(v_b) x)
                      and not exists (select 1 from mc.tasks)
                 then 'ok  T2 Claude''s four functions need the tasks secret: a wrong one or the marketing one changes nothing'
                 else 'FAIL T2 ' || v_j::text || v_k::text || v_b::text end;

  -- A customer (signed in, no authenticator, not an admin) is refused by every page function.
  perform pg_temp.as_user(v_cust);
  v_b := jsonb_build_array(mc_api.tasks_board(), mc_api.task_get('OT-90'), mc_api.task_act('OT-90', 1, 'done', null),
                           mc_api.tasks_summary(), mc_api.nav_badges());
  execute 'reset role';
  r := r || case when (select bool_and(x ->> 'ok' = 'false' and x ->> 'error' = 'not_admin') from jsonb_array_elements(v_b) x)
                 then 'ok  T3 a customer is refused by all five page functions (not_admin)'
                 else 'FAIL T3 ' || v_b::text end;

  select count(*) into v_n from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'mc_api' and p.proname in ('tasks_board', 'task_get', 'task_act', 'tasks_summary', 'nav_badges')
      and has_function_privilege('authenticated', p.oid, 'execute') and not has_function_privilege('anon', p.oid, 'execute');
  select count(*) into v_m from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'mc_api' and p.proname like 'ingest\_tasks\_%'
      and has_function_privilege('anon', p.oid, 'execute') and not has_function_privilege('authenticated', p.oid, 'execute');
  r := r || case when v_n = 5 and v_m = 4
                      and not exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
                                      where n.nspname in ('mc', 'mc_api') and (p.proname like 'task%' or p.proname like 'ingest\_tasks%')
                                        and exists (select 1 from aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) a
                                                    where a.grantee = 0 and a.privilege_type = 'EXECUTE'))
                      and not exists (select 1 from pg_class c join pg_namespace n on n.oid = c.relnamespace
                                      where n.nspname = 'mc' and c.relkind = 'r' and c.relname like 'task%'
                                        and (has_table_privilege('anon', c.oid, 'select') or has_table_privilege('authenticated', c.oid, 'select')
                                             or has_table_privilege('service_role', c.oid, 'select')))
                 then 'ok  T4 page functions for signed-in callers only, Claude''s for anon only, nothing public, no table readable'
                 else 'FAIL T4 page ' || v_n || ', ingest ' || v_m end;

  -- ======================================================================== B: pushing tasks
  v_j := pg_temp.push(jsonb_build_array(pg_temp.mk('OT-91'),
           pg_temp.mk('OT-92', jsonb_build_object('status', 'waiting', 'waiting_for', 'The final days before launch',
                                                  'priority', 'later'))));
  r := r || case when v_j ->> 'ok' = 'true'
                      and (select string_agg(x ->> 'code' || ':' || (x ->> 'result') || ':' || (x ->> 'version') || ':' || (x ->> 'status'), ' ' order by x ->> 'code')
                           from jsonb_array_elements(v_j -> 'results') x) = 'OT-91:created:1:open OT-92:created:1:waiting'
                      and (select waiting_for from mc.tasks where code = 'OT-92') = 'The final days before launch'
                      and (select count(*) from mc.task_versions) = 2
                      and (select count(*) from mc.task_events where kind = 'created' and actor = 'claude' and admin_id is null) = 2
                 then 'ok  T5 a push creates tasks (open, or waiting with what it waits for), each with version 1 and a history entry'
                 else 'FAIL T5 ' || v_j::text end;

  select row_version into v_rv from mc.tasks where code = 'OT-91';
  v_j := pg_temp.push(jsonb_build_array(pg_temp.mk('OT-91')));
  r := r || case when v_j #>> '{results,0,result}' = 'unchanged' and (select count(*) from mc.task_versions) = 2
                      and (select count(*) from mc.task_events) = 2 and (select row_version from mc.tasks where code = 'OT-91') = v_rv
                 then 'ok  T6 the same task again changes nothing (no version, no history entry)'
                 else 'FAIL T6 ' || v_j::text end;

  v_j := pg_temp.push(jsonb_build_array(pg_temp.mkset('OT-91', '{content,why}', '"Because the test needs a task that changed."')
                                          || jsonb_build_object('change_note', 'Clearer why', 'status', 'waiting',
                                                                'waiting_for', 'Something')));
  r := r || case when v_j #>> '{results,0,result}' = 'updated' and v_j #>> '{results,0,version}' = '2'
                      and v_j #>> '{results,0,status}' = 'open' and pg_temp.st('OT-91') = 'open'
                      and (select waiting_for from mc.tasks where code = 'OT-91') is null
                      and (select row_version from mc.tasks where code = 'OT-91') = v_rv + 1
                      and (select count(*) from mc.task_versions where task_id = (select id from mc.tasks where code = 'OT-91')) = 2
                      and exists (select 1 from mc.task_events where kind = 'updated' and note = 'Clearer why' and version = 2)
                      and (select content ->> 'why' from mc.tasks where code = 'OT-91') = 'Because the test needs a task that changed.'
                 then 'ok  T7 a changed write-up is version 2 with its change note; a push never changes a status'
                 else 'FAIL T7 ' || v_j::text end;

  v_j := pg_temp.push(jsonb_build_array(pg_temp.mk('OT-93'), pg_temp.mkset('OT-94', '{content,why}', '"short"')));
  r := r || case when v_j ->> 'error' = 'invalid' and v_j #>> '{problems,0,index}' = '2' and v_j #>> '{problems,0,code}' = 'OT-94'
                      and v_j #>> '{problems,0,problem}' like '%why%' and jsonb_array_length(v_j -> 'problems') = 1
                      and not exists (select 1 from mc.tasks where code in ('OT-93', 'OT-94'))
                 then 'ok  T8 all or nothing: one bad task and nothing of the push is saved, the problem named by index and code'
                 else 'FAIL T8 ' || v_j::text end;

  -- Every rule of the write-up (OWNER_TASKS.md §3), one bad task each.
  v_bad := jsonb_build_array(
    pg_temp.mk('ot-1'),
    pg_temp.mk('OT-95', '{"owner": "me"}'),
    pg_temp.mkset('OT-95', '{content,parts,0,steps,0}', '{"expect": "Something shows."}'),
    pg_temp.mkset('OT-95', '{content,parts,0,steps,0,link,url}', '"http://supabase.com/dashboard"'),
    pg_temp.mkset('OT-95', '{content,parts,0,steps,0,link,url}', '"https://localhost/x"'),
    pg_temp.mkset('OT-95', '{content,parts,0,steps,0,link,url}', '"https://user@evil.example.com/"'),
    pg_temp.mkset('OT-95', '{content,parts,0,steps,0,link,url}', '"https://10.0.0.1/admin"'),
    pg_temp.mkset('OT-95', '{content,parts,0,steps,0,html}', '"<b>x</b>"'),
    pg_temp.mkset('OT-95', '{content,parts}', (select jsonb_agg(jsonb_build_object('steps',
      (select jsonb_agg(jsonb_build_object('text', 'Step ' || s)) from generate_series(1, 31) s))) from generate_series(1, 4) p)),
    pg_temp.mkset('OT-95', '{content,parts,0,steps,0,copy}', to_jsonb(repeat('y', 8001))),
    pg_temp.mkset('OT-95', '{content,minutes}', '0'),
    pg_temp.mkset('OT-95', '{content,minutes}', '"abc"'),
    pg_temp.mk('OT-95', '{"status": "waiting"}'),
    pg_temp.mk('OT-95', '{"waiting_for": "Nothing at all"}'),
    pg_temp.mk('OT-95', '{"status": "done"}'),
    pg_temp.mkset('OT-95', '{content,parts,0,steps,0,text}', to_jsonb('Paste ' || repeat('ab12', 16))),
    pg_temp.mkset('OT-95', '{content,why}', '"Use eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.signature here"'),
    pg_temp.mk('OT-95', jsonb_build_object('title', 'Bell' || chr(7) || ' here')),
    pg_temp.mkset('OT-95', '{content,parts}', jsonb_build_array(jsonb_build_object('steps',
      (select jsonb_agg(jsonb_build_object('text', v_long)) from generate_series(1, 40) s)))),
    pg_temp.mk('OT-95', '{"due_on": "2026-02-30"}'),
    pg_temp.mk('OT-95', jsonb_build_object('title', repeat('t', 141))),
    pg_temp.mkset('OT-95', '{content,parts}', '[]'),
    pg_temp.mk('OT-95', '{"priority": "urgent"}'),
    pg_temp.mkset('OT-95', '{content,links,0,url}', '"ftp://example.com/file"'),
    pg_temp.mkset('OT-95', '{content,before}', (select jsonb_agg('Item ' || s) from generate_series(1, 11) s)),
    pg_temp.mkset('OT-95', '{content,why}', '"Paste sk_live_abcdefghijklmnop into the box"'),
    pg_temp.mkset('OT-95', '{content,parts,0,steps,0,text}', to_jsonb('A key: AIza' || repeat('Q', 35))),
    pg_temp.mkset('OT-95', '{content,if_stuck}', to_jsonb(repeat('z', 801))),
    pg_temp.mkset('OT-95', '{content,parts,0,steps,0,text}', '"   "'));
  select count(*) filter (where (pg_temp.push(jsonb_build_array(x)) ->> 'error') = 'invalid'), count(*)
    into v_n, v_m from jsonb_array_elements(v_bad) x;
  v_j := pg_temp.push(jsonb_build_array(pg_temp.mk('OT-96'), pg_temp.mk('OT-96')));
  v_k := pg_temp.push(jsonb_build_array(pg_temp.mkset('OT-95', '{content,parts,0,steps,0,link,url}', '"http://supabase.com/dashboard"')));
  r := r || case when v_n = v_m and v_j #>> '{problems,0,problem}' like '%same code twice%'
                      and v_k #>> '{problems,0,problem}' like '%https%'
                      and not exists (select 1 from mc.tasks where code in ('OT-95', 'OT-96', 'ot-1'))
                 then format('ok  T9 every write-up rule refuses its bad task (%s cases), and a code twice in one push', v_m)
                 else format('FAIL T9 %s of %s refused; %s %s', v_n, v_m, v_j, v_k) end;

  v_j := pg_temp.push('[]'::jsonb);
  v_k := pg_temp.push((select jsonb_agg(pg_temp.mk('OT-' || (100 + s))) from generate_series(1, 41) s));
  r := r || case when v_j ->> 'error' = 'bad_request' and v_k ->> 'error' = 'bad_request'
                 then 'ok  T10 a push holds 1 to 40 tasks'
                 else 'FAIL T10 ' || v_j::text || v_k::text end;

  -- ======================================================================== C: the owner's page and actions
  v_b := pg_temp.owner('select mc_api.tasks_board()');
  r := r || case when v_b ->> 'ok' = 'true'
                      and (select string_agg(x ->> 'code', ',' order by ord) from jsonb_array_elements(v_b -> 'tasks') with ordinality t(x, ord)) = 'OT-91,OT-92'
                      and v_b #>> '{tasks,0,content,parts,0,steps,0,link,url}' = 'https://supabase.com/dashboard'
                      and jsonb_array_length(v_b #> '{tasks,0,events}') = 2
                      and v_b #>> '{tasks,0,events,0,by}' = 'Claude'
                      and v_b ->> 'seen_before' = '0' and v_b ->> 'claude_read_at' is null
                      and jsonb_array_length(v_b -> 'closed') = 0
                 then 'ok  T11 the board lists open before waiting, with write-ups and history, and when Claude last read (never yet)'
                 else 'FAIL T11 ' || left(v_b::text, 400) end;

  select row_version into v_rv from mc.tasks where code = 'OT-91';
  v_j := pg_temp.act('OT-91', 'done', 'It worked', v_rv - 1);
  r := r || case when v_j ->> 'error' = 'stale' and pg_temp.st('OT-91') = 'open'
                      and not exists (select 1 from mc.task_events where actor = 'owner')
                 then 'ok  T12 a task that changed since the page showed it is refused (stale) and stays as it was'
                 else 'FAIL T12 ' || v_j::text end;

  v_j := pg_temp.act('OT-91', 'done', E'It worked\r\nwith two lines');
  r := r || case when v_j ->> 'ok' = 'true' and pg_temp.st('OT-91') = 'done'
                      and exists (select 1 from mc.task_events e where e.actor = 'owner' and e.admin_id = v_admin and e.kind = 'done'
                                  and e.note = E'It worked\nwith two lines' and e.status_before = 'open' and e.status_after = 'done')
                      and exists (select 1 from mc.audit_log a where a.action = 'tasks.done' and a.target_ref = 'OT-91'
                                  and a.admin_id = v_admin and a.result = 'ok' and a.after ->> 'status' = 'done')
                 then 'ok  T13 the owner marks a task done (a note''s new lines kept as LF), recorded and audited'
                 else 'FAIL T13 ' || v_j::text end;

  v_j := pg_temp.act('OT-91', 'done');
  v_k := pg_temp.act('OT-91', 'ask', 'Why?');
  v_b := pg_temp.act('OT-92', 'ask', null);
  r := r || case when v_j ->> 'error' = 'not_allowed' and v_k ->> 'error' = 'not_allowed' and v_b ->> 'error' = 'note_required'
                 then 'ok  T14 a closed task can''t be done again or asked about; a question needs words'
                 else 'FAIL T14 ' || v_j::text || v_k::text || v_b::text end;

  v_j := pg_temp.act('OT-92', 'note', 'Here is the key ' || repeat('0f', 32));
  v_k := pg_temp.act('OT-92', 'note', 'Token eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoic2VydmljZSJ9.abc');
  r := r || case when v_j ->> 'error' = 'looks_like_a_key' and v_k ->> 'error' = 'looks_like_a_key'
                      and v_j ->> 'message' like '%Never paste keys%'
                      and not exists (select 1 from mc.task_events where task_id = (select id from mc.tasks where code = 'OT-92') and actor = 'owner')
                 then 'ok  T15 a note that looks like a key or a token is refused and nothing is saved'
                 else 'FAIL T15 ' || v_j::text || v_k::text end;

  v_j := pg_temp.act('OT-92', 'ask', 'Which project do I open?');
  r := r || case when v_j ->> 'ok' = 'true' and pg_temp.st('OT-92') = 'asked' and (select waiting_for from mc.tasks where code = 'OT-92') is null
                 then 'ok  T16 asking Claude about a waiting task makes it Claude''s turn (asked)'
                 else 'FAIL T16 ' || v_j::text end;

  -- ======================================================================== D: Claude's read and actions
  v_j := pg_temp.feed();
  r := r || case when v_j ->> 'ok' = 'true'
                      and (select string_agg(x ->> 'code' || ':' || (x ->> 'kind'), ' ' order by (x ->> 'id')::bigint)
                           from jsonb_array_elements(v_j -> 'events') x) = 'OT-91:done OT-92:asked'
                      and v_j #>> '{events,0,note}' = E'It worked\nwith two lines' and v_j #>> '{events,0,by}' = 'Ops'
                      and v_j #>> '{questions,0,code}' = 'OT-92' and v_j #>> '{questions,0,question}' = 'Which project do I open?'
                      and v_j #>> '{counts,done}' = '1' and v_j #>> '{counts,asked}' = '1'
                      and jsonb_array_length(v_j -> 'tasks') = 2
                      and (select claude_seen_event_id from mc.tasks_state) = (select max(id) from mc.task_events)
                      and (select claude_read_at from mc.tasks_state) is not null
                 then 'ok  T17 Claude reads the owner''s entries since its position, oldest first, with the open questions; its position moves'
                 else 'FAIL T17 ' || left(v_j::text, 500) end;

  v_j := pg_temp.feed();
  v_k := pg_temp.feed(0);
  v_b := pg_temp.feed(999999);
  r := r || case when jsonb_array_length(v_j -> 'events') = 0 and jsonb_array_length(v_k -> 'events') = 2
                      and jsonb_array_length(v_b -> 'events') = 2 and v_b ->> 'since' = '0'
                 then 'ok  T18 a second read has nothing new; since=0 reads again; a position past the end starts over'
                 else 'FAIL T18 ' || v_j::text end;

  v_j := pg_temp.upd('OT-92', 'answer', 'Open lucento-test, the one with -test in its name.');
  r := r || case when v_j ->> 'ok' = 'true' and pg_temp.st('OT-92') = 'open'
                      and exists (select 1 from mc.task_events where kind = 'answered' and actor = 'claude' and status_after = 'open')
                 then 'ok  T19 Claude''s answer gives the task back to the owner (open)'
                 else 'FAIL T19 ' || v_j::text end;

  v_j := pg_temp.upd('OT-92', 'wait', null, null);
  v_k := pg_temp.upd('OT-92', 'wait', null, 'Launch day');
  v_b := pg_temp.upd('OT-92', 'open');
  r := r || case when v_j ->> 'error' = 'waiting_for_required' and v_k ->> 'ok' = 'true' and v_b ->> 'ok' = 'true'
                      and pg_temp.st('OT-92') = 'open' and (select waiting_for from mc.tasks where code = 'OT-92') is null
                      and exists (select 1 from mc.task_events where kind = 'waiting' and note = 'Launch day')
                      and exists (select 1 from mc.task_events where kind = 'opened' and status_after = 'open')
                 then 'ok  T20 Claude moves a task to Later only with what it waits for, and back'
                 else 'FAIL T20 ' || v_j::text || v_k::text || v_b::text end;

  v_j := pg_temp.upd('OT-92', 'check', 'Looked at the catalog');
  v_k := pg_temp.upd('OT-91', 'check', 'Read-only check: all columns true');
  r := r || case when v_j ->> 'error' = 'not_allowed' and v_k ->> 'ok' = 'true'
                      and (select checked_at from mc.tasks where code = 'OT-91') is not null
                      and v_k #>> '{task,checked_at}' is not null
                 then 'ok  T21 Claude records a check only on a done task'
                 else 'FAIL T21 ' || v_j::text || v_k::text end;

  v_j := pg_temp.upd('OT-91', 'reopen', null);
  v_k := pg_temp.upd('OT-91', 'reopen', 'The check showed one column false');
  r := r || case when v_j ->> 'error' = 'note_required' and v_k ->> 'ok' = 'true' and pg_temp.st('OT-91') = 'open'
                      and (select checked_at from mc.tasks where code = 'OT-91') is null
                 then 'ok  T22 Claude reopens a task only with a reason; the check goes with the done'
                 else 'FAIL T22 ' || v_j::text || v_k::text end;

  v_j := pg_temp.upd('OT-91', 'done', null);
  v_k := pg_temp.upd('OT-91', 'done', 'The owner said so in chat');
  v_b := pg_temp.upd('OT-91', 'drop', 'Not needed');
  r := r || case when v_j ->> 'error' = 'note_required' and v_k ->> 'ok' = 'true' and v_b ->> 'error' = 'not_allowed'
                      and exists (select 1 from mc.task_events where actor = 'claude' and kind = 'done' and note = 'The owner said so in chat')
                 then 'ok  T23 Claude closes a task only with how it knows; a closed task can''t be dropped'
                 else 'FAIL T23 ' || v_j::text || v_k::text || v_b::text end;

  v_j := pg_temp.upd('OT-99', 'note', 'Hello there');
  v_k := pg_temp.upd('OT-92', 'shout', 'Hello there');
  v_b := pg_temp.upd('OT-92', 'note', 'Use re_' || repeat('k', 20));
  r := r || case when v_j ->> 'error' = 'not_found' and v_k ->> 'error' = 'bad_action' and v_b ->> 'error' = 'looks_like_a_key'
                 then 'ok  T24 unknown task, unknown action and a key-like note are refused'
                 else 'FAIL T24 ' || v_j::text || v_k::text || v_b::text end;

  -- ======================================================================== E: drop, reopen, the closed list
  v_j := pg_temp.act('OT-92', 'drop');
  v_k := pg_temp.act('OT-92', 'reopen', 'Still needed after all');
  v_b := pg_temp.act('OT-92', 'reopen');
  r := r || case when v_j ->> 'ok' = 'true' and v_k ->> 'ok' = 'true' and v_b ->> 'error' = 'not_allowed' and pg_temp.st('OT-92') = 'open'
                      and exists (select 1 from mc.audit_log where action = 'tasks.drop' and target_ref = 'OT-92')
                      and exists (select 1 from mc.audit_log where action = 'tasks.reopen' and target_ref = 'OT-92')
                 then 'ok  T25 not needed (no reason required) and reopen work; an open task can''t be reopened'
                 else 'FAIL T25 ' || v_j::text || v_k::text || v_b::text end;

  perform pg_temp.act('OT-92', 'note', 'I will do it on Saturday');
  r := r || case when pg_temp.st('OT-92') = 'open'
                      and exists (select 1 from mc.task_events where kind = 'note' and actor = 'owner' and note = 'I will do it on Saturday')
                 then 'ok  T26 a note never changes the state'
                 else 'FAIL T26 ' || pg_temp.st('OT-92') end;

  v_b := pg_temp.owner('select mc_api.tasks_board()');
  r := r || case when jsonb_array_length(v_b -> 'closed') = 1 and v_b #>> '{closed,0,code}' = 'OT-91'
                      and v_b #> '{closed,0,content}' is null and v_b ->> 'closed_count' = '1'
                      and (v_b ->> 'seen_before')::bigint > 0 and v_b ->> 'claude_read_at' is not null
                 then 'ok  T27 closed tasks are listed without their write-ups; the board shows when Claude read'
                 else 'FAIL T27 ' || left(v_b::text, 400) end;

  v_j := pg_temp.owner('select mc_api.task_get(''ot-91'')');
  perform pg_temp.as_anon();
  v_k := mc_api.ingest_tasks_get(v_tasks, 'OT-91');
  execute 'reset role';
  r := r || case when v_j #>> '{task,content,why}' = 'Because the test needs a task that changed.'
                      and jsonb_array_length(v_j #> '{task,events}') >= 6
                      and jsonb_array_length(v_k -> 'versions') = 2 and v_k #>> '{versions,1,change_note}' = 'Clearer why'
                 then 'ok  T28 one task opens with its write-up and whole history; Claude''s copy lists the versions'
                 else 'FAIL T28 ' || left(v_j::text, 300) || left(v_k::text, 300) end;

  -- ======================================================================== F: Overview, the badge, the hourly limit
  perform pg_temp.push(jsonb_build_array(pg_temp.mk('OT-97', jsonb_build_object('due_on', v_today + 1, 'priority', 'now')),
                                         pg_temp.mk('OT-98', jsonb_build_object('due_on', v_today + 5)),
                                         pg_temp.mk('OT-89', jsonb_build_object('due_on', v_today - 1))));
  perform pg_temp.act('OT-92', 'ask', 'And then?');
  perform pg_temp.upd('OT-92', 'answer', 'Then click Run.');
  v_j := pg_temp.owner('select mc_api.tasks_summary()');
  r := r || case when v_j ->> 'ok' = 'true'
                      and (select string_agg(x ->> 'code', ',' order by ord) from jsonb_array_elements(v_j -> 'due') with ordinality t(x, ord)) = 'OT-89,OT-97'
                      and (select string_agg(x ->> 'code', ',' order by x ->> 'code') from jsonb_array_elements(v_j -> 'added') x) = 'OT-89,OT-97,OT-98'
                      and v_j #>> '{answered,0,code}' = 'OT-92' and v_j ->> 'open' = '4'
                 then 'ok  T29 Overview gets what is overdue or due within two days, and what Claude added or answered since the board was opened'
                 else 'FAIL T29 ' || v_j::text end;

  perform pg_temp.owner('select mc_api.tasks_board()');
  v_j := pg_temp.owner('select mc_api.tasks_summary()');
  v_k := pg_temp.owner('select mc_api.nav_badges()');
  r := r || case when jsonb_array_length(v_j -> 'added') = 0 and jsonb_array_length(v_j -> 'answered') = 0
                      and v_k ->> 'tasks' = '4' and v_k ? 'marketing' and v_k ? 'open_tickets' and v_k ? 'services_attention'
                 then 'ok  T30 opening the board clears those; the badge counts the open tasks beside the earlier badges'
                 else 'FAIL T30 ' || v_j::text || v_k::text end;

  insert into mc.rate_counters (admin_id, action, hour, count) values (v_admin, 'tasks.act', date_trunc('hour', now()), 300)
    on conflict (admin_id, action, hour) do update set count = 300;
  v_j := pg_temp.act('OT-97', 'done');
  r := r || case when v_j ->> 'error' = 'rate_limited' and pg_temp.st('OT-97') = 'open'
                 then 'ok  T31 the owner''s actions stop at 300 an hour'
                 else 'FAIL T31 ' || v_j::text end;

  -- ======================================================================== G: history that can't change
  v_ok := true;
  begin
    update mc.task_events set note = 'changed' where id = (select min(id) from mc.task_events);
    v_ok := false;
  exception when sqlstate '42501' then null;
  end;
  begin
    delete from mc.task_events where id = (select min(id) from mc.task_events);
    v_ok := false;
  exception when sqlstate '42501' then null;
  end;
  begin
    update mc.task_versions set title = 'changed';
    v_ok := false;
  exception when sqlstate '42501' then null;
  end;
  begin
    truncate mc.task_events;
    v_ok := false;
  exception when sqlstate '42501' then null;
  end;
  r := r || case when v_ok then 'ok  T32 history and versions refuse update, delete and truncate, even for their owner'
                 else 'FAIL T32 history could be changed' end;

  raise exception E'VERIFY %/% failed\n%',
    (select count(*) from unnest(r) x where x like 'FAIL%'), array_length(r, 1), array_to_string(r, E'\n');
end
$verify$;
