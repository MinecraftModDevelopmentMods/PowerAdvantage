# PowerAdvantage

**Minecraft 1.15.2**

Power Advantage 2.4.6.115021 has passed its local build/audit suite, 60 unit tests,
Eclipse checks and a packaged new-world startup/save/shutdown smoke test.
Machine operation, GUIs, migration and broader runtime checks are still pending.
See the [1.15 port status](docs/1.15/PORT-STATUS.md) for tested jars and limits.

Power Advantage provides shared power, fluids, item transport and compatibility APIs for Steam Advantage and Electric Advantage.

**Back up the entire world before upgrading. Test only disposable copies.**
The port retains existing registry names and legacy migration code, but migration
has not yet passed on 1.15. Check every other mod's target-version support too.
The accepted [1.14 results](docs/1.14/BROADER-QUALIFICATION.md) are historical
evidence, not qualification of this port or a whole modpack.

Recipe-book discovery now follows relevant ingredients. See the [all-Advantage material checklist and automated discovery tests](docs/1.13/RECIPE-DISCOVERY.md). To regenerate the checked-in unlock JSON after changing recipes, run `node tools/generate-recipe-unlocks.js`; `check` rejects missing or stale ingredient criteria.

The [manufacturing progression audit](docs/1.13/MANUFACTURING-PROGRESSION.md) traces survival parts from raw resources, including configured material aliases, processing, loot-only parts and automated restart checks.

## Building Minecraft 1.15.2

The `master-1.15.2` build uses Forge 31.2.57, snapshot mappings
`20200514-1.15.1`, MCP `20200515.085601`, ForgeGradle 7.0.34 and Gradle 9.6.1.
Run Gradle with Java 17; Gradle resolves the Java 8 production toolchain.

Build the pinned Base Metals `3.0.1.115021` and OreSpawn `4.1.1.115021` deobf jars,
then run from `D:\PowerAdvantage\PA115\PowerAdvantage`:

```text
gradlew.bat clean check build javadoc verifyReleaseDependencies verifyReleaseArtifacts writeReleaseChecksums verifyEclipseProductionClasspath
```

The default dependency jars are:

```text
D:\BaseMetals\BaseMetals 115\BaseMetals\build\libs-dev\BaseMetals-3.0.1.115021-deobf.jar
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

Client-resource, recipe-book and manual visual checks must pass again on 1.15.
Their 1.14 results do not establish a pass here.

## Compatibility

The implementation namespace remains `zone.moddev.mc.poweradvantage`, with the
historical `cyano.poweradvantage.api` package retained. The port keeps registry,
fluid, tile, NBT and provider identities; that intent still needs runtime and
migration verification on 1.15.

The 1.15 changes use native activation results, non-solid block properties,
texture-atlas access and client render-layer registration. The shared migration
hook is retargeted to 1.15; its presence is not proof that an upgrade is safe.

The [legacy migration guide](docs/1.13/MIGRATION.md) and
[Advantage Works rig](test-rig/README.md) describe the earlier protocols.
Only the [1.15 status](docs/1.15/PORT-STATUS.md) records current qualification.

The historical 1.10 handbook remains on the [1.10.2 branch](https://github.com/MinecraftModDevelopmentMods/PowerAdvantage/blob/master-1.10.2/docs/1.10/README.md).
