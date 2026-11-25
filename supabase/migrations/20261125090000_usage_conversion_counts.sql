-- Conversion counts (docs/growth/CRO_PLAN.md, CRO-1; owner 2026-11-25): four more anonymous usage counts on the way to
-- Lucento Pro, under the same opt-in as the eight before them (off until the person turns them on, kept 90 days, a random
-- install id, never an amount or typed text):
--   pro_dialog_shown    props {feature}  the Pro dialog appeared (once per feature per app session)
--   plan_page_viewed    props {from, feature}  the Lucento Plan page opened, and from where
--   purchase_started    props {plan}  Google Play's purchase sheet was asked to open
--   purchase_completed  props {plan}  Play confirmed that purchase in the same session
-- Props are fixed short values set by the app (UsageCounts.PROP_KEYS); the 512-byte props limit still applies.
--
-- Apply this before any app build that sends the new names: until then the table refuses them (23514).

alter table public.usage_events drop constraint usage_events_event_allowlist;

alter table public.usage_events add constraint usage_events_event_allowlist check (
  event in (
    'onboarding_started',
    'onboarding_step_viewed',
    'first_entry_saved',
    'd1_return',
    'd7_return',
    'first_week_card_step_done',
    'budget_suggestion_shown',
    'budget_suggestion_accepted',
    'pro_dialog_shown',
    'plan_page_viewed',
    'purchase_started',
    'purchase_completed'
  )
);
