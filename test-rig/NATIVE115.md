# Native 1.15.2 Harness

Forge 31.2.57, snapshot 20200514-1.15.1, MCP 20200515.085601,
ForgeGradle 7.0.34 / Gradle 9.6.1. Gradle runs on Java 17; Minecraft and
harness 1.1.0.115021 use Java 8. Compilation needs only the exact pinned Power
2.4.6.115021 deobf jar. Steam/Electric 2.2.7.115021 are runtime inputs.

Default tasks use `profiles/native115/`. Pending artifact hashes fail before
runtime creation. All profiles remain UNQUALIFIED until their exact packaged
candidate completes the gates. Client-resource probes are outside this port.

## Campaign Records

The initial 2026-10-26 harness A `test assemble verifyHarnessIsolation` build
passed 52 Java/runner tests with no failures/errors/skips. Runtime evidence is
candidate-specific; later harvest probes and production replacements do not
inherit an A pass. Profiles and input manifests are parent-owned.

- [Disposable114 source readiness](build/native115-disposable114-b/source-readiness.json):
  all 14 frozen and ten connected inputs verified, including six new stopped
  native114 source receipts. This is source approval, not a native115 result.
- [Connected A campaign](build/native115-connected-20261026-a/native115-campaign-results.json):
  10/10 PASS, all initial/restart preservation comparisons, 512+512 measured ticks,
  continued output and unchanged full source fingerprints. The
  [final verification log](build/native115-connected-20261026-a-final-verification.log)
  rechecks all 70 protocol gates, actual tick counts and archived jars.
- [Endurance A corrected record](build/native115-endurance-20261026-a/native115-endurance-results.corrected-attribution.json):
  SUPERSEDED_INCOMPLETE after 18/30 passing minute batches in each environment.
  The parent coordinating assistant requested the stop for replacement
  recipe-queue, creative-guard and Steam-track-property fixes; the human user
  did not request it. This is neither a machine failure nor an endurance PASS.
  The 1800-second threshold is unchanged; restart/resume and the full raw audit
  were not reached. The [attribution erratum](build/native115-endurance-20261026-a/stop-attribution-erratum.json)
  retains hashes of the unmodified original records.

Connected A and the incomplete endurance run use archived harness release
`7DAE086A7A9ADC879D0990BEB033690E90F599F2B668A3566DCB53CD453A2547` and
[Manifest A](build/native115-inputs-20261026-current-archived.json), SHA-256
`5608065DAA5C45309C9A38D5E420A0E2EFCF2496E6D9F00DA7B5B1836EFDCBA7`.
The original endurance interruption ended the process tree before the
controller's finally block, so its original shutdown flush is unverified.
Separate save-only recovery boots subsequently flushed and stopped both
disposable worlds; those logs do not prove the interrupted shutdown or
endurance restart/resume. See the [termination receipt](build/native115-endurance-20261026-a/superseded-termination.json)
with the linked attribution correction above. No A runtime remains.

These are historical A results, not qualification of the replacement candidate.
See the parent's [current qualification ledger](../docs/1.15/CURRENT-QUALIFICATION.md)
for overall status; manual/client acceptance remains separate.

## Candidate D Records

[Immutable D inputs](build/native115-inputs-20261026-final-d-archived.json) use
manifest SHA-256 `3605B9BBF2B5C94C8278B31B5984969DD55B8F277E3B994496F045FA7CA79BE8`
and harness C `B8F733F37B51A2EE8A0C11F21C1C84336FD8FCCDE998611DA334021C29CC34FA`.
The approved 14/10 source routes remain SHA-256
`A5241F5F41426DC57DD85A15B62B211415BE8B69D004154F8FC47DDF239B35C2`.

- [Frozen D](build/native115-frozen-20261026-d/native115-campaign-results.json):
  14/14 PASS. Its [independent raw audit](build/native115-frozen-20261026-d/independent-raw-audit.json)
  recomputes conversion, restart and idempotence from source/destination snapshots,
  verifies source approvals, jar pins and clean native boot logs, and records full
  source fingerprints before/after the audit.
