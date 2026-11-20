-- Mission Control: the owner's tasks (docs/mission-control/v1/OWNER_TASKS.md).
--
-- The owner's to-do list, in one place for the owner and Claude. Claude's command-line tool writes each task with a
-- complete, step-by-step write-up (its own `tasks` secret); in Mission Control › Tasks the owner marks it done, asks
-- Claude, adds a note, says it isn't needed or reopens it; Claude's tool reads everything the owner did since it last
-- read the list. Every change stays in the task's history.
--
--   * A write-up is data, never markup: the console shows plain text with **bold** and `code`, https links with their
--     host, and text to copy. Nothing that looks like a key is accepted, in a task or a note.
--   * One advisory lock (mc.tasks_lock) serialises every writer, taken before any row; the readers that hand out a read
--     position take it shared, so no entry below that position can still be on its way.
--   * The owner's actions name the task's row_version: a task that changed since the page showed it is refused (stale),
--     so nobody marks done a task whose instructions changed underneath them.
--
-- Needs 20261118090000_mission_control_autopost.sql first: docs/mission-control/APPLY_TASKS.sql carries both. Additive
-- and safe to paste twice. Outside its own new objects it changes:
--   * mc.ingest_secret's purpose check and mc.rotate_ingest_secret: a fifth purpose, `tasks`;
--   * mc_api.nav_badges: the same answer as before plus a `tasks` count.

-- ================================================================================================ the tasks secret
-- One secret per purpose (security review L10): Claude's tasks tool holds `tasks` and nothing else.
alter table mc.ingest_secret drop constraint if exists ingest_secret_purpose_check;
alter table mc.ingest_secret add constraint ingest_secret_purpose_check
  check (purpose in ('app', 'backup', 'marketing', 'autopost', 'tasks'));

-- The same function as before with the fifth purpose. Still not security definer: only the owner runs it, in the SQL
-- editor: select mc.rotate_ingest_secret('tasks');
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
  if p_purpose is null or p_purpose not in ('app', 'backup', 'marketing', 'autopost', 'tasks') then
    raise exception 'rotate_ingest_secret: purpose is app, backup, marketing, autopost or tasks' using errcode = '22023';
  end if;
  insert into mc.ingest_secret (purpose, secret_hash, rotated_at)
    values (p_purpose, encode(sha256(convert_to(v_secret, 'UTF8')), 'hex'), now())
    on conflict (purpose) do update set secret_hash = excluded.secret_hash, rotated_at = excluded.rotated_at;
  return v_secret;
end;
$$;

-- ================================================================================================ tables
create table if not exists mc.tasks (
  id uuid primary key default gen_random_uuid(),
  -- The stable key Claude's tool writes by: OT-42 (the owner's tasks keep NEXT_TASKS' numbering).
  code text not null unique check (code ~ '^[A-Z]{2,4}-[0-9]{1,4}$'),
  title text not null check (char_length(title) between 3 and 140),
  area text not null check (char_length(area) between 2 and 40),
  priority text not null check (priority in ('now', 'soon', 'later')),
  due_on date,
  -- open: the owner's turn · asked: Claude's turn (the owner's question) · waiting: not possible yet · done · dropped.
  status text not null check (status in ('open', 'asked', 'waiting', 'done', 'dropped')),
  waiting_for text check (waiting_for is null or char_length(waiting_for) between 3 and 300),
  -- The write-up's version (mc.task_versions keeps each one) and its hash with the title, area, priority and due date.
  version integer not null check (version >= 1),
  content jsonb not null check (jsonb_typeof(content) = 'object'),
  content_hash text not null check (content_hash ~ '^[0-9a-f]{64}$'),
  -- Any change at all: the owner's actions name it (stale otherwise).
  row_version bigint not null default 1 check (row_version >= 1),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  status_at timestamptz not null default now(),
  -- When Claude verified the owner's done (read-only check, or the owner's word in chat with a note).
  checked_at timestamptz,
  constraint tasks_waiting_for check ((status = 'waiting') = (waiting_for is not null)),
  constraint tasks_checked_when_done check (checked_at is null or status = 'done')
);

create index if not exists tasks_status_at on mc.tasks (status, status_at desc);

-- Every write-up the owner was shown, never changed afterwards.
create table if not exists mc.task_versions (
  task_id uuid not null references mc.tasks (id) on delete cascade,
  version integer not null check (version >= 1),
  title text not null,
  content jsonb not null,
  change_note text check (change_note is null or char_length(change_note) between 1 and 300),
  created_at timestamptz not null default now(),
  primary key (task_id, version)
);

