"""Prove both drills retain mid-cycle work across native process restarts."""
import argparse
import json
from pathlib import Path
import socket

from connected import ConnectedServer, data, entries, operation, pins, prepare, read, state
from matrix import POWER, WORLD, compare, fingerprint, write_json


def progress(document, station):
    return data(entries(document)[station + "-drill"])


def first_tick(before, after):
    failures = []
    if (before["requestedTicks"], before["advancedTicks"], before["held"]) != (0, 0, True):
        failures.append("Missing frozen load")
    if (after["requestedTicks"], after["advancedTicks"], after["held"]) != (1, 1, True):
        failures.append("Missing one native tick")
    readings = {}
    for station in ("C-SDRILL", "C-EDRILL"):
        old, new = progress(before, station), progress(after, station)
        readings[station] = dict(before=old.get("progress"), after=new.get("progress"))
        low, high = (4, 18) if station == "C-SDRILL" else (64, 530)
        if not low <= old.get("progress", 0) <= high:
            failures.append(station + ": checkpoint is not mid-cycle")
        if new.get("progress") != old.get("progress", 0) + 1:
            failures.append(station + ": saved progress not continued")
        if any(old.get(key) != new.get(key) for key in ("targetX", "targetY", "targetZ")):
            failures.append(station + ": target changed during first tick")
        a, b = entries(before)[station + "-drill"], entries(after)[station + "-drill"]
        if a["objectIdentity"] != b["objectIdentity"] or a["witness"] != b["witness"]:
            failures.append(station + ": drill replaced")
    failures.extend(state(after)["failures"])
    return dict(status="FAIL" if failures else "PASS", failures=failures, progress=readings)


def ready(server, runtime, label):
    for index in range(100):
        server.observe("snapshot", label)
        document = read(runtime, label)
        steam = progress(document, "C-SDRILL").get("progress", 0)
        electric = progress(document, "C-EDRILL").get("progress", 0)
        if 4 <= steam <= 18 and 64 <= electric <= 530:
            return document
        server.observe("step", f"{label}-align-{index}", 7)
    raise AssertionError("Cannot reach a naturally powered mid-cycle checkpoint for both drills")


def run(args):
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", args.port))
    args.output.mkdir(parents=True, exist_ok=False)
    available = pins(args.profile_manifest, args.output)
    report = dict(status="RUNNING", targets={}, protocol="No NBT/energy/inventory writes; native alignment, frozen load, first tick, 620 work ticks, two complete restarts")
    try:
        for target in args.targets:
            print("NATIVE RESTART " + target, flush=True)
            source = args.fixture110 if target == "110" else args.fixture112
            hashes = fingerprint(source / WORLD)
            profile = available.get("native110", available["maintained110"]) if target == "110" else available["bridge112" if target == "112" else "target113"]
            definition = json.loads((source / "connected-fixture.json").read_text())
            runtime = args.output / target
            launcher = prepare(runtime, profile, definition, args.port, source)
            result = dict(source=str(source.resolve()), source_hashes=hashes, phases={})
            server = ConnectedServer(runtime, args.java8, launcher, "alignment")
            try:
                server.observe("snapshot", "loaded-source")
                result["initial_preservation"] = compare(source / "migration-source.json", runtime / "migration-loaded-source.json")
                ready(server, runtime, "ready-0")
                server.send("save-all flush")
            finally:
                server.close()
            previous = "ready-0"
            for number in (1, 2):
                label = "restart-" + str(number)
                server = ConnectedServer(runtime, args.java8, launcher, label)
                try:
                    server.observe("snapshot", label + "-loaded")
                    server.observe("step", label + "-first", 1)
                    loaded, first = read(runtime, label + "-loaded"), read(runtime, label + "-first")
                    server.observe("step", label + "-worked", 620)
                    result["phases"][label] = dict(
                        preservation=compare(runtime / ("migration-" + previous + ".json"), runtime / ("migration-" + label + "-loaded.json")),
                        first_tick=first_tick(loaded, first),
                        operation=operation(first, read(runtime, label + "-worked"), 620))
                    if number == 1:
                        ready(server, runtime, "ready-1")
                        previous = "ready-1"
                    server.send("save-all flush")
                finally:
                    server.close()
            if fingerprint(source / WORLD) != hashes:
                raise AssertionError("Native regression modified its source fixture")
            result["status"] = "PASS" if result["initial_preservation"]["status"] == "PASS" and all(
                gate["status"] == "PASS" for phase in result["phases"].values() for gate in phase.values()) else "FAIL"
            report["targets"][target] = result
            write_json(args.output / "drill-restart-results.json", report)
            print(target + ": " + result["status"], flush=True)
        report["status"] = "PASS" if report["targets"] and all(r["status"] == "PASS" for r in report["targets"].values()) else "FAIL"
    except BaseException as error:
        report.update(status="FAIL", failure=str(error))
        raise
    finally:
        write_json(args.output / "drill-restart-results.json", report)
    return int(report["status"] != "PASS")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile-manifest", type=Path, required=True)
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--targets", nargs="+", choices=("110", "112", "113"), default=["110", "112", "113"])
    parser.add_argument("--fixture110", type=Path, default=POWER / "run-connected-migration/20261023-working-maintained110/maintained110")
    parser.add_argument("--fixture112", type=Path, default=POWER / "run-connected-migration/20261023-working-112/maintained112")
    parser.add_argument("--port", type=int, default=25659)
    raise SystemExit(run(parser.parse_args()))
