# Native Client Resource Check

## Stronger GUI Backport

The new `-MachineGuiLayouts` option draws every registered machine screen
through the native 1.15 container renderer. It checks literal English equipment
names and `Inventory`, separate caption rows, slot alignment, a real item hover,
and empty/half/full readings at three viewports. The combined profile requires
39 screens, 351 caption cases and 355 deliberately broken-label rejections.
The expected captions come from the separately reviewed
`resources/gui-label-expectations.json`, not from production label output.

The new probe first rejected the existing 1.15 production screens in a native
client run. The caption/slot/tooltip and finite-display corrections are now
staged for green compilation and a fresh native client run. Static checks are
not native graphical acceptance. No previous 1.15 or 1.16 result qualifies the
corrected caption contract.
Build and run only through the coordinated validation lease:

```powershell
./gradlew.bat clientResourceProbeJar
./test-rig/client-resources/run.ps1 -JavaHome '<Java 8 home>' -LaunchProfile runClientAllAdvantage.launch -RequireExtensions -MachineGuiLayouts -TimeoutSeconds 600
```

The following checks start no Java process and open no world:

```powershell
./test-rig/client-resources/test-gui-contract.ps1
./test-rig/client-resources/test-gui-contract-negative-controls.ps1
./test-rig/client-resources/test-probe-contract-negative-controls.ps1
```

The GUI contract script pins the stronger 1.16 oracle and permits only the
documented 1.15 API spellings. Both the missing-label design and older
bounds-only reports are rejected. The client runner snapshots mutable
development dependencies before starting, and retains failed images/results.

## Earlier Resource Qualification

This build-only Minecraft 1.15.2 / Forge 31.2.57 probe checks PowerAdvantage, or all three Advantage mods in the combined profile, after Minecraft bakes its models and stitches its textures. It targets the actual mapped snapshot `20200514-1.15.1` jar with MCP `20200515.085601`. It registers no gameplay content, opens no world, and shuts down its own disposable client when finished. It is not included in release jars or ordinary Eclipse launches.

Compilation and the full combined native graphical run passed on 2026-10-26,
including fresh 1.15 recipe fixtures and all 25 worldless renderer-geometry cases.
Hydroturbine orientation/spin and all 1.15 manual acceptance remain pending.
See [the native 1.15 checks and evidence](../../docs/1.15/CLIENT-RESOURCES.md).
Use matching native jars/hashes. Run Gradle only when the shared cache is free
of other builds; start the client only when authorized.

For native recipe-book grouping, supply a **new native 1.15.2 server export**
containing `recipes-{normal,tech,apocalyptic}-sweep.bin`, their matching successful
JSON reports with `bookRecipes`, and `AdvantageWorksMigration/level.dat`.
The copied convenience `migration/recipe_unlocks.py` still targets 1.13.2; do
not use its outputs or historical qualification as 1.15.2 evidence. Native
fixture generation uses the separate server harness.
The separate native campaign retains the five unlock/persistence boots and all
18 progression checks. It reads recipes and advancements from the packaged
candidate jars, validates target hashes, creates new loopback runtimes only,
and fingerprints the installed server seed before and after. It needs an
existing Python 3.11+ environment with `migration/requirements.txt`, plus Java 8;
it does not invoke Gradle:

```powershell
<python.exe> -B test-rig/migration/native115_recipes.py --target-manifest '<native115 target manifest>' --java8 '<Java 8 home>/bin/java.exe' --port 25577 --output test-rig/build/native115-recipes-new
```

`--campaign unlock` or `--campaign progression` selects a single campaign.
Native vanilla logs saved recipe IDs removed by a recipe-mode change at ERROR
level. The unlock audit checks the exact set against the difference between
the preceding and current actual recipe packets, retains all log events, and
fails on any unrelated error or a mismatched removal set. It never erases a
saved book to avoid that persistence check.

After native exports are available, pass the campaign's `unlock` subdirectory:

