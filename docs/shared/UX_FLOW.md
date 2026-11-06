# Lucento Shared — UX Flows

## As built (2026-11-05)

The sections after this one are the design brief. This section is what the app does on branch
`feature/lucento-shared`.

**Settings › Lucento Shared** (`feature/sharedspace/SharedSpaceScreen.kt`). Its row in Settings summarizes the role.
The screen has these states:

| State | Shown when | Content |
|---|---|---|
| Not available | the build has no cloud sync | One sentence; nothing to do |
| Sign in to share | signed out | Connect an account |
| Not sharing | signed in, in no space | Invitations waiting (each opens the review); "Manage money together"; your name; **Create a Shared Space** |
| Owner, ready | space created, nobody invited | Email field; **Send invitation**, then the system share sheet with a prewritten message (Lucento sends no email) |
| Owner, invitation waiting | pending invitation | Email, sent/expiry dates, **Share invitation**, **Withdraw invitation** |
| Owner, sharing | a Partner joined | "Shared with Pat", what Pat can and can't do, **Remove Pat**. The confirmation says what stays and that downloaded copies and exported backups can't be taken back |
| Partner | this phone holds the space | "You’re sharing Olivia’s finances", can/can't list, your name, **Leave Shared Space** (confirmation states unsynced changes that would be lost) |
| Joined on another phone | the account is a Partner, but this phone shows its own ledger | **Switch this phone** (unsynced warning when relevant) or **Leave Olivia’s Shared Space** (this phone keeps its own ledger) |

The Owner and Partner pages both let you change the name the other person sees. The Owner's page also lists recent
invitations and, while nobody has joined, offers **Close Shared Space**.

**Invitation review** (full screen, used in Settings and in onboarding). It covers:

- Who invited you, and what sharing means.
- "On this phone": the phone switches; your own data stays safe in your account and comes back if you leave;
  unsynced changes would be lost.
- Your name as the other person will see it.
- **Join**, **Decline**, and **Not now** ("Set up my own Lucento" in onboarding).

Joining shows progress while the space downloads, then lands on Home.

**Discovery.**

- *Home:* an invitation card on the theme's hero panel (`SpotlightCard`: an "Invitation" pill, the two people's
  initials, the landscape strip) says "Olivia invited you to share finances", or "You’re in Olivia’s Shared Space" when
  the account joined on another phone. **Review invitation** opens that invitation's review directly; **Open** opens
  Settings › Lucento Shared; **Not now** puts it away and the item stays in Settings.
- *Being in a shared ledger:* once someone has joined, both phones say so — a small "Shared" pill on the profile
  button of every primary screen (no names, so it stays short however many people share; owner, 2026-11-05), and the
  profile menu's Lucento Shared row ("Shared with Jerald").
  An Owner whose invitation is still open sees none of it (nothing is shared yet). There is no workspace switcher: the
  Owner's ledger is the Shared Space (RISKS_AND_DECISIONS.md §G.11).
- *Settings › Lucento Shared:* a waiting invitation leads the page in the same card, and "Start my own Shared Space
  instead" is a link under it, not a primary button (owner test 2026-11-05: a primary Create button under the invitation
  was tapped instead of joining). An Owner whose space nobody is in still sees invitations to them; joining closes that
  empty space, and the card and the review say so.
- *Onboarding:* an account that is already a Partner gets the space, with the restore screen reading "Opening Olivia’s
  Shared Space". An account with nothing of its own is offered its invitation before setup. An account with data of
  its own restores first, and Home offers the invitation afterwards.

**After a space leaves the phone** (leaving, removal, or leaving on another phone): the app returns to onboarding and
shows a one-time dialog first. It reads "You left Olivia’s Shared Space" or "Olivia’s Shared Space is no longer shared
with you". It says the finances were removed from *this* phone, adds "changes that hadn’t synced couldn’t be kept"
only when that's true, and says your own Lucento comes back next. After OK, the person's own account is checked and
restored as for any returning account.

**Daily use on a Partner's phone.**

- Hidden: the wallet, budget, category, goal, schedule and debt editors and their entry points. A quiet note says
  who manages them. Editors reached directly (schedule editor, category setup) show "Olivia manages this" instead of a
  form.
