-- Account deletion (supabase/migrations/20261109090000_account_deletion.sql, docs/ACCOUNT_DELETION.md). Run with
-- tools/sql-verify (see README.md):
--   node run.mjs ../../supabase/migrations account_deletion_suite.sql
-- A solo account with a ledger and a Lifetime purchase deletes itself; then a Partner, then an Owner with a Partner.
-- Checks that nothing of the deleted account is left in any public table, that nobody else loses anything, that only
-- a signed-in account can call it, and that the Play purchase can be restored on a new account. Raises one exception
-- listing every check; the runner reads "VERIFY 0/N failed" as a pass.
do $verify$
declare
  r text[] := '{}';
  v_a uuid := gen_random_uuid(); v_b uuid := gen_random_uuid(); v_o uuid := gen_random_uuid();
  v_p uuid := gen_random_uuid(); v_q uuid := gen_random_uuid(); v_n uuid := gen_random_uuid();
  h_life text := encode(sha256(convert_to('token-lifetime-deleted-account', 'UTF8')), 'hex');
  v_inv uuid; v_j jsonb; v_cnt integer; v_before integer; v_left integer; v_tab text; v_col text;
begin
  create function pg_temp.as_user(p_uid uuid, p_email text) returns void language plpgsql as $f$
  begin
    perform set_config('request.jwt.claims', json_build_object('sub', p_uid, 'role', 'authenticated', 'email', p_email)::text, true);
    perform set_config('role', 'authenticated', true);
  end $f$;

  -- Rows anywhere in public that still name the account (user_id, owner_id or partner_id), read as postgres.
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

  insert into auth.users (id, email, email_confirmed_at, aud, role) values
    (v_a, 'a@lucento-test.invalid', now(), 'authenticated', 'authenticated'),
    (v_b, 'b@lucento-test.invalid', now(), 'authenticated', 'authenticated'),
    (v_o, 'owner@lucento-test.invalid', now(), 'authenticated', 'authenticated'),
    (v_p, 'partner@lucento-test.invalid', now(), 'authenticated', 'authenticated'),
    (v_q, 'second@lucento-test.invalid', now(), 'authenticated', 'authenticated'),
    (v_n, 'new@lucento-test.invalid', now(), 'authenticated', 'authenticated');
  -- Inviting needs the SHARED capability (20261107090000_entitlements.sql).
  -- Writing a synced ledger needs CLOUD (20261111090000_cloud_write_gate_and_quotas.sql): every account here that syncs
  -- holds it, as a Cloud subscriber would. The new account N only restores a purchase.
  insert into public.entitlements (user_id, source, status, grants, expires_at, note) values
    (v_o, 'GRANT', 'ACTIVE', array['LOCAL_PRO', 'CLOUD', 'AI_PRO', 'SHARED'], now() + interval '30 days', 'suite fixture'),
    (v_a, 'GRANT', 'ACTIVE', array['CLOUD'], now() + interval '30 days', 'suite fixture'),
    (v_b, 'GRANT', 'ACTIVE', array['CLOUD'], now() + interval '30 days', 'suite fixture'),
    (v_p, 'GRANT', 'ACTIVE', array['CLOUD'], now() + interval '30 days', 'suite fixture'),
    (v_q, 'GRANT', 'ACTIVE', array['CLOUD'], now() + interval '30 days', 'suite fixture');

  -- ======================================================================== fixtures
  -- A: a small ledger written through RLS, and a Lifetime purchase recorded by the service role.
  perform pg_temp.as_user(v_a, 'a@lucento-test.invalid');
  insert into public.wallets (id, user_id, name, type, currency_code, opening_balance_minor, created_at, updated_at)
    values ('wa-' || v_a, v_a, 'Cash', 'CASH', 'PHP', 0, now(), now());
  insert into public.categories (id, user_id, name, kind, icon_key, created_at, updated_at)
    values ('ca-' || v_a, v_a, 'Food', 'EXPENSE', 'cart', now(), now());
  insert into public.budgets (id, user_id, category_id, currency_code, limit_minor, created_at, updated_at)
    values ('ba-' || v_a, v_a, 'ca-' || v_a, 'PHP', 100000, now(), now());
  insert into public.transactions (id, user_id, type, amount_minor, currency_code, wallet_id, category_id, occurred_on, note, created_at, updated_at)
    values ('ta-' || v_a, v_a, 'EXPENSE', 2500, 'PHP', 'wa-' || v_a, 'ca-' || v_a, 20398, 'lunch', now(), now());
  execute 'reset role';
  set role service_role;
  perform public.apply_play_purchase(v_a, h_life, 'GPA.del', 'lucento_pro_lifetime', null, 'ACTIVE', null, now() - interval '2 days', null);
  execute 'reset role';

  -- B: an unrelated account.
  perform pg_temp.as_user(v_b, 'b@lucento-test.invalid');
  insert into public.wallets (id, user_id, name, type, currency_code, opening_balance_minor, created_at, updated_at)
    values ('wb-' || v_b, v_b, 'Bank', 'BANK', 'PHP', 0, now(), now());
  execute 'reset role';

  -- O shares with P; P adds a transaction to O's ledger.
  perform pg_temp.as_user(v_o, 'owner@lucento-test.invalid');
  insert into public.wallets (id, user_id, name, type, currency_code, opening_balance_minor, created_at, updated_at)
    values ('wo-' || v_o, v_o, 'Household', 'BANK', 'PHP', 0, now(), now());
  perform public.create_shared_space('Olivia');
  v_inv := public.create_space_invite('partner@lucento-test.invalid');
  execute 'reset role';
  perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
  perform public.accept_space_invite(v_inv, 'Pat');
  insert into public.transactions (id, user_id, type, amount_minor, currency_code, wallet_id, occurred_on, note, created_at, updated_at)
    values ('tp-' || v_p, v_o, 'EXPENSE', 900, 'PHP', 'wo-' || v_o, 20398, 'coffee', now(), now());
  execute 'reset role';

  -- ======================================================================== who may call it
  set role anon;
  begin
    perform public.delete_my_account();
    r := r || 'FAIL D1 anon called delete_my_account'::text;
  exception when insufficient_privilege then r := r || 'ok  D1 anon cannot call delete_my_account'::text; end;
  execute 'reset role';

  perform set_config('request.jwt.claims', '', true);
  set role authenticated;
  begin
    perform public.delete_my_account();
    r := r || 'FAIL D2 a call without a signed-in account deleted something'::text;
  exception when invalid_authorization_specification then r := r || 'ok  D2 no signed-in account: refused'::text; end;
  execute 'reset role';

  -- ======================================================================== a solo account deletes itself
  v_before := pg_temp.rows_naming(v_a);
  perform pg_temp.as_user(v_a, 'a@lucento-test.invalid');
  v_j := public.delete_my_account();
  execute 'reset role';
  r := r || (case when (v_j ->> 'deleted')::boolean then 'ok  D3 the account deletes itself' else 'FAIL D3 ' || coalesce(v_j::text, 'null') end);

  v_left := pg_temp.rows_naming(v_a);
  select count(*) into v_cnt from auth.users where id = v_a;
  r := r || (case when v_before >= 6 and v_left = 0 and v_cnt = 0
                  then 'ok  D4 nothing of the account is left in any public table (' || v_before || ' rows before), nor in auth.users'
                  else 'FAIL D4 before=' || v_before || ' left=' || v_left || ' auth=' || v_cnt end);

  select count(*) into v_cnt from public.wallets where user_id = v_b;
  r := r || (case when v_cnt = 1 then 'ok  D5 another account keeps everything' else 'FAIL D5 b wallets=' || v_cnt end);

  perform pg_temp.as_user(v_a, 'a@lucento-test.invalid');
  begin
    perform public.delete_my_account();
    r := r || 'FAIL D6 a second call with the old token succeeded'::text;
  exception when no_data_found then r := r || 'ok  D6 a token that outlived its account: "already deleted"'::text; end;
  execute 'reset role';

  perform pg_temp.as_user(v_a, 'a@lucento-test.invalid');
  begin
    insert into public.wallets (id, user_id, name, type, currency_code, opening_balance_minor, created_at, updated_at)
      values ('wa2-' || v_a, v_a, 'Late sync', 'CASH', 'PHP', 0, now(), now());
    r := r || 'FAIL D7 a late sync recreated data under the deleted account'::text;
  -- Refused by the Cloud gate (the account's plan rows went with it) before the foreign key is even checked.
  exception when foreign_key_violation or insufficient_privilege then
    r := r || 'ok  D7 a late sync from the old token cannot recreate data'::text; end;
  execute 'reset role';

  -- The person still owns the purchase in Google Play: a new account can restore it.
  set role service_role;
  select public.apply_play_purchase(v_n, h_life, 'GPA.del', 'lucento_pro_lifetime', null, 'ACTIVE', null, now() - interval '2 days', null) into v_j;
  execute 'reset role';
  select count(*) into v_cnt from public.entitlements where user_id = v_n and source = 'PLAY_LIFETIME';
  r := r || (case when v_j ->> 'result' = 'ok' and v_cnt = 1 then 'ok  D8 the Lifetime purchase restores on a new account'
                  else 'FAIL D8 ' || coalesce(v_j::text, 'null') || ' rows=' || v_cnt end);

  -- ======================================================================== a Partner deletes their account
  perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
  perform public.delete_my_account();
  execute 'reset role';
  select count(*) into v_cnt from public.transactions where id = 'tp-' || v_p and user_id = v_o;
  select count(*) into v_left from public.space_members where partner_id = v_p;
  r := r || (case when v_cnt = 1 and v_left = 0 and exists (select 1 from public.shared_spaces where owner_id = v_o)
                     and exists (select 1 from public.wallets where id = 'wo-' || v_o)
                  then 'ok  D9 a Partner leaves; the Owner keeps the space, the ledger and what the Partner added'
                  else 'FAIL D9 partner tx=' || v_cnt || ' memberships=' || v_left end);

  -- ======================================================================== an Owner with a Partner deletes
  perform pg_temp.as_user(v_o, 'owner@lucento-test.invalid');
  v_inv := public.create_space_invite('second@lucento-test.invalid');
  execute 'reset role';
  perform pg_temp.as_user(v_q, 'second@lucento-test.invalid');
  perform public.accept_space_invite(v_inv, 'Quinn');
  insert into public.wallets (id, user_id, name, type, currency_code, opening_balance_minor, created_at, updated_at)
    values ('wq-' || v_q, v_q, 'Own wallet', 'CASH', 'PHP', 0, now(), now());
  execute 'reset role';
  perform pg_temp.as_user(v_o, 'owner@lucento-test.invalid');
  perform public.delete_my_account();
  execute 'reset role';
  v_left := pg_temp.rows_naming(v_o);
  select count(*) into v_cnt from public.wallets where user_id = v_q;
  perform pg_temp.as_user(v_q, 'second@lucento-test.invalid');
  v_j := public.get_my_shared_space();
  execute 'reset role';
  r := r || (case when v_left = 0 and v_cnt = 1 and (v_j ->> 'role') is null
                  then 'ok  D10 an Owner deletes: the space, memberships and invitations go; the Partner''s own ledger stays'
                  else 'FAIL D10 left=' || v_left || ' partner wallets=' || v_cnt || ' space=' || coalesce(v_j::text, 'null') end);

  raise exception E'VERIFY %/% failed\n%',
    (select count(*) from unnest(r) x where x like 'FAIL%'), array_length(r, 1), array_to_string(r, E'\n');
end
$verify$;
