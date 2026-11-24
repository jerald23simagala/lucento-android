# Mission Control V1 — lean founder console

Status: **Phase 4 built (2026-11-15, owner: "Lets start building now"). Phase 5 (production) is the owner's
steps.** Nothing is applied to any Supabase project yet. The earlier full-vision discovery is parked in
[../discovery/](../discovery/README.md) for reference.

**Phase 4 — Implementation**
- Database: `supabase/migrations/20261116090000…090300_mission_control_*.sql`; one paste for the SQL editor:
  [../APPLY_MISSION_CONTROL.sql](../APPLY_MISSION_CONTROL.sql), checks after: [../VERIFY_MISSION_CONTROL.sql](../VERIFY_MISSION_CONTROL.sql).
  Tests: `tools/sql-verify/mission_control_suite.sql` (90 checks; 362 across all suites).
- Console: the private repository `lucento-mission-control` (Next.js), with `DEPLOYMENT.md` (click-by-click owner steps).
- What changed from the design while building: [FOUNDER_DECISIONS.md](FOUNDER_DECISIONS.md) "Implementation notes".

**Phase 1 — Discovery**
- [EXISTING_ARCHITECTURE_AUDIT.md](EXISTING_ARCHITECTURE_AUDIT.md): what exists, verified
- [DATA_SOURCE_INVENTORY.md](DATA_SOURCE_INVENTORY.md): the source of truth for every number
- [INTEGRATION_REQUIREMENTS.md](INTEGRATION_REQUIREMENTS.md): credentials, permissions, owner setup
- [SECURITY_CONSIDERATIONS.md](SECURITY_CONSIDERATIONS.md): how access is controlled and audited

**Phase 2 — Design**
- [PRODUCT_SCOPE.md](PRODUCT_SCOPE.md): exactly what V1 does and doesn't do
- [NAVIGATION_PROPOSAL.md](NAVIGATION_PROPOSAL.md): eight pages, journeys, visual system
- [DATABASE_PROPOSAL.md](DATABASE_PROPOSAL.md): schemas, tables, functions, jobs
- [IMPLEMENTATION_ROADMAP.md](IMPLEMENTATION_ROADMAP.md): build order and acceptance
- [WHAT_WE_MISSED.md](WHAT_WE_MISSED.md): gaps, risks, future opportunities
- [prototype/mission-control-v1.html](prototype/mission-control-v1.html): interactive prototype (fictional data),
  published privately at https://claude.ai/artifact/XorRNPD337qP5D1DWdATz3

**Phase 3 — Review**
- [FOUNDER_DECISIONS.md](FOUNDER_DECISIONS.md): the founder's answers and what each changes
