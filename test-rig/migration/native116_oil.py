"""Qualify native116 bucket/drain/pipe transport in a disposable pinned runtime.

The original oil_transfer geometry and quantity/identity oracles are unchanged.
Only the initial empty construction and two native bucket placements may mutate
the fixture; subsequent boots must retain exactly 1000, then 2000 mB per lane.
"""

import argparse
from copy import deepcopy
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import socket
import time
import traceback

from lifecycle import LifecycleRun, at, parse
from matrix import digest, fingerprint, stage, write_json
from native116 import stage
from native116 import (LAUNCHER, RIG, output_path, pinned_profile,
                       require_clean_log, require_disjoint, jar_identity)
import oil_transfer


MINERALOGY_VERSION = "6.1.4.116051"
MINERALOGY_SOURCE_COMMIT = "f6d603d54ee7e6ad7424a5dd44e7a2858c89c846"
BOOT_LABELS = ("oil-first-fill", "oil-restart-refill", "oil-second-restart")
CONFIG_NOTICE = "[ne.mi.co.ForgeConfig/CORE]: Forge config just got changed on the file system!"


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def fixtures(pins):
    definition = deepcopy(oil_transfer.fixtures())
    mineralogy = [jar for jar in pins["jars"] if jar["id"] == "mineralogy"]
    if mineralogy:
        if (len(mineralogy) != 1 or mineralogy[0]["version"] != MINERALOGY_VERSION
                or mineralogy[0].get("sourceRef") != MINERALOGY_SOURCE_COMMIT):
            raise ValueError("Mineralogy oil lanes require the parent-pinned native 6.1.4.116051 candidate")
        pin = mineralogy[0]
        if not re.fullmatch(r"[A-Fa-f0-9]{64}", pin.get("sha256") or ""):
            raise ValueError("Mineralogy artifact SHA-256 is pending")
        if digest(Path(pin["path"])) != pin["sha256"].upper():
            raise ValueError("Mineralogy oil input differs from the exact parent hash")
        jar_identity(Path(pin["path"]), "mineralogy", MINERALOGY_VERSION)
        for template in definition[:2]:
            lane = deepcopy(template)
            for pos in [lane["min"], lane["max"], lane["source"], lane["drain"], *lane["pipes"], lane["output"]]:
                pos[0] += 256
            lane["witnesses"] = [lane["source"], lane["drain"], *lane["pipes"], lane["output"]]
            lane.update(id="OIL-mineralogy-crude_oil-" + lane["tank"].split(":")[1],
                        fluid="mineralogy:crude_oil", bucket="mineralogy:crude_oil_bucket")
            definition.append(lane)
    require_bounds(definition)
    return definition


def require_bounds(definition):
    ids = set()
    for lane in definition:
        key, low, high = lane["id"], lane["min"], lane["max"]
        if key in ids or not re.fullmatch(r"[A-Za-z0-9_-]+", key):
            raise AssertionError("Invalid or duplicate oil lane ID")
        ids.add(key)
        if (len(low) != 3 or len(high) != 3 or low[0] < 1024 or low[2] < 1024
                or low[1] < 8 or high[1] > 240
                or any(not 0 <= b - a <= 31 for a, b in zip(low, high))):
            raise AssertionError("Oil lane is outside original remote fixture bounds")
        route = [lane["source"], lane["drain"], *lane["pipes"], lane["output"]]
        if lane["witnesses"] != route or len({tuple(pos) for pos in route}) != len(route):
            raise AssertionError("Oil lane witness geometry changed")
        x, y, z = lane["source"]
        walls = [[x + dx, y, z + dz] for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1))]
        for pos in [*route, *walls]:
            if len(pos) != 3 or not all(a <= value <= b for a, value, b in zip(low, pos, high)):
                raise AssertionError("Oil witness or containment wall outside fixture bounds")
        if y >= high[1] or any(sum(abs(a - b) for a, b in zip(p, q)) != 1
                               for p, q in zip(route, route[1:])):
            raise AssertionError("Oil route disconnected or missing bucket headroom")
        for other in definition:
            if other is not lane and all(a <= d and c <= b for a, b, c, d in
                                         zip(low, high, other["min"], other["max"])):
                raise AssertionError("Oil lane bounds overlap")


