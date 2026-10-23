"""Reconcile the one-tick legacy/new-runtime comparison from raw snapshots."""
import argparse
import json
from pathlib import Path

from connected import data, entries, operation, read
from matrix import WORLD, compare, digest, fingerprint, write_json


def first_tick(loaded, first):
    old = entries(loaded)["C-EDRILL-drill"]
    new = entries(first)["C-EDRILL-drill"]
    before, after = data(old), data(new)
    if loaded["requestedTicks"] != 0 or not loaded["held"]:
        raise AssertionError("Initial snapshot was not frozen")
    if first["requestedTicks"] != 1 or first["advancedTicks"] != 1 or not first["held"]:
        raise AssertionError("Missing one-tick observation")
    if old["objectIdentity"] != new["objectIdentity"] or old["witness"] != new["witness"]:
        raise AssertionError("Drill was replaced")
    if any(before.get(key) != after.get(key) for key in ("targetX", "targetY", "targetZ")):
        raise AssertionError("This control requires an unchanged mining target")
    return dict(status="PASS" if after["progress"] == before["progress"] + 1 else "FAIL",
                loaded_progress=before["progress"], first_tick_progress=after["progress"])


def audit(root, fixture):
    report = dict(controls={})
    definition = json.loads((fixture / "connected-fixture.json").read_text())
    for name in ("legacy112", "target113"):
        runtime = root / name
        loaded, first, continued = (read(runtime, label) for label in ("loaded", "first-tick", "continued"))
        commands = json.loads((runtime / "console-trace.commands.json").read_text())
        if commands != ["advconnected snapshot loaded", "advconnected step 1 first-tick", "advconnected step 700 continued", "save-all flush", "stop"]:
            raise AssertionError("Unexpected mutation command")
        for jar in json.loads((runtime / "input-jars.json").read_text()):
            if digest(runtime / "mods" / jar["file"]) != jar["sha256"]:
                raise AssertionError("Changed staged input")
        if (runtime / "connected-build.allow").exists() or (runtime / "connected-built.json").exists():
            raise AssertionError("Trace was allowed to rebuild")
        if json.loads((runtime / "connected-fixture.json").read_text()) != definition:
            raise AssertionError("Trace definition changed")
        if any(read(runtime, label)["buildCallsThisProcess"] != 0 for label in ("loaded", "first-tick", "continued")):
            raise AssertionError("Trace rebuilt equipment")
        report["controls"][name] = dict(first_tick=first_tick(loaded, first),
            preservation=compare(fixture / "migration-intermediate-restart-worked.json", runtime / "migration-loaded.json"),
            continued_operation=operation(first, continued, 700))
    if fingerprint(fixture / WORLD) != json.loads((root / "progress-trace.json").read_text())["source_hashes"]:
        raise AssertionError("Trace source fingerprint changed")
    legacy, target = report["controls"]["legacy112"], report["controls"]["target113"]
    reproduced = legacy["first_tick"]["first_tick_progress"] < legacy["first_tick"]["loaded_progress"]
    report["status"] = "REPRODUCED_LEGACY_FAILURE_TARGET_PASS" if reproduced and target["first_tick"]["status"] == "PASS" else "FAIL"
    if any(c[gate]["status"] != "PASS" for c in report["controls"].values() for gate in ("preservation", "continued_operation")):
        report["status"] = "FAIL"
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", required=True, type=Path)
    parser.add_argument("--fixture", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    result = audit(args.run, args.fixture)
    write_json(args.output, result)
    print(result["status"])
    raise SystemExit(result["status"] == "FAIL")
