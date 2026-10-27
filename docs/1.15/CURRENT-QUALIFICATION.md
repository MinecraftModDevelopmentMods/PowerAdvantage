# Advantage 1.15.2 Test Record

This is the local test record for Power Advantage 2.4.6.115021 and Steam and
Electric Advantage 2.2.7.115021 on Forge 31.2.57. It is not a published release.
The 1.14 versions were accepted separately; none of those results is counted
as a 1.15 pass.

**Back up the whole world before upgrading. Use a disposable copy first.**
These tests cover the Advantage fixtures and their pinned dependencies, not
every mod or every possible player-built network.

## Candidate

| Release Jar | SHA-256 |
| --- | --- |
| PowerAdvantage-2.4.6.115021.jar | `F86454B9B5282A195F5C8FC58BEDEDB751DE3EB55039A9AC3A9A8495B1C0293C` |
| SteamAdvantage-2.2.7.115021.jar | `564F730D3D0C79A0C73776120D40D634C668B7C7A8E2E85350A8C753FE64C3D2` |
| ElectricAdvantage-2.2.7.115021.jar | `C4084B709221C9D9E9175B342B692C3CF4278790EEA1AA88FFBBF8150354249E` |

The current test installation uses local Base Metals 3.0.2.115021 and OreSpawn
4.1.1.115021. Mineralogy-specific tests use Mineralogy 6.0.1.115021.
Production and dependency jars are archived by hash before testing; a later
build cannot silently replace a running test's inputs.

Base Metals 3.0.2 fixes an intermittent world-data
hook registration race found during these tests. Its 64 tests, release audits
and Eclipse checks pass. The earlier failed migration remains in the record;
the affected migration, operating and endurance checks have been repeated
with this exact dependency. The Advantage production jar hashes above have
not changed.

The same direct-registration pattern is present in the read-only Base Metals
1.14 reference. That is a separate follow-up risk, not a reproduced 1.14
failure or permission to change its published release. This fix covers the
local 1.15 branch only.

## Results

| Check | Result | Scope |
| --- | --- | --- |
| Release suites and Eclipse classpaths | PASS | Power 66, Steam 68 and Electric 45 tests; Java 8 bytecode, resources, dependencies and archive checks |
| Cold-cache, paths with spaces and reproducible builds | PASS | Earlier cold bootstrap plus two current-jar exports per mod; twelve matching artifact pairs, pinned JDKs, and no downloaded toolchains |
| Frozen world conversion | PASS with corrected dependency | All fourteen F original/maintained direct and chained routes, including a newly generated 1.14 source; full restart and idempotence |
| Connected old machinery | PASS with corrected dependency | All ten F routes; preservation and two 512-tick intervals across full restart, without rebuilding or refilling |
| Recipes and manufacturing | PASS with corrected dependency | Fresh F exports: five unlock boots and eighteen progression checks; all three modes, aliases, reload and restart |
| Native energy, fluids, portable tanks and locks | PASS on previous dependency | Simulation, exact conservation, invalidation, retained contents and full restart; unchanged Advantage jar bytes |
| Mining, loot and block properties | PASS on previous dependency | 271 strict cases per boot, two boots; real survival tools, silk touch and all wire/scaffold connection masks |
| Chunk lifecycle, partial networks and extended drills | PASS on previous dependency | Five complete campaigns; two deliberately invalid controls rejected; fresh and migrated worlds with real chunk events |
| Players, entities, dimensions and hook ordering | Complete with explicit vanilla failures | F: 108 clean qualifying boots, 8 actor routes, 6 storage layouts, 6 ordering cases and 72 respawn cases pass; strict wolf-health comparisons remain FAIL |
| Complete walkable Works | PASS with corrected dependency | All 21 bays rerun in F current/decoupled profiles; 96/95 passing assertions, 3/4 explicit skips; all bays retain manual checklists |
| Oil transport | PASS on preceding input set | Six water/Advantage/Mineralogy lanes; exact bucket/drain/pipe/tank quantities across two full restarts; this profile contains no Base Metals |
| World generation | PASS with corrected sampler | All three F profiles, including Mineralogy and legacy import, complete and retain exact counts across restart |
| Thirty-minute operation | PASS with corrected dependency | Both F worlds complete 1,801.58 seconds, full restart and resumed operation; raw conservation and strict-log audits pass |
| Client resources and recipe-book rendering | PASS with manual gaps | Actual 1.15 client; 94 items, 905 states, both floor faces, gauges, six fluids, three recipe modes and 25 geometry cases |
| Maintainer visuals and recipe unlocks | ACCEPTED 2026-10-27 | In-game visuals and unlocks reported good; machine functionality relies on the recorded automated checks |
| Remaining interactive checks | Not individually qualified | The bounded tests and visual acceptance do not establish every control, weapon, menu or multiplayer interaction |

