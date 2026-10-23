"""Test isolated pipe-only chunk bridges without replacing retained endpoints."""
import argparse
import json
import socket
import time
from pathlib import Path

from endurance import require
from lifecycle import LifecycleRun, at, counts, parse
from matrix import POWER, RIG, digest, profiles, stage, write_json


def fixtures():
    result = []
    for index, name in enumerate(("C-STEAM", "C-ELECTRIC", "C-FLUID")):
        base = 9216 + index * 64
        origin = [base + 13, 80, 2062]
        sink = [base + 9, 80, 2062]
        path = [[base + 13, 80, z] for z in range(2063, 2067)]
        path += [[base + x, 80, 2066] for x in range(12, 8, -1)]
        path += [[base + 9, 80, z] for z in range(2065, 2062, -1)]
        result.append({"id": name, "min": [base, 72, 2048], "max": [base + 31, 90, 2079],
                       "witnesses": [origin, sink] + path, "pipes": path,
                       "groups": {"bridge": [[base >> 4, 129]], "endpoints": [[base >> 4, 128]]}})
    return result


def run(args):
    with socket.socket() as port:
        port.bind(("127.0.0.1", args.port))
    manifest = json.loads((RIG / "profiles/packaged-current.json").read_text())
    for mod in manifest["mods"]:
        if "sha256" in mod:
            require(digest((RIG / mod["path"]).resolve()) == mod["sha256"], "Artifact pin mismatch: " + mod["file"])
    definition = fixtures()
    launcher = stage(args.output, profiles(POWER.parents[1])["target113"])
    props = args.output / "server.properties"
    props.write_text(props.read_text().replace("server-port=25584", f"server-port={args.port}"))
    for marker in ("migration-operate.allow", "advantage-works.allow", "advantage-lifecycle.allow"):
        (args.output / marker).touch()
    write_json(args.output / "lifecycle-fixture.json", definition)
    runner = LifecycleRun(args.output, args.java8, launcher, definition)
    report = {"status": "RUNNING", "cycles": []}
    try:
        runner.boot("pipe-only")
        runner.probe("load", "loaded")
        for f in definition:
            runner.command("fill " + " ".join(map(str, f["min"] + f["max"])) + " minecraft:air replace")
            fluid, steam = f["id"] == "C-FLUID", f["id"] == "C-STEAM"
            pipe = "poweradvantage:fluid_pipe" if fluid else "steamadvantage:steam_pipe" if steam else "electricadvantage:electric_conduit"
            runner.block(f["witnesses"][0], pipe if fluid else "poweradvantage:infinite_" + ("steam" if steam else "electricity"))
            for pos in f["pipes"]: runner.block(pos, pipe)
            runner.block(f["witnesses"][1], "poweradvantage:fluid_metal_tank" if fluid else "steamadvantage:steam_furnace" if steam else "electricadvantage:electric_furnace")
            if steam: runner.inventory(f["witnesses"][1], 0, "minecraft:coal", 64)
        runner.probe("trace-loads", "trace-enabled")
        # Let normal powered-state updates settle before recording the first baseline.
        time.sleep(2)
        runner.probe("snapshot", "commissioned")
        for cycle in range(3):
            before = runner.probe("snapshot", f"bridge-{cycle}-before")
            runner.command("save-all flush")
            runner.probe("release-bridge", f"bridge-{cycle}-released")
            time.sleep(3)
            absent = runner.probe("isolated", f"bridge-{cycle}-isolated")
            runner.probe("offer-blocked", f"bridge-{cycle}-blocked-offer")
            time.sleep(3)
            confirmed = runner.probe("snapshot", f"bridge-{cycle}-confirmed")
            for f in definition:
                bridge = f["groups"]["bridge"][0]
                old = next(c for c in before["chunks"] if c["pos"] == bridge)
                for snapshot in (absent, confirmed):
                    state = next(c for c in snapshot["chunks"] if c["pos"] == bridge)
                    require(not state["loaded"] and state["unloads"] > old["unloads"], "Bridge did not stay genuinely absent")
                    for pos in f["witnesses"][:2]:
                        require(at(snapshot, f["id"], pos).get("tileIdentity") == at(before, f["id"], pos).get("tileIdentity"), "Retained endpoint replaced")
            runner.probe("load-bridge", f"bridge-{cycle}-loaded")
            runner.probe("offer", f"bridge-{cycle}-reconnected-offer")
            for f in definition:
                if f["id"] == "C-FLUID": continue
                machine = f["witnesses"][1]
                for slot in range(1 if f["id"] == "C-STEAM" else 0, 7 if f["id"] == "C-STEAM" else 10):
                    runner.inventory(machine, slot, "minecraft:air", 1)
                runner.inventory(machine, 1 if f["id"] == "C-STEAM" else 0, "minecraft:iron_ore", 4)
            time.sleep(50)
            resumed = runner.probe("snapshot", f"bridge-{cycle}-resumed")
            for f in definition:
                endpoint = at(resumed, f["id"], f["witnesses"][1])
                if f["id"] == "C-FLUID":
                    require(parse(endpoint)["Tank"]["Amount"] == (cycle + 1) * 1000, "Fluid lost or duplicated")
                else:
                    require(counts([endpoint])["minecraft:iron_ingot"] == 4 and counts([endpoint])["minecraft:iron_ore"] == 0, "Processing did not reconnect")
                require(endpoint.get("tileIdentity") == at(before, f["id"], f["witnesses"][1]).get("tileIdentity"), "Reconnection replaced endpoint")
            report["cycles"].append({"cycle": cycle, "status": "PASS"})
            print(f"Pipe-only bridge cycle {cycle + 1}: PASS", flush=True)
        report["status"] = "PASS"
        runner.command("save-all flush")
    except BaseException as error:
        report.update(status="FAIL", failure=str(error))
        raise
    finally:
        runner.close()
        write_json(args.output / "pipe-only-results.json", report)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--port", type=int, default=25587)
    run(parser.parse_args())
