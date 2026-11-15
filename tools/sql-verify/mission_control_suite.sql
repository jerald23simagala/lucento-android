-- Mission Control V1 (supabase/migrations/20261116090000…090300_mission_control_*.sql, docs/mission-control/v1).
-- Run with tools/sql-verify (see README.md):
--   node run.mjs ../../supabase/migrations mission_control_suite.sql
-- Plays three admins (one active with a fresh authenticator code, one whose code is an hour old, one deactivated), a
-- handful of customers and anonymous callers against every control in SECURITY_CONSIDERATIONS.md §8: who may call what,
-- step-up, limits, the append-only audit log, grants that never touch paid rows, suspension, deletion parity with the
-- in-app Delete account, moving a purchase, the support form's limits and allow-list, the records view's gates, and the
-- numbers behind Overview, Emails and Revenue. Raises one exception listing every check; "VERIFY 0/N failed" is a pass.
do $verify$
declare
  r text[] := '{}';
  v_admin uuid := gen_random_uuid(); v_admin2 uuid := gen_random_uuid(); v_off uuid := gen_random_uuid();
  v_c uuid := gen_random_uuid();  -- Carla: paid Lifetime, a synced ledger
  v_d uuid := gen_random_uuid();  -- Dino: free, gets complimentary grants
  v_e uuid := gen_random_uuid();  -- Erin: a GRANT row written by hand before Mission Control
  v_f uuid := gen_random_uuid();  -- Fely: annual subscriber, receives a moved purchase
  v_g uuid := gen_random_uuid();  -- Gabby: deleted by an admin
  v_h uuid := gen_random_uuid();  -- the founder's own customer account (internal)
  v_i uuid := gen_random_uuid();  -- Ivan: a hand-written GRANT that had been taken back
  v_mara uuid := gen_random_uuid();  -- Mara: a hand-written GRANT after Mission Control (left alone)
  v_tess uuid := gen_random_uuid();  -- Tess: subscription in its free trial
  v_u uuid := gen_random_uuid();  -- Uma: email not verified
  s_fresh uuid := gen_random_uuid(); s_stale uuid := gen_random_uuid(); s_jwt uuid := gen_random_uuid(); s_two uuid := gen_random_uuid();
  h_life text := encode(sha256(convert_to('mc-token-lifetime-carla', 'UTF8')), 'hex');
  h_sub text := encode(sha256(convert_to('mc-token-sub-fely', 'UTF8')), 'hex');
  h_trial text := encode(sha256(convert_to('mc-token-trial-tess', 'UTF8')), 'hex');
  h_move text := encode(sha256(convert_to('mc-token-move', 'UTF8')), 'hex');
  h_refund text := encode(sha256(convert_to('mc-token-refund', 'UTF8')), 'hex');
  i_one uuid := gen_random_uuid(); i_two uuid := gen_random_uuid(); i_three uuid := gen_random_uuid();
  v_j jsonb; v_k jsonb; v_before jsonb; v_t text; v_n integer; v_m integer; v_id uuid; v_ticket bigint; v_ticket2 bigint;
  v_msg bigint; v_secret text; v_access uuid; v_ok boolean; v_ts timestamptz;
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
  -- Rows anywhere in public that still name the account (as account_deletion_suite.sql).
  create function pg_temp.rows_naming(p_uid uuid) returns integer language plpgsql as $f$
  declare v_total integer := 0; v_one integer; c record;
  begin
    for c in select table_name, column_name from information_schema.columns
             where table_schema = 'public' and column_name in ('user_id', 'owner_id', 'partner_id') and data_type = 'uuid'
    loop
      execute format('select count(*) from public.%I where %I = $1', c.table_name, c.column_name) into v_one using p_uid;
      v_total := v_total + v_one;
    end loop;
    return v_total;
  end $f$;

  -- ======================================================================== fixtures
  insert into auth.users (id, email, email_confirmed_at, aud, role, raw_user_meta_data) values
    (v_admin, 'ops@lucento-test.invalid', now(), 'authenticated', 'authenticated', '{}'),
    (v_admin2, 'ops2@lucento-test.invalid', now(), 'authenticated', 'authenticated', '{}'),
    (v_off, 'former-ops@lucento-test.invalid', now(), 'authenticated', 'authenticated', '{}'),
    (v_c, 'carla@lucento-test.invalid', now(), 'authenticated', 'authenticated', '{"full_name": "Carla"}'),
    (v_d, 'dino@lucento-test.invalid', now(), 'authenticated', 'authenticated', '{}'),
    (v_e, 'erin@lucento-test.invalid', now(), 'authenticated', 'authenticated', '{}'),
    (v_f, 'fely@lucento-test.invalid', now(), 'authenticated', 'authenticated', '{}'),
    (v_g, 'gabby.delete@lucento-test.invalid', now(), 'authenticated', 'authenticated', '{}'),
    (v_h, 'founder-personal@lucento-test.invalid', now(), 'authenticated', 'authenticated', '{}'),
    (v_i, 'ivan@lucento-test.invalid', now(), 'authenticated', 'authenticated', '{}'),
    (v_mara, 'mara@lucento-test.invalid', now(), 'authenticated', 'authenticated', '{}'),
    (v_tess, 'tess@lucento-test.invalid', now(), 'authenticated', 'authenticated', '{}'),
    (v_u, 'uma@lucento-test.invalid', null, 'authenticated', 'authenticated', '{}');
  insert into mc.admins (user_id, email, display_name, active) values
    (v_admin, 'ops@lucento-test.invalid', 'Ops', true),
    (v_admin2, 'ops2@lucento-test.invalid', 'Ops Two', true),
    (v_off, 'former-ops@lucento-test.invalid', 'Former', false);
  -- Sessions: TOTP verified just now (Auth's own record), an hour ago, and one only the token's amr claim knows about.
  insert into auth.sessions (id, user_id, aal) values
    (s_fresh, v_admin, 'aal2'), (s_stale, v_admin, 'aal2'), (s_jwt, v_admin, 'aal2'), (s_two, v_admin2, 'aal2');
  insert into auth.mfa_amr_claims (session_id, authentication_method, created_at, updated_at) values
    (s_fresh, 'totp', now(), now()),
    (s_stale, 'totp', now() - interval '1 hour', now() - interval '1 hour'),
    (s_two, 'totp', now(), now());
  insert into auth.mfa_factors (user_id, friendly_name, factor_type, status) values (v_admin, 'Phone', 'totp', 'verified');

  -- Paid plans, written by the service role as the Vercel endpoints do.
  set role service_role;
  perform public.apply_play_purchase(v_c, h_life, 'GPA.life-carla', 'lucento_pro_lifetime',
    encode(sha256(convert_to(lower(v_c::text), 'UTF8')), 'hex'), 'ACTIVE', null, now() - interval '3 days', null);
  perform public.apply_play_purchase(v_f, h_sub, 'GPA.sub-fely', 'lucento_pro',
    encode(sha256(convert_to(lower(v_f::text), 'UTF8')), 'hex'), 'ACTIVE', 'annual', now() - interval '2 days', now() + interval '363 days');
  perform public.apply_play_purchase(v_tess, h_trial, 'GPA.trial-tess', 'lucento_pro',
    encode(sha256(convert_to(lower(v_tess::text), 'UTF8')), 'hex'), 'TRIAL', 'monthly', now() - interval '1 day', now() + interval '6 days');
  -- Bought while signed out (no account id in the order): it went to the first account that signed in, Dino's.
  perform public.apply_play_purchase(v_d, h_move, 'GPA.move', 'lucento_pro_lifetime', null, 'ACTIVE', null, now() - interval '1 day', null);
  execute 'reset role';

  -- ======================================================================== K: the gate
  perform pg_temp.as_user(v_d);
  v_j := mc_api.overview();
  execute 'reset role';
  r := r || case when v_j ->> 'error' = 'not_admin' then 'ok  K1 a customer calling Mission Control is refused (not_admin)'
                 else 'FAIL K1 ' || coalesce(v_j::text, 'null') end;
  select count(*) into v_n from mc.audit_log where admin_id = v_d and result = 'refused';
  r := r || case when v_n = 1 then 'ok  K2 the refusal is in the audit log' else 'FAIL K2 refused rows=' || v_n end;

  perform pg_temp.as_admin(v_admin, s_fresh, 'aal1', interval '1 minute');
  v_j := mc_api.overview();
  execute 'reset role';
  r := r || case when v_j ->> 'error' = 'mfa_required' then 'ok  K3 an admin without a verified code (aal1) is refused'
                 else 'FAIL K3 ' || coalesce(v_j::text, 'null') end;

  perform pg_temp.as_admin(v_off, gen_random_uuid(), 'aal2', interval '1 minute');
  v_j := mc_api.overview();
  execute 'reset role';
  r := r || case when v_j ->> 'error' = 'not_admin' then 'ok  K4 a deactivated admin is refused' else 'FAIL K4 ' || coalesce(v_j::text, 'null') end;

  perform pg_temp.as_admin(v_admin, s_fresh, 'aal2', interval '1 hour');
  v_j := mc_api.overview();
  v_k := mc_api.whoami();
  execute 'reset role';
  r := r || case when (v_j ->> 'ok')::boolean and v_j #>> '{users,total}' is not null then 'ok  K5 an active admin at aal2 reads Overview'
                 else 'FAIL K5 ' || left(coalesce(v_j::text, 'null'), 300) end;
  r := r || case when (v_k ->> 'ok')::boolean and (v_k ->> 'fresh_mfa')::boolean and v_k ->> 'aal' = 'aal2'
                 then 'ok  K6 whoami: admin, aal2, fresh code (from Auth''s own session record)' else 'FAIL K6 ' || coalesce(v_k::text, 'null') end;

  begin
    update mc.audit_log set reason = 'tampered';
    r := r || 'FAIL K7 the audit log could be updated'::text;
  exception when insufficient_privilege then r := r || 'ok  K7 the audit log refuses UPDATE, even for its owner'::text; end;
  begin
    delete from mc.audit_log;
    r := r || 'FAIL K8 the audit log could be deleted from'::text;
  exception when insufficient_privilege then r := r || 'ok  K8 the audit log refuses DELETE'::text; end;
  begin
    truncate mc.audit_log;
    r := r || 'FAIL K9 the audit log could be truncated'::text;
  exception when insufficient_privilege then r := r || 'ok  K9 the audit log refuses TRUNCATE'::text; end;

  perform pg_temp.as_user(v_d);
  begin
    perform count(*) from mc.audit_log;
    r := r || 'FAIL K10 a customer read mc.audit_log'::text;
  exception when insufficient_privilege then r := r || 'ok  K10 the mc schema is out of reach for signed-in callers'::text; end;
  begin
    perform mc.rotate_ingest_secret();
    r := r || 'FAIL K11 a customer called mc.rotate_ingest_secret'::text;
  exception when insufficient_privilege then r := r || 'ok  K11 private helpers can''t be called through the API'::text; end;
  execute 'reset role';

  perform pg_temp.as_anon();
  begin
    perform mc_api.overview();
    r := r || 'FAIL K12 anon called mc_api.overview'::text;
  exception when insufficient_privilege then r := r || 'ok  K12 anon can''t call admin functions'::text; end;
  execute 'reset role';

  -- Retrying can't fill the log: at most 10 refusals per caller and reason an hour are written.
  perform pg_temp.as_user(v_d);
  for v_n in 1..15 loop
    perform mc_api.list_users();
  end loop;
  execute 'reset role';
  select count(*) into v_n from mc.audit_log where admin_id = v_d and result = 'refused';
  r := r || case when v_n = 10 then 'ok  K13 refusals are recorded, at most 10 an hour per caller' else 'FAIL K13 refused rows=' || v_n end;

  -- Privileges in the catalog, whatever the defaults: anon executes only the ingest functions; PUBLIC nothing.
  select count(*) into v_n from pg_proc p join pg_namespace s on s.oid = p.pronamespace
    where s.nspname = 'mc_api' and has_function_privilege('anon', p.oid, 'execute')
      and p.proname not in ('ingest_email_events', 'play_orders_wanted', 'ingest_play_orders', 'ingest_job_run', 'ingest_service_checks');
  select count(*) into v_m from pg_proc p join pg_namespace s on s.oid = p.pronamespace
    where s.nspname = 'mc_api' and p.proname like 'ingest%' and has_function_privilege('authenticated', p.oid, 'execute');
  r := r || case when v_n = 0 and v_m = 0 then 'ok  K14 anon runs only the ingest functions; signed-in callers never do'
                 else 'FAIL K14 anon extra=' || v_n || ' authenticated ingest=' || v_m end;
  select count(*) into v_n from pg_proc p join pg_namespace s on s.oid = p.pronamespace
    where s.nspname in ('mc', 'mc_api') and exists (select 1 from aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) a
                                                    where a.grantee = 0 and a.privilege_type = 'EXECUTE');
  r := r || case when v_n = 0 then 'ok  K15 no Mission Control function is executable by PUBLIC' else 'FAIL K15 public-executable=' || v_n end;
  select count(*) into v_n from pg_class c join pg_namespace s on s.oid = c.relnamespace
    where s.nspname = 'mc' and c.relkind = 'r'
      and (has_table_privilege('anon', c.oid, 'select') or has_table_privilege('authenticated', c.oid, 'select')
           or has_table_privilege('service_role', c.oid, 'select'));
  r := r || case when v_n = 0 then 'ok  K16 no API role holds a privilege on any mc table' else 'FAIL K16 tables=' || v_n end;

  -- ======================================================================== S: settings
  perform pg_temp.as_admin(v_admin, s_stale, 'aal2', interval '1 hour');
  v_j := mc_api.settings_set('records_view_enabled', 'true', 'Policy is live');
  execute 'reset role';
  r := r || case when v_j ->> 'error' = 'step_up_required' then 'ok  S1 turning the records view on needs a fresh code'
                 else 'FAIL S1 ' || coalesce(v_j::text, 'null') end;
  perform pg_temp.as_admin(v_admin, s_fresh, 'aal2', interval '1 hour');
  v_j := mc_api.settings_set('resend_daily_limit', '"lots"', 'Typo test');
  v_k := mc_api.settings_set('resend_daily_limit', '150', 'Upgraded the plan');
  execute 'reset role';
  r := r || case when v_j ->> 'error' = 'invalid' and (v_k ->> 'ok')::boolean
                      and (select value from mc.settings where key = 'resend_daily_limit') = '150'::jsonb
                 then 'ok  S2 settings are validated and saved' else 'FAIL S2 ' || coalesce(v_j::text, '') || ' ' || coalesce(v_k::text, '') end;
  select count(*) into v_n from mc.audit_log where action = 'settings.set' and before = '{"resend_daily_limit": 100}'::jsonb
    and after = '{"resend_daily_limit": 150}'::jsonb;
  r := r || case when v_n = 1 then 'ok  S3 a setting change is audited with before and after' else 'FAIL S3 rows=' || v_n end;

  -- ======================================================================== G: complimentary Pro
  -- FD-V5: only a complimentary Lifetime's phone row may go without an expiry.
  begin
    insert into public.entitlements (user_id, source, status, grants, expires_at) values (v_u, 'GRANT', 'ACTIVE', array['CLOUD'], null);
    r := r || 'FAIL G0 a GRANT without a product or an expiry was accepted'::text;
  exception when check_violation then
    begin
      insert into public.entitlements (user_id, source, status, grants, product_id, expires_at)
        values (v_u, 'GRANT', 'ACTIVE', array['LOCAL_PRO'], 'lucento_pro_lifetime', null);
      delete from public.entitlements where user_id = v_u;
      r := r || 'ok  G0 only the Lifetime phone row (lucento_pro_lifetime) may be a GRANT without an expiry'::text;
    exception when check_violation then r := r || 'FAIL G0 the Lifetime phone row was refused'::text; end;
  end;
  perform pg_temp.as_admin(v_admin, s_fresh, 'aal2', interval '1 hour');
  v_j := mc_api.grant_pro(v_d, 'days_90', 'Closed-testing tester');
  execute 'reset role';
  select jsonb_agg(jsonb_build_object('p', product_id, 's', status, 'g', grants, 'x', expires_at > now() + interval '89 days'
                                      and expires_at < now() + interval '91 days')) into v_k
    from public.entitlements where user_id = v_d and source = 'GRANT';
  r := r || case when (v_j ->> 'ok')::boolean
                      and v_k = '[{"g": ["AI_PRO", "CLOUD", "LOCAL_PRO", "SHARED"], "p": null, "s": "ACTIVE", "x": true}]'::jsonb
                      and lucento_private.has_capability(v_d, 'CLOUD')
                 then 'ok  G1 a 90-day grant writes one GRANT row with everything for 90 days'
                 else 'FAIL G1 ' || coalesce(v_j::text, '') || ' rows=' || coalesce(v_k::text, 'null') end;

  perform pg_temp.as_admin(v_admin, s_stale, 'aal2', interval '1 hour');
  v_j := mc_api.grant_pro(v_d, 'lifetime', 'Founding tester');
  execute 'reset role';
  select count(*) into v_n from mc.entitlement_grants where user_id = v_d and kind = 'lifetime';
  r := r || case when v_j ->> 'error' = 'step_up_required' and v_n = 0 then 'ok  G2 a Lifetime grant with an hour-old code is refused'
                 else 'FAIL G2 ' || coalesce(v_j::text, '') end;

  perform pg_temp.as_admin(v_admin, s_jwt, 'aal2', interval '2 minutes');
  v_j := mc_api.grant_pro(v_d, 'lifetime', 'Founding tester');
  execute 'reset role';
  select jsonb_object_agg(coalesce(product_id, 'services'), jsonb_build_object('s', status, 'g', grants, 'never', expires_at is null,
           'year', expires_at > now() + interval '364 days' and expires_at < now() + interval '366 days')) into v_k
    from public.entitlements where user_id = v_d and source = 'GRANT';
  r := r || case when (v_j ->> 'ok')::boolean
                      and v_k -> 'lucento_pro_lifetime' = '{"g": ["LOCAL_PRO"], "s": "ACTIVE", "never": true, "year": null}'::jsonb
                      and v_k -> 'services' = '{"g": ["AI_PRO", "CLOUD", "SHARED"], "s": "ACTIVE", "never": false, "year": true}'::jsonb
                 then 'ok  G3 a Lifetime grant (fresh code from the token''s amr claim): phone features forever, services 365 days, like paid Lifetime'
                 else 'FAIL G3 ' || coalesce(v_j::text, '') || ' rows=' || coalesce(v_k::text, 'null') end;

  select id into v_id from mc.entitlement_grants where user_id = v_d and kind = 'lifetime';
  perform pg_temp.as_admin(v_admin, s_fresh, 'aal2', interval '1 hour');
  v_j := mc_api.revoke_grant(v_id, 'Granted to the wrong person');
  execute 'reset role';
  select jsonb_object_agg(coalesce(product_id, 'services'), jsonb_build_object('s', status, 'g', grants, '90d',
           expires_at > now() + interval '89 days' and expires_at < now() + interval '91 days')) into v_k
    from public.entitlements where user_id = v_d and source = 'GRANT';
  r := r || case when (v_j ->> 'ok')::boolean and v_k #>> '{lucento_pro_lifetime,s}' = 'REVOKED'
                      and v_k -> 'services' = '{"g": ["AI_PRO", "CLOUD", "LOCAL_PRO", "SHARED"], "s": "ACTIVE", "90d": true}'::jsonb
                 then 'ok  G4 revoking the Lifetime grant ends the phone row; the 90-day grant still covers everything'
                 else 'FAIL G4 ' || coalesce(v_j::text, '') || ' rows=' || coalesce(v_k::text, 'null') end;

  select id into v_id from mc.entitlement_grants where user_id = v_d and kind = 'days_90';
  perform pg_temp.as_admin(v_admin, s_fresh, 'aal2', interval '1 hour');
  v_j := mc_api.revoke_grant(v_id, 'Tester left the programme');
  v_k := mc_api.revoke_grant(v_id, 'Twice');
  execute 'reset role';
  -- Dino still owns the Lifetime he bought while signed out: his paid rows keep Pro, and the answer says so.
  r := r || case when (v_j ->> 'ok')::boolean and v_k ->> 'error' = 'already_revoked'
                      and (select count(*) from public.entitlements where user_id = v_d and source = 'GRANT' and status = 'REVOKED') = 2
                      and not exists (select 1 from public.entitlements where user_id = v_d and source = 'GRANT' and mc.is_live(status, expires_at))
                      and (v_j ->> 'paid_rows_kept')::integer = 2 and lucento_private.has_capability(v_d, 'LOCAL_PRO')
                 then 'ok  G5 revoking the last grant ends both GRANT rows (REVOKED); paid Pro stays and is reported; a second revoke is refused'
                 else 'FAIL G5 ' || coalesce(v_j::text, '') || ' ' || coalesce(v_k::text, '') end;
  -- Dino's Lifetime from Google Play (bought signed out) was never touched by any of this.
  select count(*) into v_n from public.entitlements where user_id = v_d and source like 'PLAY%' and status = 'ACTIVE';
  r := r || case when v_n = 2 then 'ok  G6 the paid Lifetime rows on the same account were never touched' else 'FAIL G6 live paid rows=' || v_n end;

  select jsonb_agg(to_jsonb(e) order by source) into v_before from public.entitlements e where user_id = v_c;
  perform pg_temp.as_admin(v_admin, s_fresh, 'aal2', interval '1 hour');
  v_j := mc_api.grant_pro(v_c, 'days_30', 'Goodwill after a sync bug');
  v_k := mc_api.revoke_grant((v_j ->> 'grant_id')::uuid, 'Fixed');
  execute 'reset role';
  select jsonb_agg(to_jsonb(e) order by source) into v_j from public.entitlements e where user_id = v_c and source like 'PLAY%';
  r := r || case when v_j = (select jsonb_agg(x order by x ->> 'source') from jsonb_array_elements(v_before) x where x ->> 'source' like 'PLAY%')
                 then 'ok  G7 grant and revoke on a paying customer leave every PLAY_* row byte-for-byte as it was'
                 else 'FAIL G7 paid rows changed' end;

  -- Hand-written rows from before Mission Control: one running, one already taken back.
  insert into public.entitlements (user_id, source, status, grants, expires_at, note, starts_at) values
    (v_e, 'GRANT', 'ACTIVE', array['LOCAL_PRO', 'CLOUD', 'AI_PRO', 'SHARED'], now() + interval '60 days', 'Rachel', now() - interval '5 days'),
    (v_i, 'GRANT', 'REVOKED', array['LOCAL_PRO', 'CLOUD', 'AI_PRO', 'SHARED'], now() - interval '1 day', 'Test', now() - interval '9 days');
  v_n := mc.import_manual_grants();
  v_m := mc.import_manual_grants();
  r := r || case when v_n = 2 and v_m = 0
                      and (select count(*) from mc.entitlement_grants where user_id = v_e and kind = 'imported' and revoked_at is null) = 1
                      and (select count(*) from mc.entitlement_grants where user_id = v_i and kind = 'imported' and revoked_at is not null) = 1
                 then 'ok  G8 hand-written GRANT rows become history once (running stays running, taken back stays ended)'
                 else 'FAIL G8 first=' || v_n || ' second=' || v_m end;
  -- Grants are a set to the app (EntitlementCache): compare them sorted, everything else as it is.
  select to_jsonb(e) - 'verified_at' - 'updated_at' - 'note' - 'grants'
         || jsonb_build_object('grants', (select jsonb_agg(g order by g) from unnest(e.grants) g))
    into v_before from public.entitlements e where user_id = v_e and source = 'GRANT';
  perform mc.recompute_grants(v_e);
  select to_jsonb(e) - 'verified_at' - 'updated_at' - 'note' - 'grants'
         || jsonb_build_object('grants', (select jsonb_agg(g order by g) from unnest(e.grants) g))
    into v_j from public.entitlements e where user_id = v_e and source = 'GRANT';
  r := r || case when v_j = v_before then 'ok  G9 recomputing an imported account writes the same row it had'
                 else 'FAIL G9 before=' || v_before::text || ' after=' || v_j::text end;

  -- A GRANT written by hand after Mission Control (no grant behind it) is never undone by the job.
  insert into public.entitlements (user_id, source, status, grants, expires_at, note) values
    (v_mara, 'GRANT', 'ACTIVE', array['CLOUD'], now() - interval '1 hour', 'by hand');
  -- Erin's grant runs out (time passes: dates moved back).
  update mc.entitlement_grants set starts_at = now() - interval '70 days', ends_at = now() - interval '1 hour',
    services_end_at = now() - interval '1 hour' where user_id = v_e;
  update public.entitlements set expires_at = now() - interval '1 hour' where user_id = v_e and source = 'GRANT';
  v_n := mc.expire_grants();
  r := r || case when (select status from public.entitlements where user_id = v_e and source = 'GRANT') = 'EXPIRED'
                      and (select status from public.entitlements where user_id = v_mara and source = 'GRANT') = 'ACTIVE'
                 then 'ok  G10 a grant that ran out ends as EXPIRED; a row written by hand is left alone'
                 else 'FAIL G10 expired=' || v_n end;

  perform pg_temp.as_admin(v_admin, s_fresh, 'aal2', interval '1 hour');
  v_j := mc_api.grant_pro(v_admin2, 'days_30', 'Testing the console');
  v_k := mc_api.grant_pro(v_d, 'days_30', 'ok');
  v_t := (mc_api.grant_pro(v_d, 'days_7', 'Unknown length')) ->> 'error';
  execute 'reset role';
  r := r || case when v_j ->> 'error' = 'admin_account' and v_k ->> 'error' = 'reason_required' and v_t = 'invalid'
                 then 'ok  G11 grants to admin logins, without a reason, or of unknown length are refused'
                 else 'FAIL G11 ' || coalesce(v_j::text, '') || ' ' || coalesce(v_k::text, '') || ' ' || coalesce(v_t, '') end;

  insert into mc.rate_counters (admin_id, action, hour, count) values (v_admin, 'grants.write', date_trunc('hour', now()), 20)
    on conflict (admin_id, action, hour) do update set count = 20;
  perform pg_temp.as_admin(v_admin, s_fresh, 'aal2', interval '1 hour');
  v_j := mc_api.grant_pro(v_d, 'days_30', 'One too many');
  execute 'reset role';
  r := r || case when v_j ->> 'error' = 'rate_limited' then 'ok  G12 the 21st grant in an hour is refused' else 'FAIL G12 ' || coalesce(v_j::text, '') end;
  delete from mc.rate_counters where admin_id = v_admin and action = 'grants.write';

  -- ======================================================================== A: suspension
  insert into auth.sessions (id, user_id, aal, refreshed_at) values (gen_random_uuid(), v_d, 'aal1', (now() at time zone 'utc'));
  insert into auth.refresh_tokens (token, user_id, revoked, session_id)
    select 'rt-dino', v_d::text, false, s.id from auth.sessions s where s.user_id = v_d;
  perform pg_temp.as_admin(v_admin, s_stale, 'aal2', interval '1 hour');
  v_j := mc_api.suspend(v_d, 'Chargeback abuse');
  execute 'reset role';
  r := r || case when v_j ->> 'error' = 'step_up_required' and (select banned_until from auth.users where id = v_d) is null
                 then 'ok  A1 suspending needs a fresh code' else 'FAIL A1 ' || coalesce(v_j::text, '') end;
  perform pg_temp.as_admin(v_admin, s_fresh, 'aal2', interval '1 hour');
  v_j := mc_api.suspend(v_d, 'Chargeback abuse');
  v_k := mc_api.suspend(v_d, 'Again');
  execute 'reset role';
  r := r || case when (v_j ->> 'ok')::boolean and (v_j ->> 'sessions_ended')::integer = 1
                      and (select banned_until from auth.users where id = v_d) > now() + interval '50 years'
                      and not exists (select 1 from auth.sessions where user_id = v_d)
                      and not exists (select 1 from auth.refresh_tokens where user_id = v_d::text)
                      and mc.status_of(v_d) = 'suspended' and v_k ->> 'error' = 'already_suspended'
                 then 'ok  A2 suspend: Auth refuses the account, its sessions and refresh tokens end now'
                 else 'FAIL A2 ' || coalesce(v_j::text, '') || ' ' || coalesce(v_k::text, '') end;
  perform pg_temp.as_admin(v_admin, s_fresh, 'aal2', interval '1 hour');
  v_j := mc_api.reactivate(v_d, 'Resolved with the bank');
  v_k := mc_api.suspend(v_admin2, 'Testing');
  execute 'reset role';
  r := r || case when (v_j ->> 'ok')::boolean and (select banned_until from auth.users where id = v_d) is null
                      and mc.status_of(v_d) = 'active' and v_k ->> 'error' = 'admin_account'
                 then 'ok  A3 reactivate clears the ban; admin logins can''t be suspended here'
                 else 'FAIL A3 ' || coalesce(v_j::text, '') || ' ' || coalesce(v_k::text, '') end;

  -- ======================================================================== D: deletion
  -- Gabby has a little of everything Mission Control keeps about an account, plus a synced ledger.
  insert into public.wallets (id, user_id, name, type, currency_code, opening_balance_minor, created_at, updated_at)
    values ('wg-' || v_g, v_g, 'Cash', 'CASH', 'PHP', 0, now(), now());
  insert into public.transactions (id, user_id, type, amount_minor, currency_code, wallet_id, occurred_on, note, created_at, updated_at)
    values ('tg-' || v_g, v_g, 'EXPENSE', 1500, 'PHP', 'wg-' || v_g, 20400, '', now(), now());
  insert into mc.account_clients (user_id, app_version, version_code, android_sdk, device_model) values (v_g, '0.6.0', 15, 35, 'Pixel 8');
  insert into mc.account_activity (user_id, day, signals) values (v_g, current_date, array['sync']);
  perform pg_temp.as_user(v_g);
  v_j := public.submit_support_request('account', 'Please delete my account', null, gen_random_uuid(), gen_random_uuid(), '{}');
  execute 'reset role';
  perform pg_temp.as_admin(v_admin, s_fresh, 'aal2', interval '1 hour');
  v_k := mc_api.grant_pro(v_g, 'days_30', 'Tester');
  v_j := mc_api.schedule_deletion(v_g, 'Customer asked by email', 'gabby');
  v_k := mc_api.schedule_deletion(v_g, 'Customer asked by email', 'Gabby.Delete');
  execute 'reset role';
  r := r || case when v_j ->> 'error' = 'confirm_mismatch' and (v_k ->> 'ok')::boolean
                      and (v_k ->> 'run_after')::timestamptz = now() + interval '24 hours' and mc.status_of(v_g) = 'deletion_scheduled'
                 then 'ok  D1 deletion needs the email''s local part typed; it waits 24 hours'
                 else 'FAIL D1 ' || coalesce(v_j::text, '') || ' ' || coalesce(v_k::text, '') end;
  v_n := mc.run_due_deletions();
  r := r || case when v_n = 0 and exists (select 1 from auth.users where id = v_g) then 'ok  D2 nothing is deleted during the cool-off'
                 else 'FAIL D2 deleted=' || v_n end;
  update mc.deletion_requests set requested_at = requested_at - interval '25 hours', run_after = run_after - interval '25 hours'
    where user_id = v_g;
  v_n := mc.run_due_deletions();
  r := r || case when v_n = 1 and not exists (select 1 from auth.users where id = v_g) and pg_temp.rows_naming(v_g) = 0
                      and not exists (select 1 from mc.entitlement_grants where user_id = v_g)
                      and not exists (select 1 from mc.tickets where user_id = v_g)
                      and not exists (select 1 from mc.account_clients where user_id = v_g)
                      and not exists (select 1 from mc.account_activity where user_id = v_g)
                 then 'ok  D3 after the cool-off the same cascade as Delete account runs: nothing of the account is left'
                 else 'FAIL D3 deleted=' || v_n || ' left=' || pg_temp.rows_naming(v_g) end;
  r := r || case when (select result from mc.deletion_requests where user_id = v_g) = 'deleted'
                      and exists (select 1 from mc.audit_log where action = 'accounts.deletion_executed' and target_user_id = v_g
                                  and admin_id = v_admin and result = 'ok')
                      and exists (select 1 from mc.audit_log where action = 'grants.grant' and target_user_id = v_g)
                 then 'ok  D4 the request and the audit trail outlive the account, attributed to the admin who asked'
                 else 'FAIL D4' end;
  perform pg_temp.as_admin(v_admin, s_fresh, 'aal2', interval '1 hour');
  v_j := mc_api.schedule_deletion(v_e, 'Asked by email', 'erin');
  v_k := mc_api.cancel_deletion((v_j ->> 'request_id')::uuid, 'Changed their mind');
  execute 'reset role';
  update mc.deletion_requests set requested_at = requested_at - interval '25 hours', run_after = run_after - interval '25 hours'
    where user_id = v_e;
  v_n := mc.run_due_deletions();
  r := r || case when (v_k ->> 'ok')::boolean and v_n = 0 and exists (select 1 from auth.users where id = v_e)
                 then 'ok  D5 a cancelled deletion never runs' else 'FAIL D5 ' || coalesce(v_k::text, '') end;

  -- ======================================================================== P: moving a purchase
  perform pg_temp.as_admin(v_admin, s_stale, 'aal2', interval '1 hour');
  v_j := mc_api.resolve_purchase_link(h_move, v_f, 'Bought on the wrong account', 'Customer sent order GPA.move from Google');
  execute 'reset role';
  r := r || case when v_j ->> 'error' = 'step_up_required' then 'ok  P1 moving a purchase needs a fresh code' else 'FAIL P1 ' || coalesce(v_j::text, '') end;
  perform pg_temp.as_admin(v_admin, s_fresh, 'aal2', interval '1 hour');
  v_j := mc_api.resolve_purchase_link(h_move, v_f, 'Bought on the wrong account', 'Customer sent order GPA.move from Google');
  execute 'reset role';
  r := r || case when (v_j ->> 'ok')::boolean
                      and (select user_id from public.purchase_links where purchase_token_hash = h_move) = v_f
                      and (select relinked_count from public.purchase_links where purchase_token_hash = h_move) = 1
                      and (select count(*) from public.entitlements where user_id = v_f and purchase_token_hash = h_move) = 2
                      and not exists (select 1 from public.entitlements where user_id = v_d and purchase_token_hash = h_move)
                      and lucento_private.has_capability(v_f, 'LOCAL_PRO')
                      and (select count(*) from mc.audit_log where action in ('purchases.relink_out', 'purchases.relink_in')) = 2
                 then 'ok  P2 the purchase and both of its rows move; both accounts'' histories record it'
                 else 'FAIL P2 ' || coalesce(v_j::text, '') end;
  perform pg_temp.as_admin(v_admin, s_fresh, 'aal2', interval '1 hour');
  v_j := mc_api.resolve_purchase_link(h_life, v_f, 'Wants it on the other account', 'Order GPA.life-carla, same Google account');
  execute 'reset role';
  r := r || case when v_j ->> 'error' = 'target_has_plan' and (select user_id from public.purchase_links where purchase_token_hash = h_life) = v_c
                 then 'ok  P3 a target that already holds a live Lifetime is refused, nothing moves'
                 else 'FAIL P3 ' || coalesce(v_j::text, '') end;
  set role service_role;
  perform public.apply_play_purchase(v_e, h_refund, 'GPA.refund', 'lucento_pro_lifetime', null, 'ACTIVE', null, now() - interval '1 day', null);
  perform public.revoke_play_purchase(h_refund);
  execute 'reset role';
  perform pg_temp.as_admin(v_admin, s_fresh, 'aal2', interval '1 hour');
  v_j := mc_api.resolve_purchase_link(h_refund, v_f, 'Move it', 'Order GPA.refund from the customer');
  execute 'reset role';
  r := r || case when v_j ->> 'error' = 'refunded' then 'ok  P4 a refunded purchase can''t be moved' else 'FAIL P4 ' || coalesce(v_j::text, '') end;

  -- ======================================================================== T: support
  perform pg_temp.as_anon();
  v_j := public.submit_support_request('billing', E'I bought Lifetime but it says Free\nOrder GPA.move', ' Buyer@Example.COM ', i_one, i_two,
    '{"app_version": "0.6.0", "version_code": 15, "android_sdk": 35, "device_model": "Pixel 8", "sync_enabled": true,
      "secret_note": "x", "plan": {"nested": true}}'::jsonb || jsonb_build_object('locale', repeat('x', 81)));
  v_k := public.submit_support_request('billing', 'Retry of the same request', 'buyer@example.com', i_one, i_two, '{}');
  execute 'reset role';
  v_ticket := regexp_replace(v_j ->> 'ticket', '\D', '', 'g')::bigint;
  r := r || case when (v_j ->> 'ok')::boolean and v_j ->> 'ticket' ~ '^LC-\d+$' and v_j ->> 'reply_to' = 'buyer@example.com'
                      and v_k ->> 'ticket' = v_j ->> 'ticket' and (v_k ->> 'duplicate')::boolean
                      and (select count(*) from mc.tickets where install_id = i_one) = 1
                 then 'ok  T1 signed out: a request becomes a ticket; a retry with the same request id returns the same ticket'
                 else 'FAIL T1 ' || coalesce(v_j::text, '') || ' ' || coalesce(v_k::text, '') end;
  select diagnostics into v_k from mc.tickets where id = v_ticket;
  r := r || case when v_k = '{"app_version": "0.6.0", "version_code": 15, "android_sdk": 35, "device_model": "Pixel 8", "sync_enabled": true}'::jsonb
                      and (select subject from mc.tickets where id = v_ticket) = 'Billing and Pro: I bought Lifetime but it says Free'
                 then 'ok  T2 only allow-listed details are kept; the subject is the topic and first line'
                 else 'FAIL T2 ' || coalesce(v_k::text, '') end;
  perform pg_temp.as_anon();
  v_j := public.submit_support_request('bug', 'Second', 'buyer@example.com', i_one, gen_random_uuid(), '{}');
  v_j := public.submit_support_request('bug', 'Third', 'buyer@example.com', i_one, gen_random_uuid(), '{}');
  v_k := public.submit_support_request('bug', 'Fourth', 'buyer@example.com', i_one, gen_random_uuid(), '{}');
  execute 'reset role';
  r := r || case when (v_j ->> 'ok')::boolean and v_k ->> 'error' = 'rate_limited'
                 then 'ok  T3 the 4th signed-out request from one install in a day is refused' else 'FAIL T3 ' || coalesce(v_k::text, '') end;
  perform pg_temp.as_anon();
  v_j := public.submit_support_request('bug', 'Hi', 'not-an-email', i_three, gen_random_uuid(), '{}');
  v_k := public.submit_support_request('bug', ' ', 'a@b.co', i_three, gen_random_uuid(), '{}');
  v_t := (public.submit_support_request('weather', 'Hi', 'a@b.co', i_three, gen_random_uuid(), '{}')) ->> 'error';
  v_before := public.submit_support_request('bug', repeat('x', 5001), 'a@b.co', i_three, gen_random_uuid(), '{}');
  execute 'reset role';
  r := r || case when v_j ->> 'error' = 'invalid_email' and v_k ->> 'error' = 'message_empty' and v_t = 'invalid_topic'
                      and v_before ->> 'error' = 'message_too_long'
                 then 'ok  T4 a bad email, an empty or too-long message and an unknown topic are refused'
                 else 'FAIL T4 ' || coalesce(v_j::text, '') || coalesce(v_k::text, '') || coalesce(v_t, '') || coalesce(v_before::text, '') end;
  perform pg_temp.as_anon();
  begin
    perform count(*) from mc.tickets;
    r := r || 'FAIL T5 anon read mc.tickets'::text;
  exception when insufficient_privilege then r := r || 'ok  T5 the form can''t read any ticket back'::text; end;
  execute 'reset role';

  update mc.settings set value = '4' where key = 'support_signed_out_hourly_cap';
  perform pg_temp.as_anon();
  v_j := public.submit_support_request('idea', 'Dark mode please', 'fan@example.com', gen_random_uuid(), gen_random_uuid(), '{}');
  v_k := public.submit_support_request('idea', 'Me too', 'fan2@example.com', gen_random_uuid(), gen_random_uuid(), '{}');
  execute 'reset role';
  r := r || case when (v_j ->> 'ok')::boolean and v_k ->> 'error' = 'busy'
                 then 'ok  T6 signed-out requests share an hourly cap (Settings), whatever the install id'
                 else 'FAIL T6 ' || coalesce(v_j::text, '') || ' ' || coalesce(v_k::text, '') end;
  update mc.settings set value = '20' where key = 'support_signed_out_hourly_cap';

  perform pg_temp.as_user(v_c);
  v_j := public.submit_support_request('sync', 'My transactions are missing', 'someone-else@example.com', gen_random_uuid(), gen_random_uuid(),
    '{"last_sync_at": "2026-11-14T08:00:00Z"}');
  execute 'reset role';
  v_ticket2 := regexp_replace(v_j ->> 'ticket', '\D', '', 'g')::bigint;
  r := r || case when (v_j ->> 'ok')::boolean and v_j ->> 'reply_to' = 'carla@lucento-test.invalid'
                      and (select user_id from mc.tickets where id = v_ticket2) = v_c
                      and (select requester_email from mc.tickets where id = v_ticket2) is null
                 then 'ok  T7 signed in: linked to the account, replies go to its own address (the typed one is ignored)'
                 else 'FAIL T7 ' || coalesce(v_j::text, '') end;

  perform pg_temp.as_admin(v_admin, s_fresh, 'aal2', interval '1 hour');
  v_j := mc_api.ticket_add_message(v_ticket, 'reply', 'Hi, I''m on it.');
  v_msg := (v_j ->> 'message_id')::bigint;
  v_k := mc_api.ticket_reply_result(v_msg, 're_test_123', null);
  execute 'reset role';
  r := r || case when (v_j ->> 'ok')::boolean and v_j ->> 'recipient' = 'buyer@example.com'
                      and v_j ->> 'subject' = '[LC-' || v_ticket || '] Billing and Pro: I bought Lifetime but it says Free'
                      and (select status from mc.tickets where id = v_ticket) = 'awaiting_customer'
                      and (select first_response_at from mc.tickets where id = v_ticket) is not null
                      and (select delivery from mc.ticket_messages where id = v_msg) = 'sent'
                 then 'ok  T8 a reply goes to the requester with [LC-…] in the subject; the ticket awaits the customer'
                 else 'FAIL T8 ' || coalesce(v_j::text, '') || ' ' || coalesce(v_k::text, '') end;
  perform pg_temp.as_admin(v_admin, s_fresh, 'aal2', interval '1 hour');
  v_j := mc_api.ticket_add_message(v_ticket, 'inbound', 'Thanks! It works now.');
  v_k := mc_api.ticket_add_message(v_ticket, 'note', 'Moved GPA.move to Fely');
  execute 'reset role';
  r := r || case when (select status from mc.tickets where id = v_ticket) = 'open'
                      and (select count(*) from mc.ticket_messages where ticket_id = v_ticket) = 4
                 then 'ok  T9 the customer''s answer reopens it; notes are kept with the conversation' else 'FAIL T9' end;

  perform pg_temp.as_admin(v_admin, s_fresh, 'aal2', interval '1 hour');
  v_j := mc_api.ticket_create('Carla@lucento-test.invalid', 'other', 'One-star review about sync', 'Sync never finishes', 'Play review');
  v_k := mc_api.ticket_get(v_ticket);
  execute 'reset role';
  r := r || case when (v_j ->> 'linked')::boolean and (select user_id from mc.tickets where id = (v_j ->> 'id')::bigint) = v_c
                      and (select channel from mc.tickets where id = (v_j ->> 'id')::bigint) = 'manual'
                      and (v_k ->> 'ok')::boolean and jsonb_array_length(v_k -> 'messages') = 4 and v_k -> 'customer' = 'null'::jsonb
                 then 'ok  T10 a manual ticket links to the account with that email; a signed-out ticket shows its thread'
                 else 'FAIL T10 ' || coalesce(v_j::text, '') end;

  perform pg_temp.as_user(v_c);
  v_j := public.report_client('0.6.0', 15, 35, 'Pixel 8');
  v_k := public.report_client('0.6.0', 15, 35, 'Pixel 8');
  v_t := (public.report_client('0.6.0; drop', 15, 35, 'Pixel 8')) ->> 'error';
  execute 'reset role';
  r := r || case when (v_j ->> 'recorded')::boolean and not (v_k ->> 'recorded')::boolean and v_t = 'invalid'
                      and (select app_version from mc.account_clients where user_id = v_c) = '0.6.0'
                 then 'ok  T11 report_client records the version once an hour and refuses malformed values'
                 else 'FAIL T11 ' || coalesce(v_j::text, '') || ' ' || coalesce(v_k::text, '') || ' ' || coalesce(v_t, '') end;
  perform pg_temp.as_anon();
  begin
    perform public.report_client('0.6.0', 15, 35, 'Pixel 8');
    r := r || 'FAIL T12 anon called report_client'::text;
  exception when insufficient_privilege then r := r || 'ok  T12 report_client is for signed-in accounts only'::text; end;
  execute 'reset role';

  -- ======================================================================== R: the records view
  insert into public.wallets (id, user_id, name, type, currency_code, opening_balance_minor, sort_order, created_at, updated_at) values
    ('wc-cash', v_c, 'Cash', 'CASH', 'PHP', 100000, 0, now(), now()),
    ('wc-save', v_c, 'Savings', 'SAVINGS', 'PHP', 0, 1, now(), now()),
    ('wc-card', v_c, 'Card', 'CREDIT_CARD', 'PHP', 0, 2, now(), now());
  insert into public.transactions (id, user_id, type, amount_minor, currency_code, wallet_id, occurred_on, note, transfer_group, created_at, updated_at) values
    ('tc-1', v_c, 'INCOME', 50000, 'PHP', 'wc-cash', 20400, '', null, now(), now()),
    ('tc-2', v_c, 'EXPENSE', 2500, 'PHP', 'wc-cash', 20401, 'birthday gift for mom', null, now(), now()),
    ('tc-3', v_c, 'TRANSFER_OUT', 10000, 'PHP', 'wc-cash', 20402, '', 'g1', now(), now()),
    ('tc-4', v_c, 'TRANSFER_IN', 10000, 'PHP', 'wc-save', 20402, '', 'g1', now(), now()),
    ('tc-5', v_c, 'EXPENSE', 3000, 'PHP', 'wc-card', 20403, '', null, now(), now());

  perform pg_temp.as_admin(v_admin, s_fresh, 'aal2', interval '1 hour');
  v_j := mc_api.open_records(v_c, 'LC-' || v_ticket2);
  execute 'reset role';
  r := r || case when v_j ->> 'error' = 'records_view_disabled'
                      and exists (select 1 from mc.audit_log where action = 'access.records_open' and result = 'refused' and admin_id = v_admin)
                 then 'ok  R1 with the setting off (before the policy release), records can''t be opened, and the attempt is logged'
                 else 'FAIL R1 ' || coalesce(v_j::text, '') end;
  update mc.settings set value = 'true' where key = 'records_view_enabled';
  perform pg_temp.as_admin(v_admin, s_stale, 'aal2', interval '1 hour');
  v_j := mc_api.open_records(v_c, 'LC-' || v_ticket2);
  execute 'reset role';
  r := r || case when v_j ->> 'error' = 'step_up_required' then 'ok  R2 opening records needs a fresh code' else 'FAIL R2 ' || coalesce(v_j::text, '') end;
  perform pg_temp.as_admin(v_admin, s_fresh, 'aal2', interval '1 hour');
  v_j := mc_api.open_records(v_c, ' ');
  v_k := mc_api.open_records(v_c, 'LC-' || v_ticket2);
  v_access := (v_k ->> 'access_id')::uuid;
  v_before := mc_api.user_records(v_access, 'wallets');
  v_k := mc_api.user_records(v_access, 'transactions');
  execute 'reset role';
  select jsonb_object_agg(x ->> 'name', x ->> 'balance_minor') into v_j from jsonb_array_elements(v_before -> 'rows') x;
  r := r || case when v_j = '{"Cash": "137500", "Savings": "10000", "Card": "3000"}'::jsonb
                 then 'ok  R3 balances follow docs/CALCULATIONS.md (assets count inflows, a card counts what''s owed)'
                 else 'FAIL R3 ' || coalesce(v_j::text, '') || ' ' || left(coalesce(v_before::text, ''), 300) end;
  r := r || case when (v_k ->> 'total')::integer = 5 and v_k::text not like '%birthday%'
                      and (select (x ->> 'has_note')::boolean from jsonb_array_elements(v_k -> 'rows') x where x ->> 'id' = 'tc-2')
                 then 'ok  R4 transactions come without their notes, only whether there is one'
                 else 'FAIL R4 ' || left(coalesce(v_k::text, ''), 300) end;
  perform pg_temp.as_admin(v_admin, s_fresh, 'aal2', interval '1 hour');
  v_j := mc_api.reveal_note(v_access, 'tc-2');
  execute 'reset role';
  r := r || case when v_j ->> 'note' = 'birthday gift for mom'
                      and (select count(*) from mc.audit_log where target_user_id = v_c and action in ('access.records_open', 'access.records_view', 'access.note_reveal')
                           and result = 'ok') = 4
                 then 'ok  R5 a revealed note is shown; opening, each page and the reveal are all in the audit log'
                 else 'FAIL R5 ' || coalesce(v_j::text, '') end;
  perform pg_temp.as_admin(v_admin2, s_two, 'aal2', interval '1 hour');
  v_j := mc_api.user_records(v_access, 'wallets');
  execute 'reset role';
  r := r || case when v_j ->> 'error' = 'records_session_closed' then 'ok  R6 another admin can''t use someone else''s records session'
                 else 'FAIL R6 ' || coalesce(v_j::text, '') end;
  perform pg_temp.as_admin(v_admin, s_fresh, 'aal2', interval '1 hour');
  perform mc_api.close_records(v_access);
  v_j := mc_api.user_records(v_access, 'wallets');
  execute 'reset role';
  update mc.settings set value = 'false' where key = 'records_view_enabled';
  r := r || case when v_j ->> 'error' = 'records_session_closed' then 'ok  R7 a closed records session reads nothing'
                 else 'FAIL R7 ' || coalesce(v_j::text, '') end;

  -- ======================================================================== O: activity, overview, directory
  insert into auth.sessions (id, user_id, aal, refreshed_at) values (gen_random_uuid(), v_c, 'aal1', (now() at time zone 'utc'));
  update auth.sessions set refreshed_at = (now() at time zone 'utc') where user_id = v_admin;
  v_n := mc.capture_activity();
  r := r || case when exists (select 1 from mc.account_activity where user_id = v_c and day = (now() at time zone mc.tz())::date
                              and signals @> array['refresh', 'sync'])
                      and not exists (select 1 from mc.account_activity where user_id = v_admin)
                 then 'ok  O1 the hourly job records refreshes and synced writes as a day of activity; admin logins never'
                 else 'FAIL O1 days=' || v_n end;

  perform pg_temp.as_admin(v_admin, s_fresh, 'aal2', interval '1 hour');
  v_j := mc_api.set_internal(v_h, 'Founder (personal)', 'My own customer account');
  v_k := mc_api.overview();
  execute 'reset role';
  select count(*) into v_n from auth.users u where u.id not in (v_admin, v_admin2, v_off, v_h);
  r := r || case when (v_j ->> 'ok')::boolean and (v_k #>> '{users,total}')::integer = v_n
                      and (v_k #>> '{users,plans,lifetime}')::integer = 2 and (v_k #>> '{users,plans,trial}')::integer = 1
                      and (v_k #>> '{users,dau}')::integer >= 1
                 then 'ok  O2 Overview counts customers only (admins and internal accounts left out), by plan'
                 else 'FAIL O2 expected total ' || v_n || ': ' || left(coalesce(v_k -> 'users', 'null'::jsonb)::text, 400) end;

  v_n := mc.daily_rollup((now() at time zone mc.tz())::date);
  r := r || case when v_n >= 10 and exists (select 1 from mc.daily_metrics where day = (now() at time zone mc.tz())::date and key = 'users_total')
                 then 'ok  O3 the nightly roll-up writes the day''s totals' else 'FAIL O3 metrics=' || v_n end;

  perform pg_temp.as_admin(v_admin, s_fresh, 'aal2', interval '1 hour');
  v_j := mc_api.list_users(p_plan := 'lifetime');
  v_k := mc_api.list_users(p_query := 'CARLA');
  perform mc_api.user_profile(v_c);
  v_before := mc_api.user_profile(v_c);
  execute 'reset role';
  r := r || case when (v_j ->> 'total')::integer = 2 and (v_k ->> 'total')::integer = 1
                      and v_k #>> '{rows,0,email}' = 'carla@lucento-test.invalid' and v_k #>> '{rows,0,app_version}' = '0.6.0'
                 then 'ok  O4 the directory searches and filters by plan' else 'FAIL O4 ' || left(coalesce(v_j::text, ''), 300) end;
  select count(*) into v_n from mc.audit_log where action = 'access.profile_open' and target_user_id = v_c and admin_id = v_admin;
  r := r || case when v_n = 1 and (v_before ->> 'ok')::boolean
                      and v_before #>> '{plan,label}' = 'lifetime'
                      and jsonb_array_length(v_before -> 'purchases') = 1 and v_before #>> '{purchases,0,bought}' = 'this_account'
                      and (v_before #>> '{usage,wallets}')::integer = 3 and (v_before #>> '{usage,debts_and_loans}')::integer = 1
                      and v_before #>> '{client,device_model}' = 'Pixel 8'
                 then 'ok  O5 a profile shows plan, purchases, usage and device; opening it twice in 10 minutes logs once'
                 else 'FAIL O5 opens=' || v_n || ' ' || left(coalesce(v_before::text, ''), 300) end;
  perform pg_temp.as_admin(v_admin, s_fresh, 'aal2', interval '1 hour');
  v_j := mc_api.user_profile(v_d);
  v_k := mc_api.search('LC-' || v_ticket);
  execute 'reset role';
  r := r || case when jsonb_array_length(v_j -> 'purchases_elsewhere') = 0 and jsonb_array_length(v_k -> 'tickets') = 1
                 then 'ok  O6 search finds a ticket by its number' else 'FAIL O6 ' || left(coalesce(v_k::text, ''), 300) end;

  -- ======================================================================== E: emails
  v_secret := mc.rotate_ingest_secret();
  perform pg_temp.as_anon();
  v_j := mc_api.ingest_email_events('0123456789abcdef0123456789abcdef-wrong', '[]');
  v_k := mc_api.ingest_email_events(v_secret, jsonb_build_array(
    jsonb_build_object('event_id', 'msg_1', 'email_id', 're_test_123', 'type', 'email.delivered', 'at', now(),
      'from_domain', 'send.lucentoapp.com', 'recipient', 'Buyer@Example.com', 'subject', '[LC-1001] Re'),
    jsonb_build_object('event_id', 'msg_2', 'email_id', 're_kph_1', 'type', 'email.sent', 'at', now(),
      'from_domain', 'klaroph.com', 'recipient', 'someone@example.org', 'subject', 'KlaroPH receipt'),
    jsonb_build_object('event_id', 'msg_3', 'email_id', 're_luc_1', 'type', 'email.sent', 'at', now(),
      'from_domain', 'send.lucentoapp.com', 'recipient', 'new@example.com', 'subject', 'Welcome to Lucento'),
    jsonb_build_object('event_id', 'msg_1', 'email_id', 're_test_123', 'type', 'email.delivered', 'at', now(),
      'from_domain', 'send.lucentoapp.com', 'recipient', 'Buyer@Example.com', 'subject', '[LC-1001] Re')));
  execute 'reset role';
  r := r || case when v_j ->> 'error' = 'bad_secret' and (v_k ->> 'inserted')::integer = 3
                      and (select recipient from mc.email_events where event_id = 'msg_2') is null
                      and (select subject from mc.email_events where event_id = 'msg_2') is null
                      and (select recipient from mc.email_events where event_id = 'msg_1') = 'buyer@example.com'
                      and (select delivery from mc.ticket_messages where id = v_msg) = 'delivered'
                 then 'ok  E1 Resend events need the secret; KlaroPH''s are counted without recipient or subject; a reply shows delivered'
                 else 'FAIL E1 ' || coalesce(v_j::text, '') || ' ' || coalesce(v_k::text, '') end;
  perform pg_temp.as_admin(v_admin, s_fresh, 'aal2', interval '1 hour');
  v_j := mc_api.email_overview();
  v_k := mc_api.email_send_prepare('about_purchase', v_u);
  v_before := mc_api.email_send_prepare('test');
  v_t := (mc_api.email_send_prepare('complimentary_pro', v_d)) ->> 'error';
  execute 'reset role';
  r := r || case when (v_j #>> '{quota,today}')::integer = 2 and (v_j #>> '{quota,daily_limit}')::integer = 150
                      and v_k ->> 'error' = 'no_verified_email' and v_before ->> 'recipient' = 'ops@lucento-test.invalid' and v_t = 'no_grant'
                 then 'ok  E2 the quota counts both products; template emails go only to verified accounts or the admin'
                 else 'FAIL E2 ' || left(coalesce(v_j -> 'quota', 'null'::jsonb)::text, 200) || ' ' || coalesce(v_k::text, '') || ' ' || coalesce(v_t, '') end;

  -- ======================================================================== V: revenue
  perform pg_temp.as_anon();
  v_j := mc_api.play_orders_wanted(v_secret);
  v_k := mc_api.ingest_play_orders(v_secret, jsonb_build_array(
    jsonb_build_object('order_id', 'GPA.life-carla', 'token_hash', h_life, 'product_id', 'lucento_pro_lifetime', 'currency', 'PHP',
      'total_micros', '3500000000', 'tax_micros', '375000000', 'ordered_at', now() - interval '3 days', 'state', 'PROCESSED'),
    jsonb_build_object('order_id', 'GPA.sub-fely', 'token_hash', h_sub, 'product_id', 'lucento_pro', 'base_plan_id', 'annual',
      'currency', 'PHP', 'total_micros', '1200000000', 'tax_micros', '128571428', 'ordered_at', now() - interval '2 days'),
    jsonb_build_object('order_id', 'GPA.test-1', 'product_id', 'lucento_pro_lifetime', 'currency', 'PHP', 'total_micros', '3500000000',
      'test', true, 'ordered_at', now()),
    -- Google doesn't know this one: kept as NOT_FOUND so the sweep stops asking.
    jsonb_build_object('order_id', 'GPA.trial-tess', 'state', 'NOT_FOUND')));
  select count(*) into v_n from jsonb_array_elements((mc_api.play_orders_wanted(v_secret)) -> 'orders') x
    where x ->> 'order_id' in ('GPA.trial-tess', 'GPA.life-carla', 'GPA.sub-fely');
  execute 'reset role';
  perform pg_temp.as_admin(v_admin, s_fresh, 'aal2', interval '1 hour');
  v_before := mc_api.revenue();
  execute 'reset role';
  r := r || case when jsonb_array_length(v_j -> 'orders') >= 4 and (v_k ->> 'stored')::integer = 4 and v_n = 0
                      and (select x ->> 'test' from jsonb_array_elements(v_j -> 'orders') x where x ->> 'order_id' = 'GPA.life-carla') = 'false'
                 then 'ok  V1 the sweep asks for known first orders, stores Google''s amounts, and stops asking for unknown ones'
                 else 'FAIL V1 wanted after=' || v_n || ' ' || coalesce(v_k::text, '') end;
  r := r || case when v_before #>> '{summary,lifetime,amounts,0,gross_micros}' = '3500000000'
                      and v_before #>> '{summary,lifetime,amounts,0,net_micros}' = '2656250000'
                      and v_before #>> '{summary,lifetime,amounts,0,net_reported}' = 'false'
                      and (v_before #>> '{summary,subscriptions,mrr,subscriptions}')::integer = 1
                      and v_before #>> '{summary,subscriptions,mrr,by_currency,0,mrr_micros}' = '89285714'
                      and (v_before #>> '{summary,refunds,count}')::integer = 1
                 then 'ok  V2 Lifetime gross and estimated net exclude test orders; MRR is the annual plan ÷ 12, never Lifetime or trials'
                 else 'FAIL V2 ' || left(coalesce(v_before -> 'summary', 'null'::jsonb)::text, 600) end;
  -- Once Google reports what Lucento received, that replaces the estimate.
  perform pg_temp.as_anon();
  perform mc_api.ingest_play_orders(v_secret, jsonb_build_array(
    jsonb_build_object('order_id', 'GPA.life-carla', 'token_hash', h_life, 'product_id', 'lucento_pro_lifetime', 'currency', 'PHP',
      'total_micros', '3500000000', 'tax_micros', '375000000', 'developer_revenue_micros', '2656000000',
      'ordered_at', now() - interval '3 days', 'state', 'PROCESSED')));
  execute 'reset role';
  perform pg_temp.as_admin(v_admin, s_fresh, 'aal2', interval '1 hour');
  v_before := mc_api.revenue();
  execute 'reset role';
  r := r || case when v_before #>> '{summary,lifetime,amounts,0,net_micros}' = '2656000000'
                      and v_before #>> '{summary,lifetime,amounts,0,net_reported}' = 'true'
                 then 'ok  V3 net is Google''s reported developer revenue when it has it, an estimate only otherwise'
                 else 'FAIL V3 ' || left(coalesce(v_before #> '{summary,lifetime}', 'null'::jsonb)::text, 400) end;

  raise exception E'VERIFY %/% failed\n%',
    (select count(*) from unnest(r) x where x like 'FAIL%'), array_length(r, 1), array_to_string(r, E'\n');
end
$verify$;
