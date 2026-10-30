"""Collect exact Base Metals 2.4 bucket evidence before classifying its late-enable error.

Only this process temporarily replaces the machine runner's boot auditor. The
ordinary auditor and frozen runtime/protocol files are never edited. The root
receipt, not a child operating verdict, is the diagnostic acceptance authority.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import importlib
import json
import os
from pathlib import Path
import re
import traceback
from unittest.mock import patch
import uuid

from native110_diagnostics import PUBLIC_FORGE_SHA, audit_lines

BASE_SHA = "A225D3E24CD37C377A9D843861FC13BDDB0B88F799D3D3AEDBC8B1DC636BB5E5"
LAUNCHER = "forge-1.10.2-12.18.3.2511-universal.jar"
PENDING = "PENDING_DIAGNOSTIC_PROOF"
IDS = {"power/bucket/" + name for name in ("startup", "crude_oil", "refined_oil")}
MOD_IDS = {"basemetals", "orespawn", "poweradvantage", "advantageworkstest"}
LATE_BUCKET = re.compile(r"^\[\d\d:\d\d:\d\d\] \[Server thread/ERROR\] \[FML\]: "
    r"Trying to activate the universal filled bucket too late\. Call it statically in your Mods class\. Mod: Base Metals$")


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest().upper()


def read(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def write(path, value):
    Path(path).write_text(json.dumps(value, indent=2, sort_keys=True), encoding="utf-8")


def held_diagnostics(lines, base_sha, forge_sha):
    if base_sha != BASE_SHA or forge_sha != PUBLIC_FORGE_SHA:
        raise AssertionError("Late bucket proof requires the exact Base 2.4 and public Forge 2511 bytes")
    matches = [i for i, line in enumerate(lines) if LATE_BUCKET.fullmatch(line)]
    if len(matches) != 1:
        raise AssertionError("Expected exactly one complete legacy bucket diagnostic per boot")
    withheld = list(lines)
    withheld[matches[0]] = ""  # Preserve original line numbers; never alter the raw log.
    ordinary = audit_lines(withheld, forge_sha)
    for marker in ("Starting minecraft server version 1.10.2",
                   "Forge Mod Loader version 12.18.3.2511 for Minecraft 1.10.2 loading"):
        if not any(marker in line for line in lines):
            raise AssertionError("Missing native boot proof: " + marker)
    return dict(status=PENDING, originalSeverity="ERROR", line=matches[0] + 1,
                message=lines[matches[0]], otherDiagnostics=ordinary)


def pins(manifest):
    manifest = Path(manifest).resolve()
    target = read(manifest)["target110"]
    jars = target["jars"]
    if len({row["id"] for row in jars}) != len(jars):
        raise AssertionError("Duplicate supplied mod identity")
    bound = []
    for row in jars:
        path = (manifest.parent / row["path"]).resolve()
        if sha(path) != row["sha256"].upper():
            raise AssertionError("Changed supplied mod: " + str(path))
        # This exact Base jar's mcmod.info says 2.4.0.11, but its @Mod VERSION is 2.4.0.
        runtime_version = "2.4.0" if row["id"] == "basemetals" and sha(path) == BASE_SHA else row["version"]
        bound.append(dict(id=row["id"], version=row["version"], runtimeVersion=runtime_version,
                          path=str(path), sha256=sha(path)))
    base = [row for row in bound if row["id"] == "basemetals"]
    if len(base) != 1 or base[0]["sha256"] != BASE_SHA:
        raise AssertionError("Not the exact original Base Metals 2.4 input")
    inventory = target["serverCode"]
    if inventory["files"][LAUNCHER]["sha256"].upper() != PUBLIC_FORGE_SHA:
        raise AssertionError("Wrong sealed Forge launcher")
    return dict(manifest=str(manifest), manifestSha256=sha(manifest), mods=bound, codeInventory=inventory)


def assert_paired_pins(bindings):
    baseline, candidate = bindings["baseline"], bindings["candidate"]
    left, right = ({row["id"]: row for row in bound["mods"]} for bound in (baseline, candidate))
    if set(left) != MOD_IDS or set(right) != MOD_IDS:
        raise AssertionError("Bounded bucket proof requires only Power, Base, OreSpawn and the harness")
    if baseline["codeInventory"] != candidate["codeInventory"]:
        raise AssertionError("Baseline and candidate must use the identical complete Forge/code inventory")
    for mod in MOD_IDS - {"poweradvantage"}:
        for field in ("sha256", "version", "runtimeVersion"):
            if left[mod][field] != right[mod][field]:
                raise AssertionError("Changed non-Power dependency: " + mod + "/" + field)
    if left["poweradvantage"]["sha256"] == right["poweradvantage"]["sha256"]:
        raise AssertionError("Baseline and repaired Power artifacts must be distinct")


def startup_flags(document, token, expected_versions=None):
    if document.get("token") != token or any(document.get(key) is not True
            for key in ("enabled", "created", "registered")):
        raise AssertionError("Missing actual per-process enabled/created/registered bucket flags")
    if document.get("item") != "forge:bucketFilled" or document.get("world") != "AdvantageWorksMigration":
        raise AssertionError("Wrong registered bucket or world")
    if not isinstance(document.get("mods"), dict) or "basemetals" not in document["mods"]:
        raise AssertionError("Missing actual loaded-mod inventory")
    if expected_versions is not None:
        for mod, version in expected_versions.items():
            if document["mods"].get(mod) != version:
                raise AssertionError("Loaded mod version differs from its declared runtime version: " + mod)
        unexpected = set(document["mods"]) - set(expected_versions) - {"minecraft", "mcp", "FML", "Forge"}
        if unexpected:
            raise AssertionError("Unexpected loaded mods in bounded bucket proof: " + str(sorted(unexpected)))


def bucket_records(operation):
    rows = operation.get("cases", [])
    if len(rows) != 3 or {row["id"] for row in rows} != IDS:
        raise AssertionError("Only the three complete bucket cases may establish this proof")
    for row in rows:
        if row.get("status") != "PASS" or any(row.get("bucket/" + key) is not True
                for key in ("enabled", "created", "registered")):
            raise AssertionError("Missing measured bucket state: " + row["id"])
        if row["id"] != "power/bucket/startup":
            name = row["id"].rsplit("/", 1)[1]
            if (row.get("bucket/placedFluid") != name or row.get("bucket/placedAmount") != 1000
                    or row.get("bucket/donorRemaining") != 0 or row.get("bucket/sourceGone") is not True):
                raise AssertionError("Missing native oil identity/quantity/conservation proof: " + name)


def typed_buckets(native, operation, inspected):
    for collection, key in ((operation["cases"], "after"), (inspected["cases"], "positions")):
        for case in collection:
            if case["id"] == "power/bucket/startup":
                continue
            name = case["id"].rsplit("/", 1)[1]
            stacks = []
            for row in case[key]:
                if "nbtBase64" in row:
                    stacks.extend(native.recorded_nbt(row).get("Items", []))
            if len(stacks) != 1:
                raise AssertionError("Expected exactly one recovered bucket and no duplicate inventory")
            stack = stacks[0]
            tag = stack.get("tag", {})
            for container, field, kind, expected in ((stack, "id", 8, "forge:bucketFilled"),
                    (stack, "Count", 1, 1), (tag, "FluidName", 8, name), (tag, "Amount", 3, 1000)):
                value = container.get(field)
                if getattr(value, "tag_id", None) != kind or value != expected:
                    raise AssertionError("Wrong typed recovered bucket field: " + field)


def validate_role(native, root, token, bound):
    root = Path(root)
    catalog = read(root / "advantage-works-results/machine-catalog-measured.json")
    operation = read(root / "advantage-works-results/machine-operation-measured.json")
    fixture = read(root / "machine-operation-fixture.json")
    inspected = read(root / "advantage-works-results/machine-inspection-restarted.json")
    if native.validate_operation(catalog, operation, "power/bucket")["status"] != "PASS":
        raise AssertionError("Native three-case operation did not pass")
    cold = native.validate_restart(fixture, inspected)
    if cold["status"] != "PASS" or cold["cases"] != 3:
        raise AssertionError("Exact three-case typed cold comparison did not pass")
    bucket_records(operation)
    flags = {}
    for phase in ("operate", "restart"):
        document = read(root / ("bucket-startup-" + phase + ".json"))
        startup_flags(document, token, {mod["id"]: mod["runtimeVersion"] for mod in bound["mods"]})
        flags[phase] = document
        held_diagnostics((root / ("console-" + phase + ".log")).read_text(
            encoding="utf-8", errors="replace").splitlines(), BASE_SHA, PUBLIC_FORGE_SHA)
    if flags["operate"]["mods"] != flags["restart"]["mods"]:
        raise AssertionError("Loaded mod identities changed between processes")
    for mod in bound["mods"]:
        if mod["id"] not in flags["operate"]["mods"]:
            raise AssertionError("Supplied mod was not actually loaded: " + mod["id"])
    typed_buckets(native, operation, inspected)
    return dict(status="PASS", operationCases=3, exactColdCases=3, startupFlags=flags)


def run(args):
    native = importlib.import_module("native110_machine_operations")
    output = native.output_path(args.output)
    bindings = {role: pins(getattr(args, role + "_manifest")) for role in ("baseline", "candidate")}
    assert_paired_pins(bindings)
    output.mkdir(parents=True, exist_ok=False)
    token = uuid.uuid4().hex
    receipt = output / "legacy-bucket-proof.json"
    report = dict(status=PENDING, token=token, startedAtUtc=datetime.now(timezone.utc).isoformat(),
                  inputs=bindings, roles={}, heldDiagnostics=[], classification=None,
                  scope="Exact Base 2.4/Forge 2511; original and repaired Power; three native bucket cases and exact cold restart")
    protocol = [Path(__file__), Path(native.__file__), Path(native.__file__).with_name("native110_lifecycle.py"),
                Path(native.__file__).with_name("native110_diagnostics.py"), Path(native.__file__).with_name("native110_code.py"),
                Path(native.__file__).with_name("native110.py"), Path(native.__file__).with_name("rig_transport.py")]
    report["protocolSha256"] = {str(path): sha(path) for path in protocol}
    write(receipt, report)
    try:
        for index, role in enumerate(("baseline", "candidate")):
            root = output / role

            def pending_boot(path):
                base = next(row for row in bindings[role]["mods"] if row["id"] == "basemetals")
                diagnostic = held_diagnostics(Path(path).read_text(encoding="utf-8", errors="replace").splitlines(),
                    sha(Path(path).parent / "mods" / Path(base["path"]).name), sha(Path(path).parent / LAUNCHER))
                diagnostic.update(role=role, path=str(path), logSha256=sha(path))
                report["heldDiagnostics"].append(diagnostic)
                write(receipt, report)

            with patch.dict(os.environ, {"ADVWORKS_BUCKET_PROOF": token}), \
                    patch.object(native, "require_native_boot", pending_boot):
                result = native.run(argparse.Namespace(manifest=Path(bindings[role]["manifest"]), output=root,
                    java8=args.java8, port=args.port + index, group="power/bucket"))
            if result != 0:
                raise AssertionError("Native bucket campaign failed: " + role)
            report["roles"][role] = validate_role(native, root, token, bindings[role])
            if pins(Path(bindings[role]["manifest"])) != bindings[role]:
                raise AssertionError("Immutable source inputs changed")
            write(receipt, report)
        if len(report["heldDiagnostics"]) != 4:
            raise AssertionError("Expected two genuinely audited boots for each Power version")
        if {str(path): sha(path) for path in protocol} != report["protocolSha256"]:
            raise AssertionError("Collector or primary protocol changed during proof collection")
        report.update(status="PASS", classification=dict(severity="NOTICE", originalSeverity="ERROR",
            reason="Exact redundant Base 2.4 enable call; both Power artifacts have enabled/registered buckets and conserved native oil round-trips across exact cold restarts",
            baseSha256=BASE_SHA, forgeSha256=PUBLIC_FORGE_SHA))
    except BaseException as error:
        report.update(status="FAIL", failure=str(error), traceback=traceback.format_exc(), classification=None)
    finally:
        report["filesSha256"] = {path.relative_to(output).as_posix(): sha(path)
                                 for path in output.rglob("*") if path.is_file() and path != receipt}
        report["completedAtUtc"] = datetime.now(timezone.utc).isoformat()
        write(receipt, report)
    return int(report["status"] != "PASS")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline-manifest", type=Path, required=True)
    parser.add_argument("--candidate-manifest", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--port", type=int, required=True)
    return run(parser.parse_args())


if __name__ == "__main__":
    raise SystemExit(main())
