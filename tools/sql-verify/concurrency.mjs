// Concurrency checks for the row ceilings and the usage-count caps (20261111090000_cloud_write_gate_and_quotas.sql) and
// for Mission Control's Marketing approvals (20261117090000_mission_control_marketing.sql: one advisory lock serialises
// every writer, and the feedback reader waits for them): several sessions race at the same moment, which PGlite (one
// connection) can't do. Needs a real, THROWAWAY Postgres 15+ server, reached as its superuser `postgres`:
//
//   DATABASE_URL=postgres://postgres:postgres@localhost:5432/postgres node concurrency.mjs
//
// It creates a database of its own (lucento_concurrency_<time>), replays every migration there, runs the races and
// drops the database again. Never point it at lucento-test or production. CI runs it against a Postgres service
// container (.github/workflows/android.yml, job sql-verify).
import { readFileSync, readdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import pg from 'pg';
import { SUPABASE_STANDIN } from './standin.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const migrationsDir = resolve(here, '../../supabase/migrations');
const base = process.env.DATABASE_URL;
if (!base) {
  console.error('Set DATABASE_URL to a throwaway Postgres server (see the comment at the top).');
  process.exit(2);
}
const dbName = `lucento_concurrency_${Date.now()}`;
const results = [];
const check = (ok, label) => results.push(`${ok ? 'ok  ' : 'FAIL'} ${label}`);

const admin = new pg.Client({ connectionString: base });
await admin.connect();
// Databases an interrupted earlier run left behind.
for (const { datname } of (await admin.query(`select datname from pg_database where datname like 'lucento_concurrency_%'`)).rows) {
  await admin.query(`drop database if exists ${datname} with (force)`);
}
// UTF-8 like every Supabase database, whatever the server's own locale is.
  await admin.query(`create database ${dbName} encoding 'UTF8' template template0 lc_collate 'C' lc_ctype 'C'`);
const url = new URL(base);
url.pathname = `/${dbName}`;
const connect = async () => {
  const c = new pg.Client({ connectionString: url.toString() });
  // The final forced drop ends any session still open; that is expected, not an error to crash on.
  c.on('error', () => {});
  await c.connect();
  // A lock the test never releases fails the run instead of hanging it.
  await c.query("set statement_timeout = '30s'");
  return c;
};

try {
  const setup = await connect();
  // Roles are cluster-wide: a previous run on the same server may have left them.
  const standin = SUPABASE_STANDIN.replace(
    /create role (\w+) ([^;]+);/g,
    (_, role, rest) => `do $$ begin if not exists (select 1 from pg_roles where rolname = '${role}') then create role ${role} ${rest}; end if; end $$;`,
  );
  await setup.query(standin);
  for (const f of readdirSync(migrationsDir).filter((x) => x.endsWith('.sql')).sort()) {
    if (f.includes('usage_events_purge_schedule')) continue; // pg_cron isn't on a plain server
    await setup.query(readFileSync(join(migrationsDir, f), 'utf8'));
  }
  const user = '7a1b5c2e-0000-4000-8000-000000000001';
  await setup.query(
    `insert into auth.users (id, email, email_confirmed_at, aud, role) values ($1, 'race@lucento-test.invalid', now(), 'authenticated', 'authenticated')`,
    [user],
  );
  await setup.query(
    `insert into public.entitlements (user_id, source, status, grants, expires_at, note)
       values ($1, 'GRANT', 'ACTIVE', array['CLOUD'], now() + interval '1 day', 'concurrency fixture')`,
    [user],
  );

  // A session acting as the signed-in account, as PostgREST does it.
  const asUser = async () => {
    const c = await connect();
    await c.query(`select set_config('request.jwt.claims', $1, false)`, [JSON.stringify({ sub: user, role: 'authenticated' })]);
    await c.query('set role authenticated');
    return c;
  };
  const insertRows = (c, prefix, n) =>
    c.query(
      `insert into public.transactions (id, user_id, type, amount_minor, currency_code, wallet_id, occurred_on, note, created_at, updated_at)
       select $1 || g, $2, 'EXPENSE', 100, 'PHP', 'race-wallet', 20400, '', now(), now() from generate_series(1, $3::int) g`,
      [prefix, user, n],
    );
  const stored = async () => {
    const { rows } = await setup.query(
      `select (select count(*) from public.transactions where user_id = $1)::int as rows,
              (select row_count from lucento_private.ledger_row_counts where user_id = $1 and table_name = 'transactions')::int as counter`,
      [user],
    );
    return rows[0];
  };

  // C1 — the very first rows of an account (no counter row yet), from two sessions at once.
  {
    const w = await asUser();
    await w.query(`insert into public.wallets (id, user_id, name, type, currency_code, opening_balance_minor, created_at, updated_at)
                   values ('race-wallet', $1, 'Cash', 'CASH', 'PHP', 0, now(), now())`, [user]);
    await w.end();
    const [a, b] = [await asUser(), await asUser()];
    await Promise.all([a.query('begin'), b.query('begin')]);
    // Each commits as soon as its own insert returns: whichever takes the counter row first makes the other wait.
    await Promise.all([
      insertRows(a, 'first-a-', 4).then(() => a.query('commit')),
      insertRows(b, 'first-b-', 4).then(() => b.query('commit')),
    ]);
    await Promise.all([a.end(), b.end()]);
    const s = await stored();
    check(s.rows === 8 && s.counter === 8, `C1 two first inserts racing on a new account are both counted (rows ${s.rows}, counter ${s.counter})`);
  }

  // C2 — two transactions race the last free places: 8 rows each, 10 places left. Exactly one may commit.
  {
    const s0 = await stored();
    await setup.query(`update lucento_private.plan_limits set value_int = $1 where key = 'max_rows_transactions'`, [s0.rows + 10]);
    const [a, b] = [await asUser(), await asUser()];
    await Promise.all([a.query('begin'), b.query('begin')]);
    const outcome = async (c, prefix) => {
      try {
        await insertRows(c, prefix, 8);
        await c.query('commit');
        return 'committed';
      } catch (e) {
        await c.query('rollback');
        return e.code;
      }
    };
    // A takes the counter row's lock first; B's insert waits on it until A commits, then sees A's rows.
    const ra = outcome(a, 'race-a-');
    await new Promise((r) => setTimeout(r, 200));
    const rb = outcome(b, 'race-b-');
    const both = await Promise.all([ra, rb]);
    await Promise.all([a.end(), b.end()]);
    const s = await stored();
    check(
      both.filter((x) => x === 'committed').length === 1 && both.includes('LU001') && s.rows === s0.rows + 8 && s.counter === s.rows,
      `C2 two transactions racing for 10 places with 8 rows each: one commits, one gets LU001 (${both.join(', ')}; rows ${s.rows}/${s0.rows + 10})`,
    );
  }

  // C3 — twenty sessions at once, 3 rows each, 10 places left: never more than the ceiling, counter exact.
  {
    const s0 = await stored();
    await setup.query(`update lucento_private.plan_limits set value_int = $1 where key = 'max_rows_transactions'`, [s0.rows + 10]);
    const sessions = await Promise.all(Array.from({ length: 20 }, () => asUser()));
    const outcomes = await Promise.all(
      sessions.map((c, i) => insertRows(c, `swarm-${i}-`, 3).then(() => 'ok', (e) => e.code)),
    );
    await Promise.all(sessions.map((c) => c.end()));
    const s = await stored();
    const ok = outcomes.filter((x) => x === 'ok').length;
    check(
      ok === 3 && outcomes.filter((x) => x === 'LU001').length === 17 && s.rows === s0.rows + 9 && s.counter === s.rows,
      `C3 twenty concurrent writers, 10 places: ${ok} of 20 stored (3 rows each), the rest LU001; ceiling held (rows ${s.rows}/${s0.rows + 10}, counter ${s.counter})`,
    );
    await setup.query(`update lucento_private.plan_limits set value_int = 200000 where key = 'max_rows_transactions'`);
  }

  // C4 — the project-wide usage-count cap under a burst of anonymous callers with fresh install ids.
  {
    await setup.query(`update lucento_private.plan_limits set value_int = 25 where key = 'usage_events_daily_total'`);
    const sessions = await Promise.all(
      Array.from({ length: 40 }, async () => {
        const c = await connect();
        await c.query('set role anon');
        return c;
      }),
    );
    const outcomes = await Promise.all(
      sessions.map((c) => c.query(`select public.record_usage_event(gen_random_uuid(), 'd1_return') as stored`).then((r) => r.rows[0].stored)),
    );
    await Promise.all(sessions.map((c) => c.end()));
    const { rows } = await setup.query('select count(*)::int as n from public.usage_events');
    check(
      outcomes.filter(Boolean).length === 25 && rows[0].n === 25,
      `C4 forty concurrent anonymous counts with new install ids, daily total 25: exactly ${rows[0].n} stored`,
    );
  }

  // ---------------------------------------------------------------- Marketing approvals (MARKETING_APPROVALS.md §3.1)
  // Each race: session A opens a transaction and makes its call, which takes the marketing lock and keeps it until A
  // commits; session B makes its call meanwhile and must wait for that lock (seen as an ungranted advisory lock), then
  // act on what A committed.
  const admin = '7a1b5c2e-0000-4000-8000-0000000000a1';
  const adminSession = '7a1b5c2e-0000-4000-8000-0000000000a2';
  await setup.query(
    `insert into auth.users (id, email, email_confirmed_at, aud, role) values ($1, 'ops@lucento-test.invalid', now(), 'authenticated', 'authenticated')`,
    [admin],
  );
  await setup.query(`insert into mc.admins (user_id, email, display_name) values ($1, 'ops@lucento-test.invalid', 'Ops')`, [admin]);
  await setup.query(`insert into auth.sessions (id, user_id, aal, created_at) values ($1, $2, 'aal2', now())`, [adminSession, admin]);
  const secret = (await setup.query(`select mc.rotate_ingest_secret('marketing') as s`)).rows[0].s;
  // The owner in Mission Control: signed in, verified code (aal2), within the session's 12 hours.
  const asOwner = async () => {
    const c = await connect();
    await c.query(`select set_config('request.jwt.claims', $1, false)`,
      [JSON.stringify({ sub: admin, role: 'authenticated', aal: 'aal2', session_id: adminSession })]);
    await c.query('set role authenticated');
    return c;
  };
  // Claude's tool, through the console's routes: anon with the marketing secret.
  const asClaude = async () => {
    const c = await connect();
    await c.query('set role anon');
    return c;
  };
  const card = (key, version, extra = {}) => ({
    key, position: 0, kind: 'post', phase: 'any', title: `Race ${key}`, slot_day: null, slot_time: null, slot_label: '',
    platforms: ['Lucento Page'], version, changes: version > 1 ? `Version ${version}` : null, caption: `Caption ${version}`,
    truth: 'pass. T1', media: [], ...extra,
  });
  const pack = (key, cards) => ({ pack: { key, title: `Race ${key}`, starts_on: '2026-11-23', ends_on: '2026-11-29' }, cards });
  const push = (c, p) =>
    c.query(`select mc_api.ingest_marketing_pack($1, 'test', $2::jsonb) as j`, [secret, JSON.stringify(p)]).then((r) => r.rows[0].j);
  const decide = (c, id, version, decision, note = null) =>
    c.query(`select mc_api.marketing_decide($1, $2, $3, $4, null) as j`, [id, version, decision, note]).then((r) => r.rows[0].j);
  const cardRow = async (key) =>
    (await setup.query(`select id, state, current_version, decided_at from mc.marketing_cards where key = $1`, [key])).rows[0];
  const waitingForLock = async () => {
    await new Promise((r) => setTimeout(r, 300));
    return (await setup.query(`select count(*)::int as n from pg_locks where locktype = 'advisory' and not granted`)).rows[0].n;
  };
  {
    const c = await asClaude();
    const created = await push(c, pack('race-1', [card('r1', 1), card('r2', 1), card('r4', 1)]));
    await c.end();
    check(created.ok && created.cards.every((x) => x.result === 'created'), `M-setup Claude pushes three text-only posts (${JSON.stringify(created.cards)})`);
  }

  // MC1: the owner approves version 1 while Claude pushes version 2. The push waits, then revises the approved post: it
  // waits for the owner again, the approval cleared.
  {
    const r1 = await cardRow('r1');
    const [a, b] = [await asOwner(), await asClaude()];
    await a.query('begin');
    const approved = await decide(a, r1.id, 1, 'approve');
    const pushing = push(b, pack('race-1', [card('r1', 2)]));
    const waited = await waitingForLock();
    await a.query('commit');
    const pushed = await pushing;
    await Promise.all([a.end(), b.end()]);
    const after = await cardRow('r1');
    check(
      approved.ok && approved.state === 'approved' && waited >= 1 && pushed.cards[0].result === 'revised'
        && after.state === 'waiting' && after.current_version === 2 && after.decided_at === null,
      `MC1 approve v1 racing a push of v2: the push waits (${waited}) and revises; the post waits again at v2 (${after.state}, v${after.current_version})`,
    );
  }

  // MC2: the other order. Claude's push of version 3 holds the lock; the owner's approval of version 2 waits, then is
  // refused as stale: version 3 is what the owner has to see.
  {
    const r1 = await cardRow('r1');
    const [a, b] = [await asClaude(), await asOwner()];
    await a.query('begin');
    const pushed = await push(a, pack('race-1', [card('r1', 3)]));
    const deciding = decide(b, r1.id, 2, 'approve');
    const waited = await waitingForLock();
    await a.query('commit');
    const decided = await deciding;
    await Promise.all([a.end(), b.end()]);
    const after = await cardRow('r1');
    check(
      pushed.cards[0].result === 'revised' && waited >= 1 && decided.error === 'stale_version' && decided.current_version === 3
        && after.state === 'waiting' && after.current_version === 3,
      `MC2 a push of v3 racing approve v2: the approval waits (${waited}) and is refused (${decided.error}); the post stays waiting at v${after.current_version}`,
    );
  }

  // MC3: two approvals of one post (two devices). The second waits and finds it approved: one decision, one log entry.
  {
    const r2 = await cardRow('r2');
    const [a, b] = [await asOwner(), await asOwner()];
    await a.query('begin');
    const first = await decide(a, r2.id, 1, 'approve');
    const secondCall = decide(b, r2.id, 1, 'approve');
    const waited = await waitingForLock();
    await a.query('commit');
    const second = await secondCall;
    await Promise.all([a.end(), b.end()]);
    const { rows } = await setup.query(`select count(*)::int as n from mc.marketing_log where card_id = $1 and kind = 'approved'`, [r2.id]);
    check(
      first.ok && waited >= 1 && second.error === 'bad_state' && rows[0].n === 1 && (await cardRow('r2')).state === 'approved',
      `MC3 two approvals of one post at once: one is stored, the other waits (${waited}) and gets ${second.error} (log entries ${rows[0].n})`,
    );
  }

  // MC4: the same new pack pushed twice at once (a retry while the first is still running). The second waits and finds
  // everything there: one post, one version, one file, and the same pathname to upload in both answers.
  {
    const media = [{ file: 'race-3.mp4', content_type: 'video/mp4', bytes: 4321, sha256: 'a'.repeat(64), width: 1080, height: 1920, duration_ms: 12700 }];
    const body = pack('race-3', [card('r3', 1, { kind: 'short', media })]);
    const [a, b] = [await asClaude(), await asClaude()];
    await a.query('begin');
    const first = await push(a, body);
    const secondCall = push(b, body);
    const waited = await waitingForLock();
    await a.query('commit');
    const second = await secondCall;
    await Promise.all([a.end(), b.end()]);
    const { rows } = await setup.query(
      `select (select count(*) from mc.marketing_cards where key = 'r3')::int as cards,
              (select count(*) from mc.marketing_versions v join mc.marketing_cards c on c.id = v.card_id where c.key = 'r3')::int as versions,
              (select count(*) from mc.marketing_files f join mc.marketing_cards c on c.id = f.card_id where c.key = 'r3')::int as files`,
    );
    check(
      first.cards[0].result === 'created' && waited >= 1 && second.cards[0].result === 'unchanged'
        && rows[0].cards === 1 && rows[0].versions === 1 && rows[0].files === 1
        && first.uploads.length === 1 && second.uploads.length === 1 && first.uploads[0].pathname === second.uploads[0].pathname,
      `MC4 one pack pushed twice at once: created, then unchanged after waiting (${waited}); ${rows[0].cards} post, ${rows[0].versions} version, ${rows[0].files} file, one pathname`,
    );
  }

  // MC5: Claude's feedback while the owner's request is being written. The reader waits for the writer's lock, so the
  // request is in the answer and the cursor never moves past an entry that was still on its way.
  {
    const r4 = await cardRow('r4');
    const [a, b] = [await asOwner(), await asClaude()];
    await a.query('begin');
    const asked = await decide(a, r4.id, 1, 'request_change', 'Race note: shorter hook');
    const reading = b.query(`select mc_api.ingest_marketing_feedback($1, null, 0) as j`, [secret]).then((r) => r.rows[0].j);
    const waited = await waitingForLock();
    await a.query('commit');
    const fed = await reading;
    await Promise.all([a.end(), b.end()]);
    const { rows } = await setup.query(`select max(id)::int as id from mc.marketing_log where card_id = $1 and kind = 'changes'`, [r4.id]);
    check(
      asked.ok && waited >= 1 && fed.ok && fed.events.some((e) => e.id === rows[0].id && e.note === 'Race note: shorter hook')
        && fed.cursor >= rows[0].id && fed.open_requests.some((o) => o.log_id === rows[0].id),
      `MC5 the feedback reader waits (${waited}) for a request being written and returns it (cursor ${fed.cursor}, request ${rows[0].id})`,
    );
  }

  await setup.end();
} catch (e) {
  check(false, `the run stopped: ${e.code ?? ''} ${e.message}`);
} finally {
  await admin.query(`drop database if exists ${dbName} with (force)`);
  await admin.end();
}

const failed = results.filter((x) => x.startsWith('FAIL')).length;
console.log(`CONCURRENCY ${failed}/${results.length} failed\n${results.join('\n')}`);
process.exit(failed === 0 ? 0 : 1);
