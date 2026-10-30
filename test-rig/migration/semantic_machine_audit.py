"""Independent, read-only machine evidence checks; never starts or edits Minecraft.

Historical oracles stay frozen. This second opinion consumes their raw snapshots,
not their PASS labels. Progress counters alone are not completed mining work.
"""

import argparse
import base64
from collections import Counter
import gzip
import hashlib
import io
import json
import math
from pathlib import Path
import struct

import nbtlib


PRODUCTS = {
    "C-CONVEYOR": ("minecraft:dirt", "minecraft:dirt", 1728),
    "C-STEAM": ("minecraft:iron_ore", "minecraft:iron_ingot", 192),
    "C-ELECTRIC": ("minecraft:iron_ore", "minecraft:iron_ingot", 64),
}
DRILLS = {"C-SDRILL": "minecraft:gravel", "C-EDRILL": "minecraft:cobblestone"}
# Commissioning batch: four inputs must become four named products. These
# expectations do not import the implementation or the historical batch oracle.
BATCH_MACHINES = (
    ("S-04", (3, 1, 7), ((2, 1, 7), (4, 1, 7)), "steamadvantage:steam_furnace", "iron_ingot", True),
    ("S-04", (3, 1, 13), ((2, 1, 13), (4, 1, 13)), "steamadvantage:steam_crusher", "gravel", False),
    ("E-03", (6, 1, 6), (), "electricadvantage:electric_furnace", "iron_ingot", False),
    ("E-03", (3, 1, 6), (), "electricadvantage:electric_crusher", "gravel", False),
    ("E-03", (9, 1, 6), (), "electricadvantage:electric_oven", "cooked_beef", False),
)


def unpack(entry):
    if "nbtBase64" in entry:
        value = nbtlib.File.parse(io.BytesIO(gzip.decompress(base64.b64decode(entry["nbtBase64"], validate=True))))
    elif "nbt" in entry:
        value = nbtlib.parse_nbt(entry["nbt"])
    else:
        return {}
    return value.unpack()


