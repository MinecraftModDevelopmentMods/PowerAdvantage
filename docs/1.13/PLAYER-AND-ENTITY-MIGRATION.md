# Player and entity migration qualification

## 1.12 Bridge Backport

ElectricAdvantage's local `2.2.2.112021` candidate backports the pending-coordinate
repair to `HydroturbineEntity.readEntityFromNBT`/`writeEntityToNBT`. It also clears
stale links when reading new entity data. Registry identities, `parentPos`, the
entity UUID and generated electricity are unchanged; erased coordinates are not
guessed or reconstructed. The original `2.2.1.112021` jar remains archived and
continues to define the historical `maintained112` source profile.

A separate hash-pinned `bridge112` profile substitutes only the repaired Electric
jar at the intermediate stage. The strict campaign now saves and fully restarts
1.12 before 1.13 conversion, requiring an explicit frozen-state flag and zero
entity ticks at both intermediate checkpoints. All eight strict routes now pass:
the three original/early/maintained 1.10 direct and chained pairs, plus early and
maintained 1.12 direct. The three normal-ticking chained controls, four
player-layout routes, eight block/tile routes and eleven focused Works stations'
automated assertions pass, including restart. Their independent audits pass.

Every strict stage preserves the expected player state, all five entity UUIDs,
ownership and turbine `parentPos`. No replacement turbine can conceal a failure:
tile updates remain frozen. After the frozen checkpoints the same entities must
survive at least sixty measured native ticks and another complete restart.

| Evidence | Result |
| --- | --- |
| `run-actor-migration/20261007-bridge112-strict-r2` | Eight strict routes PASS, including both 1.12 zero-tick checkpoints; independent integrity audit PASS |
| `run-actor-migration/20261007-bridge112-native-r1` | Three repaired-bridge native/restart controls PASS; integrity audit PASS |
| `run-actor-migration/20261007-bridge112-player-layout` | Four bag/Ender/nested-item routes PASS; integrity audit PASS |
| `run-migration-matrix/20261007-bridge112-r1` | Eight block/tile routes PASS with the new intermediate jar |
| `run-migration-matrix/20261007-bridge112-operation` | Eleven stations' automated assertions accepted before/after restart; manual checks remain `MANUAL_REQUIRED` |

`run-migration-matrix/qualification-audit-20261007-bridge112.json` verifies eight
routes, ten unchanged fixture fingerprints and eleven staged-jar manifests.
The combined report is `run-actor-migration/20261007-bridge112-validation.json`.
The manifests under `20261007-bridge112-inputs` pin the original source jars
and candidate bridge separately. `profiles-qualified.json` retains the exact
accepted probe hashes, using the archived 1.10 probe after its shared source was
also rebuilt successfully for 1.10. The first strict trial, `bridge112-strict-r1`,
retains correct semantic state but fails the strengthened freeze guard because
its old legacy probe omits the status flag. It is diagnostic evidence, not an
accepted strict qualification. The probe was updated and the entire strict
matrix rerun; neither that guard nor the preservation oracle was weakened.

The new 1.12 Java regressions first failed against the old serializer, then all
three passed after the backport. Both complete FG7 release-audit runs pass, with
build caching disabled. All four release/deobf/sources/Javadoc hashes reproduce
exactly. Archive comparison confirms unchanged entry names and contents apart
from version metadata, the mod entrypoint and the turbine class. The Python
runner/oracle suite now contains 73 passing tests.

```text
ElectricAdvantage-2.2.2.112021.jar
AF9B34F65F6D8E47F9F535486749C540C7C57DA3E750A3223606ACCFB417B0C9
ElectricAdvantage-2.2.2.112021-deobf.jar
4E76CA962B1464571357CC4B849D1D8FE87CA17674D550A4E0CB84531242A65F
Original ElectricAdvantage-2.2.1.112021.jar (retained)
CFF96BDCD60770D370E16BDCF34E8E08DF8139804437CA1D368D7080996E7537
Updated 1.12 legacy probe with explicit freeze status
C0DEDBB1A4FF1E33541E33C2B8B6FBDF00BE41AECDC09834462111BB89F50BF2
```

