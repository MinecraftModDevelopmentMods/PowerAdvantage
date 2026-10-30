# Stronger 1.13.2 Regression Tests

This local, unpublished candidate backports the stricter 1.16 operating and GUI
checks. Earlier acceptance applies to the archived jars, not these repairs.
Versions, registry names and saved-world identities are unchanged.

## What Changed

The tests use finite inputs, completed output, refused transfers, negative
controls and exact state after a full process restart. An animation, a built
bay or an open window is not counted as completed work.

The checks found faults in accepted-energy accounting, conveyor item data and
blocked slots, fluid conservation, growth demand, light sampling, turbine
anchoring and targeting. They also caught unsaved machine timers and energy,
and a steel-frame solidity difference on dedicated servers. Machine windows
retain their names and required captions, with bounded gauges.

Base Metals' leaves data-fixer transformer also rejected an already-correct
Forge method. A separate local repair accepts that exact valid method, retains
the older repair path and still rejects unknown structures. Its original
failing tests remain available. This dependency repair is not published.

## Tested Jars

| Jar | SHA-256 |
| --- | --- |
| PowerAdvantage-2.4.4.113021.jar | `8B02FF2B4477F7694A871F6BCA198DA1F852407C6ABB5024DB71C2291E88B977` |
| SteamAdvantage-2.2.5.113021.jar | `5344B4B898C29BDC3B7E1D3E00AD6ED6F00DEA732693B7D6D67660B9E4529EBE` |
| ElectricAdvantage-2.2.5.113021.jar | `FE2FA41F38EA7FDFCEFA4A9FE0C8E2956D22AC483B9E9078370456B003BC5498` |

Migration and later runtime profiles use the local repaired Base Metals jar,
SHA-256 `C466B1F966399599EBE5AF47DFB97898DB98541C782650B01A9C5D6E560C0547`.
Compile-only API pins remain unchanged. Each receipt records its exact harness
and dependency hashes; the operating, readiness and bounded drill-observation
extensions are not presented as one identical harness binary.

## Completed Checks

- Full build, release, dependency, Java 8 bytecode, archive and Eclipse audits.
- 277 Python protocol tests, including deliberately broken measurements and oil-transfer safety controls.
- 126 natural-tick operating jobs and 126 exact-state restart comparisons.
- 271 survival-harvest cases on each of two boots.
- Eight frozen direct/chained migration routes and five connected source profiles, each running two 2,048-tick intervals across restart without rebuilding imported machines.
- Thirty-minute repeated-batch runs in fresh and migrated-copy worlds, followed by restart and resumed operation. These commission Works bays separately from the imported-machine tests.
- Real chunk unloading, partial networks, eighteen extended drills, repeated moves, scarcity, blocked output and redstone interruption.
- Three pipe-only bridge interruption/recovery cycles.
- All 21 walkable bays in packaged-current and decoupled profiles; remaining manual checks are not relabelled as automated passes.
- Five recipe-unlock boots and eighteen manufacturing checks across the three recipe modes.
- Nine redstone witnesses and four native oil bucket/drain/descending-pipe/tank lanes through two complete restarts.
- Native GUI captures: 39 windows, 351 required-caption cases and 355 rejected broken controls.
- Two independent source exports per mod in paths containing spaces; all twelve artifact pairs match. This used the existing pinned cache, not a new cold-cache qualification.

The old all-bay drill finding was an assertion fixed to the original position.
The replacement checks exactly one drill at either the original position or the
declared adjacent track destination, including facing. Missing, duplicate or
arbitrarily relocated drills still fail. No production drill behaviour was
changed to satisfy that check.

## Evidence And Limits

Receipts are retained under
`D:/PowerAdvantage/_verification/backport-coverage-20261030/` and
`test-rig/build/coverage-20261030-candidate-{e,f}-*`.
Oil evidence is `test-rig/build/native113-oil-coverage-f`.
GUI evidence is `build/client-resource-runs/20261030-132855-8450c657`;
it uses the same three Advantage production builds but the earlier Base Metals
input. The dependency transformer repair is proved by separate server runs.
Old failures and superseded candidates are retained, not overwritten.

These are bounded scenarios, not proof of every network or third-party mod.
No new player/entity/dimension matrix, world-generation, optional-power,
Mineralogy-oil or cold-cache result is claimed by this pass. Historical results
in the other reports remain historical until rerun on these exact jars.
Worldless GUI checks do not prove in-world animation or multiplayer behaviour.
Manual acceptance of changed windows and release review remain separate.
Nothing has been committed, pushed or published as part of this backport.

