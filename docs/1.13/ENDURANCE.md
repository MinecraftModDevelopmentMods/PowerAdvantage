# Advantage Works 1.13.2 endurance qualification

This is a repeated-load test, not an accelerated clock or an idle server left
running. Read the [migration matrix](MIGRATION-MATRIX.md) separately: preserving
old save data and operating rebuilt Works equipment are different gates.

Latest qualification: [the current candidates pass](#current-candidate-qualification).
Earlier runs remain recorded below with their original artifact identities.

## Protocol

`test-rig/migration/endurance.py` creates two disposable, operator-marked worlds:

- A fresh packaged 1.13.2 Works world.
- A copy of the original Cyano 1.10 -> maintained 1.12 -> 1.13 conversion result.

Both use the packaged Advantage jars with Base Metals and OreSpawn. Artifact
hashes and the source fixture's fingerprint are checked. No source world is
opened in Minecraft. The Works bays are rebuilt in the converted **copy**; this
is not an endurance proof for every original machine in that save.

Servers run concurrently on loopback ports 25584 and 25585. Thirty real one-minute
intervals receive bounded new inputs after the preceding batch passes. Machine
blocks, progress, temperature and conduit networks are not reset between batches.
Only measured feed/output inventories and the oil circuit are replenished,
using native inventory commands and fluid handlers, never tile NBT writes.
NBT inventory writes would trigger Power's loaded-network refresh and could
conceal a stale-network defect; they are not allowed in the qualified run.
At least 18 game ticks per wall-clock second must elapse over the measured run.

Each interval requires:

- Steam and electric furnaces: four iron ores become exactly four ingots each.
- Steam and electric crushers: four cobblestone become exactly four gravel each.
- Electric oven: four raw beef become exactly four cooked beef.
- Plain conveyor: all 16 dirt reach the destination with no extra or held items.
- Zero-filter lane: eight dirt and eight apples pass forward, none go below.
- One-filter and multi-filter lanes: eight matching dirt go below and eight
  nonmatching apples go forward; no source or held input remains.
- Distillation: 2,000 mB crude oil become exactly 1,000 mB refined oil, with no
  crude remaining. All three tanks and the still's input tank are counted because
  the shared bidirectional pipe network can return output to the supply tank.
- Disconnected pump, storage and public-API water witnesses retain respectively
  1,000, 2,000 and 4,000 mB of water.
- Geothermal heat stays above zero; energy stays finite and nonnegative; item
  counts remain valid; battery charge stays within the four battery capacities.
- Stored partial-charge battery items retain their entire inventory NBT, and the
  hydroelectric generator's battery has gained charge.

After the load run, the runner checks station assertions, leaves power sources
and pipes intact, saves, shuts Minecraft down completely, starts a new process and repeats
the content assertions **before** reseeding. It then resumes operation for another
minute and requires another correct batch. No `build`, `reset`, station restart,
or power-block replacement is performed on the processing lanes after loading.
The public fluid API's saved full tank must retain its contents throughout.

Minute-by-minute JSON/SNBT checkpoints, throughput ledgers, staged artifact hashes,
console logs and a final `endurance-results.json` remain in the ignored run root.
Station `MANUAL_REQUIRED` results remain manual; the throughput oracle's PASS does
not imply a human GUI acceptance result. Error/fatal log lines are retained in the
report rather than suppressed.

## Run and repeat

From the PowerAdvantage checkout, after building the harness:

```text
gradlew.bat -p test-rig check assemble
test-rig/migration/.venv/Scripts/python.exe -m unittest discover -s test-rig/migration -p test_*.py -v
test-rig/migration/.venv/Scripts/python.exe test-rig/migration/endurance.py --java8 <java8.exe> --migrated <converted-runtime> --output run-endurance/new-run
test-rig/migration/.venv/Scripts/python.exe test-rig/migration/audit_endurance.py --run run-endurance/new-run --output run-endurance/new-run/qualification-audit.json
```

Use the normal Python executable on other platforms. The converted runtime must
contain `AdvantageWorksMigration/level.dat`. Output directories must be new.
`--profile-manifest` accepts the migration runner's pinned `target113` profile
format for another installation; `--port` changes the pair of loopback ports.
`--seconds 180` is a preflight, reported as `SMOKE_PASS_NOT_ENDURANCE`, never as a
30-minute qualification. Keep allow markers and harness jars out of real worlds.

The oracle's negative controls prove that missing, duplicated, unexpected and
unprocessed items, wrong routing, fluid loss/identity errors, closed-tank loss,
saved battery loss, negative energy and overcharge fail the test.

Preflights also repaired three test-fixture problems, not gameplay code:
initial in-flight conveyor items were not cleared before measuring a new batch;
the oil checker watched only the still rather than the whole circuit; and an
uncontained network-test drill bored into the adjacent scarcity bay. The network
drills now face immediate bedrock stops, protected by a manifest regression test.
The oil station's start action refills its source through the actual fluid handler
rather than merging a fluid compound into an existing empty-tank NBT marker.
Oil is supplied only at start, not during setup, so the initial batch has no
preloaded oil hidden in the still. Native inventory commands use the 1.13
`container.N` slot names, not the older `slot.container.N` spelling.

## Coverage limits

Active bays are `W-01,W-03,W-04,S-01,S-02,S-03,S-04,E-01,E-02,E-03,E-05,P-API-01`.
Additional inert checks cover `S-06,S-05,E-04,E-06`. `E-04` is not activated beside
the exclusive `E-01` environmental test. Extra equipment in those bays may tick,
but only the quantities listed above receive repeated-load qualification.

This run does not qualify moving drills/track recovery, elevators, weapons,
turrets, loose player data, chunk unload, growth throughput, every fluid machine,
all boiler fuel/environment combinations, every recipe mode, or a full Sylvester
modpack upgrade. GUI rendering and client/server presentation remain manual gates.
There is no system-wide fuel/energy conservation assertion: infinite test supplies
are intentional, and fuel slots are replenished. Material processing ratios and
closed fluid storage do have exact quantity assertions.

## Results

This section records the earlier jar set, before the drill and network repairs.
The [current-candidate rerun](#current-candidate-qualification) is recorded separately.

Qualified locally on 2026-10-06 using Power `2.4.4.113021`, Steam/Electric
`2.2.5.113021`, Base Metals `3.0.1.113021`, OreSpawn `4.1.0.113021`, and the
harness SHA-256 `20E0C1D4F0F12682FDD5CB307B31F50534DBB3F79094FAA75847A24DA93E8246`,
recorded in that run's `input-jars.json`. The current `packaged-current.json`
may pin a newer test-only harness for subsequent qualification; it must not be
used to infer the historical run's harness hash. No Advantage gameplay code or
release jar was changed during this testing pass.

| Environment | Timed load | Verified batches | Full restart | Resumed work | Audit |
| --- | --- | --- | --- | --- | --- |
| Fresh 1.13.2 Works | 1,801.56 seconds | 30 | PASS | PASS | PASS |
| Original 1.10 -> maintained 1.12 -> 1.13 copy | 1,801.56 seconds | 30 | PASS | PASS | PASS |

Per environment, the timed window produced 120 items in each of the five
processing lanes (600 total), routed 480 plain-conveyor items plus 480 in each
of the three filter lanes (1,920 total), and converted 60,000 mB crude oil to
exactly 30,000 mB refined oil. Before/after-restart checks are repeated observations
of the last batch, not extra production. The resumed batch is verified separately.
All closed-water and stored-battery witnesses passed; no measured loss, duplication
or incorrect yield was found. The source fixture's fingerprint remained unchanged.

The independent audit verified all 13,179 inventory-command acknowledgements
during the timed window and 439 after restart, per world. It also checked all six
staged jar hashes, thirty distinct batch records, and that no processing station
was rebuilt/reset/restarted to make the saved networks work. Both servers exited
normally; no test server remains listening on the four temporary test ports.

Twenty-four Python oracle/audit/migration tests, five Java harness tests, harness
isolation/build checks, and the normal Power release artifact checks passed.
All station-level human checks still report `MANUAL_REQUIRED`.

Evidence is retained locally under the Git-ignored
`run-endurance/20261006-native-qualified/`: `endurance-results.json`,
`qualification-audit.json`, each world's `endurance-ledger.json`, minute snapshots,
copied saves, and startup/restart console logs. The earlier
`run-endurance/20261006-qualified/` completed its quantity checks but used NBT-based
replenishment: it is **diagnostic only**, not the network-endurance qualification.
All shorter preflights and failed fixture trials are retained separately.

The logs are not error-free. The migrated world retains the already documented
Base Metals leaves-transformer error. Forge 25 also reports its config-watcher
and temporary mod-pack cleanup issues. These remain in `log_diagnostics`; there
were no `Can't keep up!` warnings in the qualified logs. No Base Metals, OreSpawn,
or Forge source was modified to suppress these messages.

## Current candidate qualification

On 6 October 2026, `run-endurance/20261006-current-candidates-160410` repeated
the unchanged native-feed protocol on the current drill- and network-repair
candidates. Both fresh 1.13 and the original Cyano 1.10 -> maintained 1.12 ->
1.13 disposable copy report **PASS**, including complete process restart,
retained-content checks before reseeding, and one new operating batch.

| Environment | Timed load | Verified batches | Game ticks | Restart/resume | Audits |
| --- | --- | --- | --- | --- | --- |
| Fresh 1.13.2 Works | 1,801.56 seconds | 30 | 36,207 | PASS | PASS |
| Original 1.10 -> maintained 1.12 -> 1.13 copy | 1,801.56 seconds | 30 | 36,037 | PASS | PASS |

Per world, the timed window produced 120 items in each of the five processing
lanes (600 total), routed 480 plain-conveyor items and 480 through each of the
three filter lanes (1,920 total), and converted 60,000 mB crude oil to exactly
30,000 mB refined oil. There were no residual inputs, unexpected products or
measured item/fluid losses. The resumed batch is a separate successful batch;
before/after-restart observations of the last timed batch are not extra output.

The isolated pump, storage tank and public-API tank retained respectively
1,000, 2,000 and 4,000 mB water. Stored partial-charge battery item NBT remained
unchanged, hydroelectric lithium batteries reached their 8,000-unit capacity
without overcharge, geothermal temperatures stayed above zero, and measured
energy remained finite and nonnegative through all checkpoints and both restarts.

Exact release pins:

| Artifact | SHA-256 |
| --- | --- |
| Power `2.4.4.113021` | `0CCA0AF27497431C3A64091809F8DD328C84D82500CF05456366770C65485E74` |
| Steam `2.2.5.113021` | `D7D8A2E728E779EF0B624EF8AF3437F3354D618F12E279BE1C134F4AD5E690E3` |
| Electric `2.2.5.113021` | `72027C2DC273FD28F23AF01BB3EB495C75076E19B27A38CBDC5213B35A6198EB` |
| Harness `1.1.0.113021` | `80402A725EC02B9D169B0E11CE3E99EF0892F6E6468891A618A34A4E5EC7C615` |

All six staged artifacts, including Base Metals and OreSpawn, match their
manifest hashes in both worlds. All 53 Python regression and negative-control
tests pass. The independent command/hash audit verifies 13,179 acknowledged
inventory writes during the timed phase and 439 after restart per world, thirty
distinct timed batches, and no processing-station rebuild/reset or replacement
of power sources and pipes after restart.

A separate offline replay uses the station definitions from each staged
harness jar and reparses all 33 checkpoints per world: thirty timed batches,
before restart, after restart and resumed work. All 1,056 station checkpoint
records agree with the quantity/state oracle and ledgers, including output
totals, closed-water witnesses, stored battery NBT and world-clock progression.
The replay sends no server commands and never rewrites original evidence.
Its result is retained as `raw-snapshot-audit.json` alongside
`qualification-audit.json`, `endurance-results.json`, ledgers, snapshots and logs.

The runner used loopback ports 25604/25605 after its initial bind check found
an unrelated server on 25584. That server remains untouched. All four test
server processes stopped normally, neither test port remains listening, and
the converted source fixture's before/after fingerprints match.

The logs retain two known Base Metals leaves-transformer errors in the converted
world (initial startup and restart), and one Forge temporary-resource-pack
cleanup FATAL on fresh-world shutdown. There are no ticking-machine crashes,
failed inventory commands, config-watcher FATALs or keep-up warnings in this run.
The diagnostic lines remain visible; this is not an error-free-log claim.

No production code, release artifact, immutable fixture or earlier result was
changed, and no commit, push or publication was performed. The converted Works
bays are newly commissioned equipment, not an endurance qualification of every
original player machine. The coverage limits above remain: moving drills and
chunk lifecycle have their separate reports, while broader player/entity
migration, every machine/fuel/recipe combination and manual client acceptance
are not qualified by this endurance run.
