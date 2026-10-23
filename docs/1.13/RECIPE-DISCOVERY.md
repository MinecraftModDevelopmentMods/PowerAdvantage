# Recipe Book Discovery: Minecraft 1.13.2

## Finding The First Parts

Each crafting and vanilla smelting recipe has a hidden recipe-book advancement:
35 for Power Advantage, 24 for Steam Advantage, and 50 for Electric Advantage. Finding **one**
relevant ingredient is enough. You do not need all the ingredients, a full stack,
or a completed machine. Tags accept equivalent ingredients from other mods.

The criteria follow Base Metals' ingredient-discovery convention: individual
ingredients are alternatives, not an AND condition requiring the entire grid.
Ordinary sticks, wooden rods, string and feathers are not discovery triggers
where a more relevant ingredient is present. Existing recipe quantities and
ingredient requirements are unchanged by discovery. The separate
[manufacturing repair](MANUFACTURING-PROGRESSION.md) restores missing processing
paths. Custom machine and crushing recipes are not recipe-book entries; vanilla
furnace recipes are, giving 104 crafting and five smelting variants.

## Inventory Checklist

With the pinned Base Metals pack and all three Advantage mods, acquiring **one
of each of these 27 items** earns every one of the 109 discovery advancements.
This deliberately includes alternate recipe variants, even if their current mode
disables the actual recipe. It is a discovery test kit, not a list of the supplies
needed to construct every machine. The kit is not claimed to be minimal.

| Item To Acquire | Exact Example ID | Main Discoveries |
| --- | --- | --- |
| Iron ingot | `minecraft:iron_ingot` | Iron pipes, rotation tool, alkaline battery and iron PSU variant |
| Steel ingot | `basemetals:steel_ingot` | Sprockets, metal tank, muskets, drill bit and steel PSU variant |
| Copper ingot | `basemetals:copper_ingot` | Copper pipes, ingot-wire variants and blank circuit board |
| Lead ingot | `basemetals:lead_ingot` | Apocalyptic lead pipes and lead-acid batteries |
| Brass ingot | `basemetals:brass_ingot` | Steam pipes and governors |
| Steel bars | `basemetals:steel_bars` | Steel frame |
| Steel frame | `poweradvantage:steel_frame` | Drains, discharge, switches, tracks and most machines |
| Fluid pipe | `poweradvantage:fluid_pipe` | Containers, drain/discharge and distillation apparatus |
| Conveyor | `poweradvantage:item_conveyor` | Every conveyor filter |
| Steam pipe | `steamadvantage:steam_pipe` | Steam tank, steam track and steam connections |
| Bioplastic ingot | `poweradvantage:bioplastic_ingot` | Portable container, circuit boards, insulated wires and batteries |
| Copper rod | `basemetals:copper_rod` | Rod-wire variants, including the rubber variant |
| Silicon ingot | `electricadvantage:silicon_ingot` | Microchips and photovoltaic generator |
| Microchip | `electricadvantage:integrated_circuit` | Control circuit, LED and growth chamber |
| Tin powder | `basemetals:tin_powder` | Both solder blends |
| Sand | `minecraft:sand` | Silicon blend |
| Gunpowder | `minecraft:gunpowder` | Black-powder cartridge and alkaline battery |
| Steel plate | `basemetals:steel_plate` | Conveyor and electric machinery |
| Power supply unit | `electricadvantage:psu` | Electric furnace and other electric machines |
| Starch | `poweradvantage:starch` | Bioplastic smelting |
| Lithium ingot | `electricadvantage:li_ingot` | Nugget conversion |
| Lithium nugget | `electricadvantage:li_nugget` | Ingot conversion |
| Lithium powder | `electricadvantage:li_powder` | Lithium smelting |
| Lithium ore | `electricadvantage:li_ore` | Direct ore smelting |
| Silicon blend | `electricadvantage:silicon_mix` | Silicon smelting |
| Solder blend | `electricadvantage:solder_mix` | Solder smelting |
| Potato | `minecraft:potato` | One-potato-to-one-starch crafting; poisonous potatoes also trigger this discovery |

Try items individually in a new test world. Pick them up, use `/give`, or move
them into your inventory from a chest. Items merely sitting in a chest do not
count. You may remove an item after its discoveries are earned. Previously
earned recipes will not produce another discovery notification.