-- The task's history: who did what, when. Append-only.
create table if not exists mc.task_events (
  id bigint generated always as identity primary key,
  task_id uuid not null references mc.tasks (id) on delete cascade,
  actor text not null check (actor in ('owner', 'claude')),
  -- The admin who acted (the owner's own entries); null for Claude's.
  admin_id uuid,
  kind text not null check (kind in ('created', 'updated', 'done', 'checked', 'reopened', 'asked', 'answered', 'note',
                                     'waiting', 'opened', 'dropped')),
  note text check (note is null or char_length(note) between 1 and 4000),
  status_before text,
  status_after text,
  version integer,
  at timestamptz not null default now(),
  constraint task_events_owner_admin check ((actor = 'owner') = (admin_id is not null))
);

create index if not exists task_events_task on mc.task_events (task_id, id);
create index if not exists task_events_owner on mc.task_events (id) where actor = 'owner';

-- One row: how far Claude and the owner have read, and when Claude last read the list.
create table if not exists mc.tasks_state (
  id boolean primary key default true check (id),
  claude_seen_event_id bigint not null default 0,
  claude_read_at timestamptz,
  owner_seen_event_id bigint not null default 0
);

insert into mc.tasks_state (id) values (true) on conflict (id) do nothing;

-- History and versions: no UPDATE, no TRUNCATE; DELETE only when the task itself is gone (cascades; nothing deletes
-- tasks today).
create or replace function mc.tasks_append_only()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if tg_op = 'DELETE' then
    if not exists (select 1 from mc.tasks t where t.id = old.task_id) then
      return old;
    end if;
  end if;
  raise exception '% is append-only', tg_table_name using errcode = '42501';
end;
$$;

drop trigger if exists task_events_no_change on mc.task_events;
create trigger task_events_no_change before update or delete on mc.task_events
  for each row execute function mc.tasks_append_only();
drop trigger if exists task_events_no_truncate on mc.task_events;
create trigger task_events_no_truncate before truncate on mc.task_events
  for each statement execute function mc.tasks_append_only();
drop trigger if exists task_versions_no_change on mc.task_versions;
create trigger task_versions_no_change before update or delete on mc.task_versions
  for each row execute function mc.tasks_append_only();
drop trigger if exists task_versions_no_truncate on mc.task_versions;
create trigger task_versions_no_truncate before truncate on mc.task_versions
  for each statement execute function mc.tasks_append_only();

-- ================================================================================================ rules (no tables)
create or replace function mc.tasks_lock()
returns void
language sql
volatile
set search_path = ''
as $$
  select pg_advisory_xact_lock(hashtextextended('mc.tasks', 0))
$$;

-- Something that looks like a key, a token or a private key, anywhere in a task or a note. Names of settings are fine
-- (LUCENTO_SUPABASE_SERVICE_ROLE_KEY); values never are.
create or replace function mc.task_secretish(p text)
returns boolean
language sql
immutable
set search_path = ''
as $$
  select coalesce(
    p ~ '[0-9A-Fa-f]{64}'
    or p ~ 'eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.'
    or p ~* '-----BEGIN'
    or p ~ '(sk_live_|sk_test_|rk_live_|sb_secret_|ghp_|gho_|ghs_|github_pat_|xox[abps]-)[A-Za-z0-9_]{8,}'
    or p ~ 'AKIA[0-9A-Z]{16}'
    or p ~ 'AIza[0-9A-Za-z_-]{30,}'
    or p ~ '(^|[^A-Za-z0-9_])re_[A-Za-z0-9_]{16,}',
    false)
$$;

-- Text of p_min..p_max characters that isn't only spaces, with no control character but new lines (and tabs where
-- p_tabs). Never null: a missing value is simply not ok.
create or replace function mc.task_text_ok(p jsonb, p_min integer, p_max integer, p_tabs boolean default false)
returns boolean
language plpgsql
immutable
set search_path = ''
as $$
declare
  v text;
begin
  if p is null or jsonb_typeof(p) <> 'string' then
    return false;
  end if;
  v := p #>> '{}';
  if char_length(v) < p_min or char_length(v) > p_max or btrim(v, E' \n\t') = '' then
    return false;
  end if;
  if p_tabs then
    return v !~ '[\x01-\x08\x0b-\x1f\x7f]';
  end if;
  return v !~ '[\x01-\x09\x0b-\x1f\x7f]';
end;
$$;

