# Advantage Works 1.16.5 Native Test Rig

The native target is Minecraft 1.16.5 / Forge 36.2.34, official mappings
1.16.5, MCP 20210115.111550, Java 8 bytecode and harness
`1.1.0.116051`. Read [the native116 runner and qualification limits](NATIVE116.md)
before using this checkout. Build success is not runtime or migration acceptance.

Default destination profiles live only in `profiles/native116/`. The original
profiles, station specification and legacy migration source adapters remain
unchanged. New profiles explicitly reference the historical PA113 source root;
the native runner refuses to launch an old profile as a native116 destination.
The native114 scripts/profiles and [their qualification record](NATIVE114.md)
remain historical evidence, together with the unchanged [native115 record](NATIVE115.md).
Missing 116 artifact pins fail before runtime creation. No earlier pass is a 116
runtime qualification. New flat fixtures require the harness-only
`advantageworkstest:test_flat` world preset; it saves as vanilla `minecraft:flat`.

## Retained 1.13.2 Protocol Reference

The remaining text describes the retained **1.13.2 source qualification**, not
1.14.4 results. Its paths and old artifact versions are historical. Use the
original source checkout at `D:/PowerAdvantage/PA113/PowerAdvantage` for that
qualification and its documentation. Do not execute copied Python defaults
from PA114 or alias `target114` to `target113`.

> **Commissioning notice:** This harness builds disposable works. It is not a gameplay mod, registers no content, and must never be shipped inside a PowerAdvantage jar.

The rig turns the 20 cards from the Advantage Works 1.10 handbook plus a public-API fixture into one walkable superflat server world. Power Works lies west of the central concourse, Steam Works north, Electric Works east, and the drill, lift, turbine, pumping, musket, and turret proving grounds south. See the [1.13 migration guide](../docs/1.13/MIGRATION.md) for this port's qualification status.

Use the [current qualification and remaining-tests checklist](../docs/1.13/CURRENT-QUALIFICATION.md)
to distinguish structural bay checks from measured machine operation and manual
acceptance. Building every bay does not prove every behaviour on its card.

The [connected legacy migration runner](../docs/1.13/CONNECTED-MIGRATION.md)
builds and runs real assemblies under old jars, then resumes copied equipment
without replacing machines or refilling supplies. Its intermediate 1.12 failure
is retained separately from successful 1.13 operation.

For non-interactive recipe checks, see [Recipe Discovery](../docs/1.13/RECIPE-DISCOVERY.md) and [Manufacturing Progression](../docs/1.13/MANUFACTURING-PROGRESSION.md). The progression runner starts from raw resources, records every item/recipe path, runs live processing witnesses and checks all modes, resource reloads and full restarts. It uses a separate marked disposable world, not the walkable viewer save.

## Safety interlocks

The `/advworks` command refuses to build or clear unless both conditions hold:

- the level name begins with `AdvantageWorks`;
- the server working directory contains `advantage-works.allow`.

Each station has declared bounds. Reset clears blocks only inside those bounds and removes only entities tagged by that station. Generated saves, staged jars, logs, reports, Forge server files, caches, and local settings are ignored by Git.

## Operator controls

Every bay has clickable start, stop, reset, and check signs plus a wool status lamp. Console operators can use:

```text
/advworks build [all|power|steam|electric]
/advworks reset <station|all>
/advworks start <station>
/advworks stop <station>
/advworks check <station|all>
/advworks status [station]
/advworks checkpoint <station> <name>
/advworks sample-worldgen <chunk-radius>
```

Before a player uses the clickable controls, grant that tester operator permission with `op <player>` at the server console. The harness intentionally keeps `/advworks` at permission level 2 as a second interlock beyond the marked disposable world.

The definition at `src/main/resources/assets/advantageworkstest/works/works-1.13.2.json` owns station origins, bounds, placements, setup/start/stop actions, assertions, required mods, and manual checks. Station IDs are the handbook card IDs `W-01` through `E-08`, plus `P-API-01` for the stable fluid-network API.

## Result meanings

| Result | Meaning |
| --- | --- |
| `PASS` | Every automated assertion passed and no manual observation remains. |
| `FAIL` | An ordinary assertion failed. Automated runners stop with failure. |
| `EXPECTED_FAIL` | A declared handbook bug reproduced; the assertion names its bug ID. |
| `UNEXPECTED_PASS` | A declared bug no longer reproduced and needs review before expectations change. |
| `SKIP` | A required mod or integration is absent. |
| `MANUAL_REQUIRED` | Structural assertions passed, but gameplay observations remain on the station card. |

Animation and GUI appearance are never sufficient proof. Use checkpoints and reconcile bounded items, fluids, and energy.

