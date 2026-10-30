# Advantage 1.16.5 Test Record

Status: **the final-t automated scopes below pass on their exact recorded
inputs. The extra network/drill scopes below are now qualified; manual acceptance remains open.
Earlier candidates and launch instructions are historical, not acceptance of
the changed final jars. Blank-caption GUI acceptance remains withdrawn.**

## 2026-10-30 Final-T Follow-Up

This is the current local 1.16.5 checkpoint, profile `pa116-candidate-c`.
It replaces the current-candidate description, not the retained historical
evidence. Power's release bytes are unchanged from the 29 October operating
follow-up; Steam, Electric and the harness have later hashes. Matching a version
or filename is not enough to reuse an old qualification.

The campaign pin file is
`D:/PowerAdvantage/_verification/backport-coverage-20261030/final-production-pins-t.json`,
SHA-256 `48CF5EE9B6A4FC4E285DB26B977F945D3673EF6278E3188348C6A74880E50346`.
The sealed server manifest is
`D:/PowerAdvantage/_verification/backport-coverage-20261030/pa116-candidate-c/inputs.json`,
SHA-256 `D191DADA303689D6575ABC2BE63DD5439499A048F433E84FB0CDEA598D1483BB`.
These hashes identify the candidate bytes; a repository HEAD alone does not
include the uncommitted repairs.

| Final-t input | Version | SHA-256 |
| --- | --- | --- |
| PowerAdvantage | 2.4.7.116051 | `AF22AD10CFDD6B191C57AF0B361D90B120D48D1FC6E0F20636BD185319F0B008` |
| SteamAdvantage | 2.2.8.116051 | `DB87982A5F3D2C57628551715469AA4466157110EE7FB4582695E0113CC487AE` |
| ElectricAdvantage | 2.2.8.116051 | `CE9B0FA249940E22929FBB660E56B5A52A19681D63C12FB6FCD537A16C3A0383` |
| AdvantageWorksTestHarness | 1.1.0.116051 | `A5AFA16C465408B4E1F80ACB198F5576E779A3A5985C2D8DF51D64DC6D0A2166` |
| BaseMetals | 3.1.1.116051 | `8643678EAC85A20F646139749AE316EE9CA00A01AD96C6F25B5994CB80B99700` |
| OreSpawn | 4.1.0.116051 | `CDF59D83F191C5FBCD8921228DB577066A63A8FEB25CA79DB4EAEEA32D32DB04` |

| Final scope | Result and limit |
| --- | --- |
| Release gates | PASS: Power's unchanged exact-byte suite plus the final Steam and Electric full suites. All 18 mod/version release and reproducibility gates pass across the six maintained Minecraft lines. |
| Operating jobs | PASS: 132 finite natural-tick jobs and 132 exact cold-process comparisons, without rebuilding the measured machines. |
| Steam controls | PASS: six first-loaded/control cases, nine explicit tile holds and an independent audit of raw metrics and typed before/after state. The corresponding six-case audits pass on all six Minecraft lines. |
| Broad campaign | PASS: both requested profiles, `packaged-current` and `packaged-decoupled`, including first/restarted checks and independent completeness/pin/raw-evidence audit. |
| Reporter-only qualification | PASS: unit controls and replay of the completed evidence; original native receipts and protocol hashes remain unchanged. This is not a native rerun with the new reporter. |
| Native GUI | PASS: 39 windows, 351 required-caption cases and 355 rejected broken controls; 94 items, 905 block states and 87 creative entries. Live animation and multiplayer remain manual. |
| Endurance | PASS: fresh and migrated-copy Works worlds, each 1,800 seconds, 30 batches and 33 verified checkpoints, then full restart and resumed processing. |
| Survival harvesting | PASS: 271 real survival checks before restart and 271 after restart, including failed tools, bounded legacy drops, silk touch and solar solidity. |
| Reproducibility | PASS: Power retains its exact-byte recompilation record; final Electric and Steam each match all four artifact pairs from two independent source exports in paths with spaces, using the existing pinned dependency cache. |
| Extra network/drill protocols | PASS: final lifecycle, holder partial/bridge, extended reads and original holder-extended native receipts; strict offline continuity re-audit also passes. See Final Network And Drill Follow-Up for the exact scopes. |

Cold comparisons include block states, all typed NBT and unexpected target-only
keys. Only the exact empty named-tank representation is canonicalized. No
expected-state writes are used. The six Steam cases cover the crusher comparator
matrix, a first-loaded crusher with no steam, signalled/unpowered crushers and
signalled/unpowered furnaces. The matrix holds four tiles; the other cases hold
one each. Every case records `heldAtMeasuredEnd` and removal, identity and
unchanged-NBT evidence. The independent audit checks these records and quantities
against actual state, rather than accepting a self-reported `PASS`.

