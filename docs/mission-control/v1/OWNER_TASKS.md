# Mission Control › Tasks: the owner's to-do list

Owner, 2026-11-20: "start streamlining my to do list to be part of the mission control. That will be our main interface
for you and me to update status of. For my tasks, indicate whats needed to be done with complete write-up and I can mark
the tasks there as complete and you can refer to it moving forward on the status."

Until now the owner's to-dos were rows in `docs/handover/NEXT_TASKS.md` (OT-1 … OT-41): one table cell per task, statuses
that went stale, and no way for the owner to mark anything except by telling Claude. Mission Control › Tasks replaces
that table. The old table stays in NEXT_TASKS as history.

## 1. Who does what

| | Claude (command-line tool, `tasks` secret) | The owner (Mission Control › Tasks, signed in with the authenticator) |
|---|---|---|
| Write a task | Creates it with a complete, click-by-click write-up; changes the write-up later (a new version) | — |
| Status | Answers a question, adds a note, moves a task to Waiting (with what it waits for) or back, closes it (done / not needed, with a note), records a check of the owner's done, reopens one | Marks it done, asks Claude, adds a note, says it isn't needed, reopens it |
| Read | Every task, and everything the owner did since Claude last read the list | Every task, its write-up, its notes and its history |

Claude never marks the owner's work done on its own guess: a `done` by Claude needs a note saying how Claude knows
(the owner said so in chat, or a read-only check), and a `check` records what Claude verified after the owner's done.

## 2. States

