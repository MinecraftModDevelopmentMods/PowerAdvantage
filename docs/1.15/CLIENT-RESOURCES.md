# Native 1.15 Client Resources

## Status

Candidate F passed the actual combined graphical probe on 2026-10-26,
using fresh F server recipe packets for all three modes. The primary resource,
model, tank, fluid and recipe checks passed, along with all 25 available
worldless renderer geometry cases. The existing captures and pixel thresholds
are unchanged. All 1.15 manual acceptance remains pending, including
hydroturbine orientation/spin in a disposable client world.

Fresh Eclipse Compiler for Java (ECJ) compilation of production and test sources
passed for all three projects. Tests executed from those separate ECJ outputs
also passed: Power 66, Steam 68 and Electric 45. These are separate gates from
the graphical results below. The accepted 1.14 behavior remains the reference,
not a substitute for native 1.15 evidence.

Candidate F uses Base Metals `3.0.2.115021`. Its full native recipe campaign,
combined graphical probe and fresh ECJ checks passed. The earlier no-fixture F
run and complete D evidence remain retained separately; neither substitutes for
the fresh F recipe exports used by the full run.

| Target | Required Value |
| --- | --- |
| Minecraft / Forge | `1.15.2` / `31.2.57` |
| Mappings / MCP | `snapshot_20200514-1.15.1` / `20200515.085601` |
| Power | `2.4.6.115021` |
| Steam / Electric | each `2.2.7.115021` |
| Runtime / build | Java 8 client; repository Java 17 Gradle process and Java 8 compilation toolchain |
| Probe resource pack | format `5` |

## Candidate F

The F packaged manifest is
`test-rig/build/native115-inputs-20261026-final-f-archived.json`, SHA-256
`A9BCD5A442F6332010F9D467BD00A685AA953339C39545812B32B825B96E64AA`.
Base Metals `3.0.2.115021` is pinned to source
`ac1781e7cade15045ffe0135077087b4d1722603`, packaged SHA-256
`3F50AFBEF17800ECADE42A69B81E541E9FA8C6C4C392ED4DD0FE5A66DC9EFF07`
and development SHA-256
`426FE639C97E1DE4F6624957E3CBDE68A5FB7D415A1047903A29DCB35FD9ADA2`.

The dependency-only Advantage commits are:

| Project | Local Source | Full Release-Gate Tests |
| --- | --- | --- |
| Power | `6666099a734eba16561d98c86f3f4bb1b7c742f7` | 66 PASS |
| Steam | `07f6a0503c57df0b51f7ac4370c0ff5eda0232f5` | 68 PASS |
| Electric | `58bb1a78d7a218779cf92a866db575f72a577546` | 45 PASS |

Each project completed one private-cache full release/dependency/artifact,
Javadoc and Eclipse verification run, without `clean` or a server/client launch.
All twelve Advantage release/development/source/Javadoc jars are byte-identical
to their previous versions. The full hashes, comparisons, logs and retained test
XML are under `build/base115-pin-propagation-20261026/`; its
`release-results.json` records the combined result. Base and Power dependency
commits are local-only and unpublished. Publish the exact dependency sources
upstream before PR/hosted builds; local success does not establish hosted
reproducibility or publication readiness.

`clientResourceProbeJar verifyEclipseProductionClasspath prepareCombinedEclipseLaunch`
then passed using only the private Gradle cache. The log is
`build/client-resource-compile-runs/final-f-prepare-20261026-143241.log`.
The unchanged probe and hash-addressed Steam/Electric jars were retained.
All 38 native launcher, 95 primary-preservation and 40 geometry wiring checks
passed again without changing thresholds or masks.

