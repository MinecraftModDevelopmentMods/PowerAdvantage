# Native 1.15 Manual Testing

## Prepared Copy

The disposable manual server is prepared, **not launched**:

```text
D:\PowerAdvantage\PA115\PowerAdvantage\test-rig\build\native115-manual-20261026-f
```

It contains the existing 21-bay Works world copied from the stopped, passed F
`native115-broad-20261026-f/packaged-current` runtime. The copied world is the
authoritative starting point for this manual session, including the machines,
supplies and saved state left by the broad test. It was not rebuilt, reset,
refilled or opened in Minecraft during preparation. This is a bounded manual
fixture, not another automated qualification or permission to open other saves.

The whitelist copy includes the world, config/defaultconfigs, libraries, all six
pinned F server mods, including Base Metals `3.0.2.115021` and harness E, plus
the Forge launcher, vanilla server jar, existing EULA,
properties, account lists and the two Works/live-operation markers. It excludes
old logs, result directories, verification JSON, migration-probe authorization
and cached mixin output. `ops.json`, whitelist, ban lists and user cache were
copied unchanged. No operator or external account was changed.

Only `server-port` changed, from `25592` to `25565`. The address remains
`127.0.0.1`; query and RCON remain disabled. The copied fixture retains
`online-mode=false`, creative mode and its existing local test configuration.
Do not expose it to a network, tunnel, public address or production players.

## Start And Stop

In a local PowerShell console, check the prepared inputs without launching Java:

```powershell
& 'D:\PowerAdvantage\PA115\PowerAdvantage\test-rig\build\native115-manual-20261026-f\Start-Manual.ps1' -CheckOnly
```

When ready to run the manual server, use the same command without `-CheckOnly`:

```powershell
& 'D:\PowerAdvantage\PA115\PowerAdvantage\test-rig\build\native115-manual-20261026-f\Start-Manual.ps1'
```

The launcher checks the exact prepared path beneath `test-rig/build`, rejects
links, verifies the local manifest and every pinned mod, library, launcher,
vanilla jar and marker, and rejects extra mod/library files or probe markers.
Properties are checked semantically as described below, not by their initial
file hash. It uses only this Java 8 home, with executable and release hashes:

```text
C:\Users\johnb\.jdks\temurin-8.0.502+7\jdk8u502-b07
```

Native 1.15.2 startup reloads and saves `server.properties` through
`ServerPropertiesProvider` and `PropertyManager`. Forge's `SortedProperties`
writes sorted keys using Java `Properties.store()`, which adds a new timestamp.
The installed native source and Java 8 source were checked directly. A raw file
hash would therefore reject an ordinary second start. The original properties
hash remains preparation evidence only; comments, timestamp, line endings and
entry order may change without blocking restart.

These eight critical properties must be present exactly once with their
canonical values:

| Property | Required Value |
| --- | --- |
| `level-name` | `AdvantageWorksMigration`, exactly; no other name or path |
| `server-ip` | `127.0.0.1` |
| `server-port` | `25565` |
| `online-mode` | `false` |
| `enable-query` | `false` |
| `enable-rcon` | `false` |
| `max-tick-time` | `60000` |
| `gamemode` | `creative` |

The validator intentionally accepts only this fixture's plain ASCII `key=value`
format, with optional surrounding key/separator whitespace, blank lines and
Java Properties `#`/`!` comment lines. Trailing value whitespace is not trimmed.
Duplicate keys anywhere, escaped keys/values, continuation lines, alternate
separators, BOM/non-ASCII bytes and malformed assignments fail closed. This is
not a general Java Properties parser. Noncritical values are not pinned by this
semantic gate, but must still use that plain syntax. The native writer's normal
output for this prepared fixture satisfies it.

World files and account lists are mutable during normal play. Their initial
hashes are evidence, not startup gates. The launcher requires the copied
`level.dat` to exist and keeps the exact world name, path and no-link checks;
it does not compare the played world to its initial bytes. Jar, library, marker,
manifest, Java and launcher-location checks remain unchanged.

It refuses to launch if any local TCP listener already uses port 25565. The
check runs again immediately before Java, but does not reserve the port. If a
competing process binds after that check, the wrapper also detects the native
`FAILED TO BIND TO PORT` message in this launch's refreshed `logs/latest.log`,
even if Java returns zero. Nonzero Java exits remain errors. Java runs in the
foreground with this manual copy as its working directory, without an installer,
Gradle, background process,
automatic commands or access to the original runtime. Missing/changed immutable
inputs and unsafe critical properties are errors; do not bypass the guards or
substitute jars to make startup succeed.

Wait for the server's `Done` message before connecting. Keep the console open.
To finish, disconnect the client and enter these commands at the server console,
without leading slashes:

```text
save-all flush
stop
```

Wait for saving and shutdown to complete before closing the console or copying
this world again. If startup or a manual check fails, retain this runtime, its
logs and the observed error. Do not overwrite the original broad evidence.

## Eclipse Client

Use Eclipse workspace `D:\PowerAdvantage\PA115` and the native Power, Steam and
Electric projects. In the Power project, select `runClientAllAdvantage` from
the Eclipse launch configurations (`runClientAllAdvantage.launch`). Use the
known Java 8 runtime above and current production classes. Eclipse classpath
preparation passed for all three projects; ECJ's separate verification outputs
are not a replacement for Eclipse's own `bin/main` output.

