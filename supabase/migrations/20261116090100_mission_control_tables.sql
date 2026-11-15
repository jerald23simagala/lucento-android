-- Mission Control V1, part 2 of 4: the tables (docs/mission-control/v1/DATABASE_PROPOSAL.md §3–§4). Functions come in
-- parts 3 and 4. Everything here lives in the private mc schema, which no API role can reach.
--
-- One change to an existing table (FD-V5): public.entitlements' constraint entitlements_grant_expires now lets exactly one
-- kind of GRANT row go without an expiry: a complimentary Lifetime's phone row (product lucento_pro_lifetime, LOCAL_PRO),
-- the same shape as a paid Lifetime. The app already reads a missing expiry as "never" (SupabaseEntitlementRemote), and
-- the existing GRANT source is kept: a new source name would break installed apps (EntitlementSource.valueOf).
--
-- Personal data and deletion: rows that describe an account (grants, activity, the app version it reports, its support
-- requests) are removed with the account through cascading foreign keys, like everything else the account owns
-- (docs/ACCOUNT_DELETION.md). What must outlive an account keeps the bare id only: the audit log, deletion requests and
-- records-view sessions. Google Play orders hold no account at all.

-- ------------------------------------------------------------------------------------------------ FD-V5
alter table public.entitlements drop constraint if exists entitlements_grant_expires;
-- coalesce: a CHECK passes when its expression is NULL, and product_id is NULL on every other GRANT row.
alter table public.entitlements add constraint entitlements_grant_expires
  check (source <> 'GRANT' or expires_at is not null or coalesce(product_id, '') = 'lucento_pro_lifetime');

-- ------------------------------------------------------------------------------------------------ complimentary Pro
-- Every complimentary grant, with who gave it and why. public.entitlements' GRANT rows are always derived from the grants
-- still running (mc.recompute_grants, part 3), so overlapping, extending or revoking grants can't drift.
create table if not exists mc.entitlement_grants (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  -- imported: a GRANT row the owner wrote by hand before Mission Control existed.
  kind text not null check (kind in ('days_30', 'days_90', 'year_1', 'lifetime', 'imported')),
  grants text[] not null default array['LOCAL_PRO', 'CLOUD', 'AI_PRO', 'SHARED']::text[]
    check (cardinality(grants) > 0 and grants <@ array['LOCAL_PRO', 'CLOUD', 'AI_PRO', 'SHARED']::text[]),
  starts_at timestamptz not null default now(),
  -- When the phone features end; null only for a Lifetime grant (never).
  ends_at timestamptz,
  -- When cloud sync, Lucento Shared and the AI adviser's Pro allowance end: ends_at, or 365 days for Lifetime (as paid).
  services_end_at timestamptz not null,
  reason text not null check (char_length(reason) between 3 and 500),
  note text check (note is null or char_length(note) <= 1000),
  ticket_id bigint,
  -- The admin who gave it; null for imported grants.
  granted_by uuid,
  granted_at timestamptz not null default now(),
  revoked_at timestamptz,
  revoked_by uuid,
  revoke_reason text check (revoke_reason is null or char_length(revoke_reason) <= 500),
  constraint entitlement_grants_lifetime_forever check ((kind = 'lifetime') = (ends_at is null)),
  constraint entitlement_grants_order check (ends_at is null or ends_at >= starts_at),
  constraint entitlement_grants_revoked check (revoked_at is not null or (revoked_by is null and revoke_reason is null))
);
create index if not exists entitlement_grants_user_idx on mc.entitlement_grants (user_id, granted_at desc);

-- The GRANT rows written by hand before today become the history they stand for, unchanged: a grant ending when the row
-- ends, ended already when the row no longer grants anything. Recomputing such an account later writes the same row.
-- Runs once here. Only accounts with no grant history in Mission Control are imported, so running it again changes
-- nothing, and the GRANT rows Mission Control writes itself are never read back in as hand-written ones.
create or replace function mc.import_manual_grants()
returns integer
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_n integer;
begin
  insert into mc.entitlement_grants
    (user_id, kind, grants, starts_at, ends_at, services_end_at, reason, note, granted_at, revoked_at, revoke_reason)
  select e.user_id, 'imported', e.grants, e.starts_at,
         greatest(e.expires_at, e.starts_at), greatest(e.expires_at, e.starts_at),
         'Granted before Mission Control', e.note, e.starts_at,
         case when e.status not in ('ACTIVE', 'TRIAL', 'GRACE', 'CANCELLED_ACTIVE') then e.updated_at end,
         case when e.status not in ('ACTIVE', 'TRIAL', 'GRACE', 'CANCELLED_ACTIVE') then 'Ended before Mission Control' end
  from public.entitlements e
  where e.source = 'GRANT'
    and e.expires_at is not null
    and not exists (select 1 from mc.entitlement_grants g where g.user_id = e.user_id);
  get diagnostics v_n = row_count;
  return v_n;
