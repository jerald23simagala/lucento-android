// Replays the repo's Supabase migrations on PGlite (real Postgres 17 in WebAssembly), then runs a SQL suite.
// usage: node run.mjs [migrations dir] [suite.sql] [skip-file-substring ...]
// Set STOCK_DEFAULTS=1 to simulate Supabase's stock default privileges instead of lucento-test's.
import { readFileSync, readdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { PGlite } from '@electric-sql/pglite';

const here = dirname(fileURLToPath(import.meta.url));
const [
  migrationsDir = resolve(here, '../../supabase/migrations'),
  suiteFile = resolve(here, 'shared_space_suite.sql'),
  ...skipArgs
] = process.argv.slice(2);
// pg_cron isn't available in PGlite; the purge schedule only registers a cron job.
const skip = skipArgs.length > 0 ? skipArgs : ['usage_events_purge_schedule'];
const db = new PGlite();

// What Supabase provides and these migrations rely on. Default privileges mirror lucento-test's for objects that
// postgres creates in public (queried 2026-11-05): functions are executable by their owner only.
await db.exec(`
  create role anon nologin noinherit;
  create role authenticated nologin noinherit;
  create role service_role nologin noinherit bypassrls;
  grant usage on schema public to anon, authenticated, service_role;
  create schema auth;
  grant usage on schema auth to anon, authenticated, service_role;
  create table auth.users (
    instance_id uuid, id uuid primary key, aud varchar(255), role varchar(255), email varchar(255),
    encrypted_password varchar(255), email_confirmed_at timestamptz, created_at timestamptz default now(),
    updated_at timestamptz default now(), raw_app_meta_data jsonb, raw_user_meta_data jsonb,
    is_sso_user boolean not null default false, is_anonymous boolean not null default false
  );
  create function auth.uid() returns uuid language sql stable as $$
    select coalesce(nullif(current_setting('request.jwt.claim.sub', true), ''),
                    (nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub'))::uuid $$;
  create function auth.role() returns text language sql stable as $$
    select coalesce(nullif(current_setting('request.jwt.claim.role', true), ''),
                    (nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'role'))::text $$;
  create function auth.jwt() returns jsonb language sql stable as $$
    select coalesce(nullif(current_setting('request.jwt.claim', true), ''),
                    nullif(current_setting('request.jwt.claims', true), ''))::jsonb $$;
  grant execute on all functions in schema auth to anon, authenticated, service_role;
  alter default privileges in schema public revoke execute on functions from public;
`);
if (process.env.STOCK_DEFAULTS === '1') {
  // Supabase's stock defaults: everything new in public is granted to anon, authenticated and service_role.
  await db.exec(`
    alter default privileges in schema public grant all on tables to anon, authenticated, service_role;
    alter default privileges in schema public grant all on functions to anon, authenticated, service_role;
  `);
  console.log('stock Supabase default privileges simulated');
}

const files = readdirSync(migrationsDir).filter((f) => f.endsWith('.sql')).sort();
for (const f of files) {
  if (skip.some((s) => f.includes(s))) {
    console.log(`skip     ${f}`);
    continue;
  }
  try {
    await db.exec(readFileSync(join(migrationsDir, f), 'utf8'));
    console.log(`applied  ${f}`);
  } catch (e) {
    console.log(`FAILED   ${f}: ${e.message}`);
    process.exit(2);
  }
}

// The suite reports by raising one exception that lists every check ("VERIFY <failed>/<total> failed").
try {
  await db.exec(readFileSync(suiteFile, 'utf8'));
  console.log('suite finished without raising its result (unexpected)');
  process.exit(3);
} catch (e) {
  console.log(e.message);
  process.exit(/ 0\/\d+ failed/.test(e.message) ? 0 : 1);
}