The all-jobs receipt is
`test-rig/build/coverage-20261030-candidate-c-final-all/native116-machine-operation-results.json`.
The independent control receipt is
`D:/PowerAdvantage/_verification/backport-coverage-20261030/pa116-candidate-c-final-steam-controls-audit.json`;
its four raw input hashes were checked. The auditor SHA-256 is
`390460F58C0A907F41EEA878F6DAB64DC2F901DE4385712E2A4CC5F42105AA12`.

### Broad Reporter And Native Proof

All four final broad campaigns, for 1.13 through 1.16, pass both requested
profiles. Each covers 21 stations. On each first/restarted boot,
`packaged-current` records 96 passing assertions and three skips;
`packaged-decoupled` records 95 passing assertions and four skips. All station
manual procedures remain `MANUAL_REQUIRED`. Save and all-dimension shutdown
evidence passes, but the historical writer did not independently record a
process exit code or an explicit save command.

The old writer could emit `PASS` for a completed subset. The later writer-only
repair keeps empty/incomplete runs `RUNNING`, records a second-profile exception
as `FAIL` and accepts the complete run as `PASS`. Across four versions,
211 controls pass and one 1.13-only control is not applicable. Replays preserve
native results and raw evidence; they only qualify reporter status/source
metadata. No world, native assertion, machine geometry or production code was
changed or rerun for that repair.

The summary is
`D:/PowerAdvantage/_verification/backport-coverage-20261030/broad-writer-applied-summary.json`,
SHA-256 `2395A4B113E57ED0F5914FEB784B5AF32DDD3D6EFD09D48984BDE405AC2904BE`.
Line-specific receipts relative to that directory unless noted are:

| Evidence | Path | SHA-256 |
| --- | --- | --- |
| Original native broad result | `test-rig/build/coverage-20261030-candidate-c-final-broad/native116-broad-results.json` (this repository) | `84E7832C90ABBA81E4A44CC119F48AB5A8AD1B4689680ADBC4831BC4F3CFFDC0` |
| Independent native audit | `broad-result-audit-116-final16c.json` | `DC44A75F67053991EAD50017818C93EB4E302B5B46132C0D2BD21623700C1C42` |
| Applied reporter replay | `broad-writer-116-applied-replay.json` | `72F977808B55BA795658C198D382B5EFB0C5FBE9B0BEB93A1735DFCC64C11976` |
| Reporter unit controls | `broad-all-116-controls-applied.log` | `3AE6804CD94867F0532C889579DD69872E78DDD32FA6BA0057EF0DB40C289898` |

The broad reporter changed from
`2978C4CE2F0AC817AE9604AD9602420EF0968D04834022A1141B55183F0CE74F` to
`08564A17FAF47C24B6056C812011AA1A82C29EA1986338751DD83AA990ACB5E7`.
The native receipt retains the original hash; its passing result is supported
by the independent complete-profile audit, not relabelled as a new native run.

### GUI, Endurance And Build Evidence

Final GUI evidence is
`run-client-resource-runs/20261030-174948-568893cd`.
Its `client-input.json` SHA-256 is
`12A3322AAB720B7520869BF2DC4822475E53CCCED33C81DC97F7C19BB1EBCCFF`;
its `production-files.json` SHA-256 is
`B25C61494913EEB9DE20B57171BB147B2C205818B0440D70F13BDC2C9A9D2180`.
This is separately hashed development production output with mapped dependencies,
not the reobfuscated server jars used as a client runtime. It proves the declared
resources/captions, not live turbine motion, every language/resource pack or
multiplayer interaction.

The endurance receipt is
`test-rig/build/coverage-20261030-candidate-c-final-endurance/native116-endurance-results.json`,
covering `fresh116` and `original110-chained113-native116`. These are commissioned
Works bays in fresh/migrated copies, not every original imported machine.
The harvest receipt is
`test-rig/build/coverage-20261030-candidate-c-final-harvest/native116-harvest-results.json`.
Both name the final 116c manifest above.

Final endurance, GUI and harvest profiles pass on all six maintained Minecraft
lines. The legacy 1.10/1.12 endurance profile is one bounded natural-tick job plus
strict restart, not the paired 30-minute protocol used by the later ports.
Manual station checks are not converted into automated passes.

Final Steam and Electric release logs are
`D:/PowerAdvantage/_verification/backport-coverage-20261030/pa116-SteamAdvantage-final-owner-callbacks-t.log`
and `pa116-ElectricAdvantage-generator-release-g.log` in the same directory.
They run `clean check build javadoc verifyReleaseDependencies
verifyReleaseArtifacts writeReleaseChecksums verifyEclipseProductionClasspath`.
Power's unchanged bytes retain
`D:/PowerAdvantage/_verification/machine-coverage-20261029/reproducibility-final6.json`
and the associated successful `power-repro-release-6.log`.

