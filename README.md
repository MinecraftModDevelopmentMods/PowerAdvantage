# PowerAdvantage

Power Advantage provides shared power, fluid, and item transport systems for its Minecraft 1.10.2 expansion mods.

The [test coverage backport notes](TEST-COVERAGE-BACKPORT.md#final-recorded-candidate) record the final machine, restart, control, partial-network, GUI, endurance, connected-world and reproducibility results alongside exact candidate hashes. The bounded legacy checks pass; the recipe progression graph is still partial. Earlier failures, modern follow-up findings and manual-testing limits are kept in the notes. This is not an all-machines or all-worlds guarantee.

The maintained implementation namespace is `zone.moddev.mc.poweradvantage`. The legacy
`cyano.poweradvantage.api` package remains the supported add-on API.

External fluid producers can offer fluid to an adjacent Power Advantage pipe through
`cyano.poweradvantage.api.fluid.FluidNetworkApi.offerFluid(...)`. The method reports the exact
accepted amount without draining the caller's tank, allowing optional integrations to remain in
control of their own fluid state.

The [Advantage Works 1.10.2 Handbook](docs/1.10/README.md) documents every machine and component across Power, Steam, and Electric Advantage, including commissioning layouts and known investigation points. The [MMD material and fluid compatibility specification](docs/1.10/mmd-material-fluid-compatibility.md) defines OreSpawn ownership, legacy migration, crude-oil aliases, and Ore Dictionary names.

The source-controlled [Advantage Works test rig](test-rig/README.md) turns those commissioning cards into a disposable, walkable Forge server world with repeatable save/restart checkpoints. It is a separate test mod and is never packaged with PowerAdvantage.

## Building Minecraft 1.10.2

The rig profiles use Steam and Electric Advantage `2.2.5.110021`, which retain unfinished
drill work after a restart. This profile update does not change Power Advantage gameplay or its version.

The `master-1.10.2` build uses ForgeGradle 7.0.34 and Gradle 9.6.1. Run Gradle with Java 17; Gradle resolves the Java 8 toolchain used for compilation.

Build the pinned OreSpawn commit `5a50df1158e948c8db55814e819090b34c12d765` first, or pass its deobf jar with `-PoreSpawnDeobfJar=<path>`.

```text
gradlew.bat clean check build verifyReleaseDependencies verifyReleaseArtifacts writeReleaseChecksums
```

Release jars are written to `build/libs`. The deobfuscated development jar for sibling mod compilation is written to `build/libs-dev` by `deobfJar` or `build`.

For Eclipse, import the repository as an existing Gradle project and run:

```text
gradlew.bat cleanEclipse verifyEclipseProductionClasspath
```

Base Metals remains a required distribution dependency and is absent from the compile classpath. OreSpawn is a required runtime dependency whose pinned public API is used at compile time; it is never bundled. Optional RF and RebornCore integrations compile against pinned API inputs and are not bundled.
