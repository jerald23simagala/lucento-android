// Checks docs/mission-control/LUCENTO_TEST_CATCH_UP.sql the way the owner will use it (node catch_up_check.mjs):
//   A. a project like lucento-test (every migration before Mission Control except the seven the catch-up brings) →
//      the catch-up → APPLY_MISSION_CONTROL.sql → VERIFY_MISSION_CONTROL.sql;
//   B. a project that already has everything → the catch-up again (it must change nothing and not fail);
//   C. a project whose history shows the 2026-11-10 production apply → the catch-up refuses and changes nothing;
//   D. Marketing approvals on top: APPLY_MISSION_CONTROL.sql → APPLY_MARKETING.sql twice (the owner may paste it again),
//      and a third time with a post stored → VERIFY_MISSION_CONTROL.sql still all true → VERIFY_MARKETING.sql all true;
//      and APPLY_MARKETING.sql is current (python tools/supabase/build_marketing_apply.py --check);
//   E. auto-posting on top of that: APPLY_AUTOPOST.sql twice (and APPLY_MARKETING.sql pasted again after it) →
//      VERIFY_MISSION_CONTROL.sql, VERIFY_MARKETING.sql and VERIFY_AUTOPOST.sql all true (but the clock, which waits for
//      the owner's setup and pg_cron, and pg_net, which PGlite lacks);
//   F. Tasks on top of Marketing approvals: APPLY_TASKS.sql twice, its key made, then APPLY_AUTOPOST.sql pasted again (the
//      owner may follow part J after part K) → every VERIFY file all true (but the clock, pg_net and later steps).
import { spawnSync } from 'node:child_process';
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
const applyMarketing = readFileSync(join(docs, 'APPLY_MARKETING.sql'), 'utf8');
const verifyMarketing = readFileSync(join(docs, 'VERIFY_MARKETING.sql'), 'utf8');
const applyAutopost = readFileSync(join(docs, 'APPLY_AUTOPOST.sql'), 'utf8');
const verifyAutopost = readFileSync(join(docs, 'VERIFY_AUTOPOST.sql'), 'utf8');
const applyTasks = readFileSync(join(docs, 'APPLY_TASKS.sql'), 'utf8');
const verifyTasks = readFileSync(join(docs, 'VERIFY_TASKS.sql'), 'utf8');
// VERIFY_MISSION_CONTROL.sql columns that wait for the owner's later steps (or for pg_cron, which PGlite lacks).
const LATER_STEPS = ['cron_jobs_4', 'admin_added_step_a5', 'both_secrets_created_step_a6'];

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
    const falses = Object.entries(v).filter(([k, val]) => val === false && !LATER_STEPS.includes(k));
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

