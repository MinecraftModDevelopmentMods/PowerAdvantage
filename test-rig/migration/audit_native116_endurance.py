"""Reconstruct every endurance verdict from retained raw station checkpoints."""

import argparse
from pathlib import Path
from unittest.mock import patch

import endurance
from matrix import digest, fingerprint, write_json
from native116_endurance import audit
from native116_lifecycle import read_json


def replay(root):
    result = audit(root)
    report = read_json(root / "native116-endurance-results.json")
    if fingerprint(Path(report["source"])) != report["source_fingerprint"]:
        raise AssertionError("Protected source changed after the run")
    for name, expected in report["helper_sha256"].items():
        if digest(Path(name)) != expected:
            raise AssertionError("Recorded protocol source changed: " + name)
    definitions_path = next(Path(p) for p in report["helper_sha256"] if p.endswith("works-1.13.2.json"))
    definitions = {station["id"]: station for station in read_json(definitions_path)["stations"]}
    for name in result["profiles"]:
        runtime = root / name
        rig = endurance.Rig(name, runtime, None, None, definitions)
        setup = endurance.parse_snapshot(read_json(runtime / "advantage-works-results/E-02-checkpoint-setup.json"))
        battery_pos = rig.pos("E-02", (3, 1, 12))
        rig.static_batteries = setup[battery_pos]["data"].get("Items", [])
        ledger = read_json(runtime / "endurance-ledger.json")
        for expected in ledger:
            label = expected["label"]
            for station in endurance.CHECKED:
                document = read_json(runtime / "advantage-works-results" / f"{station}-checkpoint-{label}.json")
                rig.documents[station] = document
                rig.snapshots[station] = endurance.parse_snapshot(document)
            # Suppress the oracle's ledger writer only; never change its checks.
            with patch.object(endurance, "write_json"):
                actual = rig.batch(label)
            if actual != expected:
                raise AssertionError(name + "/" + label + ": raw checkpoint verdict differs from ledger")
        result["profiles"][name]["raw_checkpoint_replays"] = len(ledger)
        result["profiles"][name]["station_snapshots_replayed"] = len(ledger) * len(endurance.CHECKED)
    result["source_fingerprint_rechecked"] = True
    result["protocol_hashes_rechecked"] = True
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise FileExistsError(args.output)
    result = replay(args.run.resolve())
    write_json(args.output, result)
    print("PASS: raw endurance checkpoints, inventory acknowledgements, artifact pins and protected sources", flush=True)
