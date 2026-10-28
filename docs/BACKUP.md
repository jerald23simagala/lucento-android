# JSON backup and restore

Lucento can save the ledger to a JSON file and read it back. The file is chosen with the system picker
(`ACTION_CREATE_DOCUMENT` to export, `ACTION_OPEN_DOCUMENT` to restore). Nothing is uploaded by the backup itself.
CSV export stays the spreadsheet of transactions.

## File

Schema version **1** (`BackupMigrations.CURRENT`). Top-level fields:

| Field | Meaning |
|---|---|
| `schemaVersion` | Integer. This app writes 1 and can read older versions through `BackupMigrations`. A newer version is refused. |
| `appVersion` | The Lucento version that wrote the file (`BuildConfig.VERSION_NAME`). |
| `exportedAt` | ISO-8601 instant. |
| `wallets`, `categories`, `transactions`, `budgets`, `goals`, `goalContributions`, `recurring`, `debtTerms` | The Room rows. |
| `deletions` | Unpushed and historical sync tombstones (`table`, stable `id`, `deletedAt`), so a restore can still tell the server about a delete. |
| `settings` | Theme, main currency, enabled features, display name, onboarding and category-setup flags, default wallet, guide step, acknowledged budget month, Smarter reading. |

Amounts are integer minor units plus an ISO 4217 `currency`. Calendar dates are `YYYY-MM-DD`. Instants are ISO-8601 UTC.
The stable id is the row's `client_uuid` (field name `id` in the file). Room's numeric id never leaves the device;
foreign keys in the file are the parent's stable id.

Not in the file: auth tokens, sessions, the Supabase key, the sync cursor, the synced account id, crash-report and
usage preferences, the one-time local-data notice, `dirty` / `synced_at` / `base_revision`, and goal photo files.
A custom goal photo stays on the phone that took it. Unknown fields, including anything named like a token, make the
file invalid.

## Restore

The file is parsed and checked before any write. A malformed file, a bad `schemaVersion`, a decimal amount, a broken
reference, or a forbidden field fails and changes nothing.

The preview shows how many of each record the file contains and the earliest and latest transaction date.

- **Merge** upserts by stable id. When both sides have the id, the later `updatedAt` wins (goal contributions use
  `createdAt`; a tie keeps the row already on the phone). Records only on this phone stay. Device preferences stay,
  except a default wallet that the merge removed.
- **Replace** makes the file the whole ledger inside one Room transaction. Ids that were only on this phone are
  recorded as deletions so the next sync can remove them. Preferences in the file replace theme, currency and the
  other settings listed above. Crash reports are left alone.

If the write fails, the transaction rolls back and the previous ledger remains.

Restored rows are inserted with `dirty = 1`, `synced_at = null` and `base_revision = null`. That is true when signed
out: the flag waits until sync is on. A null base revision means the next push is concurrent with the server, and the
later `updatedAt` wins. The file does not pretend to be based on a server revision.

## Older versions

`BackupMigrations` walks the document from its `schemaVersion` up to the current one, one step at a time. Version 0
called wallets `accounts` and stored dates as numbers (epoch milliseconds, or epoch days for calendar dates). The
step renames and converts them and adds the collections version 0 did not have. A new schema version adds the next step.
