"""Bounded native116 walkable-bay sweep with initial, movement and restart oracles.

Production jars come only from a new immutable manifest. Historical station
definitions and failed broad runs remain unchanged. No source world is launched.
"""

import argparse
from collections import Counter
import json
import math
from pathlib import Path
import shutil
import struct
import time
import traceback

import nbtlib
from matrix import digest, plain, stage, write_json
from native116_code import Server
from native116 import stage
from native116 import RIG, output_path, pinned_profile, require_disjoint
from native116_lifecycle import require_native_boot, verify_staged


DIRECTIONS = ("down", "up", "north", "south", "west", "east")
REAR = ((0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1), (1, 0, 0), (-1, 0, 0))
DRILL_BAYS = ("S-05", "E-06")


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def drill_cases(station):
    result = []
    for assertion in station["assertions"]:
        if assertion.get("block") not in ("steamadvantage:steam_drill", "electricadvantage:electric_drill"):
            continue
        direction = assertion.get("meta", DIRECTIONS.index(assertion.get("expectedText", "down")))
        origin = tuple(a + b for a, b in zip(station["origin"], assertion["pos"]))
        rear = tuple(a + b for a, b in zip(origin, REAR[direction]))
        result.append(dict(id=station["id"] + ":" + assertion["id"], origin=origin, rear=rear,
                           facing=DIRECTIONS[direction], block=assertion["block"]))
    if len(result) != 6 or {r["facing"] for r in result} != set(DIRECTIONS):
        raise AssertionError("The original six-facing bay definition changed")
    return result


def nbt(row):
    return plain(nbtlib.parse_nbt(row["nbt"])) if "nbt" in row else {}


def drill_state(station, document, *, initial=False):
    rows = {tuple(row["pos"]): row for row in document["snapshot"]}
    if len(rows) != len(document["snapshot"]):
        raise AssertionError("Duplicate snapshot positions")
    cases = drill_cases(station)
    stock = Counter()
    for row in rows.values():
        for stack in nbt(row).get("Items", []):
            marker = stack.get("tag", {}).get("AdvBayStock")
            if marker:
                if stack.get("id") != "minecraft:diamond":
                    raise AssertionError("Inventory witness item identity changed")
                stock[marker] += int(stack["Count"])
    expected_positions, result = set(), {}
    for case in cases:
        candidates = [rows[pos] for pos in (case["origin"], case["rear"])
                      if pos in rows and rows[pos]["block"] == case["block"]]
        if len(candidates) != 1:
            raise AssertionError(case["id"] + ": missing or duplicate drill in declared cells")
        row = candidates[0]
        pos = tuple(row["pos"])
        if initial and pos != case["origin"]:
            raise AssertionError(case["id"] + ": initial placement moved before activation")
        if initial:
            track = "steamadvantage:steam_track" if station["id"] == "S-05" else "electricadvantage:electric_track"
            if rows.get(case["rear"], {}).get("block") != track:
                raise AssertionError(case["id"] + ": initial declared rear track is missing")
        if "facing=" + case["facing"] not in row["state"]:
            raise AssertionError(case["id"] + ": block facing changed")
        data = nbt(row)
        if data.get("ForgeData", {}).get("AdvBayWitness") != case["id"]:
            raise AssertionError(case["id"] + ": persistent moved tile witness lost/replaced")
        if tuple(int(data[axis]) for axis in ("x", "y", "z")) != pos:
            raise AssertionError(case["id"] + ": tile coordinates do not match its block")
        if stock[case["id"]] != 7:
            raise AssertionError(case["id"] + ": inventory stock lost or duplicated")
        energy = data.get("Energy", [])
        if len(energy) != 1:
            raise AssertionError(case["id"] + ": energy witness missing")
        value = struct.unpack("!f", struct.pack("!I", int(energy[0]) & 0xffffffff))[0]
        capacity = 50 if station["id"] == "S-05" else 500
        if not math.isfinite(value) or not 0 <= value <= capacity:
            raise AssertionError(case["id"] + ": invalid energy")
        expected_positions.add(pos)
        result[case["id"]] = dict(pos=list(pos), facing=case["facing"], block=row["block"],
                                  tile=data["id"], witness=case["id"], stock=7,
                                  energyBits=[int(e) for e in energy], energy=value,
                                  moved=pos == case["rear"])
    actual_positions = {pos for pos, row in rows.items()
                        if row["block"] in ("steamadvantage:steam_drill", "electricadvantage:electric_drill")}
    if actual_positions != expected_positions or set(stock) != {case["id"] for case in cases}:
        raise AssertionError("Extra/off-track drill or inventory witness in bay")
    return result