| F Evidence | Location |
| --- | --- |
| Preflight, no Java started | `build/client-resource-runs/20261026-143623-7ee23754` |
| Actual resource/geometry run, no recipe fixtures | `build/client-resource-runs/20261026-143712-f1377150` |
| Earlier no-fixture input verification | `build/client-resource-compile-runs/final-f-base-client-provenance.json` |
| Full-fixture preflight, no Java started | `build/client-resource-runs/20261026-145925-0c2bf4e9` |
| Actual full recipe/resource/geometry run | `build/client-resource-runs/20261026-150022-5694d3ab` |
| Full runtime/input verification | `build/client-resource-compile-runs/final-f-full-client-provenance.json` |
| Fresh native recipe campaign | `test-rig/build/native115-recipes-20261026-f/native115-recipes-results.json` |
| Final recipe fixture audit | `build/client-resource-compile-runs/final-f-recipe-fixtures-final-audit.json` |
| Fresh ECJ main/test compilation and JUnit | `build/ecj-final-f/20261026-143452` |

Both F graphical runs passed 94 item models/names, 905 block states, 87 creative
entries, both tank-floor faces, tank simulation/10,000 mB updates, actual gauge
pixel `1E386E`, all six fluid cases and all 25 worldless renderer geometry cases.
All 537 selected Power production files matched its development jar exactly.
The full report SHA-256 is
`18BE5F772EDBCDB17A1F33E68C7B2DB2F025D2EBEEAD625BA02695A84C1E87C8`.
The gauge, recipe image and geometry report are byte-identical to D's retained
outputs, despite using newly generated F packets and the F registry snapshot.

The fresh F native campaign completed five unlock boots and 18 progression
checks across all three modes, both rubber-alias settings, reload and restart.
All ten unlock operations passed 109 discovery cases and 59 potato-crafting
checks each, with zero skips; the installed seed fingerprint was unchanged.
The campaign report SHA-256 is
`F18F848945B26A9633775855108B6E8E103D749D14A6F97B768FB9717F64CD59`.
Before graphical use, all six sweep packet/report hashes and the registry
snapshot were checked against this campaign's fixture manifest. The native saved
mod versions and six packaged candidates matched F. The registry snapshot has
SHA-256 `36A2357A5EB172689D6B4525D89DFD276C3FDF541A0F0E053A516EE9BC720708`.
No D packet or registry snapshot was reused.

| F Recipe Mode | Recipes | Native Cells | Result |
| --- | --- | --- | --- |
| Normal | 93 | 77 | PASS |
| Tech | 87 | 77 | PASS |
| Apocalyptic | 82 | 73 | PASS |

The full run reports `recipe_book_grouping_status=PASS`. Its actual thirteen-cell
image contains 8,585 qualifying pixels against the unchanged `> 1,000` assertion
in the original `1000 x 180` capture. Both recipe and tank images were visually
inspected. Their SHA-256 values are
`C0868043B7C356BBFF80825662562F0AA2C0BB5C822DD36DEC2B1D71BFE3100D`
and `7D3A9295A75EDC67442A06B300A3077E37EE4335AAA94D48F65E22E14CE5A1A6`.
The earlier F `NOT_RUN_NO_NATIVE_FIXTURES` result remains explicitly partial,
not relabelled as the full pass.

The hydroturbine remains `MANUAL_GAP_WORLD_REQUIRED`; all normal gameplay/manual
acceptance limits still apply. The graphical probe opened no integrated server
or existing save. All 216 files in the D manual runtime and 175 retained D recipe
inputs were fingerprinted unchanged. The separate F manual copy and its source
also remain unchanged; only `-CheckOnly` was run on that copy. The recipe server
was stopped after the campaign, with no listener remaining on port 25577.

Fresh ECJ compilation used the actual Base 3.0.2 development jar for all three
projects, separate main/test output trees and Java 8 bytecode. All six compiles
had zero errors; main/test warning counts remain Power 97/1, Steam 40/0 and
Electric 52/0. Java 8 JUnit execution from those fresh ECJ outputs also passed
66/68/45 tests. `ecj-results.json` and `ecj-junit-results.json` retain source refs,
source/dependency hashes, commands and results. These are compile/test checks,
not an interactive Eclipse client launch.

The separate private-cache harness gate also passed all 95 tests and
`assemble verifyHarnessIsolation verifyWorksNative115Inputs` against the current
F profile, without `clean` or a Minecraft launch. Its report is
`build/client-resource-compile-runs/final-f-harness-online-20261026-144952/harness-results.json`.
Harness E remains SHA-256
`C854AFC08344C9B5E53AC8B35F9AA0B3E3F60E8180BF1C5F40AC7B5DF594890B`
and its runner remains
`21DF61B5501D3E5E0EB10394773C5D86C5AFCEB478AE09426AA8B980F267C361`.
The first offline attempt stopped before compilation because Mixin 0.8.2 was
not cached; that failure log is retained separately.

