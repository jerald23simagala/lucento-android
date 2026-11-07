-- Multicurrency suite (supabase/migrations/20261106090000_multicurrency.sql). Run with tools/sql-verify:
--   node run.mjs ../../supabase/migrations multicurrency_suite.sql
-- Checks the FX columns' rules and who may read and write a ledger's main currency. Raises one exception at the end
-- listing every check; the runner reads "VERIFY 0/N failed" as a pass.
do $verify$
declare
  r text[] := '{}';
  v_o uuid := gen_random_uuid(); v_p uuid := gen_random_uuid(); v_x uuid := gen_random_uuid();
  v_wu text := 'wu-' || gen_random_uuid(); v_wp text := 'wp-' || gen_random_uuid();
  v_inv uuid; v_n integer; v_txt text;
begin
  create function pg_temp.as_user(p_uid uuid, p_email text) returns void language plpgsql as $f$
  begin
    perform set_config('request.jwt.claims', json_build_object('sub', p_uid, 'role', 'authenticated', 'email', p_email)::text, true);
    perform set_config('role', 'authenticated', true);
  end $f$;

  insert into auth.users (id, email, email_confirmed_at, aud, role) values
    (v_o, 'owner@lucento-test.invalid', now(), 'authenticated', 'authenticated'),
    (v_p, 'partner@lucento-test.invalid', now(), 'authenticated', 'authenticated'),
    (v_x, 'stranger@lucento-test.invalid', now(), 'authenticated', 'authenticated');

  -- Lucento Pro (20261107090000_entitlements.sql): inviting a Partner now needs the SHARED capability. Every account in
  -- this suite holds it (a GRANT row, as the server would write for a founder on lucento-test), so the suite keeps testing
  -- exactly what it did before plans existed. Plan-specific behaviour is entitlements_suite.sql.
  insert into public.entitlements (user_id, source, status, grants, expires_at, note) values
    (v_o, 'GRANT', 'ACTIVE', array['LOCAL_PRO', 'CLOUD', 'AI_PRO', 'SHARED'], now() + interval '30 days', 'suite fixture'),
    (v_p, 'GRANT', 'ACTIVE', array['LOCAL_PRO', 'CLOUD', 'AI_PRO', 'SHARED'], now() + interval '30 days', 'suite fixture'),
    (v_x, 'GRANT', 'ACTIVE', array['LOCAL_PRO', 'CLOUD', 'AI_PRO', 'SHARED'], now() + interval '30 days', 'suite fixture');

  -- ======================================================================== FX columns (as the owner, through RLS)
  perform pg_temp.as_user(v_o, 'owner@lucento-test.invalid');
  insert into public.wallets (id, user_id, name, type, currency_code, opening_balance_minor, created_at, updated_at, fx_basis_rate)
    values (v_wu, v_o, 'Dollars', 'BANK', 'USD', 10000, now(), now(), '56.1234');
  insert into public.wallets (id, user_id, name, type, currency_code, opening_balance_minor, created_at, updated_at)
    values (v_wp, v_o, 'Pesos', 'BANK', 'PHP', 0, now(), now());
  select count(*) into v_n from public.wallets where id = v_wu and fx_basis_rate = '56.1234' and fx_revalue = false;
  r := r || (case when v_n = 1 then 'ok  F1 a foreign wallet keeps its rate; revaluation off by default' else 'FAIL F1' end);

  insert into public.transactions (id, user_id, type, amount_minor, currency_code, wallet_id, occurred_on, note, created_at, updated_at,
      base_amount_minor, base_currency, fx_source)
    values ('t-ok', v_o, 'EXPENSE', 1000, 'USD', v_wu, 20396, '', now(), now(), 58000, 'PHP', 'MARKET');
  insert into public.transactions (id, user_id, type, amount_minor, currency_code, wallet_id, occurred_on, note, created_at, updated_at)
    values ('t-plain', v_o, 'EXPENSE', 1000, 'PHP', v_wp, 20396, '', now(), now());
  select count(*) into v_n from public.transactions where id in ('t-ok', 't-plain');
  r := r || (case when v_n = 2 then 'ok  F2 a value in the main currency, and a row with none, are accepted' else 'FAIL F2 ' || v_n end);

  begin
    insert into public.transactions (id, user_id, type, amount_minor, currency_code, wallet_id, occurred_on, note, created_at, updated_at,
        base_amount_minor, base_currency, fx_source)
      values ('t-same', v_o, 'EXPENSE', 1000, 'PHP', v_wp, 20396, '', now(), now(), 1000, 'PHP', 'MARKET');
    r := r || 'FAIL F3 a value in the row''s own currency accepted'::text;
  exception when check_violation then r := r || 'ok  F3 a value in the row''s own currency is refused'::text; end;

  begin
    insert into public.transactions (id, user_id, type, amount_minor, currency_code, wallet_id, occurred_on, note, created_at, updated_at,
        base_amount_minor, base_currency, fx_source)
      values ('t-src', v_o, 'EXPENSE', 1000, 'USD', v_wu, 20396, '', now(), now(), 58000, 'PHP', 'GUESSED');
    r := r || 'FAIL F4 unknown source accepted'::text;
  exception when check_violation then r := r || 'ok  F4 a source this version doesn''t know is refused'::text; end;

  begin
    insert into public.transactions (id, user_id, type, amount_minor, currency_code, wallet_id, occurred_on, note, created_at, updated_at,
        base_amount_minor)
      values ('t-half', v_o, 'EXPENSE', 1000, 'USD', v_wu, 20396, '', now(), now(), 58000);
    r := r || 'FAIL F5 a value without its currency accepted'::text;
  exception when check_violation then r := r || 'ok  F5 a value without its currency is refused (all three or none)'::text; end;

  begin
    update public.wallets set fx_basis_rate = '5.6e1', updated_at = now(), base_revision = revision where id = v_wu;
    r := r || 'FAIL F6 an exponent rate accepted'::text;
  exception when check_violation then r := r || 'ok  F6 a rate must be a plain positive decimal'::text; end;

  -- A soft delete always succeeds, whatever the FX fields hold.
  update public.transactions set deleted_at = now(), updated_at = now(), base_revision = revision where id = 't-ok';
  get diagnostics v_n = row_count;
  r := r || (case when v_n = 1 then 'ok  F7 a soft delete always succeeds' else 'FAIL F7 ' || v_n end);
  execute 'reset role';

  -- ======================================================================== ledger_settings
  perform pg_temp.as_user(v_o, 'owner@lucento-test.invalid');
  insert into public.ledger_settings (user_id, main_currency) values (v_o, 'PHP');
  select main_currency into v_txt from public.ledger_settings where user_id = v_o;
  r := r || (case when v_txt = 'PHP' then 'ok  L1 the owner sets and reads the main currency' else 'FAIL L1 ' || coalesce(v_txt, 'null') end);

  begin
    insert into public.ledger_settings (user_id, main_currency) values (v_o, 'USD');
    r := r || 'FAIL L2 a second main currency accepted'::text;
  exception when unique_violation then r := r || 'ok  L2 a second set is refused (unique): the first stays'::text; end;

  begin
    update public.ledger_settings set main_currency = 'USD' where user_id = v_o;
    get diagnostics v_n = row_count;
    r := r || (case when v_n = 0 then 'ok  L3 the main currency can''t be changed (0 rows)' else 'FAIL L3 changed ' || v_n end);
  exception when insufficient_privilege then r := r || 'ok  L3 the main currency can''t be changed (no update privilege)'::text; end;

  begin
    delete from public.ledger_settings where user_id = v_o;
    get diagnostics v_n = row_count;
    r := r || (case when v_n = 0 then 'ok  L4 nor deleted (0 rows)' else 'FAIL L4 deleted ' || v_n end);
  exception when insufficient_privilege then r := r || 'ok  L4 nor deleted (no delete privilege)'::text; end;

  begin
    insert into public.ledger_settings (user_id, main_currency) values (v_x, 'PHP');
    r := r || 'FAIL L5 set for another account'::text;
  exception when insufficient_privilege then r := r || 'ok  L5 nobody sets another account''s main currency'::text; end;
  execute 'reset role';

  perform pg_temp.as_user(v_x, 'stranger@lucento-test.invalid');
  select count(*) into v_n from public.ledger_settings where user_id = v_o;
  execute 'reset role';
  r := r || (case when v_n = 0 then 'ok  L6 a stranger can''t read it' else 'FAIL L6 saw ' || v_n end);

  -- The owner shares their ledger; the Partner then reads the main currency, and still can't write it.
  perform pg_temp.as_user(v_o, 'owner@lucento-test.invalid');
  perform public.create_shared_space('Olivia');
  v_inv := public.create_space_invite('partner@lucento-test.invalid');
  execute 'reset role';
  perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
  perform public.accept_space_invite(v_inv, 'Pat');
  select main_currency into v_txt from public.ledger_settings where user_id = v_o;
  r := r || (case when v_txt = 'PHP' then 'ok  L7 a Partner reads the Shared Space''s main currency' else 'FAIL L7 ' || coalesce(v_txt, 'null') end);
  begin
    insert into public.ledger_settings (user_id, main_currency) values (v_o, 'USD');
    r := r || 'FAIL L8 a Partner set the owner''s main currency'::text;
  exception when insufficient_privilege or unique_violation then r := r || 'ok  L8 a Partner can''t set it'::text; end;
  -- The Partner's own transaction in the owner's dollar wallet carries its value like the owner's would.
  insert into public.transactions (id, user_id, type, amount_minor, currency_code, wallet_id, occurred_on, note, created_at, updated_at,
      base_amount_minor, base_currency, fx_source)
    values ('t-partner', v_o, 'EXPENSE', 500, 'USD', v_wu, 20396, '', now(), now(), 29000, 'PHP', 'MARKET');
  get diagnostics v_n = row_count;
  execute 'reset role';
  r := r || (case when v_n = 1 then 'ok  L9 a Partner adds a foreign movement with its value' else 'FAIL L9 ' || v_n end);

  set role anon;
  begin
    select count(*) into v_n from public.ledger_settings;
    r := r || (case when v_n = 0 then 'ok  L10 anon reads nothing' else 'FAIL L10 anon saw ' || v_n end);
  exception when insufficient_privilege then r := r || 'ok  L10 anon has no access'::text; end;
  execute 'reset role';

  raise exception E'VERIFY %/% failed\n%',
    (select count(*) from unnest(r) x where x like 'FAIL%'), array_length(r, 1), array_to_string(r, E'\n');
end
$verify$;
