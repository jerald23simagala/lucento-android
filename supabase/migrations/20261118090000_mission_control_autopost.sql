-- Mission Control: auto-posting for Marketing (docs/mission-control/v1/MARKETING_AUTOPOST.md).
--
-- The owner approves a post in Mission Control › Marketing; at its slot Mission Control posts it on the owner's own
-- accounts: Facebook Reels and Page posts (the Lucento and KlaroPH Pages), Instagram Reels and YouTube Shorts directly,
-- and TikTok into the owner's TikTok drafts (TikTok lets an unaudited app post only privately; the owner taps Post).
-- The Facebook group can't be posted to by any app since Meta removed the Groups API in April 2024: it stays by hand.
--
--   * The console holds the provider apps' keys and does the posting; this database decides what is due, keeps the
--     posting jobs (one per post, version and platform, never two) and stores the account tokens only encrypted
--     (AES-256-GCM in the console; the key never reaches the database).
--   * A clock: pg_cron runs mc.autopost_kick() every minute; when something is due it calls the console's
--     /api/autopost/tick through pg_net with a secret kept in Supabase Vault (purpose `autopost`). Nothing is
--     scheduled until the owner runs `select mc.autopost_setup('<console>/api/autopost/tick');` once.
--   * Approval keeps its meaning: only an approved, ready, passing current version is posted, never a 'Get it on Google
--     Play' post before launch nor a 'coming soon' one after it, and a post undone, skipped or revised before its slot is
--     never posted (every step re-checks). Auto-posting is off until the owner turns it on in Settings.
--
-- Additive and safe to paste twice. Outside its own new objects it changes:
--   * mc.ingest_secret's purpose check and mc.rotate_ingest_secret: a fourth purpose, `autopost`;
--   * mc.settings' key check: `autopost_enabled` and `autopost_tick_url`;
--   * mc.job_runs' job check: `autopost`;
--   * mc.marketing_cards: a column `autopost_off` (the owner's "post this one by hand");
--   * mc.marketing_status (due_soon leaves out posts Mission Control will post), mc.marketing_card_json,
--     mc_api.marketing_board and mc_api.marketing_summary: the same answers as before plus auto-posting.

-- ================================================================================================ extensions
-- pg_net makes the clock's HTTP call (Supabase › Database › Extensions; available on every plan).
do $$
begin
  if exists (select 1 from pg_catalog.pg_available_extensions where name = 'pg_net') then
    create extension if not exists pg_net;
  else
    raise warning 'pg_net is not available here: the auto-posting clock can''t be set (mc.autopost_setup refuses)';
  end if;
end;
$$;

-- ================================================================================================ secrets, settings, jobs
alter table mc.ingest_secret drop constraint if exists ingest_secret_purpose_check;
alter table mc.ingest_secret add constraint ingest_secret_purpose_check
  check (purpose in ('app', 'backup', 'marketing', 'autopost'));

-- The same function as before with the fourth purpose. Still not security definer: only the owner runs it.
create or replace function mc.rotate_ingest_secret(p_purpose text default 'app')
returns text
language plpgsql
volatile
set search_path = ''
as $$
declare
  -- Two version-4 UUIDs: 244 random bits from the server's strong random source, with no extension needed.
  v_secret text := replace(gen_random_uuid()::text || gen_random_uuid()::text, '-', '');
begin
  if p_purpose is null or p_purpose not in ('app', 'backup', 'marketing', 'autopost') then
    raise exception 'rotate_ingest_secret: purpose is app, backup, marketing or autopost' using errcode = '22023';
  end if;
  insert into mc.ingest_secret (purpose, secret_hash, rotated_at)
    values (p_purpose, encode(sha256(convert_to(v_secret, 'UTF8')), 'hex'), now())
    on conflict (purpose) do update set secret_hash = excluded.secret_hash, rotated_at = excluded.rotated_at;
  return v_secret;
end;
$$;

alter table mc.settings drop constraint if exists settings_key_check;
alter table mc.settings add constraint settings_key_check check (key in (
  'resend_daily_limit', 'resend_monthly_limit', 'google_fee_rate', 'timezone', 'records_view_enabled',
  'extended_data_enabled', 'saved_replies', 'support_signed_out_hourly_cap', 'support_daily_cap',
  'autopost_enabled', 'autopost_tick_url'
));
insert into mc.settings (key, value) values ('autopost_enabled', 'false') on conflict (key) do nothing;

alter table mc.job_runs drop constraint if exists job_runs_job_check;
alter table mc.job_runs add constraint job_runs_job_check
  check (job in ('activity', 'daily', 'grants', 'deletions', 'backup', 'play_orders', 'crashlytics', 'marketing_retention',
                 'autopost'));

-- The owner's "post this one by hand" (not part of a version: it doesn't change the post).
alter table mc.marketing_cards add column if not exists autopost_off boolean not null default false;

-- ================================================================================================ helpers (no tables)
-- An encrypted token from the console: v1:<iv>:<ciphertext>:<tag>, base64url.
create or replace function mc.autopost_cipher_ok(p text)
returns boolean
language sql
immutable
set search_path = ''
as $$
  select p is not null and char_length(p) <= 8000 and p ~ '^v1:[A-Za-z0-9_-]{16}:[A-Za-z0-9_-]+:[A-Za-z0-9_-]{22}$'
$$;

-- An account or object id from a provider (Facebook Page and Instagram ids, a YouTube channel, a TikTok open_id).
create or replace function mc.autopost_id_ok(p text)
returns boolean
language sql
immutable
set search_path = ''
as $$
  select p is not null and p ~ '^[A-Za-z0-9_.:-]{1,200}$'
$$;

-- What each platform label on a post becomes (§2): a target posted automatically ('auto'), sent to the owner's drafts
-- ('draft', TikTok) or left to the owner ('manual': the Facebook group, any label not listed). Labels are matched
-- without case or surrounding spaces; "FB + IG Reels" becomes two targets.
create or replace function mc.autopost_targets(p_platforms text[])
returns table (ord bigint, label text, target text, destination text, mode text)
language sql
immutable
set search_path = ''
as $$
  select l.ord, l.label, t.target, t.destination, coalesce(t.mode, 'manual')
  from unnest(coalesce(p_platforms, '{}'::text[])) with ordinality as l(label, ord)
  left join (values
      ('tiktok', 'tiktok', 'tiktok', 'draft'),
      ('fb + ig reels', 'facebook_reel', 'lucento_page', 'auto'),
      ('fb + ig reels', 'instagram_reel', 'instagram', 'auto'),
      ('facebook reels', 'facebook_reel', 'lucento_page', 'auto'),
      ('instagram reels', 'instagram_reel', 'instagram', 'auto'),
      ('youtube shorts', 'youtube', 'youtube', 'auto'),
      ('lucento facebook page', 'facebook_photo', 'lucento_page', 'auto'),
      ('klaroph facebook page', 'klaroph_photo', 'klaroph_page', 'auto')
    ) as t(key, target, destination, mode) on t.key = lower(btrim(l.label))
$$;

-- What "Did you post it on …?" may name: the post's labels, and one platform of a two-platform label ("Instagram Reels"
-- of "FB + IG Reels") when Mission Control posted the other (design review round 3).
create or replace function mc.autopost_by_hand_names(p_platforms text[])
returns text[]
language sql
immutable
set search_path = ''
as $$
  select coalesce(p_platforms, '{}'::text[])
         || coalesce(array(select distinct case t.target when 'facebook_reel' then 'Facebook Reels' else 'Instagram Reels' end
                           from mc.autopost_targets(p_platforms) t
                           where t.target in ('facebook_reel', 'instagram_reel')), '{}'::text[])
$$;

-- Where a by_hand entry sits among the post's platforms: its label's place, a reel platform at its label's place
-- (Facebook before Instagram), so "Did you post it …?" asks in the post's own order.
create or replace function mc.autopost_by_hand_order(p_platforms text[], p_name text)
returns numeric
language sql
immutable
set search_path = ''
as $$
  select coalesce(array_position(p_platforms, p_name)::numeric,
                  (select min(t.ord)::numeric + case p_name when 'Instagram Reels' then 0.5 else 0.25 end
                   from mc.autopost_targets(p_platforms) t
                   where t.target = case p_name when 'Facebook Reels' then 'facebook_reel' when 'Instagram Reels' then 'instagram_reel' end),
                  1000)
$$;

-- The by_hand entry that covers a job's target: its label, or its platform when only that platform of the label is the
-- owner's ("Instagram Reels"), or null when the owner doesn't post it.
create or replace function mc.autopost_by_hand_entry(p_by_hand text[], p_label text, p_target text)
returns text
language sql
immutable
set search_path = ''
as $$
  select case when p_label = any(p_by_hand) then p_label
              when p_target = 'facebook_reel' and 'Facebook Reels' = any(p_by_hand) then 'Facebook Reels'
              when p_target = 'instagram_reel' and 'Instagram Reels' = any(p_by_hand) then 'Instagram Reels' end
$$;

-- When a post is due: its time on its day, or 10:00 for an "any time" day, in the pack's time zone.
create or replace function mc.autopost_due_at(p_day date, p_time time, p_tz text)
returns timestamptz
language sql
stable
set search_path = ''
as $$
  select case when p_day is null then null
              when p_time is null then (p_day + time '10:00') at time zone p_tz
              else (p_day + p_time) at time zone p_tz end
$$;

-- The last moment it may still go out: 3 hours after a set time, the end of the day for an "any time" day. Later than
-- that the job fails as missed (the owner decides; Mission Control never posts a day late on its own).
create or replace function mc.autopost_late_until(p_day date, p_time time, p_tz text)
returns timestamptz
language sql
stable
set search_path = ''
as $$
  select case when p_day is null then null
              when p_time is null then (p_day + 1 + time '00:00') at time zone p_tz
              else (p_day + p_time) at time zone p_tz + interval '3 hours' end
$$;

-- Reels are prepared up to 20 minutes ahead (Meta fetches and processes the video), then published at the slot; the
-- other targets start at the slot.
create or replace function mc.autopost_lead(p_target text)
returns interval
language sql
immutable
set search_path = ''
as $$
  select case when p_target in ('facebook_reel', 'instagram_reel') then interval '20 minutes' else interval '0' end
$$;

-- ================================================================================================ tables
-- One connected account per provider (a single-owner console). Tokens are ciphertext only; `options` lists what the
-- account can post to (Pages with their own encrypted tokens, Instagram accounts and the Page each is linked to, the
-- YouTube channel, the TikTok account), from which the owner picks the destinations.
create table if not exists mc.autopost_connections (
  provider text primary key check (provider in ('meta', 'google', 'tiktok')),
  account_id text not null check (mc.autopost_id_ok(account_id)),
  account_name text not null default '' check (char_length(account_name) <= 200),
  -- Meta posts with the Pages' own tokens (in options); its user token isn't kept (security review L5).
  token_cipher text null check (token_cipher is null or mc.autopost_cipher_ok(token_cipher)),
  refresh_cipher text null check (refresh_cipher is null or mc.autopost_cipher_ok(refresh_cipher)),
  token_expires_at timestamptz null,
  refresh_expires_at timestamptz null,
  scopes text[] not null default '{}' check (mc.text_items_ok(scopes, 30, 200)),
  options jsonb not null default '[]'::jsonb check (jsonb_typeof(options) = 'array' and pg_column_size(options) <= 200000),
  connected_by uuid not null,
  connected_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  last_error text null check (char_length(last_error) <= 500),
  last_error_at timestamptz null,
  check (provider = 'meta' or token_cipher is not null)
);

-- Where each kind of post goes. A Page (and the Instagram account posted through its Page) carries that Page's own
-- encrypted token; YouTube and TikTok use their connection's tokens.
create table if not exists mc.autopost_destinations (
  key text primary key check (key in ('lucento_page', 'klaroph_page', 'instagram', 'youtube', 'tiktok')),
  provider text not null references mc.autopost_connections (provider) on delete cascade,
  external_id text not null check (mc.autopost_id_ok(external_id)),
  name text not null default '' check (char_length(name) <= 200),
  token_cipher text null check (token_cipher is null or mc.autopost_cipher_ok(token_cipher)),
  set_by uuid not null,
  updated_at timestamptz not null default now(),
  -- The console's last Check of it (Settings): when, whether the platform answered, and why not. Cleared when the
  -- destination or its token changes.
  checked_at timestamptz null,
  check_ok boolean null,
  check_note text null check (char_length(check_note) <= 300),
  check (provider = case key when 'youtube' then 'google' when 'tiktok' then 'tiktok' else 'meta' end),
  check ((provider = 'meta') = (token_cipher is not null))
);

-- One job per post, version and target. Created at most 20 minutes before the slot (never at approval, so undoing an
-- approval before then leaves nothing behind), stepped by the console under a lease, and never repeated: a second job
-- for the same version and target can't exist.
create table if not exists mc.autopost_jobs (
  id uuid primary key default gen_random_uuid(),
  card_id uuid not null,
  version integer not null,
  target text not null check (target in ('facebook_reel', 'instagram_reel', 'youtube', 'tiktok', 'facebook_photo', 'klaroph_photo')),
  destination text not null check (destination in ('lucento_page', 'klaroph_page', 'instagram', 'youtube', 'tiktok')),
  label text not null check (char_length(label) between 1 and 60),
  due_at timestamptz not null,
  late_until timestamptz not null,
  status text not null default 'queued'
    check (status in ('queued', 'preparing', 'ready', 'posted', 'sent_to_drafts', 'failed', 'uncertain', 'cancelled', 'by_hand')),
  attempts integer not null default 0 check (attempts between 0 and 20),
  lease_id uuid null,
  lease_until timestamptz null,
  next_try_at timestamptz null,
  external_id text null check (external_id is null or mc.autopost_id_ok(external_id)),
  external_url text null check (external_url is null or (char_length(external_url) <= 1000 and external_url ~ '^https://')),
  detail jsonb not null default '{}'::jsonb check (jsonb_typeof(detail) = 'object' and pg_column_size(detail) <= 20000),
  error text null check (char_length(error) <= 500),
  error_code text null check (error_code is null or error_code ~ '^[a-z_]{1,60}$'),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  finished_at timestamptz null,
  unique (card_id, version, target),
  foreign key (card_id, version) references mc.marketing_versions (card_id, version) on delete cascade
);
create index if not exists autopost_jobs_status_due on mc.autopost_jobs (status, due_at);

