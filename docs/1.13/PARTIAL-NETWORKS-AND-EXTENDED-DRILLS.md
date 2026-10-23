# Partial networks and extended drills

This campaign extends [chunk and moving-drill qualification](CHUNK-AND-DRILLS.md).
It uses the same packaged 1.13.2 candidates in fresh and original-Cyano
1.10 -> maintained 1.12 -> 1.13 disposable worlds. New equipment is commissioned
above terrain in the converted copy; this does not qualify every original
machine in a player save. Original fixtures are fingerprinted and never launched.

## Partial-network protocol

Three independent networks cross a chunk seam: steam furnace, electric furnace,
and a fluid pipe/metal tank lane. Every supply and consumer chunk has a named,
bounded group in the fixture definition. Only the selected group's force tickets
are released and its normal chunk-provider unload queued. The other endpoint
retains its ticket, block and tile instance.

The order is consumer-only, supply-only, supply-only, consumer-only. Each check
records real Forge load/unload events, non-loading chunk-map snapshots and
retained tile identity. The observation window is ten seconds, with two final
samples separated by two seconds. A transient unload followed by automatic
reload is not a successful sustained partial-unload check.
The evidence oracle requires at least forty game ticks between those final
absence samples, not just a declared delay in the runner.

Both endpoints are subsequently released together, then reacquired in both
orders, two seconds apart. A first endpoint that loads the second itself is
reported separately from a successful controlled asymmetric reload. No source,
pipe or consumer is broken/replaced during these lifecycle checks.

Four iron ores must become four ingots after every cycle/order. Five public API
offers must conserve exactly 5,000 mB water. Recovery and sustained unloading
are separate acceptance claims: successful recovery cannot override a failure
to exercise actual sustained partial unloading. The bounded creative sources
test network delivery, not the fuel or water behavior of individual boilers.

## Extended-drill protocol

Eighteen independent lanes each make four consecutive native moves:

- Six Steam and six Electric drill facings along perpendicular horizontal track.
- Steam and Electric upward/downward track travel, with the drill facing north.
- One three-way junction per mod, with down, up and east tracks. Down is the
  source-confirmed first choice; this is not a configurable routing policy.

The first attempt starts at nine steam or 249 electricity, below movement cost.
The drill, track and tagged inventory must remain intact. Successful steps then
receive a finite public-API commissioning reserve, 40/50 steam or 400/500
electricity. Each step is observed at native world-tick START/END, must cost
10 steam plus ordinary decay or 250 electricity, and is held by external redstone
after arrival. Repriming between steps is explicit: this is repeated movement
and per-move accounting, not an autonomous continuously powered travel test.

Every moved drill carries seven tagged dirt and thirteen apples. At its final
position a rear chest is filled with 27 stacks of 64 stone. A bounded cobblestone
target yields one gravel for Steam or nine cobblestone for Electric. A full chest
must not delete those items or alter its ballast. The server saves and exits
completely, and a new process must retain position, facing, inventory and fluid
contents. Three chest slots are then freed using native inventory commands;
the exact 192 externally removed stone are accounted for. All drill output must
reach the rear chest without tag loss or duplication.

Finally, obsidian establishes unfinished work. External redstone intentionally
cancels progress, does not alter retained items, and releasing it must allow
mining to resume. This differs from reload, which must retain unfinished work.
No tile NBT, mining progress, drops or movement result is injected by the runner.

## Repeating the campaign

From the PowerAdvantage checkout, using built and pinned jars:

```text
gradlew.bat -p test-rig check assemble
test-rig/migration/.venv/Scripts/python.exe -m unittest discover -s test-rig/migration -p test_*.py -v
test-rig/migration/.venv/Scripts/python.exe test-rig/migration/extended_lifecycle.py --java8 <java8.exe> --migrated <converted-runtime> --output run-extended-lifecycle/new-run --port 25587
test-rig/migration/.venv/Scripts/python.exe test-rig/migration/audit_extended_lifecycle.py --run run-extended-lifecycle/new-run --output run-extended-lifecycle/new-run/qualification-audit.json
```

The runner requires a new output directory, loopback-only server and explicit
test-world markers. Commands require operator permission. Group selection is
limited to declared remote fixture chunks; placements and controls are bounded.
It stops its own processes on success/failure and retains raw logs, snapshots,
artifact SHA-256 values and before/after source fingerprints in ignored folders.
The independent auditor reconstructs case verdicts from those records. An
evidence-audit PASS does not relabel a gameplay or coverage failure as PASS.

