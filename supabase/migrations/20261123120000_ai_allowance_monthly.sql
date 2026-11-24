-- AI allowance per calendar month on every plan (owner, 2026-11-23): Gemini is for Ask Lucento; adding transactions is
-- read on the phone. Free: ai_free_monthly (5, was 10). AI_PRO (Pro, Lifetime's included year, the Cloud add-on):
-- ai_pro_monthly (45), with no daily limit, replacing ai_pro_daily (50) and ai_pro_monthly_ceiling (400). Smarter
-- reading (Pro, opt-in in Settings) counts against the same allowance, as before. At about $0.005 per Ask question on
-- Gemini 3.5 Flash-Lite, a Pro account costs at most about $0.23 a month.
--
-- The app mirrors these numbers in PlanCatalog (FREE_AI_QUESTIONS_PER_MONTH, PRO_AI_QUESTIONS_PER_MONTH) and shows the
-- server's `remaining`; the proxies (lucento-website api/ask, api/interpret) relay this function's answer unchanged.
-- The answer keeps its shape: `limited_by` is now only 'month', and `daily_limit` is null.

insert into lucento_private.plan_limits (key, value_int) values
  ('ai_free_monthly', 5),
  ('ai_pro_monthly', 45)
on conflict (key) do update set value_int = excluded.value_int, updated_at = now();

delete from lucento_private.plan_limits where key in ('ai_pro_daily', 'ai_pro_monthly_ceiling');

-- Counts one request for the caller today (UTC), as before, and answers against the plan's monthly allowance. The
-- refused request is counted too, so a client cannot probe for free.
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
  v_monthly integer;
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
  v_monthly := case
    when v_pro then lucento_private.plan_limit('ai_pro_monthly', 45)
    else lucento_private.plan_limit('ai_free_monthly', 5)
  end;
  return jsonb_build_object(
    'plan', case when v_pro then 'PRO' else 'FREE' end,
    'allowed', v_month <= v_monthly,
    'limited_by', case when v_month > v_monthly then 'month' end,
    'used_today', v_today,
    'used_month', v_month,
    'daily_limit', null,
    'monthly_limit', v_monthly,
    'remaining', greatest(0, v_monthly - v_month)
  );
end;
$$;

revoke all on function public.consume_ai_request() from public, anon;
grant execute on function public.consume_ai_request() to authenticated;
