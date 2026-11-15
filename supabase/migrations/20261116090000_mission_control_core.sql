-- Mission Control V1, part 1 of 4: the core (docs/mission-control/v1, SECURITY_CONSIDERATIONS.md §1–§5).
--
-- Mission Control is the founder's console (a separate Next.js app). It never holds a service-role key: it calls the
-- functions in mc_api with the signed-in admin's own token, exactly like the phone calls public functions. Every
-- mc_api function starts with mc.gate(), which refuses anyone who isn't an active admin with a verified authenticator
-- code (aal2), asks for a fresh code (step-up) for the sensitive actions, applies the per-admin hourly limits, and
-- records refusals. Every change writes its own mc.audit_log row in the same transaction: no action without its record.
--
--   mc        tables and private helpers. Not exposed through the API; no privileges for anon or authenticated.
--   mc_api    only the functions Mission Control calls. The owner adds it to the exposed schemas
--             (Project Settings › Data API › Exposed schemas). EXECUTE goes to authenticated per function, and each
--             function checks mc.admins itself, so a customer who calls one is refused (and the refusal is logged).
--
-- Strictly additive: two new schemas, new tables and functions. Nothing existing changes in this part.

create schema if not exists mc;
create schema if not exists mc_api;
revoke all on schema mc from public, anon, authenticated, service_role;
revoke all on schema mc_api from public, anon, authenticated, service_role;
grant usage on schema mc_api to authenticated;
-- The webhook and job routes call the ingest functions (part 4) without an admin session; each checks a secret.
grant usage on schema mc_api to anon;

-- ------------------------------------------------------------------------------------------------ admins
-- The allowlist. The owner inserts their admin login here in the SQL editor (DEPLOYMENT.md); Mission Control's server
-- also checks MC_ADMIN_EMAILS, and both must agree. There is no function that adds an admin.
create table if not exists mc.admins (
  user_id uuid primary key references auth.users (id) on delete cascade,
  email text not null check (char_length(email) between 3 and 254),
  display_name text not null default 'Admin' check (char_length(display_name) between 1 and 60),
  active boolean not null default true,
  created_at timestamptz not null default now()
);

-- Accounts that aren't customers (the founder's own customer account, test accounts): kept out of every metric.
-- Admins are always excluded as well. Marked from a profile in Mission Control (audited).
create table if not exists mc.internal_accounts (
  user_id uuid primary key references auth.users (id) on delete cascade,
  label text not null check (char_length(label) between 1 and 60),
  added_by uuid,
  added_at timestamptz not null default now()
);

-- ------------------------------------------------------------------------------------------------ audit log
-- Every privileged action and every record view. Append-only: no API role has any privilege on it, and the triggers
-- below refuse UPDATE, DELETE and TRUNCATE for everyone, the table owner included. Entries survive account deletion:
-- they keep the bare user id, never an email address or the contents of a record.
create table if not exists mc.audit_log (
  id bigint generated always as identity primary key,
  at timestamptz not null default now(),
  -- The caller: an admin, a refused non-admin, or null for a scheduled job acting on an admin's earlier request.
  admin_id uuid,
  action text not null check (char_length(action) between 1 and 80),
  target_user_id uuid,
  -- A ticket (LC-1042), a grant id, an order id: whatever the action was about besides an account.
  target_ref text check (target_ref is null or char_length(target_ref) <= 200),
  reason text check (reason is null or char_length(reason) <= 500),
  result text not null check (result in ('ok', 'refused', 'failed', 'pending')),
  before jsonb,
  after jsonb,
  detail text check (detail is null or char_length(detail) <= 1000)
);
create index if not exists audit_log_at_idx on mc.audit_log (at desc);
create index if not exists audit_log_target_idx on mc.audit_log (target_user_id, at desc) where target_user_id is not null;
create index if not exists audit_log_action_idx on mc.audit_log (action, at desc);

create or replace function mc.audit_log_append_only()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  raise exception 'mc.audit_log is append-only' using errcode = '42501';
end;
$$;

drop trigger if exists audit_log_no_update on mc.audit_log;
create trigger audit_log_no_update before update or delete on mc.audit_log
  for each row execute function mc.audit_log_append_only();
drop trigger if exists audit_log_no_truncate on mc.audit_log;
create trigger audit_log_no_truncate before truncate on mc.audit_log
  for each statement execute function mc.audit_log_append_only();