- [Connected D](build/native115-connected-20261026-d/native115-campaign-results.json):
  10/10 PASS, including all five native114 sources. Its
  [independent raw audit](build/native115-connected-20261026-d/independent-raw-audit.json)
  replays preservation, 512+512 tick operation, object identity/conservation and
  continued output across restart. Original whole-source fingerprints match;
  no construction/refill authority was present.
- [Endurance D](build/native115-endurance-20261026-d/native115-endurance-results.json):
  overall FAIL, retained unchanged. Both physical environments completed 30 batches
  over 1801.578 seconds, with 36536/36033 game ticks, clean restart and a resumed
  minute, but the strict log gate found a vanilla POI ERROR during fresh setup.
  The [standard independent audit log](build/native115-endurance-20261026-d-independent-audit.log)
  correctly refuses certification. The separate
  [raw diagnostic replay](build/native115-endurance-20261026-d/raw-diagnostic-replay-NOT-QUALIFICATION.json)
  reconstructs 33 checkpoints per world but is explicitly NOT an endurance PASS.

The setup ERROR is a generated village `minecraft:composter[level=0]` / farmer
POI at `(-126,5,27)`, cleared to AIR on the W-03 boundary. Saved village/template
metadata and a native before-build POI record identify the block and custody.
The [bounded same-seed setup repro](build/native115-poi-setup-repro-d/poi-setup-results.json)
reproduces first-build ERROR twice with structures enabled; repeat builds are
clean. Preload/drain and two NEW structures-disabled worlds pass the same strict
log gate. See [the diagnosis](build/native115-poi-diagnostic-d/DIAGNOSIS.md).
No ERROR filter, oracle, production jar, harness Java or imported source was changed.
Any approved fresh-fixture isolation fix requires a new full endurance run;
the failed D evidence must not be relabelled.

### Fresh Endurance Fixture Isolation

The authorized fixture-only adapter change rejects an existing world before
writing settings. Only a NEW fresh115 world receives `generate-structures=false`
and the original failed world's seed `5919679243107101036` before creation.
The imported world's native generation settings are read and compared, never
rewritten. Native POI handling, original machine oracles, 60-second watchdog,
300-second startup budget, strict ERROR gate, 30 minute batches, tick minimum,
restart and resumed minute remain unchanged.

All [14 focused guard tests](build/native115-endurance-isolation-focused-tests.log)
pass. The [bounded native adapter proof](build/native115-poi-isolated-adapter-d/fixture-isolation-results.json)
ran at 14:08:02--14:08:17 UTC on 2026-10-26 and saved/stopped cleanly. Before
any Works build, archived raw WorldInfo has `MapFeatures=0`, the exact original
seed and `generatorName=flat`; the native snapshot has AIR at `(-126,5,27)`.
Both subsequent setup builds pass the unchanged strict log gate. Its
[independent raw fixture audit](build/native115-poi-isolated-adapter-d/independent-fixture-audit.json)
also verifies the original D FAIL, five same-seed red/preload/structures-off
cases, complete approved native114 source fingerprints and all 114 protected
historical file hashes. This is NOT an endurance qualification.

The coordinating parent requested a hold before the new 1800-second run could
start because of the separately identified Base Metals persistence race.
`native115-endurance-20261026-d-isolated` was never started or created; no
timed phase was interrupted and no live inputs were swapped. That hold ended
only after the parent supplied immutable candidate F below; no D result
transfers to F.

## Candidate F Endurance

The [new F endurance run](build/native115-endurance-20261026-f-isolated/native115-endurance-results.json)
is PASS for this automated endurance scope. Both worlds entered the unchanged
1800-second measured protocol at `2026-10-26T14:21:58.222497+00:00`, on ports
25590/25591; the controller completed at `2026-10-26T14:53:34.371139+00:00`.
Both completed 1801.578 measured seconds, 30 minute batches, save/restart and
the full resumed minute, with 33 verified ledger checkpoints per environment.
Fresh/copy tick advances are 36617/35992, independently recomputed from raw
setup and batch-30 checkpoints and above the unchanged 32400-tick minimum.

