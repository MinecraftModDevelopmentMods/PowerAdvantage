# What The Tests Actually Prove

The follow-up [operating checks](OPERATING-TESTS.md) add finite jobs, negative
controls and complete-process restart comparisons. The findings and coverage
table below describe the evidence available before that work; they are retained
so the earlier gaps and incorrect acceptance are not lost. Current results and
exact candidates belong in [the test record](CURRENT-QUALIFICATION.md).

29 October 2026. This review follows the missing GUI captions that passed an
incorrect test. A machine test must describe a useful job, observe its result,
and reject a broken version of that behaviour. Being registered, retaining NBT,
animating, or showing a nonzero buffer is not enough.

**The suite does not yet establish correct operation of every machine in every
advertised setup. That broader acceptance remains open.** This is an assertion
review and read-only replay, not a new Minecraft campaign against the GUI jars.
No gameplay code changed in this review.

## Findings And Repairs

| Finding | Risk | This Review |
| --- | --- | --- |
| Empty summaries, unknown statuses and PASS with no assertions were accepted | Missing tests could look successful | Runner now checks summary identities/counts and individual assertions |
| Restart could reuse the first boot's latest result | A missing restart result could borrow an earlier pass | Archive earlier latest files before each check; validate the first boot before restarting |
| Evaluator exceptions could become EXPECTED_FAIL | A broken test looked like evidence of a known gameplay bug | Exceptions are FAIL; known failures are not unqualified operating passes |
| Missing tank NBT could satisfy a zero-volume assertion | Missing evidence looked like an empty tank | Require typed data or the explicit true empty marker; snapshot every fluid assertion position |
| Infinite numbers and registry fallbacks could satisfy assertions | Invalid measurements looked plausible | Require finite numbers, actual registry membership and valid assertion definitions |
| Connected accounting ignored extra items, negative water and invalid battery charge | Correct expected counts could coexist with corruption | Independent checks now validate both interval endpoints and these bounds |
| Any changed drill target counted as mined; growing progress could count as work | A replacement block or spinning drill could pass without delivering drops | Require air or the legitimate Steam drill-bit helper and exact products; counter-only evidence is INCOMPLETE |
| Endurance replay reused the batch oracle | Replay could repeat a mistaken expectation | Independent endpoint checks cover machine identity, quantities, routes, filter references and every oil tank |

The old connected/endurance oracles and historical fixtures remain unchanged.
The stricter overlay is in
[`semantic_machine_audit.py`](../../test-rig/migration/semantic_machine_audit.py).
New native connected runs include it in their verdict. New native endurance runs
must pass it before reaching the historical ledger. Failed measurements are
retained, not retried until green.

Regression tests demonstrate five malformed result forms accepted by the old
runner. Synthetic machine evidence demonstrates acceptance of the listed bad
states by the old oracles, then rejection by the independent checks. These are
tests of the test system, not proof those corrupt states occurred in Minecraft.
The first numeric-test attempt could not load a compile-only Power class. That
infrastructure failure is retained, not counted as a numeric negative control;
the finite-number tests now exercise the small assertion helper directly.

## Rechecking Retained Evidence

The replay reads raw snapshots from the frozen K artifacts in
[the test record](CURRENT-QUALIFICATION.md), not the later GUI jars. Each input
is hashed in the new report. No save, old report or original fixture is changed.

- **20 of 30 connected intervals pass** the stronger completed-work checks.
- **10 are INCOMPLETE:** the electric drill produces no new block/drop in that
  individual 512-tick window. The historical test accepted increased progress.
  A nine-block cycle can span a window; this is missing completed-work proof,
  not a demonstrated drill failure.
- **All 66 batch endpoints pass** the independent five-processor, conveyor,
  three-filter-lane and 1,000 mB refined-oil checks, including restart endpoints.
  Filter reference items are checked separately from transported stock.

The existing feed-command and custody checks supply the input/setup evidence.
The independent replay alone does not prove elapsed time, exact efficiency, or
behaviour without power, with invalid input, or with a full output. The 30-minute
run is repeated, re-fed batches, not every machine operating unattended.

Changing the current harness does not change the harness or hashes used by old
runs. No new endurance, upgrade, unloading or client run is claimed here.

The updated harness passes 170 Java tests, `check assemble`, and the production
jar-isolation audit. All 1,103 offline Python tests pass with child processes
forbidden and source hashes unchanged. Those counts include the new test-system
regressions, not additional Minecraft operating scenarios.

## Machine Coverage Ledger

This describes inspected retained assertions, not fresh runtime qualification of
the GUI jars. Open operating checks need new automated fixtures unless they
specifically concern player input or visual rendering. They are not all tasks
that must be handed to a human tester.

