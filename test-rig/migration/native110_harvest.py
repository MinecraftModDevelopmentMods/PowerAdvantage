"""Native legacy survival harvesting: exact tools/drops, 64 connection masks and cold reboot."""

import argparse
from datetime import datetime, timezone
import math
from pathlib import Path
import shutil
import socket
import traceback
from uuid import UUID

from rig_transport import digest, write_json
from native110 import identity, output_path, pinned_profile, require_disjoint
from native110_code import INVENTORY, Server, verify_code
from native110_lifecycle import prepare, read_json, require_native_boot, verify_staged

EXPECTED = {
    "sulfur/hand-fails": (0, False, False, 0, 0, 1),
    "sulfur/axe-fails": (0, False, False, 0, 0, 1),
    "sulfur/wood": (0, True, False, 1, 4, 8),
    "sulfur/stone": (0, True, False, 1, 4, 8),
    "sulfur/silk": (0, True, True, 1, 1, 1),
    "lithium/hand-fails": (1, False, False, 0, 0, 1),
    "lithium/wood-fails": (1, False, False, 0, 0, 1),
    "lithium/gold-fails": (1, False, False, 0, 0, 1),
    "lithium/axe-fails": (1, False, False, 0, 0, 1),
    "lithium/stone": (1, True, False, 1, 1, 1),
    "lithium/diamond": (1, True, False, 1, 1, 1),
    "lithium/silk": (1, True, True, 1, 1, 1),
    "frame/hand": (-1, True, False, 1, 1, 1),
    "frame/silk": (-1, True, True, 1, 1, 1),
}
for _mask in range(64):
    for _family in ("wire", "scaffold"):
        EXPECTED[f"{_family}/{_mask}/hand"] = (-1, True, False, 1, 1, 1)
        EXPECTED[f"{_family}/{_mask}/silk"] = (-1, True, True, 1, 1, 1)

ISOLATION_PROTOCOL = "native110-live-drops-v1"
MINECRAFT = "1.10.2"
WATER_ONLY = {"wire/waterlogged", "scaffold/waterlogged", "frame/waterlogged"}
NA_REASON = f"Minecraft {MINECRAFT} has no waterlogged blockstate/fluid API"
# Historical GUIBlock explicitly sets light opacity to zero, independent of
# PhotovoltaicGeneratorBlock's full-cube/opaque overrides. Preserve both facts.
PROPERTIES = {"solar/native-solidity": ("electricadvantage:photovoltaic_generator", True, 0),
              "frame/native-opacity": ("poweradvantage:steel_frame", False, 0)}


def block_identity(name):
    if name.startswith("sulfur/"): return "electricadvantage:sulfur_ore"
    if name.startswith("lithium/"): return "electricadvantage:li_ore"
    if name.startswith("frame/"): return "poweradvantage:steel_frame"
    return "electricadvantage:" + ("electric_conduit" if name.startswith("wire/") else "electric_track")


def drop_identity(name):
    if not EXPECTED[name][3]: return ""
    if name in ("sulfur/wood", "sulfur/stone"): return "electricadvantage:sulfur_powder"
    return block_identity(name)


def expected_boxes(name):
    if not name.startswith("wire/"): return [[0, 0, 0, 1, 1, 1]]
    mask = int(name.split("/")[1])
    boxes = [[.375, .375, .375, .625, .625, .625]]
    for bit, coordinates in (
            (2, [.375, .375, .375, .625, 1, .625]),
            (1, [.375, 0, .375, .625, .625, .625]),
            (32, [.375, .375, .375, 1, .625, .625]),
            (16, [0, .375, .375, .625, .625, .625]),
            (8, [.375, .375, .375, .625, .625, 1]),
            (4, [.375, .375, 0, .625, .625, .625])):
        if mask & bit or mask == 0 and bit >= 4: boxes.append(coordinates)
    return boxes