## Generator Follow-Up

This is a pre-final Steam-control checkpoint. It covers only the exact profile
and hashes below, before the later Steam first-tick and per-case freeze repairs.
It does not qualify newer SteamAdvantage jars or harness builds, even when the
version and filename are unchanged. Those candidates still need the final sweep.

This 30 October checkpoint covers profile `pa113-candidate-g`. The generator now
reads redstone inhibition before its first power update, preventing one unwanted
startup pulse. Generation rates are unchanged. Power and Steam retain their
earlier hashes; Electric and the harness have new hashes. The earlier results
above remain attached to their original inputs, not automatically to this set.

The input manifest is
`D:/PowerAdvantage/_verification/backport-coverage-20261030/pa113-candidate-g/inputs.json`,
SHA-256 `4AFC26B766670745B3C9E503B184F704E6A5227D6D102D5FD244163911892AC4`.

| Follow-up input | SHA-256 |
| --- | --- |
| PowerAdvantage-2.4.4.113021.jar | `8B02FF2B4477F7694A871F6BCA198DA1F852407C6ABB5024DB71C2291E88B977` |
| SteamAdvantage-2.2.5.113021.jar | `5344B4B898C29BDC3B7E1D3E00AD6ED6F00DEA732693B7D6D67660B9E4529EBE` |
| ElectricAdvantage-2.2.5.113021.jar | `E7599456DE4AC44D52F3F4A43CDA01768C7B136DFE5C3CAEC1634F2FEF769EEA` |
| AdvantageWorksTestHarness-1.1.0.113021.jar | `A5B1AD1F3BC73E8431DC16757ED310946631B475AA761507847EB76E1AC19D1C` |
| BaseMetals-3.0.1.113021.jar | `C466B1F966399599EBE5AF47DFB97898DB98541C782650B01A9C5D6E560C0547` |
| OreSpawn-4.1.0.113021.jar | `66C9CC5F8BE8F08F3DD5FA26EBEA79B52972B2FB86CB4DADBEEBC29AC01D3435` |

Base Metals is still the local, unpublished transformer repair described above.

| Check | Follow-up result and scope |
| --- | --- |
| Electric release suite | PASS: build, unit, release, dependency, bytecode, archive and Eclipse checks. |
| Operating jobs | PASS: all 126 natural-tick jobs and all 126 exact cold-restart comparisons. |
| Solar startup control | PASS within those 126 jobs: lit and dark controls plus a lit, redstone-inhibited panel with zero energy from its first pulse. |
| GUI captures | PASS: 39 windows, 351 required-caption cases and 355 rejected broken controls. |
| Frozen migration | PASS: eight routes, including conversion and restart state/resource comparisons. Frozen state is not proof of operating imported networks. |
| Connected source machinery | PASS confirmed: all 5/5 routes on profile g. This covers the declared connected fixtures, not every player-built network. |
| Paired endurance | PASS: fresh and migrated-copy worlds, each running 1,800 seconds, 30 batches and 33 verified checkpoints, then full restart and resumed processing. |

Cold comparisons include block states and typed NBT, including unexpected extra
keys. Only the declared zero-volume tank representation is canonicalized; the
tests do not write expected state to make a comparison pass.

Endurance covers commissioned Works bays in `fresh113` and
`original110-chained113-native113`, not untouched imported machinery or every
machine. Outstanding station checks remain `MANUAL_REQUIRED`.

Packaged-server receipts are under `test-rig/build/`:

- `coverage-20261030-candidate-g-126/native113-machine-operation-results.json`
- `coverage-20261030-candidate-g-frozen/native113-refresh-results.json`
- `coverage-20261030-candidate-g-connected-current-2/native113-refresh-results.json`
- `coverage-20261030-candidate-g-endurance/native113-endurance-results.json`

The Electric release log is
`D:/PowerAdvantage/_verification/backport-coverage-20261030/pa113-ElectricAdvantage-generator-release-f.log`.
GUI evidence is under `run-client-resource-runs/20261030-154705-e6a26076`.
Its `client-input.json` SHA-256 is
`26A95AC765863D9A4BB0B70BE442BA363C94974D812A2C872FFDB6602884E819`;
its `production-files.json` SHA-256 is
`4F77B90E7ADF69A4ED63F350BF43CE699A74A10C59DE1DC800CFC3DCB7590291`.
The GUI run uses separately hashed development production classes/resources and
deobf dependencies, not the packaged release jars as its runtime classpath.

