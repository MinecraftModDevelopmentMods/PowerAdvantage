# Advantage 1.14.4 Port

> The earlier acceptance below applies to the archived candidate. See the
> [stronger regression record](TEST-COVERAGE-BACKPORT.md) for the locally repaired
> jars and their new machine, migration, restart and GUI results.

## Starting Point

The tested 1.14.4 candidate is accepted for merging. Visuals and recipe unlocks
were manually checked and accepted on 26 October 2026. Machine functionality is
covered by automated tests, not personal play-testing. The acceptance does not
close the remaining coverage limits below.

The three `master-1.14.4` branches start from the tested 1.13.2 port. The approved
1.10, 1.12 and 1.13 changes are merged upstream. Commit, push, PR and merge are
authorized for this candidate; ancestry reconciliation and publication work are
handled separately from this qualification record.

The checkouts follow the existing layout:

```text
PA114/PowerAdvantage
PA114/SteamAdvantage/SteamAdvantage
PA114/ElectricAdvantage/ElectricAdvantage
```

Target: Minecraft 1.14.4, Forge 28.2.26, ForgeGradle 7.0.34, Gradle 9.6.1,
Java 17 for Gradle and Java 8 production bytecode. Base Metals' maintained
1.14 branch is the platform reference, not a source of Advantage gameplay policy.

## Native API Review

- Forge 28.2.26 has `DeferredRegister`, `FluidAttributes`, `ForgeFlowingFluid`,
  `IFluidHandler` and `LazyOptional`. Use native fluid rendering and capabilities
  instead of the Forge 25 renderer transformer. Keep the public Advantage fluid
  API as an adapter, with its existing millibucket and input-immutability rules.
- Containers now have registered `ContainerType` objects and client screen
  factories. Use `NetworkHooks.openGui` with a named provider and explicit tile
  position; replace the old GUI extension-point packet interception.
- Inventory and energy capabilities must follow tile lifecycle and invalidate
  their optionals when the tile is removed. Loading a GUI must not be required
  to restore a network or machine.
- Inspect native recipe conditions, reload listeners and loot-table events before
  carrying over the material-tag and parts-loot transformers. Remove a transformer
  only after its replacement preserves recipe modes, data-pack overrides and loot.
- The 1.14 villager profession and trade changes require a real migration policy;
  do not quietly drop saved non-vanilla profession mappings.
- Keep the pre-flattening world migration hook where vanilla/Forge still cannot
  interpret legacy mod numeric IDs. Requalify its descriptors and interaction
  with Base Metals; do not copy 1.13 bytecode patches blindly.

