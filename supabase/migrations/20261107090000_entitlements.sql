-- Lucento Pro entitlements (docs/MONETIZATION.md §5.4): what each account's plan unlocks, written by the server, read by the
-- phone. DRAFT — written on feature/lucento-pro-architecture; apply to lucento-test only on the owner's approval, and
-- only together with GRANT rows for the accounts that already share a Shared Space there (max_partners becomes
-- plan-aware below: without a row an Owner can no longer *invite*; existing Partners are untouched — D-16, lenient).
--
-- Strictly additive. Nothing is dropped or rewritten; one existing function (lucento_private.max_partners) changes its
-- answer, which is what it was built for (20261105120000_lucento_shared.sql: "the future entitlement hook").
--
--   entitlements     one row per (account, source, product): the plan rows. Clients read their own; nobody but the
--                    service role writes (the Vercel endpoints verify with the Play Developer API, docs §5.4).
--   purchase_links   one Play purchase ↔ one Lucento account, ever. Service role only; clients never see it.
--   plan_limits      remote configuration (AI allowances, partner capacity), private schema; read by the functions.
--   has_capability   the one question every server-side gate asks: does this account hold LOCAL_PRO / CLOUD / AI_PRO / SHARED now.
--   max_partners     1 with SHARED, else 0 (was: 1 for everyone).
--   consume_ai_request  counts one AI request and answers whether it is within the caller's plan (the proxy's new call;
--                    consume_ai_quota stays for the current proxy until it switches).

