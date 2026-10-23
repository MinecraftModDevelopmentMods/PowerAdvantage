"""Capture real chunk acquisition callers in isolated, marked network fixtures."""
import argparse
from datetime import datetime, timezone
import json
import socket
import time
from pathlib import Path

from extended_lifecycle import ExtendedRun, fixtures, order_results
from endurance import require
from matrix import POWER, RIG, digest, profiles, stage, write_json


def run(args):
    with socket.socket() as port:
        port.bind(("127.0.0.1", args.port))
    manifest = json.loads((RIG / "profiles/packaged-current.json").read_text())
    for mod in manifest["mods"]:
        if "sha256" in mod:
            require(digest((RIG / mod["path"]).resolve()) == mod["sha256"], "Artifact pin mismatch: " + mod["file"])
    definition = [f for f in fixtures() if "groups" in f]
    launcher = stage(args.output, profiles(POWER.parents[1])["target113"])
    props = args.output / "server.properties"
    props.write_text(props.read_text().replace("server-port=25584", f"server-port={args.port}"))
    for marker in ("migration-operate.allow", "advantage-works.allow", "advantage-lifecycle.allow"):
        (args.output / marker).touch()
    write_json(args.output / "lifecycle-fixture.json", definition)
    runner = ExtendedRun(args.output, args.java8, launcher, definition)
    report = {"started_at_utc": datetime.now(timezone.utc).isoformat(), "partial_networks": [], "reload_orders": []}
    try:
        runner.boot("partial-trace")
        runner.build()
        time.sleep(50)
        runner.processing("commissioned", 1000)
        runner.probe("trace-loads", "trace-enabled")
        for cycle, group in enumerate(("machine", "supply"), 1):
            result = runner.partial(group, cycle)
            report["partial_networks"].append(result)
            print(result, flush=True)
        for order in (("machine", "supply"), ("supply", "machine")):
            label = "order-" + "-".join(order)
            before = runner.probe("snapshot", label + "-before")
            runner.probe("release-supply", label + "-release-supply")
            runner.probe("release-machine", label + "-release-machine")
            time.sleep(5)
            absent = runner.probe("snapshot", label + "-absent")
            first = runner.probe("load-" + order[0], label + "-first")
            time.sleep(2)
            settled = runner.probe("snapshot", label + "-settled")
            loaded = runner.probe("load-" + order[1], label + "-loaded")
            result = {"label": label, "cases": order_results(definition, before, absent, first, settled, loaded, order)}
            report["reload_orders"].append(result)
            print(result, flush=True)
        report["chunk_events"] = loaded["chunkEvents"]
        report["status"] = "PASS" if all(c["status"] == "PASS" for r in report["partial_networks"] + report["reload_orders"] for c in r["cases"].values()) else "FAIL"
        runner.command("save-all flush")
    except BaseException as error:
        report.update(status="FAIL", failure=str(error))
        raise
    finally:
        runner.close()
        report["completed_at_utc"] = datetime.now(timezone.utc).isoformat()
        write_json(args.output / "partial-trace-results.json", report)
    require(report["status"] == "PASS", "Partial-unload acceptance failures; raw load stacks retained")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--port", type=int, default=25587)
    run(parser.parse_args())
