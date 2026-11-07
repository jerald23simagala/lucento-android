-- Lucento Pro, Phase 4 (docs/monetization/SERVER_CONTRACT.md §3–§5): how a Google Play purchase the Vercel endpoints
-- have checked with the Play Developer API becomes this account's entitlement rows. DRAFT — written on
-- feature/lucento-pro-billing; applies after 20261107090000_entitlements.sql (the tables it writes), to lucento-test on the
-- owner's approval and to production only with the gated release.
--
-- Strictly additive: two functions, nothing else. Only the service role may call them (the endpoints hold its key in
-- Vercel env; the app never does), so a client can no more grant itself a plan than before. Each call is one transaction:
-- the linking rule and the row writes can't interleave with another verification of the same purchase.
--
--   apply_play_purchase   verify.ts (for the signed-in account) and rtdn.ts (for the account the purchase is linked to):
--                         applies the §5.4 linking rule, then writes the product's rows from Play's state.
--   revoke_play_purchase  rtdn.ts (voided-purchase notification) and voided.ts (daily Voided Purchases check): a refund or
--                         chargeback ends every row of that purchase. Final: a later verification never revives it.

create or replace function public.apply_play_purchase(
  -- The account verifying (verify.ts). Null: the account this purchase is already linked to (rtdn.ts re-verifying).
  p_user uuid,
  -- sha256 of the purchase token, lower-case hex. The token itself is never stored.
  p_token_hash text,
  p_order_id text,
  p_product_id text,
  -- What Play reports as the order's obfuscatedExternalAccountId: sha256 of the Lucento user id the app passed at purchase.
  p_obfuscated_account_id text,
  -- Play's state in Lucento's vocabulary (the endpoint maps subscriptionsv2 / products states onto these).
  p_status text,
  p_base_plan_id text,
  -- When the purchase was made (Lifetime's included year counts from it, so a reinstall or a re-link can't restart it).
  p_purchased_at timestamptz,
  -- Subscriptions: the end of the current period. Ignored for Lifetime.
  p_expires_at timestamptz
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

  -- A refunded purchase stays refunded: a later verification, a stale notification or a move to another account never
  -- revives it. Read before the linking rule, which may remove the previous account's rows.
  if exists (select 1 from public.entitlements e where e.purchase_token_hash = p_token_hash and e.status = 'REVOKED') then
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
    if not found then
      -- First verification: the purchase belongs to this account from now on. A concurrent first verification of the
      -- same purchase loses the insert and is decided by the rule below, against the winner's row.
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
    perform public.lucento_write_play_row(v_user, 'PLAY_LIFETIME', v_status, array['LOCAL_PRO'], p_product_id, null, p_token_hash, v_starts, null);
    perform public.lucento_write_play_row(v_user, 'PLAY_LIFETIME_SERVICES', v_services_status, array['CLOUD', 'AI_PRO', 'SHARED'],
      p_product_id, null, p_token_hash, v_starts, v_starts + interval '365 days');
  elsif p_product_id = 'lucento_pro' then
    perform public.lucento_write_play_row(v_user, 'PLAY_SUBSCRIPTION', v_status, array['LOCAL_PRO', 'CLOUD', 'AI_PRO', 'SHARED'],
      p_product_id, p_base_plan_id, p_token_hash, v_starts, p_expires_at);
  else
    perform public.lucento_write_play_row(v_user, 'PLAY_CLOUD_ADDON', v_status, array['CLOUD', 'AI_PRO', 'SHARED'],
      p_product_id, p_base_plan_id, p_token_hash, v_starts, p_expires_at);
  end if;

  return jsonb_build_object('result', 'ok', 'status', v_status);
end;
$$;

-- One row per (account, source, product). A different purchase of the same product (a resubscription) replaces the row,
-- except that a stale purchase's ending never overwrites a newer purchase that is still live.
create or replace function public.lucento_write_play_row(
  p_user uuid, p_source text, p_status text, p_grants text[], p_product_id text, p_base_plan_id text,
  p_token_hash text, p_starts_at timestamptz, p_expires_at timestamptz
)
returns void
language sql
security definer
set search_path = ''
as $$
  insert into public.entitlements as e
    (user_id, source, status, grants, product_id, base_plan_id, purchase_token_hash, starts_at, expires_at, verified_at)
  values
    (p_user, p_source, p_status, p_grants, p_product_id, p_base_plan_id, p_token_hash, p_starts_at, p_expires_at, now())
  on conflict (user_id, source, (coalesce(product_id, ''))) do update
    set status = excluded.status,
        grants = excluded.grants,
        base_plan_id = excluded.base_plan_id,
        purchase_token_hash = excluded.purchase_token_hash,
        starts_at = excluded.starts_at,
        expires_at = excluded.expires_at,
        verified_at = now()
    where e.purchase_token_hash is not distinct from excluded.purchase_token_hash
       or excluded.status in ('ACTIVE', 'TRIAL', 'GRACE', 'CANCELLED_ACTIVE', 'PENDING')
       or e.status not in ('ACTIVE', 'TRIAL', 'GRACE', 'CANCELLED_ACTIVE', 'PENDING', 'ON_HOLD', 'PAUSED')
$$;

-- A refund or chargeback (Play's voided purchases): every row of that purchase ends, on whichever account holds it.
-- Returns how many rows changed, so the daily check can report what it caught.
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
  update public.entitlements e set status = 'REVOKED', verified_at = now()
    where e.purchase_token_hash = p_token_hash and e.status <> 'REVOKED';
  get diagnostics v_n = row_count;
  return v_n;
end;
$$;

revoke all on function public.apply_play_purchase(uuid, text, text, text, text, text, text, timestamptz, timestamptz),
  public.lucento_write_play_row(uuid, text, text, text[], text, text, text, timestamptz, timestamptz),
  public.revoke_play_purchase(text)
  from public, anon, authenticated;
grant execute on function public.apply_play_purchase(uuid, text, text, text, text, text, text, timestamptz, timestamptz),
  public.revoke_play_purchase(text)
  to service_role;
