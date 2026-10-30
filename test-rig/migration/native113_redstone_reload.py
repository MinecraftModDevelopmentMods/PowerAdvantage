"""Finite mid-obsidian work, frozen holds, then per-tick operation after a real process restart.

Separate protocol: never changes historical lifecycle helpers or reconstructs a restarted world.
Pin preserves every base production jar unless --electric-jar is explicitly supplied.
"""

import argparse
import copy
from datetime import datetime, timezone
import json
import math
from pathlib import Path
import shutil
import socket
import struct
import traceback

import nbtlib

from matrix import WORLD, digest, fingerprint, write_json
from native113 import output_path, pinned_profile, require_disjoint
from native113_code import Server, INVENTORY, verify_code
from native113_lifecycle import prepare, read_json, require_native_boot, verify_staged


OBSERVATION_TICKS = 16
READINESS_TICKS = 80
TARGET_KEYS = {"targetX", "targetY", "targetZ", "TargetStates"}
MUTABLE_KEYS = TARGET_KEYS | {"progress", "Energy"}


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def definitions():
    result = []
    for i in range(9):
        offset = i if i < 8 else 0
        result.append(dict(id="held-" + str(i) if i < 8 else "unheld", held=i < 8, offset=offset,
                           pos=[60000 + i * 64 + 14 + ((offset >> 1) & 1),
                                80 + (offset & 1), 60000 + ((offset >> 2) & 1)]))
    return result


def typed(value):
    if isinstance(value, nbtlib.Compound):
        return {key: typed(tag) for key, tag in value.items()}
    if isinstance(value, nbtlib.List):
        return ["List", value.subtype.tag_id, [typed(tag) for tag in value]]
    raw = value.unpack()
    if hasattr(raw, "tolist"):
        raw = raw.tolist()
    return [type(value).__name__, raw]


def parsed(row):
    try:
        return nbtlib.parse_nbt(row["nbt"])
    except Exception as error:
        raise AssertionError("Invalid raw tile NBT: " + str(error)) from error


def rows_by_id(rows, holds):
    expected = definitions()
    require(isinstance(rows, list) and len(rows) == 9, "Missing nine independent witnesses")
    require(len({row["id"] for row in rows}) == 9, "Duplicate witness")
    actual = {row["id"]: row for row in rows}
    require(set(actual) == {case["id"] for case in expected}, "Wrong witness IDs")
    for case in expected:
        row = actual[case["id"]]
        key = case["id"]
        require(all(row.get(name) == value for name, value in case.items()), key + ": changed position/phase/declaration")
        x, y, z = row["pos"]
        require(((z & 1) << 2 | (x & 1) << 1 | (y & 1)) == row["offset"], key + ": false scheduler phase")
        signal = holds and case["held"]
        require(row.get("signal") is signal, key + ": actual neighbor signal differs")
        require(row.get("holdBlock") == ("minecraft:redstone_block" if signal else "minecraft:air"), key + ": wrong hold block")
        require(row.get("block") == "electricadvantage:electric_drill", key + ": stationary drill changed")
        require(type(row.get("active")) is bool, key + ": missing actual ACTIVE")
        state = "Block{electricadvantage:electric_drill}[active=" + str(row["active"]).lower() + ",facing=north]"
        require(row.get("state") == state, key + ": raw block state disagrees with ACTIVE/facing")
        require(isinstance(row.get("energy"), (int, float)) and math.isfinite(row["energy"])
                and 0 <= row["energy"] <= 500, key + ": invalid finite reserve")
        require(type(row.get("progress")) is int and 0 <= row["progress"] < 14400, key + ": invalid mid-obsidian progress")
        targets = row.get("targets", [])
        expected_targets = {(x + dx, y + dy, z - 1): "Block{minecraft:obsidian}"
                            for dx in range(-1, 2) for dy in range(-1, 2)}
        require(len(targets) == 9 and {tuple(t["pos"]): t["state"] for t in targets} == expected_targets,
                key + ": real 3x3 obsidian target changed")
        tag = parsed(row)
        require(set(tag).issubset({"id", "x", "y", "z", "Items"} | MUTABLE_KEYS), key + ": unexpected saved state")
        require(str(tag.get("id")) == row["block"] and [int(tag.get(axis, -1)) for axis in ("x", "y", "z")] == row["pos"],
                key + ": serialized position/identity changed")
        energy = tag.get("Energy")
        require(isinstance(energy, nbtlib.IntArray) and len(energy) == 1, key + ": wrong energy storage")
        value = struct.unpack(">f", struct.pack(">i", int(energy[0])))[0]
        require(value == row["energy"], key + ": raw energy and measurement disagree")
        require(isinstance(tag.get("progress"), nbtlib.Short) and int(tag["progress"]) == row["progress"],
                key + ": raw progress and measurement disagree")
        expected_items = nbtlib.parse_nbt('{Items:[{Slot:0b,id:"minecraft:dirt",Count:7b,tag:{AdvWorksWitness:"' + key
                                        + '"}},{Slot:1b,id:"minecraft:apple",Count:13b}]}')["Items"]
        require("Items" in tag and typed(tag["Items"]) == typed(expected_items), key + ": inventory/witness changed")
        present = set(tag) & TARGET_KEYS
        require(not present or present == TARGET_KEYS, key + ": partial target serialization")
        if present:
            require([int(tag[axis]) for axis in ("targetX", "targetY", "targetZ")] == [x, y, z - 1], key + ": target moved")
            states = tag["TargetStates"]
            require(isinstance(states, nbtlib.List) and len(states) == 9
                    and all(typed(s) == {"Name": ["String", "minecraft:obsidian"]} for s in states), key + ": target oracle changed")
    return actual


