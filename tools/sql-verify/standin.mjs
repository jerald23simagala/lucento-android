// What Supabase provides and the migrations rely on (the anon, authenticated and service_role roles, auth.users,
// auth.uid()), shared by run.mjs (PGlite) and concurrency.mjs (a real Postgres server). Default privileges mirror
// lucento-test's for objects that postgres creates in public (queried 2026-11-05): functions are executable by their
// owner only.
export const SUPABASE_STANDIN = `
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
`;

// Supabase's stock defaults: everything new in public is granted to anon, authenticated and service_role.
export const STOCK_DEFAULTS_SQL = `
  alter default privileges in schema public grant all on tables to anon, authenticated, service_role;
  alter default privileges in schema public grant all on functions to anon, authenticated, service_role;
`;
