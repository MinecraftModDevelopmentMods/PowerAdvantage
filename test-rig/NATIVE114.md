# Native 1.14.4 Harness

This test-only destination targets Forge 28.2.26, snapshot 20190719-1.14.3,
MCP 20190829.143755, FG 7.0.34, Gradle 9.6.1 and Java 8 bytecode. Run Gradle
with Java 17. When projects share a Gradle cache, run their builds one at a time,
including development server tasks.

```powershell
$env:JAVA_HOME='<Java 17 home>'
$env:GRADLE_USER_HOME='<shared Gradle cache>'
$env:ADVANTAGE_WORKS_JAVA8_HOME='<Java 8 home>'
$env:ADVANTAGE_WORKS_GRADLE_JAVA_HOME=$env:JAVA_HOME
./gradlew.bat -p test-rig test assemble verifyHarnessIsolation
./gradlew.bat -p test-rig verifyWorksNative114Inputs
```

Run those commands from the PA114 Power checkout while no other build is using
the shared cache. Input preflight checks identities and records actual hashes without
creating a runtime or opening a world. `-PworksProfile=dev-core` selects another
native profile. Workspace candidates remain explicitly UNQUALIFIED; a local
hash is provenance, not qualification. Rebuild/package child release jars before
packaged preflight. `powerAdvantageDeobfJar` and `powerAdvantageReleaseJar` can
select explicit native candidate inputs for compilation and isolation checks.

## Native APIs and Invariants

All main runtime sources compile against genuine mapped 1.14.4 classes and the
actual Power deobf jar. There are no dependency shims or vanilla shadow classes.
Native state/NBT/player/packet/recipe/loot APIs replace the 1.13 names. Chunk
release removes forced tickets; only real unload/load events count as lifecycle
evidence. `ServerChunkProvider.func_225313_a` is the mapped non-loading lookup
used for observations. No snapshot may force an unloaded witness back in.

Actor holds use native `canUpdate` and restore the original flag and tick count;
actors are not removed from native tracking indexes. These paths still require
real tick and restart witnesses, not merely matching serialized NBT. See the
[broader qualification record](../docs/1.14/BROADER-QUALIFICATION.md) for their
current live results, including passenger/leash restoration and respawn.

The native works descriptor changes only the target header. It selects the
immutable 1.13.2 station specification, preserving all 21 station IDs, bounds,
placements, controls, quantities, assertions, bug markers and manual observations.
The definition regression compares every station and district. Existing command
names and PASS/FAIL/EXPECTED_FAIL/UNEXPECTED_PASS/SKIP/MANUAL_REQUIRED meanings
remain unchanged. All original tests remain present.

## Destination Profiles

All standard Gradle runner task names now select `profiles/native114/`, never
the retained top-level historical profiles. Profiles create new runtimes under
`test-rig/build/works-native114`, bind to loopback, and retain the world-name and
allow-file interlocks. The default native flat generator uses vanilla defaults,
not a legacy semicolon generator string.

| Profiles | Port State | Runtime Qualification |
| --- | --- | --- |
| dev-core, dev-core-tech-progression, dev-core-apocalyptic | Native candidates | Pending |
| packaged-current | Native candidates | Bounded connected/lifecycle, movement-aware broad bays and final endurance pass; human observations remain open |
| packaged-decoupled | Native candidates | Broad bays pass without Base Metals; required OreSpawn remains present |
| packaged-optional-power | Native candidates | Native energy/fluid capability contracts pass; real third-party implementations remain manual |
| worldgen-advantage, worldgen-legacy-import | Native candidates; retained assertions | Sampling, import and profile-restart assertions passed |
| packaged-mineralogy, worldgen-mineralogy | Native candidates | Distillation, six oil-transfer lanes and generation assertions passed; wider acceptance pending |

The original top-level JSON files remain source manifests. Each native profile's
`legacySourceProfile` resolves explicitly into `PA113/PowerAdvantage/test-rig`.
Historical qualification and intermediate failures belong to that source tree;
they are not evidence for the new destination.

The Mineralogy profiles pin the genuine `6.0.1.114041` jar from its clean
`5bcec041775bed9de562075d177e33c507b2e2e1` checkout. Its SHA-256 is
`75CE58BCDCB845407DE55F476FDB06211E2F8DBE6FCB0AC0F7FBDB5B7841F7BA`.
An explicitly unported profile still fails before runtime creation; the profile
regression retains a blocked-input witness even though all ten current profiles
now have native candidates.

## Frozen Migration Destination

`migration/native114.py` is a separate destination adapter, requiring Python
3.11+ and the existing `migration/requirements.txt` dependencies. It does not
call the copied legacy convenience-profile discovery in `matrix.py`.

First pin actual packaged candidates and an installed native114 server supplied
by the operator. This command opens no world:

```text
python test-rig/migration/native114.py pin-target --server-seed <installed-native114-server> --output test-rig/build/native114-inputs.json
```

Then run conversion only against an explicitly selected disposable fixture:

