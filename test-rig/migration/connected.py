"""Run connected legacy machinery, migrate untouched copies, and resume it."""
import argparse
from collections import Counter
import json
from pathlib import Path
import shutil
import socket
import struct

from matrix import (POWER, RIG, WORLD, Server, compare, copy_fixture, digest, fingerprint,
                    intermediate_profile, parse_nbt, plain, stage, write_json)

SOURCES = ("original110", "early110", "maintained110", "legacy112", "maintained112")


def fixture(source):
    blocks = []
    def put(station, role, x, z, name, seed=None, meta=0, y=80):
        entry = dict(id=f"{station}-{role}", station=station, kind=role, x=x, y=y, z=z,
                     name=name, metadata=meta)
        if seed is not None:
            entry["seed"] = seed
        blocks.append(entry)
    def items(entries):
        return "{Items:[" + ",".join('{Slot:%db,id:"%s",Count:%db,Damage:0s}' % e for e in entries) + "]}"
    def supply(station, x, n):
        for i in range(n):
            bx = x + 1 + i * 4
            put(station, f"water-{i}", bx, 50, "poweradvantage:fluid_metal_tank",
                '{Tank:{FluidName:"water",Amount:10000}}')
            put(station, f"water-pipe-{i}", bx, 49, "poweradvantage:fluid_pipe")
            put(station, f"boiler-{i}", bx, 48, "steamadvantage:steam_boiler_coal",
                items([(0, "minecraft:coal", 32)]))
        for dx in range(1, 13):
            put(station, f"steam-pipe-{dx}", x + dx, 47, "steamadvantage:steam_pipe")
    station = "C-CONVEYOR"
    put(station, "input", 1, 40, "minecraft:chest", items([(i, "minecraft:dirt", 64) for i in range(27)]))
    put(station, "belt-1", 2, 40, "poweradvantage:item_conveyor", meta=5)
    put(station, "belt-2", 3, 40, "poweradvantage:item_conveyor", meta=5)
    put(station, "output", 4, 40, "minecraft:chest")
    station = "C-STEAM"
    supply(station, 64, 3)
    put(station, "storage", 76, 46, "steamadvantage:steam_tank")
    put(station, "furnace", 68, 46, "steamadvantage:steam_furnace",
        items([(0, "minecraft:coal", 32)] + [(i, "minecraft:iron_ore", 64) for i in (1, 2, 3)]))
    put(station, "crusher", 74, 46, "steamadvantage:steam_crusher", items([(0, "minecraft:cobblestone", 64)]))
    station = "C-ELECTRIC"
    supply(station, 128, 2)
    put(station, "generator", 141, 47, "electricadvantage:steam_powered_generator")
    for x in (142, 144):
        put(station, f"wire-{x}", x, 47, "electricadvantage:electric_conduit")
    put(station, "battery", 143, 47, "electricadvantage:electric_battery_array",
        '{Items:[{Slot:0b,id:"electricadvantage:lithium_battery",Count:1b,Damage:0s,tag:{energy:0.0f}}]}')
    put(station, "furnace", 145, 47, "electricadvantage:electric_furnace", items([(0, "minecraft:iron_ore", 64)]))
    for station, origin, electric in (("C-SDRILL", 192, False), ("C-EDRILL", 256, True)):
        supply(station, origin, 2)
        if electric:
            put(station, "generator", origin + 13, 47, "electricadvantage:steam_powered_generator")
            pipe = "electricadvantage:electric_conduit"
        else:
            put(station, "steam-link", origin + 13, 47, "steamadvantage:steam_pipe")
            pipe = "steamadvantage:steam_pipe"
        for dx in (14, 15):
            put(station, f"power-link-{dx}", origin + dx, 47, pipe)
        for z in range(43, 47):
            put(station, f"side-link-{z}", origin + 15, z, pipe)
        put(station, "drill", origin + 14, 43, "electricadvantage:electric_drill" if electric else "steamadvantage:steam_drill", meta=2)
        put(station, "rear", origin + 14, 44, "minecraft:chest")
        for layer in range(32 if electric else 63):
            for dx in (-1, 0, 1) if electric else (0,):
                for dy in (-1, 0, 1) if electric else (0,):
                    put(station, f"target-{layer}-{dx + 1}-{dy + 1}", origin + 14 + dx, 42 - layer,
                        "minecraft:cobblestone", y=80 + dy)
    return dict(format=1, sourceProfile=source, bounds=[0, 78, -32, 280, 84, 64], blocks=blocks)


