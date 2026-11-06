-- Lucento Shared (docs/shared/ARCHITECTURE_PROPOSAL.md): an Owner's financial environment shared with Partners.
--
-- The Shared Space id IS the Owner's auth.users id — the user_id every synced row already carries — so no financial
-- row is rewritten and solo accounts see no change. A Partner is admitted by a membership row. The owner policies on
-- the seven synced tables are widened by exactly that membership:
--   * Partners SELECT every synced table of the space (wallets, categories, transactions, budgets, goals,
--     goal_contributions, recurring), soft-deleted rows included, so sync can pull them.
--   * Partners INSERT and UPDATE transactions of the space. The app's delete is a soft delete (an UPDATE setting
--     deleted_at, see docs/SYNC.md), so this is full add / edit / delete collaboration on transactions.
--   * Everything else — wallets, categories, budgets, goals, contributions, schedules — stays owner-only, and hard
--     DELETE stays owner-only on every table (sync never hard-deletes).
-- Who wrote a transaction is stamped by the database (created_by / updated_by), never taken from the client.
--
-- Membership is one row per Partner (primary key (owner_id, partner_id)), so a later Partner limit above one is a
-- change to lucento_private.max_partners() only — not a schema change. The limit is 1 in this release; it is enforced
-- in accept_space_invite / create_space_invite under advisory locks, the only ways a membership or invitation row can
-- be created (there are no INSERT grants on the three new tables).
--
-- Strictly additive: one private helper schema, three tables, RPCs, two nullable columns on transactions, one on
-- wallets, and new Partner policies beside the existing owner policies. Nothing is dropped, revoked or rewritten, and
-- existing rows keep every value. Privilege hardening is the next migration (20261105120100_lucento_shared_privileges.sql).

-- ------------------------------------------------------------------------------------------------ private helpers
-- Not in an exposed schema: PostgREST can't call these. Only authenticated gets USAGE on the schema (policies run with
-- the querying role's privileges); each helper only ever answers about the caller.

create schema if not exists lucento_private;
grant usage on schema lucento_private to authenticated;

-- The Partner limit per Shared Space: the future entitlement hook (docs/shared/PRODUCT_SPEC.md "max_partners").
-- One in this release, for every space. Nothing else in the schema assumes a single Partner.
create or replace function lucento_private.max_partners(p_owner uuid)
returns integer
language sql
stable
set search_path = ''
as $$
  select 1
$$;

-- Advisory-lock key for every membership change that involves this person (as owner or as partner).
create or replace function lucento_private.space_lock(p_user uuid)
returns bigint
language sql
immutable
set search_path = ''
as $$
  select pg_catalog.hashtextextended('lucento_space:' || p_user::text, 0)
$$;

-- tables first: the helpers below read them

create table if not exists public.shared_spaces (
  owner_id uuid primary key references auth.users (id) on delete cascade,
  owner_label text not null default '' check (char_length(owner_label) <= 60),
  created_at timestamptz not null default now()
);

create table if not exists public.space_members (
  owner_id uuid not null references public.shared_spaces (owner_id) on delete cascade,
  partner_id uuid not null references auth.users (id) on delete cascade,
  partner_label text not null default '' check (char_length(partner_label) <= 60),
  partner_email text not null default '',
  joined_at timestamptz not null default now(),
  primary key (owner_id, partner_id),
  -- A person is a Partner in at most one Shared Space.
  constraint space_members_partner_once unique (partner_id),
  constraint space_members_not_owner check (owner_id <> partner_id)
);

create table if not exists public.space_invites (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references public.shared_spaces (owner_id) on delete cascade,
  invitee_email text not null check (char_length(invitee_email) between 3 and 254),
  inviter_label text not null default '',
  inviter_email text not null default '',
  status text not null default 'pending' check (status in ('pending', 'accepted', 'declined', 'revoked', 'expired')),
  created_at timestamptz not null default now(),
  expires_at timestamptz not null,
  responded_at timestamptz
);
create unique index if not exists space_invites_one_pending_per_email
  on public.space_invites (owner_id, invitee_email) where status = 'pending';
