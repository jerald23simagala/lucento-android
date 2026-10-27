-- Smarter reading (opt-in Gemini interpretation, docs/CAPTURE.md): a per-user, per-day request counter.
-- The proxy (lucento-website api/interpret) calls consume_ai_quota() with the user's own JWT, then compares the count
-- with its own limit. Users can't change their count: the table has no write policy and no write grants, and the
-- function only ever increments. Calling it directly only uses up the caller's own allowance.

create table if not exists public.ai_usage (
  user_id uuid not null references auth.users (id) on delete cascade,
  day date not null,
  requests integer not null default 0 check (requests >= 0),
  primary key (user_id, day)
);

alter table public.ai_usage enable row level security;

revoke all on public.ai_usage from anon, authenticated;
grant select on public.ai_usage to authenticated;

create policy "ai_usage: read own"
  on public.ai_usage for select
  to authenticated
  using ((select auth.uid()) = user_id);

-- Counts one request for the caller today (UTC) and returns today's total including it.
create or replace function public.consume_ai_quota()
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := auth.uid();
  total integer;
begin
  if uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  insert into public.ai_usage (user_id, day, requests)
  values (uid, (now() at time zone 'utc')::date, 1)
  on conflict (user_id, day) do update set requests = public.ai_usage.requests + 1
  returning requests into total;
  return total;
end;
$$;

revoke all on function public.consume_ai_quota() from public, anon;
grant execute on function public.consume_ai_quota() to authenticated;
