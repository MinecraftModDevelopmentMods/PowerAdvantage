# Partial Networks And Extended Drills

The final registration-fixed **i** candidate passes all four campaigns and
their independent audits in both worlds. Earlier failures and fixture controls
remain recorded below. See [Broader Qualification](BROADER-QUALIFICATION.md)
for the complete candidate and manual-testing handoff.

## Candidate And Method

This is a local 1.14.4 qualification record, not a release approval. The first
broader runs use the immutable `native114-inputs-20261023-d-archived.json`
manifest. Power, Steam and Electric release hashes are:

| Mod | Version | SHA-256 |
| --- | --- | --- |
| Power Advantage | 2.4.5.114041 | `A0C884C3A61F1B5555F2BD5B5472D8ACBEA53EBE7291A306EBA9AFBFCCF9A011` |
| Steam Advantage | 2.2.6.114041 | `444872113E13D806D4520CCBD9B3230C931353D43875BAE57EB9B5FC86614669` |
| Electric Advantage | 2.2.6.114041 | `5BE41447AF5AA3AF60C145B3A862BC42802C620C637D1E65810B350A6A9AFBA9` |

The native adapters reuse the historical fixture geometry, movement-cost,
inventory, fluid, retained-identity, sustained-unload and restart oracles without
editing their definitions. Canonical observations are exact byte copies of real
server snapshots selected by actual chunk callbacks and advancing world ticks,
never by whether machine NBT happens to match an expectation.

Each campaign runs in a fresh native world and a disposable copy of the retained
original-1.10-through-1.13 migration fixture. The source runtime is fingerprinted
before and after. The copied world must first pass a separate frozen conversion
comparison before construction or operating authority is granted.

The evidence stays in ignored `test-rig/build/` directories. The original
fixtures, original player saves and historical scripts remain unchanged.

## First Results

| Campaign | Evidence directory | Result |
| --- | --- | --- |
| Partial network trace | `native114-broader-partial-20261025-trace-a` | FAIL in both worlds; independent evidence audit PASS |
| Empty-bay ticket control | `native114-broader-partial-20261025-empty-control-a` | Same unload failure without constructing any Advantage network; evidence audit PASS |
| Pipe-only bridge | `native114-broader-partial-20261025-bridge-a` | FAIL in both worlds; bridge-isolation command rejected before a complete cycle |
| Extended drills and networks | `native114-broader-partial-20261025-extended-a` | FAIL; fresh autosave crash, copied-world drill stages pass but partial-unload stages fail |

All 18 drills in each extended world completed four moves, with all 18 strict
movement and insufficient-reserve case verdicts passing. This establishes 144
observed moves, including every facing, vertical tracks and down-first junction
selection. The copied world also passes all 18 blocked-output, post-restart
blocked-output, resumed-output and redstone cases, plus all 21 restart-state
cases. The fresh world did not reach those later stages. Neither world qualifies
partial unloading.

## Native Fixture Limitation

The partial-network failure is not evidence of the old unwanted network-loading
bug returning. The selected chunks never produced unload callbacks in these
runs. The same failure occurs with empty bays and no network or machine blocks.

Native Forge 28.2.26's `TicketManager.func_219364_a` installs a level-31 forced
ticket. That ticket keeps neighboring chunks loaded. The old fixtures put both
endpoints in adjacent chunks and initially force every chunk intersecting their
bounds; their release groups do not remove all those spare bound tickets.
Removing a selected endpoint's own ticket therefore does not establish that it
can actually become absent on this platform.

The existing harness safety limit is 32 blocks per fixture axis. A follow-up
needs a separately bounded, ticket-halo-aware native layout and explicit support
ticket controls. Keep the original fixture and failed evidence, retain the
forty-real-tick absence requirement, and still require actual unload/load
callbacks, unchanged retained tile objects, exact zero disconnected acceptance,
and normal recovery without replacement. Do not label the present runs PASS or
remove the safety guard merely to get a result.

## Autosave Blocker

The fresh extended process crashed on its first timed autosave, after earlier
`save-all flush` commands had reported an unexpected command error. The crash
starts in `WorldPersistenceHooks.lambda$handleWorldDataSave$0`, line 40. Its
original report is retained under the fresh runtime's `crash-reports/` folder.

Forge stores persistence hooks in a plain static `ArrayList`. Both Power and
Base Metals register their hooks during parallel mod construction. This makes a
registration race a plausible explanation for a null hook, but the crash stack
alone does not establish the exact registration order or root cause. Production
and sibling code have not been changed by this test campaign. This is a manual
testing blocker until diagnosed, repaired if necessary, and retested against a
new pinned artifact manifest.

An isolated control subsequently exercised the actual Forge 28.2.26
`WorldPersistenceHooks.addHook` method, using two threads and only non-null hooks.
Across 250,000 trials it produced 258 null entries and 13 trials with lost
entries; the sequential control passed. Its source, bytecode and JSON result
are retained under `native114-broader-partial-20261025-hook-race`. The native
recompiled Forge/Minecraft classpath hash is
`E95D6915D12D46E441B20CDC9964B9DB2F75158581BDAC2BBB2DEB489EFE7B5B`.
This demonstrates the unsafe registration mechanism, not the exact interleaving
of the crashed game process. Queuing Power's registration after parallel mod
construction is a narrow repair candidate.

