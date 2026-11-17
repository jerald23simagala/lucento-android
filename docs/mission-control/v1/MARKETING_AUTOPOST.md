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
   auto-posting **on** (a fresh authenticator code). The three developer apps are the owner's own (DEPLOYMENT.md part J).
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
| FB + IG Reels | Facebook Reel on the Lucento Page, Instagram Reel, both public | The owner's own Meta app, switched to **Live** and used only by its admin: Standard Access, no App Review ("only for a business I own or manage"). In development mode Meta shows the app's posts only to the app's admins, developers and testers, so it must be Live |
| Lucento Facebook Page / KlaroPH Facebook Page | A photo post on that Page, with the alt text | Same Meta app; each Page has its own token |
| YouTube Shorts | Uploaded as a public Short | Uploads from unaudited API projects were long locked to private; reports from Oct 2026 say this was lifted. Mission Control checks the answer: a video YouTube kept private is reported ("YouTube kept it private: publish it in YouTube Studio") |
| TikTok | Sent to the owner's TikTok drafts (inbox); the owner taps Post | An unaudited app can only post privately (to a private account); TikTok's audit takes weeks and is often refused. The upload-to-drafts flow needs no audit |
| Lucento Community group | Nothing (by hand, as before) | Meta removed the Groups API on 22 April 2024 |
| Any other label | Nothing (by hand) | |

Vercel Hobby's cron runs once a day, so the clock is in the database: pg_cron every minute, pg_net to call the console.

## 3. Data model (schema `mc`)

- `mc.autopost_connections`: one row per provider (`meta`, `google`, `tiktok`): account id and name, **encrypted**
  access and refresh tokens with their expiry (none for Meta: it posts with the Pages' own tokens, and its user token
  isn't kept), scopes, `options` (what the account can post to: Pages with their own encrypted tokens, Instagram
  accounts with the Page each is linked to, the channel, the TikTok account), last error.
- `mc.autopost_destinations`: `lucento_page`, `klaroph_page`, `instagram`, `youtube`, `tiktok`, each picked from the
  connection's options (a Page carries its token; Instagram carries its Page's token).
- `mc.autopost_jobs`: one per (post, version, target), unique; due and late-until times; status `queued` → (`preparing`
  → `ready` for Reels) → `posted` / `sent_to_drafts`, or `failed` / `uncertain` / `cancelled` / `by_hand`; attempts,
  lease, next try, the provider's id and link, the error and its code.
