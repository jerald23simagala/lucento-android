-- Lucento Pro, Phase 4: Google Play purchases on the server (docs/monetization/SERVER_CONTRACT.md §3–§5). Run with
-- tools/sql-verify (see README.md):
--   node run.mjs ../../supabase/migrations play_purchases_suite.sql
-- Acts as the service role (the Vercel endpoints), signed-in accounts and anon, and checks who may write purchases, the
-- §5.4 linking rule, the Lifetime year, subscriptions over time, refunds, and that a stale notification never undoes a
-- newer purchase. Raises one exception listing every check; the runner reads "VERIFY 0/N failed" as a pass.
do $verify$
declare
  r text[] := '{}';
  v_a uuid := gen_random_uuid(); v_b uuid := gen_random_uuid(); v_c uuid := gen_random_uuid();
  h_life text := encode(sha256(convert_to('token-lifetime', 'UTF8')), 'hex');
  h_sub1 text := encode(sha256(convert_to('token-sub-1', 'UTF8')), 'hex');
  h_sub2 text := encode(sha256(convert_to('token-sub-2', 'UTF8')), 'hex');
  h_old text := encode(sha256(convert_to('token-lifetime-old', 'UTF8')), 'hex');
  obf_b text; obf_c text;
  v_j jsonb; v_n integer; v_s text; v_t timestamptz; v_bool boolean;
