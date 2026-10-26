"""Rerun archived frozen/connected fixture inputs against a new pinned candidate.

Previous results select immutable inputs only. Every destination is new and all
state/operating verdicts are recomputed by the existing native runners.
"""

import argparse
from argparse import Namespace
from pathlib import Path
import socket
import traceback
from unittest.mock import patch

import native114
import native114_connected
from matrix import digest, write_json
from native114_lifecycle import read_json


def snapshot_name(source, expected):
    matches = sorted(p.name for p in source.glob("migration-*.json") if digest(p) == expected)
    if not matches:
        raise AssertionError("No retained snapshot matches its archived digest")
    # Frozen conversion/restart snapshots can be byte-for-byte identical.
    # Identity remains the exact archived SHA, never a preferred passing state.
    return "migration-restarted.json" if "migration-restarted.json" in matches else matches[0]


def run(args):
    output = native114.output_path(args.output)
    for protected in (args.previous, args.target_manifest):
        native114.require_disjoint(output, protected)
    native114.pinned_profile(args.target_manifest.resolve(), "target114")
    previous = read_json(args.previous)
    if previous.get("status") != "PASS":
        raise AssertionError("Select a completed archived campaign as fixture provenance")
    output.mkdir(parents=True, exist_ok=False)
    report = dict(status="RUNNING", scope=args.kind, previous=str(args.previous.resolve()),
                  previousSha256=digest(args.previous), targetManifest=str(args.target_manifest.resolve()),
                  targetManifestSha256=digest(args.target_manifest), routes=[])
    rows = previous["routes"] if args.kind == "frozen" else previous["sources"]
    for index, row in enumerate(rows):
        outcome = dict(index=index, status="RUNNING")
        report["routes"].append(outcome)
        try:
            with socket.socket() as listener:
                listener.bind(("127.0.0.1", args.port))
            if args.kind == "frozen":
                old = Path(row["runtime"]) / "native114-provenance.json"
                if digest(old) != row["provenanceSha256"]:
                    raise AssertionError("Archived fixture provenance changed")
                provenance = read_json(old)
                source = Path(provenance["source"])
                hashes = output / f"source-{index:02}-approved-hashes.json"
                write_json(hashes, provenance["sourceHashes"])
                selected = snapshot_name(source, provenance["sourceSnapshotSha256"])
                settings = Namespace(source=source, source_manifest=Path(provenance["sourceManifest"]),
                                     source_key=provenance["sourceKey"], source_hashes=hashes,
                                     source_snapshot=selected, target_manifest=args.target_manifest,
                                     java8=args.java8, output=output / f"route-{index:02}")
                stage = native114.stage
                def native_stage(runtime, profile):
                    launcher = stage(runtime, profile)
                    properties = runtime / "server.properties"
                    lines = [line for line in properties.read_text().splitlines()
                             if not line.startswith(("server-port=", "generator-settings="))]
                    properties.write_text("\n".join(lines) + f"\nserver-port={args.port}\ngenerator-settings=\n", encoding="utf-8")
                    return launcher
                with patch.object(native114, "stage", native_stage):
                    code = native114.run(settings)
                outcome.update(source=str(source), sourceSnapshot=selected, runtime=str(settings.output),
                               comparison=read_json(settings.output / "state-comparison.json"))
            else:
                old = Path(row["report"])
                if digest(old) != row["sha256"]:
                    raise AssertionError("Archived connected provenance changed")
                provenance = read_json(old)
                source = Path(provenance["source"])
                hashes = Path(provenance["sourceHashes"])
                if digest(hashes) != provenance["sourceHashesSha256"]:
                    raise AssertionError("Archived source fingerprint changed")
                settings = Namespace(source=source, source_manifest=Path(provenance["sourceManifest"]),
                                     source_key=provenance["sourceKey"], source_hashes=hashes,
                                     source_checkpoint=provenance["sourceCheckpoint"], target_manifest=args.target_manifest,
                                     java8=args.java8, output=output / f"route-{index:02}",
                                     ticks=512, port=args.port)
                code = native114_connected.run(settings)
                outcome.update(source=str(source), runtime=str(settings.output),
                               result=str(settings.output / "native114-connected-results.json"))
            outcome["status"] = "PASS" if code == 0 else "FAIL"
        except Exception as error:
            outcome.update(status="FAIL", failure=str(error), traceback=traceback.format_exc())
        write_json(output / "native114-refresh-results.json", report)
        print(f"{args.kind}/{index + 1}/{len(rows)}: {outcome['status']}", flush=True)
    report["status"] = "PASS" if len(rows) == (13 if args.kind == "frozen" else 5) and all(
        row["status"] == "PASS" for row in report["routes"]) else "FAIL"
    write_json(output / "native114-refresh-results.json", report)
    return int(report["status"] != "PASS")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("kind", choices=("frozen", "connected"))
    parser.add_argument("--previous", type=Path, required=True)
    parser.add_argument("--target-manifest", type=Path, required=True)
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--port", type=int, default=25595)
    raise SystemExit(run(parser.parse_args()))
