-- Lucento Pro entitlements suite (docs/MONETIZATION.md §5.4, §10 "Server"). Run with tools/sql-verify (see README.md):
--   node run.mjs ../../supabase/migrations entitlements_suite.sql
-- Acts as a free account, a Pro account (GRANT), a Lifetime owner past the included year and a stranger, and checks what each
-- may read, write and do. Raises one exception listing every check; the runner reads "VERIFY 0/N failed" as a pass.
do $verify$
declare
  r text[] := '{}';
  v_free uuid := gen_random_uuid(); v_pro uuid := gen_random_uuid(); v_life uuid := gen_random_uuid(); v_x uuid := gen_random_uuid();
  v_n integer; v_j jsonb; v_bool boolean; v_i integer;
begin
  create function pg_temp.as_user(p_uid uuid, p_email text) returns void language plpgsql as $f$
  begin
    perform set_config('request.jwt.claims', json_build_object('sub', p_uid, 'role', 'authenticated', 'email', p_email)::text, true);
    perform set_config('role', 'authenticated', true);
  end $f$;

  insert into auth.users (id, email, email_confirmed_at, aud, role) values
    (v_free, 'free@lucento-test.invalid', now(), 'authenticated', 'authenticated'),
    (v_pro, 'pro@lucento-test.invalid', now(), 'authenticated', 'authenticated'),
    (v_life, 'lifetime@lucento-test.invalid', now(), 'authenticated', 'authenticated'),
    (v_x, 'stranger@lucento-test.invalid', now(), 'authenticated', 'authenticated');

  -- Rows the server would write (as the table owner here; the service role in Supabase).
  insert into public.entitlements (user_id, source, status, grants, expires_at, note)
    values (v_pro, 'GRANT', 'ACTIVE', array['LOCAL_PRO', 'CLOUD', 'AI_PRO', 'SHARED'], now() + interval '30 days', 'suite: founder');
  insert into public.entitlements (user_id, source, status, grants, product_id, expires_at, purchase_token_hash)
    values (v_life, 'PLAY_LIFETIME', 'ACTIVE', array['LOCAL_PRO'], 'lucento_pro_lifetime', null, repeat('a', 64));
  insert into public.entitlements (user_id, source, status, grants, product_id, expires_at, purchase_token_hash)
    values (v_life, 'PLAY_LIFETIME_SERVICES', 'EXPIRED', array['CLOUD', 'AI_PRO', 'SHARED'], 'lucento_pro_lifetime', now() - interval '1 day', repeat('a', 64));

  -- ======================================================================== reading
  perform pg_temp.as_user(v_free, 'free@lucento-test.invalid');
  select count(*) into v_n from public.entitlements;
  execute 'reset role';
  r := r || (case when v_n = 0 then 'ok  R1 a free account reads no entitlement rows' else 'FAIL R1 saw ' || v_n end);

  perform pg_temp.as_user(v_pro, 'pro@lucento-test.invalid');
  select count(*) into v_n from public.entitlements;
  execute 'reset role';
  r := r || (case when v_n = 1 then 'ok  R2 a Pro account reads exactly its own row' else 'FAIL R2 saw ' || v_n end);

  perform pg_temp.as_user(v_life, 'lifetime@lucento-test.invalid');
  select count(*) into v_n from public.entitlements where user_id <> v_life;
  execute 'reset role';
  r := r || (case when v_n = 0 then 'ok  R3 nobody reads another account''s rows' else 'FAIL R3 saw ' || v_n end);

  set role anon;
  begin
    select count(*) into v_n from public.entitlements;
    r := r || (case when v_n = 0 then 'ok  R4 anon reads nothing' else 'FAIL R4 anon saw ' || v_n end);
  exception when insufficient_privilege then r := r || 'ok  R4 anon has no access'::text; end;
  execute 'reset role';

  -- ======================================================================== writing (never from a client)
  perform pg_temp.as_user(v_free, 'free@lucento-test.invalid');
  begin
    insert into public.entitlements (user_id, source, status, grants, expires_at)
      values (v_free, 'GRANT', 'ACTIVE', array['LOCAL_PRO'], now() + interval '1 day');
    r := r || 'FAIL W1 a client granted itself Pro'::text;
  exception when insufficient_privilege then r := r || 'ok  W1 a client cannot insert an entitlement'::text; end;
  execute 'reset role';

  perform pg_temp.as_user(v_pro, 'pro@lucento-test.invalid');
  begin
    update public.entitlements set expires_at = now() + interval '10 years' where user_id = v_pro;
    r := r || 'FAIL W2 a client extended its own entitlement'::text;
  exception when insufficient_privilege then r := r || 'ok  W2 a client cannot update its entitlement'::text; end;
  begin
    delete from public.entitlements where user_id = v_pro;
    r := r || 'FAIL W3 a client deleted its entitlement'::text;
  exception when insufficient_privilege then r := r || 'ok  W3 a client cannot delete its entitlement'::text; end;
  begin
    select count(*) into v_n from public.purchase_links;
    r := r || 'FAIL W4 a client read purchase_links'::text;
  exception when insufficient_privilege then r := r || 'ok  W4 purchase_links are invisible to clients'::text; end;
  begin
    select count(*) into v_n from lucento_private.plan_limits;
    r := r || 'FAIL W5 a client read plan_limits'::text;
  exception when insufficient_privilege then r := r || 'ok  W5 plan_limits are invisible to clients'::text; end;
  begin
    select lucento_private.has_capability(v_pro, 'SHARED') into v_bool;
    r := r || 'FAIL W6 a client called has_capability directly'::text;
  exception when insufficient_privilege then r := r || 'ok  W6 has_capability is not callable by clients'::text; end;
  execute 'reset role';

  -- ======================================================================== constraints (as the server)
  begin
    insert into public.entitlements (user_id, source, status, grants, expires_at)
      values (v_pro, 'GRANT', 'ACTIVE', array['LOCAL_PRO'], now() + interval '1 day');
    r := r || 'FAIL C1 a second GRANT for the same account was accepted'::text;
  exception when unique_violation then r := r || 'ok  C1 one row per (account, source, product)'::text; end;
  begin
    insert into public.entitlements (user_id, source, status, grants, expires_at)
      values (v_x, 'GRANT', 'ACTIVE', array['LOCAL_PRO'], null);
    r := r || 'FAIL C2 a GRANT without an expiry was accepted'::text;
  exception when check_violation then r := r || 'ok  C2 a GRANT must expire'::text; end;
  begin
    insert into public.entitlements (user_id, source, status, grants, product_id, expires_at)
      values (v_x, 'PLAY_LIFETIME', 'ACTIVE', array['LOCAL_PRO'], 'lucento_pro_lifetime', now() + interval '1 year');
    r := r || 'FAIL C3 a Lifetime with an expiry was accepted'::text;
  exception when check_violation then r := r || 'ok  C3 a Lifetime never expires'::text; end;
  begin
    insert into public.entitlements (user_id, source, status, grants, expires_at)
      values (v_x, 'GRANT', 'ACTIVE', array['EVERYTHING'], now() + interval '1 day');
    r := r || 'FAIL C4 an unknown capability was accepted'::text;
  exception when check_violation then r := r || 'ok  C4 unknown capabilities are refused'::text; end;
  begin
    insert into public.entitlements (user_id, source, status, grants, expires_at)
      values (v_x, 'GRANT', 'SORT_OF', array['LOCAL_PRO'], now() + interval '1 day');
    r := r || 'FAIL C5 an unknown status was accepted'::text;
  exception when check_violation then r := r || 'ok  C5 unknown statuses are refused'::text; end;

  -- ======================================================================== has_capability and max_partners
  select lucento_private.max_partners(v_free) into v_i;
  r := r || (case when v_i = 0 then 'ok  P1 a free account may have 0 Partners' else 'FAIL P1 ' || v_i end);
  select lucento_private.max_partners(v_pro) into v_i;
  r := r || (case when v_i = 1 then 'ok  P2 a Pro account may have 1 Partner' else 'FAIL P2 ' || v_i end);
  select lucento_private.max_partners(v_life) into v_i;
  r := r || (case when v_i = 0 then 'ok  P3 a Lifetime owner past the included year may have 0 Partners (LOCAL_PRO is not SHARED)' else 'FAIL P3 ' || v_i end);
  select lucento_private.has_capability(v_life, 'LOCAL_PRO') into v_bool;
  r := r || (case when v_bool then 'ok  P4 the same owner still holds LOCAL_PRO, forever' else 'FAIL P4' end);

  update public.entitlements set status = 'ACTIVE', expires_at = now() + interval '200 days' where user_id = v_life and source = 'PLAY_LIFETIME_SERVICES';
  select lucento_private.max_partners(v_life) into v_i;
  r := r || (case when v_i = 1 then 'ok  P5 with the included services active the owner may invite' else 'FAIL P5 ' || v_i end);
  select count(*) into v_n from public.entitlements where user_id = v_life and source = 'PLAY_LIFETIME_SERVICES' and updated_at >= verified_at;
  r := r || (case when v_n = 1 then 'ok  P6 updated_at follows every update' else 'FAIL P6' end);

  update public.entitlements set status = 'REVOKED' where user_id = v_pro;
  select lucento_private.max_partners(v_pro) into v_i;
  r := r || (case when v_i = 0 then 'ok  P7 a REVOKED row grants nothing' else 'FAIL P7 ' || v_i end);
  update public.entitlements set status = 'GRACE' where user_id = v_pro;
  select lucento_private.max_partners(v_pro) into v_i;
  r := r || (case when v_i = 1 then 'ok  P8 GRACE keeps access' else 'FAIL P8 ' || v_i end);
  update public.entitlements set status = 'ON_HOLD' where user_id = v_pro;
  select lucento_private.max_partners(v_pro) into v_i;
  r := r || (case when v_i = 0 then 'ok  P9 ON_HOLD grants nothing' else 'FAIL P9 ' || v_i end);
  update public.entitlements set status = 'ACTIVE', expires_at = now() - interval '1 minute' where user_id = v_pro;
  select lucento_private.max_partners(v_pro) into v_i;
  r := r || (case when v_i = 0 then 'ok  P10 an expired row grants nothing (no grace on the server)' else 'FAIL P10 ' || v_i end);
  update public.entitlements set expires_at = now() + interval '30 days' where user_id = v_pro;

  update lucento_private.plan_limits set value_int = 2 where key = 'max_partners_pro';
  select lucento_private.max_partners(v_pro) into v_i;
  r := r || (case when v_i = 2 then 'ok  P11 the Partner capacity is remote configuration' else 'FAIL P11 ' || v_i end);
  update lucento_private.plan_limits set value_int = 1 where key = 'max_partners_pro';

  -- ======================================================================== the Shared RPCs follow the plan
  perform pg_temp.as_user(v_free, 'free@lucento-test.invalid');
  perform public.create_shared_space('Free');
  begin
    perform public.create_space_invite('stranger@lucento-test.invalid');
    r := r || 'FAIL S1 a free Owner invited a Partner'::text;
  exception when others then
    r := r || (case when sqlerrm like '%space_full%' then 'ok  S1 a free Owner cannot invite (space_full)' else 'FAIL S1 ' || sqlerrm end);
  end;
  execute 'reset role';
  perform pg_temp.as_user(v_pro, 'pro@lucento-test.invalid');
  perform public.create_shared_space('Pro');
  begin
    perform public.create_space_invite('stranger@lucento-test.invalid');
    r := r || 'ok  S2 a Pro Owner invites a Partner'::text;
  exception when others then r := r || ('FAIL S2 ' || sqlerrm); end;
  execute 'reset role';

  -- ======================================================================== AI allowance
  perform pg_temp.as_user(v_free, 'free@lucento-test.invalid');
  for v_i in 1..10 loop
    select public.consume_ai_request() into v_j;
  end loop;
  r := r || (case when (v_j ->> 'allowed')::boolean and (v_j ->> 'remaining')::int = 0 and v_j ->> 'plan' = 'FREE'
             then 'ok  A1 free: the 10th question of the month is allowed, 0 remaining' else 'FAIL A1 ' || v_j::text end);
  select public.consume_ai_request() into v_j;
  r := r || (case when not (v_j ->> 'allowed')::boolean and v_j ->> 'limited_by' = 'month'
             then 'ok  A2 free: the 11th is refused by the month' else 'FAIL A2 ' || v_j::text end);
  execute 'reset role';

  perform pg_temp.as_user(v_life, 'lifetime@lucento-test.invalid');
  select public.consume_ai_request() into v_j;
  r := r || (case when v_j ->> 'plan' = 'PRO' and (v_j ->> 'daily_limit')::int = 50 and (v_j ->> 'monthly_limit')::int = 400
             then 'ok  A3 a Lifetime owner with services active has the Pro allowance' else 'FAIL A3 ' || v_j::text end);
  execute 'reset role';
  update public.entitlements set status = 'EXPIRED', expires_at = now() - interval '1 day' where user_id = v_life and source = 'PLAY_LIFETIME_SERVICES';
  perform pg_temp.as_user(v_life, 'lifetime@lucento-test.invalid');
  select public.consume_ai_request() into v_j;
  r := r || (case when v_j ->> 'plan' = 'FREE' and (v_j ->> 'monthly_limit')::int = 10
             then 'ok  A4 after the included year the same owner has the free allowance' else 'FAIL A4 ' || v_j::text end);
  execute 'reset role';

  -- Pro: the daily limit, then the monthly ceiling (lowered through plan_limits to keep the loop short).
  perform pg_temp.as_user(v_pro, 'pro@lucento-test.invalid');
  for v_i in 1..50 loop
    select public.consume_ai_request() into v_j;
  end loop;
  r := r || (case when (v_j ->> 'allowed')::boolean and (v_j ->> 'remaining')::int = 0
             then 'ok  A5 pro: the 50th question of the day is allowed' else 'FAIL A5 ' || v_j::text end);
  select public.consume_ai_request() into v_j;
  r := r || (case when not (v_j ->> 'allowed')::boolean and v_j ->> 'limited_by' = 'day'
             then 'ok  A6 pro: the 51st is refused by the day' else 'FAIL A6 ' || v_j::text end);
  execute 'reset role';
  update lucento_private.plan_limits set value_int = 60 where key = 'ai_pro_monthly_ceiling';
  update public.ai_usage set day = day - 1 where user_id = v_pro;   -- yesterday's 51 count toward the month, not today
  perform pg_temp.as_user(v_pro, 'pro@lucento-test.invalid');
  for v_i in 1..9 loop
    select public.consume_ai_request() into v_j;
  end loop;
  r := r || (case when (v_j ->> 'allowed')::boolean and (v_j ->> 'used_month')::int = 60
             then 'ok  A7 pro: the month''s 60th is allowed under a 60 ceiling' else 'FAIL A7 ' || v_j::text end);
  select public.consume_ai_request() into v_j;
  r := r || (case when not (v_j ->> 'allowed')::boolean and v_j ->> 'limited_by' = 'month'
             then 'ok  A8 pro: the month''s 61st is refused by the ceiling' else 'FAIL A8 ' || v_j::text end);
  execute 'reset role';
  update lucento_private.plan_limits set value_int = 400 where key = 'ai_pro_monthly_ceiling';

  set role anon;
  begin
    select public.consume_ai_request() into v_j;
    r := r || 'FAIL A9 anon consumed AI quota'::text;
  exception when insufficient_privilege or sqlstate '28000' then r := r || 'ok  A9 anon cannot call consume_ai_request'::text; end;
  execute 'reset role';

  -- The old counter still works for the current proxy.
  perform pg_temp.as_user(v_x, 'stranger@lucento-test.invalid');
  select public.consume_ai_quota() into v_i;
  execute 'reset role';
  r := r || (case when v_i = 1 then 'ok  A10 consume_ai_quota is unchanged' else 'FAIL A10 ' || v_i end);

  raise exception E'VERIFY %/% failed\n%',
    (select count(*) from unnest(r) x where x like 'FAIL%'), array_length(r, 1), array_to_string(r, E'\n');
end
$verify$;
