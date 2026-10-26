# Advantage 1.15.2 Port

The three local candidates are Power Advantage **2.4.6.115021** and Steam and
Electric Advantage **2.2.7.115021**. The [test record](CURRENT-QUALIFICATION.md)
identifies the exact jars, completed checks and remaining limits.
Manual acceptance is separate, and no 1.15 port code has been pushed.

**Back up the entire world before upgrading. Test a disposable copy first.**
The Advantage fixtures do not qualify an entire modpack or another mod's
conversion support.

## Platform

| Setting | Target |
| --- | --- |
| Minecraft / Forge | 1.15.2 / 31.2.57 |
| Snapshot mappings / MCP | 20200514-1.15.1 / 20200515.085601 |
| ForgeGradle / Gradle / Renamer | 7.0.34 / 9.6.1 / 1.1.5 |
| Gradle / production / Mavenizer JDK | Java 17 / Java 8 / Java 25 |
| Base Metals / OreSpawn | local 3.0.2.115021 / 4.1.1.115021 |

The implementation uses native activation results, block properties, render
layers, matrix stacks and vertex buffers. Thin machinery stays non-solid,
solar panels stay solid, and steam tracks and elevator platforms retain their
old light and redstone behavior. Recipe-condition registration runs on Forge's
loading thread, and the shared creative tab has one initialization lock.

Mod IDs, registry and fluid names, saved-state keys, recipe identities and
legacy public API packages are retained. The migration hook is retargeted to
1.15; actual preservation and operation are tested separately from compilation.

## Source

The upstream and fork `master-1.15.2` branches started from the merged 1.14
versions:

| Mod | Starting MMD Commit | Local Feature Branch |
| --- | --- | --- |
| Power | `ef08e5cd134dc75bd73518bf4c90a6711e67280f` | `feature/poweradvantage-1.15.2-port` |
| Steam | `d0bbc53ab26870ab975f80230f92b8b29e6667a8` | `feature/steamadvantage-1.15.2-port` |
| Electric | `0f34225d871e5804bc0ece2f6c1b5482824a3039` | `feature/electricadvantage-1.15.2-port` |

The production jar bytes first came from Power `75d24d2`, Steam `7883d9b` and
Electric `f39a7f8`. The dependency update is committed locally as Power
`6666099`, Steam `07f6a05` and Electric `58bb1a7`. Steam and Electric pin
Power's exact source and development-jar checksum in `gradle.properties`.

Base Metals `3.0.2.115021` is local source `ac1781e7`, fixing the shared
world-hook registration race found by the migration tests. It has not been
pushed or published. That dependency must be available upstream before these
Advantage branches can pass hosted builds or be proposed for merge.

## Manual Testing

Use `D:\PowerAdvantage\PA115` as a separate Eclipse workspace. Import the
three nested repositories as existing Gradle projects:

- `PowerAdvantage`
- `SteamAdvantage\SteamAdvantage`
- `ElectricAdvantage\ElectricAdvantage`

Refresh Gradle, let Eclipse finish building, then use Power's generated
`runClientAllAdvantage.launch`. Its ordinary `runClient.launch` remains
Power-only. Both use Power's `run` directory; preparing a launch never opens
or converts a world.

Models, fluid screens and recipe-book cells have automated client probes,
but in-world animation, controls, live menus, real player login and the
hydroturbine still need personal acceptance. The test record lists known
vanilla differences and the boundaries of the server fixtures.

## Delivery

The 1.14 PRs are merged. The 1.15 work stays local until manual testing is
accepted. No 1.15 push, PR, merge, tag or publication is included.
