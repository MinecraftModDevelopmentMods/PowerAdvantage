# Stronger 1.15.2 Regression Tests

This local, unpublished candidate backports the stricter 1.16 tests. It replaces
the earlier test record for the repaired code, not the historical release.
Versions and saved-world identities have not changed. Back up complete worlds
and test a disposable copy before upgrading.

## What Changed

The operating tests now measure finite inputs, accepted energy, fluid and item
outputs, negative controls and exact saved state after a complete server restart.
Building a bay, seeing an animation or opening a GUI is not counted as proof that
a machine works. Missing assertions and invalid measurements fail the test.

The checks reproduced and led to repairs for conveyor NBT handling and blocked
slots, rejected power offers, waterlogged pickup, blocked discharge, partial
distillation outputs, growth demand, environmental power, turbine anchoring and
weapon targeting. Machine windows now retain their names, fit long captions and
show bounded gauges. The changelogs describe the player-facing repairs.

## Tested Jars

| Jar | SHA-256 |
| --- | --- |
| PowerAdvantage-2.4.6.115021.jar | `AB65F679C3838A43AD96F49922CF4D1F9C97F269DEB968E411C1C0D3012A67FF` |
| SteamAdvantage-2.2.7.115021.jar | `FF741FAE57E101601F0136E9CEC9B9BC0F11FACAFA51A10BA90CCA4BF338E92C` |
| ElectricAdvantage-2.2.7.115021.jar | `835195F9926789107CE596FA140A76C15107F92A7BA7C25632FC1AB3CDF22528` |

## Completed Checks

- Full release, dependency, bytecode, archive and Eclipse classpath audits for all three mods.
- 669 Python protocol tests and 134 Java harness tests, including deliberately broken result and oracle controls.
- 126 natural-tick operating jobs and 126 exact-state restart comparisons.
- Nine redstone witnesses, checking the first loaded tick and sixteen consecutive operating ticks.
- Fourteen frozen migration routes and ten connected routes, with two 2,048-tick operating intervals across restart without rebuilding old machines.
- Fresh and migrated-copy worlds with commissioned Works bays running for thirty minutes, followed by full restart and resumed operation. Imported machines are checked separately by the connected-route tests.
- Real chunk unloading, partial networks, bridge interruption, extended moving drills and repeated reads.
- All 21 walkable bays in packaged-current and decoupled profiles. Manual checks remain marked as manual.
- Five recipe-unlock boots, eighteen manufacturing checks, capability tests and 271 survival-harvest cases on each of two boots.
- Six oil-transfer lanes, including Mineralogy oil, through two complete process restarts.
- Native GUI captures: 39 windows, 351 caption cases and 355 deliberately broken visual controls. Child renderer checks are explicitly worldless geometry checks.
- Two independent clean source exports per mod, in paths containing spaces. All twelve artifact pairs match the tested jars. This used the existing pinned cache, not a new cold-cache qualification.

## Evidence And Limits

Local receipts are retained under
`D:/PowerAdvantage/_verification/backport-coverage-20261030/` and
`test-rig/build/coverage-20261030-candidate-a-*`. They include immutable input
manifests, old-jar failures, exact dependency hashes, raw logs and corrected
infrastructure attempts. GUI evidence is under
`build/client-resource-runs/20261030-102140-78f0a670`.

These results cover the declared jobs and migration fixtures, not every possible
network or unrelated mod. GUI captures do not replace a human playing at their
chosen scale, and worldless renderer checks do not prove in-world animation.
No new world-generation or fresh-cache result is claimed here. Manual acceptance
of the changed windows and a release review remain separate. Nothing has been
committed, pushed or published as part of this backport.

## Generator Follow-Up

This is a pre-final Steam-control checkpoint. It covers only the exact profile
and hashes below, before the later Steam first-tick and per-case freeze repairs.
It does not qualify newer SteamAdvantage jars or harness builds, even when the
version and filename are unchanged. Those candidates still need the final sweep.

This 30 October checkpoint covers profile `pa115-candidate-b`. The generator now
reads redstone inhibition before its first power update, preventing one unwanted
startup pulse. Generation rates are unchanged. Power and Steam retain their
earlier hashes; Electric and the harness have new hashes. The earlier results
above remain attached to their original inputs, not automatically to this set.

