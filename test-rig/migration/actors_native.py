"""Control: migrate actors and let them tick before their first target save."""
import argparse
import json
from pathlib import Path
import socket

from actors import WORLD, boot, clone, compare, phase, tick_result
from matrix import POWER, fingerprint, intermediate_profile, selected_profiles, write_json


def run(args):
    with socket.socket() as check: check.bind(("127.0.0.1", args.port))
    root = args.output
    root.mkdir(parents=True, exist_ok=False)
    available = selected_profiles(args)
    results = {}
    source_results = json.loads((args.fixtures / "actor-results.json").read_text())
    for label in source_results:
        if args.routes and label not in args.routes: continue;
        source_name = label.rsplit("-", 1)[0]
        source = args.fixtures / source_name
        bridge = source
        untouched = fingerprint(bridge / WORLD)
        if label.endswith("-chained"):
            bridge = root / (label + "-112")
            launcher112 = boot(bridge, intermediate_profile(available), args.java8, args.port)
            clone(source, bridge)
            (bridge / "actor-operate.allow").touch()
            phase(bridge, args.java8, launcher112, False, "native112", "operate")
        target = root / label
        launcher = boot(target, available["target113"], args.java8, args.port)
        clone(bridge, target)
        bridge_before = fingerprint(bridge / WORLD)
        # Unlike the frozen campaign, both stages allow parent links to resolve
        # through native ticks before the first save.
        (target / "actor-operate.allow").touch()
        baseline = json.loads((source / "semantic-source.json").read_text())
        expected = {"file": baseline["file"], "entities": {key: {k: v for k, v in value.items() if k not in ("Pos", "Motion", "Rotation")}
                    for key, value in baseline["entities"].items()}}
        verdict = {}
        for checkpoint in ("native", "native-restart"):
            state = phase(target, args.java8, launcher, True, checkpoint, "operate")
            verdict[checkpoint] = compare(expected, state)
            released = json.loads((target / f"actors-{checkpoint}-release.json").read_text())
            final = json.loads((target / f"actors-{checkpoint}.json").read_text())
            verdict[checkpoint]["timing"] = tick_result(released, final)
            if verdict[checkpoint]["timing"]["status"] != "PASS":
                verdict[checkpoint]["status"] = "FAIL"
        if fingerprint(source / WORLD) != untouched: raise AssertionError("Source fixture changed")
        if fingerprint(bridge / WORLD) != bridge_before: raise AssertionError("Intermediate fixture changed")
        results[label] = verdict
        write_json(root / "native-results.json", results)
        print(label + ": " + ", ".join(key + "=" + value["status"] for key, value in verdict.items()), flush=True)
    return int(any(stage["status"] != "PASS" for route in results.values() for stage in route.values()))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workspace", type=Path, default=POWER.parents[1])
    parser.add_argument("--profile-manifest", type=Path, help="Hash-pinned profiles; optional bridge112 overrides only the intermediate runtime")
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--fixtures", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--port", type=int, default=25617)
    parser.add_argument("--routes", nargs="+", help="Optional route subset for a short preflight")
    raise SystemExit(run(parser.parse_args()))
