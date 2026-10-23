# Additional Dimensions and Entity Lifecycle

## Scope

This extends the [Overworld player/entity matrix](PLAYER-AND-ENTITY-MIGRATION.md)
with source-serialized Nether (`-1`) and End (`1`) fixtures. It is packaged,
dedicated-server qualification, not client login, a death-screen test or a
whole-modpack upgrade. No production code or mod jar is changed by these tests.

Five source profiles are exercised: original Cyano 1.10, early maintained 1.10,
current maintained 1.10, early 1.12 and maintained 1.12. Every profile upgrades
directly; the three 1.10 profiles also upgrade through the repaired Electric
`2.2.2.112021` bridge. The original 1.12 source jar remains unchanged. That gives
eight routes per dimension, sixteen altogether.

The source worlds are copies of the fingerprinted matrix fixtures. Their actual
legacy serializers build each dimension fixture. The originals and completed
generated source fixtures are hashed before/after use and never started as targets.
The original Sylvester save is not opened or modified.

## Witnesses and Assertions

Each selected dimension has a bounded, covered bedrock enclosure containing:

- A saved player with charged batteries, a damaged enchanted musket, oil items,
  Ender inventory, experience, health, UUID and an Overworld spawn coordinate.
- A boat carrying an owned, sitting wolf. Both individual entity state and the
  actual vehicle/passenger relationship are checked.
- A tamed horse with saddle, iron armour and a fence-knot leash. Ownership,
  health, attributes, equipment and the live leash endpoint must survive.
- An Electric hydroelectric generator and its saved turbine entity. The turbine
  UUID and parent coordinate must remain intact without the machine manufacturing
  a replacement. Machine updates remain frozen.

Four persistent entity witnesses must exist exactly once. A native leash knot is
transient in 1.13: its UUID is not a persistent identity guarantee. The comparison
requires its type and fence coordinates, not an unchanged knot UUID. Other live
holder UUIDs, passenger UUIDs and persistent entity UUIDs remain exact.

Legacy horse `Type=0` becomes `minecraft:horse`; old wolf collar colours are
normalized to the same colour meaning. New Forge attributes are allowed, but
existing attributes may not change. The generator's internal `initDone` cache
flag is excluded. Item quantities, energy tags, damage, enchantments, ownership,
dimensions, parent coordinates and other compared state are not relaxed.

After native ticks, movement coordinates and rotation are not equality tests.
Actual passenger and leash relationships remain mandatory. There is no reseeding,
replacement-entity creation or inventory refill between migration checkpoints.

## Timing and Restart Protocol

The native campaign deliberately permits native entity initialization **before
the first converted save**, then measures at least sixty entity ticks. Chained
routes do the same in 1.12 and completely restart that intermediate process.
Each target has conversion, real player-file adapter, full restart, further live
ticks and another full restart checkpoints. Backups must match predecessor bytes
and remain unchanged on later loads.

Chunk tickets/force-loading and an unlogged-in sentinel keep the fixture active.
The sentinel has a packet-discarding connection so vanilla entity tracking and
End boss bars can operate without a socket. This is not a player login or Forge
handshake. Native dragon code is not disabled or replaced.

### Zero-Tick Leash Limit

An earlier zero-tick diagnostic did **not** preserve the horse's leash. Vanilla
1.13 `EntityLiving` reads the `Leash` tag into pending data, but its writer emits
that tag only from a resolved live holder. Saving before its first initialization
can therefore erase the pending endpoint. The later native-initialization test
is a separate control, not a rewritten zero-tick PASS.

The cached Forge 25 injected source shows the reader retaining pending NBT, the
writer requiring a live holder and native initialization finding/recreating the
fence knot. Those findings are also recorded in the raw source/converted/runtime
snapshots. The existing strict Overworld matrix does not contain leashes and
must not be cited as zero-tick leash coverage.

## Death and Respawn Supplement

Independent cases restore a copied source-serialized player file, then exercise
native death and respawn with a dummy connection. This explicit case reset is
not part of the migration persistence proof. The six cases are `keepInventory`
on/off crossed with forced spawn, valid bed and missing bed, in each dimension.

Assertions require the same UUID, expected health/dimension, exact retained or
dropped inventories, unchanged Ender inventory, battery charge, musket damage
and enchantments, experience semantics, spawn position and persisted player file.
Dropped stacks are reconciled by identity and quantity, ignoring only bag slot.
Each source has fourteen counted carried items. No missing or duplicated item is
accepted as an expected failure.

