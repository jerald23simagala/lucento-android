# Mission Control: auto-posting for Marketing (design and contract, v1)

Status: v1, 2026-11-17. Owner request: "I want an interface that when I approved it Claude will handle the posting. I
can connect all my accounts as needed." Owner choice (multiple choice): **all four platforms, TikTok via drafts**.
Builds on [MARKETING_APPROVALS.md](MARKETING_APPROVALS.md) (v2): approvals, versions and slots work as before; this
document adds what happens after Approve. Three pieces ship together: the database module (migration
`20261118090000_mission_control_autopost.sql`), the console (`lucento-mission-control`: Settings › Auto-posting, the
posting run, the Marketing card) and the owner's setup (lucento-mission-control `DEPLOYMENT.md` part J).

## 1. What changes for the owner

1. Once, in Settings › Auto-posting: connect **Facebook and Instagram** (one Meta sign-in: the Lucento Page, the
   KlaroPH Page and the Instagram account linked to the Lucento Page), **YouTube** and **TikTok**; check each; turn
   auto-posting **on** (a fresh authenticator code).
2. Each week, as before, the owner approves posts in Marketing. An approved post now says **"Posts automatically
   Mon 19:30"** with each platform; at the slot Mission Control posts it, even with the owner's PC off.
3. After the slot the card shows each platform's result with a link (**Posted 19:31 · View**). TikTok says **"In your
   TikTok drafts: open TikTok and tap Post"**, and the existing "Did you post it on TikTok? [Yes]" follows.
4. Something went wrong: the card and Overview say so (**"Instagram couldn't post Mon 19:30's video"**) with **Try
   again** and **I'll post it by hand**. Mission Control never posts more than 3 hours late on its own.
5. Before its slot, any post can still be undone, skipped or changed (nothing is posted), or turned to **"Post this one
   by hand"**. Turning auto-posting off stops everything not under way.

## 2. What each platform allows (research 2026-11-17; recheck before changing this)

| Label on a post | What Mission Control does | Why |
|---|---|---|
| FB + IG Reels | Facebook Reel on the Lucento Page, Instagram Reel, both public | Meta app in development mode used only by its admin: Standard Access, no App Review ("only for a business I own or manage") |
| Lucento Facebook Page / KlaroPH Facebook Page | A photo post on that Page, with the alt text | Same Meta app; each Page has its own token |
| YouTube Shorts | Uploaded as a public Short | Uploads from unaudited API projects were long locked to private; reports from Oct 2026 say this was lifted. Mission Control checks the answer: a video YouTube kept private is reported ("YouTube kept it private: publish it in YouTube Studio") |
| TikTok | Sent to the owner's TikTok drafts (inbox); the owner taps Post | An unaudited app can only post privately (to a private account); TikTok's audit takes weeks and is often refused. The upload-to-drafts flow needs no audit |
| Lucento Community group | Nothing (by hand, as before) | Meta removed the Groups API on 22 April 2024 |
| Any other label | Nothing (by hand) | |

Vercel Hobby's cron runs once a day, so the clock is in the database: pg_cron every minute, pg_net to call the console.

## 3. Data model (schema `mc`)

- `mc.autopost_connections`: one row per provider (`meta`, `google`, `tiktok`): account id and name, **encrypted**
  access and refresh tokens with their expiry, scopes, `options` (what the account can post to: Pages with their own
  encrypted tokens, Instagram accounts with the Page each is linked to, the channel, the TikTok account), last error.
- `mc.autopost_destinations`: `lucento_page`, `klaroph_page`, `instagram`, `youtube`, `tiktok`, each picked from the
  connection's options (a Page carries its token; Instagram carries its Page's token).
- `mc.autopost_jobs`: one per (post, version, target), unique; due and late-until times; status `queued` → (`preparing`
  → `ready` for Reels) → `posted` / `sent_to_drafts`, or `failed` / `uncertain` / `cancelled` / `by_hand`; attempts,
  lease, next try, the provider's id and link, the error and its code.
