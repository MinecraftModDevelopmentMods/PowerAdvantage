# Walkable Works Checks

The current manual-testing candidate is `i`; see the hook-safe rerun below.
The earlier `h` results are retained as history, and its viewer is now guarded
against startup because of the subsequently confirmed persistence-hook race.

## What This Checks

The broad sweep operates all 21 handbook bays in a new disposable native 1.14.4
world, stops the server process, and checks the same world after another boot.
It is a useful commissioning check, not proof that every machine behaviour or
every item in the handbook has been exercised.

The original station definitions remain unchanged. The old failed broad runs
remain available as evidence; their verdicts are not edited.

## Drill Assertion Repair

`Q-RIG-001` was an assertion defect: two connected drills can move into their
declared track cells, but the old assertions still demanded a drill at the
original coordinate. Simply removing those checks would hide lost drills.

The repaired server assertion accepts exactly one correctly facing drill in
either its original cell or its one declared rear-track cell. It rejects a
missing drill, two drills in those cells, a wrong facing and movement to an
undeclared cell. It does not search arbitrarily for something that will pass.

The separate `migration/native114_broad.py` runner adds stricter phase checks:

- Before activation, all twelve drills must be at their exact original cells,
  and every declared rear track must still be present.
- Commissioning gives each drill a distinct persistent tile marker and seven
  tagged diamonds. These are finite witnesses, not a refill during testing.
- After operation, all twelve tile identities, facings, coordinates and finite
  inventory totals must match. Stock may move to the existing rear chests, but
  it may not disappear or duplicate.
- The Steam down-facing and Electric up-facing reproduction lanes must actually
  move. A run in which everything stays still does not clear `Q-RIG-001`.
- The old cell must no longer contain a second copy of the drill. Its temporary
  conduit is not a stable post-operation witness: a rearward-moving Steam drill
  can subsequently drill that cell and put a helper bit there.
- The second process performs no build, reset, start, data merge, replacement
  or refill. Position, persistent identity, facing, stock and energy bits must
  match the stopped first-process state exactly.

Every original assertion is retained. Missing optional integrations are explicit
skips, not fabricated passes. The runner verifies actual loaded mod identities
and versions against its frozen manifest; the decoupled run excludes Base Metals
and Mineralogy but retains the required OreSpawn dependency.

## Running It

Use Java 8 for the packaged server, Python 3.11 or later with the existing
`test-rig/migration/requirements.txt`, a hash-frozen native target manifest and
two free loopback ports. Run from the PowerAdvantage checkout:

```text
python test-rig/migration/native114_broad.py --target-manifest <new-frozen-manifest.json> --java8 <java8.exe> --output test-rig/build/<new-run-name> --port 25588 --run-seconds 8
```

The output path must be new and inside `test-rig/build`. The runner stages only
the manifest's jars, not arbitrary mods from the installed server seed. It
never opens a protected source world. Use `--profile packaged-current` or
`--profile packaged-decoupled` for a single-profile rerun.

Fresh works use the established deterministic works seed, disabled structures,
Peaceful difficulty and no incidental mob spawning. The migration staging helper's
tile freeze is explicitly disabled for this operating campaign. The original
errors caused by omitting those settings remain recorded, not waived by the
log checker. Hostile-target manual trials need an attended proving ground and
an appropriate difficulty; they are not exercised by this quiet commissioning
configuration.

Java negative controls cover initial and moved witnesses in all twelve cases,
duplicates, missing/wrong-facing/off-track drills and undeclared track cells.
Python negative controls also cover premature movement, stock loss/duplication,
lost tile identity, coordinate drift, restart energy loss, omitted assertions,
omitted bays and a mistakenly coupled mod set.

## Limits

Each bay's manual checks remain `MANUAL_REQUIRED` even when every automated
assertion passes. These include in-world GUI operation, rendering, sound,
controls, weapons and behaviours which the broad layout only represents
structurally. Dedicated bounded lifecycle, partial-network, migration and
endurance campaigns remain separate evidence. A static registry or tile check
must not be described as an operating test of the entire machine.

Foreign Forge Energy/fluid capabilities, portable-tank break/drop state and
locked-chest automation are separate interoperability checks, not silently
covered by this sweep. The broader campaign records their results separately.

## Local Candidate Result

On 25 October 2026, both profiles passed on the frozen `h` candidate, with no
runtime errors and a complete server-process restart for each world:

| Profile | Assertions Before Restart | Assertions After Restart | Drill Witnesses |
| --- | --- | --- | --- |
| `packaged-current` | 96 pass, 3 Mineralogy skips | 96 pass, 3 Mineralogy skips | 12 preserved; 2 actual track moves |
| `packaged-decoupled` | 95 pass, 4 integration skips | 95 pass, 4 integration skips | 12 preserved; 2 actual track moves |

