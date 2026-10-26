# Native 1.15 Core Runtime Qualification

## Candidate And Scope

These are local packaged-server tests for Minecraft 1.15.2, Forge 31.2.57 and
Java 8. No 1.14 result is counted as a 1.15 pass. Graphical acceptance and the
separate actor, connected, endurance, recipe and interop campaigns are outside
this record.

The sealed input is `test-rig/build/native115-inputs-20261026-final-d-archived.json`,
SHA-256 `3605B9BBF2B5C94C8278B31B5984969DD55B8F277E3B994496F045FA7CA79BE8`.
It is not rewritten or repinned during qualification. All jars are staged from
its hash-addressed archive paths, not mutable build output.

Candidate D includes the startup recipe-condition registration repair. Its full
core matrix completed on 2026-10-26; earlier candidate A passes were not reused.

| Artifact | SHA-256 |
| --- | --- |
| Power 2.4.6.115021 | `F86454B9B5282A195F5C8FC58BEDEDB751DE3EB55039A9AC3A9A8495B1C0293C` |
| Steam 2.2.7.115021 | `564F730D3D0C79A0C73776120D40D634C668B7C7A8E2E85350A8C753FE64C3D2` |
| Electric 2.2.7.115021 | `C4084B709221C9D9E9175B342B692C3CF4278790EEA1AA88FFBBF8150354249E` |
| Harness C 1.1.0.115021 | `B8F733F37B51A2EE8A0C11F21C1C84336FD8FCCDE998611DA334021C29CC34FA` |
| Base Metals | `EE951E23DAE0B7D98C13D8AE8738AC69ADDB7C096C26E51D1E55E5083C07E4DD` |
| OreSpawn | `8E36F1B093F2ACFFB97A2ACD0579A8B585ACBBD6996F3853AFAAE54EBBEC2913` |

Campaigns run a fresh native world and then a disposable copy of the original-1.10 chained
source. The canonical source runtime at
`PA113/PowerAdvantage/run-qualification/20261023-current/matrix/original110-chained-113`
is never launched. Its archived profile and the accepted
`PA114/PowerAdvantage/test-rig/build/original110-chained-113-retained13-hashes.json`
are checked before staging. Frozen conversion must pass before live construction
is authorized. Complete source-runtime fingerprints are compared again afterward.

## Engine Geometry

The exact installed mapped source archive is
`.gradle/mavenizer/repo/net/minecraftforge/forge/1.15.2-31.2.57/forge-1.15.2-31.2.57-sources-snapshot-20200514-1.15.1.jar`,
SHA-256 `937F0B7D7098D4D1A12EA306108122B1E443CA638D120D9040FF2B577E9A0314`.
It defines:

- `ChunkManager.java:96`: `MAX_LOADED_LEVEL = 33 + ChunkStatus.maxDistance()`.
- `TicketManager.java:187`: a forced ticket at level 31.
- `ChunkStatus.java:95` and `:163`: eleven dependency ranges.
- `ChunkManager.java:425`: the actual queued Forge `ChunkEvent.Unload`.

The native harness observes engine values and actual forced tickets without
loading witness chunks. The first real 1.15 load observation confirms FULL 33,
entity-ticking/forced level 31, distance 11, last holder level 44 and radius 13.
Every raw snapshot must agree with `native115_geometry.py`; independent auditing
also scans all raw snapshots, not only the selected canonical observations.
Matching 1.14 numbers are therefore an observed 1.15 result, not an assumption.

## Phase Ledger

All evidence directories below are under ignored `test-rig/build/`.

| Phase | Evidence directory | Status |
| --- | --- | --- |
| Twelve-route lifecycle and all seven drill groups | `n115-core-20261026-lifecycle-d` | PASS in both worlds; independent audit PASS |
| Holder-aware partial networks and both load orders | `n115-core-20261026-holder-partial-d` | PASS in both worlds; independent audit PASS |
| Holder-aware pure-pipe bridge | `n115-core-20261026-holder-bridge-d` | PASS in both worlds; independent audit PASS |
| Eighteen-route extended movement and networks | `n115-core-20261026-holder-extended-d` | PASS in both worlds; independent audit PASS |
| Eighteen-route saved-read and unfinished-work guards | `n115-core-20261026-reads-d` | PASS in both worlds; independent audit PASS |
| Original adjacent-network control | `n115-core-20261026-original-partial-d` | Expected unload FAIL in both worlds; evidence audit PASS |
| Original empty-bay control | `n115-core-20261026-empty-control-d` | Expected unload FAIL in both worlds; evidence audit PASS |

