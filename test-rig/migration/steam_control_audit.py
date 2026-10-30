"""Independent offline Steam reload/control audit; never launches or edits a world.

Rechecks the complete declared all/steam operation group and its exact cold
restart, then derives the six extra control outcomes from typed snapshots.
PASS labels and duplicated scalar measurements are not sufficient evidence.
"""

import argparse
import base64
import gzip
import hashlib
import importlib
import io
import json
import math
from pathlib import Path
import struct

import nbtlib


LINES = ("110", "112", "113", "114", "115", "116")
PREFIX = "steam/lifecycle/"
COMPARATOR = PREFIX + "crusher-comparator-matrix"
CRUSHERS = {PREFIX + "crusher-first-loaded-" + name: (signal, steam)
            for name, signal, steam in (("unpowered", False, 40),
                                       ("signalled", True, 40), ("no-steam", False, 0))}
FURNACES = {PREFIX + "furnace-first-loaded-" + name: signal
            for name, signal in (("unpowered", False), ("signalled", True))}
IDS = {COMPARATOR} | set(CRUSHERS) | set(FURNACES)
INPUTS = {
    "catalog": "advantage-works-results/machine-catalog-measured.json",
    "operation": "advantage-works-results/machine-operation-measured.json",
    "fixture": "machine-operation-fixture.json",
    "cold": "advantage-works-results/machine-inspection-restarted.json",
}


def require(value, message):
    if not value:
        raise AssertionError(message)


def native(line):
    require(line in LINES, "Unsupported Minecraft line")
    return importlib.import_module("native" + line + "_machine_operations")


def indexed(document, label):
    rows = document.get("cases", [])
    require(isinstance(rows, list) and rows, "Missing " + label + " cases")
    result = {row["id"]: row for row in rows}
    require(len(result) == len(rows), "Duplicate " + label + " case")
    return result


def f32(value):
    return struct.unpack(">f", struct.pack(">f", value))[0]


def bits(value):
    return struct.unpack(">i", struct.pack(">f", value))[0]


def from_bits(value):
    number = struct.unpack(">f", struct.pack(">I", int(value) & 0xffffffff))[0]
    require(math.isfinite(number), "Non-finite saved float")
    return number


def metric(row, key, expected, integer=False):
    require(key in row, "Missing actual metric: " + key)
    value = row[key]
    if integer:
        require(type(value) is int and value == expected, "Wrong integer metric: " + key)
    else:
        require(type(value) in (int, float) and math.isfinite(value)
                and bits(value) == bits(expected), "Metric disagrees with saved outcome: " + key)


def nbt(row, line):
    if line == "110" or "nbtBase64" in row:
        require(isinstance(row.get("nbtBase64"), str), "Missing native binary NBT")
        raw = gzip.decompress(base64.b64decode(row["nbtBase64"], validate=True))
        data = nbtlib.File.parse(io.BytesIO(raw))
        if line != "110" and "nbt" in row:
            typed = native(line).typed_nbt
            require(typed(data) == typed(nbtlib.parse_nbt(row["nbt"])), "Binary/text NBT disagree")
    else:
        require(isinstance(row.get("nbt"), str), "Missing saved tile NBT")
        data = nbtlib.parse_nbt(row["nbt"])
    require(isinstance(data, nbtlib.Compound), "Tile NBT is not a compound")
    return data


def positions(rows, base, line):
    require(isinstance(rows, list) and rows, "Missing observed positions")
    result = {}
    for row in rows:
        require(all(type(row.get(k)) is int for k in ("x", "y", "z")), "Invalid position")
        key = tuple(row[k] for k in ("x", "y", "z"))
        require(key not in result, "Duplicate observed position")
        require(isinstance(row.get("block"), str) and isinstance(row.get("state"), str),
                "Missing observed block/state")
        value = {"block": row["block"], "state": row["state"]}
        if "nbt" in row or "nbtBase64" in row:
            value["nbt"] = base.typed_nbt(nbt(row, line))
        result[key] = value
    return result


def tile(rows, position, block, line):
    row = next((r for r in rows if tuple(r[k] for k in ("x", "y", "z")) == position), None)
    require(row is not None and row["block"] == block, "Missing/wrong control machine")
    data = nbt(row, line)
    for key, value in zip(("x", "y", "z"), position):
        require(isinstance(data.get(key), nbtlib.Int) and int(data[key]) == value,
                "Tile coordinates disagree with observed position")
    return data


def inventory(data, slot, item, count):
    stacks = data.get("Items")
    require(isinstance(stacks, nbtlib.List), "Missing typed inventory")
    require(len(stacks) == (1 if count else 0), "Unexpected input/output stack or lost input")
    if count:
        stack = stacks[0]
        require(isinstance(stack, nbtlib.Compound), "Malformed inventory stack")
        require(isinstance(stack.get("Slot"), nbtlib.Byte) and int(stack["Slot"]) == slot,
                "Wrong input slot/type")
        require(isinstance(stack.get("Count"), nbtlib.Byte) and int(stack["Count"]) == count,
                "Wrong input quantity/type")
        require(isinstance(stack.get("id"), nbtlib.String) and str(stack["id"]) == item,
                "Wrong input/output item")
        if "Damage" in stack:
            require(isinstance(stack["Damage"], nbtlib.Short) and int(stack["Damage"]) == 0,
                    "Wrong input metadata")