The input manifest is
`D:/PowerAdvantage/_verification/backport-coverage-20261030/pa115-candidate-b/inputs.json`,
SHA-256 `E850FF9D97417FF336015A6340F754723C171D8B991CB8D7BE251F1D60F4E163`.

| Follow-up input | SHA-256 |
| --- | --- |
| PowerAdvantage-2.4.6.115021.jar | `AB65F679C3838A43AD96F49922CF4D1F9C97F269DEB968E411C1C0D3012A67FF` |
| SteamAdvantage-2.2.7.115021.jar | `FF741FAE57E101601F0136E9CEC9B9BC0F11FACAFA51A10BA90CCA4BF338E92C` |
| ElectricAdvantage-2.2.7.115021.jar | `4E6FD9748445AFBE6A32F5E62C954C9569E40DE3691218941CD29189DE3110CE` |
| AdvantageWorksTestHarness-1.1.0.115021.jar | `20CE48CDF2E7E94A11BFBA43AF4223B5E968E0AF6A982E866A297016D451328D` |
| BaseMetals-3.0.2.115021.jar | `3F50AFBEF17800ECADE42A69B81E541E9FA8C6C4C392ED4DD0FE5A66DC9EFF07` |
| OreSpawn-4.1.1.115021.jar | `8E36F1B093F2ACFFB97A2ACD0579A8B585ACBBD6996F3853AFAAE54EBBEC2913` |

| Check | Follow-up result and scope |
| --- | --- |
| Electric release suite | PASS: build, unit, release, dependency, bytecode, archive and Eclipse checks. |
| Operating jobs | PASS: all 126 natural-tick jobs and all 126 exact cold-restart comparisons. |
| Solar startup control | PASS within those 126 jobs: lit and dark controls plus a lit, redstone-inhibited panel with zero energy from its first pulse. |
| GUI captures | PASS: 39 windows, 351 required-caption cases and 355 rejected broken controls. |
| Frozen migration | PASS confirmed: all thirteen routes completed and the runner exited successfully. This is not the earlier fourteen-route set. Frozen state is not proof of operating imported networks. |
| Connected source machinery | PASS confirmed: all 10/10 routes on profile b. This covers the declared connected fixtures, not every player-built network. |
| Paired endurance | PASS: fresh and migrated-copy worlds, each running 1,800 seconds, 30 batches and 33 verified checkpoints, then full restart and resumed processing. |

Cold comparisons include block states and typed NBT, including unexpected extra
keys. Only the declared zero-volume tank representation is canonicalized; the
tests do not write expected state to make a comparison pass.

Endurance covers commissioned Works bays in `fresh115` and
`original110-chained113-native115`, not untouched imported machinery or every
machine. Outstanding station checks remain `MANUAL_REQUIRED`.

Packaged-server receipts are under `test-rig/build/`:

- `coverage-20261030-candidate-b-126/native115-machine-operation-results.json`
- `coverage-20261030-candidate-b-frozen/native115-refresh-results.json`
- `coverage-20261030-candidate-b-connected-current/native115-campaign-results.json`
- `coverage-20261030-candidate-b-endurance/native115-endurance-results.json`

The Electric release log is
`D:/PowerAdvantage/_verification/backport-coverage-20261030/pa115-ElectricAdvantage-generator-release-g.log`.
GUI evidence is under `run-client-resource-runs/20261030-154800-7e167ed6`.
Its `client-input.json` SHA-256 is
`B874A4584B6B24E546B631367DC6FD0BA17EDD872C1F51B4C432EFCE090CAF1F`;
its `production-files.json` SHA-256 is
`DED502B7183D5F2983344F9ABA1896A2DA6AD65C1971BBD3E340757BA6121748`.
The GUI run uses separately hashed development production classes/resources and
deobf dependencies, not the packaged release jars as its runtime classpath.

Only the connected routes listed here were rerun on these inputs. Older oil,
world-generation, optional-power, reproducibility and cold-cache results are not
carried forward as new proof for the changed Electric jar. The newer Steam-control
candidate remains pending its own final sweep. Manual play-testing and release
review remain separate; these local results do not imply a push, merge or publication.

