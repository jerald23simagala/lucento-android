# Lucento Shared — Two-Device Test (lucento-test)

A manual end-to-end check of joining, collaborating, removal and leaving. It needs about 45 minutes and two phones or
emulators. Write down anything that differs from **Expect**.

## Before you start

1. The migrations are on `lucento-test` (done 2026-11-05, NEXT_TASKS OT-6, OT-7):
   `20261105090000_receivable_wallets.sql`, `20261105120000_lucento_shared.sql`,
   `20261105120100_lucento_shared_privileges.sql`.
2. Prepare two test accounts with **confirmed** email addresses. Below, **A** is the Owner, called "Alex" in the app,
   and **B** is the Partner, called "Pat". Use email sign-in: Google sign-in in debug builds depends on the machine's
   key.
3. Use the newest debug APK in the worktree's `artifacts/apk/` (`lucento-debug-shared-2026-11-05-r5.apk`; earlier ones
   lack the joining fix, LS-K10, the account switch, the "Shared" pill or the offline sign-in check), or build one (`./gradlew assembleDebug`, output in
   `app/build/outputs/apk/debug/`). Debug builds talk to `lucento-test`. Install it on two **fresh** devices:
   `adb -s <serial> install <apk>`. Don't install over an AVD whose debug app was signed with another key.
4. To sync on demand: tap the sync chip on Home, then **Sync now**. Sync both phones after each step that changes data.

**One phone instead of two.** Test builds have **Switch account**: the profile menu (your avatar) › Switch account
opens Google's account picker straight away; it is also in Settings › Account & sync. Picking the other account
removes this account's data from the phone (it stays in the account; you're asked first if changes haven't synced),
and onboarding brings the other account back, its Shared Space included. So "phone 1" and "phone 2" become "as A" and
"as B": **sync before every switch**. What one phone can't do is have both accounts offline at once, so skip steps 3.5
and 3.6 (simultaneous offline edits), or run them on a second phone or an emulator.

## 1. Owner sets up and invites (phone 1)

1. Fresh install → **Get started** → onboarding → on the sync step sign in as A → finish. Add a wallet "Household"
   (PHP) and two expenses. Sync.
2. Settings (profile menu) → **Lucento Shared** → name "Alex" → **Create a Shared Space**.
3. Enter B's email → **Send invitation**.

**Expect:** the system share sheet opens with a prewritten message (send it anywhere, or cancel). The screen then shows
"Invitation waiting" with B's email, the sent date and an expiry 7 days out, plus **Share invitation** and
**Withdraw invitation**.

## 2. Partner joins (phone 2)

1. Fresh install → **Get started** → onboarding → on the sync step sign in as B.

**Expect:** right after sign-in, a full-screen invitation reads "Alex invited you to share finances". It explains what
sharing means and what happens on this phone.

2. Name "Pat" → **Join Alex’s Shared Space**.

**Expect:** "Joining Alex’s Shared Space and downloading it…", then Home with Household and Alex's two expenses. No
onboarding questions are left, and Settings › Lucento Shared reads "You’re sharing Alex’s finances". On phone 1, after
it reopens Lucento Shared, the screen shows "Shared with Pat".

**Expect (indicator):** on phone 2 the profile avatar carries a small "Shared" pill on every main screen, and the
profile menu's Lucento Shared row says "Shared with Alex". Phone 1 shows the same pill ("Shared with Pat" in the menu)
once it has refreshed (opening Lucento Shared refreshes at once; otherwise within 5 minutes). Before anyone joins,
phone 1 shows no pill.

*Variant:* if B finishes onboarding first ("Set up my own Lucento"), or B's account already has its own data, Home
shows an invitation card on the theme's hero colour: "Alex invited you to share finances", **Review invitation** and
**Not now**. **Review invitation** opens the review itself. Joining warns about changes that haven't synced. Settings ›
Lucento Shared shows the same card first; **Start my own Shared Space instead** is a small link under it.

*Variant (first run, 2026-11-05):* if B owns an empty Shared Space (nobody in it, no invitation of its own waiting),
Settings › Lucento Shared still shows Alex's invitation, noting "Joining closes your own Shared Space". The review
says so too, and joining closes it.

## 3. Collaboration

| # | Do | Expect |
|---|---|---|
| 3.1 | Phone 2: add an expense ₱250 against Household. Sync both | It appears on phone 1. Its details say "Added by Pat" (on phone 2: "Added by you") |
| 3.2 | Phone 1: edit that expense to ₱300. Sync both | Phone 2 shows ₱300 with "Added by you", "Edited by Alex" |
| 3.3 | Phone 2: delete one of Alex's expenses. Sync both | Gone on both; balances equal on both phones |
| 3.4 | Phone 2: airplane mode; add one, edit one, delete one; airplane off; sync both | All three changes on phone 1 |
| 3.5 | Both offline: edit the **same** expense to different amounts (phone 1 first, phone 2 a minute later); go online; sync phone 2, then phone 1 | Both show phone 2's amount (the later edit). Phone 1, whose unsynced edit lost, shows "A change you made on this phone was replaced by a newer change from another device" |
| 3.6 | Both offline: phone 1 deletes an expense; a minute later phone 2 edits it; sync both | The later edit wins: the expense comes back on phone 1 with phone 2's change |
| 3.7 | Both: transfer between two wallets (Alex adds a second wallet first) | Two linked legs; totals per currency equal on both phones |

## 4. What a Partner can't change (phone 2)

| Where | Expect |
|---|---|
| Wallets | No add button; a note "Wallets in this Shared Space are managed by Alex…"; wallet detail has no Edit |
| Budget | Allocations aren't editable; a note says budgets and categories are managed by Alex |
| Goals, Scheduled, Debts | Read-only; no create, edit, pay or archive actions |
| Settings › Data | No Restore backup (a note explains); Export still works |
| Composer, transaction editor, delete | All available |

Then on phone 1: wallet detail → **Belongs to** → "Pat’s". Sync both. **Expect:** phone 2 shows "Yours" under that
wallet, and phone 1 shows "Pat’s".

## 5. Removal

1. Phone 1: Lucento Shared → **Remove Pat** → read the confirmation (what stays; downloaded copies and exported backups
   can't be taken back) → Remove.
2. Phone 2: before syncing, add one expense offline. Then go online and Sync now (or reopen the app).

**Expect:** phone 2 returns to onboarding with "Alex’s Shared Space is no longer shared with you". The dialog says the
finances were removed from this phone and that changes that hadn't synced couldn't be kept. After **OK**, B's own
account is checked: set up as new if it's empty, restored if it has data. Phone 1 keeps everything Pat added before
removal, and doesn't have the offline expense.

## 6. Leaving

1. Phone 1: invite B again. Phone 2: join again (from Home's card or Settings).
2. Phone 2: Lucento Shared → **Leave Shared Space** → confirm.

**Expect:** onboarding with "You left Alex’s Shared Space", then B's own Lucento. Phone 1's Lucento Shared, once
reopened, shows the space ready to invite again.

## 7. A Partner on a new phone (optional)

With B a Partner, reinstall phone 2, or use a third device, and sign in as B during onboarding.

**Expect:** "Opening Alex’s Shared Space", then Home with the shared ledger. No invitation is involved.

## 8. Capacity and wrong accounts (optional)

- Phone 1 with a pending invitation: inviting a second email says the space is full.
- A third account that wasn't invited sees no invitation.
- B signed in with an unconfirmed email sees no invitation.