Only the connected routes listed here were rerun on these inputs. Older oil,
world-generation, optional-power, reproducibility and cold-cache results are not
carried forward as new proof for the changed Electric jar. The newer Steam-control
candidate remains pending its own final sweep. Manual play-testing and release
review remain separate; these local results do not imply a push, merge or publication.

## Final T Candidate

This separate 30 October checkpoint covers the final Steam-control candidate
for Minecraft 1.13.2, profile `pa113-candidate-i`. Power and Electric have
exactly the same release hashes as the generator follow-up; Steam and the
harness have new hashes. Earlier receipts keep their original inputs and scope.
Passing a later candidate does not turn an earlier failed run into a pass.

The campaign pin file is
`D:/PowerAdvantage/_verification/backport-coverage-20261030/final-production-pins-t.json`,
SHA-256 `48CF5EE9B6A4FC4E285DB26B977F945D3673EF6278E3188348C6A74880E50346`.
The packaged-server input manifest is
`D:/PowerAdvantage/_verification/backport-coverage-20261030/pa113-candidate-i/inputs.json`,
SHA-256 `28E9D319A435A22EC0A72B33E30A9B742999E29770A396E4F59C6F1972AC9158`.
These identify the actual jar bytes, not just filenames, versions or a source
commit that may predate local changes.

| Final-t input | SHA-256 |
| --- | --- |
| PowerAdvantage-2.4.4.113021.jar | `8B02FF2B4477F7694A871F6BCA198DA1F852407C6ABB5024DB71C2291E88B977` |
| SteamAdvantage-2.2.5.113021.jar | `390B112711565106999A30830A008E9E9E1B3457F2646B5B10673BA6A6922E67` |
| ElectricAdvantage-2.2.5.113021.jar | `E7599456DE4AC44D52F3F4A43CDA01768C7B136DFE5C3CAEC1634F2FEF769EEA` |
| AdvantageWorksTestHarness-1.1.0.113021.jar | `B46CE658B5E6226BE3B1E17299AD0BAFF74ABCCDA79A448DE62321FD7F1371FC` |
| BaseMetals-3.0.1.113021.jar | `C466B1F966399599EBE5AF47DFB97898DB98541C782650B01A9C5D6E560C0547` |
| OreSpawn-4.1.0.113021.jar | `66C9CC5F8BE8F08F3DD5FA26EBEA79B52972B2FB86CB4DADBEEBC29AC01D3435` |

Base Metals remains the local, unpublished transformer repair recorded above.

| Check | Final-t result and scope |
| --- | --- |
| Release gates | PASS for all three mods. The campaign's 18 mod/version release gates pass across 1.10, 1.12, 1.13, 1.14, 1.15 and 1.16; this checkpoint uses the existing exact-hash Power/Electric results and the new Steam full suite. |
| Operating jobs | PASS: 132 finite jobs on normal server ticks, followed by 132 exact cold-process comparisons. |
| Independent Steam-control audit | PASS: all six control cases, all nine explicit tile-hold records and recomputed full operation/cold checks. |
| GUI checks | PASS: 39 windows, 351 required-caption cases and 355 rejected broken controls. |
| Frozen migration | PASS: 8/8 declared routes, with all route receipts passing against this manifest. Frozen preservation is not operating-network proof. |
| Connected source machinery | PASS: 5/5 declared connected-source routes; imported machines operate and survive a full restart without rebuilding. |
| Endurance | PASS: fresh and migrated-copy profiles, each 1,800 seconds, 30 batches and 33 verified checkpoints, followed by full restart and resumed processing. |
| Reproducibility | PASS: Power/Electric retain their previously proved exact bytes; two independent source exports of final-t Steam match for all four artifacts. |
| Broad campaign | PASS: both requested profiles, `packaged-current` and `packaged-decoupled`, complete before and after restart and pass the independent audit. The separate writer-only qualification also passes. |
| Survival harvesting | PASS: 271 native checks before restart and the same 271 after restart, including failed tools, exact drop quantities, silk touch and solar solidity. |