## Converter-Fix Candidate Rerun

The newer immutable manifest
`native114-inputs-20261025-fixed-h-archived.json` pins Power release
`15BAF794507BED84C879358CADD1E4C6F8D0A6B88179597391A675BE3315B834`
and harness
`64BC8390743999C5C2823EDB9E8D25CC557DC87E679ED1F1104A01AA882ECDD9`.
Steam, Electric, Base Metals and OreSpawn remain the same pinned artifacts.
The only Power archive change is `TileEntityConverter.class`; the hook
registration risk has not been repaired by that converter change.

The trace and bridge reruns in `-trace-h` and `-bridge-h` retain the same native
ticket-control failures. The independent trace evidence audit passes while its
qualification stays FAIL. The bridge cannot provide a complete isolation cycle.
An empty-bay `-empty-control-h` repeat is retained separately.

The separate `-extended-reads-h` run and its independent audit both PASS in fresh
and copied worlds. Each world has 72 moves, eighteen normal mining/output cases,
eighteen unfinished-work chunk-reload cases, eighteen unfinished-work full-process
restart cases, and eighteen cases for each of the legacy and changed-target
saved-read guards. The first loaded observation after a real tick is selected,
even if a later observation would be more convenient. The original source runtime
and protocol hashes match before and after.

The full `-extended-h` campaign and independent evidence audit completed. Every
drill/output/restart/redstone group passes in both worlds; its overall
qualification stays FAIL because the partial-unload groups never obtained
genuine unload events. Existing evidence is not overwritten or relabelled to
the newer candidate.

## Registration-Fixed Candidate

Power release `33F57A1401A13597DCA53F4E49EAEDDD2D5CB3C3F406D5200902D5BA9CB41352`
queues `LegacyWorldDataHook.register` through `DeferredWorkQueue.runLater` in
common setup, after parallel construction. Base Metals is unchanged. The
production regression is `PersistenceRegistrationThreadTest`; the fix changes
only `PowerAdvantage.class` from the converter-fixed h archive. Its deobf SHA-256
is `8D8831D54B700FBFB2B9C43EE3D8D70D2B7E57185A4DBDF12202C47D5E387B39`.

The independent campaign uses the newly generated immutable
`native114-inputs-20261025-final-holder-i1-archived.json`, retaining i's production
jars and the holder-aware harness
`09DE1F386774C4C2B36AE95A4E636D35D6A2BCCAEE938BD5A05E6EE356652C3B`.
Neither parent manifest was rewritten.

The final-i holder-aware partial trace, pure-pipe bridge, eighteen-route
saved-read and combined extended campaigns, plus their independent audits,
PASS in both worlds. These are new final-candidate runs, not passes inherited
from an earlier artifact.

| Final-i campaign | Evidence directory suffix | Result |
| --- | --- | --- |
| Partial trace and both load orders | `-holder-trace-i1` | PASS, independent audit PASS |
| Three pipe-only bridge cycles | `-holder-bridge-i1` | PASS, independent audit PASS |
| Eighteen moving-drill saved-state routes | `-extended-reads-i1` | PASS, independent audit PASS |
| Combined extended drills and partial networks | `-holder-extended-i1` | PASS, independent audit PASS |

Every suffix above belongs to the ignored prefix
`test-rig/build/native114-broader-partial-20261025`. Each run retains the source
fingerprints, exact input identities and hashes, native console logs, raw/canonical
snapshots, actual chunk events, protocol copies and its independent audit.

The final trace records 54 genuine unloads and 54 traced reloads per world,
with zero acquisition stacks from Power's conduit-network implementation.
The combined run passes four partial cycles and both asymmetric load orders
for each of steam, electricity and fluid. Each world also passes 18 cases in
each of insufficient reserve, four-move routes, blocked output,
blocked-after-restart, resumed output and redstone groups, plus all 21
restart-state cases. The independent audit checks 50 raw/canonical byte pairs
per world; it does not accept edited observations or summary-only verdicts.

All four campaigns report unchanged protected source runtimes and helpers.
The final target manifest SHA-256 is
`E1707FF45B59E7103E73444B3D6FBF24BA4EAD694A9A3215B02B98EA7EDA82FF`.
The combined run completed at `2026-10-25T08:19:07Z`, with normal save/stop
sequences before and after a full process restart. Owned ports 25581-25583
were checked free afterward. No unresolved production network defect remains
within this tested scope; graphical acceptance and the separately owned timed
endurance results are not asserted here.

## Running The Adapters

All entry points require an archived source manifest, its approved world
fingerprint, the pinned native destination manifest, Java 8 and a new output
directory under `test-rig/build`. They refuse source/output overlap and verify
the staged artifacts. The allocated loopback ports are 25581 through 25583.

- `migration/native114_partial.py --campaign partial`: original two partial
  directions and both asymmetric load orders.