def require_document(snapshot, definition):
    cases = snapshot["cases"]
    if set(cases) != {lane["id"] for lane in definition}:
        raise AssertionError("Oil snapshot lane IDs/count changed")
    for lane in definition:
        positions = [tuple(entry["pos"]) for entry in cases[lane["id"]]]
        if len(set(positions)) != len(positions) or set(positions) != {tuple(p) for p in lane["witnesses"]}:
            raise AssertionError("Oil snapshot has missing, duplicate or moved witnesses")


def require_native_boot(path):
    require_clean_log(path)
    text = path.read_text(encoding="utf-8", errors="replace")
    for marker in ("Starting minecraft server version 1.16.5",
                   "Forge mod loading, version 36.2.34, for MC 1.16.5 with MCP 20210115.111550"):
        if marker not in text:
            raise AssertionError("Missing genuine native116 boot proof: " + marker)
    failures = [line for line in text.splitlines() if any(token in line for token in (
                    "Lifecycle probe failed", "ADVLIFECYCLE FAIL", "Unknown or incomplete command",
                    "Incorrect argument for command", "An unexpected error occurred trying to execute that command"))]
    if failures:
        raise AssertionError("Native oil runtime failure: " + repr(failures[:5]))


def verify_staged(runtime, pins, seed_before):
    expected = sorted((Path(jar["path"]).name, jar["sha256"].upper()) for jar in pins["jars"])
    actual = sorted((jar.name, digest(jar)) for jar in (runtime / "mods").glob("*.jar"))
    recorded = sorted((jar["file"], jar["sha256"].upper()) for jar in read_json(runtime / "input-jars.json"))
    if len({name for name, _ in expected}) != len(expected) or actual != expected or recorded != expected:
        raise AssertionError("Staged native jars differ from captured manifest pins")
    if digest(runtime / LAUNCHER) != pins["launcher_sha256"].upper():
        raise AssertionError("Staged native launcher differs from captured manifest pin")
    # The installed seed's complete libraries and root launch jars, not its
    # worlds/config/mods, are the only seed content matrix.stage may copy.
    expected_libraries = {str(Path(name).relative_to("libraries")): value for name, value in seed_before.items()
                          if Path(name).parts[0] == "libraries"}
    if not expected_libraries or fingerprint(runtime / "libraries") != expected_libraries:
        raise AssertionError("Staged native libraries differ from seed fingerprint")
    expected_roots = {name: value for name, value in seed_before.items()
                      if len(Path(name).parts) == 1 and name.endswith(".jar") and "installer" not in name}
    if {jar.name: digest(jar) for jar in runtime.glob("*.jar")} != expected_roots:
        raise AssertionError("Staged root jars differ from seed fingerprint")


def prepare(runtime, profile, definition, port, pins, seed_before):
    if profile[1] != LAUNCHER:
        raise ValueError("Refusing a non-native116 oil runtime")
    launcher = stage(runtime, profile)
    properties = runtime / "server.properties"
    lines = [line for line in properties.read_text().splitlines()
             if not line.startswith(("server-port=", "generator-settings="))]
    properties.write_text("\n".join(lines) + f"\nserver-port={port}\ngenerator-settings=\n", encoding="utf-8")
    for marker in ("migration-operate.allow", "advantage-works.allow", "advantage-lifecycle.allow"):
        (runtime / marker).touch()
    write_json(runtime / "lifecycle-fixture.json", definition)
    verify_staged(runtime, pins, seed_before)
    return launcher


def construction_commands(definition):
    commands = []
    for lane in definition:
        commands.append("fill " + " ".join(map(str, lane["min"] + lane["max"])) + " minecraft:air replace")
        blocks = [(p, "poweradvantage:fluid_pipe") for p in lane["pipes"]]
        blocks += [(lane["drain"], "poweradvantage:fluid_drain"), (lane["output"], lane["tank"])]
        x, y, z = lane["source"]
        blocks += [([x + dx, y, z + dz], "minecraft:glass") for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1))]
        commands.extend("setblock " + " ".join(map(str, pos)) + " " + block for pos, block in blocks)
    return commands


