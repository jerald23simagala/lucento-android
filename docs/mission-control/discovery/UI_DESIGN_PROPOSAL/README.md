# UI Design Proposal — interactive prototype

`mission-control-prototype.html` is a single-file, interactive prototype of Mission Control. It's published as a
private claude.ai artifact: https://claude.ai/artifact/SqnYfVT3f9m79ordzzAuTx (only the owner can open it until it's
shared from the page's Share menu).

**All names, emails, numbers and events are fictional** (a sample day, Thursday 18 March 2027, about three months
after a hypothetical launch). Nothing connects to any service, and nothing is sent.

The file is written for the artifact host, which adds the `<!doctype html>` wrapper at publish time. To open it
locally, wrap it in a minimal HTML document or view the published link.

## Screens

| Screen | Route | Try this |
|---|---|---|
| Today | `#today` | Click a citation chip (m1…m9) in the briefing to see the metric, period and query behind it |
| Customers | `#customers` | Search "pro" or filter by plan; open a row |
| Customer profile | `#customer` | **Grant complimentary Pro**: switch durations, untick capabilities, pick a reason; > 90 days asks for step-up (any 6 digits). Then **Revoke** it. Try Suspend and Schedule deletion (typed confirmation) |
| Support | `#support` | LC-1047: use the AI draft, then **Resolve purchase link**. LC-1044: **Investigate records** |
| Investigation | `#investigate` | Reveal a masked note (logged); switch to Assist (step-up); **Repair transfer pair**; watch the banner's timer; End session |
| Revenue | `#revenue` | MRR and Lifetime sales stay separate; EST vs REPORTED; the complimentary tracker shows foregone value, not revenue |
| Messages | `#messages` | Desktop/mobile preview; Schedule stays disabled until a test is sent; typed "SEND 1,206" |
| Operations | `#operations` | Unknown is never green; quotas; crashes from two sources with their populations |
| Product | `#product` | Funnel labelled as a sample; adoption as a census of synced accounts |
| Intelligence | `#intelligence` | Switch between the three example questions; every number is cited |
| Audit log | `#audit` | Filter Refused; open an entry for before/after; your actions in the prototype appear here live |
| Team & access | `#team` | Role matrix; approve Mika's request; secrets show names and rotation dates only |

Keyboard: Ctrl/⌘ K opens the command bar; Esc closes dialogs. The moon button switches light and dark.

## Design direction

Lucento's palette roots (Midnight Navy, Electric Blue, Sunlight Yellow) and Plus Jakarta Sans, set in an
instrument-panel register: navy-tinted neutrals, blue as the only interactive accent, yellow reserved for "careful"
(the production marker and the investigation banner), JetBrains Mono for ids, hashes and queries. Tables over cards;
status by shape and word, not colour alone.
