"""Read-only replay of native respawn quantities, source items and saved players."""

import argparse
import json
from pathlib import Path

import nbtlib
from actors import enchantments, semantic
from dimensions import PLAYER_STATE, exact_item_differences
from matrix import WORLD, differences, digest, fingerprint, parse_nbt, write_json
from native114 import pinned_profile, require_clean_log
from native114_actors import baseline
from native114_respawn import native_respawn_verdict
from respawn import spawn_reference


def replay(root):
    results = json.loads((root / "results.json").read_text())
    sources = json.loads((root / "source-fingerprints.json").read_text())
    summary = json.loads((root / "summary.json").read_text())
    if digest(Path(summary["targetManifest"])) != summary["targetManifestSha256"]:
        raise AssertionError("Respawn target manifest changed")
    pinned_profile(Path(summary["targetManifest"]), "target114")
    findings, fresh = [], {}
    for route, source_entry in sources.items():
        source = Path(source_entry["path"])
        if fingerprint(source) != source_entry["fingerprint"]:
            findings.append(route + ": protected source changed")
        runtime = root / route
        expected_file = baseline(source, "dimensions")[0]["file"]
        expected_items = {key: expected_file[key] for key in ("Inventory", "EnderItems")}
        for jar in json.loads((runtime / "input-jars.json").read_text()):
            if digest(runtime / "mods" / jar["file"]) != jar["sha256"]:
                findings.append(route + ": staged mod changed")
        for label in ("respawn", "respawn-restart"):
            require_clean_log(runtime / ("console-" + label + ".log"))
        for case in (case for case in results if case.startswith(route + "/")):
            label = case.split("/")[1]
            folder = runtime / "respawn-evidence" / label
            record = json.loads((runtime / f"dimensions-{label}.json").read_text())
            registry = enchantments(runtime)
            result = native_respawn_verdict(record, registry, spawn_reference(folder / "level.dat"))
            before = semantic(parse_nbt(record["respawn"]["before"]), PLAYER_STATE, registry)
            source_issues = differences(expected_items, before, "source-inventory")
            source_issues.extend(exact_item_differences(expected_items, before, "source-inventory"))
            result["differences"].extend(source_issues)
            observed = semantic(parse_nbt(record["respawn"]["after"]), PLAYER_STATE, registry)
            saved = semantic(nbtlib.load(folder / "player.dat").unpack(), PLAYER_STATE, registry)
            save_issues = differences(observed, saved, "saved-player") + exact_item_differences(observed, saved, "saved-player")
            result["differences"].extend(save_issues)
            if source_issues or save_issues:
                result["status"] = "FAIL"
            fresh[case] = result
            if result != results[case]:
                findings.append(case + ": raw oracle result differs from recorded result")
            if result["status"] != "PASS":
                findings.append(case + ": behavioral assertion fails")
    if len(fresh) != summary["cases"] or len(fresh) != len(results):
        findings.append("Respawn case coverage is incomplete")
    return {"status": "FAIL" if findings else "PASS", "cases": len(fresh), "findings": findings,
            "results": fresh, "realClientDeathScreen": "NOT_TESTED"}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise FileExistsError(args.output)
    result = replay(args.run.resolve())
    write_json(args.output, result)
    print(result["status"] + ": " + str(result["cases"]) + " cases; " + str(len(result["findings"])) + " integrity findings")
    raise SystemExit(int(result["status"] != "PASS"))