No 1.12 Power/Steam or 1.13 production code changed in this backport pass. Source
fixtures and the Sylvester world remain untouched, and no commit, push, PR, tag,
release publication or live-instance replacement is included.

## 1.13 Repairs Before the Bridge Backport

The two findings below have been repaired in the unpublished 1.13 candidates:

- Power runs recognised old vanilla entities independently through Minecraft's
  entity datafixers before a mixed modded chunk is flattened. This retains pets
  on direct 1.10 upgrades without guessing names or altering unknown mod entities.
- Electric writes a hydroturbine's pending `parentCoord` when its live generator
  reference has not resolved yet. The existing `parentPos` key and UUID stay intact.

Packaged requalification on 2026-10-07 passes all five direct strict routes,
all eight normally ticking routes, all four player-layout routes, and all eight
block/tile migration routes. Each includes a complete process restart. Independent
raw-NBT integrity audits pass for the strict, native and player-layout campaigns.
These are measured fixture results, not universal world-upgrade guarantees.

| Source | Route | Player state | Strict turbine link | Owned wolf | Native control and restart |
| --- | --- | --- | --- | --- | --- |
| Original Cyano 1.10 | Direct to 1.13 | PASS | PASS | PASS | PASS |
| Original Cyano 1.10 | Through maintained 1.12 | PASS | FAIL in old bridge | PASS | PASS |
| Early local 1.10 | Direct to 1.13 | PASS | PASS | PASS | PASS |
| Early local 1.10 | Through maintained 1.12 | PASS | FAIL in old bridge | PASS | PASS |
| Maintained 1.10 | Direct to 1.13 | PASS | PASS | PASS | PASS |
| Maintained 1.10 | Through maintained 1.12 | PASS | FAIL in old bridge | PASS | PASS |
| Early local 1.12 | Direct to 1.13 | PASS | PASS | PASS | PASS |
| Maintained 1.12 | Direct to 1.13 | PASS | PASS | PASS | PASS |

The unchanged maintained 1.12 bridge still discards a turbine's pending link if
saved before its first tick. The strict runner correctly exits with failure for
these three chained routes; their only differences are missing `parentPos`, then
loss of that unlinked turbine when real ticks resume. Every player comparison
passes, and all three wolves are retained. The native controls permit the 1.12
parent link to resolve before saving and pass all eight routes. The target 1.13
serializer cannot recover a coordinate the old bridge already discarded. This
is a remaining legacy-bridge defect, not an accepted serialization equivalent;
no 1.12 code or pinned source jar was changed here.

Six Power Java regressions cover mixed chunks, ownership, vanilla subtype
conversion, nested passengers, unknown custom data and idempotence. Two Electric
regressions cover repeated zero-tick saves and clearing stale pending links.
All three complete FG7 release suites and the seventy Python harness tests pass.
Steam's packaged bytes are unchanged; both add-ons' Power deobf pins and the rig's
artifact hashes now point at the repaired build. Changelogs are updated.
Second clean builds with `--no-build-cache` rerun compilation, tests and renaming
and reproduce the same release/deobf hashes for all three mods. Their Java suites
contain 23 Power, 6 Steam and 11 Electric tests; the eight new regression tests
are included in those totals.

Repaired evidence under Git-ignored local directories:

