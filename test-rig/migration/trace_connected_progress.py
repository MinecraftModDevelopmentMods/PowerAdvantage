"""Observe the first native drill tick in copies, with no progress restoration."""
import argparse
import json
from pathlib import Path
import socket

from connected import (ConnectedServer, data, entries, operation, pins, prepare, read, state)
from matrix import WORLD, compare, fingerprint, intermediate_profile, write_json


def drill(document):
    return data(entries(document)["C-EDRILL-drill"])


def run(args):
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", args.port))
    before_hashes = fingerprint(args.fixture / WORLD)
    args.output.mkdir(parents=True, exist_ok=False)
    available = pins(args.profile_manifest, args.output)
    definition = json.loads((args.fixture / "connected-fixture.json").read_text())
    result = dict(controls={}, fixture=str(args.fixture.resolve()), source_hashes=before_hashes)
    for name, profile in (("legacy112", intermediate_profile(available)), ("target113", available["target113"])):
        runtime = args.output / name
        launcher = prepare(runtime, profile, definition, args.port, args.fixture)
        server = ConnectedServer(runtime, args.java8, launcher, "trace")
        try:
            server.observe("snapshot", "loaded")
            server.observe("step", "first-tick", 1)
            server.observe("step", "continued", 700)
            loaded, first, continued = (read(runtime, label) for label in ("loaded", "first-tick", "continued"))
            result["controls"][name] = dict(
                preservation=compare(args.fixture / "migration-intermediate-restart-worked.json", runtime / "migration-loaded.json"),
                loaded=drill(loaded), first_tick=drill(first), continued=drill(continued),
                continued_operation=operation(first, continued, 700),
                before=state(loaded), after=state(continued))
            server.send("save-all flush")
        finally:
            server.close()
    if fingerprint(args.fixture / WORLD) != before_hashes:
        raise AssertionError("Trace modified source world")
    controls = result["controls"]
    legacy, target = controls["legacy112"], controls["target113"]
    result["finding"] = "LEGACY_RESTART_PROGRESS_LOSS" if legacy["first_tick"]["progress"] < legacy["loaded"]["progress"] else "NOT_REPRODUCED"
    result["target_preserves_first_tick"] = target["first_tick"]["progress"] > target["loaded"]["progress"]
    result["control_protocol"] = "Frozen loaded snapshot, one native tick, 700 native ticks; no replacement, refill, energy or progress writes"
    write_json(args.output / "progress-trace.json", result)
    print(result["finding"], "target preserves:", result["target_preserves_first_tick"])
    return int(result["finding"] != "LEGACY_RESTART_PROGRESS_LOSS" or not result["target_preserves_first_tick"]
               or any(c["preservation"]["status"] != "PASS" or c["continued_operation"]["status"] != "PASS" for c in controls.values()))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--profile-manifest", type=Path, required=True)
    parser.add_argument("--fixture", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--port", type=int, default=25658)
    raise SystemExit(run(parser.parse_args()))