-- What happened to each job, oldest first (shown on the post, read by the summary).
create table if not exists mc.autopost_events (
  id bigint generated always as identity primary key,
  job_id uuid not null references mc.autopost_jobs (id) on delete cascade,
  kind text not null check (kind in ('created', 'preparing', 'ready', 'posted', 'sent_to_drafts', 'retrying', 'failed',
                                     'uncertain', 'cancelled', 'by_hand', 'retry', 'confirmed_posted', 'unconfirmed')),
  note text null check (char_length(note) <= 500),
  actor text not null check (actor in ('system', 'admin')),
  admin_id uuid null,
  at timestamptz not null default now(),
  check ((actor = 'admin') = (admin_id is not null))
);
create index if not exists autopost_events_job on mc.autopost_events (job_id, id);

-- The clock's state (one row): when the owner set it up, when it last called the console and what the console last
-- asked for.
create table if not exists mc.autopost_state (
  id boolean primary key default true check (id),
  clock_set_at timestamptz null,
  last_kick_at timestamptz null,
  last_claim_at timestamptz null,
  updated_at timestamptz not null default now()
);
insert into mc.autopost_state (id) values (true) on conflict (id) do nothing;

-- by_hand may name one platform of a label (mc.autopost_by_hand_names); the marketing module's own check named labels only.
alter table mc.marketing_cards drop constraint if exists marketing_cards_by_hand_platforms;
alter table mc.marketing_cards add constraint marketing_cards_by_hand_platforms
  check (by_hand <@ mc.autopost_by_hand_names(platforms));

-- The owner's answer for each by_hand entry once its slot has begun: posted there, or not posting there (design review
-- round 4: a platform the owner skips is closed truthfully, never with a false "Yes"). An answer counts only while its
-- entry is still in by_hand; by_hand_done_at is set once every entry has one.
alter table mc.marketing_cards add column if not exists by_hand_posted text[] not null default '{}';
alter table mc.marketing_cards add column if not exists by_hand_skipped text[] not null default '{}';
alter table mc.marketing_cards drop constraint if exists marketing_cards_by_hand_answers;
alter table mc.marketing_cards add constraint marketing_cards_by_hand_answers
  check (mc.text_items_ok(by_hand_posted, 8, 60) and mc.text_items_ok(by_hand_skipped, 8, 60)
         and not (by_hand_posted && by_hand_skipped));

-- The history's "not posting there" answer.
alter table mc.marketing_log drop constraint if exists marketing_log_kind_check;
alter table mc.marketing_log add constraint marketing_log_kind_check
  check (kind in ('created', 'revised', 'withdrawn', 'claude_note', 'approved', 'changes', 'skipped', 'undo', 'in_apps',
                  'removed_from_apps', 'posted_by_hand', 'launch_meta', 'note_redacted', 'not_posted'));

-- ================================================================================================ rules
-- Why Mission Control won't post this post whatever its approval, or null:
--   in_apps       it is already in the apps by the owner's hand (no automatic post of that version went out), or an
--                 older version is: posting it too would put it out twice. Deleting it in the apps and tapping "I've
--                 removed it from the apps" hands it over;
--   too_early     a launch post before Lucento is live;
--   after_launch  a 'coming soon' post whose slot is after launch (the same rules as "I've scheduled it").
create or replace function mc.autopost_blocked(p_card uuid)
returns text
language sql
stable
security definer
set search_path = ''
as $$
  select case
           when c.in_apps_version is not null
                and not (c.in_apps_version = c.current_version
                         and exists (select 1 from mc.autopost_jobs j
                                     where j.card_id = c.id and j.version = c.in_apps_version
                                       and j.status in ('posted', 'sent_to_drafts', 'by_hand'))) then 'in_apps'
           when mc.marketing_launch_too_early(c.phase, s.slot, c.slot_time is null, s.pday) then 'too_early'
           when mc.marketing_after_launch(c.phase, s.slot, s.pday) then 'after_launch'
         end
  from mc.marketing_cards c
  join mc.marketing_packs p on p.id = c.pack_id
  cross join lateral (select mc.marketing_slot(c.slot_day, c.slot_time, p.tz) as slot, mc.marketing_pday() as pday) s
  where c.id = p_card
$$;

-- A destination Mission Control posts to: chosen, its last Check didn't refuse, and its account's last token renewal
-- didn't fail (design review round 2: what the posts say is what happens; reconnecting clears both).
create or replace function mc.autopost_destination_ok(p_key text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (select 1 from mc.autopost_destinations d join mc.autopost_connections cn on cn.provider = d.provider
                 where d.key = p_key and d.check_ok is distinct from false and cn.last_error is null)
$$;

-- Why a job was stopped before it posted, as a code its card puts in words (design review round 2): the owner's own
-- stops are recorded where they happen (autopost_off: auto-posting turned off; disconnected: its account disconnected;
-- card_off: the post turned to by hand); the clock's are worked out here, in this order.
create or replace function mc.autopost_stop_reason(p_card uuid, p_version integer, p_target text)
returns text
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce((
    select case
             when c.current_version <> p_version then 'new_version'
             when c.state <> 'approved' then 'not_approved'
             when c.autopost_off then 'card_off'
             when not exists (select 1 from mc.autopost_targets(c.platforms) t
                              join mc.autopost_destinations d on d.key = t.destination
                              where t.target = p_target) then 'no_destination'
             when not exists (select 1 from mc.autopost_targets(c.platforms) t
                              where t.target = p_target and mc.autopost_destination_ok(t.destination)) then 'reconnect'
             when mc.autopost_blocked(c.id) is not null then 'blocked_' || mc.autopost_blocked(c.id)
             else 'not_ready'
           end
    from mc.marketing_cards c where c.id = p_card), 'not_ready')
$$;

-- Every post Mission Control may post now or soon, with each automatic target whose destination can post
-- (mc.autopost_destination_ok): approved, not turned to by hand, auto-posting on, a ready and passing current version, a
-- slot, and nothing blocking it (mc.autopost_blocked: not in the apps by hand, launch rules).
create or replace function mc.autopost_candidates()
returns table (card_id uuid, version integer, target text, destination text, label text, due_at timestamptz,
               late_until timestamptz)
language sql
stable
security definer
set search_path = ''
as $$
  select c.id, c.current_version, t.target, t.destination, t.label,
         mc.autopost_due_at(c.slot_day, c.slot_time, p.tz), mc.autopost_late_until(c.slot_day, c.slot_time, p.tz)
  from mc.marketing_cards c
  join mc.marketing_packs p on p.id = c.pack_id
  join mc.marketing_versions v on v.card_id = c.id and v.version = c.current_version
  cross join lateral mc.autopost_targets(c.platforms) t
  join mc.autopost_destinations d on d.key = t.destination
  join mc.autopost_connections cn on cn.provider = d.provider
  where mc.setting_on('autopost_enabled') and c.state = 'approved' and not c.autopost_off and c.slot_day is not null
    and t.target is not null and v.truth_pass and mc.marketing_version_ready(c.id, c.current_version)
    and d.check_ok is distinct from false and cn.last_error is null
    and mc.autopost_blocked(c.id) is null
$$;

-- Mission Control will post every label of this one: auto-posting is on, the owner hasn't turned it to by hand, nothing
-- blocks it, and every label is a target whose destination can post (a manual label, such as the group, an unconnected
-- platform or one to reconnect still needs the owner).
create or replace function mc.autopost_covers(p_card uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce((
    select mc.setting_on('autopost_enabled') and not c.autopost_off and mc.autopost_blocked(c.id) is null
       and exists (select 1 from mc.autopost_targets(c.platforms) t where t.target is not null)
       and not exists (select 1 from mc.autopost_targets(c.platforms) t
                       where t.target is null or not mc.autopost_destination_ok(t.destination))
    from mc.marketing_cards c where c.id = p_card), false)
$$;

-- Mission Control will post at least one label of this one (the rest, if any, the owner posts at the time): due_soon
-- ("approved but not scheduled") leaves these out, as the owner has nothing to schedule for them.
create or replace function mc.autopost_posts_any(p_card uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce((
    select mc.setting_on('autopost_enabled') and not c.autopost_off and mc.autopost_blocked(c.id) is null
       and exists (select 1 from mc.autopost_targets(c.platforms) t
                   where t.target is not null and mc.autopost_destination_ok(t.destination))
    from mc.marketing_cards c where c.id = p_card), false)
$$;

-- Something for the console to do now: a job to create (within its lead time, not missed) or a job whose next step is
-- due. The clock calls the console only then.
create or replace function mc.autopost_work_due()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select mc.setting_on('autopost_enabled') and (
    exists (select 1 from mc.autopost_candidates() x
            where x.due_at - mc.autopost_lead(x.target) <= now() and now() < x.late_until
              and not exists (select 1 from mc.autopost_jobs j
                              where j.card_id = x.card_id and j.version = x.version and j.target = x.target
                                and j.status <> 'cancelled'))
    or exists (select 1 from mc.autopost_jobs j
               where j.status in ('queued', 'preparing', 'ready')
                 and (j.next_try_at is null or j.next_try_at <= now())
                 and (j.lease_until is null or j.lease_until < now())
                 and (now() >= j.late_until
                      or case j.status when 'queued' then j.due_at - mc.autopost_lead(j.target) <= now()
                                       when 'preparing' then true
                                       else j.due_at <= now() end)))
$$;

-- Since when the work due now has been due, at the latest moment each part of it could have become due: a step
-- queued again ("Post it now") counts from then, a post approved, reconnected or turned on again from then. Erring late
-- only delays "the posting clock has stopped" (never raises it early). Null when nothing is due.
create or replace function mc.autopost_work_due_since()
returns timestamptz
language sql
stable
security definer
set search_path = ''
as $$
  select case when mc.setting_on('autopost_enabled') then (
    select min(w.since) from (
      select greatest(x.due_at - mc.autopost_lead(x.target), c.updated_at, d.updated_at, cn.updated_at,
                      coalesce((select s.updated_at from mc.settings s where s.key = 'autopost_enabled'), '-infinity'::timestamptz),
                      coalesce((select max(j.updated_at) from mc.autopost_jobs j
                                where j.card_id = x.card_id and j.version = x.version and j.target = x.target),
                               '-infinity'::timestamptz)) as since
      from mc.autopost_candidates() x
      join mc.marketing_cards c on c.id = x.card_id
      join mc.autopost_destinations d on d.key = x.destination
      join mc.autopost_connections cn on cn.provider = d.provider
      where x.due_at - mc.autopost_lead(x.target) <= now() and now() < x.late_until
        and not exists (select 1 from mc.autopost_jobs j
                        where j.card_id = x.card_id and j.version = x.version and j.target = x.target
                          and j.status <> 'cancelled')
      union all
      select greatest(case when now() >= j.late_until then j.late_until
                           when j.status = 'queued' then j.due_at - mc.autopost_lead(j.target)
                           when j.status = 'preparing' then j.updated_at
                           else j.due_at end,
                      j.updated_at, coalesce(j.next_try_at, '-infinity'::timestamptz),
                      coalesce(j.lease_until, '-infinity'::timestamptz))
      from mc.autopost_jobs j
      where j.status in ('queued', 'preparing', 'ready')
        and (j.next_try_at is null or j.next_try_at <= now())
        and (j.lease_until is null or j.lease_until < now())
        and (now() >= j.late_until
             or case j.status when 'queued' then j.due_at - mc.autopost_lead(j.target) <= now()
                              when 'preparing' then true
                              else j.due_at <= now() end)) w) end
$$;

-- A post's auto-posting as the Marketing page shows it: each label with its target, whether it is connected and the
-- latest job (the current version's first; else an older version's, so "version 1 went out" can be told). A stopped
-- job carries why it stopped; a destination whose account's last token renewal failed counts as refused (check_ok
-- false), as Settings shows it.
create or replace function mc.autopost_card_json(p_card uuid)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select jsonb_build_object('enabled', mc.setting_on('autopost_enabled'), 'off', c.autopost_off,
    'covers', mc.autopost_covers(c.id), 'posts_any', mc.autopost_posts_any(c.id), 'blocked', mc.autopost_blocked(c.id),
    'due_at', mc.autopost_due_at(c.slot_day, c.slot_time, p.tz),
    'late_until', mc.autopost_late_until(c.slot_day, c.slot_time, p.tz),
    'targets', (select coalesce(jsonb_agg(jsonb_build_object('label', t.label, 'target', t.target,
                   'destination', t.destination, 'mode', t.mode, 'connected', d.key is not null,
                   'destination_name', d.name,
                   'check_ok', case when cn.last_error is not null then false else d.check_ok end,
                   'checked_at', d.checked_at,
                   'job', (select jsonb_build_object('id', j.id, 'version', j.version, 'status', j.status,
                             'attempts', j.attempts, 'external_url', j.external_url, 'error', j.error,
                             'error_code', j.error_code, 'due_at', j.due_at, 'late_until', j.late_until,
                             'finished_at', j.finished_at,
                             'next_try_at', j.next_try_at, 'updated_at', j.updated_at,
                             'confirmed', j.status = 'posted'
                                          and coalesce((select e.kind = 'confirmed_posted' from mc.autopost_events e
                                                        where e.job_id = j.id and e.kind in ('confirmed_posted', 'unconfirmed')
                                                        order by e.id desc limit 1), false),
                             'stop_note', case when j.status = 'cancelled' then
                                            (select e.note from mc.autopost_events e
                                             where e.job_id = j.id and e.kind = 'cancelled' order by e.id desc limit 1) end)
                           from mc.autopost_jobs j where j.card_id = c.id and j.target = t.target
                           order by (j.version = c.current_version) desc, j.version desc limit 1))
                 order by t.ord, t.target), '[]'::jsonb)
                from mc.autopost_targets(c.platforms) t
                left join mc.autopost_destinations d on d.key = t.destination
                left join mc.autopost_connections cn on cn.provider = d.provider))
  from mc.marketing_cards c join mc.marketing_packs p on p.id = c.pack_id
  where c.id = p_card
$$;

-- Auto-posting's settings for the page: never a token (ciphertexts included).
create or replace function mc.autopost_settings_json()
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select jsonb_build_object('enabled', mc.setting_on('autopost_enabled'),
    'clock', (select jsonb_build_object('set_at', s.clock_set_at, 'last_kick_at', s.last_kick_at,
                'last_claim_at', s.last_claim_at,
                'url_set', exists (select 1 from mc.settings x where x.key = 'autopost_tick_url'))
              from mc.autopost_state s where s.id),
    'connections', (select coalesce(jsonb_agg(jsonb_build_object('provider', c.provider, 'account_id', c.account_id,
                       'account_name', c.account_name, 'connected_at', c.connected_at, 'updated_at', c.updated_at,
                       'token_expires_at', c.token_expires_at, 'refresh_expires_at', c.refresh_expires_at,
                       'scopes', to_jsonb(c.scopes), 'last_error', c.last_error, 'last_error_at', c.last_error_at,
                       'options', (select coalesce(jsonb_agg(o - 'token_cipher' order by o ->> 'kind', o ->> 'name'), '[]'::jsonb)
                                   from jsonb_array_elements(c.options) o))
                     order by c.provider), '[]'::jsonb)
                    from mc.autopost_connections c),
    'destinations', (select coalesce(jsonb_object_agg(d.key, jsonb_build_object('provider', d.provider,
                        'external_id', d.external_id, 'name', d.name, 'updated_at', d.updated_at,
                        'checked_at', d.checked_at, 'check_ok', d.check_ok, 'check_note', d.check_note)), '{}'::jsonb)
                     from mc.autopost_destinations d),
    -- Something is due, and since when: with the clock's last run, Settings can tell a clock that stopped (and not
    -- mistake work queued a moment ago, before the clock's next minute, for a stop: design review round 4).
    'work_due', mc.autopost_work_due(), 'work_due_since', mc.autopost_work_due_since())
$$;

-- For the summary and Overview: approved posts whose automatic posting failed (or stopped and can no longer start
-- again: its window is over), couldn't be confirmed, or sits in TikTok drafts, each until the owner handles it (owner,
-- design review round 3: no week's limit); the accounts' health (destinations chosen, those that should work, the
-- platforms that need a reconnect: a refused Check or a failed token renewal); and the posting clock, so Overview can
-- say when auto-posting is on but can't post, or nothing is posting.
create or replace function mc.autopost_summary_json()
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  with found as (
    select j.*, case when j.status = 'cancelled' then 'failed' else j.status end as kind,
           c.key as card_key, c.title, c.slot_label, c.state, c.current_version, c.by_hand_done_at, p.key as pack_key
    from mc.autopost_jobs j
    join mc.marketing_cards c on c.id = j.card_id
    join mc.marketing_packs p on p.id = c.pack_id
    where j.version = c.current_version and c.state = 'approved'
      -- The owner answered for this platform ("Did you post it on …?"): handled.
      and mc.autopost_by_hand_entry(c.by_hand_posted || c.by_hand_skipped, j.label, j.target) is null
      and (j.status in ('failed', 'uncertain')
           or (j.status = 'sent_to_drafts' and c.by_hand_done_at is null)
           or (j.status = 'cancelled' and not c.autopost_off and mc.autopost_blocked(c.id) is null
               and now() >= mc.autopost_late_until(c.slot_day, c.slot_time, p.tz)))
  ), problems as (
    select f.*, row_number() over (partition by case when f.kind = 'sent_to_drafts' then 'drafts' else f.kind end
                                   order by f.due_at, f.target) as rn
    from found f
  )
  select jsonb_build_object('enabled', mc.setting_on('autopost_enabled'),
    'failed', coalesce((select jsonb_agg(jsonb_build_object('pack', x.pack_key, 'card', x.card_key, 'card_id', x.card_id,
                 'title', x.title, 'target', x.target, 'label', x.label, 'error', x.error,
                 'error_code', case when x.status = 'cancelled' then 'stopped' else x.error_code end,
                 'slot', x.due_at, 'slot_label', x.slot_label, 'at', x.updated_at) order by x.due_at, x.target)
               from problems x where x.kind = 'failed' and x.rn <= 10), '[]'::jsonb),
    'uncertain', coalesce((select jsonb_agg(jsonb_build_object('pack', x.pack_key, 'card', x.card_key, 'card_id', x.card_id,
                 'title', x.title, 'target', x.target, 'label', x.label, 'error', x.error, 'slot', x.due_at,
                 'slot_label', x.slot_label, 'at', x.updated_at) order by x.due_at, x.target)
               from problems x where x.kind = 'uncertain' and x.rn <= 10), '[]'::jsonb),
    'drafts_waiting', coalesce((select jsonb_agg(jsonb_build_object('pack', x.pack_key, 'card', x.card_key,
                 'card_id', x.card_id, 'title', x.title, 'target', x.target, 'label', x.label, 'slot', x.due_at,
                 'slot_label', x.slot_label, 'at', x.finished_at) order by x.due_at)
               from problems x where x.kind = 'sent_to_drafts' and x.rn <= 10), '[]'::jsonb),
    'counts', jsonb_build_object(
      'failed', (select count(*) from problems x where x.kind = 'failed'),
      'uncertain', (select count(*) from problems x where x.kind = 'uncertain'),
      'drafts_waiting', (select count(*) from problems x where x.kind = 'sent_to_drafts')),
    'clock', (select jsonb_build_object('set', s.clock_set_at is not null,
                'last_run', coalesce(s.last_claim_at, s.last_kick_at), 'work_due', mc.autopost_work_due(),
                'work_due_since', mc.autopost_work_due_since())
              from mc.autopost_state s where s.id),
    'accounts', (select jsonb_build_object('chosen', count(*), 'working', count(*) filter (where x.ok),
                   'reconnect', coalesce(jsonb_agg(distinct x.provider order by x.provider) filter (where not x.ok), '[]'::jsonb),
                   'chosen_keys', coalesce(jsonb_agg(x.key order by x.key), '[]'::jsonb),
                   'refused', coalesce(jsonb_agg(x.key order by x.key) filter (where not x.ok), '[]'::jsonb))
                 from (select d.key, d.provider, d.check_ok is distinct from false and cn.last_error is null as ok
                       from mc.autopost_destinations d join mc.autopost_connections cn on cn.provider = d.provider) x))
$$;

-- Keeps the owner's answers to the entries still in by_hand, and by_hand_done_at to "every entry has an answer" (an
-- entry added later, such as a platform taken over after the others were answered, is asked about again).
create or replace function mc.autopost_by_hand_settle(p_card uuid)
returns void
language sql
volatile
security definer
set search_path = ''
as $$
  update mc.marketing_cards c
    set by_hand_posted = array(select x from unnest(c.by_hand_posted) x where x = any(c.by_hand)),
        by_hand_skipped = array(select x from unnest(c.by_hand_skipped) x where x = any(c.by_hand)),
        by_hand_done_at = case when cardinality(c.by_hand) > 0
                                    and c.by_hand <@ (array(select x from unnest(c.by_hand_posted) x where x = any(c.by_hand))
                                                      || array(select x from unnest(c.by_hand_skipped) x where x = any(c.by_hand)))
                               then coalesce(c.by_hand_done_at, now()) end
    where c.id = p_card
$$;

-- After a job posted (or sent to drafts, or the owner took it by hand): the post is out at its version. The first such
-- job marks the version as in the apps; what the owner still finishes by hand (manual labels, TikTok drafts, and any
-- platform the owner took over) goes into by_hand, so the page asks "Did you post it on …?" after the slot. A label is
-- named whole only when every platform of it is the owner's; else just those platforms are ("Instagram Reels" when the
-- Facebook Reel went out), so the page never asks about one Mission Control posted (design review round 3). Nothing
-- changes when the post was revised or undone meanwhile.
create or replace function mc.autopost_mark_out(p_job uuid)
returns void
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_j mc.autopost_jobs%rowtype;
  v_c mc.marketing_cards%rowtype;
  v_by_hand text[];
