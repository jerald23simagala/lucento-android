-- Account emails (supabase/migrations/20261110090000_account_emails.sql; docs/EMAILS.md). Run with tools/sql-verify:
--   node run.mjs ../../supabase/migrations account_emails_suite.sql
-- Checks that only the service role claims emails, that each email is claimed once per account and reference, that a
-- released claim can be taken again, and that deleting the account removes its rows.
do $verify$
declare
  r text[] := '{}';
  v_a uuid := gen_random_uuid(); v_b uuid := gen_random_uuid();
  v_bool boolean; v_n integer;
begin
  create function pg_temp.as_user(p_uid uuid) returns void language plpgsql as $f$
  begin
    perform set_config('request.jwt.claims', json_build_object('sub', p_uid, 'role', 'authenticated')::text, true);
    perform set_config('role', 'authenticated', true);
  end $f$;

  insert into auth.users (id, email, email_confirmed_at, aud, role) values
    (v_a, 'a@lucento-test.invalid', now(), 'authenticated', 'authenticated'),
    (v_b, 'b@lucento-test.invalid', now(), 'authenticated', 'authenticated');

  -- ======================================================================== who may claim
  perform pg_temp.as_user(v_a);
  begin
    perform public.claim_account_email(v_a, 'welcome', '');
    r := r || 'FAIL E1 a signed-in client claimed an email'::text;
  exception when insufficient_privilege then r := r || 'ok  E1 a client cannot call claim_account_email'::text; end;
  begin
    perform public.release_account_email(v_a, 'welcome', '');
    r := r || 'FAIL E2 a signed-in client released an email'::text;
  exception when insufficient_privilege then r := r || 'ok  E2 a client cannot call release_account_email'::text; end;
  begin
    perform 1 from public.account_emails;
    r := r || 'FAIL E3 a client read account_emails'::text;
  exception when insufficient_privilege then r := r || 'ok  E3 a client cannot read account_emails'::text; end;
  execute 'reset role';
  set role anon;
  begin
    perform public.claim_account_email(v_a, 'welcome', '');
    r := r || 'FAIL E4 anon claimed an email'::text;
  exception when insufficient_privilege then r := r || 'ok  E4 anon cannot call claim_account_email'::text; end;
  execute 'reset role';

  -- ======================================================================== once per account and reference
  set role service_role;
  v_bool := public.claim_account_email(v_a, 'welcome', null);
  r := r || (case when v_bool then 'ok  E5 the first welcome claim succeeds' else 'FAIL E5' end);
  v_bool := public.claim_account_email(v_a, 'welcome', '');
  r := r || (case when not v_bool then 'ok  E6 a second welcome claim is refused (null and empty ref are the same)' else 'FAIL E6' end);
  v_bool := public.claim_account_email(v_b, 'welcome', '');
  r := r || (case when v_bool then 'ok  E7 another account gets its own welcome' else 'FAIL E7' end);
  v_bool := public.claim_account_email(v_a, 'pro_purchase', repeat('a', 64));
  r := r || (case when v_bool then 'ok  E8 a purchase confirmation is claimed' else 'FAIL E8' end);
  v_bool := public.claim_account_email(v_a, 'pro_purchase', repeat('a', 64));
  r := r || (case when not v_bool then 'ok  E9 the same purchase (renewal, re-check) is not confirmed twice' else 'FAIL E9' end);
  v_bool := public.claim_account_email(v_a, 'pro_purchase', repeat('b', 64));
  r := r || (case when v_bool then 'ok  E10 a different purchase gets its own confirmation' else 'FAIL E10' end);

  -- ======================================================================== release, bad input, unknown account
  perform public.release_account_email(v_a, 'pro_purchase', repeat('b', 64));
  v_bool := public.claim_account_email(v_a, 'pro_purchase', repeat('b', 64));
  r := r || (case when v_bool then 'ok  E11 a released claim (failed send) can be claimed again' else 'FAIL E11' end);
  begin
    perform public.claim_account_email(v_a, 'newsletter', '');
    r := r || 'FAIL E12 an unknown email kind was accepted'::text;
  exception when sqlstate '22023' then r := r || 'ok  E12 an unknown email kind is refused'::text; end;
  begin
    perform public.claim_account_email(null, 'welcome', '');
    r := r || 'FAIL E13 a claim without an account was accepted'::text;
  exception when sqlstate '22023' then r := r || 'ok  E13 a claim needs an account'::text; end;
  v_bool := public.claim_account_email(gen_random_uuid(), 'welcome', '');
  r := r || (case when not v_bool then 'ok  E14 an account that does not exist claims nothing' else 'FAIL E14' end);
  execute 'reset role';

  -- ======================================================================== account deletion
  delete from auth.users where id = v_a;
  select count(*) into v_n from public.account_emails where user_id = v_a;
  r := r || (case when v_n = 0 then 'ok  E15 deleting the account removes its email rows' else 'FAIL E15' end);
  select count(*) into v_n from public.account_emails where user_id = v_b;
  r := r || (case when v_n = 1 then 'ok  E16 other accounts keep theirs' else 'FAIL E16' end);

  raise exception E'VERIFY %/% failed\n%',
    (select count(*) from unnest(r) x where x like 'FAIL%'), array_length(r, 1), array_to_string(r, E'\n');
end
$verify$;
