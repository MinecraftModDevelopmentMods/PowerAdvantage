"""Explicit, proof-bound legacy bucket notice permit for the three connected routes.

No ordinary auditor is changed. Only this process patches the connected module's
boot callback and server class. Its primary six work/preservation phases remain
mandatory, and a new Power/harness/OreSpawn/code hash requires new bucket proof.
"""
import argparse
from datetime import datetime, timezone
import importlib
import json
import os
from pathlib import Path
import re
import traceback
from unittest.mock import patch
import uuid

import legacy_bucket_proof as proof

PROBE_SHA = "B9F756681593B508A8E3222BD592A3DFCB474303532B154045893B3C585EF1D3"
EXTRAS = {"steamadvantage", "electricadvantage"}
SOURCES = {"original110", "early110", "maintained110"}
MODS_LOADED = re.compile(r"^\[\d\d:\d\d:\d\d\] \[Server thread/INFO\] \[FML\]: "
                        r"Forge Mod Loader has successfully loaded \d+ mods\s*$")


def check_proof(document):
    classification = document.get("classification") or {}
    if (document.get("status") != "PASS" or classification.get("severity") != "NOTICE"
            or classification.get("originalSeverity") != "ERROR"
            or classification.get("baseSha256") != proof.BASE_SHA
            or classification.get("forgeSha256") != proof.PUBLIC_FORGE_SHA):
        raise AssertionError("A complete exact-hash bucket proof PASS is required")
    proof.assert_paired_pins(document["inputs"])
    if set(document.get("roles", {})) != {"baseline", "candidate"}:
        raise AssertionError("Both original and repaired Power proofs are required")
    for role, result in document["roles"].items():
        if result.get("status") != "PASS" or result.get("operationCases") != 3 or result.get("exactColdCases") != 3:
            raise AssertionError("Incomplete three-case operation/cold proof")
        flags = result.get("startupFlags", {})
        if set(flags) != {"operate", "restart"}:
            raise AssertionError("Missing a fresh bucket-proof process")
        versions = {row["id"]: row["runtimeVersion"] for row in document["inputs"][role]["mods"]}
        for value in flags.values():
            proof.startup_flags(value, document["token"], versions)
    diagnostics = document.get("heldDiagnostics", [])
    expected = {(role, "console-" + phase + ".log") for role in ("baseline", "candidate")
                for phase in ("operate", "restart")}
    actual = {(row.get("role"), Path(row.get("path", "")).name) for row in diagnostics}
    if len(diagnostics) != 4 or actual != expected or any(row.get("originalSeverity") != "ERROR"
            or not proof.LATE_BUCKET.fullmatch(row.get("message", "")) for row in diagnostics):
        raise AssertionError("Exactly four genuine legacy bucket diagnostics must be retained")


def verify_proof(path, expected_sha):
    path = Path(path).resolve()
    if proof.sha(path) != expected_sha.upper():
        raise AssertionError("Explicit bucket-proof SHA-256 does not match")
    document = proof.read(path)
    check_proof(document)
    root = path.parent
    actual = {}
    for file in root.rglob("*"):
        if file.is_file() and file != path:
            if not file.resolve().is_relative_to(root):
                raise AssertionError("Proof file escapes its sealed directory")
            actual[file.relative_to(root).as_posix()] = proof.sha(file)
    if not actual or actual != document.get("filesSha256"):
        raise AssertionError("Bucket proof raw files changed, disappeared or were added")
    for role in ("baseline", "candidate"):
        for name in ("console-operate.log", "console-restart.log", "bucket-startup-operate.json",
                "bucket-startup-restart.json", "machine-operation-fixture.json",
                "advantage-works-results/machine-operation-measured.json",
                "advantage-works-results/machine-inspection-restarted.json"):
            if role + "/" + name not in actual:
                raise AssertionError("Missing sealed native bucket evidence: " + role + "/" + name)
    return document


