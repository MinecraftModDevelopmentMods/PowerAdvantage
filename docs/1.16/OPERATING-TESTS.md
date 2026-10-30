# Machine Operating Checks

The earlier coverage review found tests that proved registration, saved state
or increasing progress, without proving useful work. These checks add finite
jobs and negative controls to the native 1.16.5 server. They are separate from
manual rendering and gameplay acceptance.

## What Is Measured

The operating catalog covers conveyors in six directions, dropped items,
custom item data, locked containers, category and inventory filters, overflow,
drains, tanks, fluid switches, discharge and fuel-fired distillation.

Steam cases cover all four boilers, processing, storage priority, distillation,
pump intake and lifting, elevator travel and actual musket hits and ammunition.
Electric cases cover processing, recursive fabrication, oil processing,
growth, lighting, generation rates, battery chemistries, priorities and turret
hits. Invalid inputs, scarce supplies, full outputs, redstone and obstructed
shots have their own controls where applicable.

Construction happens while machines are held. Measurement starts only after
construction finishes. Machines then run on ordinary world ticks, without
calling their tick methods or replenishing their supplies. Assertions inspect
real output, retained inputs, fluid identities, energy and actual entity damage.
Rate checks account for each position's eight-tick network phase.

There are 42 Power cases, 52 Steam cases, 29 Electric/battery cases and three
waterlogged pickup cases. The drill lifecycle, connected migration and
stop-signal reload protocols are separate; their counts are not added to the
126-case operating catalog.

The process saves and stops, then a new process reads the same world without
construction. Block states, typed tile NBT and test actors are compared in both
directions, including unexpected additional contents. Empty named tanks may
change from an exact zero-volume fluid compound to the empty marker written by
the existing tank loader. Nonzero quantities, extra fields and different numeric
types are not covered by that allowance.

## Failed Controls Matter

The new tests reproduced lost or duplicated resources, stalled overflow routing,
custom-data merging, missing turret hits, incorrect solar sampling, the last
musket cartridge being unusable and sound broadcasts loading distant chunks.
Their failed reports and exact input jars are retained locally. A later pass
does not erase those failures.

Native pickup controls require an actual waterlogged slab to remain a slab,
lose its water and deliver exactly one bucket to the machine/receiver circuit.
They reject replacing the host with air even when the tank contains the right
amount. This reproduced a destructive pickup bug in all three mods.

The stop-signal reload test seeds real, finite-energy mining before saving,
then records every tick in a new process. Eight held drills cover the update
phases; an unheld control must continue earning progress and spending energy.
A brief active flag, progress advance or debit on a held drill is a failure,
even if a later snapshot looks correct.

Some failures were test setup errors: an uncontained dropped item could move
away, diamond has a legitimate crushing recipe, recursively merged tank NBT
retained an empty marker, and oil boilers have a historical 1.25 steam yield.
Corrected fixtures retain exact resource assertions and explicit prerequisites.

## Repeat A Run

Use Python with `test-rig/migration/requirements.txt` installed. Build the three
mods and the separate harness first, then archive a new immutable manifest:

```text
python test-rig/migration/native116_machine_operations.py pin --base <sealed-native116-manifest> --output <new-local-archive> --label <candidate>
python test-rig/migration/native116_machine_operations.py run --manifest <new-local-archive>/inputs.json --output test-rig/build/<new-run> --java8 <java8>/bin/java.exe --port <leased-port> --group all
```

`power`, `steam` and `electric` may be run separately. A missing case, empty
result, failed assertion, short observation, missing cold restart, changed jar
or changed protocol cannot produce a passing result. Reports and source worlds
are never overwritten. Large catalogs are prepared and inspected in paced
stages; the normal server watchdog remains enabled.

Use `pickup` for the three waterlogged host checks. The separate
`native116_redstone_reload.py` runner archives its own seed and cold-start
trace. Its `pin` command can retain a known-broken Electric jar as a negative
control without substituting it for the current candidate.

## Qualification

The expanded automated checks pass on the current local candidate. See
[the test audit](TEST-SUITE-AUDIT.md) for the retained historical gaps and
[the qualification record](CURRENT-QUALIFICATION.md) for exact candidates.
Manual acceptance remains separate. Do not treat an in-progress run, successful
compilation or a previous jar's result as acceptance of a different build.

## Remaining Scope

These are specific measured setups, not a proof of every possible machine
arrangement. The 30-minute run repeats a bounded processor, conveyor, filter
and oil batch circuit; it does not exercise every operating case for that long.
There are further automatable combinations, such as more weather/time states,
every automation face and larger contention networks. They are not silently
counted as covered by a registration or saved-state assertion.

Human checks still matter for live GUI interaction and synchronization, player
elevator rides, timed weapon loading, sound, multiplayer and turbine animation.
The historical broader player/entity migration record also retains a controlled
native wolf-health difference. The current machine checks do not erase it.
The corrections in this record target 1.16.5 only; no backport is implied.