The independent control audit checks the crusher comparator matrix, first-loaded
crusher with no steam, signalled and unpowered crushers, and signalled and
unpowered furnaces. The comparator case holds four tiles; each other case holds
one. All six cases must record `heldAtMeasuredEnd` and tile identity, removal
and unchanged-NBT evidence. Raw quantities and work are checked against typed
state, not accepted merely because a case reports `PASS`. Holding a measured
tile stops further native ticking; it does not rewrite its state.

Cold comparisons include all block states and typed NBT, including unexpected
extra keys. Only the declared empty named-tank representation is canonicalized.
There are no expected-state writes. The independent audit also binds its four
raw input files by SHA-256. Its auditor hash is
`390460F58C0A907F41EEA878F6DAB64DC2F901DE4385712E2A4CC5F42105AA12`.

Packaged-server receipts are under `test-rig/build/`:

- `coverage-20261030-candidate-i-final-all/native113-machine-operation-results.json`
- `coverage-20261030-candidate-i-final-frozen/native113-refresh-results.json`
- `coverage-20261030-candidate-i-final-connected/native113-refresh-results.json`
- `coverage-20261030-candidate-i-final-endurance/native113-endurance-results.json`

The independent control receipt is
`D:/PowerAdvantage/_verification/backport-coverage-20261030/pa113-candidate-i-final-steam-controls-audit.json`.

Endurance covers commissioned Works bays in `fresh113` and
`original110-chained113-native113`. Across 1.13, 1.14 and 1.15, all six
fresh/migrated-copy profiles pass. This is not a claim about every machine or
untouched imported network; station checks marked `MANUAL_REQUIRED` remain
manual.

Release logs are under
`D:/PowerAdvantage/_verification/backport-coverage-20261030/`:

- `pa113-PowerAdvantage-repaired-release-c.log`
- `pa113-SteamAdvantage-final-owner-callbacks-t.log`
- `pa113-ElectricAdvantage-generator-release-f.log`

Each full suite includes `clean check build javadoc verifyReleaseDependencies
verifyReleaseArtifacts writeReleaseChecksums verifyEclipseProductionClasspath`.
Reproducibility receipts are `qualification.json` in these sibling directories
under the same verification root:

- `PA113 reproducibility a with spaces` (unchanged Power)
- `PA113 reproducibility final-electric with spaces` (unchanged Electric)
- `PA113 reproducibility final-steam-t with spaces` (new Steam)

The release, deobf, sources and Javadoc artifact pairs match in each applicable
record. These use independent clean source exports and paths containing spaces,
with the existing pinned dependency cache. They are not fresh-cache proof.

Final GUI evidence is under
`run-client-resource-runs/20261030-174831-1918e625`.
Its `client-input.json` SHA-256 is
`8C864FDC8169285F3B084862928037E9762CD4C13A226F6230D9FB6912D2830D`;
its `production-files.json` SHA-256 is
`96724253F62EA1A4F5BA0968EAEE1816501AEE192524341686E94B9715973409`.
These are separately hashed development production classes/resources and deobf
dependencies, not the reobfuscated server jars on a client classpath. Passing
these captures does not replace in-world visual or multiplayer acceptance.

Checks outside the declared fixtures and manual acceptance remain separate.
The final extra lifecycle/network/drill scopes below are now qualified.
Earlier oil, world-generation, optional-power and
cold-cache proof is not relabelled for the new Steam/harness inputs. All failed
and partial receipts are retained. This documentation update changes no frozen
code or test protocol, and does not imply a commit, push, merge or publication.

### Final Broad And Harvest Follow-Up

The four final broad campaigns for 1.13, 1.14, 1.15 and 1.16 are now complete.
Each has two requested profiles, not four profiles per version. Both profiles
cover 21 stations before and after restart. On each boot, `packaged-current`
records 96 passing assertions and three skips; `packaged-decoupled` records
95 passing assertions and four skips. Skips remain skips, and all 21 station
manual procedures remain `MANUAL_REQUIRED`.

The original broad reporter could mark a completed prefix as `PASS` while the
other profile was missing. The independent audit requires the complete requested
set, checks the sealed inputs and raw station evidence, and verifies save and
all-dimension shutdown records. Its historical shutdown evidence does not
independently record the process exit code or an explicit save command.
This is bounded broad coverage, not a replacement for the 132 operating jobs,
exact cold comparisons, endurance runs or manual play-testing.

