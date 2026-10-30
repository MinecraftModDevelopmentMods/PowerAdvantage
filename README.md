# PowerAdvantage

**Minecraft 1.15.2**

Power Advantage 2.4.6.115021 targets Minecraft 1.15.2. See the
[current test record](docs/1.15/TEST-COVERAGE-BACKPORT.md) for the exact jars,
automated results and remaining limits. Visuals and recipe unlocks were
accepted on 27 October 2026; machine behavior is covered by the recorded
automated tests.

Power Advantage provides shared power, fluids, item transport and compatibility APIs for Steam Advantage and Electric Advantage.

**Back up the entire world before upgrading. Test only disposable copies.**
The port retains existing registry names and legacy migration code. Upgrade tests
use disposable original and maintained saves, including connected machines.
Check every other mod's target-version support too.
The accepted [1.14 results](docs/1.14/BROADER-QUALIFICATION.md) are historical
evidence, not qualification of this port or a whole modpack.

Recipe-book discovery now follows relevant ingredients. See the [all-Advantage material checklist and automated discovery tests](docs/1.13/RECIPE-DISCOVERY.md). To regenerate the checked-in unlock JSON after changing recipes, run `node tools/generate-recipe-unlocks.js`; `check` rejects missing or stale ingredient criteria.

The [manufacturing progression audit](docs/1.13/MANUFACTURING-PROGRESSION.md) traces survival parts from raw resources, including configured material aliases, processing, loot-only parts and automated restart checks.

## Building Minecraft 1.15.2

The `master-1.15.2` build uses Forge 31.2.57, snapshot mappings
`20200514-1.15.1`, MCP `20200515.085601`, ForgeGradle 7.0.34 and Gradle 9.6.1.
Run Gradle with Java 17 and compile production code with Java 8. Mavenizer also
needs Java 25. CI pins all three JDKs; the [test commands](docs/1.15/TEST-HARNESS.md)
show how to select local installations explicitly.

Build the pinned Base Metals `3.0.2.115021` and OreSpawn `4.1.1.115021` deobf jars first.
The exact Base Metals source is
`ac1781e7cade15045ffe0135077087b4d1722603`. CI builds that dependency first;
the checksums and complete source pins are in `gradle.properties`.

Then run from `D:\PowerAdvantage\PA115\PowerAdvantage`:

```text
gradlew.bat clean check build javadoc verifyReleaseDependencies verifyReleaseArtifacts writeReleaseChecksums verifyEclipseProductionClasspath
```

The default dependency jars are:

```text
D:\BaseMetals\BaseMetals 115\BaseMetals\build\libs-dev\BaseMetals-3.0.2.115021-deobf.jar
D:\MinecraftMineralogy\OS115\OreSpawn\build\libs-dev\OreSpawn-4.1.1.115021-deobf.jar
```

Other layouts can pass `-PbaseMetalsDeobfJar=<path>` and `-PoreSpawnDeobfJar=<path>`.
Dependencies are never embedded in PowerAdvantage artifacts.

Dependency checksums and source commits are recorded in `gradle.properties`.
Unknown artifacts fail the dependency audit. Archives use reproducible ordering,
LF text and no empty directories, so fresh checkouts produce the same bytes.

Release jars are written to `build/libs`; `deobfJar` writes the sibling-development jar to `build/libs-dev`.

For Eclipse, import this repository as an existing Gradle project and run:

```text
gradlew.bat cleanEclipse verifyEclipseProductionClasspath
```

Refresh the project in Eclipse, allow its Java build to finish, and launch the generated `runClient.launch`. Eclipse launches discover the mod from `bin/main`; command-line Gradle launches use `build/forge-run-mod-classes/main`. The Eclipse audit checks that discovery points at the same production output Eclipse compiles into, preventing a client that starts without PowerAdvantage.

Eclipse copies production resources from `build/resources/main`, not directly from `src/main/resources`. This prevents its builder from overwriting the expanded `mods.toml` with raw `${version}` placeholders. After updating this setup, use **Gradle > Refresh Gradle Project**, then **Project > Clean** before relaunching. After editing resources or version properties, run `processResources` or refresh the Gradle project before the Eclipse build. See the [metadata regression notes](docs/1.13/ECLIPSE-METADATA.md).

To prepare a combined client, build the Steam/Electric `2.2.7.115021` deobf jars,
run `gradlew.bat prepareCombinedEclipseLaunch`, refresh Eclipse, then launch
`runClientAllAdvantage.launch`. Inputs must match
`test-rig/profiles/native115/dev-core.json`; alternative layouts can pass
`-PsteamAdvantageDeobfJar=<path>` and `-PelectricAdvantageDeobfJar=<path>`.
The ordinary `runClient.launch` remains Power-only. Both use the `run` directory;
preparing a launch does not copy, open or qualify a save.

Client-resource and recipe-book checks run against the 1.15 candidate. Manual
visual and interaction acceptance remains separate; see the current test record.

## Compatibility

The implementation namespace remains `zone.moddev.mc.poweradvantage`, with the
historical `cyano.poweradvantage.api` package retained. The port keeps registry,
fluid, tile, NBT and provider identities. The current test record distinguishes
machine preservation, actual operation and vanilla migration differences.

The 1.15 changes use native activation results, non-solid block properties,
texture-atlas access and client render-layer registration. The shared migration
hook is retargeted to 1.15; its presence is not proof that an upgrade is safe.

The [legacy migration guide](docs/1.13/MIGRATION.md) and
[Advantage Works rig](test-rig/README.md) describe the earlier protocols.
The [1.15 status](docs/1.15/PORT-STATUS.md) links the current qualification.

The historical 1.10 handbook remains on the [1.10.2 branch](https://github.com/MinecraftModDevelopmentMods/PowerAdvantage/blob/master-1.10.2/docs/1.10/README.md).

## Generator Follow-Up

The [generator follow-up](docs/1.15/TEST-COVERAGE-BACKPORT.md#generator-follow-up) records the older `pa115-candidate-b` inputs.
Its 126 operating jobs and 126 cold checks, 39 GUI windows, 351 caption cases,
355 rejected broken controls, thirteen frozen routes, ten connected routes and
paired 30-minute runs passed. This does not extend the earlier fourteen-route
proof. It is a pre-final Steam-control checkpoint, not qualification of the newer
SteamAdvantage jars or harness. Those still need the final sweep.
Manual play-testing and release review remain separate.

## Final T Candidate

The [final-t checkpoint](docs/1.15/TEST-COVERAGE-BACKPORT.md#final-t-candidate)
records profile `pa115-candidate-d` and its exact jar hashes. All 132 operating
jobs and 132 cold-restart comparisons pass, including an independent audit of
six Steam controls and their nine tile-hold records. GUI checks pass for 39
windows, 351 caption cases and 355 rejected broken controls.
Thirteen frozen routes passed. The verified connected aggregate passes all ten
declared routes across both five-route shards.
Fresh and migrated-copy 30-minute runs pass. Power and Electric retain their
exact-hash release and reproducibility proof; final-t Steam passes its full suite
and two independent export builds. All 18 mod/version release and reproducibility
gates pass across the six maintained Minecraft lines. Both requested broad
profiles and their independent audit pass; the reporter-only unit/replay
qualification preserves the original native proof. Survival harvest checks pass
before and after restart. The report's extra network/drill scopes are now
qualified; manual acceptance remains separate. Earlier checkpoints keep their original scope;
nothing has been published by this local test work.