| Bay | Useful Evidence | Open Operating Checks |
| --- | --- | --- |
| W-01 fluids, tanks, converters | Bounded oil/water drain-pipe-tank runs, capability transfers, closed tank persistence and converter interfaces | Filter/switch/terminal faces, isolation under load, complete energy budgets |
| W-02 drain/discharge | Drain exercised by oil lanes | Discharge basin volume, source-versus-flowing search, bounded search failure, full destination |
| W-03 Power still | Bounded 2,000 mB crude to 1,000 mB refined across the observed circuit | Invalid input, full output, fuel starvation, mid-cycle interruption, all aliases/modes in operation |
| W-04 conveyors/filters | Horizontal transfer, zero/one/several inventory-filter routing and reference-slot retention | Category filters, overflow permutations, vertical/entity pickup lanes, comparator levels, blocked-route conservation |
| S-01 boilers | Finite coal boilers sustain connected fixtures; heat/water/fuel saved-state checks | All four live rates, invalid fuels, scarcity, output isolation, oil equivalence, electric debit |
| S-02 steam network | Real connected processing and unload recovery | All middle-machine GUI/server readings, scarcity/switch response, topology variants |
| S-03 steam storage | Saved reserve and declared machine presence | Priority, useful discharge after supply loss, exact charge/discharge, redstone isolation |
| S-04 processing | Exact furnace/crusher batch products; comparator and persistence checks elsewhere | Three furnace lanes together, Steam still, wrong input, full outputs, automation faces |
| S-05 Steam drill | Dedicated six-facing movement/mining, energy debit, real unload and restart fixtures | All blocked/starved cases, sustained load in every orientation/track layout |
| S-06 Steam pump | Isolated 1,000 mB restart witness and legacy-key reads | Actual intake/shaft/lift, steam cost, full output, restart while pumping |
| S-07 elevator | Helper presence and saved-state coverage | Platform travel, stopping geometry, energy use, passengers, interrupted recovery |
| S-08 musket | Items, enchantments and saved state | Loading, cartridge/durability debit, real damage/range, each enchantment effect |
| E-01 generators | Hydro activity/battery charging; finite steam chain powers processing | Solar environment matrix/rate, loaded hydro rate, complete steam-to-electric budget |
| E-02 batteries | Stored NBT/capacity bounds and a working battery path | Every chemistry under load, backup priority, redstone, exact energy accounting |
| E-03 processing/fabricator | Exact electric furnace/crusher/oven batch products | Live fabrication, invalid/full outputs, redstone, scarcity, rates, automation faces |
| E-04 pump/still/refinery | Tile/saved-state witnesses | All three doing useful work with finite inputs/products/power, restart and blocked outputs |
| E-05 growth | Growth/progress serialization and controller/chamber presence | Crop output, soil/water/electricity debit, branching, starvation, full outputs |
| E-06 Electric drill | Dedicated six-facing movement/mining and unload/restart fixtures | Ten migration windows lack completed-work proof; blocked outputs, starvation, broader sustained load |
| E-07 LED/turret | Registration, geometry and ownership/state coverage | Actual light, real target hits/friendly exclusion, energy debit, reacquisition after restart |
| E-08 recursive fabricator | Isolated diagnostic layout and manufacture reachability | Live bounded recursion, real ingredient/output accounting, nonstalling failure; no broad recipe-graph acceptance |
| P-API-01 fluid API | Exact bounded real-pipe/storage acceptance and separate API edge cases | More consumer/topology combinations and competing offers |

## Other Test Families

- **Source/resource contracts:** useful ID, tag, recipe, renderer and packaging
  safeguards. Searching for a call or checking a constant does not execute it.
- **NBT unit tests:** valuable persistence regressions. A load/save round trip
  before world attachment does not prove attached-world ticking or useful work.
- **Frozen migration comparison:** checks source fields and known representation
  changes. Its dictionary comparison is directional: unexpected target-only
  keys are not universally rejected. It is not whole-NBT equality. The connected
  overlay checks resources in its fixed fixture, not every recursive capability.
- **Lifecycle/partial unloading:** use real events, fresh tile objects, moved
  position/inventory/energy and resumed processing. Much stronger than presence,
  but not every original oracle has an independent operational cross-check yet.
- **Native mining/loot:** actual player destruction, item entities, tool levels,
  counts and isolation. This is stronger than merely declaring a loot table,
  but does not cover every way another mod can break a block.
- **Recipes/advancements:** native matching/discovery and manufacture reachability
  are separate gates. Process reachability is not actual growth, refinery or
  fabricator operation. Missing ingredient providers remain explicit skips.
- **World generation:** profile/import and biome/dimension/count samples retain
  the documented legacy-oil exclusion. Samples do not qualify every world/seed.
- **Actors/players/transformer order:** retain their narrow field, relationship,
  hook-order and restart checks. The 48 raw native-wolf-health failures remain
  failures, not newly relabelled passes.
- **GUIs:** rendered required-text controls now check captions independently.
  They do not prove every live gauge or client/server synchronization path.

## Next Operating Tests

1. Give each migrated connected drill a fixed, sufficiently long observation
   that requires a newly mined target and correct drops. Keep the incomplete
   windows; do not select whichever snapshot first passes.
2. Add finite-input operating fixtures for pumps, all boilers, Steam/Electric
   stills, refinery, fabricator and growth chambers. Capture resources before
   and after, with no-power, no-water, invalid-input and full-output controls.
3. Test storage priorities, switches, overflow and generator rates with supply,
   buffer and consumer measurements. A full battery is not a rate measurement.
4. Add automated lift travel, turret hits/ownership, LED response and musket
   consumption, retaining visual/player-control checks separately.
5. Audit target-only migration resource additions and extend independent
   mutation controls for lifecycle and the other state comparators.
6. Run strengthened fixtures on the final three GUI candidates with new sealed
   manifests. Old artifacts' passes must not be relabelled as that run.

## Repeat The Review

From the repository root, using Python with the pinned
`test-rig/migration/requirements.txt` dependencies:

```text
python test-rig/migration/semantic_machine_audit.py --connected-root <K-matrix>/connected --output <new-report.json>
python test-rig/migration/semantic_machine_audit.py --endurance-root <K-endurance> --output <new-report.json>
```

Reports refuse to overwrite evidence. INCOMPLETE has a nonzero exit status.
Harness Gradle `check assemble` uses the frozen Power API input override; offline
Python self-tests forbid child processes. These are test-system checks, not live
world runs. Local evidence is retained outside the repository under
`D:\PowerAdvantage\_verification\test-audit-20261029`.