begin
  create function pg_temp.as_user(p_uid uuid) returns void language plpgsql as $f$
  begin
    perform set_config('request.jwt.claims', json_build_object('sub', p_uid, 'role', 'authenticated')::text, true);
    perform set_config('role', 'authenticated', true);
  end $f$;

  insert into auth.users (id, email, email_confirmed_at, aud, role) values
    (v_a, 'a@lucento-test.invalid', now(), 'authenticated', 'authenticated'),
    (v_b, 'b@lucento-test.invalid', now(), 'authenticated', 'authenticated'),
    (v_c, 'c@lucento-test.invalid', now(), 'authenticated', 'authenticated');
  obf_b := encode(sha256(convert_to(v_b::text, 'UTF8')), 'hex');
  obf_c := encode(sha256(convert_to(v_c::text, 'UTF8')), 'hex');

  -- ======================================================================== who may write
  perform pg_temp.as_user(v_a);
  begin
    perform public.apply_play_purchase(v_a, h_life, 'GPA.1', 'lucento_pro_lifetime', null, 'ACTIVE', null, now(), null);
    r := r || 'FAIL P1 a signed-in client granted itself Lifetime'::text;
  exception when insufficient_privilege then r := r || 'ok  P1 a client cannot call apply_play_purchase'::text; end;
  begin
    perform public.revoke_play_purchase(h_life);
    r := r || 'FAIL P2 a client called revoke_play_purchase'::text;
  exception when insufficient_privilege then r := r || 'ok  P2 a client cannot call revoke_play_purchase'::text; end;
  begin
    perform public.lucento_write_play_row(v_a, 'PLAY_LIFETIME', 'ACTIVE', array['LOCAL_PRO'], 'lucento_pro_lifetime', null, h_life, now(), null);
    r := r || 'FAIL P3 a client wrote a row through the helper'::text;
  exception when insufficient_privilege then r := r || 'ok  P3 a client cannot call the row helper'::text; end;
  execute 'reset role';
  set role anon;
  begin
    perform public.apply_play_purchase(v_a, h_life, 'GPA.1', 'lucento_pro_lifetime', null, 'ACTIVE', null, now(), null);
    r := r || 'FAIL P4 anon granted Lifetime'::text;
  exception when insufficient_privilege then r := r || 'ok  P4 anon cannot call apply_play_purchase'::text; end;
  execute 'reset role';

  -- ======================================================================== Lifetime, first verification
  set role service_role;
  select public.apply_play_purchase(v_a, h_life, 'GPA.life', 'lucento_pro_lifetime', null, 'ACTIVE', null, now() - interval '1 day', null) into v_j;
  execute 'reset role';
  r := r || (case when v_j ->> 'result' = 'ok' then 'ok  L1 the service role records a Lifetime purchase' else 'FAIL L1 ' || v_j::text end);
  select count(*) into v_n from public.entitlements where user_id = v_a and purchase_token_hash = h_life;
  select expires_at into v_t from public.entitlements where user_id = v_a and source = 'PLAY_LIFETIME';
  r := r || (case when v_n = 2 and v_t is null then 'ok  L2 Lifetime writes LOCAL_PRO forever and the services year' else 'FAIL L2 rows ' || v_n end);
  select expires_at into v_t from public.entitlements where user_id = v_a and source = 'PLAY_LIFETIME_SERVICES';
  r := r || (case when abs(extract(epoch from (v_t - (now() - interval '1 day' + interval '365 days')))) < 5
             then 'ok  L3 the services year ends 365 days after the purchase, not the verification' else 'FAIL L3 ' || v_t end);
  r := r || (case when lucento_private.has_capability(v_a, 'LOCAL_PRO') and lucento_private.has_capability(v_a, 'CLOUD')
             then 'ok  L4 the account now holds LOCAL_PRO and CLOUD' else 'FAIL L4' end);
  select user_id into v_s from public.purchase_links where purchase_token_hash = h_life;
  r := r || (case when v_s = v_a::text then 'ok  L5 the purchase is linked to the account that verified it' else 'FAIL L5 ' || coalesce(v_s, 'null') end);

  perform pg_temp.as_user(v_a);
  select count(*) into v_n from public.entitlements;
  execute 'reset role';
  r := r || (case when v_n = 2 then 'ok  L6 the account reads its two rows' else 'FAIL L6 saw ' || v_n end);

  set role service_role;
  select public.apply_play_purchase(v_a, h_life, 'GPA.life', 'lucento_pro_lifetime', null, 'ACTIVE', null, now() - interval '1 day', null) into v_j;
  execute 'reset role';
  select count(*) into v_n from public.entitlements where user_id = v_a;
  r := r || (case when v_j ->> 'result' = 'ok' and v_n = 2 then 'ok  L7 verifying again is idempotent' else 'FAIL L7 ' || v_n end);

  -- An old purchase: owned, but the included year is over.
  set role service_role;
  perform public.apply_play_purchase(v_c, h_old, 'GPA.old', 'lucento_pro_lifetime', obf_c, 'ACTIVE', null, now() - interval '400 days', null);
  execute 'reset role';
  select status into v_s from public.entitlements where user_id = v_c and source = 'PLAY_LIFETIME_SERVICES';
  r := r || (case when v_s = 'EXPIRED' and lucento_private.has_capability(v_c, 'LOCAL_PRO') and not lucento_private.has_capability(v_c, 'CLOUD')
             then 'ok  L8 after 365 days Lifetime keeps LOCAL_PRO and its services have ended' else 'FAIL L8 ' || coalesce(v_s, 'null') end);
  delete from public.entitlements where user_id = v_c;
  delete from public.purchase_links where purchase_token_hash = h_old;

  -- ======================================================================== the linking rule (§5.4)
  set role service_role;
  select public.apply_play_purchase(v_b, h_life, 'GPA.life', 'lucento_pro_lifetime', null, 'ACTIVE', null, now() - interval '1 day', null) into v_j;
  execute 'reset role';
  select count(*) into v_n from public.entitlements where user_id = v_b;
  r := r || (case when v_j ->> 'result' = 'linked_elsewhere' and v_n = 0
             then 'ok  K1 another account submitting the same purchase is refused and gets nothing' else 'FAIL K1 ' || v_j::text end);

  set role service_role;
  select public.apply_play_purchase(v_b, h_life, 'GPA.life', 'lucento_pro_lifetime', obf_b, 'ACTIVE', null, now() - interval '1 day', null) into v_j;
  execute 'reset role';
  r := r || (case when v_j ->> 'result' = 'linked_elsewhere'
             then 'ok  K2 even bought for that account, a link younger than 30 days does not move' else 'FAIL K2 ' || v_j::text end);

  update public.purchase_links set linked_at = now() - interval '31 days' where purchase_token_hash = h_life;
  set role service_role;
  select public.apply_play_purchase(v_b, h_life, 'GPA.life', 'lucento_pro_lifetime', obf_b, 'ACTIVE', null, now() - interval '1 day', null) into v_j;
  execute 'reset role';
  select count(*) into v_n from public.entitlements where user_id = v_a;
  r := r || (case when v_j ->> 'result' = 'ok' and v_n = 0 and lucento_private.has_capability(v_b, 'LOCAL_PRO')
             then 'ok  K3 bought for B and linked over 30 days ago: it moves to B and leaves A' else 'FAIL K3 ' || v_j::text || ' A rows ' || v_n end);
  select relinked_count into v_n from public.purchase_links where purchase_token_hash = h_life;
  r := r || (case when v_n = 1 then 'ok  K4 the move is counted' else 'FAIL K4 ' || v_n end);

  update public.purchase_links set linked_at = now() - interval '31 days', relinked_count = 2 where purchase_token_hash = h_life;
  set role service_role;
  select public.apply_play_purchase(v_c, h_life, 'GPA.life', 'lucento_pro_lifetime', obf_c, 'ACTIVE', null, now() - interval '1 day', null) into v_j;
  execute 'reset role';
  r := r || (case when v_j ->> 'result' = 'review_required' and not lucento_private.has_capability(v_c, 'LOCAL_PRO')
             then 'ok  K5 a third move needs support' else 'FAIL K5 ' || v_j::text end);
  update public.purchase_links set relinked_count = 1, linked_at = now() where purchase_token_hash = h_life;

  -- ======================================================================== subscriptions over time (rtdn.ts re-verifies)
  set role service_role;
  select public.apply_play_purchase(null, h_sub1, 'GPA.sub1', 'lucento_pro', obf_c, 'ACTIVE', 'monthly', now(), now() + interval '30 days') into v_j;
  execute 'reset role';
  r := r || (case when v_j ->> 'result' = 'unlinked' and not exists (select 1 from public.entitlements where purchase_token_hash = h_sub1)
             then 'ok  S1 a notification for a purchase no account verified writes nothing' else 'FAIL S1 ' || v_j::text end);

  set role service_role;
  perform public.apply_play_purchase(v_c, h_sub1, 'GPA.sub1', 'lucento_pro', obf_c, 'TRIAL', 'annual', now(), now() + interval '7 days');
  execute 'reset role';
  r := r || (case when lucento_private.has_capability(v_c, 'CLOUD') and lucento_private.has_capability(v_c, 'SHARED')
             then 'ok  S2 a trial grants everything' else 'FAIL S2' end);

  set role service_role;
  select public.apply_play_purchase(null, h_sub1, null, 'lucento_pro', null, 'GRACE', 'annual', now(), now() + interval '3 days') into v_j;
  execute 'reset role';
  select status into v_s from public.entitlements where user_id = v_c and source = 'PLAY_SUBSCRIPTION';
  r := r || (case when v_j ->> 'result' = 'ok' and v_s = 'GRACE' and lucento_private.has_capability(v_c, 'CLOUD')
             then 'ok  S3 a notification updates the linked account: grace keeps access' else 'FAIL S3 ' || coalesce(v_s, 'null') end);

  set role service_role;
  perform public.apply_play_purchase(null, h_sub1, null, 'lucento_pro', null, 'ON_HOLD', 'annual', now(), now() - interval '1 day');
  execute 'reset role';
  r := r || (case when not lucento_private.has_capability(v_c, 'CLOUD') then 'ok  S4 on hold, access pauses' else 'FAIL S4' end);

  set role service_role;
  perform public.apply_play_purchase(null, h_sub1, null, 'lucento_pro', null, 'EXPIRED', 'annual', now(), now() - interval '1 day');
  perform public.apply_play_purchase(v_c, h_sub2, 'GPA.sub2', 'lucento_pro', obf_c, 'ACTIVE', 'monthly', now(), now() + interval '30 days');
  -- A late notification about the first, ended subscription arrives after the second began.
  perform public.apply_play_purchase(null, h_sub1, null, 'lucento_pro', null, 'EXPIRED', 'annual', now(), now() - interval '1 day');
  execute 'reset role';
  select status into v_s from public.entitlements where user_id = v_c and source = 'PLAY_SUBSCRIPTION';
  select (purchase_token_hash = h_sub2) into v_bool from public.entitlements where user_id = v_c and source = 'PLAY_SUBSCRIPTION';
  r := r || (case when v_s = 'ACTIVE' and v_bool and lucento_private.has_capability(v_c, 'CLOUD')
             then 'ok  S5 a resubscription replaces the row; a stale ending never overwrites it' else 'FAIL S5 ' || coalesce(v_s, 'null') end);

  -- ======================================================================== refunds (rtdn voided, voided.ts)
  set role service_role;
  select public.revoke_play_purchase(h_life) into v_n;
  execute 'reset role';
  r := r || (case when v_n = 2 and not lucento_private.has_capability(v_b, 'LOCAL_PRO')
             then 'ok  V1 a refund ends both Lifetime rows on the account that holds it' else 'FAIL V1 ' || v_n end);
  set role service_role;
  select public.revoke_play_purchase(h_life) into v_n;
  select public.apply_play_purchase(v_b, h_life, 'GPA.life', 'lucento_pro_lifetime', obf_b, 'ACTIVE', null, now() - interval '1 day', null) into v_j;
  execute 'reset role';
  select count(*) into v_n from public.entitlements where purchase_token_hash = h_life and status <> 'REVOKED';
  r := r || (case when v_j ->> 'status' = 'REVOKED' and v_n = 0 and not lucento_private.has_capability(v_b, 'LOCAL_PRO')
             then 'ok  V2 a refunded purchase stays refunded when verified again' else 'FAIL V2 ' || v_j::text end);

  -- ======================================================================== pending (cash payments) and bad input
  set role service_role;
  perform public.apply_play_purchase(v_a, h_old, 'GPA.cash', 'lucento_pro_lifetime', null, 'PENDING', null, now(), null);
  execute 'reset role';
  select count(*) into v_n from public.entitlements where user_id = v_a and status = 'PENDING';
  r := r || (case when v_n = 2 and not lucento_private.has_capability(v_a, 'LOCAL_PRO')
             then 'ok  N1 a pending payment writes PENDING rows that grant nothing' else 'FAIL N1 ' || v_n end);
  set role service_role;
  perform public.apply_play_purchase(null, h_old, null, 'lucento_pro_lifetime', null, 'ACTIVE', null, now(), null);
  execute 'reset role';
  r := r || (case when lucento_private.has_capability(v_a, 'LOCAL_PRO') then 'ok  N2 Play confirming the payment grants it' else 'FAIL N2' end);

  set role service_role;
  begin
    perform public.apply_play_purchase(v_a, 'not-a-hash', null, 'lucento_pro', null, 'ACTIVE', null, now(), now() + interval '1 day');
    r := r || 'FAIL N3 a malformed token hash was accepted'::text;
  exception when sqlstate '22023' then r := r || 'ok  N3 a malformed token hash is refused'::text; end;
  begin
    perform public.apply_play_purchase(v_a, h_sub1, null, 'someone_elses_sku', null, 'ACTIVE', null, now(), now() + interval '1 day');
    r := r || 'FAIL N4 an unknown product was accepted'::text;
  exception when sqlstate '22023' then r := r || 'ok  N4 an unknown product is refused'::text; end;
  begin
    perform public.apply_play_purchase(v_a, h_sub1, null, 'lucento_pro', null, 'FOREVER', null, now(), now() + interval '1 day');
    r := r || 'FAIL N5 an unknown status was accepted'::text;
  exception when sqlstate '22023' then r := r || 'ok  N5 an unknown status is refused'::text; end;
  begin
    perform public.apply_play_purchase(v_a, h_sub1, null, 'lucento_pro', null, 'ACTIVE', 'monthly', now(), null);
    r := r || 'FAIL N6 an active subscription without a period end was accepted'::text;
  exception when sqlstate '22023' then r := r || 'ok  N6 an active subscription needs its period end'::text; end;
  execute 'reset role';

  raise exception E'VERIFY %/% failed\n%',
    (select count(*) from unnest(r) x where x like 'FAIL%'), array_length(r, 1), array_to_string(r, E'\n');
end
$verify$;