end;
$$;
revoke all on function mc.import_manual_grants() from public, anon, authenticated, service_role;
select mc.import_manual_grants();

-- ------------------------------------------------------------------------------------------------ deletion requests
-- An admin-initiated deletion waits 24 hours and can be cancelled until then; a job then runs it through the same path
-- as the in-app Delete account (part 3). No foreign key: the request is the record that the account was deleted.
create table if not exists mc.deletion_requests (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null,
  requested_by uuid not null,
  reason text not null check (char_length(reason) between 3 and 500),
  requested_at timestamptz not null default now(),
  run_after timestamptz not null,
  cancelled_at timestamptz,
  cancelled_by uuid,
  cancel_reason text check (cancel_reason is null or char_length(cancel_reason) <= 500),
  executed_at timestamptz,
  result text check (result is null or result in ('deleted', 'already_gone')),
  -- A deletion the database refused (it never should): tried three times, then left for the admin with its error.
  attempts integer not null default 0 check (attempts >= 0),
  last_error text check (last_error is null or char_length(last_error) <= 300),
  constraint deletion_requests_cool_off check (run_after >= requested_at + interval '24 hours')
);
create unique index if not exists deletion_requests_one_pending
  on mc.deletion_requests (user_id) where cancelled_at is null and executed_at is null;

-- ------------------------------------------------------------------------------------------------ activity and history
-- Which days each signed-in account used Lucento: a token refresh or a sign-in, a synced write, an AI request (hourly
-- job, part 3). Kept 35 days; the daily totals in daily_metrics keep the history.
create table if not exists mc.account_activity (
  user_id uuid not null references auth.users (id) on delete cascade,
  day date not null,
  signals text[] not null check (cardinality(signals) > 0 and signals <@ array['refresh', 'sign_in', 'sync', 'ai']::text[]),
  primary key (user_id, day)
);
create index if not exists account_activity_day_idx on mc.account_activity (day);