Open a crafting table and disable the recipe book's **craftable-only** filter
to inspect recipes for which you do not yet have all the materials. Related
variants for the same output share a recipe-book cell; different output items
do not. Group IDs use the namespaced output item instead of broad categories
such as `circuits` or `conveyors`. The 109 advancements do not mean 109 visible
cells. `/recipe give @s *` bypasses discovery and should not be used for this test.

One ordinary or poisonous potato crafts into one starch in any occupied slot of
the inventory's 2x2 grid or a crafting table. This requires neither a tool nor
Base Metals. Existing crackhammer, Steam Rock Crusher and Electric Crusher
recipes remain available, also producing one starch per potato. Smelt the starch
for bioplastic. Extra occupied ingredient slots do not form the starch recipe.

## Modes, Optional Materials And Saved Games

The three recipe modes and the existing material-tag guards still control
whether a recipe is loaded. An advancement cannot enable a disabled recipe.
For example, discovering an Apocalyptic lead-pipe variant in Normal mode saves
the discovery, but does not make that variant craftable in Normal mode.

Power Advantage restores already-earned discoveries for all three namespaces on
the Forge player-login event. This makes recipes discovered while disabled
available when their mode or material provider subsequently allows them. It
does not grant undiscovered recipes. The ordinary recipe-unlocked criterion also
keeps discoveries consistent with operator recipe commands.

Optional tag names used by advancements are declared even without a provider;
empty declarations add no ingredients and cannot satisfy a recipe's material
guard. In particular, Electric's steam-related discovery metadata does not
require Steam Advantage to be installed.

The original registered items, recipes, world identities and existing player
progress are not renamed or reset. Test only copies of valuable saves.

## Rebuilding And Checking

Each repository owns `tools/generate-recipe-unlocks.js`. After changing a recipe:

```text
node tools/generate-recipe-unlocks.js
node tools/generate-recipe-unlocks.js --check
gradlew.bat check build verifyReleaseArtifacts writeReleaseChecksums
```

Normal builds do not require Node. The checked-in JSON is packaged directly;
JUnit checks exact recipe coverage, ingredient predicates, OR requirements,
recipe rewards and the absence of custom-machine/crushing unlocks.

The packaged runner reuses Advantage Works' dedicated-server staging, but makes
a fresh marked superflat world. It never opens the player's test worlds:

```text
test-rig/migration/.venv/Scripts/python.exe test-rig/migration/recipe_unlocks.py --java8 <java8.exe> --output run-recipe-unlocks/new-current
test-rig/migration/.venv/Scripts/python.exe test-rig/migration/recipe_unlocks.py --java8 <java8.exe> --output run-recipe-unlocks/new-decoupled --without-basemetals
```

Use `--extensions none`, `--extensions steam` or `--extensions electric` for
independent loading checks. The default loads both expansions. The output
directory must not already exist. Every dependency is hash-checked before staging.

The probe uses a real server player, actual inventory-change criteria and actual
recipe-book rewards, with a packet sink instead of a graphical client. It checks
empty and unrelated inventories, every available ingredient criterion, all tag
members, recipe-command discovery, and the 27-item kit. It saves an empty player
inventory, stops the complete server process and reloads the actual saved player
and advancements. A Normal restart and transitions through Tech Progression,
Apocalyptic and back to Normal test persistence and login reward restoration.
This does not constitute manual recipe-book rendering acceptance.

## Current Manufacturing Qualification

The repaired full pack loads all 109 advancements and discovers all of them with
the checklist above. The packaged discovery run passes five full server boots,
including all modes and saved-player restoration. Active recipe-book variants
are 93 in Normal, 87 in Tech Progression and 82 in Apocalyptic with the default
rubber setting. These counts include vanilla smelting, not custom crushing.

The original empty wire/carbon/rubber gaps and missing intermediate recipes are
addressed by the manufacturing repair. Exact acquisition evidence and deliberate
mode exclusions are documented in [Manufacturing Progression](MANUFACTURING-PROGRESSION.md).
Retained final discovery evidence is `run-recipe-unlocks/20261023-grouping-current`.

The final five-profile qualification repeats 25 complete process boots with the
repaired artifacts. No-Base-Metals profiles intentionally skip ingredient
predicates with no provider; they do not claim complete survival progression.