The focused [oil-transfer protocol](../docs/1.13/OIL-TRANSFER.md) adds four isolated remote lanes: crude/refined oil through a drain and descending/horizontal pipes into each container type. It uses real bucket placement, exact fluid identity/volume assertions, two complete process restarts, and a second fill without replacing machinery. This closes a gap in `W-01`/`W-02`, whose drain assertions alone did not establish bounded oil conservation.

## Build and development run

Run Gradle with Java 17. The harness and all Minecraft code compile to Java 8 bytecode.
Compiler outputs and harness jars live in `build/gradle/`. Pinned dependencies,
disposable saves and reports remain in sibling directories under `build/`.
The harness `clean` task is restricted to `build/gradle/`; it must never clear the
shared fixture and evidence root.

```text
gradlew.bat -p test-rig clean test assemble
gradlew.bat -p test-rig verifyWorksDev
gradlew.bat -p test-rig runWorksDev
```

On Linux or macOS use `./gradlew` with the same arguments. `verifyWorksDev` builds all stations, runs each bounded start/stop sequence, saves, stops the server process, starts it again, checkpoints the retained state, and reruns the structural assertions. `runWorksDev` leaves the disposable server open at `localhost:25565` for a human inspector.

Long `worksRunSeconds` holds do not replenish finite inputs and may mostly observe
idle equipment. For thirty minutes of measured repeat batches, use the separate
[endurance runner](migration/README.md#repeated-load-endurance) and read its
[qualification limits](../docs/1.13/ENDURANCE.md).
For genuine chunk unload/reload and moving-drill tests, use the separate
[chunk and drill protocol](../docs/1.13/CHUNK-AND-DRILLS.md). Its results distinguish
inventory and network recovery from the separate movement-cost, rear-output
and unfinished-progress acceptance checks, with before/after repair evidence.
The [extended lifecycle protocol](../docs/1.13/PARTIAL-NETWORKS-AND-EXTENDED-DRILLS.md)
adds separate supply/consumer unloading, reload orders, repeated moves, vertical
tracks, junctions, blocked chests, low energy and redstone controls.
The ordinary lifecycle runner gives `W-03` at least sixty seconds to finish its
2,000 mB oil batch; its exact circuit-wide yield assertion cannot finish in the
five-second quick-check default used for other stations.

Limit an automated run to selected lifecycle bays, or turn one into a 30-minute endurance run, without changing its profile:

```text
gradlew.bat -p test-rig verifyWorksDev -PworksStations=W-04
gradlew.bat -p test-rig verifyWorksPackagedCurrent -PworksStations=W-04,S-02 -PworksRunSeconds=1800
gradlew.bat -p test-rig verifyWorksPackagedCurrent -PworksStations=E-01 -PworksServerPort=25566
```

Every machinery profile still builds and checks the complete works; the selection controls which stations are started, stopped, and checkpointed around the full process restart. Normal-world generation profiles deliberately skip station construction. The configured run duration is also used as a post-restart settle interval before assertions, allowing an eight-tick distribution cycle to complete. Use `worksServerPort` when another local server occupies the profile's default port. The effective station list, duration, and port are recorded in `run-manifest.json`.

The development profile uses current PowerAdvantage source plus the sibling SteamAdvantage and ElectricAdvantage deobf jars. The normal development runtime also includes the pinned Base Metals and OreSpawn deobf jars required by the 1.13 data-driven crushing and provider APIs. Use the packaged decoupled profile to prove behavior without Base Metals.

## Run matrix

The separate [original-release/chained migration matrix](../docs/1.13/MIGRATION-MATRIX.md)
freezes source-version machines, compares their serialized state through conversion,
and checks a full server restart. Its [runner instructions](migration/README.md)
describe the source fixtures, safety interlocks, and pinned input profiles.

| Profile/task | Purpose |
| --- | --- |
| `verifyWorksDev` | Current Power source and sibling deobf extensions with pinned Base Metals and OreSpawn 4 |
| `verifyWorksPackagedCurrent` | Reobfuscated Advantage jars with BaseMetals `3.0.1.113021` and pinned OreSpawn `4.1.0.113021` |
| `verifyWorksPackagedDecoupled` | Reobfuscated Advantage jars with OreSpawn 4 but without BaseMetals/Mineralogy |
| `verifyWorksPackagedMineralogy` | Reobfuscated Advantage and Mineralogy jars; exercises both crude oils through distillation, transfer, persistence, and boilers |
| `verifyWorksPackagedOptionalPower` | Preserved RF- and Tech Reborn-labelled converter identities through Forge Energy |
| `verifyWorksTechProgression` | Registration/build audit under `TECH_PROGRESSION` |
| `verifyWorksApocalyptic` | Registration/build audit under `APOCALYPTIC` |
| `verifyWorksWorldgenAdvantage` | Normal world: Electric fallback sulfur/lithium, Power desert oil, user-disable persistence |
| `verifyWorksWorldgenMineralogy` | Normal world: Mineralogy sulfur/ocean oil, Electric lithium fallback, user-enable persistence |
| `verifyWorksWorldgenLegacyImport` | Normal world: OS1 files migrate once, remain authoritative, and do not duplicate modern rules |

Profiles pin filenames, mod IDs, versions, and SHA-256 values. Most workspace-built harness jars are identified and version-checked without a source-dependent hash; `packaged-current` also pins the harness used for the endurance qualification. Refresh that hash deliberately after rebuilding changed harness sources. The packaged runner downloads only the pinned official Forge installer, verifies it, installs a fresh server into the disposable runtime, and launches Minecraft with Java 8.

Set these only for profiles that need them:

```text
ADVANTAGE_WORKS_CURRENT_MODS=<directory containing BaseMetals-3.0.1.113021.jar>
ADVANTAGE_WORKS_JAVA8_HOME=<Java 8 home, when it is not in the default Gradle toolchain cache>
ADVANTAGE_WORKS_GRADLE_JAVA_HOME=<Java 17 home, when the runner itself is launched on Java 8>
```

## Station register

| District | Cards |
| --- | --- |
| Power Works | `W-01` fluid persistence; `W-02` drain/discharge reach; `W-03` distillation; `W-04` conveyors and all filters |
| Power API Works | `P-API-01` public fluid offer, transfer limit, full consumer, invalid target, and input immutability |
| Steam Works | `S-01` boilers; `S-02` topology; `S-03` storage/scarcity; `S-04` furnace/crusher/still |
| South proving grounds | `S-05` drill; `S-06` pump; `S-07` elevator; `S-08` musket; `E-01` generators/turbine; `E-06` drill; `E-07` light/turret |
| Electric Works | `E-02` batteries/distribution; `E-03` processors; `E-04` fluids; `E-05` growth; `E-08` isolated assembler diagnostic |

Ore generation is intentionally excluded from the superflat works. The three `verifyWorksWorldgen*` tasks create separately named disposable normal worlds, locate nearby desert and ocean biome samples, generate bounded chunk squares, count the target blocks, check oil biome placement, and repeat the same scan after a full process restart.

## OreSpawn profile checks

The normal-world profiles inspect `<world>/serverconfig/orespawn-worldgen.json` after first shutdown and after restart. They verify default ownership, edit a persisted `enabled` flag to simulate a user choice, and require OreSpawn to preserve that choice. The legacy profile stages the retained OS1 Power/Electric files under `config/orespawn/`, requires their migration report and provider files, and rejects simultaneous modern duplicate IDs.

`worldgen-mineralogy` covers Mineralogy-over-Electric sulfur ownership and both oil biomes. BaseMinerals does not yet have a 1.13 provider release, so its ownership combinations remain statically covered by ElectricAdvantage's provider tests and are an explicit pending runtime matrix item.

The sampler records block counts, sampled biome centers, any deposit-producing chunks outside their declared biome selector, and informational per-block spill at biome boundaries. OreSpawn selects deposits by chunk-centre biome. Existing chunks are sampled again after restart; disabled rules affect only terrain generated later and therefore must not erase or alter the recorded blocks. Solid ore counts must remain exact. The legacy profile excludes only `poweradvantage:crude_oil` from exact count comparison because OS1 represented it as an exposed ore vein and the fluid can continue flowing between observations; its migrated rule, enabled state, and positive presence are still asserted on both runs.

## Confirmed-bug regression witnesses

| Station | Automated witness | Manual remainder |
| --- | --- | --- |
| `S-01` | Seeds both `Tempoerature` and `Temperature`, saves canonically, and requires retained heat after a full process restart (`SA-110-002`). | Compare the geothermal GUI gauge with the saved value. |
| `S-04` | An isolated unpowered crusher with one cobblestone must report comparator level `1` before and after restart (`SA-110-003`). | Check intermediate and full stack levels when commissioning redstone automation. |
| `S-06` | An isolated pump must retain exactly 1,000 mB of water after restart (`SA-110-001`). | Confirm the pump GUI agrees with the server NBT. |
| `E-01` | Requires an active hydroelectric generator and charge in its directly connected battery after restart (`EA-110-003`). | Open the hydroelectric GUI and confirm a normal-width active bar. |

## Release isolation

`verifyHarnessIsolation` opens the PowerAdvantage release jar and rejects any `zone/moddev/mc/advantageworks` class or `assets/advantageworkstest` resource. The normal PowerAdvantage build never includes the nested `test-rig` source set.
