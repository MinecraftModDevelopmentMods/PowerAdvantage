"""Native destinations for the historical player-only bag/Ender layout oracle."""

import argparse
import json
from pathlib import Path
import shutil
import socket

from native116 import boot as native116_boot
import actors
from native116_actor_io import capture_actors, capture_dimensions
from dimensions import exact_item_differences
from matrix import WORLD, copy_fixture, digest, fingerprint, write_json
from native116_code import Server
from native116 import output_path, pinned_profile, require_clean_log, require_disjoint
from native116_actors import baseline, command, raw
from player_layout import player_verdict


ROUTES = ("original110", "maintained110", "maintained112", "original110-chained-113",
          "maintained110-direct-113", "maintained112-direct-113")


def verdict(expected, observed):
    result = player_verdict(expected, observed)
    extra = exact_item_differences({key: expected[key] for key in ("file", "adapter")}, observed)
    result["differences"].extend(extra)
    if extra:
        result["status"] = "FAIL"
    return result


def audit(root):
    summary = json.loads((root / "summary.json").read_text())
    findings = []
    manifest = Path(summary["targetManifest"])
    if digest(manifest) != summary["targetManifestSha256"]:
        findings.append("Target manifest changed")
    pinned_profile(manifest, "target116")
    for route in summary["results"]:
        runtime = root / route
        provenance = json.loads((runtime / "source-provenance.json").read_text())
        source = Path(provenance["source"])
        if fingerprint(source) != provenance["fingerprint"]:
            findings.append(route + ": protected source changed")
        expected, _ = baseline(source, "actors")
        for jar in json.loads((runtime / "input-jars.json").read_text()):
            if digest(runtime / "mods" / jar["file"]) != jar["sha256"]:
                findings.append(route + ": staged jar changed")
        for label in ("converted", "restarted"):
            state = capture_actors(runtime, label, runtime / "actor-evidence" / label)
            current = {"player": verdict(expected, state), "frozen": actors.freeze_result(raw(runtime, "actors", label))}
            if current != summary["results"][route][label] or any(value["status"] != "PASS" for value in current.values()):
                findings.append(route + "/" + label + ": raw player/freeze replay differs or fails")
            require_clean_log(runtime / ("console-" + label + ".log"))
    return {"status": "FAIL" if findings else "PASS", "routes": len(summary["results"]), "findings": findings,
            "scope": "Player storage only; no actor release or turbine operation assertion"}


def run(args):
    root = output_path(args.output)
    require_disjoint(root, args.fixtures)
    target = pinned_profile(args.target_manifest.resolve(), "target116")
    with socket.socket() as port:
        port.bind(("127.0.0.1", args.port))
    root.mkdir(parents=True)
    results = {}
    for route in ROUTES:
        source = args.fixtures / route
        before = fingerprint(source)
        approved = source / "fixture-hashes.json"
        if approved.is_file() and fingerprint(source / WORLD) != json.loads(approved.read_text()):
            raise AssertionError("Protected layout fixture changed")
        expected, source_label = baseline(source, "actors")
        runtime = root / route
        launcher = native116_boot(runtime, target, args.java8, args.port)
        copy_fixture(source, runtime)
        shutil.copy2(source / "actor-fixture.json", runtime / "actor-fixture.json")
        write_json(runtime / "source-provenance.json", {"source": str(source.resolve()), "fingerprint": before, "checkpoint": source_label})
        checks = {}
        try:
            for label in ("converted", "restarted"):
                server = Server(runtime, args.java8, launcher, label)
                try:
                    state = command(server, runtime, "actors", "read", label)
                    checks[label] = {"player": verdict(expected, state), "frozen": actors.freeze_result(raw(runtime, "actors", label))}
                finally:
                    server.close()
                require_clean_log(runtime / ("console-" + label + ".log"))
        finally:
            write_json(runtime / "player-comparison.json", checks)
            if fingerprint(source) != before:
                raise AssertionError("Protected player-layout source changed")
        results[route] = checks
        write_json(root / "results.json", results)
        print(route + ": " + ", ".join(label + "=" + check["player"]["status"] for label, check in checks.items()), flush=True)
    status = "FAIL" if any(value["status"] != "PASS" for checks in results.values() for phase in checks.values() for value in phase.values()) else "PASS"
    write_json(root / "summary.json", {"status": status, "results": results, "sourceFixtures": "UNCHANGED",
               "targetManifest": str(args.target_manifest.resolve()), "targetManifestSha256": digest(args.target_manifest),
               "scope": "Unchanged historical player-only oracle plus exact item NBT and genuinely frozen actor guard"})
    return int(status != "PASS")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--audit", type=Path)
    parser.add_argument("--fixtures", type=Path)
    parser.add_argument("--target-manifest", type=Path)
    parser.add_argument("--java8", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--port", type=int, choices=(25574, *range(25584, 25588)))
    args = parser.parse_args()
    if args.audit:
        if args.output.exists():
            raise FileExistsError(args.output)
        result = audit(args.audit.resolve())
        write_json(args.output, result)
        print(result["status"] + ": " + str(result["routes"]) + " routes; " + str(len(result["findings"])) + " integrity findings")
        raise SystemExit(int(result["status"] != "PASS"))
    if any(value is None for value in (args.fixtures, args.target_manifest, args.java8, args.port)):
        parser.error("Running requires fixtures, target manifest, Java 8 and port")
    raise SystemExit(run(args))
