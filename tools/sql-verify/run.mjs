// Replays the repo's Supabase migrations on PGlite (real Postgres 17 in WebAssembly), then runs a SQL suite.
// usage: node run.mjs [migrations dir] [suite.sql] [skip-file-substring ...]   (no suite: every *_suite.sql)
// Set STOCK_DEFAULTS=1 to simulate Supabase's stock default privileges instead of lucento-test's.
import { readFileSync, readdirSync } from 'node:fs';
import { basename, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { PGlite } from '@electric-sql/pglite';
import { STOCK_DEFAULTS_SQL, SUPABASE_STANDIN } from './standin.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const [
  migrationsDir = resolve(here, '../../supabase/migrations'),
  suiteArg,
  ...skipArgs
] = process.argv.slice(2);
// pg_cron isn't available in PGlite; the purge schedule only registers a cron job.
const skip = skipArgs.length > 0 ? skipArgs : ['usage_events_purge_schedule'];
// No suite named: every *_suite.sql here, each on a fresh database (one suite's rows never leak into another's).
const suites = suiteArg
  ? [suiteArg]
  : readdirSync(here).filter((f) => f.endsWith('_suite.sql')).sort().map((f) => join(here, f));

async function freshDatabase() {
  const db = new PGlite();
  await db.exec(SUPABASE_STANDIN);
  if (process.env.STOCK_DEFAULTS === '1') {
    // Supabase's stock defaults: everything new in public is granted to anon, authenticated and service_role.
    await db.exec(STOCK_DEFAULTS_SQL);
  }
  return db;
}

const files = readdirSync(migrationsDir).filter((f) => f.endsWith('.sql')).sort();
if (process.env.STOCK_DEFAULTS === '1') console.log('stock Supabase default privileges simulated');
let failedSuites = 0;
let totalChecks = 0;
let failedChecks = 0;
for (const suiteFile of suites) {
  const db = await freshDatabase();
  for (const f of files) {
    if (skip.some((s) => f.includes(s))) continue;
    try {
      await db.exec(readFileSync(join(migrationsDir, f), 'utf8'));
    } catch (e) {
      console.log(`FAILED   ${f}: ${e.message}`);
      process.exit(2);
    }
  }
  // The suite reports by raising one exception that lists every check ("VERIFY <failed>/<total> failed").
  const name = basename(suiteFile);
  try {
    await db.exec(readFileSync(suiteFile, 'utf8'));
    console.log(`${name}: finished without raising its result (unexpected)`);
    failedSuites++;
  } catch (e) {
    const m = /VERIFY (\d+)\/(\d+) failed/.exec(e.message);
    if (!m) {
      console.log(`${name}: ERROR ${e.message}`);
      failedSuites++;
    } else {
      failedChecks += Number(m[1]);
      totalChecks += Number(m[2]);
      if (Number(m[1]) > 0) failedSuites++;
      console.log(`== ${name}: ${m[1]}/${m[2]} failed`);
      console.log(e.message.split('\n').slice(1).join('\n'));
    }
  }
  await db.close();
}
console.log(`\n${files.length - files.filter((f) => skip.some((s) => f.includes(s))).length} migrations replayed per suite; ` +
  `${suites.length} suite(s), ${totalChecks} checks, ${failedChecks} failed${failedSuites ? `, ${failedSuites} suite(s) not passing` : ''}`);
process.exit(failedSuites === 0 ? 0 : 1);