## Final T Candidate

This separate 30 October checkpoint covers the final Steam-control candidate
for Minecraft 1.15.2, profile `pa115-candidate-d`. Power and Electric have
exactly the same release hashes as the generator follow-up; Steam and the
harness have new hashes. Earlier receipts keep their original inputs and scope.
Passing a later candidate does not turn an earlier failed run into a pass.

The campaign pin file is
`D:/PowerAdvantage/_verification/backport-coverage-20261030/final-production-pins-t.json`,
SHA-256 `48CF5EE9B6A4FC4E285DB26B977F945D3673EF6278E3188348C6A74880E50346`.
The packaged-server input manifest is
`D:/PowerAdvantage/_verification/backport-coverage-20261030/pa115-candidate-d/inputs.json`,
SHA-256 `0FE14063B5D352AAAA28AA62854836190E7AA5C50668FBCFD92162327C1BE7B1`.
These identify the actual jar bytes, not just filenames, versions or a source
commit that may predate local changes.

| Final-t input | SHA-256 |
| --- | --- |
| PowerAdvantage-2.4.6.115021.jar | `AB65F679C3838A43AD96F49922CF4D1F9C97F269DEB968E411C1C0D3012A67FF` |
| SteamAdvantage-2.2.7.115021.jar | `831E3DD0E3156CF2254EF0BB9A7439FDCEC0290F86AFBC9335BBFF5807F9D357` |
| ElectricAdvantage-2.2.7.115021.jar | `4E6FD9748445AFBE6A32F5E62C954C9569E40DE3691218941CD29189DE3110CE` |
| AdvantageWorksTestHarness-1.1.0.115021.jar | `4AD7BC40A345A0246AE237E7D09428D47B02929DAA29DBF2A0907B13F9FE0334` |
| BaseMetals-3.0.2.115021.jar | `3F50AFBEF17800ECADE42A69B81E541E9FA8C6C4C392ED4DD0FE5A66DC9EFF07` |
| OreSpawn-4.1.1.115021.jar | `8E36F1B093F2ACFFB97A2ACD0579A8B585ACBBD6996F3853AFAAE54EBBEC2913` |

| Check | Final-t result and scope |
| --- | --- |
| Release gates | PASS for all three mods. The campaign's 18 mod/version release gates pass across 1.10, 1.12, 1.13, 1.14, 1.15 and 1.16; this checkpoint uses the existing exact-hash Power/Electric results and the new Steam full suite. |
| Operating jobs | PASS: 132 finite jobs on normal server ticks, followed by 132 exact cold-process comparisons. |
| Independent Steam-control audit | PASS: all six control cases, all nine explicit tile-hold records and recomputed full operation/cold checks. |
| GUI checks | PASS: 39 windows, 351 required-caption cases and 355 rejected broken controls. |
| Frozen migration | PASS: 13/13 declared routes, with all route receipts passing against this manifest. Frozen preservation is not operating-network proof. |
| Connected source machinery | PASS: the complete aggregate confirms all 10/10 declared routes, with both five-route shards bound to this manifest. |
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

The connected aggregate is `PASS` for five `legacy/*` routes and five
`native114/*` routes. Its two shard receipts remain `PASS_SHARD`; the aggregate
checks their exact hashes, complete distinct route set and shared target and
route manifests. This qualifies the ten declared routes, not the earlier
fourteen-route proof. The aggregate receipt SHA-256 is
`3AE09D722062366784F8D9F8F9437935B4468F86FEFCCA917EA23A8592395FEE`.

Packaged-server receipts are under `test-rig/build/`:

- `coverage-20261030-candidate-d-final-all/native115-machine-operation-results.json`
- `coverage-20261030-candidate-d-final-frozen/native115-refresh-results.json`
- `coverage-20261030-candidate-d-final-connected-0/native115-campaign-results.json`
- `coverage-20261030-candidate-d-final-connected-1/native115-campaign-results.json`
- `coverage-20261030-candidate-d-final-connected-aggregate.json`
- `coverage-20261030-candidate-d-final-endurance/native115-endurance-results.json`

The independent control receipt is
`D:/PowerAdvantage/_verification/backport-coverage-20261030/pa115-candidate-d-final-steam-controls-audit.json`.