def observed(document, definition):
    expected = {entry["id"]: entry for entry in definition["blocks"]}
    rows = document["snapshot"]
    if not rows or len(rows) != len(expected) or {row["id"] for row in rows} != set(expected):
        raise ValueError("Missing, duplicate or extra snapshot witnesses")
    inventories, water, tiles, targets = {}, Counter(), {}, Counter()
    for row in rows:
        witness = expected[row["id"]]
        if any(row.get(key) != witness.get(key) for key in ("station", "kind", "name", "x", "y", "z")):
            raise ValueError("Changed witness identity: " + row["id"])
        target = row["kind"].startswith("target-")
        if target:
            allowed = {row["name"], "minecraft:air"}
            if row["station"] == "C-SDRILL": allowed.add("steamadvantage:drillbit")
            if row.get("actualName") not in allowed:
                raise ValueError("Target replaced by an unrelated block: " + row["id"])
            if row["actualName"] != row["name"]: targets[row["station"]] += 1
            # A newly created drill-bit tile is a legitimate mining product,
            # not an original machine whose object/witness must be retained.
            inventories[row["id"]] = Counter()
            continue
        elif row.get("actualName") != row["name"]:
            raise ValueError("Fixture block lost/replaced: " + row["id"])
        data = unpack(row)
        if "nbtBase64" in row or "nbt" in row:
            if row.get("witness") != row["id"]:
                raise ValueError("Tile witness lost: " + row["id"])
            tiles[row["id"]] = (row, data)
        counts, slots = Counter(), set()
        for stack in data.get("Items", []):
            slot, count = stack.get("Slot"), stack.get("Count")
            if slot is None or slot in slots or not isinstance(count, int) or not 1 <= count <= 64:
                raise ValueError("Invalid stack quantity/slot: " + row["id"])
            slots.add(slot)
            counts[stack["id"]] += count
            if stack["id"] == "electricadvantage:lithium_battery":
                charge = stack.get("tag", {}).get("energy")
                if charge is None or not math.isfinite(charge) or not 0 <= charge <= 8000 or count != 1:
                    raise ValueError("Invalid/missing battery charge: " + row["id"])
        inventories[row["id"]] = counts
        for bits in data.get("Energy", []):
            energy = struct.unpack(">f", struct.pack(">I", int(bits) & 0xffffffff))[0]
            if not math.isfinite(energy) or energy < 0:
                raise ValueError("Invalid machine energy: " + row["id"])
        if row["kind"].startswith("water-") and not row["kind"].startswith("water-pipe-") and "Tank" not in data:
            raise ValueError("Missing water tank evidence: " + row["id"])
        if "Tank" in data:
            tank = data["Tank"]
            if tank.get("Empty") == 1 and "Amount" not in tank and "FluidName" not in tank:
                continue
            amount = tank.get("Amount")
            if not isinstance(amount, int) or amount < 0 or tank.get("FluidName") not in ("water", "minecraft:water"):
                raise ValueError("Malformed or wrong-fluid tank: " + row["id"])
            water[row["station"]] += amount
    station_counts = {}
    for row in rows:
        station_counts.setdefault(row["station"], Counter()).update(inventories[row["id"]])
    permitted = {
        "C-CONVEYOR": {"minecraft:dirt"},
        "C-STEAM": {"minecraft:iron_ore", "minecraft:iron_ingot", "minecraft:cobblestone", "minecraft:gravel", "minecraft:coal"},
        "C-ELECTRIC": {"minecraft:iron_ore", "minecraft:iron_ingot", "minecraft:coal", "electricadvantage:lithium_battery"},
        "C-SDRILL": {"minecraft:gravel", "minecraft:coal"},
        "C-EDRILL": {"minecraft:cobblestone", "minecraft:coal"},
    }
    for station, counts in station_counts.items():
        if set(counts) - permitted[station]: raise ValueError("Unexpected items in " + station)
    products = {}
    for station, (raw, product, total) in PRODUCTS.items():
        counts = station_counts[station]
        if (counts[raw] if raw == product else counts[raw] + counts[product]) != total:
            raise ValueError("Item conservation failed: " + station)
        products[station] = inventories[station + "-output"][product] if raw == product else counts[product]
    counts = station_counts["C-STEAM"]
    if counts["minecraft:cobblestone"] + counts["minecraft:gravel"] != 64:
        raise ValueError("Crusher input/output conservation failed")
    products["C-CRUSHER"] = counts["minecraft:gravel"]
    for station, item in DRILLS.items():
        drops = inventories[station + "-drill"] + inventories[station + "-rear"]
        if set(drops) - {item} or drops[item] != targets[station]:
            raise ValueError("Drill targets and exact product yield disagree: " + station)
        products[station] = drops[item]
    return dict(products=products, water=water, inventories=station_counts, tiles=tiles)


def connected_interval(before, after, definition, ticks):
    failures, incomplete, deltas = [], [], {}
    try:
        if not isinstance(ticks, int) or isinstance(ticks, bool) or ticks <= 0:
            raise ValueError("No positive operating interval")
        for document in (before, after):
            if document.get("held") is not True or document.get("buildCallsThisProcess") != 0:
                raise ValueError("Missing freeze/no-rebuild proof")
        if after.get("requestedTicks") != ticks or after.get("advancedTicks") != ticks:
            raise ValueError("Incomplete measured tick interval")
        if after.get("gameTime", 0) <= before.get("gameTime", 0):
            raise ValueError("World time did not advance")
        old, new = observed(before, definition), observed(after, definition)
        for station, count in new["water"].items():
            if count > old["water"].get(station, 0): raise ValueError("Finite water created: " + station)
        for station, counts in new["inventories"].items():
            if counts["minecraft:coal"] > old["inventories"][station]["minecraft:coal"]:
                raise ValueError("Fuel created: " + station)
        for key, (row, _) in old["tiles"].items():
            current = new["tiles"].get(key)
            if current is None or row.get("objectIdentity") is None or current[0].get("objectIdentity") != row["objectIdentity"]:
                raise ValueError("Tile replaced/missing within measured interval: " + key)
        totals = {station: total for station, (_, _, total) in PRODUCTS.items()}
        totals["C-CRUSHER"] = 64
        totals.update({station: sum(row["station"] == station and row["kind"].startswith("target-")
                                   for row in definition["blocks"]) for station in DRILLS})
        for station, previous in old["products"].items():
            delta = new["products"][station] - previous
            deltas[station] = delta
            if delta < 0: raise ValueError("Previously completed output lost: " + station)
            if previous < totals[station] and delta == 0:
                if station in DRILLS:
                    incomplete.append(station + ": no completed block/drop; progress counters are diagnostic only")
                else:
                    raise ValueError("No completed work: " + station)
    except (KeyError, TypeError, ValueError, OverflowError) as error:
        failures.append(str(error))
    return dict(status="FAIL" if failures else "INCOMPLETE" if incomplete else "PASS",
                failures=failures, incomplete=incomplete, completedOutputDeltas=deltas)


