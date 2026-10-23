"""Replay bag-slot and nested-item player fixtures from retained binary NBT."""
import argparse
import json
from pathlib import Path

from actors import WORLD, capture
from matrix import digest, fingerprint, write_json
from player_layout import player_verdict


def audit(root):
    published = json.loads((root / "layout-results.json").read_text())
    issues, routes = [], {}
    for label, verdict in published.items():
        source = root / label.rsplit("-", 1)[0]
        target = root / (label + "-113")
        baseline = capture(source, "source", source / "actor-evidence/source")
        if fingerprint(source / WORLD) != json.loads((source / "fixture-hashes.json").read_text()): issues.append("Source fixture changed: " + label)
        slots = [entry["Slot"] for entry in baseline["file"]["Inventory"]]
        ender_slots = [entry["Slot"] for entry in baseline["file"]["EnderItems"]]
        if not all(slot in slots for slot in (9, 18, 27, 35, -106, 100, 101, 102, 103)): issues.append("Missing inventory boundary: " + label)
        if 26 not in ender_slots: issues.append("Missing last Ender Chest slot: " + label)
        musket = next(s for s in baseline["file"]["Inventory"] if s["id"] == "steamadvantage:musket")
        if len(musket["tag"]["Nested"]["Items"]) != 3: issues.append("Missing recursive item witnesses: " + label)
        replayed = {phase: player_verdict(baseline, capture(target, phase, target / "actor-evidence" / phase))
                    for phase in ("converted", "restarted")}
        if "intermediate" in verdict:
            middle = root / (label + "-112")
            replayed["intermediate"] = player_verdict(baseline, capture(middle, "intermediate", middle / "actor-evidence/intermediate"))
        if replayed != verdict: issues.append("Raw layout replay disagrees: " + label)
        for runtime in [source, target] + ([root / (label + "-112")] if "intermediate" in verdict else []):
            for jar in json.loads((runtime / "input-jars.json").read_text()):
                if digest(runtime / "mods" / jar["file"]) != jar["sha256"]: issues.append("Staged artifact changed: " + label)
        routes[label] = replayed
    return {"status": "FAIL" if issues else "PASS", "findings": issues, "routes": routes}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = audit(args.run)
    write_json(args.output, result)
    print(result["status"] + ": " + str(len(result["routes"])) + " routes; " + str(result["findings"]))
    raise SystemExit(result["status"] != "PASS")