The harness passes 95 Java tests and the final Python adapter suite passes 575
tests. These validate the test machinery; they are not counted as machine or
world-migration cases.

The actor fixtures expose a vanilla migration difference: a tamed wolf saved
with 8 health loads with 20. Exact health preservation remains a failed check;
it is not hidden by accepting an item or machine test. The final actor record
retains the engine source explanation and affected routes.

The completed F dimension matrix has sixteen route executions with eighty
exact-item checks and passing load/save/shutdown controls. Its wolf-health
differences remain strict failures. A separate failed respawn attempt exposed
the harness rewriting a live JSON definition while Java read it. The adapter
now publishes complete JSON by same-directory atomic replacement; red/green
tests prove that readers never see a truncated file. The three failed boot logs
are retained and excluded from qualification, and the complete respawn matrix
was rerun from untouched sources. No item expectations, retries or Java
machine code were changed for that repair.

The specialized core, mining and capability results retain their original D
dependency manifest. The Advantage binaries are identical, and the Base Metals
patch changes only its hook registration, version metadata and two packaged
documentation references. The F reruns target that startup/migration change
and repeat the complete Works, recipes, client, frozen/connected migrations,
actor matrix and endurance. The table does not relabel older dependency runs.

## Evidence

Raw reports remain local under ignored `test-rig/build/`. Failed attempts are
retained, and reruns use new directories. The current archived manifest is
`native115-inputs-20261026-final-f-archived.json`, SHA-256
`A9BCD5A442F6332010F9D467BD00A685AA953339C39545812B32B825B96E64AA`.
It uses harness E, SHA-256
`C854AFC08344C9B5E53AC8B35F9AA0B3E3F60E8180BF1C5F40AC7B5DF594890B`.
Base Metals is pinned to local commit `ac1781e7`, release checksum
`3F50AFBEF17800ECADE42A69B81E541E9FA8C6C4C392ED4DD0FE5A66DC9EFF07`.
Earlier manifests and runs remain separate; a passing older run is not a
claim that the corrected dependency has been tested in that same setup.

| Evidence Directory | Purpose |
| --- | --- |
| `native115-frozen-20261026-f` | Fourteen frozen conversion routes with the corrected dependency |
| `native115-connected-20261026-f` | Ten connected operating routes with the corrected dependency |
| `native115-broad-20261026-f` | Full current and decoupled Works with the corrected dependency |
| `native115-recipes-20261026-d` | Recipes, discovery and progression |
| `native115-interop-20261026-d` | Native capabilities, tanks and locks |
| `native115-harvest-20261026-d` | Real survival drops and fixture isolation |
| `native115-endurance-20261026-f-isolated` | Full measured endurance, restart and independent raw audit; failed D run retained separately |