| Run | Result |
| --- | --- |
| `run-actor-migration/20261007-repaired-strict-r1` | Five direct routes PASS; three zero-tick chained routes FAIL in the unchanged 1.12 bridge; integrity audit PASS |
| `run-actor-migration/20261007-repaired-native-r1` | Eight native/restart routes PASS; integrity audit PASS; uses hash-checked source fixtures from `20261007-qualified-r4` |
| `run-actor-migration/20261007-repaired-player-layout` | Four bag/Ender/nested-item routes PASS; integrity audit PASS; uses the same immutable source fixtures |
| `run-migration-matrix/20261007-repaired-r1` | Eight block/tile conversion/restart/idempotence routes PASS |
| `run-migration-matrix/20261007-repaired-operation` | Automated assertions accepted for eleven focused Works stations before/after restart; manual checks remain `MANUAL_REQUIRED` |

`run-actor-migration/20261007-repaired-validation.json` combines all campaign
verdicts and reproducibility pins. The independent block/tile audit is
`run-migration-matrix/qualification-audit-20261007-repaired.json`; it verifies
eight routes, ten unchanged source/copy fixture fingerprints and eleven staged
jar manifests. Existing runtime diagnostics are retained, not hidden.

Repaired production SHA-256 pins:

```text
PowerAdvantage-2.4.4.113021.jar
CC89D1F4BC67ADCB9BEDA9B39DB9202654248D6F490E6058D1BD541761A94BB0
PowerAdvantage-2.4.4.113021-deobf.jar
D166D677AA6261A445D7A0DD50810F93E9B694A507AB5953325EC734DD6AD3DF
SteamAdvantage-2.2.5.113021.jar (unchanged)
D7D8A2E728E779EF0B624EF8AF3437F3354D618F12E279BE1C134F4AD5E690E3
ElectricAdvantage-2.2.5.113021.jar
BF7BE4EE44A94A95DE652A910815E950337208D9850D053248B11D4456D2DDFC
ElectricAdvantage-2.2.5.113021-deobf.jar
2A02A3A7E8E20D09D4D792CC9B16FC5FA7C8B718A9A778C90D69E3B7D0EFAE8D
```

The unchanged dependency/harness pins and pre-repair results are retained below.
No original world, live client, or viewer instance was changed; nothing was
committed, pushed or published. Candidate versions remain unchanged because
these are unreleased builds.

## Pre-repair result: player witnesses pass; two entity defects

Local qualification on 2026-10-07 exercised the same eight upgrade routes as
the [block/tile migration matrix](MIGRATION-MATRIX.md), using real source-version
serializers, packaged 1.13 jars, and separate server processes for restart checks.
All enumerated player-state checks passed. Entity qualification is **not complete**:
direct 1.10 upgrades lose the owned-wolf witness, and saving a loaded turbine
before its first tick loses its generator link.

These are measured fixture results, not a guarantee for every player or modpack.
No production code or release jar was changed during this harness extension.
No original world was opened, and nothing was committed or published.

## Pre-repair upgrade results

The strict campaign loads, snapshots and saves before tagged entities receive
their first tick. The native control instead allows normal entity updates before
the first save and after a complete restart. Both require exact entity identities;
an automatically recreated turbine cannot substitute for the saved one.

| Source | Route | Player state | Strict turbine link | Owned wolf | Native control and restart |
| --- | --- | --- | --- | --- | --- |
| Original Cyano 1.10 | Direct to 1.13 | PASS | FAIL | FAIL | FAIL: wolf missing |
| Original Cyano 1.10 | Through maintained 1.12 | PASS | FAIL | PASS | PASS |
| Early local 1.10 | Direct to 1.13 | PASS | FAIL | FAIL | FAIL: wolf missing |
| Early local 1.10 | Through maintained 1.12 | PASS | FAIL | PASS | PASS |
| Maintained 1.10 | Direct to 1.13 | PASS | FAIL | FAIL | FAIL: wolf missing |
| Maintained 1.10 | Through maintained 1.12 | PASS | FAIL | PASS | PASS |
| Early local 1.12 | Direct to 1.13 | PASS | FAIL | PASS | PASS |
| Maintained 1.12 | Direct to 1.13 | PASS | FAIL | PASS | PASS |

