-- Settings that follow the account (supabase/migrations/20261122090000_user_settings.sql). Run with tools/sql-verify:
--   node run.mjs ../../supabase/migrations user_settings_suite.sql
-- Checks who may read and write an account's settings row, the compare-and-set rule phones rely on, the CLOUD gate, the
-- document's bounds and what account deletion removes. Raises one exception at the end listing every check; the runner
-- reads "VERIFY 0/N failed" as a pass.
do $verify$
declare
  r text[] := '{}';
  v_o uuid := gen_random_uuid(); v_p uuid := gen_random_uuid(); v_x uuid := gen_random_uuid(); v_f uuid := gen_random_uuid();
  v_inv uuid; v_n integer; v_rev bigint; v_txt text;
begin
  create function pg_temp.as_user(p_uid uuid, p_email text) returns void language plpgsql as $f$
  begin
    perform set_config('request.jwt.claims', json_build_object('sub', p_uid, 'role', 'authenticated', 'email', p_email)::text, true);
    perform set_config('role', 'authenticated', true);
  end $f$;

  insert into auth.users (id, email, email_confirmed_at, aud, role) values
    (v_o, 'owner@lucento-test.invalid', now(), 'authenticated', 'authenticated'),
    (v_p, 'partner@lucento-test.invalid', now(), 'authenticated', 'authenticated'),
    (v_x, 'stranger@lucento-test.invalid', now(), 'authenticated', 'authenticated'),
    (v_f, 'free@lucento-test.invalid', now(), 'authenticated', 'authenticated');

  -- The owner, the Partner and the stranger hold CLOUD (a GRANT row, as for a founder on lucento-test); v_f is on Free.
  insert into public.entitlements (user_id, source, status, grants, expires_at, note) values
    (v_o, 'GRANT', 'ACTIVE', array['LOCAL_PRO', 'CLOUD', 'AI_PRO', 'SHARED'], now() + interval '30 days', 'suite fixture'),
    (v_p, 'GRANT', 'ACTIVE', array['LOCAL_PRO', 'CLOUD', 'AI_PRO', 'SHARED'], now() + interval '30 days', 'suite fixture'),
    (v_x, 'GRANT', 'ACTIVE', array['LOCAL_PRO', 'CLOUD', 'AI_PRO', 'SHARED'], now() + interval '30 days', 'suite fixture');

  -- ======================================================================== the owner's own row
  perform pg_temp.as_user(v_o, 'owner@lucento-test.invalid');
  insert into public.user_settings (user_id, settings, revision)
    values (v_o, '{"theme": {"v": "grove", "at": 1763800000000}}', 99);
  select revision into v_rev from public.user_settings where user_id = v_o;
  r := r || (case when v_rev = 1 then 'ok  U1 the first write is revision 1 (a revision sent by the phone is ignored)' else 'FAIL U1 ' || coalesce(v_rev::text, 'null') end);

  select settings #>> '{theme,v}' into v_txt from public.user_settings where user_id = v_o;
  r := r || (case when v_txt = 'grove' then 'ok  U2 the account reads its own settings' else 'FAIL U2 ' || coalesce(v_txt, 'null') end);

  -- What the app sends (PostgREST upsert): the merged document on top of the revision it read.
  insert into public.user_settings (user_id, settings, base_revision)
    values (v_o, '{"theme": {"v": "mono", "at": 1763800000500}}', 1)
    on conflict (user_id) do update set user_id = excluded.user_id, settings = excluded.settings, base_revision = excluded.base_revision
    returning revision into v_rev;
  get diagnostics v_n = row_count;
  r := r || (case when v_n = 1 and v_rev = 2 then 'ok  U3 a write on top of the revision read is kept and becomes revision 2' else 'FAIL U3 ' || v_n || '/' || coalesce(v_rev::text, 'null') end);

  select count(*) into v_n from public.user_settings where user_id = v_o and base_revision is null;
  r := r || (case when v_n = 1 then 'ok  U4 base_revision only carries the request (null once kept)' else 'FAIL U4' end);

  -- Another phone merged with revision 1 too and saves after: skipped, no row back, the stored version stays.
  insert into public.user_settings (user_id, settings, base_revision)
    values (v_o, '{"theme": {"v": "aurora", "at": 1763800000900}}', 1)
    on conflict (user_id) do update set user_id = excluded.user_id, settings = excluded.settings, base_revision = excluded.base_revision
    returning revision into v_rev;
  get diagnostics v_n = row_count;
  select settings #>> '{theme,v}' into v_txt from public.user_settings where user_id = v_o;
  r := r || (case when v_n = 0 and v_txt = 'mono' then 'ok  U5 a write on top of an older revision is skipped (no row back), whatever its times' else 'FAIL U5 ' || v_n || ' ' || coalesce(v_txt, 'null') end);

  -- A blind write (no base: what a second "first write" becomes) never replaces a stored version either.
  update public.user_settings set settings = '{"theme": {"v": "aurora", "at": 1}}', base_revision = null where user_id = v_o;
  get diagnostics v_n = row_count;
  r := r || (case when v_n = 0 then 'ok  U6 a write without a base is skipped' else 'FAIL U6 ' || v_n end);

  update public.user_settings set settings = '{"theme": {"v": "aurora", "at": 1763800001000}}', base_revision = 2 where user_id = v_o;
  select revision into v_rev from public.user_settings where user_id = v_o;
  r := r || (case when v_rev = 3 then 'ok  U7 merging again on the newer revision is kept (revision 3)' else 'FAIL U7 ' || coalesce(v_rev::text, 'null') end);

  begin
    update public.user_settings set settings = '["not", "an", "object"]', base_revision = 3 where user_id = v_o;
    r := r || 'FAIL U8 an array accepted'::text;
  exception when check_violation then r := r || 'ok  U8 the document must be a JSON object'::text; end;

  begin
    update public.user_settings set settings = jsonb_build_object('big', repeat('x', 17000)), base_revision = 3 where user_id = v_o;
    r := r || 'FAIL U9 a document over 16 KB accepted'::text;
  exception when check_violation then r := r || 'ok  U9 the document is at most 16 KB'::text; end;

  begin
    update public.user_settings set user_id = v_x, base_revision = 3 where user_id = v_o;
    r := r || 'FAIL U10 user_id moved'::text;
  exception when others then r := r || 'ok  U10 a row can''t move to another account'::text; end;

  begin
    delete from public.user_settings where user_id = v_o;
    r := r || 'FAIL U11 a signed-in delete went through'::text;
  exception when insufficient_privilege then r := r || 'ok  U11 no deletes: the row goes with the account'::text; end;
  execute 'reset role';

  -- ======================================================================== other accounts
  perform pg_temp.as_user(v_x, 'stranger@lucento-test.invalid');
  select count(*) into v_n from public.user_settings where user_id = v_o;
  r := r || (case when v_n = 0 then 'ok  U12 another account sees no row of the owner''s' else 'FAIL U12 saw ' || v_n end);

  update public.user_settings set settings = '{}', base_revision = 3 where user_id = v_o;
  get diagnostics v_n = row_count;
  r := r || (case when v_n = 0 then 'ok  U13 another account changes none' else 'FAIL U13 changed ' || v_n end);

  begin
    insert into public.user_settings (user_id, settings) values (v_o, '{}')
      on conflict (user_id) do update set settings = excluded.settings, base_revision = excluded.base_revision;
    r := r || 'FAIL U14 wrote the owner''s row'::text;
  exception when insufficient_privilege then r := r || 'ok  U14 another account can''t write a row for the owner'::text; end;

  insert into public.user_settings (user_id, settings) values (v_x, '{"show_cents": {"v": false, "at": 5}}');
  select count(*) into v_n from public.user_settings;
  r := r || (case when v_n = 1 then 'ok  U15 each account sees exactly its own row' else 'FAIL U15 saw ' || v_n end);
  execute 'reset role';

  -- ======================================================================== the CLOUD gate (security audit F-01)
  insert into public.user_settings (user_id, settings) values (v_f, '{"theme": {"v": "sunrise", "at": 1}}');
  perform pg_temp.as_user(v_f, 'free@lucento-test.invalid');
  select count(*) into v_n from public.user_settings where user_id = v_f;
  r := r || (case when v_n = 1 then 'ok  U16 without CLOUD the account still reads its settings (a lapsed plan restores)' else 'FAIL U16 ' || v_n end);
  begin
    update public.user_settings set settings = '{}', base_revision = 1 where user_id = v_f;
    r := r || 'FAIL U17 an update without CLOUD went through'::text;
  exception when insufficient_privilege then r := r || 'ok  U17 without CLOUD an update is refused (42501)'::text; end;
  execute 'reset role';
  delete from public.user_settings where user_id = v_f;
  perform pg_temp.as_user(v_f, 'free@lucento-test.invalid');
  begin
    insert into public.user_settings (user_id, settings) values (v_f, '{}');
    r := r || 'FAIL U18 an insert without CLOUD went through'::text;
  exception when insufficient_privilege then r := r || 'ok  U18 without CLOUD a first write is refused (42501)'::text; end;
  execute 'reset role';

  -- ======================================================================== Lucento Shared: settings stay personal
  perform pg_temp.as_user(v_o, 'owner@lucento-test.invalid');
  perform public.create_shared_space('Olivia');
  v_inv := public.create_space_invite('partner@lucento-test.invalid');
  execute 'reset role';
  perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
  perform public.accept_space_invite(v_inv, 'Pat');
  select count(*) into v_n from public.user_settings where user_id = v_o;
  r := r || (case when v_n = 0 then 'ok  U19 a Partner doesn''t see the Owner''s settings' else 'FAIL U19 saw ' || v_n end);
  insert into public.user_settings (user_id, settings) values (v_p, '{"display_name": {"v": "Pat", "at": 7}}');
  get diagnostics v_n = row_count;
  r := r || (case when v_n = 1 then 'ok  U20 a Partner keeps settings of their own' else 'FAIL U20 ' || v_n end);
  execute 'reset role';
  perform pg_temp.as_user(v_o, 'owner@lucento-test.invalid');
  select count(*) into v_n from public.user_settings where user_id = v_p;
  r := r || (case when v_n = 0 then 'ok  U21 the Owner doesn''t see the Partner''s settings' else 'FAIL U21 saw ' || v_n end);
  execute 'reset role';

  -- ======================================================================== anon, and account deletion
  set role anon;
  begin
    select count(*) into v_n from public.user_settings;
    r := r || (case when v_n = 0 then 'ok  U22 anon reads nothing' else 'FAIL U22 anon saw ' || v_n end);
  exception when insufficient_privilege then r := r || 'ok  U22 anon has no access'::text; end;
  execute 'reset role';

  perform pg_temp.as_user(v_x, 'stranger@lucento-test.invalid');
  perform public.delete_my_account();
  execute 'reset role';
  select count(*) into v_n from public.user_settings where user_id = v_x;
  r := r || (case when v_n = 0 then 'ok  U23 deleting the account removes its settings' else 'FAIL U23 ' || v_n || ' left' end);

  raise exception E'VERIFY %/% failed\n%',
    (select count(*) from unnest(r) x where x like 'FAIL%'), array_length(r, 1), array_to_string(r, E'\n');
end
$verify$;
