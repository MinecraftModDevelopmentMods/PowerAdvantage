"""Nether/End players and live entity relationships on disposable pinned worlds."""

import argparse
import copy
import json
from pathlib import Path
import shutil
import socket
import time

import nbtlib

from actors import (PLAYER_KEYS, backup_state, boot, enchantments, freeze_result,
                    qualified_baselines, semantic, tick_result as actor_tick_result)
from matrix import (POWER, WORLD, Server, copy_fixture, differences, digest,
                    fingerprint, intermediate_profile, normalized, parse_nbt,
                    plain, selected_profiles, write_json)


PLAYER_STATE = PLAYER_KEYS + ("SpawnX", "SpawnY", "SpawnZ", "SpawnForced", "SpawnDimension")
ENTITY_STATE = ("id", "UUIDMost", "UUIDLeast", "Pos", "Rotation", "Motion", "Dimension",
                "OwnerUUID", "Sitting", "parentPos", "ForgeData", "Leashed", "Leash",
                "Health", "Variant", "Type", "Items", "SaddleItem", "ArmorItem", "Passengers",
                "CollarColor", "Attributes")
ENTITY_IDS = {"Boat": "minecraft:boat", "EntityHorse": "minecraft:horse",
              "Horse": "minecraft:horse", "LeashKnot": "minecraft:leash_knot",
              "Wolf": "minecraft:wolf",
              "electricadvantage.electricadvantage.Hydroturbine": "electricadvantage:hydroturbine",
              "electricadvantage:electricadvantage.Hydroturbine": "electricadvantage:hydroturbine"}
WITNESSES = {"boat", "passenger-wolf", "horse", "hydroturbine"}


def runtime_errors(content):
    markers = ("Encountered an unexpected exception", "Exception stopping the server",
               '\"Silently\" catching entity tracking error.', "Dimension probe failed",
               "Dimension tick preparation failed", "Dimension load preparation failed")
    return [line for line in content.splitlines() if any(marker in line for marker in markers)]


def tick_result(before, after):
    result = actor_tick_result(before, after)
    for record in (before, after):
        names = [entry["witness"] for entry in record["entities"]]
        if not WITNESSES.issubset(names) or len(names) != len(set(names)):
            result["status"] = "FAIL"
    return result


def exact_item_differences(expected, actual, path=""):
    issues = []
    if isinstance(expected, dict) and isinstance(actual, dict):
        if "Count" in expected and "id" in expected:
            if expected != actual:
                issues.append({"path": path + ".exact-item", "expected": expected, "actual": actual})
        else:
            for key in expected.keys() & actual.keys():
                issues.extend(exact_item_differences(expected[key], actual[key], path + "." + key))
    elif isinstance(expected, list) and isinstance(actual, list):
        for index, (old, new) in enumerate(zip(expected, actual)):
            issues.extend(exact_item_differences(old, new, path + f"[{index}]"))
    return issues


def canonical_entities(value, legacy=False):
    if isinstance(value, list):
        return [canonical_entities(entry, legacy) for entry in value]
    if not isinstance(value, dict):
        return value
    result = {key: canonical_entities(entry, legacy) for key, entry in value.items()}
    if result.get("id") in ENTITY_IDS:
        result["id"] = ENTITY_IDS[result["id"]]
    if result.get("id") == "minecraft:horse" and result.get("Type") == 0:
        # Vanilla's horse split encodes legacy Type=0 in the new entity identity.
        result.pop("Type")
    if result.get("id") == "minecraft:wolf" and legacy and "CollarColor" in result:
        result["CollarColor"] = 15 - result["CollarColor"]
    return result


def entity_state(nbt):
    result = {key: nbt[key] for key in ENTITY_STATE if key in nbt}
    if "Passengers" in result:
        result["Passengers"] = [entity_state(entry) for entry in result["Passengers"]]
    if "Attributes" in result:
        result["Attributes"] = {entry["Name"]: {key: value for key, value in entry.items() if key != "Name"}
                                for entry in result["Attributes"]}
    return result