New paired-export reproducibility receipts are `qualification.json` under
`D:/PowerAdvantage/_verification/backport-coverage-20261030/PA116 reproducibility final-electric with spaces/`
and `PA116 reproducibility final-steam-t with spaces/`.
Release, deobf, sources and Javadoc pairs match. This does not claim a new cold
Gradle-cache build or carry forward the older Electric final6b bytes.

### Open Work And Manual Inputs

The final-t network/drill scopes listed below are qualified. The earlier
15-route frozen/connected matrices, oil/Mineralogy runs and player/entity
fixtures below remain historical until checked against the changed final jars.
This follow-up does not newly qualify the full Sylvester modpack, unrelated
third-party energy implementations or unsupported blocks/items.

**Do not use the old manual launch and current-pin paths below for final-t
testing.** In particular, `fixed-inputs-6/inputs.json`,
`redstone-inputs-6/inputs.json`, `gui-development-inputs.json` and the walkthrough
`test-rig/build/native116-manual-20261027-power-f/Start-Manual.ps1` describe earlier
inputs. Existing `runClientAllAdvantage.launch` child-jar pins are not qualified
merely because the launch file still exists. A new manual copy/classpath must be
staged and checked against the final-t jar manifest before testing this candidate.

Live menus/controls, sounds, weapons, actual player login, multiplayer, elevator
rides and live turbine/beam rendering still need manual review. These local
automated results imply no commit, push, merge or publication. Failed/partial
evidence and earlier manual instructions remain below as historical records.

## Operating Follow-Up

**Historical: this section describes the 29 October candidate, not the final-t
Steam/Electric/harness jars above. "Current" and "final" in this retained section
refer to that earlier checkpoint. Its counts, hashes and launch paths are not
new qualification of the changed jars.**

The [operating checks](OPERATING-TESTS.md) now run 126 finite jobs on ordinary
server ticks, then restart the entire process and compare block states, typed
tile data and test actors without rebuilding. They cover useful output as well
as invalid inputs, scarce supplies, full destinations and stopped machines.
The tests reject missing cases, empty assertions, stale results, changed jars
and unexpected extra resources.

The new checks found real faults in energy acceptance, conveyor insertion and
custom-data handling, overflow routing, fluid discharge, pump pickup,
distillation capacity, growth-controller requests, solar sampling, turret
hits, the last musket cartridge and sound delivery. Drains and pumps now use
native bucket pickup, preserving waterlogged host blocks. A later strict
reload check exposed Electric's initially false redstone cache; the first
server use now samples the real signal before work can begin. Client rendering
retains its existing signal handling.

The current production jars are:

| Mod | Version | SHA-256 |
| --- | --- | --- |
| Power | 2.4.7.116051 | `AF22AD10CFDD6B191C57AF0B361D90B120D48D1FC6E0F20636BD185319F0B008` |
| Steam | 2.2.8.116051 | `98C15055D67C90CAB1E8888101434CC747ABE2FC39AB817CB9BF61726A754419` |
| Electric | 2.2.8.116051 | `D8D8E9BA5904C11F3ECD563FE612257A9CC05C01F36E12ACD3522BBAB094BEBA` |

The local archive is
`D:/PowerAdvantage/_verification/machine-coverage-20261029/fixed-inputs-6/inputs.json`.
Its harness is `A427F110B0AF0E295179141A90081DF7831AA9710994544ADFF9820EDFC7946A`.
Base Metals and OreSpawn retain their sealed K dependency bytes. No new source
commit is implied: these corrections are local and uncommitted.

| Check On Current Production Bytes | Result |
| --- | --- |
| Java checks | 188 Power, 109 Steam, 84 Electric and 185 harness tests pass without failures or skips; all three mod suites also pass fresh Eclipse compilation with JDK access restrictions enabled |
| Offline checks | 1,178 Python tests pass without child processes or protocol changes |
| Operating catalog | All 126 jobs pass, followed by 126 strict cold-process state/actor comparisons |
| Stop-signal regression | Old Electric spends energy on the first stopped tick; corrected Electric keeps all eight held witnesses inactive with zero debit through 16 real ticks, while the unheld control spends 336 energy on real work |
| Six-facing lifecycle | Fresh and upgraded copies pass movement, real chunk unloading, resumed processing, full restart, mid-block progress and changed-target guards |
| Partial networks | Native 32-chunk holder layouts pass machine-only, supply-only and bridge unloading/reconnection in fresh and upgraded copies; extended track moves pass too |
| Frozen migration | All 15 routes pass 45 state comparisons and 30 exact-resource comparisons |
| Connected migration | All 15 routes pass preservation, exact-resource and completed-work checks in fixed 2,048-tick intervals before and after restart |
| Endurance | Fresh and upgraded copies pass 30 measured minute batches, full process shutdown/restart and resumed production, with independent conservation checks enabled |
| Recipes | 109 recipes, five unlock boots and 18 progression records pass across all three modes and reload/restart phases |
| Mining and drops | 271 native checks pass before and after restart |
| Oil transport | Four Advantage lanes and six lanes with Mineralogy pass filling, refilling and two complete restarts |
| Native client | 94 items, 905 block states and 87 creative entries pass; 351 required-caption cases pass and 355 broken-caption controls are rejected; recipe-book grouping passes |
| Reproducibility | Re-executed compilation and packaging produce identical release, development, sources and Javadoc bytes for all three mods; Electric's current bytes use the `reproducibility-final6b.json` record, not the earlier pre-client-guard build |
| Archive/source audit | Release audits reject embedded dependencies/harness content; no agent files or generated outputs appear in the Git-visible source inventory |