- Hidden: Restore backup.
- The whole transaction surface stays: composer, editor, delete, transfers.

**Attribution.** A transaction's details show "Added by Olivia" or "Added by you", with "Edited by Pat" underneath
when someone else made the last change. Nothing appears outside a Shared Space. No history-row chip yet.

**Wallet labels.** Inside a space, each wallet reads Shared, Yours, or Olivia’s / Pat’s; unlabelled reads as Shared.
The Owner changes it from the wallet's detail screen. Labels never change who can see or use a wallet.

**Not built yet:** history-row chip; a conflict-notice variant naming the other person; a "Shared with" line in the
sync sheet.

## Design brief (discovery)

Design intent: native to Lucento — one new Settings destination, one acceptance prompt, unobtrusive
attribution, and role-appropriate controls on existing screens. No household dashboard, no redesign of
unrelated screens; existing visual language, components and mascot throughout.

Voice: screens speak in people's names ("Shared with Alex", "Alex joined your Shared Space"), never in role
jargon. "Owner"/"Partner" appear at most where a generic word is unavoidable; Manager/Companion/Member/
Administrator/Household/Couples never appear. No relationship categories anywhere — the copy works equally for
spouses, friends, roommates or siblings.

Preconditions surfaced to the user: Lucento Shared requires cloud sync (a signed-in account with sync on) on
**both** phones. Without sync, the entry point explains that and offers the existing Connect-account flow.

## 1. Entry point

`Settings (profile menu) → Lucento Shared` — a new row in `SettingsHub`.

Four states:

| State | Who sees it | Content |
|---|---|---|
| Not sharing | anyone, default | What a Shared Space is: *one shared ledger — you both see everything, both can add and edit transactions; you keep managing wallets, budgets and goals.* Primary action: **Invite someone to share your finances**. |
| Invitation pending | Owner | Invited email, sent/expires dates, status chip. Actions: **Revoke invitation**; share-again hint (§2.3). |
| Sharing — Owner view | Owner | "Shared with <label/email>", since when; what they can do (*see everything; add, edit and delete transactions*) and what stays yours (*wallets, budgets, goals, categories, schedules*). Own label field. Action: **Remove from Shared Space** (destructive, confirm). |
| Sharing — Partner view | Partner | "You're sharing <label>'s Shared Space"; the same can/can't summary from their side. Own label field. Action: **Leave Shared Space** (destructive, confirm). |

## 2. Invitation (Owner)

1. **Invite someone** → sheet: email field (validated, lowercased) + one consequence line: *"They'll see
   everything in this ledger and can add, edit and delete transactions. Wallets, budgets and goals stay
   yours to manage."*
2. Send → `create_space_invite` → pending state. Errors mapped: already invited, already sharing, offline
   ("You're offline — try again when connected"), server refusals in safe wording.
3. **Delivery is in-app, not email** (Lucento has no email infrastructure, by design). After sending, the app
   offers the system share sheet with a prewritten message — *"I set up our shared finances in Lucento. Sign
   in with <email> and you'll find my invitation in Settings → Lucento Shared."* The screen says the person
   will find the invitation by signing in with that email.
4. Statuses: Not invited · Invitation pending · Accepted · Revoked · Expired (derived from `expires_at`).
   An expired invite offers one-tap re-invite.

## 3. Acceptance (invited person)

Discovery — two paths to the same prompt:

- **Existing account:** on app open / sync, a pending invitation addressed to the signed-in verified email
  surfaces as a dismissible Home card and inside Settings → Lucento Shared. (Poll where the app already probes
  the account after sign-in, plus on the screen open.)
- **New user:** installs Lucento, signs up with the invited email during normal onboarding (sync step or
  "I already have an account"); a found invitation is offered before solo setup continues.

The acceptance screen (full screen — this is consequential):

1. Who invited them (label/email) and what joining means: *you'll both see all of the Shared Space's finances;
   you can add, edit and delete transactions; <name> manages wallets, budgets and goals; you can leave anytime.*
2. The device consequence, plainly: *"This phone switches to the Shared Space. Your own Lucento data stays
   safe in your account and comes back if you leave."* With local unsynced changes, the existing
   account-switch confirmation wording is reused ("changes that haven't synced will be lost").
