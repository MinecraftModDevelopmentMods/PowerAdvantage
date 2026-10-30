"""Read-only resource equality for frozen migration/restart snapshots, not operation.

compare_resources accepts JSON paths, migration witness arrays, or connected
documents with a snapshot array. It does not launch anything or write evidence.
Root metadata is ignored; item identity, quantity, slot and item NBT are exact.
Pass each world's enchantment-id map when comparing legacy numeric enchantments.
"""

import argparse
import base64
import gzip
import io
import json
import math
from pathlib import Path
import struct

import nbtlib

from matrix import WORLD, plain
from native116 import native_normalized
from native116_legacy_snbt import parse_legacy_snbt


# Counts are container sizes, not highest valid slot numbers. Unknown containers
# still receive structural/quantity checks; callers can supply additional limits.
SLOT_LIMITS = {
    "minecraft:chest": 27, "minecraft:trapped_chest": 27, "minecraft:barrel": 27,
    "minecraft:hopper": 5, "minecraft:furnace": 3, "minecraft:blast_furnace": 3,
    "minecraft:smoker": 3, "minecraft:dispenser": 9, "minecraft:dropper": 9,
    "electricadvantage:electric_furnace": 10, "electricadvantage:electric_crusher": 9,
    "electricadvantage:electric_oven": 2, "electricadvantage:electric_fabricator": 15,
    "electricadvantage:electric_battery_array": 8, "electricadvantage:electric_drill": 6,
    "electricadvantage:growth_chamber": 9, "electricadvantage:growth_chamber_controller": 1,
    "electricadvantage:plastic_refinery": 1, "electricadvantage:electric_pump": 0,
    "electricadvantage:electric_still": 0, "electricadvantage:photovoltaic_generator": 0,
    "electricadvantage:hydroelectric_generator": 0, "electricadvantage:steam_powered_generator": 0,
    "electricadvantage:laser_turret": 0, "electricadvantage:laser_turret_evil": 0,
    "steamadvantage:steam_furnace": 7, "steamadvantage:steam_crusher": 6,
    "steamadvantage:steam_drill": 5, "steamadvantage:steam_boiler_coal": 1,
    "poweradvantage:coal_fired_still": 1,
    "poweradvantage:still": 1, "poweradvantage:fluid_discharge": 0, "poweradvantage:fluid_drain": 0,
    "poweradvantage:fluid_metal_tank": 1, "poweradvantage:fluid_storage_tank": 0,
    "poweradvantage:item_conveyor": 1, "poweradvantage:item_filter_inventory": 10,
    "poweradvantage:item_filter_block": 1, "poweradvantage:item_filter_food": 1,
    "poweradvantage:item_filter_fuel": 1, "poweradvantage:item_filter_ore": 1,
    "poweradvantage:item_filter_overflow": 1, "poweradvantage:item_filter_plant": 1,
    "poweradvantage:item_filter_smelt": 1, "steamadvantage:steam_boiler_electric": 0,
    "steamadvantage:steam_boiler_geothermal": 0, "steamadvantage:steam_boiler_oil": 0,
    "steamadvantage:steam_pump": 0, "steamadvantage:steam_still": 0, "steamadvantage:steam_tank": 0,
    "steamadvantage:steam_elevator": 0,
}
BATTERIES = {
    "electricadvantage:lead_acid_battery": 1000,
    "electricadvantage:nickel_hydride_battery": 2000,
    "electricadvantage:alkaline_battery": 4000,
    "electricadvantage:lithium_battery": 8000,
}
SCALARS = frozenset(("Electricity", "energy", "steam", "soil", "Temperature",
                     "BurnTime", "burnTime", "Fuel", "fuel", "FuelTime", "fuelTime"))
TANKS = frozenset(("Tank", "InputTank", "OutputTank", "TankIn", "TankOut", "WaterTank", "FuelTank"))


def _integer(value, label, minimum=0):
    if isinstance(value, bool) or not isinstance(value, int) or value < minimum:
        raise ValueError(label + ": invalid integer")
    return value


def _quantity(value, label):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or value < 0:
        raise ValueError(label + ": invalid/nonfinite/negative quantity")
    return value