def audit_connected(root, ticks=512):
    routes = sorted(root.glob("route-*/target116"))
    if len(routes) != 15: raise ValueError("Expected all 15 native connected routes")
    results, hashes = {}, {}
    for route in routes:
        definition_path = route / "connected-fixture.json"
        definition = json.loads(definition_path.read_text())
        hashes[str(definition_path)] = hashlib.sha256(definition_path.read_bytes()).hexdigest().upper()
        for phase in ("converted", "restarted"):
            paths = [route / f"connected-{phase}-{state}.json" for state in ("loaded", "worked")]
            for path in paths: hashes[str(path)] = hashlib.sha256(path.read_bytes()).hexdigest().upper()
            before, after = [json.loads(path.read_text()) for path in paths]
            results[route.parent.name + "/" + phase] = connected_interval(before, after, definition, ticks)
    counts = Counter(result["status"] for result in results.values())
    return dict(scope="Read-only semantic recheck of recorded connected snapshots; not a new Minecraft run", ticks=ticks,
                results=results, counts=dict(counts), inputHashes=hashes,
                status="FAIL" if counts["FAIL"] else "INCOMPLETE" if counts["INCOMPLETE"] else "PASS")


def endurance_batch(documents, definitions):
    """Check measured endpoint facts, not the ledger's asserted output counts."""
    failures, frames = [], {}
    try:
        for station, document in documents.items():
            if document.get("station") != station or not document.get("snapshot"):
                raise ValueError("Missing or wrong station snapshot: " + station)
            frame = {}
            for row in document["snapshot"]:
                key = tuple(row["pos"])
                if key in frame: raise ValueError("Duplicate position: " + station)
                frame[key] = (row, unpack(row))
            frames[station] = frame

        def at(station, relative):
            absolute = tuple(a + b for a, b in zip(definitions[station]["origin"], relative))
            if absolute not in frames[station]: raise ValueError("Unobserved position: " + str((station, relative)))
            row, data = frames[station][absolute]
            if "nbt" not in row: raise ValueError("Missing tile evidence: " + str((station, relative)))
            return row, data

        def contents(station, relative):
            _, data = at(station, relative)
            counts, slots = Counter(), set()
            if "Items" not in data: raise ValueError("Missing inventory evidence")
            for stack in data["Items"]:
                if stack.get("Slot") in slots or "Slot" not in stack or not 1 <= stack.get("Count", 0) <= 64:
                    raise ValueError("Malformed inventory evidence")
                slots.add(stack["Slot"])
                counts[stack["id"]] += stack["Count"]
            return counts

        for station, pos, neighbours, block, product, fuel in BATCH_MACHINES:
            if at(station, pos)[0]["block"] != block: raise ValueError("Wrong processing machine")
            counts = sum((contents(station, p) for p in (pos, *neighbours)), Counter())
            if fuel: counts.pop("minecraft:coal", None)
            if counts != {"minecraft:" + product: 4}: raise ValueError("Incorrect exact processing yield: " + block)
        for pos in ((2, 1, 6), (3, 1, 6), (4, 1, 6)):
            if contents("W-04", pos): raise ValueError("Conveyor has not emptied")
        if contents("W-04", (5, 1, 6)) != {"minecraft:dirt": 16}: raise ValueError("Conveyor product not delivered")
        ghosts = {9: {1: ("minecraft:dirt", 1), 2: ("minecraft:cobblestone", 1), 3: ("minecraft:coal", 1)},
                  15: {}, 18: {1: ("minecraft:dirt", 1)}}
        for z in (9, 15, 18):
            if contents("W-04", (2, 1, z)): raise ValueError("Filter source not exhausted")
            # Reference slots are not throughput. They must nevertheless be
            # exactly retained, with no extra hidden buffered/ghost inventory.
            stacks = at("W-04", (3, 1, z))[1]["Items"]
            contents("W-04", (3, 1, z))
            actual = {s["Slot"]: (s["id"], s["Count"]) for s in stacks}
            if actual != ghosts[z]: raise ValueError("Filter reference/hidden inventory changed")
            forward = {"minecraft:apple": 8}
            if z == 15: forward["minecraft:dirt"] = 8
            if contents("W-04", (4, 1, z)) != forward: raise ValueError("Filter forward route incorrect")
            if contents("W-04", (3, 0, z)) != ({} if z == 15 else {"minecraft:dirt": 8}):
                raise ValueError("Filter lower route incorrect")
        fluids = Counter()
        for pos, key in (((4, 1, 8), "Tank"), ((6, 1, 8), "Tank"), ((8, 1, 8), "Tank"), ((6, 1, 8), "InputTank")):
            tank = at("W-03", pos)[1].get(key)
            if not isinstance(tank, dict): raise ValueError("Missing oil tank evidence")
            if tank.get("Empty") == 1 and "Amount" not in tank and "FluidName" not in tank: continue
            amount = tank.get("Amount")
            if not isinstance(amount, int) or amount < 0 or "FluidName" not in tank: raise ValueError("Malformed oil amount/identity")
            if amount: fluids[tank["FluidName"]] += amount
        if fluids != {"poweradvantage:refined_oil": 1000}: raise ValueError("Incorrect oil quantity/identity")
    except (KeyError, ValueError, TypeError) as error:
        failures.append(str(error))
    return dict(status="FAIL" if failures else "PASS", failures=failures)


