"""Read-only replay of native actor/dimension evidence and exact item NBT."""

import argparse
import json
from pathlib import Path

import actors
from native116_actor_io import capture_actors, capture_dimensions
import dimensions
from matrix import WORLD, digest, fingerprint, write_json
from native116 import pinned_profile, require_clean_log
from native116_actors import baseline, expected_state, native_dimension_state, raw
from native116_dimension_assessment import assess, summarize_checks
import native116_dimension_assessment


def inherited_sources(proofs):
    """Check the old receipts' original sources; never approve an old destination."""
    receipts = []
    for proof in proofs:
        value = json.loads(proof.read_text())
        if "sourceFingerprint" in value:
            records = [{"source": value["source"], "fingerprint": value["sourceFingerprint"]}]
        elif "source" in value and "fingerprint" in value:
            records = [value]
        else:
            records = list(value.values())
        if not records:
            raise AssertionError("Empty inherited source proof: " + str(proof))
        sources = []
        for record in records:
            source = Path(record.get("source", record.get("path", ""))).resolve()
            expected = record["fingerprint"]
            if not expected or fingerprint(source) != expected:
                raise AssertionError("Original source differs from inherited proof: " + str(source))
            sources.append({"source": str(source), "fingerprint": expected})
        receipts.append({"proof": str(proof.resolve()), "proofSha256": digest(proof), "sources": sources})
    return {"status": "PASS", "scope": "Original source custody only; no destination qualification", "receipts": receipts}


def persistence_controls(runtime, kind):
    """Audit receipts from the bounded live runner; never infer hook-list contents."""
    counts = (2, 2, 1) if kind == "dimensions" else (2, 3, 2)
    records = {}
    markers = ("WorldPersistenceHooks.lambda$handleWorldDataLoad",
               "WorldPersistenceHooks.lambda$handleWorldDataSave",
               "Exception reading", "Exception stopping the server",
               "An unexpected error occurred trying to execute that command",
               "NullPointerException")
    for label, required in zip(("conversion", "restart", "runtime-restart"), counts):
        path = runtime / ("console-" + label + ".log")
        if not path.is_file():
            records[label] = {"status": "FAIL", "reason": "Server log missing"}
            continue
        lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
        errors = [{"line": index + 1, "text": line} for index, line in enumerate(lines)
                  if "/ERROR]" in line or any(marker in line for marker in markers)]
        saved = sum("Saved the" in line for line in lines)
        started = any("Done (" in line for line in lines)
        stopped = any("Stopping server" in line for line in lines)
        records[label] = {"status": "PASS" if started and stopped and saved == required and not errors else "FAIL",
                          "log": str(path), "logSha256": digest(path), "started": started,
                          "stopMarkerPresent": stopped, "saveReceipts": saved,
                          "requiredSaveReceipts": required, "errors": errors}
    return {"status": "PASS" if all(value["status"] == "PASS" for value in records.values()) else "FAIL",
            "boots": records, "liveRunnerBudgetsSeconds": {"startup": 300, "probe": 120,
                "saveReceipt": 45, "shutdown": 90},
            "boundsAndExitCode": "Enforced by unchanged native116_actors.command and matrix.Server",
            "nativeHookListInspection": "Parent-owned; not inferred from absence of log errors"}