// D. Marketing approvals (docs/mission-control/APPLY_MARKETING.sql) on a project that has Mission Control
{
  const built = spawnSync('python', ['-I', join(root, 'tools/supabase/build_marketing_apply.py'), '--check'], { encoding: 'utf8' });
  check(built.status === 0, 'D0 APPLY_MARKETING.sql is generated from the migration and current',
    `${built.error?.message ?? ''} ${built.stdout ?? ''} ${built.stderr ?? ''}`.trim());
  const db = await project([]);
  await db.exec(apply);
  try {
    await db.exec(applyMarketing);
    await db.exec(applyMarketing);
    check(true, 'D1 APPLY_MARKETING.sql runs after APPLY_MISSION_CONTROL.sql, and again (pasted twice)');
  } catch (e) {
    check(false, 'D1 APPLY_MARKETING.sql runs after APPLY_MISSION_CONTROL.sql, and again (pasted twice)', e.message);
  }
  // Pasted again once posts are stored: the re-added constraints accept them, and the database's id (Claude's cursor) stays.
  try {
    const secret = (await db.query(`select mc.rotate_ingest_secret('marketing') as s`)).rows[0].s;
    const before = (await db.query(`select db_id::text as id from mc.marketing_launch`)).rows[0].id;
    await db.query('set role anon');
    const pushed = (await db.query(`select mc_api.ingest_marketing_pack($1, 'test', $2::jsonb) as j`, [secret, JSON.stringify({
      pack: { key: '2026-W48', title: 'Week 48', starts_on: '2026-11-23', ends_on: '2026-11-29' },
      cards: [{ key: 'W48-1', kind: 'post', phase: 'any', title: 'A post', version: 1, truth: 'pass. T1', platforms: ['Lucento Page'],
        media: [{ file: 'a.png', content_type: 'image/png', bytes: 10, sha256: 'b'.repeat(64) }] }],
    })])).rows[0].j;
    await db.query('reset role');
    await db.exec(applyMarketing);
    const after = (await db.query(`select (select count(*) from mc.marketing_cards)::int as cards,
      (select count(*) from mc.marketing_files)::int as files, (select db_id::text from mc.marketing_launch) as id,
      mc.ingest_secret_ok($1, 'marketing') as secret_ok`, [secret])).rows[0];
    check(pushed.ok && pushed.cards[0].result === 'created' && after.cards === 1 && after.files === 1 && after.id === before && after.secret_ok,
      'D1b pasted again with a post stored: the post, its file, the marketing secret and the database id stay', JSON.stringify({ pushed, after }));
  } catch (e) {
    check(false, 'D1b pasted again with a post stored', e.message);
  }
  await db.exec(`create schema if not exists cron; create table if not exists cron.job (jobname text);`);
  try {
    const v = last(await db.exec(verify))[0];
    const falses = Object.entries(v).filter(([k, val]) => val === false && !LATER_STEPS.includes(k));
    check(falses.length === 0, 'D2 VERIFY_MISSION_CONTROL.sql stays true with Marketing approvals added', JSON.stringify(falses));
  } catch (e) {
    check(false, 'D2 VERIFY_MISSION_CONTROL.sql runs with Marketing approvals added', e.message);
  }
  try {
    const m = last(await db.exec(verifyMarketing))[0];
    const falses = Object.entries(m).filter(([, val]) => val !== true);
    check(falses.length === 0 && Object.keys(m).length >= 15, 'D3 VERIFY_MARKETING.sql: every column true', JSON.stringify(m));
  } catch (e) {
    check(false, 'D3 VERIFY_MARKETING.sql runs', e.message);
  }
  await db.close();
}

// E. Auto-posting (docs/mission-control/APPLY_AUTOPOST.sql) on a project that has Marketing approvals
{
  const db = await project([]);
  await db.exec(apply);
  await db.exec(applyMarketing);
  try {
    await db.exec(applyAutopost);
    await db.exec(applyAutopost);
    check(true, 'E1 APPLY_AUTOPOST.sql runs after APPLY_MARKETING.sql, and again (pasted twice)');
  } catch (e) {
    check(false, 'E1 APPLY_AUTOPOST.sql runs after APPLY_MARKETING.sql, and again (pasted twice)', e.message);
  }
  // APPLY_MARKETING.sql pasted again later puts back its own versions of the shared checks; the owner then pastes
  // APPLY_AUTOPOST.sql again (DEPLOYMENT.md part J says so), which restores auto-posting's.
  try {
    await db.exec(applyMarketing);
    await db.exec(applyAutopost);
    const s = (await db.query(`select mc.rotate_ingest_secret('autopost') is not null as ok`)).rows[0];
    check(s.ok, 'E1b APPLY_MARKETING.sql then APPLY_AUTOPOST.sql pasted again: the autopost secret purpose is back');
  } catch (e) {
    check(false, 'E1b APPLY_MARKETING.sql then APPLY_AUTOPOST.sql pasted again', e.message);
  }
  await db.exec(`create schema if not exists cron; create table if not exists cron.job (jobname text);`);
  try {
    const v = last(await db.exec(verify))[0];
    const falses = Object.entries(v).filter(([k, val]) => val === false && !LATER_STEPS.includes(k));
    check(falses.length === 0, 'E2 VERIFY_MISSION_CONTROL.sql stays true with auto-posting added', JSON.stringify(falses));
  } catch (e) {
    check(false, 'E2 VERIFY_MISSION_CONTROL.sql runs with auto-posting added', e.message);
  }
  try {
    const m = last(await db.exec(verifyMarketing))[0];
    const falses = Object.entries(m).filter(([, val]) => val !== true);
    check(falses.length === 0, 'E3 VERIFY_MARKETING.sql stays true with auto-posting added', JSON.stringify(m));
  } catch (e) {
    check(false, 'E3 VERIFY_MARKETING.sql runs with auto-posting added', e.message);
  }
  try {
    const a = last(await db.exec(verifyAutopost))[0];
    const falses = Object.entries(a).filter(([k, val]) => val !== true && !['clock_scheduled', 'pg_net_on'].includes(k));
    check(falses.length === 0 && Object.keys(a).length >= 15, 'E4 VERIFY_AUTOPOST.sql: every column true (but the clock and pg_net)',
      JSON.stringify(a));
  } catch (e) {
    check(false, 'E4 VERIFY_AUTOPOST.sql runs', e.message);
  }
  await db.close();
}