def same_saved(left, right):
    a, b = dict(left), dict(right)
    a["nbt"], b["nbt"] = typed(parsed(left)), typed(parsed(right))
    return a == b


def clock(frame, previous_time, index):
    require(frame.get("index") == index, "Missing/reordered real tick index")
    require(frame.get("tickStartWorldTime") == previous_time and frame.get("worldTime") == previous_time + 1,
            "Missing, repeated or gapped real world tick")


def validate_readiness(document, phase, release_time):
    samples = document.get("readiness", [])
    require(1 <= len(samples) <= READINESS_TICKS, "Missing/beyond-budget native readiness observations")
    expected = {case["id"]: case["pos"] for case in definitions()}
    previous_time = None
    for index, sample in enumerate(samples):
        require(sample.get("phase") == phase, "Wrong readiness phase")
        now = sample.get("worldTime")
        require(type(now) is int and (previous_time is None or now == previous_time + 1),
                "Readiness did not observe consecutive paused real ticks")
        rows = sample.get("cases", [])
        require(len(rows) == 9 and len({row["id"] for row in rows}) == 9
                and {row["id"]: row["pos"] for row in rows} == expected, "Incomplete readiness witness set")
        require(all(type(row.get(key)) is bool for row in rows for key in ("blockTicking", "tickableListed")),
                "Missing native/list readiness measurements")
        ready = all(row["blockTicking"] and row["tickableListed"] for row in rows)
        require(ready is (index == len(samples) - 1), "Release was not the first all-native-ready sample")
        previous_time = now
    require(previous_time == release_time, "Operated before/after the independently selected readiness boundary")


