# PowerAdvantage

Power Advantage provides shared power, fluid, item transport, and compatibility APIs for its Minecraft 1.13.2 expansion mods.

This port keeps existing registry names and includes migration from 1.10 and 1.12.
Always back up a world before upgrading, and check that its other mods also support the target version.
The [qualification notes](docs/1.13/CURRENT-QUALIFICATION.md) separate tested scenarios from remaining gaps;
they are not a guarantee that an entire modpack can be upgraded without losses.

Recipe-book discovery now follows relevant ingredients. See the [all-Advantage material checklist and automated discovery tests](docs/1.13/RECIPE-DISCOVERY.md). To regenerate the checked-in unlock JSON after changing recipes, run `node tools/generate-recipe-unlocks.js`; `check` rejects missing or stale ingredient criteria.

The [manufacturing progression audit](docs/1.13/MANUFACTURING-PROGRESSION.md) traces survival parts from raw resources, including configured material aliases, processing, loot-only parts and automated restart checks.

## Building Minecraft 1.13.2

The `master-1.13.2` build uses ForgeGradle 7.0.34 and Gradle 9.6.1. Run Gradle with Java 17; Gradle resolves the Java 8 production toolchain.

Build the pinned Base Metals `3.0.1.113021` and OreSpawn `4.1.0.113021` deobf jars in their sibling checkouts, then run:

```text
gradlew.bat clean check build javadoc verifyReleaseDependencies verifyReleaseArtifacts writeReleaseChecksums verifyEclipseProductionClasspath
```

The default dependency paths follow the local MMD checkout layout. Other layouts can pass `-PbaseMetalsDeobfJar=<path>` and `-PoreSpawnDeobfJar=<path>`. Dependencies are present on development classpaths but are never embedded in PowerAdvantage artifacts.

Release jars are written to `build/libs`; `deobfJar` writes the sibling-development jar to `build/libs-dev`.

For Eclipse, import this repository as an existing Gradle project and run:

```text
gradlew.bat cleanEclipse verifyEclipseProductionClasspath
```

Refresh the project in Eclipse, allow its Java build to finish, and launch the generated `runClient.launch`. Eclipse launches discover the mod from `bin/main`; command-line Gradle launches use `build/forge-run-mod-classes/main`. The Eclipse audit checks that discovery points at the same production output Eclipse compiles into, preventing a client that starts without PowerAdvantage.

Eclipse copies production resources from `build/resources/main`, not directly from `src/main/resources`. This prevents its builder from overwriting the expanded `mods.toml` with raw `${version}` placeholders. After updating this setup, use **Gradle > Refresh Gradle Project**, then **Project > Clean** before relaunching. After editing resources or version properties, run `processResources` or refresh the Gradle project before the Eclipse build. See the [metadata regression notes](docs/1.13/ECLIPSE-METADATA.md).

To test all three Advantage mods together, run `gradlew.bat prepareCombinedEclipseLaunch`, refresh Eclipse, and launch `runClientAllAdvantage.launch`. This additional profile loads the pinned Steam/Electric deobf jars, Base Metals and OreSpawn alongside Power's Eclipse output. It uses the same `run` directory and test saves; it does not copy, open or modify a save automatically. The ordinary `runClient.launch` remains Power-only. Alternative checkout layouts can pass `-PsteamAdvantageDeobfJar=<path>` and `-PelectricAdvantageDeobfJar=<path>`; supplied jars must match the `test-rig/profiles/dev-core.json` pins. Regenerate the combined launch after refreshing its dependencies or the base launch.

The combined [client resource probe](test-rig/client-resources/README.md), with `-RequireExtensions`, checks item names, baked models, textures and every valid block state across all three Advantage mods. The legacy Steam/Electric model-path and selector failures are repaired; [the qualification notes](docs/1.13/OIL-TRANSFER.md#combined-resource-repair-21-october-2026) retain the failing evidence and subsequent full-scope result. Native resource checks remain separate from manual graphical/gameplay acceptance.

The [client resource probe](test-rig/client-resources/README.md) checks native baked models, textures, creative entries, and translated item names without opening a world. Static resource and locale regressions also run under `check`.

## Compatibility

The implementation namespace is `zone.moddev.mc.poweradvantage`. The historical `cyano.poweradvantage.api` surface, including `FluidNetworkApi`, remains available to dependent mods. Registry names, fluids, tile IDs, NBT keys, OreSpawn provider IDs, and configuration meaning remain stable.

PowerAdvantage owns the shared pre-flattening migration hook used by all three Advantage mods. See [the 1.13 migration guide](docs/1.13/MIGRATION.md) before opening an irreplaceable 1.10 or 1.12 world. The [Advantage Works rig](test-rig/README.md) provides repeatable machine, restart, world-generation, and migration checks.

The [endurance protocol](docs/1.13/ENDURANCE.md) exercises repeat-batch operation in fresh and migrated disposable worlds, followed by a complete server-process restart. Its coverage limits are separate from frozen-state migration and manual client acceptance.

The [chunk and moving-drill protocol](docs/1.13/CHUNK-AND-DRILLS.md) adds genuine chunk unload/reload witnesses, finite-energy track moves in all six facings, rear-output conservation, and mid-block progress checks.

The [partial-network and extended-drill protocol](docs/1.13/PARTIAL-NETWORKS-AND-EXTENDED-DRILLS.md) separates endpoint unloading/recovery from whole-network reloads, and covers repeated moves, vertical track travel, junctions, blocked output and redstone interruption.

The [player and entity migration report](docs/1.13/PLAYER-AND-ENTITY-MIGRATION.md) covers player files, inventories, nested items, ownership, saved entities and full restarts. The eight tested routes pass with the repaired 1.12 bridge. Use Electric Advantage `2.2.3.112021` for the current turbine and drill fixes; the original `2.2.1.112021` jar remains a failing historical control.

The [additional-dimension protocol](docs/1.13/DIMENSIONS-AND-ENTITY-LIFECYCLE.md)
covers Nether/End player and entity fixtures, native passenger/leash relationships
and separate death/respawn controls. The [transformer-order report](docs/1.13/TRANSFORMER-ORDERING.md)
qualifies both BaseMetals/Advantage application orders and records the
order-dependent provenance of loose-file backups.

Networks use loaded chunks only. Missing conduit sections interrupt distribution;
normal chunk loading reconnects them without replacing pipes. The same protocol
records acquisition-stack diagnostics and pipe-only bridge regressions.

The historical 1.10 handbook remains on the [1.10.2 branch](https://github.com/MinecraftModDevelopmentMods/PowerAdvantage/blob/master-1.10.2/docs/1.10/README.md).
