# Repeating the 1.15.2 Tests

Work from `D:\PowerAdvantage\PA115\PowerAdvantage`. The harness is a separate
test mod, never part of a Power Advantage release. The
[current test record](CURRENT-QUALIFICATION.md) separates build, operating,
migration and manual results. Earlier Minecraft results do not qualify a
1.15 candidate.

## Setup And Inputs

```powershell
$py = 'D:\PowerAdvantage\PA113\PowerAdvantage\test-rig\migration\.venv\Scripts\python.exe'
$j8 = 'C:\Users\johnb\.jdks\temurin-8.0.502+7\jdk8u502-b07\bin\java.exe'
$old = 'D:\PowerAdvantage\PA114\PowerAdvantage\test-rig\build'
$seed = 'D:\PowerAdvantage\_verification\pa115-native-seed-20261026'
$target = 'test-rig/build/native115-inputs-20261026-final-f-archived.json'
$m = 'test-rig/migration'
$env:PYTHONDONTWRITEBYTECODE = '1'
$env:TEMP = Join-Path $PWD 'test-rig/build/python-temp'
$env:TMP = $env:TEMP
New-Item -ItemType Directory -Path $env:TEMP -Force | Out-Null
```

The [current test record](CURRENT-QUALIFICATION.md) lists the three production
jar hashes. The archived F manifest pins those unchanged jars, the local
Base Metals 3.0.2 world-hook fix, OreSpawn 4.1.1 and harness E. Older manifests
and failed runs remain intact. Missing or changed pins fail before staging;
create a new manifest and output directory for a different candidate.

Harness E 1.1.0.115021 uses bounded world-generation sampling. Its 95 tests
cover sampling order and counts, fresh completion reports and owned-process
cleanup. The sample radius, halo, height range, watchdog and expected results
are unchanged:

| Artifact | SHA-256 |
| --- | --- |
| Release | `C854AFC08344C9B5E53AC8B35F9AA0B3E3F60E8180BF1C5F40AC7B5DF594890B` |
| Deobf | `767450DF1743A0E14414970C791FC8FA0EF54E61CAECDC9C13FB52A27DE7D8E3` |
| Runner | `21DF61B5501D3E5E0EB10394773C5D86C5AFCEB478AE09426AA8B980F267C361` |