def validate_seed(fixture):
    require(fixture.get("schema") == 1 and fixture.get("constructionCallsThisProcess") == 9,
            "Not nine fresh-only constructed seeds")
    require(fixture.get("initialEnergy") == 500 and fixture.get("processToken"), "Missing finite seed authority")
    initial = fixture["initial"]
    validate_readiness(fixture, "seed", initial["worldTime"])
    previous = rows_by_id(initial["cases"], False)
    for row in previous.values():
        require(row["energy"] == 500 and row["progress"] == 0 and not row["active"], "Seed did not begin empty of work")
        require(not (set(parsed(row)) & TARGET_KEYS), "Initial setup injected a mining target")
    previous_time = initial["worldTime"]
    trace = fixture.get("seedTrace", [])
    require(1 <= len(trace) <= 16, "No bounded natural seed trace")
    for index, frame in enumerate(trace, 1):
        clock(frame, previous_time, index)
        current = rows_by_id(frame["cases"], False)
        for key, row in current.items():
            old = previous[key]
            advance = row["progress"] - old["progress"]
            require(advance in (0, 1) and old["energy"] - row["energy"] == 24 * advance,
                    key + ": seed work was not earned with real debit")
            had_target, has_target = bool(set(parsed(old)) & TARGET_KEYS), bool(set(parsed(row)) & TARGET_KEYS)
            if not had_target:
                require(advance == 0, key + ": mining before target acquisition")
                require(not has_target or (frame["worldTime"] + row["offset"]) % 8 == 0,
                        key + ": target appeared outside a native power-update slot")
            else:
                require(has_target and advance == 1, key + ": seed stopped operating")
        previous, previous_time = current, frame["worldTime"]
    before = rows_by_id(fixture["preHold"], False)
    frozen = rows_by_id(fixture["cases"], True)
    require(fixture.get("worldTime") == previous_time, "Freeze did not follow natural work immediately")
    for key, row in before.items():
        require(same_saved(row, previous[key]), key + ": seed changed before the hold")
        require(row["progress"] > 0 and row["energy"] > 24 and row["active"]
                and set(parsed(row)) & TARGET_KEYS == TARGET_KEYS, key + ": not powered mid-obsidian")
        expected = dict(row, signal=row["held"], holdBlock="minecraft:redstone_block" if row["held"] else "minecraft:air")
        require(same_saved(expected, frozen[key]), key + ": adding hold altered saved work/resources")
    return dict(status="PASS", cases=9, naturalSeedTicks=len(trace), phases=list(range(8)))


def validate_reload(fixture, report):
    validate_seed(fixture)
    require(report.get("schema") == 1 and report.get("constructionCallsThisProcess") == 0,
            "Restart reconstructed the world")
    require(report.get("processToken") and report["processToken"] != fixture["processToken"], "Not a full-process restart")
    require(report.get("held") is True and report.get("observationTicks") == OBSERVATION_TICKS,
            "Incomplete or nonquiescent final measurement")
    frozen = rows_by_id(fixture["cases"], True)
    restored = report["restored"]
    validate_readiness(report, "restart", restored["worldTime"])
    previous = rows_by_id(restored["cases"], True)
    require(restored["worldTime"] >= fixture["worldTime"], "World time moved backwards across restart")
    for key in frozen:
        require(same_saved(frozen[key], previous[key]), key + ": complete frozen block/NBT changed during restart")
    ticks = report.get("ticks", [])
    require(len(ticks) == OBSERVATION_TICKS, "Missing per-real-tick observations")
    previous_time = restored["worldTime"]
    for index, frame in enumerate(ticks, 1):
        clock(frame, previous_time, index)
        current = rows_by_id(frame["cases"], True)
        for key, row in current.items():
            old = previous[key]
            before_tag, after_tag = typed(parsed(old)), typed(parsed(row))
            require({k: v for k, v in before_tag.items() if k not in MUTABLE_KEYS}
                    == {k: v for k, v in after_tag.items() if k not in MUTABLE_KEYS}, key + ": saved resources changed")
            if row["held"]:
                require(row["energy"] == frozen[key]["energy"], key + f": held energy debit at tick {index}")
                require(row["progress"] <= old["progress"], key + f": held work advanced at tick {index}")
                require(row["active"] is False, key + f": held ACTIVE immediately true at tick {index}")
            else:
                powered = old["energy"] > 24
                require(set(parsed(row)) & TARGET_KEYS == TARGET_KEYS, "Unheld control lost its real mining target")
                delta = 1 if powered else (-1 if old["progress"] else 0)
                require(row["progress"] == old["progress"] + delta
                        and row["energy"] == old["energy"] - (24 if powered else 0),
                        f"Unheld control failed real progress/debit at tick {index}")
                require(row["active"] is (row["energy"] > 24), "Unheld ACTIVE disagrees with finite reserve")
        previous, previous_time = current, frame["worldTime"]
    require(previous["unheld"]["energy"] < frozen["unheld"]["energy"], "Unheld control never consumed finite power")
    require(any(frame["cases"] and next(row for row in frame["cases"] if row["id"] == "unheld")["progress"]
                > frozen["unheld"]["progress"] for frame in ticks), "Unheld control never advanced work")
    return dict(status="PASS", heldWitnesses=8, unheldControls=1, consecutiveRealTicks=OBSERVATION_TICKS,
                heldDebit=0, firstTickInactive=True, unheldDebit=frozen["unheld"]["energy"] - previous["unheld"]["energy"])