def capture(runtime, label, evidence=None):
    definition = json.loads((runtime / "dimension-fixture.json").read_text())
    record = json.loads((runtime / f"dimensions-{label}.json").read_text())
    registry = enchantments(runtime)
    level = nbtlib.load(runtime / WORLD / "level.dat")
    legacy = int(level["Data"].get("DataVersion", -1)) < 1451
    player_file = (evidence / "player.dat" if evidence else runtime / WORLD / "playerdata"
                   / (definition["playerUuid"] + ".dat"))
    state = {"dimension": record["dimension"],
             "file": semantic(nbtlib.load(player_file).unpack(), PLAYER_STATE, registry),
             "entities": {}, "relationships": {}}
    generator = record["generator"]
    state["generator"] = {"name": generator["actualName"]}
    if "tile" in generator:
        state["generator"]["tile"] = normalized(plain(parse_nbt(generator["tile"])), registry)
        state["generator"]["tile"].pop("initDone", None)
    if record["playerAdapter"]:
        state["adapter"] = semantic(parse_nbt(record["player"]), PLAYER_STATE, registry)
        state["playerWorldDimension"] = record["playerWorldDimension"]
    uuids = set()
    names = set()
    for entry in record["entities"]:
        name = entry["witness"]
        nbt = canonical_entities(normalized(plain(parse_nbt(entry)), registry), legacy)
        identity = (nbt.get("UUIDMost"), nbt.get("UUIDLeast"))
        if name in names or identity in uuids or None in identity:
            raise AssertionError(f"Duplicate or missing entity UUID/witness: {name}")
        names.add(name)
        uuids.add(identity)
        if name == "leash-knot":
            if nbt.get("id") != "minecraft:leash_knot":
                raise AssertionError("Unexpected transient helper identity")
            continue
        state["entities"][name] = entity_state(nbt)
        state["relationships"][name] = {
            "vehicleUuid": entry.get("vehicleUuid"),
            "passengerUuids": sorted(entry.get("passengerUuids", [])),
            "leashHolderType": entry.get("leashHolderType"),
            "leashHolderPosition": entry.get("leashHolderPosition"),
        }
        if entry.get("leashHolderType") != "minecraft:leash_knot":
            state["relationships"][name]["leashHolderUuid"] = entry.get("leashHolderUuid")
    return state


def verdict(expected, actual):
    issues = differences(expected, actual)
    issues.extend(exact_item_differences(expected, actual))
    if set(expected["entities"]) != set(actual["entities"]):
        issues.append({"path": "entities.keys", "expected": sorted(expected["entities"]),
                       "actual": sorted(actual["entities"])})
    return {"status": "FAIL" if issues else "PASS", "differences": issues}


def strict_expected(baseline, adapter=True):
    result = {key: copy.deepcopy(value) for key, value in baseline.items()
              if key != "relationships" and (adapter or key not in ("adapter", "playerWorldDimension"))}
    return result


def survival_expected(baseline):
    result = strict_expected(baseline)
    for nbt in result["entities"].values():
        for key in ("Pos", "Motion", "Rotation", "Passengers"):
            nbt.pop(key, None)
    result["relationships"] = copy.deepcopy(baseline["relationships"])
    return result


def checkpoint(server, runtime, modern, operation, label, save=True):
    command = (f"advdimensions {operation} {label}" if modern
               else f"advprobe dimensions-{operation} {label}")
    server.send(command)
    server.wait("ADVDIMENSIONS COMPLETE " + label, 120)
    if not save:
        return None
    server.send("save-all flush")
    server.wait("Saved the", 30)
    evidence = runtime / "dimension-evidence" / label
    evidence.mkdir(parents=True, exist_ok=False)
    definition = json.loads((runtime / "dimension-fixture.json").read_text())
    shutil.copy2(runtime / WORLD / "playerdata" / (definition["playerUuid"] + ".dat"), evidence / "player.dat")
    shutil.copy2(runtime / WORLD / "level.dat", evidence / "level.dat")
    state = capture(runtime, label, evidence)
    write_json(runtime / f"dimension-semantic-{label}.json", state)
    return state


def start(runtime, profile, args):
    launcher = boot(runtime, profile, args.java8, args.port)
    properties = runtime / "server.properties"
    properties.write_text(properties.read_text().replace("allow-nether=false", "allow-nether=true"))
    return launcher


def clone(source, target):
    copy_fixture(source, target)
    shutil.copy2(source / "dimension-fixture.json", target / "dimension-fixture.json")


