# PowerAdvantage

Power Advantage provides shared power, fluid, and item transport systems for its Minecraft 1.12.2 expansion mods.

## Building Minecraft 1.12.2

The `master-1.12.2` build uses ForgeGradle 7.0.34 and Gradle 9.6.1. Run Gradle with Java 17; Gradle resolves the Java 8 toolchain used for compilation.

```text
gradlew.bat clean check build verifyReleaseDependencies verifyReleaseArtifacts writeReleaseChecksums
```

Release jars are written to `build/libs`. The deobfuscated development jar for sibling mod compilation is written to `build/libs-dev` by `deobfJar` or `build`.

For Eclipse, import the repository as an existing Gradle project and run:

```text
gradlew.bat cleanEclipse verifyEclipseProductionClasspath
```

Base Metals and OreSpawn remain required distribution dependencies but are deliberately absent from the compile classpath. Optional RF and RebornCore integrations compile against SHA-256-pinned API inputs and are not bundled or added to normal development launches. CI stages the exact historical RebornCore Maven release because the later CurseForge archive with the same version has different contents.