Endurance covers commissioned Works bays in `fresh115` and
`original110-chained113-native115`. Across 1.13, 1.14 and 1.15, all six
fresh/migrated-copy profiles pass. This is not a claim about every machine or
untouched imported network; station checks marked `MANUAL_REQUIRED` remain
manual.

Release logs are under
`D:/PowerAdvantage/_verification/backport-coverage-20261030/`:

- `pa115-PowerAdvantage-repaired-release-4.log`
- `pa115-SteamAdvantage-final-owner-callbacks-t.log`
- `pa115-ElectricAdvantage-generator-release-g.log`

Each full suite includes `clean check build javadoc verifyReleaseDependencies
verifyReleaseArtifacts writeReleaseChecksums verifyEclipseProductionClasspath`.
Reproducibility receipts are `qualification.json` in these sibling directories
under the same verification root:

- `PA115 reproducibility d with spaces` (unchanged Power)
- `PA115 reproducibility final-electric with spaces` (unchanged Electric)
- `PA115 reproducibility final-steam-t with spaces` (new Steam)

The release, deobf, sources and Javadoc artifact pairs match in each applicable
record. These use independent clean source exports and paths containing spaces,
with the existing pinned dependency cache. They are not fresh-cache proof.

Final GUI evidence is under
`run-client-resource-runs/20261030-174921-3d82fca2`.
Its `client-input.json` SHA-256 is
`051BD475024A6BBFD5AAE866FCB724B5034703B50A9B7DDDB688B582E62EEAAE`;
its `production-files.json` SHA-256 is
`353D77C157D2E057FB19BD494DD3D7421F74DF8F48348E442AEB3741739DE3DF`.
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
| Original native broad result | `test-rig/build/coverage-20261030-candidate-d-final-broad/native115-broad-results.json` (this repository) | `0445B47F2E815BF2DD6FDDAC0730BE0CCE2CD6780CE2EC2BE887698179199274` |
| Independent native audit | `broad-result-audit-115-final15d.json` | `8319A8CBBB66FCA59F703707B222EC77DEAD6BD0E895890F1CFCE7FCFD3763AE` |
| Applied reporter replay | `broad-writer-115-applied-replay.json` | `67DCE96D51BD9690921876079DC370DC5D429D524CB447C9110B1BC3CE8C48EE` |
| Reporter unit controls | `broad-all-115-controls-applied.log` | `4A3F124AE17C1BC4E13C620137C1584A9489649F648C42740E16BD365EB17128` |

The broad runner changed from
`1FE6DBC3BEBF629C8055D19AD4EA32A77CF1655A9B633D652EE3D22135129CAF` to
`B473EFD5C49CD1D894F26D0FCC02EBB8AE86F283C4FF426D520D0D8229D2F829`
for reporting only. The archived native proof still names the first hash;
it is not relabelled as a run with the new reporter.

The final harvest receipt is
`test-rig/build/coverage-20261030-candidate-d-final-harvest/native115-harvest-results.json`.
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
| Lifecycle | `coverage-20261030-candidate-d-final-lifecycle/native115-lifecycle-results.json` |
| Holder partial / bridge | `coverage-20261030-candidate-d-final-holder-partial/native115-holder-partial-results.json`; `coverage-20261030-candidate-d-final-holder-bridge/native115-holder-bridge-results.json` |
| Extended reads / original holder-extended | `coverage-20261030-candidate-d-final-extended/native115-extended-reads-results.json`; `coverage-20261030-candidate-d-final-holder-extended/native115-holder-extended-results.json` |

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
this version's `raw-audit-115-final.json` SHA-256 is
`F7F7DDA1244CD0C467C81B21272FEBD208C2302B311502E561E26A2694D5EA3F`.

The final Python results are
`D:/PowerAdvantage/_verification/backport-coverage-20261030/final-python-units-w.json`,
SHA-256 `AFA8185EF3826E892F4B3F3AE1091698D77B917B1D543F1302D2D0D81213456A`.
All six lines pass: 3,274 tests in total, including one documented not-applicable
skip (3,273 passing tests). These offline tests do not replace native machine
or manual checks. Existing manual, modpack and migration-scope gaps remain open.