```powershell
./test-rig/client-resources/run.ps1 -JavaHome '<Java 8 home>' -LaunchProfile runClientAllAdvantage.launch -RequireExtensions -RecipeBookFixtures '<new native115 fixture directory>/unlock'
```

The server exports its actual recipe packets and matching metadata. The client
imports the generated world's Forge registry snapshot before decoding those
packets, reproducing the item-ID synchronization normally supplied by Forge's
connection handshake. The saved header must identify Minecraft 1.15.2 and
Forge 31.2.57; a legacy fixture is rejected. It then rebuilds native
`ClientRecipeBook`/`RecipeList`
cells for Normal, Tech and Apocalyptic modes: each cell must contain exactly one
output identity, all loaded variants must remain accessible, and same-output
alternatives must not split into extra cells. A framebuffer image,
`recipe-book-cells.png`, renders the actual Minecraft recipe buttons for the
four electronics and nine conveyor/filter outputs reported hidden by users.
The probe reads only the disposable fixture's saved registry data; it never
opens that world or a user's world in Minecraft. This is a native cell-model and
button-rendering check, not manual navigation or notification acceptance.

The static checks do not require artifacts, Gradle, Java or a graphical client:

```powershell
./test-rig/client-resources/test-launcher.ps1 -StaticOnly
./test-rig/client-resources/test-probe-contract.ps1
```

Before running Gradle, check that the shared `D:/GradleCaches/minecraft-mods-shared`
cache is free of other builds. Use the repository's Gradle 9.6.1 / ForgeGradle 7 setup with Java 17
for Gradle and the configured Java 8 compilation toolchain. Supply Java 8 to
the disposable launcher. Do not launch the actual client until authorized:

```powershell
./gradlew.bat check clientResourceProbeJar verifyEclipseProductionClasspath
./gradlew.bat prepareCombinedEclipseLaunch
./test-rig/client-resources/test-launcher.ps1
./test-rig/client-resources/run.ps1 -JavaHome '<Java 8 home>' -LaunchProfile runClientAllAdvantage.launch -RequireExtensions -RecipeBookFixtures '<new native115 fixture directory>/unlock' -PreflightOnly
# Only after authorization for the graphical run:
./test-rig/client-resources/run.ps1 -JavaHome '<Java 8 home>' -LaunchProfile runClientAllAdvantage.launch -RequireExtensions -RecipeBookFixtures '<new native115 fixture directory>/unlock'
```

Each run gets a new directory under `run-client-resource-runs/`. The runner snapshots production classes/resources, uses the audited native launch dependencies, and loads only the probe as an additional mod. Helpers launch hidden, with separate stdout/stderr logs and a bounded timeout. It retains logs, images and `client-resources.json`; missing reports, failed assertions, and model/texture errors fail the run. A runner failure gets a separate `runner-failure.json`, without replacing partial native results. Existing saves, configuration, and prior reports are untouched. `-PreflightOnly` creates an input snapshot without starting Java or claiming a graphical pass.

New GUI captures are kept outside Gradle's `build/` directory so `clean` cannot remove them. This does not recreate any historical captures already lost from the former build-based location.

The default `-OutputSource Gradle` merges Gradle's classes and processed resources. To test the actual Eclipse output instead, pass `-OutputSource Eclipse`; this copies `bin/main` verbatim and does not overlay Gradle resources. `production-files.json` hashes every selected class/resource. `client-input.json` records that manifest's hash, the Java binary, probe, launch, production metadata, every dependency archive, expansion candidate hashes and optional fixture hashes. The current Power deobf candidate is recorded separately, defaulting to `build/libs-dev/PowerAdvantage-2.4.6.115021-deobf.jar`; `-PowerCandidate` selects an explicit native candidate. That jar hash is provenance, not a claim that Eclipse/Gradle output equals the jar. Missing/unexpanded metadata or a legacy target fails preflight. The native result records actual discovered mod versions and the exact game/Forge target. Refresh candidate hashes after any production rebuild before final evidence is taken. The [historical Eclipse metadata regression](../../docs/1.13/ECLIPSE-METADATA.md) explains the original failure, not native 1.15 acceptance.