The independent auditor recomputes historical movement, mining, progress,
changed-target, partial-network, bridge, retained-identity and conservation
oracles from raw JSON. Canonical observations must be exact byte copies selected
by real chunk events and advancing world ticks, never by a desired verdict.
Failures, command exceptions, incomplete runs and setup timing warnings are
retained. New attempts use new output paths.

## Findings

- Lifecycle: all seven twelve-drill groups pass in each world, including five
  genuine chunk cycles, normal mining, unfinished progress and full restarts.
- Partial networks: both unload directions and both load orders pass for steam,
  electricity and fluid. Each world records 54 actual unloads and 54 traced
  reloads, with no acquisition stack from Power's conduit-network code.
- Pipe-only bridge: all three cycles pass for all three networks in each world.
  Disconnected fluid offers accept zero; measured reconnection offers retain
  exactly 3,000 mB per world without replacing an endpoint.
- Extended drills: all eighteen routes complete four moves per world. Low
  reserve, blocked output, retained inventory/energy/facing, output recovery and
  redstone cases pass. Each world also passes all 21 restart-state cases.
- Saved reads: all eighteen routes pass unfinished-work chunk and process
  reloads, legacy reads and changed-target guards, with exact tagged-item
  conservation. Selection uses the first ready real-tick observation, not a
  later convenient verdict.

The original adjacent-network and empty-bay controls both retain FAIL results:
their endpoints never genuinely unload, so the strict reload and load-order
assertions also fail. All four control profiles have identical failure
signatures, including the worlds with no networks or machines in the bays.
This is the small fixture's forced-ticket limitation, not evidence that an
Advantage network reacquired a missing chunk. The holder-aware layouts clear
the verified thirteen-chunk dependency halo and pass the unchanged oracles.
No unload flags, events, counters, ticks or expected results were fabricated.

Every D campaign passes its independent evidence audit and reports unchanged
protected source-runtime and protocol fingerprints. Normal save/stop sequences
are retained for every boot. Setup catch-up warnings are preserved in the
audits; these results do not claim lag-free region loading or replace the
separate endurance qualification.

No unresolved core network or drill defect was found in this matrix. The last
control completed at `2026-10-26T13:52:49Z`. Client GUI/rendering acceptance,
including the hydroturbine world check, remains separate.

## Superseded Candidate A

Candidate A's lifecycle, holder partial, holder bridge and saved-read campaigns
passed in both worlds, including their independent audits. Those reports remain
under the corresponding `n115-core-20261026-*-a` directories. The original sealed
manifest `native115-inputs-20261026-current-archived.json` remains unchanged at
SHA-256 `5608065DAA5C45309C9A38D5E420A0E2EFCF2496E6D9F00DA7B5B1836EFDCBA7`.
These passes do not close the intermittent startup registration race observed
in the separate actor campaign. A's extended and control phases were not run;
the full matrix is repeated against D instead.

## Test Tooling

The adapters now accept core port 25580, and the isolated failure-retention unit
test no longer tries to bind an active server port. Both repairs have regression
coverage; earlier failed output is retained. The queued-unload comment now names
the verified 1.15 behavior. No gameplay or Java harness change was needed here.

The independent auditor also covers the twelve-route lifecycle, every raw
engine-geometry observation and archived protocol fingerprints. Its controls
reject missing or altered proof and check that observations stay unchanged.
The focused native and historical-oracle suite has 106 passing tests, separate
from the packaged runtime results above.

See [TEST-HARNESS.md](TEST-HARNESS.md) for the overall test matrix and rerun
commands.