def pin(args):
    base = args.base.resolve()
    pinned_profile(base, "target113")
    document = read_json(base)
    destination = args.output.resolve()
    require_disjoint(destination, base)
    if destination.exists():
        raise FileExistsError(destination)
    replacements = {"advantageworkstest": args.harness.resolve()}
    if args.electric_jar:
        replacements["electricadvantage"] = args.electric_jar.resolve()
    target = copy.deepcopy(document["target113"])
    require(set(replacements).issubset({jar["id"] for jar in target["jars"]}), "Missing replaced mod ID in base")
    sources = [(jar, replacements.get(jar["id"], (base.parent / jar["path"]).resolve())) for jar in target["jars"]]
    for _, source in sources:
        require_disjoint(destination, source)
    destination.mkdir(parents=True)
    for jar, source in sources:
        checksum = digest(source)
        path = destination / "artifacts" / checksum / source.name
        path.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, path)
        require(digest(path) == checksum, "Archive copy differs")
        jar.update(path=str(path), originPath=str(source), sha256=checksum)
        if jar["id"] in replacements:
            jar["sourceRef"] = "LOCAL_REDSTONE_RELOAD_" + args.label
    target.update(seed=str((base.parent / target["seed"]).resolve()), qualificationStatus="UNQUALIFIED_REDSTONE_RELOAD",
                  scope=args.label)
    from native113_code import code_files
    target["serverCode"] = {"files": code_files(Path(target["seed"]))}
    manifest = destination / "inputs.json"
    write_json(manifest, dict(target113=target, baseManifest=str(base), baseSha256=digest(base)))
    pinned_profile(manifest, "target113")
    print(str(manifest), flush=True)
    return 0