def _rows(document):
    if isinstance(document, (str, Path)):
        document = json.loads(Path(document).read_text(encoding="utf-8"))
    if isinstance(document, dict):
        document = document.get("snapshot")
    if not isinstance(document, list) or not document:
        raise ValueError("Expected a nonempty migration witness array or connected snapshot")
    return document


def _nbt(row):
    values = []
    binary = None
    if "nbtBase64" in row:
        payload = gzip.decompress(base64.b64decode(row["nbtBase64"], validate=True))
        binary = nbtlib.File.parse(io.BytesIO(payload))
        values.append(plain(binary.unpack()))
    if "nbt" in row:
        # SNBT Float parsing keeps Python decimal precision until serialization.
        # Round through its declared NBT type, not a quantity tolerance.
        stream = io.BytesIO()
        nbtlib.File(parse_legacy_snbt(row["nbt"], binary)).write(stream)
        stream.seek(0)
        values.append(plain(nbtlib.File.parse(stream).unpack()))
    if not values:
        return {}
    if any(not isinstance(value, dict) for value in values) or any(value != values[0] for value in values[1:]):
        raise ValueError("Conflicting or non-compound raw NBT encodings")
    return values[0]


def _energy(value, label):
    if not isinstance(value, list):
        raise ValueError(label + ": expected float-bit int array")
    result = {}
    for channel, bits in enumerate(value):
        if isinstance(bits, bool) or not isinstance(bits, int) or not -(1 << 31) <= bits < (1 << 31):
            raise ValueError(label + ": invalid signed float-bit integer")
        amount = _quantity(struct.unpack(">f", struct.pack(">I", bits & 0xffffffff))[0], label)
        if amount != 0:
            result[str(channel)] = amount
    return result


def _tank(value, label):
    if not isinstance(value, dict):
        raise ValueError(label + ": expected tank compound")
    amount = value.get("Amount")
    if amount is not None:
        _integer(amount, label + ".Amount")
    if "Empty" in value:
        # native_normalized retains the established presence-based Empty convention.
        if amount not in (None, 0):
            raise ValueError(label + ": contradictory empty marker and positive fluid")
        return None
    if amount is None:
        raise ValueError(label + ": missing Amount/Empty evidence")
    fluid = value.get("FluidName", value.get("FluidID"))
    if not isinstance(fluid, str) or not fluid:
        raise ValueError(label + ": missing fluid identity")
    if "FluidID" in value and "FluidName" in value and value["FluidID"] != value["FluidName"]:
        raise ValueError(label + ": conflicting fluid identities")
    # A named zero-mB tank and native Empty are the same zero resource budget.
    if amount == 0:
        return None
    result = {"fluid": fluid, "amount": amount}
    if value.get("Tag"):
        result["tag"] = value["Tag"]
    return result


def _items(value, label, limit):
    if not isinstance(value, list):
        raise ValueError(label + ": expected inventory list")
    result = {}
    for entry in value:
        if not isinstance(entry, dict):
            raise ValueError(label + ": invalid stack compound")
        slot = _integer(entry.get("Slot"), label + ".Slot")
        if limit is not None and slot >= limit:
            raise ValueError(label + ": slot outside container size " + str(limit))
        if str(slot) in result:
            raise ValueError(label + ": duplicate inventory slot " + str(slot))
        count = _integer(entry.get("Count"), label + ".Count", 1)
        item = entry.get("id")
        if not isinstance(item, str) or not item or item == "minecraft:air" or count > 64:
            raise ValueError(label + ": invalid item identity/count")
        stack = {key: val for key, val in entry.items() if key != "Slot"}
        if item in BATTERIES:
            tag = stack.get("tag", {})
            if not isinstance(tag, dict):
                raise ValueError(label + ": invalid battery tag")
            # BatteryItem's established default is a fully charged new battery.
            charge = _quantity(tag.get("energy", BATTERIES[item]), label + ".battery.energy")
            if charge > BATTERIES[item]:
                raise ValueError(label + ": battery charge exceeds chemistry capacity")
            stack["tag"] = dict(tag, energy=charge)
        if stack.get("tag") == {}:
            stack.pop("tag")
        result[str(slot)] = stack
    return result


