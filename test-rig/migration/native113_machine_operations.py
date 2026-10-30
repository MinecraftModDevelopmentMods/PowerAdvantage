"""Run independent finite machine jobs on native world ticks, then inspect a cold restart."""

import argparse
import copy
from datetime import datetime, timezone
import json
from pathlib import Path
import shutil
import socket
import traceback

import nbtlib

from matrix import digest, write_json
from native113 import RIG, LAUNCHER, output_path, pinned_profile, require_disjoint
from native113_code import Server, code_files, verify_code, INVENTORY
from native113_lifecycle import prepare, read_json, require_native_boot, verify_staged


REQUIRED = {
    "pickup": {"power-drain", "steam-pump", "electric-pump"},
    "power": {"conveyor/down", "conveyor/up", "conveyor/north", "conveyor/south", "conveyor/west", "conveyor/east",
              "conveyor/blocked", "conveyor/item-entity", "conveyor/nbt-preserved", "filter/block", "filter/food", "filter/fuel", "filter/ore",
              "filter/plant", "filter/smelt", "filter/inventory-0", "filter/inventory-1", "filter/inventory-2",
              "filter/inventory-3", "filter/overflow-0", "filter/overflow-1", "filter/overflow-2", "filter/overflow-3",
              "drain/minecraft_water", "drain/poweradvantage_crude_oil", "drain/poweradvantage_refined_oil",
              "drain/non-source", "tank/filter-true", "tank/filter-false", "tank/full", "fluid-switch/true",
              "fluid-switch/false", "discharge/true", "discharge/false", "conveyor/locked-source", "conveyor/locked-destination",
              "still/production", "still/no-fuel", "still/invalid", "still/full", "still/one-space", "still/redstone"},
    "steam": {"coal-production", "coal-invalid-fuel", "coal-dry", "coal-redstone", "oil-production", "oil-dry",
              "oil-water-not-fuel", "oil-small-charge", "geothermal-cold", "geothermal-dry",
              "electric-production", "electric-no-power", "electric-dry", "electric-insufficient", "geothermal-production",
              "furnace-three-lanes", "furnace-no-fuel", "furnace-full-outputs", "furnace-invalid-input", "furnace-without-steam",
              "crusher-and-tank-discharge", "crusher-no-power", "crusher-full-outputs", "crusher-invalid-input",
              "tank-redstone-stop", "tank-backup-no-cycling", "still-production", "still-no-power", "still-full-output",
              "still-one-output-space", "still-incomplete-input", "still-invalid-fluid",
              "pump-3x3-intake-lift", "pump-no-power", "pump-insufficient-steam", "pump-full-output",
              "elevator-up", "elevator-down", "elevator-no-power", "elevator-scarce",
              "musket-last-cartridge", "musket-no-ammo", "musket-powderless", "musket-infinity",
              "extra-priority-backup-eligible", "extra-priority-consumer-first", "extra-recoil-0", "extra-recoil-1",
              "extra-recoil-5", "extra-explosive-0", "extra-explosive-1", "extra-musket-solid-wall"},
    "electric": {"furnace", "crusher", "oven", "parallel-inputs", "fabricator-recursive", "refinery", "still",
                 "still-faced-input", "pump", "growth-chain", "growth-scarcity-full", "led-rate", "led-exhaustion",
                 "hydro-rate", "solar-daylight-rate", "solar-blocklight-rate", "steam-generator-rate", "steam-generator-supply",
                 "battery-priority", "battery-redstone", "turret-hit", "turret-no-power", "turret-scarce", "turret-occluded",
                 "turret-owner-credit", "battery-chemistry-lead_acid", "battery-chemistry-nickel_hydride",
                 "battery-chemistry-alkaline", "battery-chemistry-lithium"},
}


def require_case_set(catalog, group):
    rows = catalog.get("cases", [])
    ids = {row["id"] for row in rows}
    if not ids or len(rows) != len(ids): raise AssertionError("Empty/duplicate operating catalog")
    groups = REQUIRED if group == "all" else {group: REQUIRED[group]}
    for prefix, names in groups.items():
        missing = {prefix + "/" + name for name in names} - ids
        if missing: raise AssertionError("Missing required operating families: " + str(sorted(missing)))
    if group != "all" and any(not key.startswith(group + "/") for key in ids):
        raise AssertionError("Operating result includes the wrong group")
    return ids


