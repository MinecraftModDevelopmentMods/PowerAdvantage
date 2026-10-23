"""Replay native death/respawn NBT and the actual persisted player files."""

import argparse
import json
from pathlib import Path

import nbtlib

from actors import enchantments, semantic
from dimensions import PLAYER_STATE, exact_item_differences, runtime_errors
from matrix import differences, digest, fingerprint, parse_nbt, write_json
from respawn import respawn_verdict, spawn_reference


def audit(root):
    published = json.loads((root / "respawn-results.json").read_text())
    findings = []
    results = {}
    audited = set()
    for label, expected in published.items():
        district, profile, case = label.split("/")
        runtime = root / district / profile
        record = json.loads((runtime / f"dimensions-{case}.json").read_text())
        registry = enchantments(runtime)
        actual = respawn_verdict(record, registry, spawn_reference(runtime / "respawn-evidence" / case / "level.dat"))
        saved = semantic(nbtlib.load(runtime / "respawn-evidence" / case / "player.dat").unpack(), PLAYER_STATE, registry)
        after = semantic(parse_nbt(record["respawn"]["after"]), PLAYER_STATE, registry)
        saved_issues = differences(after, saved, "saved-player")
        saved_issues.extend(exact_item_differences(after, saved, "saved-player"))
        actual["differences"].extend(saved_issues)
        if saved_issues:
            actual["status"] = "FAIL"
        if actual != expected:
            findings.append("Oracle replay disagrees: " + label)
        results[label] = actual
        if runtime not in audited:
            audited.add(runtime)
            for jar in json.loads((runtime / "input-jars.json").read_text()):
                if digest(runtime / "mods" / jar["file"]) != jar["sha256"]:
                    findings.append("Staged artifact changed: " + str(runtime) + "/" + jar["file"])
            log = (runtime / "console-respawn.log").read_text(errors="replace")
            if "Stopping server" not in log:
                findings.append("Incomplete shutdown: " + str(runtime))
            findings.extend("Native runtime failure: " + str(runtime) + ": " + line for line in runtime_errors(log))
            for mode in ("keep", "drop"):
                for bed in ("forced", "bed", "missing"):
                    if "ADVDIMENSIONS COMPLETE " + mode + "-" + bed not in log:
                        findings.append("Missing case completion: " + str(runtime) + "/" + mode + "-" + bed)
    scope = json.loads((root / "scope.json").read_text())
    if scope["cases"] != len(published) or scope["source_fixtures"] != "UNCHANGED":
        findings.append("Missing case/source preservation evidence")
    if "expected_case_ids" in scope and set(scope["expected_case_ids"]) != set(published):
        findings.append("Case coverage differs from requested matrix")
    sources = root / "source-fingerprints.json"
    if sources.is_file():
        for label, source in json.loads(sources.read_text()).items():
            if fingerprint(Path(source["path"])) != source["hashes"]:
                findings.append("Source fixture changed: " + label)
    return {"status": "FAIL" if findings else "PASS", "meaning": "Evidence integrity, not client death-screen acceptance",
            "findings": findings, "cases": results}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = audit(args.run)
    write_json(args.output, result)
    print(result["status"] + ": " + str(len(result["cases"])) + " cases; " + str(result["findings"]))
    raise SystemExit(result["status"] != "PASS")
