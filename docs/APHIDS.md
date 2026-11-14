# Aphid husbandry and relocation

## Colony and housing

Aphid Chamber levels each add 10 **aphid places**, not 10 worker jobs. One tender
can care for a group of 10 housed aphids. Empty housing provides no tender capacity.
The first chamber receives a free starter group of 10 after Aphid Tending and the
Wild Aphids discovery, preserving the existing entry point. Aphids persist: no
replacement timer, mortality, reproduction or additional upkeep system is added.

A fully populated level retains its previous economics: one worker produces
0.8 sugar/s and consumes 0.01 protein/s before inherited sugar bonuses, in addition
to normal ant upkeep. Empty protein pauses production as before. The only new
expansion requirement is obtaining aphids for additional housing.

## Relocation

Wild Aphids at Woodland Edge unlocks Relocate Aphids after Aphid Tending. Each
safe mission brings home 10 aphids and requires:

- 5 standard workers from the available/default worker pool;
- 1 available scout;
- 10 sugar upfront;
- 10 free aphid places, reserved on departure;
- 1 ordinary expedition slot for 30 simulated seconds.

The mission is deliberately short and cheap; these values live in AphidRelocation.
It does not use the map's collection-crew or scout-speed selectors. Costs, crew,
housing and duration are visible beside its action. Incoming groups appear in the
housing counter; completion returns the party through the normal age handling.

No Knowledge, protein, insects or other haul accompanies relocation. Studying
Woodland Edge and collecting its finite insect deposit retain their own budgets.
Wild aphids remain a managed source once that deposit and its Knowledge are spent;
the hex then says Aphids instead of Site exhausted. This does not regenerate any
finite resource. Housing is the expansion limit; later source ecology is not
implemented in this step. Shared Paths does not automate relocation.

## Saves and founding

Nest stores a whole-animal count independently of fixed-point worker counts.
New colonies start with zero aphids and receive their first group after building.
Old version-7 saves without the aphidsInitialized marker populate their existing
chambers once, preserving previous tender capacity. Thereafter saves retain stock
and empty housing, including incoming reservations. Founding resets the herd,
housing and missions with the other current-colony state. Save version stays 7.

## Verification

AphidChamberSuite retains the former fed/partial-feed production checks and now
checks empty housing. AphidRelocationSuite covers the starter group, exact costs,
reserved capacity, concurrent missions, reload/replay, gates, default-task staffing,
legacy migration and access after deposit exhaustion. Map browser smoke tests
cover visible requirements and a saved in-flight relocation at phone/desktop sizes.
The real-engine balance policies relocate groups to fill their configured tender
target; the existing policy `aphids` number still means worker tenders.

### Initial pacing check — 2026-11-14

Compared clean main `99ee3f2` against this implementation with the real-engine
balanced policy, seeds 42/137, and a three-hour simulation budget per run. The
updated policy has the same tender target (three) and explicitly relocates its
extra two groups; no resources were injected. The first chamber gets its starter
group automatically in both new runs.

| Seed | Daughter queen ready before | After | Change |
| --- | ---: | ---: | ---: |
| 42 | 102.28 min | 105.46 min | +3.11% |
| 137 | 104.19 min | 103.08 min | −1.06% |

Both baseline and new runs completed one founding within the budget; the new
runs had zero starvation deaths. Earlier opening milestones through first hex
reveal were identical. Staffing/travel timing changes later choices, so a short
trip can shift readiness in either direction. This supports a small impact for
these two policy/seed combinations, not a guarantee for all playstyles or proof
of enjoyment. Wider policies and human playtests remain useful before retuning.
Raw temporary reports: /tmp/antidle-aphids-before and /tmp/antidle-aphids-after;
they are not durable repository artifacts. Reproduce with scripts/balance.py,
--policy balanced --seed 42 --seed 137 --hours 3, rebuilding at each revision.