class NativeOilRun(LifecycleRun):
    def __init__(self, runtime, java, launcher, definition, pins, seed_before):
        super().__init__(runtime, java, launcher, definition)
        self.pins, self.seed_before = pins, seed_before
        self.boots = []
        self.pending_construction = construction_commands(definition)
        self.construction_sealed = False
        self.bucket_permission = False
        self.bucket_placements = 0

    def boot(self, label):
        if self.server is not None or self.launcher != LAUNCHER or self.boots != list(BOOT_LABELS[:len(self.boots)]):
            raise AssertionError("Invalid native oil process lifecycle")
        if len(self.boots) >= 3 or label != BOOT_LABELS[len(self.boots)]:
            raise AssertionError("Only the three ordered native oil boots are permitted")
        if self.boots and not self.construction_sealed:
            raise AssertionError("Restart before initial construction was sealed")
        verify_staged(self.runtime, self.pins, self.seed_before)
        super().boot(label)
        self.boots.append(label)
        require_native_boot(self.runtime / f"console-{label}.log")

    def seal_construction(self):
        if self.pending_construction or self.construction_sealed or len(self.boots) != 1:
            raise AssertionError("Incomplete or repeated initial oil construction")
        self.construction_sealed = True

    def permit_buckets(self):
        if not self.construction_sealed or self.bucket_placements != len(self.boots) - 1 or len(self.boots) > 2:
            raise AssertionError("Only one bucket placement after each preservation gate is permitted")
        self.bucket_permission = True

    def command(self, command):
        if self.server is None:
            raise AssertionError("No native oil process is running")
        if self.pending_construction and command == self.pending_construction[0]:
            if len(self.boots) != 1 or self.construction_sealed:
                raise AssertionError("Oil construction after initial baseline is forbidden")
            self.pending_construction.pop(0)
        elif command == "save-all flush":
            pass
        else:
            match = re.fullmatch(r"advlifecycle (load|snapshot|place-buckets) [A-Za-z0-9_-]+", command)
            if not match:
                raise AssertionError("Forbidden native oil mutation: " + command)
            if match[1] == "place-buckets":
                if not self.bucket_permission or self.bucket_placements >= 2:
                    raise AssertionError("Bucket placement requires the empty/restored preservation gate")
                self.bucket_permission = False
                self.bucket_placements += 1
        super().command(command)


def exercise(runner, seconds, progress):
    definition = runner.definition
    phases = []

    def record(phase, snapshot, expected):
        require_document(snapshot, definition)
        phases.append(dict(phase=phase, cases=oil_transfer.inspect(snapshot, definition, expected)))
        progress(dict(phases=phases, full_process_restarts=len(runner.boots) - 1,
                      bucket_placements=runner.bucket_placements))

    runner.boot(BOOT_LABELS[0])
    runner.probe("load", "oil-loaded")
    for command in construction_commands(definition):
        runner.command(command)
    runner.seal_construction()
    empty = runner.probe("snapshot", "oil-empty")
    require_document(empty, definition)
    for lane in definition:
        for pos in (lane["drain"], lane["output"]):
            if parse(at(empty, lane["id"], pos)).get("Tank", {}).get("Amount", 0) != 0:
                raise AssertionError("Fixture was not empty: " + lane["id"])
    oil_transfer.require_same_machines(empty, empty, definition)
    runner.permit_buckets()
    runner.probe("place-buckets", "oil-first-buckets")
    time.sleep(seconds)
    first = runner.probe("snapshot", "oil-first-fill")
    record("first-fill", first, 1000)
    oil_transfer.require_same_machines(empty, first, definition)
    runner.command("save-all flush")
    runner.close()

    runner.boot(BOOT_LABELS[1])
    runner.probe("load", "oil-restart-loaded")
    time.sleep(seconds)
    restored = runner.probe("snapshot", "oil-restored")
    record("restored", restored, 1000)
    runner.permit_buckets()
    runner.probe("place-buckets", "oil-refill-buckets")
    time.sleep(seconds)
    refilled = runner.probe("snapshot", "oil-refilled")
    record("refilled", refilled, 2000)
    oil_transfer.require_same_machines(restored, refilled, definition)
    runner.command("save-all flush")
    runner.close()

    runner.boot(BOOT_LABELS[2])
    runner.probe("load", "oil-second-restart-loaded")
    time.sleep(seconds)
    record("retained", runner.probe("snapshot", "oil-retained"), 2000)
    runner.command("save-all flush")
    runner.close()
    if runner.boots != list(BOOT_LABELS) or runner.bucket_placements != 2:
        raise AssertionError("Incomplete native oil restart/refill protocol")