-- Totals per day (Mission Control's time zone), written nightly so charts keep their history.
create table if not exists mc.daily_metrics (
  day date not null,
  key text not null check (char_length(key) between 1 and 60),
  value numeric not null,
  primary key (day, key)
);

-- Every scheduled or reported job run: the database's own jobs, the weekly backup, the Play orders sweep.
create table if not exists mc.job_runs (
  id bigint generated always as identity primary key,
  job text not null check (job in ('activity', 'daily', 'grants', 'deletions', 'backup', 'play_orders', 'crashlytics')),
  started_at timestamptz not null default now(),
  finished_at timestamptz,
  result text not null check (result in ('running', 'ok', 'failed')),
  detail jsonb not null default '{}'::jsonb check (jsonb_typeof(detail) = 'object' and pg_column_size(detail) <= 4096)
);
create index if not exists job_runs_job_idx on mc.job_runs (job, started_at desc);

-- What the app reports once a day for a signed-in account (FD-V7): public.report_client, part 3.
create table if not exists mc.account_clients (
  user_id uuid primary key references auth.users (id) on delete cascade,
  app_version text not null check (char_length(app_version) between 1 and 40),
  version_code integer not null check (version_code > 0),
  android_sdk integer not null check (android_sdk between 21 and 100),
  device_model text not null check (char_length(device_model) between 1 and 80),
  first_seen_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now()
);

-- ------------------------------------------------------------------------------------------------ support
-- A support request from the app (public.submit_support_request) or one the admin logs by hand (an email, a Play review).
-- Shown as LC-1001, LC-1002, … Removed with the account it belongs to; a request sent signed out keeps only the email
-- address the person typed, and resolved requests are deleted after 12 months (part 3, daily job).
create table if not exists mc.tickets (
  id bigint generated by default as identity (start with 1001) primary key,
  channel text not null check (channel in ('in_app', 'manual')),
  user_id uuid references auth.users (id) on delete cascade,
  -- Only when no account is linked: the address the person typed (signed out) or the admin entered.
  requester_email text check (requester_email is null or (char_length(requester_email) <= 254
    and requester_email ~ '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$')),
  -- True only for a request sent from the account's own signed-in app. A typed address, a ticket logged by hand and a
  -- ticket linked to an account afterwards are not verified: anyone can type anyone's email (security review M1).
  requester_verified boolean not null default false,
  topic text not null check (topic in ('account', 'billing', 'sync', 'data_recovery', 'bug', 'idea', 'other')),
  subject text not null check (char_length(subject) between 1 and 200),
  status text not null default 'open' check (status in ('open', 'in_progress', 'awaiting_customer', 'resolved')),
  priority text not null default 'normal' check (priority in ('normal', 'urgent')),
  -- In-app only: the install's random id (rate limits) and the request's own id (a retry never files it twice).
  install_id uuid,
  client_ref uuid,
  -- What the app attached, allow-listed keys only (part 3).
  diagnostics jsonb not null default '{}'::jsonb check (jsonb_typeof(diagnostics) = 'object' and pg_column_size(diagnostics) <= 2048),
  -- Manual only: where it came from ("Gmail", "Play review").
  source_note text check (source_note is null or char_length(source_note) <= 200),
  created_by uuid,
  created_at timestamptz not null default now(),
  first_response_at timestamptz,
  resolved_at timestamptz,
  last_activity_at timestamptz not null default now(),
  constraint tickets_someone check (user_id is not null or requester_email is not null)
);
create unique index if not exists tickets_client_ref on mc.tickets (install_id, client_ref) where client_ref is not null;
create index if not exists tickets_status_idx on mc.tickets (status, last_activity_at desc);
create index if not exists tickets_user_idx on mc.tickets (user_id) where user_id is not null;
create index if not exists tickets_install_idx on mc.tickets (install_id, created_at desc) where install_id is not null;
create index if not exists tickets_created_idx on mc.tickets (created_at desc);

-- The conversation: what the person sent (inbound), replies sent by email (reply) and internal notes (note, never sent).
create table if not exists mc.ticket_messages (
  id bigint generated always as identity primary key,
  ticket_id bigint not null references mc.tickets (id) on delete cascade,
  kind text not null check (kind in ('inbound', 'reply', 'note')),
  author_admin_id uuid,
  body_text text not null check (char_length(body_text) between 1 and 5000),
  -- Replies: where it went, and what Resend said about it.
  recipient text check (recipient is null or char_length(recipient) <= 254),
  delivery text check (delivery is null or delivery in ('sending', 'sent', 'delivered', 'delayed', 'bounced', 'complained', 'failed')),
  resend_email_id text check (resend_email_id is null or char_length(resend_email_id) <= 100),
  error text check (error is null or char_length(error) <= 300),
  created_at timestamptz not null default now()
);
create index if not exists ticket_messages_ticket_idx on mc.ticket_messages (ticket_id, id);
create index if not exists ticket_messages_email_idx on mc.ticket_messages (resend_email_id) where resend_email_id is not null;

alter table mc.entitlement_grants drop constraint if exists entitlement_grants_ticket_fk;
alter table mc.entitlement_grants add constraint entitlement_grants_ticket_fk
  foreign key (ticket_id) references mc.tickets (id) on delete set null;

-- ------------------------------------------------------------------------------------------------ emails
-- Resend's delivery events (webhook, part 4). The Resend account is shared with KlaroPH: its events are counted for the
-- quota, but their recipient and subject are never stored here.
create table if not exists mc.email_events (
  event_id text primary key check (char_length(event_id) between 1 and 100),
  email_id text check (email_id is null or char_length(email_id) <= 100),
  type text not null check (type in ('email.sent', 'email.delivered', 'email.delivery_delayed', 'email.bounced',
    'email.complained', 'email.failed', 'email.opened', 'email.clicked', 'email.received', 'email.scheduled', 'email.suppressed')),
  at timestamptz not null,
  from_domain text check (from_domain is null or char_length(from_domain) <= 253),
  recipient text check (recipient is null or char_length(recipient) <= 254),
  subject text check (subject is null or char_length(subject) <= 300),
  reason text check (reason is null or char_length(reason) <= 500),
  received_at timestamptz not null default now()
);
create index if not exists email_events_at_idx on mc.email_events (at desc);
create index if not exists email_events_email_idx on mc.email_events (email_id) where email_id is not null;
create index if not exists email_events_recipient_idx on mc.email_events (recipient) where recipient is not null;

-- Emails Mission Control sent from a fixed template (ticket replies live in ticket_messages).
create table if not exists mc.email_sends (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users (id) on delete cascade,
  template text not null check (template in ('complimentary_pro', 'about_purchase', 'deletion_scheduled', 'test')),
  recipient text not null check (char_length(recipient) <= 254),
  sent_by uuid not null,
  created_at timestamptz not null default now(),
  status text not null default 'sending' check (status in ('sending', 'sent', 'failed')),
  resend_email_id text check (resend_email_id is null or char_length(resend_email_id) <= 100),
  error text check (error is null or char_length(error) <= 300)
);

-- ------------------------------------------------------------------------------------------------ revenue
-- Amounts from Google Play's Orders API (Mission Control's daily sweep, part 4). No account column: an order is
-- matched to a purchase by its scrambled token, and revenue history survives account deletion.
create table if not exists mc.play_orders (
  order_id text primary key check (char_length(order_id) between 1 and 100),
  purchase_token_hash text check (purchase_token_hash is null or purchase_token_hash ~ '^[0-9a-f]{64}$'),
  product_id text check (product_id is null or char_length(product_id) <= 100),
  base_plan_id text check (base_plan_id is null or char_length(base_plan_id) <= 100),
  -- (Google's buyer country is deliberately not kept: revenue doesn't need it.)
  -- Null only for an order Google doesn't know (state NOT_FOUND), so it isn't asked for again.
  currency text check (currency is null or currency ~ '^[A-Z]{3}$'),
  -- Micros of the currency (Google's own unit): 1,200 PHP = 1200000000.
  listing_price_micros bigint check (listing_price_micros is null or listing_price_micros >= 0),
  tax_micros bigint check (tax_micros is null or tax_micros >= 0),
  total_micros bigint check (total_micros is null or total_micros >= 0),
  refunded_micros bigint check (refunded_micros is null or refunded_micros >= 0),
  -- Google's developerRevenueInBuyerCurrency: what Lucento receives after tax, Google's fee and partial refunds.
  developer_revenue_micros bigint,
  state text check (state is null or char_length(state) <= 40),
  test boolean not null default false,
  ordered_at timestamptz,
  refunded_at timestamptz,
  fetched_at timestamptz not null default now()
);
create index if not exists play_orders_ordered_idx on mc.play_orders (ordered_at desc);
create index if not exists play_orders_token_idx on mc.play_orders (purchase_token_hash);

-- ------------------------------------------------------------------------------------------------ infrastructure
-- The last check of each service. Anything older than 10 minutes is shown as Unknown, never as fine.
create table if not exists mc.service_checks (
  service text primary key check (service in ('supabase_db', 'supabase_auth', 'supabase_platform', 'vercel_platform',
    'vercel_deployments', 'resend', 'crashlytics', 'play', 'backup')),
  status text not null check (status in ('ok', 'degraded', 'down', 'not_configured')),
  checked_at timestamptz not null default now(),
  latency_ms integer check (latency_ms is null or latency_ms >= 0),
  detail jsonb not null default '{}'::jsonb check (jsonb_typeof(detail) = 'object' and pg_column_size(detail) <= 4096)
);

-- ------------------------------------------------------------------------------------------------ records view
-- One row per time an admin opens a customer's records (FD-V3): a reason, a fresh authenticator code, 30 minutes at most.
-- Every tab opened and every note revealed inside it is in mc.audit_log too.
create table if not exists mc.records_access (
  id uuid primary key default gen_random_uuid(),
  admin_id uuid not null,
  -- The sign-in session that opened it (with its fresh code); only that session may read through it (review L2).
  session_id uuid not null,
  user_id uuid not null,
  reason text not null check (char_length(reason) between 3 and 200),
  opened_at timestamptz not null default now(),
  expires_at timestamptz not null,
  closed_at timestamptz
);
create index if not exists records_access_user_idx on mc.records_access (user_id, opened_at desc);

-- ------------------------------------------------------------------------------------------------ privileges
revoke all on all tables in schema mc from public, anon, authenticated, service_role;
revoke all on all sequences in schema mc from public, anon, authenticated, service_role;
