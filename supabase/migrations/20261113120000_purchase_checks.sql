-- Lucento Pro: the purchase check that needs no account (owner decision 2026-11-13; docs/monetization/SERVER_CONTRACT.md
-- §3b, docs/MONETIZATION.md §5.3 L-6). /api/billing/check tells a phone whether a Google Play purchase on it is still
-- live, signed in or not, so a refunded purchase can't come back on a phone that was signed out, cleared or reinstalled
-- while Google Play's copy on it lags behind the refund.
--
-- The endpoint asks the Play Developer API at most once an hour per purchase (Lucento's Play quota is shared by every
-- phone); this table keeps that last answer. Nothing in it names a person: a scrambled purchase token, the product,
-- Google's state and when it was read. A purchase in lucento_private.revoked_purchases (a refund or chargeback from the
-- notifications or the daily voided check, F-02) always answers REVOKED, whatever Google's last reading said.
-- Only the service role (the Vercel endpoints) may call the two functions; no client can read the table.
--
-- Additive: one private table and two functions.

create table if not exists lucento_private.purchase_checks (
  purchase_token_hash text primary key check (purchase_token_hash ~ '^[0-9a-f]{64}$'),
  product_id text not null check (product_id in ('lucento_pro', 'lucento_pro_lifetime', 'lucento_cloud')),
  status text not null check (status in ('ACTIVE', 'TRIAL', 'GRACE', 'CANCELLED_ACTIVE', 'ON_HOLD', 'PAUSED', 'PENDING', 'EXPIRED', 'REVOKED')),
  checked_at timestamptz not null default now()
);
alter table lucento_private.purchase_checks enable row level security;
revoke all on table lucento_private.purchase_checks from public, anon, authenticated;

-- What Lucento last heard about a purchase: {status, checked_at, revoked}. status and checked_at are null when it never
-- asked Google; revoked is true for a refund or chargeback (F-02).
create or replace function public.purchase_check_status(p_token_hash text)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_status text;
  v_checked timestamptz;
begin
  if p_token_hash is null or p_token_hash !~ '^[0-9a-f]{64}$' then
    raise exception 'purchase_check_status: token hash must be 64 lower-case hex characters' using errcode = '22023';
  end if;
  select c.status, c.checked_at into v_status, v_checked
    from lucento_private.purchase_checks c where c.purchase_token_hash = p_token_hash;
  return jsonb_build_object(
    'status', v_status,
    'checked_at', v_checked,
    'revoked', exists (select 1 from lucento_private.revoked_purchases rp where rp.purchase_token_hash = p_token_hash)
  );
end;
$$;

-- Keeps Google's reading of a purchase and answers the status to report: REVOKED for a refunded one (F-02).
create or replace function public.record_purchase_check(p_token_hash text, p_product_id text, p_status text)
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_status text := p_status;
begin
  if p_token_hash is null or p_token_hash !~ '^[0-9a-f]{64}$' then
    raise exception 'record_purchase_check: token hash must be 64 lower-case hex characters' using errcode = '22023';
  end if;
  if p_product_id is null or p_product_id not in ('lucento_pro', 'lucento_pro_lifetime', 'lucento_cloud') then
    raise exception 'record_purchase_check: unknown product %', p_product_id using errcode = '22023';
  end if;
  if p_status is null or p_status not in ('ACTIVE', 'TRIAL', 'GRACE', 'CANCELLED_ACTIVE', 'ON_HOLD', 'PAUSED', 'PENDING', 'EXPIRED', 'REVOKED') then
    raise exception 'record_purchase_check: unknown status %', p_status using errcode = '22023';
  end if;
  if exists (select 1 from lucento_private.revoked_purchases rp where rp.purchase_token_hash = p_token_hash) then
    v_status := 'REVOKED';
  end if;
  insert into lucento_private.purchase_checks as c (purchase_token_hash, product_id, status, checked_at)
    values (p_token_hash, p_product_id, v_status, pg_catalog.now())
    on conflict (purchase_token_hash) do update
      set product_id = excluded.product_id, status = excluded.status, checked_at = excluded.checked_at;
  return v_status;
end;
$$;

revoke all on function public.purchase_check_status(text), public.record_purchase_check(text, text, text)
  from public, anon, authenticated;
grant execute on function public.purchase_check_status(text), public.record_purchase_check(text, text, text)
  to service_role;
