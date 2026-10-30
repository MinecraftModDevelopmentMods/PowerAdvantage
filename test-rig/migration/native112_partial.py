"""Explicit native 112 partial-network adapter; shared running protocols are never modified."""
import argparse
import base64
import gzip
import io
import json
import math
from datetime import datetime, timezone
from pathlib import Path
import traceback
import nbtlib

import native112_machine_operations as operating
from rig_transport import digest, write_json

REQUIRED = {"power/partial/" + kind + suffix
            for kind in ("steam", "electric", "fluid") for suffix in ("", "-source", "-consumer")}


def validate_partial(catalog, measured, fixture, inspected):
    def keyed(rows):
        result = {row["id"]: row for row in rows}
        if len(result) != len(rows):
            raise AssertionError("Duplicate native case ID")
        return result
    catalogs = keyed(catalog["cases"])
    actual = keyed(measured["cases"])
    saved = keyed(fixture["cases"])
    cold = keyed(inspected["cases"])
    if not all(REQUIRED <= set(rows) for rows in (catalogs, actual, saved, cold)):
        raise AssertionError("Missing required native partial-network case; a passing subset is forbidden")
    if measured.get("held") is not True or inspected.get("held") is not True or inspected.get("constructionCallsThisProcess") != 0:
        raise AssertionError("Missing untouched cold inspection")
    results = []
    for identity in sorted(REQUIRED):
        row = actual[identity]
        if row.get("status") != "PASS" or row.get("partial.completed") is not True or row.get("partial.cleanupCalled") is not True:
            raise AssertionError("Partial case did not complete: " + identity)
        if row.get("partial.measuredTicks") != 1100 or row["finishTick"] - row["startTick"] != 1100:
            raise AssertionError("Missing exact measured natural-tick window: " + identity)
        if row.get("partial.absentTicks", -1) < 240:
            raise AssertionError("No sustained real partial absence: " + identity)
        events = row.get("partial.events", [])
        unloads = [event for event in events if event.get("operation") == "unload"]
        loads = [event for event in events if event.get("operation") == "load"]
        if len(unloads) != 1 or len(loads) != 1 or loads[0].get("typedStateEqual") is not True:
            raise AssertionError("Missing/duplicate native chunk events or changed typed reload state: " + identity)
        selected = row["partial.selectedChunk"]
        if any(event.get("chunk") != selected for event in unloads + loads):
            raise AssertionError("Native events refer to another chunk")
        restore = row["partial.restoreTick"]
        if not unloads[0]["tick"] < restore <= loads[0]["tick"] < 1100:
            raise AssertionError("Native release/recovery order is invalid: " + identity)
        if restore - unloads[0]["tick"] < 240:
            raise AssertionError("Native callback times do not prove the required absence duration")
        if loads[0].get("explicitRecovery") is not True:
            raise AssertionError("Automatic reload was accepted as explicit recovery")
        if not unloads[0].get("networkBlocks") or unloads[0]["networkBlocks"] != loads[0].get("networkBlocks"):
            raise AssertionError("Actual native bridge/endpoint block observations changed")
        def typed(encoded):
            return operating.typed_nbt(nbtlib.File.parse(io.BytesIO(gzip.decompress(
                base64.b64decode(encoded, validate=True)))))
        before_tags = {tuple(tag["pos"]): typed(tag["observedNbtBase64"]) for tag in unloads[0].get("unloadState", [])}
        after_tags = {tuple(tag["pos"]): typed(tag["observedNbtBase64"]) for tag in loads[0].get("unloadState", [])}
        if before_tags != after_tags or (identity.endswith(("-source", "-consumer")) and len(before_tags) != 1):
            raise AssertionError("Native callback typed NBT changed or was missing")
        absent = [sample for sample in row.get("partial.observations", [])
                  if sample.get("selectedPresent") is False and sample.get("explicitRecovery") is False]
        if not absent or max(p["tick"] for p in absent) - min(p["tick"] for p in absent) < 220:
            raise AssertionError("Missing independent sustained-absence snapshots")
        if any(sample.get("retainedLoaded") is not True for sample in row.get("partial.observations", [])):
            raise AssertionError("Retained endpoint did not remain loaded")
        if any(event.get("failure") for event in events):
            raise AssertionError("Native callback failure was concealed")
        if identity.endswith("-source"):
            if row.get("partial.isolationApplicable") is not False:
                raise AssertionError("Source-unloaded case must not claim a live-source witness")
        else:
            if row.get("partial.isolationApplicable") is not True:
                raise AssertionError("Missing live-source isolation witness")
            samples = row.get("partial.isolationSamples")
            if not isinstance(samples, list) or len(samples) < 221:
                raise AssertionError("Missing at least 220 stable source-isolation ticks")
            ticks = [sample.get("tick") for sample in samples]
            if (any(type(tick) is not int for tick in ticks) or not 0 <= ticks[0] < ticks[-1] <= 1100
                    or ticks != list(range(ticks[0], ticks[-1] + 1))):
                raise AssertionError("Source-isolation observations skip or duplicate native ticks")
            if not unloads[0]["tick"] <= ticks[0] <= unloads[0]["tick"] + 1 or ticks[-1] != restore:
                raise AssertionError("Isolation must cover the absent interval with at most one unload-phase grace tick")
            kind = identity.split("/")[-1].split("-")[0]
            anchor = samples[0].get("source")
            if type(anchor) not in (int, float) or not math.isfinite(anchor) or anchor < 0:
                raise AssertionError("Invalid measured source-isolation anchor")
            for sample, tick in zip(samples, ticks):
                source = sample.get("source")
                expected_source = anchor - (tick - ticks[0]) * (0.0625 if kind == "steam" else 0)
                if (sample.get("sourceLoaded") is not True or sample.get("selectedAbsent") is not True
                        or type(source) not in (int, float) or not math.isfinite(source) or source != expected_source
                        or sample.get("expectedSource") != expected_source):
                    raise AssertionError("Ghost flow crossed the missing bridge/consumer: " + identity)
            if (row.get("partial.isolationAnchorTick") != ticks[0]
                    or row.get("partial.isolationLastTick") != ticks[-1]
                    or row.get("partial.isolationAnchorSource") != anchor
                    or row.get("partial.isolationLastSource") != samples[-1]["source"]
                    or row.get("partial.isolationStableTicks") != ticks[-1] - ticks[0]
                    or ticks[-1] - ticks[0] < 220):
                raise AssertionError("Isolation summary does not match actual quantitative samples")
        results.append({"id": identity, "status": "PASS", "absentTicks": row["partial.absentTicks"],
                        "restoreTick": restore, "unloadTick": unloads[0]["tick"], "loadTick": loads[0]["tick"]})
    return {"status": "PASS", "cases": results}


