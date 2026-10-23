"""Collect existing matrix evidence without launching or changing any world."""

import argparse
import json
from pathlib import Path
from matrix import WORLD, digest, fingerprint, write_json


def audit(args):
    report = {"routes": {}, "immutable_fixtures": [], "staged_jar_manifests": 0,
              "operation": {}, "runtime_log_findings": []}
    for root in args.results:
        results = json.loads((root / "matrix-results.json").read_text())
        for label, stages in results.items():
            for stage, result in stages.items():
                if result["status"] != "PASS": raise AssertionError(f"Failed {root.name}/{label}/{stage}")
            # The first local run predated identification of the actual Cyano artifacts.
            corrected = label.replace("original110", "early110") if root.name == "full-20261005-r3" else label
            if corrected in report["routes"]: raise AssertionError(f"Duplicate route: {corrected}")
            report["routes"][corrected] = {"source_report": str(root / "matrix-results.json"), "stages": stages}
        for manifest in root.glob("*/input-jars.json"):
            for entry in json.loads(manifest.read_text()):
                staged = manifest.parent / "mods" / entry["file"]
                if digest(staged) != entry["sha256"]: raise AssertionError(f"Staged jar changed: {staged}")
            report["staged_jar_manifests"] += 1
        for log in root.glob("*-113/console-*.log"):
            errors = [line for line in log.read_text(encoding="utf-8").splitlines() if "/ERROR]" in line or "/FATAL]" in line]
            if errors: report["runtime_log_findings"].append({"log": str(log), "errors": errors})
    for root in args.fixtures:
        for hashes in root.glob("*/fixture-hashes.json"):
            expected = json.loads(hashes.read_text())
            if fingerprint(hashes.parent / WORLD) != expected:
                raise AssertionError(f"Immutable fixture changed: {hashes.parent}")
            report["immutable_fixtures"].append({"path": str(hashes.parent), "files": len(expected), "status": "PASS"})
    for phase in ("operation", "operation-restart"):
        results = json.loads((args.operation / (phase + "-results.json")).read_text())
        if not results or any(status not in ("PASS", "MANUAL_REQUIRED") for status in results.values()):
            raise AssertionError(f"Operation assertion failed: {phase}")
        report["operation"][phase] = results
    report["status"] = "STATE_MATRIX_PASS_WITH_REMAINING_RELEASE_GATES"
    write_json(args.output, report)
    print(f"PASS: {len(report['routes'])} routes, {len(report['immutable_fixtures'])} unchanged source fixtures, "
          f"{report['staged_jar_manifests']} verified staged-jar manifests")
    print(f"Report: {args.output}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--results", nargs="+", type=Path, required=True)
    parser.add_argument("--fixtures", nargs="+", type=Path, required=True)
    parser.add_argument("--operation", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    audit(parser.parse_args())
