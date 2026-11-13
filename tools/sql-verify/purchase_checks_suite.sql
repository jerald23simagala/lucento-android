-- Lucento Pro: the purchase check that needs no account (20261113120000_purchase_checks.sql; SERVER_CONTRACT.md §3b).
-- Run with tools/sql-verify (see README.md):
--   node run.mjs ../../supabase/migrations purchase_checks_suite.sql
-- Acts as the service role (the Vercel endpoint), a signed-in account and anon, and checks who may read or record a
-- check, what an unknown purchase answers, and that a refund always answers REVOKED, with or without an account.
do $verify$
declare
  r text[] := '{}';
  v_a uuid := gen_random_uuid();
  h_life text := encode(sha256(convert_to('token-check-lifetime', 'UTF8')), 'hex');
  h_sub text := encode(sha256(convert_to('token-check-sub', 'UTF8')), 'hex');
  h_unlinked text := encode(sha256(convert_to('token-check-unlinked', 'UTF8')), 'hex');
  h_never text := encode(sha256(convert_to('token-check-never', 'UTF8')), 'hex');
  v_j jsonb; v_s text; v_n integer;
begin
  create function pg_temp.as_user(p_uid uuid) returns void language plpgsql as $f$
  begin
    perform set_config('request.jwt.claims', json_build_object('sub', p_uid, 'role', 'authenticated')::text, true);
    perform set_config('role', 'authenticated', true);
  end $f$;

  insert into auth.users (id, email, email_confirmed_at, aud, role) values
    (v_a, 'a@lucento-test.invalid', now(), 'authenticated', 'authenticated');

  -- ======================================================================== who may call
  perform pg_temp.as_user(v_a);
  begin
    perform public.purchase_check_status(h_life);
    r := r || 'FAIL K1 a signed-in client read a purchase check'::text;
  exception when insufficient_privilege then r := r || 'ok  K1 a client cannot call purchase_check_status'::text; end;
  begin
    perform public.record_purchase_check(h_life, 'lucento_pro_lifetime', 'ACTIVE');
    r := r || 'FAIL K2 a signed-in client recorded a purchase check'::text;
  exception when insufficient_privilege then r := r || 'ok  K2 a client cannot call record_purchase_check'::text; end;
  begin
    select count(*) into v_n from lucento_private.purchase_checks;
    r := r || 'FAIL K3 a signed-in client read the purchase_checks table'::text;
  exception when insufficient_privilege then r := r || 'ok  K3 a client cannot read the table'::text; end;
  execute 'reset role';
  set role anon;
  begin
    perform public.record_purchase_check(h_life, 'lucento_pro_lifetime', 'ACTIVE');
    r := r || 'FAIL K4 anon recorded a purchase check'::text;
  exception when insufficient_privilege then r := r || 'ok  K4 anon cannot call record_purchase_check'::text; end;
  begin
    perform public.purchase_check_status(h_life);
    r := r || 'FAIL K5 anon read a purchase check'::text;
  exception when insufficient_privilege then r := r || 'ok  K5 anon cannot call purchase_check_status'::text; end;
  execute 'reset role';

  -- ======================================================================== the service role
  set role service_role;
  select public.purchase_check_status(h_never) into v_j;
  r := r || (case when v_j ->> 'status' is null and v_j ->> 'checked_at' is null and (v_j ->> 'revoked')::boolean = false
    then 'ok  K6 a purchase never asked about answers no status' else 'FAIL K6 ' || v_j::text end);

  select public.record_purchase_check(h_life, 'lucento_pro_lifetime', 'ACTIVE') into v_s;
  select public.purchase_check_status(h_life) into v_j;
  r := r || (case when v_s = 'ACTIVE' and v_j ->> 'status' = 'ACTIVE' and v_j ->> 'checked_at' is not null and (v_j ->> 'revoked')::boolean = false
    then 'ok  K7 a reading is kept and read back' else 'FAIL K7 ' || coalesce(v_s, 'null') || ' ' || v_j::text end);

  select public.record_purchase_check(h_life, 'lucento_pro_lifetime', 'EXPIRED') into v_s;
  execute 'reset role';   -- the table itself is out of reach even for the service role; only the functions read it
  select count(*) into v_n from lucento_private.purchase_checks where purchase_token_hash = h_life;
  set role service_role;
  r := r || (case when v_s = 'EXPIRED' and v_n = 1
    then 'ok  K8 a newer reading replaces the last one (one row per purchase)' else 'FAIL K8 ' || coalesce(v_s, 'null') || ' rows ' || v_n end);

  -- A refund recorded by a notification or the daily check (F-02) answers REVOKED, whatever Google says afterwards.
  perform public.record_purchase_check(h_sub, 'lucento_pro', 'ACTIVE');
  perform public.revoke_play_purchase(h_sub);
  select public.record_purchase_check(h_sub, 'lucento_pro', 'ACTIVE') into v_s;
  select public.purchase_check_status(h_sub) into v_j;
  r := r || (case when v_s = 'REVOKED' and v_j ->> 'status' = 'REVOKED' and (v_j ->> 'revoked')::boolean
    then 'ok  K9 a refunded purchase answers REVOKED even when Google''s reading still says ACTIVE' else 'FAIL K9 ' || coalesce(v_s, 'null') || ' ' || v_j::text end);

  -- No account ever recorded this purchase: the refund still counts.
  perform public.revoke_play_purchase(h_unlinked);
  select public.purchase_check_status(h_unlinked) into v_j;
  r := r || (case when (v_j ->> 'revoked')::boolean and v_j ->> 'status' is null
    then 'ok  K10 a refund with no account behind it is known before Google is asked' else 'FAIL K10 ' || v_j::text end);

  begin
    perform public.record_purchase_check('not-a-hash', 'lucento_pro', 'ACTIVE');
    r := r || 'FAIL K11 a malformed token hash was accepted'::text;
  exception when sqlstate '22023' then r := r || 'ok  K11 a malformed token hash is refused'::text; end;
  begin
    perform public.record_purchase_check(h_never, 'someone_elses_sku', 'ACTIVE');
    r := r || 'FAIL K12 an unknown product was accepted'::text;
  exception when sqlstate '22023' then r := r || 'ok  K12 an unknown product is refused'::text; end;
  begin
    perform public.record_purchase_check(h_never, 'lucento_pro', 'FOREVER');
    r := r || 'FAIL K13 an unknown status was accepted'::text;
  exception when sqlstate '22023' then r := r || 'ok  K13 an unknown status is refused'::text; end;
  begin
    perform public.purchase_check_status('NOT-A-HASH');
    r := r || 'FAIL K14 a malformed token hash was read'::text;
  exception when sqlstate '22023' then r := r || 'ok  K14 reading a malformed token hash is refused'::text; end;
  execute 'reset role';

  raise exception E'VERIFY %/% failed\n%',
    (select count(*) from unnest(r) x where x like 'FAIL%'), array_length(r, 1), array_to_string(r, E'\n');
end
$verify$;