The [independent raw audit](build/native115-endurance-20261026-f-isolated/independent-raw-audit.json)
replays all 33 ledgers and 528 raw station snapshots per world: 66 ledgers and
1056 snapshots total, using the original conservation algorithms. It also
checks exact inventory-command acknowledgements, 13179 before restart and
439 after restart per world. Strict native log checks PASS across all five
boot logs; no ERROR waiver or POI filter was added. Both servers saved and
stopped through the controller with exit code zero, and both leases were free
after completion. No interruption or human stop is attributed to this run.

[Launch/completion provenance](build/native115-endurance-20261026-f-isolated-provenance.json)
records the complete approved source runtime fingerprint, jar identities,
controller/helper hashes, arguments, Java 8 executable and runtime seed files.
The [additional independent provenance audit](build/native115-endurance-20261026-f-isolated/final-provenance-audit.json)
PASS rechecks those hashes, the approved native114 source receipt, actual
saved WorldInfo, clean saved shutdowns and all 114 protected historical files.
The complete source runtime, server seed, F manifest and protocol sources
are unchanged before/after. The original D FAIL is retained with its hash;
this new F PASS does not relabel D or qualify unrelated suites/manual checks.

[Immutable F inputs](build/native115-inputs-20261026-final-f-archived.json) have
SHA-256 `A9BCD5A442F6332010F9D467BD00A685AA953339C39545812B32B825B96E64AA`.
They use corrected Base Metals 3.0.2.115021 source
`ac1781e7cade15045ffe0135077087b4d1722603`, release
`3F50AFBEF17800ECADE42A69B81E541E9FA8C6C4C392ED4DD0FE5A66DC9EFF07`, and
harness E `C854AFC08344C9B5E53AC8B35F9AA0B3E3F60E8180BF1C5F40AC7B5DF594890B`.
The three Advantage release jars retain D's exact bytes, but F results are
independent of D. No inputs are repinned or swapped during execution.