def audit_endurance(root, definitions_path):
    definitions = {row["id"]: row for row in json.loads(definitions_path.read_text())["stations"]}
    stations = ("S-04", "E-03", "W-04", "W-03")
    labels = [f"cycle-{n:03d}" for n in range(1, 31)] + ["before-restart", "after-restart", "restart-resumed"]
    results, hashes = {}, {}
    for profile in ("fresh116", "original110-chained113-native116"):
        previous_time = -1
        for label in labels:
            documents = {}
            for station in stations:
                path = root / profile / "advantage-works-results" / f"{station}-checkpoint-{label}.json"
                hashes[str(path)] = hashlib.sha256(path.read_bytes()).hexdigest().upper()
                documents[station] = json.loads(path.read_text())
            result = endurance_batch(documents, definitions)
            current_time = documents["W-03"]["worldTime"]
            if current_time <= previous_time:
                result["status"] = "FAIL"
                result["failures"].append("Checkpoint world time did not advance")
            previous_time = current_time
            results[profile + "/" + label] = result
    counts = Counter(row["status"] for row in results.values())
    hashes[str(definitions_path)] = hashlib.sha256(definitions_path.read_bytes()).hexdigest().upper()
    return dict(scope="Independent endpoint recheck, five processors/three filter lanes/conveyor/oil; not a wall-clock, rate or all-machine proof",
                status="FAIL" if counts["FAIL"] else "PASS", counts=dict(counts), results=results, inputHashes=hashes)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    choice = parser.add_mutually_exclusive_group(required=True)
    choice.add_argument("--connected-root", type=Path)
    choice.add_argument("--endurance-root", type=Path)
    parser.add_argument("--definitions", type=Path,
                        default=Path(__file__).parents[1] / "src/main/resources/assets/advantageworkstest/works/works-1.13.2.json")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--ticks", type=int, default=512)
    args = parser.parse_args()
    result = audit_connected(args.connected_root, args.ticks) if args.connected_root else audit_endurance(args.endurance_root, args.definitions)
    # Never overwrite earlier good or bad evidence.
    with args.output.open("x", encoding="utf-8") as stream: json.dump(result, stream, indent=2)
    print(json.dumps({key: value for key, value in result.items() if key not in ("results", "inputHashes")}, indent=2))
    return int(result["status"] != "PASS")


if __name__ == "__main__":
    raise SystemExit(main())