def run(args):
    own = Path(__file__).resolve()
    own_hash = digest(own)
    args.group = "power"
    result = operating.run(args)
    root = args.output.resolve()
    base_path = root / "native112-machine-operation-results.json"
    report = {"status": "FAIL", "wrapperSha256": own_hash, "baseReportSha256": digest(base_path),
              "completedAtUtc": datetime.now(timezone.utc).isoformat()}
    try:
        base = json.loads(base_path.read_text())
        if result != 0 or base.get("status") != "PASS" or base.get("protocolUnchanged") is not True:
            raise AssertionError("Underlying strict native operating/cold campaign failed")
        reports = root / "advantage-works-results"
        def read(path): return json.loads(path.read_text())
        report.update(validate_partial(read(reports / "machine-catalog-measured.json"),
            read(reports / "machine-operation-measured.json"), read(root / "machine-operation-fixture.json"),
            read(reports / "machine-inspection-restarted.json")))
        report["evidenceSha256"] = {str(path.relative_to(root)): digest(path) for path in (
            base_path, reports / "machine-catalog-measured.json", reports / "machine-operation-measured.json",
            root / "machine-operation-fixture.json", reports / "machine-inspection-restarted.json")}
    except Exception as error:
        report.update(status="FAIL", failure=str(error), traceback=traceback.format_exc())
    if digest(own) != own_hash:
        report.update(status="FAIL", failure="Partial adapter changed during its run")
    write_json(root / "native112-partial-results.json", report)
    return int(report["status"] != "PASS")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--port", type=int, required=True)
    raise SystemExit(run(parser.parse_args()))