def read_phase(runtime, launcher, args, label, modern):
    server = Server(runtime, args.java8, launcher, label)
    try:
        if args.native_load:
            checkpoint(server, runtime, modern, "operate", label + "-release", save=False)
            time.sleep(4)
        return checkpoint(server, runtime, modern, "read", label)
    finally:
        server.close()


def run(args):
    with socket.socket() as check:
        check.bind(("127.0.0.1", args.port))
    profiles = selected_profiles(args)
    valid_routes = {name + "-" + route for name in args.sources
                    for route in (["direct", "chained"] if name.endswith("110") else ["direct"])}
    if args.routes and not set(args.routes).issubset(valid_routes):
        raise ValueError("Unknown route subset")
    sources = qualified_baselines()
    originals = {name: fingerprint(sources[name] / WORLD) for name in args.sources}
    for name, hashes in originals.items():
        if hashes != json.loads((sources[name] / "fixture-hashes.json").read_text()):
            raise AssertionError("Changed historical fixture: " + name)
    args.output.mkdir(parents=True, exist_ok=False)
    write_json(args.output / "profiles.json", {name: {"seed": str(p[0]), "launcher": p[1],
               "jars": [{"path": str(j), "sha256": digest(j)} for j in p[2]]} for name, p in profiles.items()})
    results = {}
    for dimension in args.dimensions:
        district = "nether" if dimension == -1 else "end"
        root = args.output / district
        root.mkdir()
        for name in args.sources:
            routes = [route for route in (["direct", "chained"] if name.endswith("110") else ["direct"])
                      if not args.routes or name + "-" + route in args.routes]
            if not routes:
                continue
            source = root / name
            launcher = start(source, profiles[name], args)
            copy_fixture(sources[name], source)
            write_json(source / "dimension-fixture.json", {"dimension": dimension})
            print(f"BUILD {district}/{name}: source serializers", flush=True)
            server = Server(source, args.java8, launcher, "source")
            try:
                baseline = checkpoint(server, source, False, "build", "source")
            finally:
                server.close()
            if set(baseline["entities"]) != WITNESSES or baseline["file"]["Dimension"] != dimension:
                raise AssertionError("Incomplete source dimension fixture")
            immutable = fingerprint(source / WORLD)
            write_json(source / "fixture-hashes.json", immutable)
            for route in routes:
                label = name + "-" + route
                print("MIGRATE " + district + "/" + label, flush=True)
                predecessor = source
                checks = {}
                if route == "chained":
                    predecessor = root / (label + "-112")
                    middle_launcher = start(predecessor, intermediate_profile(profiles), args)
                    clone(source, predecessor)
                    for phase in ("intermediate", "intermediate-restart"):
                        state = read_phase(predecessor, middle_launcher, args, phase, False)
                        checks[phase] = verdict(survival_expected(baseline) if args.native_load else strict_expected(baseline), state)
                        if not args.native_load:
                            checks[phase + "-frozen"] = freeze_result(json.loads((predecessor / f"dimensions-{phase}.json").read_text()))
                        else:
                            checks[phase + "-ticks"] = tick_result(json.loads((predecessor / f"dimensions-{phase}-release.json").read_text()),
                                                                  json.loads((predecessor / f"dimensions-{phase}.json").read_text()))
                target = root / (label + "-113")
                target_launcher = start(target, profiles["target113"], args)
                clone(predecessor, target)
                player_id = json.loads((target / "dimension-fixture.json").read_text())["playerUuid"]
                relative_player = Path("playerdata") / (player_id + ".dat")
                original_player = digest(target / WORLD / relative_player)
                server = Server(target, args.java8, target_launcher, "conversion")
                try:
                    if args.native_load:
                        checkpoint(server, target, True, "operate", "conversion-release", save=False)
                        time.sleep(4)
                    converted = checkpoint(server, target, True, "snapshot", "converted")
                    expectation = survival_expected(baseline) if args.native_load else strict_expected(baseline, adapter=False)
                    expectation.pop("adapter", None)
                    expectation.pop("playerWorldDimension", None)
                    checks["conversion"] = verdict(expectation, converted)
                    if not args.native_load:
                        checks["conversion-frozen"] = freeze_result(json.loads((target / "dimensions-converted.json").read_text()))
                    else:
                        checks["conversion-ticks"] = tick_result(json.loads((target / "dimensions-conversion-release.json").read_text()),
                                                                 json.loads((target / "dimensions-converted.json").read_text()))
                    backups = backup_state(target)
                    backup_file = target / WORLD / relative_player.with_name(relative_player.name + ".poweradvantage-legacy-backup")
                    checks["backup"] = {"status": "PASS" if backup_file.is_file() and digest(backup_file) == original_player else "FAIL",
                                        "files": backups}
                    adapter = checkpoint(server, target, True, "read", "adapter")
                    checks["adapter"] = verdict(survival_expected(baseline) if args.native_load else strict_expected(baseline), adapter)
                finally:
                    server.close()
                server = Server(target, args.java8, target_launcher, "restart")
                try:
                    if args.native_load:
                        checkpoint(server, target, True, "operate", "restart-load-release", save=False)
                        time.sleep(4)
                    restarted = checkpoint(server, target, True, "read", "restarted")
                    checks["restart"] = verdict(survival_expected(baseline) if args.native_load else strict_expected(baseline), restarted)
                    if not args.native_load:
                        checks["restart-frozen"] = freeze_result(json.loads((target / "dimensions-restarted.json").read_text()))
                    checks["idempotence"] = verdict(survival_expected(adapter) if args.native_load else strict_expected(adapter), restarted)
                    checkpoint(server, target, True, "operate", "released")
                    time.sleep(4)
                    operated = checkpoint(server, target, True, "read", "runtime")
                    checks["runtime"] = verdict(survival_expected(baseline), operated)
                    checks["runtime-ticks"] = tick_result(json.loads((target / "dimensions-released.json").read_text()),
                                                         json.loads((target / "dimensions-runtime.json").read_text()))
                finally:
                    server.close()
                server = Server(target, args.java8, target_launcher, "runtime-restart")
                try:
                    checkpoint(server, target, True, "operate", "restart-released", save=not args.native_load)
                    time.sleep(4)
                    operated = checkpoint(server, target, True, "read", "runtime-restart")
                    checks["runtime-restart"] = verdict(survival_expected(baseline), operated)
                    checks["restart-ticks"] = tick_result(json.loads((target / "dimensions-restart-released.json").read_text()),
                                                         json.loads((target / "dimensions-runtime-restart.json").read_text()))
                finally:
                    server.close()
                checks["backup-stability"] = {"status": "PASS" if backup_state(target) == backups else "FAIL"}
                results[district + "/" + label] = checks
                write_json(target / "dimension-comparison.json", checks)
                write_json(args.output / "dimension-results.json", results)
                print(district + "/" + label + ": " + ", ".join(k + "=" + v["status"] for k, v in checks.items()), flush=True)
                if args.fail_fast and any(v["status"] != "PASS" for v in checks.values()):
                    return 1
            if fingerprint(source / WORLD) != immutable:
                raise AssertionError("Generated source fixture changed")
    for name, hashes in originals.items():
        if fingerprint(sources[name] / WORLD) != hashes:
            raise AssertionError("Historical source fixture changed")
    write_json(args.output / "scope.json", {"real_client_login": "NOT_TESTED", "dimensions": args.dimensions,
               "routes": len(results), "immutable_sources": "PASS",
               "expected_route_ids": sorted(("nether" if dimension == -1 else "end") + "/" + route
                                            for dimension in args.dimensions for route in (args.routes or valid_routes)),
               "native_load": args.native_load,
               "live_relationships": "Measured vehicle/passenger/leash UUIDs after native ticks and full restart"})
    return int(any(v["status"] != "PASS" for checks in results.values() for v in checks.values()))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workspace", type=Path, default=POWER.parents[1])
    parser.add_argument("--profile-manifest", type=Path, required=True)
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--port", type=int, default=25622)
    parser.add_argument("--sources", nargs="+", choices=list(qualified_baselines()), default=list(qualified_baselines()))
    parser.add_argument("--routes", nargs="+")
    parser.add_argument("--dimensions", nargs="+", type=int, choices=[-1, 1], default=[-1, 1])
    parser.add_argument("--fail-fast", action="store_true")
    parser.add_argument("--native-load", action="store_true", help="Permit vanilla entity initialization before the first save")
    raise SystemExit(run(parser.parse_args()))
