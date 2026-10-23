# Packaged migration matrix

For current-version inventory-trigger and recipe-book persistence tests, see
[Recipe Discovery](../../docs/1.13/RECIPE-DISCOVERY.md). The dedicated runner tests
all three recipe modes, independent expansions, missing material providers and
full-process restarts in fresh disposable worlds.

For raw-material-to-machine reachability and live furnace, hammer, oil and
plastic processing, see [Manufacturing Progression](../../docs/1.13/MANUFACTURING-PROGRESSION.md).
Run `progression.py --java8 <java8.exe> --output run-progression/new-current`;
`--mode NORMAL`, `--mode TECH_PROGRESSION` or `--mode APOCALYPTIC` narrows a
diagnostic run. The default qualifies all modes with both rubber settings.

For the standalone current-version bucket/drain/pipe/container regression, see [Oil Transfer](../../docs/1.13/OIL-TRANSFER.md). It reuses this runner's packaged server and lifecycle snapshots but does not migrate or open an existing world.

For working **source-version connected assemblies**, use `connected.py`, not
`matrix.py --operate`: the latter commissions new machines after conversion.
Read the [connected protocol, results and retained failures](../../docs/1.13/CONNECTED-MIGRATION.md).
The connected runner builds once in a fresh legacy process, supplies finite
items/water, runs 803 ticks, then permits only snapshots, 512-tick intervals,
saving and shutdown in intermediate/target copies. Imported worlds carry no
construction authority. Both adapters require `advantage-connected.allow` and
`migration-operate.allow`; only the initial source receives `connected-build.allow`.

```text
python test-rig/migration/connected.py --java8 <java8.exe> --profile-manifest <archived-profiles.json> --output <new-ignored-directory>
python test-rig/migration/audit_connected.py <completed-root> --output <ignored-audit.json>
```

Run from the Power checkout. For split campaigns, supply all roots to the audit;
it requires five sources and eight routes and does not accept partial evidence.
`--sources`, `--routes` and `--port` narrow diagnostic runs, not complete acceptance.
Current production pins remain unchanged; rebuilt test probes are archived and
repinned explicitly. The default profile is an existing local qualification,
so another machine must supply its own equivalent hash-checked manifest.
The current full campaign/audit intentionally exits nonzero for the 1.12
intermediate drill-progress loss rather than silently treating it as expected.

That is the **original** campaign. The subsequent
[legacy drill repair](../../docs/1.13/DRILL-PROGRESS-REPAIR.md) passes all eight
routes with explicit candidate overrides while keeping historical source jars
unchanged. `drill_restart.py` adds native 1.10/1.12/1.13 two-restart checks for
both naturally powered mid-cycle drills, and `audit_drill_restart.py` verifies
first-tick progress, frozen state, further production and untouched source saves.
Use `--red` only to audit a retained two-version unpatched reproduction; its
campaign result remains FAIL. `pin_drill_profiles.py` archives old outputs,
prepares separate candidate profiles and refreshes mutable 1.10 rig references.
None of these scripts modifies archived historical manifests or production jars.

This runner never opens a source world in Minecraft. It creates disposable,
explicitly marked superflat fixtures through source-version serializers, hashes
the saved worlds, and converts only copies. Read the
[qualification report](../../docs/1.13/MIGRATION-MATRIX.md) before interpreting PASS.

## Prerequisites

- Java 17 for Gradle; Java 8 for each legacy Minecraft server.
- Python 3 with `requirements.txt` installed in an ignored local virtual environment.
- Built Power/Steam/Electric 1.13 jars and the Works harness.
- Built legacy probes for both source runtimes:

```text
gradlew.bat --no-daemon -p test-rig/migration-probe -PprobeTarget=110 assemble
gradlew.bat --no-daemon -p test-rig/migration-probe -PprobeTarget=112 assemble
gradlew.bat -p test-rig check assemble
```

The legacy probe compiles against Minecraft/Forge only, not any Advantage version.
It registers no blocks/items. Its operator-only build/snapshot commands require
both `advantage-migration.allow` and the exact `AdvantageWorksMigration` world name.
The 1.13 snapshot adapter lives in the separate Works harness, never in release jars.

## Inputs and repeatability

The local default profiles discover the existing qualified server libraries and
jar locations recorded in `matrix.py`. Those paths are workspace conveniences,
not public distribution dependencies. On another machine, supply an explicit
`--profile-manifest` with entries in this format:

```json
{
  "original110": {
    "seed": "../servers/forge110",
    "launcher": "forge-1.10.2-12.18.3.2511-universal.jar",
    "jars": [{"path": "../jars/Original.jar", "sha256": "FULL_UPPERCASE_SHA256"}]
  }
}
```

