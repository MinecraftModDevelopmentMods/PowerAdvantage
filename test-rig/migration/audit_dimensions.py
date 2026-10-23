"""Independently replay raw dimension snapshots, item NBT and lifecycle evidence."""

import argparse
import json
from pathlib import Path

from actors import backup_state, freeze_result
from dimensions import WORLD, capture, runtime_errors, strict_expected, survival_expected, tick_result, verdict
from matrix import digest, fingerprint, write_json


def audit(root):
    published = json.loads((root / "dimension-results.json").read_text())
    scope = json.loads((root / "scope.json").read_text())
    native = scope.get("native_load", False)
    findings = []
    replayed = {}
    for route, results in published.items():
        district, label = route.split("/")
        name = label.rsplit("-", 1)[0]
        source = root / district / name
        target = root / district / (label + "-113")
        baseline = capture(source, "source", source / "dimension-evidence/source")
        if fingerprint(source / WORLD) != json.loads((source / "fixture-hashes.json").read_text()):
            findings.append("Generated source fixture changed: " + route)
        runtimes = [source, target]
        stages = {}
        if label.endswith("-chained"):
            middle = root / district / (label + "-112")
            runtimes.append(middle)
            for phase in ("intermediate", "intermediate-restart"):
                state = capture(middle, phase, middle / "dimension-evidence" / phase)
                stages[phase] = verdict(survival_expected(baseline) if native else strict_expected(baseline), state)
                if native:
                    stages[phase + "-ticks"] = tick_result(json.loads((middle / f"dimensions-{phase}-release.json").read_text()),
                                                          json.loads((middle / f"dimensions-{phase}.json").read_text()))
                else:
                    stages[phase + "-frozen"] = freeze_result(json.loads((middle / f"dimensions-{phase}.json").read_text()))
        states = {phase: capture(target, phase, target / "dimension-evidence" / phase)
                  for phase in ("converted", "adapter", "restarted", "runtime", "runtime-restart")}
        expectation = survival_expected(baseline) if native else strict_expected(baseline, adapter=False)
        expectation.pop("adapter", None)
        expectation.pop("playerWorldDimension", None)
        stages["conversion"] = verdict(expectation, states["converted"])
        stages["adapter"] = verdict(survival_expected(baseline) if native else strict_expected(baseline), states["adapter"])
        stages["restart"] = verdict(survival_expected(baseline) if native else strict_expected(baseline), states["restarted"])
        stages["idempotence"] = verdict(survival_expected(states["adapter"]) if native else strict_expected(states["adapter"]), states["restarted"])
        stages["runtime"] = verdict(survival_expected(baseline), states["runtime"])
        stages["runtime-restart"] = verdict(survival_expected(baseline), states["runtime-restart"])
        if native:
            stages["conversion-ticks"] = tick_result(json.loads((target / "dimensions-conversion-release.json").read_text()),
                                                    json.loads((target / "dimensions-converted.json").read_text()))
        else:
            for key, phase in (("conversion-frozen", "converted"), ("restart-frozen", "restarted")):
                stages[key] = freeze_result(json.loads((target / f"dimensions-{phase}.json").read_text()))
        for key, start, end in (("runtime-ticks", "released", "runtime"),
                                ("restart-ticks", "restart-released", "runtime-restart")):
            stages[key] = tick_result(json.loads((target / f"dimensions-{start}.json").read_text()),
                                     json.loads((target / f"dimensions-{end}.json").read_text()))
        for key, value in stages.items():
            if value != results[key]:
                findings.append("Oracle replay disagrees: " + route + "/" + key)
        predecessor = runtimes[-1] if label.endswith("-chained") else source
        player_uuid = json.loads((source / "dimension-fixture.json").read_text())["playerUuid"]
        affected_files = [Path("playerdata") / (player_uuid + ".dat")]
        if "level.dat.poweradvantage-legacy-backup" in results["backup"]["files"]:
            affected_files.append(Path("level.dat"))
        for relative in affected_files:
            backup = target / WORLD / relative.with_name(relative.name + ".poweradvantage-legacy-backup")
            if not backup.is_file() or digest(backup) != digest(predecessor / WORLD / relative):
                findings.append("Backup does not match predecessor bytes: " + route + "/" + str(relative))
        if backup_state(target) != results["backup"]["files"]:
            findings.append("Backups changed: " + route)
        diagnostics = []
        for runtime in runtimes:
            for jar in json.loads((runtime / "input-jars.json").read_text()):
                if digest(runtime / "mods" / jar["file"]) != jar["sha256"]:
                    findings.append("Staged artifact changed: " + route + "/" + jar["file"])
            for log in runtime.glob("console-*.log"):
                content = log.read_text(errors="replace")
                if "ADVDIMENSIONS COMPLETE" not in content or "Stopping server" not in content:
                    findings.append("Incomplete server phase: " + str(log))
                findings.extend("Native runtime failure: " + str(log) + ": " + line for line in runtime_errors(content))
                diagnostics.extend({"log": str(log.relative_to(root)), "line": line}
                                   for line in content.splitlines() if "/ERROR]" in line or "/FATAL]" in line
                                   or "Exception loading entity" in line)
        replayed[route] = {"stages": stages, "diagnostics": diagnostics}
    if scope["routes"] != len(published) or scope["immutable_sources"] != "PASS":
        findings.append("Missing route/source preservation evidence")
    if "expected_route_ids" in scope and set(scope["expected_route_ids"]) != set(published):
        findings.append("Route coverage differs from requested matrix")
    return {"status": "FAIL" if findings else "PASS", "meaning": "Evidence integrity, not client acceptance",
            "findings": findings, "routes": replayed}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = audit(args.run)
    write_json(args.output, result)
    print(result["status"] + ": " + str(len(result["routes"])) + " routes; " + str(result["findings"]))
    raise SystemExit(result["status"] != "PASS")