The extra decoupled skip is the Base Metals steel-plate integration. Each world
retains all 84 tagged inventory items and exactly the same twelve saved energy
values after restart. Two Electric buffers retain a nonzero value of 20; the
Steam buffers have naturally decayed to zero by the stopped checkpoint. Charged
Steam retention remains a separate bounded lifecycle test, not a claim made by
these zero-buffer witnesses. Every bay still reports `MANUAL_REQUIRED` for its
manual observations.

The three Java oracle tests and fifteen Python regression tests pass. The old
fixed-position implementation fails two of the Java tests before the repair.
`Q-RIG-001` is resolved for this native broad campaign without changing the
historical station specification or removing any of its assertions.

Local evidence, intentionally excluded from Git:

```text
test-rig/build/native114-inputs-20261025-fixed-h-archived.json
test-rig/build/native114-broader-works-20261025-layout-fixed-h/native114-broad-results.json
test-rig/build/native114-broader-works-20261025-layout-diagnosis.json
```

The earlier `first`, `feedback-fixed`, `operation-fixed` and `fixed-h` run folders
are retained. They record, respectively, the feedback-text mismatch, omitted
operating permission, incorrect temporary-conduit expectation, and the generated
village/POI issue. Their failed verdicts were not rewritten. The POI diagnosis
records the real village bounds and the source file hashes.

| Candidate Artifact | SHA-256 |
| --- | --- |
| Power `2.4.5.114041` | `15BAF794507BED84C879358CADD1E4C6F8D0A6B88179597391A675BE3315B834` |
| Steam `2.2.6.114041` | `444872113E13D806D4520CCBD9B3230C931353D43875BAE57EB9B5FC86614669` |
| Electric `2.2.6.114041` | `5BE41447AF5AA3AF60C145B3A862BC42802C620C637D1E65810B350A6A9AFBA9` |
| Test harness `1.1.0.114041` | `64BC8390743999C5C2823EDB9E8D25CC557DC87E679ED1F1104A01AA882ECDD9` |

For that earlier run, a manual viewer was prepared under `test-rig/build/manual114-20261025`,
using those pinned inputs and an unchanged copy of the stopped passing current
world. It was not started and is now superseded and guarded. Its local README and
`manual-provenance.json` contain the start command, localhost binding, offline
operator identity and source/save fingerprint checks. No user or Eclipse save
was copied. These are local, uncommitted candidates, not published releases.

## Hook-Safe Candidate Rerun

The later candidate `i` defers persistence-hook registration to common setup,
avoiding the confirmed unsafe Forge collection update during parallel mod
construction. Both broad profiles were rerun from fresh worlds with the same
oracles, station definitions and full-process restart protocol.

| Profile | Before Restart | After Restart | Drill State |
| --- | --- | --- | --- |
| `packaged-current` | 96 pass, 3 expected skips | 96 pass, 3 expected skips | 12 exact; 2 track moves |
| `packaged-decoupled` | 95 pass, 4 expected skips | 95 pass, 4 expected skips | 12 exact; 2 track moves |

Both runs passed without runtime errors. Inventory witnesses and all saved
energy bits match after restart. All 21 manual-check lists remain open.
The earlier results and worlds were not overwritten.

```text
Manifest: test-rig/build/native114-inputs-20261025-hook-i-archived.json
Results:  test-rig/build/native114-broader-works-20261025-hook-i/native114-broad-results.json
Power release SHA-256: 33F57A1401A13597DCA53F4E49EAEDDD2D5CB3C3F406D5200902D5BA9CB41352
Power deobf SHA-256:   8D8831D54B700FBFB2B9C43EE3D8D70D2B7E57185A4DBDF12202C47D5E387B39
Tested harness:       64BC8390743999C5C2823EDB9E8D25CC557DC87E679ED1F1104A01AA882ECDD9
```

The ten source profiles now pin the updated Power release and the actual
working harness build (`09DE1F38...`, deobf `690C49EA...`). All ten pass input
preflight, which checks files, hashes and mod identities without opening worlds.
That is not a claim that every profile was runtime-qualified with that newer
fixture harness. This broad rerun and the new viewer use the accepted frozen
`64BC8390...` harness from the unchanged `i` manifest.

The new, not-started viewer is `test-rig/build/manual114-20261025-i`. Its README,
checked start script and provenance record identify the tested inputs and the
unchanged copied save. The previous viewer's original start script is preserved
as text, and its replacement fails immediately with a `SUPERSEDED` notice.
The old world, jars and original provenance remain unchanged. No active viewer
was found or stopped, and no production code or launch account was changed.