The final results and native spawn diagnostics are recorded separately below.
An evidence replay PASS means records and assertions agree; it does not promote
a native respawn FAIL to gameplay PASS.

### Forge 25 Spawn Lookup Finding

The ordinary `NetHandlerPlayServer.PERFORM_RESPAWN` path reproduces the same
failure as the earlier direct `PlayerList` pilot: a valid bed or forced Overworld
spawn is ignored, the player returns to world spawn and saved spawn keys disappear.
The source player has the correct `SpawnX/Y/Z=160/100/24` before and after death;
the live Overworld `getBedLocation` nevertheless returns null before recreation.
Missing-bed cases returning to world spawn are expected and are not failures.

Inspection of the installed Forge 25 server bytecode and matching injected source
points to a native field mismatch: `setSpawnPoint` and NBT loading/writing use
`EntityPlayer.spawnPos`, whereas Forge's Overworld `getBedLocation(dim)` returns
the distinct `bedLocation` sleeping-position field. The respawn handler resolves
the destination dimension before asking for that location. Passing Overworld
instead of the original dimension would not repair this lookup.

The cached `net.minecraftforge:forge:1.13.2-25.0.223` injected `EntityPlayer.java`
has the Overworld getter at lines 1580-1582 and setter at 1624-1629. In the
installed SRG class, the getter reads `field_71081_bT` at bytecode offset 8;
the setter writes `field_71077_c` at offset 84. These are distinct fields, not
an inferred failure caused by an incorrectly requested destination dimension.

This is a candidate **platform-path defect**, not observed Advantage item loss.
The probe does not seed the sleeping-position field, alter vanilla methods or
rewrite expectations to obtain a PASS. The same handler is reached by an actual
respawn request, but an authenticated client sleeping/death test remains necessary
before calling its full gameplay impact confirmed. No production workaround is
included here.

## Repeating the Tests

Run from the PowerAdvantage checkout with a hash-pinned manifest containing the
dimension probes. `pin_dimension_profiles.py` replaces only test probes in an
existing qualification manifest and checks that production/dependency bytes
remain unchanged. Preserve or archive those probe jars before rebuilding them.
All output paths must be new, ignored directories.

```text
python test-rig/migration/pin_dimension_profiles.py --base <qualified-profiles.json> --output <dimension-profiles.json> --archive <ignored-probe-directory>
python test-rig/migration/dimensions.py --profile-manifest <dimension-profiles.json> --java8 <java8.exe> --output <new-run> --native-load
python test-rig/migration/audit_dimensions.py --run <completed-run> --output <audit.json>
python test-rig/migration/respawn.py --profile-manifest <dimension-profiles.json> --java8 <java8.exe> --fixtures <completed-run> --output <new-respawn-run> --port 25623
python test-rig/migration/audit_respawn.py --run <completed-respawn-run> --output <respawn-audit.json>
python -m unittest discover -s test-rig/migration -p test_*.py
```

The Python environment requires `nbtlib`; the local configured interpreter is
`test-rig/migration/.venv/Scripts/python.exe`. Servers bind localhost only and
shut down between phases. Existing listeners are never stopped. Reports contain
staged-jar pins, raw binary NBT, semantic comparisons, tick measurements, backups,
source fingerprints and console logs. Worlds, jars and generated reports remain
untracked.

## Qualified Results, 7 October 2026

The final dimension runs use identical archived test pins and unchanged
production/dependency jars. Both commands exit zero; both independent replays
exit zero and require all eight requested route IDs, all four persistent
witnesses' tick evidence and unchanged source fixtures.

| Campaign | Result |
| --- | --- |
| Nether, all eight direct/chained routes | PASS |
| End, all eight direct/chained routes | PASS |
| Intermediate 1.12 save and full restart on all six chains | PASS |
| Player file/adapter, exact normalized item NBT and charged/damaged/enchantment-bearing items | PASS |
| Persistent UUIDs, ownership, equipment, live passenger/leash endpoints and turbine parent | PASS |
| Target conversion, native ticks and two full process restarts | PASS |
| Predecessor-byte backups and later backup stability | PASS |
| Both independent dimension replays | PASS, zero integrity findings |
| Python regression/oracle suite | 128 tests, zero failures/errors |
| Java probe builds, 110/112/113 | `compileJava check assemble` PASS; legacy tests NO-SOURCE, modern 12 tests PASS |