For the combined run, use `gradlew.bat prepareCombinedEclipseLaunch`, then add `-LaunchProfile runClientAllAdvantage.launch -RequireExtensions` to the probe command. The report's `advantage_mods` must list all three mods. With this flag, resource and item-name assertions cover Power, Steam and Electric, and model/blockstate errors from any of their namespaces fail the run. Without it, the original Power-only scope is unchanged. Power must be `2.4.6.115021`; Steam and Electric must each be `2.2.7.115021`. Pin every rebuilt expansion jar in the native115 profiles before regenerating the combined launch. Recipe grouping requires that combined scope; without fixtures it explicitly reports `NOT_RUN_NO_NATIVE_FIXTURES`, not a full combined resource/recipe pass.

Assertions cover every registered item and valid block state in the selected Advantage namespaces, nonempty item/machine geometry, missing-model and missing-texture sprites, English item names, the shared creative tab and its icon, and agreement between Power's modern block and legacy GUI translation keys. Native fluid block models are intentionally particle-only. This test does not certify machine operation, animated entity rendering, or third-party resource packs.

The `ClientResourceContractTest` checks cover literal model paths, removed legacy blockstate selectors, model parents/textures (including vanilla assets), oil item models, locale aliases, and English block/bucket coverage. Their historical 1.13 red/green results do not qualify this destination.

Tank visual regressions use Minecraft's actual neighbour-face culling beneath both tanks, native Forge fluid capability simulation/execution, full 10,000 mB and empty tank update packets, and a native registered `ContainerType` screen initialized without a player/world. The actual filtered-tank gauge and frame render into an OpenGL framebuffer through the production GUI. The pixel at `(88,39)` must remain blue with the original thresholds (`blue > red + 20`, `blue > green + 10`); `tank-water-gauge.png` is written before assertions. Historical hidden-floor/untinted `ADADAD` observations remain 1.13 evidence only. No test-world loading or fluid-network changes are required.

Oil regressions verify both buckets through native `BucketItem.getFluid()` and Forge's fluid-item capability, then call the initialized native `BlockRendererDispatcher` / `FluidBlockRenderer` against a read-only synthetic `ILightReader`. Still and flowing crude/refined oil must emit vertices using their own atlas sprites and `FFFFFF` top-face tint. Vanilla water and lava remain controls. Forge 31 reads sprites/tint from `FluidAttributes`; no removed oil transformer or 1.13 hook is assumed. Completed vertices are inspected through `BufferBuilder.getNextBuffer()` and the actual native format element offsets; atlas lookup uses `getAtlasSpriteGetter(AtlasTexture.LOCATION_BLOCKS_TEXTURE)`. The probe temporarily supplies native vanilla water/lava tags at the main menu and restores them afterward; no production tags or saves are written. `fluid_rendering` retains case results. The historical water-sprite/`3F76E4` failure is not native 1.15 evidence.

The native recipe image still requires exactly thirteen separate cells and more
than 1,000 colored framebuffer pixels. A blank image is retained before failure.
Compilation and input preflight are not graphical acceptance, recipe discovery
acceptance, manual GUI navigation or server-to-client menu-handshake acceptance.

The optional `-ChildRendererGeometry` combined-run switch adds actual registered
Steam drill-bit and Electric beam/turret buffer checks after these primary
oracles. It does not alter their capture logic or thresholds. Hydroturbine
registration/texture are checked, but orientation/spin geometry remains an
explicit disposable-world gap. See [Additional Renderer Geometry](../../docs/1.15/CLIENT-RESOURCES.md#additional-renderer-geometry)
for scope, the static wiring test and the completed native run evidence.