def replay(run, kind, persistence=False):
    recorded = json.loads((run / "results.json").read_text())
    findings, routes, native_only_findings = [], {}, set()
    capture = capture_dimensions if kind == "dimensions" else capture_actors
    verdict = dimensions.verdict if kind == "dimensions" else actors.compare
    tick = dimensions.tick_result if kind == "dimensions" else actors.tick_result
    for route, checks in recorded.items():
        runtime = run / route
        provenance = json.loads((runtime / "native116-source-provenance.json").read_text())
        source = Path(provenance["source"])
        if fingerprint(source) != provenance["sourceFingerprint"]:
            findings.append(route + ": protected source fingerprint changed")
        manifest = Path(provenance["targetManifest"])
        if digest(manifest) != provenance["targetManifestSha256"]:
            findings.append(route + ": target manifest changed")
        pinned_profile(manifest, "target116")
        for jar in json.loads((runtime / "input-jars.json").read_text()):
            if digest(runtime / "mods" / jar["file"]) != jar["sha256"]:
                findings.append(route + ": staged jar changed: " + jar["file"])
        controls = persistence_controls(runtime, kind) if persistence else None
        if controls and controls["status"] != "PASS":
            findings.append(route + ": bounded persistence controls fail")
        if "campaign-error" in checks:
            findings.append(route + ": aborted route has no completed behavioral qualification")
            for log in runtime.glob("console-*.log"):
                try:
                    require_clean_log(log)
                except AssertionError as error:
                    findings.append(route + ": " + str(error))
            routes[route] = {"status": "FAIL", "recordedCampaignError": checks["campaign-error"],
                             "semanticReplay": "NOT_POSSIBLE_WITH_INCOMPLETE_SAVED_EVIDENCE"}
            if persistence:
                routes[route]["persistenceControls"] = controls
            continue
        expected, _ = baseline(source, kind)
        states = {}
        live = kind == "dimensions"
        prefix = "dimension-semantic-" if live else "semantic-"
        evidence = "dimension-evidence" if live else "actor-evidence"
        for label in ("converted", "adapter", "restarted", "runtime", "runtime-restart"):
            state = capture(runtime, label, runtime / evidence / label)
            if live:
                state = native_dimension_state(state)
            stored = json.loads((runtime / (prefix + label + ".json")).read_text())
            if state != stored:
                findings.append(route + "/" + label + ": stored semantic differs from raw evidence")
            states[label] = state
        fresh = {
            "conversion": verdict(expected_state(expected, kind, survival=live, adapter=False), states["converted"]),
            "adapter": verdict(expected_state(expected, kind, survival=live), states["adapter"]),
            "restart": verdict(expected_state(expected, kind, survival=live), states["restarted"]),
            "idempotence": verdict(expected_state(states["adapter"], kind, survival=live), states["restarted"]),
            "runtime": verdict(expected_state(expected, kind, survival=True), states["runtime"]),
            "runtime-restart": verdict(expected_state(expected, kind, survival=True), states["runtime-restart"]),
            "runtime-ticks": tick(raw(runtime, kind, "released"), raw(runtime, kind, "runtime")),
            "restart-ticks": tick(raw(runtime, kind, "restart-released"), raw(runtime, kind, "runtime-restart")),
        }
        if live:
            fresh["conversion-ticks"] = tick(raw(runtime, kind, "conversion-release"), raw(runtime, kind, "converted"))
        else:
            for key, label in (("conversion-frozen", "converted"), ("adapter-frozen", "adapter"), ("restart-frozen", "restarted")):
                fresh[key] = actors.freeze_result(raw(runtime, kind, label))
        backups = actors.backup_state(runtime)
        fresh["backup-stability"] = {"status": "PASS" if backups == checks["backup-stability"]["backups"] else "FAIL",
                                     "backups": backups}
        platform, semantic_assessments = None, {}
        if live:
            sidecar = runtime / "native116-platform-assessment.json"
            if not sidecar.is_file():
                findings.append(route + ": missing separate native platform assessment")
            else:
                recorded_platform = json.loads(sidecar.read_text())
                proof = recorded_platform.get("proofPath")
                pairs = {
                    "conversion": (expected_state(expected, kind, survival=True, adapter=False), states["converted"], False),
                    "adapter": (expected_state(expected, kind, survival=True), states["adapter"], True),
                    "restart": (expected_state(expected, kind, survival=True), states["restarted"], True),
                    "idempotence": (expected_state(states["adapter"], kind, survival=True), states["restarted"], True),
                    "runtime": (expected_state(expected, kind, survival=True), states["runtime"], True),
                    "runtime-restart": (expected_state(expected, kind, survival=True), states["runtime-restart"], True),
                }
                semantic_assessments = {key: assess(before, after, fresh[key], proof, "survival", adapter)
                                        for key, (before, after, adapter) in pairs.items()}
                platform = {"assessment": summarize_checks(fresh, semantic_assessments),
                            "semanticAssessments": semantic_assessments, "proofPath": proof,
                            "assessorSha256": digest(Path(native116_dimension_assessment.__file__))}
                if platform != recorded_platform:
                    findings.append(route + ": separate native assessment differs from independent replay")
                    platform["assessment"]["status"] = "FAIL"
        strict_items = {}
        for label in ("converted", "adapter", "restarted", "runtime", "runtime-restart"):
            strict = expected_state(expected, kind, survival=live or label.startswith("runtime"), adapter=label != "converted")
            issues = dimensions.exact_item_differences(strict, states[label])
            strict_items[label] = {"status": "FAIL" if issues else "PASS", "differences": issues}
            if issues:
                findings.append(route + "/" + label + ": exact nested item NBT mismatch")
        for key, value in fresh.items():
            if value != checks[key]:
                findings.append(route + "/" + key + ": recomputed oracle differs from recorded result")
            if value["status"] != "PASS":
                if semantic_assessments.get(key, {}).get("status") == "PASS_WITH_NATIVE_DIFFERENCE":
                    native_only_findings.add(len(findings))
                findings.append(route + "/" + key + ": behavioral assertion fails")
        for label in ("conversion", "restart", "runtime-restart"):
            require_clean_log(runtime / ("console-" + label + ".log"))
        routes[route] = {"oracles": fresh, "exactItems": strict_items,
                         "carriedItems": sum(item["Count"] for item in expected["file"].get("Inventory", [])),
                         "enderItems": sum(item["Count"] for item in expected["file"].get("EnderItems", []))}
        if persistence:
            routes[route]["persistenceControls"] = controls
        if live and platform:
            routes[route]["nativePlatformAssessment"] = platform
    result = {"status": "FAIL" if findings else "PASS", "kind": kind, "routes": routes, "findings": findings}
    if kind == "dimensions":
        summary_file = run / "summary.json"
        summary = json.loads(summary_file.read_text()) if summary_file.is_file() else {}
        complete = (bool(recorded) and summary.get("kind") == kind
                    and len(summary.get("routes", [])) == len(recorded)
                    and set(summary.get("routes", [])) == set(recorded))
        integrity = [finding for index, finding in enumerate(findings) if index not in native_only_findings]
        statuses = [value.get("nativePlatformAssessment", {}).get("assessment", {}).get("status")
                    for value in routes.values()]
        status = "FAIL"
        if complete and not integrity and all(value in ("PASS", "PASS_WITH_NATIVE_DIFFERENCE") for value in statuses):
            status = "PASS_WITH_NATIVE_DIFFERENCE" if "PASS_WITH_NATIVE_DIFFERENCE" in statuses else "PASS"
        result["nativePlatformAssessment"] = {"status": status, "rawStrictStatus": result["status"],
                                              "completeCampaign": complete, "otherFindings": integrity,
                                              "rawNativeDifferenceFindingIndexes": sorted(native_only_findings)}
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", type=Path)
    parser.add_argument("--kind", choices=("actors", "dimensions"))
    parser.add_argument("--source-proofs", type=Path, nargs="+")
    parser.add_argument("--persistence-controls", action="store_true")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise FileExistsError(args.output)
    if args.source_proofs:
        result = inherited_sources(args.source_proofs)
        write_json(args.output, result)
        print("PASS: " + str(len(result["receipts"])) + " inherited source proofs")
        raise SystemExit(0)
    if args.run is None or args.kind is None:
        parser.error("Replay requires run and kind, or explicit inherited source proofs")
    result = replay(args.run.resolve(), args.kind, args.persistence_controls)
    write_json(args.output, result)
    print(result["status"] + ": " + str(len(result["routes"])) + " routes; " + str(len(result["findings"])) + " integrity findings")
    raise SystemExit(int(result["status"] != "PASS"))
