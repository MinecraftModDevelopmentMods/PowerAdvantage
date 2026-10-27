"""Actual survival mining and silk-touch drops in two clean native116 boots."""

import argparse
from datetime import datetime, timezone
from pathlib import Path
import traceback

from matrix import digest, write_json
from native116_code import Server
from native116 import output_path, pinned_profile, require_disjoint
from native116_lifecycle import prepare, read_json, require_native_boot, verify_staged


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

ISOLATION_PROTOCOL = "native116-live-drops-v1"


def require_checks(document, label):
    rows = document.get("checks", [])
    actual = {row["id"]: row for row in rows}
    if (document.get("label") != label or document.get("minecraft") != "1.16.5"
            or actual.keys() != EXPECTED.keys() | {"solar/native-solidity"}
            or len(rows) != len(EXPECTED) + 1):
        raise AssertionError("Incomplete native116 harvest witness set")
    if document.get("status") != "PASS":
        failures = [row["id"] + ": " + row.get("failure", row.get("status", "missing status"))
                    for row in rows if row.get("status") != "PASS"]
        raise AssertionError("Native116 harvest witnesses failed: " + "; ".join(failures[:5]))
    for name, (level, allowed, silk, minimum, maximum, trials) in EXPECTED.items():
        row = actual[name]
        counts = row.get("actualDropCounts", [])
        mask = int(name.split("/")[1]) if name.startswith(("wire/", "scaffold/")) else -1
        if (row.get("status") != "PASS" or row.get("historicalHarvestLevel") != level
                or row.get("harvestAllowed") is not allowed or row.get("silkTouch") is not silk
                or row.get("connectionMask") != mask
                or row.get("actualSolid") is not name.startswith(("sulfur/", "lithium/", "wire/"))
                or row.get("fullCollision") is not (not name.startswith("wire/"))
                or row.get("trials") != trials or len(counts) != trials
                or any(type(count) is not int or not minimum <= count <= maximum for count in counts)):
            raise AssertionError("Historical mining/drop oracle failed: " + name)
    if actual["solar/native-solidity"].get("status") != "PASS" or actual["solar/native-solidity"].get("actualSolid") is not True:
        raise AssertionError("Historical photovoltaic solid=true property was lost")


def require_isolation(document):
    if document.get("fixtureIsolation") != ISOLATION_PROTOCOL:
        raise AssertionError("Missing native116 live-drop fixture isolation protocol")
    seen_ids, seen_uuids = set(), set()
    for row in document["checks"]:
        if row["id"] == "solar/native-solidity":
            continue
        evidence = row.get("dropEvidence", [])
        if len(evidence) != row["trials"]:
            raise AssertionError("Incomplete per-trial drop isolation evidence: " + row["id"])
        for trial, entry in enumerate(evidence):
            if (entry.get("trial") != trial or type(entry.get("liveAfterCleanup")) is not int
                    or entry["liveAfterCleanup"] != 0
                    or any(type(entry.get(key)) is not int or entry[key] < 0
                           for key in ("liveBeforeCleanup", "removedBeforeCleanup", "worldTime"))):
                raise AssertionError("Harvest trial was not isolated: " + row["id"])
            drops = entry.get("actualDrops")
            if not isinstance(drops, list):
                raise AssertionError("Missing actual native drop identities: " + row["id"])
            count = 0
            for drop in drops:
                entity_id, uuid = drop.get("entityId"), drop.get("uuid")
                if (type(entity_id) is not int or entity_id in seen_ids or not isinstance(uuid, str)
                        or not uuid or uuid in seen_uuids or drop.get("removed") is not False
                        or not isinstance(drop.get("item"), str) or not drop["item"]
                        or type(drop.get("count")) is not int or drop["count"] <= 0):
                    raise AssertionError("Removed, repeated or invalid native harvest drop: " + row["id"])
                seen_ids.add(entity_id)
                seen_uuids.add(uuid)
                count += drop["count"]
            if count != row["actualDropCounts"][trial]:
                raise AssertionError("Native drop identities disagree with counted items: " + row["id"])


def mark_runtime(output):
    (output / "advantage-native-harvest.allow").touch()
    (output / "advantage-works.allow").touch()


def run(args):
    output = output_path(args.output)
    require_disjoint(output, args.target_manifest)
    target = pinned_profile(args.target_manifest.resolve(), "target116")
    for protected in (target[0], *target[2]):
        require_disjoint(output, protected)
    launcher = prepare(output, target, [], args.port)
    mark_runtime(output)
    report = dict(status="RUNNING", started_at_utc=datetime.now(timezone.utc).isoformat(), phases={},
                  fixture_isolation_protocol=ISOLATION_PROTOCOL, runner_sha256=digest(Path(__file__)),
                  target_manifest=str(args.target_manifest.resolve()), target_manifest_sha256=digest(args.target_manifest),
                  scope="Real survival PlayerInteractionManager harvesting, failed tools, exact legacy drop bounds, silk touch and full restart",
                  source_baseline="EA 1.10/1.12 sulfur pickaxe 0 quantity 1..4; lithium pickaxe 1 quantity 1; Power steel-frame normal harvesting")
    server = None
    try:
        for label in ("exercise", "restored"):
            server = Server(output, args.java8, launcher, label)
            server.send("forceload add 1198 1022 1202 1026")
            server.send("advharvest " + label)
            server.wait("ADVHARVEST COMPLETE " + label, 120)
            document = read_json(output / "advantage-works-results" / f"native-harvest-{label}.json")
            report["phases"][label] = document
            server.send("save-all flush")
            server.close()
            server = None
            require_native_boot(output / f"console-{label}.log")
            require_checks(document, label)
            require_isolation(document)
        verify_staged(output, target)
        report["status"] = "PASS"
    except Exception as error:
        report.update(status="FAIL", failure=str(error), traceback=traceback.format_exc())
    finally:
        if server:
            try:
                server.close()
            except Exception as error:
                report.update(status="FAIL", shutdown_error=str(error))
        report["completed_at_utc"] = datetime.now(timezone.utc).isoformat()
        write_json(output / "native116-harvest-results.json", report)
    return int(report["status"] != "PASS")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target-manifest", type=Path, required=True)
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--port", type=int, default=25596)
    raise SystemExit(run(parser.parse_args()))