def read(runtime, label):
    return json.loads((runtime / f"connected-{label}.json").read_text())


def entries(document):
    return {e["id"]: e for e in document["snapshot"]}


def data(entry):
    return plain(parse_nbt(entry)) if "nbtBase64" in entry else {}


def stocks(document, station, kinds=None):
    counts = Counter()
    for e in document["snapshot"]:
        if e["station"] == station and (kinds is None or e["kind"] in kinds):
            for stack in data(e).get("Items", []):
                counts[stack["id"]] += int(stack["Count"])
    return counts


def state(document):
    failures, cases, water = [], {}, Counter()
    for station, raw, product, total in (("C-CONVEYOR", "minecraft:dirt", "minecraft:dirt", 1728),
                                       ("C-STEAM", "minecraft:iron_ore", "minecraft:iron_ingot", 192),
                                       ("C-ELECTRIC", "minecraft:iron_ore", "minecraft:iron_ingot", 64)):
        count = stocks(document, station)
        accounted = count[raw] if raw == product else count[raw] + count[product]
        if accounted != total:
            failures.append(f"{station}: expected {total} {raw}/{product}, got {accounted}")
        produced = stocks(document, station, {"output"})[product] if raw == product else count[product]
        cases[station] = dict(produced=produced, remaining=total - produced, accounted=accounted)
    count = stocks(document, "C-STEAM")
    if count["minecraft:cobblestone"] + count["minecraft:gravel"] != 64:
        failures.append("C-STEAM: crusher input/output lost or duplicated")
    cases["C-CRUSHER"] = dict(produced=count["minecraft:gravel"], remaining=count["minecraft:cobblestone"])
    for station in ("C-SDRILL", "C-EDRILL"):
        targets = [e for e in document["snapshot"] if e["station"] == station and e["kind"].startswith("target-")]
        remaining = sum(e["actualName"] == e["name"] for e in targets)
        mined = len(targets) - remaining
        drops = stocks(document, station, {"drill", "rear"})
        output = sum(drops.values())
        if output != mined:
            failures.append(f"{station}: mined {mined} blocks but retained {output} drops")
        product = "minecraft:gravel" if station == "C-SDRILL" else "minecraft:cobblestone"
        if set(drops) - {product}:
            failures.append(station + ": unexpected drill product identity")
        cases[station] = dict(produced=output, remaining=remaining, targets=len(targets))
    for e in document["snapshot"]:
        if "nbtBase64" not in e or e["kind"].startswith("target-"):
            continue
        if e.get("witness") != e["id"]:
            failures.append(e["id"] + ": source tile witness lost/replaced")
        for value in data(e).get("Energy", []):
            number = struct.unpack("!f", struct.pack("!I", int(value) & 0xffffffff))[0]
            if not (0 <= number < 1000000):
                failures.append(e["id"] + ": invalid energy")
        tank = data(e).get("Tank", {})
        if int(tank.get("Amount", 0)) > 0:
            if tank.get("FluidName") not in ("water", "minecraft:water"):
                failures.append(e["id"] + ": unexpected fluid")
            water[e["station"]] += int(tank["Amount"])
    charge = [s.get("tag", {}).get("energy", 0) for s in data(entries(document)["C-ELECTRIC-battery"]).get("Items", [])]
    cases["battery"] = dict(charge=sum(charge))
    return dict(status="FAIL" if failures else "PASS", failures=failures, cases=cases, water=dict(water))


def cold_state(document):
    verdict = state(document)
    if sum(verdict["water"].values()) != 90000:
        verdict["failures"].append("Source must start with exactly 90,000 mB water")
    for e in document["snapshot"]:
        if e["name"].startswith(("steamadvantage:", "electricadvantage:")):
            if any(data(e).get("Energy", [])):
                verdict["failures"].append(e["id"] + ": nonzero initial power reserve")
    if verdict["cases"]["battery"]["charge"] != 0:
        verdict["failures"].append("Initial battery is not empty")
    verdict["status"] = "FAIL" if verdict["failures"] else "PASS"
    return verdict