-- ------------------------------------------------------------------------------------------------ settings
create table if not exists mc.settings (
  key text primary key check (key in (
    'resend_daily_limit', 'resend_monthly_limit', 'google_fee_rate', 'timezone', 'records_view_enabled',
    'saved_replies', 'support_signed_out_hourly_cap'
  )),
  value jsonb not null,
  updated_by uuid,
  updated_at timestamptz not null default now()
);

insert into mc.settings (key, value) values
  ('resend_daily_limit', '100'),
  ('resend_monthly_limit', '3000'),
  ('google_fee_rate', '0.15'),
  ('timezone', '"Asia/Manila"'),
  -- FD-V3 / FD-V15: off until the app release with the updated privacy policy is live.
  ('records_view_enabled', 'false'),
  ('support_signed_out_hourly_cap', '20'),
  ('saved_replies', jsonb_build_array(
    jsonb_build_object('title', 'Got it, looking into it',
      'body', E'Hi,\n\nThanks for writing to Lucento. I''m looking into this now and will get back to you as soon as I know more.\n\nJerald\nLucento'),
    jsonb_build_object('title', 'Pro is fixed',
      'body', E'Hi,\n\nYour Lucento Pro is working again. Open Lucento and pull down on the Lucento Plan page (or reopen the app) to see it.\n\nIf anything still looks wrong, just reply to this email.\n\nJerald\nLucento'),
    jsonb_build_object('title', 'Need a bit more detail',
      'body', E'Hi,\n\nThanks for the report. Could you tell me a little more: what you tapped just before it happened, and what you expected to see?\n\nA reply to this email is all it takes.\n\nJerald\nLucento')
  ))
on conflict (key) do nothing;