def _project(data, block, slot_limits):
    result = {}

    def visit(node, prefix=""):
        for key, value in node.items():
            path = prefix + key
            if key == "Energy":
                channels = _energy(value, path)
                for channel, amount in channels.items(): result[path + "[" + channel + "]"] = amount
            elif key in SCALARS:
                amount = _quantity(value, path)
                if amount != 0: result[path] = amount
            elif key == "Items":
                inventory = _items(value, path, slot_limits.get(block) if not prefix else None)
                for slot, item in inventory.items(): result[path + "[" + slot + "]"] = item
            elif key in TANKS or key.endswith("Tank"):
                tank = _tank(value, path)
                if tank is not None: result[path] = tank
            elif isinstance(value, dict):
                # Catch known resource fields inside capability compounds too, while
                # ignoring ordinary ForgeData/witness/custom-name metadata.
                visit(value, path + ".")

    visit(data)
    return result


def canonical_resources(document, *, enchantments=None, slot_limits=None):
    """Return per-position exact budgets; malformed evidence raises ValueError.

    No operation is permitted between the input snapshots. Zero optional reserves
    and empty inventories/tanks may be omitted by native serialization. Channel,
    tank, slot, fluid and item identities remain separate; station totals cannot
    conceal a transfer, duplication or loss. Unknown root metadata is ignored.
    """
    limits = dict(SLOT_LIMITS)
    if slot_limits:
        for name, limit in slot_limits.items():
            limits[name] = _integer(limit, "slot limit")
    result = {}
    for row in _rows(document):
        if not isinstance(row, dict): raise ValueError("Invalid witness row")
        pos = tuple(_integer(row.get(key), key, -(1 << 31)) for key in ("x", "y", "z"))
        if pos in result: raise ValueError("Duplicate witness position " + str(pos))
        identity = row.get("actualName", row.get("block"))
        if not isinstance(identity, str) or not identity: raise ValueError("Missing observed block identity")
        block = native_normalized({"id": identity})["id"]
        if block in limits and not ("nbtBase64" in row or "nbt" in row):
            raise ValueError("Missing NBT for known resource container " + str(pos))
        data = native_normalized(_nbt(row), enchantments)
        result[pos] = {"block": block, "resources": _project(data, block, limits)}
    return result


def compare_resources(before, after, *, before_enchantments=None, after_enchantments=None, slot_limits=None):
    """JSON-safe PASS/FAIL result. Does not mutate inputs or write any files."""
    frames, failures, differences = [], [], []
    for phase, document, enchantments in (("before", before, before_enchantments), ("after", after, after_enchantments)):
        try:
            frames.append(canonical_resources(document, enchantments=enchantments, slot_limits=slot_limits))
        except Exception as error:
            # Evidence/normalization errors must fail closed, not become an empty budget.
            frames.append(None)
            failures.append(phase + ": " + type(error).__name__ + ": " + str(error))
    if not failures:
        old, new = frames
        for pos in sorted(old.keys() | new.keys()):
            if pos not in old or pos not in new:
                differences.append(dict(position=list(pos), resource="witness", before=old.get(pos), after=new.get(pos)))
                continue
            if old[pos]["block"] != new[pos]["block"]:
                differences.append(dict(position=list(pos), resource="block", before=old[pos]["block"], after=new[pos]["block"]))
            a, b = old[pos]["resources"], new[pos]["resources"]
            for resource in sorted(a.keys() | b.keys()):
                if a.get(resource) != b.get(resource):
                    differences.append(dict(position=list(pos), resource=resource, before=a.get(resource), after=b.get(resource)))
    return dict(status="FAIL" if failures or differences else "PASS", failures=failures, differences=differences,
                positions=len(frames[0]) if frames[0] is not None else 0,
                scope="Exact per-position resource equality across a frozen migration/restart boundary; not operation")


def compare_snapshot_resources(before, after):
    def enchantments(snapshot):
        level = nbtlib.load(Path(snapshot).parent / WORLD / "level.dat")
        entries = level.get("FML", {}).get("Registries", {}).get("minecraft:enchantments", {}).get("ids", [])
        return {int(entry["V"]): str(entry["K"]) for entry in entries}
    return compare_resources(before, after, before_enchantments=enchantments(before),
                             after_enchantments=enchantments(after))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("before", type=Path)
    parser.add_argument("after", type=Path)
    args = parser.parse_args()
    result = compare_resources(args.before, args.after)
    print(json.dumps(result, indent=2, sort_keys=True))
    return int(result["status"] != "PASS")


if __name__ == "__main__":
    raise SystemExit(main())
