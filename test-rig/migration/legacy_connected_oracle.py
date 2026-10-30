"""Historical operating oracles only; no source creation or historical runtime defaults."""
import base64
from collections import Counter
import gzip
import io
import json
from pathlib import Path
import struct
import nbtlib
from rig_transport import WORLD, copy_fixture, digest, fingerprint, stage, write_json
from native112_code import Server


def typed_nbt(value):
    if isinstance(value, nbtlib.Compound): return {key: typed_nbt(tag) for key, tag in value.items()}
    if isinstance(value, nbtlib.List): return ["List", value.subtype.tag_id, [typed_nbt(tag) for tag in value]]
    plain = value.unpack()
    if hasattr(plain, "tolist"): plain = plain.tolist()
    return [type(value).__name__, plain]


def saved_tile_nbt(value):
    result = typed_nbt(value)
    for key in ("Tank", "InputTank", "TankIn", "TankOut", "FuelTank", "WaterTank"):
        tank = result.get(key)
        if (isinstance(tank, dict) and set(tank) == {"Amount", "FluidName"}
                and tank["Amount"] == ["Int", 0] and tank["FluidName"][0] == "String" and tank["FluidName"][1]):
            result[key] = {"Empty": ["Byte", 1]}
    return result


def parse_nbt(entry):
    return nbtlib.File.parse(io.BytesIO(gzip.decompress(base64.b64decode(entry["nbtBase64"])))).unpack()


def plain(value):
    if isinstance(value, dict): return {k: plain(v) for k, v in value.items()}
    if isinstance(value, (list, tuple)): return [plain(v) for v in value]
    if hasattr(value, "tolist"): return value.tolist()
    if hasattr(value, "item"): return value.item()
    return value


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
