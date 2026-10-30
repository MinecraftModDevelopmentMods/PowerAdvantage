# Stronger 1.14.4 Regression Tests

This local, unpublished candidate backports the stricter 1.16 tests. The earlier
port acceptance applies to its archived jars, not to this repaired candidate.
Versions, registry names and saved-world identities are unchanged.

## What The Tests Prove

The operating tests measure finite inputs, useful outputs, refused transfers,
negative controls and saved state after a full process restart. Building a bay,
seeing an animation or opening a window is not counted as completed work.

The checks exposed faults in conveyor item data, blocked slots, accepted-energy
accounting, fluid conservation, growth demand, light sampling, turbine anchoring
and targeting. A real dedicated-server harvest test also caught the steel frame
being treated as solid. Its unit test now models the server's stripped rendering
method, rather than relying on the client-only render layer.

Machine windows retain their names, fit required captions and clamp their gauges.
The changelogs describe the player-facing repairs.

## Tested Jars

| Jar | SHA-256 |
| --- | --- |
| PowerAdvantage-2.4.5.114041.jar | `797B904D22AE83BE311175564373DB177E12AC8057ACF67DF74111F73ED47832` |
| SteamAdvantage-2.2.6.114041.jar | `57245CB75A0BAF469BA26F03556FEFD4AF8113D522642ED2BB3A8FA6D0855814` |
| ElectricAdvantage-2.2.6.114041.jar | `DBF0A9DF1D8C0BA25B32E2A88B47BFF7A3ABD83CF99FBB15B86A1A6B8A63AE6B` |

## Completed Checks

- Full build, release, dependency, Java 8 bytecode, archive and Eclipse audits.
- 443 Python protocol tests, including deliberately broken measurements.
- 126 natural-tick operating jobs and 126 exact-state restart comparisons.
- Nine redstone witnesses, checking the first loaded tick and sixteen operating ticks.
- Thirteen frozen migration routes and five retained connected source profiles, with two 2,048-tick operating intervals across restart without rebuilding imported machines.
- Thirty-minute runs in fresh and migrated-copy worlds, then restart and resumed operation. These commission the Works bays separately from the imported-machine checks.
- Real chunk unloading, partial networks, bridge interruption and extended moving drills.
- All 21 walkable bays in packaged-current and decoupled profiles, with manual checks still marked as manual.
- Five recipe-unlock boots, eighteen manufacturing checks, capability tests and 271 survival-harvest cases on each of two boots.
- Six oil-transfer lanes, including Mineralogy oil, through two complete process restarts.
- Native GUI captures: 39 windows, 351 required-caption cases and 355 rejected broken controls.
- Two independent clean source exports per mod in paths containing spaces. All twelve artifact pairs match the tested jars. This used the existing pinned cache, not a new cold-cache qualification.

## Evidence And Limits

Local receipts are retained under
`D:/PowerAdvantage/_verification/backport-coverage-20261030/` and
`test-rig/build/coverage-20261030-candidate-b-*`. Oil evidence is under
`test-rig/build/native114-oil-coverage-20261030-candidate-b-mineralogy`.
GUI evidence is under `build/client-resource-runs/20261030-115616-4693ddb7`.
Failed old-jar tests and superseded candidates remain available for comparison.

These are bounded scenarios, not proof of every network, third-party mod or
multiplayer interaction. Worldless renderer checks do not prove in-world
animation. No new world-generation or cold-cache result is claimed. Manual
acceptance of the changed windows and release review remain separate. Nothing
has been committed, pushed or published as part of this backport.

## Generator Follow-Up

This is a pre-final Steam-control checkpoint. It covers only the exact profile
and hashes below, before the later Steam first-tick and per-case freeze repairs.
It does not qualify newer SteamAdvantage jars or harness builds, even when the
version and filename are unchanged. Those candidates still need the final sweep.

This 30 October checkpoint covers profile `pa114-candidate-c`. The generator now
reads redstone inhibition before its first power update, preventing one unwanted
startup pulse. Generation rates are unchanged. Power and Steam retain their
earlier hashes; Electric and the harness have new hashes. The earlier results
above remain attached to their original inputs, not automatically to this set.

The input manifest is
`D:/PowerAdvantage/_verification/backport-coverage-20261030/pa114-candidate-c/inputs.json`,
SHA-256 `7C2BFE231B57D7ED54F7769C115DE5C6ED515A9AF39877D9D672338651ECAB43`.

