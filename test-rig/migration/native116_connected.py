"""Resume prebuilt, pinned connected source copies in two genuine native116 boots.

Only staging and process adapters are injected into connected.prepare/resume;
the connected state, stock and operation oracles are reused unchanged.
"""

import argparse
import json
from pathlib import Path
import re
import socket
import shutil
from unittest.mock import patch

import connected
from native116_code import CodeCustody, INVENTORY, verify_if_sealed
from matrix import WORLD, compare, digest, fingerprint, write_json
from native116 import (LAUNCHER, configure_creation, native_normalized, output_path, pinned_profile,
                       require_clean_log, require_disjoint)
from native116_save_io import capture_save, require_same_settings


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def require_command(command):
    if command in ("stop", "save-all", "save-all flush"):
        return
    if re.fullmatch(r"advconnected snapshot [A-Za-z0-9_-]+", command):
        return
    match = re.fullmatch(r"advconnected step ([0-9]+) [A-Za-z0-9_-]+", command)
    if match and 1 <= int(match[1]) <= 4000:
        return
    raise AssertionError("Forbidden native connected mutation: " + command)


def require_document(document, definition, *, destination):
    expected = {entry["id"]: entry for entry in definition["blocks"]}
    rows = document["snapshot"]
    actual = {entry["id"]: entry for entry in rows}
    if len(expected) != len(definition["blocks"]) or len(actual) != len(rows) or actual.keys() != expected.keys():
        raise AssertionError("Connected witness IDs/count changed")
    for key, entry in actual.items():
        for field in ("station", "kind", "name", "x", "y", "z"):
            if entry[field] != expected[key][field]:
                raise AssertionError(f"Connected witness changed: {key}.{field}")
    if not document.get("held"):
        raise AssertionError("Connected snapshot is not frozen")
    if destination and document.get("buildCallsThisProcess") != 0:
        raise AssertionError("Destination construction is forbidden")
    verdict = connected.state(document)
    if verdict["status"] != "PASS":
        raise AssertionError("Invalid connected stocks: " + json.dumps(verdict))
    return verdict


def require_native_boot(log):
    require_clean_log(log)
    text = log.read_text(encoding="utf-8", errors="replace")
    for marker in ("Starting minecraft server version 1.16.5",
                   "Forge mod loading, version 36.2.34, for MC 1.16.5 with MCP 20210115.111550"):
        if marker not in text:
            raise AssertionError("Missing genuine native116 boot proof: " + marker)


def verify_source(args):
    source = args.source.resolve()
    definition = read_json(source / "connected-fixture.json")
    if definition.get("format") != 1:
        raise ValueError("Unsupported connected fixture format")
    if definition.get("sourceProfile") != (getattr(args, "source_fixture_key", None) or args.source_key):
        raise ValueError("Source fixture profile does not match the explicit source key")
    before = fingerprint(source)
    expected = read_json(args.source_hashes)
    if not expected or fingerprint(source / WORLD) != expected:
        raise AssertionError("Protected connected source world fingerprint mismatch")
    profile = pinned_profile(args.source_manifest.resolve(), args.source_key)
    pins = sorted((jar.name, digest(jar)) for jar in profile[2])
    staged = read_json(source / "input-jars.json")
    if sorted((entry["file"], entry["sha256"].upper()) for entry in staged) != pins:
        raise AssertionError("Source fixture inputs differ from the explicit archived profile")
    if sorted((jar.name, digest(jar)) for jar in (source / "mods").glob("*.jar")) != pins:
        raise AssertionError("Source runtime jars differ from the explicit archived profile")
    if not re.fullmatch(r"[A-Za-z0-9_-]+", args.source_checkpoint):
        raise ValueError("Unsafe source checkpoint")
    snapshot = connected.read(source, args.source_checkpoint)
    if snapshot["snapshot"] != read_json(source / f"migration-{args.source_checkpoint}.json"):
        raise AssertionError("Retained connected and migration checkpoints disagree")
    verdict = require_document(snapshot, definition, destination=False)
    if snapshot.get("requestedTicks", 0) <= 0 or snapshot.get("advancedTicks") != snapshot["requestedTicks"]:
        raise AssertionError("Source checkpoint lacks an exact measured native interval")
    if args.source_checkpoint == "source":
        source_operation = connected.operation(connected.read(source, "cold"), snapshot, snapshot["requestedTicks"])
        if source_operation["status"] != "PASS":
            raise AssertionError("Retained source did not operate: " + json.dumps(source_operation))
    history = source.parent / "connected-results.json"
    historical = read_json(history) if history.is_file() else None
    if historical is not None and historical.get("sources", {}).get(args.source_key, {}).get("status") != "PASS":
        raise AssertionError("Retained source operation did not pass; do not manufacture a replacement")
    if fingerprint(source) != before:
        raise AssertionError("Source changed during preflight")
    return definition, snapshot, verdict, before, historical


