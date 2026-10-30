# PowerAdvantage

**Minecraft 1.14.4**

This port has passed the automated machine/migration tests and a manual check of
visuals and recipe unlocks. See the [qualification report](docs/1.14/BROADER-QUALIFICATION.md)
for coverage and limits. Use disposable copies for upgrades.

Power Advantage provides shared power, fluids, item transport and compatibility APIs for Steam Advantage and Electric Advantage.

This port keeps existing registry names and carries forward migration from 1.10 and 1.12.
Always back up a world before upgrading, and check that its other mods also support the target version.
The archived [1.13 qualification notes](docs/1.13/CURRENT-QUALIFICATION.md) separate its tested scenarios from remaining gaps;
they are not a guarantee that an entire modpack can be upgraded without losses.
The [1.14 qualification report](docs/1.14/BROADER-QUALIFICATION.md) records the
repeated checks, exact tested jars and remaining limits for this candidate.

Recipe-book discovery now follows relevant ingredients. See the [all-Advantage material checklist and automated discovery tests](docs/1.13/RECIPE-DISCOVERY.md). To regenerate the checked-in unlock JSON after changing recipes, run `node tools/generate-recipe-unlocks.js`; `check` rejects missing or stale ingredient criteria.

The [manufacturing progression audit](docs/1.13/MANUFACTURING-PROGRESSION.md) traces survival parts from raw resources, including configured material aliases, processing, loot-only parts and automated restart checks.

## Building Minecraft 1.14.4

The `master-1.14.4` build uses Forge 28.2.26, ForgeGradle 7.0.34 and Gradle 9.6.1. Run Gradle with Java 17; Gradle resolves the Java 8 production toolchain.

Build the pinned Base Metals `3.0.1.114041` and OreSpawn `4.1.1.114041` deobf jars in their sibling checkouts, then run:

```text
gradlew.bat clean check build javadoc verifyReleaseDependencies verifyReleaseArtifacts writeReleaseChecksums verifyEclipseProductionClasspath
```

The default dependency paths follow the local MMD checkout layout. Other layouts can pass `-PbaseMetalsDeobfJar=<path>` and `-PoreSpawnDeobfJar=<path>`. Dependencies are present on development classpaths but are never embedded in PowerAdvantage artifacts.

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

To test all three Advantage mods together, run `gradlew.bat prepareCombinedEclipseLaunch`, refresh Eclipse, and launch `runClientAllAdvantage.launch`. This additional profile loads the pinned Steam/Electric deobf jars, Base Metals and OreSpawn alongside Power's Eclipse output. It uses the same `run` directory and test saves; it does not copy, open or modify a save automatically. The ordinary `runClient.launch` remains Power-only. Alternative checkout layouts can pass `-PsteamAdvantageDeobfJar=<path>` and `-PelectricAdvantageDeobfJar=<path>`; supplied jars must match the `test-rig/profiles/native114/dev-core.json` pins. Regenerate the combined launch after refreshing its dependencies or the base launch.

The combined [client resource probe](test-rig/client-resources/README.md) checks native baked models, textures, creative entries, translated item names, fluid rendering and tank gauges without opening a save. Static resource and locale regressions also run under `check`. Automated recipe-book fixtures and the accepted manual visual/recipe-unlock checks have separate scopes; see the [current port status](docs/1.14/PORT-STATUS.md).

The [broader 1.14 test results and play-test checklist](docs/1.14/BROADER-QUALIFICATION.md) separate migration, connected machinery, endurance and manual checks. Test worlds and reports stay local; use disposable copies when trying an old save.

## Compatibility

The implementation namespace is `zone.moddev.mc.poweradvantage`. The historical `cyano.poweradvantage.api` surface, including `FluidNetworkApi`, remains available to dependent mods. Registry names, fluids, tile IDs, NBT keys, OreSpawn provider IDs, and configuration meaning remain stable.

Forge 1.14 handles oil rendering, fluid containers, registered menus, tag updates
and parts-loot events directly. Advantage's public fluid contract is bridged to
native sided fluid capabilities; its millibucket units and saved tank keys remain
unchanged. The remaining coremod runs before vanilla's legacy-world data fixes.