def run(args):
    output = output_path(args.output)
    manifest = args.manifest.resolve()
    target = pinned_profile(manifest, "target113")
    expected_code = read_json(manifest)["target113"].get("serverCode")
    verify_code(target[0], expected_code)
    for protected in (manifest, target[0], *target[2]):
        require_disjoint(output, protected)
    require(args.java8.is_file() and 1024 <= args.port <= 65535, "Supply existing Java 8 and available nonprivileged port")
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", args.port))
    launcher = prepare(output, target, [], args.port)
    verify_code(output, expected_code)
    write_json(output / INVENTORY, expected_code)
    (output / "advantage-redstone-reload.allow").touch()
    (output / "migration-operate.allow").touch()
    helpers = [Path(__file__), Path(__file__).with_name("test_native113_redstone_reload.py"),
               Path(__file__).with_name("native113_lifecycle.py"), Path(__file__).with_name("native113_diagnostics.py"),
               Path(__file__).with_name("native113_code.py"), Path(__file__).with_name("native113.py")]
    helpers += [Path(__file__).with_name(name) for name in ("native113.py", "native113_code.py", "native113_lifecycle.py", "lifecycle.py", "matrix.py")]
    java_root = Path(__file__).parents[1] / "src/main/java/zone/moddev/mc/advantageworks"
    helpers += [java_root / "RedstoneReloadProbe.java", java_root / "AdvantageWorksMod.java"]
    hashes = {str(path): digest(path) for path in helpers}
    report = dict(status="RUNNING", manifest=str(manifest), manifestSha256=digest(manifest), protocolSha256=hashes,
                  startedAtUtc=datetime.now(timezone.utc).isoformat(), phases={},
                  scope="Nine finite naturally earned mid-obsidian seeds; same frozen world, two server processes, 16 real ticks")
    for path in helpers:
        destination = output / "protocol-sources" / path.name
        destination.parent.mkdir(exist_ok=True)
        shutil.copy2(path, destination)
    result_path = output / "native113-redstone-reload-results.json"
    write_json(result_path, report)
    server = None
    try:
        server = Server(output, args.java8, launcher, "redstone-seed")
        for command in ("gamerule doMobSpawning false", "gamerule randomTickSpeed 0", "gamerule doWeatherCycle false", "weather clear"):
            server.send(command)
        server.send("advredstonereload seed seed")
        server.wait("ADVREDSTONE COMPLETE seed", 180)
        failure = output / "advantage-works-results/redstone-reload-failure-seed.json"
        require(not failure.exists(), "Seed probe failed; retained " + str(failure))
        fixture_path = output / "redstone-reload-fixture.json"
        fixture = read_json(fixture_path)
        report["phases"]["seed"] = validate_seed(fixture)
        report["fixtureSha256"] = digest(fixture_path)
        server.send("save-all flush")
        server.close()
        server = None
        require_native_boot(output / "console-redstone-seed.log")
        frozen = output / "frozen-save" / WORLD
        shutil.copytree(output / WORLD, frozen)
        report["frozenSaveSha256"] = fingerprint(frozen)
        write_json(result_path, report)
        server = Server(output, args.java8, launcher, "redstone-restart")
        server.send("advredstonereload resume restarted")
        server.wait("ADVREDSTONE COMPLETE restarted", 180)
        failure = output / "advantage-works-results/redstone-reload-failure-restarted.json"
        require(not failure.exists(), "Restart probe failed; retained " + str(failure))
        measured = read_json(output / "advantage-works-results/redstone-reload-restarted.json")
        # Store a failed semantic verdict before shutdown; never resample until green.
        try:
            report["phases"]["reload"] = validate_reload(fixture, measured)
        except AssertionError as error:
            report["phases"]["reload"] = dict(status="FAIL", failure=str(error))
        server.send("save-all flush")
        server.close()
        server = None
        require_native_boot(output / "console-redstone-restart.log")
        require(digest(fixture_path) == report["fixtureSha256"], "Frozen fixture authority changed")
        require(fingerprint(frozen) == report["frozenSaveSha256"], "Archived pre-restart save changed")
        verify_staged(output, target)
        report["status"] = "PASS" if all(row["status"] == "PASS" for row in report["phases"].values()) else "FAIL"
    except BaseException as error:
        report.update(status="FAIL", failure=str(error), traceback=traceback.format_exc())
    finally:
        if server:
            try:
                server.close()
            except BaseException as error:
                report.update(status="FAIL", shutdownError=str(error))
        report["protocolUnchanged"] = hashes == {str(path): digest(path) for path in helpers}
        if not report["protocolUnchanged"]:
            report.update(status="FAIL", protocolError="Protocol changed during execution")
        if digest(manifest) != report["manifestSha256"]:
            report.update(status="FAIL", manifestError="Input manifest changed during execution")
        report["completedAtUtc"] = datetime.now(timezone.utc).isoformat()
        write_json(result_path, report)
    print(report["status"] + ": " + str(output), flush=True)
    return int(report["status"] != "PASS")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    p = commands.add_parser("pin")
    p.add_argument("--base", type=Path, required=True)
    p.add_argument("--harness", type=Path, required=True)
    p.add_argument("--electric-jar", type=Path)
    p.add_argument("--output", type=Path, required=True)
    p.add_argument("--label", required=True)
    r = commands.add_parser("run")
    r.add_argument("--manifest", type=Path, required=True)
    r.add_argument("--output", type=Path, required=True)
    r.add_argument("--java8", type=Path, required=True)
    r.add_argument("--port", type=int, required=True)
    args = parser.parse_args()
    return pin(args) if args.command == "pin" else run(args)


if __name__ == "__main__":
    raise SystemExit(main())
