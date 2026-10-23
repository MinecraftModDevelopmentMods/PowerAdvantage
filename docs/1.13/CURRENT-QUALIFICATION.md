# Current Candidate Qualification and Remaining Tests

## Scope

This is the 23 October 2026 rerun against the current, unpublished 1.13.2 jars.
It supplements, rather than replaces, the historical failed and repaired reports.
The main campaign and independent audits completed: all 29 steps passed, all ten
protected fixture fingerprints remained unchanged and all 45 input/archive checks
passed. The separate all-station sweep retains two drill-location oracle failures
per phase. This is **core qualification with an open rig finding and coverage
gaps**, not an all-setups-pass or release-approval claim.

The subsequent [connected legacy works campaign](CONNECTED-MIGRATION.md) closes
the source-operating-fixture gap for five bounded assemblies. All eight 1.13
stages preserve state and resume work after restart. The three chained routes
retain a **1.12 intermediate** electric-drill progress-reset failure; this is not
an all-routes-pass claim. The results below remain the earlier campaign's record.

The later [legacy drill repair and rerun](DRILL-PROGRESS-REPAIR.md) resolves that
intermediate failure in both drill types on maintained 1.10/1.12. All eight
connected routes and the two-restart native checks now pass with the repaired
artifacts. The original failed campaign is not overwritten; other coverage gaps
and `Q-RIG-001` remain open.

| Artifact | SHA-256 |
| --- | --- |
| `PowerAdvantage-2.4.4.113021.jar` | `E5B62769812AE02393A305CF33D2E168BFB2B6D370AB04D43409B94F5740D193` |
| `SteamAdvantage-2.2.5.113021.jar` | `A87F141913D511DB025FEE6D6FBA4000098598D8692B4BA53E7E80C67B14ACCA` |
| `ElectricAdvantage-2.2.5.113021.jar` | `C785155C05133D1A408613D2C9738A32C9818E31C2599ECBD9F2FE76965DB5BA` |
| Works harness `1.1.0.113021` | `0D0F71F25EB0D99FC871ACBD9120E14EA6F14B3349E4DB2F1A0283D2A2A84A1B` |

The harness hash above belongs to that archived campaign. The connected
extension uses harness SHA-256
`2A260DF4B66454B57ECB6B021BCF26B3A812CA5FC0A8B9C4C9E904879AA67A1C`;
the three production hashes have not changed.

The server uses Forge `25.0.223`, Base Metals `3.0.1.113021` and OreSpawn
`4.1.0.113021`. Production and probe jars are archived by content hash before
launch. The original source fixtures, source jars and protected player saves are
never opened as running worlds. Every conversion uses a disposable copy.

The source profiles retain original Cyano 1.10, early local 1.10, maintained 1.10,
early local 1.12 and maintained 1.12. There is no original published Cyano 1.12
fixture. Chained routes use the separately pinned repaired Electric 1.12
`2.2.2.112021` bridge; the historical `2.2.1.112021` source remains unchanged.
See [source provenance](MIGRATION-MATRIX.md) and the
[bridge repair](PLAYER-AND-ENTITY-MIGRATION.md).

## Rerun Results

