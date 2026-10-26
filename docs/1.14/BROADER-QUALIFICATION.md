# Advantage 1.14.4: broader qualification

This records the local candidate and the checks run on 25 October 2026. Visuals
and recipe unlocks were manually checked and accepted on 26 October 2026, and
the tested candidate is accepted for merging. Machine functionality is covered
by automated tests, not personal play-testing. The remaining limits below still
apply; this is not a guarantee for an entire modpack or proof of publication.

## Candidate

| Mod | Version | Release SHA-256 |
| --- | --- | --- |
| Power Advantage | 2.4.5.114041 | `33F57A1401A13597DCA53F4E49EAEDDD2D5CB3C3F406D5200902D5BA9CB41352` |
| Steam Advantage | 2.2.6.114041 | `444872113E13D806D4520CCBD9B3230C931353D43875BAE57EB9B5FC86614669` |
| Electric Advantage | 2.2.6.114041 | `5BE41447AF5AA3AF60C145B3A862BC42802C620C637D1E65810B350A6A9AFBA9` |

The disposable server inputs are frozen in
`test-rig/build/native114-inputs-20261025-hook-i-archived.json`.
That manifest pins genuine Forge 28.2.26, Base Metals 3.0.1.114041,
OreSpawn 4.1.1.114041 and the isolated test harness. Each campaign records its
actual inputs; later harness observations must not be attributed to an earlier
harness jar. These were working-tree candidates at qualification time; the hashes
identify the tested jars, not a later source commit or published release.

## Defects found and repaired

The broader capability test exposed an incoming converter sign error. All four
RF- and Tech Reborn-labelled converters consumed 100 Forge Energy but added
negative energy to an empty Advantage buffer, leaving nothing stored. Incoming
extracted energy is now added positively. Both conversion directions preserve
their configured equivalence; neither conversion rates nor capacities changed.

The packaged failure, failing regression test, corrected test and packaged rerun
are retained. A bytewise archive comparison finds only
`cyano/poweradvantage/api/modsupport/TileEntityConverter.class` changed between
the earlier and corrected Power release jars. Registry names and saved data did
not change. This repair is confined to the 1.14 candidate; it is not a claim that
another Minecraft branch has been repaired.

The early extended run also crashed while saving the world. Forge's persistence
hooks live in a plain `ArrayList`; Power and Base Metals both added hooks during
parallel mod construction. An isolated two-thread control using the actual
Forge API produced null or lost entries, while its sequential control passed.
That establishes the unsafe mechanism, not the exact interleaving of the crashed
game process. Power now registers through `DeferredWorkQueue.runLater` during
common setup, after parallel construction. A regression fails against the old
constructor and passes with the queued registration. Only `PowerAdvantage.class`
differs between the converter-fix jar and this hook-fix jar.

The `h` candidate's earlier passes remain recorded, but the table below does not
attribute them to the newer `i` artifact. All final results below are recorded
against `i`.

## Checks

| Check | Current result | What it establishes |
| --- | --- | --- |
| Native capability and portable-item witnesses | PASS | Twenty server-side checks and two full-restart checks |
| Connected legacy assemblies | PASS, final i candidate | Five sources; initial preservation, 512 ticks, full restart and another 512 ticks; no rebuilding or refill |
| Frozen migration matrix | PASS, final i candidate | Thirteen original/maintained and direct/chained routes, full restart and idempotence |
| Partial-network and extended drills | PASS, final i candidate | Real unload/load events, both load orders, pipe-only bridge interruption, eighteen drill routes, persisted work and redstone |
| Players, entities, dimensions and respawn | PASS, final i candidate | Eight Overworld routes, sixteen Nether/End routes, six nested-storage layouts and 72 server-side respawn cases |
| Live migration-transformer ordering | Final i semantic PASS | Six runtimes, observed/reversed real Base/Power transformers, full restart and evidence replay; backup caveat below |
| Walkable current and decoupled Works | PASS, final i candidate | Initial equipment, twelve tagged drill witnesses, bounded movement and restart; human observations remain manual |
| Measured endurance | PASS, final i candidate | Two thirty-minute runs, followed by full restart and resumed production; independent raw-checkpoint replay PASS |
| Power release and Eclipse audits | PASS | Full task suite and actual Eclipse batch compiler |
| Independent Power build | PASS | Uncached source export in a path with spaces; all four jars match |
| Combined client resource probe | PASS, final i candidate | 94 items, 905 block states, 87 creative entries, fluids, gauge and recipe-book rendering |

The capability witnesses cover receive/extract simulation, negative requests,
capacity limits, both converter directions, invalidated/replaced capabilities,
fluid identity and conservation, native portable-tank loot and placement, and
locked/unlocked chest automation. The tank retains exactly 500 mB after replacing
its native dropped item and restarting the entire process. The conveyor retains
exactly sixteen delivered dirt blocks.

The decoupled Works profile excludes Base Metals and Mineralogy; it retains
OreSpawn, a required dependency of the current port. It does not establish that
the port starts without OreSpawn. Its extra integration skip is the Base Metals
steel-plate check. The ordinary profile has 96 passing assertions and three
Mineralogy skips; the decoupled profile has 95 passes and four integration skips,
both before and after restart. Manual visual/recipe acceptance does not sign off
all 21 itemized human observation lists or establish personal machine play-tests.

Transformer-order testing uses an audited, test-only coremod observer library to
observe and reverse the real transformers, retaining the production mod jars
unchanged. Blocks, items, actors and restart state agree across the orders.
Original-byte custody can belong to different per-mod backup files depending on
which hook ran first. The original bytes survive in the combined backup set;
keep that entire set, and make a whole-world backup before any upgrade.
Real client login, death-screen interaction, portal travel, mounted-player
travel and arbitrary third-party entities are not covered by those server tests.