The disposable F manual copy is prepared, not launched, from the stopped passed
F `packaged-current` runtime. Its 29 restart guards and final `-CheckOnly` passed;
the complete source and D manual fingerprints are unchanged. See
[Manual Testing](MANUAL-TESTING.md) for its path, checksums and launch limits.

## Retained D Inputs

The immutable packaged target manifest is
`test-rig/build/native115-inputs-20261026-final-d-archived.json`, SHA-256
`3605B9BBF2B5C94C8278B31B5984969DD55B8F277E3B994496F045FA7CA79BE8`.
The native server exported the recipe fixtures with these packaged candidates:

| Candidate | Production Source | Packaged SHA-256 |
| --- | --- | --- |
| Power | `75d24d2641e8123ff7a7f961d9a1e5a1d5203194` | `F86454B9B5282A195F5C8FC58BEDEDB751DE3EB55039A9AC3A9A8495B1C0293C` |
| Steam | `7883d9b697a7c0961c721f01baa6ed1046d256de` | `564F730D3D0C79A0C73776120D40D634C668B7C7A8E2E85350A8C753FE64C3D2` |
| Electric | `f39a7f8f2f09727cd8dec1a1ebc553d52d710404` | `C4084B709221C9D9E9175B342B692C3CF4278790EEA1AA88FFBBF8150354249E` |

The client uses the mapped development inputs, not packaged production jars:

| Input | Development SHA-256 |
| --- | --- |
| Power | `F5029356CB837E7D782BA59D4C3B961CE0AAA116B739F14DA63F0A13562A2D1F` |
| Steam | `CB02BE3E9CCD1E4498000503D825B2B6FD350EBDF37A6414EE23697A22BE925E` |
| Electric | `7CF50EA6FBEB30F1683359EFF5C541B6674A30D12AA4C7D0B236CE5E52D9D7DF` |
| BaseMetals | `A98EAC370D908F14103DB6D064C738160161FDD7203F30905562B23EEE265210` |
| OreSpawn | `358B13375A4B60622C491D6729290971A446FF14D524A600CA13710934DB3FDC` |

Steam and Electric were loaded from their hash-addressed D archives. Power
was loaded from a snapshot of Gradle's production output: all 537 selected
class/resource files matched its D development jar byte-for-byte. The launcher
records that jar as provenance, not as a second installed Power mod. The server
fixture used harness C, packaged SHA-256
`B8F733F37B51A2EE8A0C11F21C1C84336FD8FCCDE998611DA334021C29CC34FA`;
the harness is not installed in the client.

## Native API Adaptation

The installed mapped Forge source archive under
`.gradle/mavenizer/repo/net/minecraftforge/forge/1.15.2-31.2.57/`
was inspected directly. Its `forge-1.15.2-31.2.57-sources-snapshot-20200514-1.15.1.jar`
SHA-256 is `937F0B7D7098D4D1A12EA306108122B1E443CA638D120D9040FF2B577E9A0314`.
The API names below use this snapshot. Official-mapping names, including those
in IronAgeFurniture's 1.15 client probe, are not interchangeable with them.

- Fluid rendering uses `ILightReader` and the native dispatcher with a real
  `BufferBuilder`/`IVertexBuilder`. Its tiny read-only neighbourhood provides
  full light and plains biome colors without a world or light-manager instance.
- `finishDrawing()` resets the builder's vertex count in 1.15. Read the completed
  `DrawState` and byte slice from `getNextBuffer()` instead. UV0/color offsets
  come from the actual `VertexFormatElement` usage/index and Forge `getOffset()`.
- Sprite lookup uses the block atlas's `getAtlasSpriteGetter()` function.
- Baked face sprites use the snapshot's `BakedQuad.func_187508_a()` accessor.
  The missing-face-texture check is unchanged.
