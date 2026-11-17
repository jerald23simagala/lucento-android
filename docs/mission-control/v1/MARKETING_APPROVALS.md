# Mission Control: Marketing approvals (design and contract, v2)

Status: design v2, 2026-11-16, after an adversarial review by four independent reviewers (security, data model,
owner flow, integration; 2 blockers, 34 major and 44 minor issues, all addressed below or listed in section 12).
Owner request (2026-11-16): "I also want my marketing approval workflow integrated here … a section in Mission Control
so I can handle approval and change requests there." The owner said "go ahead"; the defaults in section 2 follow from
that and are reversible. This document is the contract for three builds that ship together: the database module (this
repo), the console section (`lucento-mission-control`) and Claude's command-line tool (`lucento-studio/marketing/mc`).

Not FD-4's opt-in marketing *email*: this section covers the weekly social posts Claude prepares and the owner approves
before posting them by hand.

## 1. What changes for the owner

1. Claude prepares a pack (the week's posts: videos, pictures, captions, YouTube texts, alt text, truth check) and runs
   `node marketing/mc/mc.mjs push 2026-W48`. It appears under **Marketing** (second item in the nav), and Overview's
   "Needs you now" says how many posts wait and when the first one goes out.
2. On the phone or the computer the owner plays each video and reads the texts, then per post: **Approve**, **Ask for a
   change** (a note plus optional "what": video, caption, YouTube text, alt text, timing, other), or **Don't post this
   one** (skip it, or skip it and ask Claude for another post in that slot).
3. Claude runs `mc.mjs feedback`, fixes the post and pushes **version 2** with "what changed". The post returns to
   "Waiting for you" showing the owner's request beside Claude's change and a comparison with version 1. An approval
   never carries over to a new version. The page shows whether Claude has seen each request.
4. After approving, the owner schedules from the Marketing page (Copy buttons, Download per file) in TikTok Studio, Meta
   Business Suite and YouTube Studio, then taps **I've scheduled it** (optionally "except these, I'll post them by hand at
   the time", e.g. TikTok from the phone). Mission Control then knows which version is in the apps and warns when an
   approved post goes out soon and isn't scheduled, or when the apps hold an old or skipped version.
5. Sunday: the owner types the week's numbers (followers per channel, views per video per platform) on the pack.
6. Launch day: when Lucento's Play Store page shows an Install button, the owner taps **Lucento is live on Google Play**
   and confirms. "Coming soon" posts set after that moment are listed for replacement; "Get it on Google Play" posts set
   before it are flagged as too early.

Stays on the Launch Board for now: the launch checklist and reply drafts (reply drafts need a data rule first:
commenters' names, and T32, Klaro users' data never enters Lucento's systems).

## 2. Defaults (2026-11-16, owner: "go ahead")

| Topic | Default |
|---|---|
| Database | lucento-test now (the TEST console in use); marketing moves to production with OT-28 (new `marketing` secret there, PRODUCTION profile in the tool) |
| Media | **Private Vercel Blob stores**, free tier. A **test store** connected to Production and Preview while Production still points at lucento-test; at OT-28/C4 a **production store** connected to Production only, and the test store moved to Preview only. Pathnames carry the environment (`marketing/test/…`, `marketing/production/…`) and the console refuses to sign or delete outside its own prefix. Over-use locks Blob for 30 days, so the database enforces a storage budget (section 5.3). Supabase Storage was rejected: its free quota is organisation-wide and shared with the live app, and it caps files at 50 MB |
| What is stored | The final full-quality files; Mission Control is the scheduling sheet |
| Revision after approval | The approval is cleared; the post returns to waiting with the change shown |
| Choices per post | Approve; Ask for a change; Don't post this one (skip / skip and ask for another); Undo approval / Cancel my request / Bring it back |
| Scheduling | Which version is in the apps (`in_apps_version`) plus the platforms posted by hand at the time |
| Truth gate | A push with a truth line that doesn't pass is refused for that card; Approve also requires a passing line |
| Claude's trigger | Until a scheduler exists: the page shows when Claude last checked and offers a Copy button with the prompt "Check Mission Control marketing requests" for the owner to paste into Claude. An hourly Task Scheduler check (08:00–22:00 Manila) is a separate owner decision |
| W47 / W48 | W47 settled on the Launch Board. Go/no-go for W48 in Mission Control: part I and the phone check done by Sat 21 Nov 18:00 Manila; otherwise W48 runs on the Board |
| Clock | Mission Control's time zone (`mc.tz()`, Asia/Manila) is the only clock; this PC runs on US Mountain time |

## 3. Data model (schema `mc`)

Migration `supabase/migrations/20261117090000_mission_control_marketing.sql`: additive, safe to paste twice (create …
if not exists, create or replace, drop trigger if exists, constraint drop-if-exists/add under the exact auto names),
ending with part 2's table and sequence revokes and part 4's per-function revokes and grants.

### 3.1 Helpers
- `mc.marketing_text(p text) returns text` (immutable): `normalize(regexp_replace(regexp_replace(p, E'\r\n?', E'\n',
  'g'), '^\s+|\s+$', '', 'g'), NFC)`, empty → null.
- `mc.text_items_ok(p text[], p_max int, p_len int) returns boolean` (immutable): at most one dimension, ≤ p_max items,
  no null, each 1..p_len characters after trim.
- `mc.marketing_content_hash(p_card jsonb) returns text` (stable; see 3.9).
- `mc.marketing_slot(p_day date, p_time time, p_tz text) returns tstzrange`: `[day+time, day+time]` when time is set,
  `[day 00:00, day+1 00:00)` when not ("any time" = the whole day), in `p_tz`; null day → null.
- `mc.marketing_version_ready(p_card uuid, p_version int) returns boolean` (stable): true when no media row of that
  version points at a file that is not ready, deleted or expired (true for zero media: text-only posts).
- `mc.marketing_lock()`: `perform pg_advisory_xact_lock(hashtextextended('mc.marketing', 0))`. **Every marketing writer
  takes it first** (after the gate or secret check, before any row lock): one lock order, no races. Readers that return
  the log cursor take `pg_advisory_xact_lock_shared` on the same key.

### 3.2 `mc.marketing_packs`
id uuid PK; key text unique `^[A-Za-z0-9][A-Za-z0-9._-]{0,39}$`; title text 1–200; starts_on date not null; ends_on
date not null, `ends_on >= starts_on`, at most 31 days apart; tz text not null default `mc.tz()` (fixed at creation);
created_at, updated_at.

### 3.3 `mc.marketing_cards`
| Column | Rule |
|---|---|
| id uuid PK; pack_id uuid → packs on delete cascade; key text `^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$`, unique (pack_id, key) | |
| position int not null default 0 | tie-breaker |
| kind text not null | in ('short','post','group','klaro','other') |
| phase text not null | in ('warmup','launch','any'); **required** in pushes |
| title text 1–200 | |
| slot_day date null; slot_time time null; slot_label text default '' (≤ 80) | slot_day within pack starts_on−7 .. ends_on+7 |
| platforms text[] not null default '{}' | `mc.text_items_ok(platforms, 8, 60)` |
| current_version int not null default 1 | 1..20 |
| state text not null default 'waiting' | in ('waiting','approved','changes','skipped','withdrawn') |
| withdrawn_reason text null | ≤ 500 |
| decided_at timestamptz, decided_by uuid | cleared on revision |
| in_apps_version int null | the version the owner scheduled in the apps (null: nothing scheduled) |
| by_hand text[] not null default '{}' | platform labels the owner posts by hand at the slot; ⊆ platforms |
| by_hand_done_at timestamptz null | owner confirmed the by-hand posts went out |
| created_at, updated_at | |

Checks: `in_apps_version is null or in_apps_version <= current_version`; `cardinality(by_hand) = 0 or in_apps_version is
not null`. Index (pack_id, slot_day, slot_time, position).

### 3.4 `mc.marketing_versions` (immutable after insert: a BEFORE UPDATE trigger refuses changes)
PK (card_id, version); card_id → cards on delete cascade; caption_label text default 'Caption' (≤ 80); caption text
(≤ 5000); yt_title (≤ 100); yt_desc (≤ 5000); alt_text (≤ 1500); truth text 1–2000; `truth_pass boolean generated
always as (truth ~* '^\s*pass\.(\s|$)' and truth !~* '\m(fail|fails|failed|except|but)\M') stored`; changes text
(≤ 1000, required for version > 1); content_hash text not null; created_at.

### 3.5 `mc.marketing_files` (one stored file; shared by versions of the same card when unchanged)
id uuid PK; card_id uuid → cards on delete cascade; pathname text unique: `marketing/<env>/<pack>/<card>/<12 hex>/<file
name>` (env = 'test' | 'production' | 'local', from the console's MC_ENVIRONMENT, passed by the route); file_name text
`^[A-Za-z0-9][A-Za-z0-9._ -]{0,199}$`; content_type in ('video/mp4','image/png','image/jpeg','image/webp'); `kind
text generated always as (case when content_type like 'video/%' then 'video' else 'image' end) stored`; bytes bigint
1..104857600; sha256 `^[0-9a-f]{64}$`; width, height int null 1..10000; duration_ms int null 1..600000; ready boolean
default false; uploaded_at timestamptz null, `ready = (uploaded_at is not null)`; etag text null; expires_at
timestamptz null; deleted_at timestamptz null; created_at. Unique (card_id, sha256, bytes, content_type) where
deleted_at is null.

A file is **still to come** (`mc.marketing_file_uploadable`) while it isn't uploaded, isn't deleted and is shown by the
current version of a post that waits, has a change asked or is approved. No other file is ever asked for again: one of a
version Claude replaced, of a skipped post (an undone skip asks for it again at the next push) or of a post taken out.
A file **holds room** (`mc.marketing_file_held`) while it is uploaded and not expired, or still to come: the storage
budget and the one-environment check count only these, so a file that can never arrive holds nothing. An uploaded
file of a skipped or withdrawn post stays in the store until its TTL ends.

### 3.6 `mc.marketing_media` (a version's files in order)
PK (card_id, version, position); (card_id, version) → versions on delete cascade; position 0..9; file_id uuid → files.

### 3.7 `mc.marketing_log` (append-only; both sides write)
id bigint generated always as identity PK; card_id → cards on delete cascade; version int; kind in ('created','revised',
'withdrawn','claude_note','approved','changes','skipped','undo','in_apps','removed_from_apps','posted_by_hand',
'launch_meta','note_redacted'); note text ≤ 2000; tags text[] default '{}' ⊆ ('video','caption','youtube','alt',
'timing','other','replace'); actor in ('admin','claude'); admin_id uuid, `(actor = 'admin') = (admin_id is not null)`;
at timestamptz default now(). Unique index on (card_id, version, md5(note)) where kind = 'claude_note' (idempotent
notes). Trigger: refuses UPDATE except `mc_api.marketing_redact_note` setting note to '[removed]' (session flag
`set_config('mc.marketing_redact', 'on', true)`), refuses DELETE unless the parent card no longer exists (cascades), and
refuses TRUNCATE.

### 3.8 `mc.marketing_launch` (one row: id boolean PK default true check (id))
closed_test_start date; closed_test_days int 1..60; apply_on date; pday_label text ≤ 80 (all from pushes, logged as
`launch_meta`); pday_at timestamptz (owner); pday_set_by uuid; pday_set_at timestamptz; claude_checked_at timestamptz;
claude_seen_log_id bigint not null default 0; db_id uuid not null default gen_random_uuid() (ties the tool's cursor to
this database); updated_at.

### 3.9 `mc.marketing_numbers`
PK pack_id → packs on delete cascade; followers jsonb (object of ≤ 10 channel keys `^[a-z][a-zA-Z0-9]{0,30}$` to integer
0..100000000 or null); views jsonb (object card key → object platform key `^[a-z][a-zA-Z0-9]{0,30}$` → integer or null;
≤ 60 cards); installs int null; listing_visitors int null; updated_at; updated_by uuid.

### 3.10 Content hash
`mc.marketing_content_hash(p_card jsonb)`: `encode(sha256(convert_to(jsonb_build_array(text(title), kind, phase,
slot_day::date, slot_time::time, text(slot_label), platforms (each trimmed, order kept), text(caption_label),
text(caption), text(yt_title), text(yt_desc), text(alt), text(truth), media)::text, 'UTF8')), 'hex')` where text(x) =
`mc.marketing_text(x)` and media = array of `[content_type, sha256, bytes]` by position. Stored on the version at insert;
computed on an incoming card with the same function. The tool sends no hash.

## 4. Card rules

- **Decisions name the version**; a decision on a version that is no longer current is refused (`stale_version`).
  Transitions are written as `update … where id = $1 and current_version = $2 and state = any($allowed)` and check
  `found`, under the marketing lock.
- **Approve**: state in (waiting, changes); version current; `marketing_version_ready`; `truth_pass`.
- **Ask for a change**: note 3–2000 chars, no e-mail address or phone number (refused `note_has_contact`); allowed from
  waiting, approved or changes (a second note is appended). From approved it moves to changes; `in_apps_version` is
  kept (version N is still scheduled until the owner removes it).
- **Don't post this one**: from waiting, changes or approved → skipped; tag `replace` when the owner asks for another
  post in the slot. `in_apps_version` is kept (the banner asks to remove it from the apps).
- **Undo**: approved → waiting ("Undo approval"), changes → waiting ("Cancel my request"), skipped → waiting ("Bring it
  back"); version unchanged; `in_apps_version` kept.
- **Revision** (push of version current+1 with `changes`): from waiting, approved or changes → waiting; from skipped →
  stays skipped unless the card says `"revive": true`; withdrawn → refused. Clears decided_*. Logs `revised` (actor
  claude, note = changes).
- **Withdraw** (push with `"withdrawn": true` and a `withdrawn_reason`, on the current version) → withdrawn; a repeat
  is `unchanged`; any other push to a withdrawn card is refused (`withdrawn`: use a new card key).
- **In the apps** (owner): `in_apps` sets `in_apps_version = current_version` and `by_hand = p_by_hand` (approved
  only; refused with `launch_not_live` for a launch post while it would go out too early, the same rule as the
  `too_early` flag, and with `launch_version_needed` for a 'coming soon' post whose slot is after launch, the same rule
  as `replace_after_launch` (`mc.marketing_after_launch`): approving either stays allowed); `removed_from_apps` clears
  both (any state, when `in_apps_version` is set); `posted_by_hand` sets `by_hand_done_at` (after the slot started);
  `posted_late` ("I posted it" on an approved post never scheduled, once its slot began) sets `in_apps_version`,
  `by_hand = platforms` and `by_hand_done_at = now()` in one step, logged as `posted_by_hand` ("Posted late, by hand")
  and audited as marketing.posted_late; the same two launch refusals apply.
- **Derived flags** (computed on read with the slot interval in the pack's tz):
  - `replace_after_launch`: phase warmup, pday_at set, slot end > pday_at and > now() (one that already went out can't
    be replaced), and (state in waiting/changes/approved or in_apps_version set). Urgent when in the apps.
  - `too_early`: phase launch, state not skipped/withdrawn, and pday_at null, or pday_at > slot start for a set time
    and ≥ slot end for an "any time" day (once Lucento is live that day, the rest of the day is fine)
    (`mc.marketing_launch_too_early`, which `marketing_apps` also uses to refuse `in_apps`).
  - `apps_stale`: in_apps_version set and (in_apps_version < current_version or state in skipped/withdrawn).
  - `due_soon`: approved, in_apps_version null, allowed to be scheduled (not too early, not after launch for a
    'coming soon' post), slot start < now()+36 h and slot end > now().
  - `needs_decision_soon`: state waiting (a post with Claude, or a 'coming soon' one that needs a launch version, isn't
    waiting for a yes), slot start < now()+36 h, slot end > now() (warn < 36 h, bad < 12 h).
  - `missed`: state in waiting/changes, not in the apps (in_apps_version null: a post in the apps went out at its
    slot) and slot end ≤ now() (leaves the badge; offers skip).
  - `media_missing`: current version not ready and the version is older than 2 hours (else "Uploading").
- Request status for the owner: an open request is "Claude saw it <time>" when `claude_seen_log_id >= log id`, else
  "Claude hasn't seen it yet".

## 5. SQL API

Template (part 4): `language plpgsql volatile security definer set search_path = ''`, `returns jsonb`, parameters
`p_*`, no overloads. Admin functions call `mc.gate(...)` first and are granted to `authenticated` only. Ingest
functions check their secret first and are granted to `anon` only. Audit rows use `p_target = null` and
`target_ref = '<pack>/<card> v<n>'`; notes never go into the audit.

### 5.1 Secret purpose `marketing`
Replace `ingest_secret_purpose_check` with ('app','backup','marketing'); recreate `mc.rotate_ingest_secret(p_purpose
text default 'app')` with its full body, the new list and the message 'purpose is app, backup or marketing' (owner
only, not security definer).

### 5.2 Admin functions
| Function | Gate | Notes |
|---|---|---|
| `marketing_board(p_pack text)` | ('marketing.read', false, null) | section 5.4 |
| `marketing_decide(p_card uuid, p_version int, p_decision text, p_note text, p_tags text[])` | ('marketing.decide', false, 300) | decision in ('approve','request_change','skip','undo'); audits marketing.approve / request_change / skip / undo; returns `{ok, state, cleanup: [pathnames]}` (files no current or in-apps version references, on approve and skip) |
| `marketing_apps(p_card uuid, p_version int, p_action text, p_by_hand text[])` | ('marketing.apps', false, 300) | action in ('in_apps','removed_from_apps','posted_by_hand','posted_late'); `in_apps`/`posted_late` on a launch post that is still too early → `launch_not_live`, on a 'coming soon' post after launch → `launch_version_needed`; audits marketing.in_apps / removed_from_apps / posted_by_hand / posted_late |
| `marketing_set_launch(p_local text)` | ('marketing.launch', true, 10) | step-up (fresh code); `p_local` 'YYYY-MM-DDTHH:MM' Manila or null to clear; converted `(p_local::timestamp) at time zone mc.tz()`; refuses > 1 h ahead or > 7 days back; returns `{ok, pday_at, replace_after_launch, in_apps_after_launch, too_early}`; audits marketing.launch_set / launch_cleared |
| `marketing_launch_preview(p_local text)` | ('marketing.read', false, null) | what setting that moment would flag (for the confirm step) |
| `marketing_save_numbers(p_pack text, p_followers jsonb, p_views jsonb, p_installs int, p_visitors int)` | ('marketing.numbers', false, 60) | audits marketing.numbers |
| `marketing_redact_note(p_log_id bigint, p_reason text)` | ('marketing.redact', true, 20) | sets note to '[removed]'; logs `note_redacted`; audits marketing.redact_note with the reason |
| `marketing_summary()` | ('marketing.read', false, null) | `{ok, waiting, first_slot, needs_decision_soon[], due_soon[], apps_stale[], replace_after_launch[], too_early[], missed, media_missing, media_missing_items[], media_missing_since, unseen_requests[], counts, claude_checked_at}` (each flag list ≤ 10 posts with pack, card, card_id, title, kind, phase, state, current_version, in_apps_version and slot; `unseen_requests` ≤ 10 with the request's log_id, kind, tags and time; all sorted by slot; `media_missing` is the count and `media_missing_items` the first 10 posts; `counts` the full count of every list, plus `replace_after_launch_in_apps`: how many of the posts to replace after launch are already in the apps, all of them, not only the 10 listed) |
| `marketing_media_deleted(p_pathnames text[])` | ('marketing.decide', false, 300) | ≤ 50; marks files deleted only if unreferenced; audits marketing.media_deleted (count) |
| `marketing_file(p_file uuid)` | ('marketing.read', false, null) | `{ok, pathname, file_name, content_type, ready, deleted}` for the gated download route; `not_found` otherwise |

`mc_api.nav_badges()` is recreated with its full current body plus key `marketing`: cards waiting with a ready, passing
current version and a slot that hasn't ended, leaving out 'coming soon' posts that need a launch version (the page's
"to approve" count follows the same rule).

Refusal codes and the console's words:
| Code | Owner-facing text |
|---|---|
| stale_version | "Claude changed this post a moment ago (now version N). Your note is kept: look at version N, then send it again if it still applies." |
| not_ready | "You can approve once the video has uploaded." |
| truth_failed | "This post fails the truth check, so it can't be approved. Ask Claude to fix it." |
| bad_state | "This post changed on another device. Reload to see it." |
| note_required | "Say what should change, in a few words." |
| note_has_contact | "Leave out names, e-mail addresses and phone numbers." |
| not_in_apps | "This post isn't marked as in the apps." |
| launch_version_needed | "Ask Claude for a launch version first: this post says 'coming soon' and its slot is after launch." (The card shows the same words instead of the scheduling form.) |
| quota (from "Bring it back") | "Mission Control's file storage is full, so this post's files can't come back yet. Decide or skip older posts so their files can go, then bring it back." |
| launch_not_live | "Mark Lucento as live first (below the posts): this post says 'Get it on Google Play'." (The card itself shows "Schedule this after you mark Lucento as live (below the posts)." instead of the scheduling form, so the refusal only meets a stale page.) |
| launch_future / launch_too_old | "Set this once Lucento is live (not more than an hour ahead)." / "That's more than 7 days ago." |

### 5.3 Ingest functions
| Function | Secret | Purpose |
|---|---|---|
| `ingest_marketing_pack(p_secret text, p_env text, p_pack jsonb)` | marketing | upsert pack and cards, create versions, files and slots |
| `ingest_marketing_pending(p_secret text, p_pathnames text[], p_purpose text default 'upload')` | marketing | `upload`: the subset of pathnames still to come (≤ 60; §3.5), with content type and bytes, used before issuing an upload token; `record`: any file of a current version not recorded yet unless its post was taken out (one may reach the store just before the owner skips its post), used by `/uploaded` before checking files |
| `ingest_marketing_media_ready(p_app_secret text, p_items jsonb)` | **app** (MC_INGEST_SECRET, held by the console only) | record files the console found in the store |
| `ingest_marketing_feedback(p_secret text, p_db text, p_since bigint)` | marketing | decisions since the cursor, open requests, flags; stamps claude_checked_at |

Volume limits (refused with `quota`): payload `pg_column_size(p_pack)` ≤ 1 MB; ≤ 40 cards per push; ≤ 10 media per
version; ≤ 20 versions per card; ≤ 30 pushes per hour (counted in `mc.rate_counters` under the zero uuid and action
'ingest.marketing_pack'); storage budget: bytes of files that hold room (uploaded and not expired, or still to come:
§3.5), this push's new files included, ≤ 700 MB (setting-free constant). A push is refused `wrong_environment` while
the database holds room for files of another environment.

`ingest_marketing_pack` payload:
```json
{
  "pack": { "key": "2026-W48", "title": "Week 48 · 23–28 November", "starts_on": "2026-11-23", "ends_on": "2026-11-29" },
  "launch": { "closed_test_start": "2026-11-14", "closed_test_days": 16, "apply_on": "2026-11-30", "pday_label": "Expected 1–8 Dec" },
  "cards": [{
    "key": "W48-1-sweldo-day", "position": 10, "kind": "short", "phase": "warmup",
    "title": "Sweldo day, sorted", "slot_day": "2026-11-23", "slot_time": "19:30", "slot_label": "Mon 23 Nov, 19:30",
    "platforms": ["TikTok", "FB + IG Reels", "YouTube Shorts"],
    "version": 1, "changes": null, "revive": false, "withdrawn": false, "withdrawn_reason": null,
    "caption_label": "Caption (TikTok and Reels)", "caption": "…", "yt_title": "…", "yt_desc": "…", "alt": null,
    "truth": "pass. T1 (not called AI), T3 …",
    "media": [{ "file": "W48-1-sweldo-day.mp4", "content_type": "video/mp4", "bytes": 16301234,
                "sha256": "…", "width": 1080, "height": 1920, "duration_ms": 12700 }]
  }],
  "notes": [{ "card": "W48-1-sweldo-day", "text": "Kept 'ipon': T10 forbids the other wording." }]
}
```
Per card (each in its own savepoint; a failing card never half-writes): new card → version must be 1 (`created`);
version = current → `unchanged` when the hash matches, else refused `version_changed`; version = current + 1 with
`changes` → `revised`; otherwise `version_out_of_order`; truth not passing → refused `truth_failed`; phase missing →
`phase_required`. Files: an item with the same (sha256, bytes, content_type) as a ready, live file of this card reuses
it; otherwise a new file row with a fresh pathname. A file that the console reported as a mismatch is replaced by a new
row (new 12-hex) on the next push. `uploads` lists each card's current-version files still to come (§3.5): none for a
skipped post or one taken out, and the same pathnames again once a skip is undone. Response:
```json
{ "ok": true, "pack": "2026-W48", "environment": "test",
  "cards": [{ "key": "…", "version": 1, "result": "created", "reason": null, "state": "waiting" }],
  "uploads": [{ "card": "…", "version": 1, "position": 0, "file": "W48-1-sweldo-day.mp4",
                "pathname": "marketing/test/2026-W48/W48-1-sweldo-day/3fa9c1d2e4b5/W48-1-sweldo-day.mp4",
                "content_type": "video/mp4", "bytes": 16301234 }],
  "notes": { "added": 1, "already": 0 } }
```

`ingest_marketing_media_ready(p_app_secret, p_items)`: items `[{pathname, bytes, content_type, etag, expires_at}]`
(≤ 60). Per item: `ready`, `already`, `mismatch` (wrong size or type: the file row is marked failed and the next push
issues a new pathname; the console deletes the blob), `unknown`.

`ingest_marketing_feedback(p_secret, p_db, p_since)`: takes the shared marketing lock; returns `{ok, db_id, reset,
cursor, more, now, tz, launch, open_requests[], events[], replace_after_launch[], too_early[], apps_stale[],
media_missing[]}`. `events`: owner entries with id > p_since, oldest first, ≤ 500 (`more` when capped); `cursor` = id
of the last returned event (or p_since); `reset: true` when p_db ≠ db_id or p_since > max id. Lists other than events
come from current state. Stamps `claude_checked_at` and `claude_seen_log_id`.

### 5.4 `marketing_board(p_pack)` answer
`{ok, now, tz, environment, launch, claude_checked_at, packs[], pack, numbers, cards[]}`. packs: key, title, dates,
counts {total, waiting, approved, changes, skipped, withdrawn}, newest first by `starts_on desc nulls last, created_at
desc`. Default pack: the newest with a card waiting or in changes, else the newest. Cards ordered by slot_day, slot_time
(nulls last), position, each with: id, key, position, kind, phase, title, slot_day, slot_time, slot_label, platforms,
state, current_version, decided_at, decided_by (name), in_apps_version, by_hand, by_hand_done_at, withdrawn_reason,
flags {replace_after_launch, too_early, apps_stale, due_soon, needs_decision_soon, missed, media_missing, uploading},
version {all text fields, truth_pass, changes, ready, created_at}, previous (the version before current: caption,
yt_title, yt_desc, alt_text, changes) or null, media [{position, file_id, kind, file_name, pathname, content_type, bytes,
width, height, duration_ms, ready, expired, deleted}], open_requests [{log_id, note, tags, at, seen}], latest_claude_note
(when newer than the last owner entry), log (latest 30, newest first).

## 6. Console

### 6.1 Routes for Claude's tool
Exact paths are exempted in `src/proxy.ts` (a Set checked at the top of `proxy()`; no prefix lookahead):
`/api/marketing/pack`, `/api/marketing/upload`, `/api/marketing/uploaded`, `/api/marketing/feedback`, and in development
only `/api/marketing/dev-blob`. Each: `export const dynamic = 'force-dynamic'`; bearer must match `^[0-9a-f]{64}$`
before anything else (401); body size checked in bytes (`arrayBuffer().byteLength`); errors `{error, code}` with
400/401/409/413/503/500; every answer carries `environment` (TEST/PRODUCTION/LOCAL).

| Route | Steps |
|---|---|
| `POST /api/marketing/pack` | ≤ 1 MB → `ingest_marketing_pack(secret, env, pack)` → answer + `storage: 'blob' \| 'local'` (+ `put_url` per upload in local mode) |
| `POST /api/marketing/upload` | Blob mode. Accept only `{type: 'blob.generate-presigned-url', payload: {pathname, multipart: false}}` with a pathname under this environment's prefix; `handleUploadPresigned({ body, request, getSignedToken })`; getSignedToken calls `ingest_marketing_pending(secret, [pathname])` (refusal → 401, not pending → 409), then `issueSignedToken({ pathname, operations: ['put'], validUntil: now + 30 min, allowedContentTypes: [type], maximumSizeInBytes: bytes })` and returns `urlOptions: { ttlDays, allowOverwrite: false, cacheControlMaxAge: 3600 }` with ttlDays = max(30, days until the pack's ends_on + 14); never an onUploadCompleted; returns `NextResponse.json(result)`. 503 when `BLOB_WEBHOOK_PUBLIC_KEY` (required by the helper) or the store is missing |
| `POST /api/marketing/uploaded` | `{pathnames}` ≤ 60 → `ingest_marketing_pending` (secret check; pending subset) → `head()` each confirmed one, at most 5 at a time (BlobNotFoundError → `missing`, not sent to SQL; a public-store URL → 503 "store is public") → `ingest_marketing_media_ready(MC_INGEST_SECRET, items)` → for mismatches, `del()` the blob → per-item results (ready, already, missing, mismatch) |
| `GET /api/marketing/feedback?since=&db=` | `ingest_marketing_feedback` |
| `PUT, GET /api/marketing/dev-blob` | Local only: `if (process.env.NODE_ENV !== 'development') return 404` (compiled out of production builds), plus MC_ENVIRONMENT=LOCAL, loopback SUPABASE_URL and no Blob variables. HMAC key = HMAC(MC_SESSION_SECRET, 'mc.dev-blob.v1') over method, pathname, expiry, bytes and content type; path regex and containment under `.local/marketing-blobs`; stores the content type beside the file; answers Range requests; nosniff |

Owner-facing (behind the normal gate): `GET /marketing/file/[fileId]?download=1` calls `marketing_board`-equivalent
access through a small admin function check (the file must belong to a card the admin can read: `marketing_file(p_file
uuid)` returns pathname and file_name, gate 'marketing.read'), presigns on tap, sets `download=1` with URLSearchParams
and redirects (302). If the phone check shows the private CDN ignores `download=1`, this route streams the file with
`get()` and `Content-Disposition: attachment; filename="<file_name>"`, honouring Range.

### 6.2 Media adapter (`src/lib/marketing-media.ts`, server-only)
- Mode: `blob` when a valid store id is derivable (BLOB_STORE_ID with `store_` stripped, matching `^[A-Za-z0-9]+$`) —
  OIDC only; `local` in development with the local stack; otherwise `none`. `blobOrigin()` (shared with the proxy) gives
  `https://<id>.private.blob.vercel-storage.com` or null.
- Previews: one read token `issueSignedToken({ pathname: '*', operations: ['get'], validUntil: now + 2 h })`, cached in
  module memory (in-flight promise memoised) and reused while ≥ 65 min remain; `presignUrl(token, { operation: 'get',
  pathname, access: 'private' })` with no per-URL validUntil, memoised per (pathname, token), so re-renders keep the
  same `src`. Links last 65–120 minutes. A refused token or presign → that card shows "Previews unavailable right now";
  decisions still work.
- `head`, `del` via the SDK (OIDC); refuse any pathname outside `marketing/<env>/`.
- New dependency `@vercel/blob` 2.9.0 (presigned URLs and private stores).

### 6.3 CSP
`contentSecurityPolicy(nonce, origin)` adds `media-src 'self' <origin>` and appends `<origin>` to `img-src`. No wildcard;
nothing added without a valid id. README and SECURITY_CONSIDERATIONS §7 updated.

### 6.4 The Marketing page
Layout (phone-first column, max ≈ 42rem; every control ≥ 48 px on touch screens; works at 200 % text with no sideways
scroll):
- Nav: **Marketing** second (after Overview), badge `nav_badges.marketing` (missing key = 0).
- Header: "Marketing" · "Posts Claude prepared for your yes. Schedule them from here." (with auto-posting on: "…Mission
  Control posts approved ones at their slots; the rest you schedule from here.", MARKETING_AUTOPOST.md) · on TEST: "These posts are real.
  TEST refers to the database." · one-line launch strip on phones (Closed test day N of 16 · Apply Mon 30 Nov · Launch
  expected 1–8 Dec / "Live on Google Play since Tue 2 Dec, 10:00"). "Claude last checked your requests 2 h ago" with a
  **Copy** button for "Check Mission Control marketing requests".
- **Lucento is live on Google Play…** (disclosure): "Tap this only when Lucento's Play Store page shows an Install
  button. Google's 'production access approved' e-mail isn't enough." Field "When it went live (Manila time)" defaulting
  to now → **Continue** shows the preview: "This marks 4 'coming soon' posts after Tue 2 Dec 10:00 for replacement (2
  are already in the apps) and lets Claude write 'Get it on Google Play' posts." [Yes, Lucento is live] [Change the time]
  [Cancel]; a fresh authenticator code is asked (step-up). When set: **Undo** in the same disclosure.
- Pack picker (`?pack=`): "3 approved · 2 skipped · 4 waiting".
- Card (in slot order): slot line with status (shape + word) and banners directly under it; title; platform chips;
  phase chip (Warm-up / Launch); version badge with "You asked (v1): … · Claude changed (v2): …" and a **Compare with
  version 1** disclosure; Claude's latest note when newer than the last decision; media (`<video controls playsinline
  preload="metadata">` sized from width/height; `<img>`; several images as a row), each with **Download** ("Saves to your
  phone's Downloads. If the video opens instead, tap ⋮ › Download.") and "File: W48-1-sweldo-day.mp4 · version 2";
  `onError` → "This preview has expired. [Reload]"; captions with **Copy** (select-text fallback "Selected: copy it");
  YouTube title/description and alt text in disclosures with Copy; truth line.
- Status words: waiting "Waiting for you"; approved "Approved" / "Approved · in the apps"; changes "Change asked · with
  Claude"; skipped "Skipped: not posting"; withdrawn "Taken out by Claude: <reason>".
- Banners (from flags): apps_stale (older version) "The apps still have version 1. Approve version 2, replace it in the
  apps, then tap 'I've scheduled it'." · in the apps while a change is asked "Version 1 is still scheduled for Mon 19:30.
  If Claude's fix isn't approved by then: [Let version 1 go out] [I've removed it from the apps]" · skipped or withdrawn
  but in the apps "Still scheduled for Mon 19:30. Delete it in the apps, then tap 'I've removed it'." · replace after
  launch "Says 'coming soon' but goes out after launch. Ask Claude to replace it, or don't post it." · too early "Says
  'Get it on Google Play', but Lucento isn't live yet. Don't schedule it until you've tapped 'Lucento is live on Google
  Play'." · missed "Mon 19:30 has passed without a decision." [Don't post it] · uploading "Uploading (Claude started Mon
  08:12)" · media missing "This video didn't arrive. Tell Claude: 'Upload the week 48 videos again'." · no store
  "Videos and pictures can't be shown yet: Mission Control's file storage isn't connected (DEPLOYMENT.md part I, step 4).
  You can approve once you can see them." (Approve disabled in the UI).
- Decision area by state: waiting → [Approve] [Ask for a change] and a disclosure "Don't post this one…" with [Skip it]
  [Skip it and ask Claude for another post]; approved → "I've scheduled it" with checkboxes "Except these, I'll post
  them by hand at the time" (one per platform) → [I've scheduled it]; [Undo approval]; [Ask for a change]; after the
  slot with by-hand platforms: "Did you post it on TikTok? [Yes] [Don't post it there]", one row per platform
  (MARKETING_AUTOPOST.md, design review round 4); changes → open requests with "Claude saw it
  … / hasn't seen it yet", [Approve this version anyway], [Add to my request], [Cancel my request]; skipped → [Bring it
  back]; withdrawn → read-only; in the apps → [I've removed it from the apps] where relevant.
- Ask-for-a-change form: textarea (3–2000) with "No names, handles or contact details", checkboxes (each a full-width
  48 px label) Video, Caption, YouTube text, Alt text, Timing, Other. Drafts saved to localStorage per card on every
  input (try/catch), offered on the next version, cleared on send, expired after 24 h and on sign-out.
- After each decision: "1 more post needs you: Fri 13 Nov ›" (anchor; the page's "Needs you" rule). Decisions run through a client wrapper: a failed action
  shows inline "No connection: your choice wasn't saved. Tap again." instead of the error page. The pack refreshes when
  the tab regains focus ("Claude updated 1 post: [Show it]").
- Numbers (Sunday): followers per channel (TikTok, Instagram, YouTube, Facebook Page, Community group), views per short
  per platform (TikTok, FB Reels, IG Reels, YouTube), installs and listing visitors (after launch) → Save.
- History disclosure per card: the log, newest first, with [Remove this note] on owner notes (redact, fresh code).
- Session: `?next=` survives /login and /mfa (same-origin only); a client timer from the last server round trip shows at
  25 minutes "You'll be signed out in 5 minutes. [Stay signed in]"; a decision after expiry says "You were signed out
  after 30 minutes without activity. Sign in, then tap again: this choice wasn't saved."
- Overview "Needs you now" (through `call()`: a missing function shows no marketing items; NavLinks treats a missing key
  as 0), sorted by slot: "Mon 19:30 post has no approved version yet" (warn < 36 h, bad < 12 h) · "5 posts wait for your
  yes · first goes out Mon 19:30" · "Tue 12:15 is approved but not scheduled" · "The apps still have an old or skipped
  version: Mon 19:30" · "3 'coming soon' posts are scheduled after launch" (how many are already in the apps:
  `counts.replace_after_launch_in_apps`, exact beyond the 10 listed) · "Wed 19:30 says 'Get it on Google Play' but
  Lucento isn't live yet" · "2 posts are missing their video since Mon 08:12" · "1 change request waiting for Claude since
  Tue 21:05 (goes out Wed 19:30)" (unseen 12 h or slot < 24 h). Each links to `/marketing?pack=…#card-<key>` (the
  missing files through `media_missing_items`).
- Settings › Integrations: "Vercel Blob file storage" (connected / not connected / store id invalid) and "Blob upload
  signing key (BLOB_WEBHOOK_PUBLIC_KEY)"; "Marketing key (database)" created <ago> / not created.
- Audit labels for `marketing.*` and ACTION_GROUPS "Marketing".

## 7. Claude's tool (`lucento-studio/marketing/mc/`)

- `mc.mjs` (Node ≥ 20, ESM), dependency `@vercel/blob` 2.9.0 in lucento-studio's package.json.
- Config outside every repo: `%USERPROFILE%\.lucento\mission-control.json`:
  `{"default": "TEST", "TEST": {"url": "https://lucento-mission-control.vercel.app", "secret": "…"}, "LOCAL": {"url":
  "http://127.0.0.1:3100", "secret_file": "C:/Users/…/lucento-mission-control/.local/marketing-secret.txt"}}`.
  https required except 127.0.0.1; every fetch uses `redirect: 'error'`; the secret is never printed or logged.
  Claude sessions never open this file; `mc.mjs check` validates it and prints "TEST: reachable, environment TEST".
- `mc.mjs setup --env TEST --url <url>` reads the secret from the clipboard (PowerShell `Get-Clipboard`) and writes the
  file ("Saved. Mission Control TEST is ready."), so the owner never edits JSON.
- `mc.mjs push <week> [--env] [--dry-run]`: runs `python -X utf8 marketing/board/seed_week.py --mc-pack
  marketing/weeks/<week> <tmp>.json` (copy stays single-source), reads each card's `media` files from
  `Downloads/lucento-ready-packs/<week>/` (explicit list per card in board.json, position = order), computes bytes and
  sha256 (width/height read from PNG/JPEG headers; MP4 values from board.json or null), refuses when the answer's
  environment differs from `--env`, then: (1) POST /pack; (2) POST /uploaded with every listed pathname; (3) for each
  `missing`: `uploadPresigned(pathname, await fs.openAsBlob(path), { access: 'private', contentType, handleUploadUrl,
  headers: { authorization } })` (local mode: PUT to `put_url`); an "already exists" error counts as uploaded; (4) POST
  /uploaded again; prints a per-card table. Re-running resumes. `--dry-run` prints the table of cards, versions and files
  and fails on a missing file.
- `mc.mjs feedback [--env] [--json]`: prints open requests, recent decisions and the flag lists; keeps the cursor per
  environment and db_id under `%USERPROFILE%\.lucento\`.
- `seed_week.py --mc-pack <week dir> <out.json>` (parsed before the existing positional arguments, which keep working):
  board.json cards gain `phase` (required), `media` (list of files relative to the pack folder), optional `version`,
  `changes`, `revive`, `withdrawn`, `withdrawn_reason`, and a top-level `notes`. Slots: "Ddd D Mon, HH:MM" or "Ddd D Mon,
  any time"; the date is the unique day inside the pack's ISO week (starts_on − 3 .. ends_on + 3) whose weekday matches;
  starts_on/ends_on come from the week key (Monday..Sunday); anything else fails loudly. Warns when a caption says
  "Coming soon" and phase isn't warmup. Writes UTF-8 to the file (never stdout).
- `SCHEDULE.md` gains a first line: "Working copy: it can include versions you haven't approved. Schedule from Mission
  Control › Marketing."

## 8. Security and privacy

- No service-role key. Admin functions: the admin's session. Claude's tool: anon + the `marketing` secret (separate from
  `app` and `backup`). What the marketing secret allows: push packs (create, revise or withdraw posts, which clears
  approvals), write Claude notes, update the launch details shown on the page, reserve upload slots within the quotas,
  and read the owner's decisions and notes. It cannot mark files ready (that needs the console's own `app` secret), read
  customer data, or reach any other Mission Control function. Rotate it in the SQL editor if it leaks.
- Upload tokens: one exact pathname, PUT only, the slot's type and size, 30 minutes, no overwrite.
- The Blob stores hold marketing media only, never customer data. Anyone holding a deployment's environment can read
  every file in that environment's store; preview deployments only reach the test store after OT-28.
- Preview links last 65–120 minutes and are never stored or logged, but can remain in browser history.
- Notes and captions never include a person's name or contact details; KlaroPH content never carries Klaro users' data
  (T32). The database refuses notes with e-mail addresses or phone numbers; an owner note can be redacted (audited).
- Retention: a daily job `mc.marketing_retention()` (pg_cron `mc-marketing-retention`, 21:15 UTC) deletes packs whose
  ends_on is more than 365 days old (cascading to cards, versions, files, numbers and log) after deleting nothing in the
  store (files expire there after their TTL).
- check-bundle NAMES gains BLOB_STORE_ID, BLOB_READ_WRITE_TOKEN, BLOB_WEBHOOK_PUBLIC_KEY and VERCEL_OIDC_TOKEN.

## 9. Owner setup (DEPLOYMENT.md part I, click by click)

1. Paste `docs/mission-control/APPLY_MARKETING.sql` into lucento-test's SQL editor → Run; then `VERIFY_MARKETING.sql`
   → every column `true`. (Merge the console PR only after this.)
2. Run `select mc.rotate_ingest_secret('marketing');` → copy the value once.
3. On the PC, in PowerShell from the lucento-studio folder: `node marketing/mc/mc.mjs setup --env TEST --url
   https://lucento-mission-control.vercel.app` (it reads the copied value).
4. Vercel → Storage → Create Database → Blob → name `lucento-marketing-test`, **Private**, region Singapore (sin1) →
   Connect to `lucento-mission-control`, environments Production and Preview, and **leave "Add a read-write token
   environment variable" unticked** → Redeploy.
5. Mission Control → Settings: "Vercel Blob file storage: Connected", "Blob upload signing key: Connected", "Marketing
   key: created"; Marketing shows "No posts yet".
6. Phone check with the first pack: in Chrome on the phone sign in once (password manager and authenticator on the same
   phone), open Marketing, tap ⋮ › Add to Home screen; a video plays and seeks; Download saves it; in TikTok › + ›
   Upload the video shows in the gallery.

Production (with OT-28): fold the marketing file into the regenerated `APPLY_MISSION_CONTROL.sql`; create a new
`marketing` secret there and a PRODUCTION profile in the tool; create `lucento-marketing-production` (Production only)
and move the test store to Preview only; run C4 on a Sunday after the open pack is decided, then re-push.

## 10. Tests

- SQL `tools/sql-verify/marketing_suite.sql`: refusals (customer → not_admin with null args for every admin function;
  anon → no access to admin functions; bad secret and the `app` secret refused by the marketing ingest functions; the
  marketing secret refused by media_ready), pushes (created / unchanged / version_changed / revised / out_of_order /
  withdrawn / truth_failed / phase_required / caps / quota / storage budget / slot outside the pack), file reuse across
  versions, pending and media_ready paths (ready / already / mismatch → new pathname / unknown), text-only approval,
  decisions (each transition, approve and skip from changes included, stale_version, note rules incl. contact
  refusal, skip with replace tag, undo per state,
  in_apps / removed / posted_by_hand), derived flags (replace_after_launch with "any time" on launch day, too_early,
  apps_stale, due_soon, missed but not for a post in the apps), files that can never arrive holding no room (taken
  out, skipped, replaced; in the budget and the one-environment check), feedback cursor (more, reset, shared lock), claude notes idempotent, redaction, log
  append-only (UPDATE/DELETE/TRUNCATE refused; cascade allowed), launch set/clear with time-zone conversion and limits,
  numbers validation, nav_badges key, audit rows, retention. Update K14 (anon list) and VERIFY counts in the same PR;
  add races (decide vs revise, two decides, two pushes) to `concurrency.mjs`.
- Console: unit tests (pure rules in `src/lib/marketing.ts`: status words, banners from flags, slot formatting, draft
  storage, launch preview text; `blobOrigin()`; route request validation: bearer shape, body size, upload event shape,
  pathname prefix), route tests (a bad bearer makes zero Blob or filesystem calls), e2e on the local stack with seeded
  packs including a Pixel 5 project at 360 px and a 200 % text pass (no sideways scroll, targets ≥ 48 px).
- Tool: `node --test` (slot parsing incl. 2026-W53, payload building, file table, config loader, UTF-8 captions with ₱
  and emoji) and a local end-to-end push against the local stack (start.mjs creates a marketing secret and writes
  `.local/marketing-secret.txt`; seed.mjs writes small fixture files under `.local/marketing-blobs/`).
- Before the UI ships to TEST: with the store connected, curl a presigned URL with `-r 0-1` (expect 206) and with
  `download=1` (expect an attachment), then play, seek and Download on the owner's phone.

## 11. Order of work

1. Database (this repo, feature branch, PR with CI) → owner pastes APPLY_MARKETING into lucento-test (part I step 1).
2. Console (feature branch, PR with CI) → merged after step 1.
3. Tool (lucento-studio, after a local end-to-end run).
4. Owner steps 2–6; W48 go/no-go Sat 21 Nov 18:00 Manila.

## 12. Deferred (reviewed, not in v1)

E-mail digests (needs Resend, part E); an hourly Task Scheduler check (owner decision); keying anonymous refusal
buckets per action and a "last used" time for secrets (touch shared core functions); multipart uploads above 100 MB;
moving the checklist and reply drafts; an etag check before Download.