The combined profile contains the pinned F Steam/Electric development jars,
Base Metals `3.0.2.115021` and
the native 1.15.2 / Forge 31.2.57 runtime. Do not add packaged server jars to the
client's mods folder, duplicate Power, or select an old launch or protected save.
Starting the Eclipse client alone need not connect to any server. To inspect
this prepared Works copy, start the manual server separately, then use
Multiplayer / Direct Connect to `127.0.0.1:25565`. Connection and native menu
handshakes are part of manual acceptance, not claimed by preparation.

Stay in the Works bays and proving grounds. The existing world already contains
the 21 stations; do not run `advworks build all` to initialize it again. Rebuild
and reset commands replace bounded fixture state. The server controller's
in-memory station status is not a persisted qualification result.

The server console can inspect `advworks status` or operate one selected bay.
Clickable bay controls require operator permission. No permission was granted
during preparation. `SkyBlade1978` is not automatically made an operator; any
grant must be requested separately and entered in the running server console.

## What Remains Manual

The F automated combined-client probe passed resource/model/locale checks,
both tank-floor faces, actual gauge pixels, six native fluid cases and 25
worldless child-renderer geometry cases. Fresh F recipe exports and the complete
recipe-book/pixel rerun are pending; the earlier D three-mode pass remains
separate evidence, not an F result. All original pixel thresholds and captures
are retained. Fresh F ECJ production/test compilation and tests passed for all
three projects. See [Client Resources](CLIENT-RESOURCES.md)
for exact scope, pins and retained evidence.

Those results do not close the remaining manual checks:

- Actual menu opening, server/client handshake, normal GUI use and recipe-book
  discovery/navigation during gameplay.
- Hydroturbine orientation and spin in an actual disposable client world; its
  worldless constructor requires `World.rand`, so no geometry pass was claimed.
- Normal world-dispatched rendering, animation/interpolation, render state and
  gameplay observation of drills, beams, turbines and turrets.
- The manual observations on all 21 Works bay cards, including bounded machine
  operation and the relevant start/stop behavior.

The post-test copy is not a fresh supply set. Record what was changed during a
manual session and distinguish new observations from the retained automated
reports. The copy and any later manual changes are disposable, never new release
qualification by themselves.

## Preparation Evidence

The source had a clean save/shutdown log, no listener on its port 25592 and an
exclusively readable session lock. Its complete 190-file fingerprint was equal
before and after the whitelist copy. All 132 copied source files initially
matched, before the one documented port edit. No server was launched.

The manual directory contains:

- `manual-inputs.json`: copied whitelist, exclusions, initial world/account
  hashes, original properties hash, fixed launch inputs, source report identity
  and F manifest hash. This original manifest is unchanged.
- `manual-evidence/source-fingerprint-before.json` and
  `manual-evidence/source-fingerprint-after.json`: complete original-runtime
  fingerprints, retained separately and equal.
- `manual-evidence/source-fingerprint-final.json`: source fingerprint rechecked
  after the no-launch guard tests, still equal to the initial source.
- `manual-evidence/final-f-inputs.json`: byte-identical immutable F input manifest.
- `manual-evidence/preparation-results.json`: passing final checksum and
  `-CheckOnly` verification; the source and retained D copy are unchanged.
- `manual-evidence/restart-guards/20261026-144547-1cb04885`: 29 passing F checks,
  including real `-CheckOnly` tests for comment/reorder/store
  fixtures, all eight changed critical fields, unsafe world paths, missing keys,
  duplicates, escaping, continuations and trailing value whitespace. Wrong hash,
  missing input, relocated script and foreground/port guards remain covered.

Those tests temporarily changed only this manual copy's properties and restored
the exact initial bytes in `finally`. No Java/server was started, no world was
opened or edited, and no operator was changed. Bind-exit controls use explicitly
synthetic log fixtures; they are not live bind or server-restart evidence.

| Evidence | SHA-256 |
| --- | --- |
| F input manifest | `A9BCD5A442F6332010F9D467BD00A685AA953339C39545812B32B825B96E64AA` |
| Passed source broad report | `1E898C091E5B6FD83C259D8A64445C53493CD3E97499ECEA830043344EFDDDE9` |
| Initial before/after source fingerprint files | `84F736DE8C8F3E44A855EA3268194EB690DD2D8DDF016E2BB07B8B18690798ED` |
| Manual input manifest | `629EBED1F8607D8EC25F790011B3C7203152F32FCDEB0DE1C193D3CB01EB1FC6` |
| Initial properties, evidence only | `1FD9D2E19D225D537393AF6316B5C15E39414D754372AF3FFC5B6393DA43F646` |
| Current Start-Manual.ps1 | `CC79BF63A0FD2FFB3CBE1847DE40F4064585499840BAC14BC00AD00519C56F90` |

The separate `native115-manual-20261026-d` directory is retained unchanged,
including all 216 files and its earlier red/green restart-guard evidence.
It is not the F manual runtime. Its
`manual-evidence/restart-guards/native-properties-source-evidence.json` and
extracted source text document the same native 1.15.2 reload/save/sorting,
Java timestamp behavior and native bind-error handling used by F. Those source
checks are retained D evidence, not a claim that F was started or restarted.
Use only F's current root `Start-Manual.ps1`, not archived scripts.

The world is expected to change only when a human starts and uses this copy.
The source fingerprint is a preparation check, not a claim that a later manual
session left the copied world unchanged.
