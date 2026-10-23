# Advantage 1.13.2 World Migration

This release contains migration support for Advantage worlds created by Minecraft 1.10.2 and 1.12.2. PowerAdvantage owns one shared pre-flattening hook; SteamAdvantage and ElectricAdvantage register their legacy state, tile, entity, item, fluid, and NBT codecs with it. See the [original-release and chained migration matrix](MIGRATION-MATRIX.md) for qualified fixtures and remaining acceptance gates.

The [current-candidate qualification and remaining tests](CURRENT-QUALIFICATION.md)
separates exact-artifact reruns, state preservation, measured operation and the
station-by-station behavioural work still outstanding.

The [connected legacy works campaign](CONNECTED-MIGRATION.md) runs equipment
under the old jars before importing it, then resumes the same machinery without
rebuilding or replacing anything. All 1.13 stages pass; chained routes retain an
intermediate 1.12 electric-drill progress-reset finding.

The [legacy drill repair](DRILL-PROGRESS-REPAIR.md) resolves that finding in the
maintained 1.10/1.12 implementations; all eight connected routes pass with the
repaired bridge while retaining the historical failed evidence.

## Before conversion

1. Back up the complete instance and world outside Minecraft.
2. Install matching 1.13.2 releases of PowerAdvantage, SteamAdvantage, ElectricAdvantage, Base Metals, and OreSpawn where the old world used them.
3. Remove unrelated 1.10/1.12 jars. A mod without a 1.13 port cannot be made compatible by the Advantage migration.
4. Convert a copy first and retain the original world unchanged.
5. Check the latest log and `<world>/poweradvantage-migration-report.txt` before accepting the conversion.

The migration reads the old Forge registry snapshot before vanilla flattening. Numeric block IDs and metadata are mapped to current states, including six-way drill facings, conveyors, filters, switches, pipes, fluids, tanks, helpers, machines, and converter variants.

Nested NBT traversal covers tile entities, ordinary entities, player data, inventories, item stacks, durable-item damage, Forge universal buckets, and named fluids. Existing quantities are not intentionally changed. Loose files are copied to `*.poweradvantage-legacy-backup` before mutation.

## Configuration

When no 1.13 TOML exists, each mod imports understood values from its retained Forge `.cfg` file and writes a migration report beside the new configuration. The original file is not removed. Repeated starts are idempotent and do not overwrite an existing TOML.

OreSpawn writes a world-owned profile under `<world>/serverconfig`. Once created, that profile remains authoritative when mods are added or removed. Existing chunks are protected from regeneration; generation changes apply only to new terrain.

The original Advantage releases used OreSpawn 1.x files under `config/orespawn/`. OreSpawn 1.13 directly imports its later OS2/OS3 format, while PowerAdvantage supplies the missing narrow OS1 bridge for `poweradvantage.json` and `electricadvantage.json`. It writes normal `<modid>-orespawn.json` provider overrides before OreSpawn initializes, retains the old files byte-for-byte, records the conversion in `config/orespawn-os3-migration-report.json`, and does nothing when a modern provider override or global profile is already authoritative.

## State acceptance

A converted test copy should retain:

- block and machine orientation;
- inventories, filters, batteries, and item NBT;
- fluids, tank levels, steam, energy, and temperature;
- processing progress and drill movement state;
- helper blocks and hydroturbine entities;
- conduit connectivity after a complete process restart;
- registered oil, sulfur/sulphur, lithium, steel, and solder interoperability.

A clean launch is necessary but not sufficient. Inspect representative machines, run them normally, save, stop Minecraft completely, restart, and inspect them again.

## Automated qualification

The nested [Advantage Works rig](../../test-rig/README.md) reuses the 1.10 station IDs and expected behavior on 1.13. It supports development and packaged runs, full-process restart checks, recipe modes, OreSpawn ownership, oil processing, and focused persistence witnesses.

Source fixtures and the Sylvester world are immutable. Migration runs use hash-checked disposable copies under ignored local test directories. Unrelated blocks from mods without 1.13 ports are exclusions, not acceptable air substitutions.