The stopped-signal regression has its own frozen harness archive at
`D:/PowerAdvantage/_verification/machine-coverage-20261029/redstone-inputs-6/inputs.json`.
Only the separate probe's setup readiness changed after the operating manifest
was sealed; all three production hashes above are the same in both manifests.
The original probe setup failure remains recorded. Readiness now requires real
native block-ticking chunks and registered tickable tiles, not a successful
test verdict, before starting its fixed measurement window.

The client result is
`build/client-resource-runs/20261029-211144-6a96ad97`. Full tank and battery
screenshots were inspected, including both caption rows. Child renderer geometry
retains `PASS_WITH_MANUAL_GAPS`: worldless geometry is not live turbine animation.
An earlier wrapper falsely treated a stale shell exit code as client failure;
its successful native report is retained separately from the corrected wrapper run.

The final local report directories end in `20261029-final6`; the operating run
is `test-rig/build/machine-operation-final-all-20261029-j`. Source worlds are
unchanged. This follow-up does not repeat the earlier cold-cache/spaced-checkout
builds, nor relabel old player/entity fixtures as current acceptance.

Fourteen accepted reports and 6,455 raw evidence files are also archived under
`D:/PowerAdvantage/_verification/machine-coverage-20261029/final6-evidence`.
Every archive entry was reread and checked against its recorded SHA-256. The
failed setup and old-jar regression remain included, not overwritten. All
disposable test servers have stopped; no commits, pushes or publication were made.

The recorded automated qualification passes. Failed attempts, superseded manifests and
raw observations remain available beside the current runs. The stopped-world
and operating comparisons are separate checks; neither animation nor a rising
progress counter counts as completed production. Connected intervals use a
fixed 2,048-tick window so an electric drill can complete its nine-block cycle.
An already exhausted target is reported as exhausted, not as new mining.

The paired 30-minute protocol repeats bounded production batches with measured
inputs. It is not a claim that every weapon, elevator or drill ran unattended
for 30 minutes. Live GUI interaction, sounds, multiplayer, player elevator
rides and arbitrary modpacks still need human review. The earlier wolf-health
normalization finding remains a separate native difference, not a newly green
player/entity migration matrix.

## Historical Qualification

The retained records below describe earlier candidates. The test-suite review
and its 20 passing/10 incomplete connected intervals were a reason to add the
new operating protocol, not acceptance of current machine behaviour.

This record covers Minecraft 1.16.5 and Forge 36.2.34. Earlier-version passes
and earlier 1.16 candidates do not qualify these jars. All world tests use
disposable copies. Back up the whole world before upgrading, including its
configuration and player files.

## Required-Caption Candidate

The replacement adds dedicated machine-name and inventory-label rows, moving
artwork and slot hitboxes together. Default conveyor titles now use block
translations rather than Java class names; player-assigned names are retained.

Power's release jar is `6E49CF0B0130B9CE423A21A9FC692F0D7CB945A31A798276EDE62504EA31FBE1`;
its development jar is `B9AF3B71967A662F28CF69A249C61C2DABCA369D7146191CF6F23BE854820384`.
Steam and Electric are unchanged from the local GUI jars listed below.

The new required-text check first rejected the old blank-caption build on all
39 screens. The corrected Power build passes 178 Java tests, its release audits,
and a fresh Eclipse-compiler build with restricted JDK references treated as
errors. The final native run uses Eclipse's production output, the normal
combined launch, and the existing Java 8 installation.

All 351 size/state cases match the independently specified English machine name
and `Inventory` glyphs. All 355 blank, single-missing, incorrect or misplaced
caption controls are rejected. Long-name fitting and its full-name tooltip pass
too. The 396 retained images include 351 normal layouts, 39 item tooltips, four
deliberately bad controls and two long-name views. The renderer, model and
recipe-book checks pass in the same process. Full-view screenshots are reviewed
without cropping off caption rows. This is still not a new machine-endurance,
world-upgrade or multiplayer test, nor an approval of every resource pack or
language. See [GUI checks](GUI-CHECKS.md).