// F. Tasks (docs/mission-control/APPLY_TASKS.sql: auto-posting's migration, then Tasks') on a project with Marketing
{
  const db = await project([]);
  await db.exec(apply);
  await db.exec(applyMarketing);
  try {
    await db.exec(applyTasks);
    await db.exec(applyTasks);
    check(true, 'F1 APPLY_TASKS.sql runs after APPLY_MARKETING.sql, and again (pasted twice)');
  } catch (e) {
    check(false, 'F1 APPLY_TASKS.sql runs after APPLY_MARKETING.sql, and again (pasted twice)', e.message);
  }
  try {
    const k = (await db.query(`select mc.rotate_ingest_secret('tasks') ~ '^[0-9a-f]{64}$' as ok`)).rows[0];
    await db.exec(applyAutopost);
    const again = (await db.query(`select mc.rotate_ingest_secret('tasks') is not null as ok,
      (select count(*) from mc.ingest_secret where purpose = 'tasks') = 1 as kept`)).rows[0];
    check(k.ok && again.ok && again.kept, 'F2 the tasks key survives APPLY_AUTOPOST.sql pasted again after Tasks');
  } catch (e) {
    check(false, 'F2 the tasks key survives APPLY_AUTOPOST.sql pasted again after Tasks', e.message);
  }
  await db.exec(`create schema if not exists cron; create table if not exists cron.job (jobname text);`);
  try {
    const t = last(await db.exec(verifyTasks))[0];
    const falses = Object.entries(t).filter(([, val]) => val !== true);
    check(falses.length === 0 && Object.keys(t).length >= 13, 'F3 VERIFY_TASKS.sql: every column true', JSON.stringify(t));
  } catch (e) {
    check(false, 'F3 VERIFY_TASKS.sql runs', e.message);
  }
  try {
    const v = last(await db.exec(verify))[0];
    const m = last(await db.exec(verifyMarketing))[0];
    const a = last(await db.exec(verifyAutopost))[0];
    const falses = [
      ...Object.entries(v).filter(([k, val]) => val === false && !LATER_STEPS.includes(k)),
      ...Object.entries(m).filter(([, val]) => val !== true),
      ...Object.entries(a).filter(([k, val]) => val !== true && !['clock_scheduled', 'pg_net_on'].includes(k)),
    ];
    check(falses.length === 0, 'F4 VERIFY_MISSION_CONTROL, VERIFY_MARKETING and VERIFY_AUTOPOST stay true with Tasks added', JSON.stringify(falses));
  } catch (e) {
    check(false, 'F4 the earlier VERIFY files run with Tasks added', e.message);
  }
  await db.close();
}

console.log(failed ? `\n${failed} check(s) failed` : '\nall catch-up checks passed');
process.exit(failed ? 1 : 0);
