# Manufacturing Progression: Minecraft 1.13.2

## Works Notice

An entry in the recipe book is not proof of a usable workshop. This audit starts
with natural resources and follows the actual loaded recipes to each registered
Advantage component. Normal and Tech Progression are manufacturing modes;
Apocalyptic deliberately requires salvaged parts. Creative sources and
machine-generated helpers are not survival crafting targets.

This concerns the local Power 2.4.4.113021 and Steam/Electric 2.2.5.113021
candidates, using pinned Base Metals 3.0.1.113021 and OreSpawn 4.1.0.113021.
It does not rename existing items, blocks, fluids, tile IDs, NBT or recipes.

## Material Ledger

| Material | Supply And Use |
| --- | --- |
| Wire | The electric conduit is a member of `forge:wires`. This supplies the electric boiler, electric switch, LED, photovoltaic generator and Normal PSU recipes. |
| Carbon powder | `forge:dusts/carbon` includes Base Metals coal and charcoal powders through required `forge:dusts/coal` and `forge:dusts/charcoal` references. Coal/charcoal can be crushed with a crackhammer or a powered crusher. |
| Rubber | Bioplastic joins `forge:rubber` and `forge:ingots/rubber` only when Power's `options.plasticEqualsRubber` is true. Turning it off retains the plastic recipe alternatives and preserves any rubber supplied by another data pack or mod. Petrolplastic is plastic, not an unconditional rubber substitute. |
| Plastic | Both `poweradvantage:bioplastic_ingot` and `electricadvantage:petrolplastic_ingot` belong to `forge:plastic` and `forge:ingots/plastic`. |
| Solder | Normal mode additionally accepts lead, tin or silver ingots through `forge:solder`, as the legacy mod did. Tech/Apocalyptic require actual solder unless a user data pack adds alternatives. |
| Circuit board | `forge:circuit_boards` means the finished `electricadvantage:control_circuit`, not the blank board. A blank board cannot bypass the microchip/control-circuit stage. |

Forge 25's early optional nested-tag resolution can cache an unresolved tag.
The carbon references are therefore required, with empty declarations so reduced
mod sets still load safely. Empty tags remain empty: there is no free substitute
for a missing material provider.

Configured aliases are applied at the end of item-tag reload, before recipe and
advancement parsing. Server tag serialization therefore includes the same policy
used by recipes. The initial vanilla-only bootstrap is ignored until FML's mod
list and Advantage items exist. Resource reload starts from fresh data-pack tags,
so aliases do not accumulate when a mode or option changes between runs.

## The First Workshop

1. Mine and process metals through Base Metals. Iron and copper equipment lead
   to crackhammers, steel, brass, bars, rods and plates.
2. Craft one ordinary or poisonous potato into one starch in the inventory grid
   or a crafting table. Crackhammers and both powered crushers remain alternative
   routes at the same ratio. Smelt starch for one bioplastic ingot (200 ticks,
   0.1 XP). This removes the plastic/refinery bootstrap loop.
3. Crush coal or charcoal for carbon powder. Silicon blend smelts to silicon;
   solder blend smelts to solder (200 ticks and 0.5 XP each).
4. Smelt lithium ore or powder for one lithium ingot (200 ticks, 0.5 XP). Crushing
   lithium ore yields two powders; crushing an ingot yields one. Nine lithium
   nuggets combine into one ingot, and one ingot makes nine nuggets.
5. Sulfur ore can be mined for its existing powder drops or crushed for four
   powders. Use sulfur, lithium, solder and circuits for the electrical parts.
6. Use frames, pipes and conveyors to build the steam/electric machinery. Finding
   a relevant ingredient earns the matching recipe discovery; no `/recipe give`
   command is needed. See the [inventory checklist](RECIPE-DISCOVERY.md).

Bioplastic is the initial plastic supply. Petrolplastic is a later alternative:
collect crude oil in a bucket, distil two buckets into one refined-oil bucket,
then process refined oil in a powered plastic refinery. The refinery consumes
100 mB refined oil and 1,200 electricity per ingot, unchanged from the machine's
existing code. Oil is acquired from a world source, not crafted out of an empty
bucket. World-generation qualification is a separate test suite.

## Converters

All four preserved converters now have acquisition recipes:

| Converter | Crafting Inputs |
| --- | --- |
| RF-labelled steam | Governor, steel frame and redstone block in a row |
| RF-labelled electricity | PSU, steel frame and redstone block in a row |
| RF-labelled quantum | Ender pearl, steel frame and redstone block in a row |
| Tech Reborn-labelled electricity | PSU surrounded by eight refined-iron ingots; ordinary iron is the fallback only when no refined-iron tag provider exists |

The labels and saved identities remain historical; the 1.13 implementations use
Forge Energy. Steam or Electric Advantage supplies the relevant governor or PSU.
The recipes remain guarded when that expansion or another ingredient is absent.

## Apocalyptic Salvage

Apocalyptic intentionally disables new manufacture of sprockets, governors and
PSUs, and forbids new photovoltaic generators. This is not an empty-tag defect.
Mineshaft, dungeon, blacksmith and stronghold chest pools supply the original
Advantage parts; Steam/Electric also contribute to end-city chests. Native loot
tables now provide a fallback when Additional Loot Tables is absent. Existing
legacy pools, weights, quantities and musket-enchantment functions are retained.
Data packs can replace the namespaced `chests/advantage_parts` tables.

The native fallback is disabled when Additional Loot Tables is installed, to
avoid injecting both copies. Setting Power's treasure-chest loot factor to zero
also disables it. Positive factor scaling is not newly implemented: the legacy
static pools did not scale their weights with that value. Optional ALT behavior
is not qualified by the default no-ALT pack.