## Superseded GUI Candidate

The first 29 October GUI correction removed required captions as well as the
overlap. Its test expected labels to be absent and the visual review failed to
reject the missing machine name and inventory label. Its GUI acceptance is
withdrawn. The new, independent required-text check rejects all 39 screens from
that build. The hashes below identify the superseded artifacts, not an accepted
caption layout. See [GUI checks](GUI-CHECKS.md) for the replacement contract.

| New Release Jar | SHA-256 |
| --- | --- |
| PowerAdvantage-2.4.7.116051.jar | `CDA03F5D7729CCD4FCA021946FEB3D34784FCB3B7DE1AC493DCF2352CBBBC588` |
| SteamAdvantage-2.2.8.116051.jar | `43F41CFD17D0674B0C97B589FBDBE1F0085D9E8C64736467A7E458658E8F2EC3` |
| ElectricAdvantage-2.2.8.116051.jar | `B1F59A4F84A43A0FBAE6C37262E82A428E791CB2282EFE02A7D946D80A56B559` |

For that superseded candidate, all three release suites passed: 175 Power, 89 Steam and 51 Electric tests, with
no failures or skips. Fresh Eclipse-compiler builds passed those same tests with
restricted JDK references treated as errors. The combined native client passed
the earlier, insufficient assertions for 39 machine screens, with 351 layout/state images and 39 item-tooltip images,
as well as the model, fluid, recipe-book and renderer checks.
See [GUI checks](GUI-CHECKS.md) for the exact scope and repeatable command.

The extensions retain their frozen Power compile pin; the GUI preview runs the
new API-compatible Power output. Its development jar hashes are recorded in
`gui-development-inputs.json`. Source changes remain local and uncommitted.
The frozen operating and migration evidence below is not relabelled as a fresh
world run against these GUI jars. No cold-cache, independent reproducibility,
migration or endurance qualification is claimed for this follow-up candidate.

## Frozen Operating Builds

| Mod | Version | Tested Production Source |
| --- | --- | --- |
| Power Advantage | 2.4.7.116051 | `bcb75bfa91c14751ebf1e31865e1ec0ffa82c0b7` |
| Steam Advantage | 2.2.8.116051 | `5be1ce9faf7465d072f2c8b0e3206899bafb34e8` |
| Electric Advantage | 2.2.8.116051 | `9a8fdf53a111499f2ebc969ac24a6f3e1017c18e` |

| Release Jar | SHA-256 |
| --- | --- |
| PowerAdvantage-2.4.7.116051.jar | `5BE5C05D9F78BAF730553B71D07D76BA923EA0A54ABCA53F4149386B27019E71` |
| SteamAdvantage-2.2.8.116051.jar | `0E8EF7DA681A5105E5206F166A36F56AEF356DFD227F1C3910DE7BDC606AA148` |
| ElectricAdvantage-2.2.8.116051.jar | `A9B62DD737E42455FEE72BD2D855C9C5EDBF5A9C5EAD7B48C650C9073A35B129` |

Both extensions compile against Power's exact deobf jar:
`2FB923944A456B4F26AB2F9212C646469CE3F49BFD4DAE894C15559B48908386`.
The local K manifests seal these artifacts, Base Metals 3.1.1.116051 and
OreSpawn 4.1.0.116051. Mineralogy 6.1.4.116051 is added for its relevant tests.
Dependencies remain separate jars. Base Metals' hook fix is merged into MMD;
no tag or release upload is included in this campaign.

The chained 1.10-to-1.13-to-1.16 Nether test rejected the previous Power jar
because Forge name-tag-distance attributes disappeared. The repair covers all
four Forge identities found in the supported source versions. Native loading
tests now retain their values and modifiers. All eight Nether and eight End
routes have completed full restarts without further attribute loss. Earlier
failed candidates remain recorded; only the K runs below qualify these jars.

Later local commits update documentation and verification tools only. The
production-source references above identify the packaged code; release and
development jar hashes remain unchanged by those follow-up commits.

The harness and its native serialization tools are pinned at
`c7a79fc9894e067ed0645daad253e1355628d138`. The rebuilt harness jars are byte-for-byte
identical to the previous runtime; the compile dependency and finite attribute
name projections have been updated. It has 146 Java tests.
Test tools are not packaged in any Advantage release.

## Completed Build Checks

| Check | Result |
| --- | --- |
| Release suites | Power 175, Steam 87 and Electric 47 tests pass with no failures or skips; dependency, identity, Java 8, resource, archive and Eclipse audits pass |
| Independent Eclipse compiler | Fresh production and test compilation passes the same 175/87/47 tests |
| Reproducibility | Power passes two independent spaced checkouts, the first using an empty cache; all four jars match exactly. Rebuilt Steam and Electric jars exactly match their independently reproduced builds, including sources and Javadoc |
| Harness self-tests | 146 Java tests and 1,087 Python tests pass; Python child processes are forbidden during the offline run |
| Profile validation | All ten sealed native profiles validate without starting Minecraft or opening a world |

