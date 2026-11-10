-- Security remediation (docs/security/REMEDIATION_REPORT.md): regression checks for audit findings F-01, F-02, F-03,
-- F-07, F-12 and F-13, including the audit's attack probes X1, X2, X3, X8a and X8b (FINAL_SECURITY_AUDIT.md §5).
-- Run with tools/sql-verify (see README.md):
--   node run.mjs ../../supabase/migrations security_remediation_suite.sql
-- Acts as Free, Pro, lapsed and Partner accounts, a stranger, anon and the service role. Raises one exception listing
-- every check; the runner reads "VERIFY 0/N failed" as a pass. Concurrency (two sessions racing one row ceiling) needs
-- real Postgres connections and is checked by concurrency.mjs, not here.
do $verify$
declare
  r text[] := '{}';
  v_free uuid := gen_random_uuid();   -- signed in, no plan
  v_pro uuid := gen_random_uuid();    -- CLOUD through a GRANT
  v_lap uuid := gen_random_uuid();    -- had CLOUD, now expired
  v_own uuid := gen_random_uuid();    -- Owner of a Shared Space
  v_par uuid := gen_random_uuid();    -- the Owner's Partner, no plan of their own
  v_str uuid := gen_random_uuid();    -- a stranger with CLOUD
  v_buy uuid := gen_random_uuid();    -- Play buyer
  v_oth uuid := gen_random_uuid();    -- another account on the same Google Play account
  v_new uuid := gen_random_uuid();    -- a fresh account
  h_x1 text := encode(sha256(convert_to('token-x1-lifetime', 'UTF8')), 'hex');
  h_x2 text := encode(sha256(convert_to('token-x2-sub', 'UTF8')), 'hex');
  h_so text := encode(sha256(convert_to('token-signed-out', 'UTF8')), 'hex');
  h_un text := encode(sha256(convert_to('token-never-verified', 'UTF8')), 'hex');
  h_tp text := encode(sha256(convert_to('token-tester', 'UTF8')), 'hex');
  obf_buy text; obf_oth text;
  v_inv uuid; v_j jsonb; v_n bigint; v_m bigint; v_s text; v_b boolean;
