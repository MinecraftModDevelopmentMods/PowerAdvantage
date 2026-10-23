# Packaged Transformer Order Qualification

## Strategy and Scope

This is a focused qualification of deliberately reversed BaseMetals / PowerAdvantage
pre-datafix transformer **application**, not a source-order inspection. The test uses
Minecraft 1.13.2, Forge 25.0.223, ModLauncher 2.1.5, coremods 0.5.0 and Java 8u202.
No production source, mod jar, existing actor runner or existing probe is edited.

The separate `TransformerOrderBridge.java` probe patches exactly one return site
in a **disposable copy** of `CoreModEngine.initializeCoreMods()`. The added call:

1. Finds exactly one BaseMetals and one PowerAdvantage transformer for each of
   `world_info`, `chunk_status` and `worldgen_guard`.
2. Swaps only those three pairs' original registration slots for the requested
   order. Other transformers retain their positions, callbacks and votes.
3. Adds observer wrappers which delegate to the actual Forge-created
   `CoreModClassTransformer` objects. Forge still loads the unchanged JavaScript
   resources from the original hash-pinned production jars.

An archive-content audit requires the only changed existing library entry to be
`net/minecraftforge/coremod/CoreModEngine.class`, plus two new probe classes.
The test library is not a production artifact and must not be installed elsewhere.
No Gradle task or harness rebuild is needed.

## Actual Order Evidence

Every server process records the returned registration list, actual transformer
callback order, hook instructions before/after each callback and SHA-256-checked
ASM bytecode dumps. Exactly two callbacks must apply to each real target class:

| Pair | Target |
| --- | --- |
| World reader | `net.minecraft.world.storage.SaveFormatOld` |
| Chunk reader | `net.minecraft.world.chunk.storage.AnvilChunkLoader` |
| Generation guard | `net.minecraft.world.gen.WorldGenRegion` |

The second callback must receive exactly the hook sequence emitted by the first.
Missing callbacks, missing/duplicate prefix injections, wrong application order,
wrong emitted call order and changed bytecode evidence fail qualification.

Application order and emitted execution order are **different**:

| Application order | `prepareLegacyWorld`, `prepareLegacyChunk`, guard | `finalizeLegacyChunk` |
| --- | --- | --- |
| BaseMetals then PowerAdvantage | PowerAdvantage then BaseMetals | BaseMetals then PowerAdvantage |
| PowerAdvantage then BaseMetals | BaseMetals then PowerAdvantage | PowerAdvantage then BaseMetals |

The prep/guard transformers prepend instructions. The finalize transformers insert
before the return instruction; this target has three return sites. Guard execution
can short-circuit, so the second guard is conditional. The probe proves emitted
guard order and real loader application, not that every guard branch executes.

The natural-order control uses the observer library without swapping. The measured
natural order in this campaign is BaseMetals then PowerAdvantage. This observation
is not a promise about discovery order on another installation.

Preserved fixtures can start without loading `WorldGenRegion`. After taking the
semantic checkpoints, each launch force-loads a separate fresh remote Overworld
chunk, at block coordinates `(1024,1024)` and `(2048,1024)` respectively, to make
Forge apply both guard transformers. The force-load is removed before shutdown.
The first trial's adjacent restart coordinate was already generated as a neighbor;
that trial correctly failed the guard-evidence requirement and is not accepted.

## Fixtures and Oracles

The campaign uses immutable original Cyano 1.10 and maintained 1.12 actor sources
from `run-actor-migration/20261007-bridge112-strict-r2`. Each source fingerprint is
checked against its pinned manifest before copying and again after the campaign.
Each order starts from the same independently hash-checked mixed-input copy.

Only that disposable input is extended:

- Steel, copper and tin blocks use numeric IDs read from the source's saved Forge
  registry, at `(180,81..83,8)`. The region rewrite preserves every unrelated
  compressed chunk payload. These are synthetic registry-backed witnesses, not
  claimed historical gameplay placements.
- A damaged steel pickaxe with custom nested data occupies player Inventory slot
  10, EnderItems slot 26 and a nested item list in the musket's custom tag.
- The existing migration oracle checks 355 stock block/state witnesses, 332 tiles
  and 2,654 counted tile inventory items for original 1.10; maintained 1.12 has
  359 witnesses, 336 tiles and 2,708 counted items.
- The five source-serialized actors retain their identities, UUIDs, contents,
  ownership and turbine parent coordinate. Every measured actor remains at zero
  native ticks, and the player file is read/written through the existing real
  player adapter. Tile updates remain frozen.

