# Advantage 1.16.5 Test Record

Status: **ready for manual testing; bounded automated checks complete, with the recorded native difference below**.

This record covers Minecraft 1.16.5 and Forge 36.2.34. Earlier-version passes
and earlier 1.16 candidates do not qualify these jars. All world tests use
disposable copies. Back up the whole world before upgrading, including its
configuration and player files.

## Current Builds

| Mod | Version | Tested Production Source |
| --- | --- | --- |
| Power Advantage | 2.4.7.116051 | `bcb75bfa91c14751ebf1e31865e1ec0ffa82c0b7` |
| Steam Advantage | 2.2.8.116051 | `5be1ce9faf7465d072f2c8b0e3206899bafb34e8` |
| Electric Advantage | 2.2.8.116051 | `9a8fdf53a111499f2ebc969ac24a6f3e1017c18e` |

| Release Jar | SHA-256 |
| --- | --- |
| PowerAdvantage-2.4.7.116051.jar | `5BE5C05D9F78BAF730553B71D07D76BA923EA0A54ABCA53F4149386B27019E71` |
| SteamAdvantage-2.2.8.116051.jar | `0E8EF7DA681A5105E5206F166A36F56AEF356DFD227F1C3910DE7BDC606AA148` |
| ElectricAdvantage-2.2.8.116051.jar | `A9B62DD737E42455FEE72BD2D855C9C5EDBF5A9C5EAD7B48C650C9073A35B129` |

Both extensions compile against Power's exact deobf jar:
`2FB923944A456B4F26AB2F9212C646469CE3F49BFD4DAE894C15559B48908386`.
The local K manifests seal these artifacts, Base Metals 3.1.1.116051 and
OreSpawn 4.1.0.116051. Mineralogy 6.1.4.116051 is added for its relevant tests.
Dependencies remain separate jars. Base Metals' hook fix is merged into MMD;
no tag or release upload is included in this campaign.

The chained 1.10-to-1.13-to-1.16 Nether test rejected the previous Power jar
because Forge name-tag-distance attributes disappeared. The repair covers all
four Forge identities found in the supported source versions. Native loading
tests now retain their values and modifiers. All eight Nether and eight End
routes have completed full restarts without further attribute loss. Earlier
failed candidates remain recorded; only the K runs below qualify these jars.

Later local commits update documentation and verification tools only. The
production-source references above identify the packaged code; release and
development jar hashes remain unchanged by those follow-up commits.

The harness and its native serialization tools are pinned at
`c7a79fc9894e067ed0645daad253e1355628d138`. The rebuilt harness jars are byte-for-byte
identical to the previous runtime; the compile dependency and finite attribute
name projections have been updated. It has 146 Java tests.
Test tools are not packaged in any Advantage release.

## Completed Build Checks

| Check | Result |
| --- | --- |
| Release suites | Power 174, Steam 87 and Electric 47 tests pass with no failures or skips; dependency, identity, Java 8, resource, archive and Eclipse audits pass |
| Independent Eclipse compiler | Fresh production and test compilation passes the same 174/87/47 tests |
| Reproducibility | Power passes two independent spaced checkouts, the first using an empty cache; all four jars match exactly. Rebuilt Steam and Electric jars exactly match their independently reproduced builds, including sources and Javadoc |
| Harness self-tests | 146 Java tests and 1,087 Python tests pass; Python child processes are forbidden during the offline run |
| Profile validation | All ten sealed native profiles validate without starting Minecraft or opening a world |

## Current Runtime Checks

These are fresh K runs against the repaired artifacts above, not earlier passes
relabeled for the new build. Raw results, copied worlds, logs, input hashes and
independent audits are retained locally outside the release artifacts.