-- https with a real host name (no address, no localhost, no user name), at most 500 characters.
create or replace function mc.task_url_ok(p jsonb)
returns boolean
language plpgsql
immutable
set search_path = ''
as $$
begin
  if p is null or jsonb_typeof(p) <> 'string' or char_length(p #>> '{}') > 500 then
    return false;
  end if;
  return (p #>> '{}') ~ '^https://[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)*\.[A-Za-z]{2,}(:[0-9]{1,5})?(/[^[:space:]<>"]*)?$';
end;
$$;

create or replace function mc.task_link_ok(p jsonb)
returns boolean
language plpgsql
immutable
set search_path = ''
as $$
declare
  k text;
begin
  if p is null or jsonb_typeof(p) <> 'object' then
    return false;
  end if;
  for k in select jsonb_object_keys(p) loop
    if k not in ('url', 'label') then
      return false;
    end if;
  end loop;
  return mc.task_url_ok(p -> 'url') and mc.task_text_ok(p -> 'label', 2, 80);
end;
$$;

-- Present and not JSON null.
create or replace function mc.task_has(p jsonb, p_key text)
returns boolean
language sql
immutable
set search_path = ''
as $$
  select coalesce(p ? p_key and jsonb_typeof(p -> p_key) <> 'null', false)
$$;

-- Why a write-up can't be saved, in words for Claude's tool; null when it can (OWNER_TASKS.md §3).
create or replace function mc.task_content_problem(p jsonb)
returns text
language plpgsql
immutable
set search_path = ''
as $$
declare
  k text;
  v_part jsonb;
  v_step jsonb;
  v_item jsonb;
  i integer := 0;
  j integer;
  v_steps integer := 0;
begin
  if p is null or jsonb_typeof(p) <> 'object' then
    return 'content must be an object';
  end if;
  for k in select jsonb_object_keys(p) loop
    if k not in ('why', 'minutes', 'before', 'parts', 'done_when', 'if_stuck', 'links') then
      return format('content has an unknown field "%s"', left(k, 40));
    end if;
  end loop;
  if not mc.task_text_ok(p -> 'why', 10, 800) then
    return 'content.why must be text of 10 to 800 characters';
  end if;
  if mc.task_has(p, 'minutes') then
    if jsonb_typeof(p -> 'minutes') <> 'number' or (p ->> 'minutes') !~ '^[0-9]{1,3}$' then
      return 'content.minutes must be a whole number from 1 to 600, or null';
    end if;
    if (p ->> 'minutes')::integer not between 1 and 600 then
      return 'content.minutes must be a whole number from 1 to 600, or null';
    end if;
  end if;
  if mc.task_has(p, 'before') then
    if jsonb_typeof(p -> 'before') <> 'array' or jsonb_array_length(p -> 'before') > 10 then
      return 'content.before must be a list of at most 10 items';
    end if;
    for v_item in select x from jsonb_array_elements(p -> 'before') x loop
      if not mc.task_text_ok(v_item, 2, 400) then
        return 'each item of content.before must be text of 2 to 400 characters';
      end if;
    end loop;
  end if;
  if jsonb_typeof(p -> 'parts') is distinct from 'array' or jsonb_array_length(p -> 'parts') not between 1 and 10 then
    return 'content.parts must be a list of 1 to 10 parts';
  end if;
  for v_part in select x from jsonb_array_elements(p -> 'parts') x loop
    i := i + 1;
    if jsonb_typeof(v_part) <> 'object' then
      return format('part %s must be an object', i);
    end if;
    for k in select jsonb_object_keys(v_part) loop
      if k not in ('title', 'steps') then
        return format('part %s has an unknown field "%s"', i, left(k, 40));
      end if;
    end loop;
    if mc.task_has(v_part, 'title') and not mc.task_text_ok(v_part -> 'title', 2, 140) then
      return format('part %s: the title must be text of 2 to 140 characters', i);
    end if;
    if jsonb_typeof(v_part -> 'steps') is distinct from 'array' or jsonb_array_length(v_part -> 'steps') not between 1 and 40 then
      return format('part %s needs 1 to 40 steps', i);
    end if;
    j := 0;
    for v_step in select x from jsonb_array_elements(v_part -> 'steps') x loop
      j := j + 1;
      v_steps := v_steps + 1;
      if jsonb_typeof(v_step) <> 'object' then
        return format('part %s, step %s must be an object', i, j);
      end if;
      for k in select jsonb_object_keys(v_step) loop
        if k not in ('text', 'link', 'copy', 'expect') then
          return format('part %s, step %s has an unknown field "%s"', i, j, left(k, 40));
        end if;
      end loop;
      if not mc.task_text_ok(v_step -> 'text', 2, 1500) then
        return format('part %s, step %s: text must be 2 to 1500 characters', i, j);
      end if;
      if mc.task_has(v_step, 'link') and not mc.task_link_ok(v_step -> 'link') then
        return format('part %s, step %s: a link is {url, label}: an https address of at most 500 characters and a label of 2 to 80', i, j);
      end if;
      if mc.task_has(v_step, 'copy') and not mc.task_text_ok(v_step -> 'copy', 1, 8000, true) then
        return format('part %s, step %s: copy must be text of 1 to 8000 characters', i, j);
      end if;
      if mc.task_has(v_step, 'expect') and not mc.task_text_ok(v_step -> 'expect', 2, 600) then
        return format('part %s, step %s: expect must be text of 2 to 600 characters', i, j);
      end if;
    end loop;
  end loop;
  if v_steps > 120 then
    return format('a task has at most 120 steps (this one has %s)', v_steps);
  end if;
  if mc.task_has(p, 'done_when') and not mc.task_text_ok(p -> 'done_when', 2, 800) then
    return 'content.done_when must be text of 2 to 800 characters';
  end if;
  if mc.task_has(p, 'if_stuck') and not mc.task_text_ok(p -> 'if_stuck', 2, 800) then
    return 'content.if_stuck must be text of 2 to 800 characters';
  end if;
  if mc.task_has(p, 'links') then
    if jsonb_typeof(p -> 'links') <> 'array' or jsonb_array_length(p -> 'links') > 10 then
      return 'content.links must be a list of at most 10 links';
    end if;
    for v_item in select x from jsonb_array_elements(p -> 'links') x loop
      if not mc.task_link_ok(v_item) then
        return 'each of content.links is {url, label}: an https address of at most 500 characters and a label of 2 to 80';
      end if;
    end loop;
  end if;
  if char_length(p::text) > 60000 then
    return 'the write-up is over 60000 characters';
  end if;
  return null;
end;
$$;

-- Why a task (everything but its write-up) can't be saved; null when it can.
create or replace function mc.task_problem(p jsonb)
returns text
language plpgsql
stable
set search_path = ''
as $$
declare
  k text;
  v_day date;
begin
  if p is null or jsonb_typeof(p) <> 'object' then
    return 'each task must be an object';
  end if;
  for k in select jsonb_object_keys(p) loop
    if k not in ('code', 'title', 'area', 'priority', 'due_on', 'status', 'waiting_for', 'content', 'change_note') then
      return format('unknown field "%s"', left(k, 40));
    end if;
  end loop;
  if jsonb_typeof(p -> 'code') is distinct from 'string' or (p ->> 'code') !~ '^[A-Z]{2,4}-[0-9]{1,4}$' then
    return 'code must look like OT-42';
  end if;
  if not mc.task_text_ok(p -> 'title', 3, 140) then
    return 'title must be text of 3 to 140 characters';
  end if;
  if not mc.task_text_ok(p -> 'area', 2, 40) then
    return 'area must be text of 2 to 40 characters';
  end if;
  if jsonb_typeof(p -> 'priority') is distinct from 'string' or (p ->> 'priority') not in ('now', 'soon', 'later') then
    return 'priority must be now, soon or later';
  end if;
  if mc.task_has(p, 'due_on') then
    if jsonb_typeof(p -> 'due_on') <> 'string' or (p ->> 'due_on') !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' then
      return 'due_on must be a date like 2026-11-30, or null';
    end if;
    begin
      v_day := (p ->> 'due_on')::date;
    exception when others then
      return 'due_on must be a real date';
    end;
  end if;
  if mc.task_has(p, 'status') and (jsonb_typeof(p -> 'status') <> 'string' or (p ->> 'status') not in ('open', 'waiting')) then
    return 'status must be open or waiting (it applies when the task is created)';
  end if;
  if coalesce(p ->> 'status', 'open') = 'waiting' then
    if not mc.task_text_ok(p -> 'waiting_for', 3, 300) then
      return 'a waiting task needs waiting_for: text of 3 to 300 characters';
    end if;
  elsif mc.task_has(p, 'waiting_for') then
    return 'waiting_for is only for a waiting task';
  end if;
  if mc.task_has(p, 'change_note') and not mc.task_text_ok(p -> 'change_note', 1, 300) then
    return 'change_note must be text of 1 to 300 characters';
  end if;
  return mc.task_content_problem(p -> 'content');
end;
$$;

-- A note from the owner or Claude: null when fine, else the refusal (as mc.failed would answer).
create or replace function mc.task_note_problem(p_note text, p_required boolean)
returns jsonb
language plpgsql
immutable
set search_path = ''
as $$
begin
  if p_note is null then
    return case when p_required then mc.failed('note_required', 'Write the note first.') end;
  end if;
  if char_length(p_note) > 4000 or p_note ~ '[\x01-\x09\x0b-\x1f\x7f]' then
    return mc.failed('bad_note', 'A note is plain text of at most 4000 characters.');
  end if;
  if mc.task_secretish(p_note) then
    return mc.failed('looks_like_a_key', 'That looks like a key or password, so nothing was saved. Never paste keys into Mission Control: they go straight from the clipboard into the tool that needs them.');
  end if;
  return null;
end;
$$;

-- ================================================================================================ helpers (rows)
create or replace function mc.task_log(p_task uuid, p_actor text, p_kind text, p_note text, p_before text, p_after text,
                                       p_version integer, p_admin uuid)
returns bigint
language sql
volatile
security definer
set search_path = ''
as $$
  insert into mc.task_events (task_id, actor, admin_id, kind, note, status_before, status_after, version)
  values (p_task, p_actor, p_admin, p_kind, p_note, p_before, p_after, p_version)
  returning id
$$;

-- Lists in one order everywhere: whose turn, how soon, then the code.
create or replace function mc.task_rank(p_status text, p_priority text, p_due date)
returns text
language sql
immutable
set search_path = ''
as $$
  select concat_ws('|',
    case p_status when 'open' then 1 when 'asked' then 2 when 'waiting' then 3 when 'done' then 4 else 5 end,
    case p_priority when 'now' then 1 when 'soon' then 2 else 3 end,
    coalesce(to_char(p_due, 'YYYY-MM-DD'), '9999-12-31'))
$$;

-- A task as the page and the tool see it: with its write-up (p_full) and its newest p_events history entries.
create or replace function mc.task_json(p_task uuid, p_full boolean, p_events integer)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select jsonb_build_object('id', t.id, 'code', t.code, 'title', t.title, 'area', t.area, 'priority', t.priority,
           'due_on', t.due_on, 'status', t.status, 'waiting_for', t.waiting_for, 'version', t.version,
           'row_version', t.row_version, 'created_at', t.created_at, 'updated_at', t.updated_at,
           'status_at', t.status_at, 'checked_at', t.checked_at,
           'last', (select jsonb_build_object('id', e.id, 'kind', e.kind, 'actor', e.actor, 'at', e.at, 'note', e.note)
                    from mc.task_events e where e.task_id = t.id order by e.id desc limit 1))
    || case when p_full then jsonb_build_object('content', t.content) else '{}'::jsonb end
    || case when p_events > 0 then jsonb_build_object('events', coalesce((
         select jsonb_agg(x.j order by x.id)
         from (select e.id,
                      jsonb_build_object('id', e.id, 'actor', e.actor, 'kind', e.kind, 'note', e.note,
                        'status_before', e.status_before, 'status_after', e.status_after, 'version', e.version,
                        'at', e.at, 'by', case when e.actor = 'owner' then coalesce(a.display_name, 'Admin') else 'Claude' end) as j
               from mc.task_events e
               left join mc.admins a on a.user_id = e.admin_id
               where e.task_id = t.id
               order by e.id desc
               limit p_events) x), '[]'::jsonb))
       else '{}'::jsonb end
  from mc.tasks t
  where t.id = p_task
$$;

-- ================================================================================================ Claude's functions
-- Up to 40 tasks, all or nothing: every task is checked before anything is written. A new code creates the task (open,
-- or waiting with what it waits for); an existing one gets a new version when its title, area, priority, due date or
-- write-up changed, and nothing at all otherwise. Never changes a status: that is ingest_tasks_update.
create or replace function mc_api.ingest_tasks_push(p_secret text, p_tasks jsonb)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_item jsonb;
  v_problem text;
  v_problems jsonb := '[]'::jsonb;
  v_results jsonb := '[]'::jsonb;
  v_codes text[] := '{}';
  v_code text;
  v_t mc.tasks%rowtype;
  v_hash text;
  v_status text;
  v_id uuid;
  v_n integer := 0;
begin
  if not mc.ingest_secret_ok(p_secret, 'tasks') then
    perform mc.log_refusal(null, 'ingest.tasks_push', 'bad_secret');
    return mc.refused('bad_secret');
  end if;
  if p_tasks is null or jsonb_typeof(p_tasks) <> 'array' or jsonb_array_length(p_tasks) not between 1 and 40 then
    return mc.failed('bad_request', 'Send a list of 1 to 40 tasks.');
  end if;
  if char_length(p_tasks::text) > 2000000 then
    return mc.failed('too_large', 'Send at most 2 000 000 characters at once.');
  end if;
  for v_item in select x from jsonb_array_elements(p_tasks) x loop
    v_n := v_n + 1;
    v_problem := mc.task_problem(v_item);
    if v_problem is null and mc.task_secretish(v_item::text) then
      v_problem := 'something in it looks like a key or a token';
    end if;
    if v_problem is null and (v_item ->> 'code') = any (v_codes) then
      v_problem := 'the same code twice in one push';
    end if;
    if v_problem is not null then
      v_problems := v_problems || jsonb_build_array(jsonb_build_object('index', v_n,
        'code', case when jsonb_typeof(v_item -> 'code') = 'string' then left(v_item ->> 'code', 20) end, 'problem', v_problem));
    end if;
    v_codes := v_codes || (v_item ->> 'code');
  end loop;
  if jsonb_array_length(v_problems) > 0 then
    return jsonb_build_object('ok', false, 'error', 'invalid', 'problems', v_problems,
      'message', format('Nothing was saved: %s of %s tasks can''t be used as they are.', jsonb_array_length(v_problems), v_n));
  end if;

  perform mc.tasks_lock();
  for v_item in select x from jsonb_array_elements(p_tasks) x loop
    v_code := v_item ->> 'code';
    v_hash := encode(sha256(convert_to(jsonb_build_object('title', v_item -> 'title', 'area', v_item -> 'area',
                'priority', v_item -> 'priority', 'due_on', coalesce(v_item -> 'due_on', 'null'::jsonb),
                'content', v_item -> 'content')::text, 'UTF8')), 'hex');
    select * into v_t from mc.tasks t where t.code = v_code for update;
    if not found then
      v_status := coalesce(v_item ->> 'status', 'open');
      insert into mc.tasks (code, title, area, priority, due_on, status, waiting_for, version, content, content_hash)
        values (v_code, v_item ->> 'title', v_item ->> 'area', v_item ->> 'priority', (v_item ->> 'due_on')::date,
                v_status, case when v_status = 'waiting' then btrim(v_item ->> 'waiting_for') end, 1, v_item -> 'content', v_hash)
        returning id into v_id;
      insert into mc.task_versions (task_id, version, title, content)
        values (v_id, 1, v_item ->> 'title', v_item -> 'content');
      perform mc.task_log(v_id, 'claude', 'created', null, null, v_status, 1, null);
      v_results := v_results || jsonb_build_array(jsonb_build_object('code', v_code, 'result', 'created', 'version', 1,
        'status', v_status));
    elsif v_t.content_hash = v_hash then
      v_results := v_results || jsonb_build_array(jsonb_build_object('code', v_code, 'result', 'unchanged',
        'version', v_t.version, 'status', v_t.status));
    else
      update mc.tasks t
        set title = v_item ->> 'title', area = v_item ->> 'area', priority = v_item ->> 'priority',
            due_on = (v_item ->> 'due_on')::date, content = v_item -> 'content', content_hash = v_hash,
            version = t.version + 1, row_version = t.row_version + 1, updated_at = now()
        where t.id = v_t.id;
      insert into mc.task_versions (task_id, version, title, content, change_note)
        values (v_t.id, v_t.version + 1, v_item ->> 'title', v_item -> 'content', nullif(btrim(v_item ->> 'change_note'), ''));
      perform mc.task_log(v_t.id, 'claude', 'updated', nullif(btrim(v_item ->> 'change_note'), ''), v_t.status, v_t.status,
                          v_t.version + 1, null);
      v_results := v_results || jsonb_build_array(jsonb_build_object('code', v_code, 'result', 'updated',
        'version', v_t.version + 1, 'status', v_t.status));
    end if;
  end loop;
  return jsonb_build_object('ok', true, 'results', v_results);
end;
$$;

-- Claude's read: every task still open, asked or waiting and those closed in the last 30 days (no write-ups), the
-- owner's entries after Claude's read position (or after p_since), oldest first, and the open questions. Moves the
-- position and stamps when Claude read, which the page shows.
create or replace function mc_api.ingest_tasks_feed(p_secret text, p_since bigint)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_s mc.tasks_state%rowtype;
  v_max bigint;
  v_since bigint;
  v_n integer;
  v_events jsonb;
  v_last bigint;
  v_cursor bigint;
begin
  if not mc.ingest_secret_ok(p_secret, 'tasks') then
    perform mc.log_refusal(null, 'ingest.tasks_feed', 'bad_secret');
    return mc.refused('bad_secret');
  end if;
  -- Shared: every writer holds this lock exclusively until it commits, so no entry up to v_max can still be on its way.
  perform pg_advisory_xact_lock_shared(hashtextextended('mc.tasks', 0));
  select * into v_s from mc.tasks_state s where s.id;
  select coalesce(max(e.id), 0) into v_max from mc.task_events e;
  v_since := case when p_since is null then v_s.claude_seen_event_id
                  when p_since < 0 or p_since > v_max then 0
                  else p_since end;
  select count(*) into v_n from (select 1 from mc.task_events e where e.actor = 'owner' and e.id > v_since limit 501) x;
  select coalesce(jsonb_agg(x.j order by x.id), '[]'::jsonb), max(x.id)
    into v_events, v_last
    from (select e.id,
                 jsonb_build_object('id', e.id, 'code', t.code, 'title', t.title, 'kind', e.kind, 'note', e.note,
                   'status_before', e.status_before, 'status_after', e.status_after, 'status_now', t.status,
                   'at', e.at, 'by', coalesce(a.display_name, 'Admin')) as j
          from mc.task_events e
          join mc.tasks t on t.id = e.task_id
          left join mc.admins a on a.user_id = e.admin_id
          where e.actor = 'owner' and e.id > v_since
          order by e.id
          limit 500) x;
  v_cursor := case when v_n > 500 then v_last else v_max end;
  update mc.tasks_state s
    set claude_seen_event_id = greatest(s.claude_seen_event_id, v_cursor), claude_read_at = now()
    where s.id;
  return jsonb_build_object('ok', true, 'now', now(), 'tz', mc.tz(), 'since', v_since, 'cursor', v_cursor,
    'more', v_n > 500, 'events', v_events,
    'tasks', (select coalesce(jsonb_agg(mc.task_json(t.id, false, 0) order by mc.task_rank(t.status, t.priority, t.due_on), t.code), '[]'::jsonb)
              from mc.tasks t
              where t.status in ('open', 'asked', 'waiting') or t.status_at > now() - interval '30 days'),
    'questions', (select coalesce(jsonb_agg(jsonb_build_object('code', t.code, 'title', t.title,
                     'question', (select e.note from mc.task_events e where e.task_id = t.id and e.kind = 'asked' order by e.id desc limit 1),
                     'asked_at', t.status_at) order by t.status_at), '[]'::jsonb)
                  from mc.tasks t where t.status = 'asked'),
    'counts', (select jsonb_build_object('open', count(*) filter (where t.status = 'open'),
                 'asked', count(*) filter (where t.status = 'asked'), 'waiting', count(*) filter (where t.status = 'waiting'),
                 'done', count(*) filter (where t.status = 'done'), 'dropped', count(*) filter (where t.status = 'dropped'))
               from mc.tasks t));
end;
$$;

-- One task with its write-up, its whole history (up to 500 entries) and its versions' dates and notes.
create or replace function mc_api.ingest_tasks_get(p_secret text, p_code text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_id uuid;
begin
  if not mc.ingest_secret_ok(p_secret, 'tasks') then
    perform mc.log_refusal(null, 'ingest.tasks_get', 'bad_secret');
    return mc.refused('bad_secret');
  end if;
  select t.id into v_id from mc.tasks t where t.code = upper(btrim(p_code));
  if v_id is null then
    return mc.failed('not_found', 'No task has that code.');
  end if;
  return jsonb_build_object('ok', true, 'task', mc.task_json(v_id, true, 500),
    'versions', (select coalesce(jsonb_agg(jsonb_build_object('version', v.version, 'title', v.title,
                   'change_note', v.change_note, 'at', v.created_at) order by v.version), '[]'::jsonb)
                 from mc.task_versions v where v.task_id = v_id));
end;
$$;

-- Claude's changes to a task's state (OWNER_TASKS.md §2). Every one but `open` needs a note: an answer, why it waits
-- (p_waiting_for), how Claude knows it's done, what Claude checked, why it's reopened or not needed.
create or replace function mc_api.ingest_tasks_update(p_secret text, p_code text, p_action text, p_note text, p_waiting_for text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_t mc.tasks%rowtype;
  -- New lines are kept as LF only (a browser's text box sends CRLF).
  v_note text := nullif(btrim(regexp_replace(p_note, E'\r\n?', E'\n', 'g'), E' \n\t'), '');
  v_wait text := nullif(btrim(regexp_replace(p_waiting_for, E'\r\n?', E'\n', 'g'), E' \n\t'), '');
  v_problem jsonb;
  v_after text;
  v_kind text;
begin
  if not mc.ingest_secret_ok(p_secret, 'tasks') then
    perform mc.log_refusal(null, 'ingest.tasks_update', 'bad_secret');
    return mc.refused('bad_secret');
  end if;
  if p_action is null or p_action not in ('answer', 'note', 'wait', 'open', 'done', 'check', 'reopen', 'drop') then
    return mc.failed('bad_action', 'The action is answer, note, wait, open, done, check, reopen or drop.');
  end if;
  v_problem := mc.task_note_problem(v_note, p_action not in ('wait', 'open'));
  if v_problem is not null then
    return v_problem;
  end if;
  if p_action = 'wait' and not mc.task_text_ok(to_jsonb(v_wait), 3, 300) then
    return mc.failed('waiting_for_required', 'Say what the task waits for (3 to 300 characters).');
  end if;
  if mc.task_secretish(v_wait) then
    return mc.failed('looks_like_a_key', 'That looks like a key or a token, so nothing was saved.');
  end if;
  perform mc.tasks_lock();
  select * into v_t from mc.tasks t where t.code = upper(btrim(p_code)) for update;
  if not found then
    return mc.failed('not_found', 'No task has that code.');
  end if;
  case p_action
    when 'answer' then
      v_kind := 'answered';
      v_after := case when v_t.status = 'asked' then 'open' else v_t.status end;
    when 'note' then
      v_kind := 'note';
      v_after := v_t.status;
    when 'wait' then
      if v_t.status not in ('open', 'asked', 'waiting') then
        return mc.failed('not_allowed', format('%s is %s: reopen it first.', v_t.code, v_t.status));
      end if;
      v_kind := 'waiting';
      v_after := 'waiting';
    when 'open' then
      if v_t.status <> 'waiting' then
        return mc.failed('not_allowed', format('%s isn''t waiting (it is %s).', v_t.code, v_t.status));
      end if;
      v_kind := 'opened';
      v_after := 'open';
    when 'done' then
      if v_t.status not in ('open', 'asked', 'waiting') then
        return mc.failed('not_allowed', format('%s is already %s.', v_t.code, v_t.status));
      end if;
      v_kind := 'done';
      v_after := 'done';
    when 'check' then
      if v_t.status <> 'done' then
        return mc.failed('not_allowed', format('%s isn''t done (it is %s): only a done task can be checked.', v_t.code, v_t.status));
      end if;
      v_kind := 'checked';
      v_after := 'done';
    when 'reopen' then
      if v_t.status not in ('done', 'dropped') then
        return mc.failed('not_allowed', format('%s is %s, not closed.', v_t.code, v_t.status));
      end if;
      v_kind := 'reopened';
      v_after := 'open';
    when 'drop' then
      if v_t.status not in ('open', 'asked', 'waiting') then
        return mc.failed('not_allowed', format('%s is already %s.', v_t.code, v_t.status));
      end if;
      v_kind := 'dropped';
      v_after := 'dropped';
  end case;
  update mc.tasks t
    set status = v_after,
        waiting_for = case when v_after = 'waiting' then coalesce(v_wait, t.waiting_for) end,
        status_at = case when v_after <> v_t.status then now() else t.status_at end,
        checked_at = case when p_action = 'check' then now() when v_after = 'done' then t.checked_at end,
        row_version = t.row_version + 1, updated_at = now()
    where t.id = v_t.id;
  perform mc.task_log(v_t.id, 'claude', v_kind, coalesce(v_note, case when p_action = 'wait' then v_wait end),
                      v_t.status, v_after, v_t.version, null);
  return jsonb_build_object('ok', true, 'task', mc.task_json(v_t.id, false, 0));
end;
$$;

-- ================================================================================================ the owner's functions
-- The Tasks page: every task still open, asked or waiting with its write-up and its newest 50 history entries; the 50
-- most recently closed without; when Claude last read the list. Opening it moves the owner's read position (what the
-- page marks as new is what came after the previous one).
create or replace function mc_api.tasks_board()
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('tasks.read');
  v_s mc.tasks_state%rowtype;
  v_max bigint;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  perform pg_advisory_xact_lock_shared(hashtextextended('mc.tasks', 0));
  select * into v_s from mc.tasks_state s where s.id;
  select coalesce(max(e.id), 0) into v_max from mc.task_events e;
  update mc.tasks_state s set owner_seen_event_id = greatest(s.owner_seen_event_id, v_max) where s.id;
  return jsonb_build_object('ok', true, 'now', now(), 'tz', mc.tz(), 'today', (now() at time zone mc.tz())::date,
    'claude_read_at', v_s.claude_read_at, 'seen_before', v_s.owner_seen_event_id,
    'tasks', (select coalesce(jsonb_agg(mc.task_json(t.id, true, 50) order by mc.task_rank(t.status, t.priority, t.due_on), t.code), '[]'::jsonb)
              from mc.tasks t where t.status in ('open', 'asked', 'waiting')),
    'closed', (select coalesce(jsonb_agg(mc.task_json(c.id, false, 0) order by c.status_at desc, c.code), '[]'::jsonb)
               from (select t.id, t.status_at, t.code from mc.tasks t where t.status in ('done', 'dropped')
                     order by t.status_at desc, t.code limit 50) c),
    'closed_count', (select count(*) from mc.tasks t where t.status in ('done', 'dropped')));
end;
$$;

-- One task with its write-up and its whole history (a closed task opened from the list, or a link to one).
create or replace function mc_api.task_get(p_code text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('tasks.read');
  v_id uuid;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  select t.id into v_id from mc.tasks t where t.code = upper(btrim(p_code));
  if v_id is null then
    return mc.failed('not_found', 'No task has that code.');
  end if;
  return jsonb_build_object('ok', true, 'task', mc.task_json(v_id, true, 500));
end;
$$;

-- The owner's actions: done, ask (a question for Claude), note, drop (not needed), reopen. p_row_version is the version
-- the page showed: anything else is stale and changes nothing.
create or replace function mc_api.task_act(p_code text, p_row_version bigint, p_action text, p_note text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('tasks.act', false, 300);
  -- New lines are kept as LF only (a browser's text box sends CRLF).
  v_note text := nullif(btrim(regexp_replace(p_note, E'\r\n?', E'\n', 'g'), E' \n\t'), '');
  v_problem jsonb;
  v_t mc.tasks%rowtype;
  v_after text;
  v_kind text;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  if p_action is null or p_action not in ('done', 'ask', 'note', 'drop', 'reopen') then
    return mc.failed('bad_action', 'Choose done, ask, note, not needed or reopen.');
  end if;
  v_problem := mc.task_note_problem(v_note, p_action in ('ask', 'note'));
  if v_problem is not null then
    return v_problem;
  end if;
  perform mc.tasks_lock();
  select * into v_t from mc.tasks t where t.code = upper(btrim(p_code)) for update;
  if not found then
    return mc.failed('not_found', 'No task has that code.');
  end if;
  if p_row_version is distinct from v_t.row_version then
    return mc.failed('stale', 'This task changed since the page showed it. It now shows what changed: read it, then try again.');
  end if;
  case p_action
    when 'done' then
      if v_t.status not in ('open', 'asked', 'waiting') then
        return mc.failed('not_allowed', 'This task is already closed.');
      end if;
      v_kind := 'done';
      v_after := 'done';
    when 'ask' then
      if v_t.status not in ('open', 'asked', 'waiting') then
        return mc.failed('not_allowed', 'Reopen the task first, then ask.');
      end if;
      v_kind := 'asked';
      v_after := 'asked';
    when 'note' then
      v_kind := 'note';
      v_after := v_t.status;
    when 'drop' then
      if v_t.status not in ('open', 'asked', 'waiting') then
        return mc.failed('not_allowed', 'This task is already closed.');
      end if;
      v_kind := 'dropped';
      v_after := 'dropped';
    when 'reopen' then
      if v_t.status not in ('done', 'dropped') then
        return mc.failed('not_allowed', 'This task isn''t closed.');
      end if;
      v_kind := 'reopened';
      v_after := 'open';
  end case;
  update mc.tasks t
    set status = v_after,
        waiting_for = case when v_after = 'waiting' then t.waiting_for end,
        status_at = case when v_after <> v_t.status then now() else t.status_at end,
        checked_at = case when v_after = 'done' and v_t.status = 'done' then t.checked_at end,
        row_version = t.row_version + 1, updated_at = now()
    where t.id = v_t.id;
  perform mc.task_log(v_t.id, 'owner', v_kind, v_note, v_t.status, v_after, v_t.version, auth.uid());
  perform mc.audit('tasks.' || p_action, null, v_t.code, null, 'ok', jsonb_build_object('status', v_t.status),
                   jsonb_build_object('status', v_after));
  return jsonb_build_object('ok', true, 'task', mc.task_json(v_t.id, false, 0));
end;
$$;

-- Overview › Needs you now: tasks overdue or due within two days, what Claude answered or added since the owner last
-- opened Tasks, and the counts.
create or replace function mc_api.tasks_summary()
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('tasks.read');
  v_seen bigint;
  v_today date := (now() at time zone mc.tz())::date;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  select s.owner_seen_event_id into v_seen from mc.tasks_state s where s.id;
  return jsonb_build_object('ok', true, 'today', v_today,
    'open', (select count(*) from mc.tasks t where t.status = 'open'),
    'asked', (select count(*) from mc.tasks t where t.status = 'asked'),
    'waiting', (select count(*) from mc.tasks t where t.status = 'waiting'),
    'due', (select coalesce(jsonb_agg(jsonb_build_object('code', t.code, 'title', t.title, 'due_on', t.due_on,
                'status', t.status) order by t.due_on, t.code), '[]'::jsonb)
            from mc.tasks t where t.status in ('open', 'asked') and t.due_on <= v_today + 2),
    'answered', (select coalesce(jsonb_agg(jsonb_build_object('code', t.code, 'title', t.title) order by t.code), '[]'::jsonb)
                 from mc.tasks t
                 where t.status in ('open', 'asked', 'waiting')
                   and exists (select 1 from mc.task_events e where e.task_id = t.id and e.kind = 'answered'
                               and e.id > coalesce(v_seen, 0))),
    'added', (select coalesce(jsonb_agg(jsonb_build_object('code', t.code, 'title', t.title) order by t.code), '[]'::jsonb)
              from mc.tasks t
              where t.status in ('open', 'waiting')
                and exists (select 1 from mc.task_events e where e.task_id = t.id and e.kind = 'created'
                            and e.id > coalesce(v_seen, 0))));
end;
$$;

-- The same answer as before plus the open tasks (the owner's turn).
create or replace function mc_api.nav_badges()
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('nav.read');
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  return jsonb_build_object('ok', true,
    'extended_data_enabled', mc.setting_on('extended_data_enabled'),
    'open_tickets', (select count(*) from mc.tickets t where t.status in ('open', 'in_progress')),
    'services_attention', (select count(*) from mc.service_checks s
                           where s.status in ('degraded', 'down') and s.checked_at >= now() - interval '10 minutes'),
    'records_view_enabled', coalesce((select (s.value #>> '{}')::boolean from mc.settings s where s.key = 'records_view_enabled'), false),
    'marketing', (mc.marketing_waiting() ->> 'count')::integer,
    'tasks', (select count(*) from mc.tasks t where t.status = 'open'));
end;
$$;

-- ================================================================================================ privileges
-- Tables and helpers in mc: nobody but their owner. Functions in mc_api one by one: the page's to authenticated (each
-- checks the admin itself), Claude's to anon (each checks the tasks secret first).
revoke all on all tables in schema mc from public, anon, authenticated, service_role;
revoke all on all sequences in schema mc from public, anon, authenticated, service_role;
revoke all on all functions in schema mc from public, anon, authenticated, service_role;
revoke all on function
  mc_api.nav_badges(),
  mc_api.tasks_board(),
  mc_api.task_get(text),
  mc_api.task_act(text, bigint, text, text),
  mc_api.tasks_summary(),
  mc_api.ingest_tasks_push(text, jsonb),
  mc_api.ingest_tasks_feed(text, bigint),
  mc_api.ingest_tasks_get(text, text),
  mc_api.ingest_tasks_update(text, text, text, text, text)
  from public, anon, authenticated, service_role;

grant execute on function
  mc_api.nav_badges(),
  mc_api.tasks_board(),
  mc_api.task_get(text),
  mc_api.task_act(text, bigint, text, text),
  mc_api.tasks_summary()
  to authenticated;

grant execute on function
  mc_api.ingest_tasks_push(text, jsonb),
  mc_api.ingest_tasks_feed(text, bigint),
  mc_api.ingest_tasks_get(text, text),
  mc_api.ingest_tasks_update(text, text, text, text, text)
  to anon;