The endurance protocol feeds native inventory commands and fluid handlers, not
tile NBT. Each minute reconciles five processing lanes, plain and filtered
conveyors and 2:1 oil distillation. Closed tanks, geothermal heat, stored battery
items and valid energy ranges are checked alongside throughput. The migrated
copy receives rebuilt Works bays; that is not an endurance claim for every
original machine in its source save.

Both environments completed 30 measured batches over 1,801.56 seconds, followed
by three restart-related checkpoints. The fresh world advanced 36,833 game
ticks and the migrated copy 36,033. Each produced 120 items in each of the five
processing lanes, routed 480 items in each of the four conveyor/filter lanes,
and distilled 60,000 mB crude oil into exactly 30,000 mB refined oil. Stored
water, battery items and heat remained within their stated invariants. The
read-only replay reconstructs all 33 checkpoints per environment from raw
SNBT, and independently rechecks inventory-command acknowledgements, jar pins,
protocol hashes and the protected source fingerprint.

Final regression checks pass: 60 Power, 27 Steam and 34 Electric unit tests,
46 isolated-harness Java tests, 342 Python tests and 15 launcher/preflight
checks. Power's complete release suite and the child compilation, assembly and
Eclipse preparation checks pass; the children retain the publication source-pin
gate described below.

The partial-network fixtures account for Forge's separate FULL availability
and holder-removal distances. Anchors are 32 chunks apart and release their
thirteen-chunk support halos. Original drill geometry, sustained absence,
actual callbacks, object identity and conservation oracles remain intact.
The final trace records 108 genuine unloads and 108 traced reloads across both
worlds, with no acquisition stack from Power's network implementation. See
[the partial-network report](PARTIAL-NETWORKS-AND-EXTENDED-DRILLS.md) and
[the broad bay report](BROAD-WORKS.md) for limits and retained failures.

## Evidence and repeatability

Local reports, saves, console logs, screenshots and pinned jars remain below
`test-rig/build/` and `build/client-resource-runs/`, excluded from Git. Earlier
failures remain separate from successful reruns. Test-fixture mistakes are not
recorded as production fixes: the portable placement probe initially supplied a
null player, and the frozen refresh initially rejected two byte-identical
snapshots with the same approved hash.

Final evidence roots, deliberately untracked:

```text
test-rig/build/native114-broader-frozen-20261025-i
test-rig/build/native114-broader-connected-20261025-i
test-rig/build/native114-broader-endurance-20261025-full-i
test-rig/build/native114-broader-interop-20261025-green-i
test-rig/build/native114-broader-works-20261025-hook-i
test-rig/build/native114-broader-partial-20261025-*-i1
test-rig/build/native114-broader-actors-20261025-i
test-rig/build/native114-broader-dimensions-*-20261025-i
test-rig/build/native114-broader-player-storage-20261025-i
test-rig/build/native114-broader-respawn-*-20261025-i
test-rig/build/native114-broader-order-20261025-i
build/client-resource-runs/20261025-075733-909c9110
```

The new runners reuse the existing state and stock oracles. Native adapters
handle actual asynchronous chunk readiness and target-platform field names;
they may not fabricate events, extend an oracle's budget, or wait for a desired
machine result before selecting an observation. No protected source fixture or
original Sylvester world is launched or changed.

## Manual inspection

Visuals and recipe unlocks have been manually accepted. That acceptance is
separate from the automated machine, migration and endurance results above;
machine functionality was not personally tested. It does not close every item
in the original play-test checklist.

Use `runClientAllAdvantage.launch` in the PA114 Power project for the coordinated
client. Refresh the Gradle project and let Eclipse rebuild before launching.
The ordinary `runClient.launch` intentionally remains Power-only.

The separate disposable viewer was prepared under
`test-rig/build/manual114-20261025-i/`. Its `Start-Manual.ps1` verifies the pinned
inputs before starting on `localhost:25565`; its README covers controls and
operator access. Eclipse's default player name is `Dev`, so its console may need
`op Dev`. Use only this viewer: the earlier `manual114-20261025` is superseded and
refuses startup. Its original script, world, jars and provenance remain retained.

Remaining personal play-test coverage:

- Open the machine screens and compare pressure, power, fluid and progress
  displays with actual operation, including a same-session reload, a complete
  Minecraft restart and a multiplayer rejoin.
- Check moving drill bits, turbine blades and turrets during actual operation.
  The accepted visuals and recipe unlocks are not a machine-function play-test.
- Try controls, rotation tools, elevators carrying a player, muskets, weapon
  effects, sounds and multiplayer interaction.
- Try real third-party energy and fluid equipment. The native contract tests
  do not qualify every external mod's implementation.
- Upgrade only disposable world copies. Unported unrelated mods and their
  blocks, items and entities are outside this candidate's compatibility claim.

## Before publication

Commit, push, PR and merge are authorized for the tested candidate. Ancestry
reconciliation and publication are handled separately: preserve the port while
reconciling the final 1.13 merge, pin the exact tested Power source commit and
deobf hash in Steam and Electric, and rerun their complete dependency/release
audits. Their deliberate `UNQUALIFIED-1.14-PORT` dependency source pins remain
a provenance gate, not a failed gameplay test to bypass. This record does not
claim those steps, a release or public-download verification are complete.

Native114 profile metadata still uses `PORTED_UNQUALIFIED` and `UNQUALIFIED`.
These are historical launch/schema guards required by `WorksRunner`,
`native114.py` and `Native114ProfileTest`, not a summary of today's acceptance.
They remain unchanged; the reports carry the actual automated PASS results,
manual acceptance and coverage limits. Working-tree source markers remain
pending until the real commit is available.
