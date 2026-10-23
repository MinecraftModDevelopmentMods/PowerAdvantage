"""Independently reconcile lifecycle results with staged jars and raw snapshots."""
import argparse
import json
from pathlib import Path
from lifecycle import FACINGS, LifecycleRun, at, changed_target_results, fixtures, move_result, offset, parse, progress_results, require_unloaded
from endurance import require
from matrix import digest, write_json


def audit(root):
    report = json.loads((root / "lifecycle-results.json").read_text())
    require(report.get("source_fixture_unchanged") is True, "Source protection not verified")
    require(set(report["profiles"]) == {"fresh113", "original110-chained113"}, "Incomplete profile coverage")
    result = {"status": "PASS", "qualification": "PASS", "profiles": {}}
    for name, summary in report["profiles"].items():
        require(summary.get("chunk_cycles") == 5 and summary.get("full_restart") == "PASS", "Incomplete lifecycle run")
        runtime = root / name
        definition = json.loads((runtime / "lifecycle-fixture.json").read_text())
        require(definition == json.loads(json.dumps(fixtures())), "Unexpected fixture geometry")
        def snapshot(label):
            return json.loads((runtime / "advantage-works-results" / f"lifecycle-{label}.json").read_text())
        inputs = json.loads((runtime / "input-jars.json").read_text())
        require(len(inputs) == 6, "Unexpected mod count")
        for jar in inputs:
            require(digest(runtime / "mods" / jar["file"]) == jar["sha256"], "Staged jar changed")
        require({e["file"]: e["sha256"] for e in inputs} == {e["file"]: e["sha256"] for e in report["artifacts"]}, "Report pins differ")
        movement_records = {m["id"]: m for m in snapshot("commissioned")["movements"]}
        recomputed = {f["id"]: move_result(f, movement_records[f["id"]]) for f in definition if "from" in f}
        require(recomputed == summary["move_cases"], "Movement verdict differs from raw evidence")
        verdicts = [recomputed]
        chunk_events = 0
        for label in [f"chunk-cycle-{n}" for n in range(1, 4)] + ["mining-chunk-reload", "midblock-chunk"]:
            before, absent, loaded = snapshot(label + "-before"), snapshot(label + "-absent-confirmed"), snapshot(label + "-loaded")
            require_unloaded(before, absent)
            chunk_events += len(absent["chunks"])
            for a, b in zip(before["chunks"], loaded["chunks"]):
                require(a["pos"] == b["pos"] and b["loaded"] and b["loads"] > a["loads"], "No reload event")
        runner = LifecycleRun(runtime, None, None, definition)
        for label, key in (("restart-resumed", "mining"), ("mining-retained", "mining_after_chunk_reload")):
            mining = runner.mining_results(snapshot(label))
            require(mining == summary[key], "Mining result mismatch")
            verdicts.append(mining)
        for label in ("restart-resumed", "mining-retained"):
            document = snapshot(label)
            for fixture in definition:
                if fixture.get("energyType") != "steam": continue
                helper = at(document, fixture["id"], offset(fixture["to"], fixture["direction"]))
                require(helper["block"] == "steamadvantage:drillbit" and parse(helper).get("dir") == list(FACINGS).index(fixture["facing"]),
                        "Steam drill-bit orientation was not restored")
        for route, first, second in (("chunk_reload", "midblock-before-chunk", "midblock-after-chunk"),
                                     ("full_restart", "midblock-before-restart", "midblock-after-restart")):
            progress = progress_results(definition, snapshot(first), snapshot(second))
            require(progress == summary["mid_block_progress"][route],
                    "Progress verdict differs from raw evidence")
            verdicts.append(progress)
        if "save_read_guards" in summary:
            legacy = progress_results(definition, snapshot("save-read-before"), snapshot("save-read-legacy"))
            changed = changed_target_results(definition, snapshot("save-read-legacy"), snapshot("save-read-changed"))
            require(legacy == summary["save_read_guards"]["legacy_read"], "Legacy-read verdict differs from raw evidence")
            require(changed == summary["save_read_guards"]["changed_target_read"], "Changed-target verdict differs from raw evidence")
            verdicts.extend((legacy, changed))
        qualification = "PASS" if all(case["status"] == "PASS" for group in verdicts for case in group.values()) else "FAIL"
        require(summary["status"] == qualification, "Profile qualification label differs from raw case verdicts")
        logs = [p.read_text(encoding="utf-8") for p in runtime.glob("console-*.log")]
        require(not any("Unknown item slot" in log or "Could not replace slot" in log or "Ticking block entity" in log for log in logs),
                "Command error or machine crash")
        result["profiles"][name] = {"evidence_audit": "PASS", "machine_qualification": qualification,
                                   "drill_moves": len(recomputed), "real_chunk_unloads": chunk_events, "staged_jars": len(inputs),
                                   "steam_helper_orientations": 6}
        if qualification != "PASS": result["qualification"] = "FAIL"
    require(report["status"] == result["qualification"], "Overall qualification label is inconsistent")
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = audit(args.run)
    write_json(args.output, result)
    print(json.dumps(result, indent=2))