- `mc.autopost_events`: what happened to each job; `mc.autopost_state`: the clock (set up, last call, last claim).
- `mc.marketing_cards.autopost_off`: the owner's "post this one by hand". Settings `autopost_enabled` (default false)
  and `autopost_tick_url`. Ingest-secret purpose `autopost` (the clock's). `job_runs.job` gains `autopost`.

Tokens: AES-256-GCM in the console (`v1:<iv>:<ciphertext>:<tag>`, base64url, the provider and account as additional
data); the key never reaches the database, and no answer to a page ever carries a ciphertext.

## 4. Rules

- **Targets** (`mc.autopost_targets`): the labels above, matched without case or surrounding spaces.
- **Due** at the slot (10:00 for an "any time" day); **late until** 3 hours after (the day's end for "any time").
  Reels start 20 minutes ahead (Meta fetches and processes the video), then publish at the slot; the rest start at the
  slot.
- **Candidates** (`mc.autopost_candidates`): auto-posting on; post approved, not `autopost_off`, with a slot; current
  version ready and passing the truth check; not a launch post before launch nor a 'coming soon' post after it (the
  same rules as "I've scheduled it"); the target has a destination.
- **Covers** (`mc.autopost_covers`): every label on the post is a connected target. `due_soon` ("approved but not
  scheduled") leaves those out; a post with a manual label (the group) still raises it.
- **Jobs** are created by the claim within their lead time (never at approval), stepped under a 3-minute lease, and
  re-checked at every step: a post undone, skipped, revised or turned to by hand, a destination gone, or auto-posting off
  stops it before its next step (`cancelled`). Past late-until a job `failed` with `missed`.
- **Results**: `preparing` (checked again in a minute), `ready` (published at the slot), `posted`, `sent_to_drafts`,
  `failed` (retried up to 3 attempts when the console says it may help, at 30 s × attempts or as asked), `uncertain`
  (the platform may have posted it: never retried; the owner looks). A late "posted" from a lapsed lease is still
  recorded: what happened on the platform wins.
- **Out**: the first job posted (or sent to drafts, or taken by hand) sets `in_apps_version` to that version; `by_hand`
  gets the labels the owner still finishes (manual ones, a target sent to drafts or taken by hand, an unconnected
  target), so the page asks "Did you post it on …?" after the slot. Nothing changes when the post was revised or undone
  meanwhile.

## 5. SQL API

Owner (granted to authenticated; each calls `mc.gate` first, its own hourly counter):

| Function | Gate | Notes |
|---|---|---|
| `autopost_settings()` | marketing.read | enabled, the clock, connections (no tokens), destinations |
| `autopost_set_enabled(p_on)` | marketing.autopost_enable, step-up to turn on, 20/h | refuses `clock_not_set`, `nothing_connected` |
| `autopost_save_connection(provider, account_id, account_name, token_cipher, refresh_cipher, token_expires_at, refresh_expires_at, scopes, options)` | marketing.autopost_connect, step-up, 30/h | refreshes or clears this provider's destinations; picks the plain ones (Page named Lucento, Page named KlaroPH, the Instagram account linked to the Lucento Page, the only channel, the TikTok account) |
| `autopost_set_destination(key, option_id)` | marketing.autopost_destination, step-up, 60/h | only from the connection's options, of the right kind; null clears |
| `autopost_disconnect(provider)` | marketing.autopost_disconnect, step-up, 20/h | cancels its jobs not under way; deletes tokens and destinations |
| `autopost_credentials(key)` | marketing.autopost_credentials, 120/h | the destination's ciphertexts, for the console's Check |
| `autopost_save_token(...)` | marketing.autopost_token, 120/h | after a refresh in a Check, or its error |
| `autopost_job_action(job, action)` | marketing.decide | `retry` (failed/uncertain, approved current version), `posted` (uncertain), `by_hand` |
| `autopost_card(card, version, off)` | marketing.decide | `stale_version`; `already_started` once a step began |

Console (granted to anon; each checks its secret first): `ingest_autopost_claim(tick_secret, app_secret, limit)` (both
secrets), `ingest_autopost_result(app_secret, job, lease, result)`, `ingest_autopost_token(app_secret, ...)`.

Clock: `mc.autopost_kick()` (pg_cron `mc-autopost`, every minute: off / no address / nothing due / a step still leased
→ nothing; else one `net.http_post` to the tick address with the Vault secret) and `mc.autopost_setup(url)` (owner,
SQL editor: rotates the `autopost` secret into Vault as `mc_autopost_tick`, records the address, schedules the job).

The board's cards gain `autopost {enabled, off, covers, due_at, late_until, targets[{label, target, destination, mode,
connected, destination_name, job}]}`; the board gains `autopost` (the settings); the summary gains `autopost {failed[],
uncertain[], drafts_waiting[], counts}` (each ≤ 10, by slot).

## 6. Console

- `POST /api/autopost/tick` (exempt from the session gate; the bearer must be 64 hex): claim → for each job, its step →
  result; stops taking new steps after ~45 s. Never logs a token, caption or link with a token.
- Steps per target:
  - **Facebook Reel**: start an upload session and let Meta fetch the video from a presigned Blob link (ahead) →
    `ready` → at the slot finish with `video_state=PUBLISHED` and the caption → `posted` (link `facebook.com/reel/<id>`).
  - **Instagram Reel**: create a REELS container from the presigned link (ahead) → poll `status_code` → `ready` → at the
    slot `media_publish` → `posted` (the permalink).
  - **Facebook photo** (either Page): `/{page}/photos` with the presigned link, the caption and `alt_text_custom` →
    `posted`.
  - **YouTube**: resumable upload streamed from Blob (title, description, public, not made for kids) → `posted`, or
    `uncertain` when YouTube kept it private.
  - **TikTok**: refresh the daily token if needed → inbox upload (`/v2/post/publish/inbox/video/init/`, one chunk) →
    `sent_to_drafts`.
- Errors: 429/5xx/network → `failed` retryable; 4xx (permissions, media) → `failed` with the platform's message; an
  expired or revoked token → `failed` "Reconnect <platform> in Settings"; a timeout after a publish call → `uncertain`.
- Settings › Auto-posting: the switch, the clock line, each provider (Connect / Reconnect / Check / Disconnect; the
  Pages and Instagram account to use). OAuth: `/settings/connect/<provider>` (state and PKCE in a signed, HttpOnly,
  10-minute cookie) and `/settings/connect/<provider>/callback`; connecting needs a fresh authenticator code first.
- Marketing card: the plan before the slot, each result after it, Try again / I'll post it by hand / It's posted; the
  approve button says "Approve and post Mon 19:30" when Mission Control will post it.

## 7. Security and privacy

- Only the owner's own accounts; tokens never leave the console server unencrypted; the database holds ciphertext only
  and no API role can read the tables. The tick secret lets its holder ask for a posting run, never post anything that
  isn't approved and due; the claim also needs the console's app secret.
- What the platforms receive is what the owner approved (videos, pictures, captions, alt text), sent from the owner's
  console to the owner's accounts. No customer data, ever.
- Revoking access: Disconnect in Settings, and/or remove the app in Facebook (Settings › Business integrations), Google
  (Account › Security › Third-party access) or TikTok (Settings › Security › Manage app permissions).

## 8. Tests

- SQL `tools/sql-verify/autopost_suite.sql` (38 checks: secrets and grants, the map, due times, connections and picks,
  every job step and refusal, coverage and due_soon, cancellations, missed, launch rules, the clock's guards, the board,
  the summary, the audit) and race MC6 in `concurrency.mjs` (two claims at once: each job handed out once).
  `catch_up_check.mjs` part E pastes APPLY_AUTOPOST.sql the owner's way.
- Console: unit tests (encryption, each provider's requests and answers with a fake fetch, the step runner, the routes'
  checks), e2e for Settings and the card with seeded jobs.
- Owner's first run: Check on each destination, then one approved post watched at its slot.
