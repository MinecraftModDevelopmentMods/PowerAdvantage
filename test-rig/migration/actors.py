"""Player-file adapters and saved entity migration on disposable, pinned runtimes."""

import argparse
import json
from pathlib import Path
import shutil
import socket
import time

import nbtlib
from matrix import (POWER, WORLD, Server, copy_fixture, differences, digest, fingerprint,
                    intermediate_profile, normalized, parse_nbt, plain, selected_profiles, stage, write_json)


PLAYER_KEYS = ("Inventory", "EnderItems", "SelectedItemSlot", "Pos", "Rotation", "Dimension",
               "UUIDMost", "UUIDLeast", "Health", "XpLevel", "XpTotal", "XpP", "foodLevel", "foodSaturationLevel")
ENTITY_KEYS = ("id", "UUIDMost", "UUIDLeast", "Pos", "Rotation", "Motion", "Item", "Items",
               "OwnerUUID", "Sitting", "parentPos", "ForgeData")
VANILLA_ENTITIES = {"Item": "minecraft:item", "MinecartChest": "minecraft:chest_minecart", "Wolf": "minecraft:wolf"}


def enchantments(runtime):
    level = nbtlib.load(runtime / WORLD / "level.dat")
    entries = level.get("FML", {}).get("Registries", {}).get("minecraft:enchantments", {}).get("ids", [])
    return {int(e["V"]): str(e["K"]) for e in entries}


def semantic(value, keys, registry):
    result = normalized(plain(value), registry)
    if result.get("id") in VANILLA_ENTITIES: result["id"] = VANILLA_ENTITIES[result["id"]]
    if "hydroturbine" in str(result.get("id", "")).lower(): result["id"] = "electricadvantage:hydroturbine"
    for key in ("Inventory", "EnderItems", "Items"):
        if key in result: result[key] = sorted(result[key], key=lambda entry: int(entry["Slot"]))
    return {key: result[key] for key in keys if key in result}


def capture(runtime, label, evidence=None):
    definition = json.loads((runtime / "actor-fixture.json").read_text())
    registry = enchantments(runtime)
    record = json.loads((runtime / f"actors-{label}.json").read_text())
    player_file = evidence / "player.dat" if evidence else runtime / WORLD / "playerdata" / (definition["playerUuid"] + ".dat")
    result = {"file": semantic(nbtlib.load(player_file).unpack(), PLAYER_KEYS, registry),
              "entities": {}}
    if "embedded" in record: result["embedded"] = semantic(parse_nbt(record["embedded"]), PLAYER_KEYS, registry)
    if record["playerAdapter"]: result["adapter"] = semantic(parse_nbt(record["player"]), PLAYER_KEYS, registry)
    for entry in record["entities"]:
        witness = entry["witness"]
        if witness in result["entities"]: raise AssertionError(f"Duplicate entity witness {witness}")
        result["entities"][witness] = semantic(parse_nbt(entry), ENTITY_KEYS, registry)
    return result


def compare(expected, actual):
    issues = differences(expected, actual)
    if set(expected["entities"]) != set(actual["entities"]):
        issues.append({"path": "entities.keys", "expected": sorted(expected["entities"]), "actual": sorted(actual["entities"])})
    for container in ("file", "embedded", "adapter"):
        if container not in expected or container not in actual: continue
        for inventory in ("Inventory", "EnderItems"):
            before, after = expected[container].get(inventory, []), actual[container].get(inventory, [])
            if len(before) != len(after): continue  # differences already reports length changes
            old_slots = [entry["Slot"] for entry in before]
            new_slots = [entry["Slot"] for entry in after]
            if len(set(new_slots)) != len(new_slots) or old_slots != new_slots:
                issues.append({"path": f"{container}.{inventory}.slots", "expected": old_slots, "actual": new_slots})
    return {"status": "FAIL" if issues else "PASS", "differences": issues,
            "player_status": "FAIL" if any(not d["path"].lstrip(".").startswith("entities") for d in issues) else "PASS",
            "entity_status": "FAIL" if any(d["path"].lstrip(".").startswith("entities") for d in issues) else "PASS"}


def tick_result(released, final):
    before = {e["witness"]: e["ticksExisted"] for e in released["entities"]}
    ticks = {e["witness"]: e["ticksExisted"] - before[e["witness"]] for e in final["entities"] if e["witness"] in before}
    world_ticks = final["gameTime"] - released["gameTime"]
    ok = world_ticks >= 60 and bool(ticks) and min(ticks.values()) >= 60
    return {"status": "PASS" if ok else "FAIL", "world_ticks": world_ticks, "entity_ticks": ticks}


def freeze_result(record):
    counters = {e["witness"]: e["ticksExisted"] for e in record["entities"]}
    return {"status": "PASS" if record.get("frozen") is True and bool(counters) and all(t == 0 for t in counters.values()) else "FAIL",
            "entity_ticks": counters}


