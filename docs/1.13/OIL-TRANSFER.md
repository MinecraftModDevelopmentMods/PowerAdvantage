# Oil Transfer And Full Restart

## Scope

The ordinary rig already covered oil distillation and tank persistence, but its drain bays did not assert the entire bounded oil-source-to-container path. This focused protocol exercises four independent lanes in a fresh, marked disposable world:

| Oil | Destination |
| --- | --- |
| Power crude oil | Portable fluid storage tank |
| Power crude oil | Metal fluid container |
| Power refined oil | Portable fluid storage tank |
| Power refined oil | Metal fluid container |

Each lane has a glass-bounded source directly above a drain, three descending pipe blocks, three horizontal pipe blocks, and an initially empty container. No tanks or drains are seeded with fluid. The harness calls the native bucket placement method and immediately verifies the resulting source-fluid identity.

1. Place one bucket per lane and allow eight seconds of normal server ticking. Require the source to be gone, the drain empty, and exactly 1,000 mB of the correct oil in the destination.
2. Save and stop the entire server process. Start a new process and load the same world; require exactly the same identities and quantities.
3. Add a second bucket at each source position without replacing any drain, pipe, or container, changing tile NBT, or offering fluid through the network API. Require exactly 2,000 mB per destination, an empty drain, and unchanged endpoint object identities within this process.
4. Save, stop, and start another process. Require the 2,000 mB contents to persist without loss, duplication, or conversion to water.

Every pipe and endpoint must still be present. Assertions reconcile all fluid-holding members of each lane, not animation or gauge appearance. The test does not claim full-container backpressure, oil scarcity, filtering, Mineralogy-oil interoperability, or migrated-world coverage; those remain separate protocols/manual scenarios.

## Local Qualification: 21 October 2026

`run-oil-transfer/20261021-102135/oil-transfer-results.json` reports PASS for all four lanes in all four phases: first fill, restored, refilled, and retained. Each destination held exactly 1,000 mB after the first fill/restart and 2,000 mB after refill/the second restart. Every drain ended empty and both oil identities remained intact. Three separate server processes started and shut down normally. The packaged inputs were Power `2.4.4.113021`, Steam/Electric `2.2.5.113021`, Base Metals `3.0.1.113021` and OreSpawn `4.1.0.113021`; their exact hashes and the harness hash are retained in the report. Power/harness profile pins were refreshed for these locally rebuilt candidates; older reports and staged jars were not rewritten.

The original combined native client report at `build/client-resource-runs/20261021-102145-66fb7337/client-resources.json` lists all three Advantage mod IDs. Its Power-scoped resource, oil-renderer and tank-gauge checks passed (33 Power items, 253 block states; 87 shared creative-tab entries with extensions loaded). Logs contain zero Power model/blockstate warnings, but 38 missing-model and two blockstate warnings for Steam, plus 76 missing-model and three blockstate warnings for Electric. Those defects were outside the original oil-test change and were subsequently repaired as recorded below. Original test saves were not opened or altered.

## Combined Resource Repair: 21 October 2026

Steam's 17 blockstate files and Electric's 25 now reference literal `namespace:block/...` model paths. The five obsolete `normal` selectors are replaced with the empty variant selector. Existing variant rotations, model geometry, registry IDs and production classes are unchanged: all 66 Steam and 91 Electric production classes compare byte-for-byte with the previously qualified packaged jars. Modern item/block translation aliases supplement, rather than replace, legacy GUI keys in all 16 Steam and 14 Electric locales.

Static resource tests reproduced the bad paths/selectors before repair. Their results are retained under `build/resource-repair-red/3ff06a5e70e14945bb3dc51d9b949c85/`. The expanded native probe also failed on untranslated expansion names at `build/client-resource-runs/20261021-104923-ffe68a39/` and the hidden turret variant at `build/client-resource-runs/20261021-105147-324743c9/`.

After repair, `build/client-resource-runs/20261021-105354-cd766247/client-resources.json` reports PASS for 94 registered items and all 905 valid block states across Power, Steam and Electric, plus 87 shared creative-tab entries. The final rebuilt/whitespace-normalized jars repeat that result at `build/client-resource-runs/20261021-105931-dea220ee/client-resources.json`. All three namespaces have zero missing-model or blockstate warnings. Oil sprites/tints, water-gauge pixels, tank neighbour culling, and full/empty tank packet checks continue to pass. The combined runner now rejects resource errors from either expansion instead of reporting a Power-only result. This is native resource acceptance, not a substitute for manual inspection of entity animations or in-world machine operation.

Steam's 10 unit tests, Electric's 15, Power's 33, and the harness's 15 pass. Release/dependency/archive, Eclipse and profile-pin audits pass. Checksum tasks now declare their jar inputs so subsequent resource rebuilds cannot leave a stale `SHA256SUMS`; the resulting files were independently checked against the rebuilt jars. No saved world was opened, and no commit, push or publication was performed.

## Re-run

Run Gradle with Java 17 and Minecraft with Java 8. Build the current Power candidate and harness, then deliberately refresh the corresponding SHA-256 fields in `test-rig/profiles/packaged-current.json` and any other current-candidate profiles before running. Do not rewrite archived run manifests.

```text
gradlew.bat check assemble prepareCombinedEclipseLaunch
gradlew.bat -p test-rig test assemble
test-rig/migration/.venv/Scripts/python.exe -m unittest discover -s test-rig/migration -p test_oil_transfer.py
test-rig/migration/.venv/Scripts/python.exe test-rig/migration/oil_transfer.py --java8 "<Java 8 home>/bin/java.exe"
```

The script uses the existing qualified Forge 1.13.2 server-library seed named in its source, never that seed's world. It requires a fresh output directory under PowerAdvantage, verifies every packaged mod hash, binds only `127.0.0.1`, and closes its servers even after an assertion failure. `--output`, `--port` (default `25588`) and `--seconds` (default `8`, minimum `2`) are available. All three Advantage mods, Base Metals and OreSpawn are loaded. The isolated fixtures start at `11264,80,2056`, with subsequent lanes 64 blocks east; their geometry is declared by `fixtures()` and retained in `lifecycle-fixture.json`.

Each run retains `input-jars.json`, complete console logs, per-phase lifecycle snapshots, and `oil-transfer-results.json` beneath `run-oil-transfer/<timestamp>/`. A failure stays a failure; the source test world and previous results are not modified. Oracle unit tests deliberately substitute water, lose/duplicate oil, leave oil in the drain, and replace an endpoint, requiring each mutation to fail.

## Combined Client

`prepareCombinedEclipseLaunch` generates `runClientAllAdvantage.launch` beside `runClient.launch`. Refresh Eclipse before selecting it. It shares the ordinary launch's `run` directory, saves, Forge bootstrap and Power `bin/main` output, but adds the pinned Steam/Electric deobf jars. The base launch is not changed. Neither the lifecycle harness nor resource probe is added to this normal play-test profile.

The native client probe can verify all three mod IDs are loaded:

```text
gradlew.bat clientResourceProbeJar prepareCombinedEclipseLaunch
test-rig/client-resources/run.ps1 -JavaHome "<Java 8 home>" -LaunchProfile runClientAllAdvantage.launch -RequireExtensions
```

With `-RequireExtensions`, this probe validates static assets and item names for all three mods, as well as Power's oil renderer and tank checks. It rejects missing models, missing textures and blockstate errors in any Advantage namespace. Logs and failed reports remain retained; in-world rendering, animated entities and gameplay still need their own acceptance checks.