def energy(data):
    values = data.get("Energy")
    require(isinstance(values, nbtlib.IntArray) and len(values) == 1, "Missing typed single steam reserve")
    return from_bits(values[0])


def process(data):
    values = data.get("Data")
    require(isinstance(values, nbtlib.IntArray) and len(values) == 6, "Missing typed furnace process state")
    return [int(value) for value in values]


def progress(data):
    value = data.get("progress")
    require(isinstance(value, nbtlib.Short), "Crusher progress must be a saved short")
    return int(value)


def control_case(row, saved, declaration, base, line):
    index = saved.get("index")
    require(type(index) is int and index >= 0, "Missing station index")
    require(type(row.get("startTick")) is int and type(row.get("finishTick")) is int
            and row["finishTick"] - row["startTick"] == 1
            and type(row.get("requiredTicks")) is int and row["requiredTicks"] == 1
            and type(declaration.get("ticks")) is int and declaration["ticks"] == 1,
            "Control must observe exactly the first native tick")
    measured = positions(row["after"], base, line)
    require(measured == positions(saved["positions"], base, line),
            "Measured after-state differs from the cold fixture")
    origin = (50000 + index * 64, 80, 50000)
    def at(x, y=4, z=8):
        return (origin[0] + x, origin[1] + y, origin[2] + z)
    require(row.get("control.heldAtMeasuredEnd") is True, "Missing actual measured-end tile hold")
    witnesses = row.get("control.heldTiles")
    expected = {(x, 4, 8) for x in ((3, 9, 15, 21) if row["id"] == COMPARATOR else (8,))}
    require(isinstance(witnesses, list) and len(witnesses) == len(expected),
            "Missing/extra measured tile hold records")
    actual = set()
    for witness in witnesses:
        require(isinstance(witness, dict)
                and all(type(witness.get(key)) is int for key in ("x", "y", "z")),
                "Malformed measured tile hold coordinates")
        point = tuple(witness[key] for key in ("x", "y", "z"))
        require(point in expected and point not in actual, "Wrong/duplicate measured tile hold")
        require(all(witness.get(key) is True for key in ("removed", "sameTile", "nbtUnchanged")),
                "Tile hold must remove ticking, retain the same tile and leave NBT unchanged")
        observed = measured.get(at(*point))
        require(observed is not None and "nbt" in observed
                and observed["block"] == ("steamadvantage:steam_furnace" if row["id"] in FURNACES
                                          else "steamadvantage:steam_crusher"),
                "Held tile is not the measured native machine")
        actual.add(point)
    require(actual == expected, "Incomplete measured tile hold coverage")
    return at