Conversion, adapter read and a complete process restart are checked against the
source. Selected player-file/adapter/entity semantics and all 358 (original 1.10)
or 362 (maintained 1.12) block/state/tile snapshots must match exactly across the
three orders. Restart idempotence compares
the same file/adapter/entity surface on both sides. Embedded player NBT is checked
separately at initial conversion; it is not part of adapter restart idempotence.
Binary player files and encoded entity/tile NBT are retained for read-only replay.

## Backup Provenance Finding

Both mods mutate the same loose `level.dat` and player files, but have separate
backup suffixes. **The second writer's backup is an intermediate migration, not
the untouched original.** This is observed for both level and player data:

| Transformer application | Original bytes retained in | Power backup matches untouched input |
| --- | --- | --- |
| Natural / BaseMetals first | `.poweradvantage-legacy-backup` | Yes |
| Deliberately PowerAdvantage first | `.basemetals-legacy-backup` | No |

Both complete original files remain recoverable and every recorded backup stays
byte-for-byte stable across restart. The harness reports every input/backup hash,
and requires original-byte custody across the two backup families. It does **not**
relabel an intermediate Power backup as original or claim byte-identical backup
provenance across orders. A guarantee that the Power backup alone always contains
the untouched source would require a separate production design/fix; none is
included in this qualification.

The affected files are `level.dat` and
`playerdata/a614e658-6270-33c3-9297-7ce80908964f.dat`. Each runtime's
`order-results.json -> backup_provenance.files` records the exact input hash and
both backup hashes; `ordering-backups.json` and the replay audit prove they remain
available and unchanged after restart. The first executing mod's backup matches
the input SHA-256 for **both files in all six cases**.

Restore procedure: stop the disposable server and keep the upgraded copy intact.
Create a new copy of the complete pre-upgrade world, then select the backup whose
recorded SHA-256 matches the input for each file. In natural/BM-first application
use the Power suffix; in PA-first application use the BaseMetals suffix. Copy those
two files into the new copy without their backup suffixes and verify the hashes
again before using it. **These loose-file backups alone cannot roll back the world:
already-converted region files require the complete pre-upgrade world copy.**
No restore was applied to a running instance, and no production backup/gameplay
fix is authorized or included.

## Inputs and Commands

The target profile was archived **before** the dimension-probe extensions rebuilt
the shared harness. All six archived target jars match
`run-actor-migration/20261007-bridge112-inputs/profiles-qualified.json`:

```text
AdvantageWorksTestHarness-1.1.0.113021.jar
AD9C627A156306C66980DD7BB981A25DCC7878BDABE7B461D10F9FBEFFD31FA7
PowerAdvantage-2.4.4.113021.jar
CC89D1F4BC67ADCB9BEDA9B39DB9202654248D6F490E6058D1BD541761A94BB0
SteamAdvantage-2.2.5.113021.jar
D7D8A2E728E779EF0B624EF8AF3437F3354D618F12E279BE1C134F4AD5E690E3
ElectricAdvantage-2.2.5.113021.jar
BF7BE4EE44A94A95DE652A910815E950337208D9850D053248B11D4456D2DDFC
BaseMetals-3.0.1.113021.jar
4ACDD0F12ECE2258B3FA0394F9328E025AB02605B9CB0EBD17BB23F822E03542
OreSpawn-4.1.0.113021.jar
66C9CC5F8BE8F08F3DD5FA26EBEA79B52972B2FB86CB4DADBEEBC29AC01D3435
Forge launcher
0EE9DDDA49381A67707909502AC9212C89F9D7A8772160E566A27CEFDA4173C5
Minecraft server
FFD3AA2C25C5BA68A706B59F2ABDC69AC1748E115CA9D3B47941E197736F088E
Original coremods-0.5.0.jar
723BC497B557BD3A643C621B4F7F391AE82476194F445BC550076AE5601E9D4D
Test-only coremods-order-test.jar
7BCC62C593A2E65D02509041C664C1DFCBB434A1B581AF2B9C85D92425AE369A
```

The archived profile is
`run-transformer-order/20261007-qualified-inputs/profiles-archived.json`.
Large worlds/libraries/logs are on C:, not D:. Use a fresh output name for a rerun.

