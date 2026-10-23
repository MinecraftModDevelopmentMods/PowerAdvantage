# Original-release and chained migration qualification

## Result: State matrix passes; release qualification is not complete

Local qualification on 2026-10-05 passed all eight conversion routes below,
including a complete server-process restart and a second state comparison.
The three 1.13 candidates were tested as packaged, reobfuscated jars on Forge
25.0.223 with Base Metals 3.0.1.113021 and OreSpawn 4.1.0.113021.

All eight block/tile routes were rerun successfully on 2026-10-07 after the
[player/entity repairs](PLAYER-AND-ENTITY-MIGRATION.md), using new packaged Power
and Electric jars and hash-checked copies of the same five source fixtures.
Conversion, complete restart, idempotence and all intermediate comparisons pass.
The focused operation campaign was also rerun in a fresh original-release chained
copy: eleven stations' automated assertions are accepted before/after restart,
with manual checks still explicitly outstanding. Independent artifact/fixture
audit passes; this does not qualify the separate chained zero-tick entity defect.

This is evidence for the enumerated fixtures, not a guarantee about every
modpack, saved world, client renderer, player inventory, or moving entity.
No original world was opened or modified, and nothing was committed or published.

## Source provenance

| Source | Actual source jars | Origin |
| --- | --- | --- |
| Original Cyano 1.10 | Power 2.3.0; Steam/Electric 2.2.0 | Power/Steam read from the immutable Sylvester instance; Electric downloaded from [CurseForge file 2323092](https://www.curseforge.com/minecraft/mc-mods/electric-advantage/files/2323092) |
| Early local 1.10 | Power 2.4.0; Steam/Electric 2.2.0 | Local rebuilt `com.mcmoddev` jars from the early porting work, not original published releases |
| Maintained 1.10 | Power 2.4.3.110021; Steam/Electric 2.2.4.110021 | Maintained namespace/API releases, with Base Metals 2.5.0-beta4.238 and OreSpawn 4.0.8.110021 |
| Early local 1.12 | Power 2.4.1; Steam/Electric 2.2.1 | Local pre-FG7 1.12 port, not a historical Cyano release |
| Maintained 1.12 | Power 2.4.1.112021; Steam/Electric 2.2.1.112021 | Maintained FG7 releases |

The original Cyano jar SHA-256 values are pinned in `test-rig/migration/matrix.py`:

```text
PowerAdvantage_1.10.2-2.3.0.jar
47CD509C1141095BFE1C8C7DD689C2ECD3F99C89195E34A88CC9E0B5FC12413E
SteamAdvantage_1.10.2-2.2.0.jar
34A0C46C7B36DEF313F35720BD788539BDEECAD3C30880D30D82BC84E35AD9A5
ElectricAdvantage_1.10.2-2.2.0.jar
61AE5343A79F518F54F1717D508EA3F6DA7209EE69B0C4433E0B7A9722213EE0
```

All runtime input jars are copied into the disposable server and recorded by
source path, filename, and SHA-256 in `input-jars.json`. Libraries and Forge
launchers are staged from previously qualified local server installations.

## Passing matrix

Every row passed conversion, full-process restart, and before/after restart
stability. Each chained row also passed the intermediate 1.12 comparison.
The item column counts stack quantities across the tile witnesses, not distinct
item types. Block witnesses include inventory chests and hidden helper states.

| Source | Route | Block witnesses | Tile witnesses | Stored item quantity | Result |
| --- | --- | ---: | ---: | ---: | --- |
| Original Cyano 1.10 | 1.10 -> 1.13 | 355 | 332 | 2,654 | PASS |
| Original Cyano 1.10 | 1.10 -> maintained 1.12 -> 1.13 | 355 | 332 | 2,654 | PASS |
| Early local 1.10 | 1.10 -> 1.13 | 355 | 332 | 2,654 | PASS |
| Early local 1.10 | 1.10 -> maintained 1.12 -> 1.13 | 355 | 332 | 2,654 | PASS |
| Maintained 1.10 | 1.10 -> 1.13 | 359 | 336 | 2,706 | PASS |
| Maintained 1.10 | 1.10 -> maintained 1.12 -> 1.13 | 359 | 336 | 2,706 | PASS |
| Early local 1.12 | 1.12 -> 1.13 | 355 | 332 | 2,656 | PASS |
| Maintained 1.12 | 1.12 -> 1.13 | 359 | 336 | 2,708 | PASS |

There is no published Cyano 1.12 source fixture in this matrix. Do not describe
the early local 1.12 port as an original public release.

## What the oracle checks

The legacy probe enumerates the installed registries and each distinct valid
metadata state. It places isolated machines, tanks, conduits, filters, helpers,
and source-fluid blocks, plus chest-held copies of every registered Advantage
item. It seeds inventories, tank contents, temperature, progress, energy,
battery NBT, durable-item damage, custom item NBT, and the four musket
enchantments through the source version's own serializers. Filled Forge
buckets are added where that source version registers them.

The resulting **serialized source state**, rather than the requested seed,
is the expectation. A serializer that never stored a field cannot retroactively
provide that field during conversion; historical defects remain separate issues.

The probe freezes tile ticking in explicitly marked disposable worlds before
capturing the baseline and each conversion. Fluid sources are sealed because
scheduled fluid ticks are not frozen. Each fixture is saved, stopped, and hashed;
conversion always starts from a new copy. No source fixture is reused as a runtime.

The oracle compares registry identities, all serialized block properties, and
recursive binary NBT. It requires the original keys, values, ordered inventory
entries, quantities, fluid levels, energy bits, progress, temperature, damage,
and enchantment identities/levels to survive. It permits new target-version
default fields, but not missing old fields or changed stack-list lengths.
Only declared serialization equivalents are normalized: namespaced fluid names,
legacy tile aliases, the old presence-based empty-tank marker, durable-item
damage, saved-registry enchantment IDs, and full 1,000 mB oil bucket identities.
Five Python tests explicitly demonstrate that item loss, duplication, fluid loss,
energy loss, and incorrectly resolved enchantments fail the comparison.

## Migration defects found and repaired

1. **Numeric musket enchantments:** vanilla flattening interpreted saved mod
   enchantment numbers as unrelated vanilla enchantments or null. Power now
   resolves them from that world's saved Forge enchantment registry before
   vanilla datafixing, including nested and stored enchantment lists.
2. **Prefixed Power/Steam tile IDs:** early 1.12 saves contained
   `minecraft:poweradvantage...` and `minecraft:steamadvantage...` IDs. Explicit
   aliases now retain those tiles and their contents rather than skipping them.
3. **LED tile alias:** the saved `electricadvantage:l_e_d` spelling was missing
   from Electric's canonical aliases. It now maps to the preserved LED tile.

The failed pre-repair comparisons remain in ignored local run directories.
Regression tests, changelogs, dependent Power deobf pins, and rig artifact hashes
were updated. All three FG7 release suites passed after these fixes, including
Java 8/dependency/archive and Eclipse production-classpath audits.

## Operation after conversion

A fresh copy of the original-release chained result was commissioned using
`W-01,W-04,S-01,S-04,S-06,E-01,E-02,E-03,P-API-01`, then saved, stopped completely,
and restarted. These are rebuilt Works stations inside the converted world,
not a claim that every seeded legacy machine has been wired up and run.
Unpowered `S-05` and `E-06` orientation checks supplement that run.

The first all-station diagnostic exposed two misplaced drill assertions:
Steam's downward and Electric's upward drill checks pointed at different heights
from their placed drills. A red-to-green manifest test now guards those positions.
Inert distillation/elevator bays are not treated as commissioned operation tests.
Station reports retain `MANUAL_REQUIRED` when GUI or gameplay observation remains;
passing automated assertions does not promote them to a manual PASS.

## Remaining limits and warnings

- Base Metals emits a `basemetals_forge25_leaves_fixer` transformer error on the
  packaged server. Inspection of the actual SRG server jar shows the corrected
  `Typed.set(OpticFinder,Object)` instruction already present, so the repair's
  guard cannot find its old faulty pattern. The Advantage state comparisons
  pass, but the logs are **not error-free**. No Base Metals code was changed here.
- Forge 25 also logs config-watcher messages and, on some shutdowns, failure to
  remove its temporary mod-pack directory. Servers reached readiness and exited
  normally; these messages remain in the retained logs rather than being hidden.
- Client rendering/GUI acceptance, loose player data, moving entities, ownership,
  moving drills and chunk unload are not qualified by this frozen-tile matrix.
  Subsequent [player and entity qualification](PLAYER-AND-ENTITY-MIGRATION.md)
  repairs the direct 1.10 wolf and 1.13 pre-tick turbine defects and passes the
  enumerated player-state checks. All eight strict entity routes also pass with
  the backported Electric `2.2.2.112021` intermediate; the original 1.12 serializer
  remains a failing historical control, not a silently replaced source fixture;
  real client login/handshake acceptance remains a separate gate. The separately qualified
  [30-minute native-feed endurance and live-network restart](ENDURANCE.md) covers
  selected Works processing/routing/storage cases, not those remaining scenarios.
  Subsequent [chunk and moving-drill checks](CHUNK-AND-DRILLS.md) have their own
  measured coverage and defect verdicts; do not infer gameplay PASS from frozen
  migration state alone.
- A whole-modpack Sylvester conversion was not attempted here. Its unrelated
  mods need their own 1.13 replacements and acceptance checks. The original save
  and its files remain untouched; only two original jar files were read.
- Both Base Metals and Advantage hooks ran together in this initial matrix.
  Subsequent [transformer-order qualification](TRANSFORMER-ORDERING.md) explicitly
  reverses their real application order and preserves identical selected state.
  Backup provenance is order-dependent: the second writer keeps intermediate
  bytes, so a complete pre-upgrade world copy remains necessary.
- The [additional-dimension and entity lifecycle protocol](DIMENSIONS-AND-ENTITY-LIFECYCLE.md)
  adds Nether/End fixtures and separate death/respawn controls. Its measured
  coverage and findings must not be inferred from this frozen-state matrix.

## Retained evidence

Local evidence lives under `PowerAdvantage/run-migration-matrix/` (Git-ignored):

- `full-20261005-r3`: six early/maintained routes. Its two `original110-*` labels
  are historical **mislabels** for the early local rebuilt 1.10 jars; use the
  provenance table above. They are not original Cyano release evidence.
- `original-cyano-20261005-final`: two actual original-release routes, including
  intermediate comparisons, conversion and restart snapshots, and packaged logs.
- `original-cyano-20261005-r1/original110`: hash-checked immutable original fixture.
- `operation-original-chained-final`: focused operation and restart results.
- `qualification-audit-20261005.json`: combined route results, unchanged-fixture
  checks, staged-jar checksum checks, operation statuses, and retained log errors.
- `20261007-repaired-fixtures`: five hash-checked disposable source copies; its
  provenance manifest resolves the historical early-1.10 fixture mislabel.
- `20261007-repaired-r1`: eight passing block/tile routes against the repaired
  packaged jars, including complete restart and idempotence comparisons.
- `20261007-repaired-operation`: focused Works operation/restart against the
  repaired jars in a disposable original-release chained conversion.
- `qualification-audit-20261007-repaired.json`: eight routes, ten unchanged
  original/copy fixture fingerprints and eleven staged-jar manifests verified.
- `20261007-bridge112-r1` and `20261007-bridge112-operation`: all eight state
  routes and the eleven-station operation/restart supplement against the repaired
  `2.2.2.112021` intermediate; the historical source jars remain unchanged.
- `qualification-audit-20261007-bridge112.json`: independent audit of the backport
  matrix, original/copy fixtures and staged artifacts.

Each route contains binary-NBT JSON snapshots, `state-comparison.json`, the
copied world, migration reports, console logs, and `input-jars.json`. The run
root contains `profiles.json` and `matrix-results.json`. Generated worlds and
dependency jars must not be committed.