## Eclipse Access Correction

On 28 October, Eclipse rejected direct `sun.misc.Unsafe` imports in Power and
Electric test fixtures. The earlier standalone Eclipse-compiler check did not
apply the IDE's JRE access rules, so it missed these errors. This was a test
source problem, not a reason to weaken Eclipse's restrictions or replace the JDK.

The fixtures retain their constructor-free setup through reflection, without
compile-time references to restricted JDK types. Power adds a regression against
those imports. Fresh compilation now applies Java 8 boot-library access rules
with restricted references treated as errors. All 175 Power and 47 Electric
tests pass under that compiler and Java 8, and both Gradle release/Eclipse suites
pass without failures or skips.

All four Power jars and all four Electric jars remain byte-identical to the
qualified candidates above. No production source, resource, dependency pin or
machine behaviour changed. The existing native-world evidence therefore still
applies to those exact binaries; no new live-world run is claimed for this
test-only correction. Refresh the Gradle projects in Eclipse and use
**Project > Clean** to clear the old error markers. Non-blocking warnings remain.

## Client World Restart Check

The 28 October world-creation crash came from a damaged local mapped Forge jar.
Its ZIP directory still listed vanilla networking classes, but reading their
compressed data failed. The resulting missing-class error prevented the client
from joining its integrated server. This was not a change to machine behaviour.

The damaged jar and original logs were retained. Only that cached jar was
regenerated; its SHA-256 then matched the checksum recorded before the damage.
The build and Eclipse audits now read every runtime jar entry and verify its
size and CRC. The client probe also initializes vanilla networking instead of
checking only resources. That probe passes on the same Java 8 installation.

On 29 October, the disposable normal world was loaded with all three Advantage
mods, Base Metals and OreSpawn on Oracle Java 8u202, using Eclipse's production
output and no test probe. Minecraft was closed normally, with player and all
three dimension saves confirmed in the log. A second, fresh client process
loaded that same save successfully and closed normally with exit code 0.
Neither run logged an ERROR or FATAL entry. The original saves were hash-checked
before and after and were not changed.

The local restart evidence is under `_verification/world-reopen-20261029` outside
the repository. This completes the previously interrupted singleplayer
close/reopen check. It does not replace manual visual checks, machine operation
tests, multiplayer tests, or qualification of a different modpack.

## Frozen Machine Runtime Checks

These are fresh K runs against the repaired artifacts above, not earlier passes
relabeled for the new build. Raw results, copied worlds, logs, input hashes and
independent audits are retained locally outside the release artifacts.

| Check | Result |
| --- | --- |
| Complete Works, current dependencies | All 21 bays pass their automated assertions, with 96 pass and 3 explicit skips per boot, including full restart |
| Complete Works, without Base Metals | 95 pass and 4 explicit integration skips per boot, including full restart; this does not qualify upgrading a Base Metals world without that mod |
| Extended drill reads | Eighteen routes pass mining, movement, mid-block restart and saved-state checks in fresh and upgraded worlds; independent replay passes |
| Survival mining and loot | 271 cases pass before and after restart, including failed tools, exact drops, Silk Touch and connection masks |
| Recipes and progression | Five unlock boots and eighteen progression checks pass across all three modes, rubber settings, reload and restart |
| Fluids and integration | Six oil lanes pass across three boots; 22 native capability/converter checks and two restart checks pass, with independent raw replay |
| World generation | Advantage-only, Mineralogy and imported legacy profiles pass generation and full-restart checks; the existing exclusions below remain explicit |
| Chunk lifecycle | Five real unload/reload cycles per fresh/upgraded profile pass with retained tank contents |
| Partial-network unloading | Separate 32-chunk layouts pass machine-only and supply-only unloading, reload-order checks and bridge recovery |
| Extended physical unload and drills | 144 native drill movements pass across fresh/upgraded profiles, together with blocked-output, scarcity, redstone, restart and partial-unload checks |
| Combined client | 94 items, 905 states, 87 creative entries, three recipe-book modes and 25 worldless renderer cases pass, using the current K server exports; all 543 actual Power classes/resources match the frozen development jar |
| World-upgrade matrix | Fifteen frozen and fifteen connected routes pass across 60 boots and thirty exact 512-tick operating intervals, without rebuilding or replacing imported machines |
| Focused machine inventories | Thirty-six boots pass strict saved-file checks, including nested items, material variants, wear and enchantments |
| Players, storage and transformer order | Twenty-four actor boots, twelve storage boots and twelve transformer-order boots pass independent replay |
| Nether and End migration | Forty-eight boots complete with the exact native wolf-health difference below; every other field passes the existing strict comparison |
| Respawn and player data | Twelve boots pass, including the native default Overworld representation described below |
| Paired endurance | Fresh and upgraded-copy worlds each pass 1,801.578 measured seconds, thirty one-minute batches, save, full-process restart and resumed processing; independent replay checks all 33 checkpoints per world |
| Prepared manual copy | Reset, save, full-process restart and shutdown pass; ten launch-guard controls pass. No real player login is claimed |