The final death/respawn supplement uses those completed dimension fixtures,
not the interrupted pilot worlds. Each dimension contributes eighteen cases:
original 1.10, maintained 1.12 and original-1.10-chained 1.13, each crossed with
the six keep/drop and spawn modes. Every case uses the native `PERFORM_RESPAWN`
handler, archives its saved player and `level.dat`, and checks missing-bed
respawns against the actual saved world-spawn coordinate and `spawnRadius`.

| Respawn group | Exact result |
| --- | --- |
| Original 1.10, both dimensions | 12 PASS |
| Maintained 1.12, both dimensions | 12 PASS |
| 1.13 missing-bed cases, both keep/drop modes and dimensions | 4 PASS |
| 1.13 valid-bed/forced-spawn cases, both modes and dimensions | 8 FAIL: spawn lookup/retained spawn keys only |
| Carried/drop quantities, item NBT, charge, damage, enchantments, Ender contents, UUID and health/dimension checks | PASS in all 36 cases |
| Saved player files match the native post-respawn observations | PASS in all 36 cases |
| Both independent respawn replays | PASS: recorded failures reproduce, no integrity findings |

The respawn runners intentionally exit **1**, because eight behavioral assertions
fail. Their audits exit zero because those results agree with the raw records;
this is not an all-tests-pass or release-approval claim.

### Evidence and Pins

Large saves, staged libraries, binary snapshots and logs are retained under
`C:\Users\johnb\AppData\Local\AdvantageWorks\qualification\`:

- `20261007-dimensions-nether-final`: eight routes, scope, raw NBT, tick evidence,
  source fingerprints, backups and `integrity-audit.json`.
- `20261007-dimensions-end-final`: the matching eight End routes and replay.
- `20261007-respawn-nether-qualified`: eighteen native cases and their replay.
- `20261007-respawn-end-qualified`: eighteen native cases and their replay.
- `20261007-order-full-r2`: the separately qualified six transformer-order cases.

Small manifests/reports are retained under ignored `run-dimension-migration/`.
The pin manifest is `20261007-inputs/profiles-qualified.json`; its provenance
requires every production/dependency SHA-256 to match the preceding accepted
actor campaign. The archived test artifacts are:

```text
AdvantageMigrationProbe-110-1.0.0.jar and AdvantageMigrationProbe-112-1.0.0.jar
38565B6671E249B6E12D5B238355FD7D48218B261789E7E92316F9D9DC29F94A
AdvantageWorksTestHarness-1.1.0.113021.jar
8B39BB591A376A626FC25C7A8167F05F0937338B9BE21D45DCE9D1D60B8C092E
```

Reproduction of these split runs uses `dimensions.py --dimensions -1` or `1`
with separate outputs/ports; use `respawn.py --districts nether` or `end` and
its matching final dimension root. Archive copies protect the probe pins from
later builds. Nothing is installed into an active client or server.

Earlier full-r1/r2, smoke and respawn pilots are retained diagnostics, not the
accepted campaigns. They exposed duplicate freeze-list restoration, pending
vanilla leash serialization, null sentinel connections during tracking/End boss
packets and legacy chunk-ticket double release during shutdown. The final probes
correct the test-only connection/freeze/ticket handling; they do not suppress
native dragon behavior or restore lost production data. Replay now rejects
tracking, crash and shutdown exceptions even if the process exits zero.

Item-stack dictionaries compare exactly after declared serialization normalization.
Unknown turbine-like names are not aliases; duplicate helper witnesses are
rejected. New native entity defaults outside the selected semantic surface remain
allowed. Existing BaseMetals leaves-fixer/config/resource-pack diagnostics are
retained separately: these results are not a blanket error-free-log claim.

## Remaining Limits

Client login/handshake, death-screen interaction, rendering, GUI and ownership
synchronization remain manual gates. This does not test portal travel, mounted
player travel, combat, riding elevators, advancements/statistics or arbitrary
third-party capabilities/entities. Fixed, invulnerable fixture actors establish
persistence and native relationships, not complete natural animal gameplay.

See [transformer ordering](TRANSFORMER-ORDERING.md) for mixed BaseMetals/Advantage
hook execution and the separate backup-provenance caveat. A complete pre-upgrade
world copy is still required; loose-file backups do not restore converted regions.
