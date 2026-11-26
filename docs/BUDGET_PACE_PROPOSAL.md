# Budget pace with bills on their due dates (26 Nov 2026)

**Owner's choices (26 Nov):**
1. Pace bills by their due dates, and show the before and after first. This page is that before and after.
2. After the owner asked "what if the payment was delayed from last month": count every bill **on its due date, paid or
   not**. A bill already due and still owed counts as spent for pace, whichever month it belongs to.
3. Only the chip's word changes. The bar and its "days elapsed" tick stay as they are.

**Built:**
- `domain/BudgetPace.kt`, read by `BudgetPlanner.burn` (Home, the Budget page, widgets, the wellness tier);
- the definition in docs/CALCULATIONS.md "Budget pace";
- `BudgetPaceTest` (15 tests, all pass), including the late-payment cases below.

The "What changes on screen" and "Open question" sections below are superseded by choice 3: there's no new label.

## The problem

The pace chip ("Ahead of pace" / "On track" / "Under budget") compares the share of the budget spent with the share of
the month gone (docs/CALCULATIONS.md, "Budget pace"). That's a straight line. Rent is often the biggest line in a budget
and is paid near the start of the month, so a renter who is exactly on plan sees the amber "Ahead of pace" for most of
the month. That's likely in a new user's first week.

## The rule

By the end of a day, the plan expects:
- every scheduled bill due so far (paid or not);
- plus the rest of the budget spread evenly over the month.

Spending more than 10 points above that expectation is "Ahead of pace"; more than 10 below is "Under budget". The
10-point margin is today's.

- **No bills scheduled:** the rule is today's straight line, to the percent.
- **Bills above the budget:** they're capped at it, so the expectation never passes 100 %.
- **What's ignored:** bills in another month or currency, and transfers to debts (they aren't spending).
- **Computation:** whole minor units, rounded down once.

## Before and after

**Profile:** a $2,590 budget (the demo month's nine categories), with $1,500 rent scheduled on the 2nd. Each case is a
test in `BudgetPaceTest`.

**A renter exactly on plan** (rent on the 2nd, the other $1,090 spent evenly):

| Day | Spent | Today's rule | Proposed |
|---|---|---|---|
| 2 | 61 % | **Ahead of pace** (the line is at 6 %) | On track (the plan is at 60 %) |
| 8 | 69 % | **Ahead of pace** (26 %) | On track (69 %) |
| 15 | 79 % | **Ahead of pace** (50 %) | On track (78 %) |
| 22 | 89 % | **Ahead of pace** (73 %) | On track (88 %) |
| 29 | 99 % | On track (96 %) | On track (98 %) |

**The same renter overspending** (everything else at twice the planned speed). The proposed rule still warns:

| Day | Spent | Proposed |
|---|---|---|
| 8 | 80 % | **Ahead of pace** (the plan is at 69 %) |
| 12 | 92 % | **Ahead of pace** (74 %) |

**Late and unpaid payments** (choice 2), on day 8:

| Case | Used for pace | Proposed |
|---|---|---|
| This month's rent due on the 2nd, not paid yet | 69 % (it counts as spent) | On track (not "Under budget") |
| Last month's rent paid late on the 5th; this month's not paid yet | 127 % | **Ahead of pace** |
| Last month's rent paid late, and this month's paid too | 127 % (the card also says Over budget) | **Ahead of pace** |
| Last month's rent and this month's both still unpaid | 127 % | **Ahead of pace** |

**Other cases:**
- **Rent typed by hand, with no schedule:** the plan can't know about it, so the chip reads as today ("Ahead of pace" on
  day 8). The rule only follows bills it knows about.
- **A skipped bill:** skips aren't recorded apart from payments, so it still counts as planned for its day. That errs
  toward "On track", never toward a false warning.

## What changes on screen, once you say yes

1. **The chip:** the pace chip on Home and on the Budget page uses the proposed rule.
2. **The bar's marker:** the marker on the Budget page's bar moves from "26 % days elapsed" to the plan's figure. Its
   label needs new words (the question below).
3. **The definition:** docs/CALCULATIONS.md "Budget pace" is rewritten to this rule.
4. **The bills:** both screens take the month's bills from the same schedules Home already reads
   (`ScheduleOutlook.occurrences` for the month, expense schedules only).
5. **Unchanged:** the daily allowance, the projection, Safe to spend, budget health and the budget amounts themselves.

## Open question for the owner

The marker's words. Without bills, the plan's figure equals the days gone, so one label fits both cases. Options:
- "planned by today";
- "expected by today";
- keep "days elapsed" when there are no bills, and say "planned by today" only when bills move it.