- `mc.autopost_events`: what happened to each job; `mc.autopost_state`: the clock (set up, last call, last claim).
- `mc.marketing_cards.autopost_off`: the owner's "post this one by hand". Settings `autopost_enabled` (default false)
  and `autopost_tick_url`. Ingest-secret purpose `autopost` (the clock's). `job_runs.job` gains `autopost`.

Tokens: AES-256-GCM in the console (`v1:<iv>:<ciphertext>:<tag>`, base64url, the provider, the account and the
token's purpose as additional data); the key never reaches the database, and no answer to a page ever carries a
ciphertext.

## 4. Rules

- **Targets** (`mc.autopost_targets`): the labels above, matched without case or surrounding spaces.
- **Due** at the slot (10:00 for an "any time" day); **late until** 3 hours after (the day's end for "any time").
  Reels start 20 minutes ahead (Meta fetches and processes the video), then publish at the slot; the rest start at the
  slot.
- **Blocked** (`mc.autopost_blocked`, design review round 1): Mission Control never posts a post that is already in
  the apps by the owner's hand (`in_apps`: `in_apps_version` set and no automatic post of that version went out, or an
  older version is in the apps), a launch post before launch (`too_early`) or a 'coming soon' post after it
  (`after_launch`). The card says which; deleting it in the apps and tapping "I've removed it from the apps" hands it
  over.
- **Candidates** (`mc.autopost_candidates`): auto-posting on; post approved, not `autopost_off`, with a slot; current
  version ready and passing the truth check; not blocked; the target has a destination.
- **Covers** (`mc.autopost_covers`): every label on the post is a connected target. **Posts any**
  (`mc.autopost_posts_any`): at least one is; `due_soon` ("approved but not scheduled") leaves those out, as the owner
  has nothing to schedule (a manual label, such as the group, is posted at the time and asked about after the slot).
- **Jobs** are created by the claim within their lead time (never at approval), stepped under a 3-minute lease, and
  re-checked at every step: a post undone, skipped, revised or turned to by hand, a destination gone, or auto-posting off
  stops it before its next step (`cancelled`; turning auto-posting off cancels the waiting steps at once). A cancelled
  job starts over when its post can go out again (approved again, back to automatic, reconnected, on again): it never
  posted anything. Past late-until a job `failed` with `missed`.
- **A run that never reported** (`mc.autopost_settle_lapsed`, first in the claim, disconnecting and "post this one by
  hand"): every result, retry, cancellation and miss clears the lease, so a lease left behind means the run died or its
  result couldn't be saved. A step taken at or after the slot that publishes from its status (`ready`; `queued` Page,
  YouTube and TikTok steps; `preparing` Reels) may have posted: it becomes `uncertain` (`lease_lapsed`) and is never
  handed out again. A step taken earlier only prepared, and goes out again. (Security review M1.)
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
| `autopost_save_connection(provider, account_id, account_name, token_cipher, refresh_cipher, token_expires_at, refresh_expires_at, scopes, options)` | marketing.autopost_connect, step-up, 30/h | `token_cipher` null for Meta only; refreshes or clears this provider's destinations; picks the plain ones (Page named Lucento, Page named KlaroPH, the Instagram account linked to the Lucento Page, the only channel, the TikTok account) |
| `autopost_set_destination(key, option_id)` | marketing.autopost_destination, step-up, 60/h | only from the connection's options, of the right kind; null clears |
| `autopost_disconnect(provider)` | marketing.autopost_disconnect, step-up, 20/h | cancels its jobs not under way; deletes tokens and destinations |
| `autopost_credentials(key)` | marketing.autopost_credentials, 120/h | the destination's ciphertexts, for the console's Check |
| `autopost_record_check(key, ok, note)` | marketing.autopost_check, 120/h | keeps the Check's answer on the destination (Settings and the posts show it); cleared when the destination or its token changes |
| `autopost_save_token(provider, account_id, …)` | marketing.autopost_token, 120/h | after a refresh in a Check, or its error; only for that account (a reconnect meanwhile is left alone) |
| `autopost_job_action(job, action)` | marketing.decide | `retry` (failed/uncertain, approved current version), `posted` (uncertain), `by_hand` |
| `autopost_card(card, version, off)` | marketing.decide | `stale_version`; `busy` while a step is under way; `already_started` once a step began |

Console (granted to anon; each checks its secret first): `ingest_autopost_claim(tick_secret, app_secret, limit)` (both
secrets), `ingest_autopost_result(app_secret, job, lease, result)` (a repeated "posted" or "uncertain" changes
nothing), `ingest_autopost_token(app_secret, provider, account_id, …)`.

Clock: `mc.autopost_kick()` (pg_cron `mc-autopost`, every minute: off / no address / nothing due / a step still leased
→ nothing; else one `net.http_post` to the tick address with the Vault secret) and `mc.autopost_setup(url)` (owner,
SQL editor: rotates the `autopost` secret into Vault as `mc_autopost_tick`, records the address, schedules the job).

The board's cards gain `autopost {enabled, off, covers, posts_any, blocked, due_at, late_until, targets[{label, target,
destination, mode, connected, destination_name, check_ok, checked_at, job}]}`; the board gains `autopost` (the
settings: the clock, connections, destinations with their last Check, and `work_due`); the summary gains `autopost
{failed[], uncertain[], drafts_waiting[], counts}` (each ≤ 10, by slot).

## 6. Console

- `POST /api/autopost/tick` (exempt from the session gate; the bearer must be 64 hex; `maxDuration` 60 s): claim (up to
  4) → each job's step, side by side → its result under its lease. Every platform request ends within 40 s of the
  start, and a publishing call isn't started with less than 12 s left (an upload: 20 s), so the function never stops
  mid-publish; each result is then saved with up to 3 time-limited tries by 56 s (if it still doesn't land, the next
  claim judges the step as above). Tokens are renewed once per provider per run. Never logs or answers a token, a
  caption or a link with a signature, and before the secrets are checked it says nothing about its configuration.
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
  Pages and Instagram account to use). OAuth: Connect (a Server Action) verifies a fresh authenticator code, keeps the
  state, the PKCE verifier (Google) and the return address in a signed, HttpOnly, 10-minute cookie scoped to
  `/settings/connect`, and sends the owner to the platform. The platform returns to
  `/settings/connect/<provider>/callback`; the session cookies are SameSite=Strict and don't come with a return from
  another site, so that page (exempt from the session gate, reading nothing) only passes the answer on to
  `/settings/connect/<provider>/finish` with a navigation of its own (meta refresh, no script). /finish is gated like
  every page: it checks the state against the cookie and the admin, exchanges the code, encrypts the tokens and calls
  `autopost_save_connection` (whose step-up the code entered at Connect still covers).
- Settings › Auto-posting keeps each destination's last Check on its row ("Works · checked 2 min ago", "Reconnect
  needed"); turning auto-posting on checks every account and names those that can't post. The posting-clock line warns
  when something is due and the clock hasn't run for 5 minutes (`work_due`).
- Marketing card: the plan before the slot, naming both sides ("posts it on Facebook, Instagram and YouTube Mon 19:30.
  TikTok goes to your drafts…; you post it on … at the time"), each result after it (at the top of a settled card),
  Try again (or "Post it now" past the window, naming the slot) / I'll post it by hand / It's posted; a step that can't
  go on says why: paused while auto-posting is off, waiting for a posting clock that stopped, not posted in time. The
  approve button says "Approve and post Mon 19:30" only when Mission Control will post it (not blocked, an account
  that passes its Check). The chip, banner and filters agree: "Partly posted · 1 needs you" when a platform failed
  after another went out.

## 7. Security and privacy

- Only the owner's own accounts; tokens never leave the console server unencrypted; the database holds ciphertext only
  and no API role can read the tables. The tick secret lets its holder ask for a posting run, never post anything that
  isn't approved and due; the claim also needs the console's app secret.
- What the platforms receive is what the owner approved (videos, pictures, captions, alt text), sent from the owner's
  console to the owner's accounts. No customer data, ever.
- Revoking access: Disconnect in Settings deletes the tokens and withdraws the sign-in at Google and TikTok (their
  revoke endpoints; the answer says when it couldn't); Facebook's is removed in Facebook (Settings › Business
  integrations), as for Google (Account › Security › Your connections to third-party apps) or TikTok (Settings and
  privacy › Security › Manage app permissions) when the withdrawal didn't go through.
- Security review (2026-11-17, before merging): no critical or high finding. Fixed: a post whose result wasn't saved
  could be posted again (M1); "post this one by hand" while a step was posting (L1); a renewal overwriting a newer
  connection (L2); a sign-in refusal or "connected" notice from a link that wasn't this sign-in's (L3); the tick naming
  its missing settings (L4); the Meta user token kept and nothing revoked on Disconnect (L5). Kept on purpose: every
  listed Page's own token (so a Page can be chosen without reconnecting; the owner's Pages are few).

## 8. Tests

- SQL `tools/sql-verify/autopost_suite.sql` (48 checks: secrets and grants, the map, due times, connections and picks,
  every job step and refusal, coverage and due_soon, cancellations and starting over, missed, a run that never reported,
  busy, renewals for one account only, Meta without a user token, posts in the apps by hand and launch posts blocked,
  Checks kept and cleared, `work_due`, launch rules, the clock's guards, the board, the summary, the audit) and race MC6 in `concurrency.mjs` (two claims at once: each job handed out once).
  `catch_up_check.mjs` part E pastes APPLY_AUTOPOST.sql the owner's way.
- Console (`lucento-mission-control`): unit tests `autopost-crypto` (encryption, additional data, the state cookie,
  PKCE), `autopost-runner` (every target's steps against fake platforms, the retry/uncertain rules, the deadline, one
  token renewal per run), `autopost-words` (the card's lines, Approve's words, Marketing's status, filters and Overview
  lines with auto-posting) and `autopost-routes` (the tick's secrets, the callback page, /finish's refusals); end to end
  `settings-autopost.spec.ts` on the local stack's seeded connections and results.
- Owner's first run: Check on each destination, then one approved post watched at its slot.
