"""Reconcile native drill restart evidence and keep the original failures visible."""
import argparse
import json
from pathlib import Path
import re

from connected import entries, operation, read, state
from drill_restart import first_tick
from matrix import WORLD, compare, digest, fingerprint, write_json


def audit(root, red=False):
    raw = json.loads((root / "drill-restart-results.json").read_text())
    expected = {"110", "112"} if red else {"110", "112", "113"}
    if set(raw["targets"]) != expected or raw["status"] != ("FAIL" if red else "PASS"):
        raise AssertionError("Incomplete campaign or incorrect overall verdict")
    profiles = json.loads((root / "profiles.json").read_text())
    for profile in profiles.values():
        for jar in profile["jars"]:
            if digest(Path(jar["path"])) != jar["sha256"]:
                raise AssertionError("Archived jar changed")
    result = dict(targets={})
    for name, target in raw["targets"].items():
        runtime = root / name
        source = Path(target["source"])
        if fingerprint(source / WORLD) != target["source_hashes"]:
            raise AssertionError("Source fixture changed")
        for jar in json.loads((runtime / "input-jars.json").read_text()):
            if digest(runtime / "mods" / jar["file"]) != jar["sha256"]:
                raise AssertionError("Staged jar changed")
        if (runtime / "connected-build.allow").exists() or (runtime / "connected-built.json").exists():
            raise AssertionError("Native test had construction authority")
        for path in runtime.glob("connected-*.json"):
            if path.name == "connected-fixture.json":
                continue
            snapshot = json.loads(path.read_text())
            if snapshot["buildCallsThisProcess"] != 0 or state(snapshot)["status"] != "PASS":
                raise AssertionError("Native snapshot lost items or rebuilt equipment")
            counterpart = runtime / path.name.replace("connected-", "migration-", 1)
            if snapshot["snapshot"] != json.loads(counterpart.read_text()):
                raise AssertionError("Raw NBT and semantic records disagree")
        for path in runtime.glob("console-*.commands.json"):
            commands = json.loads(path.read_text())
            for command in commands:
                if command not in ("save-all flush", "stop") and not re.fullmatch(
                    r"advconnected (snapshot (loaded-source|ready-[01]|restart-[12]-loaded)|step (7 ready-[01]-align-\d+|1 restart-[12]-first|620 restart-[12]-worked))", command):
                    raise AssertionError("Unexpected mutation command: " + command)
            log = path.with_name(path.name.replace(".commands.json", ".log")).read_text(encoding="utf-8")
            if "Done (" not in log or "Stopping server" not in log or "Connected probe failed" in log:
                raise AssertionError("Native process did not start/shut down cleanly")
        initial = compare(source / "migration-source.json", runtime / "migration-loaded-source.json")
        if initial["status"] != "PASS":
            raise AssertionError("Initial imported state differs")
        phases = {}
        for number in (1, 2):
            label = "restart-" + str(number)
            loaded, first, worked = (read(runtime, label + suffix) for suffix in ("-loaded", "-first", "-worked"))
            preserved = compare(runtime / f"migration-ready-{number - 1}.json", runtime / ("migration-" + label + "-loaded.json"))
            if preserved["status"] != "PASS":
                raise AssertionError("Saved checkpoint changed before ticking")
            live = operation(first, worked, 620)
            if live["status"] != "PASS":
                raise AssertionError("Continued native operation failed: " + str(live))
            tick = first_tick(loaded, first)
            if red:
                if tick["status"] != "FAIL" or len(tick["failures"]) != 2 or any(
                        tick["progress"][station]["after"] >= tick["progress"][station]["before"] for station in ("C-SDRILL", "C-EDRILL")):
                    raise AssertionError("Expected both original resets to be reproduced")
            elif tick["status"] != "PASS":
                raise AssertionError("Repaired first tick failed: " + str(tick))
            phases[label] = dict(preservation=preserved, first_tick=tick, operation=live)
        result["targets"][name] = dict(phases=phases)
    result["status"] = "REPRODUCED_FOUR_LEGACY_FAILURES" if red else "PASS"
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", type=Path, required=True)
    parser.add_argument("--red", action="store_true")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = audit(args.run, args.red)
    write_json(args.output, result)
    print(result["status"])
