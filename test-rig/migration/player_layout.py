"""Player bag/Ender Chest boundaries and recursively stored item stacks."""
import argparse
import copy
import json
from pathlib import Path
import socket

import nbtlib
from actors import WORLD, boot, clone, phase
from matrix import POWER, differences, fingerprint, intermediate_profile, selected_profiles, write_json


def seed(runtime):
    definition = json.loads((runtime / "actor-fixture.json").read_text())
    path = runtime / WORLD / "playerdata" / (definition["playerUuid"] + ".dat")
    player = nbtlib.load(path)
    slots = iter([0, 7, 8, 9, 18, 27, 35, 1, 2])
    for stack in player["Inventory"]:
        if 0 <= int(stack["Slot"]) <= 8: stack["Slot"] = nbtlib.Byte(next(slots))
    for stack, slot in zip(player["EnderItems"], [0, 3, 5, 8, 13, 17, 20, 25, 26]): stack["Slot"] = nbtlib.Byte(slot)
    musket = next(s for s in player["Inventory"] if str(s["id"]) == "steamadvantage:musket")
    battery = next(s for s in player["Inventory"] if str(s["id"]) == "electricadvantage:lithium_battery")
    oil = next(s for s in player["Inventory"] if str(s["id"]) in ("forge:bucketfilled", "poweradvantage:crude_oil"))
    cargo = [copy.deepcopy(s) for s in (musket, battery, oil)]
    for slot, stack in enumerate(cargo): stack["Slot"] = nbtlib.Byte(slot)
    musket["tag"]["Nested"]["Items"] = nbtlib.List[nbtlib.Compound](cargo)
    player.save()


def player_verdict(expected, state):
    issues = differences({key: expected[key] for key in ("file", "adapter")}, state)
    return {"status": "FAIL" if issues else "PASS", "differences": issues}


def run(args):
    with socket.socket() as check: check.bind(("127.0.0.1", args.port))
    root = args.output
    root.mkdir(parents=True, exist_ok=False)
    available = selected_profiles(args)
    results = {}
    for name in ("original110", "maintained110", "maintained112"):
        immutable = args.fixtures / name
        before = fingerprint(immutable / WORLD)
        source = root / name
        launcher = boot(source, available[name], args.java8, args.port)
        clone(immutable, source)
        seed(source)
        baseline = phase(source, args.java8, launcher, False, "source")
        write_json(source / "fixture-hashes.json", fingerprint(source / WORLD))
        for route in (["direct", "chained"] if name == "original110" else ["direct"]):
            label = name + "-" + route
            predecessor = source
            verdict = {}
            if route == "chained":
                predecessor = root / (label + "-112")
                launcher112 = boot(predecessor, intermediate_profile(available), args.java8, args.port)
                clone(source, predecessor)
                verdict["intermediate"] = player_verdict(baseline, phase(predecessor, args.java8, launcher112, False, "intermediate"))
            target = root / (label + "-113")
            launcher113 = boot(target, available["target113"], args.java8, args.port)
            clone(predecessor, target)
            for checkpoint in ("converted", "restarted"):
                verdict[checkpoint] = player_verdict(baseline, phase(target, args.java8, launcher113, True, checkpoint))
            results[label] = verdict
            write_json(root / "layout-results.json", results)
            print(label + ": " + ", ".join(stage + "=" + value["status"] for stage, value in verdict.items()), flush=True)
        if fingerprint(immutable / WORLD) != before: raise AssertionError("Source fixture changed")
        if fingerprint(source / WORLD) != json.loads((source / "fixture-hashes.json").read_text()): raise AssertionError("Layout fixture changed")
    return int(any(stage["status"] != "PASS" for route in results.values() for stage in route.values()))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workspace", type=Path, default=POWER.parents[1])
    parser.add_argument("--profile-manifest", type=Path, help="Hash-pinned profiles; optional bridge112 overrides only the intermediate runtime")
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--fixtures", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--port", type=int, default=25618)
    raise SystemExit(run(parser.parse_args()))
