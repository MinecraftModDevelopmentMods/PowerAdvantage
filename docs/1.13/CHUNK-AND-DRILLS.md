# Chunk recovery and moving-drill qualification

These tests complement the [frozen migration matrix](MIGRATION-MATRIX.md) and
[repeat-load endurance](ENDURANCE.md). They operate packaged 1.13.2 Advantage
mods in disposable worlds, not Eclipse classes or an irreplaceable player save.

Latest qualification: [the current network-repair candidate passes](#current-network-repair-regression).
The earlier repair results and original failure evidence remain below for comparison.

## Protocol

`test-rig/migration/lifecycle.py` creates a fresh world and a copy of the original
Cyano 1.10 -> maintained 1.12 -> 1.13 conversion fixture. It builds isolated lanes
above superflat terrain, at least 2,048 blocks from spawn. The converted copy's
lanes are newly commissioned equipment; this is not operation coverage for every
original machine in an old world.

Twelve drills cover both mods in all six facings. Each advances one block along
horizontal track across a chunk boundary, against an immediate bedrock stop.
Each carries seven dirt with identifying custom item NBT and thirteen apples.
Public energy setters commission a finite reserve within the actual capacity:
40/50 steam or 400/500 electricity. There is no generation during the move.

A test-only world-tick observer records the drill immediately before and after
its native movement. It checks destination, serialized position, facing, exact
inventory including tags, replacement of the old drill with conduit, and cost:
10 steam plus its ordinary 1/16-per-tick decay, or 250 electricity. After recording
the move, the observer adds an external redstone block to hold the new drill.
It never rewrites that drill's NBT, inventory, progress or energy.

Independent lanes place a steam furnace, electric furnace and fluid receiver on
the opposite side of a chunk seam from their supply/conduit. Native inventory
setters feed four iron ores per furnace; solid fuel is retained in the steam
furnace. Public fluid-network offers supply five measured 1,000 mB water batches
to a 10,000 mB metal tank. Sources and pipes are not replaced across reloads.

Three cycles release the fixture chunk tickets and queue normal provider unloads.
The non-loading observer requires actual Forge unload events, absence from the
loaded-chunk map, and continued absence after another two seconds. Reload requires
new load events and different tile instances, stable facing/inventory/tank contents,
and resumed ore processing/fluid acceptance. A snapshot must not load the chunk
whose absence it claims to measure.

The server then saves and exits completely. A new process checks retained moved
drills, inventories and 4,000 mB water before new inputs. Derived active/redstone
flags settle through normal ticks; they are not treated as immutable save state.
The original pipe at each moved drill is now supplied, rear chests are installed,
and bounded mining checks one cobblestone -> one gravel for Steam and nine
cobblestone -> nine cobblestone drops for Electric. All twenty carried items and
their tags must reach the rear chest with the mined output. Another real chunk
cycle checks those results remain intact.

Finally, long-lived obsidian targets establish nonzero mining progress. Chunk
reload and a second full process restart must retain progress toward the same
target. These observations distinguish retained completed items from lost work
on an unfinished block. No redstone intervention is made during the mid-block
checks, because redstone intentionally cancels progress.

The repair regression also performs two separate saved-NBT read checks after
the real process restarts. `legacy-read` round-trips each drill's actual saved
record with only the optional target-state snapshot removed. It never invents
inventory, energy or progress values. `changed-target-read` replaces the saved
target's centre with bedrock before reading that record; progress and target
coordinates must clear. These focused read-path checks supplement, rather than
replace, the genuine chunk and process lifecycle tests.

## Repeating the test

From the PowerAdvantage checkout, with built/pinned release and harness jars:

```text
gradlew.bat -p test-rig check assemble
test-rig/migration/.venv/Scripts/python.exe -m unittest discover -s test-rig/migration -p test_*.py -v
test-rig/migration/.venv/Scripts/python.exe test-rig/migration/lifecycle.py --java8 <java8.exe> --migrated <converted-runtime> --output run-lifecycle/new-run --port 25586
test-rig/migration/.venv/Scripts/python.exe test-rig/migration/audit_lifecycle.py --run run-lifecycle/new-run --output run-lifecycle/new-run/qualification-audit.json
```

Use the normal Python executable on other platforms. Output directories must be
new. All source fixture files are fingerprinted before/after; staged jars are
SHA-256 checked. The runner stops its own servers on both success and failure.
Minecraft worlds, logs and reports remain Git-ignored. A completed run may return
`FAIL` for a reproducible gameplay defect; a passing evidence audit validates the
record, not the defective gameplay.

## Coverage limits

This gate covers one horizontal track step for each of the six drill facings,
not every track direction, long multi-step travel, track junctions or blocked/full
output behavior. Whole fixture networks unload together; deliberately partial
network unload and asymmetric loading are separate scenarios. It does not claim
client animation/GUI acceptance, elevators, weapons, or a complete Sylvester
modpack upgrade. Known Forge/Base Metals log errors remain visible in the reports.

The subsequent [partial-network and extended-drill campaign](PARTIAL-NETWORKS-AND-EXTENDED-DRILLS.md)
addresses selected limits without changing what this earlier gate proved.

## Before repair: 6 October 2026

The authoritative bounded-reserve run is `run-lifecycle/20261006-native-final`.
It completed against both a fresh 1.13 world and a disposable copy of the
original 1.10 -> maintained 1.12 -> 1.13 fixture. Earlier fixture trials remain
diagnostic evidence only. No production gameplay code changed in this pass.

| Acceptance case | Fresh 1.13 | Converted fixture |
| --- | --- | --- |
| Five genuine chunk unload/reload cycles | PASS | PASS |
| Network processing resumes without replacing pipes/sources | PASS | PASS |
| Water conservation: five offers, 5,000 mB retained | PASS | PASS |
| Moved drill positions, six facings, inventories and custom item NBT | PASS | PASS |
| Steam helper-bit orientation after reload | PASS | PASS |
| Steam movement energy cost | PASS | PASS |
| Electric movement energy cost | FAIL | FAIL |
| Bounded mining yield retained across reload | PASS | PASS |
| Output reaches an empty vanilla rear chest | FAIL | FAIL |
| Unfinished mining progress survives chunk/full restart | FAIL | FAIL |
| Independent evidence audit | PASS | PASS |
| Overall machine qualification | FAIL | FAIL |

Each world exercised 60 unique chunks over five cycles: 300 independently
confirmed unload events per world, 600 total. Each also performed twelve moves
and two full process restarts. All six Steam and six Electric facings were
covered in both worlds. Each drill retained its twenty carried items, their
custom tags, and the exact mined yield; the failed output check means those
items remained inside the drill, not that they disappeared.

The locally retained `lifecycle-results.json` contains every case, input jar
SHA-256, source-fixture protection result and log diagnostic.
`qualification-audit.json` independently reconciles those verdicts against raw
snapshots, real chunk events and staged jar hashes. The source fixture's before
and after fingerprints match. All six server processes shut down normally;
five emitted Forge's temporary-resource-directory cleanup error on exit. There
were no ticking-block-entity crashes, failed native inventory commands or
"Can't keep up!" warnings. These cleanup diagnostics remain in the report.

The harness used was `AdvantageWorksTestHarness-1.1.0.113021.jar`, SHA-256
`559D141BF7798C5BD2748170BDEBC235BB3FE2D732CB5DE75CFA2E5E9405E421`.
All production jar pins remained unchanged. The harness checks passed (nine
Java tests), the migration runner tests passed (33 Python tests), and
PowerAdvantage's `check verifyReleaseArtifacts` passed, including harness
isolation. None of those passes overrides a failed gameplay assertion.

## Confirmed defect register

### EA-113-001: Electric track moves do not pay their energy cost

- Expected: the moved Electric Drill retains its inventory/facing and debits the
  defined `ENERGY_COST_MOVE = 250` from the transferred reserve.
- Observed in both worlds: all six moves leave 400 electricity as 400,
  rather than 150. The finite reserve is within the drill's 500-unit capacity.
- Cause: `ElectricDrillTileEntity.followTrack()` copies energy into the new tile,
  then calls `this.subtractEnergy(...)` on the old, removed tile instead
  (before-repair source: `machines/ElectricDrillTileEntity.java:164`).
- Impact: free track travel, not lost inventory. Steam's 10-unit debit works.
- Older versions: the same implementation is present in maintained 1.10 and
  1.12 source. Those versions were not runtime-qualified for this defect here.

### AW-113-001: Drills cannot insert into a new empty vanilla rear chest

- Expected: both drills deliver stored and freshly mined items into an available
  rear inventory, consistent with their documented automation purpose.
- Observed in both worlds: every rear chest remains empty after 60 seconds.
  The drill retains the exact twenty carried items plus the correct mined yield:
  one gravel for Steam or nine cobblestone for Electric. Custom item NBT survives.
- Cause: both `inventoryTransfer()` implementations treat only `null` as an empty
  destination. `InventoryWrapper.getStackInSlot()` returns vanilla's non-null
  `ItemStack.EMPTY`, which matches neither insertion branch. Before-repair locations:
  Steam `machines/SteamDrillTileEntity.java:419`, Electric
  `machines/ElectricDrillTileEntity.java:426`, Power
  `util/InventoryWrapper.java:236`.
- Impact: rear output stalls; this observation is not item disappearance.
- Older versions: maintained 1.12 has the same null-only check. A legacy 1.10
  empty-slot contract is different; do not claim the same runtime failure there.

### AW-113-002: Reload resets work on an unfinished drill target

- Expected: saved mining progress continues against an unchanged target, while
  inventories, energy and facing are restored.
- Observed in both worlds: progress of 92-98 ticks becomes 0-6 ticks after
  chunk reload; progress of 100-107 becomes 1-6 after full restart. The target
  remains the same obsidian block in all twelve lanes per world. No redstone
  signal or target completion accounts for the reset.
- Cause: both drills read saved `progress`, then reconstruct the target through
  `targetBlock()`, which unconditionally sets `progress = 0`. Deferred target
  reconstruction repeats this on the first loaded tick. Before-repair reset locations:
  Steam `machines/SteamDrillTileEntity.java:324` and Electric
  `machines/ElectricDrillTileEntity.java:313`.
- Impact: unfinished work is lost; repeated unloads can prevent a slow block
  from ever completing. Already collected items remain intact.
- Older versions: the same reconstruction/reset sequence exists in maintained
  1.10 and 1.12 source. Runtime reproduction here is specifically the 1.13 candidate.

These are failing acceptance cases, not `EXPECTED_FAIL` baselines or rewritten
gameplay requirements. The observations above describe the original failing
jars; the repair and its independently audited rerun are recorded below. No
defect is silently accepted by this test report.

## Repair implementation

The 1.13.2 candidate repairs are confined to the two drill implementations:

- Electric track movement charges the new tile before marking it dirty and
  removing the original. The defined 250-unit price is unchanged.
- Both rear-output paths recognize `null` and `ItemStack.EMPTY`, preserve stack
  tags and the one-item transfer rate, and mark source/destination inventories
  dirty after successful insertion.
- Target reconstruction retains saved progress, bounded by zero and the
  recalculated goal. New target acquisition, redstone cancellation and target
  invalidation still reset work. Steam initializes its redstone reading when
  restoring the target, avoiding the constructor's default shutdown state.
- Existing `progress`, `targetX`, `targetY` and `targetZ` keys remain unchanged.
  Optional `TargetState` (Steam) and `TargetStates` (Electric's nine blocks)
  snapshots permit changed-target detection on restoration. Legacy records
  without a snapshot remain readable, but cannot prove a target's old identity;
  they reconstruct the current target at their saved coordinates.

Mod versions remain the unpublished `2.2.5.113021` port candidates. No registry,
tile/entity ID, recipe, dependency or PowerAdvantage gameplay changes accompany
these repairs. The 1.10 and 1.12 implementations are not modified here.

## Repair retest

The bounded-reserve run `run-lifecycle/20261006-drill-fixes` completed on
6 October 2026. Both profiles and the independent evidence audit passed.

| Repair acceptance case | Fresh 1.13 | Converted fixture |
| --- | --- | --- |
| All six Steam/Electric movements, inventory/facing and exact energy debit | PASS | PASS |
| Five genuine chunk unload/reload cycles and resumed network processing | PASS | PASS |
| 5,000 mB water conserved | PASS | PASS |
| All carried items, custom tags and mined drops reach the rear chest | PASS | PASS |
| Output and helper-bit orientation retained after chunk reload | PASS | PASS |
| Unfinished progress retained after chunk reload and full process restart | PASS | PASS |
| Legacy record without target snapshot retains progress | PASS | PASS |
| Changed saved target clears progress and target coordinates | PASS | PASS |
| Independent evidence audit and overall machine qualification | PASS | PASS |

The rerun covered 24 moves, 600 actual fixture-chunk unloads, four full process
restarts and 48 extra saved-record read checks across the two worlds. Electric
movement now leaves 150 from a 400-unit reserve. Steam still pays 10 plus normal
decay. The exact twenty carried items plus one gravel (Steam) or nine cobblestone
(Electric) reached each rear chest and survived reload with their custom tags.

Unfinished progress of 92-97 ticks continued to 100-105 after chunk reload;
201-206 ticks continued to 208-213 after full restart. The target coordinates
remained unchanged. The legacy read kept progressing; the changed-target read
cleared work rather than applying it to bedrock.

The original failed run remains intact. Reauditing it with the strengthened
oracle still reports gameplay `FAIL`, while the repaired run reports `PASS`.
The auditor recomputes individual verdicts and overall qualification from raw
snapshots instead of trusting the runner's status labels.

Fixed candidate release pins:

| Jar | SHA-256 |
| --- | --- |
| `SteamAdvantage-2.2.5.113021.jar` | `D7D8A2E728E779EF0B624EF8AF3437F3354D618F12E279BE1C134F4AD5E690E3` |
| `ElectricAdvantage-2.2.5.113021.jar` | `72027C2DC273FD28F23AF01BB3EB495C75076E19B27A38CBDC5213B35A6198EB` |
| `AdvantageWorksTestHarness-1.1.0.113021.jar` | `B5043325294299E97B5C2E20559FEF35E9DBF0799B12C43D36E7EC1DB5D39083` |

PowerAdvantage, Base Metals and OreSpawn pins did not change. Development and
packaged profiles now reference the repaired Steam/Electric hashes. The full
FG7 release suites and Eclipse audits passed for both changed mods, including
six Steam tests and nine Electric tests. The harness's nine Java tests and
34 Python runner tests passed; PowerAdvantage's artifact/isolation audit passed.
Separate forced-rebuild Gradle invocations produced identical release, deobf,
sources and Javadoc hashes for both changed mods. All twenty Steam/Electric
development and packaged profile pins match the rebuilt files.

The converted source fixture's fingerprints match, and all test servers stopped.
Four processes logged the known Forge temporary-directory cleanup diagnostic;
two fresh-world boots logged its config-file watcher diagnostic. Neither is a
machine crash or a failed assertion. No ticking-block-entity crash, native
inventory command failure or "Can't keep up!" warning was found. The raw logs
and their diagnostics remain in `lifecycle-results.json`.

This is focused chunk/drill qualification, not a new complete migration-matrix,
30-minute endurance, clean-cache or manual client campaign. The coverage limits
above still apply. No original player save, 1.10/1.12 code, commit or published
artifact was modified.

## Current network-repair regression

On 6 October 2026, `run-lifecycle/20261006-current-power-reload-154618` repeated
the unchanged whole-network and mid-block protocol after the PowerAdvantage
[partial-network repair](PARTIAL-NETWORKS-AND-EXTENDED-DRILLS.md). Both the runner
and independent `qualification-audit.json` report **PASS** for fresh 1.13 and
the original Cyano 1.10 -> maintained 1.12 -> 1.13 disposable copy.

| Acceptance case | Fresh 1.13 | Converted fixture |
| --- | --- | --- |
| All six Steam/Electric drill facings, movement cost, carried items and tags | PASS | PASS |
| Five real whole-network chunk unload/reload cycles | PASS | PASS |
| Furnace processing and fluid delivery resume without replacing equipment | PASS | PASS |
| Exactly 5,000 mB water retained | PASS | PASS |
| Bounded mining yield reaches rear chests and survives reload | PASS | PASS |
| Steam helper-bit orientation restores correctly | PASS | PASS |
| Same-target unfinished progress survives chunk reload and full restart | PASS | PASS |
| Legacy saved record retains progress; changed target clears it | PASS | PASS |
| Independent evidence audit and machine qualification | PASS | PASS |

The run records 24 native drill moves, 600 real chunk-unload events, four full
process restarts and 48 saved-record read checks across both profiles. Each
profile retains all twenty carried items per drill, including their custom tags,
plus the expected one gravel (Steam) or nine cobblestone (Electric) mining yield.
Fresh-world progress advances from 97-99 to 105-107 ticks after chunk reload and
206-208 to 213-215 after restart. Converted-world progress advances from 92-98 to
100-106 and 201-207 to 208-214 respectively; target coordinates remain unchanged.

The new artifact pins are:

| Artifact | SHA-256 |
| --- | --- |
| Power release `2.4.4.113021` | `0CCA0AF27497431C3A64091809F8DD328C84D82500CF05456366770C65485E74` |
| Harness release `1.1.0.113021` | `80402A725EC02B9D169B0E11CE3E99EF0892F6E6468891A618A34A4E5EC7C615` |

Steam/Electric release hashes remain those in the earlier repair table. All six
staged jars match their manifest pins in both worlds. All 53 Python regression
and negative-control tests pass. The converted source fixture's before/after
fingerprints match, all six server processes stop normally, and port 25586 has
no remaining listener. There is no ticking-machine crash, failed inventory
command or keep-up warning.

Three Forge temporary-resource-pack cleanup FATALs and two Forge configuration
watcher FATALs remain recorded in `lifecycle-results.json` and the console logs.
They do not abort server startup, assertions or ordinary shutdown; they are not
silently removed from the evidence or described as a clean-log pass.

No production code, artifacts, source-version worlds or prior results changed
during this rerun. The converted-world lanes are newly commissioned equipment,
not proof that every original player machine has been qualified. The earlier
30-minute endurance results still apply to their older jar hashes; refreshed
endurance, broader player/entity migration and manual client checks remain
separate gates.

The subsequent [current-candidate endurance rerun](ENDURANCE.md#current-candidate-qualification)
now passes thirty real minutes, complete restarts and resumed processing on
these exact release hashes in both worlds. Broader migration and manual client
coverage remain separate gates; the original endurance evidence is unchanged.
