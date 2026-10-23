"""Server death/respawn adapters, not a client death screen or login test."""

import argparse
from collections import Counter
import itertools
import json
from pathlib import Path
import shutil
import socket

import nbtlib

from actors import enchantments, semantic
from dimensions import PLAYER_STATE, clone, exact_item_differences, start
from matrix import POWER, WORLD, Server, differences, fingerprint, normalized, parse_nbt, plain, selected_profiles, write_json


def stacks(items):
    result = Counter()
    for original in items:
        value = dict(original)
        value.pop("Slot", None)
        quantity = int(value.pop("Count", 0))
        result[json.dumps(value, sort_keys=True)] += quantity
    return result


def spawn_reference(level_file):
    data = nbtlib.load(level_file)["Data"]
    return {"position": [int(data[key]) for key in ("SpawnX", "SpawnY", "SpawnZ")],
            "radius": max(0, int(data.get("GameRules", {}).get("spawnRadius", "10")))}


def respawn_verdict(record, registry, world_spawn=None):
    data = record["respawn"]
    before = semantic(parse_nbt(data["before"]), PLAYER_STATE, registry)
    dead = semantic(parse_nbt(data["afterDeath"]), PLAYER_STATE, registry)
    after = semantic(parse_nbt(data["after"]), PLAYER_STATE, registry)
    issues = []
    keep = data["keepInventory"]
    expected_items = before["Inventory"] if keep else []
    expected = {"Dimension": 0, "Health": 20.0, "Inventory": expected_items,
                "EnderItems": before["EnderItems"], "UUIDMost": before["UUIDMost"], "UUIDLeast": before["UUIDLeast"],
                "XpLevel": before["XpLevel"] if keep else 0, "XpTotal": before["XpTotal"] if keep else 0}
    issues.extend(differences(expected, after, "respawn"))
    issues.extend(exact_item_differences(expected, after, "respawn"))
    issues.extend(differences({"Health": 0.0, "Inventory": expected_items}, dead, "death"))
    issues.extend(differences({"fromDimension": record["dimension"], "toDimension": 0}, data, "world"))
    if data.get("handler") != "NetHandlerPlayServer.PERFORM_RESPAWN":
        issues.append({"path": "handler", "expected": "NetHandlerPlayServer.PERFORM_RESPAWN", "actual": data.get("handler")})
    if not before["Inventory"] or not before["EnderItems"]:
        issues.append({"path": "source-items", "expected": "Nonempty source inventories", "actual": before})
    drops = [normalized(plain(parse_nbt(entry))["Item"], registry) for entry in data["drops"]]
    expected_drops = Counter() if keep else stacks(before["Inventory"])
    actual_drops = stacks(drops)
    if expected_drops != actual_drops:
        issues.append({"path": "drops", "expected": dict(expected_drops), "actual": dict(actual_drops)})
    position = after["Pos"]
    near_bed = abs(position[0] - 160.5) <= 3 and 99 <= position[1] <= 104 and abs(position[2] - 24.5) <= 3
    if (data["bedMode"] != "missing") != near_bed:
        issues.append({"path": "spawn-position", "expected": "bed area" if data["bedMode"] != "missing" else "world spawn",
                       "actual": position})
    if data["bedMode"] == "missing":
        if world_spawn is None:
            issues.append({"path": "world-spawn-reference", "expected": "Native saved spawn position/radius", "actual": None})
        else:
            spawn = world_spawn["position"]
            radius = world_spawn["radius"] + 1
            if abs(position[0] - spawn[0]) > radius or abs(position[2] - spawn[2]) > radius or not 0 <= position[1] < 256:
                issues.append({"path": "world-spawn-position", "expected": world_spawn, "actual": position})
    if data["bedMode"] != "missing":
        spawn = {key: before[key] for key in ("SpawnX", "SpawnY", "SpawnZ", "SpawnForced") if key in before}
        issues.extend(differences(spawn, after, "saved-spawn"))
    return {"status": "FAIL" if issues else "PASS", "differences": issues,
            "inventory_quantity": sum(stacks(before["Inventory"]).values()), "drop_quantity": sum(actual_drops.values()),
            "scope": data["scope"], "keepInventory": keep, "bedMode": data["bedMode"]}