def operation(before, after, ticks):
    verdict = state(after)
    previous = state(before)
    for station, amount in verdict["water"].items():
        if amount > previous["water"].get(station, 0):
            verdict["failures"].append(station + ": finite water was created")
    if not after.get("held") or after["requestedTicks"] != ticks or after["advancedTicks"] != ticks:
        verdict["failures"].append("Missing exact native tick interval/freeze proof")
    for name, case in previous["cases"].items():
        if name == "battery":
            continue
        current = verdict["cases"][name]
        if case["remaining"] > 0 and current["produced"] <= case["produced"]:
            # A nine-block electric drilling cycle can span an observation window.
            old = data(entries(before).get(name + "-drill", {})).get("progress", 0)
            new = data(entries(after).get(name + "-drill", {})).get("progress", 0)
            if name not in ("C-SDRILL", "C-EDRILL") or new <= old:
                verdict["failures"].append(name + ": no further work from retained inputs")
    old_entries = entries(before)
    for key, entry in entries(after).items():
        if "objectIdentity" in old_entries[key] and not entry["kind"].startswith("target-"):
            if entry.get("objectIdentity") != old_entries[key]["objectIdentity"]:
                verdict["failures"].append(key + ": tile replaced during the observation interval")
    verdict["status"] = "FAIL" if verdict["failures"] else "PASS"
    return verdict


def pins(base, output):
    profiles = json.loads(base.read_text())
    changes = []
    for name, profile in profiles.items():
        for index, jar in enumerate(profile["jars"]):
            original = Path(jar["path"])
            if digest(original) != jar["sha256"]:
                raise AssertionError("Changed archived input: " + str(original))
            path = original
            if original.name.startswith("AdvantageMigrationProbe-"):
                target = original.name.split("-")[1]
                path = RIG / f"migration-probe/build/{target}/libs" / original.name
            elif original.name.startswith("AdvantageWorksTestHarness-"):
                path = RIG / "build/libs" / original.name
            actual = digest(path)
            archived = output / "inputs" / actual / path.name
            archived.parent.mkdir(parents=True, exist_ok=True)
            if not archived.exists():
                shutil.copy2(path, archived)
            if digest(archived) != actual:
                raise AssertionError("Archive changed")
            if path != original:
                changes.append(dict(profile=name, previous=jar, replacement=str(path), sha256=actual))
            profile["jars"][index] = dict(path=str(archived.resolve()), sha256=actual)
    write_json(output / "profiles.json", profiles)
    write_json(output / "input-provenance.json", dict(base=str(base.resolve()), sha256=digest(base),
               replacements=changes, production_and_dependency_pins="UNCHANGED"))
    return {name: (Path(p["seed"]), p["launcher"], [Path(j["path"]) for j in p["jars"]]) for name, p in profiles.items()}


def prepare(runtime, profile, definition, port, source=None):
    launcher = stage(runtime, profile)
    if source is not None:
        copy_fixture(source, runtime)
        definition = json.loads((source / "connected-fixture.json").read_text())
    else:
        write_json(runtime / "migration-fixture.json", definition["blocks"])
    write_json(runtime / "connected-fixture.json", definition)
    for marker in ("advantage-connected.allow", "migration-operate.allow"):
        (runtime / marker).touch()
    props = runtime / "server.properties"
    props.write_text(props.read_text().replace("server-port=25584", f"server-port={port}")
                     + "spawn-monsters=false\nspawn-animals=false\n")
    return launcher


class ConnectedServer(Server):
    def __init__(self, *args):
        self.commands = []
        super().__init__(*args)

    def send(self, command):
        if command != "stop" and not command.startswith(("advconnected ", "save-all")):
            raise AssertionError("Forbidden connected-world mutation: " + command)
        self.commands.append(command)
        super().send(command)

    def observe(self, operation_name, label, ticks=0):
        self.send(f"advconnected step {ticks} {label}" if ticks else f"advconnected {operation_name} {label}")
        self.wait("ADVCONNECTED COMPLETE " + label, max(120, ticks / 20 + 90))

    def close(self):
        try:
            super().close()
        finally:
            path = Path(self.log.name)
            write_json(path.with_suffix(".commands.json"), self.commands)


def boot(runtime, args, launcher, label):
    return ConnectedServer(runtime, args.java8, launcher, label)


def resume(runtime, args, launcher, label):
    server = boot(runtime, args, launcher, label)
    try:
        server.observe("snapshot", label + "-loaded")
        loaded = read(runtime, label + "-loaded")
        server.observe("step", label + "-worked", args.ticks)
        worked = read(runtime, label + "-worked")
        verdict = operation(loaded, worked, args.ticks)
        if worked["buildCallsThisProcess"] != 0:
            verdict["status"] = "FAIL"
            verdict["failures"].append("Equipment rebuilt")
        server.send("save-all flush")
    finally:
        server.close()
    return verdict