| Check | Result |
| --- | --- |
| Complete Works, current dependencies | All 21 bays pass their automated assertions, with 96 pass and 3 explicit skips per boot, including full restart |
| Complete Works, without Base Metals | 95 pass and 4 explicit integration skips per boot, including full restart; this does not qualify upgrading a Base Metals world without that mod |
| Extended drill reads | Eighteen routes pass mining, movement, mid-block restart and saved-state checks in fresh and upgraded worlds; independent replay passes |
| Survival mining and loot | 271 cases pass before and after restart, including failed tools, exact drops, Silk Touch and connection masks |
| Recipes and progression | Five unlock boots and eighteen progression checks pass across all three modes, rubber settings, reload and restart |
| Fluids and integration | Six oil lanes pass across three boots; 22 native capability/converter checks and two restart checks pass, with independent raw replay |
| World generation | Advantage-only, Mineralogy and imported legacy profiles pass generation and full-restart checks; the existing exclusions below remain explicit |
| Chunk lifecycle | Five real unload/reload cycles per fresh/upgraded profile pass with retained tank contents |
| Partial-network unloading | Separate 32-chunk layouts pass machine-only and supply-only unloading, reload-order checks and bridge recovery |
| Extended physical unload and drills | 144 native drill movements pass across fresh/upgraded profiles, together with blocked-output, scarcity, redstone, restart and partial-unload checks |
| Combined client | 94 items, 905 states, 87 creative entries, three recipe-book modes and 25 worldless renderer cases pass, using the current K server exports; all 543 actual Power classes/resources match the frozen development jar |
| World-upgrade matrix | Fifteen frozen and fifteen connected routes pass across 60 boots and thirty exact 512-tick operating intervals, without rebuilding or replacing imported machines |
| Focused machine inventories | Thirty-six boots pass strict saved-file checks, including nested items, material variants, wear and enchantments |
| Players, storage and transformer order | Twenty-four actor boots, twelve storage boots and twelve transformer-order boots pass independent replay |
| Nether and End migration | Forty-eight boots complete with the exact native wolf-health difference below; every other field passes the existing strict comparison |
| Respawn and player data | Twelve boots pass, including the native default Overworld representation described below |
| Paired endurance | Fresh and upgraded-copy worlds each pass 1,801.578 measured seconds, thirty one-minute batches, save, full-process restart and resumed processing; independent replay checks all 33 checkpoints per world |
| Prepared manual copy | Reset, save, full-process restart and shutdown pass; ten launch-guard controls pass. No real player login is claimed |

The earlier broader campaign stopped after fifteen successful boots and one
failed chained route. Its raw failures and failed assessments remain unchanged.
The new campaign completes all 144 machine, player, storage, transformer-order,
dimension and respawn boots with the repaired jar. Ninety-six pass the strict
comparison; forty-eight retain a raw failure and receive only the separate
`PASS_WITH_NATIVE_DIFFERENCE` assessment. All 54 runtimes stop cleanly, with no
forced process termination. Independent replay reproduces the same results.

Independent exact mod-inventory replay checks the closed runtimes against the
sealed six-mod manifest. Alternative profile/schema cases are explicitly
outside that validator, not silently counted. The 72 source runtimes, 198
historical files and frozen helper snapshot remain unchanged. Inventory checks
establish input custody, not gameplay correctness.

Endurance exercises rebuilt Works stations in both a fresh world and a copy of
an upgraded world. It does not claim thirty minutes of operation for every
original imported machine. The separate connected-world matrix operates those
original setups without rebuilding or replacing them.

These are automated checks, not a claim that real menus or player controls have
been accepted in game. Individual station reports retain `MANUAL_REQUIRED` for
their human checks.

## Upgrade Repairs

The wider checks rejected earlier candidates. Repairs cover vanilla registry
renames, entity UUIDs and ownership, mixed vanilla/mod entity conversion,
pending-save NBT ownership, and capability invalidation and revival on unload.
Stored items retain material variants, wear, nested contents and named
enchantments. Native conversion remains responsible for the vanilla formats.

The latest repair translates four exact Forge attribute identities. It retains
customized values and modifiers for entities, passengers, players and stored
mounts. Native negative controls demonstrate the losses without that repair.
Colliding aliases fail closed instead of choosing one value and discarding the
other; private inventory compounds are not traversed.

| Legacy Name | Native Registry Name |
| --- | --- |
| `forge.swimSpeed` | `forge:swim_speed` |
| `forge.nameTagDistance` | `forge:nametag_distance` |
| `forge.entity_gravity` | `forge:entity_gravity` |
| `generic.reachDistance` | `forge:reach_distance` |

Native serialization observations use actual entity/world identity, not an
expected fixture dimension. Comparison accounts for finite known attribute-name
changes, empty modifier lists and the explicit Overworld respawn default.
Unknown names, nonempty modifiers and saved quantities are not silently
discarded.

## Native Behaviour

An isolated, current-version 1.16 control with no Power code shows a tamed wolf
loading at 20 health after being saved at 8. Native DFU retains 8; the wolf's
normal load/taming code heals it. Wild wolves do not show that change. This is
not repaired by changing vanilla behaviour in Advantage.

The source-preservation comparison remains `FAIL` for that exact change. A
separate `PASS_WITH_NATIVE_DIFFERENCE` assessment requires the pinned control,
matching entity and owner UUIDs, the exact 8-to-20 change, and all other fields
matching the existing comparison scope. Raw failures and nonzero exit statuses
are retained. Any additional inventory, attribute, relationship or dimension
difference still fails. This assessment is not an all-green preservation result.