def run(args):
    if not 1 <= args.port <= 65535 or not 2 <= args.seconds <= 120 or not args.java8.is_file():
        raise ValueError("Supply Java 8, a TCP port, and 2..120 seconds per observation interval")
    output = output_path(args.output)
    if not output.name.startswith("native116-oil-"):
        raise ValueError("Use a new native116-oil-* output under test-rig/build")
    manifest = args.target_manifest.resolve()
    require_disjoint(output, manifest)
    manifest_bytes = manifest.read_bytes()
    manifest_hash = hashlib.sha256(manifest_bytes).hexdigest().upper()
    pins = json.loads(manifest_bytes)["target116"]
    profile = pinned_profile(manifest, "target116")
    if digest(manifest) != manifest_hash:
        raise AssertionError("Target manifest changed during preflight")
    for protected in (profile[0], *profile[2]):
        require_disjoint(output, protected)
    definition = fixtures(pins)
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", args.port))
    seed_before = fingerprint(profile[0])
    helpers = [Path(oil_transfer.__file__), Path(__file__), Path(__file__).with_name("lifecycle.py")]
    helpers_before = {path.name: digest(path) for path in helpers}
    output.mkdir(parents=True, exist_ok=False)
    (output / "target-manifest.json").write_bytes(manifest_bytes)
    report_path = output / "native116-oil-results.json"
    runtime = output / "target116"
    report = dict(status="RUNNING", started_at_utc=datetime.now(timezone.utc).isoformat(),
                  scope="native116-buckets-descending-pipes-two-full-restarts",
                  protocol="Initial empty construction only; native bucket sources twice; no tile.read() restores/API offers/manual tank refills/resets",
                  targetManifest=str(manifest), targetManifestSha256=manifest_hash, targetProfile=pins,
                  seed=str(profile[0]), seedFingerprintBefore=seed_before, helperSha256=helpers_before,
                  port=args.port, seconds=args.seconds, laneCount=len(definition), phases=[], full_process_restarts=0,
                  mineralogy="INCLUDED" if len(definition) == 6 else "ABSENT_FROM_MANIFEST")
    write_json(output / "oil-fixture.json", definition)
    write_json(report_path, report)
    runner = None
    try:
        launcher = prepare(runtime, profile, definition, args.port, pins, seed_before)
        report["stagedInputs"] = read_json(runtime / "input-jars.json")
        runner = NativeOilRun(runtime, args.java8, launcher, definition, pins, seed_before)

        def progress(value):
            report.update(value)
            write_json(report_path, report)
            print("Oil " + value["phases"][-1]["phase"] + ": PASS, " + str(len(definition)) + " lanes", flush=True)

        exercise(runner, args.seconds, progress)
        report["status"] = "PASS"
    except Exception as error:
        report.update(status="FAIL", failure=str(error), traceback=traceback.format_exc())
    except BaseException as error:
        report.update(status="FAIL", failure=repr(error), traceback=traceback.format_exc())
        raise
    finally:
        if runner is not None:
            try:
                runner.close()
            except Exception as error:
                report.update(status="FAIL", shutdown_failure=str(error))
        try:
            verify_staged(runtime, pins, seed_before)
            logs = sorted(runtime.glob("console-*.log"))
            for log in logs:
                require_native_boot(log)
            report["nativeBoots"] = [log.name for log in logs]
            report["forge_config_reload_notices"] = {
                log.name: log.read_text(encoding="utf-8", errors="replace").count(CONFIG_NOTICE) for log in logs}
            if report["status"] == "PASS" and len(logs) != 3:
                raise AssertionError("Three complete native116 boot logs are required")
        except Exception as error:
            report.update(status="FAIL", runtime_audit_failure=str(error))
        try:
            after = fingerprint(profile[0])
            report["seedFingerprintAfter"] = after
            report["seed_unchanged"] = after == seed_before
            report["helpers_unchanged"] = {path.name: digest(path) for path in helpers} == helpers_before
            report["manifest_unchanged"] = digest(manifest) == manifest_hash
            if not all(report[key] for key in ("seed_unchanged", "helpers_unchanged", "manifest_unchanged")):
                raise AssertionError("Protected seed, helper or manifest changed")
        except Exception as error:
            report.update(status="FAIL", preservation_failure=str(error))
        report["completed_at_utc"] = datetime.now(timezone.utc).isoformat()
        write_json(report_path, report)
    print(report["status"] + ": " + str(report_path), flush=True)
    return int(report["status"] != "PASS")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target-manifest", type=Path, required=True)
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--port", type=int, default=25576)
    parser.add_argument("--seconds", type=int, default=8)
    return run(parser.parse_args())


if __name__ == "__main__":
    raise SystemExit(main())