## Scope limits

This is not a new full migration matrix or 30-minute endurance run. It does not
qualify player/entity migration, every possible junction arrangement, autonomous
long-distance travel, full drill-internal inventories, moving unloaded drills,
all boiler conditions, every processing recipe or client GUIs/rendering. Vertical
track travel is distinct from vertical drill facing; both are represented here.
No production gameplay changes, commits or publication are part of this pass.

## Qualification

The run `run-extended-lifecycle/20261006-native` completed on 6 October 2026.
Its independent `qualification-audit.json` reports evidence-audit **PASS** and
overall acceptance **FAIL** for both worlds. The failed criteria concern sustained
partial unloading and controlled asymmetric loading, not lost machine state.

| Acceptance case | Fresh 1.13 | Converted fixture |
| --- | --- | --- |
| Four native moves per drill, position/facing/tags and exact movement cost | PASS | PASS |
| All six facings, upward/downward tracks and down-first junction selection | PASS | PASS |
| Insufficient reserve does not consume track or move the drill | PASS | PASS |
| Full rear chest retains carried items and bounded mining yield | PASS | PASS |
| Full process restart retains moved drills, output and network state | PASS | PASS |
| Freeing three rear slots resumes output without loss/duplication | PASS | PASS |
| Redstone cancels work, retains inventory and permits resumed mining | PASS | PASS |
| Processing and measured fluid offers recover after every unload/load order | PASS | PASS |
| All endpoints stay partially unloaded until explicitly reloaded | FAIL | FAIL |
| Both reload orders leave the unrequested endpoint unloaded | FAIL | FAIL |
| Independent evidence audit | PASS | PASS |

Each world witnessed 72 moves across eighteen drills, seven processing batches
per furnace and exactly 5,000 mB water retained. In total: 144 moves, two full
process restarts, four gracefully stopped server processes and 51 real fixture
chunk-unload events before those restarts (26 fresh, 25 converted). All thirty-six
drills retained their twenty carried items and tags plus the correct yield:
one gravel per Steam drill or nine cobblestone per Electric drill. There were
no machine crashes, failed native inventory commands or keep-up warnings.

