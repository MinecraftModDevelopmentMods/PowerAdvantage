"""Replay raw actor snapshots and saved player files without running Minecraft."""
import argparse
import json
from pathlib import Path

from actors import WORLD, backup_state, capture, compare, tick_result, freeze_result
from matrix import digest, fingerprint, write_json


def audit(root, selected=None):
    published = json.loads((root / "actor-results.json").read_text())
    if selected and not set(selected).issubset(published):
        raise ValueError(f"Unavailable audit routes: {set(selected) - set(published)}")
    findings = []
    routes = {}
    for label, verdict in published.items():
        if selected and label not in selected: continue
        name = label.rsplit("-", 1)[0]
        source = root / name
        target = root / (label + "-113")
        baseline = capture(source, "source", source / "actor-evidence/source")
        baseline["embedded"] = baseline["file"]
        if fingerprint(source / WORLD) != json.loads((source / "fixture-hashes.json").read_text()):
            findings.append("Generated fixture changed: " + name)
        for runtime in [source, target] + ([root / (label + "-112")] if label.endswith("-chained") else []):
            for jar in json.loads((runtime / "input-jars.json").read_text()):
                if digest(runtime / "mods" / jar["file"]) != jar["sha256"]:
                    findings.append("Staged artifact changed: " + jar["file"])
        replayed = {}
        states = {phase: capture(target, phase, target / "actor-evidence" / phase)
                  for phase in ("converted", "adapter", "restarted", "runtime", "runtime-restart")}
        for stage, phase in (("frozen_conversion", "converted"), ("frozen_adapter", "adapter"), ("frozen_restart", "restarted")):
            frozen = freeze_result(json.loads((target / f"actors-{phase}.json").read_text()))
            if frozen != verdict[stage] or frozen["status"] != "PASS": findings.append("Pre-tick freeze not proven: " + label + "/" + phase)
        replayed["conversion"] = compare({k: v for k, v in baseline.items() if k != "adapter"}, states["converted"])
        for stage, phase in (("player_adapter", "adapter"), ("restart", "restarted")):
            replayed[stage] = compare({k: v for k, v in baseline.items() if k != "embedded"}, states[phase])
        replayed["idempotence"] = compare(states["adapter"], states["restarted"])
        survival = {"file": baseline["file"], "entities": {key: {k: v for k, v in value.items() if k not in ("Pos", "Motion", "Rotation")}
                    for key, value in baseline["entities"].items()}}
        replayed["runtime_survival"] = compare(survival, states["runtime"])
        replayed["runtime_restart"] = compare(survival, states["runtime-restart"])
        if "intermediate" in verdict:
            middle = root / (label + "-112")
            state = capture(middle, "intermediate", middle / "actor-evidence/intermediate")
            if "frozen_intermediate" in verdict:
                frozen = freeze_result(json.loads((middle / "actors-intermediate.json").read_text()))
                if frozen != verdict["frozen_intermediate"] or frozen["status"] != "PASS":
                    findings.append("Intermediate pre-tick freeze not proven: " + label)
            state["embedded"] = state["file"]
            replayed["intermediate"] = compare(baseline, state)
            if "intermediate_restart" in verdict:
                state = capture(middle, "intermediate-restart", middle / "actor-evidence/intermediate-restart")
                state["embedded"] = state["file"]
                replayed["intermediate_restart"] = compare(baseline, state)
                frozen = freeze_result(json.loads((middle / "actors-intermediate-restart.json").read_text()))
                if frozen != verdict["frozen_intermediate_restart"] or frozen["status"] != "PASS":
                    findings.append("Intermediate restart pre-tick freeze not proven: " + label)
        for stage, value in replayed.items():
            if value != verdict[stage]: findings.append("Oracle replay disagrees: " + label + "/" + stage)
        release = json.loads((target / "actors-runtime-release.json").read_text())
        ticked = json.loads((target / "actors-runtime.json").read_text())
        timing = tick_result(release, ticked)
        if timing != verdict["runtime_ticks"] or timing["status"] != "PASS": findings.append("Insufficient native entity ticks: " + label)
        if backup_state(target) != verdict["backup"]["files"]: findings.append("Migration backups changed: " + label)
        predecessor = root / (label + "-112") if label.endswith("-chained") else source
        definition = json.loads((source / "actor-fixture.json").read_text())
        player_relative = Path("playerdata") / (definition["playerUuid"] + ".dat")
        for relative in (Path("level.dat"), player_relative):
            backup = target / WORLD / relative.with_name(relative.name + ".poweradvantage-legacy-backup")
            if not backup.is_file() or digest(backup) != digest(predecessor / WORLD / relative):
                findings.append("Backup does not match original predecessor bytes: " + label + "/" + str(relative))
        errors = []
        for log in target.glob("console-*.log"):
            text = log.read_text(errors="replace")
            if "ADVACTORS COMPLETE" not in text or "Stopping server" not in text: findings.append("Incomplete phase: " + str(log))
            errors.extend({"log": log.name, "line": line} for line in text.splitlines() if "/ERROR]" in line or "/FATAL]" in line
                          or "Exception loading entity" in line)
        routes[label] = {"replayed_stages": replayed, "timing": timing, "diagnostics": errors}
    return {"status": "FAIL" if findings else "PASS", "meaning": "Evidence integrity, not gameplay acceptance",
            "findings": findings, "routes": routes}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--routes", nargs="+", help="Audit only the explicitly listed completed routes")
    args = parser.parse_args()
    result = audit(args.run, args.routes)
    write_json(args.output, result)
    print(result["status"] + ": " + str(len(result["routes"])) + " routes; " + str(result["findings"]))
    raise SystemExit(result["status"] != "PASS")