- Tank and recipe framebuffer state/readback use `RenderSystem`. The native
  container/screen, recipe manager, recipe lists and actual recipe widgets remain.
- Launch guards require the exact game, Forge, snapshot and MCP target, reject
  1.13/1.14 archives and unsafe world/server arguments, and require the stated
  Power/Steam/Electric versions. Probe metadata also requires the exact target.

The probe uses the production render layers, fluid attributes, tank shapes,
GUI atlas/UV handling and blit offsets. It does not replace those renderers.

## Retained Assertions

- Every registered item and valid block state in all three Advantage namespaces:
  English item translations, baked geometry, missing model/particle/face sprites,
  shared creative tab entries/icon, and modern/legacy still-name agreement.
  Native fluid block models remain intentionally particle-only.
- Both portable and metal tanks must leave Minecraft's actual grass-floor top
  face visible beneath their inset corners.
- Native Forge tank fill simulation must leave the tank empty. Executed fill
  and drain remain 10,000 mB. Full and empty production update packets must
  reproduce the corresponding client tank contents.
- The actual filtered-tank GUI uses its registered native container type and
  initialized screen. Production decorations draw water and frame into the
  original `128 x 96` framebuffer. The image is written before checking pixel
  `(88,39)`: `blue > red + 20` and `blue > green + 10`, unchanged.
- Crude/refined buckets retain native bucket, filled-bucket and capability
  identities with 1,000 mB contents. Still and flowing crude/refined oil retain
  their four exact `poweradvantage:blocks/*` sprites and `FFFFFF` top-face tint.
  Native water/lava remain controls. Top-face output requires at least four
  vertices; UV bounds are unchanged. Temporary water/lava tags are restored.
- New native server packets for Normal, Tech and Apocalyptic retain complete
  packet consumption, successful manifest, unique recipe IDs, output/count,
  group/type, recipe count, one-output-per-cell and accessible-variant checks.
  Grouping must not split same-output alternatives or merge different outputs.
- The same four electronics plus conveyor/eight filters remain exactly thirteen
  distinct native recipe cells. Actual widgets and translated labels render to
  the same `1000 x 180` framebuffer, with the same background, spacing, projection
  and color test. `recipe-book-cells.png` is retained before requiring more than
  1,000 colored pixels. No tolerance or capture-coordinate changes were made.

## Static Verification

From the Power 1.15 checkout, without Gradle, Java or graphical execution:

```powershell
./test-rig/client-resources/test-launcher.ps1 -StaticOnly
./test-rig/client-resources/test-probe-contract.ps1
```

Results: 33 launcher checks and 95 preservation checks passed. By default, the
preservation test finds `PA114/PowerAdvantage/test-rig/client-resources` beside
the `PA115` directory, relative to the script rather than the current directory.
For another workspace layout, pass the accepted source directory explicitly:

```powershell
./test-rig/client-resources/test-probe-contract.ps1 -ReferenceRoot '<accepted PA114 client-resources directory>'
```

Each reference file must match its pinned SHA-256 before comparison. A different
path does not permit a different baseline. The pins cover the original file
bytes, before line-ending or native API/version normalization:

| Reference File | SHA-256 |
| --- | --- |
| `ClientResourceProbe.java` | `A6003A2EE76006C3BA3E279EC49E49DA69FD0A9B9146C7D0328288A85E173E02` |
| `TankVisualProbe.java` | `1256340A70F53F5B0D2C9B824DF5C9C6E2F0DFFAD1AFDB4EA560FE03B30137F4` |
| `OilRenderProbe.java` | `DF697A6BE69453F4956C25756110F2BB51B2DE78D95F4BF79A7612B49F18B1BA` |
| `RecipeBookGroupingProbe.java` | `9D842426742BEC9B6E0BEB1C763CAF1CF0D9C96F006A53796459B4B92BA1F4F5` |

The checks compare every original assertion and resource/fixture diagnostic literal, plus the
complete tank and recipe implementations after only native API/version
normalization. Bucket checks, all six fluid cases, tag restoration and separate
failure evidence are checked too. These are static regression checks, not a
substitute for Java compilation or actual image results.

## Verified Runs

