"""Native capability, portable loot and locked automation witnesses on disposable servers."""

import argparse
from datetime import datetime, timezone
from pathlib import Path
import traceback

from matrix import digest, write_json
from native116_code import Server
from native116 import output_path, pinned_profile, require_disjoint
from native116_lifecycle import prepare, read_json, require_native_boot, verify_staged


EXERCISE_CHECK_IDS = frozenset(
    f"{converter}/{check}"
    for converter in ("converter_rf_steam", "converter_rf_electricity", "converter_rf_quantum",
                      "converter_tr_electricity")
    for check in ("capability", "forge-to-advantage", "advantage-to-forge", "invalidation")
) | frozenset((
    "fluid/simulation-conservation-and-identity",
    "portable-tank/native-break-drop-and-replace",
    "locked-chest/conveyor-refuses-extraction",
    "unlocked-chest/conveyor-transfers-exactly",
    "capability/native-chunk-unloaded-callback",
    "capability/native-clear-removed-and-revive",
))
RESTORED_CHECK_IDS = frozenset((
    "portable-tank/full-process-restart",
    "conveyor/full-process-restart",
))


def require_checks(document, phase):
    if phase not in ("exercise", "restored"):
        raise AssertionError("Unknown native interop phase: " + str(phase))
    expected = EXERCISE_CHECK_IDS if phase == "exercise" else RESTORED_CHECK_IDS
    if document.get("phase") != phase or len(document.get("checks", [])) != len(expected):
        raise AssertionError("Incomplete native interop witness set")
    actual = {row["id"] for row in document["checks"]}
    if len(actual) != len(expected):
        raise AssertionError("Duplicate native interop witnesses")
    if actual != expected:
        raise AssertionError("Unexpected native interop witnesses: " + str(sorted(actual - expected))
                             + "; missing: " + str(sorted(expected - actual)))
    failed = [row for row in document["checks"] if row.get("status") != "PASS"]
    if failed or document.get("status") != "PASS":
        raise AssertionError("Native interop defects: " + str(failed))


def run(args):
    output = output_path(args.output)
    require_disjoint(output, args.target_manifest)
    target = pinned_profile(args.target_manifest.resolve(), "target116")
    for protected in (target[0], *target[2]):
        require_disjoint(output, protected)
    launcher = prepare(output, target, [], args.port)
    (output / "advantage-native-interop.allow").touch()
    report = dict(status="RUNNING", started_at_utc=datetime.now(timezone.utc).isoformat(), phases={},
                  target_manifest=str(args.target_manifest.resolve()), target_manifest_sha256=digest(args.target_manifest),
                  scope="Live Forge capability contracts, standalone tank unload/revival callbacks, both conversion directions, native tank loot, locked chest automation and full restart",
                  limitation="Standalone tank callbacks do not prove physical chunk unload; no third-party mod-specific energy implementation or real client input is claimed")
    server = None
    try:
        for phase in ("exercise", "restored"):
            server = Server(output, args.java8, launcher, phase)
            server.send("forceload add 1023 1023 1100 1030")
            server.send("advinterop " + phase)
            server.wait("ADVINTEROP COMPLETE " + phase, 120)
            report["phases"][phase] = read_json(output / "advantage-works-results" / f"native-interop-{phase}.json")
            server.send("save-all flush")
            server.close()
            server = None
            require_native_boot(output / f"console-{phase}.log")
        for phase, document in report["phases"].items():
            require_checks(document, phase)
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
        write_json(output / "native116-interop-results.json", report)
    print(report["status"] + ": " + str(output / "native116-interop-results.json"), flush=True)
    return int(report["status"] != "PASS")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target-manifest", type=Path, required=True)
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--port", type=int, default=25592)
    raise SystemExit(run(parser.parse_args()))
