// Concurrency checks for the row ceilings and the usage-count caps (20261111090000_cloud_write_gate_and_quotas.sql):
// several sessions race one limit at the same moment, which PGlite (one connection) can't do. Needs a real, THROWAWAY
// Postgres 15+ server, reached as its superuser `postgres`:
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
