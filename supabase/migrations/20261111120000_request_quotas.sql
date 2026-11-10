-- Security audit F-11 (docs/security/REMEDIATION_REPORT.md): an account-level limit for the website endpoints that spend
-- a shared external quota. /api/billing/verify asks the Google Play Developer API on every call, so one signed-in script
-- could exhaust Lucento's Play quota and block real verifications. The AI endpoints are already bounded per account by
-- public.consume_ai_request (20261107090000_entitlements.sql) and need nothing here.
--
-- Per account, not per IP address: people behind one network (a household, an office, a carrier NAT) never share a
-- limit. An IP-level limit for requests that aren't signed in is the Vercel Firewall's job
-- (docs/security/OWNER_ACTIONS_REQUIRED.md §7).
--
-- Additive: one private table, one function, one plan_limits key; purge_usage_events also drops old counters.

create table if not exists lucento_private.request_quota (
  user_id uuid not null references auth.users (id) on delete cascade,
  kind text not null,
  day date not null,
  n integer not null default 0,
  primary key (user_id, kind, day)
);
alter table lucento_private.request_quota enable row level security;
revoke all on table lucento_private.request_quota from public, anon, authenticated;

insert into lucento_private.plan_limits (key, value_int) values ('billing_verify_daily', 60)
on conflict (key) do nothing;

-- Counts one request of p_kind for the signed-in caller today (UTC) and answers whether it is within the limit. The
-- refused request is counted too, so a client cannot probe for free. The endpoint calls it with the person's own token.
create or replace function public.consume_request_quota(p_kind text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_n integer;
  v_limit integer;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if p_kind is null or p_kind not in ('billing_verify') then
    raise exception 'consume_request_quota: unknown kind %', p_kind using errcode = '22023';
  end if;
  insert into lucento_private.request_quota as q (user_id, kind, day, n)
    values (v_uid, p_kind, (pg_catalog.now() at time zone 'utc')::date, 1)
    on conflict (user_id, kind, day) do update set n = q.n + 1
    returning q.n into v_n;
  v_limit := lucento_private.plan_limit(p_kind || '_daily', 60);
  return jsonb_build_object('allowed', v_n <= v_limit, 'used', v_n, 'limit', v_limit);
end;
$$;

revoke all on function public.consume_request_quota(text) from public, anon;
grant execute on function public.consume_request_quota(text) to authenticated;

-- The daily purge (pg_cron, 20261029010000) also drops request counters older than two days.
create or replace function public.purge_usage_events()
returns void
language sql
set search_path = ''
as $$
  delete from public.usage_events
  where created_at < pg_catalog.now() - interval '90 days';
  delete from lucento_private.usage_event_quota
  where day < ((pg_catalog.now() at time zone 'utc')::date - 2);
  delete from lucento_private.request_quota
  where day < ((pg_catalog.now() at time zone 'utc')::date - 2);
$$;

revoke all on function public.purge_usage_events() from public, anon, authenticated;
