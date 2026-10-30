# PowerAdvantage

**Minecraft 1.16.5, version 2.4.7.116051. Source port candidate.**

See the [1.16 port record](docs/1.16/PORT-STATUS.md) for current checks and remaining
work. The accepted [1.15 test record](docs/1.15/CURRENT-QUALIFICATION.md) is retained
as historical evidence, not a pass for this version. Manual 1.16 acceptance is
still required.

The [test-suite review](docs/1.16/TEST-SUITE-AUDIT.md) separates operating checks
from saved-state and presence checks. It records the remaining machine coverage
gaps; a green build does not qualify every machine setup.

The follow-up [machine operating checks](docs/1.16/OPERATING-TESTS.md) use finite
inputs, real server ticks, blocked and starved controls, and full-process
restarts. They check useful output and retained resources, not just animation
or a rising progress counter.

Power Advantage provides shared power, fluids, item transport and compatibility APIs for Steam Advantage and Electric Advantage.

**Back up the entire world before upgrading. Test only disposable copies.**
The port retains existing registry names and legacy migration code. Upgrade tests
use disposable original and maintained saves, including connected machines.
Check every other mod's target-version support too.
The accepted [1.14 results](docs/1.14/BROADER-QUALIFICATION.md) are historical
evidence, not qualification of this port or a whole modpack.

Recipe-book discovery now follows relevant ingredients. See the [all-Advantage material checklist and automated discovery tests](docs/1.13/RECIPE-DISCOVERY.md). To regenerate the checked-in unlock JSON after changing recipes, run `node tools/generate-recipe-unlocks.js`; `check` rejects missing or stale ingredient criteria.

The [manufacturing progression audit](docs/1.13/MANUFACTURING-PROGRESSION.md) traces survival parts from raw resources, including configured material aliases, processing, loot-only parts and automated restart checks.

## Building Minecraft 1.16.5

The `master-1.16.5` build uses Forge 36.2.34, official `1.16.5` mappings,
MCP `20210115.111550`, ForgeGradle 7.0.34 and Gradle 9.6.1.
Run Gradle with Java 17 and compile production code with Java 8. Mavenizer also
needs Java 25. CI pins all three JDKs. Pass the installed homes using
`-Porg.gradle.java.installations.paths=<java8>,<java17>,<java25>` and disable
automatic discovery and downloads for a reproducibility check.

Build the pinned Base Metals `3.1.1.116051` and OreSpawn `4.1.0.116051` deobf jars first.
The exact Base Metals source is
`995f6cc4dd2d5eecc628a42e5c43fa5390c24bca`. CI builds that dependency first;
the checksums and complete source pins are in `gradle.properties`.

Then run from `D:\PowerAdvantage\PA116\PowerAdvantage`:

```text
gradlew.bat clean check build javadoc verifyReleaseDependencies verifyReleaseArtifacts writeReleaseChecksums verifyEclipseProductionClasspath
```

The default dependency jars are:

```text
D:\BaseMetals\BaseMetals 116\BaseMetals\build\libs-dev\BaseMetals-3.1.1.116051-deobf.jar
D:\MinecraftMineralogy\OS116\OreSpawn\build\libs-dev\OreSpawn-4.1.0.116051-deobf.jar
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

To prepare a combined client, build the Steam/Electric `2.2.8.116051` deobf jars,
run `gradlew.bat prepareCombinedEclipseLaunch`, refresh Eclipse, then launch
`runClientAllAdvantage.launch`. Inputs must match
`test-rig/profiles/native116/dev-core.json`; alternative layouts can pass
`-PsteamAdvantageDeobfJar=<path>` and `-PelectricAdvantageDeobfJar=<path>`.
The ordinary `runClient.launch` remains Power-only. Both use the `run` directory;
preparing a launch does not copy, open or qualify a save.

The local GUI corrections have separate input hashes, keeping the earlier machine
test profiles unchanged. To prepare that combined preview, run
`gradlew.bat prepareCombinedEclipseLaunch -PadvantageDevelopmentProfile=docs/1.16/gui-development-inputs.json`.
See the [GUI checks](docs/1.16/GUI-CHECKS.md) for coverage and the repeatable command.

`check` and Eclipse preparation also run `verifyDevelopmentRuntimeArchives`,
which reads every development runtime jar entry and checks its size and CRC.
This catches damaged cached dependencies before the next test launch.

Client-resource and recipe-book checks must use the exact 1.16 candidate. Manual
visual and interaction acceptance remains separate.

The migration test fixtures do not import restricted JDK internals. A plain ECJ
batch compile does not apply Eclipse's JRE access rules automatically. After
`eclipseClasspath` and `test`, run `tools/verification/Verify-EclipseCompilation.ps1`
with `-Compiler <ecj.jar>`, `-Java17Home <jdk17>`, `-Java8Home <jdk8>` and
`-Evidence <new-directory>` to compile fresh main/test classes with restricted
references treated as errors, then run the tests on Java 8. Keep Eclipse's
access-restriction checks enabled.

## Compatibility

The implementation namespace remains `zone.moddev.mc.poweradvantage`, with the
historical `cyano.poweradvantage.api` package retained. The port keeps registry,
fluid, tile, NBT and provider identities. The current test record distinguishes
machine preservation, actual operation and vanilla migration differences.

The upgrade path prepares stored items before vanilla flattening and converts
entities individually, so a mod entity cannot prevent a nearby pet's UUID and
ownership from being migrated. Old vanilla registry names are remapped explicitly;
unknown mod mappings still require their own compatible mod rather than being
silently discarded.
Legacy Forge swimming speed, name-tag distance, gravity and player reach use
their native registry names without discarding saved values or modifiers.

The 1.16 port uses native dimension registry keys, tile save/load methods and
matrix-based menus. Config-dependent material aliases are resolved during the
server's native tag load and synchronized to clients. The shared pre-flattening
migration hook remains necessary; its presence is not proof that an upgrade is safe.

The [legacy migration guide](docs/1.13/MIGRATION.md) and
[Advantage Works rig](test-rig/README.md) describe the earlier protocols.
The [1.16 status](docs/1.16/PORT-STATUS.md) tracks this port's qualification.

The historical 1.10 handbook remains on the [1.10.2 branch](https://github.com/MinecraftModDevelopmentMods/PowerAdvantage/blob/master-1.10.2/docs/1.10/README.md).