def require_checks(document, label):
    rows = document.get("checks")
    if not isinstance(rows, list) or any(not isinstance(row, dict) or not isinstance(row.get("id"), str) for row in rows):
        raise AssertionError("Missing native harvest witness rows")
    actual = {row["id"]: row for row in rows}
    if (document.get("label") != label or document.get("minecraft") != MINECRAFT
            or actual.keys() != EXPECTED.keys() | PROPERTIES.keys() | WATER_ONLY
            or len(rows) != 275 or document.get("executedChecks") != 272
            or document.get("notApplicableChecks") != 3):
        raise AssertionError("Incomplete native110 harvest witness set")
    if document.get("status") != "PASS":
        failures = [row["id"] + ": " + row.get("failure", str(row.get("status")))
                    for row in rows if row.get("status") not in ("PASS", "N/A")]
        raise AssertionError("Native110 harvest witnesses failed: " + "; ".join(failures[:5]))
    for name in WATER_ONLY:
        if actual[name].get("status") != "N/A" or actual[name].get("reason") != NA_REASON:
            raise AssertionError("Waterlogging must remain an explicit unsupported N/A: " + name)
    for name, (level, allowed, silk, minimum, maximum, trials) in EXPECTED.items():
        row = actual[name]
        counts = row.get("actualDropCounts", [])
        mask = int(name.split("/")[1]) if name.startswith(("wire/", "scaffold/")) else -1
        ore = name.startswith(("sulfur/", "lithium/"))
        if (row.get("status") != "PASS" or row.get("block") != block_identity(name)
                or type(row.get("historicalHarvestLevel")) is not int or row["historicalHarvestLevel"] != level
                or row.get("harvestAllowed") is not allowed or row.get("silkTouch") is not silk
                or type(row.get("connectionMask")) is not int or row["connectionMask"] != mask
                or mask >= 0 and (type(row.get("actualConnectionMask")) is not int or row["actualConnectionMask"] != mask)
                or row.get("actualFullCube") is not ore or row.get("actualOpaque") is not ore
                or row.get("materialSolid") is not True
                or type(row.get("trials")) is not int or row["trials"] != trials or not isinstance(counts, list)
                or len(counts) != trials
                or any(type(count) is not int or not minimum <= count <= maximum for count in counts)):
            raise AssertionError("Historical mining/drop oracle failed: " + name)
        boxes = row.get("collisionBoxes")
        if (not isinstance(boxes, list) or any(not isinstance(box, list) or len(box) != 6
                or any(type(value) not in (int, float) or not math.isfinite(value) for value in box) for box in boxes)
                or sorted(map(tuple, boxes)) != sorted(map(tuple, expected_boxes(name)))):
            raise AssertionError("Legacy collision geometry changed: " + name)
    for name, (block, opaque, light_opacity) in PROPERTIES.items():
        row = actual[name]
        if (row.get("status") != "PASS" or row.get("block") != block or row.get("actualOpaque") is not opaque
                or row.get("actualFullCube") is not opaque or type(row.get("lightOpacity")) is not int
                or row["lightOpacity"] != light_opacity):
            raise AssertionError("Legacy frame/photovoltaic opacity changed: " + name)


def require_isolation(document):
    if document.get("fixtureIsolation") != ISOLATION_PROTOCOL:
        raise AssertionError("Missing native110 live-drop fixture isolation protocol")
    seen_ids, seen_uuids = set(), set()
    for name, spec in EXPECTED.items():
        rows = [row for row in document["checks"] if row["id"] == name]
        if len(rows) != 1: raise AssertionError("Missing/duplicate harvest row: " + name)
        row = rows[0]
        evidence = row.get("dropEvidence", [])
        if not isinstance(evidence, list) or len(evidence) != spec[5]:
            raise AssertionError("Incomplete per-trial drop isolation evidence: " + name)
        for trial, entry in enumerate(evidence):
            if (not isinstance(entry, dict) or type(entry.get("trial")) is not int or entry["trial"] != trial
                    or type(entry.get("liveAfterCleanup")) is not int or entry["liveAfterCleanup"] != 0
                    or any(type(entry.get(key)) is not int or entry[key] < 0
                           for key in ("liveBeforeCleanup", "removedBeforeCleanup", "worldTime"))):
                raise AssertionError("Harvest trial was not isolated: " + name)
            drops = entry.get("actualDrops")
            if not isinstance(drops, list): raise AssertionError("Missing actual native drop identities: " + name)
            count = 0
            for drop in drops:
                if not isinstance(drop, dict): raise AssertionError("Invalid native drop record: " + name)
                entity_id, uuid = drop.get("entityId"), drop.get("uuid")
                try: valid_uuid = isinstance(uuid, str) and str(UUID(uuid)) == uuid
                except (ValueError, TypeError, AttributeError): valid_uuid = False
                if (type(entity_id) is not int or entity_id in seen_ids or not valid_uuid or uuid in seen_uuids
                        or drop.get("removed") is not False or drop.get("item") != drop_identity(name)
                        or type(drop.get("count")) is not int or drop["count"] <= 0
                        or type(drop.get("damage")) is not int or drop["damage"] != 0 or drop.get("tagged") is not False
                        or any(type(drop.get(axis)) not in (int, float) or not math.isfinite(drop[axis])
                               or not low <= drop[axis] <= high
                               for axis, low, high in (("x",1198,1203),("y",6,11),("z",1022,1027)))):
                    raise AssertionError("Removed, repeated, wrong or invalid native harvest drop: " + name)
                seen_ids.add(entity_id); seen_uuids.add(uuid)
                count += drop["count"]
            if count != row["actualDropCounts"][trial]:
                raise AssertionError("Native drop identities disagree with counted items: " + name)