Older Forge can omit a player's default Overworld respawn dimension. Native
1.16 writes it explicitly when a spawn position exists. Patched source from
1.10, 1.12 and 1.16 confirms this is the same default, not a changed destination.
The comparison accepts only that absent-versus-zero representation at the two
player roots, with complete integer coordinates. Changed coordinates, forced
flags, explicit non-default dimensions and unknown extra fields still fail.

## Evidence Handling

Each native server starts from a sealed 69-file Forge installation. Its code is
checked before boot and after shutdown. Transformer-order observers have their
own derived inventory: exactly one validated Forge library entry may differ;
the original inventory is retained. Target errors, fatal messages and Java
exceptions are rejected, including errors logged while a fixer returns partial
data.

Some original source jars emit known historical diagnostics. Source witnesses
record only exact messages and counts from pinned original logs and are labelled
`SOURCE_WITNESS_WITH_KNOWN_DIAGNOSTICS`, not clean-startup passes. Those allowances
do not apply to the 1.16 destination.

Shorter unloading layouts remained inside native chunk-ticket influence and
failed to deliver the required physical unload events. Those failures remain
recorded. Separate 32-chunk layouts require real unload/reload callbacks, a
stable absent interval and unchanged transfer/content checks; loaded-only
transfers are not substituted for recovery after unloading.

The imported legacy world-generation profile retains its existing crude-oil
count-stability exclusion and does not require the zero-biome assertion used by
the two fresh profiles. No new exclusion was added for this port. Its pass does
not claim the excluded count was checked.

An earlier harness cleanup mistake removed disposable raw output under
`test-rig/build/`. Original fixtures, accepted-version checkouts and the Sylvester
world were not affected. Lost raw results are not claimed as preserved proof.
Compiler output now lives in `test-rig/build/gradle/`, cleanup refuses other
targets, and a real cleanup regression checks sibling fixtures survive. Current
completed evidence is also archived outside build directories.

One offline run encountered an occupied test port during live world testing.
It remains a failed attempt. Repeating it in a quiet window passed without
changing or bypassing the port guard. The final, expanded suite passes all
1,087 tests with child processes forbidden and source hashes unchanged.

## Manual Acceptance

Still check real-world rendering, menus, controls, sound, weapons, multiplayer
and actual player login. The worldless renderer cases do not qualify turbine
orientation or animation in a live world. The generated Works copy is prepared
for a walkthrough but is not left running automatically.

Use `D:\PowerAdvantage\PA116` as a separate Eclipse workspace, keeping the accepted
1.15 workspace separate. Import the three projects there as existing Gradle
projects. In Power, use **Gradle > Refresh Gradle Project**, then
**Project > Clean**, let compilation finish, and select
`runClientAllAdvantage.launch`. This uses the pinned Steam and Electric
development jars. `runClient.launch` remains Power-only. The resource snapshot
checks do not populate Eclipse's compiled class output for it.

The disposable walkthrough server is
`PowerAdvantage/test-rig/build/native116-manual-20261027-power-f/Start-Manual.ps1`.
Run it with `-CheckOnly` first, then without that switch to serve
`localhost:25565`. It verifies the exact six mod jars, native server libraries,
Java 8, loopback-only settings and test-world markers before starting. The
prepared world has all 21 Works bays reset and operator access for SkyBlade1978.
Its two startup/save/restart checks and ten launch-guard controls pass; no actual
player login has been tested. Stop it normally before reusing its world.

For the walkthrough, check recipe discovery from raw materials, the creative
tab, all six drill directions, helper models, oil buckets, tank gauges and
transparent machinery edges. Open the steam and electric menus, then check
live turbine orientation/animation, turret beams, musket controls and sound.
Repeat a save and complete client restart. A separate client joining the Works
server remains the check for real login and multiplayer interaction.

These fixtures do not qualify the whole original Sylvester modpack. Every other
mod needs compatible target-version support too. Unsupported unrelated blocks
or items are not treated as acceptable data loss.

The connected-machine matrix includes 1.14 and 1.15 source worlds. The focused
player/entity fixtures instead cover original and maintained 1.10/1.12 sources
and their 1.13 upgrade routes; dedicated 1.14/1.15-origin actor fixtures are not
qualified. BaseMinerals combinations and individual third-party energy mods are
also outside this run. Converter checks exercise both directions of the native
Forge Energy contract, not every external mod's implementation.

The 1.16 changes remain local. Pushes, merges, tags and release uploads follow
manual acceptance and separate authorization.