def operating_state(initial, operated):
    if initial.keys() != operated.keys() or len(initial) != 12:
        raise AssertionError("Twelve unique drill witnesses are required")
    for key in initial:
        for field in ("facing", "block", "tile", "witness", "stock"):
            if initial[key][field] != operated[key][field]:
                raise AssertionError(key + ": movement changed " + field)
    # These two original connected lanes are the Q-RIG-001 reproduction.
    for key in ("S-05:down-facing", "E-06:up-facing"):
        if not operated[key]["moved"]:
            raise AssertionError(key + ": the broad run did not exercise actual track movement")
    return dict(status="PASS", drills=12, moved=sum(v["moved"] for v in operated.values()))


def restarted_state(before, after):
    if before != after:
        changed = [key for key in set(before) | set(after) if before.get(key) != after.get(key)]
        raise AssertionError("Restart changed position, identity, facing, stock or energy: " + str(changed))
    return dict(status="PASS", drills=len(after), exactEnergy=True)


def assertions_state(records, stations):
    if set(records) != {station["id"] for station in stations}:
        raise AssertionError("The broad sweep omitted a bay")
    counts = Counter()
    manual = []
    for key, document in records.items():
        if document["station"] != key or document["minecraft"] != "1.16.5":
            raise AssertionError("Wrong bay/target report")
        if document["status"] not in ("PASS", "MANUAL_REQUIRED"):
            raise AssertionError(key + ": " + document["status"])
        expected = next(s for s in stations if s["id"] == key)
        if (len(document["assertions"]) != len(expected["assertions"])
                or {a["id"] for a in document["assertions"]} != {a["id"] for a in expected["assertions"]}):
            raise AssertionError(key + ": assertion set changed")
        for assertion in document["assertions"]:
            counts[assertion["status"]] += 1
            if assertion["status"] not in ("PASS", "SKIP"):
                raise AssertionError(key + "/" + assertion["id"] + ": " + assertion["status"])
            if assertion["status"] == "SKIP":
                spec = next(a for a in expected["assertions"] if a["id"] == assertion["id"])
                if not set(spec.get("requiredMods", [])) - set(document["mods"]):
                    raise AssertionError(key + "/" + assertion["id"] + ": unexplained skip")
        if document["manualChecks"]:
            manual.append(key)
    return dict(status="PASS", assertions=dict(counts), manualRequired=manual)


def loaded_mods(records, expected):
    for key, record in records.items():
        if record["mods"] != expected:
            raise AssertionError(key + ": loaded mod identities/versions differ from the pinned profile")


def authorize_broad(runtime):
    # matrix.stage is shared with frozen conversion campaigns. A live works
    # campaign must explicitly opt out of MigrationProbe's tile-tick freeze.
    for name in ("advantage-works.allow", "migration-operate.allow"):
        (runtime / name).touch()


def configure_broad(runtime, port):
    # Match the walkable WorksRunner rather than matrix.stage's migration-only
    # defaults. Clearing a freshly generated village can race native POI tasks.
    overrides = {"server-port": str(port), "generator-settings": "", "generate-structures": "false",
                 "level-seed": "advantage-works-1.16.5-v1", "difficulty": "0", "gamemode": "creative",
                 "allow-flight": "true", "enable-command-block": "true", "force-gamemode": "true",
                 "spawn-animals": "false", "spawn-monsters": "false", "spawn-npcs": "false", "pvp": "false"}
    path = runtime / "server.properties"
    lines = [line for line in path.read_text().splitlines() if line.partition("=")[0] not in overrides]
    path.write_text("\n".join(lines) + "\n" + "".join(f"{key}={value}\n" for key, value in overrides.items()))


class BroadServer(Server):
    def command(self, command, token):
        self.send(command)
        self.wait(token, 150)

    def checkpoint(self, station, label):
        self.command(f"advworks checkpoint {station} {label}", "Checkpoint written for " + station)
        return read_json(Path(self.log.name).parent / "advantage-works-results" / f"{station}-checkpoint-{label}.json")

    def check_all(self, stations, phase):
        self.command("advworks check all", "Advantage Works check:")
        directory = Path(self.log.name).parent / "advantage-works-results"
        records = {s["id"]: read_json(directory / (s["id"] + "-latest.json")) for s in stations}
        write_json(directory / (phase + "-all-stations.json"), records)
        return records


