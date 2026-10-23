# Connected Legacy Works: Migration and Continued Operation

## Verdict

**Follow-up:** the subsequent [legacy drill repair](DRILL-PROGRESS-REPAIR.md)
fixes both drill types in maintained 1.10/1.12 code. Native restart tests and all
eight migration routes pass with the repaired bridge. The verdict below records
the original pre-repair campaign and its failures; those records are retained.

The 23 October 2026 campaign builds and operates five connected assemblies under
five historical source profiles, then migrates copies of the **already working
equipment**. Nothing is rebuilt, refilled, replaced or manually reconnected after
import. The production jars are unchanged from the
[current-candidate qualification](CURRENT-QUALIFICATION.md#scope).

All eight routes preserve their frozen saved state and operate successfully in
1.13, including a complete 1.13 process restart. All five direct routes pass.
The three chained routes remain **FAIL** overall because the intermediate 1.12
Electric Drill loses partial mining progress when ticking resumes after reload.
That failure is not waived because the subsequent 1.13 stage succeeds.

| Source profile | Direct to 1.13 | Via 1.12, including intermediate restart | 1.13 operation and restart |
| --- | --- | --- | --- |
| Original Cyano 1.10 | PASS | FAIL: `Q-CONNECTED-001` | PASS on both routes |
| Early local 1.10 | PASS | FAIL: `Q-CONNECTED-001` | PASS on both routes |
| Maintained 1.10 | PASS | FAIL: `Q-CONNECTED-001` | PASS on both routes |
| Early local 1.12 | PASS | Not applicable | PASS |
| Maintained 1.12 | PASS | Not applicable | PASS |

These are the archived profiles described in the
[source provenance ledger](MIGRATION-MATRIX.md), not newly rebuilt historical
mods. There is no original published Cyano 1.12 fixture. The chained runtime uses
the separately pinned repaired Electric 1.12 bridge, without replacing the
historical source profile.

## What Was Built

Each source world contains 456 declared block positions in five isolated
assemblies. Targets and helper-bit positions are observed alongside equipment.
The rigs begin with exactly 90,000 mB water, finite coal and item inputs, zero
machine energy, and an empty lithium battery. There are no infinite power sources.
All construction and inventory seeding occur only in the initial legacy process.

| Assembly | Physical connection and workload |
| --- | --- |
| `C-CONVEYOR` | Chest containing 1,728 dirt, two east-facing conveyors, empty destination chest |
| `C-STEAM` | Three coal boilers, each supplied through a fluid pipe from a water container; steam main, steam tank, three-lane furnace and rock crusher |
| `C-ELECTRIC` | Two supplied coal boilers, steam main, steam-to-electric generator, cables, initially empty lithium battery array and electric furnace |
| `C-SDRILL` | Two supplied boilers, steam pipes, north-facing steam drill, rear output chest and 63 cobblestone targets |
| `C-EDRILL` | Two supplied boilers, generator and cables, north-facing electric drill, rear chest and 32 nine-block target planes |

The steam drill crushes cobblestone into gravel. The electric drill harvests
ordinary cobblestone; it is not a crusher. Both require exactly one retained item
per removed target in this fixture. Coal/water are not replenished between stages.

After 803 native source ticks, **each historical profile** had produced:

| Witness | Measured output |
| --- | --- |
| Conveyor destination | 114 dirt |
| Steam furnace | 12 iron ingots |
| Steam crusher | 5 gravel |
| Electric furnace | 3 iron ingots |
| Steam drill | 24 gravel |
| Electric drill | 9 cobblestone |
| Lithium battery | 8,000 charge, from the connected generator |

The remaining inputs, current progress, stored steam/electricity and water are
saved by the real source-version serializers. Each source save is fingerprinted
after shutdown and is never started again; conversion uses copies.

## Observation Protocol

1. Build the assembly once in a new legacy superflat world. Validate cold input
   quantities and zero power; run 803 ticks and require real output.
2. Save, stop the process, fingerprint the source world and copy it.
3. Load the copy with machinery held out of the native ticking list. Compare
   actual block states and complete saved tile NBT, including nested inventories,
   fluids, energy bits, progress and target coordinates, before any machine work.
4. Permit exactly 512 normal world ticks and check further output, finite water,
   inventory accounting and retained tile identity. Save and stop completely.
5. Start another process, compare the saved checkpoint while frozen, then permit
   another 512 ticks and check continued operation.
6. On chained routes perform the same load/work/restart sequence under 1.12
   before copying that worked save into 1.13.

The adapter holds and restores the real tickable tile list without reconstructing
tiles. During measured intervals Minecraft performs normal machine and network
updates. Bounded chunk tickets and an unlogged-in fake-player sentinel keep the
fixture active; this is not a networked-player test. No pressure, inventory,
progress or conduit-cache priming is performed after construction.

Each original machine carries an NBT witness marker. During a process interval
its object identity must remain unchanged as well. The 1.13 command adapter has
no construction method; it supports only snapshots and tick-bounded observation.
Command logs must contain only those operations, `save-all flush` and `stop`.
Source-only build markers are not copied to later runtimes.

All 22 conversion/restart state comparisons have zero differences. All sixteen
1.13 operating intervals pass material accounting and work checks. Each route
also requires actual additional items across its two 1.13 intervals; animation
or an increasing progress bar alone cannot satisfy that gate. Steam drills in
the longer chained route finish all 63 targets during the first 1.13 interval;
their subsequent restart checks completed-job retention, not further excavation.

The shared item totals are conserved: dirt `1,728`, steam-furnace ore plus ingots
`192`, electric-furnace ore plus ingots `64`, and crusher cobblestone plus gravel
`64`. Drill drops must equal removed targets with the correct product identity.
Water cannot increase, and stored energy must be finite and nonnegative. Full NBT
equality applies to frozen boundaries; normal fuel consumption and generation
are allowed during native operation. This does not establish exact energy
efficiency or cross-version throughput equality for every machine.

The offline audit records 450 passing checks and five failures: the three
intermediate restart checks plus the two enclosing split-campaign verdicts.
These are one reproduced legacy defect, not five separate bugs. All source-world
fingerprints, archived/staged jars and forbidden-mutation checks pass. The
separate first-tick audit reports `REPRODUCED_LEGACY_FAILURE_TARGET_PASS`.

## Findings Retained

### Q-CONNECTED-001: Intermediate 1.12 Electric Drill Progress Loss

The electric drill reaches saved progress `512` of a 576-tick nine-cobblestone
cycle. Frozen reload preserves `512`, but the first native 1.12 tick resets it to
`1`. Repeated 512-tick sessions therefore do not finish that saved target. The
failure is reproduced on all three chained routes.

An isolated comparison copies that same worked 1.12 world into both runtimes:

| Runtime | Frozen loaded progress | After one native tick | Continued operation |
| --- | --- | --- | --- |
| Qualified 1.12 bridge | 512 | 1: FAIL | Completes a new cycle when allowed 700 further ticks |
| Current 1.13 | 512 | 513: PASS | Continues mining without losing saved work |

In the 1.12 implementation, deferred target reconstruction calls `targetBlock`,
which assigns `progress = 0`. The existing 1.13 `restoreTargetBlock` rebuilds the
target and drops while retaining valid saved progress. Neither the source save
nor the migrator is repairing the 1.12 runtime in this test.

Source locations: legacy Electric
`machines/ElectricDrillTileEntity.java`, methods `tickUpdate`, `powerUpdate`,
`targetBlock` and `loadFrom`; current
[ElectricDrillTileEntity.java](../../../ElectricAdvantage/ElectricAdvantage/src/main/java/zone/moddev/mc/electricadvantage/machines/ElectricDrillTileEntity.java),
methods `restoreTargetBlock` and `read`.

No mined items disappear and the machine is not permanently stalled; it must
repeat unfinished work. Repairing the maintained 1.12 implementation is separate
work. The three chained campaign failures and the offline audit's nonzero exit
remain visible. No production fix is included in this pass.

### Dependency Recipe Difference: Obsidian

An earlier pilot successfully preserved the original saved blocks/NBT and
continued drilling, but did not preserve *newly generated recipe output*: the
old Base Metals pack dropped one obsidian, whereas pinned Base Metals 1.13
defines `basemetals:obsidian_crushing` as four obsidian powders. Previously saved
obsidian items remained unchanged. This is a dependency recipe difference, not
item duplication during migration, but it prevents claiming identical behaviour
for every old crusher input. The failed pilot remains under
`run-connected-migration/20261023-original-pilot2/`.

The final fixture uses cobblestone because both source and target packs agree on
its crushing output. Initial setup/oracle mistakes involving `Tank.Empty` and
electric-versus-steam drill products also remain in their original diagnostic
runs. Cold-state and negative oracle tests now guard against those mistakes.

## Evidence and Repetition

Local ignored evidence roots:

```text
run-connected-migration/20261023-working-legacy110/
run-connected-migration/20261023-working-maintained110/
run-connected-migration/20261023-working-112/
run-connected-migration/20261023-connected-audit.json
run-connected-migration/20261023-progress-trace-verified/
```

Each contains raw snapshots, compressed NBT, source fingerprints, staged jar
identities, hash-addressed input archives, commands, console logs and verdicts.
Production/dependency pins match the previous qualification; only test probes
changed. Existing Base Metals/Forge/OreSpawn log diagnostics remain recorded,
including the Base Metals leaves transformer and legacy bucket registration
messages. State/operation success does not mean error-free modpack logs.

From the PowerAdvantage checkout, after building both legacy probes and the
current harness as described in the [runner instructions](../../test-rig/migration/README.md):

```text
python test-rig/migration/connected.py --java8 <java8.exe> --profile-manifest <archived-profiles.json> --output <new-ignored-directory>
python test-rig/migration/audit_connected.py <run-root> --output <ignored-audit.json>
```

`--sources` and `--port` permit separate campaigns. Supply all completed roots to
the offline audit; it requires all five sources/eight routes, the qualified
803/512-tick protocol, unchanged input jars/source worlds, valid commands and raw
state/operation evidence. It exits nonzero while the intermediate failures remain.
The default profile path is a local archived qualification, not a portable public
download. Supply equivalent hash-pinned inputs on another machine.

`trace_connected_progress.py` accepts `--fixture` pointing to a completed chained
`*-112` runtime and `--profile-manifest`, `--java8`, `--output`; it uses a separate
port and disposable copies. `audit_connected_progress.py --run <trace-root>
--fixture <same-source> --output <ignored-audit.json>` independently checks the
one-tick failure and successful target continuation.

The harness compiles/checks and remains absent from the Power release jar.
All 158 Python tests and 20 harness JUnit tests pass. Regression/oracle tests
include rejection of lost/duplicated items,
replaced tiles, invalid power, unmeasured ticks, progress reset and incomplete
campaign evidence. All production hashes remain unchanged. Nothing is committed,
pushed or published.

## Remaining Scope

This closes the specific connected-source-equipment gap for these five
assemblies, not for every card in the works. It does not cover connected imported
elevators, growth chambers, every filter, pumps, oil processing, turrets, muskets,
all drill facings/tracks or third-party converters. Those retain their
[remaining-behaviour checklist](CURRENT-QUALIFICATION.md#remaining-behavioural-checklist).
The pre-existing `Q-RIG-001` moving-drill oracle finding is unchanged.
Real-client interaction and whole-Sylvester-modpack acceptance remain separate.
