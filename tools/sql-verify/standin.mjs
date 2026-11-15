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
    is_sso_user boolean not null default false, is_anonymous boolean not null default false,
    last_sign_in_at timestamptz, banned_until timestamptz, deleted_at timestamptz, phone text
  );
  -- The rest of Auth's tables that Mission Control reads (types as in production, queried 2026-11-15; Auth's own foreign
  -- keys cascade from users and sessions the same way).
  create type auth.aal_level as enum ('aal1', 'aal2', 'aal3');
  create type auth.factor_type as enum ('totp', 'webauthn', 'phone');
  create type auth.factor_status as enum ('unverified', 'verified');
  create table auth.identities (
    provider_id text not null, user_id uuid not null references auth.users (id) on delete cascade,
    identity_data jsonb not null default '{}', provider text not null, last_sign_in_at timestamptz,
    created_at timestamptz default now(), updated_at timestamptz default now(), email text,
    id uuid primary key default gen_random_uuid()
  );
  create table auth.mfa_factors (
    id uuid primary key default gen_random_uuid(), user_id uuid not null references auth.users (id) on delete cascade,
    friendly_name text, factor_type auth.factor_type not null default 'totp', status auth.factor_status not null default 'verified',
    created_at timestamptz not null default now(), updated_at timestamptz not null default now(), secret text
  );
  create table auth.sessions (
    id uuid primary key default gen_random_uuid(), user_id uuid not null references auth.users (id) on delete cascade,
    created_at timestamptz default now(), updated_at timestamptz default now(),
    factor_id uuid, aal auth.aal_level, not_after timestamptz,
    refreshed_at timestamp without time zone, user_agent text, ip inet
  );
  create table auth.mfa_amr_claims (
    session_id uuid not null references auth.sessions (id) on delete cascade,
    created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
    authentication_method text not null, id uuid primary key default gen_random_uuid(),
    unique (session_id, authentication_method)
  );
  create table auth.refresh_tokens (
    instance_id uuid, id bigserial primary key, token varchar(255), user_id varchar(255), revoked boolean,
    created_at timestamptz default now(), updated_at timestamptz default now(), parent varchar(255),
    session_id uuid references auth.sessions (id) on delete cascade
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