def run(args):
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", args.port))
    args.output.mkdir(parents=True, exist_ok=False)
    available = pins(args.profile_manifest, args.output)
    report = dict(status="RUNNING", sources={}, routes={},
                  protocol="Source-only construction; finite fuel/water; target snapshot/step only; no block/inventory/energy/network resets")
    try:
        for name in args.sources:
            print("BUILD AND RUN " + name, flush=True)
            definition = fixture(name)
            source = args.output / name
            launcher = prepare(source, available[name], definition, args.port)
            (source / "connected-build.allow").touch()
            server = boot(source, args, launcher, "source")
            try:
                server.observe("build", "cold")
                cold = read(source, "cold")
                if cold_state(cold)["status"] != "PASS":
                    raise AssertionError("Invalid cold source fixture: " + json.dumps(cold_state(cold)))
                server.observe("step", "source", args.warmup)
                actual = read(source, "source")
                qualified = operation(cold, actual, args.warmup)
                if qualified["cases"]["battery"]["charge"] <= 0:
                    qualified["failures"].append("Battery did not charge from real generation")
                    qualified["status"] = "FAIL"
                report["sources"][name] = qualified
                server.send("save-all flush")
            finally:
                server.close()
            write_json(args.output / "connected-results.json", report)
            if qualified["status"] != "PASS":
                raise AssertionError("Legacy source did not operate: " + json.dumps(qualified))
            source_hashes = fingerprint(source / WORLD)
            write_json(source / "fixture-hashes.json", source_hashes)
            for route in ("direct", "chained") if name.endswith("110") else ("direct",):
                label = name + "-" + route
                if args.routes and label not in args.routes:
                    continue
                print("IMPORT AND RESUME " + label, flush=True)
                previous, checkpoint = source, "source"
                result = {}
                if route == "chained":
                    middle = args.output / (label + "-112")
                    launcher112 = prepare(middle, intermediate_profile(available), definition, args.port, source)
                    result["intermediate_operation"] = resume(middle, args, launcher112, "intermediate")
                    result["intermediate_preservation"] = compare(source / "migration-source.json", middle / "migration-intermediate-loaded.json")
                    result["intermediate_restart"] = resume(middle, args, launcher112, "intermediate-restart")
                    result["intermediate_restart_preservation"] = compare(middle / "migration-intermediate-worked.json", middle / "migration-intermediate-restart-loaded.json")
                    previous, checkpoint = middle, "intermediate-restart-worked"
                target = args.output / (label + "-113")
                launcher113 = prepare(target, available["target113"], definition, args.port, previous)
                result["operation"] = resume(target, args, launcher113, "converted")
                result["preservation"] = compare(previous / ("migration-" + checkpoint + ".json"), target / "migration-converted-loaded.json")
                result["restart_operation"] = resume(target, args, launcher113, "restarted")
                result["restart_preservation"] = compare(target / "migration-converted-worked.json", target / "migration-restarted-loaded.json")
                first = state(read(target, "converted-loaded"))
                last = state(read(target, "restarted-worked"))
                missing = [key for key, case in first["cases"].items() if key != "battery" and case["remaining"] > 0
                           and last["cases"][key]["produced"] <= case["produced"]]
                result["real_output_across_restarts"] = dict(status="FAIL" if missing else "PASS", failures=missing)
                result["status"] = "PASS" if all(v["status"] == "PASS" for v in result.values()) else "FAIL"
                report["routes"][label] = result
                write_json(args.output / "connected-results.json", report)
                print(label + ": " + result["status"], flush=True)
            if fingerprint(source / WORLD) != source_hashes:
                raise AssertionError("Immutable connected source fixture changed")
        report["status"] = "PASS" if report["routes"] and all(r["status"] == "PASS" for r in report["routes"].values()) else "FAIL"
    except BaseException as error:
        report.update(status="FAIL", failure=str(error))
        raise
    finally:
        write_json(args.output / "connected-results.json", report)
    print(report["status"] + ": " + str(args.output), flush=True)
    return int(report["status"] != "PASS")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile-manifest", type=Path, default=POWER / "run-qualification/20261023-current/profiles-archived.json")
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--sources", nargs="+", choices=SOURCES, default=list(SOURCES))
    parser.add_argument("--routes", nargs="+")
    parser.add_argument("--port", type=int, default=25655)
    parser.add_argument("--warmup", type=int, default=803)
    parser.add_argument("--ticks", type=int, default=512)
    raise SystemExit(run(parser.parse_args()))
