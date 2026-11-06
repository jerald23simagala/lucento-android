-- Lucento Shared security suite (docs/shared/SECURITY_MODEL.md). Run with tools/sql-verify (see README.md).
-- Signs in as an Owner, a Partner, a stranger, a second Partner and an unverified account, and checks what each may
-- read and write. Raises one exception at the end listing every check; the runner reads "VERIFY 0/N failed" as a pass.
do $verify$
declare
  r text[] := '{}';
  v_o uuid := gen_random_uuid(); v_p uuid := gen_random_uuid(); v_x uuid := gen_random_uuid();
  v_q uuid := gen_random_uuid(); v_u uuid := gen_random_uuid();
  v_w1 text := 'w1-' || gen_random_uuid(); v_wx text := 'wx-' || gen_random_uuid(); v_c1 text := 'c1-' || gen_random_uuid();
  v_t1 text := 't1-' || gen_random_uuid(); v_t2 text := 't2-' || gen_random_uuid();
  v_inv uuid; v_inv2 uuid; v_inv3 uuid;
  v_n integer; v_n2 integer; v_rev bigint; v_txt text; v_cb uuid; v_ub uuid; v_j jsonb; v_bool boolean;
begin
  -- ======================================================================== fixtures (as postgres)
  create function pg_temp.as_user(p_uid uuid, p_email text) returns void language plpgsql as $f$
  begin
    perform set_config('request.jwt.claims', json_build_object('sub', p_uid, 'role', 'authenticated', 'email', p_email)::text, true);
    perform set_config('role', 'authenticated', true);
  end $f$;

  insert into auth.users (id, email, email_confirmed_at, aud, role) values
    (v_o, 'owner@lucento-test.invalid', now(), 'authenticated', 'authenticated'),
    (v_p, 'partner@lucento-test.invalid', now(), 'authenticated', 'authenticated'),
    (v_x, 'stranger@lucento-test.invalid', now(), 'authenticated', 'authenticated'),
    (v_q, 'second@lucento-test.invalid', now(), 'authenticated', 'authenticated'),
    (v_u, 'unverified@lucento-test.invalid', null, 'authenticated', 'authenticated');

  -- The owner's ledger, written as the owner through RLS (the app's normal path).
  perform pg_temp.as_user(v_o, 'owner@lucento-test.invalid');
  insert into public.wallets (id, user_id, name, type, currency_code, opening_balance_minor, created_at, updated_at)
    values (v_w1, v_o, 'Household', 'BANK', 'PHP', 0, now(), now());
  insert into public.categories (id, user_id, name, kind, icon_key, created_at, updated_at)
    values (v_c1, v_o, 'Groceries', 'EXPENSE', 'cart', now(), now());
  insert into public.budgets (id, user_id, category_id, currency_code, limit_minor, created_at, updated_at)
    values ('b1-' || v_t1, v_o, v_c1, 'PHP', 500000, now(), now());
  insert into public.transactions (id, user_id, type, amount_minor, currency_code, wallet_id, category_id, occurred_on, note, created_at, updated_at)
    values (v_t1, v_o, 'EXPENSE', 125000, 'PHP', v_w1, v_c1, 20396, 'groceries', now() - interval '1 hour', now() - interval '1 hour');
  execute 'reset role';
  select created_by, revision into v_cb, v_rev from public.transactions where id = v_t1;
  r := r || (case when v_cb = v_o then 'ok  K1 owner insert stamped created_by=owner' else 'FAIL K1 created_by=' || coalesce(v_cb::text,'null') end);

  -- A stranger's own ledger.
  perform pg_temp.as_user(v_x, 'stranger@lucento-test.invalid');
  insert into public.wallets (id, user_id, name, type, currency_code, opening_balance_minor, created_at, updated_at)
    values (v_wx, v_x, 'Mine', 'CASH', 'PHP', 0, now(), now());
  execute 'reset role';

  -- ======================================================================== before any space
  perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
  select count(*) into v_n from public.transactions where user_id = v_o;
  execute 'reset role';
  r := r || (case when v_n = 0 then 'ok  B1 non-member sees 0 of the owner''s transactions' else 'FAIL B1 saw ' || v_n end);

  begin
    perform pg_temp.as_user(v_o, 'owner@lucento-test.invalid');
    perform public.create_space_invite('partner@lucento-test.invalid');
    r := r || 'FAIL B3 invite without a space accepted'::text;
  exception when others then r := r || ('ok  B3 invite before creating a space: ' || sqlerrm); end;
  execute 'reset role';

  perform pg_temp.as_user(v_o, 'owner@lucento-test.invalid');
  perform public.create_shared_space('Olivia');
  execute 'reset role';
  r := r || 'ok  B4 owner created a Shared Space'::text;

  begin
    perform pg_temp.as_user(v_o, 'owner@lucento-test.invalid');
    perform public.create_space_invite('not-an-email');
    r := r || 'FAIL B5 invalid email accepted'::text;
  exception when others then r := r || ('ok  B5 invalid email: ' || sqlerrm); end;
  execute 'reset role';

  begin
    perform pg_temp.as_user(v_o, 'owner@lucento-test.invalid');
    perform public.create_space_invite('Owner@Lucento-Test.invalid');
    r := r || 'FAIL B6 self-invite accepted'::text;
  exception when others then r := r || ('ok  B6 self-invite: ' || sqlerrm); end;
  execute 'reset role';

  perform pg_temp.as_user(v_o, 'owner@lucento-test.invalid');
  v_inv := public.create_space_invite('  Partner@Lucento-Test.INVALID ');
  execute 'reset role';
  select invitee_email, extract(day from expires_at - created_at)::int into v_txt, v_n from public.space_invites where id = v_inv;
  r := r || (case when v_txt = 'partner@lucento-test.invalid' and v_n = 7 then 'ok  B7 invite normalised to lowercase, expires in 7 days'
                  else 'FAIL B7 email=' || v_txt || ' days=' || v_n end);

  begin
    perform pg_temp.as_user(v_o, 'owner@lucento-test.invalid');
    perform public.create_space_invite('partner@lucento-test.invalid');
    r := r || 'FAIL B9 duplicate invitation accepted'::text;
  exception when others then r := r || ('ok  B9 duplicate invitation: ' || sqlerrm); end;
  execute 'reset role';

  begin
    perform pg_temp.as_user(v_o, 'owner@lucento-test.invalid');
    perform public.create_space_invite('second@lucento-test.invalid');
    r := r || 'FAIL B8 second invitation over capacity accepted'::text;
  exception when others then r := r || ('ok  B8 pending invitation fills the one place: ' || sqlerrm); end;
  execute 'reset role';

  perform pg_temp.as_user(v_x, 'stranger@lucento-test.invalid');
  v_j := public.get_my_shared_space();
  execute 'reset role';
  r := r || (case when jsonb_array_length(v_j->'received') = 0 and (v_j->>'role') is null then 'ok  B10 stranger sees no invitation and no role'
                  else 'FAIL B10 ' || v_j::text end);

  perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
  v_j := public.get_my_shared_space();
  execute 'reset role';
  r := r || (case when jsonb_array_length(v_j->'received') = 1 and v_j->'received'->0->>'inviter_label' = 'Olivia'
                       and v_j->'received'->0->>'inviter_email' = 'owner@lucento-test.invalid'
                  then 'ok  B11 invitee sees the invitation with the inviter''s name and email'
                  else 'FAIL B11 ' || v_j::text end);

  -- Direct table reads of invitations: the invitee sees only their pending one, a stranger none.
  perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
  select count(*) into v_n from public.space_invites;
  execute 'reset role';
  r := r || (case when v_n = 1 then 'ok  E0 invitee reads exactly their own pending invitation' else 'FAIL E0 invitee saw ' || v_n end);
  perform pg_temp.as_user(v_x, 'stranger@lucento-test.invalid');
  select count(*) into v_n from public.space_invites;
  execute 'reset role';
  r := r || (case when v_n = 0 then 'ok  E0b stranger reads no invitation' else 'FAIL E0b stranger saw ' || v_n end);

  begin
    perform pg_temp.as_user(v_x, 'stranger@lucento-test.invalid');
    perform public.accept_space_invite(v_inv, 'X');
    r := r || 'FAIL B13 stranger accepted someone else''s invitation'::text;
  exception when others then r := r || ('ok  B13 wrong account can''t accept: ' || sqlerrm); end;
  execute 'reset role';

  -- Unverified email: a second space (owned by Q) invites the unverified user.
  perform pg_temp.as_user(v_q, 'second@lucento-test.invalid');
  perform public.create_shared_space('Quinn');
  v_inv2 := public.create_space_invite('unverified@lucento-test.invalid');
  execute 'reset role';
  begin
    perform pg_temp.as_user(v_u, 'unverified@lucento-test.invalid');
    perform public.accept_space_invite(v_inv2, 'U');
    r := r || 'FAIL B12 unverified email accepted an invitation'::text;
  exception when others then r := r || ('ok  B12 unverified email can''t accept: ' || sqlerrm); end;
  execute 'reset role';
  perform pg_temp.as_user(v_u, 'unverified@lucento-test.invalid');
  v_j := public.get_my_shared_space();
  select count(*) into v_n from public.space_invites;
  execute 'reset role';
  r := r || (case when jsonb_array_length(v_j->'received') = 0 and v_n = 0 then 'ok  B12b unverified email sees no invitation (RPC and table)'
                  else 'FAIL B12b ' || v_j::text || ' rows=' || v_n end);

  -- ======================================================================== join
  perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
  v_txt := public.accept_space_invite(v_inv, 'Pat')::text;
  execute 'reset role';
  -- The partner's key is taken by nothing before this acceptance; both people's keys must now be held.
  select count(*) into v_n from pg_locks l
    join (values (lucento_private.space_lock(v_p)), (lucento_private.space_lock(v_o))) as keys(k)
      on l.classid::bigint = ((keys.k >> 32) & 4294967295) and l.objid::bigint = (keys.k & 4294967295)
   where l.locktype = 'advisory' and l.objsubid = 1 and (l.pid = pg_backend_pid() or pg_backend_pid() = 0);
  r := r || (case when v_txt = v_o::text then 'ok  B14 partner accepted; returns the owner id (the Shared Space id)' else 'FAIL B14 ' || v_txt end);
  r := r || (case when v_n = 2 then 'ok  B14b acceptance holds both the owner''s and the partner''s advisory locks until commit' else 'FAIL B14b matching locks=' || v_n end);
  select status into v_txt from public.space_invites where id = v_inv;
  r := r || (case when v_txt = 'accepted' then 'ok  B14c invitation marked accepted in the same transaction' else 'FAIL B14c ' || v_txt end);

  begin
    perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
    perform public.accept_space_invite(v_inv, 'Pat');
    r := r || 'FAIL B15 double acceptance succeeded'::text;
  exception when others then r := r || ('ok  B15 accepting twice: ' || sqlerrm); end;
  execute 'reset role';

  begin
    perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
    perform public.create_shared_space('Pat''s space');
    r := r || 'FAIL B16 partner created their own space'::text;
  exception when others then r := r || ('ok  B16 a Partner can''t own a space: ' || sqlerrm); end;
  execute 'reset role';

  begin
    perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
    perform public.create_space_invite('someone@lucento-test.invalid');
    r := r || 'FAIL B17 partner invited someone'::text;
  exception when others then r := r || ('ok  B17 a Partner can''t invite: ' || sqlerrm); end;
  execute 'reset role';

  begin
    perform pg_temp.as_user(v_o, 'owner@lucento-test.invalid');
    perform public.create_space_invite('second@lucento-test.invalid');
    r := r || 'FAIL B19 invite beyond capacity accepted'::text;
  exception when others then r := r || ('ok  B19 one Partner fills the space: ' || sqlerrm); end;
  execute 'reset role';

  -- ======================================================================== partner access to the shared ledger
  perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
  select (select count(*) from public.wallets where user_id = v_o) + (select count(*) from public.categories where user_id = v_o)
       + (select count(*) from public.transactions where user_id = v_o) + (select count(*) from public.budgets where user_id = v_o)
    into v_n;
  execute 'reset role';
  r := r || (case when v_n = 4 then 'ok  C1 partner reads the owner''s wallets, categories, transactions and budgets' else 'FAIL C1 saw ' || v_n end);

  perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
  select count(*) into v_n from public.wallets where user_id = v_x;
  execute 'reset role';
  r := r || (case when v_n = 0 then 'ok  C1b partner still sees nothing of an unrelated account' else 'FAIL C1b saw ' || v_n end);

  perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
  insert into public.transactions (id, user_id, type, amount_minor, currency_code, wallet_id, category_id, occurred_on, note, created_at, updated_at, created_by, updated_by)
    values (v_t2, v_o, 'EXPENSE', 30000, 'PHP', v_w1, v_c1, 20397, 'coffee', now(), now(), v_x, v_x);
  execute 'reset role';
  select created_by, updated_by into v_cb, v_ub from public.transactions where id = v_t2;
  r := r || (case when v_cb = v_p and v_ub is null then 'ok  C2 partner adds a transaction; forged created_by/updated_by replaced by the partner'
                  else 'FAIL C2 created_by=' || coalesce(v_cb::text, 'null') || ' updated_by=' || coalesce(v_ub::text, 'null') end);

  begin
    perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
    insert into public.transactions (id, user_id, type, amount_minor, currency_code, wallet_id, occurred_on, note, created_at, updated_at)
      values ('tx-' || v_t1, v_x, 'EXPENSE', 100, 'PHP', v_wx, 20397, '', now(), now());
    r := r || 'FAIL C3 partner wrote into an unrelated account'::text;
  exception when others then r := r || ('ok  C3 partner can''t write into an unrelated account: ' || sqlstate); end;
  execute 'reset role';

  perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
  update public.transactions set note = 'groceries (edited by Pat)', updated_at = now(), base_revision = v_rev, created_by = v_p where id = v_t1;
  get diagnostics v_n = row_count;
  execute 'reset role';
  select created_by, updated_by, revision into v_cb, v_ub, v_rev from public.transactions where id = v_t1;
  r := r || (case when v_n = 1 and v_cb = v_o and v_ub = v_p and v_rev = 2
                  then 'ok  C4 partner edits the owner''s transaction; created_by kept (owner), updated_by=partner, revision 2'
                  else 'FAIL C4 rows=' || v_n || ' cb=' || coalesce(v_cb::text,'null') || ' ub=' || coalesce(v_ub::text,'null') || ' rev=' || v_rev end);

  -- A stale edit (based on revision 1, older edit time): skipped by the conflict rule, no error.
  perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
  update public.transactions set note = 'stale', updated_at = now() - interval '2 hours', base_revision = 1 where id = v_t1;
  get diagnostics v_n = row_count;
  execute 'reset role';
  select note into v_txt from public.transactions where id = v_t1;
  r := r || (case when v_n = 0 and v_txt = 'groceries (edited by Pat)' then 'ok  C7 stale partner edit skipped silently (no error), newer edit kept'
                  else 'FAIL C7 rows=' || v_n || ' note=' || v_txt end);

  -- The app's push: INSERT ... ON CONFLICT (id) DO UPDATE, as a causal successor.
  perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
  insert into public.transactions (id, user_id, type, amount_minor, currency_code, wallet_id, category_id, occurred_on, note, created_at, updated_at, base_revision)
    values (v_t1, v_o, 'EXPENSE', 130000, 'PHP', v_w1, v_c1, 20396, 'groceries', now() - interval '1 hour', now(), 2)
    on conflict (id) do update set amount_minor = excluded.amount_minor, note = excluded.note, updated_at = excluded.updated_at, base_revision = excluded.base_revision;
  get diagnostics v_n = row_count;
  execute 'reset role';
  select amount_minor, revision, created_by into v_rev, v_n, v_cb from public.transactions where id = v_t1;
  r := r || (case when v_rev = 130000 and v_n = 3 and v_cb = v_o then 'ok  C12 partner upsert (the sync push) updates an existing row; revision 3; creator unchanged'
                  else 'FAIL C12 amount=' || v_rev || ' rev=' || v_n end);

  -- A lost-reply retry of that same push (same base 2, same edit time): refused by the conflict rule, no error.
  perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
  insert into public.transactions (id, user_id, type, amount_minor, currency_code, wallet_id, category_id, occurred_on, note, created_at, updated_at, base_revision)
    select id, user_id, type, 999, currency_code, wallet_id, category_id, occurred_on, note, created_at, updated_at, 2 from public.transactions where id = v_t1
    on conflict (id) do update set amount_minor = excluded.amount_minor, updated_at = excluded.updated_at, base_revision = excluded.base_revision;
  get diagnostics v_n = row_count;
  execute 'reset role';
  select amount_minor into v_rev from public.transactions where id = v_t1;
  r := r || (case when v_n = 0 and v_rev = 130000 then 'ok  C12b replayed push (lost reply) is a no-op: no duplicate, no error'
                  else 'FAIL C12b rows=' || v_n || ' amount=' || v_rev end);

  begin
    perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
    update public.transactions set user_id = v_p where id = v_t2;
    r := r || 'FAIL C14 partner moved a transaction out of the space'::text;
  exception when others then r := r || ('ok  C14 user_id can''t change: ' || sqlerrm); end;
  execute 'reset role';

  perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
  select revision into v_rev from public.transactions where id = v_t2;
  update public.transactions set deleted_at = now(), updated_at = now(), base_revision = v_rev where id = v_t2;
  get diagnostics v_n = row_count;
  execute 'reset role';
  select (deleted_at is not null), updated_by into v_bool, v_ub from public.transactions where id = v_t2;
  r := r || (case when v_n = 1 and v_bool and v_ub = v_p then 'ok  C6 partner soft-deletes a transaction (the app''s delete); updated_by=partner'
                  else 'FAIL C6 rows=' || v_n end);

  perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
  delete from public.transactions where id = v_t1;
  get diagnostics v_n = row_count;
  execute 'reset role';
  r := r || (case when v_n = 0 then 'ok  C8 hard DELETE stays owner-only (0 rows for the partner)' else 'FAIL C8 deleted ' || v_n end);

  perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
  update public.wallets set name = 'Renamed by partner', updated_at = now(), base_revision = revision where id = v_w1;
  get diagnostics v_n = row_count;
  update public.wallets set classification = 'PARTNER', updated_at = now(), base_revision = revision where id = v_w1;
  get diagnostics v_n2 = row_count;
  v_n := v_n + v_n2;
  update public.categories set name = 'x', updated_at = now(), base_revision = revision where id = v_c1;
  get diagnostics v_n2 = row_count;
  v_n := v_n + v_n2;
  update public.budgets set limit_minor = 1, updated_at = now(), base_revision = revision where user_id = v_o;
  get diagnostics v_n2 = row_count;
  v_n := v_n + v_n2;
  delete from public.wallets where id = v_w1;
  get diagnostics v_n2 = row_count;
  v_n := v_n + v_n2;
  execute 'reset role';
  r := r || (case when v_n = 0 then 'ok  C9 partner updates/deletes of wallets (incl. classification), categories, budgets affect 0 rows'
                  else 'FAIL C9 rows=' || v_n end);

  begin
    perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
    insert into public.wallets (id, user_id, name, type, currency_code, opening_balance_minor, created_at, updated_at)
      values ('pw-' || v_t1, v_o, 'Sneaky', 'CASH', 'PHP', 0, now(), now());
    r := r || 'FAIL C10 partner created a wallet in the space'::text;
  exception when others then r := r || ('ok  C10 partner can''t create a wallet: ' || sqlstate); end;
  execute 'reset role';

  begin
    perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
    insert into public.categories (id, user_id, name, kind, icon_key, created_at, updated_at)
      values ('pc-' || v_t1, v_o, 'Sneaky', 'EXPENSE', 'other', now(), now());
    r := r || 'FAIL C10b partner created a category'::text;
  exception when others then r := r || ('ok  C10b partner can''t create a category: ' || sqlstate); end;
  execute 'reset role';

  begin
    perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
    insert into public.budgets (id, user_id, category_id, currency_code, limit_minor, created_at, updated_at)
      values ('pb-' || v_t1, v_o, v_c1, 'PHP', 1, now(), now());
    r := r || 'FAIL C10c partner created a budget'::text;
  exception when others then r := r || ('ok  C10c partner can''t create a budget: ' || sqlstate); end;
  execute 'reset role';

  -- What a mixed batch would do: a wallet upsert from a partner fails the whole statement (the app never sends one).
  begin
    perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
    insert into public.wallets (id, user_id, name, type, currency_code, opening_balance_minor, created_at, updated_at)
      values (v_w1, v_o, 'Upserted', 'BANK', 'PHP', 0, now(), now())
      on conflict (id) do update set name = excluded.name, updated_at = excluded.updated_at;
    get diagnostics v_n = row_count;
    execute 'reset role';
    select name into v_txt from public.wallets where id = v_w1;
    r := r || (case when v_n = 0 and v_txt = 'Household' then 'ok  C13 partner wallet upsert changed nothing (skipped, no error)'
                    else 'FAIL C13 partner wallet upsert rows=' || v_n || ' name=' || v_txt end);
  exception when others then r := r || ('ok  C13 partner wallet upsert refused as a whole statement: ' || sqlstate || ' ' || sqlerrm); end;
  execute 'reset role';

  -- Wallet classification: the owner sets it; an unknown value is refused.
  perform pg_temp.as_user(v_o, 'owner@lucento-test.invalid');
  update public.wallets set classification = 'PARTNER', updated_at = now(), base_revision = revision where id = v_w1;
  get diagnostics v_n = row_count;
  execute 'reset role';
  r := r || (case when v_n = 1 then 'ok  L1 owner classifies a wallet' else 'FAIL L1 rows=' || v_n end);
  begin
    perform pg_temp.as_user(v_o, 'owner@lucento-test.invalid');
    update public.wallets set classification = 'PRIVATE', updated_at = now(), base_revision = revision where id = v_w1;
    r := r || 'FAIL L2 unknown classification accepted'::text;
  exception when others then r := r || ('ok  L2 unknown classification refused: ' || sqlstate); end;
  execute 'reset role';

  -- ======================================================================== strangers, anon, membership tables
  perform pg_temp.as_user(v_x, 'stranger@lucento-test.invalid');
  select (select count(*) from public.wallets where user_id = v_o) + (select count(*) from public.transactions where user_id = v_o)
       + (select count(*) from public.space_members) + (select count(*) from public.shared_spaces where owner_id = v_o)
    into v_n;
  update public.transactions set note = 'x', base_revision = revision where id = v_t1;
  get diagnostics v_n2 = row_count;
  execute 'reset role';
  r := r || (case when v_n = 0 and v_n2 = 0 then 'ok  D1 stranger reads 0 rows of the space and updates 0' else 'FAIL D1 read=' || v_n || ' updated=' || v_n2 end);

  begin
    perform pg_temp.as_user(v_x, 'stranger@lucento-test.invalid');
    perform public.remove_space_partner(v_p);
    r := r || 'FAIL D4 stranger removed the partner'::text;
  exception when others then r := r || ('ok  D4 stranger can''t remove a partner: ' || sqlerrm); end;
  execute 'reset role';

  perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
  select count(*) into v_n from public.space_members;
  select count(*) into v_n2 from public.shared_spaces;
  execute 'reset role';
  r := r || (case when v_n = 1 and v_n2 = 1 then 'ok  E1 partner reads their membership and the space' else 'FAIL E1 members=' || v_n || ' spaces=' || v_n2 end);

  begin
    perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
    insert into public.space_members (owner_id, partner_id) values (v_q, v_p);
    r := r || 'FAIL E2 direct membership insert accepted'::text;
  exception when others then r := r || ('ok  E2 no direct membership insert: ' || sqlstate); end;
  execute 'reset role';

  begin
    perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
    delete from public.space_members where partner_id = v_p;
    r := r || 'FAIL E3 direct membership delete allowed'::text;
  exception when others then r := r || ('ok  E3 no direct membership delete: ' || sqlstate); end;
  execute 'reset role';

  begin
    perform pg_temp.as_user(v_o, 'owner@lucento-test.invalid');
    update public.space_invites set status = 'accepted' where owner_id = v_o;
    r := r || 'FAIL E4 direct invitation update allowed'::text;
  exception when others then r := r || ('ok  E4 no direct invitation update: ' || sqlstate); end;
  execute 'reset role';

  begin
    insert into public.space_members (owner_id, partner_id) values (v_q, v_p);
    r := r || 'FAIL E5 a second membership for the same person was stored'::text;
  exception when others then r := r || ('ok  E5 a person can be a Partner in only one space (unique constraint, even without the RPC): ' || sqlstate); end;

  begin
    perform set_config('request.jwt.claims', '{"role":"anon"}', true);
    perform set_config('role', 'anon', true);
    perform public.get_my_shared_space();
    r := r || 'FAIL J1 anon called get_my_shared_space'::text;
  exception when others then r := r || ('ok  J1 anon can''t call the RPCs: ' || sqlstate); end;
  execute 'reset role';

  begin
    perform set_config('request.jwt.claims', '{"role":"anon"}', true);
    perform set_config('role', 'anon', true);
    select count(*) into v_n from public.space_members;
    r := r || 'FAIL J2 anon read space_members'::text;
  exception when others then r := r || ('ok  J2 anon can''t read membership: ' || sqlstate); end;
  execute 'reset role';

  begin
    perform set_config('request.jwt.claims', '{"role":"anon"}', true);
    perform set_config('role', 'anon', true);
    select count(*) into v_n from public.transactions;
    r := r || 'FAIL J3 anon read transactions (' || v_n || ')';
  exception when others then r := r || ('ok  J3 anon can''t read transactions: ' || sqlstate); end;
  execute 'reset role';

  -- ======================================================================== the N-ready structure (limit raised to 2, rolled back)
  begin
    perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
    truncate public.space_members;
    r := r || 'FAIL J4 a signed-in user truncated space_members'::text;
  exception when others then r := r || ('ok  J4 no TRUNCATE on membership for signed-in users: ' || sqlstate); end;
  execute 'reset role';

  begin
    perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
    perform lucento_private.max_partners(v_o);
    r := r || 'FAIL J5 a signed-in user called an internal helper'::text;
  exception when others then r := r || ('ok  J5 internal helpers aren''t callable by users: ' || sqlstate); end;
  execute 'reset role';

  select count(*) into v_n from information_schema.routine_privileges
   where routine_schema in ('public', 'lucento_private') and grantee in ('anon', 'PUBLIC')
     and routine_name in ('get_my_shared_space', 'create_shared_space', 'set_my_space_label', 'create_space_invite',
       'revoke_space_invite', 'accept_space_invite', 'decline_space_invite', 'remove_space_partner', 'leave_shared_space',
       'close_shared_space', 'max_partners', 'space_lock', 'partner_spaces', 'verified_email', 'check_space_roles',
       'lucento_stamp_transaction_actor');
  r := r || (case when v_n = 0 then 'ok  J6 nothing Lucento Shared added is executable by anon or PUBLIC' else 'FAIL J6 grants=' || v_n end);

  create or replace function lucento_private.max_partners(p_owner uuid) returns integer language sql stable set search_path = '' as $$ select 2 $$;
  begin
    perform pg_temp.as_user(v_o, 'owner@lucento-test.invalid');
    v_inv3 := public.create_space_invite('second@lucento-test.invalid');
    execute 'reset role';
    -- Q owns a space with a pending invitation, so can't join until it is ended.
    begin
      perform pg_temp.as_user(v_q, 'second@lucento-test.invalid');
      perform public.accept_space_invite(v_inv3, 'Quinn');
      r := r || 'FAIL N1 an owner with a pending invitation joined another space'::text;
    exception when others then r := r || ('ok  N1 an Owner with a live space can''t become a Partner: ' || sqlerrm); end;
    execute 'reset role';
    perform pg_temp.as_user(v_q, 'second@lucento-test.invalid');
    perform public.revoke_space_invite(v_inv2);
    perform public.accept_space_invite(v_inv3, 'Quinn');
    execute 'reset role';
    select count(*) into v_n from public.space_members where owner_id = v_o;
    select count(*) into v_n2 from public.shared_spaces where owner_id = v_q;
    r := r || (case when v_n = 2 and v_n2 = 0 then 'ok  N2 limit 2: a second Partner joins (their empty space closed); 2 membership rows'
                    else 'FAIL N2 members=' || v_n || ' q_space=' || v_n2 end);
    perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
    select count(*) into v_n from public.space_members;
    execute 'reset role';
    r := r || (case when v_n = 2 then 'ok  N3 each Partner sees the fellow member (for names on attribution)' else 'FAIL N3 saw ' || v_n end);
    perform pg_temp.as_user(v_o, 'owner@lucento-test.invalid');
    perform public.remove_space_partner(v_q);
    execute 'reset role';
    perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
    select count(*) into v_n from public.transactions where user_id = v_o;
    execute 'reset role';
    perform pg_temp.as_user(v_q, 'second@lucento-test.invalid');
    select count(*) into v_n2 from public.transactions where user_id = v_o;
    execute 'reset role';
    r := r || (case when v_n = 2 and v_n2 = 0 then 'ok  N4 removing one Partner leaves the other''s access intact'
                    else 'FAIL N4 p=' || v_n || ' q=' || v_n2 end);
  end;
  create or replace function lucento_private.max_partners(p_owner uuid) returns integer language sql stable set search_path = '' as $$ select 1 $$;

  -- ======================================================================== removal, re-invite, decline, leave, expiry, close
  perform pg_temp.as_user(v_o, 'owner@lucento-test.invalid');
  perform public.remove_space_partner(v_p);
  execute 'reset role';
  perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
  select count(*) into v_n from public.transactions where user_id = v_o;
  update public.transactions set note = 'after removal', base_revision = revision where id = v_t1;
  get diagnostics v_n2 = row_count;
  v_j := public.get_my_shared_space();
  execute 'reset role';
  r := r || (case when v_n = 0 and v_n2 = 0 and (v_j->>'role') is null then 'ok  F1 removal: the partner''s reads and writes stop immediately; role cleared'
                  else 'FAIL F1 read=' || v_n || ' updated=' || v_n2 || ' ' || v_j::text end);
  begin
    perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
    insert into public.transactions (id, user_id, type, amount_minor, currency_code, wallet_id, occurred_on, note, created_at, updated_at)
      values ('late-' || v_t1, v_o, 'EXPENSE', 100, 'PHP', v_w1, 20398, 'offline entry', now(), now());
    r := r || 'FAIL F2 removed partner inserted'::text;
  exception when others then r := r || ('ok  F2 removed partner''s late insert refused: ' || sqlstate); end;
  execute 'reset role';
  select count(*) into v_n from public.transactions where id = v_t2 and created_by = v_p;
  r := r || (case when v_n = 1 then 'ok  F3 what the partner added stays in the space' else 'FAIL F3' end);

  perform pg_temp.as_user(v_o, 'owner@lucento-test.invalid');
  v_inv := public.create_space_invite('partner@lucento-test.invalid');
  execute 'reset role';
  perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
  perform public.decline_space_invite(v_inv);
  execute 'reset role';
  begin
    perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
    perform public.accept_space_invite(v_inv, 'Pat');
    r := r || 'FAIL G0 declined invitation accepted'::text;
  exception when others then r := r || ('ok  G0 removal frees the place; a declined invitation can''t be accepted: ' || sqlerrm); end;
  execute 'reset role';

  perform pg_temp.as_user(v_o, 'owner@lucento-test.invalid');
  v_inv := public.create_space_invite('partner@lucento-test.invalid');
  perform public.revoke_space_invite(v_inv);
  execute 'reset role';
  begin
    perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
    perform public.accept_space_invite(v_inv, 'Pat');
    r := r || 'FAIL H0 revoked invitation accepted'::text;
  exception when others then r := r || ('ok  H0 a revoked invitation can''t be accepted: ' || sqlerrm); end;
  execute 'reset role';

  perform pg_temp.as_user(v_o, 'owner@lucento-test.invalid');
  v_inv := public.create_space_invite('partner@lucento-test.invalid');
  execute 'reset role';
  update public.space_invites set expires_at = now() - interval '1 minute', created_at = now() - interval '8 days' where id = v_inv;
  begin
    perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
    perform public.accept_space_invite(v_inv, 'Pat');
    r := r || 'FAIL H1 expired invitation accepted'::text;
  exception when others then r := r || ('ok  H1 an expired invitation can''t be accepted: ' || sqlerrm); end;
  execute 'reset role';
  perform pg_temp.as_user(v_o, 'owner@lucento-test.invalid');
  v_j := public.get_my_shared_space();
  v_inv := public.create_space_invite('partner@lucento-test.invalid');
  execute 'reset role';
  r := r || (case when exists (select 1 from jsonb_array_elements(v_j->'invites') e where e->>'status' = 'expired')
                  then 'ok  H2 the owner sees it as expired, and can invite again'
                  else 'FAIL H2 ' || (v_j->'invites')::text end);

  perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
  perform public.accept_space_invite(v_inv, 'Pat');
  v_txt := public.leave_shared_space()::text;
  select count(*) into v_n from public.transactions where user_id = v_o;
  execute 'reset role';
  r := r || (case when v_txt = v_o::text and v_n = 0 then 'ok  G1 partner leaves; access ends at once' else 'FAIL G1 ' || v_txt || ' read=' || v_n end);

  perform pg_temp.as_user(v_o, 'owner@lucento-test.invalid');
  v_inv := public.create_space_invite('partner@lucento-test.invalid');
  execute 'reset role';
  perform pg_temp.as_user(v_p, 'partner@lucento-test.invalid');
  perform public.accept_space_invite(v_inv, 'Pat');
  execute 'reset role';
  begin
    perform pg_temp.as_user(v_o, 'owner@lucento-test.invalid');
    perform public.close_shared_space();
    r := r || 'FAIL I1 closed a space that has a partner'::text;
  exception when others then r := r || ('ok  I1 a space with a Partner can''t be closed: ' || sqlerrm); end;
  execute 'reset role';
  perform pg_temp.as_user(v_o, 'owner@lucento-test.invalid');
  perform public.remove_space_partner(v_p);
  perform public.close_shared_space();
  v_j := public.get_my_shared_space();
  select count(*) into v_n from public.transactions where user_id = v_o;
  execute 'reset role';
  r := r || (case when (v_j->>'role') is null and v_n = 2 then 'ok  I2 owner closes the empty space; their ledger is untouched'
                  else 'FAIL I2 ' || v_j::text || ' tx=' || v_n end);

  -- Solo regression: a user with no space reads and writes exactly as before.
  perform pg_temp.as_user(v_x, 'stranger@lucento-test.invalid');
  insert into public.transactions (id, user_id, type, amount_minor, currency_code, wallet_id, occurred_on, note, created_at, updated_at)
    values ('solo-' || v_t1, v_x, 'INCOME', 500, 'PHP', v_wx, 20398, '', now(), now());
  select count(*) into v_n from public.transactions where user_id = v_x;
  update public.wallets set name = 'Still mine', updated_at = now(), base_revision = revision where id = v_wx;
  get diagnostics v_n2 = row_count;
  execute 'reset role';
  select created_by into v_cb from public.transactions where id = 'solo-' || v_t1;
  r := r || (case when v_n = 1 and v_n2 = 1 and v_cb = v_x then 'ok  S1 solo account unchanged (own reads and writes; created_by = self)'
                  else 'FAIL S1 read=' || v_n || ' updated=' || v_n2 end);

  raise exception E'VERIFY %/% failed\n%',
    (select count(*) from unnest(r) x where x like 'FAIL%'), array_length(r, 1), array_to_string(r, E'\n');
end
$verify$;