Harness build logs and the retained red/green cleanup tests are under
`D:\PowerAdvantage\_verification\worldgen-sampling-e-20261026\runner-cleanup\`.
Use quoted complete arguments for paths with spaces. To select local JDKs,
pass `-Porg.gradle.java.installations.paths=<comma-separated JDK paths>`,
`-Porg.gradle.java.installations.auto-detect=false` and
`-Porg.gradle.java.installations.auto-download=false`. JVM `-D` options
do not enforce these Gradle project settings.

The recorded candidate is immutable throughout campaigns. Do not rerun
pinning against an existing output or
modify a running campaign's inputs. The CPU-only Python command is independent.

```powershell
./gradlew.bat -p test-rig test assemble verifyHarnessIsolation
& $py -B -m unittest discover -s test-rig/migration -p 'test_*.py'
./gradlew.bat -p test-rig verifyWorksNative115Inputs
# Only for a NEW future candidate, never overwrite the sealed $target:
# & $py "$m/native115.py" pin-target --server-seed $seed --output <NEW manifest>
```

Hash-address and freeze all candidate jars before another build can replace
them. Input manifests are immutable. Outputs below must not already exist;
use a new run suffix after any failed attempt.

## Frozen And Connected Sources

These are the exact retained final 114 proof paths. Their `sourceHashes` refer
to original historical worlds, NOT approval of native114 destination worlds.

```powershell
& $py "$m/native115_campaign.py" prepare --frozen-evidence "$old/native114-broader-frozen-20261025-i/native114-refresh-results.json" --connected-evidence "$old/native114-broader-connected-20261025-i/native114-refresh-results.json" --output test-rig/build/native115-source-plan-a
# Completed source generation used the following NEW output after retaining -a:
# & $py "$m/native115_campaign.py" generate114 --routes test-rig/build/native115-source-plan-a/native115-upgrade-routes.json --java8 $j8 --port 25595 --output test-rig/build/native115-disposable114-b
$routes = 'test-rig/build/native115-disposable114-b/native115-upgrade-routes.json'
& $py "$m/native115_campaign.py" run frozen --routes $routes --target-manifest $target --java8 $j8 --port 25595 --output test-rig/build/native115-frozen-a
& $py "$m/native115_campaign.py" run connected --routes $routes --target-manifest $target --java8 $j8 --port 25595 --output test-rig/build/native115-connected-a
```

Required: 14 frozen cases (13 original/current 110/112 direct/chained/113,
plus a new accepted114 comprehensive 355-block/332-tile source) and ten connected
cases (five historical plus five disposable native114 sources). Each connected
destination preserves its initial state and operates 512+512 ticks across a full
restart, without rebuilding/refilling. A freshly fingerprinted old destination
cannot replace a generated source receipt.

The `-b` bundle is source-ready: all six disposable114 receipts and all 24
route inputs passed the final read-only readiness audit. Routes SHA-256:
`A5241F5F41426DC57DD85A15B62B211415BE8B69D004154F8FC47DDF239B35C2`.
Audit: `test-rig/build/native115-disposable114-b/source-readiness.json`.
The comprehensive fixture retained 355 blocks, 332 tiles and 2,654 item witnesses.
All five connected sources passed two 512-tick intervals, preservation and
clean restarts with every lane, both drills and battery present. Canonical
world/runtime protections passed. Source readiness is not a native115 pass.

Failed `native115-disposable114-a` is retained. Its actual comprehensive
conversion/restart/idempotence passed, but receipt recording rejected metadata
whose parent directory overlapped the new runtime. Metadata now uses the
disjoint `source-records/` sibling; the original guard remains strict and four
focused source-receipt tests pass. Never reuse or overwrite failed output paths.

## Other Suites

Each command takes `--target-manifest $target --java8 $j8 --output <NEW build path>`.
Lifecycle/endurance additionally take the explicit source, archived source
manifest/key and approved hashes from the route plan. Never use copied matrix
convenience defaults or treat smoke duration as endurance.

| Runner | Required Scope | Ports |
| --- | --- | --- |
| `native115_actors.py --kind actors` | All eight historical actor routes; approved disposable114 counterparts when prepared | 25584 |
| `native115_actors.py --kind dimensions` | Nether and End, all routes, frozen/restart/live state | 25584, sequential |
| `native115_player_layout.py` | Inventory/ender storage and layout audit | 25584, sequential |
| `native115_respawn.py` | Nether/End death, bed/forced/missing spawn, keepInventory, restart | 25584, sequential |
| `native115_transformer_order.py` | Both source versions, every original hook order | 25584 after actors |
| `native115_holder_networks.py --campaign partial/bridge/extended` | Real holder unload/load and retained drill/network oracles | 25580 |
| `native115_ticket_networks.py --campaign partial/bridge/extended` | Full-status halo protocol, distinct from holder-unload proof | 25581 |
| `native115_lifecycle.py`, `native115_partial.py`, `native115_extended.py` | Chunk/movement/mining/progress/read guards and reload order | 25580 sequential |
| `native115_recipes.py --campaign all` | Discovery, packets, all modes, progression, reload/restart | 25577 |
| `native115_oil.py` | Four lanes; six with pinned Mineralogy; exact refill/two restarts | 25576 |
| `native115_interop.py`, `native115_harvest.py` | Capabilities/portable loot/locks and real tool/silk drops | 25592 / 25596 |
| `native115_broad.py --profile both` | Current and decoupled full works | 25588 |
| `native115_endurance.py --seconds 1800` | Both fresh/copied environments and independent conservation audit | 25590-25591 |
| `verifyWorksWorldgenAdvantage`, `verifyWorksWorldgenLegacyImport`, `verifyWorksWorldgenMineralogy` | Native ownership/import/user overrides and restart; distinct Gradle runs | exclusive Gradle slot |

Original actor/Nether/End/respawn/order proof roots are respectively
`native114-broader-actors-20261025-i`, `native114-broader-dimensions-{nether,end}-20261025-i`,
`native114-broader-respawn-{nether,end}-20261025-i`, and
`native114-broader-order-20261025-i` under `$old`. Reuse their original approved
fixture provenance; do not freshly approve those destination saves. Client
resource/render/recipe-button probes belong to the separate client agent.

## Concurrency And Results

Start with two server processes; four is the absolute ceiling, not a target.
Keep frozen/source-generation/connected phases sequential. Actor/dimension/
respawn/order jobs may overlap only with distinct ports AND distinct NEW writable
runtimes and immutable shared sources. One order or dimension process can
contain several sequential boots. Endurance reserves its port pair. No parallel
Gradle tasks. Measure free disk before source staging; do not delete evidence to
recover space without approval. Archive each report's pins, original source
fingerprints, actual native ticket observations, failure logs and restart proof.