`clientResourceProbeJar verifyEclipseProductionClasspath prepareCombinedEclipseLaunch`
passed for D. Steam and Electric also passed their own
`verifyEclipseProductionClasspath` tasks. All three native Eclipse targets were
regenerated without cleaning or replacing existing output classes. The probe's
nine class files use Java 8 bytecode (major version 52), including the
renderer-geometry probe. The native baked-quad accessor fix remains as described
above.

The build logs are under `build/client-resource-compile-runs/`:
`final-d-prepare-20261026-130532.log`,
`final-d-steam-eclipse-20261026-130711.log` and
`final-d-electric-eclipse-20261026-130728.log`. Earlier successful and failed
invocation/toolchain/compile logs remain there too.

The combined launch verified its expansion hashes against the pinned native115
profile. The actual-input launcher check passed 38 checks; the static-only subset
remains 33 checks. The 95 primary-oracle preservation checks and 40 additional
geometry wiring checks also passed unchanged.

| Evidence | Location Or SHA-256 |
| --- | --- |
| Compiled probe | `build/client-resource-probe/advantageclientresources-deobf.jar` |
| Probe SHA-256 | `B0E1519928878995E356E0035B3FAA620A750C08F06AD177C99E531147C706EE` |
| D combined launch SHA-256 | `C1E20DA39405F2DE503F251209D02C089170387AF692CC283D2071C68D47BE71` |
| D input preflight, no Java started | `build/client-resource-runs/20261026-130625-be14c356` |
| D full recipe/geometry run | `build/client-resource-runs/20261026-130632-3753a0c0` |
| Full `client-resources.json` SHA-256 | `18BE5F772EDBCDB17A1F33E68C7B2DB2F025D2EBEEAD625BA02695A84C1E87C8` |
| Geometry report SHA-256 | `027EFAAAE82DE28FF61738063C15C4E3D67BB5940BF04CA67ACDB776EAE2B053` |
| D fixture verification | `build/client-resource-compile-runs/final-d-fixture-preflight.json` |
| D runtime/input verification | `build/client-resource-compile-runs/final-d-client-provenance.json` |

The D graphical run passed 94 item models/names, 905 valid block states and
87 creative entries. Both tank-floor faces remained visible. Native tank
simulation, 10,000 mB fill/drain and full/empty packets passed. The actual gauge
pixel was `1E386E`, satisfying the original blue thresholds. All six native
fluid cases passed; both oils used their own still/flowing sprites and `FFFFFF`
top-face tint. All 25 worldless child-renderer geometry cases passed with balanced
matrices; hydroturbine registration/texture passed with its explicit world gap.

The full run used only the fresh D recipe campaign's `unlock` exports
under `test-rig/build/native115-recipes-20261026-d`. Before use, all five unlock
boots and all three packet reports were checked for `PASS`; the six packet/report
hashes and generated registry snapshot matched their fixture manifest. The saved
registry's candidate versions and all six candidate jar hashes matched target manifest
`3605B9BBF2B5C94C8278B31B5984969DD55B8F277E3B994496F045FA7CA79BE8`.
The native registry snapshot SHA-256 is
`73DF3EE41E8C5FC4C09DC7D9C79B221C127C749191C2900ABE4E61635B861383`.
The client independently validated the native 1.15.2/31.2.57 saved header and
decoded copied exported packets. No earlier campaign's packets were reused, and
the separate manufacturing progression runtime was not touched. This probe does
not qualify that campaign.

| Recipe Mode | Recipes | Native Cells | Result |
| --- | --- | --- | --- |
| Normal | 93 | 77 | PASS |
| Tech | 87 | 77 | PASS |
| Apocalyptic | 82 | 73 | PASS |

