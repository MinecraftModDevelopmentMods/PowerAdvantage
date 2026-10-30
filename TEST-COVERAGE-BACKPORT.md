# Test Coverage Backport: Minecraft 1.10.2

## Current Status

The provisional checkpoint below is retained as history. See [Final Recorded Candidate](#final-recorded-candidate) for the completed candidate h gates, exact hashes and remaining coverage limits. The older pending statements are not the current result.

**Provisional, 30 October 2026. The latest controls and boiler-boundary repairs still need native requalification.**

This work brings the stronger machine tests back to 1.10.2. The aim is to catch lost contents, incorrect resource use and broken restart behaviour, not simply confirm that a server starts or a block animates.

Earlier candidates passed useful gates, listed below. Those results belong to their recorded jar hashes and manifests. They do not automatically qualify newer source or rebuilt jars. In particular, the last Steam/harness build round passed before the final water-only boiler boundary patch was added.

There are still known defects awaiting closure through tests. This is not a statement that every machine, configuration or old world is bug-free.

## What The Tests Exercise

The packaged harness is a separate test mod. It places finite, isolated jobs in disposable worlds, lets the native server tick them, and records actual inventories, tanks, energy and process state. It does not call a machine's tick method to make an operating test complete.

| Area | Examples in the operating suite | Important limit |
| --- | --- | --- |
| Item transport | Six conveyor directions, blocked inventories, dropped items, filters, overflow, custom item NBT and stack limits | These are declared layouts and stocks, not every modded inventory implementation. |
| Fluid handling | Real source pickup, water and Advantage oils, full/refusing tanks, filters, switches, discharge and distillation | A passing transfer does not prove every boiler's public fluid API is safe. |
| Steam | Finite boilers, furnace/crusher/still inputs, unavailable power, full outputs, pumps, tanks, elevators and musket controls | Rates and conservation are checked per fixture; visual indications are separate. |
| Electric | Processing, generation, battery chemistry and priority, growth, lighting, turret and redstone controls | Representative valid, invalid and scarce conditions are tested, not every combination. |
| Moving drills | Steam and Electric drills in all six facings, one track movement, cargo/NBT/facing/energy retention, no-power and redstone controls | This is not an endurance test of unrestricted mining through every block type. |
| Harvest | Actual mining/drop contracts in a dedicated run, followed by restart checks | Does not certify every tool, enchantment or external block. |
| Reload | Full process shutdown and cold comparison of block states, typed NBT, inventories and recorded entities | A frozen cold comparison is not proof of another long period of active work after restart. |

The [first-loaded control cases](test-rig/src/main/java/zone/moddev/mc/advantageworks/SteamReloadControlCases.java) add six explicit checks:

- `steam/lifecycle/crusher-comparator-matrix`: actual empty, 1, 32 and 64-item inputs must give signals 0, 1, 8 and 15.
- `steam/lifecycle/crusher-first-loaded-unpowered`: progress 106 must become 107 on the first real tick.
- `steam/lifecycle/crusher-first-loaded-signalled`: real redstone must prevent productive crushing on that tick.
- `steam/lifecycle/crusher-first-loaded-no-steam`: no steam must not be mistaken for productive work.
- `steam/lifecycle/furnace-first-loaded-unpowered`: restored burning state must receive its first permitted steam heat bonus.
- `steam/lifecycle/furnace-first-loaded-signalled`: redstone must prevent that bonus without inventing steam consumption.

These check exact progress, heat, fuel time, items and steam, rather than waiting until some eventual output appears. They need the independent [Steam control audit](test-rig/migration/steam_control_audit.py); an older required-case list alone must not allow their omission.

Unit tests also call the actual crusher comparator and boiler fill/drain methods. The coal-boiler save-notification tests use a non-null server-side world test double and a client-side negative control. They prove notification behaviour, not a real disk save.

## Completed Historical Gates

The following are retained local receipts from earlier candidates. Paths are relative to `test-rig/build/`; generated receipts are deliberately not committed.

| Gate | Recorded result | Receipt |
| --- | --- | --- |
| Ordinary operating jobs and cold restart | 173 operating cases and 173 strict cold comparisons passed | `coverage-20261030-candidate-d-173/native110-machine-operation-results.json` |
| Active steam endurance | One steam network/crusher/hopper circuit passed 36,000 measured ticks, about 1,799.93 seconds between its first and last samples, then a strict cold comparison | `coverage-20261030-candidate-c-endurance/native110-machine-operation-results.json` |
| Harvest/drop exercise | Exercise and restored-state checks passed | `coverage-20261030-candidate-d-harvest/native110-harvest-results.json` |
| Later network candidate | 181 of 182 operating cases passed; the crusher consumer-reload case failed. All 182 cold comparisons passed. Overall result remains FAIL. | `coverage-20261030-candidate-f-all-network-fixed/native110-machine-operation-results.json` |
| Release and harness build round `r` | Steam release suite and harness checks/build passed before the final boundary patch | Local build receipts; repeat for the final candidate |

- The old Base Metals 2.4 connected-world runs retain a strict diagnostic failure for late universal-bucket activation. Useful machine observations inside a failed run do not make the dependency profile green.

The endurance result is deliberately narrow: one active steam circuit with finite input and stored steam. It is not 30-minute evidence for every machine, fluids, drills, weapons, partial-unload layout or mod combination. The ordinary `all` group does not run that endurance case.

## Partial Networks And Restart Findings

The [partial-network checks](test-rig/src/main/java/zone/moddev/mc/advantageworks/LegacyPartialNetworkCases.java) separate source-only, consumer-only and pipe-bridge unloading for steam, electricity and fluids.

A valid result requires an actual native unload callback, 240 consecutive ticks of absence without an unrequested reload, retained endpoints staying loaded, and explicit recovery of the same chunk. With a loaded source, electricity and fluid stock must stay constant during isolation; steam may lose only its rated idle amount. This catches transfers through a missing bridge even if total resources remain conserved.

The older code reloaded released chunks while collecting cached requests. The subsequent network-guard candidate passed eight of the nine partial operating cases, but exposed a separate crusher problem: its unload/load NBT matched exactly, then an initially true redstone cache reduced saved progress before the first scheduled control update. That is an operating defect, not permission to add a grace period to the test.

An earlier fluid-consumer run also showed 4,000 mB in memory but 3,995 mB after restart following an unsafe reload. It did not recur in the later guarded-network cold run. The coal tank's additional save callback still needs qualification against the final jars; the earlier pass must not be stretched to cover that newer change.

## Earlier Review: Known And Pending

These were the findings before final qualification. The specific repairs have since passed the bounded gates below; the broader coverage limits still apply.

- **KNOWN, closure pending:** Electric and geothermal boiler copies had the same empty typed-drain null dereference as the coal boiler. Targeted null/empty/invalid request guards and water-only fill checks are now in the working tree, with actual-method tests for all three boiler types. They remain open for qualification until the rebuilt candidate passes. Do not describe all known bugs as resolved yet.
- Crusher/furnace first-loaded control behaviour, comparator range and the final boiler boundaries need the consolidated build, operating and cold checks against one immutable candidate.
- Recipe audit evidence includes FAIL/PARTIAL results. Do not claim complete progression, all recipe modes or every ingredient path from registration or a passing machine test alone.
- GUI title/layout checks do not certify every rendered screen, resolution, animation, texture or multiplayer interaction. Those still need client observation.
- Other fluid machines, oil-boiler paths, arbitrary external capabilities, broad chunk topologies, every configuration combination and unlisted migration routes are not certified by the focused boiler repair.
- No generated world is a release artifact. Never use the original player world as a disposable fixture.

## Running The Checks

Use Java 17 for Gradle and Java 8 for the native legacy server. Build Power first, then the matching Steam/Electric dependencies, and finally the harness. Follow each repository's pinned dependency instructions; do not mix development and reobfuscated release jars.

From each mod repository, run its release suite:

```powershell
.\gradlew.bat clean check build javadoc verifyReleaseDependencies verifyReleaseArtifacts writeReleaseChecksums verifyEclipseProductionClasspath
```

From PowerAdvantage, build the separate harness:

```powershell
.\gradlew.bat -p test-rig clean test assemble
```

The native runner needs a prepared Python environment with `nbtlib` and a hash-pinned input manifest. Use fresh output directories and an unused port. Do not change jars, scripts or manifests while a run is active.

### Profile And Bootstrap Boundary

The tracked `test-rig/profiles/packaged-current.json` belongs to the historical Java runner. It still names harness `1.0.0.110020` under `build/libs/`, whereas the final native candidate uses `1.1.0.110021` from `test-rig/build/gradle/libs/`. Its older dependency and artifact pins have not been silently updated. It is not the entry point used to qualify candidate h.

The Gradle commands above build and check artifacts; they are not a claim that a clean checkout can run the final native campaign using a default Java-runner task. No clean-checkout/default-profile end-to-end pass is asserted here.

The authoritative runtime input is the exact native CLI manifest used for the recorded candidate:

```text
D:\PowerAdvantage\_verification\backport-coverage-20261030\pa110-candidate-h\inputs.json
```

Rerunning it requires the retained, hash-verified seed installation and fixtures, Forge launcher/libraries, and exact dependency and mod jars referenced by the manifest. Migration routes also need their recorded source-world fixtures. These local inputs and generated saves are not committed to the repository; a clone alone does not supply them. Restore and verify those inputs before starting the native CLI. Do not substitute a fresh world, another dependency release or the historical profile defaults and reuse the old result.

The commands below deliberately use that retained manifest. A different installation or replacement input needs a separately recorded manifest and qualification run; do not rewrite the earlier pins to make it fit.

```powershell
$python = ".\test-rig\migration\.venv\Scripts\python.exe"
$java8 = "C:\path\to\jdk8\bin\java.exe"
$manifest = "D:\PowerAdvantage\_verification\backport-coverage-20261030\pa110-candidate-h\inputs.json"

& $python .\test-rig\migration\native110_machine_operations.py run --manifest $manifest --java8 $java8 --port 25666 --group all --output .\test-rig\build\coverage-example-all
& $python .\test-rig\migration\steam_control_audit.py --runtime .\test-rig\build\coverage-example-all --line 110
```

The [partial wrapper](test-rig/migration/native110_partial.py) runs the Power district and adds independent quantitative unloading/recovery checks. It needs a different fresh directory:

```powershell
& $python .\test-rig\migration\native110_partial.py --manifest $manifest --java8 $java8 --port 25667 --output .\test-rig\build\coverage-example-partial
```

Run the bounded endurance smoke before the genuine 30-minute case. Keep both receipts:

```powershell
& $python .\test-rig\migration\native110_machine_operations.py run --manifest $manifest --java8 $java8 --port 25668 --group endurance-smoke --output .\test-rig\build\coverage-example-smoke
& $python .\test-rig\migration\native110_machine_operations.py run --manifest $manifest --java8 $java8 --port 25668 --group endurance --output .\test-rig\build\coverage-example-endurance
```

Endurance must show 36,000 measured ticks, at least 1,799 wall-clock seconds under the documented first-to-last-sample convention, continuing paid work and finite resource accounting. Merely leaving a server open for half an hour is not equivalent.

Keep the manifest, jar hashes, catalog, raw measurements, first-process save, cold inspection, diagnostics and final receipts together. An absent case, FAIL, PARTIAL, changed protocol or unqualified dependency profile is not a pass. Preserve failed attempts; never overwrite a receipt just to obtain a green result.

## Before Calling A Candidate Ready

Require the final release/build gates, the complete declared operating catalog, independent control/partial audits and strict cold comparisons to pass on matching artifacts. Repeat the agreed endurance job after changes that affect it. State the exact remaining gaps, then do the client/visual and representative-world checks. Historical passes are useful evidence, not a substitute for that final qualification.

## Final Recorded Candidate

**30 October 2026: candidate h has passed its declared legacy operating, cold-restart, control, partial-network, GUI, endurance, connected-world, harvest and reproducibility gates. Recipe progression remains PARTIAL; manual and broader modern follow-up limits remain below.**

This is an additional checkpoint, not a replacement for the earlier manifests or failed receipts above. The provisional and KNOWN entries above describe the earlier review state. The final candidate has since passed the declared machine/control checks, including the repaired boiler contracts, but this is still not an all-machines, all-worlds or complete-progression guarantee. No earlier pins have been rewritten.

### Exact Candidate Pins

The immutable input manifest is `_verification/backport-coverage-20261030/pa110-candidate-h/inputs.json`, relative to the workspace containing the version checkouts. Its SHA-256 is:

```text
B705926BCCA8EAB337E6067B9D844BBC2A795B82538DC3502FEA4DBD9BD4EF14
```

All listed artifact bytes were checked against this manifest when this section was written.

| Artifact | SHA-256 |
| --- | --- |
| `BaseMetals-1.10.2-2.5.0-beta4.238.jar` | `51F04770C316E62D9ED3EF713144F02FA2EC899645813627091365E12F61C3EF` |
| `OreSpawn-4.0.8.110021.jar` | `FD27D4F5B4B6087C5F64344903C0E0124115E08FA2D465ECDB88D291F36B2B21` |
| `PowerAdvantage-2.4.3.110021.jar` | `68837BA9997CA152A55125F584C4FD3E24690F2B615D9AFCF21B4C3BEF14A554` |
| `SteamAdvantage-2.2.5.110021.jar` | `1E1949EE26E6E4D6DAE979795320EBCDF879C6F9A573C41F345D4256C962D836` |
| `ElectricAdvantage-2.2.5.110021.jar` | `CF944B613938B26040EE20749CB59C25D32706F59DA3CE53E75F16230CE61151` |
| `AdvantageWorksTestHarness-1.1.0.110021.jar` | `97C55F41D4F35B30175F2E73F2BE9B5B16EBBD0C84F94F66A3D536429EF26F66` |

The harness is test-only, not part of a distributed PowerAdvantage jar. Client GUI proof uses separately recorded mapped/deobf inputs in `client-input.json`; those are not interchangeable with the release-jar hashes above.

### Completed Gates

| Gate | Result | Retained reference |
| --- | --- | --- |
| Ordinary native operation | 188 declared cases PASS | `test-rig/build/coverage-20261030-candidate-h-final-all/native110-machine-operation-results.json` |
| Full process restart | All 188 strict cold comparisons PASS | Same receipt, restart phase; raw fixture and inspection files remain beside it |
| Independent Steam controls | All six first-loaded/comparator controls PASS, including measured tile holds and raw progress/heat/steam comparisons | `_verification/backport-coverage-20261030/pa110-candidate-h-final-steam-controls-audit.json` |
| Independent partial networks | All nine source/consumer/bridge cases PASS; actual unload, 240 absent ticks, resource isolation and explicit restoration checked | `_verification/backport-coverage-20261030/pa110-candidate-h-final-partial-audit.json` |
| Native machine GUI probe | 39 layouts, 351 caption checks and 355 rejected controls PASS | `run-client-resource-runs/20261030-174752-7b8e45fe/client-resources.json`, with mapped inputs in `client-input.json` |
| Owned native crafting | 452 craft-consumption checks PASS across three modes, before and after restart | `test-rig/build/coverage-20261030-final-h/native110-recipe-audit-results.json` |
| Recipe book | Not applicable in 1.10.2 | No 1.12-style recipe-book/advancement claim is made |
| Final active endurance | 36,000 measured ticks, 1,799.98 seconds between first and last samples, 287 completed products and no lost progress; strict cold comparison PASS | `test-rig/build/coverage-20261030-candidate-h-final-endurance/native110-machine-operation-results.json`, with the raw batch/resource ledger beside it |
| Final harvest/drop exercise | Native exercise and full-restart restored-state checks PASS | `test-rig/build/coverage-20261030-candidate-h-final-harvest/native110-harvest-results.json` |
| Connected source worlds | Original, early and maintained 1.10 fixtures each passed two 2,048-tick operating intervals across a full process restart | `test-rig/build/coverage-20261030-source-matched-final-h-connected/native110-per-source-connected-results.json`; independent check in `_verification/backport-coverage-20261030/pa110-source-matched-final-h-connected-verified.json` |
| Final reproducibility | Two independent clean source exports per mod reproduced all four expected artifacts, using paths with spaces and the retained dependency cache | The three `qualification.json` files under `_verification/backport-coverage-20261030/PA110 reproducibility network-carbon with spaces`, `PA110 reproducibility final-steam-t with spaces` and `PA110 reproducibility final-electric with spaces` |

The GUI evidence is retained outside `build/`. It is explicitly `MACHINE_GUI_ONLY`: these counts do not prove every world model, texture, resolution or player interaction.

Across all six Minecraft lines, the final ordinary matrix reported **904 operating passes and 904 cold passes**: 188 per legacy line and 132 per modern line. The two legacy recipe runs together account for **904 owned native crafts**; the 452 server recipe-book checks belong to 1.12.2 alone. These totals must not be confused with a count of distinct machines or survival progression routes.

The earlier unsafe chunk reload and crusher first-tick regression no longer reproduce in these final partial/control cases. The final boiler method tests also cover null, empty, zero, negative and mismatched requests, refusal of lava in an empty water-only tank, positive water acceptance, unchanged finite offers, simulation behaviour and owner save notifications. That closes the specific copied-boiler failure under the tested contracts; it does not qualify every fluid path in the mods.

### Bounded Recipe Classification

The native recipe audit is **PASS**, but its overall receipt remains **PARTIAL** because complete progression is not established. Its declared scope is native craft consumption and registered processing functions, not running every production machine or proving finite raw-material conservation.

| Mode | This line's owned native craft checks | Types not reached by the bounded graph |
| --- | --- | --- |
| NORMAL | 81 before + 81 after restart | 4 |
| TECH_PROGRESSION | 75 before + 75 after restart | 32 |
| APOCALYPTIC | 70 before + 70 after restart | 47 |

The four common graph gaps are:

- Lead-acid and nickel-hydride batteries need water buckets. The graph does not model collecting water into a bucket.
- Petroplastic needs refinery or trade routes that the graph does not model.
- `electricadvantage:li_nugget` is registered and visible, but no consuming input or reachable route was found in these profiles. Its intended role remains uncertain; no new balance recipe was invented.

The additional 28 entries in **1.10 TECH_PROGRESSION** follow the graph's first diamond-rod provider. That Base Metals beta recipe has empty ingredients and cannot supply that path. Iron/steel rod alternatives can satisfy the same Advantage recipes, but those alternatives were not exercised natively as progression routes in this audit. The 32 graph gaps are therefore not proof that 32 player recipes are unobtainable. The 1.12 profile has only the common four gaps in this mode.

**APOCALYPTIC** has the common four plus 43 uncredited loot/trade component routes. Additional Loot Tables was absent and the native loot sample was empty. Actual delivery through those routes remains unproven; absence from this sample is not proof that the design is impossible.

Minecraft 1.10.2 has no 1.12 recipe-book unlock gate. The crafting checks concern actual native crafting and consumption instead.

### Final Evidence And Remaining Limits

The final rows above close the previously pending endurance, connected-world and reproducibility gates without changing any candidate pins. `final-production-pins-t.json` and `final-production-recheck-u.json` under `_verification/backport-coverage-20261030/` retain the 18 production artifact hashes and unchanged-HEAD checks. `final-hygiene-v.json` records the tracked-file and generated-output checks. Generated worlds and receipts remain local, not release content.

The endurance result remains one finite steam network/crusher/hopper circuit, not 30-minute coverage of every machine. Its 1,799.98-second figure uses the documented first-to-last-sample convention. Reproducibility used the retained pinned dependency cache; it is not a fresh-cache or default-profile bootstrap claim.

The two source-matched Base Metals 2.4 connected routes have a narrowly bound bucket diagnostic proof, recorded by the independent connected verification receipt above. The earlier strict failures remain retained. The later proof is tied to the exact Base Metals, Power, harness and bucket/restart evidence; it is not permission to ignore that diagnostic in other runs. The maintained Base Metals route used strict diagnostics.

The wider campaign's bounded frozen and connected migration checks also completed. Those fixture-specific results do not establish arbitrary player-world compatibility. Separate modern lifecycle, partial-network, bridge and drill/read reruns are recorded under each modern checkout's `test-rig/build/coverage-20261030-candidate-<id>-final-*` directories.

The original 1.14 holder-extended receipt remains FAIL: its last observation pair spans 38 native ticks against a 40-tick requirement, although the absence samples and unchanged event counters span 189 ticks without a reload. The corrected pure validator measures continuous absence while retaining the 40-native-tick minimum and load/unload counter checks. A separately authorized fresh-and-imported run then passed at 19:32:01 on 30 October, with identical production jars, target manifest and fixture geometry. No gameplay change was needed, and the original failure was not overwritten.

The strict pure-validator repair passed 41 focused unit cases per modern line, 1.13 through 1.16: 164 cases in total. Four independent raw audits passed 96 partial-network cases, 24 per line, while retaining the 40-native-tick minimum and rejecting observed or hidden reloads. These audits replay recorded measurements; they are not new native world runs. The proof is retained in `_verification/backport-coverage-20261030/extended-absence-repair/completed-summary.json`.

That fresh native follow-up is recorded in `PA114/PowerAdvantage/test-rig/build/coverage-20261030-candidate-e-final-holder-extended-continuity-v/native114-holder-extended-results.json`, relative to the version workspace. The matching exit-0 receipt is `_verification/backport-coverage-20261030/pa114-candidate-e-final-holder-extended-continuity-v.log.exit.json`. Source, helper and holder-protocol checks passed; both fresh and imported profiles are PASS. A separate audit of this new run also passed, bound to its exact report hash in `_verification/backport-coverage-20261030/pa114-native-continuity-v-independent.json`. The original failed evidence remains unchanged.

Still open are the recipe-graph gaps above, manual gameplay/visual checks outside the measured cases, arbitrary external capabilities and unlisted world/mod combinations. No release, publication or blanket compatibility claim follows from these bounded passes.