The later reporter-only change passes unit and replay checks: an empty or
incomplete run stays `RUNNING`, a second-profile exception yields `FAIL`, and
the complete run yields `PASS`. Across four versions, 211 controls pass and
one 1.13-only control is not applicable. No native world was rebuilt or rerun
for that reporter repair. Only reporter status/source metadata changes in the
replay; original native receipts and their original protocol hashes are retained.

The summary is
`D:/PowerAdvantage/_verification/backport-coverage-20261030/broad-writer-applied-summary.json`,
SHA-256 `2395A4B113E57ED0F5914FEB784B5AF32DDD3D6EFD09D48984BDE405AC2904BE`.
The line-specific receipts, relative to that verification directory unless
noted, are:

| Evidence | Path | SHA-256 |
| --- | --- | --- |
| Original native broad result | `test-rig/build/coverage-20261030-candidate-i-final-broad/native113-broad-results.json` (this repository) | `E77AB1B99889B46A27E9075282482E60F436D4AC62E5A21D1169DFCD6706DD5C` |
| Independent native audit | `broad-result-audit-113-final13i.json` | `B590F53B4B02078E157CCD4AFA504255E66ABF3DF69B26DDE4DD190BF51166EC` |
| Applied reporter replay | `broad-writer-113-applied-replay.json` | `F2D64202D2A0F5309F7396A7B9CEAA5B399377BF9B5E6B5E6190BB08E5D34CB7` |
| Reporter unit controls | `broad-all-113-controls-applied.log` | `0B23DE3FCAD9697CCF520E4F0FF5D6BF54598DB7CC197CBC4038BCA5D992DB81` |

The broad runner changed from
`493864A0A752CAD617B56B616369E3050B248FCC8A3968694C6D1567FD94C983` to
`809F823E2D1022D164DCA56D85F6F09D3FCE071391FC1B2CEFC0894FA0A1D747`
for reporting only. The archived native proof still names the first hash;
it is not relabelled as a run with the new reporter.

The final harvest receipt is
`test-rig/build/coverage-20261030-candidate-i-final-harvest/native113-harvest-results.json`.
Its target manifest matches the final-t manifest above. Final GUI, harvest and
endurance profiles pass across all six maintained Minecraft lines. The legacy
1.10/1.12 endurance jobs are bounded natural-tick/restart checks, not the paired
30-minute world runs used here. All 18 mod/version release and exact-artifact
reproducibility gates pass; the build scopes and warm-cache limitation above
still apply. Larger migration matrices, unrelated modpacks, third-party power
integrations and manual procedures are not newly qualified by this follow-up.

### Final Network And Drill Follow-Up

The following native receipts are `PASS` on the final-t inputs above. Paths
are relative to this repository's `test-rig/build/`; these qualify the declared
fresh/migrated fixture scopes, not every player-built network.

| Scope | Native receipt |
| --- | --- |
| Lifecycle | `coverage-20261030-candidate-i-final-lifecycle/lifecycle-results.json` |
| Extended drills and partial networks | `coverage-20261030-candidate-i-final-extended/extended-results.json` |
| Pipe interruptions | `coverage-20261030-candidate-i-final-pipe/pipe-only-results.json` |

The pure-validator continuity repair requires at least 40 continuous absent
native ticks with stable load/unload event counters. Observed or hidden reloads
still fail; disjoint absence intervals are not added together. Its 164 focused
controls and independent raw re-audits pass: 24 partial cases per version,
96 across 1.13-1.16. Those re-audits are offline qualification, not new native
runs; the original 1.13, 1.15 and 1.16 extended receipts remain their original
`PASS` results. No Java, production logic, fixtures or native transports changed.

Repair evidence is under
`D:/PowerAdvantage/_verification/backport-coverage-20261030/extended-absence-repair/`.
`completed-summary.json` SHA-256 is
`B0CE16C439D0628F0D3141208BA6E07352CD4F62AEF8105049C6873634CF3E89`;
this version's `raw-audit-113-final.json` SHA-256 is
`89445966104837E4DAC7C0C42F1504B477945E33C36AB1882BEEDB11334B8611`.

The final Python results are
`D:/PowerAdvantage/_verification/backport-coverage-20261030/final-python-units-w.json`,
SHA-256 `AFA8185EF3826E892F4B3F3AE1091698D77B917B1D543F1302D2D0D81213456A`.
All six lines pass: 3,274 tests in total, including one documented not-applicable
skip (3,273 passing tests). These offline tests do not replace native machine
or manual checks. Existing manual, modpack and migration-scope gaps remain open.