-- The reporting time zone: what "today" and "this month" mean in Mission Control.
create or replace function mc.tz()
returns text
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce((select s.value #>> '{}' from mc.settings s where s.key = 'timezone'), 'Asia/Manila')
$$;

create or replace function mc.setting_int(p_key text, p_default integer)
returns integer
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce((select (s.value #>> '{}')::integer from mc.settings s where s.key = p_key), p_default)
$$;

-- ------------------------------------------------------------------------------------------------ rate counters
-- Per caller, per action, per hour. Pruned daily (part 4).
create table if not exists mc.rate_counters (
  admin_id uuid not null,
  action text not null,
  hour timestamptz not null,
  count integer not null default 0,
  primary key (admin_id, action, hour)
);

-- ------------------------------------------------------------------------------------------------ ingest secret
-- The webhook and job routes (Resend events, Play orders, the weekly backup's result) hold one random secret instead of
-- an admin session or a service-role key. Only its sha256 is stored. The owner creates it in the SQL editor with
-- `select mc.rotate_ingest_secret();` and pastes the answer into Vercel and GitHub (DEPLOYMENT.md); running it again
-- replaces it.
create table if not exists mc.ingest_secret (
  id boolean primary key default true check (id),
  secret_hash text not null check (secret_hash ~ '^[0-9a-f]{64}$'),
  rotated_at timestamptz not null default now()
);

create or replace function mc.rotate_ingest_secret()
returns text
language plpgsql
volatile
set search_path = ''
as $$
declare
  -- Two version-4 UUIDs: 244 random bits from the server's strong random source, with no extension needed.
  v_secret text := replace(gen_random_uuid()::text || gen_random_uuid()::text, '-', '');
begin
  insert into mc.ingest_secret (id, secret_hash, rotated_at)
    values (true, encode(sha256(convert_to(v_secret, 'UTF8')), 'hex'), now())
    on conflict (id) do update set secret_hash = excluded.secret_hash, rotated_at = excluded.rotated_at;
  return v_secret;
end;
$$;

-- True when p_secret is the current ingest secret. Compares hashes, so the comparison leaks nothing useful about the
-- secret through timing.
create or replace function mc.ingest_secret_ok(p_secret text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select p_secret is not null and char_length(p_secret) between 32 and 200 and exists (
    select 1 from mc.ingest_secret s where s.secret_hash = encode(sha256(convert_to(p_secret, 'UTF8')), 'hex')
  )
$$;

-- ------------------------------------------------------------------------------------------------ audit helpers
create or replace function mc.audit(
  p_action text, p_target uuid, p_ref text, p_reason text, p_result text,
  p_before jsonb default null, p_after jsonb default null, p_detail text default null,
  p_admin uuid default null
)
returns bigint
language sql
volatile
security definer
set search_path = ''
as $$
  insert into mc.audit_log (admin_id, action, target_user_id, target_ref, reason, result, before, after, detail)
  values (coalesce(p_admin, auth.uid()), p_action, p_target, left(p_ref, 200), left(p_reason, 500), p_result,
          p_before, p_after, left(p_detail, 1000))
  returning id
$$;

-- A refusal is recorded, but at most 10 per caller and reason an hour, so nobody can fill the log by retrying.
create or replace function mc.log_refusal(p_caller uuid, p_action text, p_reason text)
returns void
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_count integer;
begin
  insert into mc.rate_counters as c (admin_id, action, hour, count)
    values (coalesce(p_caller, '00000000-0000-0000-0000-000000000000'::uuid), 'refused:' || p_reason, date_trunc('hour', now()), 1)
    on conflict (admin_id, action, hour) do update set count = c.count + 1
    returning c.count into v_count;
  if v_count <= 10 then
    perform mc.audit(p_action, null, null, null, 'refused', null, null, p_reason, p_caller);
  end if;
end;
$$;

-- ------------------------------------------------------------------------------------------------ the gate
-- A fresh authenticator code: a TOTP verification on this session within the last 10 minutes. Read from Auth's own
-- record of the session (auth.mfa_amr_claims, updated on every verification) and, failing that, from the token's amr
-- claim, so a step-up counts whether or not the client has refreshed its token since.
create or replace function mc.fresh_mfa(p_jwt jsonb)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select
    exists (
      select 1 from auth.mfa_amr_claims c
      where c.session_id = case when p_jwt ->> 'session_id' ~ '^[0-9a-fA-F-]{36}$' then (p_jwt ->> 'session_id')::uuid end
        and c.authentication_method = 'totp'
        and c.updated_at > now() - interval '10 minutes'
    )
    or exists (
      select 1 from jsonb_array_elements(case when jsonb_typeof(p_jwt -> 'amr') = 'array' then p_jwt -> 'amr' else '[]'::jsonb end) a
      where a ->> 'method' = 'totp'
        and a ->> 'timestamp' ~ '^\d{1,12}$'
        and to_timestamp((a ->> 'timestamp')::bigint) > now() - interval '10 minutes'
    )
$$;

-- Null when the caller may go ahead; otherwise why not: not_signed_in, not_admin, mfa_required, step_up_required or
-- rate_limited. Never raises, so the function that asked can return the refusal and the refusal's record commits.
create or replace function mc.gate(p_action text, p_step_up boolean default false, p_hourly_max integer default null)
returns text
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_jwt jsonb := coalesce(auth.jwt(), '{}'::jsonb);
  v_refusal text;
  v_count integer;
begin
  if v_uid is null then
    return 'not_signed_in';
  end if;
  if not exists (select 1 from mc.admins a where a.user_id = v_uid and a.active) then
    v_refusal := 'not_admin';
  elsif coalesce(v_jwt ->> 'aal', '') <> 'aal2' then
    v_refusal := 'mfa_required';
  elsif p_step_up and not mc.fresh_mfa(v_jwt) then
    v_refusal := 'step_up_required';
  elsif p_hourly_max is not null then
    insert into mc.rate_counters as c (admin_id, action, hour, count)
      values (v_uid, p_action, date_trunc('hour', now()), 1)
      on conflict (admin_id, action, hour) do update set count = c.count + 1
      returning c.count into v_count;
    if v_count > p_hourly_max then
      v_refusal := 'rate_limited';
    end if;
  end if;
  if v_refusal is not null then
    perform mc.log_refusal(v_uid, p_action, v_refusal);
  end if;
  return v_refusal;
end;
$$;

create or replace function mc.refused(p_reason text)
returns jsonb
language sql
immutable
set search_path = ''
as $$
  select jsonb_build_object('ok', false, 'error', p_reason)
$$;

create or replace function mc.failed(p_error text, p_message text default null)
returns jsonb
language sql
immutable
set search_path = ''
as $$
  select jsonb_build_object('ok', false, 'error', p_error) || case when p_message is null then '{}'::jsonb else jsonb_build_object('message', p_message) end
$$;

-- Accounts that don't count as customers: admins and internal accounts.
create or replace function mc.is_excluded(p_user uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (select 1 from mc.admins a where a.user_id = p_user)
      or exists (select 1 from mc.internal_accounts i where i.user_id = p_user)
$$;

-- A LIKE pattern that matches p_text literally anywhere.
create or replace function mc.like_any(p_text text)
returns text
language sql
immutable
set search_path = ''
as $$
  select '%' || replace(replace(replace(coalesce(p_text, ''), '\', '\\'), '%', '\%'), '_', '\_') || '%'
$$;

-- ------------------------------------------------------------------------------------------------ mc_api: session
-- Who am I, as the database sees it: whether this token is an admin's, at which assurance level, and whether the last
-- authenticator code is fresh. Mission Control's server calls it after sign-in. Not gated (it answers the gate's own
-- questions) and never reveals anything about anyone else.
create or replace function mc_api.whoami()
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_jwt jsonb := coalesce(auth.jwt(), '{}'::jsonb);
  v_admin mc.admins%rowtype;
begin
  if v_uid is null then
    return mc.refused('not_signed_in');
  end if;
  select * into v_admin from mc.admins a where a.user_id = v_uid and a.active;
  if not found then
    perform mc.log_refusal(v_uid, 'session.whoami', 'not_admin');
    return mc.refused('not_admin');
  end if;
  return jsonb_build_object(
    'ok', true,
    'admin', jsonb_build_object('user_id', v_admin.user_id, 'email', v_admin.email, 'display_name', v_admin.display_name),
    'aal', v_jwt ->> 'aal',
    'fresh_mfa', mc.fresh_mfa(v_jwt),
    'timezone', mc.tz(),
    'now', now()
  );
end;
$$;

-- Mission Control's server records sign-ins and sign-outs (the database can't see them otherwise).
create or replace function mc_api.record_session_event(p_event text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text;
begin
  if p_event is null or p_event not in ('signed_in', 'signed_out', 'expired_idle', 'expired_absolute', 'mfa_enrolled') then
    return mc.failed('invalid', 'Unknown session event');
  end if;
  v_refusal := mc.gate('session.' || p_event, false, 60);
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  perform mc.audit('session.' || p_event, null, null, null, 'ok');
  return jsonb_build_object('ok', true);
end;
$$;

-- ------------------------------------------------------------------------------------------------ mc_api: settings
create or replace function mc_api.settings_get()
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('settings.read');
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  return jsonb_build_object(
    'ok', true,
    'settings', (select jsonb_object_agg(s.key, jsonb_build_object('value', s.value, 'updated_at', s.updated_at,
                   'updated_by', (select a.display_name from mc.admins a where a.user_id = s.updated_by)))
                 from mc.settings s),
    'admins', (select coalesce(jsonb_agg(jsonb_build_object('email', a.email, 'display_name', a.display_name, 'active', a.active,
                   'created_at', a.created_at,
                   'mfa_factors', (select count(*) from auth.mfa_factors f where f.user_id = a.user_id and f.status::text = 'verified'))
                 order by a.created_at), '[]'::jsonb) from mc.admins a),
    'internal_accounts', (select coalesce(jsonb_agg(jsonb_build_object('user_id', i.user_id, 'label', i.label,
                   'email', (select u.email from auth.users u where u.id = i.user_id), 'added_at', i.added_at)
                 order by i.added_at), '[]'::jsonb) from mc.internal_accounts i),
    'ingest_secret_set', exists (select 1 from mc.ingest_secret),
    'ingest_secret_rotated_at', (select s.rotated_at from mc.ingest_secret s)
  );
end;
$$;

create or replace function mc_api.settings_set(p_key text, p_value jsonb, p_reason text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text;
  v_before jsonb;
  v_ok boolean;
  v_item jsonb;
begin
  -- Turning the records view on or off changes what Mission Control may show about customers: fresh code required.
  v_refusal := mc.gate('settings.write', p_key = 'records_view_enabled', 30);
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  if p_reason is null or char_length(btrim(p_reason)) < 3 then
    return mc.failed('reason_required', 'Say why you are changing this setting');
  end if;
  v_ok := case p_key
    when 'resend_daily_limit' then jsonb_typeof(p_value) = 'number' and (p_value #>> '{}') ~ '^\d{1,7}$' and (p_value #>> '{}')::integer >= 1
    when 'resend_monthly_limit' then jsonb_typeof(p_value) = 'number' and (p_value #>> '{}') ~ '^\d{1,9}$' and (p_value #>> '{}')::integer >= 1
    when 'support_signed_out_hourly_cap' then jsonb_typeof(p_value) = 'number' and (p_value #>> '{}') ~ '^\d{1,4}$'
    when 'google_fee_rate' then jsonb_typeof(p_value) = 'number' and (p_value #>> '{}')::numeric between 0 and 0.5
    when 'records_view_enabled' then jsonb_typeof(p_value) = 'boolean'
    when 'timezone' then jsonb_typeof(p_value) = 'string' and exists (select 1 from pg_catalog.pg_timezone_names z where z.name = p_value #>> '{}')
    when 'saved_replies' then jsonb_typeof(p_value) = 'array' and jsonb_array_length(p_value) between 0 and 10
    else false
  end;
  if v_ok and p_key = 'saved_replies' then
    for v_item in select * from jsonb_array_elements(p_value) loop
      if jsonb_typeof(v_item) <> 'object'
         or jsonb_typeof(v_item -> 'title') <> 'string' or char_length(v_item ->> 'title') not between 1 and 60
         or jsonb_typeof(v_item -> 'body') <> 'string' or char_length(v_item ->> 'body') not between 1 and 3000 then
        v_ok := false;
      end if;
    end loop;
  end if;
  if not coalesce(v_ok, false) then
    return mc.failed('invalid', 'That value is not allowed for ' || coalesce(p_key, 'this setting'));
  end if;
  select s.value into v_before from mc.settings s where s.key = p_key;
  insert into mc.settings as s (key, value, updated_by, updated_at) values (p_key, p_value, auth.uid(), now())
    on conflict (key) do update set value = excluded.value, updated_by = excluded.updated_by, updated_at = excluded.updated_at;
  perform mc.audit('settings.set', null, p_key, p_reason, 'ok',
    jsonb_build_object(p_key, v_before), jsonb_build_object(p_key, p_value));
  return jsonb_build_object('ok', true);
end;
$$;

-- ------------------------------------------------------------------------------------------------ mc_api: audit log
create or replace function mc_api.audit(
  p_query text default null, p_action text default null, p_result text default null, p_target uuid default null,
  p_from timestamptz default null, p_to timestamptz default null, p_before_id bigint default null, p_limit integer default 50
)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('audit.read');
  v_limit integer := least(greatest(coalesce(p_limit, 50), 1), 200);
  v_rows jsonb;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  select coalesce(jsonb_agg(r order by r.id desc), '[]'::jsonb) into v_rows from (
    select l.id, l.at, l.action, l.result, l.target_user_id, l.target_ref, l.reason, l.detail, l.before, l.after,
           l.admin_id,
           (select a.display_name from mc.admins a where a.user_id = l.admin_id) as admin_name,
           (select u.email from auth.users u where u.id = l.target_user_id) as target_email
    from mc.audit_log l
    where (p_before_id is null or l.id < p_before_id)
      and (p_action is null or l.action like replace(replace(p_action, '%', ''), '_', '\_') || '%')
      and (p_result is null or l.result = p_result)
      and (p_target is null or l.target_user_id = p_target)
      and (p_from is null or l.at >= p_from)
      and (p_to is null or l.at < p_to)
      and (p_query is null or btrim(p_query) = ''
           or l.reason ilike mc.like_any(p_query) or l.target_ref ilike mc.like_any(p_query)
           or l.detail ilike mc.like_any(p_query) or l.action ilike mc.like_any(p_query)
           or l.target_user_id::text = btrim(lower(p_query))
           or exists (select 1 from auth.users u where u.id = l.target_user_id and u.email ilike mc.like_any(p_query)))
    order by l.id desc
    limit v_limit
  ) r;
  return jsonb_build_object('ok', true, 'rows', v_rows,
    'actions', (select coalesce(jsonb_agg(distinct split_part(l.action, '.', 1)), '[]'::jsonb) from mc.audit_log l));
end;
$$;

-- ------------------------------------------------------------------------------------------------ privileges
-- Tables and helpers in mc: nobody but their owner. Functions in mc_api: authenticated, one by one (each checks the
-- caller itself). Written per object so the result is the same whatever default privileges the project has.
revoke all on all tables in schema mc from public, anon, authenticated, service_role;
revoke all on all sequences in schema mc from public, anon, authenticated, service_role;
revoke all on all functions in schema mc from public, anon, authenticated, service_role;
revoke all on all functions in schema mc_api from public, anon, authenticated, service_role;
grant execute on function
  mc_api.whoami(),
  mc_api.record_session_event(text),
  mc_api.settings_get(),
  mc_api.settings_set(text, jsonb, text),
  mc_api.audit(text, text, text, uuid, timestamptz, timestamptz, bigint, integer)
  to authenticated;