Before construction, [F's native fresh-world proof](build/native115-endurance-20261026-f-isolated/fresh115/fresh-fixture-proof.json)
records actual `MapFeatures=0`, original seed `5919679243107101036`, and AIR at
`(-126,5,27)`. The comprehensive native114 copied source instead retains
`MapFeatures=1`, its original seed `8795347008554539309`, and
[frozen import preservation](build/native115-endurance-20261026-f-isolated/original110-chained113-native115/native115-import-comparison.json)
PASS before any disposable Works construction. Endurance measures rebuilt
Works, not the original imported machinery. The protected source world was
never launched or edited; full before/after fingerprints match.

## Candidate F Migrations

The [F frozen campaign](build/native115-frozen-20261026-f/native115-campaign-results.json)
and its [independent raw audit](build/native115-frozen-20261026-f/independent-raw-audit.json)
PASS all 14 routes: 13 historical direct/chained routes plus comprehensive
native114. The audit independently recomputes all 42 conversion, restart and
idempotence comparisons with unchanged oracles, checks all 28 native boot/save
logs, and hashes every staged F jar and launcher. Source manifest/snapshot
provenance matches the original A524 route file. All source worlds match their
approved hashes; complete source runtimes also match the prior D audit's source
fingerprints and the new audit's before/after hashes. This uses D only as a
source-fingerprint reference, never as an inherited F runtime verdict.

All 114 protected historical files and every existing F campaign evidence file
are unchanged. No server, Gradle build, fixture rewrite or oracle edit was used
for this audit. Audit JSON SHA-256:
`EF0C37E1F1422452AFC5FFCC8FDFC4C68E4D7E3BFDA9F7124A56EBD781AB3226`.
The [read-only auditor](build/audit-migrations-f.py) is new ignored build evidence;
the original campaign report and immutable F/A524 inputs are untouched.

The [F connected campaign](build/native115-connected-20261026-f/native115-campaign-results.json)
and its [independent raw audit](build/native115-connected-20261026-f/independent-raw-audit.json)
PASS all ten cases: five historical sources and all five disposable native114
sources. The same unchanged auditor independently recomputes all 70 gates:
initial/restart preservation, exact 512+512 requested and advanced native ticks,
operating object identity/conservation, continued output and both native boot
proofs per case. All 20 boot/save logs and every staged F jar/launcher pass.
No build/refill authority was present. Source receipt/world hashes, complete
source fingerprints, all 114 protected historical files and existing campaign
evidence remain unchanged. This replay starts no server and runs no Gradle.
Connected audit JSON SHA-256:
`86B6E7BEADA8C766698EC8806551588E51EA6914B35DADAD1F125405B23B121C`.
F connected is independently verified, not inferred from frozen F or connected D.

## Invariants

- Historical profiles, every native114 Python script/test, original semantic
  algorithms and both station source resources remain byte-identical.
  `migration/native115-historical-inputs.json` checks 114 protected inputs.
- Frozen/connected destinations cannot build, reset or refill imported equipment.
  Frozen preservation and connected operating results remain separate.
- Endurance first preserves the copied fixture, then measures rebuilt Works in
  fresh and copied worlds using the retained feeding protocol. It does not prove
  endurance of the original imported equipment. The copied A, D and F source is the
  verified native114 comprehensive fixture despite the retained historical
  runtime label `original110-chained113-native115`.
- Original fixtures are never launched or edited. Output paths must be new,
  under `test-rig/build`, and disjoint from all protected inputs.
- Existing assertions, timings, quantities, normalizations and failure meanings
  remain unchanged. Failed evidence is retained, never overwritten by reruns.
- A fresh fingerprint of an old native114 destination is not approval.

## Engine Adapters

Non-loading observations use `ServerChunkProvider.getChunkWithoutLoading`.
Lifecycle snapshots read actual FORCED tickets and native ChunkHolder/ChunkStatus
calculations without adding tickets or loading chunks. Exact Forge 31 sources
give forced/entity-ticking level 31, FULL 33, maximum holder 44, generation
distance 11 and holder radius 13. Live snapshots must agree before lifecycle
oracles run; load observations require a real forced ticket. The inherited
45-second unload budget is unchanged.

Private NBT constructors use native value factories. Machine clones use
`setWorldAndPos`. Respawn uses the native four-argument spawn setter, disabling
only the optional chat notification; real death and native respawn handling
remain required.

`native115_harvest.py` exercises actual survival
`PlayerInteractionManager.tryHarvestBlock` across two boots. The original fourteen cases
require wrong-tool/under-tier failures, sulfur pickaxe level 0 and 1..4 powder,
lithium level 1 and one ore, silk-touch ore identity, and normal/silk steel-frame
drops remain required. Another 256 cases mine wire/scaffold blocks by hand and
silk touch across every six-way connection mask, checking real collision shapes
and native solidness. The accepted114 baseline is wire=true, scaffold=false;
see [the harvest baseline](../docs/1.15/HARVEST-BASELINE.md). A separate live photovoltaic witness retains its
legacy solid=true property: 271 checks per boot. Loot JSON presence alone cannot
pass. Portable tank break/drop/replace,
retained NBT, capabilities and locked automation remain in the interop runner.

## Native114 Source Generation

`native115_campaign.py prepare` reads the exact accepted frozen-13 and
connected-5 records, original source manifests/checkpoints and approved hashes.
It creates a pending plan, not approval of old destination saves.

`generate114` stages NEW disposable sources with accepted archived target114
jars and copies only approved historical inputs. It reuses native114's strict
conversion/operation algorithms and records receipts after clean shutdown.
The comprehensive source must match all 355 blocks and 332 tiles. All five
connected cases retain conveyor/crusher, electric/steam lanes, both drills and
battery, preserve stock and operate for 512 ticks on each of two boots.
Receipts bind complete runtime/world hashes, jar manifest hashes and semantic
comparisons. No canonical process is launched.

Only then can `run frozen` and `run connected` create 115 copies. Their required
matrix is 13 historical frozen routes plus comprehensive native114, and five
historical plus five new native114 connected sources. There is no reconstruction
or refill in 115. Actors, dimensions, player storage, respawn and transformer
ordering retain their independent original oracles.

See [the command matrix](../docs/1.15/TEST-HARNESS.md) for exact proof paths,
remaining suites, concurrency and qualification status.