| Follow-up input | SHA-256 |
| --- | --- |
| PowerAdvantage-2.4.5.114041.jar | `797B904D22AE83BE311175564373DB177E12AC8057ACF67DF74111F73ED47832` |
| SteamAdvantage-2.2.6.114041.jar | `57245CB75A0BAF469BA26F03556FEFD4AF8113D522642ED2BB3A8FA6D0855814` |
| ElectricAdvantage-2.2.6.114041.jar | `94664B7F8156EFCAAD3ED2B17F9837E5B74649D459BAC79619C2F9AB93652655` |
| AdvantageWorksTestHarness-1.1.0.114041.jar | `9CADDD42A9263CE2A0C6009B505251E3170D2F23EBEA044DF8E692DD18AACE79` |
| BaseMetals-3.0.1.114041.jar | `8C5416A59F88C41883DB2B8DCD180E2BDC29AD00EF6DF964D67B6C49AEB6EB9E` |
| OreSpawn-4.1.1.114041.jar | `219AE0FA08CCE400C3952AECEA9EB93B8C193D4337B2ECC09A382E6B1D525294` |

| Check | Follow-up result and scope |
| --- | --- |
| Electric release suite | PASS: build, unit, release, dependency, bytecode, archive and Eclipse checks. |
| Operating jobs | PASS: all 126 natural-tick jobs and all 126 exact cold-restart comparisons. |
| Solar startup control | PASS within those 126 jobs: lit and dark controls plus a lit, redstone-inhibited panel with zero energy from its first pulse. |
| GUI captures | PASS: 39 windows, 351 required-caption cases and 355 rejected broken controls. |
| Frozen migration | PASS confirmed: all thirteen routes completed and the runner exited successfully. Frozen state is not proof of operating imported networks. |
| Connected source machinery | PASS confirmed: all 5/5 routes on profile c. This covers the declared connected fixtures, not every player-built network. |
| Paired endurance | PASS corrected: fresh and migrated-copy worlds each completed 1,800 seconds, 30 batches and 33 verified checkpoints, then full restart and resumed processing. The first POI-failed attempt remains retained as FAIL. |

Cold comparisons include block states and typed NBT, including unexpected extra
keys. Only the declared zero-volume tank representation is canonicalized; the
tests do not write expected state to make a comparison pass.

The first endurance attempt remains FAIL for `POI data mismatch: never registered`;
its completed machine batches do not override that failed runtime audit. The
separate no-structures run has now passed shutdown, audit, restart and resumed
processing in both `fresh114` and `original110-chained113-native114`.
These are 1,800-second commissioned Works-bay tests, not endurance of untouched
imported networks or every machine. This corrected pass belongs only to profile c,
not the newer Steam-control candidate. Outstanding station checks remain
`MANUAL_REQUIRED`.

Packaged-server receipts are under `test-rig/build/`:

- `coverage-20261030-candidate-c-126/native114-machine-operation-results.json`
- `coverage-20261030-candidate-c-frozen/native114-refresh-results.json`
- `coverage-20261030-candidate-c-connected-current/native114-refresh-results.json`
- `coverage-20261030-candidate-c-endurance/native114-endurance-results.json` (retained FAIL)
- `coverage-20261030-candidate-c-endurance-nostructures/native114-endurance-results.json` (corrected PASS)

The Electric release log is
`D:/PowerAdvantage/_verification/backport-coverage-20261030/pa114-ElectricAdvantage-generator-release-h.log`.
GUI evidence is under `run-client-resource-runs/20261030-154731-384c5ce2`.
Its `client-input.json` SHA-256 is
`11CFFB7FAF9657CB1ADD6026F13FE64173350C4524601DD4CE198E70DA57DCBB`;
its `production-files.json` SHA-256 is
`D0230A2DA49048414072CC0D6215863E0CD937E191537DB77A4E1D38011FAD85`.
The GUI run uses separately hashed development production classes/resources and
deobf dependencies, not the packaged release jars as its runtime classpath.

Only the connected routes listed here were rerun on these inputs. Older oil,
world-generation, optional-power, reproducibility and cold-cache results are not
carried forward as new proof for the changed Electric jar. The newer Steam-control
candidate remains pending its own final sweep. Manual play-testing and release
review remain separate; these local results do not imply a push, merge or publication.

## Final T Candidate

This separate 30 October checkpoint covers the final Steam-control candidate
for Minecraft 1.14.4, profile `pa114-candidate-e`. Power and Electric have
exactly the same release hashes as the generator follow-up; Steam and the
harness have new hashes. Earlier receipts keep their original inputs and scope.
Passing a later candidate does not turn an earlier failed run into a pass.