Current client evidence is indexed by
`build/client-resource-compile-runs/final-f-full-client-provenance.json`.
The earlier D run remains at
`build/client-resource-runs/20261026-130632-3753a0c0/`.
The recipe capture has 8,585 qualifying pixels against the unchanged
`>1,000` threshold. Fresh ECJ production and test outputs passed all three
mods' 66/68/45 tests; selected Power class/resources matched its development
jar byte-for-byte.
Cold-cache bootstrap evidence is under
`D:\PowerAdvantage\_verification\pa115-clean-cold-20261026\Fixed JDK retry\`.
Both exports passed the full release gates using Java 8u502 for compilation
and tests. The earlier system-property attempt is retained but does not prove
toolchain isolation. The corrected run passes Gradle project properties;
automatic detection and download are both disabled. Its source revisions are
Power `e3da24b`, Steam `bced14a` and Electric `a73d1ad`; subsequent workflow
guards passed locally. Candidate D additionally repairs recipe-condition
registration, the shared creative-tab guard and Steam's native track/platform
properties. Both independent D exports passed 66/68/45 tests and every release
gate, with all twelve artifact pairs identical. The D report is under
`D:\PowerAdvantage\_verification\pa115-clean-cold-20261026\Candidate D\`.
The missing local Steam recipe-regression red log was reproduced separately
from its exact old source and old Power pin; that new record is labelled as a
reproduction, not the original log.

The first endurance run was stopped because its candidate was superseded. Its
18 measured batches and save-only cleanup are retained, but it is explicitly
`SUPERSEDED_INCOMPLETE`, not a 30-minute pass. Later runs qualify only their
own full 1,800 seconds, restart and resumed operation.

Candidate D then completed all 30 batches in both worlds over 1,801.58 seconds,
with 36,536/36,033 game ticks, full restart and resumed processing. Its overall
report remains **FAIL**: the fresh setup logged
`PointOfInterestData: POI data mismatch: never registered at (-126,5,27)`
before the Works finished construction. The normal strict log auditor rejects
it. Successful quantities are useful evidence, but do not erase that failure.

Candidate F then passed both worlds for 1,801.58 seconds, with 36,617/35,992
ticks, full process restart and resumed processing. The independent raw audit
reconstructs 33 checkpoints per world. The fresh fixture uses the original
failing seed but disables structures before creating the blank test world;
raw `level.dat` proves `MapFeatures=0` and the old composter coordinate is air
before construction. The imported world's existing structure setting remains
`MapFeatures=1`. No POI entries, existing-world data, expected quantities,
timing thresholds or watchdog settings were changed to obtain the pass.

The original Mineralogy world-generation scan requested and counted 3,267
chunks in one synchronous test command. Its tick exceeded the unchanged
60-second watchdog. The resulting counts are retained but do not qualify
that failed run. The harness was changed to distribute the same work
across ticks, preserving centers, radius, population halo, height range,
quantities and biome checks; no production-mod fix or watchdog relaxation
was used to obtain a pass.

The bounded Mineralogy rerun completes both scans of 3,267 chunks and keeps
exact counts across its restart: 6,759 Power oil, 8,945 Mineralogy oil, 2,975
lithium ore and 47,136 Mineralogy sulfur ore blocks, with zero Electric sulfur
and zero oil biome violations. These are observations from the new world,
not a claim that every count equals the earlier watchdog-failed world's count.
See [client checks](CLIENT-RESOURCES.md), [core runtime checks](CORE-RUNTIME.md),
[mining checks](HARVEST-BASELINE.md) and [repeatable commands](TEST-HARNESS.md).

## Manual Checks

The worldless renderer probe cannot construct a real hydroelectric turbine
because that entity needs a world. Its renderer and texture are registered,
but its in-world orientation and animation remain manual checks. Real-world
render dispatch, animation interpolation, screen interaction and live menu
synchronization also need personal acceptance.

The practical manual checklist is:

- Check creative-tab entries, names, models, transparency and tooltips.
- Collect the raw recipe ingredients and inspect recipe-book unlocks in the intended mode.
- Open real machine screens while working and after restarting, comparing visible gauges with operation.
- Check in-world drills, the turbine, turret beams and elevator motion.
- Try elevator controls, musket use and turret ownership/targeting in a disposable proving ground.
- Join with a real client and check dimension travel, the death screen and respawn. Server tests use a dummy network handler.

The available tests are bounded examples, not every possible factory layout,
redstone circuit, recursive fabricator recipe graph or third-party modpack.
No BaseMinerals 1.15 jar is included in this tested set, so its material-owner
combination remains outside runtime qualification rather than an implied pass.
A dedicated set of 1.14-origin player/entity/storage/respawn fixtures has not
been generated. The passing 1.14-origin frozen and connected machine fixtures
are separate evidence, not a substitute for those actor cases.
A complete Sylvester modpack upgrade is not established by these bounded
Advantage fixtures; unrelated mods need their own matching ports and checks.

Import the three `PA115` repositories into Eclipse as existing Gradle projects.
Power's generated `runClientAllAdvantage.launch` loads all three; its ordinary
`runClient.launch` remains Power-only. Preparing either launch does not open,
convert or approve a saved world.

Final release and Eclipse checks were rerun for all three projects using the
normal shared cache, `D:\GradleCaches\minecraft-mods-shared`. Their Buildship
settings no longer point at the private reproducibility cache. The disposable
[manual server](MANUAL-TESTING.md) is prepared and passes `-CheckOnly`; it has
not been started.

The maintainer authorized source pushes and merges on 27 October 2026 after
accepting the visuals and recipe unlocks. The remaining limits above stay
explicit. A source merge does not authorize a tag, CurseForge upload or release.