def bind_target(document, target):
    core = {row["id"]: row for row in document["inputs"]["candidate"]["mods"]}
    mods = {row["id"]: row for row in target["mods"]}
    if len(mods) != len(target["mods"]) or set(mods) != proof.MOD_IDS | EXTRAS:
        raise AssertionError("Connected extension must declare only the core plus Steam and Electric")
    if target["codeInventory"] != document["inputs"]["candidate"]["codeInventory"]:
        raise AssertionError("Destination Forge/code inventory differs from bucket proof")
    for mod in proof.MOD_IDS:
        for field in ("sha256", "version", "runtimeVersion"):
            if mods[mod][field] != core[mod][field]:
                raise AssertionError("Bucket proof does not cover destination " + mod + "/" + field)
    return core


def verify_destination(root, target):
    root = Path(root)
    expected = {Path(row["path"]).name: row["sha256"] for row in target["mods"]}
    if len(expected) != len(target["mods"]):
        raise AssertionError("Declared mod artifact filenames must be unique")
    actual = {file.name: proof.sha(file) for file in (root / "mods").glob("*.jar")}
    extra = {name: checksum for name, checksum in actual.items() if name not in expected}
    if len(extra) != 1 or next(iter(extra.values())) != PROBE_SHA:
        raise AssertionError("Only the exact archived server-only LegacyProbe may be injected")
    if actual != {**expected, **extra}:
        raise AssertionError("Staged destination mods differ from the declared current profile")
    if proof.sha(root / proof.LAUNCHER) != proof.PUBLIC_FORGE_SHA:
        raise AssertionError("Destination is not the proven Forge 2511")
    return dict(actualMods=actual, legacyProbe=extra)


def declared_sources(sources=None):
    if sources is None:
        return set(SOURCES)
    selected = set(sources)
    if not selected or not selected <= SOURCES or len(selected) != len(sources):
        raise AssertionError("Missing, duplicate or unknown declared connected source")
    return selected


def require_primary(document, processes, sources=None):
    sources = declared_sources(sources)
    routes = document.get("routes", [])
    if (document.get("status") != "PASS" or document.get("protocolUnchanged") is not True
            or len(routes) != len(sources) or {row.get("sourceKey") for row in routes} != sources):
        raise AssertionError("All declared connected routes must pass; subsets are forbidden")
    required = {phase + "-" + kind for phase in ("converted", "restarted")
                for kind in ("preservation", "operation", "completed-work")}
    for route in routes:
        if route.get("status") != "PASS" or route.get("sourceUnchanged") is not True:
            raise AssertionError("Connected route failed or changed its protected source")
        if set(route.get("phases", {})) != required or any(row.get("status") != "PASS"
                for row in route["phases"].values()):
            raise AssertionError("Missing preservation or actual completed work before/after restart")
    expected = {(source, phase) for source in sources for phase in ("converted", "restarted")}
    actual = {(row.get("sourceKey"), row.get("phase")) for row in processes.values()}
    if len(processes) != 2 * len(sources) or actual != expected or any(row.get("status") != "PASS" for row in processes.values()):
        raise AssertionError("Two fresh independently validated processes per declared source are required")