References: [Forge registries](https://docs.minecraftforge.net/en/1.14.x/concepts/registries/),
[Forge 28.2.26 sources](https://github.com/MinecraftForge/MinecraftForge/tree/1.14.x/src/main/java/net/minecraftforge),
and the local published Base Metals 1.14 implementation. The exact installed
Forge source jar was inspected to verify these APIs exist on this target.

## Compatibility Gates

Preserve mod IDs, registry and fluid identities, tile/entity aliases, recipe modes,
NBT keys, provider IDs, configuration meaning and legacy public API packages.
New native platform types may replace implementation glue, not saved identities.

Run the existing 1.10/1.12 direct and chained migration fixtures, then add a
1.13-to-1.14 route. Compare state before ticking, operate connected assemblies,
save, stop the process completely and compare again. Source fixtures and the
original Sylvester world remain untouched.

Required checks include native fluid/bucket capability interoperability,
drain-pipe-container conservation, native menu synchronization, every drill facing,
movement and unfinished work, partial-network unloading, inventories, batteries,
player/entity migration, all recipe modes, client resources and endurance.
The older qualification gaps remain open until tested, including the broad
station oracle and real-client bed/death/respawn behavior.

## Current Status

The current broader run is recorded in
[Broader Qualification](BROADER-QUALIFICATION.md), including exact candidate
hashes, retained failures, manual acceptance and the disposable viewer. Earlier
results below retain their original scope; they are not a claim of whole-modpack
acceptance.

- Local branches and source-only checkouts created.
- Exact target Forge API and sibling dependency versions inspected.
- All three production source sets compile against the native 1.14 target.
- Power's 60 unit tests, release, dependency, artifact and Eclipse audits passed.
  Steam's 27 unit tests and Electric's 34 unit tests passed; these are
  not connected-machine or migration results.
  The isolated harness has 46 passing Java tests; its Python adapters and
  unchanged semantic oracles have 342 passing regression tests.
- Native fluid attributes/capabilities and containers replace the transitional
  1.13 implementations. Native tag and loot events replace their transformers.
- The first packaged Power server exposed an immutable-tag-collection failure.
  The original log is retained locally. The collection is now rebuilt through
  `registerAll`, with a regression fixture. The fixed combined packaged server
  started cleanly and passed the W-01, W-02 and P-API-01 server assertions.
- Native block loot includes portable tank NBT. Registered tile types declare
  valid blocks. The legacy migration scripts have been adapted to the target
  chunk converter, with both Base Metals transformer orders covered by unit tests.
- Thirteen original/maintained and chained frozen fixture routes pass native
  conversion and a full process restart: 355 block, 332 tile and 2,654 item
  witnesses in the standard route, with 2,656 items in the 1.12 fixture. These
  are state-preservation checks and have passed again on the final candidate.
- Five archived connected assemblies also pass initial preservation and
  512+512 operating ticks, including another process restart. Conveyors,
  processors, both drill types and battery charging resume without rebuilding,
  replacement or refill; bounded inputs and outputs reconcile.
- Those fixtures caught a fluid-name lookup defect: Forge's empty-fluid default
  hid the vanilla `water` fallback. Registry membership is now checked explicitly,
  with three regression tests. The original failing evidence is retained.
- Vanilla 1.14 omits empty chest lock keys. Only vanilla chest roots normalize
  missing `Lock` to the canonical empty string; changed or lost nonempty locks
  still fail comparison. Raw snapshots remain unchanged.
- The combined native client resource probe passed for 94 items, 905 block
  states and 87 creative entries. It also checked both oil buckets, oil/water/lava
  rendering, the tank floor and a 10,000 mB water gauge. Manual visuals and recipe
  unlocks are now accepted; in-world machine operation was not personally tested.
- The original 21-bay failure is retained. `Q-RIG-001` is now repaired with exact
  initial positions, bounded track destinations, persistent tile markers,
  inventory conservation and saved energy checks. Both current and decoupled
  profiles pass before and after restart. Visual acceptance does not sign off all
  21 itemized human observation lists; machine-function results are automated.
  The decoupled profile omits Base Metals but retains required OreSpawn.
- Native fallback generation and legacy import passed. The genuine pinned
  Mineralogy 1.14 build also passed both distillation lanes and ore-source/profile
  checks, including restart persistence and the existing boundary-aware oil
  generation oracle. This does not qualify every fuel or bucket interaction.
- Native recipe discovery passes five boots, with 109 cases per operation and
  no skips. Manufacturing progression passes 18 checks across all recipe modes,
  reloads, restarts and both rubber-alias settings. The combined graphical probe
  also passes recipe-book grouping in all three modes and renders all 13 target
  buttons with the existing pixel threshold.
- Six oil-transfer lanes pass bucket placement, drains, descending pipes and both
  container types, including Mineralogy crude oil. Exact 1,000/2,000 mB quantities
  and fluid identities survive two process restarts; refill does not replace the
  machines. Earlier unsupported-fixture evidence is retained separately.
- All three production source sets also pass Eclipse's actual batch compiler.
  Power's clean source export in a path with spaces and a fresh Gradle cache
  passes its full suite with build caching disabled. Release, source, Javadoc
  and deobf hashes match the working checkout. This is an uncommitted source
  export, not a claim of published-commit provenance.
- Steam and Electric also pass independent, uncached production builds in paths
  with spaces. All twelve release, deobf, source and Javadoc artifacts match the
  corresponding working-checkout artifacts byte-for-byte.
- The first movement-aware run traced a Steam drill reload loop to a world lookup
  during unload-save. Saving now uses cached facing only; the new unit regression
  fails against the old method and passes with the fix.
- The native lifecycle campaign now passes in both a fresh world and the copied
  1.10-to-1.12-to-1.13 world. Each completes all seven twelve-drill groups and five
  genuine unload/reload cycles, network processing, 5,000 mB transfer, mining
  output, inventory and energy conservation, mid-block work, full restarts and
  deferred legacy/changed-target read guards.
- Native observation readiness explicitly restores fixture tickets after restart
  and waits for real Load events and the first measured tick. Unload observations
  also await the actual queued callback within the original 45-second budget.
  No flags, events, ticks or work values are fabricated. The original fixtures,
  semantic oracles, thresholds and failed evidence remain unchanged.
- Wider actors, nested storage, both dimensions, 72 server-side respawn cases
  and observed/reversed real migration-transformer orders pass on the final
  candidate. Keep the complete backup set: which mod's backup holds the original
  bytes depends on callback order. Real client travel and death-screen handling
  remain manual.
- Broader tests caught incoming converter energy being subtracted instead of
  added, and unsafe registration of Forge's world-save hook during parallel mod
  construction. Both have focused red/green regressions and packaged reruns.
  Registration now uses queued common-setup work. Existing identities and
  conversion rates are unchanged.
- Native partial-unload fixtures require wider geometry: Forge's level-31
  forced tickets retain holders through level 44. The separate holder-aware
  layouts preserve genuine unload/load, sustained absence and conservation
  assertions. Their final partial, bridge, saved-read and extended campaigns
  pass, with independent evidence audits. Both final thirty-minute environments
  also pass full restart and resumed processing; raw-checkpoint replay passes.
- Steam and Electric dependency source/hash pins remain deliberately
  `UNQUALIFIED-1.14-PORT` until the exact Power candidate can be pinned.
- These results identify the tested local artifacts, not a completed upstream
  merge or published release. All world conversions used disposable copies;
  no protected source save was opened or converted in place.
- Native114 profiles retain `PORTED_UNQUALIFIED` and `UNQUALIFIED` as historical
  launch/schema guards. The runner, adapter and profile regression require those
  exact tokens; they are not the current acceptance status. The reports record
  the actual automated PASS results and manual scope. No harness code or profile
  metadata was changed to rename them.

## Remaining Coverage and Delivery

- Visuals and recipe unlocks are manually accepted. Personal checks of machine
  operation, GUI readings during use, sound, controls, weapons, multiplayer and
  real third-party equipment remain outside that acceptance. These results do
  not qualify the entire Sylvester modpack or any unrelated unported mod.
- For the authorized commit, push, PR and merge work, reconcile the final 1.13
  merge ancestry while preserving the port. Pin the exact tested Power source
  commit and deobf hash in Steam/Electric, then run their complete release audits.
  Their current `UNQUALIFIED-1.14-PORT` dependency pins intentionally fail those
  gates; acceptance is not a substitute for exact source/artifact provenance.
- Publication is handled separately. This record does not claim a completed
  merge, tag, release or independently verified public download.