create index if not exists space_invites_owner on public.space_invites (owner_id);
create index if not exists space_invites_invitee_pending on public.space_invites (invitee_email) where status = 'pending';

-- The Shared Spaces the caller belongs to as a Partner (their Owners' ids). Security definer so the policies of
-- space_members can use it without recursing into themselves; it only ever reveals the caller's own memberships.
create or replace function lucento_private.partner_spaces()
returns uuid[]
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce(array_agg(m.owner_id), '{}'::uuid[])
  from public.space_members m
  where m.partner_id = (select auth.uid())
$$;

-- The caller's email, lowercased, only when it is confirmed (an unconfirmed email never matches an invitation).
create or replace function lucento_private.verified_email()
returns text
language sql
stable
security definer
set search_path = ''
as $$
  select lower(u.email) from auth.users u where u.id = (select auth.uid()) and u.email_confirmed_at is not null
$$;

grant execute on function lucento_private.partner_spaces() to authenticated;
grant execute on function lucento_private.verified_email() to authenticated;

-- Defense in depth for "an Owner is never a Partner elsewhere": the RPCs check it under locks; these refuse a row that
-- would break it whatever wrote it.
create or replace function lucento_private.check_space_roles()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if tg_table_name = 'shared_spaces' then
    if exists (select 1 from public.space_members m where m.partner_id = new.owner_id) then
      raise exception 'lucento_space:already_partner';
    end if;
  elsif exists (select 1 from public.shared_spaces s where s.owner_id = new.partner_id) then
    raise exception 'lucento_space:owns_space';
  end if;
  return new;
end;
$$;

create trigger trg_shared_spaces_roles before insert on public.shared_spaces
  for each row execute function lucento_private.check_space_roles();
create trigger trg_space_members_roles before insert on public.space_members
  for each row execute function lucento_private.check_space_roles();

-- ------------------------------------------------------------------------------------------------ access to the new tables
-- Read-only for signed-in users, scoped by RLS (no INSERT/UPDATE/DELETE policy exists, so RLS refuses every direct
-- write); every write goes through the RPCs below.

alter table public.shared_spaces enable row level security;
alter table public.space_members enable row level security;
alter table public.space_invites enable row level security;

grant select on table public.shared_spaces to authenticated;
grant select on table public.space_members to authenticated;
grant select on table public.space_invites to authenticated;

create policy "shared_spaces_select_member" on public.shared_spaces for select to authenticated
  using (owner_id = (select auth.uid()) or owner_id = any ((select lucento_private.partner_spaces())::uuid[]));
create policy "space_members_select_member" on public.space_members for select to authenticated
  using (owner_id = (select auth.uid()) or owner_id = any ((select lucento_private.partner_spaces())::uuid[]));
create policy "space_invites_select_owner_or_invitee" on public.space_invites for select to authenticated
  using (
    owner_id = (select auth.uid())
    or (status = 'pending' and invitee_email = (select lucento_private.verified_email()))
  );

-- ------------------------------------------------------------------------------------------------ financial tables

-- Attribution: who added and who last changed a transaction. Plain uuids, no foreign key: history stays truthful
-- after a Partner's account is deleted. Nullable; rows written before this migration keep null (the Owner's own).
alter table public.transactions
  add column if not exists created_by uuid,
  add column if not exists updated_by uuid;

-- Stamped from the authenticated session on every write; any client value is overwritten. created_by never changes
-- after the insert. Fires after trg_transactions_lww (BEFORE triggers run alphabetically), so an update the conflict
-- rule skips is never stamped.
create or replace function public.lucento_stamp_transaction_actor()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if tg_op = 'INSERT' then
    new.created_by := auth.uid();
    new.updated_by := null;
  else
    new.created_by := old.created_by;
    new.updated_by := coalesce(auth.uid(), old.updated_by);
  end if;
  return new;
end;
$$;

create trigger trg_transactions_stamp_actor before insert or update on public.transactions
  for each row execute function public.lucento_stamp_transaction_actor();