The earlier broader campaign stopped after fifteen successful boots and one
failed chained route. Its raw failures and failed assessments remain unchanged.
The new campaign completes all 144 machine, player, storage, transformer-order,
dimension and respawn boots with the repaired jar. Ninety-six pass the strict
comparison; forty-eight retain a raw failure and receive only the separate
`PASS_WITH_NATIVE_DIFFERENCE` assessment. All 54 runtimes stop cleanly, with no
forced process termination. Independent replay reproduces the same results.

Independent exact mod-inventory replay checks the closed runtimes against the
sealed six-mod manifest. Alternative profile/schema cases are explicitly
outside that validator, not silently counted. The 72 source runtimes, 198
historical files and frozen helper snapshot remain unchanged. Inventory checks
establish input custody, not gameplay correctness.

Endurance exercises rebuilt Works stations in both a fresh world and a copy of
an upgraded world. It does not claim thirty minutes of operation for every
original imported machine. The separate connected-world matrix operates those
original setups without rebuilding or replacing them.

These are automated checks, not a claim that real menus or player controls have
been accepted in game. Individual station reports retain `MANUAL_REQUIRED` for
their human checks.

## Upgrade Repairs

The wider checks rejected earlier candidates. Repairs cover vanilla registry
renames, entity UUIDs and ownership, mixed vanilla/mod entity conversion,
pending-save NBT ownership, and capability invalidation and revival on unload.
Stored items retain material variants, wear, nested contents and named
enchantments. Native conversion remains responsible for the vanilla formats.

The latest repair translates four exact Forge attribute identities. It retains
customized values and modifiers for entities, passengers, players and stored
mounts. Native negative controls demonstrate the losses without that repair.
Colliding aliases fail closed instead of choosing one value and discarding the
other; private inventory compounds are not traversed.

| Legacy Name | Native Registry Name |
| --- | --- |
| `forge.swimSpeed` | `forge:swim_speed` |
| `forge.nameTagDistance` | `forge:nametag_distance` |
| `forge.entity_gravity` | `forge:entity_gravity` |
| `generic.reachDistance` | `forge:reach_distance` |

Native serialization observations use actual entity/world identity, not an
expected fixture dimension. Comparison accounts for finite known attribute-name
changes, empty modifier lists and the explicit Overworld respawn default.
Unknown names, nonempty modifiers and saved quantities are not silently
discarded.

## Native Behaviour

An isolated, current-version 1.16 control with no Power code shows a tamed wolf
loading at 20 health after being saved at 8. Native DFU retains 8; the wolf's
normal load/taming code heals it. Wild wolves do not show that change. This is
not repaired by changing vanilla behaviour in Advantage.

The source-preservation comparison remains `FAIL` for that exact change. A
separate `PASS_WITH_NATIVE_DIFFERENCE` assessment requires the pinned control,
matching entity and owner UUIDs, the exact 8-to-20 change, and all other fields
matching the existing comparison scope. Raw failures and nonzero exit statuses
are retained. Any additional inventory, attribute, relationship or dimension
difference still fails. This assessment is not an all-green preservation result.

Older Forge can omit a player's default Overworld respawn dimension. Native
1.16 writes it explicitly when a spawn position exists. Patched source from
1.10, 1.12 and 1.16 confirms this is the same default, not a changed destination.
The comparison accepts only that absent-versus-zero representation at the two
player roots, with complete integer coordinates. Changed coordinates, forced
flags, explicit non-default dimensions and unknown extra fields still fail.

## Evidence Handling

Each native server starts from a sealed 69-file Forge installation. Its code is
checked before boot and after shutdown. Transformer-order observers have their
own derived inventory: exactly one validated Forge library entry may differ;
the original inventory is retained. Target errors, fatal messages and Java
exceptions are rejected, including errors logged while a fixer returns partial
data.

Some original source jars emit known historical diagnostics. Source witnesses
record only exact messages and counts from pinned original logs and are labelled
`SOURCE_WITNESS_WITH_KNOWN_DIAGNOSTICS`, not clean-startup passes. Those allowances
do not apply to the 1.16 destination.

Shorter unloading layouts remained inside native chunk-ticket influence and
failed to deliver the required physical unload events. Those failures remain
recorded. Separate 32-chunk layouts require real unload/reload callbacks, a
stable absent interval and unchanged transfer/content checks; loaded-only
transfers are not substituted for recovery after unloading.