- `migration/native114_partial.py --campaign bridge`: original three pipe-only
  cycles and unchanged independent bridge oracle.
- `migration/native114_partial_diagnostic.py`: empty-bay ticket control, not a
  replacement for a real network test.
- `migration/native114_extended.py`: four moves per drill, four partial cycles,
  both load orders, blocked output, process restart and redstone cancellation.
- `migration/native114_extended_reads.py`: all eighteen routes through normal
  mining, whole-chunk and process restart with unfinished work, and both saved
  target-state read guards.
- `migration/audit_native114_partial.py --run <run> --output <new-json>`:
  reconstruct verdicts from raw snapshots and verify exact canonical bytes,
  source protection, fixture geometry, artifacts, boots and shutdown.

## Ticket-Aware Follow-Up

`native114_ticket_networks.py` adds separate first-attempt native layouts rather than changing
the historical ones. The three linear networks have endpoints eight chunks
apart and 128-block paths. The three U-shaped pipe bridges have both endpoints
in one chunk and a remote bridge chunk, also eight chunks away. Non-overlapping
bays and explicit support-ticket groups keep each selected core outside the
two-chunk halo of every retained ticket.

That first attempt loses FULL availability but still does not receive a genuine
unload event. Its failed `-halo-*-h1` evidence remains retained. FULL availability
and actual holder removal are distinct on this target; a false snapshot
`loaded` flag is not permission to manufacture an unload callback.

The exact installed source defines
`ChunkManager.field_219249_a = 33 + ChunkStatus.func_222600_b()`.
`ChunkStatus.field_222620_p` has eleven entries, so holders remain through level
44. `ChunkManager.func_219213_a` schedules removal only above that level, and
`func_219212_a` posts the real Forge unload event from the later save task.
`TicketManager.func_219364_a` gives forced chunks level 31. Consequently, a
two-chunk FULL halo is insufficient: the holder-dependency radius is thirteen.
These are facts from Forge 28.2.26's exact native sources, not inferred loading
behavior from a newer Minecraft version.

The inspected `injected-sources.jar` locations are
`net/minecraft/world/server/ChunkManager.java:95` (maximum holder level),
`:275` (level transition), `:392` and `:402` (queued removal and real unload
event); `net/minecraft/world/server/TicketManager.java:198` (level-31 forced
ticket); and `net/minecraft/world/chunk/ChunkStatus.java:96` and `:164`
(eleven-entry dependency list and its size). The archive belongs to the local
ForgeGradle Mavenizer cache for `forge/1.14.4-28.2.26`, not a newer engine.

`native114_holder_networks.py` therefore adds separately named fixtures with
32-chunk-separated anchors and explicit thirteen-chunk support-halo release.
Linear networks are 512 blocks long; U-shaped paths contain 1,027 passive pipes.
Every retained forced ticket lies outside the selected core's complete holder
radius. All eighteen historical drill layouts are unchanged. Six additional
Python tests protect the threshold, bounds, non-overlap, paths and clearing.

The converter-fixed h2 trace and bridge, plus their independent audits, PASS in
fresh and copied worlds. The trace records 108 genuine unload events and 108
traced reload events across the two worlds, with zero acquisition stacks from
Power's network implementation. The bridge completes all six network cycles
across the two worlds, each checking all three networks: eighteen network cases.
It never replaces an endpoint, accepts exactly zero while disconnected, then
retains precisely 3,000 mB per world after measured reconnect offers.

Releasing a core records the baseline before releasing its support halo, then
releases the core's own ticket. Loading restores only the requested halo before
applying the ordinary chunk/tick readiness checks. The historical sustained
absence, callback, identity, conservation and recovery oracles remain unchanged.
Large clearing operations are divided into native sub-chunk-sized fill commands,
all confined exactly to the declared bay.

The Java harness retains its 32-block legacy limit. Only an explicitly marked
`C-STEAM`, `C-ELECTRIC` or `C-FLUID` network with declared endpoint groups may use
one of the fixed 160-by-32 or 544-by-32 footprints; drill/oil fixtures, excessive height, two expanded
axes, arbitrary IDs and out-of-bounds chunk groups are rejected. The native
layouts use rebuilt, separately pinned harnesses. Old manifests still resolve
their original archive bytes.

The related focused Python suite has 87 passing tests, including eleven native
observation controls, nine ticket-layout/control tests and six holder tests.
The complete Gradle harness test, assembly and isolation audit pass with 46 Java
tests. Earlier direct Java 8-targeted compilation and 27 probe tests are retained
as separate development evidence, not substituted for the Gradle suite.

Some large-fixture runs log native catch-up warnings while synchronously loading
the test region, before commissioning. The runner records their counts and the
independent auditor retains their exact lines. Sustained absence still requires
forty actual game ticks, not elapsed sleep. These operating/state campaigns are
not a claim of lag-free region creation or a substitute for the separate timed
endurance campaign.

No production network change, commit, push or publication was performed as part
of this independent campaign. The registration repair was built and pinned
separately, then supplied to these final-candidate reruns.
