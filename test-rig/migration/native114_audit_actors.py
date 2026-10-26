"""Read-only replay of native actor/dimension evidence and exact item NBT."""

import argparse
import json
from pathlib import Path

import actors
import dimensions
from matrix import WORLD, digest, fingerprint, write_json
from native114 import pinned_profile, require_clean_log
from native114_actors import baseline, expected_state, native_dimension_state, raw


def replay(run, kind):
    recorded = json.loads((run / "results.json").read_text())
    findings, routes = [], {}
    capture = dimensions.capture if kind == "dimensions" else actors.capture
    verdict = dimensions.verdict if kind == "dimensions" else actors.compare
    tick = dimensions.tick_result if kind == "dimensions" else actors.tick_result
    for route, checks in recorded.items():
        runtime = run / route
        provenance = json.loads((runtime / "native114-source-provenance.json").read_text())
        source = Path(provenance["source"])
        if fingerprint(source) != provenance["sourceFingerprint"]:
            findings.append(route + ": protected source fingerprint changed")
        manifest = Path(provenance["targetManifest"])
        if digest(manifest) != provenance["targetManifestSha256"]:
            findings.append(route + ": target manifest changed")
        pinned_profile(manifest, "target114")
        for jar in json.loads((runtime / "input-jars.json").read_text()):
            if digest(runtime / "mods" / jar["file"]) != jar["sha256"]:
                findings.append(route + ": staged jar changed: " + jar["file"])
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
                findings.append(route + "/" + key + ": behavioral assertion fails")
        if actors.backup_state(runtime) != checks["backup-stability"]["backups"]:
            findings.append(route + ": migrated backup bytes changed")
        for label in ("conversion", "restart", "runtime-restart"):
            require_clean_log(runtime / ("console-" + label + ".log"))
        routes[route] = {"oracles": fresh, "exactItems": strict_items,
                         "carriedItems": sum(item["Count"] for item in expected["file"].get("Inventory", [])),
                         "enderItems": sum(item["Count"] for item in expected["file"].get("EnderItems", []))}
    return {"status": "FAIL" if findings else "PASS", "kind": kind, "routes": routes, "findings": findings}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", type=Path, required=True)
    parser.add_argument("--kind", choices=("actors", "dimensions"), required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise FileExistsError(args.output)
    result = replay(args.run.resolve(), args.kind)
    write_json(args.output, result)
    print(result["status"] + ": " + str(len(result["routes"])) + " routes; " + str(len(result["findings"])) + " integrity findings")
    raise SystemExit(int(result["status"] != "PASS"))