-- Wallet classification: an organizational label (Shared, the Owner's personal, a Partner's personal). Never an access
-- rule. Null = never classified (every existing wallet), shown as Shared inside a Shared Space. Wallets stay
-- owner-only to write, so only the Owner sets it. Builds that don't know the column never send it, so an upsert from
-- them leaves it as it is.
alter table public.wallets add column if not exists classification text;
alter table public.wallets add constraint wallets_classification_known
  check (classification is null or classification in ('SHARED', 'OWNER', 'PARTNER'));

-- Partner policies sit beside each table's existing owner policy; permissive policies are OR-ed, so a row is visible
-- (or writable) to its owner as before, and to a Partner of the owner's Shared Space. The owner policies are untouched.
create policy "wallets_select_partner" on public.wallets for select to authenticated
  using (user_id = any ((select lucento_private.partner_spaces())::uuid[]));

create policy "categories_select_partner" on public.categories for select to authenticated
  using (user_id = any ((select lucento_private.partner_spaces())::uuid[]));

create policy "transactions_select_partner" on public.transactions for select to authenticated
  using (user_id = any ((select lucento_private.partner_spaces())::uuid[]));

create policy "budgets_select_partner" on public.budgets for select to authenticated
  using (user_id = any ((select lucento_private.partner_spaces())::uuid[]));

create policy "goals_select_partner" on public.goals for select to authenticated
  using (user_id = any ((select lucento_private.partner_spaces())::uuid[]));

create policy "goal_contributions_select_partner" on public.goal_contributions for select to authenticated
  using (user_id = any ((select lucento_private.partner_spaces())::uuid[]));

create policy "recurring_select_partner" on public.recurring for select to authenticated
  using (user_id = any ((select lucento_private.partner_spaces())::uuid[]));

-- Transactions: Partners add, edit and (soft-)delete within the space. user_id can never change
-- (lucento_reject_stale_update), so a row can't be moved out of the space either.
create policy "transactions_insert_partner" on public.transactions for insert to authenticated
  with check (user_id = any ((select lucento_private.partner_spaces())::uuid[]));

create policy "transactions_update_partner" on public.transactions for update to authenticated
  using (user_id = any ((select lucento_private.partner_spaces())::uuid[]))
  with check (user_id = any ((select lucento_private.partner_spaces())::uuid[]));

-- ------------------------------------------------------------------------------------------------ RPCs
-- Functions created here start executable by their owner only (this project's default privileges); EXECUTE is granted
-- to authenticated below, never to anon.
-- Each runs as its owner with an empty search_path, takes the caller from auth.uid() (never from an argument), and
-- validates everything inside one transaction. Errors are 'lucento_space:<code>' so the app can say what happened.

-- Everything the Lucento Shared screen needs: the caller's role, the space, its members, the Owner's invitations, and
-- invitations addressed to the caller's verified email.
create or replace function public.get_my_shared_space()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_email text;
  v_owner uuid;
  v_role text;
  v_received jsonb;
  v_result jsonb;
begin
  if v_uid is null then
    raise exception 'lucento_space:not_authenticated' using errcode = '28000';
  end if;
  select lower(u.email) into v_email from auth.users u where u.id = v_uid and u.email_confirmed_at is not null;
  if exists (select 1 from public.shared_spaces s where s.owner_id = v_uid) then
    v_owner := v_uid;
    v_role := 'OWNER';
  else
    select m.owner_id into v_owner from public.space_members m where m.partner_id = v_uid;
    if v_owner is not null then
      v_role := 'PARTNER';
    end if;
  end if;
  v_received := coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', i.id, 'owner_id', i.owner_id, 'inviter_label', i.inviter_label, 'inviter_email', i.inviter_email,
      'created_at', i.created_at, 'expires_at', i.expires_at
    ) order by i.created_at desc)
    from public.space_invites i
    where v_email is not null and i.invitee_email = v_email and i.status = 'pending' and i.expires_at > now()
      and i.owner_id <> v_uid
  ), '[]'::jsonb);
  if v_owner is null then
    return jsonb_build_object('role', null, 'max_partners', lucento_private.max_partners(v_uid), 'received', v_received);
  end if;
  select jsonb_build_object(
    'role', v_role,
    'owner_id', s.owner_id,
    'owner_label', s.owner_label,
    'owner_email', coalesce((select lower(u.email) from auth.users u where u.id = s.owner_id), ''),
    'created_at', s.created_at,
    'max_partners', lucento_private.max_partners(s.owner_id),
    'members', coalesce((
      select jsonb_agg(jsonb_build_object(
        'partner_id', m.partner_id, 'label', m.partner_label, 'email', m.partner_email, 'joined_at', m.joined_at
      ) order by m.joined_at)
      from public.space_members m where m.owner_id = s.owner_id
    ), '[]'::jsonb),
    'invites', case when v_role = 'OWNER' then coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', i.id, 'email', i.invitee_email,
        'status', case when i.status = 'pending' and i.expires_at <= now() then 'expired' else i.status end,
        'created_at', i.created_at, 'expires_at', i.expires_at, 'responded_at', i.responded_at
      ) order by i.created_at desc)
      from (
        select * from public.space_invites x where x.owner_id = s.owner_id
        order by x.created_at desc limit 10
      ) i
    ), '[]'::jsonb) else '[]'::jsonb end,
    'received', v_received
  ) into v_result
  from public.shared_spaces s where s.owner_id = v_owner;
  return v_result;