```text
python test-rig/migration/native114.py run --source <immutable-source-runtime> --source-manifest <archived-PA113-profile-manifest> --source-key <source-profile-key> --source-hashes <approved-world-fingerprint.json> --source-snapshot migration-source.json --target-manifest test-rig/build/native114-inputs.json --java8 <java8.exe> --output test-rig/build/native114-conversion-new
```

For a retained 1.13 destination, select its actual existing snapshot filename,
such as `migration-restarted.json`, and its original pinned source key. Relative
source-manifest paths resolve at the original archived manifest, not at PA114.
Source manifests and fixture saves are never rewritten. The runner checks the
approved fingerprint before copying and the complete source runtime afterward.
Output paths must be new, confined to `test-rig/build`, and disjoint from inputs.
No source process is launched. The destination can only snapshot, save, stop and
restart; it cannot rebuild, refill or reset imported equipment. Existing state
legacy comparison rules remain the default, including failure on loss or duplication.
The native destination alone supplies an explicit platform adapter: vanilla 1.14
omits an empty chest `Lock`, so an omitted key and an empty string both mean
unlocked. Nonempty locks remain strict in both directions; custom machine NBT
is not covered by this exception. Original snapshots are never rewritten.
Runtime errors also fail a conversion even if Minecraft produces a snapshot.

## Remaining Campaign Gaps

- Thirteen frozen conversion routes passed with a full destination-process
  restart. Five archived connected sources also passed preservation and
  512+512 ticks of operation, with exact stock reconciliation and no rebuilding.
  Their source fingerprints remained unchanged. These are bounded checks, not
  actor, lifecycle or endurance acceptance.
- The original copied campaign defaults still target 1.13. Use the separate
  native adapters below for the qualified 1.14 destination, and the PA113
  checkout with explicit manifests for historical source construction.
- The new destination runner measures frozen state conversion and one full
  process restart only. It does not qualify machine operation, links, inventory
  transactions, live chunk unload orders, actors or death/respawn.
- The native respawn adapter explicitly binds
  `ServerPlayNetHandler.PERFORM_RESPAWN`, with guarded target-name translation
  and 72 passing server-side cases. Real client death-screen interaction and
  player travel remain manual.
- The native `client-resources/` probe and launcher have passed the combined
  model, texture, language, creative-entry, fluid-rendering and tank-gauge checks.
  Native recipe packets also pass all three recipe-book modes and the unchanged
  button-rendering threshold. Manual in-world graphical acceptance remains a
  separate gate. A main-menu probe does not qualify machine GUI interaction.
- Mineralogy distillation and boundary-aware ore/oil generation assertions passed
  with the pinned native jar. That does not qualify every boiler, fuel, bucket
  or third-party capability interaction. No artifact is substituted to fill a gap.

Never modify or launch original fixture saves. Preserve failed evidence and
keep source failures, conversion results, restarts and native operation separate.

## Additional Native Runners

These wrappers select genuine native inputs explicitly. They do not retarget the
historical campaign defaults or change their stock and state oracles.

| Runner | Scope |
| --- | --- |
| `migration/native114_connected.py` | Prebuilt source assemblies; initial preservation, 512 operating ticks, full restart, 512 more ticks; no rebuilding or refill |
| `migration/native114_recipes.py` | Packaged ingredient discoveries, native recipe packets, manufacturing progression, recipe modes, reloads and restarts |
| `migration/native114_oil.py` | Native oil-bucket placement, drain/pipe/container transfer, exact refill quantities and two full process restarts; six lanes when Mineralogy is pinned |
| `migration/native114_lifecycle.py` | Unchanged movement, real unload/load, mining and saved-work oracles; passed fresh and copied chained worlds |
| `migration/native114_extended.py`, `native114_partial.py` | Extended drill and partial-network observations; current result remains in the broader qualification record |
| `migration/native114_actors.py`, `native114_dimensions.py`, `native114_player_layout.py`, `native114_respawn.py` | Native destination adapters for archived player/entity/dimension fixtures and real server respawn handling |
| `migration/native114_transformer_order.py` | Live Base/Power migration-hook ordering checks |
| `migration/native114_broad.py` | Current and decoupled walkable bays with initial placement, tagged drill movement and restart assertions |
| `migration/native114_endurance.py` | Two measured thirty-minute environments, native feeding, exact batch conservation and full restart |
| `migration/native114_interop.py` | Native capability simulation and limits, converter conservation, portable tank loot, locked automation and restart |
| `migration/native114_refresh.py` | Refresh all thirteen frozen and five connected source routes against another explicitly pinned candidate |

Each wrapper has `--help`, requires a pinned `--target-manifest`, writes only new
outputs under `test-rig/build`, and records failures rather than replacing them
with successful reruns. Freeze candidate jars into hash-addressed artifact copies
before another build changes the development outputs; do not rewrite an earlier
manifest to point at replacement bytes.

The native lifecycle adapter accounts for asynchronous chunk transitions. After
restart it restores only fixture tickets, requires genuine Load events and loaded
witnesses, then selects the first measured tick advance. A queued Unload callback
must also arrive before the unchanged unload oracle runs; the original 45-second
budget is not extended. Immediate and pending observations are retained under
distinct names. Selection never reads progress, inventories or a desired verdict.
The inherited operations and all gameplay/state oracles remain unchanged.
