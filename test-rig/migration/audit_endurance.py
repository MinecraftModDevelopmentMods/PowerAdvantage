"""Independently check native-feed endurance reports, commands and staged jars."""

import argparse
import json
from pathlib import Path

from matrix import digest, write_json


def expected_inventory_acknowledgements(batches):
    # Five machine inventories, four neighbouring chests, seven destinations,
    # four source chests; initial conveyor clearing also preserves filter slots.
    ordinary = 7 + 6 + 10 + 9 + 2 + 4 * 27 + 7 * 27 + 4 * 27
    return batches * ordinary + 9, ordinary


def audit(root):
    report = json.loads((root / "endurance-results.json").read_text())
    assert report["status"] == "PASS", "Not a completed endurance pass"
    assert report["duration_seconds"] >= 1800 and report["interval_seconds"] == 60
    assert report.get("feeding_protocol", "").startswith("IInventory replaceitem"), "NBT-fed runs are diagnostics only"
    assert report["source_fixture_unchanged"] is True
    result = {"status": "PASS", "source_report": str(root / "endurance-results.json"), "profiles": {}}
    errors = ("Unknown slot", "Could not replace", "Unknown or incomplete command", "Incorrect argument",
              "Unknown item", "There is no container")
    for name, profile in report["profiles"].items():
        assert profile["status"] == "PASS" and profile["restart"] == "PASS" and profile["resumed_processing"] == "PASS"
        assert profile["elapsed_seconds"] >= report["duration_seconds"]
        assert profile["game_ticks"] >= report["duration_seconds"] * 18
        runtime = root / name
        initial, restarted = expected_inventory_acknowledgements(profile["endurance_batches"])
        counts = {}
        for filename, expected in (("console-endurance.log", initial), ("console-endurance-restart.log", restarted)):
            content = (runtime / filename).read_text(encoding="utf-8")
            assert not any(error in content for error in errors), f"Failed commands in {name}/{filename}"
            assert content.count("Replaced a slot at") == expected, f"Missing inventory writes in {name}/{filename}"
            if "restart" in filename:
                assert not any(f"Started {station} " in content for station in ("S-04", "E-03", "E-02", "S-02", "E-01")), \
                    "Power stations were restarted instead of testing loaded networks"
                assert "Advantage Works built" not in content and "Reset 1 station" not in content
            counts[filename] = expected
        pins = {artifact["file"]: artifact["sha256"] for artifact in report["artifacts"]}
        jars = sorted((runtime / "mods").glob("*.jar"))
        assert {jar.name for jar in jars} == set(pins), f"Staged jar identities changed in {name}"
        assert all(digest(jar) == pins[jar.name] for jar in jars), f"Staged jar hashes changed in {name}"
        ledger = json.loads((runtime / "endurance-ledger.json").read_text())
        batches = [entry for entry in ledger if entry["label"].startswith("cycle-")]
        expected_batches = report["duration_seconds"] // report["interval_seconds"]
        assert len(batches) == profile["endurance_batches"] == expected_batches
        assert {entry["label"] for entry in batches} == {f"cycle-{index:03}" for index in range(1, expected_batches + 1)}
        assert [entry["label"] for entry in ledger[-3:]] == ["before-restart", "after-restart", "restart-resumed"]
        result["profiles"][name] = {"status": "PASS", "verified_inventory_commands": counts,
                                   "staged_jars": len(jars), "distinct_endurance_batches": len(batches)}
    assert set(result["profiles"]) == {"fresh113", "original110-chained113"}
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = audit(args.run)
    write_json(args.output, result)
    print(json.dumps(result, indent=2))