def run(args):
    connected = importlib.import_module("native110_connected")
    sources = declared_sources(getattr(args, "source_keys", None))
    if len(sources) not in (1, 3):
        raise AssertionError("Only a single explicit route or the unchanged three-route campaign is supported")
    proof_path = args.bucket_proof.resolve()
    qualified = verify_proof(proof_path, args.bucket_proof_sha256)
    target = proof.pins(args.target_manifest)
    bind_target(qualified, target)
    output = connected.output_path(args.output)
    if output.is_relative_to(proof_path.parent) or proof_path.parent.is_relative_to(output):
        raise AssertionError("Connected output must not overlap the protected bucket proof")
    previous_sha = proof.sha(args.previous)
    expected_versions = {row["id"]: row["runtimeVersion"] for row in target["mods"]}
    expected_versions["advantagemigrationprobe"] = "1.0.0"
    report = dict(status=proof.PENDING, bucketProof=str(proof_path), bucketProofSha256=args.bucket_proof_sha256.upper(),
        target=target, previous=str(args.previous.resolve()), previousSha256=previous_sha,
        startedAtUtc=datetime.now(timezone.utc).isoformat(), processes={}, classification=None,
        scope="Explicit connected workload extension; core bytes proven separately, Steam/Electric profile-pinned and LegacyProbe hash-pinned")
    report["declaredSources"] = sorted(sources)
    report["declaredExtraScenario"] = [row for row in target["mods"] if row["id"] in EXTRAS]
    report["declaredExtraScenario"].append(dict(id="advantagemigrationprobe", version="1.0.0", sha256=PROBE_SHA))
    receipt = output.parent / (output.name + "-bucket-permit.json")
    if receipt.exists():
        raise FileExistsError(receipt)
    helpers = [Path(__file__), Path(proof.__file__), Path(connected.__file__),
               Path(connected.oracle.__file__), Path(connected.semantic_machine_audit.__file__),
               Path(connected.__file__).with_name("native110_lifecycle.py"),
               Path(connected.__file__).with_name("native110_diagnostics.py"),
               Path(connected.__file__).with_name("native110_code.py"),
               Path(connected.__file__).with_name("native110.py"), Path(connected.__file__).with_name("rig_transport.py")]
    report["protocolSha256"] = {str(file): proof.sha(file) for file in helpers}
    proof.write(receipt, report)
    original_server = connected.NativeServer

    class ProvenServer(original_server):
        def read(self):
            for line in self.process.stdout:
                if MODS_LOADED.fullmatch(line):
                    # The pre-init registration window has closed. Never enable
                    # MachineOperationalProbe while collecting connected evidence.
                    try:
                        self.bucket_marker.touch(exist_ok=False)
                        self.bucket_armed = True
                    except OSError as error:
                        self.bucket_arm_error = str(error)
                self.log.write(line)
                self.log.flush()
                self.lines.put(line)

        def __init__(self, runtime, java, launcher, label):
            root = Path(runtime).resolve()
            if not root.is_relative_to(output) or root.name not in sources or label not in ("converted", "restarted"):
                raise AssertionError("Unexpected destination process")
            custody = verify_destination(root, target)
            connected.verify_code(root, target["codeInventory"])
            if launcher != proof.LAUNCHER:
                raise AssertionError("Wrong connected launcher")
            phase = "operate" if label == "converted" else "restart"
            flags_path = root / ("bucket-startup-" + phase + ".json")
            if flags_path.exists() or (label == "converted" and (root / "bucket-startup-restart.json").exists()):
                raise AssertionError("Startup flags must be produced freshly by this destination process")
            self.bucket_marker = root / "advantage-machine-operation.allow"
            if self.bucket_marker.exists():
                raise AssertionError("Machine operating mode must not be enabled during connected pre-init")
            self.bucket_armed = False
            self.bucket_arm_error = None
            token = uuid.uuid4().hex
            key = str(root / ("console-" + label + ".log"))
            report["processes"][key] = dict(status=proof.PENDING, sourceKey=root.name, phase=label,
                token=token, startupPath=str(flags_path), custody=custody)
            proof.write(receipt, report)
            try:
                with patch.dict(os.environ, {"ADVWORKS_BUCKET_PROOF": token}):
                    super().__init__(runtime, java, launcher, label)
                if not self.bucket_armed or self.bucket_arm_error is not None:
                    raise AssertionError("The post-mod-loading startup witness was never armed")
                flags = proof.read(flags_path)
                proof.startup_flags(flags, token, expected_versions)
                report["processes"][key].update(startupFlags=flags, startupSha256=proof.sha(flags_path))
                proof.write(receipt, report)
            except BaseException:
                if getattr(self, "process", None) is not None and self.process.poll() is None:
                    try: self.close()
                    except BaseException as error:
                        report["processes"][key]["failedStartupShutdown"] = str(error)
                raise
            finally:
                if self.bucket_armed:
                    self.bucket_marker.unlink(missing_ok=True)

    def proven_boot(path):
        path = Path(path).resolve()
        key = str(path)
        process = report["processes"].get(key)
        if process is None:
            raise AssertionError("Boot audit has no registered destination process")
        verify_destination(path.parent, target)
        connected.verify_code(path.parent, target["codeInventory"])
        flags_path = Path(process["startupPath"])
        flags = proof.read(flags_path)
        proof.startup_flags(flags, process["token"], expected_versions)
        if "startupSha256" in process and proof.sha(flags_path) != process["startupSha256"]:
            raise AssertionError("Fresh startup flags changed after their first observation")
        # Per-boot custody is checked before any classification, not merely after work.
        if proof.sha(proof_path) != args.bucket_proof_sha256.upper():
            raise AssertionError("Protected proof receipt changed during the connected run")
        diagnostic = proof.held_diagnostics(path.read_text(encoding="utf-8", errors="replace").splitlines(),
                                             proof.BASE_SHA, proof.sha(path.parent / proof.LAUNCHER))
        process.update(status="PASS", startupFlags=flags, startupSha256=proof.sha(flags_path),
            logSha256=proof.sha(path), diagnostic=dict(diagnostic, classification="PROOF_BOUND_REDUNDANT_BASE24_BUCKET_NOTICE",
            severity="NOTICE", originalSeverity="ERROR", bucketProofSha256=args.bucket_proof_sha256.upper()))
        proof.write(receipt, report)

    try:
        with patch.object(connected, "SOURCE_KEYS", tuple(sorted(sources))), \
                patch.object(connected, "NativeServer", ProvenServer), patch.object(connected, "require_native_boot", proven_boot):
            result = connected.run(argparse.Namespace(target_manifest=args.target_manifest, previous=args.previous,
                output=output, java8=args.java8, port=args.port, match_source_metals=False))
        primary_path = output / "native110-connected-results.json"
        primary = proof.read(primary_path)
        if result != 0:
            raise AssertionError("The original three-route connected campaign did not pass")
        require_primary(primary, report["processes"], sources)
        report["primaryReceiptSha256"] = proof.sha(primary_path)
        if proof.pins(args.target_manifest) != target or proof.sha(args.previous) != previous_sha:
            raise AssertionError("Current profile or source provenance changed")
        report.update(status="PASS_ROUTE" if len(sources) == 1 else "PASS",
            classification="PROOF_BOUND_CONNECTED_ROUTE_ONLY" if len(sources) == 1 else "PROOF_BOUND_CONNECTED_EXTENSION_ONLY")
    except BaseException as error:
        report.update(status="FAIL", failure=str(error), traceback=traceback.format_exc(), classification=None)
    finally:
        try:
            verify_proof(proof_path, args.bucket_proof_sha256)
            if {str(file): proof.sha(file) for file in helpers} != report["protocolSha256"]:
                raise AssertionError("Wrapper or original connected protocol changed")
            report["proofUnchanged"] = True
        except BaseException as error:
            report.update(status="FAIL", proofUnchanged=False, custodyFailure=str(error), classification=None)
        report["completedAtUtc"] = datetime.now(timezone.utc).isoformat()
        report["filesSha256"] = {file.relative_to(output).as_posix(): proof.sha(file)
                                 for file in output.rglob("*") if file.is_file()} if output.exists() else {}
        proof.write(receipt, report)
    return int(report["status"] not in ("PASS", "PASS_ROUTE"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bucket-proof", type=Path, required=True)
    parser.add_argument("--bucket-proof-sha256", required=True)
    parser.add_argument("--target-manifest", type=Path, required=True)
    parser.add_argument("--previous", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--source-key", choices=sorted(SOURCES), action="append", dest="source_keys")
    return run(parser.parse_args())


if __name__ == "__main__": raise SystemExit(main())