| Gate | Current result | Retained evidence under the run root |
| --- | --- | --- |
| Block/tile state migration | PASS: eight direct/chained routes, conversion/restart/stability, 355-359 block and 332-336 tile witnesses per route | `matrix/matrix-results.json`, `matrix-audit.json` |
| Commissioned operation inside converted copies | Automated assertions accepted in all eight routes, eleven stations before/after restart; manual checks remain open | `operation-<route>/operation*-results.json` |
| Strict player/entity migration | PASS: eight routes, frozen checkpoints, player adapter, native entity ticks and restart | `actors/actor-results.json`, `actors-audit.json` |
| Normal-ticking actor controls and inventory layouts | PASS: eight normal-ticking routes and four inventory-layout routes | `actors-native/native-results.json`, `player-layout/layout-results.json`, corresponding audits |
| Nether/End migration | PASS: all eight routes in both dimensions, native ticks and complete restarts | `dimensions/dimension-results.json`, `dimensions-audit.json` |
| Transformer ordering | Semantic PASS in six runtimes/twelve launches; backup-origin order dependence remains documented, not waived | `transformer-order/summary.json`, `transformer-order-audit.json` |
| Six-facing drills and chunk lifecycle | PASS in fresh and original-release chained worlds: 24 moves, 600 observed chunk unloads, progress/output/energy checks and full restart | `lifecycle/lifecycle-results.json`, `lifecycle-audit.json` |
| Partial networks and extended drills | PASS in both worlds: 144 native moves, 24 partial-unload cases, twelve reload-order cases, scarcity/blockage/redstone and full restart | `extended-lifecycle/extended-results.json`, `extended-lifecycle-audit.json` |
| Pipe-only unloaded bridge | PASS: three fresh-world cycles, absent-bridge isolation, retained endpoints and resumed processing/fluid conservation | `pipe-only/pipe-only-results.json`, `pipe-only-audit.json` |
| Thirty-minute repeated production | PASS in both worlds: thirty timed batches, full restart and resumed processing | `endurance/endurance-results.json`, `endurance-audit.json` |
| Oil bucket/drain/pipe/container lanes | PASS: four crude/refined lanes, exact identities/volumes and two process restarts | `oil-transfer/oil-transfer-results.json` |
| Runner/oracle unit tests | PASS: 140 tests | `runner-unit-tests.log` |
| All 21 walkable bays activated | FAIL: two stale drill-location assertions in each phase; other 19 bays remain manual-qualified only | `all-stations-*.json`; see `Q-RIG-001` below |

Each endurance world ran for 1,801.563 seconds: 36,233 game ticks in the fresh
world and 36,041 in the converted copy. Each produced 120 items in each of five
processing lanes, routed 480 items through each of the plain and three filter
lanes, and converted 60,000 mB crude oil to 30,000 mB refined oil. These are thirty
distinct timed batches, not repeated readings of unchanged output. The resumed
post-restart batch is checked separately and is not counted as timed production.