Include all required mod jars and the appropriate probe/harness in each profile.
A full manifest has `original110`, `early110`, `maintained110`, `legacy112`,
`maintained112`, and `target113`; chained routes use `maintained112` as the bridge.
Relative paths resolve against the manifest file. A previous run's `profiles.json`
can serve as a manifest while its pinned input files still match.
The `seed` supplies installed Forge launchers and libraries, never a world or mods.
The runner rejects hash mismatches and unavailable requested profiles.

The default original-release profile requires the two original Sylvester jars
and the official Electric jar staged under
`run-migration-matrix/original-release-jars/`. Its filenames, hashes, and Cyano
class provenance are checked. No jar is downloaded automatically by this runner.

## Commands

From the PowerAdvantage checkout (replace the Java path with the local Java 8):

```text
test-rig/migration/.venv/Scripts/python.exe -m unittest discover -s test-rig/migration -p test_matrix.py -v
test-rig/migration/.venv/Scripts/python.exe test-rig/migration/matrix.py --java8 <java8.exe> --output run-migration-matrix/fresh-run
test-rig/migration/.venv/Scripts/python.exe test-rig/migration/matrix.py --java8 <java8.exe> --sources original110 --fixtures run-migration-matrix/fresh-run --output run-migration-matrix/repeat-run
test-rig/migration/.venv/Scripts/python.exe test-rig/migration/matrix.py --java8 <java8.exe> --operate run-migration-matrix/repeat-run/original110-chained-113 --output run-migration-matrix/operation-run
```

Use the environment's normal Python executable on other platforms. Output paths
must be new directories. The local server binds only `127.0.0.1:25584`, disables
online authentication for these automated fixtures, and is shut down between phases.
No viewer server is left running.

Each route saves and fully stops Minecraft after conversion, launches a new
process, snapshots again, and checks source preservation and restart stability.
The operation pass enables ticking in another copy, commissions nine Works
stations, checks two inert drill bays, and repeats checks after full restart.
Manual checks retain `MANUAL_REQUIRED`; this is not a human GUI acceptance test.

Never copy the allow markers, probe jars, or harness into a real gameplay instance.
Do not commit worlds, dependency jars, caches, logs, or generated reports.

`audit_results.py` combines completed run roots, verifies the immutable source
fixtures and actual staged-jar hashes, and retains log errors alongside the state
result. It never opens Minecraft. Supply `--results`, `--fixtures`, `--operation`,
and a Git-ignored `--output` JSON path; see `--help` for the argument shapes.

## Repeated-load endurance

`endurance.py` runs fresh and original-release chained converted copies for thirty
real minutes, checks exact repeat-batch throughput and storage each minute, then
performs a complete process restart and another operating batch. See the
[endurance protocol and qualification limits](../../docs/1.13/ENDURANCE.md).
Use `--help` for paths, alternate pinned server profiles and shorter preflights;
a short smoke run is never labelled an endurance pass.

## Chunk lifecycle and moving drills

`lifecycle.py` observes actual chunk unload/reload events and six-facing drill
movement, saved inventories/energy, rear output, and mid-block progress. It tests
fresh and converted disposable worlds and performs complete process restarts.
Read the [protocol and coverage limits](../../docs/1.13/CHUNK-AND-DRILLS.md) and
use `audit_lifecycle.py` to independently reconcile the raw evidence. A gameplay
FAIL must not be relabelled PASS simply because the evidence audit succeeds.

`extended_lifecycle.py` adds partial supply/consumer unloading, both reload orders,
repeated native moves, vertical tracks, junctions, low reserves, full rear chests
and redstone interruptions. See the [extended protocol](../../docs/1.13/PARTIAL-NETWORKS-AND-EXTENDED-DRILLS.md);
`audit_extended_lifecycle.py` reconciles the raw records independently.

`partial_network_trace.py` captures opt-in real chunk-acquisition stacks.
`pipe_only_lifecycle.py` checks unloading/reconnection through a bridge chunk
with no machine endpoints, including zero fluid acceptance while disconnected.
Use `audit_pipe_only.py` for its independent state/conservation audit. These
diagnostics use fresh, marked disposable worlds and never operate on player saves.

## Players and saved entities

`actors.py` extends the qualified source fixtures with a synthetic, deterministic
offline player and five tagged entities. Legacy game serializers produce the
inventory, Ender Chest, armour, offhand, charged batteries, damaged/enchantment-bearing
musket, custom tags, and oil item/bucket witnesses. A source without the registered
Forge universal bucket uses its crude-oil item instead; `actor-fixture.json`
records that bucket coverage is unavailable for that source.

The player adapter is the real `PlayerList.readPlayerDataFromFile` and
`IPlayerFileData.writePlayerData` operating on `EntityPlayerMP`, not `FakePlayer`.
It does not establish a network connection or exercise the Forge login handshake.
A separate unlogged-in Forge fake-player sentinel prevents the witness world
from going idle; it never loads or saves the actual witness player's file.
Embedded single-player NBT is injected from the source serializer into `level.dat`
and observed before the dedicated server removes host-only data. This is a
pre-datafix embedded-data test, not integrated-server login acceptance.

