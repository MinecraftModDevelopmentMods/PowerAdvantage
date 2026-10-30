"""Requalify immutable historical fixtures with current, sealed native 1.13 jars."""

import argparse
from argparse import Namespace
from pathlib import Path
import socket
import traceback
from unittest.mock import patch

import matrix
import native113
import native113_connected
from native113_code import INVENTORY, Server, verify_code
from native113_lifecycle import read_json, require_native_boot
from native113_resource_audit import compare_snapshot_resources


def run(args):
    output = native113.output_path(args.output)
    for path in (args.target_manifest, args.previous, args.fixtures, args.source_manifest):
        if path is not None:
            native113.require_disjoint(output, path)
    target = native113.pinned_profile(args.target_manifest, "target113")
    pins = read_json(args.target_manifest)["target113"]
    helpers = [Path(__file__), Path(matrix.__file__), Path(native113_connected.__file__),
               Path(__file__).with_name("native113_resource_audit.py"),
               Path(__file__).with_name("semantic_machine_audit.py")]
    protocol = {str(path): matrix.digest(path) for path in helpers}
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", args.port))
    output.mkdir(parents=True, exist_ok=False)
    report = dict(status="RUNNING", scope=args.kind, targetManifest=str(args.target_manifest.resolve()),
                  targetManifestSha256=matrix.digest(args.target_manifest), protocolSha256=protocol,
                  routes=[])
    receipt = output / "native113-refresh-results.json"
    matrix.write_json(receipt, report)
    try:
        if args.kind == "frozen":
            if args.source_manifest is None or args.fixtures is None:
                raise ValueError("Frozen refresh requires archived profiles and immutable fixtures")
            profiles = read_json(args.source_manifest)
            profiles["target113"] = pins
            manifest = output / "profiles.json"
            matrix.write_json(manifest, profiles)
            original_stage = matrix.stage

            def stage(runtime, profile):
                launcher = original_stage(runtime, profile)
                properties = runtime / "server.properties"
                lines = [line for line in properties.read_text().splitlines()
                         if not line.startswith("server-port=")
                         and not (launcher == native113.LAUNCHER and line.startswith("generator-settings="))]
                properties.write_text("\n".join(lines) + f"\nserver-port={args.port}\n", encoding="utf-8")
                if launcher == native113.LAUNCHER:
                    verify_code(runtime, pins["serverCode"])
                    matrix.write_json(runtime / INVENTORY, pins["serverCode"])
                return launcher

            def snapshot(runtime, java, launcher, label, build=False):
                if build:
                    raise AssertionError("Historical source construction is forbidden")
                server = Server(runtime, java, launcher, label)
                try:
                    server.snapshot(label, False)
                    server.send("save-all flush")
                finally:
                    server.close()
                if launcher == native113.LAUNCHER:
                    require_native_boot(runtime / f"console-{label}.log")

            settings = Namespace(profile_manifest=manifest, workspace=None, output=output / "matrix",
                                 sources=None, fixtures=args.fixtures, java8=args.java8)
            with patch.object(matrix, "stage", stage), patch.object(matrix, "start_snapshot", snapshot):
                code = matrix.run(settings)
            original = read_json(settings.output / "matrix-results.json")
            for label, comparisons in original.items():
                name = label.rsplit("-", 1)[0]
                source = args.fixtures / name / "migration-source.json"
                runtime = settings.output / (label + "-113")
                resources = {phase: compare_snapshot_resources(source, runtime / f"migration-{phase}.json")
                             for phase in ("converted", "restarted")}
                passed = all(row["status"] == "PASS" for row in comparisons.values()) and all(
                    row["status"] == "PASS" for row in resources.values())
                report["routes"].append(dict(id=label, status="PASS" if passed else "FAIL",
                                              comparisons=comparisons, resources=resources))
            report["status"] = "PASS" if code == 0 and len(report["routes"]) == 8 and all(
                row["status"] == "PASS" for row in report["routes"]) else "FAIL"
        else:
            if args.previous is None:
                raise ValueError("Connected refresh requires exact retained source provenance")
            previous = read_json(args.previous)
            if previous.get("status") != "PASS":
                raise AssertionError("Archived source provenance is not a completed campaign")
            report["previousSha256"] = matrix.digest(args.previous)
            for index, row in enumerate(previous["sources"]):
                history = Path(row["report"])
                if matrix.digest(history) != row["sha256"]:
                    raise AssertionError("Archived source provenance changed")
                provenance = read_json(history)
                hashes = Path(provenance["sourceHashes"])
                if matrix.digest(hashes) != provenance["sourceHashesSha256"]:
                    raise AssertionError("Archived connected world fingerprint changed")
                settings = Namespace(source=Path(provenance["source"]),
                                     source_manifest=Path(provenance["sourceManifest"]),
                                     source_key=provenance["sourceKey"], source_hashes=hashes,
                                     source_checkpoint=provenance["sourceCheckpoint"],
                                     target_manifest=args.target_manifest, java8=args.java8,
                                     output=output / f"route-{index:02}", port=args.port, ticks=2048)
                code = native113_connected.run(settings)
                report["routes"].append(dict(source=str(settings.source),
                                              status="PASS" if code == 0 else "FAIL",
                                              runtime=str(settings.output)))
                matrix.write_json(receipt, report)
            report["status"] = "PASS" if len(report["routes"]) == 5 and all(
                row["status"] == "PASS" for row in report["routes"]) else "FAIL"
    except Exception as error:
        report.update(status="FAIL", failure=str(error), traceback=traceback.format_exc())
    finally:
        report["protocolUnchanged"] = protocol == {str(path): matrix.digest(path) for path in helpers}
        if not report["protocolUnchanged"] or matrix.digest(args.target_manifest) != report["targetManifestSha256"]:
            report["status"] = "FAIL"
        matrix.write_json(receipt, report)
    return int(report["status"] != "PASS")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("kind", choices=("frozen", "connected"))
    parser.add_argument("--target-manifest", type=Path, required=True)
    parser.add_argument("--source-manifest", type=Path)
    parser.add_argument("--fixtures", type=Path)
    parser.add_argument("--previous", type=Path)
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--port", type=int, required=True)
    raise SystemExit(run(parser.parse_args()))