The original converted fixture's before/after file fingerprints match. All six
staged jar hashes per profile match the report. The production jars are unchanged
from the [drill repair qualification](CHUNK-AND-DRILLS.md#repair-retest). The new
harness SHA-256 is
`D8741B8EA69931C4E412C50573F76CC49BF32FE4793EC80FBECB28AAA6E4A8BA`.
A separate forced rebuild produced that identical hash. Twelve harness Java
tests, forty-seven Python tests and PowerAdvantage's `check verifyReleaseArtifacts`
passed, including harness exclusion from the production artifact.

Two processes logged Forge's known temporary-directory cleanup diagnostic;
the converted-world processes logged its config-file watcher diagnostic. These
remain in the report and are not hidden by the successful machine assertions.

## AW-113-003: Unrequested neighbour chunk loading

**Runtime verified:** releasing a selected endpoint produced real unload events,
but a transmitting neighbour could load it again without a ticket reacquisition
command. In the fresh energy lanes the receiver chunks returned seven ticks
after unloading, well before the deliberate reload. Repeated partial checks
gave the following results in both worlds:

| Endpoint released, other endpoint retained | Steam | Electric | Fluid |
| --- | --- | --- | --- |
| Consumer only | Automatic reload | Automatic reload | Stayed absent |
| Supply/pipe side only | Stayed absent | Stayed absent | Automatic reload |

Reload order also matters. Supply-first loaded the energy consumers automatically,
but left the fluid receiver absent in both worlds. Consumer-first automatically
loaded the fluid pipe side in both worlds; it also loaded the energy supplies in
the fresh world, but not in the converted world. These differences are retained
in the raw records rather than collapsed into one universal outcome.

**Code confirmed acquisition sites:**
[network scanning](../../src/main/java/zone/moddev/mc/poweradvantage/conduitnetwork/ConduitNetworkManager.java#L178)
reads neighbouring blocks through `World.getBlockState`, and
[cached request collection](../../src/main/java/zone/moddev/mc/poweradvantage/conduitnetwork/ConduitRegistry.java#L110)
reads every cached network position without a non-loading chunk guard. The
[filled tank's distribution update](../../src/main/java/cyano/poweradvantage/api/simple/TileEntitySimpleFluidMachine.java#L208)
also requests the network while offering stored fluid. These are plausible causes
of the source-driven observations; this campaign did not capture acquisition
call stacks proving every automatic reload's exact caller.

**Possible issue, not a confirmed data-loss defect:** implicit loading prevents
some long networks from remaining partly unloaded and may cause extra loading
and ticking. All processing, stored items, tags and fluids recovered correctly.
Historical intent about loading remote chunks has not been established, so this
is a chunk-loading policy and qualification concern, not evidence that normal
drilling or persistence is broken. Older versions were not tested for it here.

A follow-up should capture chunk-load callers and compare live finite providers
with these creative sources before choosing a repair. Any loaded-chunk-only
network policy must still reconnect correctly when the absent side later loads,
without replacing pipes or losing valid cached membership. No production repair
or changed acceptance baseline is made in this pass.

## AW-113-003 Diagnosis And Repair

The later diagnostic run `run-extended-lifecycle/20261006-partial-trace-before`
captured actual Forge chunk-load call stacks with the original candidate jars.
It reproduced the failed partial-unload and reload-order checks. Two unwanted
loads originated in `ConduitRegistry.collectRequests`; four originated in
`ConduitNetworkManager.recursiveScan`. The latter included the finite filled
metal tank's fluid distribution, not only creative energy sources. All other
recorded acquisitions in this small diagnostic were explicit harness commands.
This establishes unintended loading in the shared network implementation;
it does not establish lost player data or a historical pressure-loss rule.

The repair is confined to PowerAdvantage's network cache and lifecycle wiring:

- Check `World.isBlockLoaded` before reading the scan origin, each neighbour,
  or a cached request position. An unloaded origin returns no requests.
- Cache a loaded isolated origin as a singleton rather than relying on a
  connected neighbour to create its cache entry.
- Invalidate the entire affected network when one of its chunks unloads.
  A cache must not conduct across an absent intermediate pipe chunk.
- On chunk load, invalidate networks touching the chunk or its one-block
  boundary. This includes pure pipe chunks with no powered tile load callback.
- Ignore client chunk events. Keep unrelated networks and dimensions cached.

Networks operate only over currently loaded chunks, then reconnect through
normal server distribution updates after the missing chunks load. No tickets
are requested, no chunks are generated by scanning, and no pipe replacement
is required. World identities, block states, NBT write keys, fluid quantities,
public API descriptors, machine rates and distribution priorities are unchanged.
Steam/Electric pins are refreshed to the rebuilt Power deobf dependency; their
production source and release hashes are unchanged by this repair.

The focused after-repair trace, `20261006-partial-trace-after`, passes both
partial-unload directions and both asymmetric reload orders for all three
network types. Its acquisition stacks contain no PowerAdvantage network caller.
The original failure records remain intact.

### Pipe-Only Bridge Regression

`pipe_only_lifecycle.py` adds three bounded U-shaped networks. Both endpoints
remain in one chunk; the only connecting route goes through a second chunk
containing pipes and no machines. Each of three cycles requires real bridge
unload events and sustained absence, no remote requests and exactly zero fluid
acceptance while disconnected, then deliberate bridge reload and resumed
processing/public fluid offers without replacing either endpoint.

`audit_pipe_only.py` independently checks the event counters, exact block/state
restoration, retained tile identities, fluid conservation, four-ore/four-ingot
batches, native assertion acknowledgements, staged hashes and shutdown. Its
negative controls reject automatic loading, missing unload events, replaced
endpoints and duplicated fluid.

An initial new bridge fixture took its baseline before the furnace's first
powered-state tick. The independent audit correctly rejected that derived-state
change; those records remain in `20261006-pipe-only-after`. The corrected runner
waits for normal commissioning updates before taking the baseline. It does not
ignore `active` or `powered` properties to obtain a pass.

Run these focused checks before the full extended campaign:

```text
test-rig/migration/.venv/Scripts/python.exe test-rig/migration/partial_network_trace.py --java8 <java8.exe> --output run-extended-lifecycle/new-trace --port 25587
test-rig/migration/.venv/Scripts/python.exe test-rig/migration/pipe_only_lifecycle.py --java8 <java8.exe> --output run-extended-lifecycle/new-bridge --port 25587
test-rig/migration/.venv/Scripts/python.exe test-rig/migration/audit_pipe_only.py --run run-extended-lifecycle/new-bridge --output run-extended-lifecycle/new-bridge/qualification-audit.json
```

The stack observer is opt-in via `advlifecycle trace-loads <label>`, captures at
most 64 stack frames per fixture load, and exists only in the marked-world test
harness. Neither the observer nor any harness resource enters a production jar.

### Repair Qualification

The completed rerun `run-extended-lifecycle/20261006-partial-fixed` reports
**PASS** for both worlds. Its independent `qualification-audit.json` reports
evidence-audit **PASS** and overall qualification **PASS** under the same
partial-unload and loading-order criteria used by the original failed run.
Reauditing `20261006-native` still reports evidence-audit PASS / qualification
FAIL; the expectations and failure history have not been weakened or replaced.

| Acceptance case | Fresh 1.13 | Converted fixture |
| --- | --- | --- |
| Four sustained supply/consumer unload cycles, all three networks | PASS | PASS |
| Both asymmetric loading orders leave unrequested chunks absent | PASS | PASS |
| Processing, fluid conservation and retained endpoint identity | PASS | PASS |
| 72 native drill moves with exact energy, facing and item tags | PASS | PASS |
| Scarcity, blocked output, full restart and resumed output | PASS | PASS |
| Redstone cancellation/resumption without item loss | PASS | PASS |
| Independent evidence audit | PASS | PASS |

The full rerun records 144 drill moves, 48 actual endpoint chunk-unload events,
36 partial/loading-order cases, two complete process restarts and four stopped
server processes. Seven four-ore batches per furnace produced 28 ingots in each
world, and each world retained exactly 5,000 mB from its measured fluid offers.
All drill output and carried-item conservation checks passed. Source-world
fingerprints and the six staged artifact hashes per profile match.

The separate corrected bridge run, `20261006-pipe-only-qualified`, and its
independent audit pass all nine network/cycle cases. Real unloads span at least
forty game ticks; retained endpoints remain the same objects. The disconnected
public API accepts zero, then accepts exactly 1,000 mB after deliberate reload;
the three offers retain exactly 3,000 mB. Each furnace resumes four-ore batches
without a machine or pipe replacement. This run has no ERROR/FATAL log entries.

The full rerun has no ticking-machine crash, failed native command or keep-up
warning. Two processes logged Forge's temporary-resource-pack cleanup FATAL;
both diagnostics are retained in `extended-results.json` and are not machine
failures. No other ERROR/FATAL diagnostic appeared in the full run.

Current repair pins:

| Artifact | SHA-256 |
| --- | --- |
| Power release `2.4.4.113021` | `0CCA0AF27497431C3A64091809F8DD328C84D82500CF05456366770C65485E74` |
| Power deobf `2.4.4.113021` | `131573A86A9E2E2C5D862B35EDA1C90B23C1A9D71BF7AFE256C62C6F464FAD38` |
| Harness release `1.1.0.113021` | `80402A725EC02B9D169B0E11CE3E99EF0892F6E6468891A618A34A4E5EC7C615` |

Steam/Electric releases remain the exact hashes recorded above. Their dependency
properties now pin the rebuilt Power deobf jar. All applicable rig Power release
pins match. The Power archive comparison has no added/removed entries and only
three changed classes: the entry point and two network classes. Resources and
metadata are byte-identical to the previous candidate.

All three full FG7 release suites, dependency/artifact/Java-8 audits, checksum
tasks and Eclipse production-classpath audits pass. Seventeen Power tests,
six Steam tests, nine Electric tests, twelve harness tests and fifty-three
Python tests pass. Separate forced rebuilds preserve the exact Power release
and deobf hashes and the harness release hash. Local Markdown file links resolve;
the historical handbook link points to its verified upstream 1.10.2 branch.

This closes AW-113-003 for the exercised loaded-chunk network topologies. It is
not a fresh complete world-migration matrix, whole-network mid-block reload,
30-minute endurance, clean-cache or manual client campaign. The earlier results
remain tied to their original artifacts. No original player save, older-version
code, registry/NBT identity, commit, push or publication was changed.

A subsequent [whole-network and mid-block regression](CHUNK-AND-DRILLS.md#current-network-repair-regression)
now passes on this exact Power release and harness in fresh and converted
worlds. It does not refresh the earlier endurance or full migration-matrix
qualification; those remain tied to their own input artifact hashes.

The subsequent [current-candidate endurance qualification](ENDURANCE.md#current-candidate-qualification)
also passes on these exact release hashes. It refreshes the bounded thirty-minute
repeat-load and full-restart gate, not the complete migration matrix or manual
client qualification.