def qualified_baselines():
    root = POWER / "run-migration-matrix"
    return {"original110": root / "original-cyano-20261005-r1/original110",
            "early110": root / "full-20261005-r1/original110",
            "maintained110": root / "full-20261005-r1/maintained110",
            "legacy112": root / "full-20261005-r1/legacy112",
            "maintained112": root / "full-20261005-r1/maintained112"}


def boot(runtime, profile, java, port):
    launcher = stage(runtime, profile)
    properties = runtime / "server.properties"
    properties.write_text(properties.read_text().replace("server-port=25584", f"server-port={port}"))
    return launcher


def actor_command(server, modern, operation, label):
    command = f"advactors {operation} {label}" if modern else f"advprobe actors-{operation} {label}"
    server.send(command)
    server.wait("ADVACTORS COMPLETE " + label, 120)


def phase(runtime, java, launcher, modern, label, operation="read"):
    server = Server(runtime, java, launcher, label)
    try:
        if operation == "operate":
            actor_command(server, modern, "operate" if modern else "snapshot", label + "-release")
            time.sleep(4)
            actor_command(server, modern, "snapshot" if modern else "read", label)
        else:
            actor_command(server, modern, operation, label)
        server.send("save-all flush")
        server.wait("Saved the", 30)
    finally:
        server.close()
    if not modern:
        # A dedicated server has no host player. Seed an integrated-style section
        # from the actual source serializer; do not claim integrated-server login.
        definition = json.loads((runtime / "actor-fixture.json").read_text())
        player_file = runtime / WORLD / "playerdata" / (definition["playerUuid"] + ".dat")
        level = nbtlib.load(runtime / WORLD / "level.dat")
        level["Data"]["Player"] = nbtlib.Compound(dict(nbtlib.load(player_file)))
        level.save()
    state = capture(runtime, label)
    if not modern: state["embedded"] = state["file"]
    evidence = runtime / "actor-evidence" / label
    evidence.mkdir(parents=True, exist_ok=False)
    definition = json.loads((runtime / "actor-fixture.json").read_text())
    shutil.copy2(runtime / WORLD / "playerdata" / (definition["playerUuid"] + ".dat"), evidence / "player.dat")
    shutil.copy2(runtime / WORLD / "level.dat", evidence / "level.dat")
    write_json(runtime / f"semantic-{label}.json", state)
    return state


def clone(source, target):
    copy_fixture(source, target)
    shutil.copy2(source / "actor-fixture.json", target / "actor-fixture.json")


def backup_state(runtime):
    return {str(path.relative_to(runtime / WORLD)): digest(path)
            for path in (runtime / WORLD).rglob("*.poweradvantage-legacy-backup")}