def run(args):
    with socket.socket() as check:
        check.bind(("127.0.0.1", args.port))
    available = selected_profiles(args)
    args.output.mkdir(parents=True, exist_ok=False)
    results = {}
    sources = {}
    for district in args.districts:
        for profile in args.profiles:
            name = profile if profile != "target113" else args.target_fixture
            fixture = args.fixtures / district / name
            old_hashes = fingerprint(fixture / WORLD)
            sources[district + "/" + profile] = {"path": str(fixture.resolve() / WORLD), "hashes": old_hashes}
            write_json(args.output / "source-fingerprints.json", sources)
            runtime = args.output / district / profile
            launcher = start(runtime, available[profile], args)
            clone(fixture, runtime)
            definition = json.loads((runtime / "dimension-fixture.json").read_text())
            player_file = runtime / WORLD / "playerdata" / (definition["playerUuid"] + ".dat")
            original_player = player_file.read_bytes()
            server = Server(runtime, args.java8, launcher, "respawn")
            try:
                for keep, mode in itertools.product([True, False], ["forced", "bed", "missing"]):
                    label = ("keep" if keep else "drop") + "-" + mode
                    # Independent cases start from the source-serialized player, never from a previous death result.
                    player_file.write_bytes(original_player)
                    definition["respawn"] = {"keepInventory": keep, "bedMode": mode}
                    write_json(runtime / "dimension-fixture.json", definition)
                    command = ("advdimensions respawn " if profile == "target113" else "advprobe dimensions-respawn ") + label
                    server.send(command)
                    server.wait("ADVDIMENSIONS COMPLETE " + label, 120)
                    server.send("save-all flush")
                    server.wait("Saved the", 30)
                    record = json.loads((runtime / f"dimensions-{label}.json").read_text())
                    result = respawn_verdict(record, enchantments(runtime), spawn_reference(runtime / WORLD / "level.dat"))
                    saved = semantic(nbtlib.load(player_file).unpack(), PLAYER_STATE, enchantments(runtime))
                    expected_saved = semantic(parse_nbt(record["respawn"]["after"]), PLAYER_STATE, enchantments(runtime))
                    saved_issues = differences(expected_saved, saved, "saved-player")
                    saved_issues.extend(exact_item_differences(expected_saved, saved, "saved-player"))
                    result["differences"].extend(saved_issues)
                    if saved_issues:
                        result["status"] = "FAIL"
                    results[district + "/" + profile + "/" + label] = result
                    evidence = runtime / "respawn-evidence" / label
                    evidence.mkdir(parents=True)
                    shutil.copy2(player_file, evidence / "player.dat")
                    shutil.copy2(runtime / WORLD / "level.dat", evidence / "level.dat")
                    write_json(args.output / "respawn-results.json", results)
                    print(district + "/" + profile + "/" + label + "=" + result["status"], flush=True)
            finally:
                server.close()
            current = fingerprint(fixture / WORLD)
            if current != old_hashes:
                raise AssertionError("Source fixture changed")
    write_json(args.output / "scope.json", {"real_client_login_and_death_screen": "NOT_TESTED", "cases": len(results),
               "expected_case_ids": sorted(district + "/" + profile + "/" + ("keep" if keep else "drop") + "-" + mode
                                           for district in args.districts for profile in args.profiles
                                           for keep, mode in itertools.product([True, False], ["forced", "bed", "missing"])),
               "source_fixtures": "UNCHANGED", "case_initialisation": "Restore source-serialized player.dat before each independent case",
               "respawn_adapter": "Native onDeath and NetHandlerPlayServer.PERFORM_RESPAWN, server-only dummy connection"})
    return int(any(value["status"] != "PASS" for value in results.values()))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workspace", type=Path, default=POWER.parents[1])
    parser.add_argument("--profile-manifest", type=Path, required=True)
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--fixtures", type=Path, required=True)
    parser.add_argument("--port", type=int, default=25622)
    parser.add_argument("--districts", nargs="+", choices=["nether", "end"], default=["nether", "end"])
    parser.add_argument("--profiles", nargs="+", default=["original110", "maintained112", "target113"])
    parser.add_argument("--target-fixture", default="original110-chained-113")
    raise SystemExit(run(parser.parse_args()))