3. **Accept** → `accept_space_invite` → wipe (existing `LocalLedgerReset` path) → pull-first bootstrap with
   the existing restore progress screen → lands on the shared Home. **Dismiss** leaves the invitation pending
   until it expires or is revoked (no hard "decline" state).
4. Failure paths: expired/revoked ("This invitation is no longer valid — ask <name> to send a new one");
   network failure before accept (retry, nothing changed); interruption mid-bootstrap (existing restore
   resume on reopen; cursor rules prevent half-applied state).

## 4. Daily shared use

- **Both people** see the same Home, Transactions, Budget, Wallets, Insights — same navigation, no new tabs.
- **Transactions are fully collaborative:** both see the composer, the full-page editor and Delete, on any
  transaction, whoever created it. No approval flows.
- **Attribution, unobtrusive:** transaction details show "Added by <label>" and, when different, "Edited by
  <label>". History rows carry at most a small initial chip, and only inside a Shared Space. Solo users see
  no attribution UI at all. Signed-amount formatting rules unchanged.
- **Wallet classification:** inside a Shared Space, each wallet shows a quiet label — *Shared*, *Personal ·
  <owner label>*, *Personal · <partner label>* — in the wallet card/detail, with optional grouping in the
  Wallets list. Unclassified wallets read as Shared. The Owner sets the label in the wallet editor (one new
  field); the Partner sees labels everywhere but has no editor. **Labels never hide anything:** both people
  see all wallets, balances and transactions, and can record against any wallet — the Partner-view copy in §1
  says so. Solo users never see the field or the labels.
- **Partner's gated controls** are structure management only: wallet/budget/goal/category/schedule/debt
  editors and their entry points are not rendered (not merely disabled). View screens stay identical.
- **Explaining absence:** the Lucento Shared screen (Partner view) lists what only <name> can change, so
  missing buttons read as policy, not bugs. Gated-away controls don't exist in the semantics tree — nothing
  announced as "disabled" without a reason.
- **Conflict notice:** the existing overridden-change notice (P2-6) fires exactly as today when one person's
  edit loses to the other's; inside a Shared Space its copy may name the other person ("a newer change from
  <label>'s phone") instead of "another device" — copy-only change.
- **Sync chip:** unchanged mechanics; the sheet gains one context line, "Shared with <label>".

## 5. Removal and leaving

**Owner removes the Partner:** Settings → Lucento Shared → Remove from Shared Space → confirmation states the
effect: *"<label> immediately loses access to this ledger. Anything they added stays. What their phone has
already downloaded, and any backups they exported, can't be taken back."* → membership row deleted → back to
Not sharing (re-invite possible).

**Partner leaves:** Settings → Lucento Shared → Leave Shared Space → confirmation: *"This phone stops showing
<name>'s finances and goes back to your own Lucento. Anything you added stays in the Shared Space."* plus the
unsynced-changes warning when applicable → membership row deleted → local wipe → the existing post-wipe path:
still signed in to their own account, which is probed and restored/set up exactly like today's account flows.

**Partner discovers they were removed** (next app open / sync → `MembershipRevoked`): a full-screen notice —
*"<name> ended the Shared Space on this phone. Your own Lucento data is in your account."* — with one action,
**Continue** (same wipe + own-account restore). Shared data is not browsable from the notice. If the device is
offline when removal happens, the notice appears at the next connection; until then the downloaded copy remains
on the device (documented limitation — no remote wipe is promised). Entries made after removal that never
synced are lost, and the notice says so.

## 6. Edge-state copy requirements (for implementation)

- Every destructive confirmation names what is kept and what is lost, in that order, and makes no promise the
  system can't keep (no remote-wipe or backup-revocation implications).
- Offline attempts at invite/accept/remove/leave fail soft with a retry, never half-apply (single
  server-validated calls).
- All new screens: 48dp targets, full TalkBack labels (attribution chip reads "Added by <label>";
  classification label reads "Shared wallet" / "Personal wallet — <label>"), light/dark, font scaling, and
  populated/empty/loading/error states per the project's standing design rules. Haptics via
  `rememberHaptics()` on accept/remove/leave commits; success animation only after the committed write.