begin
  select * into v_j from mc.autopost_jobs j where j.id = p_job;
  select * into v_c from mc.marketing_cards c where c.id = v_j.card_id for update;
  if not found or v_c.current_version <> v_j.version then
    return;
  end if;
  -- The owner's: a manual label; a target sent to drafts or taken by hand; a target whose destination can't post (not
  -- connected, disconnected or to reconnect) and that didn't go out (one that may have, uncertain, has its own buttons).
  select coalesce(array_agg(y.name order by y.ord), '{}') into v_by_hand
  from (
    select case when z.whole then z.label else z.platform end as name, min(z.ord) as ord
    from (
      select t.label, t.ord, b.mine, bool_and(b.mine) over (partition by t.label) as whole,
             case t.target when 'facebook_reel' then 'Facebook Reels' when 'instagram_reel' then 'Instagram Reels'
                           else t.label end as platform
      from mc.autopost_targets(v_c.platforms) t
      left join mc.autopost_jobs j on j.card_id = v_c.id and j.version = v_j.version and j.target = t.target
      cross join lateral (select t.target is null
                                 or coalesce(j.status in ('sent_to_drafts', 'by_hand'), false)
                                 or (coalesce(j.status, '') not in ('posted', 'sent_to_drafts', 'uncertain')
                                     and not mc.autopost_destination_ok(t.destination)) as mine) b
    ) z
    where z.mine
    group by case when z.whole then z.label else z.platform end
  ) y;
  if v_c.in_apps_version is distinct from v_j.version then
    update mc.marketing_cards c set in_apps_version = v_j.version, by_hand = v_by_hand, by_hand_done_at = null,
      by_hand_posted = '{}', by_hand_skipped = '{}', updated_at = now()
      where c.id = v_c.id;
  else
    -- A label (or a platform of one) Mission Control has now posted (its account came back) is never asked about again.
    update mc.marketing_cards c
      set by_hand = (select coalesce(array_agg(n.l order by mc.autopost_by_hand_order(v_c.platforms, n.l)), '{}')
                     from (select distinct l from unnest(c.by_hand || v_by_hand) l
                     where l not in (select x.label from (
                                       select t.label, bool_and(coalesce(j.status = 'posted', false)) as all_posted
                                       from mc.autopost_targets(v_c.platforms) t
                                       left join mc.autopost_jobs j on j.card_id = v_c.id and j.version = v_j.version
                                                                   and j.target = t.target
                                       group by t.label) x
                                     where x.all_posted)
                       and l not in (select case j.target when 'facebook_reel' then 'Facebook Reels' else 'Instagram Reels' end
                                     from mc.autopost_jobs j
                                     where j.card_id = v_c.id and j.version = v_j.version and j.status = 'posted'
                                       and j.target in ('facebook_reel', 'instagram_reel'))) n),
          updated_at = now()
      where c.id = v_c.id;
    perform mc.autopost_by_hand_settle(v_c.id);
  end if;
end;
$$;