No measured machine-state, item or fluid loss occurred in the accepted cases.
Every test server stopped; no viewer instance was left running. The existing
unrelated server was not stopped or changed. The log diagnostics and the
transformer [backup-provenance limitation](TRANSFORMER-ORDERING.md#backup-provenance-finding)
remain recorded; semantic migration success does not make partial loose-file
backups a substitute for a complete original-world backup.

## Evidence and Interpretation

Local evidence is under the ignored directory
`run-qualification/20261023-current/`. It contains archived input profiles,
commands, console logs, raw NBT snapshots, per-stage comparisons, source
fingerprints and independent audit reports. Generated worlds and jars are not
documentation assets and must not be committed.

Three different claims must stay separate:

1. **State preservation:** the old serializer's actual saved state survives
   conversion and restart. Frozen witnesses do not prove processing throughput.
2. **Operating behaviour:** bounded machinery performs the measured work, with
   the specified item/fluid/energy accounting and lifecycle checks.
3. **Presentation and player interaction:** a real client can operate controls,
   see correct GUI values, and interact with other players and entities.

Commissioning new machines inside a converted world is not proof that every
original player-built network runs correctly. Registry/metadata coverage is not
the Cartesian product of every orientation, fuel, network topology, automation
face, shortage, unload timing and upgrade route.

Station `MANUAL_REQUIRED` is not promoted to `PASS` by a successful runner exit.
An existence assertion proves only that a block/tile is present. A report audit
can pass while faithfully retaining gameplay failures. Known Forge/Base Metals
diagnostic messages remain in logs; successful state assertions do not imply
error-free logs or approval of the whole modpack.

## Walkable Drill Check Finding

The additional fresh-world all-station sweep starts all 21 bays, saves, fully
stops Minecraft and checks again after restart. Both phases currently report
94 assertion passes, two failures and three Mineralogy-dependent skips. The
other 19 stations retain `MANUAL_REQUIRED`, not complete gameplay acceptance.

`Q-RIG-001`: the `S-05` down-facing and `E-06` up-facing assertions require a
drill at its original coordinate even after the station has powered it and
provided track. The snapshots instead contain a down-facing Steam drill one
block above its start and an up-facing Electric drill one block below its start.
Both displaced witnesses remain in the restarted snapshot. The other five
facing assertions in each bay pass.

This is a stale fixed-position oracle after activation, not evidence by itself
that a drill vanished or rotated. It also is not sufficient evidence of correct
movement, energy consumption or output. The separate movement-aware campaigns
must decide those claims. The all-station result remains **FAIL** until its
moving-witness assertions are repaired and independently rerun; this pass does
not weaken them or label the failures expected passes. Raw records are
`all-stations-commissioned.json`, `all-stations-full-restart.json` and
`all-stations-results.json` under the local evidence root.

## Remaining Behavioural Checklist

This is the work queue, not a list of newly discovered defects. A behaviour can
have partial coverage without its complete acceptance card being satisfied.
The definitions in
[`works-1.13.2.json`](../../test-rig/src/main/resources/assets/advantageworkstest/works/works-1.13.2.json)
retain the detailed manual cards.

| Station | Behaviour still needing dedicated evidence | Suitable next method |
| --- | --- | --- |
| `W-01` fluid transfer | Matching/nonmatching tank filters, redstone fluid isolation, all terminal/third-party handler faces, portable-tank break/place retention versus intentional metal-tank loss | Automated bounded volume/identity fixtures; client check for controls |
| `W-02` drain/discharge | Source versus flowing water, stepped 32-step search boundary, full-destination backpressure and exact discharge-to-world conservation | Automated terrain and blocked-output fixtures |
| `W-03` distillation | Mid-cycle input/progress/fuel restart, full-output and interruption cases; Mineralogy oil equivalence on these exact artifacts | Automated finite-input fixtures with Mineralogy profile |
| `W-04` sorting | Complete zero/one/many configurable-filter routing matrix, every category filter, vertical/entity pickup, all overflow branch blockages, comparator levels and loss/duplication accounting | Automated item-count fixtures, not tile-existence checks |
| `S-01` boilers | All four boilers' valid/invalid fuels, starvation and output saturation, exact electric-to-steam relationship, oil aliases/config overrides and sustained measured rated load | Automated finite-fuel/water/energy fixtures |
| `S-02` distribution | Direct machine-to-machine conduction and short/long-network comparison under measured scarcity; matching GUI/server readings in a connected client | Automated topology fixture plus real client |
| `S-03` storage | Tank backup priority, scarcity/recovery and redstone branch isolation with whole-circuit accounting | Automated finite supply/demand fixture |
| `S-04` processing | All furnace lanes, all automation faces, invalid inputs, full outputs, mid-cycle restart, steam-still throughput and full comparator scale | Automated bounded processor fixtures |
| `S-05` Steam Drill | Continuous naturally supplied multi-step travel; every new/imported orientation combined with every mid-block/unload/track condition; visual helper-bit restoration | Extended automatic cross-product plus real client |
| `S-06` Steam Pump | Lift/depth geometry, pipe-section costs, full output, empty pool and repeated shaft cleanup; pumping throughput rather than isolated tank persistence | Automated finite-pool and energy accounting |
| `S-07` elevator | Actual up/down travel, redstone pulses, ceiling stops, range/steam costs, riders and mid-travel unload/restart recovery without duplicate platforms | Automated movement fixtures; real player riding/control test |
| `S-08` musket | Real loading/firing, cartridge/durability consumption, damage/range, block interactions and every enchantment's effects | Automated firing/target fixtures plus real client; enclosed disposable range |
| `E-01` generation | Photovoltaic time/weather/light matrix, exact steam-conversion rate/cap, turbine damage and player interaction | Automated environmental/energy fixtures plus real client |
| `E-02` batteries | All four batteries charging/discharging at capacity boundaries, mixed-array transfer, backup priority, switching and exact whole-circuit energy accounting | Automated finite generator/consumer fixture; tooltip/GUI checks |
| `E-03` processors | Fabricator and other processors' valid/invalid/full-output/scarce-power cases, automation faces and mid-cycle restart across machine-specific inventories | Automated recipe/output fixtures; not just crafting reachability |
| `E-04` fluid plant | Steam/electric pump geometry and work costs, live pump persistence/resume, powered still/refinery blockage and interrupted fluid/energy paths | Automated isolated finite-fluid fixtures; client controls |
| `E-05` growth | Actual seed/crop/sapling harvests, shared chamber starvation, soil/water/electricity consumption and mid-growth restart | Automated crop-output and resource accounting |
| `E-06` Electric Drill | Same remaining cross-product and continuously powered travel as `S-05`; client laser/render behaviour | Extended automatic fixture plus real client |
| `E-07` light/turret | LED emitted light and priority, turret owner/team permissions, targeting/range, real hits, shot cost and reacquisition across reload | Automated target/energy fixtures plus authenticated players |
| `E-08` assembler | Actual reference-recipe crafting, recursion limits, reversible recipes, output blockage and timeout protection | Isolated time-bounded automatic fixture; no valued world |
| `P-API-01` fluid API | Real IronAgeFurniture caller and third-party interaction beyond the native pipe/tank contract | Integration profile/client with IAF; API contract stays separate |

### Recommended Order

1. The [connected-source pilot](CONNECTED-MIGRATION.md) is implemented for
   conveyor transfer, supplied steam processing/storage, electric generation and
   processing, and both stationary drills. `Q-CONNECTED-001` in the maintained
   legacy implementations is now repaired and independently rerun; retain its
   original failure evidence. Extend the same no-rebuild protocol to other
   assemblies as their dedicated behavioural fixtures are added. The isolated
   state matrix and newly commissioned converted Works remain complementary
   evidence.
2. Repair `Q-RIG-001`: distinguish initial facing/placement from expected track
   movement, then assert moved identity, facing, inventory and energy. Do not
   clear a failure by merely removing the assertion or resetting before checking.
3. Elevator movement and growth-chamber output: large functional gaps with
   practical, finite inputs and measurable outputs.
4. Conveyor/filter and tank priority matrices: automate the advertised variants
   that the walkable bays currently represent only structurally.
5. Pump geometry/costs, boiler fuel/starvation and processor blocked-output cases.
6. Generator environmental states, batteries and optional Forge Energy converters.
7. Musket/turret gameplay, real-player permissions and GUI/server agreement.

An individual row closes only when its required outputs and retained state are
recorded. The same fixture should be exercised natively and after relevant
direct/chained conversions, not merely copied into an already converted world.

## Cross-Cutting Limits

- The latest recipe manufacturing/discovery and native resource/render results
  remain in [Manufacturing Progression](MANUFACTURING-PROGRESSION.md) and
  [Recipe Discovery](RECIPE-DISCOVERY.md). Those are not machine/network tests.
- Not every original saved machine is connected and operated by the migration
  matrix. State lost by an old serializer before conversion cannot be recovered
  by declaring the new migrator successful.
- Current optional integration, Mineralogy, OreSpawn world-generation/profile
  migration and configuration-import combinations need their own exact-artifact
  reruns; superflat machine tests cannot prove terrain-generation behaviour.
- Full network login/handshake, integrated-server host loading, returning to the
  menu and replacing worlds within the same client process, GUI controls,
  pressure/power bars, sounds, targeting visuals, multiple players and all modpack
  interactions remain distinct from dedicated-server adapter tests.
- Chunk/drill campaigns do not exhaust every unload timing, portal travel,
  mounted dimension transfer, arbitrary dimensions or natural animal behaviour.
- Advancement/statistic and third-party capability migration is not implied by
  item-NBT, player-adapter or owned-entity persistence coverage.
- The [Forge respawn finding](DIMENSIONS-AND-ENTITY-LIFECYCLE.md#forge-25-spawn-lookup-finding)
  remains open: a real-client bed/death test is required. The earlier eight
  valid-bed/forced-spawn failures are not changed to passes by this campaign.
- A complete Sylvester modpack conversion has not been accepted. Unsupported
  unrelated mods are not acceptable silent data loss. Retain a full original
  world backup; loose-file backups cannot roll back flattened region files.

No gameplay fix, version bump, commit, push, PR, tag or release is part of this
qualification pass.