The campaign pin file is
`D:/PowerAdvantage/_verification/backport-coverage-20261030/final-production-pins-t.json`,
SHA-256 `48CF5EE9B6A4FC4E285DB26B977F945D3673EF6278E3188348C6A74880E50346`.
The packaged-server input manifest is
`D:/PowerAdvantage/_verification/backport-coverage-20261030/pa114-candidate-e/inputs.json`,
SHA-256 `A749AC68E1AA93B67ED83F71EC034348675459F814450C7340719A14C1F572A6`.
These identify the actual jar bytes, not just filenames, versions or a source
commit that may predate local changes.

| Final-t input | SHA-256 |
| --- | --- |
| PowerAdvantage-2.4.5.114041.jar | `797B904D22AE83BE311175564373DB177E12AC8057ACF67DF74111F73ED47832` |
| SteamAdvantage-2.2.6.114041.jar | `F9A8EAB913859906A0477FACF32824F03C60374BA5C7F02A0E0BCDDF22CD31FB` |
| ElectricAdvantage-2.2.6.114041.jar | `94664B7F8156EFCAAD3ED2B17F9837E5B74649D459BAC79619C2F9AB93652655` |
| AdvantageWorksTestHarness-1.1.0.114041.jar | `0FF3D6DB8CC008B9D862116974DAF0A30AD61FC5502C5229AB34050948AA6743` |
| BaseMetals-3.0.1.114041.jar | `8C5416A59F88C41883DB2B8DCD180E2BDC29AD00EF6DF964D67B6C49AEB6EB9E` |
| OreSpawn-4.1.1.114041.jar | `219AE0FA08CCE400C3952AECEA9EB93B8C193D4337B2ECC09A382E6B1D525294` |

The earlier POI fixture failure remains recorded against its original run; it
has not been relabelled as a pass.

| Check | Final-t result and scope |
| --- | --- |
| Release gates | PASS for all three mods. The campaign's 18 mod/version release gates pass across 1.10, 1.12, 1.13, 1.14, 1.15 and 1.16; this checkpoint uses the existing exact-hash Power/Electric results and the new Steam full suite. |
| Operating jobs | PASS: 132 finite jobs on normal server ticks, followed by 132 exact cold-process comparisons. |
| Independent Steam-control audit | PASS: all six control cases, all nine explicit tile-hold records and recomputed full operation/cold checks. |
| GUI checks | PASS: 39 windows, 351 required-caption cases and 355 rejected broken controls. |
| Frozen migration | PASS: 13/13 declared routes, with all route receipts passing against this manifest. Frozen preservation is not operating-network proof. |
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

- `coverage-20261030-candidate-e-final-all/native114-machine-operation-results.json`
- `coverage-20261030-candidate-e-final-frozen/native114-refresh-results.json`
- `coverage-20261030-candidate-e-final-connected/native114-refresh-results.json`
- `coverage-20261030-candidate-e-final-endurance/native114-endurance-results.json`

The independent control receipt is
`D:/PowerAdvantage/_verification/backport-coverage-20261030/pa114-candidate-e-final-steam-controls-audit.json`.

Endurance covers commissioned Works bays in `fresh114` and
`original110-chained113-native114`. Across 1.13, 1.14 and 1.15, all six
fresh/migrated-copy profiles pass. This is not a claim about every machine or
untouched imported network; station checks marked `MANUAL_REQUIRED` remain
manual.

Release logs are under
`D:/PowerAdvantage/_verification/backport-coverage-20261030/`:

- `pa114-PowerAdvantage-repaired-release-b.log`
- `pa114-SteamAdvantage-final-owner-callbacks-t.log`
- `pa114-ElectricAdvantage-generator-release-h.log`

Each full suite includes `clean check build javadoc verifyReleaseDependencies
verifyReleaseArtifacts writeReleaseChecksums verifyEclipseProductionClasspath`.
Reproducibility receipts are `qualification.json` in these sibling directories
under the same verification root:

- `PA114 reproducibility b with spaces` (unchanged Power)
- `PA114 reproducibility final-electric with spaces` (unchanged Electric)
- `PA114 reproducibility final-steam-t with spaces` (new Steam)

The release, deobf, sources and Javadoc artifact pairs match in each applicable
record. These use independent clean source exports and paths containing spaces,
with the existing pinned dependency cache. They are not fresh-cache proof.

