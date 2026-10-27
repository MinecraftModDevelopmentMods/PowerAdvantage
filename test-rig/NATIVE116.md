# Native 1.16.5 Harness

Status: **UNQUALIFIED**. Compile and deterministic tests are not server,
migration, gameplay or manual acceptance. No 1.15 PASS transfers to 1.16.
Publication, production code and client-resources belong to the parent task.

## Build Input

- Minecraft 1.16.5, Forge 36.2.34, official 1.16.5 mappings, MCP 20210115.111550.
- ForgeGradle 7.0.34, Gradle 9.6.1, Java 8 bytecode.
- Java 17 runs Gradle; pinned Java 8u502 compiles the harness; Java 25 runs Mavenizer.
- Power 2.4.7.116051 bootstrap B source: `098d4625abc8a849bd1a11dcf697b3fee0cade7b`.
- B deobf SHA-256: `78E58DD2AB04E2B9F3267E5735C64AAE7E93EAC97571CB04485038862C853ED3`.
- B release SHA-256: `60E10F94F447D948244EF088A3B77134C13FBEBCCA1D8DB6210E8630F94A051F`.

The portable compile default is `../build/libs-dev/PowerAdvantage-2.4.7.116051-deobf.jar`.
`-PpowerAdvantageDeobfJar` overrides its location, not its exact hash check.
Power is compile-only and is not embedded. `verifyHarnessIsolation` checks the
separate production release; use `-PpowerAdvantageReleaseJar` for archived B.
The private build cache used for this port was already warm, not a cold-cache proof.
Build logs and failed attempts are retained in `build/native116-port-evidence/`.

## Fresh Creation Preset

Forge 36's vanilla flat properties path decodes with JsonOps, but the flat codec
requires a registry lookup. Both JSON flat settings and empty vanilla FLAT
settings can log `Not a registry ops`. Reaching Done does not make that PASS.

The harness registers only a ForgeWorldType, `advantageworkstest:test_flat`.
It constructs native FlatGenerationSettings directly with one bedrock, two dirt
and one grass layer, no stronghold or other structures, then calls updateLayers.
It registers no block, item or custom chunk-generator codec. Saved generator
identity remains vanilla `minecraft:flat`. Strict constructor and full save-codec
tests cover the layers, biome, structures, seed and three dimension keys.

New disposable runtime properties select the preset, `generate-structures=false`
and empty `generator-settings`. Worldgen sampling profiles retain DEFAULT.
These launch properties never rewrite imported level.dat or source worlds.
The original failed Power-only creation log is retained by the parent.
Every ERROR, including the registry-ops ERROR, remains a failure.

## Native Boundaries

Ticket geometry uses exact fields: ServerChunkProvider `field_217240_d` and
TicketManager `field_219377_e`. It never chooses a field merely by type because
Forge adds a second ticket map. Static native evidence gives forced level 31,
full level 33, max status distance 11, last holder level 44 and radius 13.
Actual geometry and forced tickets must still be observed in real runtime tests.

Native chunk reads use getChunkNow; world lookup and respawn use RegistryKey.
PlayerList.save remains protected and is called through its exact mapped method,
without an access-transformer relaxation. Tile reads use load(BlockState, NBT).
SaveFormat's native pre-datafix reader includes LevelSave; ChunkLoader upgrades
take RegistryKey, Supplier and CompoundNBT. Production hooks remain parent-owned.

Native actor comparison translates only exact four-int UUID and dimension-key
representations. It does not discard changed health, ownership, inventory or IDs.
WorldGenSettings retains seed, flags and the complete dimension/generator maps.
Saved-format receipts retain raw level.dat, compressed witness chunks and snapshots.
Their padded palette decoder is replayed against exact block names/properties;
old cross-long packing, missing data and changed identities fail. Converted and
restarted full WorldGenSettings must agree. Native engine tests also exercise
real BitArray packing at five, seven and thirteen bits.

## Migration Matrix

The native116 campaign requires 15 frozen and 15 connected routes. All historical
routes remain, plus native114 and explicit native115 sources. Frozen fixtures
retain 355 blocks and 332 tiles. Connected routes retain the unchanged operating
oracles and two exact 512-tick intervals. No target rebuilding or refilling is allowed.

Six native115 source receipts are required: native115/full and the five historical
connected lineages. They must identify accepted merged Power115
`5b8e73a78391960643420a09798a1ef8eb06f518`, a cleanly stopped disposable source,
the archived manifest and exact world/runtime fingerprints. Prior destination
PASS is not source approval. `native116_campaign.py attach115` attaches approved
receipts to a NEW route document; it does not launch or alter a source.

Historical adapters, profiles, resources and oracles remain byte-for-byte frozen
in `migration/native116-historical-inputs.json`. The preserved 1.15 Java harvest
test cannot compile against native 1.16 APIs; Native116HarvestIsolationTest runs
the same five assertions with native API names instead. No oracle is relaxed.

## First Server Handoff

Use `profiles/native116/packaged-current.json` after the parent archives every
runtime input and seals all hashes. Until then input preflight fails before any
runtime directory, download or world access. The parent-installed code-only seed
is `build/native116-seed-20261027`; only launcher/root jars and libraries are seed
inputs, never its worlds. Preserve every failed/partial attempt.

Set ADVANTAGE_WORKS_JAVA8_HOME to the pinned 8u502 JDK. Run
`verifyWorksNative116Inputs -PworksProfile=packaged-current` first. The first
packaged startup task is `runWorksPackagedCurrent -PworksServerPort=<leased-port>`;
the bounded two-boot task is `verifyWorksPackagedCurrent`. Both use Gradle
`-p test-rig` and the archived-B compile override during this handoff. Obtain an
explicit runtime/port lease before either startup task. The build lease alone
does not authorize Minecraft. Server startup, broad operation and all migration
campaigns remain pending, irrespective of deterministic test results.
