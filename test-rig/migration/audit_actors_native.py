"""Independently replay native actor controls from NBT and tick counters."""
import argparse
import json
from pathlib import Path

from actors import WORLD, capture, compare, tick_result
from matrix import digest, fingerprint, write_json


def audit(root, fixtures):
    published = json.loads((root / "native-results.json").read_text())
    issues, routes = [], {}
    for label, verdict in published.items():
        source = fixtures / label.rsplit("-", 1)[0]
        target = root / label
        if fingerprint(source / WORLD) != json.loads((source / "fixture-hashes.json").read_text()):
            issues.append("Actor source fixture changed: " + label)
        baseline = capture(source, "source", source / "actor-evidence/source")
        expected = {"file": baseline["file"], "entities": {key: {k: v for k, v in value.items() if k not in ("Pos", "Motion", "Rotation")}
                    for key, value in baseline["entities"].items()}}
        for runtime in [target] + ([root / (label + "-112")] if label.endswith("-chained") else []):
            for jar in json.loads((runtime / "input-jars.json").read_text()):
                if digest(runtime / "mods" / jar["file"]) != jar["sha256"]:
                    issues.append("Staged artifact changed: " + str(runtime / jar["file"]))
        replayed = {}
        for phase in ("native", "native-restart"):
            state = capture(target, phase, target / "actor-evidence" / phase)
            replayed[phase] = compare(expected, state)
            release = json.loads((target / f"actors-{phase}-release.json").read_text())
            final = json.loads((target / f"actors-{phase}.json").read_text())
            replayed[phase]["timing"] = tick_result(release, final)
            if replayed[phase]["timing"]["status"] != "PASS": replayed[phase]["status"] = "FAIL"
            if replayed[phase] != verdict[phase]: issues.append("Raw native replay disagrees: " + label + "/" + phase)
            log = (target / f"console-{phase}.log").read_text(errors="replace")
            if "ADVACTORS COMPLETE" not in log or "Stopping server" not in log: issues.append("Incomplete native phase: " + label)
        routes[label] = replayed
    return {"status": "FAIL" if issues else "PASS", "meaning": "Evidence integrity, not gameplay acceptance",
            "findings": issues, "routes": routes}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", type=Path, required=True)
    parser.add_argument("--fixtures", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = audit(args.run, args.fixtures)
    write_json(args.output, result)
    print(result["status"] + ": " + str(len(result["routes"])) + " routes; " + str(result["findings"]))
    raise SystemExit(result["status"] != "PASS")
