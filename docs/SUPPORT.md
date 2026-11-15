# Contact support

**Settings › Help › Contact support** lets anyone, signed in or not, ask a question or report a problem from the app.
The request becomes a ticket in Lucento Mission Control (the founder's console,
`docs/mission-control/v1/PRODUCT_SCOPE.md` C), and the owner replies by email. Founder decisions FD-V4, FD-V4a, FD-V4b,
FD-V7 and FD-V15 (`docs/mission-control/v1/FOUNDER_DECISIONS.md`).

## The form

- **What is it about?** One of seven topics (`SupportTopic`): account and sign-in, Lucento Pro and payments, cloud sync,
  missing or lost records, something isn't working, an idea, something else.
- **Your message**, up to 5,000 characters, with a counter.
- **Where should we reply?** Only while signed out: an email address, checked the way the server checks it. Signed in,
  the form says the reply goes to the account's own address (the server takes it from the account, not from the phone).
- **About this phone**: "Attach details about this phone" (on by default) and, under it, exactly what goes with the
  request, line by line: app version and version code, Android version and API level, phone maker and model, whether
  cloud sync is on and when it last synced, the plan the app shows, whether the person is signed in, and the app's
  language. Turned off, nothing about the phone is attached (`SupportRules.diagnostics` returns `{}`). Nothing from the
  person's records is ever attached.
- A note says how long a request is kept, with a link to the Privacy Policy.
- **Send.** "Thanks. We'll reply by email to <address>." with the ticket reference (LC-1042), or "Saved. It will be sent
  when you're online." Refusals say what to do in plain words; too many requests in a day (`rate_limited`) or a busy
  inbox (`busy`) point to lucentoapp@gmail.com. "Write an email instead" opens the mail app.
- **Your recent requests**: the last five sent or given up on this phone, and how many are waiting. Signing out (either
  way: keeping or removing this phone's records) clears the list, so the next person here doesn't see it; requests still
  waiting go as promised, unlinked from whoever signs in next.

## What happens to a request

1. `SupportService.submit` writes the request down first (`PrefsSupportOutboxStore`: app-private SharedPreferences,
   synchronous commit, like the session-revoke queue), with a `client_ref` made once.
2. It sends `public.submit_support_request` (`SupabaseSupportRpc`: `POST /rest/v1/rpc/submit_support_request`).
   - Written signed in and that account is still signed in: with the account's token (`AuthRepository.accessToken()`).
     If no token can be had, it waits rather than send the request unlinked.
   - Written signed out, or the account that wrote it is no longer the one signed in: without an account, to the
     address saved with it (the typed address, or the writer's own). Never into another account.
3. The answer (`SupportRules.outcome`):
   - **Sent**: removed from the outbox, recorded with its ticket number.
   - **Refused** (bad email, empty or too-long message, a request the server won't take; or, on the form, a limit):
     shown and not kept. A waiting request that meets a daily limit (`rate_limited`, `busy`) stays and goes later.
   - **Later** (offline, timeout, 401/403/404/408/429/5xx): kept; `SupportWorker` sends it when the phone is online
     (WorkManager, network constraint, exponential backoff, survives the app closing). The same `client_ref` goes every
     time, so the server files one ticket however many tries it takes.
4. A request that couldn't go in 30 days is given up and shown as not sent.

The **install id** sent with a request is a random UUID made for support only (`support_install_id`). The server uses it
for its per-install limit on signed-out requests (3 a day). It is deliberately **not** the usage-counts install id: a
request can carry an email address, and reusing that id would tie a person to their anonymous usage counts.

## The daily version report (FD-V7)

`ClientReporter` calls `public.report_client(app_version, version_code, android_sdk, device_model)` with the account's
token, at most once a calendar day per account, when an account is signed in (LucentoApplication collects
`AuthRepository.observeUserId()`). Never while signed out. The day is marked before the call, so a failure is silent and
tried again the next day. Mission Control shows the version in Users and on tickets.

## Server contract

Both functions come from `supabase/migrations/20261116090200_mission_control_functions.sql` (branch
`feature/mission-control-db`), which must be applied to lucento-test and production **before** a build with this form
can send anything. Until then every request answers 404 and waits in the outbox.

| Function | Caller | Answers |
|---|---|---|
| `submit_support_request(p_topic, p_message, p_email, p_install_id, p_client_ref, p_diagnostics)` | anon, authenticated | `{ok, ticket, reply_to, duplicate?}` or `{ok: false, error}`: `invalid_topic`, `message_empty`, `message_too_long`, `invalid_request`, `invalid_email`, `rate_limited` (3 a day per install signed out, 10 a day per account), `busy` (the shared hourly cap on signed-out requests) |
| `report_client(p_app_version, p_version_code, p_android_sdk, p_device_model)` | authenticated | `{ok, recorded}`; ignored within the hour when nothing changed |

The server keeps only allow-listed diagnostics keys (`mc.clean_diagnostics`); `SupportRulesTest` checks the app sends
exactly those.

## Privacy

`feature/legal/LegalDocuments.kt` (version 2026-11-15) lists both paths, the staff-access disclosure (FD-V3), the 35
days of activity, the Google Play order amounts and the weekly encrypted backups. The website's `/privacy` must publish
the same text in the same release (`tools/legal/export_legal.py`, docs/LEGAL.md). A request is kept until it is
resolved and deleted 12 months after that, or with the account (`mc.tickets` cascades from `auth.users`).

## Tests

- `SupportRulesTest`: the diagnostics allow-list and types, nothing attached when off, the preview, email checks, form
  problems, topics, every server answer.
- `SupportServiceTest`: saved before sending; offline then sent later with one `client_ref`; signed in with its own
  token; another account signed in later sends it unlinked; no token waits; refusals not kept; limits waited out in the
  queue; a month's expiry; a failed save never claims "saved"; builds without a server.
- `ClientReporterTest`: never signed out; once a day per account; failures wait a day; no token, nothing marked.
- `ScreenshotTourTest` frame `26-contact-support`.

## Owner test (before merging)

1. Apply the Mission Control migrations to lucento-test (Mission Control's DEPLOYMENT.md).
2. Install the debug build (it talks to lucento-test). Signed out: Settings › Help › Contact support, choose a topic,
   write a message, type your email, keep "Attach details" on, Send. You should see "Thanks. We'll reply by email to …"
   and a reference like LC-1001. In Mission Control (pointed at lucento-test) › Support, the ticket shows the message and
   the details.
3. Turn on airplane mode, send another. You should see "Saved. It will be sent when you're online." Turn airplane mode
   off: within a minute or two the ticket appears in Mission Control. The form's "Your recent requests" shows it as sent.
4. Sign in, send one more: the form doesn't ask for an email, and the ticket is linked to your account.
5. After signing in, Mission Control › Users shows your app version and phone model (once a day).