The original recycling paths recover steel plates, fluid pipes, governors, PSUs,
control circuits and microchips from salvaged equipment. Photovoltaic equipment
remains intentionally unobtainable in a fresh Apocalyptic world; use hydro or
steam generation. Preserved existing photovoltaic blocks are not deleted.

## Inspector's Procedure

```text
test-rig/migration/.venv/Scripts/python.exe test-rig/migration/progression.py --java8 <java8.exe> --output run-progression/new-current
test-rig/migration/.venv/Scripts/python.exe test-rig/migration/progression.py --java8 <java8.exe> --output run-progression/new-baseline --baseline
```

The runner creates a marked disposable superflat world, stages hash-checked
packaged jars and exposes an operator-only `/advprogression <label>` command.
It does not touch Sylvester or the user's saved client worlds. Live witnesses
are confined to the declared 32-block test bay at x/z 16384.

The natural-resource basis is recorded in `progression-fixture.json`. It includes
ores, crops, wood, ordinary drops and basic world materials, not finished
Advantage parts. Quantities are renewable/unbounded for this reachability test;
it is not a balanced finite survival playthrough or a measure of player effort.
Apocalyptic alone may add parts sampled from actual vanilla chest loot.

The probe executes loaded crafting/processing recipe matching with exact grids,
retains input/output provenance, and triggers native inventory advancements with
a real server player. Every loaded Advantage crafting/smelting variant must be
reachable and discovered, not merely one alternative per output item.
Dedicated live witnesses exercise vanilla furnace ticks, crackhammer item use,
world-fluid bucket pickup, fuelled distillation and plastic-refinery conversion.
The refinery witness injects a measured finite energy budget only after a
generator is obtainable; it is not a replacement for network/generator tests.
The reset helper uses clean native tile instances and clears prior test inputs;
it does not mistake an earlier witness's residual furnace heat or removed item
entities for the current operation.

Each of the three modes is checked with the rubber option true and false, then
after `/reload` and after stopping/restarting the complete server process.
The tag oracle rejects empty wire/carbon supplies, wrong circuit boards,
unconditional rubber and Normal-only solder leaking into other modes.
Reports enumerate every registered item, its route or an explicit exclusion,
executed/blocked recipe IDs, actual tag members, live witnesses and jar hashes.
An eight-seed dungeon-chest witness must still return exactly the single dirt
block declared by its test data pack, proving the native salvage fallback does
not append loot to a player override.

The baseline uses immutable jars from the earlier discovery qualification,
demonstrating failures before these repairs. Failed helper/debug runs are kept
alongside the final evidence, not overwritten or treated as product passes.
This is automated manufacturing/API qualification, not visual recipe-book
acceptance, arbitrary modpack coverage or a new endurance/worldgen qualification.

## Current Recipe-Book Update: 23 October 2026

The new shapeless potato recipe removes the crackhammer prerequisite for initial
starch and bioplastic. Both potato types work without Base Metals, in all recipe
modes. The original crushing recipe remains unchanged.

The complete manufacturing matrix passes again: all three modes, both rubber
settings, 12 process boots and six resource reloads, with zero blocked targets or
recipes. Each check additionally runs both potato types through the live Steam
and Electric Crushers, supplying finite power and asserting exactly one input
consumed for one starch. The native crackhammer and furnace witnesses still pass.
Exact evidence is `run-progression/20261023-grouping-current/progression-results.json`.

The five-profile discovery matrix passes 109 advancements across 25 process
boots. Seventy-five mod JUnit tests, 15 harness tests and 140 Python contracts
pass. See [Recipe Discovery](RECIPE-DISCOVERY.md) for the native client grouping,
rendered buttons, new 27-item checklist and exact pack-specific skip counts.
No source worlds, registry identities or existing machine states were changed.

## Archived Qualification: 22 October 2026

| Gate | Result |
| --- | --- |
| Original immutable manufacturing baseline | Expected failure on both boots: 42 blocked items and 29 blocked loaded recipe variants |
| Current manufacturing matrix | PASS: all three modes, both rubber settings, 12 full process boots and six in-process resource reloads; zero blocked items/recipes |
| Survival registry coverage | Normal/Tech: 83 reachable items and 11 explicit helper/creative exclusions. Apocalyptic: 82 reachable, the same 11 exclusions and the intentional photovoltaic restriction |
| Discovery/persistence matrix | PASS: five pack combinations and 25 process boots; all 108 discoveries in the full pack |
| Missing-provider profiles | PASS with explicit unavailable-ingredient skips, never free ingredient substitution |
| Unit/runner contracts | 70 mod JUnit tests, 15 harness tests and 140 Python tests passed |
| Release/dependency/Eclipse audits | PASS for all three mods; Java 8 output, no embedded dependencies or harness content, current artifact checksums |
| Combined native client | PASS: 94 items, 905 block states and 87 creative entries; no Advantage model/texture failures |

Final evidence is local and intentionally untracked:

- `run-progression/20261022-baseline-qualified/progression-results.json`
- `run-progression/20261022-qualified-current/progression-results.json`
- `run-recipe-unlocks/20261022-progression-final-{current,decoupled,none,steam,electric}/recipe-unlock-results.json`
- `build/client-resource-runs/20261022-102605-c56bf236/client-resources.json`

Each report preserves exact input hashes. Both native loot acquisition and the
player data-pack override witness pass after resource reload and full restart.
No source worlds were opened, and no commit, push or publication was performed.
Manual recipe-book layout/notifications remain a separate graphical acceptance
check; successful server discovery does not claim that check was performed.
