# Legacy Drill Progress Repair

## Result

`Q-CONNECTED-001` is repaired in the maintained 1.10.2 and 1.12.2 Steam and
Electric implementations. The 1.13.2 implementations already had the repair and
remain unchanged. The red native run reproduced the defect in all four affected
mod/version combinations; the repaired native run and all eight connected
migration routes pass. The original failed records remain intact.

This closes the intermediate drill-progress failure in
[Connected Legacy Works](CONNECTED-MIGRATION.md), not the other entries in the
[remaining behavioural checklist](CURRENT-QUALIFICATION.md#remaining-behavioural-checklist).
No commit, push, PR or publication is included.

## Implementation and Candidates

Both legacy drills saved `progress` and `targetX/targetY/targetZ`, but rebuilt
transient drops/hardness after loading through `targetBlock`, which reset that
saved progress. Restoration now remembers the saved value, reconstructs the
target and clamps the value to its current mining goal. Both the deferred
first-tick path and the already-attached-world NBT read path use this helper.

Steam's cached redstone flag also starts as `true`. Restoring it from the actual
world before the resumed tick prevents that uninitialized flag from discarding
the saved work. Actual redstone disabling, power costs, mining time factors,
output recipes and track behaviour are not changed.

Reading an untargeted tile clears old progress, target pointers and deferred
state instead of retaining work from an earlier NBT read. No registry, tile,
package, inventory, fluid, recipe, dependency or NBT key is renamed. This is a
runtime repair; existing worlds need no new datafixer for it.

| Version | Mod | Candidate | Local branch |
| --- | --- | --- | --- |
| 1.10.2 | Steam | `2.2.5.110021` | `feature/steamadvantage-1.10.2-drill-progress` |
| 1.10.2 | Electric | `2.2.5.110021` | `feature/electricadvantage-1.10.2-drill-progress` |
| 1.12.2 | Steam | `2.2.2.112021` | `feature/steamadvantage-1.12.2-drill-progress` |
| 1.12.2 | Electric | `2.2.3.112021` | Existing `feature/electricadvantage-1.12.2-turbine-persistence` |

The pending Electric 1.12 turbine repair remains present; this candidate builds
on it rather than replacing it. PowerAdvantage production code and all 1.13
release versions/hashes remain unchanged. The Power 1.10 branch
`feature/poweradvantage-1.10.2-drill-regression-profiles` changes only mutable
rig profiles to reference the new Steam/Electric filenames, versions and hashes.

| Release jar | SHA-256 |
| --- | --- |
| `SteamAdvantage-2.2.5.110021.jar` | `1C9DE2F9EE29439B727D2087E18BAD5277115BCCF7162F0F9342F322F44F9E9E` |
| `ElectricAdvantage-2.2.5.110021.jar` | `1854A854332FC02151C289BAEAF9DC3C6677ED6CDC1ED04EA8EDD0CB54A085F5` |
| `SteamAdvantage-2.2.2.112021.jar` | `A249E7A90729519BD19040F3BF6042E871E61EFF7A67E4CEF464458B1E9F0A15` |
| `ElectricAdvantage-2.2.3.112021.jar` | `B5AB6769E05FE87CED655EB4B729C3EE016BD9D069DDC7111432F061BA7D08E2` |

Each jar has exactly the previous entry set. Only its drill class, version-bearing
main class, manifest and `mcmod.info` differ from the preserved pre-fix jar.
Every other resource/class is byte-identical. `javap -protected` confirms the
drill's public/protected API is unchanged. Release audits reject embedded
dependency/test/agent content and require Java 8 bytecode.

## Native Red-to-Green Test

`drill_restart.py` starts with copies of the existing working, connected
maintained 1.10 and 1.12 fixtures. The unchanged 1.13 control imports the latter.
It never builds or refills the equipment and writes no inventory, energy or
progress NBT. A maximum of 100 seven-tick intervals is allowed to reach a
natural mid-cycle checkpoint with both drills powered. That checkpoint is saved
and the process stopped.

Each runtime then undergoes two complete restarts. At each restart the test:

1. Compares the entire saved block/tile state before any machine ticks.
2. Permits exactly one native tick, requiring both saved targets and original
   tile witnesses to remain and each drill's progress to increase by exactly one.
3. Permits 620 more native ticks and checks actual item output and conservation,
   finite water/energy and retained tile instances.
4. Saves and shuts down. Before the second restart, bounded natural ticking
   again selects a mid-cycle checkpoint; no progress is supplied by the harness.

At the first restart in both legacy runtimes:

| Drill | Saved progress | Unpatched first tick | Repaired first tick |
| --- | --- | --- | --- |
| Steam | 17 | 0: FAIL | 18: PASS |
| Electric | 209 | 1: FAIL | 210: PASS |

Both repaired versions and the unchanged 1.13 control also pass their second
restart and subsequent production interval. This gives twelve successful
first-tick observations across six mod/version cases. The native evidence audit
reports `PASS`; the original run remains `FAIL`, with a separate audit reporting
`REPRODUCED_FOUR_LEGACY_FAILURES` rather than rewriting its verdict.

## Migration Rerun

All five historical source profiles remain pinned to their original jars,
including the affected maintained source jars. The only bridge changes are the
explicit new Steam/Electric 1.12 candidates. The 1.13 target jars and every
Base Metals/OreSpawn/Power dependency remain unchanged.

The eight direct/chained routes rerun the connected source construction,
803-tick source qualification, 512-tick intermediate/target intervals and full
process restarts described in the original protocol. All eight routes now pass,
including all three formerly failing 1.12 intermediate restarts. All 22 frozen
state comparisons have zero differences. The independent audit passes 491
checks, including all source fingerprints and archived/staged jar checks.

Historical sources can still contain the old runtime defect: they provide
unchanged saved fixtures, not repaired historical binaries. The native candidate
test separately proves the fixed 1.10 and 1.12 builds operate and restart
correctly. State/operation success is not a clean-log or whole-modpack claim;
known Forge/Base Metals/OreSpawn diagnostics remain in the raw console records.

## Build and Test Evidence

All four affected mods pass:

```text
clean check build javadoc verifyReleaseDependencies verifyReleaseArtifacts writeReleaseChecksums verifyEclipseProductionClasspath
```

The four legacy builds pass 18 JUnit tests in total, including ten new drill
persistence tests and the retained turbine/OreSpawn tests. The Python runner and
oracle suite passes 162 tests. Sources, Javadoc, deobf and release jars are built;
Eclipse launch metadata is regenerated with production/test isolation.

The removed local OreSpawn 4.0.8 deobf dependency was rebuilt from pinned commit
`5a50df1158e948c8db55814e819090b34c12d765` in a disposable checkout. Its SHA-256
is exactly the existing pin
`33BFB8C9D626D071147A0952A74B633F0BCC6A27E0408B4524370F4833B003C2`.
The build uses `-PoreSpawnDeobfJar=<rebuilt-jar>`; it does not upgrade OreSpawn or
alter its active checkout. A future clean build still needs that exact artifact
or its documented pinned-source bootstrap.

Local ignored evidence is under `run-drill-progress/`:

```text
20261023-before-artifacts/          Pre-fix jars and failed unit-test records
20261023-red/                       Unpatched native reproduction and audit
20261023-candidate-inputs/          Immutable candidate inputs and replacement provenance
20261023-green/                     Repaired native run and audit
20261023-matrix-original/           Original/early 1.10 direct and chained routes
20261023-matrix-maintained/         Maintained 1.10 direct and chained routes
20261023-matrix-112/                Early/maintained 1.12 direct routes
20261023-matrix-audit.json          Combined eight-route audit
20261023-artifact-comparison.json   Exact entry/resource/API comparison
20261023-workspace-profiles/        Mutable 1.10 profile update record
```

The runners take explicit hash-pinned manifests. Use `pin_drill_profiles.py`
to archive pre-fix outputs and prepare `native110`/`bridge112` overrides without
changing historical source profiles; `drill_restart.py` performs the native
checks and `audit_drill_restart.py` replays the raw evidence. See each script's
`--help` and the [runner instructions](../../test-rig/migration/README.md).

Original worlds, earlier failed evidence and immutable historical jars remain
untouched. Real-client GUIs, arbitrary unload timing and other outstanding
machine behaviours are not newly accepted by this focused repair.
