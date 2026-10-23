"""Recompute partial-network and extended-drill verdicts from raw server evidence."""
import argparse
import json
from pathlib import Path

from endurance import require
from extended_lifecycle import (fixtures, movement_results, order_results, output_results,
                               partial_result, qualification, redstone_results, reserve_results, restart_results)
from lifecycle import at, counts, parse
from matrix import digest, write_json


def audit(root):
    report = json.loads((root / "extended-results.json").read_text())
    require(report.get("source_fixture_unchanged") is True, "Source protection missing")
    require(json.loads((root / "source-fingerprint-before.json").read_text())
            == json.loads((root / "source-fingerprint-after.json").read_text()), "Fixture fingerprints changed")
    require(set(report["profiles"]) == {"fresh113", "original110-chained113"}, "Incomplete profile coverage")
    result = {"status": "PASS", "qualification": "PASS", "profiles": {}}
    definition = json.loads(json.dumps(fixtures()))
    for name, summary in report["profiles"].items():
        runtime = root / name
        require(json.loads((runtime / "lifecycle-fixture.json").read_text()) == definition, "Fixture geometry differs")
        def snapshot(label):
            return json.loads((runtime / "advantage-works-results" / f"lifecycle-{label}.json").read_text())
        inputs = json.loads((runtime / "input-jars.json").read_text())
        require(len(inputs) == 6, "Unexpected jar count")
        for jar in inputs: require(digest(runtime / "mods" / jar["file"]) == jar["sha256"], "Staged jar changed")
        require({p["file"]: p["sha256"] for p in inputs} == {p["file"]: p["sha256"] for p in report["artifacts"]}, "Report pins differ")
        computed = {"insufficient_energy": reserve_results(definition, snapshot("low-before"), snapshot("low-stalled")),
                    "moves": movement_results(definition, snapshot("step-4-complete")),
                    "blocked_output": output_results(definition, snapshot("blocked-output"), True),
                    "restart": restart_results(definition, snapshot("blocked-output"), snapshot("blocked-retained")),
                    "blocked_after_restart": output_results(definition, snapshot("blocked-retained"), True),
                    "resumed_output": output_results(definition, snapshot("freed-output"), False),
                    "redstone": redstone_results(definition, snapshot("redstone-before"), snapshot("redstone-held"), snapshot("redstone-resumed")),
                    "network_processing": "PASS", "partial_networks": [], "reload_orders": []}
        for cycle, group in enumerate(("machine", "supply", "supply", "machine"), 1):
            label = f"partial-{cycle}-{group}"
            samples = [snapshot(f"{label}-sample-{n}") for n in range(8)]
            confirmed = snapshot(label + "-confirmed"); confirmed["confirmed"] = True; samples.append(confirmed)
            computed["partial_networks"].append({"label": label, "group": group,
                                                 "cases": partial_result(definition, snapshot(label+"-before"), samples,
                                                                         snapshot(label+"-loaded"), group)})
        for order in (("machine", "supply"), ("supply", "machine")):
            label = "order-" + "-".join(order)
            computed["reload_orders"].append({"label": label, "order": list(order),
                                              "cases": order_results(definition, snapshot(label+"-before"), snapshot(label+"-absent"),
                                                                     snapshot(label+"-first"), snapshot(label+"-settled"),
                                                                     snapshot(label+"-loaded"), order)})
        for key, value in computed.items(): require(value == summary[key], "Verdict differs from raw evidence: " + key)
        for label, fluid in [("network-commissioned", 1000)] + [(f"partial-resumed-{n}", (n+1)*1000) for n in range(1,5)] + [
                ("order-machine-supply-resumed", 5000), ("order-supply-machine-resumed", 5000)]:
            document = snapshot(label)
            for f in definition:
                if f["id"] in ("C-STEAM", "C-ELECTRIC"):
                    machine = at(document, f["id"], f["witnesses"][4])
                    items = counts([machine])
                    require(items["minecraft:iron_ingot"] == 4 and items["minecraft:iron_ore"] == 0
                            and sum(machine["energy"].values()) > 0, "Processing did not resume: " + label)
                elif f["id"] == "C-FLUID":
                    tank = parse(at(document, f["id"], f["witnesses"][4])).get("Tank", {})
                    require(tank.get("FluidName") == "minecraft:water" and tank.get("Amount") == fluid, "Fluid conservation failure")
        checked = qualification(computed)
        require(checked == summary["status"], "Profile status disagrees with raw verdicts")
        logs = [p.read_text(encoding="utf-8") for p in runtime.glob("console-*.log")]
        require(len(logs) == 2, "Full process restart missing")
        require(all("Stopping server" in log for log in logs), "Server shutdown evidence missing")
        require(not any("Unknown item slot" in log or "Could not replace slot" in log or "Ticking block entity" in log
                        or "Encountered an unexpected exception" in log for log in logs), "Command failure or server crash")
        end = snapshot("redstone-resumed")
        events = end.get("chunkEvents", [])
        result["profiles"][name] = {"evidence_audit": "PASS", "machine_qualification": checked,
                                   "native_moves": len(snapshot("step-4-complete")["movements"]),
                                   "real_unloads_before_restart": sum(e["operation"] == "unload" for e in snapshot("blocked-output")["chunkEvents"]),
                                   "partial_cases": sum(len(e["cases"]) for e in computed["partial_networks"]),
                                   "reload_order_cases": sum(len(e["cases"]) for e in computed["reload_orders"]),
                                   "staged_jars": len(inputs)}
        if checked != "PASS": result["qualification"] = "FAIL"
    require(report["status"] == result["qualification"], "Overall status disagrees")
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = audit(args.run)
    write_json(args.output, result)
    print(json.dumps(result, indent=2))
