# Native 1.16.5 Harness

See the [current test record](../docs/1.16/CURRENT-QUALIFICATION.md) for exact
tested jars, completed checks and remaining work. Compilation and deterministic
tests alone are not server, gameplay or manual acceptance. Earlier-version
passes do not qualify this version. Publication follows manual acceptance.

## Build Input

- Minecraft 1.16.5, Forge 36.2.34, official 1.16.5 mappings, MCP 20210115.111550.
- ForgeGradle 7.0.34, Gradle 9.6.1, Java 8 bytecode.
- Java 17 runs Gradle; pinned Java 8u502 compiles the harness; Java 25 runs Mavenizer.
- Power 2.4.7.116051 source: `3f1100e6f75b1fb949cc883be7288aa30789876d`.
- Deobf SHA-256: `1A3E5B9EE45CF932CE963146D016E5270BD587A5D5AA2B9283886FF89EF14FD4`.
- Release SHA-256: `EB226ADC200BFB96278F1449CDB7C6941F289DB43330C8206369048378E931EB`.

The portable compile default is `../build/libs-dev/PowerAdvantage-2.4.7.116051-deobf.jar`.
`-PpowerAdvantageDeobfJar` overrides its location, not its exact hash check.
Power is compile-only and is not embedded. `verifyHarnessIsolation` checks the
separate production release; use `-PpowerAdvantageReleaseJar` when checking an
archived release. Keep logs and reports in ignored output directories. The test
record distinguishes cold-cache builds from warm-cache checks.

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
Every ERROR, including the registry-ops ERROR, remains a failure.

## Native Boundaries

Ticket geometry uses exact fields: ServerChunkProvider `field_217240_d` and
TicketManager `field_219377_e`. It never chooses a field merely by type because
Forge adds a second ticket map. Static native evidence gives forced level 31,
full level 33, max status distance 11, last holder level 44 and radius 13.
Actual geometry and forced tickets must still be observed in real runtime tests.

Native chunk reads use getChunkNow; world lookup and respawn use RegistryKey.
Connected players use protected PlayerList.save through its exact mapped method,
without an access-transformer relaxation. Forge skips that method for offline
probe players without a connection, so those fixtures use native PlayerData.save
and verify the actual saved file. Tile reads use load(BlockState, NBT).
SaveFormat's native pre-datafix reader includes LevelSave; ChunkLoader upgrades
take RegistryKey, Supplier and CompoundNBT.

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
retain each sealed source's exact block and tile counts, including maintained-only
witnesses. Connected routes retain the unchanged operating
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

## Running A Profile

Use `profiles/native116/packaged-current.json` with the exact archived inputs.
Input preflight checks their identities and hashes before opening a world.
A prepared Forge server seed contains only launcher/root jars and libraries,
never worlds or configuration. Supply its inventory and SHA-256 with
`--prepared-server-seed`, `--prepared-server-seed-inventory` and
`--prepared-server-seed-sha256` to use those exact installed code bytes.
Changing the fixture harness requires its own sealed manifest, not overwriting
an active campaign's jars. Preserve failed and partial attempts.

Set ADVANTAGE_WORKS_JAVA8_HOME to the pinned 8u502 JDK. Run
`verifyWorksNative116Inputs -PworksProfile=packaged-current` first. The first
packaged startup task is `runWorksPackagedCurrent -PworksServerPort=<leased-port>`;
the bounded two-boot task is `verifyWorksPackagedCurrent`. Both use Gradle
`-p test-rig` and the pinned Power compile input above. Coordinate a free local
port before starting; never stop an unidentified server to make room. Results
are recorded per campaign rather than inferred from successful compilation.
