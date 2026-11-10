-- Security audit F-02, F-03 and F-12 (docs/security/FINAL_SECURITY_AUDIT.md, docs/security/REMEDIATION_REPORT.md):
-- the integrity of Google Play purchases on the server. Replaces public.apply_play_purchase and
-- public.revoke_play_purchase (20261108090000_play_purchases.sql); service role only, as before.
--
--   F-02  A refund is permanent. lucento_private.revoked_purchases keeps the sha256 of every refunded purchase token,
--         with no account column (no personal data, and nothing that cascades when an account is deleted).
--         revoke_play_purchase writes it — also for a purchase no account holds yet — and apply_play_purchase reads it,
--         so a refunded purchase verified again (on the same account, a new account, after the refunded account was
--         deleted, or by a stale notification) is stored as REVOKED and grants nothing.
--   F-03  A purchase binds to the account it was made for. Play reports the obfuscated account id the app passed at
--         purchase (sha256 of the Lucento user id). When it is present and isn't the verifying account's, the answer is
--         'other_account' and nothing is linked or written — on the first verification too (it used to link to whoever
--         verified first). A purchase made while signed out carries no id; it links to the first account that verifies
--         it (docs/MONETIZATION.md §5.4, the explicit signed-out policy). The existing re-link rule is unchanged.
--   F-12  Test purchases (Play license testers) never grant a plan unless this project allows them:
--         lucento_private.plan_limits 'allow_test_purchases' (0 = refuse, the default; 1 = allow, for lucento-test and an
--         owner's internal-testing window). A refused one answers 'test_purchase_refused'. Rows that were granted carry
--         entitlements.test_purchase = true, so they can be found and ended in one statement.
--
-- Additive: one private table, one column, one plan_limits key; two functions replaced (one gains a defaulted argument,
-- so the endpoints deployed today keep calling it unchanged).

-- ------------------------------------------------------------------------------------------------ refund tombstones
create table if not exists lucento_private.revoked_purchases (
  purchase_token_hash text primary key check (purchase_token_hash ~ '^[0-9a-f]{64}$'),
  revoked_at timestamptz not null default now()
);
alter table lucento_private.revoked_purchases enable row level security;
revoke all on table lucento_private.revoked_purchases from public, anon, authenticated;

-- Refunds already recorded on entitlement rows.
insert into lucento_private.revoked_purchases (purchase_token_hash, revoked_at)
select e.purchase_token_hash, min(e.verified_at)
from public.entitlements e
where e.status = 'REVOKED' and e.purchase_token_hash is not null
group by e.purchase_token_hash
on conflict (purchase_token_hash) do nothing;

-- ------------------------------------------------------------------------------------------------ test purchases
alter table public.entitlements add column if not exists test_purchase boolean not null default false;

insert into lucento_private.plan_limits (key, value_int) values ('allow_test_purchases', 0)
on conflict (key) do nothing;

-- ------------------------------------------------------------------------------------------------ the row helper
-- As 20261108090000, plus the test flag (a row keeps the flag of the purchase that wrote it).
drop function if exists public.lucento_write_play_row(uuid, text, text, text[], text, text, text, timestamptz, timestamptz);
create or replace function public.lucento_write_play_row(
  p_user uuid, p_source text, p_status text, p_grants text[], p_product_id text, p_base_plan_id text,
  p_token_hash text, p_starts_at timestamptz, p_expires_at timestamptz, p_test_purchase boolean default false
)
returns void
language sql
security definer
set search_path = ''
as $$
  insert into public.entitlements as e
    (user_id, source, status, grants, product_id, base_plan_id, purchase_token_hash, starts_at, expires_at, verified_at,
     test_purchase)
  values
    (p_user, p_source, p_status, p_grants, p_product_id, p_base_plan_id, p_token_hash, p_starts_at, p_expires_at, now(),
     p_test_purchase)
  on conflict (user_id, source, (coalesce(product_id, ''))) do update
    set status = excluded.status,
        grants = excluded.grants,
        base_plan_id = excluded.base_plan_id,
        purchase_token_hash = excluded.purchase_token_hash,
        starts_at = excluded.starts_at,
        expires_at = excluded.expires_at,
        test_purchase = excluded.test_purchase,
        verified_at = now()
    where e.purchase_token_hash is not distinct from excluded.purchase_token_hash
       or excluded.status in ('ACTIVE', 'TRIAL', 'GRACE', 'CANCELLED_ACTIVE', 'PENDING')
       or e.status not in ('ACTIVE', 'TRIAL', 'GRACE', 'CANCELLED_ACTIVE', 'PENDING', 'ON_HOLD', 'PAUSED')
$$;

-- ------------------------------------------------------------------------------------------------ apply
drop function if exists public.apply_play_purchase(uuid, text, text, text, text, text, text, timestamptz, timestamptz);
create or replace function public.apply_play_purchase(
  -- The account verifying (verify.ts). Null: the account this purchase is already linked to (rtdn.ts re-verifying).
  p_user uuid,
  -- sha256 of the purchase token, lower-case hex. The token itself is never stored.
  p_token_hash text,
  p_order_id text,
  p_product_id text,
  -- What Play reports as the purchase's obfuscated external account id: sha256 of the Lucento user id the app passed at
  -- purchase, or null for a purchase made while signed out. Read from Play by the endpoint, never from the app.
  p_obfuscated_account_id text,
  -- Play's state in Lucento's vocabulary (the endpoint maps subscriptionsv2 / productsv2 states onto these).
  p_status text,
  p_base_plan_id text,
  -- When the purchase was made (Lifetime's included year counts from it, so a reinstall or a re-link can't restart it).
  p_purchased_at timestamptz,
  -- Subscriptions: the end of the current period. Ignored for Lifetime.
  p_expires_at timestamptz,
  -- Play marks license-tester purchases (subscriptionsv2 testPurchase, productsv2 testPurchaseContext).
  p_test_purchase boolean default false
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := p_user;
  v_link public.purchase_links%rowtype;
  v_status text := p_status;
  v_starts timestamptz := coalesce(p_purchased_at, now());
  v_services_status text;
  v_test boolean := coalesce(p_test_purchase, false);
begin
  if p_token_hash is null or p_token_hash !~ '^[0-9a-f]{64}$' then
    raise exception 'apply_play_purchase: token hash must be 64 lower-case hex characters' using errcode = '22023';
  end if;
  if p_product_id is null or p_product_id not in ('lucento_pro', 'lucento_pro_lifetime', 'lucento_cloud') then
    raise exception 'apply_play_purchase: unknown product %', p_product_id using errcode = '22023';
  end if;
  if p_status is null or p_status not in ('ACTIVE', 'TRIAL', 'GRACE', 'CANCELLED_ACTIVE', 'ON_HOLD', 'PAUSED', 'PENDING', 'EXPIRED', 'REVOKED') then
    raise exception 'apply_play_purchase: unknown status %', p_status using errcode = '22023';
  end if;
  if p_product_id <> 'lucento_pro_lifetime' and v_status not in ('PENDING', 'REVOKED') and p_expires_at is null then
    raise exception 'apply_play_purchase: a subscription needs its period end' using errcode = '22023';
  end if;
  if p_obfuscated_account_id is not null and p_obfuscated_account_id !~ '^[0-9a-f]{64}$' then
    raise exception 'apply_play_purchase: obfuscated account id must be 64 lower-case hex characters' using errcode = '22023';
  end if;

  -- F-12: a license tester's purchase grants nothing on a project that doesn't allow them.
  if v_test and lucento_private.plan_limit('allow_test_purchases', 0) <> 1 then
    return jsonb_build_object('result', 'test_purchase_refused');
  end if;

  -- F-02: a refunded purchase stays refunded — a later verification, a stale notification, a move to another account,
  -- or a new account after the refunded one was deleted never revives it. Read before the linking rule.
  if exists (select 1 from lucento_private.revoked_purchases rp where rp.purchase_token_hash = p_token_hash)
     or exists (select 1 from public.entitlements e where e.purchase_token_hash = p_token_hash and e.status = 'REVOKED') then
    v_status := 'REVOKED';
  end if;

  -- ---------------------------------------------------------------------------------------------- the linking rule
  select * into v_link from public.purchase_links l where l.purchase_token_hash = p_token_hash for update;

  if v_user is null then
    -- A notification about a purchase no account has verified yet: nothing to write. The app verifies it when it opens.
    if not found then
      return jsonb_build_object('result', 'unlinked');
    end if;
    v_user := v_link.user_id;
  else
    -- F-03: Play says this purchase was made for a different Lucento account (two accounts on one Google Play account,
    -- or someone else's token). Never linked here, never written; the buyer's own account verifies it.
    if p_obfuscated_account_id is not null
       and p_obfuscated_account_id <> encode(sha256(convert_to(lower(v_user::text), 'UTF8')), 'hex') then
      return jsonb_build_object('result', 'other_account');
    end if;

    if not found then
      -- First verification: made for this account, or made while signed out (no id). A concurrent first verification of
      -- the same purchase loses the insert and is decided by the rule below, against the winner's row.
      insert into public.purchase_links (purchase_token_hash, order_id, product_id, user_id, obfuscated_account_id)
      values (p_token_hash, p_order_id, p_product_id, v_user, p_obfuscated_account_id)
      on conflict (purchase_token_hash) do nothing;
      select * into v_link from public.purchase_links l where l.purchase_token_hash = p_token_hash for update;
    end if;

    if v_link.user_id <> v_user then
      -- Another account holds it. It moves only when the purchase was made *for* this account (Play carries the hash the
      -- app sent) and the other link is older than 30 days; a third move needs support.
      if p_obfuscated_account_id is distinct from encode(sha256(convert_to(lower(v_user::text), 'UTF8')), 'hex')
         or v_link.linked_at > now() - interval '30 days' then
        return jsonb_build_object('result', 'linked_elsewhere');
      end if;
      if v_link.relinked_count >= 2 then
        return jsonb_build_object('result', 'review_required');
      end if;
      delete from public.entitlements e where e.user_id = v_link.user_id and e.purchase_token_hash = p_token_hash;
      update public.purchase_links l
        set user_id = v_user, linked_at = now(), relinked_count = l.relinked_count + 1,
            obfuscated_account_id = coalesce(p_obfuscated_account_id, l.obfuscated_account_id),
            order_id = coalesce(p_order_id, l.order_id)
        where l.purchase_token_hash = p_token_hash;
    elsif v_link.order_id is null or v_link.obfuscated_account_id is null then
      update public.purchase_links l
        set order_id = coalesce(l.order_id, p_order_id), obfuscated_account_id = coalesce(l.obfuscated_account_id, p_obfuscated_account_id)
        where l.purchase_token_hash = p_token_hash;
    end if;
  end if;

  -- ---------------------------------------------------------------------------------------------- the rows
  if p_product_id = 'lucento_pro_lifetime' then
    -- Every Pro feature on the phone, forever; and the online services for 365 days from the purchase (D-13).
    v_services_status := case
      when v_status in ('PENDING', 'REVOKED', 'EXPIRED') then v_status
      when v_starts + interval '365 days' > now() then 'ACTIVE'
      else 'EXPIRED'
    end;
    perform public.lucento_write_play_row(v_user, 'PLAY_LIFETIME', v_status, array['LOCAL_PRO'], p_product_id, null, p_token_hash, v_starts, null, v_test);
    perform public.lucento_write_play_row(v_user, 'PLAY_LIFETIME_SERVICES', v_services_status, array['CLOUD', 'AI_PRO', 'SHARED'],
      p_product_id, null, p_token_hash, v_starts, v_starts + interval '365 days', v_test);
  elsif p_product_id = 'lucento_pro' then
    perform public.lucento_write_play_row(v_user, 'PLAY_SUBSCRIPTION', v_status, array['LOCAL_PRO', 'CLOUD', 'AI_PRO', 'SHARED'],
      p_product_id, p_base_plan_id, p_token_hash, v_starts, p_expires_at, v_test);
  else
    perform public.lucento_write_play_row(v_user, 'PLAY_CLOUD_ADDON', v_status, array['CLOUD', 'AI_PRO', 'SHARED'],
      p_product_id, p_base_plan_id, p_token_hash, v_starts, p_expires_at, v_test);
  end if;

  return jsonb_build_object('result', 'ok', 'status', v_status);
end;
$$;

-- ------------------------------------------------------------------------------------------------ revoke
-- A refund or chargeback (Play's voided purchases): recorded permanently, then every row of that purchase ends, on
-- whichever account holds it. Returns how many entitlement rows changed, so the daily check can report what it caught.
create or replace function public.revoke_play_purchase(p_token_hash text)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_n integer;
begin
  if p_token_hash is null or p_token_hash !~ '^[0-9a-f]{64}$' then
    raise exception 'revoke_play_purchase: token hash must be 64 lower-case hex characters' using errcode = '22023';
  end if;
  insert into lucento_private.revoked_purchases (purchase_token_hash) values (p_token_hash)
  on conflict (purchase_token_hash) do nothing;
  update public.entitlements e set status = 'REVOKED', verified_at = now()
    where e.purchase_token_hash = p_token_hash and e.status <> 'REVOKED';
  get diagnostics v_n = row_count;
  return v_n;
end;
$$;

revoke all on function public.apply_play_purchase(uuid, text, text, text, text, text, text, timestamptz, timestamptz, boolean),
  public.lucento_write_play_row(uuid, text, text, text[], text, text, text, timestamptz, timestamptz, boolean),
  public.revoke_play_purchase(text)
  from public, anon, authenticated;
grant execute on function public.apply_play_purchase(uuid, text, text, text, text, text, text, timestamptz, timestamptz, boolean),
  public.revoke_play_purchase(text)
  to service_role;