The source versions and original-release hashes are recorded in the
[provenance table](MIGRATION-MATRIX.md#source-provenance). There is no published
Cyano 1.12 source fixture here; early local 1.12 is a rebuilt port.

The native control preserves the turbine's `parentPos` and UUID in **all eight**
routes. Its three failing rows fail solely because the wolf is absent.
Conversely, all eight strict rows lose `parentPos`, then lose the turbine when
actual entity ticking is released. Chained strict routes also expose the link
loss in the intermediate 1.12 serializer.

Stage names such as `player_adapter` in `actor-results.json` are aggregate
player-and-entity comparisons. Their overall `FAIL` does not imply player loss:
the separate `player_status` is `PASS` at every compared stage.

## Player coverage

Each source creates a deterministic offline-profile `EntityPlayerMP` through
its own game code. The fixture includes:

- Four charged battery types, each with `energy = 123.0`, and exact quantities.
- A damaged musket carrying all four registered Steam enchantments at level one.
- Sprocket, dirt, food, custom nested tags, and an oil item or filled oil bucket.
- Hotbar contents, selected slot, damaged armour in all four slots, and offhand.
- Ender Chest contents, UUID, position, rotation, dimension, health, experience,
  hunger and saturation.

The normal fixture's serialized player inventory contains fourteen stacks;
its Ender Chest contains nine. The oracle compares the **actual saved source
state**, rather than assuming every requested seed was accepted by the source.
If a source does not register `forge:bucketfilled`, its oil witness is the
registered Power crude-oil item. `actor-fixture.json` explicitly records that
universal-bucket coverage is unavailable for that source.

The target adapter calls the actual `PlayerList.readPlayerDataFromFile` and
`IPlayerFileData.writePlayerData` on an `EntityPlayerMP`. Binary player files
and the adapter's serialized NBT are retained separately. Full-process restart
checks confirm the enumerated state is retained after the real writer runs.
This is **not** a network login or Forge handshake test.

An additional four-route campaign relocates main-bag witnesses to slots
`9,18,27,35`, includes hotbar edges, keeps armour/offhand, and fills selected
Ender Chest slots including the last slot, `26`. A musket's custom nested
inventory contains three copied item stacks: enchanted/damaged musket,
charged battery, and oil item/bucket. Original 1.10 direct/chained, maintained
1.10 direct, and maintained 1.12 direct all pass conversion and restart.

Embedded single-player `Data.Player` is injected into disposable `level.dat`
from the source's serialized player file after its dedicated server stops.
The target observes it before dedicated-server saving removes host-only data.
That qualifies pre-datafix embedded NBT, **not** integrated-server player login.

## Entity coverage and timing proof

Five tagged source entities are used: inventory chest minecart, dropped musket,
dropped oil item/bucket, owned seated wolf, and hydroturbine. The oracle checks
their IDs, UUIDs, inventories/items, custom Forge data, ownership, seating,
and generator link. Frozen comparisons also require positions, rotations and
motion to remain unchanged. Native comparisons permit those three physical
fields to change, but do not permit loss, duplication or ownership changes.

The strict harness temporarily removes only tagged witnesses from the world's
entity-update list at tick start and restores them at tick end. Snapshots must
report a frozen state and **zero measured `ticksExisted` for every present
witness** at conversion, adapter read and restart. A Forge update flag alone
was found insufficient and is not accepted as freeze proof.

Native phases load neighbouring chunks and add a separate, unlogged-in Forge
fake-player sentinel to prevent an idle world from skipping entity updates.
That sentinel never reads or writes the witness player's file. Every surviving
entity must accumulate at least sixty actual `ticksExisted` increments; advancing
world time or displaying an animation alone cannot pass the timing assertion.
Missing entities still fail preservation independently of the tick guard.

Tile ticking remains suppressed so generator code cannot spawn a replacement
turbine and conceal loss of the saved UUID. This campaign is entity persistence
qualification, not another turbine generation/charging endurance test.

## Findings

### AWM-113-001: owned wolf lost on direct 1.10 upgrade

**Current status:** fixed and packaged-qualified on all three direct 1.10 sources.

**Observed:** all three direct 1.10 fixtures lose a valid saved wolf, in both
strict and normally ticking controls. Their corresponding chained routes retain
it. The source chunk uses `DataVersion = 512` and the legacy entity ID `Wolf`;
the saved owner and entity UUIDs are present before conversion.

**Pre-repair diagnostics:** target logs report `Unsupported key: Wolf`, followed by
`Exception loading entity` and an invalid `minecraft:Wolf` resource location.
The wolf shares its chunk with the custom legacy hydroturbine. A red-to-green
regression isolated the failure: vanilla's early entity conversion succeeds for
the lone wolf, but can stop when the chunk also contains an unknown mod entity.
This does not establish that every old vanilla entity was affected.

**Pre-repair impact:** direct 1.10 upgrade safety cannot be accepted while a player's pet
can disappear. Keep this as a release-blocking migration finding, not an
accepted limitation or a reason to silently change the supported upgrade route.

**Repair:** `LegacyWorldDataHook.prepareLegacyChunk` now applies Minecraft's real
`ENTITY_TYPE` fixer independently to each recognised vanilla entity, up to
pre-flattening data version `1343`. Passenger trees are handled recursively;
unknown mod vehicles remain unchanged. The chunk's original `DataVersion` is
retained so normal block/chunk conversion still runs. Regression tests require
the original wolf UUID, owner and seated state, and test skeleton subtype
conversion rather than merely lowercasing legacy names.

### AWM-113-002: turbine link lost before first entity tick

**Current status:** fixed and packaged-qualified for direct upgrades to 1.13 and
for chained upgrades using ElectricAdvantage `2.2.2.112021` in the 1.12 bridge.
The original `2.2.1.112021` jar still reproduces the pre-tick defect and is retained
as the historical control; it has not been overwritten or retroactively repaired.

**Observed:** a source turbine stores `parentPos = [164,81,8]`. After loading and
saving without its first tick, that field is absent. A later process reloads the
unlinked entity; when released for real updates it removes itself. All eight
strict routes reproduce this. Native-before-save controls retain the link and
the original turbine in all eight routes, including both restart checkpoints.

**Pre-repair code evidence:** Electric's `HydroturbineEntity.readAdditional` reads the saved
coordinate into `parentCoord`; `tick` resolves it into `parent`. However,
`writeAdditional` writes `parentPos` only from a non-null `parent`, not from the
still-pending coordinate. This is a read/save timing defect also exposed by the
legacy intermediate serializer, not evidence that every normal restart fails.

Relevant 1.13 source:
`zone/moddev/mc/electricadvantage/entities/HydroturbineEntity.java`,
`readAdditional`, `writeAdditional`, and server-side parent resolution in `tick`.

**Pre-repair impact:** saving a newly loaded but not-yet-updated turbine can discard its
generator link. A generator may subsequently create a replacement, masking the
saved-entity loss during casual testing.

**Repair:** `writeAdditional` now writes from the live parent's position, or from
the pending coordinate when the parent is unresolved. Reading also clears stale
links before accepting new NBT. Repeated zero-tick serialization is regression
tested. The old 1.12 bridge remains unchanged and is still tested against its
original pinned bytes.

## Evidence and artifact pins

Local evidence is retained under `run-actor-migration/` (Git-ignored):

| Run | Accepted coverage |
| --- | --- |
| `20261007-qualified-r4` | Final eight-route strict matrix, measured zero-tick freezes, real player adapters, release-to-native checks and complete restarts |
| `20261007-native-r2` | Full eight-route native control, measured entity updates and complete restarts |
| `20261007-final-harness-native` | Final harness confirmation of maintained 1.12 PASS and original 1.10 wolf-only FAIL |
| `20261007-player-layout` | Four-route bag boundaries, last Ender slot and recursive item stacks, conversion and restart |

Each has an independent `integrity-audit.json` replaying binary NBT, staged jar
hashes and fixture preservation. All four audits pass. **Audit PASS means the
recorded evidence is consistent; it does not turn a gameplay FAIL into PASS.**
The strict audit also verifies exact predecessor bytes in migration backups,
unchanged backups after restart, and idempotent repeated-load state.

Earlier pilots and `qualified-r1/r2/r3` are retained diagnostics, not the final
strict qualification. World-time-only pilots do not prove entity updates;
`qualified-r3` does not prove a zero-tick freeze. Native-r2 and the layout campaign
used the preceding harness build; final-harness native confirmation explicitly
rechecked the relevant native branches after the freeze implementation changed.

Production jars stayed unchanged throughout the pre-repair investigation:

```text
PowerAdvantage-2.4.4.113021.jar
0CCA0AF27497431C3A64091809F8DD328C84D82500CF05456366770C65485E74
SteamAdvantage-2.2.5.113021.jar
D7D8A2E728E779EF0B624EF8AF3437F3354D618F12E279BE1C134F4AD5E690E3
ElectricAdvantage-2.2.5.113021.jar
72027C2DC273FD28F23AF01BB3EB495C75076E19B27A38CBDC5213B35A6198EB
BaseMetals-3.0.1.113021.jar
4ACDD0F12ECE2258B3FA0394F9328E025AB02605B9CB0EBD17BB23F822E03542
OreSpawn-4.1.0.113021.jar
66C9CC5F8BE8F08F3DD5FA26EBEA79B52972B2FB86CB4DADBEEBC29AC01D3435
Final Advantage Works harness 1.1.0.113021
AD9C627A156306C66980DD7BB981A25DCC7878BDABE7B461D10F9FBEFFD31FA7
Preceding native/layout harness
DEED9C5711855AF4D61CA425CE7E072AA8D82AE619A99B919BBC060363DB070B
```

Every runtime records its actual source paths, filenames and hashes in
`input-jars.json`; this takes precedence over a display label. Original fixture
fingerprints are checked before copying and after the campaign. The original
Sylvester world remains untouched. All campaign servers bind only localhost
and shut down between phases; no viewer server is left running.

Harness checks pass: seventy Python regression/oracle tests, Java harness tests,
FG7 `check assemble`, and release/harness isolation. The actor tests include
negative controls for lost or duplicated quantities, entities, ownership, UUIDs,
charge, experience, enchantments, custom tags and false tick/freeze evidence.

## Remaining qualification

- Chained zero-tick turbine safety requires the repaired Electric 1.12 bridge
  (`2.2.2.112021`) rather than the original `2.2.1.112021` jar. Direct upgrades to
  the repaired 1.13 candidates bypass that old serializer. A generator coordinate
  already erased by an older save cannot be recovered without further evidence.
- A real client login, Forge handshake, client entity/ownership synchronization,
  rendering and GUI acceptance remain untested by these adapters.
- This report covers only Overworld player dimension/position. The separate
  [additional-dimension campaign](DIMENSIONS-AND-ENTITY-LIFECYCLE.md) adds Nether,
  End, native passenger/leash relationships and death/respawn controls, with its
  own timing limits and findings. Advancements, statistics and capability-bearing
  third-party player state still require separate fixtures or client tests.
- Leashes and live passenger relationships are not covered by these five-entity
  fixtures; use the separate dimension report, not this report, as their evidence.
  Riding elevators, combat, mounted player travel and other mods' entities remain
  outside the automated coverage.
- Base Metals transformer and Forge shutdown/config-watcher diagnostics are
  retained; logs must not be described as error-free.
- This is not a whole-modpack Sylvester conversion or proof for unrelated mods.

See the [runner protocol](../../test-rig/migration/README.md#players-and-saved-entities)
for repeatable commands and input requirements.