def prepare_target(runtime, profile, definition, port, source, pins):
    if profile[1] != LAUNCHER:
        raise ValueError("Only the genuine native116 launcher is permitted")
    verify_if_sealed(profile[0])
    launcher = connected.prepare(runtime, profile, definition, port, source)
    if (profile[0] / INVENTORY).is_file():
        shutil.copy2(profile[0] / INVENTORY, runtime / INVENTORY)
        verify_if_sealed(runtime)
    configure_creation(runtime)
    for marker in ("connected-build.allow", "connected-built.json"):
        if (runtime / marker).exists():
            raise AssertionError("Source construction authority leaked into the destination")
    expected = sorted((Path(jar["path"]).name, jar["sha256"].upper()) for jar in pins["jars"])
    actual = sorted((jar.name, digest(jar)) for jar in (runtime / "mods").glob("*.jar"))
    if actual != expected:
        raise AssertionError("Staged native candidate jars differ from the pinned profile")
    if digest(runtime / launcher) != pins["launcher_sha256"].upper():
        raise AssertionError("Staged native launcher differs from the pinned profile")
    return launcher


class NativeConnectedServer(CodeCustody, connected.ConnectedServer):
    def __init__(self, runtime, java, launcher, label, on_loaded):
        if launcher != LAUNCHER:
            raise AssertionError("Refusing a non-native116 process")
        self.on_loaded = on_loaded
        self.loaded_verified = False
        super().__init__(runtime, java, launcher, label)
        try:
            require_native_boot(Path(self.log.name))
        except BaseException:
            self.close()
            raise

    def send(self, command):
        require_command(command)
        if command.startswith("advconnected step ") and not self.loaded_verified:
            raise AssertionError("Preservation must pass before any destination operation")
        super().send(command)

    def observe(self, operation_name, label, ticks=0):
        super().observe(operation_name, label, ticks)
        if operation_name == "snapshot" and ticks == 0:
            self.on_loaded(label)
            self.loaded_verified = True


def continued_output(first, last):
    initial, final = connected.state(first), connected.state(last)
    missing = [key for key, case in initial["cases"].items()
               if key != "battery" and case["remaining"] > 0
               and final["cases"][key]["produced"] <= case["produced"]]
    return dict(status="FAIL" if missing else "PASS", failures=missing)