The actual thirteen-cell image contains 8,585 qualifying pixels against the
unchanged `> 1,000` assertion. Both the recipe and tank images were visually
inspected. Their SHA-256 values are
`C0868043B7C356BBFF80825662562F0AA2C0BB5C822DD36DEC2B1D71BFE3100D`
and `7D3A9295A75EDC67442A06B300A3077E37EE4335AAA94D48F65E22E14CE5A1A6`,
respectively. Each run retained its own reports, images, raw geometry, logs and
hashed production/launch/fixture inputs. The D result and both images are
byte-identical to the earlier full probe's outputs. The earlier preflight,
base-only run and full run remain at
`build/client-resource-runs/20261026-115710-d645702e`,
`build/client-resource-runs/20261026-115733-27d26ea0` and
`build/client-resource-runs/20261026-115941-52827e4b`; they are historical evidence,
not the D input record. No existing world was loaded.

## Eclipse Compiler Verification

Installed ECJ `3.46.0` (`v20260528-0407`) compiled all current production and test
sources into fresh, separate main/test outputs under
`build/ecj-final-d/20261026-130845/`. It ran on Java 25 with Java 8 source/target
and the actual Java 8 boot classpath. Every output class has major version 52.
The compiler jar SHA-256 is
`D0D43F8E2D7003E5EFED612E2CBB5F01870043397D8F1BBE536FD9128F4FCBF7`.

| Project | Main/Test Sources | Main/Test Errors | Main/Test Warnings | Tests From ECJ Outputs |
| --- | --- | --- | --- | --- |
| Power | 139 / 24 | 0 / 0 | 97 / 1 | 66 PASS |
| Steam | 63 / 11 | 0 / 0 | 40 / 0 | 68 PASS |
| Electric | 85 / 16 | 0 / 0 | 52 / 0 | 45 PASS |

`ecj-results.json` records source hashes, checkout refs, Eclipse classpath and
dependency hashes, commands and all six compiler results. Each project retains
`main.log`, `test.log` and XML diagnostics. `ecj-junit-results.json` and each
`junit.log` record successful Java 8 JUnit execution using the newly compiled ECJ
main/test classes, not Gradle's output classes. No tests were skipped. No
`bin/main`, `bin/test` or Gradle class output was replaced. The graphical probe
used the separately recorded Gradle production snapshot, not these ECJ outputs;
this does not claim an interactive Eclipse client launch.

## Reproduce The Combined Run

1. Use rebuilt native Steam/Electric candidates with matching combined hashes.
   Run Gradle only when the shared `D:/GradleCaches/minecraft-mods-shared` cache
   is free of other builds. Refresh pins after any production rebuild. Never substitute
   existing 1.14 binaries or replay its fixture/evidence directories.
2. Build the probe and prepare the combined launch with the root tasks:

   ```powershell
   ./gradlew.bat clientResourceProbeJar verifyEclipseProductionClasspath
   ./gradlew.bat prepareCombinedEclipseLaunch
   ./test-rig/client-resources/test-launcher.ps1
   ```

3. Use a new native115 recipe campaign's `unlock` directory. It must contain
   all three `recipes-*-sweep.bin` packets,
   corresponding successful JSON reports with `bookRecipes`, and generated
   `AdvantageWorksMigration/level.dat`. The saved header must identify native
   1.15.2/31.2.57. The server campaign retains its five unlock/persistence boots
   and eighteen progression checks; this probe does not generate those fixtures.
4. Snapshot inputs without starting Java:

   ```powershell
   ./test-rig/client-resources/run.ps1 -JavaHome '<Java 8 home>' -LaunchProfile runClientAllAdvantage.launch -RequireExtensions -ChildRendererGeometry -RecipeBookFixtures '<native115 campaign>/unlock' -PreflightOnly
   ```

5. Only when the graphical run is authorized, repeat the preceding command
   without `-PreflightOnly`. Inspect the retained tank and recipe images and
   native logs. Require all three
   `advantage_mods`, exact discovered versions, `recipe_book_grouping_status=PASS`,
   all six `fluid_rendering` cases, the tank results and the aggregate `PASS`.
   Missing fixtures explicitly yield `NOT_RUN_NO_NATIVE_FIXTURES`, not combined
   recipe qualification. Manual GUI navigation/discovery and real server-client
   menu handshake acceptance remain separate and pending even after a probe pass.

## Evidence And Safety

