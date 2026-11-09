-- Account emails sent by Lucento's site (lucento-website src/server/email.ts; docs/EMAILS.md): the welcome note after an
-- account's email is verified, and the confirmation when a Google Play purchase first becomes Lucento Pro. Supabase Auth
-- still sends the verification email itself (supabase/templates/confirm-signup.html).
--
-- Strictly additive: one table and two functions. The site claims an email before sending it, so a retry, a second
-- phone, a renewal or a re-verification never sends the same email twice. Only the service role may call the functions
-- (the site holds its key in Vercel env; the app never does). Rows hold no email address and no money: the account, which
-- email, a reference (the purchase's token hash for Pro, empty for welcome) and when. Deleting the account removes them.

create table if not exists public.account_emails (
  user_id uuid not null references auth.users (id) on delete cascade,
  kind text not null check (kind in ('welcome', 'pro_purchase')),
  ref text not null default '' check (length(ref) <= 128),
  sent_at timestamptz not null default now(),
  primary key (user_id, kind, ref)
);

alter table public.account_emails enable row level security;
revoke all on table public.account_emails from public, anon, authenticated;
-- No policies on purpose: only the service role (through the functions below) reads or writes these rows.

-- True when this call claimed the email (send it now); false when it was already claimed (don't send again).
create or replace function public.claim_account_email(p_user uuid, p_kind text, p_ref text)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_n integer;
begin
  if p_user is null then
    raise exception 'claim_account_email: an account is required' using errcode = '22023';
  end if;
  if p_kind is null or p_kind not in ('welcome', 'pro_purchase') then
    raise exception 'claim_account_email: unknown email %', p_kind using errcode = '22023';
  end if;
  if not exists (select 1 from auth.users u where u.id = p_user) then
    return false;
  end if;
  insert into public.account_emails (user_id, kind, ref)
  values (p_user, p_kind, coalesce(p_ref, ''))
  on conflict (user_id, kind, ref) do nothing;
  get diagnostics v_n = row_count;
  return v_n = 1;
end;
$$;

-- The send failed after the claim: give the claim back so the next attempt can send it.
create or replace function public.release_account_email(p_user uuid, p_kind text, p_ref text)
returns void
language sql
security definer
set search_path = ''
as $$
  delete from public.account_emails e
  where e.user_id = p_user and e.kind = p_kind and e.ref = coalesce(p_ref, '');
$$;

revoke all on function public.claim_account_email(uuid, text, text), public.release_account_email(uuid, text, text)
  from public, anon, authenticated;
grant execute on function public.claim_account_email(uuid, text, text), public.release_account_email(uuid, text, text)
  to service_role;