-- A step taken at or after the slot whose run never reported (it died, or its result couldn't be saved) may have
-- posted: it becomes uncertain for the owner to look at, never handed out again (security review M1). Every result,
-- retry, cancellation and miss clears the lease, so a lease left behind means exactly that. A step taken before the
-- slot only prepared (a Reel's upload, an Instagram container, a status check): it goes out again.
create or replace function mc.autopost_settle_lapsed()
returns void
language plpgsql
volatile
security definer
set search_path = ''
as $$
begin
  with lapsed as (
    update mc.autopost_jobs j set status = 'uncertain', error_code = 'lease_lapsed', lease_id = null, lease_until = null,
      next_try_at = null, finished_at = now(), updated_at = now(), attempts = least(j.attempts + 1, 20),
      error = 'Mission Control may have posted this but couldn''t record it. Look on the platform, then tap It''s posted, or Try again if it isn''t there.'
    where j.status in ('queued', 'preparing', 'ready') and j.lease_id is not null and j.lease_until < now()
      and j.due_at <= j.lease_until - interval '3 minutes'
      and (j.status = 'ready'
           or (j.status = 'queued' and j.target in ('facebook_photo', 'klaroph_photo', 'youtube', 'tiktok'))
           or (j.status = 'preparing' and j.target in ('facebook_reel', 'instagram_reel')))
    returning j.id)
  insert into mc.autopost_events (job_id, kind, note, actor) select l.id, 'uncertain', 'lease_lapsed', 'system' from lapsed l;
end;
$$;

-- ================================================================================================ the owner's apps steps (recreated)
-- As in the marketing module, plus the owner's answer per by_hand entry once the slot has begun (design review round
-- 4): posted_there or not_posting_there (p_by_hand names the entries). posted_by_hand answers every open entry "posted";
-- by_hand_done_at is set once every entry has an answer. Scheduling, removing or posting late starts the answers over.
create or replace function mc_api.marketing_apps(p_card uuid, p_version integer, p_action text, p_by_hand text[])
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('marketing.apps', false, 300);
  v_c mc.marketing_cards%rowtype;
  v_before mc.marketing_cards%rowtype;
  v_tz text;
  v_pack text;
  v_wanted text[];
  v_by_hand text[];
  v_version integer;
  v_note text;
  v_kind text;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  perform mc.marketing_lock();
  if p_action is null or p_action not in ('in_apps', 'removed_from_apps', 'posted_by_hand', 'posted_late', 'posted_there',
                                          'not_posting_there') then
    return mc.failed('invalid', 'Unknown action');
  end if;
  select * into v_c from mc.marketing_cards c where c.id = p_card for update;
  if not found then
    return mc.failed('not_found', 'There is no such post');
  end if;
  if p_version is distinct from v_c.current_version then
    return mc.failed('stale_version', 'Claude changed this post a moment ago (now version ' || v_c.current_version || ').')
      || jsonb_build_object('current_version', v_c.current_version, 'state', v_c.state);
  end if;
  v_before := v_c;
  v_kind := p_action;
  select p.tz, p.key into v_tz, v_pack from mc.marketing_packs p where p.id = v_c.pack_id;
  if p_action = 'in_apps' then
    if v_c.state <> 'approved' then
      return mc.failed('bad_state', 'This post changed on another device. Reload to see it.')
        || jsonb_build_object('state', v_c.state);
    end if;
    -- A "Get it on Google Play" post can be approved ahead of launch, but not scheduled: it would go out too early.
    if mc.marketing_launch_too_early(v_c.phase, mc.marketing_slot(v_c.slot_day, v_c.slot_time, v_tz), v_c.slot_time is null, mc.marketing_pday()) then
      return mc.failed('launch_not_live', 'Mark Lucento as live first: this post says “Get it on Google Play”.');
    end if;
    if mc.marketing_after_launch(v_c.phase, mc.marketing_slot(v_c.slot_day, v_c.slot_time, v_tz), mc.marketing_pday()) then
      return mc.failed('launch_version_needed', 'This post says “coming soon” and its slot is after launch: ask Claude for a launch version first.');
    end if;
    v_wanted := array(select mc.marketing_text(h) from unnest(coalesce(p_by_hand, '{}'::text[])) h);
    if not mc.text_items_ok(v_wanted, 8, 60) or not (v_wanted <@ v_c.platforms) then
      return mc.failed('invalid', 'Choose the platforms you post by hand among this post''s platforms');
    end if;
    v_by_hand := array(select x.v from unnest(v_c.platforms) with ordinality x(v, o) where x.v = any(v_wanted) order by x.o);
    update mc.marketing_cards c
      set in_apps_version = c.current_version, by_hand = v_by_hand, by_hand_done_at = null, by_hand_posted = '{}',
          by_hand_skipped = '{}', updated_at = now()
      where c.id = p_card and c.current_version = p_version and c.state = 'approved'
      returning * into v_c;
    if not found then
      return mc.failed('bad_state', 'This post changed on another device. Reload to see it.');
    end if;
    v_version := v_c.current_version;
    v_note := case when cardinality(v_by_hand) > 0 then 'By hand at the time: ' || array_to_string(v_by_hand, ', ') end;
  elsif p_action = 'posted_late' then
    -- Approved but never scheduled, and its slot has begun: the owner posted it by hand (late). Recorded as posted by hand
    -- on every platform, not as scheduled in the apps.
    if v_c.state <> 'approved' or v_c.in_apps_version is not null then
      return mc.failed('bad_state', 'This post changed on another device. Reload to see it.')
        || jsonb_build_object('state', v_c.state);
    end if;
    if lower(mc.marketing_slot(v_c.slot_day, v_c.slot_time, v_tz)) > now() then
      return mc.failed('bad_state', 'Its time hasn''t come yet: schedule it instead.');
    end if;
    if mc.marketing_launch_too_early(v_c.phase, mc.marketing_slot(v_c.slot_day, v_c.slot_time, v_tz), v_c.slot_time is null, mc.marketing_pday()) then
      return mc.failed('launch_not_live', 'Mark Lucento as live first: this post says “Get it on Google Play”.');
    end if;
    if mc.marketing_after_launch(v_c.phase, mc.marketing_slot(v_c.slot_day, v_c.slot_time, v_tz), mc.marketing_pday()) then
      return mc.failed('launch_version_needed', 'This post says “coming soon” and its slot is after launch: ask Claude for a launch version first.');
    end if;
    update mc.marketing_cards c
      set in_apps_version = c.current_version, by_hand = c.platforms, by_hand_done_at = now(), by_hand_posted = c.platforms,
          by_hand_skipped = '{}', updated_at = now()
      where c.id = p_card and c.current_version = p_version and c.state = 'approved' and c.in_apps_version is null
      returning * into v_c;
    if not found then
      return mc.failed('bad_state', 'This post changed on another device. Reload to see it.');
    end if;
    v_version := v_c.current_version;
    v_note := 'Posted late, by hand';
    v_kind := 'posted_by_hand';
  elsif p_action = 'removed_from_apps' then
    if v_c.in_apps_version is null then
      return mc.failed('not_in_apps', 'This post isn''t marked as in the apps.');
    end if;
    v_version := v_c.in_apps_version;
    update mc.marketing_cards c set in_apps_version = null, by_hand = '{}', by_hand_done_at = null, by_hand_posted = '{}',
      by_hand_skipped = '{}', updated_at = now()
      where c.id = p_card and c.current_version = p_version and c.in_apps_version is not null
      returning * into v_c;
    if not found then
      return mc.failed('not_in_apps', 'This post isn''t marked as in the apps.');
    end if;
  else
    -- posted_by_hand, posted_there, not_posting_there: answers about the by_hand entries, once the slot has begun.
    if v_c.in_apps_version is null then
      return mc.failed('not_in_apps', 'This post isn''t marked as in the apps.');
    end if;
    if cardinality(v_c.by_hand) = 0 then
      return mc.failed('bad_state', 'Nothing of this post is posted by hand.');
    end if;
    if lower(mc.marketing_slot(v_c.slot_day, v_c.slot_time, v_tz)) > now() then
      return mc.failed('bad_state', 'Its time hasn''t come yet.');
    end if;
    if p_action = 'posted_by_hand' then
      if v_c.by_hand_done_at is not null then
        return jsonb_build_object('ok', true, 'already', true, 'state', v_c.state, 'in_apps_version', v_c.in_apps_version,
          'by_hand', to_jsonb(v_c.by_hand), 'by_hand_done_at', v_c.by_hand_done_at);
      end if;
      -- Every entry still open: posted.
      v_wanted := array(select x from unnest(v_c.by_hand) x where not (x = any(v_c.by_hand_skipped)));
    else
      v_wanted := array(select mc.marketing_text(h) from unnest(coalesce(p_by_hand, '{}'::text[])) h);
      if cardinality(v_wanted) = 0 or not mc.text_items_ok(v_wanted, 8, 60) or not (v_wanted <@ v_c.by_hand) then
        return mc.failed('invalid', 'Choose among the platforms you post by hand.');
      end if;
    end if;
    v_version := v_c.in_apps_version;
    update mc.marketing_cards c
      set by_hand_posted = array(select x.v from unnest(c.by_hand) with ordinality x(v, o)
                                 where case when p_action = 'not_posting_there' then x.v = any(c.by_hand_posted) and not (x.v = any(v_wanted))
                                            else x.v = any(c.by_hand_posted) or x.v = any(v_wanted) end
                                 order by x.o),
          by_hand_skipped = array(select x.v from unnest(c.by_hand) with ordinality x(v, o)
                                  where case when p_action = 'not_posting_there' then x.v = any(c.by_hand_skipped) or x.v = any(v_wanted)
                                             else x.v = any(c.by_hand_skipped) and not (x.v = any(v_wanted)) end
                                  order by x.o),
          updated_at = now()
      where c.id = p_card and c.current_version = p_version and c.in_apps_version is not null;
    if not found then
      return mc.failed('not_in_apps', 'This post isn''t marked as in the apps.');
    end if;
    perform mc.autopost_by_hand_settle(p_card);
    select * into v_c from mc.marketing_cards c where c.id = p_card;
    v_note := case when p_action = 'posted_by_hand' and cardinality(v_wanted) = cardinality(v_c.by_hand) then null
                   else array_to_string(v_wanted, ', ') end;
    v_kind := case when p_action = 'not_posting_there' then 'not_posted' else 'posted_by_hand' end;
  end if;
  insert into mc.marketing_log (card_id, version, kind, note, actor, admin_id)
    values (v_c.id, v_version, v_kind, v_note, 'admin', auth.uid());
  perform mc.audit('marketing.' || p_action, null, v_pack || '/' || v_c.key || ' v' || v_version, null, 'ok',
    jsonb_build_object('in_apps_version', v_before.in_apps_version, 'by_hand', v_before.by_hand,
                       'by_hand_posted', v_before.by_hand_posted, 'by_hand_skipped', v_before.by_hand_skipped),
    jsonb_build_object('in_apps_version', v_c.in_apps_version, 'by_hand', v_c.by_hand,
                       'by_hand_posted', v_c.by_hand_posted, 'by_hand_skipped', v_c.by_hand_skipped));
  return jsonb_build_object('ok', true, 'state', v_c.state, 'in_apps_version', v_c.in_apps_version,
    'by_hand', to_jsonb(v_c.by_hand), 'by_hand_done_at', v_c.by_hand_done_at,
    'by_hand_posted', to_jsonb(v_c.by_hand_posted), 'by_hand_skipped', to_jsonb(v_c.by_hand_skipped));
end;
$$;

-- ================================================================================================ the board (recreated)
-- The same as before, but due_soon ("approved but not scheduled") leaves out posts Mission Control will post itself.
create or replace function mc.marketing_status(p_pday timestamptz, p_card uuid default null)
returns table (
  card_id uuid, pack_id uuid, pack_key text, card_key text, title text, kind text, phase text, state text,
  current_version integer, in_apps_version integer, card_position integer, slot_day date, slot_time time, slot_label text,
  slot_start timestamptz, slot_end timestamptz, truth_pass boolean, version_created_at timestamptz, ready boolean,
  replace_after_launch boolean, too_early boolean, apps_stale boolean, due_soon boolean, needs_decision_soon boolean,
  missed boolean, media_missing boolean, uploading boolean
)
language sql
stable
security definer
set search_path = ''
as $$
  select c.id, c.pack_id, p.key, c.key, c.title, c.kind, c.phase, c.state, c.current_version, c.in_apps_version,
         c.position, c.slot_day, c.slot_time, c.slot_label, lower(s.slot), upper(s.slot), v.truth_pass, v.created_at, r.ready,
         -- Says "coming soon" but goes out after launch (still to decide, approved, or already in the apps), while its
         -- slot hasn't ended: one that already went out can't be replaced any more.
         mc.marketing_after_launch(c.phase, s.slot, p_pday) and upper(s.slot) > now()
           and (c.state in ('waiting', 'changes', 'approved') or c.in_apps_version is not null),
         -- Says "Get it on Google Play" but goes out before launch (or launch hasn't been marked yet).
         c.state not in ('skipped', 'withdrawn') and mc.marketing_launch_too_early(c.phase, s.slot, c.slot_time is null, p_pday),
         -- The apps hold an older version, or a post that is skipped or taken out.
         coalesce(c.in_apps_version is not null
                  and (c.in_apps_version < c.current_version or c.state in ('skipped', 'withdrawn')), false),
         -- Approved, not in the apps, and allowed to be scheduled (not a launch post before launch, nor a 'coming soon'
         -- one after it), within 36 hours, and not one Mission Control will post itself.
         coalesce(c.state = 'approved' and c.in_apps_version is null
                  and lower(s.slot) < now() + interval '36 hours' and upper(s.slot) > now()
                  and not mc.marketing_launch_too_early(c.phase, s.slot, c.slot_time is null, p_pday)
                  and not mc.marketing_after_launch(c.phase, s.slot, p_pday)
                  and not mc.autopost_posts_any(c.id), false),
         -- Waiting for the owner's yes within 36 hours (a post with Claude, or one that needs a launch version, isn't).
         coalesce(c.state = 'waiting' and not mc.marketing_after_launch(c.phase, s.slot, p_pday)
                  and lower(s.slot) < now() + interval '36 hours' and upper(s.slot) > now(), false),
         -- Passed without a yes, and not in the apps (a post in the apps went out at its slot).
         coalesce(c.state in ('waiting', 'changes') and c.in_apps_version is null and upper(s.slot) <= now(), false),
         coalesce(not r.ready and v.created_at <= now() - interval '2 hours', false),
         coalesce(not r.ready and v.created_at > now() - interval '2 hours', false)
  from mc.marketing_cards c
  join mc.marketing_packs p on p.id = c.pack_id
  join mc.marketing_versions v on v.card_id = c.id and v.version = c.current_version
  cross join lateral (select mc.marketing_slot(c.slot_day, c.slot_time, p.tz) as slot) s
  cross join lateral (select mc.marketing_version_ready(c.id, c.current_version) as ready) r
  where p_card is null or c.id = p_card
$$;

-- One post as the Marketing page shows it (§5.4 of MARKETING_APPROVALS.md), plus its auto-posting.
create or replace function mc.marketing_card_json(p_card uuid, p_pday timestamptz, p_seen bigint)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select jsonb_build_object(
    'id', s.card_id, 'key', s.card_key, 'position', s.card_position, 'kind', s.kind, 'phase', s.phase, 'title', s.title,
    'slot_day', s.slot_day, 'slot_time', left(s.slot_time::text, 5), 'slot_label', s.slot_label,
    'slot_start', s.slot_start, 'slot_end', s.slot_end, 'platforms', to_jsonb(c.platforms),
    'state', s.state, 'current_version', s.current_version, 'decided_at', c.decided_at,
    'decided_by', (select a.display_name from mc.admins a where a.user_id = c.decided_by),
    'in_apps_version', s.in_apps_version, 'by_hand', to_jsonb(c.by_hand), 'by_hand_done_at', c.by_hand_done_at,
    'by_hand_posted', to_jsonb(array(select x from unnest(c.by_hand_posted) x where x = any(c.by_hand))),
    'by_hand_skipped', to_jsonb(array(select x from unnest(c.by_hand_skipped) x where x = any(c.by_hand))),
    'withdrawn_reason', c.withdrawn_reason,
    'flags', jsonb_build_object('replace_after_launch', s.replace_after_launch, 'too_early', s.too_early,
      'apps_stale', s.apps_stale, 'due_soon', s.due_soon, 'needs_decision_soon', s.needs_decision_soon,
      'missed', s.missed, 'media_missing', s.media_missing, 'uploading', s.uploading),
    'version', jsonb_build_object('version', v.version, 'caption_label', v.caption_label, 'caption', v.caption,
      'yt_title', v.yt_title, 'yt_desc', v.yt_desc, 'alt_text', v.alt_text, 'truth', v.truth,
      'truth_pass', v.truth_pass, 'changes', v.changes, 'ready', s.ready, 'created_at', v.created_at),
    'previous', (select jsonb_build_object('version', pv.version, 'caption', pv.caption, 'yt_title', pv.yt_title,
                   'yt_desc', pv.yt_desc, 'alt_text', pv.alt_text, 'changes', pv.changes)
                 from mc.marketing_versions pv where pv.card_id = s.card_id and pv.version = s.current_version - 1),
    'media', (select coalesce(jsonb_agg(jsonb_build_object('position', m.position, 'file_id', f.id, 'kind', f.kind,
                'file_name', f.file_name, 'pathname', f.pathname, 'content_type', f.content_type, 'bytes', f.bytes,
                'width', f.width, 'height', f.height, 'duration_ms', f.duration_ms, 'ready', f.ready,
                'expired', coalesce(f.expires_at <= now(), false), 'deleted', f.deleted_at is not null)
                order by m.position), '[]'::jsonb)
              from mc.marketing_media m join mc.marketing_files f on f.id = m.file_id
              where m.card_id = s.card_id and m.version = s.current_version),
    'open_requests', (select coalesce(jsonb_agg(jsonb_build_object('log_id', o.log_id, 'version', o.version, 'kind', o.kind,
                        'note', o.note, 'tags', to_jsonb(o.tags), 'at', o.at, 'seen', p_seen >= o.log_id)
                        order by o.log_id), '[]'::jsonb)
                      from mc.marketing_open_requests(s.card_id) o),
    -- Claude's latest note, while no owner entry came after it.
    'latest_claude_note', (select jsonb_build_object('log_id', n.id, 'version', n.version, 'note', n.note, 'at', n.at)
                           from mc.marketing_log n
                           where n.card_id = s.card_id and n.kind = 'claude_note'
                             and n.id > coalesce((select max(o.id) from mc.marketing_log o
                                                  where o.card_id = s.card_id and o.actor = 'admin'), 0)
                           order by n.id desc limit 1),
    -- History: the owner's and Claude's entries, and what auto-posting did on each platform (posted, sent to drafts,
    -- didn't go out, couldn't be confirmed, stopped) with the owner's answers to it. An automatic entry's id is negative
    -- (its event's), so it never meets a log entry's.
    'log', (select coalesce(jsonb_agg(g.entry order by g.at desc, g.ord desc), '[]'::jsonb)
            from (select h.* from (
                    select x.at, x.id as ord,
                           jsonb_build_object('id', x.id, 'version', x.version, 'kind', x.kind, 'note', x.note,
                             'tags', to_jsonb(x.tags), 'actor', x.actor,
                             'by', (select a.display_name from mc.admins a where a.user_id = x.admin_id), 'at', x.at) as entry
                    from mc.marketing_log x where x.card_id = s.card_id
                    union all
                    select e.at, e.id,
                           jsonb_build_object('id', -e.id, 'version', j.version, 'kind', 'autopost', 'event', e.kind,
                             'target', j.target, 'note', e.note, 'tags', '[]'::jsonb, 'actor', e.actor,
                             'by', (select a.display_name from mc.admins a where a.user_id = e.admin_id), 'at', e.at)
                    from mc.autopost_events e join mc.autopost_jobs j on j.id = e.job_id
                    where j.card_id = s.card_id
                      and e.kind in ('posted', 'sent_to_drafts', 'failed', 'uncertain', 'cancelled', 'retry', 'by_hand',
                                     'confirmed_posted', 'unconfirmed')) h
                  order by h.at desc, h.ord desc limit 30) g),
    'autopost', mc.autopost_card_json(s.card_id))
  from mc.marketing_status(p_pday, p_card) s
  join mc.marketing_cards c on c.id = s.card_id
  join mc.marketing_versions v on v.card_id = s.card_id and v.version = s.current_version
$$;

create or replace function mc_api.marketing_board(p_pack text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('marketing.read');
  v_pack mc.marketing_packs%rowtype;
  v_l mc.marketing_launch%rowtype;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  select * into v_l from mc.marketing_launch l where l.id;
  if p_pack is not null then
    select * into v_pack from mc.marketing_packs p where p.key = p_pack;
    if not found then
      return mc.failed('not_found', 'There is no such pack');
    end if;
  else
    select * into v_pack from mc.marketing_packs p
      where exists (select 1 from mc.marketing_cards c where c.pack_id = p.id and c.state in ('waiting', 'changes'))
      order by p.starts_on desc, p.created_at desc
      limit 1;
    if not found then
      select * into v_pack from mc.marketing_packs p order by p.starts_on desc, p.created_at desc limit 1;
    end if;
  end if;
  return jsonb_build_object('ok', true, 'now', now(), 'tz', mc.tz(), 'environment', mc.marketing_environment(),
    'launch', mc.marketing_launch_json(), 'claude_checked_at', v_l.claude_checked_at,
    'packs', mc.marketing_packs_json(),
    'pack', case when v_pack.id is null then null
                 else jsonb_build_object('id', v_pack.id, 'key', v_pack.key, 'title', v_pack.title, 'starts_on', v_pack.starts_on,
                        'ends_on', v_pack.ends_on, 'tz', v_pack.tz, 'created_at', v_pack.created_at,
                        'updated_at', v_pack.updated_at) end,
    'numbers', (select jsonb_build_object('followers', n.followers, 'views', n.views, 'installs', n.installs,
                  'listing_visitors', n.listing_visitors, 'updated_at', n.updated_at,
                  'updated_by', (select a.display_name from mc.admins a where a.user_id = n.updated_by))
                from mc.marketing_numbers n where n.pack_id = v_pack.id),
    'cards', (select coalesce(jsonb_agg(mc.marketing_card_json(c.id, v_l.pday_at, v_l.claude_seen_log_id)
                order by c.slot_day nulls last, c.slot_time nulls last, c.position, c.key), '[]'::jsonb)
              from mc.marketing_cards c where c.pack_id = v_pack.id),
    'autopost', mc.autopost_settings_json());
end;
$$;

create or replace function mc_api.marketing_summary()
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('marketing.read');
  v_l mc.marketing_launch%rowtype;
  v_waiting jsonb;
  v_soon jsonb;
  v_due jsonb;
  v_stale jsonb;
  v_after jsonb;
  v_early jsonb;
  v_missing jsonb;
  v_unseen jsonb;
  v_unseen_count integer;
  v_after_in_apps integer;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  select * into v_l from mc.marketing_launch l where l.id;
  -- The same posts as replace_after_launch's list (those that haven't gone out), counted whole.
  select count(*) into v_after_in_apps from mc.marketing_status(v_l.pday_at) s
    where s.replace_after_launch and s.in_apps_version is not null and (s.slot_end is null or s.slot_end > now());
  v_waiting := mc.marketing_waiting();
  v_soon := mc.marketing_flag_items('needs_decision_soon', 10);
  v_due := mc.marketing_flag_items('due_soon', 10);
  v_stale := mc.marketing_flag_items('apps_stale', 10);
  v_after := mc.marketing_flag_items('replace_after_launch', 10);
  v_early := mc.marketing_flag_items('too_early', 10);
  v_missing := mc.marketing_flag_items('media_missing', 10);
  select count(*), coalesce(jsonb_agg(x.j order by x.slot_start nulls last, x.log_id) filter (where x.rn <= 10), '[]'::jsonb)
    into v_unseen_count, v_unseen
    from (select o.log_id, s.slot_start,
                 row_number() over (order by s.slot_start nulls last, o.log_id) as rn,
                 jsonb_build_object('pack', s.pack_key, 'card', s.card_key, 'card_id', s.card_id, 'title', s.title,
                   'state', s.state, 'current_version', s.current_version, 'slot', s.slot_start, 'slot_end', s.slot_end,
                   'slot_day', s.slot_day, 'slot_time', left(s.slot_time::text, 5), 'slot_label', s.slot_label,
                   'log_id', o.log_id, 'kind', o.kind, 'tags', to_jsonb(o.tags), 'at', o.at) as j
          from mc.marketing_open_requests() o
          join mc.marketing_status(v_l.pday_at) s on s.card_id = o.card_id
          where o.log_id > v_l.claude_seen_log_id) x;
  return jsonb_build_object('ok', true,
    'waiting', (v_waiting ->> 'count')::integer, 'first_slot', v_waiting -> 'first_slot',
    'needs_decision_soon', v_soon -> 'items', 'due_soon', v_due -> 'items', 'apps_stale', v_stale -> 'items',
    'replace_after_launch', v_after -> 'items', 'too_early', v_early -> 'items',
    'missed', (select count(*) from mc.marketing_status(v_l.pday_at) s where s.missed),
    'media_missing', (v_missing ->> 'count')::integer, 'media_missing_items', v_missing -> 'items',
    'media_missing_since', (select min(s.version_created_at) from mc.marketing_status(v_l.pday_at) s
                            where s.media_missing and s.state not in ('skipped', 'withdrawn')
                              and (s.slot_end is null or s.slot_end > now())),
    'unseen_requests', v_unseen,
    'counts', jsonb_build_object('needs_decision_soon', v_soon -> 'count', 'due_soon', v_due -> 'count',
                'apps_stale', v_stale -> 'count', 'replace_after_launch', v_after -> 'count',
                'replace_after_launch_in_apps', v_after_in_apps,
                'too_early', v_early -> 'count', 'unseen_requests', v_unseen_count),
    'claude_checked_at', v_l.claude_checked_at,
    'autopost', mc.autopost_summary_json());
end;
$$;

-- ================================================================================================ the owner's functions
create or replace function mc_api.autopost_settings()
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('marketing.read');
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  return jsonb_build_object('ok', true) || mc.autopost_settings_json();
end;
$$;

-- Turning auto-posting on asks for a fresh authenticator code and needs the clock and at least one destination;
-- turning it off never asks.
create or replace function mc_api.autopost_set_enabled(p_on boolean)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('marketing.autopost_enable', coalesce(p_on, false), 20);
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  if p_on is null then
    return mc.failed('invalid', 'On or off?');
  end if;
  perform mc.marketing_lock();
  if p_on and not exists (select 1 from mc.autopost_state s where s.id and s.clock_set_at is not null) then
    return mc.failed('clock_not_set', 'Set up the posting clock in Supabase first (DEPLOYMENT.md part J).');
  end if;
  if p_on and not exists (select 1 from mc.autopost_destinations) then
    return mc.failed('nothing_connected', 'Connect at least one account first.');
  end if;
  insert into mc.settings (key, value, updated_by, updated_at) values ('autopost_enabled', to_jsonb(p_on), auth.uid(), now())
    on conflict (key) do update set value = excluded.value, updated_by = excluded.updated_by, updated_at = excluded.updated_at;
  if not p_on then
    -- The steps waiting stop now (a step under way finishes on its own); turning it on again starts them over.
    perform mc.autopost_settle_lapsed();
    with gone as (
      update mc.autopost_jobs j set status = 'cancelled', finished_at = now(), updated_at = now(), next_try_at = null,
        lease_id = null, lease_until = null
      where j.status in ('queued', 'preparing', 'ready') and (j.lease_until is null or j.lease_until < now())
      returning j.id)
    insert into mc.autopost_events (job_id, kind, note, actor, admin_id)
      select g.id, 'cancelled', 'autopost_off', 'admin', auth.uid() from gone g;
  end if;
  perform mc.audit(case when p_on then 'marketing.autopost_on' else 'marketing.autopost_off' end, null, 'autopost', null, 'ok');
  return jsonb_build_object('ok', true, 'enabled', p_on);
end;
$$;

-- What a provider's account offers, checked: [{kind, id, name, page_id?, token_cipher?}] with the kinds that provider
-- has (Pages carry their own encrypted token; an Instagram account names the Page it is posted through).
create or replace function mc.autopost_options_ok(p_provider text, p jsonb)
returns boolean
language sql
immutable
set search_path = ''
as $$
  select jsonb_typeof(p) = 'array' and jsonb_array_length(p) <= 100 and not exists (
    select 1 from jsonb_array_elements(p) o
    where jsonb_typeof(o) <> 'object'
       or not coalesce(case p_provider when 'meta' then o ->> 'kind' in ('page', 'instagram')
                                       when 'google' then o ->> 'kind' = 'youtube'
                                       when 'tiktok' then o ->> 'kind' = 'tiktok' end, false)
       or not mc.autopost_id_ok(o ->> 'id')
       or char_length(coalesce(o ->> 'name', '')) > 200
       or (o ->> 'kind' = 'page' and not mc.autopost_cipher_ok(o ->> 'token_cipher'))
       or (o ->> 'kind' <> 'page' and o ? 'token_cipher')
       or (o ->> 'kind' = 'instagram' and not mc.autopost_id_ok(o ->> 'page_id')))
$$;

-- A destination from the connection's options: a Page (lucento_page, klaroph_page), the Instagram account (posted with
-- its Page's token), the YouTube channel, the TikTok account. Answers the destination row's values or null.
create or replace function mc.autopost_option_for(p_key text, p_id text)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select jsonb_build_object('provider', c.provider, 'external_id', o ->> 'id', 'name', coalesce(o ->> 'name', ''),
    'token_cipher', case o ->> 'kind'
                      when 'page' then o ->> 'token_cipher'
                      when 'instagram' then (select pg ->> 'token_cipher' from jsonb_array_elements(c.options) pg
                                             where pg ->> 'kind' = 'page' and pg ->> 'id' = o ->> 'page_id' limit 1)
                    end)
  from mc.autopost_connections c cross join lateral jsonb_array_elements(c.options) o
  where c.provider = case p_key when 'youtube' then 'google' when 'tiktok' then 'tiktok' else 'meta' end
    and o ->> 'id' = p_id
    and o ->> 'kind' = case p_key when 'lucento_page' then 'page' when 'klaroph_page' then 'page' when 'instagram' then 'instagram'
                                  when 'youtube' then 'youtube' else 'tiktok' end
  limit 1
$$;

-- Sets (or clears, with p_id null) one destination. Internal: the owner's functions check the gate first.
create or replace function mc.autopost_put_destination(p_key text, p_id text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_o jsonb;
begin
  if p_id is null then
    delete from mc.autopost_destinations d where d.key = p_key;
    return jsonb_build_object('ok', true, 'cleared', true);
  end if;
  v_o := mc.autopost_option_for(p_key, p_id);
  if v_o is null or (v_o ->> 'provider' = 'meta' and v_o ->> 'token_cipher' is null) then
    return mc.failed('not_found', 'That account isn''t one the connection offers.');
  end if;
  insert into mc.autopost_destinations (key, provider, external_id, name, token_cipher, set_by, updated_at)
    values (p_key, v_o ->> 'provider', v_o ->> 'external_id', v_o ->> 'name', v_o ->> 'token_cipher',
            coalesce(auth.uid(), '00000000-0000-0000-0000-000000000000'), now())
    on conflict (key) do update set provider = excluded.provider, external_id = excluded.external_id, name = excluded.name,
      token_cipher = excluded.token_cipher, set_by = excluded.set_by, updated_at = excluded.updated_at,
      checked_at = null, check_ok = null, check_note = null;
  return jsonb_build_object('ok', true);
end;
$$;

-- After the provider's sign-in (OAuth) the console saves the account with its tokens encrypted, and what it can post to.
-- Destinations still offered are refreshed (new Page tokens), those no longer offered are cleared, and empty ones are
-- picked when the choice is plain: the Page named Lucento, the Page named KlaroPH, the Instagram account linked to the
-- Lucento Page, the only YouTube channel, the TikTok account. Asks for a fresh authenticator code.
create or replace function mc_api.autopost_save_connection(p_provider text, p_account_id text, p_account_name text,
  p_token_cipher text, p_refresh_cipher text, p_token_expires_at timestamptz, p_refresh_expires_at timestamptz,
  p_scopes text[], p_options jsonb)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('marketing.autopost_connect', true, 30);
  v_d record;
  v_pick text;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  if p_provider is null or p_provider not in ('meta', 'google', 'tiktok') then
    return mc.failed('invalid', 'Unknown provider');
  end if;
  if not mc.autopost_id_ok(p_account_id) or char_length(coalesce(p_account_name, '')) > 200
     or (p_token_cipher is null and p_provider <> 'meta')
     or (p_token_cipher is not null and not mc.autopost_cipher_ok(p_token_cipher))
     or (p_refresh_cipher is not null and not mc.autopost_cipher_ok(p_refresh_cipher))
     or not mc.text_items_ok(coalesce(p_scopes, '{}'), 30, 200)
     or not mc.autopost_options_ok(p_provider, coalesce(p_options, '[]'::jsonb)) then
    return mc.failed('invalid', 'The account details aren''t in the expected form');
  end if;
  perform mc.marketing_lock();
  insert into mc.autopost_connections (provider, account_id, account_name, token_cipher, refresh_cipher, token_expires_at,
      refresh_expires_at, scopes, options, connected_by, connected_at, updated_at, last_error, last_error_at)
    values (p_provider, p_account_id, coalesce(p_account_name, ''), p_token_cipher, p_refresh_cipher, p_token_expires_at,
            p_refresh_expires_at, coalesce(p_scopes, '{}'), coalesce(p_options, '[]'::jsonb), auth.uid(), now(), now(), null, null)
    on conflict (provider) do update set account_id = excluded.account_id, account_name = excluded.account_name,
      token_cipher = excluded.token_cipher, refresh_cipher = excluded.refresh_cipher,
      token_expires_at = excluded.token_expires_at, refresh_expires_at = excluded.refresh_expires_at,
      scopes = excluded.scopes, options = excluded.options, connected_by = excluded.connected_by,
      connected_at = excluded.connected_at, updated_at = excluded.updated_at, last_error = null, last_error_at = null;
  -- Refresh or clear the destinations this provider had.
  for v_d in select d.key, d.external_id from mc.autopost_destinations d where d.provider = p_provider loop
    if mc.autopost_option_for(v_d.key, v_d.external_id) is null then
      perform mc.autopost_put_destination(v_d.key, null);
    else
      perform mc.autopost_put_destination(v_d.key, v_d.external_id);
    end if;
  end loop;
  -- Pick the plain choices.
  if p_provider = 'meta' then
    if not exists (select 1 from mc.autopost_destinations where key = 'lucento_page') then
      select min(o ->> 'id') into v_pick from jsonb_array_elements(p_options) o
        where o ->> 'kind' = 'page' and o ->> 'name' ilike '%lucento%'
        having count(*) = 1;
      if v_pick is not null then perform mc.autopost_put_destination('lucento_page', v_pick); end if;
    end if;
    if not exists (select 1 from mc.autopost_destinations where key = 'klaroph_page') then
      select min(o ->> 'id') into v_pick from jsonb_array_elements(p_options) o
        where o ->> 'kind' = 'page' and o ->> 'name' ilike '%klaro%'
        having count(*) = 1;
      if v_pick is not null then perform mc.autopost_put_destination('klaroph_page', v_pick); end if;
    end if;
    if not exists (select 1 from mc.autopost_destinations where key = 'instagram') then
      select min(o ->> 'id') into v_pick from jsonb_array_elements(p_options) o
        where o ->> 'kind' = 'instagram'
          and o ->> 'page_id' = (select d.external_id from mc.autopost_destinations d where d.key = 'lucento_page')
        having count(*) = 1;
      if v_pick is null then
        select min(o ->> 'id') into v_pick from jsonb_array_elements(p_options) o
          where o ->> 'kind' = 'instagram' having count(*) = 1;
      end if;
      if v_pick is not null then perform mc.autopost_put_destination('instagram', v_pick); end if;
    end if;
  elsif not exists (select 1 from mc.autopost_destinations d where d.key = case p_provider when 'google' then 'youtube' else 'tiktok' end) then
    select min(o ->> 'id') into v_pick from jsonb_array_elements(p_options) o having count(*) = 1;
    if v_pick is not null then
      perform mc.autopost_put_destination(case p_provider when 'google' then 'youtube' else 'tiktok' end, v_pick);
    end if;
  end if;
  perform mc.audit('marketing.autopost_connect', null, p_provider, null, 'ok', null, null, left(p_account_name, 200));
  return jsonb_build_object('ok', true) || mc.autopost_settings_json();
end;
$$;

create or replace function mc_api.autopost_set_destination(p_key text, p_id text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('marketing.autopost_destination', true, 60);
  v_r jsonb;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  if p_key is null or p_key not in ('lucento_page', 'klaroph_page', 'instagram', 'youtube', 'tiktok') then
    return mc.failed('invalid', 'Unknown destination');
  end if;
  perform mc.marketing_lock();
  v_r := mc.autopost_put_destination(p_key, p_id);
  if not (v_r ->> 'ok')::boolean then
    return v_r;
  end if;
  perform mc.audit('marketing.autopost_destination', null, p_key, null, 'ok', null, null,
                   (select d.name from mc.autopost_destinations d where d.key = p_key));
  return jsonb_build_object('ok', true) || mc.autopost_settings_json();
end;
$$;

-- Removes the account, its tokens and its destinations. Jobs not started for those destinations are cancelled; a step
-- already under way finishes or fails on its own.
create or replace function mc_api.autopost_disconnect(p_provider text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('marketing.autopost_disconnect', true, 20);
  v_name text;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  perform mc.marketing_lock();
  select c.account_name into v_name from mc.autopost_connections c where c.provider = p_provider;
  if not found then
    return mc.failed('not_found', 'That account isn''t connected.');
  end if;
  perform mc.autopost_settle_lapsed();
  with gone as (
    update mc.autopost_jobs j set status = 'cancelled', finished_at = now(), updated_at = now(), next_try_at = null,
      lease_id = null, lease_until = null
    where j.status in ('queued', 'preparing', 'ready') and (j.lease_until is null or j.lease_until < now())
      and j.destination in (select d.key from mc.autopost_destinations d where d.provider = p_provider)
    returning j.id)
  insert into mc.autopost_events (job_id, kind, note, actor, admin_id)
    select g.id, 'cancelled', 'disconnected', 'admin', auth.uid() from gone g;
  delete from mc.autopost_connections c where c.provider = p_provider;
  perform mc.audit('marketing.autopost_disconnect', null, p_provider, null, 'ok', null, null, left(v_name, 200));
  return jsonb_build_object('ok', true) || mc.autopost_settings_json();
end;
$$;

-- The console's Check of one destination: whether the platform answered and, if not, why (shown in Settings and on the
-- posts). Cleared whenever the destination or its token changes.
create or replace function mc_api.autopost_record_check(p_key text, p_ok boolean, p_note text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('marketing.autopost_check', false, 120);
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  if p_ok is null or p_key is null then
    return mc.failed('invalid', 'Which destination, and did it work?');
  end if;
  update mc.autopost_destinations d set checked_at = now(), check_ok = p_ok,
    check_note = case when p_ok then null else left(nullif(btrim(p_note), ''), 300) end
    where d.key = p_key;
  if not found then
    return mc.failed('not_found', 'That destination isn''t set.');
  end if;
  -- A Check that passed proves the account's sign-in works: an earlier failed token renewal no longer stands.
  if p_ok then
    update mc.autopost_connections c set last_error = null, last_error_at = null
      where c.provider = (select d.provider from mc.autopost_destinations d where d.key = p_key) and c.last_error is not null;
  end if;
  return jsonb_build_object('ok', true);
end;
$$;

-- The encrypted tokens for one destination, for the console's "Check" (it alone holds the key that opens them).
create or replace function mc_api.autopost_credentials(p_key text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('marketing.autopost_credentials', false, 120);
  v jsonb;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  select jsonb_build_object('ok', true, 'key', d.key, 'provider', d.provider, 'external_id', d.external_id, 'name', d.name,
           'account_id', c.account_id, 'token_cipher', coalesce(d.token_cipher, c.token_cipher),
           'refresh_cipher', case when d.provider = 'meta' then null else c.refresh_cipher end,
           'token_expires_at', case when d.provider = 'meta' then null else c.token_expires_at end,
           'refresh_expires_at', c.refresh_expires_at)
    into v
    from mc.autopost_destinations d join mc.autopost_connections c on c.provider = d.provider
    where d.key = p_key;
  return coalesce(v, mc.failed('not_found', 'That destination isn''t set.'));
end;
$$;

-- New tokens after a refresh (YouTube's hourly access token, TikTok's daily one), or the error that stopped it. Only
-- for the account it was renewed for: a reconnect meanwhile, as another account, is left alone (security review L2).
create or replace function mc.autopost_put_token(p_provider text, p_account_id text, p_token_cipher text,
  p_token_expires_at timestamptz, p_refresh_cipher text, p_refresh_expires_at timestamptz, p_error text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
begin
  if p_error is not null then
    update mc.autopost_connections c set last_error = left(p_error, 500), last_error_at = now(), updated_at = now()
      where c.provider = p_provider and c.account_id = p_account_id;
    return jsonb_build_object('ok', found);
  end if;
  if not mc.autopost_cipher_ok(p_token_cipher) or (p_refresh_cipher is not null and not mc.autopost_cipher_ok(p_refresh_cipher)) then
    return mc.failed('invalid', 'The token isn''t in the expected form');
  end if;
  update mc.autopost_connections c set token_cipher = p_token_cipher, token_expires_at = p_token_expires_at,
    refresh_cipher = coalesce(p_refresh_cipher, c.refresh_cipher),
    refresh_expires_at = case when p_refresh_cipher is null then c.refresh_expires_at else p_refresh_expires_at end,
    last_error = null, last_error_at = null, updated_at = now()
    where c.provider = p_provider and c.account_id = p_account_id and c.provider in ('google', 'tiktok');
  return jsonb_build_object('ok', found);
end;
$$;

create or replace function mc_api.autopost_save_token(p_provider text, p_account_id text, p_token_cipher text,
  p_token_expires_at timestamptz, p_refresh_cipher text, p_refresh_expires_at timestamptz, p_error text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('marketing.autopost_token', false, 120);
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  return mc.autopost_put_token(p_provider, p_account_id, p_token_cipher, p_token_expires_at, p_refresh_cipher,
                               p_refresh_expires_at, p_error);
end;
$$;

-- The owner on one job: retry (a failed, unconfirmed or stopped one, when Mission Control may post it now: auto-posting
-- on, the post approved at this version, its account set), posted (an unconfirmed one is up after all), by_hand (the
-- owner posts this platform: the job stops and the label joins "Did you post it on …?"), not_posting (the owner won't
-- post it there: as by_hand, answered "not posting there" at once) or not_posted (the owner's "It's posted" taken back:
-- unconfirmed again; design review round 4).
create or replace function mc_api.autopost_job_action(p_job uuid, p_action text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('marketing.decide', false, 300);
  v_j mc.autopost_jobs%rowtype;
  v_c mc.marketing_cards%rowtype;
  v_ref text;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  if p_action is null or p_action not in ('retry', 'posted', 'by_hand', 'not_posting', 'not_posted') then
    return mc.failed('invalid', 'Retry, posted, by hand, not posting or not posted?');
  end if;
  perform mc.marketing_lock();
  select * into v_j from mc.autopost_jobs j where j.id = p_job for update;
  if not found then
    return mc.failed('not_found', 'There is no such job');
  end if;
  select * into v_c from mc.marketing_cards c where c.id = v_j.card_id;
  v_ref := (select p.key from mc.marketing_packs p where p.id = v_c.pack_id) || '/' || v_c.key || ' v' || v_j.version
           || ' ' || v_j.target;
  if v_j.lease_until is not null and v_j.lease_until > now() then
    return mc.failed('busy', 'Mission Control is posting this right now. Try again in a minute.');
  end if;
  if p_action = 'retry' then
    if v_j.status not in ('failed', 'uncertain', 'cancelled') then
      return mc.failed('bad_state', 'Only a failed, unconfirmed or stopped post can be tried again.');
    end if;
    if v_c.state <> 'approved' or v_c.current_version <> v_j.version then
      return mc.failed('bad_state', 'This post is no longer the approved version.');
    end if;
    -- A retry that the next run would stop again is refused here, in words (design review round 2).
    if not exists (select 1 from mc.autopost_candidates() x
                   where x.card_id = v_j.card_id and x.version = v_j.version and x.target = v_j.target) then
      return mc.failed('not_allowed', case
        when not mc.setting_on('autopost_enabled') then 'Auto-posting is off. Turn it on in Settings first, or post it by hand.'
        when exists (select 1 from mc.autopost_destinations d where d.key = v_j.destination)
             and not mc.autopost_destination_ok(v_j.destination)
          then 'Its account needs a reconnect first (Settings), or post it by hand.'
        else 'Mission Control can''t post this one now: check its account in Settings, or post it by hand.' end);
    end if;
    update mc.autopost_jobs j set status = 'queued', attempts = 0, next_try_at = null, error = null, error_code = null,
      lease_id = null, lease_until = null,
      due_at = least(j.due_at, now()), late_until = greatest(j.late_until, now() + interval '1 hour'),
      detail = '{}'::jsonb, external_id = null, external_url = null, finished_at = null, updated_at = now()
      where j.id = p_job;
    insert into mc.autopost_events (job_id, kind, actor, admin_id) values (p_job, 'retry', 'admin', auth.uid());
    perform mc.audit('marketing.autopost_retry', null, v_ref, null, 'ok');
  elsif p_action = 'posted' then
    if v_j.status <> 'uncertain' then
      return mc.failed('bad_state', 'Only an unconfirmed post can be marked as posted.');
    end if;
    update mc.autopost_jobs j set status = 'posted', finished_at = now(), updated_at = now(), lease_id = null,
      lease_until = null where j.id = p_job;
    insert into mc.autopost_events (job_id, kind, actor, admin_id) values (p_job, 'confirmed_posted', 'admin', auth.uid());
    perform mc.autopost_mark_out(p_job);
    perform mc.audit('marketing.autopost_confirmed', null, v_ref, null, 'ok');
  elsif p_action = 'not_posted' then
    -- Only the owner's own "It's posted" can be taken back (never a post Mission Control saw go out).
    if v_j.status <> 'posted' or coalesce((select e.kind from mc.autopost_events e
                                           where e.job_id = p_job and e.kind in ('confirmed_posted', 'unconfirmed')
                                           order by e.id desc limit 1), '') <> 'confirmed_posted' then
      return mc.failed('bad_state', 'Only a post you marked as posted can be taken back.');
    end if;
    update mc.autopost_jobs j set status = 'uncertain', finished_at = now(), updated_at = now() where j.id = p_job;
    insert into mc.autopost_events (job_id, kind, actor, admin_id) values (p_job, 'unconfirmed', 'admin', auth.uid());
    -- Out only through its other platforms now; nothing else out: not in the apps any more.
    if exists (select 1 from mc.autopost_jobs j where j.card_id = v_j.card_id and j.version = v_j.version
               and j.status in ('posted', 'sent_to_drafts', 'by_hand')) then
      perform mc.autopost_mark_out((select j.id from mc.autopost_jobs j where j.card_id = v_j.card_id and j.version = v_j.version
                                    and j.status in ('posted', 'sent_to_drafts', 'by_hand') order by j.updated_at desc limit 1));
    else
      update mc.marketing_cards c set in_apps_version = null, by_hand = '{}', by_hand_posted = '{}', by_hand_skipped = '{}',
        by_hand_done_at = null, updated_at = now()
        where c.id = v_j.card_id and c.in_apps_version = v_j.version;
    end if;
    perform mc.audit('marketing.autopost_unconfirmed', null, v_ref, null, 'ok');
  else
    if v_j.status not in ('queued', 'preparing', 'ready', 'failed', 'uncertain', 'cancelled') then
      return mc.failed('bad_state', 'This one is already done.');
    end if;
    update mc.autopost_jobs j set status = 'by_hand', finished_at = now(), updated_at = now(), next_try_at = null,
      lease_id = null, lease_until = null
      where j.id = p_job;
    insert into mc.autopost_events (job_id, kind, note, actor, admin_id)
      values (p_job, 'by_hand', case when p_action = 'not_posting' then 'not_posting' end, 'admin', auth.uid());
    perform mc.autopost_mark_out(p_job);
    if p_action = 'not_posting' then
      -- The answer for its by_hand entry ("Instagram Reels", or the whole label): not posting there.
      update mc.marketing_cards c
        set by_hand_skipped = array(select x.v from unnest(c.by_hand) with ordinality x(v, o)
                                    where x.v = any(c.by_hand_skipped) or x.v = mc.autopost_by_hand_entry(c.by_hand, v_j.label, v_j.target)
                                    order by x.o),
            by_hand_posted = array(select x from unnest(c.by_hand_posted) x
                                   where x is distinct from mc.autopost_by_hand_entry(c.by_hand, v_j.label, v_j.target)),
            updated_at = now()
        where c.id = v_j.card_id and mc.autopost_by_hand_entry(c.by_hand, v_j.label, v_j.target) is not null;
      perform mc.autopost_by_hand_settle(v_j.card_id);
      insert into mc.marketing_log (card_id, version, kind, note, actor, admin_id)
        select c.id, v_j.version, 'not_posted', mc.autopost_by_hand_entry(c.by_hand, v_j.label, v_j.target), 'admin', auth.uid()
        from mc.marketing_cards c
        where c.id = v_j.card_id and mc.autopost_by_hand_entry(c.by_hand, v_j.label, v_j.target) is not null;
    end if;
    perform mc.audit(case when p_action = 'not_posting' then 'marketing.autopost_not_posting' else 'marketing.autopost_by_hand' end,
      null, v_ref, null, 'ok');
  end if;
  return jsonb_build_object('ok', true, 'status', (select j.status from mc.autopost_jobs j where j.id = p_job));
end;
$$;

-- "Post this one by hand" (p_off true) or back to automatic, before Mission Control has started on that version.
create or replace function mc_api.autopost_card(p_card uuid, p_version integer, p_off boolean)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('marketing.decide', false, 300);
  v_c mc.marketing_cards%rowtype;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  if p_off is null then
    return mc.failed('invalid', 'By hand or automatic?');
  end if;
  perform mc.marketing_lock();
  select * into v_c from mc.marketing_cards c where c.id = p_card for update;
  if not found then
    return mc.failed('not_found', 'There is no such post');
  end if;
  if v_c.current_version <> p_version then
    return jsonb_build_object('ok', false, 'error', 'stale_version', 'current_version', v_c.current_version);
  end if;
  -- A step under way may be posting right now (security review L1).
  if p_off and exists (select 1 from mc.autopost_jobs j where j.card_id = p_card and j.version = p_version
                       and j.lease_until > now()) then
    return mc.failed('busy', 'Mission Control is posting this right now. Try again in a minute.');
  end if;
  perform mc.autopost_settle_lapsed();
  if p_off and exists (select 1 from mc.autopost_jobs j where j.card_id = p_card and j.version = p_version
                       and j.status in ('preparing', 'ready', 'posted', 'sent_to_drafts', 'uncertain')) then
    return mc.failed('already_started', 'Mission Control has already started posting this one.');
  end if;
  if p_off then
    with gone as (
      update mc.autopost_jobs j set status = 'cancelled', finished_at = now(), updated_at = now(), next_try_at = null,
        lease_id = null, lease_until = null
      where j.card_id = p_card and j.version = p_version and j.status = 'queued'
        and (j.lease_until is null or j.lease_until < now())
      returning j.id)
    insert into mc.autopost_events (job_id, kind, note, actor, admin_id)
      select g.id, 'cancelled', 'card_off', 'admin', auth.uid() from gone g;
  end if;
  update mc.marketing_cards c set autopost_off = p_off, updated_at = now() where c.id = p_card;
  perform mc.audit(case when p_off then 'marketing.autopost_card_off' else 'marketing.autopost_card_on' end, null,
                   (select p.key from mc.marketing_packs p where p.id = v_c.pack_id) || '/' || v_c.key || ' v' || p_version,
                   null, 'ok');
  return jsonb_build_object('ok', true, 'off', p_off);
end;
$$;

-- ================================================================================================ the console's functions
-- The clock's call (the tick secret) by the console (its own app secret): creates the jobs that are due, cancels those
-- no longer allowed, fails those missed, and hands out up to p_limit next steps, each under a 3-minute lease, with
-- the post's texts, files and the destination's encrypted tokens.
create or replace function mc_api.ingest_autopost_claim(p_tick_secret text, p_app_secret text, p_limit integer default 4)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_limit integer := least(greatest(coalesce(p_limit, 4), 1), 10);
  v_jobs jsonb;
begin
  if not mc.ingest_secret_ok(p_tick_secret, 'autopost') or not mc.ingest_secret_ok(p_app_secret, 'app') then
    return mc.refused('bad_secret');
  end if;
  perform mc.marketing_lock();
  update mc.autopost_state s set last_claim_at = now(), updated_at = now() where s.id;
  if not mc.setting_on('autopost_enabled') then
    return jsonb_build_object('ok', true, 'enabled', false, 'jobs', '[]'::jsonb);
  end if;

  -- New jobs: within their lead time and not missed. A cancelled one starts again (it never posted anything).
  with made as (
    insert into mc.autopost_jobs (card_id, version, target, destination, label, due_at, late_until)
    select x.card_id, x.version, x.target, x.destination, x.label, x.due_at, x.late_until
    from mc.autopost_candidates() x
    where x.due_at - mc.autopost_lead(x.target) <= now() and now() < x.late_until
    on conflict (card_id, version, target) do update
      set status = 'queued', destination = excluded.destination, label = excluded.label, due_at = excluded.due_at,
          late_until = excluded.late_until, attempts = 0, lease_id = null, lease_until = null, next_try_at = null,
          external_id = null, external_url = null, detail = '{}'::jsonb, error = null, error_code = null,
          finished_at = null, updated_at = now()
      where mc.autopost_jobs.status = 'cancelled'
    returning id)
  insert into mc.autopost_events (job_id, kind, actor) select m.id, 'created', 'system' from made m;

  -- Steps whose run never reported: uncertain when they may have posted (security review M1).
  perform mc.autopost_settle_lapsed();

  -- Jobs no longer allowed (undone, skipped, revised, turned to by hand, auto-posting's destination gone, launch rules)
  -- stop before their next step.
  with gone as (
    update mc.autopost_jobs j set status = 'cancelled', finished_at = now(), updated_at = now(), next_try_at = null,
      lease_id = null, lease_until = null
    where j.status in ('queued', 'preparing', 'ready') and (j.lease_until is null or j.lease_until < now())
      and not exists (select 1 from mc.autopost_candidates() x
                      where x.card_id = j.card_id and x.version = j.version and x.target = j.target)
    returning j.id, j.card_id, j.version, j.target)
  insert into mc.autopost_events (job_id, kind, note, actor)
    select g.id, 'cancelled', mc.autopost_stop_reason(g.card_id, g.version, g.target), 'system' from gone g;

  -- Missed: past the last moment it may go out.
  with late as (
    update mc.autopost_jobs j set status = 'failed', error_code = 'missed', finished_at = now(), updated_at = now(),
      next_try_at = null, lease_id = null, lease_until = null,
      error = 'Mission Control couldn''t post it in time (within 3 hours of its slot). Post it by hand or skip it.'
    where j.status in ('queued', 'preparing', 'ready') and (j.lease_until is null or j.lease_until < now())
      and now() >= j.late_until
    returning j.id)
  insert into mc.autopost_events (job_id, kind, note, actor) select l.id, 'failed', 'missed', 'system' from late l;

  -- The next steps: create or check a Reel ahead of time, everything else at the slot.
  with picked as (
    select j.id from mc.autopost_jobs j
    where j.status in ('queued', 'preparing', 'ready')
      and (j.next_try_at is null or j.next_try_at <= now())
      and (j.lease_until is null or j.lease_until < now())
      and case j.status when 'queued' then j.due_at - mc.autopost_lead(j.target) <= now()
                        when 'preparing' then true
                        else j.due_at <= now() end
    order by j.due_at, j.created_at, j.target
    limit v_limit
    for update skip locked),
  leased as (
    update mc.autopost_jobs j set lease_id = gen_random_uuid(), lease_until = now() + interval '3 minutes',
      updated_at = now()
    from picked p where j.id = p.id
    returning j.*)
  select coalesce(jsonb_agg(jsonb_build_object(
      'id', l.id, 'lease_id', l.lease_id, 'status', l.status, 'target', l.target, 'destination', l.destination,
      'label', l.label, 'due_at', l.due_at, 'attempts', l.attempts, 'detail', l.detail, 'external_id', l.external_id,
      'card', jsonb_build_object('pack', p.key, 'key', c.key, 'title', c.title, 'version', l.version,
                'slot_label', c.slot_label),
      'content', jsonb_build_object('caption', v.caption, 'yt_title', v.yt_title, 'yt_desc', v.yt_desc,
                   'alt_text', v.alt_text,
                   'media', (select coalesce(jsonb_agg(jsonb_build_object('position', m.position, 'pathname', f.pathname,
                               'file_name', f.file_name, 'content_type', f.content_type, 'bytes', f.bytes,
                               'width', f.width, 'height', f.height, 'duration_ms', f.duration_ms)
                               order by m.position), '[]'::jsonb)
                             from mc.marketing_media m join mc.marketing_files f on f.id = m.file_id
                             where m.card_id = l.card_id and m.version = l.version)),
      'account', jsonb_build_object('provider', d.provider, 'external_id', d.external_id, 'name', d.name,
                   'account_id', cn.account_id, 'token_cipher', coalesce(d.token_cipher, cn.token_cipher),
                   'refresh_cipher', case when d.provider = 'meta' then null else cn.refresh_cipher end,
                   'token_expires_at', case when d.provider = 'meta' then null else cn.token_expires_at end,
                   'refresh_expires_at', cn.refresh_expires_at))
      order by l.due_at, l.target), '[]'::jsonb)
    into v_jobs
  from leased l
  join mc.marketing_cards c on c.id = l.card_id
  join mc.marketing_packs p on p.id = c.pack_id
  join mc.marketing_versions v on v.card_id = l.card_id and v.version = l.version
  join mc.autopost_destinations d on d.key = l.destination
  join mc.autopost_connections cn on cn.provider = d.provider;
  return jsonb_build_object('ok', true, 'enabled', true, 'now', now(), 'jobs', v_jobs);
end;
$$;

-- One step's outcome, under the step's lease: preparing (a Reel being processed: checked again in a minute), ready
-- (prepared, published at the slot), posted, sent_to_drafts, failed (retried up to 3 attempts when the console says it
-- may help) or uncertain (the provider may have posted it: never retried; the owner looks). A late "posted" from an
-- expired lease is still recorded: what happened on the platform always wins.
create or replace function mc_api.ingest_autopost_result(p_app_secret text, p_job uuid, p_lease uuid, p_result jsonb)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_j mc.autopost_jobs%rowtype;
  v_status text := p_result ->> 'status';
  v_detail jsonb := coalesce(p_result -> 'detail', '{}'::jsonb);
  v_error text := left(p_result ->> 'error', 500);
  v_code text := p_result ->> 'error_code';
  v_ext text := p_result ->> 'external_id';
  v_url text := p_result ->> 'external_url';
  v_retry boolean := coalesce((p_result ->> 'retryable')::boolean, false);
  v_after integer := least(greatest(coalesce((p_result ->> 'retry_after_s')::integer, 120), 30), 3600);
  v_back text := coalesce(p_result ->> 'back_to', 'queued');
  v_card mc.marketing_cards%rowtype;
begin
  if not mc.ingest_secret_ok(p_app_secret, 'app') then
    return mc.refused('bad_secret');
  end if;
  if v_status is null or v_status not in ('preparing', 'ready', 'posted', 'sent_to_drafts', 'failed', 'uncertain')
     or jsonb_typeof(v_detail) <> 'object' or pg_column_size(v_detail) > 20000
     or (v_ext is not null and not mc.autopost_id_ok(v_ext))
     or (v_url is not null and (char_length(v_url) > 1000 or v_url !~ '^https://'))
     or (v_code is not null and v_code !~ '^[a-z_]{1,60}$')
     or v_back not in ('queued', 'ready') then
    return mc.failed('invalid', 'The result isn''t in the expected form');
  end if;
  perform mc.marketing_lock();
  select * into v_j from mc.autopost_jobs j where j.id = p_job for update;
  if not found then
    return mc.failed('not_found', 'There is no such job');
  end if;
  if v_j.status in ('posted', 'sent_to_drafts') or (v_j.status = 'uncertain' and v_status = 'uncertain') then
    return jsonb_build_object('ok', true, 'already', true, 'status', v_j.status);
  end if;
  if v_j.lease_id is distinct from p_lease and v_status not in ('posted', 'sent_to_drafts', 'uncertain') then
    return mc.failed('lease_lost', 'Another step took this job over.');
  end if;

  if v_status in ('preparing', 'ready') then
    update mc.autopost_jobs j set status = v_status, detail = j.detail || v_detail, external_id = coalesce(v_ext, j.external_id),
      lease_id = null, lease_until = null, updated_at = now(),
      next_try_at = case when v_status = 'preparing' then now() + interval '1 minute' end
      where j.id = p_job;
  elsif v_status in ('posted', 'sent_to_drafts') then
    update mc.autopost_jobs j set status = v_status, detail = j.detail || v_detail, external_id = coalesce(v_ext, j.external_id),
      external_url = coalesce(v_url, j.external_url), error = null, error_code = null, lease_id = null, lease_until = null,
      next_try_at = null, finished_at = now(), updated_at = now()
      where j.id = p_job;
    perform mc.autopost_mark_out(p_job);
    insert into mc.job_runs (job, result, started_at, finished_at, detail)
      values ('autopost', 'ok', v_j.created_at, now(), jsonb_build_object('job', p_job, 'target', v_j.target, 'status', v_status));
  elsif v_status = 'failed' and v_retry and v_j.attempts + 1 < 3 and now() < v_j.late_until then
    update mc.autopost_jobs j set status = v_back, attempts = j.attempts + 1, detail = j.detail || v_detail,
      error = v_error, error_code = v_code, lease_id = null, lease_until = null,
      next_try_at = now() + make_interval(secs => v_after * (j.attempts + 1)), updated_at = now()
      where j.id = p_job;
    v_status := 'retrying';
  else
    update mc.autopost_jobs j set status = v_status, attempts = j.attempts + 1, detail = j.detail || v_detail,
      external_id = coalesce(v_ext, j.external_id), external_url = coalesce(v_url, j.external_url),
      error = coalesce(v_error, case when v_status = 'uncertain' then 'Mission Control couldn''t confirm it was posted.'
                                     else 'It couldn''t be posted.' end),
      error_code = v_code, lease_id = null, lease_until = null, next_try_at = null, finished_at = now(), updated_at = now()
      where j.id = p_job;
    insert into mc.job_runs (job, result, started_at, finished_at, detail)
      values ('autopost', 'failed', v_j.created_at, now(),
              jsonb_build_object('job', p_job, 'target', v_j.target, 'status', v_status, 'code', v_code));
  end if;
  insert into mc.autopost_events (job_id, kind, note, actor) values (p_job, v_status, left(coalesce(v_error, v_code), 500), 'system');
  return jsonb_build_object('ok', true, 'status', (select j.status from mc.autopost_jobs j where j.id = p_job));
end;
$$;

create or replace function mc_api.ingest_autopost_token(p_app_secret text, p_provider text, p_account_id text,
  p_token_cipher text, p_token_expires_at timestamptz, p_refresh_cipher text, p_refresh_expires_at timestamptz, p_error text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
begin
  if not mc.ingest_secret_ok(p_app_secret, 'app') then
    return mc.refused('bad_secret');
  end if;
  return mc.autopost_put_token(p_provider, p_account_id, p_token_cipher, p_token_expires_at, p_refresh_cipher,
                               p_refresh_expires_at, p_error);
end;
$$;

-- ================================================================================================ the clock
-- Every minute (pg_cron mc-autopost, once the owner ran mc.autopost_setup): when something is due, one call to the
-- console's /api/autopost/tick with the tick secret from Vault. Never while a step still holds a fresh lease.
create or replace function mc.autopost_kick()
returns text
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_url text;
  v_secret text;
begin
  if not mc.setting_on('autopost_enabled') then
    return 'off';
  end if;
  select s.value #>> '{}' into v_url from mc.settings s where s.key = 'autopost_tick_url';
  if v_url is null then
    return 'no_url';
  end if;
  if not mc.autopost_work_due() then
    return 'idle';
  end if;
  if exists (select 1 from mc.autopost_jobs j where j.lease_until > now() + interval '1 minute') then
    return 'busy';
  end if;
  execute 'select decrypted_secret from vault.decrypted_secrets where name = $1 limit 1' into v_secret using 'mc_autopost_tick';
  if v_secret is null then
    return 'no_secret';
  end if;
  execute 'select net.http_post(url := $1, body := $2, headers := $3, timeout_milliseconds := $4)'
    using v_url, '{}'::jsonb, jsonb_build_object('Authorization', 'Bearer ' || v_secret, 'Content-Type', 'application/json'),
          55000;
  update mc.autopost_state s set last_kick_at = now(), updated_at = now() where s.id;
  return 'kicked';
end;
$$;

-- The owner's one-time setup, in the SQL editor (DEPLOYMENT.md part J):
--   select mc.autopost_setup('https://lucento-mission-control.vercel.app/api/autopost/tick');
-- Creates (or replaces) the tick secret, keeps it in Vault, records the address and schedules mc-autopost every minute.
-- Not security definer: only the owner runs it. Running it again rotates the secret.
create or replace function mc.autopost_setup(p_tick_url text)
returns text
language plpgsql
volatile
set search_path = ''
as $$
declare
  v_secret text;
  v_id uuid;
begin
  if p_tick_url is null or p_tick_url !~ '^https://[A-Za-z0-9.-]+(:[0-9]+)?/api/autopost/tick$' then
    raise exception 'autopost_setup: give the console''s address ending in /api/autopost/tick, for example https://lucento-mission-control.vercel.app/api/autopost/tick'
      using errcode = '22023';
  end if;
  if not exists (select 1 from pg_catalog.pg_extension x where x.extname = 'pg_net') then
    raise exception 'autopost_setup: turn on the pg_net extension first (Database › Extensions › pg_net)' using errcode = '55000';
  end if;
  if not exists (select 1 from pg_catalog.pg_extension x where x.extname = 'pg_cron') then
    raise exception 'autopost_setup: turn on the pg_cron extension first (Database › Extensions › pg_cron)' using errcode = '55000';
  end if;
  if to_regclass('vault.secrets') is null then
    raise exception 'autopost_setup: Supabase Vault isn''t available in this project' using errcode = '55000';
  end if;
  v_secret := mc.rotate_ingest_secret('autopost');
  execute 'select id from vault.secrets where name = $1 limit 1' into v_id using 'mc_autopost_tick';
  if v_id is null then
    execute 'select vault.create_secret($1, $2, $3)' using v_secret, 'mc_autopost_tick', 'Mission Control auto-posting clock';
  else
    execute 'select vault.update_secret($1, $2)' using v_id, v_secret;
  end if;
  insert into mc.settings (key, value, updated_at) values ('autopost_tick_url', to_jsonb(p_tick_url), now())
    on conflict (key) do update set value = excluded.value, updated_at = excluded.updated_at;
  execute 'select cron.schedule($1, $2, $3)' using 'mc-autopost', '* * * * *', 'select mc.autopost_kick()';
  update mc.autopost_state s set clock_set_at = now(), updated_at = now() where s.id;
  return 'The posting clock is set: every minute Mission Control checks for posts to send. Auto-posting stays off until you turn it on in Settings.';
end;
$$;

-- ================================================================================================ privileges
revoke all on all tables in schema mc from public, anon, authenticated, service_role;
revoke all on all sequences in schema mc from public, anon, authenticated, service_role;
revoke all on all functions in schema mc from public, anon, authenticated, service_role;
revoke all on function
  mc_api.marketing_board(text),
  mc_api.marketing_summary(),
  mc_api.marketing_apps(uuid, integer, text, text[]),
  mc_api.autopost_settings(),
  mc_api.autopost_set_enabled(boolean),
  mc_api.autopost_save_connection(text, text, text, text, text, timestamptz, timestamptz, text[], jsonb),
  mc_api.autopost_set_destination(text, text),
  mc_api.autopost_disconnect(text),
  mc_api.autopost_credentials(text),
  mc_api.autopost_record_check(text, boolean, text),
  mc_api.autopost_save_token(text, text, text, timestamptz, text, timestamptz, text),
  mc_api.autopost_job_action(uuid, text),
  mc_api.autopost_card(uuid, integer, boolean),
  mc_api.ingest_autopost_claim(text, text, integer),
  mc_api.ingest_autopost_result(text, uuid, uuid, jsonb),
  mc_api.ingest_autopost_token(text, text, text, text, timestamptz, text, timestamptz, text)
  from public, anon, authenticated, service_role;

grant execute on function
  mc_api.marketing_board(text),
  mc_api.marketing_summary(),
  mc_api.marketing_apps(uuid, integer, text, text[]),
  mc_api.autopost_settings(),
  mc_api.autopost_set_enabled(boolean),
  mc_api.autopost_save_connection(text, text, text, text, text, timestamptz, timestamptz, text[], jsonb),
  mc_api.autopost_set_destination(text, text),
  mc_api.autopost_disconnect(text),
  mc_api.autopost_credentials(text),
  mc_api.autopost_record_check(text, boolean, text),
  mc_api.autopost_save_token(text, text, text, timestamptz, text, timestamptz, text),
  mc_api.autopost_job_action(uuid, text),
  mc_api.autopost_card(uuid, integer, boolean)
  to authenticated;

grant execute on function
  mc_api.ingest_autopost_claim(text, text, integer),
  mc_api.ingest_autopost_result(text, uuid, uuid, jsonb),
  mc_api.ingest_autopost_token(text, text, text, text, timestamptz, text, timestamptz, text)
  to anon;
