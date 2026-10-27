# Advantage 1.16.5 Port

This branch starts from the merged, accepted 1.15.2 sources. Earlier tests remain
in their original records and do not qualify the 1.16 build.

## Target

- Minecraft 1.16.5, Forge 36.2.34, official 1.16.5 mappings.
- ForgeGradle 7.0.34, Gradle 9.6.1, Java 17 for Gradle and Java 8 production code.
- Power Advantage 2.4.7.116051; Steam and Electric Advantage 2.2.8.116051.
- Base Metals 3.1.1.116051 and OreSpawn 4.1.0.116051, pinned without embedding.

## Compatibility

Registry names, assets, fluid identities, recipes, provider IDs and saved machine
keys are retained. Network caches use dimension registry keys, including custom
dimensions, instead of numeric IDs. The legacy public API and compatibility
facades remain; native Minecraft method signatures change with the platform.

The native server tag reader adds the configured rubber and solder aliases
before recipes load. Two access-transformer entries expose only the native reader
and its owner. No tag coremod or client-side configuration rewrite is needed.

## Checks

The running [qualification record](CURRENT-QUALIFICATION.md) tracks the current
artifacts, completed checks and remaining gaps.

Production compilation, unit tests, artifact/dependency/resource audits, Eclipse
classpaths, independent builds, the combined client and connected-world
migrations pass locally. The wider player/entity campaign has completed its
144 boots with only the separately controlled native wolf-health difference.
After repairing all four old Forge attribute names, fresh operating, unloading
and paired 30-minute endurance runs pass against the exact new jars, including
complete process restarts and resumed processing. Independent raw-result replay
passes. The qualification record keeps native differences, earlier failures and
remaining work separate.
The local candidates are ready for manual testing, not gameplay acceptance or
publication.

Use hash-pinned disposable copies for every world test. Original fixtures and
the Sylvester world must remain untouched. Preserve failed results and report
vanilla migration differences separately from Advantage state or inventory loss.

Manual visuals, recipe unlocks and interaction checks follow the automated suite.
No 1.16 port changes have been pushed, tagged or published.