def run_profile(args, profile, output, name, port, stations):
    if name == "packaged-decoupled":
        profile = (profile[0], profile[1], [p for p in profile[2] if not p.name.startswith("BaseMetals-")])
    runtime = output / name
    launcher = stage(runtime, profile)
    configure_broad(runtime, port)
    authorize_broad(runtime)
    result = dict(status="FAIL", profile=name, port=port, phases={})
    pins = read_json(args.target_manifest)["target116"]["jars"]
    expected_mods = {pin["id"]: pin["version"] for pin in pins
                     if name != "packaged-decoupled" or pin["id"] != "basemetals"}
    expected_mods.update(minecraft="1.16.5", forge="36.2.34")
    result["expectedMods"] = expected_mods
    server = None
    try:
        verify_staged(runtime, profile)
        server = BroadServer(runtime, args.java8, launcher, "first")
        server.command("advworks build all", "Advantage Works built 21 station(s)")
        # Named finite stock and tile data are commissioning witnesses, not repairs
        # performed after operation or restart. Every destination is unchanged later.
        for station in stations:
            if station["id"] not in DRILL_BAYS:
                continue
            for case in drill_cases(station):
                pos = " ".join(map(str, case["origin"]))
                marker = json.dumps(case["id"])
                tag = ('{ForgeData:{AdvBayWitness:' + marker + '},Items:[{Slot:0b,id:"minecraft:diamond",'
                       'Count:7b,tag:{AdvBayStock:' + marker + '}}]}')
                server.command(f"data merge block {pos} {tag}", "Modified block data of")
        initial = {}
        for station in stations:
            if station["id"] in DRILL_BAYS:
                initial.update(drill_state(station, server.checkpoint(station["id"], "initial"), initial=True))
        result["phases"]["initial"] = dict(status="PASS", drills=initial)
        write_json(runtime / "broad-results.json", result)
        for station in stations:
            sid = station["id"]
            server.command("advworks start " + sid, "Started " + sid)
            time.sleep(max(60, args.run_seconds) if sid == "W-03" else args.run_seconds)
            server.checkpoint(sid, "operated")
            server.command("advworks stop " + sid, "Stopped " + sid)
        first = server.check_all(stations, "first")
        loaded_mods(first, expected_mods)
        result["phases"]["first"] = assertions_state(first, stations)
        before = {}
        for station in stations:
            if station["id"] in DRILL_BAYS:
                before.update(drill_state(station, first[station["id"]]))
        result["phases"]["operation"] = operating_state(initial, before)
        result["beforeRestart"] = before
        server.close()
        server = None
        require_native_boot(runtime / "console-first.log")
        verify_staged(runtime, profile)
        server = BroadServer(runtime, args.java8, launcher, "restarted")
        # No start, reset, data merge, replacement, or refill on the second boot.
        time.sleep(args.run_seconds)
        second = server.check_all(stations, "restarted")
        loaded_mods(second, expected_mods)
        result["phases"]["restarted"] = assertions_state(second, stations)
        after = {}
        for station in stations:
            if station["id"] in DRILL_BAYS:
                after.update(drill_state(station, second[station["id"]]))
        result["afterRestart"] = after
        result["phases"]["persistence"] = restarted_state(before, after)
        server.close()
        server = None
        require_native_boot(runtime / "console-restarted.log")
        verify_staged(runtime, profile)
        result["status"] = "PASS"
    except Exception:
        result["error"] = traceback.format_exc()
    finally:
        if server is not None:
            try:
                server.close()
            except Exception:
                result["shutdownError"] = traceback.format_exc()
        write_json(runtime / "broad-results.json", result)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target-manifest", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--port", type=int, default=25588)
    parser.add_argument("--run-seconds", type=int, default=8)
    parser.add_argument("--profile", choices=("both", "packaged-current", "packaged-decoupled"), default="both")
    args = parser.parse_args()
    if not 1 <= args.run_seconds <= 60 or not 1024 <= args.port < 65535:
        parser.error("Bounded run duration and unprivileged consecutive ports required")
    output = output_path(args.output)
    profile = pinned_profile(args.target_manifest, "target116")
    target_pins = read_json(args.target_manifest)["target116"]["jars"]
    required = {"poweradvantage", "steamadvantage", "electricadvantage", "advantageworkstest", "basemetals", "orespawn"}
    if {pin["id"] for pin in target_pins} != required:
        raise AssertionError("Supply the complete current-profile manifest; the runner derives its decoupled subset")
    for protected in (args.target_manifest, profile[0], *profile[2]):
        require_disjoint(output, protected)
    stations_path = RIG / "src/main/resources/assets/advantageworkstest/works/works-1.13.2.json"
    stations = read_json(stations_path)["stations"]
    if len(stations) != 21:
        raise AssertionError("The complete 21-bay specification is required")
    output.mkdir(parents=True)
    shutil.copy2(Path(__file__), output / "runner-source.py")
    source_hash = digest(output / "runner-source.py")
    names = ("packaged-current", "packaged-decoupled") if args.profile == "both" else (args.profile,)
    results = {}
    for index, name in enumerate(names):
        results[name] = run_profile(args, profile, output, name, args.port + index, stations)
        write_json(output / "native116-broad-results.json", dict(
            status="PASS" if all(r["status"] == "PASS" for r in results.values()) else "FAIL",
            targetManifest=str(args.target_manifest.resolve()), targetManifestSha256=digest(args.target_manifest),
            runnerSha256=source_hash, stationDefinitionSha256=digest(stations_path), profiles=results))
        print(name, results[name]["status"], flush=True)
    return int(any(r["status"] != "PASS" for r in results.values()))


if __name__ == "__main__":
    raise SystemExit(main())
