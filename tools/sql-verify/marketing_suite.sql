-- Mission Control: Marketing approvals (supabase/migrations/20261117090000_mission_control_marketing.sql,
-- docs/mission-control/v1/MARKETING_APPROVALS.md §3–§5, §8 and §10). Run with tools/sql-verify (see README.md):
--   node run.mjs ../../supabase/migrations marketing_suite.sql
-- Plays the owner (an admin with a fresh authenticator code, and the same admin with an hour-old one), a customer, anon,
-- Claude's command-line tool (anon + the marketing secret) and the console's upload check (anon + the app secret)
-- against every rule: who may call what, pushes and their refusals, files and their checks, every decision, the
-- derived flags around launch, Claude's feedback cursor, notes, redaction, the append-only log, numbers, the badge,
-- the audit and retention. Time-dependent rules are tested by placing slots around now() (one transaction: now() never
-- moves) and by moving stored timestamps. Raises one exception listing every check; "VERIFY 0/N failed" is a pass.
do $verify$
declare
  r text[] := '{}';
  v_admin uuid := gen_random_uuid();
  v_cust uuid := gen_random_uuid();
  s_fresh uuid := gen_random_uuid();
  s_stale uuid := gen_random_uuid();
  v_today date := (now() at time zone 'Asia/Manila')::date;
  v_p1_start date;
  v_mkt text; v_app text; v_bak text;
  v_p1 jsonb; v_p2 jsonb; v_cards jsonb; v_j jsonb; v_k jsonb; v_m jsonb; v_b jsonb;
  v_t text; v_n integer; v_x integer; v_y integer; v_ok boolean; v_id bigint; v_id2 bigint; v_db text; v_cursor bigint;
  v_w1 uuid; v_post uuid; v_text uuid; v_edge uuid; v_fid uuid; v_file_a uuid;
  v_path_a text; v_path_b text; v_path_c text; v_path_b2 text; v_path_d text; v_path_e text;
  v_pday_local timestamp; v_pday_day date; v_pday_text text; v_soon timestamp;
  v_hash bigint := hashtextextended('mc.marketing', 0);
  v_fn record; v_sql text;
  v_g uuid; v_g2 uuid; v_paths text[]; v_q jsonb;