def run(args):
    with socket.socket() as check:
        check.bind(("127.0.0.1", args.port))
    available = selected_profiles(args)
    valid_routes = {name + "-" + route for name in args.sources
                    for route in (["direct", "chained"] if name.endswith("110") else ["direct"])}
    if args.routes and not set(args.routes).issubset(valid_routes):
        raise ValueError(f"Unavailable routes: {set(args.routes) - valid_routes}")
    for name in args.sources:
        fixture = qualified_baselines()[name]
        if fingerprint(fixture / WORLD) != json.loads((fixture / "fixture-hashes.json").read_text()):
            raise AssertionError(f"Changed immutable fixture {fixture}")
    root = args.output
    root.mkdir(parents=True, exist_ok=False)
    write_json(root / "profiles.json", {name: {"seed": str(p[0]), "launcher": p[1],
               "jars": [{"path": str(j), "sha256": digest(j)} for j in p[2]]} for name, p in available.items()})
    results = {}
    for name in args.sources:
        routes = [route for route in (["direct", "chained"] if name.endswith("110") else ["direct"])
                  if not args.routes or name + "-" + route in args.routes]
        if not routes: continue
        source = qualified_baselines()[name]
        before = fingerprint(source / WORLD)
        expected_hashes = json.loads((source / "fixture-hashes.json").read_text())
        if before != expected_hashes: raise AssertionError(f"Changed immutable fixture {source}")
        fixture = root / name
        launcher = boot(fixture, available[name], args.java8, args.port)
        copy_fixture(source, fixture)
        print(f"BUILD actors using source serializers: {name}", flush=True)
        baseline = phase(fixture, args.java8, launcher, False, "source", "build")
        if len(baseline["entities"]) != 5: raise AssertionError("Source did not retain five entities")
        if len(baseline["file"]["Inventory"]) < 14: raise AssertionError("Missing source inventory/armor/offhand")
        immutable = fingerprint(fixture / WORLD)
        write_json(fixture / "fixture-hashes.json", immutable)
        for route in routes:
            label = name + "-" + route
            print("MIGRATE " + label, flush=True)
            input_fixture = fixture
            verdict = {}
            if route == "chained":
                middle = root / (label + "-112")
                middle_launcher = boot(middle, intermediate_profile(available), args.java8, args.port)
                clone(fixture, middle)
                state = phase(middle, args.java8, middle_launcher, False, "intermediate")
                verdict["frozen_intermediate"] = freeze_result(json.loads((middle / "actors-intermediate.json").read_text()))
                verdict["intermediate"] = compare(baseline, state)
                state = phase(middle, args.java8, middle_launcher, False, "intermediate-restart")
                verdict["frozen_intermediate_restart"] = freeze_result(json.loads((middle / "actors-intermediate-restart.json").read_text()))
                verdict["intermediate_restart"] = compare(baseline, state)
                input_fixture = middle
            target = root / (label + "-113")
            launcher = boot(target, available["target113"], args.java8, args.port)
            clone(input_fixture, target)
            copied_player = next((target / WORLD / "playerdata").glob("*.dat"))
            original_player_hash = digest(copied_player)
            # First inspect migrated files/entities without invoking the player loader.
            converted = phase(target, args.java8, launcher, True, "converted", "snapshot")
            verdict["frozen_conversion"] = freeze_result(json.loads((target / "actors-converted.json").read_text()))
            expected = {key: value for key, value in baseline.items() if key != "adapter"}
            verdict["conversion"] = compare(expected, converted)
            backups = backup_state(target)
            backup_file = copied_player.with_name(copied_player.name + ".poweradvantage-legacy-backup")
            verdict["backup"] = {"status": "PASS" if backup_file.is_file() and digest(backup_file) == original_player_hash else "FAIL",
                                 "files": backups}
            loaded = phase(target, args.java8, launcher, True, "adapter")
            verdict["frozen_adapter"] = freeze_result(json.loads((target / "actors-adapter.json").read_text()))
            adapter_expected = {key: value for key, value in baseline.items() if key != "embedded"}
            verdict["player_adapter"] = compare(adapter_expected, loaded)
            restarted = phase(target, args.java8, launcher, True, "restarted")
            verdict["frozen_restart"] = freeze_result(json.loads((target / "actors-restarted.json").read_text()))
            verdict["restart"] = compare(adapter_expected, restarted)
            verdict["idempotence"] = compare(loaded, restarted)
            verdict["backup_stability"] = {"status": "PASS" if backup_state(target) == backups else "FAIL"}
            runtime_state = phase(target, args.java8, launcher, True, "runtime", "operate")
            survival = {"file": baseline["file"], "entities": {key: {k: v for k, v in value.items() if k not in ("Pos", "Motion", "Rotation")}
                        for key, value in baseline["entities"].items()}}
            verdict["runtime_survival"] = compare(survival, runtime_state)
            release = json.loads((target / "actors-runtime-release.json").read_text())
            ticked = json.loads((target / "actors-runtime.json").read_text())
            verdict["runtime_ticks"] = tick_result(release, ticked)
            runtime_restart = phase(target, args.java8, launcher, True, "runtime-restart")
            verdict["runtime_restart"] = compare(survival, runtime_restart)
            results[label] = verdict
            write_json(target / "actor-comparison.json", verdict)
            write_json(root / "actor-results.json", results)
            print(label + ": " + ", ".join(f"{key}={value['status']}" for key, value in verdict.items()), flush=True)
        if fingerprint(fixture / WORLD) != immutable: raise AssertionError("Generated actor fixture changed")
        if fingerprint(source / WORLD) != before: raise AssertionError("Historical source fixture changed")
    write_json(root / "scope.json", {"real_client_login": "NOT_TESTED", "runtime_behaviour": "Entity survival, ownership and contents after >=60 native ticks and full restart",
               "embedded_player": "Source-serialized NBT injected into level.dat; observed before dedicated-server host data removal",
               "player_path": "PlayerList.readPlayerDataFromFile -> real EntityPlayerMP -> IPlayerFileData.writePlayerData",
               "routes": len(results), "immutable_sources": "PASS"})
    return 1 if any(stage["status"] != "PASS" for route in results.values() for stage in route.values()) else 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workspace", type=Path, default=POWER.parents[1])
    parser.add_argument("--profile-manifest", type=Path, help="Hash-pinned profiles; optional bridge112 overrides only the intermediate runtime")
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--port", type=int, default=25616)
    parser.add_argument("--sources", nargs="+", choices=list(qualified_baselines()), default=list(qualified_baselines()))
    parser.add_argument("--routes", nargs="+", help="Optional route subset for focused regression checks")
    raise SystemExit(run(parser.parse_args()))