def validate_operation(catalog, document, group):
    expected = require_case_set(catalog, group)
    rows = document.get("cases", [])
    if len(rows) != len(expected) or {row["id"] for row in rows} != expected:
        raise AssertionError("Incomplete operating result; a passing subset is forbidden")
    if document.get("held") is not True: raise AssertionError("Final measurement is not quiescent")
    failures = []
    for row in rows:
        assertions = row.get("assertions", [])
        measured = [check for check in assertions if check.get("phase") == "verify"]
        if (row.get("status") != "PASS" or row.get("verifiedAssertions", 0) < 1 or not measured
                or any(check.get("status") != "PASS" for check in assertions)
                or row.get("finishTick", 0) - row.get("startTick", 0) < row.get("requiredTicks", 1)
                or not row.get("before") or not row.get("after")):
            failures.append({"id": row["id"], "failure": row.get("failure", "Missing/failed measured natural-tick outcome")})
    return dict(status="FAIL" if failures or document.get("status") != "PASS" else "PASS", cases=len(rows), failures=failures)


def normalized_rows(rows):
    result = {}
    for row in rows:
        key = (row["x"], row["y"], row["z"])
        if key in result: raise AssertionError("Duplicate persisted position")
        value = {name: row[name] for name in ("block", "state")}
        if "nbt" in row: value["nbt"] = saved_tile_nbt(nbtlib.parse_nbt(row["nbt"]))
        result[key] = value
    return result


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
            # FluidTank.readFromNBT drops a zero-volume fluid identity. No material or filter is stored here.
            result[key] = {"Empty": ["Byte", 1]}
    return result


def validate_restart(fixture, inspected):
    before = fixture.get("cases", [])
    after = inspected.get("cases", [])
    if (not before or len(after) != len(before) or {r["id"] for r in before} != {r["id"] for r in after}
            or len({r["id"] for r in after}) != len(after) or inspected.get("held") is not True
            or inspected.get("constructionCallsThisProcess") != 0):
        raise AssertionError("Missing/rebuilt/unfrozen full-process restart evidence")
    actual = {row["id"]: normalized_rows(row["positions"]) for row in after}
    failures = []
    for row in before:
        expected = normalized_rows(row["positions"])
        if actual[row["id"]] != expected:
            changed = [dict(pos=key, expected=value, actual=actual[row["id"]].get(key)) for key, value in expected.items()
                       if actual[row["id"]].get(key) != value]
            extra = set(actual[row["id"]]) - set(expected)
            failures.append(dict(id=row["id"], differences=changed, extraPositions=sorted(extra)))
        actual_case = next(case for case in after if case["id"] == row["id"])
        def actors(case):
            rows = case.get("entities", [])
            result = {actor["uuid"]: typed_nbt(nbtlib.parse_nbt(actor["nbt"])) if "nbt" in actor else actor for actor in rows}
            if len(result) != len(rows): raise AssertionError("Duplicate persisted actor UUID")
            return result
        if actors(row) != actors(actual_case): failures.append(dict(id=row["id"], difference="Saved test actors changed across restart"))
    return dict(status="FAIL" if failures else "PASS", cases=len(before), failures=failures,
                comparison="Full block/state/typed-NBT equality including target-only keys; only exact zero-volume named tanks canonicalized; no expected-state writes")


def pin(args):
    base = read_json(args.base)
    destination = args.output.resolve()
    if destination.exists(): raise FileExistsError(destination)
    destination.mkdir(parents=True)
    replacements = {
        "poweradvantage": RIG.parent / "build/libs/PowerAdvantage-2.4.4.113021.jar",
        "steamadvantage": RIG.parent.parent / "SteamAdvantage/SteamAdvantage/build/libs/SteamAdvantage-2.2.5.113021.jar",
        "electricadvantage": RIG.parent.parent / "ElectricAdvantage/ElectricAdvantage/build/libs/ElectricAdvantage-2.2.5.113021.jar",
        "advantageworkstest": RIG / "build/gradle/libs/AdvantageWorksTestHarness-1.1.0.113021.jar",
    }
    if getattr(args, "original_production", False):
        replacements = {key: value for key, value in replacements.items() if key == "advantageworkstest"}
    target = copy.deepcopy(base["target113"])
    for jar in target["jars"]:
        source = replacements.get(jar["id"], Path(jar["path"]))
        checksum = digest(source)
        folder = destination / "artifacts" / checksum
        folder.mkdir(parents=True, exist_ok=True)
        path = folder / source.name
        if path.exists(): raise FileExistsError(path)
        shutil.copy2(source, path)
        if digest(path) != checksum: raise AssertionError("Archive copy differs")
        jar.update(path=str(path), originPath=str(source), sha256=checksum)
        if jar["id"] in replacements: jar["sourceRef"] = "LOCAL_OPERATING_CANDIDATE_" + args.label
    target["qualificationStatus"] = "UNQUALIFIED_OPERATING_CANDIDATE"
    target["scope"] = args.label
    target["serverCode"] = {"files": code_files(Path(target["seed"]))}
    path = destination / "inputs.json"
    write_json(path, {"target113": target, "baseManifest": str(args.base.resolve()), "baseSha256": digest(args.base)})
    pinned_profile(path, "target113")
    print(str(path), flush=True)
    return 0


