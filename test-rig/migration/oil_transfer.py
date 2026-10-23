"""Native oil buckets, drains, pipes and both containers across complete server restarts."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import socket
import time

from endurance import require
from lifecycle import LifecycleRun, at, parse
from matrix import POWER, RIG, digest, stage, write_json


def fixtures():
    result = []
    for index, (oil, tank) in enumerate((
            (oil, tank) for oil in ("crude_oil", "refined_oil")
            for tank in ("fluid_storage_tank", "fluid_metal_tank"))):
        x, y, z = 11264 + index * 64, 80, 2056
        source, drain = [x + 8, y + 1, z], [x + 8, y, z]
        # Reproduce the player's descending pipe layout, then turn horizontally into the tank.
        pipes = [[x + 8, y - step, z] for step in (1, 2, 3)]
        pipes += [[x + step, y - 3, z] for step in (9, 10, 11)]
        output = [x + 12, y - 3, z]
        result.append({"id": f"OIL-{oil}-{tank}", "min": [x, 72, 2048], "max": [x + 31, 88, 2079],
                       "source": source, "drain": drain, "pipes": pipes, "output": output,
                       "fluid": "poweradvantage:" + oil, "bucket": "poweradvantage:" + oil + "_bucket",
                       "tank": "poweradvantage:" + tank, "witnesses": [source, drain, *pipes, output]})
    return result


def inspect(snapshot, definition, expected):
    """Exact oracle: filling some tank with some fluid is not sufficient."""
    rows = []
    for f in definition:
        source = at(snapshot, f["id"], f["source"])
        drain = at(snapshot, f["id"], f["drain"])
        output = at(snapshot, f["id"], f["output"])
        require(source["block"] == "minecraft:air", f"Source not collected: {f['id']}")
        require(drain["block"] == "poweradvantage:fluid_drain", f"Drain missing: {f['id']}")
        require(output["block"] == f["tank"], f"Container changed: {f['id']}")
        for pos in f["pipes"]:
            require(at(snapshot, f["id"], pos)["block"] == "poweradvantage:fluid_pipe", f"Pipe missing: {f['id']}")
        held = parse(drain).get("Tank", {}).get("Amount", 0)
        tank = parse(output).get("Tank", {})
        require(held == 0, f"Oil stuck in drain ({held} mB): {f['id']}")
        require(tank.get("Amount") == expected, f"Lost/duplicated oil: {f['id']}: {tank}")
        require(tank.get("FluidName") == f["fluid"], f"Oil changed identity: {f['id']}: {tank}")
        rows.append({"id": f["id"], "status": "PASS", "fluid": f["fluid"], "tank": f["tank"],
                     "drain_mB": held, "container_mB": tank["Amount"], "total_mB": held + tank["Amount"]})
    return rows


def require_same_machines(before, after, definition):
    for f in definition:
        for pos in (f["drain"], f["output"]):
            old, new = at(before, f["id"], pos), at(after, f["id"], pos)
            require("tileIdentity" in old and old["tileIdentity"] == new.get("tileIdentity"),
                    f"Refill replaced a machine: {f['id']} {pos}")


def run(args):
    output = args.output.resolve()
    require(output.is_relative_to(POWER.resolve()) and output != POWER.resolve(), "Output must be inside PowerAdvantage")
    require(not output.exists(), "Use a new disposable output directory")
    with socket.socket() as port:
        port.bind(("127.0.0.1", args.port))
    manifest = json.loads((RIG / "profiles/packaged-current.json").read_text())
    jars = []
    for mod in manifest["mods"]:
        jar = (RIG / mod["path"]).resolve()
        require(digest(jar) == mod["sha256"], "Artifact pin mismatch: " + mod["file"])
        jars.append(jar)
    seed = POWER / "run-advantage-works/packaged-decoupled-20261005-184456"
    launcher = stage(output, (seed, "forge-1.13.2-25.0.223.jar", jars))
    props = output / "server.properties"
    props.write_text(props.read_text().replace("server-port=25584", f"server-port={args.port}"))
    for marker in ("migration-operate.allow", "advantage-works.allow", "advantage-lifecycle.allow"):
        (output / marker).touch()
    definition = fixtures()
    write_json(output / "lifecycle-fixture.json", definition)
    runner = LifecycleRun(output, args.java8, launcher, definition)
    report = {"status": "RUNNING", "phases": [], "full_process_restarts": 0,
              "inputs": json.loads((output / "input-jars.json").read_text())}
    try:
        runner.boot("oil-first-fill")
        runner.probe("load", "oil-loaded")
        for f in definition:
            runner.command("fill " + " ".join(map(str, f["min"] + f["max"])) + " minecraft:air replace")
            for pos in f["pipes"]: runner.block(pos, "poweradvantage:fluid_pipe")
            runner.block(f["drain"], "poweradvantage:fluid_drain")
            runner.block(f["output"], f["tank"])
            x, y, z = f["source"]
            for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                runner.block([x + dx, y, z + dz], "minecraft:glass")
        empty = runner.probe("snapshot", "oil-empty")
        for f in definition:
            for pos in (f["drain"], f["output"]):
                require(parse(at(empty, f["id"], pos)).get("Tank", {}).get("Amount", 0) == 0, "Fixture was not empty")
        runner.probe("place-buckets", "oil-first-buckets")
        time.sleep(args.seconds)
        first = runner.probe("snapshot", "oil-first-fill")
        report["phases"].append({"phase": "first-fill", "cases": inspect(first, definition, 1000)})
        require_same_machines(empty, first, definition)
        runner.command("save-all flush")
        runner.close()

        runner.boot("oil-restart-refill")
        report["full_process_restarts"] += 1
        runner.probe("load", "oil-restart-loaded")
        time.sleep(args.seconds)
        restored = runner.probe("snapshot", "oil-restored")
        report["phases"].append({"phase": "restored", "cases": inspect(restored, definition, 1000)})
        # Only add new world-fluid sources: no tile.read(), block replacement, API offers or manual tank filling.
        runner.probe("place-buckets", "oil-refill-buckets")
        time.sleep(args.seconds)
        refilled = runner.probe("snapshot", "oil-refilled")
        report["phases"].append({"phase": "refilled", "cases": inspect(refilled, definition, 2000)})
        require_same_machines(restored, refilled, definition)
        runner.command("save-all flush")
        runner.close()

        runner.boot("oil-second-restart")
        report["full_process_restarts"] += 1
        runner.probe("load", "oil-second-restart-loaded")
        time.sleep(args.seconds)
        retained = runner.probe("snapshot", "oil-retained")
        report["phases"].append({"phase": "retained", "cases": inspect(retained, definition, 2000)})
        runner.command("save-all flush")
        report["status"] = "PASS"
    except BaseException as error:
        report.update(status="FAIL", failure=str(error))
        raise
    finally:
        try:
            runner.close()
        except BaseException as error:
            report.update(status="FAIL", shutdown_failure=str(error))
            raise
        finally:
            write_json(output / "oil-transfer-results.json", report)
    print(f"Oil transfer: PASS, four lanes, two full restarts. Evidence: {output}", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java8", type=Path, required=True, help="Java 8 executable")
    parser.add_argument("--output", type=Path, default=POWER / "run-oil-transfer" /
                        datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S"))
    parser.add_argument("--port", type=int, default=25588)
    parser.add_argument("--seconds", type=int, default=8)
    args = parser.parse_args()
    require(args.seconds >= 2, "Allow at least two seconds for the eight-tick distribution cycle")
    run(args)
