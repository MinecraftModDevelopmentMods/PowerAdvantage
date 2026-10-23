# Eclipse Mod Metadata Regression

## Failure: 22 October 2026

The combined Eclipse launch rejected PowerAdvantage as incompatible with Steam and Electric, despite the built jar having the correct version. The user's `run/logs/latest.log` loaded Power from `bin/main`; that output contained the literal `${version}`, `${loader_version_range}` and other placeholders from the source `META-INF/mods.toml` template.

Gradle had expanded the template correctly under `build/resources/main`. However, the Eclipse Java build path still listed `src/main/resources`, so an Eclipse build could copy the raw file over the processed copy previously staged in `bin/main`. The earlier disposable client probe merged Gradle outputs and therefore did not exercise this failure. No dependency version range needs weakening.

## Repair

All three projects now supply exactly one production resource source root to Eclipse: `build/resources/main`, with output `bin/main`. Raw resource roots are removed from both generated `.classpath` files and the Tooling API Eclipse model imported by Buildship. `eclipseClasspath` depends on `processResources`; existing Gradle synchronization tasks stage processed resources before launch preparation. The Eclipse audits reject raw resource roots and check the expanded mod ID/version in `bin/main`.

In Eclipse, use **Gradle > Refresh Gradle Project**, then **Project > Clean**, allow the build to finish, and relaunch `runClientAllAdvantage`. Resource/version edits require `processResources` or a Gradle refresh before the Eclipse build. Java classes remain compiled by Eclipse into `bin/main`; normal launches still exclude test code. No gameplay classes, world data, registry identities or dependency ranges were changed.

## Evidence

- `build/client-resource-runs/20261022-050744-ad86296d/` retains the failing Eclipse-output snapshot. The new preflight detects its unresolved template without opening a world or starting Minecraft.
- `build/client-resource-runs/20261022-051012-75105698/client-input.json` records `output_source: Eclipse`, the combined launch and the snapshotted metadata hash. No Gradle resources were overlaid on that snapshot.
- The corresponding `client-resources.json` reports actual Forge versions Power `2.4.4.113021`, Steam `2.2.5.113021` and Electric `2.2.5.113021`, plus PASS for all 94 items and 905 block states. Existing oil and tank rendering/synchronization checks also pass.
- `build/eclipse-tooling-models.log` records successful Gradle Tooling API Eclipse-model checks for all three projects: `build/resources/main -> bin/main`, with no raw production resource roots.
- All three generated Eclipse/classpath audits pass, and each `bin/main/META-INF/mods.toml` matches its processed counterpart byte-for-byte. This validates the metadata/resource configuration and actual Eclipse output, not an automated run of the Eclipse GUI itself.

## Repeat

Run Gradle with Java 17, then use Java 8 for the disposable client:

```powershell
./gradlew.bat check clientResourceProbeJar prepareCombinedEclipseLaunch
./test-rig/client-resources/run.ps1 -JavaHome '<Java 8 home>' -LaunchProfile runClientAllAdvantage.launch -RequireExtensions -OutputSource Eclipse
```

After a genuine Eclipse rebuild, repeat the second command without rerunning Gradle staging first; this checks what Eclipse actually left in `bin/main`. The probe copies that output and never opens the user's save.

The independent Buildship-model check uses the Gradle installation's Tooling API jars and Java 17 source-file mode:

```text
java --class-path "<Gradle home>/lib/*" test-rig/eclipse/EclipseModelProbe.java "<Gradle home>" "<Gradle user home>" "<Power checkout>" "<Steam checkout>" "<Electric checkout>"
```

Both tools are test-only and are excluded from mod artifacts and ordinary play-test launches. Reports and generated IDE files remain local and ignored.