def run(args):
    output = output_path(args.output)
    target = pinned_profile(args.manifest.resolve(), "target113")
    for protected in (args.manifest, target[0], *target[2]): require_disjoint(output, protected)
    with socket.socket() as listener: listener.bind(("127.0.0.1", args.port))
    launcher = prepare(output, target, [], args.port)
    expected_code = read_json(args.manifest)["target113"].get("serverCode")
    if not expected_code:
        raise AssertionError("Operating inputs require an exact server-code inventory")
    verify_code(target[0], expected_code)
    verify_code(output, expected_code)
    write_json(output / INVENTORY, expected_code)
    for marker in ("advantage-machine-operation.allow", "migration-operate.allow"): (output / marker).touch()
    report = dict(status="RUNNING", group=args.group, manifest=str(args.manifest.resolve()), manifestSha256=digest(args.manifest),
                  startedAtUtc=datetime.now(timezone.utc).isoformat(), phases={},
                  scope="Finite natural-tick machine jobs, negative controls, then frozen strict full-process restart; no rebuilding")
    helpers = [Path(__file__), Path(__file__).with_name("native113_lifecycle.py"),
               Path(__file__).with_name("native113_diagnostics.py"),
               Path(__file__).with_name("native113_code.py"), Path(__file__).with_name("native113.py")]
    report["protocolSha256"] = {str(path): digest(path) for path in helpers}
    for path in helpers:
        destination = output / "protocol-sources" / path.name
        destination.parent.mkdir(exist_ok=True)
        shutil.copy2(path, destination)
    server = None
    try:
        server = Server(output, args.java8, launcher, "operate")
        server.send("gamerule doMobSpawning false")
        server.send("gamerule randomTickSpeed 0")
        server.send("gamerule doWeatherCycle false")
        server.send("weather clear")
        server.send("advmachines run " + args.group + " measured")
        server.wait("ADVMACHINES COMPLETE measured", 1500)
        catalog = read_json(output / "advantage-works-results/machine-catalog-measured.json")
        result = read_json(output / "advantage-works-results/machine-operation-measured.json")
        report["phases"]["operation"] = validate_operation(catalog, result, args.group)
        server.send("save-all flush")
        server.close(); server = None
        require_native_boot(output / "console-operate.log")
        fixture = read_json(output / "machine-operation-fixture.json")
        report["fixtureSha256"] = digest(output / "machine-operation-fixture.json")
        # Keep the actual first-process save independently of subsequent restart writes.
        shutil.copytree(output / "AdvantageWorksMigration", output / "pre-restart-save")
        write_json(output / "native113-machine-operation-results.json", report)
        server = Server(output, args.java8, launcher, "restart")
        server.send("advmachines inspect restarted")
        server.wait("ADVMACHINES INSPECTED restarted", 360)
        observed = read_json(output / "advantage-works-results/machine-inspection-restarted.json")
        report["phases"]["restart"] = validate_restart(fixture, observed)
        server.send("save-all flush")
        server.close(); server = None
        require_native_boot(output / "console-restart.log")
        if digest(output / "machine-operation-fixture.json") != report["fixtureSha256"]: raise AssertionError("Fixture authority changed across restart")
        verify_staged(output, target)
        report["status"] = "PASS" if all(row["status"] == "PASS" for row in report["phases"].values()) else "FAIL"
    except BaseException as error:
        report.update(status="FAIL", failure=str(error), traceback=traceback.format_exc())
    finally:
        if server:
            try: server.close()
            except BaseException as error: report.update(status="FAIL", shutdownError=str(error))
        report["protocolUnchanged"] = {str(path): digest(path) for path in helpers} == report["protocolSha256"]
        if not report["protocolUnchanged"]: report.update(status="FAIL", protocolError="Operating protocol changed during the run")
        report["completedAtUtc"] = datetime.now(timezone.utc).isoformat()
        write_json(output / "native113-machine-operation-results.json", report)
    print(report["status"] + ": " + str(output), flush=True)
    return int(report["status"] != "PASS")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    p = commands.add_parser("pin")
    p.add_argument("--base", type=Path, required=True); p.add_argument("--output", type=Path, required=True)
    p.add_argument("--label", required=True)
    p.add_argument("--original-production", action="store_true", help="Keep the original three production jars; replace only the test harness")
    r = commands.add_parser("run")
    r.add_argument("--manifest", type=Path, required=True); r.add_argument("--output", type=Path, required=True)
    r.add_argument("--java8", type=Path, required=True); r.add_argument("--port", type=int, required=True)
    r.add_argument("--group", choices=("all", *REQUIRED), default="all")
    args = parser.parse_args()
    return pin(args) if args.command == "pin" else run(args)


if __name__ == "__main__": raise SystemExit(main())
