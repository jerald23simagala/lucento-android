-- Mission Control: auto-posting for Marketing (supabase/migrations/20261118090000_mission_control_autopost.sql,
-- docs/mission-control/v1/MARKETING_AUTOPOST.md). Run with tools/sql-verify (see README.md):
--   node run.mjs ../../supabase/migrations autopost_suite.sql
-- Plays the owner (fresh and hour-old authenticator codes), a customer, anon, and the console's posting run (anon + the
-- tick secret + the app secret) against every rule: who may call what, the label-to-platform map, due times, connections
-- and the destinations picked from them, the jobs' life (created within their lead time, stepped under a lease, retried,
-- unconfirmed, missed, cancelled when the post is undone or revised), the post marked out with what the owner finishes
-- by hand, due_soon left out for posts Mission Control will post, turning it on and off, the clock's guards, the board,
-- the summary and the audit. Raises one exception listing every check; "VERIFY 0/N failed" is a pass.
do $verify$
declare
  r text[] := '{}';
  v_admin uuid := gen_random_uuid();
  v_cust uuid := gen_random_uuid();
  s_fresh uuid := gen_random_uuid();
  s_stale uuid := gen_random_uuid();
  v_local timestamp := now() at time zone 'Asia/Manila';
  v_today date := (now() at time zone 'Asia/Manila')::date;
  v_mkt text; v_app text; v_tick text;
  v_j jsonb; v_k jsonb; v_m jsonb; v_b jsonb; v_s jsonb;
  v_t text; v_n integer; v_ok boolean;
  v_fn record; v_sql text;
  v_c1 uuid; v_c2 uuid; v_c3 uuid; v_c4 uuid; v_c5 uuid; v_c6 uuid;
  v_job uuid; v_job2 uuid; v_lease uuid; v_lease2 uuid;
  v_cipher text := 'v1:AAAAAAAAAAAAAAAA:ZmFrZS10b2tlbg:AAAAAAAAAAAAAAAAAAAAAA';
  v_page_cipher text := 'v1:BBBBBBBBBBBBBBBB:cGFnZS10b2tlbg:BBBBBBBBBBBBBBBBBBBBBB';
  v_klaro_cipher text := 'v1:CCCCCCCCCCCCCCCC:a2xhcm8tdG9rZW4:CCCCCCCCCCCCCCCCCCCCCC';
  v_soon_day date; v_soon_time text;
  v_far_day date; v_far_time text;
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
  create function pg_temp.as_owner(p_session uuid default null) returns void language plpgsql as $f$
  begin
    perform pg_temp.as_admin(current_setting('verify.admin')::uuid, coalesce(p_session, current_setting('verify.fresh')::uuid),
      'aal2', interval '1 hour');
  end $f$;
  create function pg_temp.mk_card(p_key text, p_day date, p_time text, p_over jsonb default '{}'::jsonb) returns jsonb language sql as $f$
    select jsonb_build_object('key', p_key, 'position', 10, 'kind', 'short', 'phase', 'any', 'title', 'Post ' || p_key,
      'slot_day', p_day, 'slot_time', p_time, 'slot_label', 'Slot', 'platforms', jsonb_build_array('TikTok', 'FB + IG Reels', 'YouTube Shorts'),
      'version', 1, 'changes', null, 'revive', false, 'withdrawn', false, 'withdrawn_reason', null,
      'caption_label', 'Caption', 'caption', 'Sweldo day? Sorted.', 'yt_title', 'Sweldo day', 'yt_desc', 'Track it.',
      'alt', null, 'truth', 'pass. T1 (not called AI), T3', 'media', '[]'::jsonb) || p_over
  $f$;
  create function pg_temp.push(p_cards jsonb) returns jsonb language plpgsql as $f$
  declare v jsonb;
  begin
    perform pg_temp.as_anon();
    v := mc_api.ingest_marketing_pack(current_setting('verify.mkt'), 'test',
      jsonb_build_object('pack', jsonb_build_object('key', '2099-W01', 'title', 'Week test',
        'starts_on', current_setting('verify.today')::date - 2, 'ends_on', current_setting('verify.today')::date + 4),
        'cards', p_cards));
    execute 'reset role';
    return v;
  end $f$;
  create function pg_temp.cid(p_key text) returns uuid language sql as $f$
    select c.id from mc.marketing_cards c where c.key = p_key
  $f$;
  create function pg_temp.decide(p_card uuid, p_version integer, p_decision text) returns jsonb language plpgsql as $f$
  declare v jsonb;
  begin
    perform pg_temp.as_owner();
    v := mc_api.marketing_decide(p_card, p_version, p_decision, null, null);
    execute 'reset role';
    return v;
  end $f$;
  create function pg_temp.owner_call(p_sql text, p_session uuid default null) returns jsonb language plpgsql as $f$
  declare v jsonb;
  begin
    perform pg_temp.as_owner(p_session);
    execute p_sql into v;
    execute 'reset role';
    return v;
  end $f$;
  create function pg_temp.claim(p_limit integer default 10) returns jsonb language plpgsql as $f$
  declare v jsonb;
  begin
    perform pg_temp.as_anon();
    v := mc_api.ingest_autopost_claim(current_setting('verify.tick'), current_setting('verify.app'), p_limit);
    execute 'reset role';
    return v;
  end $f$;
  create function pg_temp.result(p_job uuid, p_lease uuid, p_result jsonb) returns jsonb language plpgsql as $f$
  declare v jsonb;
  begin
    perform pg_temp.as_anon();
    v := mc_api.ingest_autopost_result(current_setting('verify.app'), p_job, p_lease, p_result);
    execute 'reset role';
    return v;
  end $f$;
  create function pg_temp.board() returns jsonb language plpgsql as $f$
  declare v jsonb;
  begin
    perform pg_temp.as_owner();
    v := mc_api.marketing_board('2099-W01');
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
  create function pg_temp.cardj(p_board jsonb, p_key text) returns jsonb language sql as $f$
    select x from jsonb_array_elements(p_board -> 'cards') x where x ->> 'key' = p_key
  $f$;
  create function pg_temp.job(p_card uuid, p_target text) returns mc.autopost_jobs language sql as $f$
    select j.* from mc.autopost_jobs j where j.card_id = p_card and j.target = p_target order by j.version desc limit 1
  $f$;
  create function pg_temp.claimed(p_claim jsonb, p_card_key text, p_target text) returns jsonb language sql as $f$
    select x from jsonb_array_elements(p_claim -> 'jobs') x where x #>> '{card,key}' = p_card_key and x ->> 'target' = p_target
  $f$;
  create function pg_temp.due_soon(p_card uuid) returns boolean language sql as $f$
    select s.due_soon from mc.marketing_status(mc.marketing_pday(), p_card) s
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
  perform set_config('verify.today', v_today::text, true);
  -- A slot 15 minutes ahead (Reels may start) and one 10 hours ahead (nothing yet), in Manila time.
  v_soon_day := (v_local + interval '15 minutes')::date;
  v_soon_time := to_char(v_local + interval '15 minutes', 'HH24:MI');
  v_far_day := (v_local + interval '10 hours')::date;
  v_far_time := to_char(v_local + interval '10 hours', 'HH24:MI');

  -- ======================================================================== A: secrets and who may call what
  v_mkt := mc.rotate_ingest_secret('marketing');
  v_app := mc.rotate_ingest_secret('app');
  v_tick := mc.rotate_ingest_secret('autopost');
  perform set_config('verify.mkt', v_mkt, true);
  perform set_config('verify.app', v_app, true);
  perform set_config('verify.tick', v_tick, true);
  begin
    perform mc.rotate_ingest_secret('posting');
    v_t := 'accepted';
  exception when sqlstate '22023' then
    v_t := sqlerrm;
  end;
  r := r || case when v_tick ~ '^[0-9a-f]{64}$' and v_tick not in (v_app, v_mkt)
                      and v_t like '%purpose is app, backup, marketing or autopost%'
                      and (select value from mc.settings where key = 'autopost_enabled') = 'false'::jsonb
                 then 'ok  P1 a fourth secret purpose, autopost (the clock); auto-posting starts off'
                 else 'FAIL P1 ' || coalesce(v_t, 'null') end;

  v_t := null;
  v_n := 0;
  for v_fn in select p.proname, p.pronargs from pg_proc p join pg_namespace s on s.oid = p.pronamespace
           where s.nspname = 'mc_api' and p.proname like 'autopost\_%' order by p.proname
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
  r := r || case when v_t is null and v_n = 10 then 'ok  P2 all 10 auto-posting page functions refuse a customer first: not_admin'
                 else 'FAIL P2 functions=' || v_n || ' ' || coalesce(v_t, '') end;

  v_t := null;
  for v_fn in select p.proname, p.pronargs from pg_proc p join pg_namespace s on s.oid = p.pronamespace
           where s.nspname = 'mc_api' and p.proname like 'autopost\_%' order by p.proname
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
  for v_fn in select p.proname, p.pronargs from pg_proc p join pg_namespace s on s.oid = p.pronamespace
           where s.nspname = 'mc_api' and p.proname like 'ingest\_autopost\_%' order by p.proname
  loop
    v_sql := format('select mc_api.%I(%s)', v_fn.proname, array_to_string(array_fill('null'::text, array[v_fn.pronargs]), ', '));
    perform pg_temp.as_owner();
    begin
      execute v_sql into v_j;
      v_t := coalesce(v_t || ', ', '') || 'signed-in:' || v_fn.proname;
    exception when insufficient_privilege then
      null;
    end;
    execute 'reset role';
  end loop;
  r := r || case when v_t is null then 'ok  P3 anon can''t call the page functions, and signed-in callers can''t call the 3 ingest functions'
                 else 'FAIL P3 ' || v_t end;

  perform pg_temp.as_anon();
  v_j := mc_api.ingest_autopost_claim(v_mkt, v_app, 4);
  v_k := mc_api.ingest_autopost_claim(v_tick, v_mkt, 4);
  v_m := mc_api.ingest_autopost_result(v_tick, gen_random_uuid(), gen_random_uuid(), '{"status": "posted"}');
  v_b := mc_api.ingest_autopost_token(v_mkt, 'google', 'g-1', v_cipher, now(), null, null, null);
  v_s := mc_api.ingest_autopost_claim(v_tick, v_app, 4);
  execute 'reset role';
  r := r || case when v_j ->> 'error' = 'bad_secret' and v_k ->> 'error' = 'bad_secret' and v_m ->> 'error' = 'bad_secret'
                      and v_b ->> 'error' = 'bad_secret' and (v_s ->> 'ok')::boolean and not (v_s ->> 'enabled')::boolean
                      and (select last_claim_at from mc.autopost_state where id) = now()
                 then 'ok  P4 the claim needs both the tick secret and the app secret, results and tokens the app secret; while off it hands out nothing'
                 else 'FAIL P4 ' || coalesce(v_j::text, '') || coalesce(v_s::text, '') end;

  -- ======================================================================== B: the map and the clock's times
  select string_agg(coalesce(t.target, '-') || '@' || coalesce(t.destination, '-') || '/' || t.mode, ',' order by t.ord, t.target)
    into v_t
    from mc.autopost_targets(array['TikTok', ' fb + IG reels ', 'YouTube Shorts', 'Lucento Community group',
                                   'Lucento Facebook Page', 'KlaroPH Facebook Page', 'Threads']) t;
  r := r || case when v_t = 'tiktok@tiktok/draft,facebook_reel@lucento_page/auto,instagram_reel@instagram/auto,youtube@youtube/auto,'
                            || '-@-/manual,facebook_photo@lucento_page/auto,klaroph_photo@klaroph_page/auto,-@-/manual'
                 then 'ok  P5 labels map to targets: TikTok to drafts, FB + IG Reels to two, YouTube, both Pages; the group and unknown labels stay by hand'
                 else 'FAIL P5 ' || coalesce(v_t, 'null') end;

  r := r || case when mc.autopost_due_at('2026-11-23', '19:30', 'Asia/Manila') = '2026-11-23 11:30:00+00'::timestamptz
                      and mc.autopost_due_at('2026-11-25', null, 'Asia/Manila') = '2026-11-25 02:00:00+00'::timestamptz
                      and mc.autopost_late_until('2026-11-23', '19:30', 'Asia/Manila') = '2026-11-23 14:30:00+00'::timestamptz
                      and mc.autopost_late_until('2026-11-25', null, 'Asia/Manila') = '2026-11-25 16:00:00+00'::timestamptz
                      and mc.autopost_lead('instagram_reel') = interval '20 minutes' and mc.autopost_lead('youtube') = interval '0'
                 then 'ok  P6 due at the slot (10:00 for "any time"), late until 3 hours after it (the day''s end for "any time"); Reels lead by 20 minutes'
                 else 'FAIL P6' end;

  -- ======================================================================== C: connections and destinations
  v_j := pg_temp.owner_call(format('select mc_api.autopost_save_connection(%L, %L, %L, %L, null, null, null, %L, %L)',
           'meta', '1001', 'Jerald', v_cipher, '{pages_manage_posts}', '[]'), s_stale);
  v_k := pg_temp.owner_call('select mc_api.autopost_set_enabled(true)', s_stale);
  v_m := pg_temp.owner_call('select mc_api.autopost_set_enabled(false)', s_stale);
  r := r || case when v_j ->> 'error' = 'step_up_required' and v_k ->> 'error' = 'step_up_required' and (v_m ->> 'ok')::boolean
                 then 'ok  P7 connecting and turning on ask for a fresh authenticator code; turning off never does'
                 else 'FAIL P7 ' || coalesce(v_j::text, '') || coalesce(v_k::text, '') || coalesce(v_m::text, '') end;

  v_j := pg_temp.owner_call(format('select mc_api.autopost_save_connection(%L, %L, %L, %L, null, null, null, %L, %L::jsonb)',
           'meta', '1001', 'Jerald', 'not-a-cipher', '{pages_manage_posts}', '[]'));
  v_k := pg_temp.owner_call(format('select mc_api.autopost_save_connection(%L, %L, %L, %L, null, null, null, %L, %L::jsonb)',
           'meta', '1001', 'Jerald', v_cipher, '{pages_manage_posts}',
           jsonb_build_array(jsonb_build_object('kind', 'instagram', 'id', '17841', 'name', 'lucentoapp', 'page_id', '501',
                                                'token_cipher', v_page_cipher))));
  v_m := pg_temp.owner_call(format('select mc_api.autopost_save_connection(%L, %L, %L, %L, null, null, null, %L, %L::jsonb)',
           'google', '1001', 'Jerald', v_cipher, '{}', jsonb_build_array(jsonb_build_object('kind', 'page', 'id', '9',
                                                'name', 'x', 'token_cipher', v_page_cipher))));
  r := r || case when v_j ->> 'error' = 'invalid' and v_k ->> 'error' = 'invalid' and v_m ->> 'error' = 'invalid'
                      and not exists (select 1 from mc.autopost_connections)
                 then 'ok  P8 a token that isn''t ciphertext, a token on anything but a Page, or a kind the provider doesn''t have is refused'
                 else 'FAIL P8 ' || coalesce(v_j::text, '') end;

  v_j := pg_temp.owner_call('select mc_api.autopost_set_enabled(true)');
  update mc.autopost_state set clock_set_at = now() where id;
  v_k := pg_temp.owner_call('select mc_api.autopost_set_enabled(true)');
  r := r || case when v_j ->> 'error' = 'clock_not_set' and v_k ->> 'error' = 'nothing_connected'
                 then 'ok  P9 turning on needs the posting clock set up, then at least one connected account'
                 else 'FAIL P9 ' || coalesce(v_j::text, '') || coalesce(v_k::text, '') end;

  v_j := pg_temp.owner_call(format('select mc_api.autopost_save_connection(%L, %L, %L, %L, null, %L, null, %L, %L::jsonb)',
           'meta', '1001', 'Jerald', v_cipher, (now() + interval '60 days')::text,
           '{pages_show_list,pages_read_engagement,pages_manage_posts,instagram_basic,instagram_content_publish}',
           jsonb_build_array(
             jsonb_build_object('kind', 'page', 'id', '501', 'name', 'Lucento', 'token_cipher', v_page_cipher),
             jsonb_build_object('kind', 'page', 'id', '502', 'name', 'KlaroPH', 'token_cipher', v_klaro_cipher),
             jsonb_build_object('kind', 'page', 'id', '503', 'name', 'Jerald''s Bakery', 'token_cipher', v_klaro_cipher),
             jsonb_build_object('kind', 'instagram', 'id', '17841', 'name', 'lucentoapp', 'page_id', '501'),
             jsonb_build_object('kind', 'instagram', 'id', '17842', 'name', 'bakery', 'page_id', '503'))));
  r := r || case when (v_j ->> 'ok')::boolean
                      and (select external_id || '/' || token_cipher from mc.autopost_destinations where key = 'lucento_page') = '501/' || v_page_cipher
                      and (select external_id || '/' || token_cipher from mc.autopost_destinations where key = 'klaroph_page') = '502/' || v_klaro_cipher
                      and (select external_id || '/' || token_cipher from mc.autopost_destinations where key = 'instagram') = '17841/' || v_page_cipher
                      and v_j #>> '{destinations,instagram,name}' = 'lucentoapp'
                      and position('v1:' in v_j::text) = 0 and position('token_cipher' in v_j::text) = 0
                 then 'ok  P10 a Meta account picks the Lucento Page, the KlaroPH Page and the Instagram account linked to Lucento (posted with that Page''s token); the answer shows no token'
                 else 'FAIL P10 ' || left(coalesce(v_j::text, 'null'), 600) end;

  v_j := pg_temp.owner_call(format('select mc_api.autopost_save_connection(%L, %L, %L, %L, %L, %L, null, %L, %L::jsonb)',
           'google', 'g-1', 'Lucento', v_cipher, v_cipher, (now() + interval '1 hour')::text, '{}',
           jsonb_build_array(jsonb_build_object('kind', 'youtube', 'id', 'UCg8f6wbVTgYPnXBeMvD0FyA', 'name', 'Lucento'))));
  v_k := pg_temp.owner_call(format('select mc_api.autopost_save_connection(%L, %L, %L, %L, %L, %L, %L, %L, %L::jsonb)',
           'tiktok', 'open-1', 'lucentoapp', v_cipher, v_cipher, (now() + interval '1 day')::text, (now() + interval '365 days')::text,
           '{user.info.basic,video.upload}', jsonb_build_array(jsonb_build_object('kind', 'tiktok', 'id', 'open-1', 'name', 'lucentoapp'))));
  v_m := pg_temp.owner_call('select mc_api.autopost_credentials(''youtube'')');
  v_b := pg_temp.owner_call('select mc_api.autopost_credentials(''instagram'')');
  r := r || case when (v_j ->> 'ok')::boolean and (v_k ->> 'ok')::boolean
                      and (select count(*) from mc.autopost_destinations) = 5
                      and v_m ->> 'token_cipher' = v_cipher and v_m ->> 'refresh_cipher' = v_cipher and v_m ->> 'external_id' = 'UCg8f6wbVTgYPnXBeMvD0FyA'
                      and v_b ->> 'token_cipher' = v_page_cipher and v_b ->> 'refresh_cipher' is null
                 then 'ok  P11 YouTube and TikTok pick their only channel and account; the console''s Check gets each destination''s encrypted tokens (a Page''s own for Instagram)'
                 else 'FAIL P11 ' || coalesce(v_m::text, '') || coalesce(v_b::text, '') end;

  v_j := pg_temp.owner_call('select mc_api.autopost_set_destination(''klaroph_page'', ''999'')');
  v_k := pg_temp.owner_call('select mc_api.autopost_set_destination(''klaroph_page'', ''17841'')');
  v_m := pg_temp.owner_call('select mc_api.autopost_set_destination(''klaroph_page'', ''503'')');
  r := r || case when v_j ->> 'error' = 'not_found' and v_k ->> 'error' = 'not_found' and (v_m ->> 'ok')::boolean
                      and (select name from mc.autopost_destinations where key = 'klaroph_page') = 'Jerald''s Bakery'
                 then 'ok  P12 a destination is picked only from what the connection offers, of the right kind'
                 else 'FAIL P12 ' || coalesce(v_j::text, '') || coalesce(v_k::text, '') end;
  perform pg_temp.owner_call('select mc_api.autopost_set_destination(''klaroph_page'', ''502'')');

  -- Reconnecting without the KlaroPH Page clears that destination and refreshes the Lucento Page's token.
  v_j := pg_temp.owner_call(format('select mc_api.autopost_save_connection(%L, %L, %L, %L, null, null, null, %L, %L::jsonb)',
           'meta', '1001', 'Jerald', v_cipher, '{}',
           jsonb_build_array(
             jsonb_build_object('kind', 'page', 'id', '501', 'name', 'Lucento', 'token_cipher', v_klaro_cipher),
             jsonb_build_object('kind', 'instagram', 'id', '17841', 'name', 'lucentoapp', 'page_id', '501'))));
  r := r || case when (v_j ->> 'ok')::boolean and not exists (select 1 from mc.autopost_destinations where key = 'klaroph_page')
                      and (select token_cipher from mc.autopost_destinations where key = 'lucento_page') = v_klaro_cipher
                      and (select token_cipher from mc.autopost_destinations where key = 'instagram') = v_klaro_cipher
                 then 'ok  P13 reconnecting refreshes the destinations still offered (new Page tokens) and clears the rest'
                 else 'FAIL P13 ' || left(coalesce(v_j::text, ''), 400) end;
  perform pg_temp.owner_call(format('select mc_api.autopost_save_connection(%L, %L, %L, %L, null, null, null, %L, %L::jsonb)',
           'meta', '1001', 'Jerald', v_cipher, '{}',
           jsonb_build_array(
             jsonb_build_object('kind', 'page', 'id', '501', 'name', 'Lucento', 'token_cipher', v_page_cipher),
             jsonb_build_object('kind', 'page', 'id', '502', 'name', 'KlaroPH', 'token_cipher', v_klaro_cipher),
             jsonb_build_object('kind', 'instagram', 'id', '17841', 'name', 'lucentoapp', 'page_id', '501'))));

  -- ======================================================================== D: posts, coverage and due_soon
  v_j := pg_temp.push(jsonb_build_array(
    pg_temp.mk_card('A-soon', v_soon_day, v_soon_time),
    pg_temp.mk_card('A-far', v_far_day, v_far_time),
    pg_temp.mk_card('A-group', v_far_day, v_far_time, jsonb_build_object('kind', 'group', 'platforms', jsonb_build_array('Lucento Community group'))),
    pg_temp.mk_card('A-page', v_soon_day, v_soon_time, jsonb_build_object('kind', 'post', 'platforms', jsonb_build_array('Lucento Facebook Page'))),
    pg_temp.mk_card('A-launch', v_soon_day, v_soon_time, jsonb_build_object('phase', 'launch')),
    pg_temp.mk_card('A-off', v_soon_day, v_soon_time)));
  v_c1 := pg_temp.cid('A-soon'); v_c2 := pg_temp.cid('A-far'); v_c3 := pg_temp.cid('A-group');
  v_c4 := pg_temp.cid('A-page'); v_c5 := pg_temp.cid('A-launch'); v_c6 := pg_temp.cid('A-off');
  perform pg_temp.decide(v_c1, 1, 'approve'); perform pg_temp.decide(v_c2, 1, 'approve');
  perform pg_temp.decide(v_c3, 1, 'approve'); perform pg_temp.decide(v_c4, 1, 'approve');
  perform pg_temp.decide(v_c5, 1, 'approve'); perform pg_temp.decide(v_c6, 1, 'approve');
  v_ok := pg_temp.due_soon(v_c2);
  v_k := pg_temp.owner_call('select mc_api.autopost_set_enabled(true)');
  r := r || case when (select count(*) from mc.marketing_cards where state = 'approved') = 6
                      and v_ok and (v_k ->> 'ok')::boolean and mc.setting_on('autopost_enabled')
                      and not pg_temp.due_soon(v_c2) and pg_temp.due_soon(v_c3)
                      and mc.autopost_covers(v_c2) and not mc.autopost_covers(v_c3)
                 then 'ok  P14 once auto-posting is on, an approved post Mission Control will post isn''t "approved but not scheduled"; the group (by hand) still is'
                 else 'FAIL P14 ' || coalesce(v_k::text, '') end;

  v_j := pg_temp.owner_call(format('select mc_api.autopost_card(%L, 2, true)', v_c6));
  v_k := pg_temp.owner_call(format('select mc_api.autopost_card(%L, 1, true)', v_c6));
  r := r || case when v_j ->> 'error' = 'stale_version' and (v_k ->> 'ok')::boolean
                      and (select autopost_off from mc.marketing_cards where id = v_c6) and not mc.autopost_covers(v_c6)
                 then 'ok  P15 "post this one by hand" names the version and takes the post out of auto-posting'
                 else 'FAIL P15 ' || coalesce(v_j::text, '') || coalesce(v_k::text, '') end;

  -- ======================================================================== E: the jobs' life
  v_j := pg_temp.claim();
  r := r || case when (v_j ->> 'ok')::boolean and (v_j ->> 'enabled')::boolean
                      and (select count(*) from mc.autopost_jobs) = 2
                      and pg_temp.claimed(v_j, 'A-soon', 'facebook_reel') is not null
                      and pg_temp.claimed(v_j, 'A-soon', 'instagram_reel') is not null
                      and jsonb_array_length(v_j -> 'jobs') = 2
                      and not exists (select 1 from mc.autopost_jobs where card_id in (v_c2, v_c3, v_c4, v_c5, v_c6))
                      and not exists (select 1 from mc.autopost_jobs where card_id = v_c1 and target in ('youtube', 'tiktok'))
                 then 'ok  P16 15 minutes ahead only the Reels start (20-minute lead); the Page post waits for its slot; nothing for a far slot, the group, a launch post before launch or one turned to by hand'
                 else 'FAIL P16 ' || left(coalesce(v_j::text, 'null'), 600) end;

  v_k := pg_temp.claimed(v_j, 'A-soon', 'instagram_reel');
  v_job := (v_k ->> 'id')::uuid;
  v_lease := (v_k ->> 'lease_id')::uuid;
  r := r || case when v_k #>> '{account,provider}' = 'meta' and v_k #>> '{account,external_id}' = '17841'
                      and v_k #>> '{account,token_cipher}' = v_page_cipher and v_k #>> '{content,caption}' = 'Sweldo day? Sorted.'
                      and v_k #>> '{card,pack}' = '2099-W01' and (v_k ->> 'attempts')::integer = 0
                      and (pg_temp.claim() -> 'jobs') = '[]'::jsonb
                 then 'ok  P17 each step carries the post''s texts and files and the destination''s encrypted token, under a lease nobody else gets'
                 else 'FAIL P17 ' || left(coalesce(v_k::text, 'null'), 500) end;

  v_m := pg_temp.result(v_job, gen_random_uuid(), '{"status": "preparing", "detail": {"container_id": "c-1"}}');
  v_b := pg_temp.result(v_job, v_lease, '{"status": "preparing", "external_id": "c-1", "detail": {"container_id": "c-1"}}');
  r := r || case when v_m ->> 'error' = 'lease_lost' and (v_b ->> 'ok')::boolean
                      and (pg_temp.job(v_c1, 'instagram_reel')).status = 'preparing'
                      and (pg_temp.job(v_c1, 'instagram_reel')).next_try_at = now() + interval '1 minute'
                      and (pg_temp.job(v_c1, 'instagram_reel')).lease_id is null
                      and (pg_temp.job(v_c1, 'instagram_reel')).detail ->> 'container_id' = 'c-1'
                      and pg_temp.claimed(pg_temp.claim(), 'A-soon', 'instagram_reel') is null
                 then 'ok  P18 a step reported under another lease is refused; "preparing" keeps the container and is checked again in a minute'
                 else 'FAIL P18 ' || coalesce(v_m::text, '') || coalesce(v_b::text, '') end;

  update mc.autopost_jobs set next_try_at = now() - interval '1 second' where id = v_job;
  v_k := pg_temp.claimed(pg_temp.claim(), 'A-soon', 'instagram_reel');
  v_lease := (v_k ->> 'lease_id')::uuid;
  v_m := pg_temp.result(v_job, v_lease, '{"status": "ready"}');
  r := r || case when v_k ->> 'status' = 'preparing' and v_k #>> '{detail,container_id}' = 'c-1' and (v_m ->> 'ok')::boolean
                      and (pg_temp.job(v_c1, 'instagram_reel')).status = 'ready'
                      and pg_temp.claimed(pg_temp.claim(), 'A-soon', 'instagram_reel') is null
                 then 'ok  P19 a preparing Reel is checked again, then waits ready until its slot'
                 else 'FAIL P19 ' || left(coalesce(v_k::text, ''), 300) end;

  -- The slot arrives (moved back for the test).
  update mc.autopost_jobs set due_at = now() - interval '1 minute' where card_id = v_c1;
  update mc.marketing_cards set slot_time = to_char(v_local - interval '1 minute', 'HH24:MI')::time,
                                slot_day = (v_local - interval '1 minute')::date where id = v_c1;
  v_j := pg_temp.claim();
  v_k := pg_temp.claimed(v_j, 'A-soon', 'instagram_reel');
  v_lease := (v_k ->> 'lease_id')::uuid;
  v_m := pg_temp.result(v_job, v_lease, jsonb_build_object('status', 'posted', 'external_id', '17900001',
           'external_url', 'https://www.instagram.com/reel/abc/'));
  v_b := pg_temp.result(v_job, v_lease, jsonb_build_object('status', 'posted'));
  r := r || case when v_k ->> 'status' = 'ready' and (v_m ->> 'ok')::boolean and (v_b ->> 'already')::boolean
                      and pg_temp.claimed(v_j, 'A-soon', 'youtube') is not null and pg_temp.claimed(v_j, 'A-soon', 'tiktok') is not null
                      and (pg_temp.job(v_c1, 'instagram_reel')).status = 'posted'
                      and (pg_temp.job(v_c1, 'instagram_reel')).external_url = 'https://www.instagram.com/reel/abc/'
                      and (select in_apps_version from mc.marketing_cards where id = v_c1) = 1
                      and (select by_hand from mc.marketing_cards where id = v_c1) = '{}'
                      and exists (select 1 from mc.job_runs where job = 'autopost' and result = 'ok')
                 then 'ok  P20 at the slot the ready Reel is published and YouTube and TikTok start; the first post marks version 1 as out; a repeat "posted" changes nothing'
                 else 'FAIL P20 ' || left(coalesce(v_j::text, ''), 500) end;

  -- TikTok: sent to the owner's drafts, so "TikTok" joins what the owner finishes.
  v_k := pg_temp.claimed(v_j, 'A-soon', 'tiktok');
  v_m := pg_temp.result((v_k ->> 'id')::uuid, (v_k ->> 'lease_id')::uuid, jsonb_build_object('status', 'sent_to_drafts',
           'external_id', 'v_inbox_123'));
  r := r || case when (v_m ->> 'ok')::boolean and (pg_temp.job(v_c1, 'tiktok')).status = 'sent_to_drafts'
                      and (select by_hand from mc.marketing_cards where id = v_c1) = '{TikTok}'
                 then 'ok  P21 a TikTok draft adds TikTok to "Did you post it on …?"'
                 else 'FAIL P21 ' || coalesce(v_m::text, '') end;

  -- YouTube fails three times (each may help), then stays failed.
  v_k := pg_temp.claimed(v_j, 'A-soon', 'youtube');
  v_job2 := (v_k ->> 'id')::uuid;
  v_m := pg_temp.result(v_job2, (v_k ->> 'lease_id')::uuid, '{"status": "failed", "retryable": true, "retry_after_s": 30, "error": "503", "error_code": "provider_unavailable"}');
  r := r || case when v_m ->> 'status' = 'queued' and (pg_temp.job(v_c1, 'youtube')).attempts = 1
                      and (pg_temp.job(v_c1, 'youtube')).next_try_at = now() + interval '30 seconds'
                      and (pg_temp.job(v_c1, 'youtube')).error_code = 'provider_unavailable'
                 then 'ok  P22 a failure that may pass is retried later (30 s × attempts)'
                 else 'FAIL P22 ' || coalesce(v_m::text, '') end;
  update mc.autopost_jobs set next_try_at = now() - interval '1 second' where id = v_job2;
  v_k := pg_temp.claimed(pg_temp.claim(), 'A-soon', 'youtube');
  perform pg_temp.result(v_job2, (v_k ->> 'lease_id')::uuid, '{"status": "failed", "retryable": true}');
  update mc.autopost_jobs set next_try_at = now() - interval '1 second' where id = v_job2;
  v_k := pg_temp.claimed(pg_temp.claim(), 'A-soon', 'youtube');
  v_m := pg_temp.result(v_job2, (v_k ->> 'lease_id')::uuid, '{"status": "failed", "retryable": true, "error": "Quota exceeded"}');
  v_s := pg_temp.summary();
  r := r || case when v_m ->> 'status' = 'failed' and (pg_temp.job(v_c1, 'youtube')).attempts = 3
                      and (pg_temp.job(v_c1, 'youtube')).finished_at = now()
                      and v_s #>> '{autopost,failed,0,card}' = 'A-soon' and v_s #>> '{autopost,failed,0,target}' = 'youtube'
                      and (v_s #>> '{autopost,counts,failed}')::integer = 1
                      and v_s #>> '{autopost,drafts_waiting,0,target}' = 'tiktok'
                      and exists (select 1 from mc.job_runs where job = 'autopost' and result = 'failed')
                 then 'ok  P23 after 3 attempts it stays failed; the summary lists the failure and the TikTok draft waiting'
                 else 'FAIL P23 ' || left(coalesce(v_s::text, ''), 500) end;

  -- The owner: try again, then take it by hand.
  v_m := pg_temp.owner_call(format('select mc_api.autopost_job_action(%L, ''retry'')', v_job2));
  v_b := pg_temp.owner_call(format('select mc_api.autopost_job_action(%L, ''retry'')', v_job));
  r := r || case when v_m ->> 'status' = 'queued' and (pg_temp.job(v_c1, 'youtube')).attempts = 0
                      and (pg_temp.job(v_c1, 'youtube')).error is null and v_b ->> 'error' = 'bad_state'
                 then 'ok  P24 "Try again" puts a failed post back in the queue from scratch; a posted one can''t be retried'
                 else 'FAIL P24 ' || coalesce(v_m::text, '') || coalesce(v_b::text, '') end;
  v_m := pg_temp.owner_call(format('select mc_api.autopost_job_action(%L, ''by_hand'')', v_job2));
  r := r || case when v_m ->> 'status' = 'by_hand'
                      and (select by_hand from mc.marketing_cards where id = v_c1) @> '{TikTok,YouTube Shorts}'
                 then 'ok  P25 "I''ll post it by hand" stops the job and adds YouTube Shorts to what the owner finishes'
                 else 'FAIL P25 ' || coalesce(v_m::text, '') end;

  -- Facebook Reel: unconfirmed, then confirmed by the owner.
  v_k := pg_temp.claimed(pg_temp.claim(), 'A-soon', 'facebook_reel');
  if v_k is null then
    -- Still under its first lease from P16: let it lapse.
    update mc.autopost_jobs set lease_until = now() - interval '1 second' where card_id = v_c1 and target = 'facebook_reel';
    v_k := pg_temp.claimed(pg_temp.claim(), 'A-soon', 'facebook_reel');
  end if;
  v_m := pg_temp.result((v_k ->> 'id')::uuid, gen_random_uuid(), '{"status": "uncertain", "error": "Timed out after publishing"}');
  v_b := pg_temp.owner_call(format('select mc_api.autopost_job_action(%L, ''posted'')', v_k ->> 'id'));
  r := r || case when v_m ->> 'status' = 'uncertain' and v_b ->> 'status' = 'posted'
                      and exists (select 1 from mc.autopost_events where job_id = (v_k ->> 'id')::uuid and kind = 'confirmed_posted' and actor = 'admin')
                 then 'ok  P26 "uncertain" is recorded even from a lapsed lease and never retried; the owner confirms it''s up'
                 else 'FAIL P26 ' || coalesce(v_m::text, '') || coalesce(v_b::text, '') end;

  -- ======================================================================== F: stops
  -- The Page post's slot arrives; the owner undoes the approval first: its job is cancelled, nothing posts.
  update mc.marketing_cards set slot_day = (v_local - interval '1 minute')::date,
                                slot_time = to_char(v_local - interval '1 minute', 'HH24:MI')::time where id = v_c4;
  v_j := pg_temp.claim();
  v_ok := pg_temp.claimed(v_j, 'A-page', 'facebook_photo') is not null;
  update mc.autopost_jobs set lease_until = now() - interval '1 second' where card_id = v_c4;
  perform pg_temp.decide(v_c4, 1, 'undo');
  v_j := pg_temp.claim();
  r := r || case when v_ok and (pg_temp.job(v_c4, 'facebook_photo')).status = 'cancelled' and pg_temp.claimed(v_j, 'A-page', 'facebook_photo') is null
                      and exists (select 1 from mc.autopost_events e join mc.autopost_jobs j on j.id = e.job_id
                                  where j.card_id = v_c4 and e.kind = 'cancelled' and e.note = 'not_approved')
                 then 'ok  P27 a post undone before its slot is cancelled at the next step (why: not_approved): nothing posts'
                 else 'FAIL P27 ' || left(coalesce(v_j::text, ''), 300) end;

  -- A revised post: the old version's queued jobs are cancelled, the new version waits for the owner.
  update mc.marketing_cards set slot_day = v_soon_day, slot_time = v_soon_time::time where id = v_c2;
  v_j := pg_temp.claim();
  update mc.autopost_jobs set lease_until = now() - interval '1 second' where card_id = v_c2;
  v_k := pg_temp.push(jsonb_build_array(pg_temp.mk_card('A-far', v_soon_day, v_soon_time,
           jsonb_build_object('version', 2, 'changes', 'Shorter hook'))));
  v_m := pg_temp.claim();
  r := r || case when (pg_temp.job(v_c2, 'instagram_reel')).status = 'cancelled' and (pg_temp.job(v_c2, 'instagram_reel')).version = 1
                      and (select state from mc.marketing_cards where id = v_c2) = 'waiting'
                      and not exists (select 1 from mc.autopost_jobs where card_id = v_c2 and version = 2)
                      and exists (select 1 from mc.autopost_events e join mc.autopost_jobs j on j.id = e.job_id
                                  where j.card_id = v_c2 and j.version = 1 and e.kind = 'cancelled' and e.note = 'new_version')
                 then 'ok  P28 a revision cancels the old version''s jobs (why: new_version); the new version posts only once approved'
                 else 'FAIL P28 ' || left(coalesce(v_k::text, ''), 300) end;

  -- Approved again, then missed (past 3 hours after its slot).
  perform pg_temp.decide(v_c2, 2, 'approve');
  perform pg_temp.claim();
  update mc.autopost_jobs set late_until = now() - interval '1 second', lease_until = null where card_id = v_c2 and version = 2;
  perform pg_temp.claim();
  r := r || case when (select count(*) from mc.autopost_jobs where card_id = v_c2 and version = 2) = 2
                      and not exists (select 1 from mc.autopost_jobs where card_id = v_c2 and version = 2 and status <> 'failed')
                      and (select error_code from mc.autopost_jobs where card_id = v_c2 and version = 2 limit 1) = 'missed'
                 then 'ok  P29 a step that can''t happen within 3 hours of the slot fails as missed (never a day late)'
                 else 'FAIL P29' end;

  -- Started posts can't be turned to by hand; launch rules; turning it off stops new jobs.
  v_j := pg_temp.owner_call(format('select mc_api.autopost_card(%L, 1, true)', v_c1));
  update mc.marketing_cards set phase = 'warmup' where id = v_c2;
  update mc.marketing_launch set pday_at = now() - interval '1 day' where id;
  r := r || case when v_j ->> 'error' = 'already_started'
                      and exists (select 1 from mc.marketing_cards where id = v_c2 and state = 'approved')
                      and not exists (select 1 from mc.autopost_candidates() x where x.card_id = v_c2)
                      and exists (select 1 from mc.autopost_candidates() x where x.card_id = v_c5)
                 then 'ok  P30 a post already under way can''t be turned to by hand; after launch a ''coming soon'' post isn''t posted and a launch post is'
                 else 'FAIL P30 ' || coalesce(v_j::text, '') end;
  update mc.marketing_launch set pday_at = null where id;
  update mc.marketing_cards set phase = 'any' where id = v_c2;

  v_k := pg_temp.owner_call('select mc_api.autopost_set_enabled(false)');
  v_m := pg_temp.claim();
  r := r || case when (v_k ->> 'ok')::boolean and not (v_m ->> 'enabled')::boolean and (v_m -> 'jobs') = '[]'::jsonb
                      and not mc.autopost_covers(v_c2) and mc.autopost_kick() = 'off'
                 then 'ok  P31 off: the claim hands out nothing, nothing counts as covered, and the clock stays quiet'
                 else 'FAIL P31 ' || coalesce(v_m::text, '') end;
  perform pg_temp.owner_call('select mc_api.autopost_set_enabled(true)');

  -- ======================================================================== G: the clock's guards
  r := r || case when mc.autopost_kick() = 'no_url' then 'ok  P32 the clock does nothing before its address is set'
                 else 'FAIL P32 ' || mc.autopost_kick() end;
  insert into mc.settings (key, value) values ('autopost_tick_url', '"https://example.invalid/api/autopost/tick"')
    on conflict (key) do update set value = excluded.value;
  update mc.autopost_jobs set status = 'cancelled', lease_id = null, lease_until = null where status in ('queued', 'preparing', 'ready');
  update mc.marketing_cards set state = 'waiting' where state = 'approved';
  r := r || case when mc.autopost_kick() = 'idle' and not mc.autopost_work_due()
                 then 'ok  P33 with nothing due the clock doesn''t call the console'
                 else 'FAIL P33 ' || mc.autopost_kick() end;
  begin
    perform mc.autopost_setup('http://lucento-mission-control.vercel.app/api/autopost/tick');
    v_t := 'accepted';
  exception when sqlstate '22023' then
    v_t := 'refused';
  end;
  begin
    perform mc.autopost_setup('https://lucento-mission-control.vercel.app/api/autopost/tick');
    v_n := 0;
  exception when sqlstate '55000' then
    v_n := 1;
  end;
  r := r || case when v_t = 'refused' and (v_n = 1 or exists (select 1 from pg_extension where extname = 'pg_net'))
                 then 'ok  P34 the setup takes only the console''s https tick address, and refuses without pg_net, pg_cron or Vault'
                 else 'FAIL P34 ' || v_t || v_n end;

  -- ======================================================================== H: the board, disconnecting and the audit
  v_b := pg_temp.board();
  v_k := pg_temp.cardj(v_b, 'A-soon');
  r := r || case when (v_b #>> '{autopost,enabled}')::boolean and v_b #>> '{autopost,destinations,instagram,name}' = 'lucentoapp'
                      and jsonb_array_length(v_k #> '{autopost,targets}') = 4
                      and v_k #>> '{autopost,targets,0,mode}' = 'draft' and v_k #>> '{autopost,targets,0,job,status}' = 'sent_to_drafts'
                      and v_k #>> '{autopost,targets,2,target}' = 'instagram_reel'
                      and v_k #>> '{autopost,targets,2,job,external_url}' = 'https://www.instagram.com/reel/abc/'
                      and v_k #>> '{autopost,targets,3,job,status}' = 'by_hand'
                      and position('v1:' in v_b::text) = 0
                 then 'ok  P35 the board shows each post''s targets with their latest job and link, and never a token'
                 else 'FAIL P35 ' || left(coalesce(v_k::text, ''), 600) end;

  update mc.marketing_cards set state = 'approved' where id = v_c6;
  update mc.marketing_cards set autopost_off = false, slot_day = v_soon_day, slot_time = v_soon_time::time where id = v_c6;
  perform pg_temp.claim();
  update mc.autopost_jobs set lease_until = now() - interval '1 second' where card_id = v_c6;
  v_j := pg_temp.owner_call('select mc_api.autopost_disconnect(''meta'')');
  r := r || case when (v_j ->> 'ok')::boolean
                      and not exists (select 1 from mc.autopost_destinations where provider = 'meta')
                      and not exists (select 1 from mc.autopost_connections where provider = 'meta')
                      and not exists (select 1 from mc.autopost_jobs where card_id = v_c6 and status in ('queued', 'preparing', 'ready')
                                        and destination in ('lucento_page', 'instagram'))
                      and exists (select 1 from mc.autopost_events e join mc.autopost_jobs j on j.id = e.job_id
                                  where j.card_id = v_c6 and e.kind = 'cancelled' and e.note = 'disconnected')
                 then 'ok  P36 disconnecting removes the account, its tokens and destinations, and cancels its jobs not under way (why: disconnected)'
                 else 'FAIL P36 ' || left(coalesce(v_j::text, ''), 300) end;

  perform pg_temp.as_anon();
  v_j := mc_api.ingest_autopost_token(v_app, 'google', 'g-1', v_page_cipher, now() + interval '1 hour', null, null, null);
  v_k := mc_api.ingest_autopost_token(v_app, 'tiktok', 'open-1', null, null, null, null, 'refresh_token expired');
  execute 'reset role';
  r := r || case when (v_j ->> 'ok')::boolean and (select token_cipher from mc.autopost_connections where provider = 'google') = v_page_cipher
                      and (select refresh_cipher from mc.autopost_connections where provider = 'google') = v_cipher
                      and (select last_error from mc.autopost_connections where provider = 'tiktok') = 'refresh_token expired'
                 then 'ok  P37 a refreshed token replaces the old one (the refresh token kept); a failed refresh is recorded for Settings'
                 else 'FAIL P37 ' || coalesce(v_j::text, '') end;

  -- ======================================================================== I: after the security review
  -- Meta again, without its user token (it posts with the Pages' own); YouTube can't do without one.
  v_j := pg_temp.owner_call(format('select mc_api.autopost_save_connection(%L, %L, %L, null, null, null, null, %L, %L::jsonb)',
           'meta', '1001', 'Jerald', '{pages_manage_posts}',
           jsonb_build_array(jsonb_build_object('kind', 'page', 'id', '501', 'name', 'Lucento', 'token_cipher', v_page_cipher),
                             jsonb_build_object('kind', 'instagram', 'id', '17841', 'name', 'lucentoapp', 'page_id', '501'))));
  v_k := pg_temp.owner_call(format('select mc_api.autopost_save_connection(%L, %L, %L, null, %L, null, null, %L, %L::jsonb)',
           'google', 'g-1', 'Lucento', v_cipher, '{}',
           jsonb_build_array(jsonb_build_object('kind', 'youtube', 'id', 'UCg8f6wbVTgYPnXBeMvD0FyA', 'name', 'Lucento'))));
  r := r || case when (v_j ->> 'ok')::boolean and (select token_cipher from mc.autopost_connections where provider = 'meta') is null
                      and (select token_cipher from mc.autopost_destinations where key = 'lucento_page') = v_page_cipher
                      and v_k ->> 'error' = 'invalid'
                      and (select token_cipher from mc.autopost_connections where provider = 'google') = v_page_cipher
                 then 'ok  P38 a Meta account is kept without its user token (posting uses the Pages'' own); YouTube and TikTok still need theirs'
                 else 'FAIL P38 ' || coalesce(v_j::text, '') || coalesce(v_k::text, '') end;

  -- The post turned to by hand earlier is approved and its slot has passed; a new post's Reels start ahead of theirs.
  update mc.marketing_cards set slot_day = (v_local - interval '1 minute')::date,
                                slot_time = to_char(v_local - interval '1 minute', 'HH24:MI')::time where id = v_c6;
  perform pg_temp.push(jsonb_build_array(pg_temp.mk_card('A-sec', v_soon_day, v_soon_time)));
  perform pg_temp.decide(pg_temp.cid('A-sec'), 1, 'approve');
  v_j := pg_temp.claim();
  r := r || case when pg_temp.claimed(v_j, 'A-off', 'facebook_reel') is not null and pg_temp.claimed(v_j, 'A-off', 'youtube') is not null
                      and (select count(*) from mc.autopost_events where job_id = (pg_temp.job(v_c6, 'facebook_reel')).id and kind = 'created') = 2
                      and (pg_temp.job(v_c6, 'facebook_reel')).attempts = 0 and (pg_temp.job(v_c6, 'facebook_reel')).detail = '{}'::jsonb
                      and pg_temp.claimed(v_j, 'A-sec', 'facebook_reel') is not null
                 then 'ok  P39 a job cancelled earlier (account disconnected) starts over once its post can go out again'
                 else 'FAIL P39 ' || left(coalesce(v_j::text, ''), 400) end;

  -- The YouTube run dies after the slot (its lease lapses without a result); the Reels' run dies before theirs.
  v_job := (pg_temp.job(v_c6, 'youtube')).id;
  update mc.autopost_jobs set due_at = now() - interval '10 minutes', lease_until = now() - interval '1 second' where id = v_job;
  update mc.autopost_jobs set lease_until = now() - interval '1 second' where card_id = pg_temp.cid('A-sec');
  v_j := pg_temp.claim();
  v_n := (pg_temp.job(v_c6, 'youtube')).attempts;
  v_m := pg_temp.result(v_job, gen_random_uuid(), '{"status": "uncertain", "error": "late"}');
  r := r || case when (pg_temp.job(v_c6, 'youtube')).status = 'uncertain' and (pg_temp.job(v_c6, 'youtube')).error_code = 'lease_lapsed'
                      and (pg_temp.job(v_c6, 'youtube')).lease_id is null and pg_temp.claimed(v_j, 'A-off', 'youtube') is null
                      and exists (select 1 from mc.autopost_events where job_id = v_job and kind = 'uncertain' and note = 'lease_lapsed')
                      and pg_temp.claimed(v_j, 'A-sec', 'facebook_reel') is not null
                      and (pg_temp.job(pg_temp.cid('A-sec'), 'facebook_reel')).status = 'queued'
                      and (v_m ->> 'already')::boolean and (pg_temp.job(v_c6, 'youtube')).attempts = v_n
                 then 'ok  P40 a step taken at its slot whose run never reported becomes uncertain, never handed out again; one taken before its slot only prepared and goes out again; a repeated "uncertain" changes nothing'
                 else 'FAIL P40 ' || left(coalesce(v_j::text, ''), 400) end;

  v_m := pg_temp.owner_call(format('select mc_api.autopost_job_action(%L, ''retry'')', v_job));
  v_j := pg_temp.claim();
  r := r || case when v_m ->> 'status' = 'queued' and pg_temp.claimed(v_j, 'A-off', 'youtube') is not null
                      and (pg_temp.job(v_c6, 'youtube')).status = 'queued'
                 then 'ok  P41 "Try again" clears the old lease, so the step is handed out again (not judged lapsed)'
                 else 'FAIL P41 ' || coalesce(v_m::text, '') || left(coalesce(v_j::text, ''), 300) end;

  v_m := pg_temp.owner_call(format('select mc_api.autopost_card(%L, 1, true)', v_c6));
  r := r || case when v_m ->> 'error' = 'busy' and not (select autopost_off from mc.marketing_cards where id = v_c6)
                 then 'ok  P42 "post this one by hand" waits while a step is under way (it may be posting right now)'
                 else 'FAIL P42 ' || coalesce(v_m::text, '') end;

  perform pg_temp.as_anon();
  v_j := mc_api.ingest_autopost_token(v_app, 'tiktok', 'open-OTHER', v_page_cipher, now() + interval '1 day', null, null, null);
  v_k := mc_api.ingest_autopost_token(v_app, 'tiktok', 'open-1', v_klaro_cipher, now() + interval '1 day', null, null, null);
  execute 'reset role';
  r := r || case when not (v_j ->> 'ok')::boolean and (v_k ->> 'ok')::boolean
                      and (select token_cipher from mc.autopost_connections where provider = 'tiktok') = v_klaro_cipher
                 then 'ok  P43 a renewed token is saved only for the account it was renewed for'
                 else 'FAIL P43 ' || coalesce(v_j::text, '') || coalesce(v_k::text, '') end;

  -- Off stops the steps waiting (not one under way); on again starts them over.
  update mc.autopost_jobs set lease_until = now() - interval '1 second' where card_id = pg_temp.cid('A-sec');
  v_m := pg_temp.owner_call('select mc_api.autopost_set_enabled(false)');
  v_ok := (pg_temp.job(pg_temp.cid('A-sec'), 'facebook_reel')).status = 'cancelled'
          and (pg_temp.job(v_c6, 'youtube')).status = 'queued'
          and (select e.note from mc.autopost_events e where e.job_id = (pg_temp.job(pg_temp.cid('A-sec'), 'facebook_reel')).id
               and e.kind = 'cancelled' order by e.id desc limit 1) = 'autopost_off';
  v_k := pg_temp.owner_call('select mc_api.autopost_set_enabled(true)');
  v_j := pg_temp.claim();
  r := r || case when (v_m ->> 'ok')::boolean and v_ok and (v_k ->> 'ok')::boolean
                      and pg_temp.claimed(v_j, 'A-sec', 'facebook_reel') is not null
                 then 'ok  P44 turning auto-posting off stops the steps waiting (not one under way; why: autopost_off); on again starts them over'
                 else 'FAIL P44 ' || coalesce(v_m::text, '') || left(coalesce(v_j::text, ''), 300) end;

  -- ======================================================================== J: after the design review
  -- A post the owner already scheduled in the apps by hand is left alone (it would go out twice); a launch post before
  -- launch waits; the card says which.
  perform pg_temp.push(jsonb_build_array(pg_temp.mk_card('A-apps', (v_local - interval '1 minute')::date,
                                                         to_char(v_local - interval '1 minute', 'HH24:MI'))));
  perform pg_temp.decide(pg_temp.cid('A-apps'), 1, 'approve');
  v_m := pg_temp.owner_call(format('select mc_api.marketing_apps(%L, 1, ''in_apps'', ''{}''::text[])', pg_temp.cid('A-apps')));
  v_j := pg_temp.claim();
  v_b := pg_temp.board();
  r := r || case when (v_m ->> 'ok')::boolean
                      and not exists (select 1 from mc.autopost_jobs where card_id = pg_temp.cid('A-apps'))
                      and pg_temp.cardj(v_b, 'A-apps') #>> '{autopost,blocked}' = 'in_apps'
                      and not (pg_temp.cardj(v_b, 'A-apps') #>> '{autopost,posts_any}')::boolean
                      and mc.autopost_blocked(v_c5) = 'too_early' and pg_temp.cardj(v_b, 'A-launch') #>> '{autopost,blocked}' = 'too_early'
                      and mc.autopost_blocked(v_c1) is null
                 then 'ok  P45 a post already scheduled in the apps by hand gets no job (no second post) and a launch post waits for launch; the card says why; one whose own automatic post went out isn''t blocked'
                 else 'FAIL P45 ' || coalesce(v_m::text, '') || left(coalesce(pg_temp.cardj(v_b, 'A-apps')::text, ''), 300) end;

  -- Settings › Check is kept per destination, shown in Settings and on the posts, and cleared when the token changes.
  v_m := pg_temp.owner_call('select mc_api.autopost_record_check(''lucento_page'', false, ''  Reconnect Facebook and Instagram: the sign-in was refused.  '')');
  v_k := pg_temp.owner_call('select mc_api.autopost_record_check(''klaroph_page'', true, null)');
  v_s := pg_temp.owner_call('select mc_api.autopost_settings()');
  v_b := pg_temp.board();
  v_ok := v_s #>> '{destinations,lucento_page,check_ok}' = 'false'
          and v_s #>> '{destinations,lucento_page,check_note}' = 'Reconnect Facebook and Instagram: the sign-in was refused.'
          and (v_s #>> '{destinations,lucento_page,checked_at}')::timestamptz = now()
          and (select bool_or(x ->> 'check_ok' = 'false') from jsonb_array_elements(pg_temp.cardj(v_b, 'A-off') #> '{autopost,targets}') x
               where x ->> 'destination' = 'lucento_page');
  perform pg_temp.owner_call(format('select mc_api.autopost_save_connection(%L, %L, %L, null, null, null, null, %L, %L::jsonb)',
           'meta', '1001', 'Jerald', '{pages_manage_posts}',
           jsonb_build_array(jsonb_build_object('kind', 'page', 'id', '501', 'name', 'Lucento', 'token_cipher', v_klaro_cipher),
                             jsonb_build_object('kind', 'instagram', 'id', '17841', 'name', 'lucentoapp', 'page_id', '501'))));
  r := r || case when (v_m ->> 'ok')::boolean and v_k ->> 'error' = 'not_found' and v_ok
                      and (select check_ok is null and checked_at is null from mc.autopost_destinations where key = 'lucento_page')
                      and (v_s ->> 'work_due') is not null
                 then 'ok  P46 a Check''s answer is kept for its destination (Settings and the posts show it) and cleared when the token changes; only a set destination takes one'
                 else 'FAIL P46 ' || coalesce(v_m::text, '') || coalesce(v_k::text, '') || left(coalesce(v_s::text, ''), 300) end;

  update mc.autopost_jobs set status = 'cancelled', lease_id = null, lease_until = null where status in ('queued', 'preparing', 'ready');
  v_s := pg_temp.owner_call('select mc_api.autopost_settings()');
  v_ok := (v_s ->> 'work_due')::boolean;
  update mc.marketing_cards set state = 'waiting' where state = 'approved';
  v_s := pg_temp.owner_call('select mc_api.autopost_settings()');
  r := r || case when v_ok and not (v_s ->> 'work_due')::boolean
                 then 'ok  P47 Settings knows when something is due (so it can tell a posting clock that stopped)'
                 else 'FAIL P47 ' || left(coalesce(v_s::text, ''), 300) end;

  -- ======================================================================== K: after the design review, round 2
  -- A step stopped by turning auto-posting off: its card says why; "Post it now" is refused while off and, once on (past
  -- the slot's window, so nothing restarts it by itself), sends it.
  perform pg_temp.push(jsonb_build_array(pg_temp.mk_card('A-stop', (v_local - interval '1 minute')::date,
             to_char(v_local - interval '1 minute', 'HH24:MI'), jsonb_build_object('platforms', jsonb_build_array('YouTube Shorts')))));
  perform pg_temp.decide(pg_temp.cid('A-stop'), 1, 'approve');
  perform pg_temp.claim();
  update mc.autopost_jobs set lease_id = null, lease_until = null where card_id = pg_temp.cid('A-stop');
  v_job := (pg_temp.job(pg_temp.cid('A-stop'), 'youtube')).id;
  perform pg_temp.owner_call('select mc_api.autopost_set_enabled(false)');
  v_b := pg_temp.board();
  v_m := pg_temp.owner_call(format('select mc_api.autopost_job_action(%L, ''retry'')', v_job));
  perform pg_temp.owner_call('select mc_api.autopost_set_enabled(true)');
  update mc.marketing_cards set slot_day = (v_local - interval '4 hours')::date,
                                slot_time = to_char(v_local - interval '4 hours', 'HH24:MI')::time where id = pg_temp.cid('A-stop');
  v_j := pg_temp.claim();
  v_ok := (pg_temp.job(pg_temp.cid('A-stop'), 'youtube')).status = 'cancelled' and pg_temp.claimed(v_j, 'A-stop', 'youtube') is null;
  v_k := pg_temp.owner_call(format('select mc_api.autopost_job_action(%L, ''retry'')', v_job));
  v_j := pg_temp.claim();
  r := r || case when v_job is not null
                      and pg_temp.cardj(v_b, 'A-stop') #>> '{autopost,targets,0,job,status}' = 'cancelled'
                      and pg_temp.cardj(v_b, 'A-stop') #>> '{autopost,targets,0,job,stop_note}' = 'autopost_off'
                      and v_m ->> 'error' = 'not_allowed' and v_ok
                      and v_k ->> 'status' = 'queued' and pg_temp.claimed(v_j, 'A-stop', 'youtube') is not null
                      and (pg_temp.job(pg_temp.cid('A-stop'), 'youtube')).late_until > now() + interval '59 minutes'
                 then 'ok  P48 a step stopped by turning auto-posting off says why on its card; "Post it now" is refused while off and, once on, sends it'
                 else 'FAIL P48 ' || coalesce(v_m::text, '') || coalesce(v_k::text, '') || left(coalesce(pg_temp.cardj(v_b, 'A-stop')::text, ''), 300) end;

  -- "I'll post it by hand" takes a stopped step too: the label joins what the owner finishes.
  update mc.autopost_jobs set lease_id = null, lease_until = null where id = v_job;
  perform pg_temp.owner_call('select mc_api.autopost_set_enabled(false)');
  v_m := pg_temp.owner_call(format('select mc_api.autopost_job_action(%L, ''by_hand'')', v_job));
  perform pg_temp.owner_call('select mc_api.autopost_set_enabled(true)');
  r := r || case when v_m ->> 'status' = 'by_hand'
                      and (select by_hand from mc.marketing_cards where id = pg_temp.cid('A-stop')) @> '{YouTube Shorts}'
                      and (select in_apps_version from mc.marketing_cards where id = pg_temp.cid('A-stop')) = 1
                 then 'ok  P49 "I''ll post it by hand" takes a stopped step: the label joins "Did you post it on …?"'
                 else 'FAIL P49 ' || coalesce(v_m::text, '') end;

  -- The accounts' health: Overview can tell auto-posting is on but can't post; a failed token renewal counts as a
  -- refusal on the posts until a Check passes.
  v_t := (select account_id from mc.autopost_connections where provider = 'google');
  perform pg_temp.owner_call('select mc_api.autopost_record_check(''youtube'', true, null)');
  v_s := pg_temp.summary();
  v_n := (v_s #>> '{autopost,accounts,working}')::integer;
  perform pg_temp.as_anon();
  v_j := mc_api.ingest_autopost_token(v_app, 'google', v_t, null, null, null, null, 'invalid_grant');
  execute 'reset role';
  v_m := pg_temp.summary();
  v_b := pg_temp.board();
  v_ok := (v_m #>> '{autopost,accounts,working}')::integer = v_n - 1
          and (v_m #> '{autopost,accounts,reconnect}') @> '["google"]'::jsonb
          and pg_temp.cardj(v_b, 'A-stop') #>> '{autopost,targets,0,check_ok}' = 'false';
  perform pg_temp.owner_call('select mc_api.autopost_record_check(''youtube'', true, null)');
  v_k := pg_temp.summary();
  r := r || case when (v_j ->> 'ok')::boolean and v_ok and v_n >= 1
                      and (v_k #>> '{autopost,accounts,working}')::integer = v_n
                      and not ((v_k #> '{autopost,accounts,reconnect}') @> '["google"]'::jsonb)
                      and (select last_error from mc.autopost_connections where provider = 'google') is null
                      and (v_k #>> '{autopost,accounts,chosen}')::integer >= v_n
                 then 'ok  P50 the summary counts the accounts that can post and names those to reconnect (a refused Check or a failed renewal, which the posts show too); a passing Check clears a failed renewal'
                 else 'FAIL P50 ' || left(coalesce((v_m #> '{autopost,accounts}')::text, ''), 200) || left(coalesce((v_k #> '{autopost,accounts}')::text, ''), 200) end;

  -- A platform disconnected before its slot is the owner's: when the rest goes out, "Did you post it on …?" asks.
  perform pg_temp.push(jsonb_build_array(pg_temp.mk_card('A-disc', (v_local - interval '1 minute')::date,
             to_char(v_local - interval '1 minute', 'HH24:MI'),
             jsonb_build_object('kind', 'post', 'platforms', jsonb_build_array('Lucento Facebook Page', 'YouTube Shorts')))));
  perform pg_temp.decide(pg_temp.cid('A-disc'), 1, 'approve');
  perform pg_temp.claim();
  update mc.autopost_jobs set lease_id = null, lease_until = null where card_id = pg_temp.cid('A-disc');
  perform pg_temp.owner_call('select mc_api.autopost_disconnect(''google'')');
  v_j := pg_temp.claim();
  v_k := pg_temp.claimed(v_j, 'A-disc', 'facebook_photo');
  v_m := pg_temp.result((v_k ->> 'id')::uuid, (v_k ->> 'lease_id')::uuid,
           '{"status": "posted", "external_id": "p1", "external_url": "https://www.facebook.com/p1"}');
  r := r || case when (v_m ->> 'ok')::boolean
                      and (pg_temp.job(pg_temp.cid('A-disc'), 'youtube')).status = 'cancelled'
                      and (select by_hand from mc.marketing_cards where id = pg_temp.cid('A-disc')) = '{YouTube Shorts}'
                 then 'ok  P51 a platform disconnected before its slot is the owner''s: once the rest goes out, "Did you post it on YouTube Shorts?" asks'
                 else 'FAIL P51 ' || coalesce(v_m::text, '') || left(coalesce(v_j::text, ''), 300) end;

  -- An account whose Check refused doesn't post: its post isn't counted as Mission Control's, its waiting step stops
  -- (why: reconnect), and "Post it now" says to reconnect first.
  perform pg_temp.push(jsonb_build_array(pg_temp.mk_card('A-refused', (v_local - interval '1 minute')::date,
             to_char(v_local - interval '1 minute', 'HH24:MI'),
             jsonb_build_object('kind', 'post', 'platforms', jsonb_build_array('Lucento Facebook Page')))));
  perform pg_temp.decide(pg_temp.cid('A-refused'), 1, 'approve');
  v_ok := mc.autopost_posts_any(pg_temp.cid('A-refused')) and mc.autopost_covers(pg_temp.cid('A-refused'));
  perform pg_temp.claim();
  update mc.autopost_jobs set lease_id = null, lease_until = null where card_id = pg_temp.cid('A-refused');
  perform pg_temp.owner_call('select mc_api.autopost_record_check(''lucento_page'', false, ''Reconnect Facebook and Instagram: the sign-in was refused.'')');
  v_j := pg_temp.claim();
  v_t := (select e.note from mc.autopost_events e where e.job_id = (pg_temp.job(pg_temp.cid('A-refused'), 'facebook_photo')).id
          and e.kind = 'cancelled' order by e.id desc limit 1);
  v_m := pg_temp.owner_call(format('select mc_api.autopost_job_action(%L, ''retry'')', (pg_temp.job(pg_temp.cid('A-refused'), 'facebook_photo')).id));
  r := r || case when v_ok
                      and not mc.autopost_posts_any(pg_temp.cid('A-refused')) and not mc.autopost_covers(pg_temp.cid('A-refused'))
                      and (pg_temp.job(pg_temp.cid('A-refused'), 'facebook_photo')).status = 'cancelled' and v_t = 'reconnect'
                      and pg_temp.claimed(v_j, 'A-refused', 'facebook_photo') is null
                      and v_m ->> 'error' = 'not_allowed' and v_m ->> 'message' like 'Its account needs a reconnect%'
                 then 'ok  P52 an account whose Check refused doesn''t post: its post isn''t counted as Mission Control''s, its waiting step stops (why: reconnect) and "Post it now" says to reconnect first'
                 else 'FAIL P52 ' || coalesce(v_t, 'no note') || coalesce(v_m::text, '') end;

  -- Reconnected: the Checks are cleared and the stopped steps start over; a label the owner was asked about (YouTube,
  -- disconnected before its slot) isn't asked about any more once Mission Control posts it.
  perform pg_temp.owner_call(format('select mc_api.autopost_save_connection(%L, %L, %L, null, null, null, null, %L, %L::jsonb)',
           'meta', '1001', 'Jerald', '{pages_manage_posts}',
           jsonb_build_array(jsonb_build_object('kind', 'page', 'id', '501', 'name', 'Lucento', 'token_cipher', v_page_cipher),
                             jsonb_build_object('kind', 'instagram', 'id', '17841', 'name', 'lucentoapp', 'page_id', '501'))));
  perform pg_temp.owner_call(format('select mc_api.autopost_save_connection(%L, %L, %L, %L, %L, %L, null, %L, %L::jsonb)',
           'google', 'g-1', 'Lucento', v_cipher, v_cipher, (now() + interval '1 hour')::text, '{}',
           jsonb_build_array(jsonb_build_object('kind', 'youtube', 'id', 'UCg8f6wbVTgYPnXBeMvD0FyA', 'name', 'Lucento'))));
  v_j := pg_temp.claim();
  v_k := pg_temp.claimed(v_j, 'A-disc', 'youtube');
  v_m := pg_temp.result((v_k ->> 'id')::uuid, (v_k ->> 'lease_id')::uuid,
           '{"status": "posted", "external_id": "yt1", "external_url": "https://www.youtube.com/shorts/yt1"}');
  r := r || case when pg_temp.claimed(v_j, 'A-refused', 'facebook_photo') is not null
                      and (v_m ->> 'ok')::boolean
                      and (select by_hand from mc.marketing_cards where id = pg_temp.cid('A-disc')) = '{}'
                 then 'ok  P53 reconnected, the stopped steps start over; a label Mission Control then posts is no longer asked about'
                 else 'FAIL P53 ' || coalesce(v_m::text, '') || left(coalesce(v_j::text, ''), 300) end;

  select string_agg(a, ',' order by a) into v_t from (
    select distinct substr(action, 11) as a from mc.audit_log where action like 'marketing.autopost%' and result = 'ok') x;
  r := r || case when v_t = 'autopost_by_hand,autopost_card_off,autopost_confirmed,autopost_connect,autopost_destination,autopost_disconnect,autopost_off,autopost_on,autopost_retry'
                      and not exists (select 1 from mc.audit_log where action like 'marketing.autopost%'
                                      and coalesce(detail, '') || coalesce(target_ref, '') || coalesce(before::text, '') || coalesce(after::text, '') like '%v1:%')
                 then 'ok  P54 every auto-posting action is audited, never with a token'
                 else 'FAIL P54 ' || coalesce(v_t, 'none') end;

  raise exception E'VERIFY %/% failed\n%',
    (select count(*) from unnest(r) x where x like 'FAIL%'), array_length(r, 1), array_to_string(r, E'\n');
end
$verify$;