def run(args):
    if not 1 <= args.port <= 65535 or not 1 <= args.ticks <= 4000:
        raise ValueError("Port/tick interval outside native command bounds")
    output = output_path(args.output)
    for protected in (args.source, args.source_manifest, args.source_hashes, args.target_manifest):
        require_disjoint(output, protected)
    definition, checkpoint, source_state, source_before, historical = verify_source(args)
    target = pinned_profile(args.target_manifest.resolve(), "target116")
    target_pins = read_json(args.target_manifest)["target116"]
    identities = {entry["id"] for entry in target_pins["jars"]}
    if not {"steamadvantage", "electricadvantage"}.issubset(identities):
        raise ValueError("Connected qualification requires native Steam and Electric candidates")
    require_disjoint(output, target[0])
    for jar in target[2]:
        require_disjoint(output, jar)
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", args.port))
    output.mkdir(parents=True, exist_ok=False)
    runtime = output / "target116"
    report_path = output / "native116-connected-results.json"
    report = dict(status="RUNNING", scope="prebuilt-connected-source-to-native116-two-boots",
                  protocol="No source process; destination snapshot/step/save/stop only; no rebuilding/refilling/resetting",
                  source=str(args.source.resolve()), sourceKey=args.source_key,
                  sourceFixtureKey=getattr(args, "source_fixture_key", None) or args.source_key,
                  sourceCheckpoint=args.source_checkpoint, sourceState=source_state,
                  sourceManifest=str(args.source_manifest.resolve()), sourceManifestSha256=digest(args.source_manifest),
                  sourceHashes=str(args.source_hashes.resolve()), sourceHashesSha256=digest(args.source_hashes),
                  sourceRuntimeBefore=source_before, sourceWorldExpected=read_json(args.source_hashes),
                  sourceCheckpointSha256=digest(args.source / f"connected-{args.source_checkpoint}.json"),
                  targetManifest=str(args.target_manifest.resolve()), targetManifestSha256=digest(args.target_manifest),
                  targetProfile=target_pins, port=args.port, ticks=args.ticks,
                  runnerSha256=digest(Path(__file__)), oracleSha256=digest(Path(connected.__file__)),
                  historicalCampaign=historical, results={})
    write_json(report_path, report)
    try:
        launcher = prepare_target(runtime, target, definition, args.port, args.source, target_pins)
        report["stagedInputs"] = read_json(runtime / "input-jars.json")
        for label, previous in (("converted", args.source / f"migration-{args.source_checkpoint}.json"),
                                ("restarted", runtime / "migration-converted-worked.json")):
            def on_loaded(loaded_label):
                loaded = connected.read(runtime, loaded_label)
                require_document(loaded, definition, destination=True)
                if loaded.get("requestedTicks") != 0 or loaded.get("advancedTicks") != 0:
                    raise AssertionError("Initial preservation requires a frozen zero-step snapshot")
                result = compare(previous, runtime / f"migration-{loaded_label}.json", native_normalized)
                report["results"][label + "_preservation"] = result
                write_json(report_path, report)
                if result["status"] != "PASS":
                    raise AssertionError(label + " native116 preservation failed")

            def boot(current, settings, selected_launcher, selected_label):
                if current.resolve() != runtime.resolve():
                    raise AssertionError("Only the newly staged target may be launched")
                return NativeConnectedServer(current, settings.java8, selected_launcher, selected_label, on_loaded)

            # Dependency injection is confined to the process factory, never an oracle.
            with patch.object(connected, "boot", boot):
                verdict = connected.resume(runtime, args, launcher, label)
            report["results"][label + "_operation"] = verdict
            require_document(connected.read(runtime, label + "-worked"), definition, destination=True)
            require_native_boot(runtime / f"console-{label}.log")
            report["results"][label + "_native_boot"] = dict(status="PASS", launcher=launcher)
            report.setdefault("nativeSaveFormat", {})[label] = capture_save(
                runtime, label, runtime / f"migration-{label}-worked.json")
            write_json(report_path, report)
        require_same_settings(report["nativeSaveFormat"]["converted"], report["nativeSaveFormat"]["restarted"])
        report["results"]["real_output_across_restarts"] = continued_output(
            connected.read(runtime, "converted-loaded"), connected.read(runtime, "restarted-worked"))
        report["status"] = "PASS" if all(result["status"] == "PASS" for result in report["results"].values()) else "FAIL"
    except BaseException as error:
        report.update(status="FAIL", failure=str(error))
        raise
    finally:
        source_after = fingerprint(args.source)
        report["sourceRuntimeAfter"] = source_after
        preserved = source_after == source_before
        report["sourcePreservation"] = dict(status="PASS" if preserved else "FAIL")
        if not preserved:
            report.update(status="FAIL", failure="Protected source runtime changed")
        write_json(report_path, report)
        if not preserved:
            raise AssertionError("Protected source runtime changed")
    print(report["status"] + ": " + str(report_path), flush=True)
    return int(report["status"] != "PASS")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--source-manifest", type=Path, required=True)
    parser.add_argument("--source-key", required=True)
    parser.add_argument("--source-fixture-key", help="Explicit retained assembly lineage when the source manifest is target113/target114")
    parser.add_argument("--source-hashes", type=Path, required=True)
    parser.add_argument("--source-checkpoint", default="source")
    parser.add_argument("--target-manifest", type=Path, required=True)
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--port", type=int, default=25576)
    parser.add_argument("--ticks", type=int, default=512)
    return run(parser.parse_args())


if __name__ == "__main__":
    raise SystemExit(main())