```powershell
Set-Location D:\PowerAdvantage\PA113\PowerAdvantage
$python = 'test-rig\migration\.venv\Scripts\python.exe'
$run = 'C:\Users\johnb\AppData\Local\AdvantageWorks\qualification\20261007-order-full-r2'
& $python -m unittest discover -s test-rig\migration -p test_transformer_order.py -v
& $python test-rig\migration\transformer_order.py `
  --profile-manifest run-transformer-order\20261007-qualified-inputs\profiles-archived.json `
  --fixtures run-actor-migration\20261007-bridge112-strict-r2 `
  --java8 'C:\Program Files\Java\jdk1.8.0_202\bin\java.exe' `
  --output $run --port 25621
& $python test-rig\migration\audit_transformer_order.py `
  --run $run --output "$run\integrity-audit.json"
```

The runner's `probe-build.json` records the exact standalone `javac`/patcher
commands, probe source hash and dependency hashes. Per-runtime `input-jars.json`
records the untouched staged mod bytes. The local Store runtime resolves the C:
alias into its `OpenAI.Codex_2p2nqsd0c76g0/LocalCache/Local/AdvantageWorks` directory;
both the requested alias and resolved paths point at the same evidence.

## Results and Limits

Final campaign and independent replay results are recorded in `summary.json`,
`results.json`, `cross-order.json` and `integrity-audit.json` under the campaign root.
The accepted `20261007-order-full-r2` campaign completed with:

| Check | Exact result |
| --- | --- |
| Original 1.10 x natural / BM-first / PA-first | All three PASS, including full restart |
| Maintained 1.12 x natural / BM-first / PA-first | All three PASS, including full restart |
| Actual class application order / emitted hook order | All three targets in all twelve launches PASS |
| Conversion / player adapter / restart / idempotence | PASS in all six runtimes |
| 355/359 stock witnesses plus three metal blocks | Conversion and restart PASS in all six runtimes |
| Selected actor/player/block/tile semantic equality across orders | Exact equality for both inputs |
| Original-byte custody / both backup families' restart stability | PASS in all six runtimes |
| Untouched original source worlds and mixed input copies | PASS |
| Read-only replay audit | `PASS: 6 runtimes; semantic=PASS` |
| Focused Python regressions | 15 tests, zero failures/errors |
| Existing matrix / actor oracle regressions | 5 / 17 tests, zero failures/errors |
| Campaign result | `PASS_WITH_BACKUP_ORDER_DEPENDENCE`, semantic status `PASS` |

The accepted runner and replay commands both returned exit code zero. All twelve
server processes shut down, and no listener remained on localhost port 25621.

Final observer dumps differ as expected between the forced orders, while selected
saved semantics remain equal. Hashes below are identical for both inputs/restarts:

```text
BM-first: SaveFormatOld
5043D49B985BD492E7F4054FAC8B7B2B2D49A0430AA8DD02646B12717360A11A
BM-first: AnvilChunkLoader
290C86880FC5761FFB652B7B775F6BB4C9B90D8BA42E86D441C44C1F8B0BC9F4
BM-first: WorldGenRegion
CB7283F83652169555B1E9A56642C23D5886A708470766FB2216DA543004B80F
PA-first: SaveFormatOld
4035A19F6994E26D67C99212D71EB1A0DF54D4A682705ED7A24C2217CE6A255A
PA-first: AnvilChunkLoader
48588E0979904FD4D9BA921D742B78B1C1D0D6A4AF4D219755C86DA4A7D7789A
PA-first: WorldGenRegion
85961242C307FE757B887494B94505D90A8A9A4D722DE4937F1995288DEE0D56
```

Small copied reports and artifact hashes are under
`run-transformer-order/20261007-final-report`; large runtime evidence stays on C:.

Early pilots and `20261007-order-full-r1` are retained diagnostics, not accepted
qualification. They caught test-library ZIP metadata, writable NBT arrays,
not-loaded command positions, missing guard class application, a mismatched
embedded-player oracle surface and the genuine backup-provenance difference.

No semantic migration defect has been observed in the completed comparisons.
BaseMetals' existing leaves-fixer error and Forge shutdown/config/resource-pack
diagnostics are retained in the logs; this is not an error-free-runtime claim.

This qualifies only the pinned Forge/coremod stack and selected Overworld data.
It does not qualify client login/rendering/GUI, other dimensions, native entity
behavior, passenger gameplay, all worldgen guard branches, other transformers,
whole modpacks or arbitrary future jar combinations. No commit, push, live
instance replacement, source-world mutation or gameplay repair is included.