PowerAdvantage owns the shared pre-flattening migration hook used by all three Advantage mods. See [the 1.13 migration guide](docs/1.13/MIGRATION.md) before opening an irreplaceable 1.10 or 1.12 world. The [Advantage Works rig](test-rig/README.md) provides repeatable machine, restart, world-generation, and migration checks.

The [endurance protocol](docs/1.13/ENDURANCE.md) exercises repeat-batch operation in fresh and migrated disposable worlds, followed by a complete server-process restart. Its coverage limits are separate from frozen-state migration and manual client acceptance.

The [chunk and moving-drill protocol](docs/1.13/CHUNK-AND-DRILLS.md) adds genuine chunk unload/reload witnesses, finite-energy track moves in all six facings, rear-output conservation, and mid-block progress checks.

The [partial-network and extended-drill protocol](docs/1.13/PARTIAL-NETWORKS-AND-EXTENDED-DRILLS.md) separates endpoint unloading/recovery from whole-network reloads, and covers repeated moves, vertical track travel, junctions, blocked output and redstone interruption.

The archived [player and entity migration report](docs/1.13/PLAYER-AND-ENTITY-MIGRATION.md) covers player files, inventories, nested items, ownership, saved entities and full restarts on 1.13. Its eight routes passed with the repaired 1.12 bridge. Use Electric Advantage `2.2.3.112021` for that bridge's turbine and drill fixes; the original `2.2.1.112021` jar remains a failing historical control.

The [additional-dimension protocol](docs/1.13/DIMENSIONS-AND-ENTITY-LIFECYCLE.md)
covers Nether/End player and entity fixtures, native passenger/leash relationships
and separate death/respawn controls. The [transformer-order report](docs/1.13/TRANSFORMER-ORDERING.md)
qualifies both BaseMetals/Advantage application orders and records the
order-dependent provenance of loose-file backups.

Networks use loaded chunks only. Missing conduit sections interrupt distribution;
normal chunk loading reconnects them without replacing pipes. The same protocol
records acquisition-stack diagnostics and pipe-only bridge regressions.

The historical 1.10 handbook remains on the [1.10.2 branch](https://github.com/MinecraftModDevelopmentMods/PowerAdvantage/blob/master-1.10.2/docs/1.10/README.md).

The local [test-coverage backport](docs/1.14/TEST-COVERAGE-BACKPORT.md) records the
repaired candidate, measured machine jobs, restart checks and GUI captures.

## Generator Follow-Up

The [generator follow-up](docs/1.14/TEST-COVERAGE-BACKPORT.md#generator-follow-up) records the older `pa114-candidate-c` inputs.
Its 126 operating jobs and 126 cold checks, 39 GUI windows, 351 caption cases,
355 rejected broken controls, thirteen frozen routes and five connected routes
passed. The corrected paired 30-minute run also passed; the original POI failure
is retained. This is a pre-final Steam-control checkpoint, not qualification of
the newer SteamAdvantage jars or harness. Those still need the final sweep.
Manual play-testing and release review remain separate.

## Final T Candidate

The [final-t checkpoint](docs/1.14/TEST-COVERAGE-BACKPORT.md#final-t-candidate)
records profile `pa114-candidate-e` and its exact jar hashes. All 132 operating
jobs and 132 cold-restart comparisons pass, including an independent audit of
six Steam controls and their nine tile-hold records. GUI checks pass for 39
windows, 351 caption cases and 355 rejected broken controls.
Frozen migration passed on 13 routes and connected-source operation passed on
all five declared routes.
Fresh and migrated-copy 30-minute runs pass. Power and Electric retain their
exact-hash release and reproducibility proof; final-t Steam passes its full suite
and two independent export builds. All 18 mod/version release and reproducibility
gates pass across the six maintained Minecraft lines. Both requested broad
profiles and their independent audit pass; the reporter-only unit/replay
qualification preserves the original native proof. Survival harvest checks pass
before and after restart. The report's extra network/drill scopes are now
qualified; manual acceptance remains separate. Earlier checkpoints keep their original scope;
nothing has been published by this local test work.
