# Native Client Resource Check

This build-only Forge 1.13.2 probe checks PowerAdvantage, or all three Advantage mods in the combined profile, after Minecraft bakes its models and stitches its textures. It registers no gameplay content, opens no world, and shuts down its own disposable client when finished. It is not included in release jars or ordinary Eclipse launches.

For native recipe-book grouping, first complete the packaged `recipe_unlocks.py`
run, then pass its output directory:

```powershell
./test-rig/client-resources/run.ps1 -JavaHome '<Java 8 home>' -LaunchProfile runClientAllAdvantage.launch -RequireExtensions -RecipeBookFixtures 'run-recipe-unlocks/new-current'
```

The server exports its actual recipe packets and matching metadata. The client
imports the generated world's Forge registry snapshot before decoding those
packets, reproducing the item-ID synchronization normally supplied by Forge's
connection handshake. It then rebuilds native `RecipeBookClient`/`RecipeList`
cells for Normal, Tech and Apocalyptic modes: each cell must contain exactly one
output identity, all loaded variants must remain accessible, and same-output
alternatives must not split into extra cells. A framebuffer image,
`recipe-book-cells.png`, renders the actual Minecraft recipe buttons for the
four electronics and nine conveyor/filter outputs reported hidden by users.
The probe reads only the disposable fixture's saved registry data; it never
opens that world or a user's world in Minecraft. This is a native cell-model and
button-rendering check, not manual navigation or notification acceptance.

Run Gradle with Java 17, then supply a Java 8 runtime to the launcher:

```powershell
./gradlew.bat check clientResourceProbeJar verifyEclipseProductionClasspath
./test-rig/client-resources/run.ps1 -JavaHome '<Java 8 home>'
```

Each run gets a new directory under `build/client-resource-runs/`. The runner snapshots production classes/resources, uses the audited launch dependencies, and loads only the probe as an additional mod. It retains logs and `client-resources.json`; missing reports, failed assertions, and model/texture errors fail the run. Existing saves, configuration, and prior reports are untouched.

The default `-OutputSource Gradle` merges Gradle's classes and processed resources. To test the actual Eclipse output instead, pass `-OutputSource Eclipse`; this copies `bin/main` verbatim and does not overlay Gradle resources. `client-input.json` records the selected output, launch and metadata hash. Missing/unexpanded metadata fails the preflight before Minecraft opens, and the native result records the versions Forge actually discovers. The [Eclipse metadata regression](../../docs/1.13/ECLIPSE-METADATA.md) explains why this is a separate acceptance gate.

For coordinated startup, first run `gradlew.bat prepareCombinedEclipseLaunch`, then add `-LaunchProfile runClientAllAdvantage.launch -RequireExtensions` to the probe command. The report's `advantage_mods` must list all three mods. With this flag, resource and item-name assertions cover Power, Steam and Electric, and model/blockstate errors from any of their namespaces fail the run. Without it, the original Power-only scope is unchanged. Pin every rebuilt expansion jar in the development and packaged rig profiles before regenerating the combined launch.

Assertions cover every registered item and valid block state in the selected Advantage namespaces, nonempty item/machine geometry, missing-model and missing-texture sprites, English item names, the shared creative tab and its icon, and agreement between Power's modern block and legacy GUI translation keys. Native fluid block models are intentionally particle-only. This test does not certify machine operation, animated entity rendering, or third-party resource packs.

The six `ClientResourceContractTest` checks first failed against the pre-repair resources. They cover literal model paths, removed legacy blockstate selectors, model parents/textures (including vanilla assets), oil item models, locale aliases, and English block/bucket coverage.

Tank visual regressions also use the native client. They test Minecraft's actual neighbour-face culling beneath both tanks, synchronize a full 10,000 mB metal tank and then an empty tank, and render the filtered tank's gauge and frame into an OpenGL framebuffer. The water pixel must be blue; `tank-water-gauge.png` retains the rendered indicator. These checks reproduced hidden floor faces and an untinted `ADADAD` water pixel before the rendering repairs. No test-world loading or fluid-network changes are required for this focused check.

Oil regressions verify both native buckets' fluid identities, then run Minecraft's actual `BlockFluidRenderer` against a read-only synthetic neighbourhood. Still and flowing crude/refined oil must emit vertices using their own atlas sprites, without a biome water tint. Vanilla water and lava are control cases. The probe installs vanilla water/lava tag memberships explicitly because no server has synchronized tags at the main menu. It changes no production tags or saves. Before the repair all four oil cases used water sprites and the blue `3F76E4` tint; `fluid_rendering` in the JSON report retains the results. Separate Java 8 transformer tests cover mapped/SRG-style method names and preservation of sibling hook chains in either order; these structural checks do not certify every third-party renderer or resource pack.