| State | The owner sees | Set by |
|---|---|---|
| `open` | **To do** | created open; a reopen; Claude's answer to a question; Claude moving a waiting task back |
| `asked` | **Asked Claude** (Claude's turn) | the owner's question |
| `waiting` | **Waiting**, with what it waits for ("the final days before launch day") | Claude only (created waiting, or moved) |
| `done` | **Done** (with "Checked by Claude" once Claude verified it) | the owner, or Claude with a note |
| `dropped` | **Not needed** | the owner (reason optional) or Claude (reason required) |

Moves: `open|asked|waiting → done|dropped`, `open|waiting → asked`, `done|dropped → open` (reopen), Claude's answer
`asked → open`, Claude's `wait`/`open` between `open|asked` and `waiting`. A note never changes the state. Every change is
an entry in the task's history (`mc.task_events`, append-only), with who did it and when.

The owner's actions name the task's `row_version` (any change bumps it); a stale one is refused (`stale`) and the page
shows the task as it is now, so nobody marks done a task whose instructions changed underneath them.

## 3. The write-up

Claude's tool sends each task as JSON (files in `lucento-studio/mission-control/owner-tasks/<code>.json`):

```json
{
  "code": "OT-37",
  "title": "Turn on auto-posting for Marketing",
  "area": "Marketing",
  "priority": "soon",
  "due_on": null,
  "status": "open",
  "waiting_for": null,
  "content": {
    "why": "What it unlocks, or what stays broken without it (1–3 sentences).",
    "minutes": 60,
    "before": ["What to have ready: accounts, the phone, a file."],
    "parts": [
      {
        "title": "Paste the database update",
        "steps": [
          {
            "text": "In the left bar click **SQL Editor**, then **+ New query**.",
            "link": { "url": "https://supabase.com/dashboard", "label": "Supabase dashboard" },
            "copy": "select mc.rotate_ingest_secret('tasks');",
            "expect": "A table with one row appears at the bottom."
          }
        ]
      }
    ],
    "done_when": "How the owner knows it worked.",
    "if_stuck": "What to do when something looks different.",
    "links": [{ "label": "DEPLOYMENT.md part J", "url": "https://github.com/jerald23simagala/lucento-mission-control/blob/main/DEPLOYMENT.md" }]
  },
  "change_note": "On an update only: what changed, in one line (shown in the history)."
}
```

- `code`: `^[A-Z]{2,4}-[0-9]{1,4}$`, the stable key (owner tasks keep the OT numbering: OT-42 onwards).
- `title` 3–140 characters; `area` 2–40 (Play Console, Phone check, Database, Mission Control, Marketing, Launch,
  Decision, Accounts & keys); `priority` `now` (do next) · `soon` · `later`; `due_on` a date or null.
- `status` and `waiting_for` apply when the task is created; afterwards only the update actions change them.
  `waiting_for` (3–300) is required exactly when the status is `waiting`.
- `content`: `why` 10–800 (required); `minutes` 1–600 or null; `before` up to 10 items of 2–400; `parts` up to 10, each
  with an optional `title` (≤ 140) and 1–40 steps, 120 steps in all; a step has `text` 2–1500 (required), and optionally
  `link` {`url`, `label` 2–80}, `copy` 1–8000 (text to paste, shown in a box with a Copy button), `expect` 2–600 ("You
  should see"); `done_when` and `if_stuck` up to 800; `links` up to 10. An open or waiting task needs at least one step.
  No other keys, 60 000 characters at most in all.
- Text may use `**bold**` (exact labels on screen) and `` `code` `` (exact text to type or see); nothing else is
  interpreted: no HTML, no Markdown links. Links are only in `link`/`links`: `https://` with a host name, at most 500
  characters; the page shows each link's host beside its label, so the owner sees where it goes.
- Refused anywhere in a task or a note: control characters (other than new lines, and tabs in `copy`) and anything that
  looks like a key: 64 hexadecimal characters in a row, a JWT, a PEM block (`-----BEGIN`), Google API keys (`AIza…`),
  AWS key ids, and the prefixes `sk_live_`, `sk_test_`, `rk_live_`, `sb_secret_`, `re_`, `ghp_`, `gho_`, `ghs_`,
  `github_pat_`, `xox…-`. Setting names such as `LUCENTO_SUPABASE_SERVICE_ROLE_KEY` are fine. Task text must never
  carry a secret, and the note box says so.
- A push with the same title, area, priority, due date and content as the current version changes nothing
  (`unchanged`); anything else makes a new version (`mc.task_versions`, immutable) and an `updated` history entry with the
  change note.

Writing rules (the owner's standing preference, [[feedback-step-by-step-owner-instructions]]): one action per step, the
exact words on the button or menu, where it is on the screen, what the owner should see after it, and what to do if they
don't. Plain words; no repository jargon unless the owner must type it. Never ask the owner to paste a key, password or
token into Mission Control: keys go straight from the clipboard into the tool that needs them.

## 4. Database (`supabase/migrations/20261120090000_mission_control_tasks.sql`)

- `mc.tasks` (one row per task: code, title, area, priority, due_on, status, waiting_for, version, content, content_hash,
  row_version, timestamps), `mc.task_versions` (every write-up, immutable), `mc.task_events` (append-only history:
  `created`, `updated`, `done`, `checked`, `reopened`, `asked`, `answered`, `note`, `waiting`, `opened`, `dropped`),
  `mc.tasks_state` (one row: Claude's and the owner's read positions, when Claude last read).
- A fifth ingest-secret purpose, `tasks` (`select mc.rotate_ingest_secret('tasks');`). Claude's tool holds it and nothing
  else holds it.
- Every writer takes `mc.tasks_lock()` (one advisory lock); readers that hand out a position take it shared, so no
  entry below it can still be on its way.
- Page functions (signed-in admins with the authenticator, `mc.gate`): `tasks_board()`, `task_get(code)`,
  `task_act(code, row_version, action, note)` (`done`, `ask`, `note`, `drop`, `reopen`; audited as `tasks.act`, 300 an
  hour), `tasks_summary()` (Overview), and `nav_badges()` with a `tasks` count (open tasks).
- Claude's functions (anon, `tasks` secret first): `ingest_tasks_push(secret, tasks)` (up to 40 tasks, all or nothing),
  `ingest_tasks_feed(secret, since)` (every task's state, the owner's entries since Claude's read position, open
  questions; moves the position and stamps when Claude read), `ingest_tasks_get(secret, code)` (one task, its write-up
  and history), `ingest_tasks_update(secret, code, action, note, waiting_for)` (`answer`, `note`, `wait`, `open`, `done`,
  `check`, `reopen`, `drop`).
- It needs the auto-posting migration first (both change the ingest-secret purposes): `APPLY_TASKS.sql` carries the
  auto-posting migration, then this one, so one paste works whether or not OT-37 was started. Auto-posting stays off and
  unscheduled until its own setup (DEPLOYMENT.md part J).

## 5. Console

Mission Control is used on a laptop (owner, 2026-11-20: it "need not be mobile friendly"). Pages are designed and
reviewed for laptop windows, 1280–1920 px and a snapped half-screen window, at text sizes up to 200 %. A narrower
window gets one column and must not break, but phones are not a target.

- **Tasks** in the navigation, second, with the count to do (tasks that can wait are left out of it). The list is
  navigation: one **Start here** card (overdue first, then due today, then priority `now`, then by due date; quiet
  while another task is open), then **To do**, **Asked Claude**, **Waiting**, **Can wait** (priority `later`) and
  **Closed** (done and not needed, 50 shown). Each row shows its code, due date, what it waits for, how far the owner
  ticked, and what is new.
- One task opens beside the list once the page is 56rem wide. The switch is a container query in rem, so at large text
  the list moves below the task instead of crushing it. The task is fetched alone with `task_get` and shows:
  - why, and what to have ready;
  - the steps by part: numbered 6.3, each number a tick box kept in this browser only; Copy for text to paste; each
    link with its host; other tasks' codes link to them;
  - done when, if stuck, links, then notes and history.

  Its first control, **All tasks**, takes the keyboard to the list. The open task's row is scrolled into view there.
- A bar under the console's header keeps the progress and the next move in reach:
  - "Start at step 1.1", then "Continue at step 6.3", then **Mark done** once every step is ticked;
  - nothing filled while it's Claude's turn or the task waits;
  - "Next: <task>" once it is closed.

  The bar sticks only while it takes at most a fifth of the screen; otherwise it stays in its place. The page's scroll
  padding follows what stays on screen, so a step reached by an address or the keyboard is never under the bar. After
  the last step come **Add a note** and **Not needed**, or the whole set when the bar doesn't stick.
- Every action opens one dialog: **Ask Claude** (filled with the step the owner is on; a question is required),
  **Mark done**, **Add a note**, **Not needed**, **Reopen**.
  - The note being written is kept in the tab (sessionStorage) until it is saved, so a refusal, a stale task, no
    connection or a sign-in never loses it.
  - A success comes back to where the owner was (the step, the end or the top), with a line saying what happened.
  - A question's line offers the sentence to paste into Claude.
- The page shows when Claude last read the list and offers that sentence ("Check my Mission Control tasks") with a
  Copy button, also inside an asked task's note. It works the same way as Marketing (no scheduled checker, MC-11), and
  warns five minutes before the idle sign-out.
- **Overview › Needs you now**:
  - tasks on the owner's turn that are overdue (red) or due within two days;
  - then what Claude answered or added since the owner last opened Tasks, three lines at most, each named by its code
    ("Due tomorrow: OT-43 · Apply for production access");
  - then the count ("9 tasks to do (4 more can wait)").

## 6. Claude's tool (`lucento-studio/mission-control/tasks.mjs`)

```
node mission-control/tasks.mjs setup --env TEST --url https://lucento-mission-control.vercel.app   (key from the clipboard)
node mission-control/tasks.mjs check
node mission-control/tasks.mjs status [--all] [--json]       what changed since Claude last read, and every open task
node mission-control/tasks.mjs show OT-37 [--json]           one task with its history
node mission-control/tasks.mjs push [OT-37 …] [--dry-run]    owner-tasks/*.json (all, or the ones named)
node mission-control/tasks.mjs set OT-37 answer|note|wait|open|done|check|reopen|drop [--note "…"] [--for "…"]
```

Settings in `%USERPROFILE%\.lucento\mission-control-tasks.json` (outside every repository; the key is never printed).
Every answer names its environment; a different one stops the tool.

**Rule for every Claude session** (CLAUDE.md, Handover Protocol): owner tasks live in Mission Control. Read them with
`tasks.mjs status` before reporting or relying on an owner task's state; add or change an owner task with a file and
`push`, never only in NEXT_TASKS.md or a chat message.

## 7. Not in this version

Tasks the owner writes for Claude, attachments, reminders by email or push notification (see the desktop-app note in
`docs/mission-control/v1/DESKTOP_APP.md`), and moving the list to the production console (with OT-28; the tool will
copy each task with its current state).
