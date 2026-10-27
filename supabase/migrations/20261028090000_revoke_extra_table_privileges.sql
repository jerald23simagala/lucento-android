-- Least privilege for signed-in users. The live project's default privileges also gave `authenticated` TRUNCATE,
-- TRIGGER and REFERENCES on the sync tables, beyond the select/insert/update/delete that migrations 4 and 6 intend.
-- TRUNCATE is not subject to row-level security, so it must never be held by an end-user role (PostgREST does not
-- expose it today, but that is not the protection we rely on). No row, policy or CRUD grant changes here.
revoke truncate, trigger, references on table
    public.wallets,
    public.categories,
    public.budgets,
    public.transactions,
    public.goals,
    public.goal_contributions,
    public.recurring
from authenticated;

-- Tables created later in this schema start without these privileges for signed-in users.
alter default privileges in schema public revoke truncate, trigger, references on tables from authenticated;
