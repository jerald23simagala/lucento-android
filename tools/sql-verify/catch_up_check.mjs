// Checks docs/mission-control/LUCENTO_TEST_CATCH_UP.sql the way the owner will use it (node catch_up_check.mjs):
//   A. a project like lucento-test (every migration before Mission Control except the seven the catch-up brings) →
//      the catch-up → APPLY_MISSION_CONTROL.sql → VERIFY_MISSION_CONTROL.sql;
//   B. a project that already has everything → the catch-up again (it must change nothing and not fail);
//   C. a project whose history shows the 2026-11-10 production apply → the catch-up refuses and changes nothing.
import { readFileSync, readdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { PGlite } from '@electric-sql/pglite';
import { SUPABASE_STANDIN } from './standin.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, '../..');
const migrations = join(root, 'supabase/migrations');
const docs = join(root, 'docs/mission-control');
const CATCH_UP = [
  '20261108090000', '20261110090000', '20261111090000', '20261111100000', '20261111110000', '20261111120000', '20261113120000',
];
const catchUp = readFileSync(join(docs, 'LUCENTO_TEST_CATCH_UP.sql'), 'utf8');
const apply = readFileSync(join(docs, 'APPLY_MISSION_CONTROL.sql'), 'utf8');
const verify = readFileSync(join(docs, 'VERIFY_MISSION_CONTROL.sql'), 'utf8');

async function project(skip) {
  const db = new PGlite();
  await db.exec(SUPABASE_STANDIN);
  for (const f of readdirSync(migrations).filter((x) => x.endsWith('.sql')).sort()) {
    if (f.includes('usage_events_purge_schedule') || f.includes('mission_control') || skip.some((v) => f.startsWith(v))) continue;
    await db.exec(readFileSync(join(migrations, f), 'utf8'));
  }
  return db;
}
const last = (results) => results[results.length - 1].rows;
let failed = 0;
const check = (ok, label, detail = '') => {
  console.log(`${ok ? 'ok  ' : 'FAIL'} ${label}${!ok && detail ? ` — ${detail}` : ''}`);
  if (!ok) failed++;
};

// A. lucento-test
{
  const db = await project(CATCH_UP);
  const before = await db.query(`select pg_catalog.to_regclass('lucento_private.purchase_checks') is null as missing`);
  check(before.rows[0].missing, 'A0 the simulated lucento-test lacks the catch-up migrations');
  // A test account that syncs without a Cloud plan, to see it listed.
  await db.exec(`insert into auth.users (id, email) values ('11111111-1111-1111-1111-111111111111', 'tester@example.com');
    insert into public.categories (id, user_id, name, kind, icon_key, created_at, updated_at)
    values ('c-1', '11111111-1111-1111-1111-111111111111', 'Food', 'EXPENSE', 'cart', now(), now());`);
  const rows = last(await db.exec(catchUp));
  const byItem = Object.fromEntries(rows.map((r) => [r.item, r.result]));
  const yes = rows.filter((r) => r.result === 'yes').length;
  check(yes === 8, 'A1 the catch-up applies; its checklist says yes for all seven migrations and test purchases', JSON.stringify(rows));
  check(/^1: tester@example.com$/.test(byItem['accounts that sync but have no Cloud plan (their uploads now pause)'] ?? ''),
    'A2 it lists the account that syncs without a Cloud plan', JSON.stringify(byItem));
  try {
    await db.exec(apply);
    check(true, 'A3 APPLY_MISSION_CONTROL.sql then runs on it');
  } catch (e) {
    check(false, 'A3 APPLY_MISSION_CONTROL.sql then runs on it', e.message);
  }
  // PGlite has no pg_cron: a stand-in table lets the read-only checks run (the real project switches pg_cron on first).
  await db.exec(`create schema if not exists cron; create table if not exists cron.job (jobname text);`);
  try {
    const v = last(await db.exec(verify))[0];
    const falses = Object.entries(v).filter(([k, val]) => val === false && !['cron_jobs_4', 'admin_added_step_a5', 'both_secrets_created_step_a6'].includes(k));
    check(falses.length === 0, 'A4 VERIFY_MISSION_CONTROL.sql: everything true except the steps still to come (and pg_cron here)', JSON.stringify(falses));
  } catch (e) {
    check(false, 'A4 VERIFY_MISSION_CONTROL.sql runs', e.message);
  }
  await db.close();
}

// B. already up to date
{
  const db = await project([]);
  try {
    const rows = last(await db.exec(catchUp));
    check(rows.filter((r) => r.result === 'yes').length === 8, 'B1 on a project that already has everything, the catch-up runs again harmlessly');
    await db.exec(apply);
    check(true, 'B2 and APPLY_MISSION_CONTROL.sql still runs after it');
  } catch (e) {
    check(false, 'B1/B2 re-run', e.message);
  }
  await db.close();
}

// C. production
{
  const db = await project([]);
  await db.exec(`create schema if not exists supabase_migrations;
    create table supabase_migrations.schema_migrations (version text primary key, name text, statements text[], created_by text);
    insert into supabase_migrations.schema_migrations values ('20261111090000', 'cloud_write_gate_and_quotas', '{}', 'owner-sql-editor-2026-11-10');
    update lucento_private.plan_limits set value_int = 0 where key = 'allow_test_purchases';`);
  try {
    await db.exec(catchUp);
    check(false, 'C1 the catch-up refuses production');
  } catch (e) {
    check(/This looks like PRODUCTION/.test(e.message), 'C1 the catch-up refuses production', e.message);
  }
  await db.exec('rollback').catch(() => {});
  const still = await db.query(`select value_int from lucento_private.plan_limits where key = 'allow_test_purchases'`);
  check(still.rows[0].value_int === 0, 'C2 and changes nothing there (test purchases stay off)');
  await db.close();
}

console.log(failed ? `\n${failed} check(s) failed` : '\nall catch-up checks passed');
process.exit(failed ? 1 : 0);