Each run receives a fresh `build/client-resource-runs/<UTC>-<unique-id>` directory.
The runner retains hashed selected production classes/resources, launch XML,
probe and candidate/dependency hashes, optional fixture hashes, native report,
images and separate stdout/stderr/native logs. Power's candidate jar hash is
provenance only: the runtime uses separately hashed Gradle or Eclipse output.
`-OutputSource Eclipse` copies `bin/main` without overlaying Gradle resources.

Preflight is not client evidence. Native partial results/images are never
overwritten by runner failures; `runner-failure.json` is separate. Timeouts keep
the run and terminate only the launched process tree. Existing runs and all old
fixtures remain untouched. The client must remain at the main menu with no
player, world or integrated server; recipe registry import only reads the copied
disposable fixture's NBT, never opens it in Minecraft. No protected save is used.

## Additional Renderer Geometry

`-ChildRendererGeometry` adds a bounded CPU geometry check after all of the
existing resource, tank, oil and recipe oracles. It requires `-RequireExtensions`
and is off by default. Use it only with matching native child jars:

```powershell
./test-rig/client-resources/run.ps1 -JavaHome '<Java 8 home>' -LaunchProfile runClientAllAdvantage.launch -RequireExtensions -RecipeBookFixtures '<native115 campaign>/unlock' -ChildRendererGeometry -PreflightOnly
```

After the required build checks and authorization for the graphical run, remove
`-PreflightOnly`. Compilation and the available worldless cases passed in the
full run recorded above. Its static wiring check is available without Gradle:

```powershell
./test-rig/client-resources/test-geometry-contract.ps1
```

All 40 static wiring/preservation checks passed. They do not execute the native
renderers; the separate combined-client run executed all 25 geometry cases and
recorded the one hydroturbine gap.

The probe creates ordinary worldless tiles through the registered tile factories
and retrieves their actual renderers from `TileEntityRendererDispatcher`. There
are no Steam/Electric compile dependencies and no replacement renderers or calls
to geometry helpers. Reflection sets only mutable fixture inputs: drill rotation,
cached facing/beam length, and turret angle, charge and target state. This checks
rendering of those inputs, not the world/network logic that produces them.

The real render methods receive native `MatrixStack` and `IRenderTypeBuffer`
inputs. Each selected production render type supplies a real `BufferBuilder`.
The resulting native buffers are decoded without uploading replacement geometry
or changing render state. Checks cover:

- Steam drill bits in all six directions (all three axes), at partial ticks
  `0` and `0.5`: 24 finite vertices, unit normals, unchanged UV bounds, cardinal
  extent, block bounds and visible geometry movement between rotation frames.
- Electric drill beams in all six directions: 16 finite vertices, four-block
  length in the requested direction, original `0.125` radius and beam UV layout.
- Turrets at idle and targeting all six directions: 24 body vertices when idle,
  40 body/beam vertices when firing, cardinal beam bounds, rotated body bounds,
  and the distinct idle/active optic UV regions.
- Nondegenerate quads, nonzero alpha, finite attributes, normalized normals,
  matrix depth/root/position/normal restoration, and readable expected textures
  selected by the production render types. Matrix state and partial buffers are
  retained even if rendering throws. The new geometry-only floating-point epsilon
  is `0.0001`; none of the existing pixel thresholds or captures is changed.

`child-renderer-geometry.json` retains per-case renderer identity, raw native
XYZ/UV/normal/alpha attributes, counts, bounds, texture dimensions, matrix results
and failures. Failures do not prevent the remaining bounded cases from running.
`primary_resource_status` records the existing oracles independently; a failure
in an enabled geometry case still fails the aggregate run.

The hydroturbine's registered entity renderer and texture are checked directly
through the entity renderer manager. Its constructor reads `World.rand`, so
orientation/spin geometry cannot use an ordinary worldless entity. That case is
explicitly `MANUAL_GAP_WORLD_REQUIRED`, with no constructed entity or render
call. It needs a fresh disposable client-world test; constructor bypasses,
synthetic vertices and protected saves are not used. Successful execution of the
available cases therefore reports `PASS_WITH_MANUAL_GAPS`, never full child
renderer acceptance. This is buffer-geometry evidence, not pixel, render-state,
normal world-dispatch, interpolation/network or manual graphical acceptance.