def audit_documents(catalog, operation, fixture, cold, line):
    report = {"status": "FAIL", "line": line, "scope": "Six Steam first-tick/control cases plus full declared operation/cold checks",
              "cases": {}, "failures": []}
    try:
        base = native(line)
        group = catalog.get("group")
        require(group in ("all", "steam"), "A complete all/steam group is required, not a six-case subset")
        report["baseGroup"] = group
        report["operation"] = base.validate_operation(catalog, operation, group)
        require(report["operation"]["status"] == "PASS", "Base operation audit failed")
        observed = indexed(operation, "operation")
        saved = indexed(fixture, "fixture")
        restarted = indexed(cold, "restart")
        declared = indexed(catalog, "catalog")
        require(set(observed) == set(saved) == set(restarted) == set(declared),
                "Operation and cold fixture must cover the same complete case set")
        require(IDS <= set(observed), "Missing one or more of the six Steam control cases")
        report["cold"] = base.validate_restart(fixture, cold)
        require(report["cold"]["status"] == "PASS", "Base exact cold-restart audit failed")
        for case_id in sorted(IDS):
            row = observed[case_id]
            at = control_case(row, saved[case_id], declared[case_id], base, line)
            require(positions(saved[case_id]["positions"], base, line)
                    == positions(restarted[case_id]["positions"], base, line),
                    "Control typed NBT changed across cold restart")
            if case_id == COMPARATOR:
                counts, actual = row.get("comparator.inputs"), row.get("comparator.actual")
                require(isinstance(counts, list) and all(type(n) is int for n in counts)
                        and counts == [0, 1, 32, 64], "Missing/wrong comparator stock measurements")
                require(isinstance(actual, list) and all(type(n) is int for n in actual)
                        and len(actual) == 4, "Missing comparator output measurements")
                expected_positions = {at(3 + 6 * i) for i in range(4)}
                for stage in ("before", "after"):
                    require(set(positions(row[stage], base, line)) == expected_positions, "Wrong comparator layout")
                    for i, count in enumerate(counts):
                        data = tile(row[stage], at(3 + 6 * i), "steamadvantage:steam_crusher", line)
                        inventory(data, 0, "minecraft:stone", count)
                        require(energy(data) == 0 and progress(data) == 0, "Comparator witness must remain unpowered")
                for i, count in enumerate(counts):
                    metric(row, "count/" + str(3 + 6 * i) + "/4/8/minecraft:stone", count, integer=True)
                    wanted = 0 if count == 0 else 1 + 14 * count // 64
                    require(actual[i] == wanted, "Comparator output disagrees with typed inventory")
            else:
                is_crusher = case_id in CRUSHERS
                signal, initial = CRUSHERS[case_id] if is_crusher else (FURNACES[case_id], 40)
                require(row.get("firstLoaded.signalled") is signal, "Missing/wrong first-tick control measurement")
                expected_positions = {at(8), at(7)} if signal else {at(8)}
                for stage in ("before", "after"):
                    snapshot = positions(row[stage], base, line)
                    require(set(snapshot) == expected_positions, "Wrong controlled-machine layout")
                    if signal:
                        require(snapshot[at(7)]["block"] == "minecraft:redstone_block", "Missing actual redstone source")
                block = "steamadvantage:steam_crusher" if is_crusher else "steamadvantage:steam_furnace"
                before = tile(row["before"], at(8), block, line)
                after = tile(row["after"], at(8), block, line)
                item = "minecraft:stone" if is_crusher else "minecraft:sand"
                for data in (before, after):
                    inventory(data, 0 if is_crusher else 1, item, 1)
                require(energy(before) == initial, "Wrong saved initial steam reserve")
                metric(row, "count/8/4/8/" + item, 1, integer=True)
                metric(row, "count/8/4/8/minecraft:" + ("cobblestone" if is_crusher else "glass"), 0, integer=True)
                if is_crusher:
                    wanted = 105 if signal or initial == 0 else 107
                    reserve = 0 if initial == 0 else 39.9375 if signal else 38.4375
                    require(progress(before) == 106 and progress(after) == wanted, "Wrong saved first-tick crusher work")
                    metric(row, "firstLoaded.progress", progress(after), integer=True)
                else:
                    before_data, after_data = process(before), process(after)
                    require(before_data == [bits(.25), 0, 0, 10, 10, bits(1000)], "Wrong loaded furnace state")
                    heat = 989.5 if signal else 1027.0
                    work = f32(.25 + f32(f32(3.5e-6) * heat))
                    require(after_data == [bits(work), 0, 0, 9, 10, bits(heat)], "Wrong saved first-tick furnace work/heat/fuel")
                    metric(row, "firstLoaded.heat", from_bits(after_data[5]))
                    metric(row, "firstLoaded.progress", from_bits(after_data[0]))
                    reserve = 40 if signal else 39.5
                require(energy(after) == reserve, "Wrong saved first-tick steam consumption")
                metric(row, "firstLoaded.steam", energy(after))
                metric(row, "energy/8/4/8/steam", energy(after))
            report["cases"][case_id] = {"status": "PASS", "heldTilesChecked": len(row["control.heldTiles"]),
                                      "comparison": "explicit tile holds and raw metrics checked against typed before/after state"}
        report["status"] = "PASS"
    except Exception as error:
        report["failures"].append(str(error))
    return report


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, "Duplicate JSON key: " + key)
        result[key] = value
    return result


def audit(runtime, line):
    runtime = Path(runtime).resolve()
    raw = {name: (runtime / path).read_bytes() for name, path in INPUTS.items()}
    documents = {name: json.loads(value, object_pairs_hook=unique_object) for name, value in raw.items()}
    report = audit_documents(**documents, line=line)
    report["runtime"] = str(runtime)
    report["inputSha256"] = {INPUTS[name]: hashlib.sha256(value).hexdigest().upper() for name, value in raw.items()}
    report["auditorSha256"] = hashlib.sha256(Path(__file__).read_bytes()).hexdigest().upper()
    report["baseValidatorSha256"] = hashlib.sha256(Path(native(line).__file__).read_bytes()).hexdigest().upper()
    for name, value in raw.items():
        require((runtime / INPUTS[name]).read_bytes() == value, "Raw evidence changed during audit")
    return report


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime", type=Path, required=True)
    parser.add_argument("--line", choices=LINES, required=True)
    args = parser.parse_args(argv)
    try:
        report = audit(args.runtime, args.line)
    except Exception as error:
        report = {"status": "FAIL", "line": args.line, "runtime": str(args.runtime), "failures": [str(error)]}
    print(json.dumps(report, indent=2, sort_keys=True, allow_nan=False))
    return int(report["status"] != "PASS")


if __name__ == "__main__":
    raise SystemExit(main())
