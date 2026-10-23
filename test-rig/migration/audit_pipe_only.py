"""Reconstruct pipe-only bridge verdicts from raw snapshots and packaged inputs."""
import argparse
import json
from pathlib import Path

from lifecycle import at, counts, parse
from matrix import digest, write_json
from pipe_only_lifecycle import fixtures


def bridge_result(definition, before, absent, confirmed, loaded, resumed, cycle):
    errors = []
    if confirmed["worldTime"] - absent["worldTime"] < 40:
        errors.append("absence was shorter than forty game ticks")
    for f in definition:
        bridge = f["groups"]["bridge"][0]
        old = next(c for c in before["chunks"] if c["pos"] == bridge)
        for snapshot in (absent, confirmed):
            state = next(c for c in snapshot["chunks"] if c["pos"] == bridge)
            if state["loaded"] or state["unloads"] <= old["unloads"]:
                errors.append(f["id"] + ": bridge was not genuinely absent")
        state = next(c for c in loaded["chunks"] if c["pos"] == bridge)
        if not state["loaded"] or state["loads"] <= old["loads"]:
            errors.append(f["id"] + ": missing deliberate reload event")
        for pos in f["witnesses"][:2]:
            original = at(before, f["id"], pos)
            for snapshot in (absent, confirmed, resumed):
                endpoint = at(snapshot, f["id"], pos)
                if endpoint.get("tileIdentity") != original.get("tileIdentity"):
                    errors.append(f["id"] + ": retained endpoint replaced")
                if endpoint.get("block") != original.get("block") or endpoint.get("state") != original.get("state"):
                    errors.append(f["id"] + ": retained endpoint changed")
        for pos in f["witnesses"]:
            original, restored = at(before, f["id"], pos), at(resumed, f["id"], pos)
            if restored.get("block") != original.get("block") or restored.get("state") != original.get("state"):
                errors.append(f["id"] + ": bridge block/state changed")
        endpoint = at(resumed, f["id"], f["witnesses"][1])
        if f["id"] == "C-FLUID":
            if parse(endpoint).get("Tank", {}).get("Amount") != (cycle + 1) * 1000:
                errors.append("fluid loss or duplication")
            for snapshot in (absent, confirmed):
                if parse(at(snapshot, f["id"], f["witnesses"][1])).get("Tank") != parse(at(before, f["id"], f["witnesses"][1])).get("Tank"):
                    errors.append("fluid changed while disconnected")
        elif counts([endpoint])["minecraft:iron_ingot"] != 4 or counts([endpoint])["minecraft:iron_ore"] != 0:
            errors.append(f["id"] + ": processing did not resume")
    return {"status": "FAIL" if errors else "PASS", "failures": sorted(set(errors))}


def audit(root):
    definition = fixtures()
    result = {"status": "PASS", "cycles": [], "failures": []}
    if json.loads((root / "lifecycle-fixture.json").read_text()) != definition:
        result["failures"].append("fixture geometry differs")
    for cycle in range(3):
        def read(label):
            return json.loads((root / "advantage-works-results" / f"lifecycle-bridge-{cycle}-{label}.json").read_text())
        result["cycles"].append(bridge_result(definition, *(read(label) for label in
                ("before", "isolated", "confirmed", "loaded", "resumed")), cycle))
    jars = json.loads((root / "input-jars.json").read_text())
    if len(jars) != 6 or any(digest(root / "mods" / jar["file"]) != jar["sha256"] for jar in jars):
        result["failures"].append("staged artifact hashes differ")
    log = (root / "console-pipe-only.log").read_text(encoding="utf-8")
    for cycle in range(3):
        for operation, label in (("isolated", "isolated"), ("offer-blocked", "blocked-offer"), ("offer", "reconnected-offer")):
            if f"ADVLIFECYCLE COMPLETE {operation} bridge-{cycle}-{label}" not in log:
                result["failures"].append("missing successful real network assertion")
    if "Stopping server" not in log or "Saving chunks for level" not in log:
        result["failures"].append("missing graceful shutdown")
    result["log_errors"] = [line for line in log.splitlines() if "/ERROR]" in line or "/FATAL]" in line]
    if "/ERROR]" in log or "Can't keep up!" in log:
        result["failures"].append("server errors or timing warnings")
    if result["failures"] or any(c["status"] != "PASS" for c in result["cycles"]): result["status"] = "FAIL"
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = audit(args.run)
    write_json(args.output, result)
    print(json.dumps(result, indent=2))
    if result["status"] != "PASS": raise SystemExit(1)