begin
  create function pg_temp.as_user(p_uid uuid) returns void language plpgsql as $f$
  begin
    perform set_config('request.jwt.claims', json_build_object('sub', p_uid, 'role', 'authenticated')::text, true);
    perform set_config('role', 'authenticated', true);
  end $f$;
  -- Runs one statement as the current role; 'ok' or the SQLSTATE it failed with.
  create function pg_temp.attempt(p_sql text) returns text language plpgsql as $f$
  begin
    execute p_sql;
    return 'ok';
  exception when others then
    return sqlstate;
  end $f$;
  -- A wallet row for an account, as the app sends it.
  create function pg_temp.wallet_sql(p_id text, p_user uuid, p_name text) returns text language sql as $f$
    select format('insert into public.wallets (id, user_id, name, type, currency_code, opening_balance_minor, created_at, updated_at) '
                  'values (%L, %L, %L, ''CASH'', ''PHP'', 0, now(), now())', p_id, p_user, p_name)
  $f$;
  create function pg_temp.tx_sql(p_id text, p_user uuid, p_wallet text, p_note text) returns text language sql as $f$
    select format('insert into public.transactions (id, user_id, type, amount_minor, currency_code, wallet_id, occurred_on, note, created_at, updated_at) '
                  'values (%L, %L, ''EXPENSE'', 100, ''PHP'', %L, 20400, %L, now(), now())', p_id, p_user, p_wallet, p_note)
  $f$;
  -- n transactions in ONE statement, each with a note of p_len characters.
  create function pg_temp.bulk_tx_sql(p_prefix text, p_user uuid, p_wallet text, p_n integer, p_len integer) returns text language sql as $f$
    select format('insert into public.transactions (id, user_id, type, amount_minor, currency_code, wallet_id, occurred_on, note, created_at, updated_at) '
                  'select %L || g, %L, ''EXPENSE'', 100, ''PHP'', %L, 20400, repeat(''x'', %s), now(), now() from generate_series(1, %s) g',
                  p_prefix, p_user, p_wallet, p_len, p_n)
  $f$;

  -- New functions are no longer executable by anyone but their owner (F-13's default); the helpers are granted explicitly.
  grant execute on function pg_temp.attempt(text), pg_temp.wallet_sql(text, uuid, text), pg_temp.tx_sql(text, uuid, text, text),
    pg_temp.bulk_tx_sql(text, uuid, text, integer, integer) to public;

  insert into auth.users (id, email, email_confirmed_at, aud, role) values
    (v_free, 'free@lucento-test.invalid', now(), 'authenticated', 'authenticated'),
    (v_pro, 'pro@lucento-test.invalid', now(), 'authenticated', 'authenticated'),
    (v_lap, 'lapsed@lucento-test.invalid', now(), 'authenticated', 'authenticated'),
    (v_own, 'owner@lucento-test.invalid', now(), 'authenticated', 'authenticated'),
    (v_par, 'partner@lucento-test.invalid', now(), 'authenticated', 'authenticated'),
    (v_str, 'stranger@lucento-test.invalid', now(), 'authenticated', 'authenticated'),
    (v_buy, 'buyer@lucento-test.invalid', now(), 'authenticated', 'authenticated'),
    (v_oth, 'other@lucento-test.invalid', now(), 'authenticated', 'authenticated'),
    (v_new, 'new@lucento-test.invalid', now(), 'authenticated', 'authenticated');
  insert into public.entitlements (user_id, source, status, grants, expires_at, note) values
    (v_pro, 'GRANT', 'ACTIVE', array['LOCAL_PRO', 'CLOUD', 'AI_PRO', 'SHARED'], now() + interval '30 days', 'suite fixture'),
    (v_lap, 'GRANT', 'ACTIVE', array['LOCAL_PRO', 'CLOUD', 'AI_PRO', 'SHARED'], now() + interval '30 days', 'suite fixture'),
    (v_own, 'GRANT', 'ACTIVE', array['LOCAL_PRO', 'CLOUD', 'AI_PRO', 'SHARED'], now() + interval '30 days', 'suite fixture'),
    (v_str, 'GRANT', 'ACTIVE', array['LOCAL_PRO', 'CLOUD', 'AI_PRO', 'SHARED'], now() + interval '30 days', 'suite fixture');
  obf_buy := encode(sha256(convert_to(v_buy::text, 'UTF8')), 'hex');
  obf_oth := encode(sha256(convert_to(v_oth::text, 'UTF8')), 'hex');

  -- ======================================================================== F-01 the Cloud write gate
  perform pg_temp.as_user(v_free);
  v_s := pg_temp.attempt(pg_temp.wallet_sql('w-free', v_free, 'Cash'));
  r := r || (case when v_s = '42501' then 'ok  G1 a Free account cannot insert a wallet' else 'FAIL G1 ' || v_s end);
  v_s := pg_temp.attempt(format('insert into public.categories (id, user_id, name, kind, icon_key, created_at, updated_at) values (%L, %L, ''Food'', ''EXPENSE'', ''cart'', now(), now())', 'c-free', v_free));
  r := r || (case when v_s = '42501' then 'ok  G2 a Free account cannot insert a category' else 'FAIL G2 ' || v_s end);
  v_s := pg_temp.attempt(format('insert into public.ledger_settings (user_id, main_currency) values (%L, ''PHP'')', v_free));
  r := r || (case when v_s = '42501' then 'ok  G3 a Free account cannot set the ledger''s main currency' else 'FAIL G3 ' || v_s end);
  -- Probe X8a: one statement, 3,000 transactions with 2,000-character notes.
  v_s := pg_temp.attempt(pg_temp.bulk_tx_sql('x8a-', v_free, 'w-free', 3000, 2000));
  r := r || (case when v_s = '42501' then 'ok  X8a a Free account''s bulk insert of 3,000 large rows is refused' else 'FAIL X8a ' || v_s end);
  -- A plan claimed by the client is never read: entitlements can't be written, the capability helper can't be called.
  v_s := pg_temp.attempt(format('insert into public.entitlements (user_id, source, status, grants, expires_at) values (%L, ''GRANT'', ''ACTIVE'', array[''CLOUD''], now() + interval ''1 day'')', v_free));
  r := r || (case when v_s = '42501' then 'ok  G4 a Free account cannot write itself a plan' else 'FAIL G4 ' || v_s end);
  v_s := pg_temp.attempt(format('select lucento_private.has_capability(%L, ''CLOUD'')', v_pro));
  r := r || (case when v_s = '42501' then 'ok  G5 clients cannot ask about another account''s plan' else 'FAIL G5 ' || v_s end);
  execute 'reset role';
  select count(*) into v_n from public.transactions where user_id = v_free;
  r := r || (case when v_n = 0 then 'ok  G6 nothing of the Free account''s attempts was stored' else 'FAIL G6 ' || v_n end);

  perform pg_temp.as_user(v_pro);
  v_s := pg_temp.attempt(pg_temp.wallet_sql('w-pro', v_pro, 'Cash'));
  r := r || (case when v_s = 'ok' then 'ok  G7 a Pro (CLOUD) account inserts a wallet' else 'FAIL G7 ' || v_s end);
  v_s := pg_temp.attempt(pg_temp.tx_sql('t-pro-1', v_pro, 'w-pro', 'lunch'));
  r := r || (case when v_s = 'ok' then 'ok  G8 a Pro account inserts a transaction' else 'FAIL G8 ' || v_s end);
  v_s := pg_temp.attempt(format('update public.transactions set note = ''dinner'', updated_at = now() + interval ''1 second'' where id = ''t-pro-1'''));
  r := r || (case when v_s = 'ok' and (select note from public.transactions where id = 't-pro-1') = 'dinner'
             then 'ok  G9 a Pro account updates its transaction' else 'FAIL G9 ' || v_s end);
  v_s := pg_temp.attempt(format('insert into public.ledger_settings (user_id, main_currency) values (%L, ''PHP'')', v_pro));
  r := r || (case when v_s = 'ok' then 'ok  G10 a Pro account sets its main currency' else 'FAIL G10 ' || v_s end);
  -- A plan doesn't open anyone else's ledger.
  v_s := pg_temp.attempt(pg_temp.wallet_sql('w-pro-into-free', v_free, 'Planted'));
  r := r || (case when v_s = '42501' then 'ok  G11 a Pro account still cannot write into another account''s ledger' else 'FAIL G11 ' || v_s end);
  execute 'reset role';

  -- Lapsed: wrote while it had CLOUD, then the grant ended.
  perform pg_temp.as_user(v_lap);
  perform pg_temp.attempt(pg_temp.wallet_sql('w-lap', v_lap, 'Cash'));
  perform pg_temp.attempt(pg_temp.tx_sql('t-lap-1', v_lap, 'w-lap', 'one'));
  perform pg_temp.attempt(pg_temp.tx_sql('t-lap-2', v_lap, 'w-lap', 'two'));
  execute 'reset role';
  update public.entitlements set expires_at = now() - interval '1 minute' where user_id = v_lap;
  perform pg_temp.as_user(v_lap);
  v_s := pg_temp.attempt(pg_temp.tx_sql('t-lap-3', v_lap, 'w-lap', 'three'));
  r := r || (case when v_s = '42501' then 'ok  G12 a lapsed account''s new upload is refused (42501)' else 'FAIL G12 ' || v_s end);
  v_s := pg_temp.attempt('update public.transactions set note = ''edited'', updated_at = now() + interval ''1 second'' where id = ''t-lap-1''');
  r := r || (case when v_s = '42501' then 'ok  G13 a lapsed account''s edit (and soft delete) is refused, not silently dropped' else 'FAIL G13 ' || v_s end);
  select count(*) into v_n from public.transactions where user_id = v_lap;
  r := r || (case when v_n = 2 then 'ok  G14 a lapsed account still reads (restores) its whole ledger' else 'FAIL G14 saw ' || v_n end);
  v_s := pg_temp.attempt('delete from public.transactions where id = ''t-lap-2''');
  r := r || (case when v_s = 'ok' and not exists (select 1 from public.transactions where id = 't-lap-2')
             then 'ok  G15 a lapsed account can still clean up (hard delete)' else 'FAIL G15 ' || v_s end);
  execute 'reset role';
  select note into v_s from public.transactions where id = 't-lap-1';
  r := r || (case when v_s = 'one' then 'ok  G16 the refused edit left the stored row unchanged' else 'FAIL G16 ' || coalesce(v_s, 'null') end);
  update public.entitlements set expires_at = now() + interval '30 days' where user_id = v_lap;
  perform pg_temp.as_user(v_lap);
  v_s := pg_temp.attempt(pg_temp.tx_sql('t-lap-3', v_lap, 'w-lap', 'three'));
  r := r || (case when v_s = 'ok' then 'ok  G17 renewing resumes uploads at once' else 'FAIL G17 ' || v_s end);
  execute 'reset role';

  -- ======================================================================== F-01 Partners write on the Owner's plan
  perform pg_temp.as_user(v_own);
  perform pg_temp.attempt(pg_temp.wallet_sql('w-own', v_own, 'Household'));
  perform public.create_shared_space('Olivia');
  v_inv := public.create_space_invite('partner@lucento-test.invalid');
  execute 'reset role';
  perform pg_temp.as_user(v_par);
  perform public.accept_space_invite(v_inv, 'Pat');
  v_s := pg_temp.attempt(pg_temp.tx_sql('t-par-1', v_own, 'w-own', 'groceries'));
  r := r || (case when v_s = 'ok' then 'ok  G18 a Partner with no plan writes the space''s transactions on the Owner''s CLOUD' else 'FAIL G18 ' || v_s end);
  v_s := pg_temp.attempt(pg_temp.wallet_sql('w-par-planted', v_own, 'Planted'));
  r := r || (case when v_s = '42501' then 'ok  G19 a Partner still cannot write the Owner''s wallets' else 'FAIL G19 ' || v_s end);
  v_s := pg_temp.attempt(pg_temp.wallet_sql('w-par-own', v_par, 'Mine'));
  r := r || (case when v_s = '42501' then 'ok  G20 the Owner''s plan doesn''t cover the Partner''s own ledger' else 'FAIL G20 ' || v_s end);
  execute 'reset role';

  -- The Owner's plan lapses. Lenient (the approved default, docs/MONETIZATION.md §6.4): the Partner keeps syncing.
  update public.entitlements set expires_at = now() - interval '1 minute' where user_id = v_own;
  perform pg_temp.as_user(v_par);
  v_s := pg_temp.attempt(pg_temp.tx_sql('t-par-2', v_own, 'w-own', 'rent'));
  r := r || (case when v_s = 'ok' then 'ok  G21 lenient rule: the Partner keeps writing after the Owner lapses' else 'FAIL G21 ' || v_s end);
  execute 'reset role';
  perform pg_temp.as_user(v_own);
  v_s := pg_temp.attempt(pg_temp.tx_sql('t-own-lapsed', v_own, 'w-own', 'own'));
  r := r || (case when v_s = '42501' then 'ok  G22 the lapsed Owner''s own uploads pause' else 'FAIL G22 ' || v_s end);
  execute 'reset role';
  -- Strict, when the owner turns it on: the Partner's writes follow the Owner's plan.
  update lucento_private.plan_limits set value_int = 0 where key = 'partner_sync_after_owner_lapse';
  perform pg_temp.as_user(v_par);
  v_s := pg_temp.attempt(pg_temp.tx_sql('t-par-3', v_own, 'w-own', 'fuel'));
  r := r || (case when v_s = '42501' then 'ok  G23 strict rule: the Partner''s writes follow the Owner''s lapsed plan' else 'FAIL G23 ' || v_s end);
  select count(*) into v_n from public.transactions where user_id = v_own;
  r := r || (case when v_n = 2 then 'ok  G24 strict rule: the Partner still reads the space' else 'FAIL G24 saw ' || v_n end);
  execute 'reset role';
  update lucento_private.plan_limits set value_int = 1 where key = 'partner_sync_after_owner_lapse';
  update public.entitlements set expires_at = now() + interval '30 days' where user_id = v_own;
  -- A stranger's own plan opens nothing in the space; the helper only answers about the caller.
  perform pg_temp.as_user(v_str);
  v_s := pg_temp.attempt(pg_temp.tx_sql('t-str-1', v_own, 'w-own', 'planted'));
  r := r || (case when v_s = '42501' then 'ok  G25 a stranger with CLOUD cannot write into the space' else 'FAIL G25 ' || v_s end);
  select cardinality(lucento_private.cloud_partner_spaces()) into v_n;
  r := r || (case when v_n = 0 then 'ok  G26 cloud_partner_spaces() answers only about the caller' else 'FAIL G26 ' || v_n end);
  execute 'reset role';
  -- The Partner is removed: no more writes, no more reads.
  perform pg_temp.as_user(v_own);
  perform public.remove_space_partner(v_par);
  execute 'reset role';
  perform pg_temp.as_user(v_par);
  v_s := pg_temp.attempt(pg_temp.tx_sql('t-par-4', v_own, 'w-own', 'after removal'));
  select count(*) into v_n from public.transactions where user_id = v_own;
  r := r || (case when v_s = '42501' and v_n = 0 then 'ok  G27 a removed Partner can neither write nor read' else 'FAIL G27 ' || v_s || ' saw ' || v_n end);
  execute 'reset role';

  -- ======================================================================== F-01 size bounds
  perform pg_temp.as_user(v_pro);
  v_s := pg_temp.attempt(pg_temp.tx_sql('t-pro-500', v_pro, 'w-pro', repeat('n', 500)));
  r := r || (case when v_s = 'ok' then 'ok  B1 a 500-character note is accepted' else 'FAIL B1 ' || v_s end);
  v_s := pg_temp.attempt(pg_temp.tx_sql('t-pro-501', v_pro, 'w-pro', repeat('n', 501)));
  r := r || (case when v_s = '23514' then 'ok  B2 a 501-character note is refused' else 'FAIL B2 ' || v_s end);
  v_s := pg_temp.attempt(pg_temp.wallet_sql('w-pro-long', v_pro, repeat('w', 101)));
  r := r || (case when v_s = '23514' then 'ok  B3 a 101-character wallet name is refused' else 'FAIL B3 ' || v_s end);
  v_s := pg_temp.attempt(pg_temp.wallet_sql(repeat('i', 129), v_pro, 'Cash'));
  r := r || (case when v_s = '23514' then 'ok  B4 an id longer than 128 characters is refused' else 'FAIL B4 ' || v_s end);
  v_s := pg_temp.attempt(format('insert into public.goals (id, user_id, name, target_minor, currency_code, created_at, updated_at) values (''g-long'', %L, %L, 100, ''PHP'', now(), now())', v_pro, repeat('g', 101)));
  r := r || (case when v_s = '23514' then 'ok  B5 a 101-character goal name is refused' else 'FAIL B5 ' || v_s end);
  execute 'reset role';
  -- A legacy row from before the bounds can still be soft-deleted. It is recreated the way production's old rows sit: stored
  -- before the constraint existed, which then arrived NOT VALID.
  alter table public.transactions drop constraint transactions_text_bounded;
  insert into public.transactions (id, user_id, type, amount_minor, currency_code, wallet_id, occurred_on, note, created_at, updated_at)
    values ('t-pro-legacy', v_pro, 'EXPENSE', 100, 'PHP', 'w-pro', 20400, repeat('l', 900), now() - interval '1 day', now() - interval '1 day');
  alter table public.transactions add constraint transactions_text_bounded check (
    deleted_at is not null or (char_length(id) <= 128 and char_length(note) <= 500
                               and (transfer_group is null or char_length(transfer_group) <= 128))) not valid;
  perform pg_temp.as_user(v_pro);
  v_s := pg_temp.attempt('update public.transactions set deleted_at = now(), updated_at = now(), base_revision = 1 where id = ''t-pro-legacy''');
  r := r || (case when v_s = 'ok' and (select deleted_at from public.transactions where id = 't-pro-legacy') is not null
             then 'ok  B6 an oversize legacy row can still be soft-deleted' else 'FAIL B6 ' || v_s end);
  execute 'reset role';

  -- ======================================================================== F-01 batch cap and row ceilings
  perform pg_temp.as_user(v_pro);
  -- Probe X8a as a paying account: one statement of 3,000 rows (notes within bounds).
  v_s := pg_temp.attempt(pg_temp.bulk_tx_sql('x8a-pro-', v_pro, 'w-pro', 3000, 200));
  r := r || (case when v_s = 'LU002' then 'ok  X8a-pro a 3,000-row statement is refused even on a paid plan (LU002)' else 'FAIL X8a-pro ' || v_s end);
  v_s := pg_temp.attempt(pg_temp.bulk_tx_sql('bulk-', v_pro, 'w-pro', 1000, 200));
  r := r || (case when v_s = 'ok' then 'ok  Q1 a 1,000-row sync batch (the cap) is accepted' else 'FAIL Q1 ' || v_s end);
  execute 'reset role';
  select row_count into v_n from lucento_private.ledger_row_counts where user_id = v_pro and table_name = 'transactions';
  select count(*) into v_m from public.transactions where user_id = v_pro;
  r := r || (case when v_n = v_m then 'ok  Q2 the account''s counter equals its stored rows (' || v_m || ')' else 'FAIL Q2 counter ' || v_n || ' rows ' || v_m end);

  -- A ceiling just above what is stored.
  update lucento_private.plan_limits set value_int = (v_m + 5)::integer where key = 'max_rows_transactions';
  perform pg_temp.as_user(v_pro);
  v_s := pg_temp.attempt(pg_temp.bulk_tx_sql('fill-', v_pro, 'w-pro', 6, 10));
  r := r || (case when v_s = 'LU001' then 'ok  Q3 a statement that would pass the ceiling is refused whole (LU001)' else 'FAIL Q3 ' || v_s end);
  v_s := pg_temp.attempt(pg_temp.bulk_tx_sql('fill-', v_pro, 'w-pro', 5, 10));
  r := r || (case when v_s = 'ok' then 'ok  Q4 rows up to the ceiling are accepted' else 'FAIL Q4 ' || v_s end);
  v_s := pg_temp.attempt(pg_temp.tx_sql('t-over', v_pro, 'w-pro', 'one too many'));
  r := r || (case when v_s = 'LU001' then 'ok  Q5 the next row past the ceiling is refused' else 'FAIL Q5 ' || v_s end);
  -- Updates (and upserts of rows already stored) never count.
  v_s := pg_temp.attempt('insert into public.transactions (id, user_id, type, amount_minor, currency_code, wallet_id, occurred_on, note, created_at, updated_at) '
                         'select id, user_id, type, amount_minor, currency_code, wallet_id, occurred_on, ''upserted'', created_at, now() + interval ''1 second'' '
                         'from public.transactions where id like ''fill-%'' on conflict (id) do update set note = excluded.note, updated_at = excluded.updated_at');
  r := r || (case when v_s = 'ok' and (select count(*) from public.transactions where id like 'fill-%' and note = 'upserted') = 5
             then 'ok  Q6 at the ceiling, upserting rows already stored still works' else 'FAIL Q6 ' || v_s end);
  -- Cleanup frees room.
  v_s := pg_temp.attempt('delete from public.transactions where id in (''fill-1'', ''fill-2'')');
  v_s := v_s || '/' || pg_temp.attempt(pg_temp.bulk_tx_sql('refill-', v_pro, 'w-pro', 2, 10));
  r := r || (case when v_s = 'ok/ok' then 'ok  Q7 hard-deleting rows frees room under the ceiling' else 'FAIL Q7 ' || v_s end);
  execute 'reset role';
  select row_count into v_n from lucento_private.ledger_row_counts where user_id = v_pro and table_name = 'transactions';
  select count(*) into v_m from public.transactions where user_id = v_pro;
  r := r || (case when v_n = v_m then 'ok  Q8 the counter still equals the stored rows after refusals and deletes' else 'FAIL Q8 counter ' || v_n || ' rows ' || v_m end);
  update lucento_private.plan_limits set value_int = 200000 where key = 'max_rows_transactions';
  -- Clients can't see or change the counters.
  perform pg_temp.as_user(v_pro);
  v_s := pg_temp.attempt(format('update lucento_private.ledger_row_counts set row_count = 0 where user_id = %L', v_pro));
  r := r || (case when v_s = '42501' then 'ok  Q9 clients cannot reset their row counter' else 'FAIL Q9 ' || v_s end);
  execute 'reset role';

  -- ======================================================================== F-01 usage_events (probe X8b)
  set role anon;
  v_s := pg_temp.attempt('insert into public.usage_events (install_id, event) select gen_random_uuid(), ''d1_return'' from generate_series(1, 3000)');
  r := r || (case when v_s = '42501' then 'ok  X8b anon can no longer insert usage_events directly' else 'FAIL X8b ' || v_s end);
  select public.record_usage_event('11111111-1111-4111-8111-111111111111', 'd1_return', '{}'::jsonb, '1.0') into v_b;
  r := r || (case when v_b then 'ok  U1 anon records one count through record_usage_event' else 'FAIL U1' end);
  v_n := 0;
  for i in 1..60 loop
    if public.record_usage_event('22222222-2222-4222-8222-222222222222', 'd7_return', '{}'::jsonb, null) then v_n := v_n + 1; end if;
  end loop;
  r := r || (case when v_n = 50 then 'ok  U2 one install stores at most 50 counts a day; the rest are dropped quietly' else 'FAIL U2 stored ' || v_n end);
  v_s := pg_temp.attempt('select public.record_usage_event(''33333333-3333-4333-8333-333333333333'', ''not_an_event'')');
  r := r || (case when v_s = '23514' then 'ok  U3 the event allow-list still applies' else 'FAIL U3 ' || v_s end);
  v_s := pg_temp.attempt('select public.record_usage_event(''00000000-0000-0000-0000-000000000000'', ''d1_return'')');
  r := r || (case when v_s = '22023' then 'ok  U4 the reserved all-zero install id is refused' else 'FAIL U4 ' || v_s end);
  v_s := pg_temp.attempt('select count(*) from lucento_private.usage_event_quota');
  r := r || (case when v_s = '42501' then 'ok  U5 anon cannot read the quota table' else 'FAIL U5 ' || v_s end);
  execute 'reset role';
  -- Inventing new install ids can't raise the day's total.
  select n into v_m from lucento_private.usage_event_quota where install_id = '00000000-0000-0000-0000-000000000000';
  update lucento_private.plan_limits set value_int = (v_m + 10)::integer where key = 'usage_events_daily_total';
  set role anon;
  v_n := 0;
  for i in 1..40 loop
    if public.record_usage_event(gen_random_uuid(), 'd1_return', '{}'::jsonb, null) then v_n := v_n + 1; end if;
  end loop;
  execute 'reset role';
  r := r || (case when v_n <= 10 then 'ok  U6 rotating install ids stops at the project-wide daily total (' || v_n || ' stored)' else 'FAIL U6 stored ' || v_n end);
  update lucento_private.plan_limits set value_int = 20000 where key = 'usage_events_daily_total';
  perform pg_temp.as_user(v_pro);
  select public.record_usage_event(gen_random_uuid(), 'first_entry_saved', '{}'::jsonb, '1.0') into v_b;
  execute 'reset role';
  r := r || (case when v_b then 'ok  U7 a signed-in phone records counts too' else 'FAIL U7' end);

  -- ======================================================================== F-02 refunds are permanent (probe X1)
  set role service_role;
  select public.apply_play_purchase(v_buy, h_x1, 'GPA.x1', 'lucento_pro_lifetime', obf_buy, 'ACTIVE', null, now() - interval '1 day', null) into v_j;
  perform public.revoke_play_purchase(h_x1);
  execute 'reset role';
  r := r || (case when v_j ->> 'result' = 'ok' and not lucento_private.has_capability(v_buy, 'LOCAL_PRO')
             then 'ok  P1 a refund ends the Lifetime purchase' else 'FAIL P1 ' || coalesce(v_j::text, 'null') end);
  perform pg_temp.as_user(v_buy);
  perform public.delete_my_account();
  execute 'reset role';
  select count(*) into v_n from lucento_private.revoked_purchases where purchase_token_hash = h_x1;
  r := r || (case when v_n = 1 then 'ok  P2 the refund record survives the account''s deletion' else 'FAIL P2 ' || v_n end);
  -- X1: a new account submits the refunded token (signed-out purchase: no account id to compare).
  set role service_role;
  select public.apply_play_purchase(v_new, h_x1, 'GPA.x1', 'lucento_pro_lifetime', null, 'ACTIVE', null, now() - interval '1 day', null) into v_j;
  execute 'reset role';
  r := r || (case when v_j ->> 'status' = 'REVOKED' and not lucento_private.has_capability(v_new, 'LOCAL_PRO')
                     and not lucento_private.has_capability(v_new, 'CLOUD')
             then 'ok  X1 a refunded purchase never revives on a new account after the refunded one is deleted'
             else 'FAIL X1 ' || coalesce(v_j::text, 'null') end);
  -- A refund that arrives before any account verified the purchase is remembered too.
  set role service_role;
  select public.revoke_play_purchase(h_un) into v_n;
  select public.apply_play_purchase(v_new, h_un, 'GPA.un', 'lucento_pro', null, 'ACTIVE', 'monthly', now(), now() + interval '1 month') into v_j;
  execute 'reset role';
  r := r || (case when v_j ->> 'status' = 'REVOKED' and not lucento_private.has_capability(v_new, 'CLOUD')
             then 'ok  P3 a refund recorded before the first verification still wins' else 'FAIL P3 ' || coalesce(v_j::text, 'null') end);
  -- A later "ACTIVE" from Play (stale or replayed) can't undo the refund either.
  set role service_role;
  select public.apply_play_purchase(null, h_un, 'GPA.un', 'lucento_pro', null, 'ACTIVE', 'monthly', now(), now() + interval '1 month') into v_j;
  execute 'reset role';
  select status into v_s from public.entitlements where purchase_token_hash = h_un;
  r := r || (case when v_s = 'REVOKED' then 'ok  P4 a revoked purchase cannot regain its entitlement' else 'FAIL P4 ' || coalesce(v_s, 'null') end);
  perform pg_temp.as_user(v_new);
  v_s := pg_temp.attempt('select count(*) from lucento_private.revoked_purchases');
  r := r || (case when v_s = '42501' then 'ok  P5 clients cannot read the refund records' else 'FAIL P5 ' || v_s end);
  execute 'reset role';

  -- ======================================================================== F-03 account binding (probe X2)
  -- A subscription bought for account OTH (two Lucento accounts on one Google Play account). Account NEW sees it first.
  set role service_role;
  select public.apply_play_purchase(v_new, h_x2, 'GPA.x2', 'lucento_pro', obf_oth, 'ACTIVE', 'monthly', now(), now() + interval '1 month') into v_j;
  execute 'reset role';
  select count(*) into v_n from public.purchase_links where purchase_token_hash = h_x2;
  r := r || (case when v_j ->> 'result' = 'other_account' and v_n = 0 and not exists (select 1 from public.entitlements where purchase_token_hash = h_x2)
             then 'ok  X2 a first verification by the wrong account is refused (other_account) and links nothing'
             else 'FAIL X2 ' || coalesce(v_j::text, 'null') || ' links=' || v_n end);
  set role service_role;
  select public.apply_play_purchase(v_oth, h_x2, 'GPA.x2', 'lucento_pro', obf_oth, 'ACTIVE', 'monthly', now(), now() + interval '1 month') into v_j;
  execute 'reset role';
  select user_id::text into v_s from public.purchase_links where purchase_token_hash = h_x2;
  r := r || (case when v_j ->> 'result' = 'ok' and v_s = v_oth::text and lucento_private.has_capability(v_oth, 'CLOUD')
             then 'ok  P6 the buyer''s own account links it at once' else 'FAIL P6 ' || coalesce(v_j::text, 'null') end);
  set role service_role;
  select public.apply_play_purchase(v_new, h_x2, 'GPA.x2', 'lucento_pro', obf_oth, 'ACTIVE', 'monthly', now(), now() + interval '1 month') into v_j;
  execute 'reset role';
  r := r || (case when v_j ->> 'result' = 'other_account' and not lucento_private.has_capability(v_new, 'CLOUD')
             then 'ok  P7 replaying the buyer''s token from another account is refused' else 'FAIL P7 ' || coalesce(v_j::text, 'null') end);
  set role service_role;
  select public.apply_play_purchase(v_oth, h_x2, 'GPA.x2', 'lucento_pro', obf_oth, 'ACTIVE', 'monthly', now(), now() + interval '1 month') into v_j;
  select public.apply_play_purchase(v_oth, h_x2, 'GPA.x2', 'lucento_pro', obf_oth, 'ACTIVE', 'monthly', now(), now() + interval '1 month') into v_j;
  execute 'reset role';
  select count(*) into v_n from public.entitlements where purchase_token_hash = h_x2;
  r := r || (case when v_j ->> 'result' = 'ok' and v_n = 1 then 'ok  P8 duplicate submissions by the buyer are idempotent (one row)' else 'FAIL P8 rows ' || v_n end);
  -- Legitimate restoration: the buyer reinstalls and verifies again; still theirs.
  set role service_role;
  select public.apply_play_purchase(v_oth, h_x2, 'GPA.x2', 'lucento_pro', obf_oth, 'ACTIVE', 'monthly', now(), now() + interval '2 months') into v_j;
  execute 'reset role';
  r := r || (case when v_j ->> 'result' = 'ok' and lucento_private.has_capability(v_oth, 'CLOUD')
             then 'ok  P9 the buyer''s restore after a reinstall keeps working' else 'FAIL P9 ' || coalesce(v_j::text, 'null') end);
  -- Signed-out purchase (no account id): the first account links it; anyone else is refused.
  set role service_role;
  select public.apply_play_purchase(v_new, h_so, 'GPA.so', 'lucento_pro_lifetime', null, 'ACTIVE', null, now(), null) into v_j;
  select public.apply_play_purchase(v_oth, h_so, 'GPA.so', 'lucento_pro_lifetime', null, 'ACTIVE', null, now(), null) into v_s;
  execute 'reset role';
  r := r || (case when v_j ->> 'result' = 'ok' and (v_s::jsonb) ->> 'result' = 'linked_elsewhere'
             then 'ok  P10 a signed-out purchase links to the first verifier only (documented policy)' else 'FAIL P10 ' || v_s end);
  set role service_role;
  v_s := pg_temp.attempt(format('select public.apply_play_purchase(%L, %L, null, ''lucento_pro'', ''NOT-A-HASH'', ''ACTIVE'', ''monthly'', now(), now() + interval ''1 month'')', v_oth, h_so));
  execute 'reset role';
  r := r || (case when v_s = '22023' then 'ok  P11 a malformed account id is refused' else 'FAIL P11 ' || v_s end);

  -- ======================================================================== F-12 test purchases
  set role service_role;
  select public.apply_play_purchase(v_oth, h_tp, 'GPA.tp', 'lucento_pro_lifetime', obf_oth, 'ACTIVE', null, now(), null, true) into v_j;
  execute 'reset role';
  r := r || (case when v_j ->> 'result' = 'test_purchase_refused' and not exists (select 1 from public.entitlements where purchase_token_hash = h_tp)
             then 'ok  T1 a license-tester purchase grants nothing by default' else 'FAIL T1 ' || coalesce(v_j::text, 'null') end);
  update lucento_private.plan_limits set value_int = 1 where key = 'allow_test_purchases';
  set role service_role;
  select public.apply_play_purchase(v_oth, h_tp, 'GPA.tp', 'lucento_pro_lifetime', obf_oth, 'ACTIVE', null, now(), null, true) into v_j;
  execute 'reset role';
  select count(*) into v_n from public.entitlements where purchase_token_hash = h_tp and test_purchase;
  r := r || (case when v_j ->> 'result' = 'ok' and v_n = 2 then 'ok  T2 allowed on purpose, its rows are marked test_purchase' else 'FAIL T2 ' || v_n end);
  update lucento_private.plan_limits set value_int = 0 where key = 'allow_test_purchases';

  -- ======================================================================== F-07 seed category ids (probe X3)
  perform pg_temp.as_user(v_str);
  v_s := pg_temp.attempt(format('insert into public.categories (id, user_id, name, kind, icon_key, created_at, updated_at) values (%L, %L, ''Salary'', ''INCOME'', ''cash'', now(), now())',
                                'seed-' || v_pro || '-income-salary', v_str));
  r := r || (case when v_s = '23514' then 'ok  X3 another account cannot claim a user''s seed category id' else 'FAIL X3 ' || v_s end);
  execute 'reset role';
  perform pg_temp.as_user(v_pro);
  v_s := pg_temp.attempt(format('insert into public.categories (id, user_id, name, kind, icon_key, created_at, updated_at) values (%L, %L, ''Salary'', ''INCOME'', ''cash'', now(), now())',
                                'seed-' || v_pro || '-income-salary', v_pro));
  r := r || (case when v_s = 'ok' then 'ok  S1 the account itself still uploads its seed category' else 'FAIL S1 ' || v_s end);
  v_s := pg_temp.attempt(format('insert into public.categories (id, user_id, name, kind, icon_key, created_at, updated_at) values (%L, %L, ''Food'', ''EXPENSE'', ''cart'', now(), now())',
                                gen_random_uuid()::text, v_pro));
  r := r || (case when v_s = 'ok' then 'ok  S2 ordinary (random) category ids are unaffected' else 'FAIL S2 ' || v_s end);
  execute 'reset role';

  -- ======================================================================== F-11 per-account request quota
  update lucento_private.plan_limits set value_int = 3 where key = 'billing_verify_daily';
  perform pg_temp.as_user(v_pro);
  v_n := 0;
  for i in 1..5 loop
    select public.consume_request_quota('billing_verify') into v_j;
    if (v_j ->> 'allowed')::boolean then v_n := v_n + 1; end if;
  end loop;
  execute 'reset role';
  r := r || (case when v_n = 3 and (v_j ->> 'used')::int = 5 then 'ok  R1 an account gets its daily verify allowance, then is refused (refusals counted)'
             else 'FAIL R1 allowed ' || v_n || ' ' || coalesce(v_j::text, 'null') end);
  perform pg_temp.as_user(v_str);
  select public.consume_request_quota('billing_verify') into v_j;
  execute 'reset role';
  r := r || (case when (v_j ->> 'allowed')::boolean then 'ok  R2 one account''s use never spends another''s allowance' else 'FAIL R2 ' || v_j::text end);
  set role anon;
  v_s := pg_temp.attempt('select public.consume_request_quota(''billing_verify'')');
  execute 'reset role';
  r := r || (case when v_s = '42501' then 'ok  R3 anon cannot call the request quota' else 'FAIL R3 ' || v_s end);
  perform pg_temp.as_user(v_pro);
  v_s := pg_temp.attempt('select public.consume_request_quota(''anything_else'')');
  execute 'reset role';
  r := r || (case when v_s = '22023' then 'ok  R4 an unknown kind is refused' else 'FAIL R4 ' || v_s end);
  update lucento_private.plan_limits set value_int = 60 where key = 'billing_verify_daily';

  -- ======================================================================== F-13 default privileges
  create table public.zz_future_table (id integer primary key);
  create function public.zz_future_function() returns integer language sql as $f$ select 1 $f$;
  r := r || (case when not has_table_privilege('anon', 'public.zz_future_table', 'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')
                     and not has_table_privilege('authenticated', 'public.zz_future_table', 'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')
             then 'ok  D1 a new public table grants anon and authenticated nothing by default' else 'FAIL D1' end);
  r := r || (case when not has_function_privilege('anon', 'public.zz_future_function()', 'EXECUTE')
                     and not has_function_privilege('authenticated', 'public.zz_future_function()', 'EXECUTE')
             then 'ok  D2 a new public function is not executable by anon or authenticated by default' else 'FAIL D2' end);
  select count(*) into v_n from pg_default_acl d join pg_namespace n on n.oid = d.defaclnamespace
    where n.nspname = 'public' and d.defaclrole = 'postgres'::regrole
      and exists (select 1 from aclexplode(d.defaclacl) a where a.grantee in ('anon'::regrole, 'authenticated'::regrole));
  r := r || (case when v_n = 0 then 'ok  D3 pg_default_acl for postgres in public names neither anon nor authenticated' else 'FAIL D3 ' || v_n end);
  drop table public.zz_future_table;
  drop function public.zz_future_function();
  -- What exists keeps its intended grants.
  r := r || (case when has_table_privilege('authenticated', 'public.transactions', 'SELECT,INSERT,UPDATE,DELETE')
                     and not has_table_privilege('anon', 'public.transactions', 'SELECT')
                     and has_function_privilege('authenticated', 'public.delete_my_account()', 'EXECUTE')
             then 'ok  D4 existing tables and RPCs keep their intended grants' else 'FAIL D4' end);

  raise exception E'VERIFY %/% failed\n%',
    (select count(*) from unnest(r) x where x like 'FAIL%'), array_length(r, 1), array_to_string(r, E'\n');
end
$verify$;