Final GUI evidence is under
`run-client-resource-runs/20261030-174855-75524d89`.
Its `client-input.json` SHA-256 is
`BC0553B1A9B47F808DB832C22C5A5B8EB04870F6B916C3BB1DCE8F8614DFECF0`;
its `production-files.json` SHA-256 is
`BA25137C78E44A24306A35C18BC9E364B9A789EACD0D3AD3F1083A96438B7366`.
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
| Original native broad result | `test-rig/build/coverage-20261030-candidate-e-final-broad/native114-broad-results.json` (this repository) | `93E38D2620619608A65C0C43646F03FFE498B81CCDF17D8A0D8561A0F0FC6BA9` |
| Independent native audit | `broad-result-audit-114-final14e.json` | `AC6E000E42F704A840061E068F492B6C4A496B3E819AE26BFA5E5EC09DA8A806` |
| Applied reporter replay | `broad-writer-114-applied-replay.json` | `D15FCFA5C83D9C998F961FAF16FA8B6754B2F83FC754BACA5CD16581F37D5CA5` |
| Reporter unit controls | `broad-all-114-controls-applied.log` | `B52E35CA4CFC5EF4AA4D3A393C51C827D3CB0275B0972CDBFD1D0D4DD19A7E05` |

The broad runner changed from
`C5AD9B69465DD59312F2686059688727D0836BEFA9F31788DC44C3DD08A6CF23` to
`DB6E4A401B69B7AC13B96764A6A9F1E7081451D559C9F9DDB988D3F854B6ADCB`
for reporting only. The archived native proof still names the first hash;
it is not relabelled as a run with the new reporter.

The final harvest receipt is
`test-rig/build/coverage-20261030-candidate-e-final-harvest/native114-harvest-results.json`.
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
| Lifecycle | `coverage-20261030-candidate-e-final-lifecycle/native114-lifecycle-results.json` |
| Holder partial / bridge | `coverage-20261030-candidate-e-final-holder-partial-port25581/native114-holder-partial-results.json`; `coverage-20261030-candidate-e-final-holder-bridge/native114-holder-bridge-results.json` |
| Extended reads | `coverage-20261030-candidate-e-final-extended/native114-extended-reads-results.json` |
| New holder-extended native run | `coverage-20261030-candidate-e-final-holder-extended-continuity-v/native114-holder-extended-results.json` |

The pure-validator continuity repair requires at least 40 continuous absent
native ticks with stable load/unload event counters. Observed or hidden reloads
still fail; disjoint absence intervals are not added together. Its 164 focused
controls and independent raw re-audits pass: 24 partial cases per version,
96 across 1.13-1.16. Those re-audits are offline qualification, not new native
runs; the original 1.13, 1.15 and 1.16 extended receipts remain their original
`PASS` results. No Java, production logic, fixtures or native transports changed.

The original holder-extended receipt remains `FAIL`, SHA-256
`D01C2EA29B1D80A237B227EE65EBD6FB14315AB416C6F607E89B961EE2F8030C`.
Its three fresh-world machine-absence findings used only the last adjacent
38-tick interval, although the event-stable absence lasted 189 ticks. The
amended offline audit passes without rewriting that failed native receipt.
The separate continuity-v native rerun is `PASS` with exit code 0, unchanged
helpers, jars and fixtures; its receipt SHA-256 is
`5770BCCAD5F92EF0C9B3C434A37F25CC93785CCECE8584478ECFA6289373EC54`.
The new independent audit `pa114-native-continuity-v-independent.json` also
passes with no changed cases, SHA-256
`287A6F8C08310B08F245908D8A3838C2CACB04BFDA2DDAA21EA6ED28B808F8A2`.

Repair evidence is under
`D:/PowerAdvantage/_verification/backport-coverage-20261030/extended-absence-repair/`.
`completed-summary.json` SHA-256 is
`B0CE16C439D0628F0D3141208BA6E07352CD4F62AEF8105049C6873634CF3E89`;
this version's `raw-audit-114-final.json` SHA-256 is
`5AF61EEBFB33C76FB9259927CE8C9024EB2C7CB35F22DF804AACEA38CFEDF6E6`.

The final Python results are
`D:/PowerAdvantage/_verification/backport-coverage-20261030/final-python-units-w.json`,
SHA-256 `AFA8185EF3826E892F4B3F3AE1091698D77B917B1D543F1302D2D0D81213456A`.
All six lines pass: 3,274 tests in total, including one documented not-applicable
skip (3,273 passing tests). These offline tests do not replace native machine
or manual checks. Existing manual, modpack and migration-scope gaps remain open.