```text
python actors.py --java8 <java8.exe> --output <new-ignored-run-directory>
python audit_actors.py --run <completed-run-directory> --output <ignored-audit.json>
python actors_native.py --java8 <java8.exe> --fixtures <completed-run-directory> --output <new-ignored-control-directory>
python audit_actors_native.py --run <completed-control-directory> --fixtures <completed-run-directory> --output <ignored-control-audit.json>
python player_layout.py --java8 <java8.exe> --fixtures <completed-run-directory> --output <new-ignored-layout-directory>
python audit_player_layout.py --run <completed-layout-directory> --output <ignored-layout-audit.json>
```

Run these commands from this directory using the configured Python environment,
or supply the script paths from the PowerAdvantage checkout. The default ports
are isolated localhost ports 25616, 25617 and 25618. Existing listeners are not stopped.
The runner requires the local hash-checked source fixtures listed in
`qualified_baselines()` and stages the dependency profiles recorded by `matrix.py`.

The actor, native and player-layout runners also accept `--profile-manifest`
with the same hash-pinned format as `matrix.py`. An optional `bridge112` entry
selects only the intermediate runtime; `maintained112` stays the original source
profile. This allows a repaired 1.12 Electric jar to be tested without silently
replacing historical fixtures or artifacts. The original `2.2.1.112021` Electric
jar may be preserved under ignored `run-migration-matrix/legacy-release-jars/`;
the default source profile verifies its qualified SHA-256. Never overwrite it
with a rebuilt candidate. Profile manifests and jars remain local and untracked.

Use `actors.py --routes original110-chained early110-chained maintained110-chained`
alongside the normal arguments for a focused chained regression. The strict
runner now saves and completely restarts the 1.12 intermediate before converting
it to 1.13. Both checkpoints require the legacy probe's explicit `frozen` flag
and zero measured entity ticks. `audit_actors.py --routes ...` can independently
audit a specified completed subset without rewriting the original reports.

The strict campaign suppresses entity ticking before the first converted save,
checks migrated player files, runs the player adapter across complete restarts,
then releases entities for at least sixty measured entity ticks and restarts again.
Freeze checkpoints require every present tagged entity's counter to remain zero;
a Forge update flag alone is not accepted as proof. Tagged witnesses are removed
from the entity-update list at tick start and restored at tick end.
Per-entity `ticksExisted` deltas are mandatory: elapsed world time alone cannot pass.
The native control separately allows entity ticks before the first save, including
the intermediate 1.12 stage on chained routes. It does not restore lost entity
fields, reseed inventories, or replace missing entities to obtain a pass.
The layout supplement covers main-bag boundaries, the last Ender Chest slot,
armour/offhand, and recursively stored enchanted, charged and oil item stacks.
Tile ticking remains suppressed so machines cannot manufacture replacement
turbines and hide loss of the saved entity. Generated saves, logs, snapshots,
backups and reports remain Git-ignored. See the
[actor qualification report](../../docs/1.13/PLAYER-AND-ENTITY-MIGRATION.md)
for verdicts, timing-sensitive failures, and untested client/ownership cases.

After a completed native-feed run, `audit_endurance.py --run <run-root> --output
<ignored-audit.json>` independently verifies command acknowledgements, untouched
restart networks, distinct batch records and the actual staged jar hashes. It
rejects older NBT-fed diagnostic passes, which could refresh caches while feeding.

## Additional Dimensions and Transformer Ordering

`dimensions.py --native-load` extends all eight source routes to both the Nether
and End, with real player-file adapters, a mounted owned wolf, leashed equipped
horse and saved turbine. It requires native initialization before the first save
and measured entity ticks on intermediate/target restarts. `audit_dimensions.py`
replays the evidence. The separate `respawn.py` supplement exercises keep/drop
inventory and forced/valid/missing-bed cases; `audit_respawn.py` reconciles saved
files and item drops. See the [dimension protocol and limits](../../docs/1.13/DIMENSIONS-AND-ENTITY-LIFECYCLE.md).
Use `pin_dimension_profiles.py` to pin rebuilt test probes without changing
production/dependency pins; rebuilding an input afterwards makes the runner refuse it.

`transformer_order.py` swaps only the actual BaseMetals/PowerAdvantage transformer
pairs inside a disposable Forge coremods library, while retaining unchanged mod
jars. It observes real application and emitted hook order, then compares original
1.10 and maintained 1.12 migrations across natural and both forced orders.
`audit_transformer_order.py` replays the bytecode, semantic and backup evidence.
See the [ordering report](../../docs/1.13/TRANSFORMER-ORDERING.md), especially the
first-writer/second-writer backup distinction. Never install the patched library
in a gameplay instance.