def run(args):
    output = output_path(args.output)
    require_disjoint(output, args.target_manifest)
    target = pinned_profile(args.target_manifest.resolve(), "target110")
    for protected in (target[0], *target[2]): require_disjoint(output, protected)
    if not {"poweradvantage","steamadvantage","electricadvantage","advantageworkstest"} <= {identity(jar)[0] for jar in target[2]}:
        raise AssertionError("Harvest requires all three Advantage mods and the native harness")
    expected_code = read_json(args.target_manifest)["target110"].get("serverCode")
    if not expected_code: raise AssertionError("Harvest requires an exact sealed server-code inventory")
    verify_code(target[0], expected_code)
    with socket.socket() as listener: listener.bind(("127.0.0.1", args.port))
    launcher = prepare(output, target, [], args.port)
    verify_code(output, expected_code)
    write_json(output / INVENTORY, expected_code)
    (output / "advantage-native-harvest.allow").touch()
    helpers = [Path(__file__), *[Path(__file__).with_name(name) for name in
               ("rig_transport.py","native110.py","native110_lifecycle.py","native110_code.py","native110_diagnostics.py")],
               Path(__file__).parents[1] / "src/main/java/zone/moddev/mc/advantageworks/NativeHarvestProbe.java"]
    report = dict(status="RUNNING", started_at_utc=datetime.now(timezone.utc).isoformat(), phases={},
                  fixture_isolation_protocol=ISOLATION_PROTOCOL, runner_sha256=digest(Path(__file__)),
                  target_manifest=str(args.target_manifest.resolve()), target_manifest_sha256=digest(args.target_manifest),
                  scope="Real survival harvest on each of two cold boots; 270 mining cases, solar solidity, dedicated frame opacity, 3 waterlogging N/A",
                  source_baseline="Legacy sulfur pickaxe 0, dust 1..4; lithium pickaxe 1, ore 1; PISTON frame/wire/scaffold material; native nonopaque geometry",
                  executed_per_boot=272, not_applicable_per_boot=3)
    report["protocolSha256"] = {str(path): digest(path) for path in helpers}
    for path in helpers:
        destination = output / "protocol-sources" / path.name
        destination.parent.mkdir(exist_ok=True)
        shutil.copy2(path, destination)
    server = None
    try:
        for label in ("exercise", "restored"):
            verify_staged(output, target)
            server = Server(output, args.java8, launcher, label)
            server.send("advharvest " + label)
            server.wait("ADVHARVEST COMPLETE " + label, 120)
            document = read_json(output / "advantage-works-results" / f"native-harvest-{label}.json")
            report["phases"][label] = document
            server.send("save-all flush")
            server.close()
            server = None
            verify_staged(output, target)
            require_native_boot(output / f"console-{label}.log")
            require_checks(document, label)
            require_isolation(document)
        verify_staged(output, target)
        report["status"] = "PASS"
    except Exception as error:
        report.update(status="FAIL", failure=str(error), traceback=traceback.format_exc())
    finally:
        if server:
            try: server.close()
            except Exception as error: report.update(status="FAIL", shutdown_error=str(error))
        report["completed_at_utc"] = datetime.now(timezone.utc).isoformat()
        report["protocolUnchanged"] = report["protocolSha256"] == {str(path): digest(path) for path in helpers}
        if not report["protocolUnchanged"]: report.update(status="FAIL", protocolError="Harvest protocol changed during run")
        report["manifestUnchanged"] = digest(args.target_manifest) == report["target_manifest_sha256"]
        if not report["manifestUnchanged"]: report.update(status="FAIL", manifestError="Harvest input manifest changed during run")
        write_json(output / "native110-harvest-results.json", report)
    return int(report["status"] != "PASS")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target-manifest", type=Path, required=True)
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--port", type=int, default=25596)
    raise SystemExit(run(parser.parse_args()))