| Final Profile | Advancements Loaded | Discoverable | Explicit Missing-Provider Skips |
| --- | --- | --- | --- |
| Full current pack | 109 | 109 | 0 |
| Both expansions, no Base Metals | 109 | 100 | 9 |
| Power only, no Base Metals | 35 | 28 | 7 |
| Power/Steam, no Base Metals | 59 | 51 | 8 |
| Power/Electric, no Base Metals | 85 | 77 | 8 |

On 23 October the rig additionally checked 59 native starch-crafting cases per
operation: both potato types, every slot of 2x2/3x3 grids, stacks, invalid inputs,
extra occupied slots, exact output and one-item consumption. Loaded recipe
groups are checked on every discovery/persistence operation. The client decodes
the server's actual recipe packets after registry synchronization and rebuilds
native recipe-book cells: Normal 93 recipes/77 cells, Tech 87/77, Apocalyptic
82/73, counting crafting and furnace books together. No cell contains different
output identities, and all alternative recipes remain accessible.

The rendered native-button evidence is
`build/client-resource-runs/20261023-114732-a23f152c/recipe-book-cells.png`;
`client-resources.json` retains the complete native-client results. The full
25-boot discovery matrix lives under `run-recipe-unlocks/20261023-grouping-*`.
This verifies native cell grouping and button rendering, not a human's complete
recipe-book navigation or notification acceptance.

## Archived First Discovery Qualification: 22 October 2026

The figures in this section describe the earlier 96-advancement candidate,
before the manufacturing repair. They are retained as baseline evidence, not
the current recipe inventory.

The initial missing-resource regression was reproduced before adding the JSON.
The new static checks pass for all 96 crafting variants. Packaged current and
no-Base-Metals runs passed all three modes and full-process restart checks. All
96 advancements load in both packs. All 96 can be discovered with the documented
kit in the current pack; 85 have an available discovery ingredient without Base
Metals, with the remaining 11 explicitly reported as
`SKIP_NO_INGREDIENT_PROVIDER` rather than passed.

Evidence is retained locally under `run-recipe-unlocks/`, including failed
test-helper runs and the first incomplete checklist. Each final report records
the input jar hashes, active recipe count, per-criterion/tag-member checks and
restart outcomes. Existing material guards mean the full pack has 71 active
crafting variants in Normal, 66 in Tech Progression and 62 in Apocalyptic. The
unlock additions do not change those counts or silently enable guarded recipes.

Independent Power-only, Power/Steam and Power/Electric profiles also pass all
three modes, without Base Metals. The final qualification spans five profiles
and 25 dedicated-server boots (20 complete process restarts). All three release,
dependency, archive and Eclipse audits pass, together with 67 mod JUnit tests,
15 harness tests and 137 runner/migration tests.

| Final Local Evidence Directory Under `run-recipe-unlocks/` | Advancements Loaded | Discoverable With Available Ingredients |
| --- | --- | --- |
| `20261022-current-final` | 96 | 96 |
| `20261022-decoupled-final` | 96 | 85 |
| `20261022-power-only` | 28 | 22 |
| `20261022-steam-only` | 52 | 45 |
| `20261022-electric-only` | 72 | 62 |

The earlier tag gaps were not unlock failures. In that baseline pack, empty
`forge:wires`, `forge:dusts/carbon` and `forge:rubber` guards prevent some recipes
from loading, even if their discovery advancement has been earned. These affect
the electric boiler, electric switch, LED, photovoltaic generator, the Normal
PSU variants, lithium battery, silicon blend and rubber wire variants. This
baseline records why separate recipe/tag compatibility work was needed.

For Eclipse, refresh the Gradle projects and run `prepareEclipseResources` after
resource changes. Regenerate Power's `prepareCombinedEclipseLaunch` after updating
the expansion jars. Restart Minecraft before testing the new resources.

## Testing To-Do: 23 October 2026

Implemented after John's testing, then checked with the automated rig and native
client. Manual in-world recipe-book navigation remains available for further testing.

- [x] Separate different output items into their own recipe-book cells. Share a
  group only between genuine alternative recipes for the same output item.
- [x] Add a shapeless one-potato-to-one-starch crafting recipe as an accessible
  early-game alternative. Retain the crackhammer, Steam Rock Crusher and
  Electric Crusher routes. Add a potato inventory discovery and a distinct
  recipe-book group. Both ordinary and poisonous potatoes are accepted, matching
  the existing crushing inputs.

Recipe/discovery contracts, the 18-check manufacturing restart matrix, powered
crusher witnesses and the native client grouping/rendering gate all pass.
