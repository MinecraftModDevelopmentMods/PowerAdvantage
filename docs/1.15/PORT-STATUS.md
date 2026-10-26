# Advantage 1.15.2 Port

## Current Status

Power Advantage 2.4.6.115021 passes its local build/audit suite, 60 unit tests,
Eclipse checks and a packaged new-world smoke test. The smoke test loaded Power,
Base Metals and OreSpawn on Forge 31.2.57, started cleanly, saved and shut down
without errors. It does not establish machine operation, GUI behavior, migration
or combined Steam/Electric functionality. Those checks remain pending.

**Back up the entire world before upgrading. Test only disposable copies.**
No 1.15 migration or whole-modpack acceptance is claimed.

## Source and Target

The local checkout is `D:\PowerAdvantage\PA115\PowerAdvantage`, on
`master-1.15.2`, starting from merged MMD Power commit
`ef08e5cd134dc75bd73518bf4c90a6711e67280f` (1.14 PR 43).
The accepted 1.14 visuals, recipe unlocks and automated results remain historical
evidence. They must not be counted as 1.15 passes. The child 1.14 PR checks are
tracked separately from this port.

| Setting | Target |
| --- | --- |
| Minecraft / Forge | 1.15.2 / 31.2.57 |
| Snapshot mappings / MCP | 20200514-1.15.1 / 20200515.085601 |
| ForgeGradle / Gradle | 7.0.34 / 9.6.1 |
| Gradle runtime / production toolchain | Java 17 / Java 8 |
| Base Metals / OreSpawn | 3.0.1.115021 / 4.1.1.115021 |

The native API changes cover `ActionResultType` activation, `notSolid` block
properties, texture-atlas getters, client render-layer registration and the
still's light-query signature. The legacy migration hook is retargeted to 1.15.
Existing registry and saved-state identities are retained; runtime preservation
still needs qualification.

## Tested Power Artifacts

The reported packaged smoke and local checks used version `2.4.6.115021`.
These hashes identify that candidate, not a published release or a later build.

| Artifact | SHA-256 |
| --- | --- |
| Release | `9C53B15C37FF6F5B7B5D4DEC23C798B12E7FC9C9AFAAC5B2816BE9FCC0626448` |
| Deobf | `E39A0D03A903186E93C2D3D32268E3B7EB5FEA75C53492C05D2B680B5EBF17B8` |

## Qualification Matrix

| Check | Status | Scope or remaining work |
| --- | --- | --- |
| Power production build and full local audit suite | PASS | Build, release/dependency/artifact checks and checksums; not gameplay evidence |
| Power unit tests | PASS | 60 tests; not operating-machine or migration tests |
| Eclipse checks | PASS | Build setup checks; no claim of interactive client operation |
| Packaged fresh-world smoke | PASS | Power + Base Metals + OreSpawn; clean startup, save and shutdown without errors |
| Combined Steam/Electric operation | PENDING | No combined runtime pass recorded here |
| Native capabilities, fluid conservation and portable items | PENDING | Repeat the native runtime witnesses on 1.15 |
| Connected machinery, processing and drill operation | PENDING | Operate bounded assemblies and check saved state after full restart |
| Frozen and connected migration routes | PENDING | Repeat direct/chained routes on disposable copies; compare before ticks and after restart |
| Chunk lifecycle, partial networks and extended drills | PENDING | Genuine unload/reload events, persisted work and conservation |
| Players, entities, dimensions and transformer order | PENDING | Repeat preservation, lifecycle and backup-custody checks |
| Recipes, unlocks, progression and world generation | PENDING | Repeat native recipe modes, discovery and generation checks |
| Client resources and machine GUIs | PENDING | Models, fluids, render layers, screens and synchronization |
| Endurance | PENDING | Measured processing followed by full restart and resumed work |
| Manual acceptance | PENDING | Personal visuals, recipe unlocks, controls and gameplay checks on 1.15 |

Update these results only when the corresponding 1.15 evidence is available.
Startup and compilation are not substitutes for operating or migration tests.
Test-specific evidence reports are maintained separately.

## Delivery Limits

Local 1.15 commits are authorized. No 1.15 push is authorized before manual
acceptance. Earlier 1.14 merge/publication approval does not remove that gate.
No 1.15 push, PR, merge or publication is claimed by this record.