end;
$$;

-- Creates the caller's Shared Space (or renames it: idempotent). Refused for someone who is a Partner elsewhere.
create or replace function public.create_shared_space(p_owner_label text default '')
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_label text := left(btrim(coalesce(p_owner_label, '')), 60);
begin
  if v_uid is null then
    raise exception 'lucento_space:not_authenticated' using errcode = '28000';
  end if;
  perform pg_advisory_xact_lock(lucento_private.space_lock(v_uid));
  if exists (select 1 from public.space_members m where m.partner_id = v_uid) then
    raise exception 'lucento_space:already_partner';
  end if;
  insert into public.shared_spaces (owner_id, owner_label) values (v_uid, v_label)
  on conflict (owner_id) do update
    set owner_label = case when excluded.owner_label <> '' then excluded.owner_label else public.shared_spaces.owner_label end;
end;
$$;

-- The caller's own display name in their Shared Space (as Owner, or as a Partner).
create or replace function public.set_my_space_label(p_label text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_label text := left(btrim(coalesce(p_label, '')), 60);
begin
  if v_uid is null then
    raise exception 'lucento_space:not_authenticated' using errcode = '28000';
  end if;
  update public.shared_spaces set owner_label = v_label where owner_id = v_uid;
  if found then
    update public.space_invites set inviter_label = v_label where owner_id = v_uid and status = 'pending';
    return;
  end if;
  update public.space_members set partner_label = v_label where partner_id = v_uid;
  if not found then
    raise exception 'lucento_space:no_space';
  end if;
end;
$$;

-- An invitation from the caller's Shared Space to an email address, valid for seven days. Pending invitations count
-- toward the Partner limit, so invitations can never outnumber the free places.
create or replace function public.create_space_invite(p_email text)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_email text := lower(btrim(coalesce(p_email, '')));
  v_own_email text;
  v_label text;
  v_used integer;
  v_id uuid;
begin
  if v_uid is null then
    raise exception 'lucento_space:not_authenticated' using errcode = '28000';
  end if;
  if char_length(v_email) > 254 or v_email !~ '^[^@\s]+@[^@\s]+\.[^@\s]+$' then
    raise exception 'lucento_space:invalid_email';
  end if;
  perform pg_advisory_xact_lock(lucento_private.space_lock(v_uid));
  select s.owner_label into v_label from public.shared_spaces s where s.owner_id = v_uid;
  if not found then
    raise exception 'lucento_space:no_space';
  end if;
  select lower(u.email) into v_own_email from auth.users u where u.id = v_uid;
  if v_email = v_own_email then
    raise exception 'lucento_space:self_invite';
  end if;
  update public.space_invites set status = 'expired', responded_at = now()
    where owner_id = v_uid and status = 'pending' and expires_at <= now();
  if exists (select 1 from public.space_members m where m.owner_id = v_uid and m.partner_email = v_email) then
    raise exception 'lucento_space:already_member';
  end if;
  if exists (select 1 from public.space_invites i where i.owner_id = v_uid and i.status = 'pending' and i.invitee_email = v_email) then
    raise exception 'lucento_space:already_invited';
  end if;
  select (select count(*) from public.space_members m where m.owner_id = v_uid)
       + (select count(*) from public.space_invites i where i.owner_id = v_uid and i.status = 'pending')
    into v_used;
  if v_used >= lucento_private.max_partners(v_uid) then
    raise exception 'lucento_space:space_full';
  end if;
  insert into public.space_invites (owner_id, invitee_email, inviter_label, inviter_email, expires_at)
    values (v_uid, v_email, coalesce(v_label, ''), coalesce(v_own_email, ''), now() + interval '7 days')
    returning id into v_id;
  return v_id;
end;
$$;

create or replace function public.revoke_space_invite(p_invite_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then
    raise exception 'lucento_space:not_authenticated' using errcode = '28000';
  end if;
  perform pg_advisory_xact_lock(lucento_private.space_lock(v_uid));
  update public.space_invites set status = 'revoked', responded_at = now()
    where id = p_invite_id and owner_id = v_uid and status = 'pending';
  if not found then
    raise exception 'lucento_space:invite_not_found';
  end if;
end;
$$;

-- Joins the Shared Space that sent the invitation. Atomic: the membership row and the accepted invitation are written
-- together or not at all. Both people's locks are taken in a fixed order, so concurrent acceptances (the same
-- invitation twice, two invitations of one space, crossed invitations) are serialised and can't exceed the limit.
create or replace function public.accept_space_invite(p_invite_id uuid, p_partner_label text default '')
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_email text;
  v_inv public.space_invites%rowtype;
  v_members integer;
begin
  if v_uid is null then
    raise exception 'lucento_space:not_authenticated' using errcode = '28000';
  end if;
  select lower(u.email) into v_email from auth.users u where u.id = v_uid and u.email_confirmed_at is not null;
  if v_email is null then
    raise exception 'lucento_space:email_unverified';
  end if;
  select * into v_inv from public.space_invites i where i.id = p_invite_id;
  if not found or v_inv.invitee_email <> v_email then
    raise exception 'lucento_space:invite_not_found';
  end if;
  perform pg_advisory_xact_lock(lucento_private.space_lock(least(v_inv.owner_id, v_uid)));
  perform pg_advisory_xact_lock(lucento_private.space_lock(greatest(v_inv.owner_id, v_uid)));
  -- Read again under the locks: another acceptance, a revoke or a removal may have committed meanwhile.
  select * into v_inv from public.space_invites i where i.id = p_invite_id for update;
  if not found then
    raise exception 'lucento_space:invite_not_found';
  end if;
  if v_inv.status <> 'pending' then
    raise exception 'lucento_space:invite_%', v_inv.status;
  end if;
  if v_inv.expires_at <= now() then
    raise exception 'lucento_space:invite_expired';
  end if;
  if v_inv.owner_id = v_uid then
    raise exception 'lucento_space:own_space';
  end if;
  if exists (select 1 from public.space_members m where m.partner_id = v_uid) then
    raise exception 'lucento_space:already_partner';
  end if;
  if exists (select 1 from public.shared_spaces s where s.owner_id = v_uid) then
    -- An Owner can't be a Partner elsewhere. A space of the caller's own with nobody in it and nothing pending is
    -- closed so they can join; anything else must be ended by them first.
    if exists (select 1 from public.space_members m where m.owner_id = v_uid)
       or exists (select 1 from public.space_invites i where i.owner_id = v_uid and i.status = 'pending' and i.expires_at > now()) then
      raise exception 'lucento_space:owns_space';
    end if;
    delete from public.shared_spaces where owner_id = v_uid;
  end if;
  select count(*) into v_members from public.space_members m where m.owner_id = v_inv.owner_id;
  if v_members >= lucento_private.max_partners(v_inv.owner_id) then
    raise exception 'lucento_space:space_full';
  end if;
  insert into public.space_members (owner_id, partner_id, partner_label, partner_email)
    values (v_inv.owner_id, v_uid, left(btrim(coalesce(p_partner_label, '')), 60), v_email);
  update public.space_invites set status = 'accepted', responded_at = now() where id = p_invite_id;
  return v_inv.owner_id;
end;
$$;

create or replace function public.decline_space_invite(p_invite_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_email text;
begin
  if v_uid is null then
    raise exception 'lucento_space:not_authenticated' using errcode = '28000';
  end if;
  select lower(u.email) into v_email from auth.users u where u.id = v_uid and u.email_confirmed_at is not null;
  update public.space_invites set status = 'declined', responded_at = now()
    where id = p_invite_id and v_email is not null and invitee_email = v_email and status = 'pending';
  if not found then
    raise exception 'lucento_space:invite_not_found';
  end if;
end;
$$;

-- The Owner removes a Partner. Their access ends with this statement; what they added stays in the space.
create or replace function public.remove_space_partner(p_partner_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then
    raise exception 'lucento_space:not_authenticated' using errcode = '28000';
  end if;
  perform pg_advisory_xact_lock(lucento_private.space_lock(least(v_uid, p_partner_id)));
  perform pg_advisory_xact_lock(lucento_private.space_lock(greatest(v_uid, p_partner_id)));
  delete from public.space_members where owner_id = v_uid and partner_id = p_partner_id;
  if not found then
    raise exception 'lucento_space:partner_not_found';
  end if;
end;
$$;

-- A Partner leaves. Returns the Owner's id of the space they left.
create or replace function public.leave_shared_space()
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_owner uuid;
begin
  if v_uid is null then
    raise exception 'lucento_space:not_authenticated' using errcode = '28000';
  end if;
  select m.owner_id into v_owner from public.space_members m where m.partner_id = v_uid;
  if v_owner is null then
    raise exception 'lucento_space:not_partner';
  end if;
  perform pg_advisory_xact_lock(lucento_private.space_lock(least(v_owner, v_uid)));
  perform pg_advisory_xact_lock(lucento_private.space_lock(greatest(v_owner, v_uid)));
  delete from public.space_members where partner_id = v_uid;
  if not found then
    raise exception 'lucento_space:not_partner';
  end if;
  return v_owner;
end;
$$;

-- The Owner closes an empty Shared Space (no Partners; pending invitations are withdrawn with it). The ledger is the
-- Owner's own and is untouched.
create or replace function public.close_shared_space()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then
    raise exception 'lucento_space:not_authenticated' using errcode = '28000';
  end if;
  perform pg_advisory_xact_lock(lucento_private.space_lock(v_uid));
  if exists (select 1 from public.space_members m where m.owner_id = v_uid) then
    raise exception 'lucento_space:has_partners';
  end if;
  delete from public.shared_spaces where owner_id = v_uid;
  if not found then
    raise exception 'lucento_space:no_space';
  end if;
end;
$$;

grant execute on function public.get_my_shared_space() to authenticated;
grant execute on function public.create_shared_space(text) to authenticated;
grant execute on function public.set_my_space_label(text) to authenticated;
grant execute on function public.create_space_invite(text) to authenticated;
grant execute on function public.revoke_space_invite(uuid) to authenticated;
grant execute on function public.accept_space_invite(uuid, text) to authenticated;
grant execute on function public.decline_space_invite(uuid) to authenticated;
grant execute on function public.remove_space_partner(uuid) to authenticated;
grant execute on function public.leave_shared_space() to authenticated;
grant execute on function public.close_shared_space() to authenticated;