The imported legacy world-generation profile retains its existing crude-oil
count-stability exclusion and does not require the zero-biome assertion used by
the two fresh profiles. No new exclusion was added for this port. Its pass does
not claim the excluded count was checked.

An earlier harness cleanup mistake removed disposable raw output under
`test-rig/build/`. Original fixtures, accepted-version checkouts and the Sylvester
world were not affected. Lost raw results are not claimed as preserved proof.
Compiler output now lives in `test-rig/build/gradle/`, cleanup refuses other
targets, and a real cleanup regression checks sibling fixtures survive. Current
completed evidence is also archived outside build directories.

One offline run encountered an occupied test port during live world testing.
It remains a failed attempt. Repeating it in a quiet window passed without
changing or bypassing the port guard. The final, expanded suite passes all
1,087 tests with child processes forbidden and source hashes unchanged.

## Manual Acceptance

**Historical walkthrough instructions only. The old pins and server path here
are not valid final-t manual-test inputs; see Open Work And Manual Inputs above.**

Still check real-world rendering, menus, controls, sound, weapons, multiplayer
and actual player login. The worldless renderer cases do not qualify turbine
orientation or animation in a live world. The generated Works copy is prepared
for a walkthrough but is not left running automatically.

Use `D:\PowerAdvantage\PA116` as a separate Eclipse workspace, keeping the accepted
1.15 workspace separate. Import the three projects there as existing Gradle
projects. In Power, use **Gradle > Refresh Gradle Project**, then
**Project > Clean**, let compilation finish, and select
`runClientAllAdvantage.launch`. This uses the pinned Steam and Electric
development jars. `runClient.launch` remains Power-only. The resource snapshot
checks do not populate Eclipse's compiled class output for it.

The disposable walkthrough server is
`PowerAdvantage/test-rig/build/native116-manual-20261027-power-f/Start-Manual.ps1`.
Run it with `-CheckOnly` first, then without that switch to serve
`localhost:25565`. It verifies the exact six mod jars, native server libraries,
Java 8, loopback-only settings and test-world markers before starting. The
prepared world has all 21 Works bays reset and operator access for SkyBlade1978.
Its two startup/save/restart checks and ten launch-guard controls pass; no actual
player login has been tested. Stop it normally before reusing its world.

For the walkthrough, check recipe discovery from raw materials, the creative
tab, all six drill directions, helper models, oil buckets, tank gauges and
transparent machinery edges. Open the steam and electric menus, then check
live turbine orientation/animation, turret beams, musket controls and sound.
Repeat a save and complete client restart. A separate client joining the Works
server remains the check for real login and multiplayer interaction.

These fixtures do not qualify the whole original Sylvester modpack. Every other
mod needs compatible target-version support too. Unsupported unrelated blocks
or items are not treated as acceptable data loss.

The connected-machine matrix includes 1.14 and 1.15 source worlds. The focused
player/entity fixtures instead cover original and maintained 1.10/1.12 sources
and their 1.13 upgrade routes; dedicated 1.14/1.15-origin actor fixtures are not
qualified. BaseMinerals combinations and individual third-party energy mods are
also outside this run. Converter checks exercise both directions of the native
Forge Energy contract, not every external mod's implementation.

The 1.16 changes remain local. Pushes, merges, tags and release uploads follow
manual acceptance and separate authorization.

### Final Network And Drill Follow-Up

The following native receipts are `PASS` on the final-t inputs above. Paths
are relative to this repository's `test-rig/build/`; these qualify the declared
fresh/migrated fixture scopes, not every player-built network.

| Scope | Native receipt |
| --- | --- |
| Lifecycle | `coverage-20261030-candidate-c-final-lifecycle/native116-lifecycle-results.json` |
| Holder partial / bridge | `coverage-20261030-candidate-c-final-holder-partial/native116-holder-partial-results.json`; `coverage-20261030-candidate-c-final-holder-bridge/native116-holder-bridge-results.json` |
| Extended reads / original holder-extended | `coverage-20261030-candidate-c-final-extended/native116-extended-reads-results.json`; `coverage-20261030-candidate-c-final-holder-extended/native116-holder-extended-results.json` |

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
this version's `raw-audit-116-final.json` SHA-256 is
`A2C4AF17F7FB6AD028DEBFF5E92FA5B0BF84BC138C644C3BA0F14D8E06581134`.

The final Python results are
`D:/PowerAdvantage/_verification/backport-coverage-20261030/final-python-units-w.json`,
SHA-256 `AFA8185EF3826E892F4B3F3AE1091698D77B917B1D543F1302D2D0D81213456A`.
All six lines pass: 3,274 tests in total, including one documented not-applicable
skip (3,273 passing tests). These offline tests do not replace native machine
or manual checks. Existing manual, modpack and migration-scope gaps remain open.