begin
  -- ======================================================================== helpers
  create function pg_temp.as_admin(p_uid uuid, p_session uuid, p_aal text, p_totp_age interval) returns void language plpgsql as $f$
  begin
    perform set_config('request.jwt.claims', json_build_object('sub', p_uid, 'role', 'authenticated', 'aal', p_aal,
      'session_id', p_session, 'amr', json_build_array(
        json_build_object('method', 'totp', 'timestamp', floor(extract(epoch from now() - p_totp_age))::bigint),
        json_build_object('method', 'password', 'timestamp', floor(extract(epoch from now() - interval '2 hours'))::bigint)))::text, true);
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
  -- The admin, signed in with a fresh code (or with the session given).
  create function pg_temp.as_owner(p_session uuid default null) returns void language plpgsql as $f$
  begin
    perform pg_temp.as_admin(current_setting('verify.admin')::uuid, coalesce(p_session, current_setting('verify.fresh')::uuid),
      'aal2', interval '1 hour');
  end $f$;
  -- A card as Claude's tool sends it (MARKETING_APPROVALS.md §5.3), with overrides.
  create function pg_temp.mk_card(p_key text, p_day date, p_over jsonb default '{}'::jsonb) returns jsonb language sql as $f$
    select jsonb_build_object('key', p_key, 'position', 10, 'kind', 'short', 'phase', 'any', 'title', 'Sweldo day, sorted',
      'slot_day', p_day, 'slot_time', '19:30', 'slot_label', 'Slot', 'platforms', jsonb_build_array('TikTok', 'FB + IG Reels', 'YouTube Shorts'),
      'version', 1, 'changes', null, 'revive', false, 'withdrawn', false, 'withdrawn_reason', null,
      'caption_label', 'Caption (TikTok and Reels)', 'caption', 'Sweldo day? Sorted.', 'yt_title', 'Sweldo day',
      'yt_desc', 'Track it with Lucento.', 'alt', null, 'truth', 'pass. T1 (not called AI), T3', 'media', '[]'::jsonb) || p_over
  $f$;
  create function pg_temp.mk_media(p_file text, p_type text, p_bytes bigint, p_seed text) returns jsonb language sql as $f$
    select jsonb_build_object('file', p_file, 'content_type', p_type, 'bytes', p_bytes,
      'sha256', encode(sha256(convert_to(p_seed, 'UTF8')), 'hex'), 'width', 1080,
      'height', case when p_type = 'video/mp4' then 1920 else 1350 end,
      'duration_ms', case when p_type = 'video/mp4' then 12700 end)
  $f$;
  create function pg_temp.mk_pack(p_key text, p_starts date, p_cards jsonb, p_extra jsonb default '{}'::jsonb) returns jsonb language sql as $f$
    select jsonb_build_object('pack', jsonb_build_object('key', p_key, 'title', 'Week ' || p_key, 'starts_on', p_starts,
      'ends_on', p_starts + 6), 'cards', p_cards) || p_extra
  $f$;
  -- Claude's tool and the console's routes: anon with their secrets.
  create function pg_temp.push(p_pack jsonb, p_env text default 'test') returns jsonb language plpgsql as $f$
  declare v jsonb;
  begin
    perform pg_temp.as_anon();
    v := mc_api.ingest_marketing_pack(current_setting('verify.mkt'), p_env, p_pack);
    execute 'reset role';
    return v;
  end $f$;
  create function pg_temp.pending(p_paths text[]) returns jsonb language plpgsql as $f$
  declare v jsonb;
  begin
    perform pg_temp.as_anon();
    v := mc_api.ingest_marketing_pending(current_setting('verify.mkt'), p_paths);
    execute 'reset role';
    return v;
  end $f$;
  create function pg_temp.ready(p_items jsonb) returns jsonb language plpgsql as $f$
  declare v jsonb;
  begin
    perform pg_temp.as_anon();
    v := mc_api.ingest_marketing_media_ready(current_setting('verify.app'), p_items);
    execute 'reset role';
    return v;
  end $f$;
  create function pg_temp.ready_one(p_path text, p_bytes bigint, p_type text) returns text language sql as $f$
    select pg_temp.ready(jsonb_build_array(jsonb_build_object('pathname', p_path, 'bytes', p_bytes, 'content_type', p_type,
      'etag', '"etag-1"', 'expires_at', now() + interval '30 days'))) #>> '{results,0,result}'
  $f$;
  create function pg_temp.feedback(p_db text, p_since bigint) returns jsonb language plpgsql as $f$
  declare v jsonb;
  begin
    perform pg_temp.as_anon();
    v := mc_api.ingest_marketing_feedback(current_setting('verify.mkt'), p_db, p_since);
    execute 'reset role';
    return v;
  end $f$;
  -- The owner's calls on the Marketing page.
  create function pg_temp.decide(p_card uuid, p_version integer, p_decision text, p_note text default null, p_tags text[] default null)
  returns jsonb language plpgsql as $f$
  declare v jsonb;
  begin
    perform pg_temp.as_owner();
    v := mc_api.marketing_decide(p_card, p_version, p_decision, p_note, p_tags);
    execute 'reset role';
    return v;
  end $f$;
  create function pg_temp.apps(p_card uuid, p_version integer, p_action text, p_by_hand text[] default null)
  returns jsonb language plpgsql as $f$
  declare v jsonb;
  begin
    perform pg_temp.as_owner();
    v := mc_api.marketing_apps(p_card, p_version, p_action, p_by_hand);
    execute 'reset role';
    return v;
  end $f$;
  create function pg_temp.board(p_pack text default null) returns jsonb language plpgsql as $f$
  declare v jsonb;
  begin
    perform pg_temp.as_owner();
    v := mc_api.marketing_board(p_pack);
    execute 'reset role';
    return v;
  end $f$;
  create function pg_temp.summary() returns jsonb language plpgsql as $f$
  declare v jsonb;
  begin
    perform pg_temp.as_owner();
    v := mc_api.marketing_summary();
    execute 'reset role';
    return v;
  end $f$;
  create function pg_temp.badge() returns integer language plpgsql as $f$
  declare v jsonb;
  begin
    perform pg_temp.as_owner();
    v := mc_api.nav_badges();
    execute 'reset role';
    return (v ->> 'marketing')::integer;
  end $f$;
  create function pg_temp.launch(p_local text, p_session uuid default null) returns jsonb language plpgsql as $f$
  declare v jsonb;
  begin
    perform pg_temp.as_owner(p_session);
    v := mc_api.marketing_set_launch(p_local);
    execute 'reset role';
    return v;
  end $f$;
  create function pg_temp.preview(p_local text) returns jsonb language plpgsql as $f$
  declare v jsonb;
  begin
    perform pg_temp.as_owner();
    v := mc_api.marketing_launch_preview(p_local);
    execute 'reset role';
    return v;
  end $f$;
  create function pg_temp.numbers(p_pack text, p_followers jsonb, p_views jsonb, p_installs integer default null,
                                  p_visitors integer default null) returns jsonb language plpgsql as $f$
  declare v jsonb;
  begin
    perform pg_temp.as_owner();
    v := mc_api.marketing_save_numbers(p_pack, p_followers, p_views, p_installs, p_visitors);
    execute 'reset role';
    return v;
  end $f$;
  create function pg_temp.redact(p_log bigint, p_reason text, p_session uuid default null) returns jsonb language plpgsql as $f$
  declare v jsonb;
  begin
    perform pg_temp.as_owner(p_session);
    v := mc_api.marketing_redact_note(p_log, p_reason);
    execute 'reset role';
    return v;
  end $f$;
  create function pg_temp.media_deleted(p_paths text[]) returns jsonb language plpgsql as $f$
  declare v jsonb;
  begin
    perform pg_temp.as_owner();
    v := mc_api.marketing_media_deleted(p_paths);
    execute 'reset role';
    return v;
  end $f$;
  create function pg_temp.file(p_file uuid) returns jsonb language plpgsql as $f$
  declare v jsonb;
  begin
    perform pg_temp.as_owner();
    v := mc_api.marketing_file(p_file);
    execute 'reset role';
    return v;
  end $f$;
  -- Lookups.
  create function pg_temp.cid(p_pack text, p_key text) returns uuid language sql as $f$
    select c.id from mc.marketing_cards c join mc.marketing_packs p on p.id = c.pack_id where p.key = p_pack and c.key = p_key
  $f$;
  create function pg_temp.res(p_push jsonb, p_key text) returns jsonb language sql as $f$
    select x from jsonb_array_elements(p_push -> 'cards') x where x ->> 'key' = p_key limit 1
  $f$;
  create function pg_temp.cardj(p_board jsonb, p_key text) returns jsonb language sql as $f$
    select x from jsonb_array_elements(p_board -> 'cards') x where x ->> 'key' = p_key
  $f$;
  create function pg_temp.has(p_list jsonb, p_key text) returns boolean language sql as $f$
    select exists (select 1 from jsonb_array_elements(coalesce(p_list, '[]'::jsonb)) x where x ->> 'card' = p_key)
  $f$;
  create function pg_temp.reasons(p_push jsonb) returns jsonb language sql as $f$
    select coalesce(jsonb_object_agg(x ->> 'key', coalesce(x ->> 'reason', x ->> 'result')), '{}'::jsonb)
    from jsonb_array_elements(p_push -> 'cards') x
  $f$;

  -- ======================================================================== fixtures
  insert into auth.users (id, email, email_confirmed_at, aud, role, raw_user_meta_data) values
    (v_admin, 'ops@lucento-test.invalid', now(), 'authenticated', 'authenticated', '{}'),
    (v_cust, 'customer@lucento-test.invalid', now(), 'authenticated', 'authenticated', '{}');
  insert into mc.admins (user_id, email, display_name, active) values (v_admin, 'ops@lucento-test.invalid', 'Ops', true);
  insert into auth.sessions (id, user_id, aal, created_at) values (s_fresh, v_admin, 'aal2', now()), (s_stale, v_admin, 'aal2', now());
  insert into auth.mfa_amr_claims (session_id, authentication_method, created_at, updated_at) values
    (s_fresh, 'totp', now(), now()), (s_stale, 'totp', now() - interval '1 hour', now() - interval '1 hour');
  perform set_config('verify.admin', v_admin::text, true);
  perform set_config('verify.fresh', s_fresh::text, true);

  -- ======================================================================== A: the secret and who may call what
  v_mkt := mc.rotate_ingest_secret('marketing');
  v_app := mc.rotate_ingest_secret('app');
  v_bak := mc.rotate_ingest_secret('backup');
  perform set_config('verify.mkt', v_mkt, true);
  perform set_config('verify.app', v_app, true);
  begin
    perform mc.rotate_ingest_secret('newsletter');
    v_t := 'accepted';
  exception when sqlstate '22023' then
    v_t := sqlerrm;
  end;
  r := r || case when v_mkt ~ '^[0-9a-f]{64}$' and v_mkt <> v_app
                      and exists (select 1 from mc.ingest_secret where purpose = 'marketing')
                      and v_t like '%purpose is app, backup or marketing%'
                 then 'ok  M1 a third secret purpose, marketing, separate from app and backup; anything else refused'
                 else 'FAIL M1 ' || coalesce(v_t, 'null') end;

  -- Every Marketing page function refuses a customer first, whatever the input (as K17 for the whole schema).
  v_t := null;
  v_n := 0;
  for v_fn in select p.proname, p.pronargs from pg_proc p join pg_namespace s on s.oid = p.pronamespace
           where s.nspname = 'mc_api' and (p.proname like 'marketing\_%' or p.proname = 'nav_badges') order by p.proname
  loop
    v_sql := format('select mc_api.%I(%s)', v_fn.proname, array_to_string(array_fill('null'::text, array[v_fn.pronargs]), ', '));
    perform pg_temp.as_user(v_cust);
    execute v_sql into v_j;
    execute 'reset role';
    v_n := v_n + 1;
    if v_j ->> 'error' is distinct from 'not_admin' then
      v_t := coalesce(v_t || ', ', '') || v_fn.proname || '=' || coalesce(v_j ->> 'error', v_j::text);
    end if;
  end loop;
  r := r || case when v_t is null and v_n = 11 then 'ok  M2 all 11 Marketing page functions (and the badges) refuse a customer first: not_admin'
                 else 'FAIL M2 functions=' || v_n || ' ' || coalesce(v_t, '') end;

  v_t := null;
  for v_fn in select p.proname, p.pronargs from pg_proc p join pg_namespace s on s.oid = p.pronamespace
           where s.nspname = 'mc_api' and (p.proname like 'marketing\_%' or p.proname = 'nav_badges') order by p.proname
  loop
    v_sql := format('select mc_api.%I(%s)', v_fn.proname, array_to_string(array_fill('null'::text, array[v_fn.pronargs]), ', '));
    perform pg_temp.as_anon();
    begin
      execute v_sql into v_j;
      v_t := coalesce(v_t || ', ', '') || v_fn.proname;
    exception when insufficient_privilege then
      null;
    end;
    execute 'reset role';
  end loop;
  r := r || case when v_t is null then 'ok  M3 anon can''t call any Marketing page function'
                 else 'FAIL M3 anon called ' || v_t end;

  v_t := null;
  for v_fn in select p.proname, p.pronargs from pg_proc p join pg_namespace s on s.oid = p.pronamespace
           where s.nspname = 'mc_api' and p.proname like 'ingest\_marketing\_%' order by p.proname
  loop
    v_sql := format('select mc_api.%I(%s)', v_fn.proname, array_to_string(array_fill('null'::text, array[v_fn.pronargs]), ', '));
    perform pg_temp.as_owner();
    begin
      execute v_sql into v_j;
      v_t := coalesce(v_t || ', ', '') || v_fn.proname;
    exception when insufficient_privilege then
      null;
    end;
    execute 'reset role';
  end loop;
  r := r || case when v_t is null then 'ok  M4 signed-in callers, the admin too, can''t call Claude''s or the console''s ingest functions'
                 else 'FAIL M4 signed in called ' || v_t end;

  v_p1_start := v_today - 2;
  v_p1 := pg_temp.mk_pack('2026-W48', v_p1_start, jsonb_build_array(pg_temp.mk_card('W48-1-sweldo', v_today + 1)));
  perform pg_temp.as_anon();
  v_j := mc_api.ingest_marketing_pack(repeat('0', 64), 'test', v_p1);
  v_k := mc_api.ingest_marketing_pack(v_app, 'test', v_p1);
  v_m := mc_api.ingest_marketing_pending(v_app, array['marketing/test/x']);
  v_b := mc_api.ingest_marketing_feedback(v_bak, null, 0);
  v_t := (mc_api.ingest_marketing_media_ready(v_mkt, '[]'::jsonb)) ->> 'error';
  execute 'reset role';
  r := r || case when v_j ->> 'error' = 'bad_secret' and v_k ->> 'error' = 'bad_secret' and v_m ->> 'error' = 'bad_secret'
                      and v_b ->> 'error' = 'bad_secret' and v_t = 'bad_secret' and not exists (select 1 from mc.marketing_packs)
                      and not exists (select 1 from mc.rate_counters where action = 'ingest.marketing_pack')
                 then 'ok  M5 a wrong secret, the app''s or the backup''s is refused by Claude''s functions; the marketing secret can''t mark files ready'
                 else 'FAIL M5 ' || coalesce(v_j::text, '') || ' ' || coalesce(v_k::text, '') || ' ' || coalesce(v_t, '') end;
  select count(*) into v_n from mc.audit_log where action like 'ingest.marketing%' and result = 'refused' and detail = 'bad_secret';
  r := r || case when v_n between 1 and 3 then 'ok  M6 refused secrets are recorded, at most 3 a day'
                 else 'FAIL M6 refusal rows=' || v_n end;

  -- ======================================================================== B: pushes
  v_cards := jsonb_build_array(
    pg_temp.mk_card('W48-1-sweldo', v_today + 1, jsonb_build_object(
      'caption', E'  Sweldo day?\r\nSorted in 13 s, ₱0, 😀 caf' || U&'e\0301' || E'  \r\n',
      'media', jsonb_build_array(pg_temp.mk_media('W48-1-sweldo.mp4', 'video/mp4', 1000, 'video-a')))),
    pg_temp.mk_card('W48-post-tue', v_today + 2, jsonb_build_object('kind', 'post', 'slot_time', '10:00', 'position', 20,
      'platforms', jsonb_build_array('Lucento Page'),
      'media', jsonb_build_array(pg_temp.mk_media('post-1.png', 'image/png', 2000, 'img-b'),
                                 pg_temp.mk_media('post-2.png', 'image/png', 3000, 'img-c')))),
    pg_temp.mk_card('W48-group-wed', v_today + 3, jsonb_build_object('kind', 'group', 'slot_time', null, 'title', 'Group question',
      'platforms', jsonb_build_array('Community group'))),
    pg_temp.mk_card('W48-edge', v_p1_start - 7, '{}'),
    pg_temp.mk_card('W48-fail', v_today, jsonb_build_object('truth', 'fail. T16: says coming soon after launch')),
    pg_temp.mk_card('W48-but', v_today, jsonb_build_object('truth', 'pass. T1, but T3 is a stretch')),
    pg_temp.mk_card('W48-nophase', v_today) - 'phase',
    pg_temp.mk_card('W48-far', v_p1_start - 8, '{}'),
    pg_temp.mk_card('W48-v2', v_today, jsonb_build_object('version', 2, 'changes', 'Second take')),
    pg_temp.mk_card('W48-many', v_today, jsonb_build_object('media',
      (select jsonb_agg(pg_temp.mk_media('m' || g || '.png', 'image/png', 100, 'many-' || g)) from generate_series(1, 11) g))),
    pg_temp.mk_card('W48-kind', v_today, jsonb_build_object('kind', 'reel')),
    pg_temp.mk_card('W48-contact', v_today, jsonb_build_object('changes', 'Asked juan@example.com for the photo')));
  v_p1 := pg_temp.mk_pack('2026-W48', v_p1_start, v_cards, jsonb_build_object(
    'launch', jsonb_build_object('closed_test_start', v_today - 2, 'closed_test_days', 16, 'apply_on', v_today + 14,
                                 'pday_label', 'Expected 1–8 Dec'),
    'notes', jsonb_build_array(
      jsonb_build_object('card', 'W48-1-sweldo', 'text', 'Kept ''ipon'': T10 forbids the other wording.'),
      jsonb_build_object('card', 'W48-nope', 'text', 'For a post that isn''t here'),
      jsonb_build_object('card', 'W48-post-tue', 'text', 'Call 0917 123 4567 about the shoot'))));
  v_j := pg_temp.push(v_p1);
  r := r || case when (v_j ->> 'ok')::boolean and v_j ->> 'pack' = '2026-W48' and v_j ->> 'environment' = 'test'
                      and pg_temp.reasons(v_j) = jsonb_build_object('W48-1-sweldo', 'created', 'W48-post-tue', 'created',
                            'W48-group-wed', 'created', 'W48-edge', 'created', 'W48-fail', 'truth_failed', 'W48-but', 'truth_failed',
                            'W48-nophase', 'phase_required', 'W48-far', 'slot_outside_pack', 'W48-v2', 'version_out_of_order',
                            'W48-many', 'quota', 'W48-kind', 'invalid', 'W48-contact', 'note_has_contact')
                      and pg_temp.res(v_j, 'W48-1-sweldo') ->> 'state' = 'waiting'
                      and (pg_temp.res(v_j, 'W48-1-sweldo') ->> 'version')::integer = 1
                 then 'ok  M7 a push creates its posts; a failing truth line ("fail", "but"), a missing phase, a slot 8 days outside the pack, version 2 of a new post, 11 files, an unknown kind and contact details are refused per card'
                 else 'FAIL M7 ' || left(coalesce(pg_temp.reasons(v_j)::text, v_j::text), 900) end;
  select count(*) into v_n from mc.marketing_cards;
  select count(*) into v_x from mc.marketing_files;
  r := r || case when v_n = 4 and v_x = 3 and (select count(*) from mc.marketing_versions) = 4
                      and (select count(*) from mc.marketing_media) = 3
                      and (select count(*) from mc.marketing_log where kind = 'created' and actor = 'claude') = 4
                      and not exists (select 1 from mc.marketing_cards where key in ('W48-fail', 'W48-many', 'W48-far'))
                      and not exists (select 1 from mc.marketing_files f join mc.marketing_cards c on c.id = f.card_id where c.key = 'W48-many')
                 then 'ok  M8 a refused card writes nothing (each card in its own savepoint): 4 posts, 4 versions, 3 files'
                 else 'FAIL M8 cards=' || v_n || ' files=' || v_x end;
  select u ->> 'pathname' into v_path_a from jsonb_array_elements(v_j -> 'uploads') u where u ->> 'file' = 'W48-1-sweldo.mp4';
  select u ->> 'pathname' into v_path_b from jsonb_array_elements(v_j -> 'uploads') u where u ->> 'file' = 'post-1.png';
  select u ->> 'pathname' into v_path_c from jsonb_array_elements(v_j -> 'uploads') u where u ->> 'file' = 'post-2.png';
  select count(*) into v_n from jsonb_array_elements(v_j -> 'uploads') u
    where split_part(u ->> 'pathname', '/', 5) ~ '^[0-9a-f]{12}$'
      and u ->> 'pathname' = 'marketing/test/2026-W48/' || (u ->> 'card') || '/' || split_part(u ->> 'pathname', '/', 5) || '/' || (u ->> 'file')
      and (u ->> 'version')::integer = 1 and (u ->> 'bytes')::bigint > 0 and u ->> 'content_type' like '%/%';
  r := r || case when jsonb_array_length(v_j -> 'uploads') = 3 and v_n = 3
                      and (select u ->> 'position' from jsonb_array_elements(v_j -> 'uploads') u where u ->> 'file' = 'post-2.png') = '1'
                 then 'ok  M9 the answer lists each file to upload: marketing/test/<pack>/<card>/<12 hex>/<file>, its version, position, type and size'
                 else 'FAIL M9 ' || left((v_j -> 'uploads')::text, 600) end;
  r := r || case when v_j -> 'notes' = '{"added": 1, "already": 0, "skipped": 2}'::jsonb
                      and (select count(*) from mc.marketing_log where kind = 'claude_note') = 1
                      and (select l.closed_test_days = 16 and l.apply_on = v_today + 14 and l.pday_label = 'Expected 1–8 Dec'
                           from mc.marketing_launch l)
                      and (select count(*) from mc.marketing_log where kind = 'launch_meta' and card_id is null and actor = 'claude') = 1
                 then 'ok  M10 Claude''s notes are stored (one for a post that isn''t here and one with a phone number skipped); the launch details are kept and logged'
                 else 'FAIL M10 notes=' || coalesce((v_j -> 'notes')::text, 'null') end;
  r := r || case when (select v.caption from mc.marketing_versions v where v.card_id = pg_temp.cid('2026-W48', 'W48-1-sweldo'))
                      = E'Sweldo day?\nSorted in 13 s, ₱0, 😀 caf' || U&'\00E9'
                      and (select c.slot_time is null and c.slot_label = 'Slot' from mc.marketing_cards c where c.key = 'W48-group-wed')
                 then 'ok  M11 text is stored as Unicode NFC with \n line breaks and no surrounding white space; no time means any time'
                 else 'FAIL M11 ' || coalesce((select v.caption from mc.marketing_versions v
                                               where v.card_id = pg_temp.cid('2026-W48', 'W48-1-sweldo')), 'null') end;
  v_w1 := pg_temp.cid('2026-W48', 'W48-1-sweldo');
  v_post := pg_temp.cid('2026-W48', 'W48-post-tue');
  v_text := pg_temp.cid('2026-W48', 'W48-group-wed');
  v_edge := pg_temp.cid('2026-W48', 'W48-edge');

  -- The same push again: nothing new, and the files still to upload are listed again (re-running resumes).
  v_k := pg_temp.push(v_p1);
  r := r || case when pg_temp.res(v_k, 'W48-1-sweldo') ->> 'result' = 'unchanged' and pg_temp.res(v_k, 'W48-post-tue') ->> 'result' = 'unchanged'
                      and pg_temp.res(v_k, 'W48-group-wed') ->> 'result' = 'unchanged' and pg_temp.res(v_k, 'W48-edge') ->> 'result' = 'unchanged'
                      and pg_temp.res(v_k, 'W48-fail') ->> 'reason' = 'truth_failed'
                      and (select jsonb_agg(u ->> 'pathname' order by u ->> 'pathname') from jsonb_array_elements(v_k -> 'uploads') u)
                          = (select jsonb_agg(u ->> 'pathname' order by u ->> 'pathname') from jsonb_array_elements(v_j -> 'uploads') u)
                      and v_k -> 'notes' = '{"added": 0, "already": 1, "skipped": 2}'::jsonb
                      and (select count(*) from mc.marketing_versions) = 4 and (select count(*) from mc.marketing_files) = 3
                      and (select count(*) from mc.marketing_log where kind = 'launch_meta') = 1
                 then 'ok  M12 the same push again: unchanged, the same pathnames to upload, notes already there, no second launch entry'
                 else 'FAIL M12 ' || left(coalesce(pg_temp.reasons(v_k)::text, v_k::text), 600) end;

  v_j := pg_temp.push(pg_temp.mk_pack('2026-W48', v_p1_start, jsonb_build_array(
    pg_temp.mk_card('W48-1-sweldo', v_today + 1, jsonb_build_object('caption', 'A different caption',
      'media', jsonb_build_array(pg_temp.mk_media('W48-1-sweldo.mp4', 'video/mp4', 1000, 'video-a')))))));
  r := r || case when pg_temp.res(v_j, 'W48-1-sweldo') ->> 'reason' = 'version_changed'
                      and pg_temp.res(v_j, 'W48-1-sweldo') ->> 'state' = 'waiting'
                      and (select v.caption from mc.marketing_versions v where v.card_id = v_w1) like 'Sweldo day?%'
                 then 'ok  M13 the same version with other content is refused (version_changed); the stored version stays'
                 else 'FAIL M13 ' || coalesce(pg_temp.res(v_j, 'W48-1-sweldo')::text, v_j::text) end;
  v_j := pg_temp.push(pg_temp.mk_pack('2026-W48', v_p1_start, jsonb_build_array(
    pg_temp.mk_card('W48-1-sweldo', v_today + 1, jsonb_build_object('version', 3, 'changes', 'Skipped a version')),
    pg_temp.mk_card('W48-post-tue', v_today + 2, jsonb_build_object('version', 2, 'changes', null)))));
  r := r || case when pg_temp.res(v_j, 'W48-1-sweldo') ->> 'reason' = 'version_out_of_order'
                      and pg_temp.res(v_j, 'W48-post-tue') ->> 'reason' = 'version_out_of_order'
                      and (select max(current_version) from mc.marketing_cards) = 1
                 then 'ok  M14 a version that skips one, or a next version without "changes", is refused (version_out_of_order)'
                 else 'FAIL M14 ' || left(pg_temp.reasons(v_j)::text, 300) end;

  -- ======================================================================== C: uploads and the console's check
  v_j := pg_temp.pending(array[v_path_a, v_path_b, v_path_c, 'marketing/test/2026-W48/x/000000000000/x.mp4']);
  r := r || case when (v_j ->> 'ok')::boolean and jsonb_array_length(v_j -> 'pending') = 3
                      and (select (x ->> 'bytes')::bigint = 1000 and x ->> 'content_type' = 'video/mp4' and (x ->> 'ttl_days')::integer = 30
                                  and x ->> 'pack' = '2026-W48' and x ->> 'card' = 'W48-1-sweldo'
                           from jsonb_array_elements(v_j -> 'pending') x where x ->> 'pathname' = v_path_a)
                 then 'ok  M15 pending: the subset still waiting for its upload, with type, size and the store''s days (30 at least)'
                 else 'FAIL M15 ' || left(v_j::text, 500) end;
  v_j := pg_temp.ready(jsonb_build_array(
    jsonb_build_object('pathname', v_path_a, 'bytes', 1000, 'content_type', 'video/mp4', 'etag', '"a1"', 'expires_at', now() + interval '30 days'),
    jsonb_build_object('pathname', v_path_b, 'bytes', 2001, 'content_type', 'image/png'),
    jsonb_build_object('pathname', 'marketing/test/2026-W48/x/000000000000/x.mp4', 'bytes', 1, 'content_type', 'video/mp4')));
  v_t := pg_temp.ready_one(v_path_a, 1000, 'video/mp4');
  r := r || case when (select jsonb_agg(x ->> 'result') from jsonb_array_elements(v_j -> 'results') x) = '["ready", "mismatch", "unknown"]'::jsonb
                      and v_t = 'already'
                      and (select f.ready and f.uploaded_at is not null and f.etag = '"a1"' and f.expires_at = now() + interval '30 days'
                           from mc.marketing_files f where f.pathname = v_path_a)
                      and (select not f.ready and f.deleted_at is not null from mc.marketing_files f where f.pathname = v_path_b)
                 then 'ok  M16 the console''s check: ready, then already; a wrong size is a mismatch (the file is marked failed); an unknown pathname is unknown'
                 else 'FAIL M16 ' || left(v_j::text, 400) || ' second=' || coalesce(v_t, 'null') end;
  v_j := pg_temp.pending(array[v_path_a, v_path_b, v_path_c]);
  r := r || case when (select jsonb_agg(x ->> 'pathname') from jsonb_array_elements(v_j -> 'pending') x) = jsonb_build_array(v_path_c)
                 then 'ok  M17 a ready file and a failed one are no longer pending' else 'FAIL M17 ' || left(v_j::text, 300) end;
  -- The next push asks for the failed file again at a new pathname.
  v_j := pg_temp.push(v_p1);
  select u ->> 'pathname' into v_path_b2 from jsonb_array_elements(v_j -> 'uploads') u where u ->> 'file' = 'post-1.png';
  r := r || case when pg_temp.res(v_j, 'W48-post-tue') ->> 'result' = 'unchanged'
                      and v_path_b2 is not null and v_path_b2 <> v_path_b and v_path_b2 like 'marketing/test/2026-W48/W48-post-tue/%/post-1.png'
                      and exists (select 1 from jsonb_array_elements(v_j -> 'uploads') u where u ->> 'pathname' = v_path_c)
                      and not exists (select 1 from jsonb_array_elements(v_j -> 'uploads') u where u ->> 'pathname' = v_path_a)
                      and (select f.pathname from mc.marketing_media m join mc.marketing_files f on f.id = m.file_id
                           where m.card_id = v_post and m.version = 1 and m.position = 0) = v_path_b2
                      and (select deleted_at is not null from mc.marketing_files where pathname = v_path_b)
                 then 'ok  M18 a file that arrived wrong gets a new pathname on the next push (the version''s content is unchanged)'
                 else 'FAIL M18 ' || left(coalesce(v_j -> 'uploads', v_j)::text, 500) end;
  perform pg_temp.ready_one(v_path_b2, 2000, 'image/png');
  perform pg_temp.ready_one(v_path_c, 3000, 'image/png');

  -- ======================================================================== D: one file for versions that show it
  v_j := pg_temp.push(pg_temp.mk_pack('2026-W48', v_p1_start, jsonb_build_array(
    pg_temp.mk_card('W48-post-tue', v_today + 2, jsonb_build_object('kind', 'post', 'slot_time', '10:00', 'position', 20,
      'platforms', jsonb_build_array('Lucento Page'), 'version', 2, 'changes', 'Swapped the second picture',
      'media', jsonb_build_array(pg_temp.mk_media('post-1.png', 'image/png', 2000, 'img-b'),
                                 pg_temp.mk_media('post-3.png', 'image/png', 4000, 'img-d')))))));
  select u ->> 'pathname' into v_path_d from jsonb_array_elements(v_j -> 'uploads') u where u ->> 'file' = 'post-3.png';
  r := r || case when pg_temp.res(v_j, 'W48-post-tue') ->> 'result' = 'revised'
                      and jsonb_array_length(v_j -> 'uploads') = 1 and v_path_d is not null
                      and (select m.file_id from mc.marketing_media m where m.card_id = v_post and m.version = 2 and m.position = 0)
                          = (select m.file_id from mc.marketing_media m where m.card_id = v_post and m.version = 1 and m.position = 0)
                      and (select count(*) from mc.marketing_files where card_id = v_post) = 4
                 then 'ok  M19 a new version reuses the stored file for unchanged content and asks only for the new one'
                 else 'FAIL M19 ' || left(v_j::text, 500) end;

  -- ======================================================================== E: decisions
  v_j := pg_temp.decide(v_w1, 2, 'approve');
  r := r || case when v_j ->> 'error' = 'stale_version' and (v_j ->> 'current_version')::integer = 1
                      and (select state from mc.marketing_cards where id = v_w1) = 'waiting'
                 then 'ok  M20 a decision on a version that isn''t current is refused (stale_version, with the current version)'
                 else 'FAIL M20 ' || coalesce(v_j::text, 'null') end;
  v_j := pg_temp.decide(v_text, 1, 'approve');
  r := r || case when (v_j ->> 'ok')::boolean and v_j ->> 'state' = 'approved' and v_j -> 'cleanup' = '[]'::jsonb
                      and (select decided_by = v_admin and decided_at = now() from mc.marketing_cards where id = v_text)
                 then 'ok  M21 a text-only post (no files) can be approved at once'
                 else 'FAIL M21 ' || coalesce(v_j::text, 'null') end;
  v_j := pg_temp.decide(v_post, 2, 'approve');
  v_t := pg_temp.ready_one(v_path_d, 4000, 'image/png');
  v_k := pg_temp.decide(v_post, 2, 'approve');
  r := r || case when v_j ->> 'error' = 'not_ready' and v_t = 'ready' and (v_k ->> 'ok')::boolean
                      and v_k -> 'cleanup' = jsonb_build_array(v_path_c)
                 then 'ok  M22 approving waits for every file; afterwards the answer lists the files nothing shows any more (version 1''s second picture)'
                 else 'FAIL M22 ' || coalesce(v_j::text, '') || ' ' || coalesce(v_k::text, '') end;
  v_j := pg_temp.media_deleted(array[v_path_c, v_path_b2, 'marketing/test/nope']);
  r := r || case when v_j -> 'deleted' = jsonb_build_array(v_path_c)
                      and v_j -> 'kept' @> jsonb_build_array(v_path_b2, 'marketing/test/nope') and jsonb_array_length(v_j -> 'kept') = 2
                      and (select deleted_at is not null from mc.marketing_files where pathname = v_path_c)
                      and (select deleted_at is null from mc.marketing_files where pathname = v_path_b2)
                 then 'ok  M23 marking files deleted takes only those nothing shows (a file of the current version stays)'
                 else 'FAIL M23 ' || coalesce(v_j::text, 'null') end;
  -- A file only the version in the apps shows is kept until the apps hold another version.
  v_j := pg_temp.push(pg_temp.mk_pack('2026-W48', v_p1_start, jsonb_build_array(pg_temp.mk_card('C-apps', v_today + 4,
    jsonb_build_object('media', jsonb_build_array(pg_temp.mk_media('c-apps-1.mp4', 'video/mp4', 600, 'video-x')))))));
  v_t := v_j #>> '{uploads,0,pathname}';
  perform pg_temp.ready_one(v_t, 600, 'video/mp4');
  perform pg_temp.decide(pg_temp.cid('2026-W48', 'C-apps'), 1, 'approve');
  perform pg_temp.apps(pg_temp.cid('2026-W48', 'C-apps'), 1, 'in_apps');
  v_j := pg_temp.push(pg_temp.mk_pack('2026-W48', v_p1_start, jsonb_build_array(pg_temp.mk_card('C-apps', v_today + 4,
    jsonb_build_object('version', 2, 'changes', 'A new video',
      'media', jsonb_build_array(pg_temp.mk_media('c-apps-2.mp4', 'video/mp4', 650, 'video-y')))))));
  perform pg_temp.ready_one(v_j #>> '{uploads,0,pathname}', 650, 'video/mp4');
  v_k := pg_temp.decide(pg_temp.cid('2026-W48', 'C-apps'), 2, 'approve');
  v_m := pg_temp.media_deleted(array[v_t]);
  perform pg_temp.apps(pg_temp.cid('2026-W48', 'C-apps'), 2, 'in_apps');
  v_b := pg_temp.decide(pg_temp.cid('2026-W48', 'C-apps'), 2, 'skip');
  r := r || case when v_k -> 'cleanup' = '[]'::jsonb and v_m -> 'kept' = jsonb_build_array(v_t) and v_m -> 'deleted' = '[]'::jsonb
                      and v_b -> 'cleanup' = jsonb_build_array(v_t)
                 then 'ok  M23b the version the apps hold keeps its files out of the clean-up; once the apps hold the new one they can go'
                 else 'FAIL M23b ' || coalesce(v_k::text, '') || ' ' || coalesce(v_m::text, '') || ' ' || coalesce(v_b::text, '') end;

  v_j := jsonb_build_array(
    pg_temp.decide(v_w1, 1, 'request_change', '') ->> 'error',
    pg_temp.decide(v_w1, 1, 'request_change', 'ok') ->> 'error',
    pg_temp.decide(v_w1, 1, 'request_change', 'Please email me at me@lucento-test.invalid') ->> 'error',
    pg_temp.decide(v_w1, 1, 'request_change', 'Text +63 917 123 4567 first') ->> 'error',
    pg_temp.decide(v_w1, 1, 'request_change', 'or call 0917-123-4567') ->> 'error',
    pg_temp.decide(v_w1, 1, 'request_change', 'the shop is (02) 8123 4567') ->> 'error');
  v_k := pg_temp.decide(v_w1, 1, 'request_change', 'Shorter caption; keep 19:30 on 2026-11-24 and the ₱1,250 price', array['timing', 'caption']);
  r := r || case when v_j = '["note_required", "note_required", "note_has_contact", "note_has_contact", "note_has_contact", "note_has_contact"]'::jsonb
                      and (v_k ->> 'ok')::boolean and v_k ->> 'state' = 'changes'
                      and (select count(*) from mc.marketing_log where card_id = v_w1 and kind = 'changes') = 1
                      and (select tags from mc.marketing_log where card_id = v_w1 and kind = 'changes') = array['caption', 'timing']
                 then 'ok  M24 a change request needs a note of 3+ characters with no e-mail address or phone number; dates, times and prices are fine'
                 else 'FAIL M24 ' || coalesce(v_j::text, '') || ' ' || coalesce(v_k::text, '') end;
  v_k := pg_temp.decide(v_w1, 1, 'request_change', 'Also the hook in the first second', array['video']);
  v_b := pg_temp.cardj(pg_temp.board('2026-W48'), 'W48-1-sweldo');
  r := r || case when (v_k ->> 'ok')::boolean and jsonb_array_length(v_b -> 'open_requests') = 2
                      and v_b #>> '{open_requests,1,note}' = 'Also the hook in the first second'
                      and v_b #> '{open_requests,1,tags}' = '["video"]'::jsonb
                      and not (v_b #>> '{open_requests,0,seen}')::boolean and v_b ->> 'state' = 'changes'
                 then 'ok  M25 a second request on a post in changes is added; both are open and Claude hasn''t seen them'
                 else 'FAIL M25 ' || left(coalesce(v_b::text, 'null'), 500) end;
  v_j := jsonb_build_array(
    pg_temp.decide(v_w1, 1, 'request_change', 'Another post instead', array['replace']) ->> 'error',
    pg_temp.decide(v_w1, 1, 'approve', null, array['video']) ->> 'error',
    pg_temp.decide(v_w1, 1, 'publish') ->> 'error');
  r := r || case when v_j = '["invalid", "invalid", "invalid"]'::jsonb then 'ok  M26 tags that don''t fit the decision, and unknown decisions, are refused'
                 else 'FAIL M26 ' || v_j::text end;
  v_j := pg_temp.decide(v_w1, 1, 'undo');
  v_b := pg_temp.cardj(pg_temp.board('2026-W48'), 'W48-1-sweldo');
  v_k := pg_temp.decide(v_w1, 1, 'request_change', 'One more thought on the hook', array['video']);
  v_m := pg_temp.cardj(pg_temp.board('2026-W48'), 'W48-1-sweldo');
  perform pg_temp.decide(v_w1, 1, 'undo');
  r := r || case when v_j ->> 'state' = 'waiting' and jsonb_array_length(v_b -> 'open_requests') = 0 and v_b -> 'decided_at' = 'null'::jsonb
                      and v_k ->> 'state' = 'changes' and jsonb_array_length(v_m -> 'open_requests') = 1
                      and v_m #>> '{open_requests,0,note}' = 'One more thought on the hook'
                 then 'ok  M27 "Cancel my request": changes back to waiting, nothing left open; a later request stands alone'
                 else 'FAIL M27 ' || coalesce(v_j::text, '') || ' ' || left(coalesce(v_m -> 'open_requests', 'null'::jsonb)::text, 300) end;
  v_j := jsonb_build_array(pg_temp.decide(v_w1, 1, 'approve') ->> 'state', pg_temp.decide(v_w1, 1, 'approve') ->> 'error',
                           pg_temp.decide(v_w1, 1, 'undo') ->> 'state', pg_temp.decide(v_w1, 1, 'approve') ->> 'state',
                           pg_temp.decide(v_edge, 1, 'undo') ->> 'error');
  r := r || case when v_j = '["approved", "bad_state", "waiting", "approved", "bad_state"]'::jsonb
                 then 'ok  M28 approve, approve again (bad_state), "Undo approval", approve; undo on a waiting post is bad_state'
                 else 'FAIL M28 ' || v_j::text end;

  -- ======================================================================== F: in the apps
  v_j := jsonb_build_array(
    pg_temp.apps(v_edge, 1, 'in_apps') ->> 'error',
    pg_temp.apps(v_w1, 1, 'in_apps', array['Instagram']) ->> 'error',
    pg_temp.apps(v_w1, 1, 'scheduled') ->> 'error',
    pg_temp.apps(v_w1, 2, 'in_apps') ->> 'error');
  v_k := pg_temp.apps(v_w1, 1, 'in_apps', array['TikTok']);
  r := r || case when v_j = '["bad_state", "invalid", "invalid", "stale_version"]'::jsonb
                      and (v_k ->> 'ok')::boolean and (v_k ->> 'in_apps_version')::integer = 1 and v_k -> 'by_hand' = '["TikTok"]'::jsonb
                 then 'ok  M29 "I''ve scheduled it" only on an approved post, its own platforms by hand, the current version'
                 else 'FAIL M29 ' || v_j::text || ' ' || coalesce(v_k::text, '') end;
  v_t := pg_temp.apps(v_w1, 1, 'posted_by_hand') ->> 'error';
  update mc.marketing_cards set slot_day = v_today - 1 where id = v_w1;
  v_j := pg_temp.apps(v_w1, 1, 'posted_by_hand');
  v_k := pg_temp.apps(v_w1, 1, 'posted_by_hand');
  update mc.marketing_cards set slot_day = v_today + 1 where id = v_w1;
  r := r || case when v_t = 'bad_state' and (v_j ->> 'ok')::boolean and v_j ->> 'by_hand_done_at' is not null
                      and (v_k ->> 'already')::boolean
                      and (select count(*) from mc.marketing_log where card_id = v_w1 and kind = 'posted_by_hand') = 1
                 then 'ok  M30 "Did you post it on TikTok? Yes" only once its time has come; a second yes changes nothing'
                 else 'FAIL M30 before=' || coalesce(v_t, 'null') || ' ' || coalesce(v_j::text, '') end;
  v_j := pg_temp.decide(v_w1, 1, 'request_change', 'Make the end card brighter', array['video']);
  r := r || case when v_j ->> 'state' = 'changes'
                      and (select in_apps_version = 1 and by_hand = array['TikTok'] from mc.marketing_cards where id = v_w1)
                      and (select apps_stale from mc.marketing_status(null, v_w1)) = false
                 then 'ok  M31 asking for a change after scheduling keeps version 1 in the apps (until the owner removes it)'
                 else 'FAIL M31 ' || coalesce(v_j::text, '') end;

  -- ======================================================================== G: skipping and bringing back
  v_j := pg_temp.decide(v_edge, 1, 'skip', null, array['replace']);
  v_m := pg_temp.cardj(pg_temp.board('2026-W48'), 'W48-edge');
  v_k := pg_temp.decide(v_edge, 1, 'undo');
  v_b := pg_temp.cardj(pg_temp.board('2026-W48'), 'W48-edge');
  r := r || case when v_j ->> 'state' = 'skipped' and v_m #>> '{open_requests,0,kind}' = 'skipped'
                      and v_m #> '{open_requests,0,tags}' = '["replace"]'::jsonb
                      and v_k ->> 'state' = 'waiting' and jsonb_array_length(v_b -> 'open_requests') = 0
                 then 'ok  M32 "Skip it and ask Claude for another post" is a request for Claude; "Bring it back" ends it'
                 else 'FAIL M32 ' || coalesce(v_j::text, '') || ' ' || left(coalesce(v_m::text, ''), 300) end;
  v_j := jsonb_build_array(pg_temp.decide(v_text, 1, 'skip') ->> 'state', pg_temp.decide(v_text, 1, 'approve') ->> 'error',
                           pg_temp.decide(v_text, 1, 'undo') ->> 'state', pg_temp.decide(v_edge, 1, 'skip') ->> 'state');
  r := r || case when v_j = '["skipped", "bad_state", "waiting", "skipped"]'::jsonb
                      and jsonb_array_length((pg_temp.cardj(pg_temp.board('2026-W48'), 'W48-edge')) -> 'open_requests') = 0
                 then 'ok  M33 an approved post can be skipped (not approved again until brought back); a plain skip asks nothing of Claude'
                 else 'FAIL M33 ' || v_j::text end;

  -- ======================================================================== G2: deciding on a post with a change asked
  -- "Approve this version anyway": the request closes, and the answer lists what nothing shows any more.
  v_j := pg_temp.push(pg_temp.mk_pack('2026-W48', v_p1_start, jsonb_build_array(pg_temp.mk_card('G-approve', v_today + 5,
    jsonb_build_object('kind', 'post', 'platforms', jsonb_build_array('Lucento Page'),
      'media', jsonb_build_array(pg_temp.mk_media('g-1.png', 'image/png', 500, 'img-g1')))))));
  v_t := v_j #>> '{uploads,0,pathname}';
  perform pg_temp.ready_one(v_t, 500, 'image/png');
  v_g := pg_temp.cid('2026-W48', 'G-approve');
  perform pg_temp.decide(v_g, 1, 'request_change', 'A brighter picture, please', array['video']);
  v_j := pg_temp.push(pg_temp.mk_pack('2026-W48', v_p1_start, jsonb_build_array(pg_temp.mk_card('G-approve', v_today + 5,
    jsonb_build_object('kind', 'post', 'platforms', jsonb_build_array('Lucento Page'), 'version', 2, 'changes', 'A brighter picture',
      'media', jsonb_build_array(pg_temp.mk_media('g-2.png', 'image/png', 510, 'img-g2')))))));
  perform pg_temp.ready_one(v_j #>> '{uploads,0,pathname}', 510, 'image/png');
  v_k := pg_temp.decide(v_g, 2, 'request_change', 'Close, but crop it tighter', array['video']);
  v_m := pg_temp.cardj(pg_temp.board('2026-W48'), 'G-approve');
  v_b := pg_temp.decide(v_g, 2, 'approve');
  v_q := pg_temp.cardj(pg_temp.board('2026-W48'), 'G-approve');
  r := r || case when v_k ->> 'state' = 'changes' and jsonb_array_length(v_m -> 'open_requests') = 1
                      and (v_b ->> 'ok')::boolean and v_b ->> 'state' = 'approved' and v_b -> 'cleanup' = jsonb_build_array(v_t)
                      and v_q ->> 'state' = 'approved' and jsonb_array_length(v_q -> 'open_requests') = 0
                      and (select count(*) from mc.marketing_log where card_id = v_g and version = 2 and kind = 'approved') = 1
                      and exists (select 1 from mc.audit_log where action = 'marketing.approve' and target_ref = '2026-W48/G-approve v2'
                                  and before = '{"state": "changes"}'::jsonb and after = '{"state": "approved"}'::jsonb)
                 then 'ok  M33b approve with a change asked ("Approve this version anyway"): approved, nothing left open, version 1''s picture in the clean-up, audited'
                 else 'FAIL M33b ' || coalesce(v_k::text, '') || ' ' || coalesce(v_b::text, '') || ' '
                        || left(coalesce(v_q -> 'open_requests', 'null'::jsonb)::text, 200) end;
  -- "Don't post this one" with a change asked: the request gives way to the skip, or to "ask Claude for another post".
  perform pg_temp.push(pg_temp.mk_pack('2026-W48', v_p1_start, jsonb_build_array(
    pg_temp.mk_card('G-skip', v_today + 5, jsonb_build_object('kind', 'group', 'platforms', jsonb_build_array('Community group'))),
    pg_temp.mk_card('G-replace', v_today + 5, jsonb_build_object('kind', 'group', 'platforms', jsonb_build_array('Community group'))))));
  v_g := pg_temp.cid('2026-W48', 'G-skip');
  v_g2 := pg_temp.cid('2026-W48', 'G-replace');
  perform pg_temp.decide(v_g, 1, 'request_change', 'Ask a shorter question', array['caption']);
  perform pg_temp.decide(v_g2, 1, 'request_change', 'Ask a shorter question', array['caption']);
  v_j := pg_temp.decide(v_g, 1, 'skip');
  v_k := pg_temp.decide(v_g2, 1, 'skip', null, array['replace']);
  v_m := pg_temp.board('2026-W48');
  r := r || case when v_j ->> 'state' = 'skipped' and v_k ->> 'state' = 'skipped'
                      and jsonb_array_length(pg_temp.cardj(v_m, 'G-skip') -> 'open_requests') = 0
                      and jsonb_array_length(pg_temp.cardj(v_m, 'G-replace') -> 'open_requests') = 1
                      and pg_temp.cardj(v_m, 'G-replace') #>> '{open_requests,0,kind}' = 'skipped'
                      and pg_temp.cardj(v_m, 'G-replace') #> '{open_requests,0,tags}' = '["replace"]'::jsonb
                      and (pg_temp.cardj(v_m, 'G-replace') #>> '{open_requests,0,log_id}')::bigint
                          = (select max(id) from mc.marketing_log where card_id = v_g2 and kind = 'skipped')
                      and exists (select 1 from mc.audit_log where action = 'marketing.skip' and target_ref = '2026-W48/G-skip v1'
                                  and before = '{"state": "changes"}'::jsonb and detail is null)
                      and exists (select 1 from mc.audit_log where action = 'marketing.skip' and target_ref = '2026-W48/G-replace v1'
                                  and detail = 'replace')
                 then 'ok  M33c skip with a change asked: a plain skip leaves nothing open; "skip it and ask Claude for another post" becomes the open request'
                 else 'FAIL M33c ' || coalesce(v_j::text, '') || ' ' || coalesce(v_k::text, '') || ' '
                        || left(coalesce(pg_temp.cardj(v_m, 'G-replace') -> 'open_requests', 'null'::jsonb)::text, 300) end;
  -- Nothing left open for Claude's checks below.
  perform pg_temp.decide(v_g2, 1, 'undo');

  -- ======================================================================== H: new versions and taking posts out
  v_j := pg_temp.push(pg_temp.mk_pack('2026-W48', v_p1_start, jsonb_build_array(
    pg_temp.mk_card('W48-1-sweldo', v_today + 1, jsonb_build_object('version', 2,
      'changes', 'Shorter caption and a brighter end card', 'caption', 'Sweldo day? Sorted.',
      'media', jsonb_build_array(pg_temp.mk_media('W48-1-sweldo.mp4', 'video/mp4', 1000, 'video-a')))))));
  v_b := pg_temp.cardj(pg_temp.board('2026-W48'), 'W48-1-sweldo');
  r := r || case when pg_temp.res(v_j, 'W48-1-sweldo') ->> 'result' = 'revised' and v_b ->> 'state' = 'waiting'
                      and v_b -> 'decided_at' = 'null'::jsonb and (v_b ->> 'in_apps_version')::integer = 1
                      and v_b -> 'by_hand' = '["TikTok"]'::jsonb and (v_b #>> '{flags,apps_stale}')::boolean
                      and (v_b #>> '{version,version}')::integer = 2 and v_b #>> '{version,changes}' = 'Shorter caption and a brighter end card'
                      and (v_b #>> '{previous,version}')::integer = 1 and v_b #>> '{previous,caption}' like 'Sweldo day?%13 s%'
                      and jsonb_array_length(v_b -> 'open_requests') = 0 and jsonb_array_length(v_j -> 'uploads') = 0
                      and (v_b #>> '{version,ready}')::boolean
                 then 'ok  M34 a new version after a change request: waiting again, the decision cleared, the apps still on version 1 (apps_stale), version 1 shown beside it'
                 else 'FAIL M34 ' || left(coalesce(v_b::text, v_j::text), 600) end;
  v_j := pg_temp.push(pg_temp.mk_pack('2026-W48', v_p1_start, jsonb_build_array(
    pg_temp.mk_card('W48-edge', v_p1_start - 7, jsonb_build_object('version', 2, 'changes', 'Tighter cut')))));
  v_k := pg_temp.push(pg_temp.mk_pack('2026-W48', v_p1_start, jsonb_build_array(
    pg_temp.mk_card('W48-edge', v_p1_start - 7, jsonb_build_object('version', 3, 'changes', 'Brought back', 'revive', true)))));
  r := r || case when pg_temp.res(v_j, 'W48-edge') ->> 'result' = 'revised' and pg_temp.res(v_j, 'W48-edge') ->> 'state' = 'skipped'
                      and pg_temp.res(v_k, 'W48-edge') ->> 'state' = 'waiting'
                 then 'ok  M35 a new version of a skipped post stays skipped, unless Claude asks to revive it'
                 else 'FAIL M35 ' || coalesce(pg_temp.res(v_j, 'W48-edge')::text, '') || ' ' || coalesce(pg_temp.res(v_k, 'W48-edge')::text, '') end;
  v_j := pg_temp.push(pg_temp.mk_pack('2026-W48', v_p1_start, jsonb_build_array(
    pg_temp.mk_card('W48-post-tue', v_today + 2, jsonb_build_object('kind', 'post', 'slot_time', '10:00', 'position', 20,
      'platforms', jsonb_build_array('Lucento Page'), 'version', 3, 'changes', 'New headline', 'title', 'Payday, planned',
      'media', jsonb_build_array(pg_temp.mk_media('post-1.png', 'image/png', 2000, 'img-b'),
                                 pg_temp.mk_media('post-3.png', 'image/png', 4000, 'img-d')))))));
  r := r || case when pg_temp.res(v_j, 'W48-post-tue') ->> 'state' = 'waiting'
                      and (select state = 'waiting' and decided_at is null and decided_by is null and title = 'Payday, planned'
                           from mc.marketing_cards where id = v_post)
                 then 'ok  M36 an approval never carries over: a new version of an approved post waits for a new yes'
                 else 'FAIL M36 ' || coalesce(pg_temp.res(v_j, 'W48-post-tue')::text, '') end;
  v_j := pg_temp.push(pg_temp.mk_pack('2026-W48', v_p1_start, jsonb_build_array(
    pg_temp.mk_card('W48-group-wed', v_today + 3, jsonb_build_object('kind', 'group', 'slot_time', null, 'title', 'Group question',
      'platforms', jsonb_build_array('Community group'), 'withdrawn', true, 'withdrawn_reason', 'Replaced by W48-group-thu')))));
  v_k := pg_temp.push(pg_temp.mk_pack('2026-W48', v_p1_start, jsonb_build_array(
    pg_temp.mk_card('W48-group-wed', v_today + 3, jsonb_build_object('kind', 'group', 'slot_time', null,
      'withdrawn', true, 'withdrawn_reason', 'Replaced by W48-group-thu')))));
  v_m := pg_temp.push(pg_temp.mk_pack('2026-W48', v_p1_start, jsonb_build_array(
    pg_temp.mk_card('W48-group-wed', v_today + 3, jsonb_build_object('version', 2, 'changes', 'Back again')),
    pg_temp.mk_card('W48-edge', v_p1_start - 7, jsonb_build_object('version', 3, 'withdrawn', true)),
    pg_temp.mk_card('W48-edge', v_p1_start - 7, jsonb_build_object('version', 2, 'withdrawn', true, 'withdrawn_reason', 'Old')),
    pg_temp.mk_card('W48-ghost', v_today, jsonb_build_object('withdrawn', true, 'withdrawn_reason', 'Never pushed')))));
  v_t := pg_temp.decide(v_text, 1, 'approve') ->> 'error';
  r := r || case when pg_temp.res(v_j, 'W48-group-wed') ->> 'result' = 'withdrawn' and pg_temp.res(v_j, 'W48-group-wed') ->> 'state' = 'withdrawn'
                      and pg_temp.res(v_k, 'W48-group-wed') ->> 'result' = 'unchanged'
                      and pg_temp.res(v_m, 'W48-group-wed') ->> 'reason' = 'withdrawn'
                      and (select jsonb_agg(x ->> 'reason') from jsonb_array_elements(v_m -> 'cards') x where x ->> 'key' = 'W48-edge')
                          = '["reason_required", "version_out_of_order"]'::jsonb
                      and pg_temp.res(v_m, 'W48-ghost') ->> 'reason' = 'not_found' and v_t = 'bad_state'
                      and (select withdrawn_reason from mc.marketing_cards where id = v_text) = 'Replaced by W48-group-thu'
                      and (select count(*) from mc.marketing_log where card_id = v_text and kind = 'withdrawn') = 1
                 then 'ok  M37 Claude takes a post out with a reason (again: unchanged); after that it takes no push and no decision'
                 else 'FAIL M37 ' || left(coalesce(pg_temp.reasons(v_m)::text, '') || ' ' || coalesce(v_t, ''), 400) end;

  -- ======================================================================== I: launch day and the derived flags
  v_pday_local := date_trunc('minute', (now() - interval '1 hour') at time zone 'Asia/Manila');
  v_pday_day := v_pday_local::date;
  v_pday_text := to_char(v_pday_local, 'YYYY-MM-DD"T"HH24:MI');
  v_soon := (now() + interval '20 hours') at time zone 'Asia/Manila';
  v_p2 := pg_temp.mk_pack('F-pack', v_today - 3, jsonb_build_array(
    pg_temp.mk_card('F-anytime', v_pday_day, jsonb_build_object('phase', 'warmup', 'kind', 'post', 'slot_time', null)),
    pg_temp.mk_card('F-warm-before', v_pday_day - 1, jsonb_build_object('phase', 'warmup', 'slot_time', '10:00')),
    pg_temp.mk_card('F-launch-early', v_today + 2, jsonb_build_object('phase', 'launch')),
    pg_temp.mk_card('F-launch-past', v_pday_day - 1, jsonb_build_object('phase', 'launch', 'slot_time', '10:00')),
    pg_temp.mk_card('F-due', v_soon::date, jsonb_build_object('slot_time', to_char(v_soon, 'HH24:MI'))),
    pg_temp.mk_card('F-soon', v_soon::date, jsonb_build_object('slot_time', to_char(v_soon, 'HH24:MI'))),
    pg_temp.mk_card('F-missed', v_today - 2, jsonb_build_object('slot_time', '10:00')),
    pg_temp.mk_card('F-video', v_today + 2, jsonb_build_object('slot_time', '12:15',
      'media', jsonb_build_array(pg_temp.mk_media('f-video.mp4', 'video/mp4', 5000, 'video-e'))))));
  v_j := pg_temp.push(v_p2);
  select u ->> 'pathname' into v_path_e from jsonb_array_elements(v_j -> 'uploads') u where u ->> 'file' = 'f-video.mp4';
  v_b := pg_temp.board('F-pack');
  v_m := pg_temp.summary();
  r := r || case when (select count(*) from jsonb_array_elements(v_j -> 'cards') x where x ->> 'result' = 'created') = 8
                      and (pg_temp.cardj(v_b, 'F-launch-early') #>> '{flags,too_early}')::boolean
                      and (pg_temp.cardj(v_b, 'F-launch-past') #>> '{flags,too_early}')::boolean
                      and not (pg_temp.cardj(v_b, 'F-anytime') #>> '{flags,replace_after_launch}')::boolean
                      and (pg_temp.cardj(v_b, 'F-soon') #>> '{flags,needs_decision_soon}')::boolean
                      and (pg_temp.cardj(v_b, 'F-missed') #>> '{flags,missed}')::boolean
                      and not (pg_temp.cardj(v_b, 'F-missed') #>> '{flags,needs_decision_soon}')::boolean
                      and (pg_temp.cardj(v_b, 'F-video') #>> '{flags,uploading}')::boolean
                      and not (pg_temp.cardj(v_b, 'F-video') #>> '{flags,media_missing}')::boolean
                      and pg_temp.has(v_m -> 'needs_decision_soon', 'F-soon') and pg_temp.has(v_m -> 'too_early', 'F-launch-early')
                      and not pg_temp.has(v_m -> 'too_early', 'F-launch-past') and (v_m ->> 'missed')::integer >= 1
                 then 'ok  M38 before launch: "Get it on Google Play" posts are too early, a slot within 36 hours needs a decision, a passed one is missed, a new video is uploading'
                 else 'FAIL M38 ' || left(coalesce(v_m::text, ''), 500) end;
  v_j := pg_temp.decide(pg_temp.cid('F-pack', 'F-due'), 1, 'approve');
  v_ok := (select due_soon and not needs_decision_soon from mc.marketing_status(null, pg_temp.cid('F-pack', 'F-due')));
  v_m := pg_temp.summary();
  v_k := pg_temp.apps(pg_temp.cid('F-pack', 'F-due'), 1, 'in_apps');
  r := r || case when v_ok and pg_temp.has(v_m -> 'due_soon', 'F-due') and (v_m #>> '{counts,due_soon}')::integer >= 1
                      and not (select due_soon from mc.marketing_status(null, pg_temp.cid('F-pack', 'F-due')))
                 then 'ok  M39 approved but not scheduled within 36 hours is due soon, until "I''ve scheduled it"'
                 else 'FAIL M39 ' || left(coalesce(v_m::text, ''), 300) end;
  v_j := pg_temp.decide(pg_temp.cid('F-pack', 'F-due'), 1, 'skip');
  r := r || case when (select apps_stale from mc.marketing_status(null, pg_temp.cid('F-pack', 'F-due')))
                      and (select apps_stale from mc.marketing_status(null, v_w1))
                      and pg_temp.has(pg_temp.summary() -> 'apps_stale', 'F-due') and pg_temp.has(pg_temp.summary() -> 'apps_stale', 'W48-1-sweldo')
                 then 'ok  M40 the apps hold a stale post: an older version, or one now skipped'
                 else 'FAIL M40 ' || coalesce(v_j::text, '') end;

  v_j := pg_temp.preview(v_pday_text);
  r := r || case when (v_j ->> 'ok')::boolean and (v_j ->> 'pday_at')::timestamptz = v_pday_local::timestamp at time zone 'Asia/Manila'
                      and (v_j ->> 'replace_after_launch')::integer = 1 and (v_j ->> 'in_apps_after_launch')::integer = 0
                      and (v_j ->> 'too_early')::integer = 1 and (select pday_at is null from mc.marketing_launch)
                 then 'ok  M41 the launch preview says what marking that moment would flag, and changes nothing'
                 else 'FAIL M41 ' || coalesce(v_j::text, 'null') end;
  v_j := pg_temp.launch(v_pday_text, s_stale);
  v_k := pg_temp.launch(v_pday_text);
  r := r || case when v_j ->> 'error' = 'step_up_required' and (v_k ->> 'ok')::boolean
                      and (select l.pday_at = (v_pday_local - interval '8 hours') at time zone 'UTC'
                                  and (l.pday_at at time zone 'Asia/Manila') = v_pday_local
                                  and l.pday_set_by = v_admin and l.pday_set_at = now() from mc.marketing_launch l)
                      and (v_k ->> 'replace_after_launch')::integer = 1 and (v_k ->> 'too_early')::integer = 1
                      and exists (select 1 from mc.audit_log where action = 'marketing.launch_set' and result = 'ok' and before = '{"pday_at": null}'::jsonb)
                 then 'ok  M42 "Lucento is live on Google Play" needs a fresh code; the Manila moment is stored exactly (UTC+8) and audited'
                 else 'FAIL M42 ' || coalesce(v_j::text, '') || ' ' || coalesce(v_k::text, '') end;
  v_b := pg_temp.board('F-pack');
  r := r || case when (pg_temp.cardj(v_b, 'F-anytime') #>> '{flags,replace_after_launch}')::boolean
                      and not (pg_temp.cardj(v_b, 'F-warm-before') #>> '{flags,replace_after_launch}')::boolean
                      and not (pg_temp.cardj(v_b, 'F-launch-early') #>> '{flags,too_early}')::boolean
                      and (pg_temp.cardj(v_b, 'F-launch-past') #>> '{flags,too_early}')::boolean
                      and pg_temp.has(pg_temp.summary() -> 'replace_after_launch', 'F-anytime')
                      and v_b #>> '{launch,pday_set_by}' = 'Ops'
                 then 'ok  M43 after launch: a "coming soon" post set for any time on launch day is flagged for replacement; one before it isn''t; launch posts after it are fine'
                 else 'FAIL M43 ' || left(coalesce(pg_temp.cardj(v_b, 'F-anytime')::text, ''), 400) end;
  perform pg_temp.decide(pg_temp.cid('F-pack', 'F-anytime'), 1, 'approve');
  perform pg_temp.apps(pg_temp.cid('F-pack', 'F-anytime'), 1, 'in_apps');
  v_j := pg_temp.preview(v_pday_text);
  r := r || case when (v_j ->> 'replace_after_launch')::integer = 1 and (v_j ->> 'in_apps_after_launch')::integer = 1
                 then 'ok  M44 the count says how many of them are already in the apps' else 'FAIL M44 ' || coalesce(v_j::text, '') end;
  v_j := jsonb_build_array(
    pg_temp.launch(to_char((now() + interval '2 hours') at time zone 'Asia/Manila', 'YYYY-MM-DD"T"HH24:MI')) ->> 'error',
    pg_temp.launch(to_char((now() - interval '8 days') at time zone 'Asia/Manila', 'YYYY-MM-DD"T"HH24:MI')) ->> 'error',
    pg_temp.launch('tomorrow') ->> 'error',
    pg_temp.launch('2026-02-30T10:00') ->> 'error');
  r := r || case when v_j = '["launch_future", "launch_too_old", "invalid", "invalid"]'::jsonb
                      and (select pday_at = (v_pday_local - interval '8 hours') at time zone 'UTC' from mc.marketing_launch)
                 then 'ok  M45 not more than an hour ahead, not more than 7 days back, a real YYYY-MM-DDTHH:MM'
                 else 'FAIL M45 ' || v_j::text end;
  v_j := pg_temp.launch(null);
  v_ok := (select pday_at is null and pday_set_by is null from mc.marketing_launch)
          and not (select replace_after_launch from mc.marketing_status(null, pg_temp.cid('F-pack', 'F-anytime')))
          and exists (select 1 from mc.audit_log where action = 'marketing.launch_cleared' and result = 'ok');
  v_k := pg_temp.launch(v_pday_text);
  r := r || case when v_ok and (v_j ->> 'ok')::boolean and (v_k ->> 'ok')::boolean
                 then 'ok  M46 Undo clears the moment (audited); setting it again works'
                 else 'FAIL M46 ' || coalesce(v_j::text, '') end;
  alter table mc.marketing_versions disable trigger marketing_versions_no_update;
  update mc.marketing_versions set created_at = now() - interval '3 hours' where card_id = pg_temp.cid('F-pack', 'F-video');
  alter table mc.marketing_versions enable trigger marketing_versions_no_update;
  v_b := pg_temp.cardj(pg_temp.board('F-pack'), 'F-video');
  v_m := pg_temp.summary();
  r := r || case when (v_b #>> '{flags,media_missing}')::boolean and not (v_b #>> '{flags,uploading}')::boolean
                      and (v_m ->> 'media_missing')::integer = 1 and (v_m ->> 'media_missing_since')::timestamptz = now() - interval '3 hours'
                 then 'ok  M47 a video that hasn''t arrived 2 hours after its version is missing, with the time it was pushed'
                 else 'FAIL M47 ' || left(coalesce(v_b -> 'flags', 'null'::jsonb)::text, 300) end;
  r := r || case when jsonb_array_length(v_m -> 'media_missing_items') = (v_m ->> 'media_missing')::integer
                      and v_m #>> '{media_missing_items,0,card}' = 'F-video' and v_m #>> '{media_missing_items,0,pack}' = 'F-pack'
                      and (v_m #>> '{media_missing_items,0,card_id}')::uuid = pg_temp.cid('F-pack', 'F-video')
                      and (v_m #>> '{media_missing_items,0,current_version}')::integer = 1
                      and v_m #>> '{media_missing_items,0,slot_time}' = '12:15'
                 then 'ok  M47b Overview gets the posts missing their files too (pack, card, version, slot), so it links straight to the card'
                 else 'FAIL M47b ' || left(coalesce(v_m -> 'media_missing_items', 'null'::jsonb)::text, 300) end;
  perform pg_temp.decide(pg_temp.cid('F-pack', 'F-missed'), 1, 'skip');
  r := r || case when not (select missed from mc.marketing_status(null, pg_temp.cid('F-pack', 'F-missed')))
                 then 'ok  M48 "Don''t post it" on a missed post clears missed' else 'FAIL M48' end;
  -- A post the apps hold went out at its slot: never "missed", even with a change asked since.
  perform pg_temp.push(pg_temp.mk_pack('F-pack', v_today - 3, jsonb_build_array(
    pg_temp.mk_card('F-gone', v_today - 1, jsonb_build_object('slot_time', '10:00')),
    pg_temp.mk_card('F-gone-2', v_today - 1, jsonb_build_object('slot_time', '10:00')))));
  v_g := pg_temp.cid('F-pack', 'F-gone');
  v_g2 := pg_temp.cid('F-pack', 'F-gone-2');
  perform pg_temp.decide(v_g, 1, 'approve');
  perform pg_temp.apps(v_g, 1, 'in_apps');
  perform pg_temp.decide(v_g, 1, 'request_change', 'Shorter next time', array['caption']);
  perform pg_temp.decide(v_g2, 1, 'request_change', 'A shorter caption', array['caption']);
  v_ok := not (select missed from mc.marketing_status(null, v_g)) and (select missed from mc.marketing_status(null, v_g2))
          and not (pg_temp.cardj(pg_temp.board('F-pack'), 'F-gone') #>> '{flags,missed}')::boolean
          and (pg_temp.cardj(pg_temp.board('F-pack'), 'F-gone') ->> 'state') = 'changes';
  v_n := (pg_temp.summary() ->> 'missed')::integer;
  v_y := (select count(*) from mc.marketing_status(null) s where s.missed);
  perform pg_temp.apps(v_g, 1, 'removed_from_apps');
  v_x := (pg_temp.summary() ->> 'missed')::integer;
  r := r || case when v_ok and v_n = v_y and v_x = v_n + 1 and (select missed from mc.marketing_status(null, v_g))
                 then 'ok  M48b a post in the apps went out at its slot: not missed, even with a change asked (Overview''s count follows); removed from the apps, it is'
                 else 'FAIL M48b ok=' || coalesce(v_ok::text, 'null') || ' ' || coalesce(v_n::text, 'null') || ' '
                        || coalesce(v_y::text, 'null') || ' ' || coalesce(v_x::text, 'null') end;
  -- Nothing left open for Claude's checks below.
  perform pg_temp.decide(v_g, 1, 'skip');
  perform pg_temp.decide(v_g2, 1, 'skip');

  -- ======================================================================== J: Claude's feedback
  v_k := pg_temp.decide(pg_temp.cid('F-pack', 'F-soon'), 1, 'request_change', 'Change the hook to sweldo', array['video']);
  select id into v_id from mc.marketing_log where card_id = pg_temp.cid('F-pack', 'F-soon') and kind = 'changes';
  v_b := pg_temp.cardj(pg_temp.board('F-pack'), 'F-soon');
  v_m := pg_temp.summary();
  r := r || case when not (v_b #>> '{open_requests,0,seen}')::boolean and pg_temp.has(v_m -> 'unseen_requests', 'F-soon')
                      and (select (x ->> 'log_id')::bigint = v_id from jsonb_array_elements(v_m -> 'unseen_requests') x where x ->> 'card' = 'F-soon')
                 then 'ok  M49 a new request: "Claude hasn''t seen it yet", and Overview lists it'
                 else 'FAIL M49 ' || left(coalesce(v_m -> 'unseen_requests', 'null'::jsonb)::text, 300) end;
  v_j := pg_temp.feedback(null, null);
  v_db := v_j ->> 'db_id';
  v_cursor := (v_j ->> 'cursor')::bigint;
  r := r || case when (v_j ->> 'ok')::boolean and (v_j ->> 'reset')::boolean and not (v_j ->> 'more')::boolean
                      and v_db = (select db_id::text from mc.marketing_launch)
                      and jsonb_array_length(v_j -> 'events') = (select count(*) from mc.marketing_log where actor = 'admin')
                      and v_cursor = (select max(id) from mc.marketing_log where actor = 'admin')
                      and (v_j #>> '{events,0,id}')::bigint = (select min(id) from mc.marketing_log where actor = 'admin')
                      and v_j #>> '{events,0,kind}' = 'approved' and v_j #>> '{events,0,pack}' = '2026-W48'
                      and exists (select 1 from jsonb_array_elements(v_j -> 'open_requests') x
                                  where x ->> 'card' = 'F-soon' and x ->> 'note' = 'Change the hook to sweldo' and (x ->> 'log_id')::bigint = v_id)
                      and pg_temp.has(v_j -> 'replace_after_launch', 'F-anytime') and pg_temp.has(v_j -> 'media_missing', 'F-video')
                      and pg_temp.has(v_j -> 'apps_stale', 'W48-1-sweldo') and pg_temp.has(v_j -> 'too_early', 'F-launch-past') is false
                      and v_j #>> '{launch,pday_label}' = 'Expected 1–8 Dec' and v_j ->> 'tz' = 'Asia/Manila'
                 then 'ok  M50 the first check (no cursor): every owner entry oldest first, the open requests, launch and the posts to replace or re-upload'
                 else 'FAIL M50 ' || left(v_j::text, 700) end;
  v_b := pg_temp.cardj(pg_temp.board('F-pack'), 'F-soon');
  v_m := pg_temp.summary();
  r := r || case when (v_b #>> '{open_requests,0,seen}')::boolean and jsonb_array_length(v_m -> 'unseen_requests') = 0
                      and (select claude_checked_at = now() and claude_seen_log_id >= v_id from mc.marketing_launch)
                      and (v_m ->> 'claude_checked_at')::timestamptz = now()
                 then 'ok  M51 after Claude''s check: "Claude saw it", and when Claude last checked'
                 else 'FAIL M51 ' || left(coalesce(v_b -> 'open_requests', 'null'::jsonb)::text, 300) end;
  v_j := pg_temp.feedback(v_db, v_cursor);
  perform pg_temp.decide(pg_temp.cid('F-pack', 'F-soon'), 1, 'undo');
  v_k := pg_temp.feedback(v_db, v_cursor);
  r := r || case when not (v_j ->> 'reset')::boolean and jsonb_array_length(v_j -> 'events') = 0 and (v_j ->> 'cursor')::bigint = v_cursor
                      and jsonb_array_length(v_k -> 'events') = 1 and v_k #>> '{events,0,kind}' = 'undo' and v_k #>> '{events,0,card}' = 'F-soon'
                      and (v_k ->> 'cursor')::bigint > v_cursor and jsonb_array_length(v_k -> 'open_requests') = 0
                 then 'ok  M52 with the cursor: nothing new, then exactly the next owner entry'
                 else 'FAIL M52 ' || left(coalesce(v_k::text, ''), 400) end;
  v_cursor := (v_k ->> 'cursor')::bigint;
  v_j := jsonb_build_array(pg_temp.feedback(gen_random_uuid()::text, v_cursor) -> 'reset', pg_temp.feedback(v_db, v_cursor + 100000) -> 'reset',
                           pg_temp.feedback(v_db, -5) -> 'reset', pg_temp.feedback('not-a-uuid', v_cursor) -> 'reset',
                           pg_temp.feedback(v_db, v_cursor) -> 'reset');
  r := r || case when v_j = '[true, true, true, true, false]'::jsonb
                 then 'ok  M53 a cursor from another database, or beyond the log''s end, starts again (reset)'
                 else 'FAIL M53 ' || v_j::text end;
  insert into mc.marketing_log (card_id, version, kind, actor, admin_id)
    select v_text, 1, 'in_apps', 'admin', v_admin from generate_series(1, 510);
  v_j := pg_temp.feedback(v_db, v_cursor);
  v_k := pg_temp.feedback(v_db, (v_j ->> 'cursor')::bigint);
  r := r || case when jsonb_array_length(v_j -> 'events') = 500 and (v_j ->> 'more')::boolean
                      and (v_j ->> 'cursor')::bigint = (v_j #>> '{events,499,id}')::bigint
                      and jsonb_array_length(v_k -> 'events') = 10 and not (v_k ->> 'more')::boolean
                      and (v_k ->> 'cursor')::bigint = (select max(id) from mc.marketing_log where actor = 'admin')
                 then 'ok  M54 at most 500 entries at a time (more: true); the next call continues from the cursor'
                 else 'FAIL M54 ' || coalesce(v_j ->> 'more', '') || ' ' || jsonb_array_length(v_j -> 'events') end;
  r := r || case when exists (select 1 from pg_locks l where l.locktype = 'advisory' and l.mode = 'ShareLock' and l.pid = pg_backend_pid()
                              and l.classid::text::bigint = ((v_hash >> 32) & 4294967295) and l.objid::text::bigint = (v_hash & 4294967295))
                      and exists (select 1 from pg_locks l where l.locktype = 'advisory' and l.mode = 'ExclusiveLock' and l.pid = pg_backend_pid()
                                  and l.classid::text::bigint = ((v_hash >> 32) & 4294967295) and l.objid::text::bigint = (v_hash & 4294967295))
                 then 'ok  M55 the feedback reader takes the marketing lock shared; the writers take it exclusively'
                 else 'FAIL M55 locks=' || (select coalesce(string_agg(mode, ','), 'none') from pg_locks where locktype = 'advisory') end;
  v_j := pg_temp.apps(pg_temp.cid('F-pack', 'F-due'), 1, 'removed_from_apps');
  v_k := pg_temp.apps(pg_temp.cid('F-pack', 'F-due'), 1, 'removed_from_apps');
  r := r || case when (v_j ->> 'ok')::boolean and v_j -> 'in_apps_version' = 'null'::jsonb and v_j -> 'by_hand' = '[]'::jsonb
                      and v_k ->> 'error' = 'not_in_apps'
                      and not (select apps_stale from mc.marketing_status(null, pg_temp.cid('F-pack', 'F-due')))
                 then 'ok  M56 "I''ve removed it from the apps" (any state) ends apps_stale; a second time: not_in_apps'
                 else 'FAIL M56 ' || coalesce(v_j::text, '') || ' ' || coalesce(v_k::text, '') end;

  -- ======================================================================== K: Claude's notes
  v_j := pg_temp.push(pg_temp.mk_pack('2026-W48', v_p1_start, '[]'::jsonb, jsonb_build_object('notes', jsonb_build_array(
    jsonb_build_object('card', 'W48-1-sweldo', 'text', 'Kept ''ipon'': T10 forbids the other wording.'),
    jsonb_build_object('card', 'W48-1-sweldo', 'text', 'Kept ''ipon'': T10 forbids the other wording.'),
    jsonb_build_object('card', 'W48-1-sweldo', 'text', 'Ask maria@example.com')))));
  v_k := pg_temp.push(pg_temp.mk_pack('2026-W48', v_p1_start, '[]'::jsonb, jsonb_build_object('notes', jsonb_build_array(
    jsonb_build_object('card', 'W48-1-sweldo', 'text', 'Kept ''ipon'': T10 forbids the other wording.')))));
  r := r || case when v_j -> 'notes' = '{"added": 1, "already": 1, "skipped": 1}'::jsonb and v_k -> 'notes' = '{"added": 0, "already": 1, "skipped": 0}'::jsonb
                      and (select count(*) from mc.marketing_log where card_id = v_w1 and kind = 'claude_note') = 2
                 then 'ok  M57 a note is stored once per post version (the same text on version 2 is new); one with an e-mail address is skipped'
                 else 'FAIL M57 ' || coalesce(v_j -> 'notes', 'null'::jsonb)::text || ' ' || coalesce(v_k -> 'notes', 'null'::jsonb)::text end;
  perform pg_temp.push(pg_temp.mk_pack('F-pack', v_today - 3, '[]'::jsonb, jsonb_build_object('notes', jsonb_build_array(
    jsonb_build_object('card', 'F-soon', 'text', 'Kept the hook short: TikTok cuts at 3 s')))));
  v_b := pg_temp.cardj(pg_temp.board('F-pack'), 'F-soon');
  perform pg_temp.decide(pg_temp.cid('F-pack', 'F-soon'), 1, 'request_change', 'Fine, but say sweldo in the first line', array['caption']);
  v_m := pg_temp.cardj(pg_temp.board('F-pack'), 'F-soon');
  r := r || case when v_b #>> '{latest_claude_note,note}' = 'Kept the hook short: TikTok cuts at 3 s' and v_m -> 'latest_claude_note' = 'null'::jsonb
                 then 'ok  M58 Claude''s latest note shows while it is newer than the owner''s last entry'
                 else 'FAIL M58 ' || coalesce(v_b -> 'latest_claude_note', 'null'::jsonb)::text end;

  -- ======================================================================== L: removing a note
  select id into v_id from mc.marketing_log where card_id = v_w1 and kind = 'changes' and note like 'Shorter caption%';
  select id into v_id2 from mc.marketing_log where card_id = v_w1 and kind = 'claude_note' order by id limit 1;
  v_j := jsonb_build_array(pg_temp.redact(v_id, 'Typed a name by mistake', s_stale) ->> 'error', pg_temp.redact(v_id, 'x') ->> 'error',
                           pg_temp.redact(v_id2, 'Not mine') ->> 'error',
                           pg_temp.redact((select id from mc.marketing_log where card_id = v_w1 and kind = 'created'), 'No note') ->> 'error');
  v_k := pg_temp.redact(v_id, 'Typed a name by mistake');
  v_m := pg_temp.redact(v_id, 'Again');
  r := r || case when v_j = '["step_up_required", "reason_required", "not_found", "not_found"]'::jsonb
                      and (v_k ->> 'ok')::boolean and (v_m ->> 'already')::boolean
                      and (select note from mc.marketing_log where id = v_id) = '[removed]'
                      and (select count(*) from mc.marketing_log where card_id = v_w1 and kind = 'note_redacted' and version = 1 and actor = 'admin') = 1
                      and exists (select 1 from mc.audit_log where action = 'marketing.redact_note' and reason = 'Typed a name by mistake'
                                  and target_ref = '2026-W48/W48-1-sweldo v1')
                      and not exists (select 1 from mc.audit_log where coalesce(reason, '') || coalesce(detail, '') || coalesce(before::text, '')
                                      || coalesce(after::text, '') like '%Shorter caption%')
                 then 'ok  M59 the owner removes one of their own notes with a fresh code and a reason: "[removed]", logged and audited, never copied'
                 else 'FAIL M59 ' || v_j::text || ' ' || coalesce(v_k::text, '') end;

  -- ======================================================================== M: the log and the versions don't change
  begin
    update mc.marketing_log set note = 'tampered' where id = v_id;
    r := r || 'FAIL M60 the log could be updated'::text;
  exception when insufficient_privilege then
    begin
      delete from mc.marketing_log where id = v_id;
      r := r || 'FAIL M60 the log could be deleted from'::text;
    exception when insufficient_privilege then
      begin
        truncate mc.marketing_log;
        r := r || 'FAIL M60 the log could be truncated'::text;
      exception when insufficient_privilege then
        begin
          -- Without the redaction function's switch, not even "[removed]".
          update mc.marketing_log set note = '[removed]' where id = v_id2;
          r := r || 'FAIL M60 a note could be removed without marketing_redact_note'::text;
        exception when insufficient_privilege then
          r := r || 'ok  M60 the log refuses UPDATE (even "[removed]" outside marketing_redact_note), DELETE and TRUNCATE, even for its owner'::text;
        end;
      end;
    end;
  end;
  perform set_config('mc.marketing_redact', 'on', true);
  begin
    update mc.marketing_log set kind = 'approved' where id = v_id;
    v_t := 'kind changed';
  exception when insufficient_privilege then
    begin
      update mc.marketing_log set note = 'something else' where id = v_id2;
      v_t := 'note rewritten';
    exception when insufficient_privilege then
      v_t := null;
    end;
  end;
  perform set_config('mc.marketing_redact', 'off', true);
  r := r || case when v_t is null then 'ok  M61 the redaction switch allows only setting a note to [removed], nothing else'
                 else 'FAIL M61 ' || v_t end;
  begin
    update mc.marketing_versions set caption = 'tampered' where card_id = v_w1;
    r := r || 'FAIL M62 a version could be changed'::text;
  exception when insufficient_privilege then
    r := r || 'ok  M62 a version never changes once written'::text;
  end;
  -- A post always points at versions that exist (checked at commit; forced here, since this suite never commits).
  begin
    update mc.marketing_cards set current_version = 20 where id = v_edge;
    set constraints mc.marketing_cards_current_version immediate;
    v_t := 'a post pointed at a version that doesn''t exist';
  exception when foreign_key_violation then
    v_t := null;
  end;
  set constraints mc.marketing_cards_current_version, mc.marketing_cards_in_apps_version deferred;
  r := r || case when v_t is null and (select current_version from mc.marketing_cards where id = v_edge) = 3
                 then 'ok  M62b a post''s current version must exist (a deferred foreign key, checked at commit)'
                 else 'FAIL M62b ' || coalesce(v_t, 'version changed') end;

  -- ======================================================================== N: Sunday's numbers
  v_j := pg_temp.numbers('2026-W48', '{"tiktok": 120, "instagram": 40, "youtube": null, "facebookPage": 15, "communityGroup": 33}',
                         '{"W48-1-sweldo": {"tiktok": 1500, "fbReels": 200, "igReels": null, "youtube": 90}}', null, null);
  v_b := pg_temp.board('2026-W48');
  r := r || case when (v_j ->> 'ok')::boolean and v_b #> '{numbers,followers}' = '{"tiktok": 120, "instagram": 40, "youtube": null, "facebookPage": 15, "communityGroup": 33}'::jsonb
                      and (v_b #>> '{numbers,views,W48-1-sweldo,tiktok}')::integer = 1500 and v_b #>> '{numbers,updated_by}' = 'Ops'
                      and exists (select 1 from mc.audit_log where action = 'marketing.numbers' and target_ref = '2026-W48' and result = 'ok')
                 then 'ok  M63 Sunday''s numbers are saved per channel and per post and platform, and audited'
                 else 'FAIL M63 ' || coalesce(v_j::text, '') end;
  v_j := jsonb_build_array(
    pg_temp.numbers('2026-W48', '{"TikTok": 1}', '{}') ->> 'error',
    pg_temp.numbers('2026-W48', '{"tiktok": 100000001}', '{}') ->> 'error',
    pg_temp.numbers('2026-W48', '{"tiktok": -1}', '{}') ->> 'error',
    pg_temp.numbers('2026-W48', '{"a": 1, "b": 1, "c": 1, "d": 1, "e": 1, "f": 1, "g": 1, "h": 1, "i": 1, "j": 1, "k": 1}', '{}') ->> 'error',
    pg_temp.numbers('2026-W48', '{}', '{"W48-nope": {"tiktok": 1}}') ->> 'error',
    pg_temp.numbers('2026-W48', '{}', '{"W48-1-sweldo": {"tiktok": 1.5}}') ->> 'error',
    pg_temp.numbers('2026-W48', '{}', '{"W48-1-sweldo": 5}') ->> 'error',
    pg_temp.numbers('2026-W48', '"many"', '{}') ->> 'error',
    pg_temp.numbers('2026-W48', '{}', '{}', -1, null) ->> 'error',
    pg_temp.numbers('2099-W01', '{}', '{}') ->> 'error');
  v_k := pg_temp.numbers('2026-W48', null, null, 12, 340);
  r := r || case when v_j = '["invalid", "invalid", "invalid", "invalid", "invalid", "invalid", "invalid", "invalid", "invalid", "not_found"]'::jsonb
                      and (v_k ->> 'ok')::boolean
                      and (select followers = '{}'::jsonb and views = '{}'::jsonb and installs = 12 and listing_visitors = 340
                           from mc.marketing_numbers n join mc.marketing_packs p on p.id = n.pack_id where p.key = '2026-W48')
                 then 'ok  M64 numbers are checked (channel names, whole numbers 0..100,000,000, at most 10 channels, posts of the pack); saving again replaces them'
                 else 'FAIL M64 ' || v_j::text end;

  -- ======================================================================== O: the navigation badge
  v_n := pg_temp.badge();
  perform pg_temp.push(pg_temp.mk_pack('2026-W48', v_p1_start, jsonb_build_array(
    pg_temp.mk_card('B-wait', v_today + 3, '{}'),
    pg_temp.mk_card('B-video', v_today + 3, jsonb_build_object('media', jsonb_build_array(
      pg_temp.mk_media('b-video.mp4', 'video/mp4', 700, 'video-badge')))))));
  v_x := pg_temp.badge();
  perform pg_temp.ready_one((select f.pathname from mc.marketing_files f where f.card_id = pg_temp.cid('2026-W48', 'B-video')), 700, 'video/mp4');
  v_y := pg_temp.badge();
  perform pg_temp.decide(pg_temp.cid('2026-W48', 'B-wait'), 1, 'approve');
  perform pg_temp.as_owner();
  v_j := mc_api.nav_badges();
  execute 'reset role';
  r := r || case when v_x = v_n + 1 and v_y = v_x + 1 and (v_j ->> 'marketing')::integer = v_y - 1
                      and v_j ? 'open_tickets' and v_j ? 'services_attention' and v_j ? 'records_view_enabled' and v_j ? 'extended_data_enabled'
                      and (pg_temp.summary() ->> 'waiting')::integer = v_y - 1
                 then 'ok  M65 the badge counts posts waiting with every file in and a passing truth line (Overview the same); the other badges are kept'
                 else 'FAIL M65 ' || v_n || ' ' || v_x || ' ' || v_y || ' ' || coalesce(v_j::text, '') end;

  -- ======================================================================== P: retention
  v_j := pg_temp.push(pg_temp.mk_pack('OLD-1', v_today - 406, jsonb_build_array(
    pg_temp.mk_card('old-a', v_today - 403, '{}'),
    pg_temp.mk_card('old-b', v_today - 403, jsonb_build_object('media', jsonb_build_array(
      pg_temp.mk_media('old.mp4', 'video/mp4', 900, 'video-old'))))),
    jsonb_build_object('notes', jsonb_build_array(jsonb_build_object('card', 'old-a', 'text', 'An old note')))));
  perform pg_temp.decide(pg_temp.cid('OLD-1', 'old-a'), 1, 'approve');
  perform pg_temp.numbers('OLD-1', '{"tiktok": 1}', '{}');
  -- Ended 365 days ago (not more): kept.
  perform pg_temp.push(pg_temp.mk_pack('OLD-2', v_today - 371, jsonb_build_array(pg_temp.mk_card('old-c', v_today - 368, '{}'))));
  v_n := mc.marketing_retention();
  r := r || case when v_n = 1 and not exists (select 1 from mc.marketing_packs where key = 'OLD-1')
                      and exists (select 1 from mc.marketing_cards c join mc.marketing_packs p on p.id = c.pack_id
                                  where p.key = 'OLD-2' and p.ends_on = v_today - 365 and c.key = 'old-c')
                      and not exists (select 1 from mc.marketing_cards where key in ('old-a', 'old-b'))
                      and not exists (select 1 from mc.marketing_files where pathname like 'marketing/test/OLD-1/%')
                      and not exists (select 1 from mc.marketing_log l where l.card_id is not null
                                      and not exists (select 1 from mc.marketing_cards c where c.id = l.card_id))
                      and (select count(*) from mc.marketing_numbers) = 1 and exists (select 1 from mc.marketing_packs where key = '2026-W48')
                      and (select count(*) from mc.marketing_versions v where not exists (select 1 from mc.marketing_cards c where c.id = v.card_id)) = 0
                      and (select count(*) from mc.marketing_media m where not exists (select 1 from mc.marketing_versions v
                                                                                     where v.card_id = m.card_id and v.version = m.version)) = 0
                      and exists (select 1 from mc.job_runs where job = 'marketing_retention' and result = 'ok' and detail = '{"packs_deleted": 1}'::jsonb)
                      and (select count(*) from mc.marketing_log where kind = 'launch_meta') = 1
                 then 'ok  M66 retention: a pack that ended over 365 days ago goes with its posts, versions, files, numbers and log (the cascade passes the log''s guard)'
                 else 'FAIL M66 deleted=' || v_n end;

  -- ======================================================================== Q: limits
  delete from mc.rate_counters where action = 'ingest.marketing_pack';
  v_j := pg_temp.push(pg_temp.mk_pack('2026-W48', v_p1_start, jsonb_build_array(pg_temp.mk_card('Q-big', v_today, '{}')),
    jsonb_build_object('padding', (select string_agg(md5(g::text), '') from generate_series(1, 70000) g))));
  v_k := pg_temp.push(pg_temp.mk_pack('2026-W48', v_p1_start,
    (select jsonb_agg(pg_temp.mk_card('Q-' || g, v_today, '{}')) from generate_series(1, 41) g)));
  v_m := pg_temp.push(pg_temp.mk_pack('2026-W48', v_p1_start, jsonb_build_array(
    pg_temp.mk_card('W48-edge', v_p1_start - 7, jsonb_build_object('version', 21, 'changes', 'One more')))));
  r := r || case when v_j ->> 'error' = 'quota' and v_k ->> 'error' = 'quota' and pg_temp.res(v_m, 'W48-edge') ->> 'reason' = 'quota'
                      and not exists (select 1 from mc.marketing_cards where key like 'Q-%')
                 then 'ok  M67 a push over 1 MB or of more than 40 posts, and a 21st version, are refused (quota)'
                 else 'FAIL M67 ' || coalesce(v_j ->> 'error', '') || ' ' || coalesce(v_k ->> 'error', '') || ' ' || coalesce(pg_temp.res(v_m, 'W48-edge')::text, '') end;
  v_j := pg_temp.push(pg_temp.mk_pack('S-pack', v_today - 10, jsonb_build_array(
    pg_temp.mk_card('S1', v_today - 9, jsonb_build_object('media',
      (select jsonb_agg(pg_temp.mk_media('s1-' || g || '.mp4', 'video/mp4', 104857600, 'big-s1-' || g)) from generate_series(1, 4) g))),
    pg_temp.mk_card('S2', v_today - 9, jsonb_build_object('media',
      (select jsonb_agg(pg_temp.mk_media('s2-' || g || '.mp4', 'video/mp4', 104857600, 'big-s2-' || g)) from generate_series(1, 4) g))))));
  v_k := pg_temp.push(pg_temp.mk_pack('S-pack', v_today - 10, jsonb_build_array(
    pg_temp.mk_card('S3', v_today - 9, jsonb_build_object('media',
      (select jsonb_agg(pg_temp.mk_media('s3-' || g || '.mp4', 'video/mp4', 104857600, 'big-s3-' || g)) from generate_series(1, 4) g))))));
  r := r || case when pg_temp.res(v_j, 'S1') ->> 'result' = 'created' and pg_temp.res(v_j, 'S2') ->> 'reason' = 'quota'
                      and pg_temp.res(v_k, 'S3') ->> 'reason' = 'quota'
                      and not exists (select 1 from mc.marketing_cards where key in ('S2', 'S3'))
                      and (select count(*) from mc.marketing_files where pathname like 'marketing/test/S-pack/%') = 4
                 then 'ok  M68 the storage budget (700 MB of files kept, this push''s new ones included) refuses the post that would go over it'
                 else 'FAIL M68 ' || left(pg_temp.reasons(v_j)::text || ' ' || pg_temp.reasons(v_k)::text, 300) end;
  -- Files that can never arrive hold no room: those of a post taken out, of a skipped one, of a version Claude replaced.
  v_j := pg_temp.push(pg_temp.mk_pack('S-pack', v_today - 10, jsonb_build_array(
    pg_temp.mk_card('S1', v_today - 9, jsonb_build_object('withdrawn', true, 'withdrawn_reason', 'Its videos never arrived')),
    pg_temp.mk_card('S4', v_today - 9, jsonb_build_object('media',
      (select jsonb_agg(pg_temp.mk_media('s4-' || g || '.mp4', 'video/mp4', 104857600, 'big-s4-' || g)) from generate_series(1, 4) g))))));
  select array_agg(f.pathname order by f.pathname) into v_paths from mc.marketing_files f where f.card_id = pg_temp.cid('S-pack', 'S1');
  r := r || case when pg_temp.res(v_j, 'S1') ->> 'result' = 'withdrawn' and pg_temp.res(v_j, 'S4') ->> 'result' = 'created'
                      and jsonb_array_length(v_j -> 'uploads') = 4
                      and (select bool_and(u ->> 'card' = 'S4') from jsonb_array_elements(v_j -> 'uploads') u)
                      and cardinality(v_paths) = 4 and jsonb_array_length(pg_temp.pending(v_paths) -> 'pending') = 0
                 then 'ok  M68b a post taken out before its videos arrived frees their room: the next 400 MB post fits'
                 else 'FAIL M68b ' || left(pg_temp.reasons(v_j)::text, 300) end;
  v_g := pg_temp.cid('S-pack', 'S4');
  select array_agg(u ->> 'pathname' order by u ->> 'pathname') into v_paths from jsonb_array_elements(v_j -> 'uploads') u;
  v_q := pg_temp.mk_card('S4', v_today - 9, jsonb_build_object('media',
    (select jsonb_agg(pg_temp.mk_media('s4-' || g || '.mp4', 'video/mp4', 104857600, 'big-s4-' || g)) from generate_series(1, 4) g)));
  perform pg_temp.decide(v_g, 1, 'skip');
  v_k := pg_temp.push(pg_temp.mk_pack('S-pack', v_today - 10, jsonb_build_array(v_q,
    pg_temp.mk_card('S5', v_today - 9, jsonb_build_object('media',
      (select jsonb_agg(pg_temp.mk_media('s5-' || g || '.mp4', 'video/mp4', 104857600, 'big-s5-' || g)) from generate_series(1, 4) g))))));
  v_m := pg_temp.pending(v_paths);
  perform pg_temp.decide(v_g, 1, 'undo');
  v_b := pg_temp.push(pg_temp.mk_pack('S-pack', v_today - 10, jsonb_build_array(v_q,
    pg_temp.mk_card('S6', v_today - 9, jsonb_build_object('media', jsonb_build_array(pg_temp.mk_media('s6.png', 'image/png', 1000, 'img-s6')))))));
  r := r || case when pg_temp.res(v_k, 'S4') ->> 'result' = 'unchanged' and pg_temp.res(v_k, 'S4') ->> 'state' = 'skipped'
                      and pg_temp.res(v_k, 'S5') ->> 'result' = 'created'
                      and not exists (select 1 from jsonb_array_elements(v_k -> 'uploads') u where u ->> 'card' = 'S4')
                      and jsonb_array_length(v_m -> 'pending') = 0
                      and pg_temp.res(v_b, 'S4') ->> 'state' = 'waiting'
                      and (select array_agg(u ->> 'pathname' order by u ->> 'pathname') from jsonb_array_elements(v_b -> 'uploads') u
                           where u ->> 'card' = 'S4') = v_paths
                      and jsonb_array_length(pg_temp.pending(v_paths) -> 'pending') = 4
                      and pg_temp.res(v_b, 'S6') ->> 'reason' = 'quota'
                 then 'ok  M68c a skipped post''s videos that never arrived are no longer asked for and hold no room; "Bring it back" asks for the same files again and counts them'
                 else 'FAIL M68c ' || left(pg_temp.reasons(v_k)::text || ' ' || pg_temp.reasons(v_b)::text || ' ' || coalesce(v_m::text, ''), 400) end;
  v_k := pg_temp.push(pg_temp.mk_pack('S-pack', v_today - 10, jsonb_build_array(
    pg_temp.mk_card('S5', v_today - 9, jsonb_build_object('version', 2, 'changes', 'One picture instead of four videos',
      'media', jsonb_build_array(pg_temp.mk_media('s5.png', 'image/png', 1000, 'img-s5')))),
    pg_temp.mk_card('S7', v_today - 9, jsonb_build_object('media',
      (select jsonb_agg(pg_temp.mk_media('s7-' || g || '.mp4', 'video/mp4', 104857600, 'big-s7-' || g)) from generate_series(1, 2) g))))));
  select array_agg(f.pathname order by f.pathname) into v_paths from mc.marketing_files f
    join mc.marketing_media m on m.file_id = f.id where m.card_id = pg_temp.cid('S-pack', 'S5') and m.version = 1;
  r := r || case when pg_temp.res(v_k, 'S5') ->> 'result' = 'revised' and pg_temp.res(v_k, 'S7') ->> 'result' = 'created'
                      and cardinality(v_paths) = 4 and jsonb_array_length(pg_temp.pending(v_paths) -> 'pending') = 0
                      and (select count(*) from jsonb_array_elements(v_k -> 'uploads') u where u ->> 'card' = 'S5') = 1
                 then 'ok  M68d a new version without the videos that never arrived frees their room (they are no longer asked for)'
                 else 'FAIL M68d ' || left(pg_temp.reasons(v_k)::text, 300) end;
  update mc.marketing_files set deleted_at = now() where pathname like 'marketing/test/S-pack/%';
  v_j := pg_temp.push(pg_temp.mk_pack('2026-W48', v_p1_start, jsonb_build_array(pg_temp.mk_card('Q-env', v_today, '{}'))), 'local');
  v_k := pg_temp.push(pg_temp.mk_pack('2026-W48', v_p1_start, jsonb_build_array(pg_temp.mk_card('Q-env', v_today, '{}'))), 'staging');
  r := r || case when v_j ->> 'error' = 'wrong_environment' and v_k ->> 'error' = 'invalid'
                      and not exists (select 1 from mc.marketing_cards where key = 'Q-env')
                 then 'ok  M69 a push for another environment than the stored files'' is refused; unknown environments too'
                 else 'FAIL M69 ' || coalesce(v_j::text, '') || ' ' || coalesce(v_k::text, '') end;
  -- Another environment's file that can never arrive (here on a post taken out) doesn't block a push; one still to come
  -- (on a post waiting for the owner) does. Both rows are fixtures, removed again right after.
  insert into mc.marketing_files (card_id, pathname, file_name, content_type, bytes, sha256)
    values (v_text, 'marketing/local/2026-W48/W48-group-wed/aaaaaaaaaaaa/x.png', 'x.png', 'image/png', 1000, repeat('a', 64))
    returning id into v_fid;
  insert into mc.marketing_media (card_id, version, position, file_id) values (v_text, 1, 9, v_fid);
  v_j := pg_temp.push(pg_temp.mk_pack('2026-W48', v_p1_start, '[]'::jsonb));
  insert into mc.marketing_files (card_id, pathname, file_name, content_type, bytes, sha256)
    values (pg_temp.cid('2026-W48', 'B-video'), 'marketing/local/2026-W48/B-video/bbbbbbbbbbbb/x.png', 'x.png', 'image/png', 1000,
            repeat('b', 64))
    returning id into v_fid;
  insert into mc.marketing_media (card_id, version, position, file_id) values (pg_temp.cid('2026-W48', 'B-video'), 1, 9, v_fid);
  v_k := pg_temp.push(pg_temp.mk_pack('2026-W48', v_p1_start, '[]'::jsonb));
  delete from mc.marketing_media where position = 9 and card_id in (v_text, pg_temp.cid('2026-W48', 'B-video'));
  delete from mc.marketing_files where pathname like 'marketing/local/%';
  r := r || case when (v_j ->> 'ok')::boolean and v_k ->> 'error' = 'wrong_environment'
                 then 'ok  M69b only files that hold room in a store or may still take it count as another environment''s: one that can never arrive doesn''t block'
                 else 'FAIL M69b ' || coalesce(v_j::text, '') || ' ' || coalesce(v_k::text, '') end;
  insert into mc.rate_counters (admin_id, action, hour, count)
    values ('00000000-0000-0000-0000-000000000000', 'ingest.marketing_pack', date_trunc('hour', now()), 30)
    on conflict (admin_id, action, hour) do update set count = 30;
  v_j := pg_temp.push(pg_temp.mk_pack('2026-W48', v_p1_start, jsonb_build_array(pg_temp.mk_card('Q-hour', v_today, '{}'))));
  r := r || case when v_j ->> 'error' = 'quota' and not exists (select 1 from mc.marketing_cards where key = 'Q-hour')
                 then 'ok  M70 the 31st push in an hour is refused (quota)' else 'FAIL M70 ' || coalesce(v_j::text, '') end;
  delete from mc.rate_counters where action = 'ingest.marketing_pack';

  -- ======================================================================== R: the board and the file route
  v_b := pg_temp.board(null);
  select string_agg(c.key, ',' order by c.slot_day nulls last, c.slot_time nulls last, c.position, c.key) into v_t
    from mc.marketing_cards c join mc.marketing_packs p on p.id = c.pack_id where p.key = '2026-W48';
  r := r || case when (v_b ->> 'ok')::boolean and v_b #>> '{pack,key}' = '2026-W48' and v_b ->> 'environment' = 'test'
                      and v_b #>> '{packs,0,key}' = '2026-W48' and jsonb_array_length(v_b -> 'packs') = 4
                      and v_b #>> '{packs,3,key}' = 'OLD-2'
                      and (v_b #>> '{packs,0,counts,total}')::integer = (select count(*) from mc.marketing_cards c join mc.marketing_packs p on p.id = c.pack_id where p.key = '2026-W48')
                      and (v_b #>> '{packs,0,counts,withdrawn}')::integer = 1
                      and (select string_agg(x ->> 'key', ',' order by o) from jsonb_array_elements(v_b -> 'cards') with ordinality t(x, o)) = v_t
                      and v_b ->> 'tz' = 'Asia/Manila' and (v_b ->> 'claude_checked_at')::timestamptz = now()
                      and pg_temp.cardj(v_b, 'W48-post-tue') #>> '{media,1,file_name}' = 'post-3.png'
                      and pg_temp.cardj(v_b, 'W48-post-tue') #>> '{version,truth}' = 'pass. T1 (not called AI), T3'
                      and jsonb_array_length(pg_temp.cardj(v_b, 'W48-1-sweldo') -> 'log') between 1 and 30
                      and pg_temp.cardj(v_b, 'W48-1-sweldo') #>> '{log,0,by}' is not null
                 then 'ok  M71 the board: the newest pack with posts waiting by default, packs newest first with counts, posts in slot order with versions, files and history'
                 else 'FAIL M71 ' || left(v_b::text, 600) end;
  v_j := pg_temp.board('2099-W01');
  select f.id into v_file_a from mc.marketing_files f where f.pathname = v_path_a;
  v_k := pg_temp.file(v_file_a);
  v_m := pg_temp.file(gen_random_uuid());
  r := r || case when v_j ->> 'error' = 'not_found' and (v_k ->> 'ok')::boolean and v_k ->> 'pathname' = v_path_a
                      and v_k ->> 'file_name' = 'W48-1-sweldo.mp4' and (v_k ->> 'ready')::boolean and not (v_k ->> 'deleted')::boolean
                      and v_m ->> 'error' = 'not_found'
                      and (pg_temp.file((select id from mc.marketing_files where pathname = v_path_c)) ->> 'deleted')::boolean
                 then 'ok  M72 an unknown pack is not_found; the download route gets a file''s pathname and name, and whether it is ready or gone'
                 else 'FAIL M72 ' || coalesce(v_k::text, '') end;

  -- ======================================================================== S: the audit
  select string_agg(a, ',' order by a) into v_t from (
    select distinct substr(action, 11) as a from mc.audit_log where action like 'marketing.%' and result = 'ok') x;
  r := r || case when v_t = 'approve,in_apps,launch_cleared,launch_set,media_deleted,numbers,posted_by_hand,redact_note,removed_from_apps,request_change,skip,undo'
                      and not exists (select 1 from mc.audit_log where action like 'marketing.%' and target_user_id is not null)
                      and not exists (select 1 from mc.audit_log where action in ('marketing.approve', 'marketing.request_change', 'marketing.skip',
                                        'marketing.undo', 'marketing.in_apps', 'marketing.removed_from_apps', 'marketing.posted_by_hand')
                                      and target_ref !~ '^[^/]+/[^ ]+ v[0-9]+$')
                      and exists (select 1 from mc.audit_log where action = 'marketing.request_change' and admin_id = v_admin
                                  and target_ref = '2026-W48/W48-1-sweldo v1' and detail = 'caption,timing')
                      and not exists (select 1 from mc.audit_log
                                      where coalesce(reason, '') || coalesce(detail, '') || coalesce(before::text, '') || coalesce(after::text, '')
                                            similar to '%(brighter end card|Also the hook|Change the hook|say sweldo)%')
                 then 'ok  M73 every owner action is audited as marketing.<action> with "<pack>/<card> v<n>", never with the note''s words'
                 else 'FAIL M73 ' || coalesce(v_t, 'none') end;

  raise exception E'VERIFY %/% failed\n%',
    (select count(*) from unnest(r) x where x like 'FAIL%'), array_length(r, 1), array_to_string(r, E'\n');
end
$verify$;
