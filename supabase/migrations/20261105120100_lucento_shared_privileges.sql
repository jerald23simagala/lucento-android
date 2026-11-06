-- Lucento Shared, least privilege for what 20261105120000_lucento_shared.sql created, whatever a project's default
-- privileges are (lucento-test gives new public functions to their owner only, but a project with Supabase's stock
-- defaults would also let anon execute them and hold TRUNCATE on new tables, which row-level security doesn't cover).
-- Same intent as 20261028050634_revoke_extra_table_privileges.sql. Nothing a signed-in user needs is removed:
-- SELECT on the three tables (scoped by RLS) and EXECUTE on the RPCs and on the two helpers the policies call.

revoke all on table public.shared_spaces, public.space_members, public.space_invites from anon, authenticated;
grant select on table public.shared_spaces, public.space_members, public.space_invites to authenticated;

revoke all on schema lucento_private from public, anon;
grant usage on schema lucento_private to authenticated;
revoke all on function
  lucento_private.max_partners(uuid),
  lucento_private.space_lock(uuid),
  lucento_private.partner_spaces(),
  lucento_private.verified_email(),
  lucento_private.check_space_roles()
from public, anon, authenticated;
grant execute on function lucento_private.partner_spaces(), lucento_private.verified_email() to authenticated;

-- Triggers fire without an EXECUTE check, so nobody needs to call this one directly.
revoke all on function public.lucento_stamp_transaction_actor() from public, anon, authenticated;

revoke all on function
  public.get_my_shared_space(),
  public.create_shared_space(text),
  public.set_my_space_label(text),
  public.create_space_invite(text),
  public.revoke_space_invite(uuid),
  public.accept_space_invite(uuid, text),
  public.decline_space_invite(uuid),
  public.remove_space_partner(uuid),
  public.leave_shared_space(),
  public.close_shared_space()
from public, anon;
grant execute on function
  public.get_my_shared_space(),
  public.create_shared_space(text),
  public.set_my_space_label(text),
  public.create_space_invite(text),
  public.revoke_space_invite(uuid),
  public.accept_space_invite(uuid, text),
  public.decline_space_invite(uuid),
  public.remove_space_partner(uuid),
  public.leave_shared_space(),
  public.close_shared_space()
to authenticated;