-- ------------------------------------------------------------------------------------------------ entitlements
create table if not exists public.entitlements (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  source text not null check (source in ('PLAY_SUBSCRIPTION', 'PLAY_LIFETIME', 'PLAY_LIFETIME_SERVICES', 'PLAY_CLOUD_ADDON', 'GRANT')),
  status text not null check (status in ('ACTIVE', 'TRIAL', 'GRACE', 'CANCELLED_ACTIVE', 'ON_HOLD', 'PAUSED', 'PENDING', 'EXPIRED', 'REVOKED')),
  grants text[] not null check (cardinality(grants) > 0 and grants <@ array['LOCAL_PRO', 'CLOUD', 'AI_PRO', 'SHARED']::text[]),
  product_id text check (product_id is null or product_id in ('lucento_pro', 'lucento_pro_lifetime', 'lucento_cloud')),
  base_plan_id text check (base_plan_id is null or char_length(base_plan_id) <= 60),
  purchase_token_hash text check (purchase_token_hash is null or purchase_token_hash ~ '^[0-9a-f]{64}$'),
  starts_at timestamptz not null default now(),
  -- null: never (Lifetime's LOCAL_PRO). A GRANT must have one (the trigger below).
  expires_at timestamptz,
  verified_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  -- GRANT rows: who and why, for the owner's own bookkeeping; never shown to anyone else.
  note text check (note is null or char_length(note) <= 200),
  constraint entitlements_grant_expires check (source <> 'GRANT' or expires_at is not null),
  constraint entitlements_lifetime_forever check (source <> 'PLAY_LIFETIME' or expires_at is null)
);

create unique index if not exists entitlements_user_source_product
  on public.entitlements (user_id, source, coalesce(product_id, ''));
create index if not exists entitlements_user_idx on public.entitlements (user_id);

alter table public.entitlements enable row level security;
revoke all on table public.entitlements from public, anon, authenticated;
grant select on table public.entitlements to authenticated;

drop policy if exists "entitlements: read own" on public.entitlements;
create policy "entitlements: read own"
  on public.entitlements for select
  to authenticated
  using ((select auth.uid()) = user_id);

-- ------------------------------------------------------------------------------------------------ purchase links
create table if not exists public.purchase_links (
  purchase_token_hash text primary key check (purchase_token_hash ~ '^[0-9a-f]{64}$'),
  order_id text,
  product_id text not null,
  user_id uuid not null references auth.users (id) on delete cascade,
  -- sha256(user id) the app passed to Play at purchase, when the person was signed in; the verifier's proof of intent.
  obfuscated_account_id text,
  linked_at timestamptz not null default now(),
  relinked_count integer not null default 0 check (relinked_count >= 0)
);

alter table public.purchase_links enable row level security;
revoke all on table public.purchase_links from public, anon, authenticated;
-- No policies on purpose: only the service role (which bypasses RLS) reads or writes links.

-- ------------------------------------------------------------------------------------------------ plan limits
create table if not exists lucento_private.plan_limits (
  key text primary key,
  value_int integer not null check (value_int >= 0),
  updated_at timestamptz not null default now()
);
-- Private schema, no client privileges; RLS on top so the dashboard's check and production agree. The functions below
-- are security definer and owned by the table's owner, so RLS never applies to them.
alter table lucento_private.plan_limits enable row level security;
revoke all on table lucento_private.plan_limits from public, anon, authenticated;

insert into lucento_private.plan_limits (key, value_int) values
  ('ai_free_monthly', 10),
  ('ai_pro_daily', 50),
  ('ai_pro_monthly_ceiling', 400),
  ('max_partners_pro', 1)
on conflict (key) do nothing;

create or replace function lucento_private.plan_limit(p_key text, p_default integer)
returns integer
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce((select l.value_int from lucento_private.plan_limits l where l.key = p_key), p_default)
$$;

-- ------------------------------------------------------------------------------------------------ the one question
-- Does this account hold p_capability right now? Status and dates as the app reads them (Entitlement.covers), without the
-- phone's offline grace: the server is exact.
create or replace function lucento_private.has_capability(p_user uuid, p_capability text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1
    from public.entitlements e
    where e.user_id = p_user
      and p_capability = any (e.grants)
      and e.status in ('ACTIVE', 'TRIAL', 'GRACE', 'CANCELLED_ACTIVE')
      and (e.expires_at is null or e.expires_at > now())
  )
$$;

revoke all on function lucento_private.plan_limit(text, integer), lucento_private.has_capability(uuid, text)
  from public, anon, authenticated;

-- The Partner limit per Shared Space, now plan-aware (PRODUCT_SPEC §7). Same signature; every caller is unchanged.
create or replace function lucento_private.max_partners(p_owner uuid)
returns integer
language sql
stable
set search_path = ''
as $$
  select case
    when lucento_private.has_capability(p_owner, 'SHARED') then lucento_private.plan_limit('max_partners_pro', 1)
    else 0
  end
$$;

-- ------------------------------------------------------------------------------------------------ AI allowance
-- Counts one request for the caller today (UTC) — the same row as consume_ai_quota — and answers against the plan:
-- Free: ai_free_monthly per calendar month; AI_PRO: ai_pro_daily per day and ai_pro_monthly_ceiling per month.
-- The refused request is counted too, so a client cannot probe for free. The proxy (lucento-website api/ask, api/interpret)
-- calls this with the user's own JWT and returns `remaining` / `limited_by` to the app, which only displays them.
create or replace function public.consume_ai_request()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := auth.uid();
  v_pro boolean;
  v_today integer;
  v_month integer;
  v_daily integer;
  v_monthly integer;
  v_limited text;
  v_remaining integer;
begin
  if uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  v_pro := lucento_private.has_capability(uid, 'AI_PRO');
  insert into public.ai_usage (user_id, day, requests)
  values (uid, (now() at time zone 'utc')::date, 1)
  on conflict (user_id, day) do update set requests = public.ai_usage.requests + 1
  returning requests into v_today;
  select coalesce(sum(u.requests), 0) into v_month
    from public.ai_usage u
    where u.user_id = uid and u.day >= date_trunc('month', now() at time zone 'utc')::date;
  if v_pro then
    v_daily := lucento_private.plan_limit('ai_pro_daily', 50);
    v_monthly := lucento_private.plan_limit('ai_pro_monthly_ceiling', 400);
    v_limited := case when v_today > v_daily then 'day' when v_month > v_monthly then 'month' else null end;
    v_remaining := greatest(0, least(v_daily - v_today, v_monthly - v_month));
  else
    v_daily := null;
    v_monthly := lucento_private.plan_limit('ai_free_monthly', 10);
    v_limited := case when v_month > v_monthly then 'month' else null end;
    v_remaining := greatest(0, v_monthly - v_month);
  end if;
  return jsonb_build_object(
    'plan', case when v_pro then 'PRO' else 'FREE' end,
    'allowed', v_limited is null,
    'limited_by', v_limited,
    'used_today', v_today,
    'used_month', v_month,
    'daily_limit', v_daily,
    'monthly_limit', v_monthly,
    'remaining', v_remaining
  );
end;
$$;

revoke all on function public.consume_ai_request() from public, anon;
grant execute on function public.consume_ai_request() to authenticated;

-- ------------------------------------------------------------------------------------------------ housekeeping
create or replace function public.lucento_touch_entitlement()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;
revoke all on function public.lucento_touch_entitlement() from public, anon, authenticated;

drop trigger if exists entitlements_touch on public.entitlements;
create trigger entitlements_touch before update on public.entitlements
  for each row execute function public.lucento_touch_entitlement();
