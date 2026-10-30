"""Local, worldless CoreMods observation plans; never edits shared seeds or runners.

Derivation is from a pinned original inventory and a pinned validated bridge build,
not from arbitrary current runtime bytes. Parent integration calls seal_runtime
after its existing observer copy and before its unchanged CodeCustody/Server gate.
"""

import argparse
import copy
import json
import os
from pathlib import Path
import re
import shutil
import zipfile

from matrix import digest, write_json
from native116_code import INVENTORY, code_files, verify_code
from transformer_order import validate_patch


COREMODS = "libraries/net/minecraftforge/coremods/4.0.6/coremods-4.0.6.jar"
CUSTODY = "observation-custody"
BRIDGE_SOURCE = Path(__file__).parent / "transformer-order-probe/TransformerOrderBridge.java"


def read(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def pinned(path, sha256):
    path = Path(path).resolve()
    if not re.fullmatch(r"[0-9A-Fa-f]{64}", sha256) or digest(path) != sha256.upper():
        raise AssertionError("Changed pinned observation input: " + str(path))
    return path


def disjoint(output, protected):
    output, protected = Path(output).resolve(), Path(protected).resolve()
    if output.is_relative_to(protected) or protected.is_relative_to(output):
        raise ValueError("Observation output overlaps a protected input: " + str(protected))


def validate_inventory(value):
    if set(value) != {"files"} or not isinstance(value["files"], dict) or not value["files"]:
        raise ValueError("Expected exact nonempty code inventory schema")
    folded = set()
    for name, metadata in value["files"].items():
        path = Path(name)
        if (path.is_absolute() or "\\" in name or ":" in name or any(part in ("", ".", "..") for part in name.split("/"))
                or name.lower() in folded
                or not (name.startswith("libraries/") or (len(path.parts) == 1 and name.endswith(".jar")))):
            raise ValueError("Unsafe, duplicate or non-code inventory path: " + name)
        folded.add(name.lower())
        if (set(metadata) != {"sha256", "size"} or not re.fullmatch(r"[0-9A-F]{64}", metadata["sha256"])
                or type(metadata["size"]) is not int or metadata["size"] <= 0):
            raise ValueError("Invalid code inventory metadata: " + name)
    if COREMODS not in value["files"]:
        raise ValueError("Pinned original CoreMods entry missing")


def make_plan(seed, inventory_sha256, observer, observer_sha256, build_proof, build_proof_sha256,
              bridge_source=BRIDGE_SOURCE):
    seed = Path(seed).resolve()
    receipt = pinned(seed / INVENTORY, inventory_sha256)
    baseline = read(receipt)
    validate_inventory(baseline)
    verify_code(seed, baseline)
    observer = pinned(observer, observer_sha256)
    proof_path = pinned(build_proof, build_proof_sha256)
    proof = read(proof_path)
    bridge_source = pinned(bridge_source, proof["sourceSha256"])
    for jar in (seed / COREMODS, observer):
        with zipfile.ZipFile(jar) as archive:
            names = archive.namelist()
            if len(names) != len(set(names)):
                raise ValueError("Duplicate ZIP entry in observation input")
    patch = validate_patch(seed / COREMODS, observer)
    if any(proof.get(key) != value for key, value in patch.items()):
        raise AssertionError("Pinned bridge receipt differs from actual exact-entry validation")
    if not isinstance(proof.get("dependencies"), dict) or not proof["dependencies"]:
        raise ValueError("Bridge build dependency provenance missing")
    dependencies = {}
    for name, checksum in proof["dependencies"].items():
        path = pinned(name, checksum)
        relative = path.relative_to(seed).as_posix()
        if relative not in baseline["files"] or baseline["files"][relative]["sha256"] != checksum:
            raise AssertionError("Bridge dependency is not in the original sealed code inventory")
        dependencies[relative] = checksum
    derived = copy.deepcopy(baseline)
    derived["files"][COREMODS] = dict(sha256=digest(observer), size=observer.stat().st_size)
    changed = [name for name in baseline["files"] if baseline["files"][name] != derived["files"][name]]
    if changed != [COREMODS] or set(derived["files"]) != set(baseline["files"]):
        raise AssertionError("Observation inventory must replace exactly the original CoreMods entry")
    verify_code(seed, baseline)
    pinned(receipt, inventory_sha256)
    return dict(format=1, kind="DERIVED_COREMODS_OBSERVATION_ONLY", seed=str(seed),
        baselineReceiptSha256=inventory_sha256.upper(), baselineInventory=baseline,
        observerJar=str(observer), observerJarSha256=observer_sha256.upper(),
        buildProof=str(proof_path), buildProofSha256=build_proof_sha256.upper(), patch=patch,
        bridgeSource=str(bridge_source), bridgeSourceSha256=digest(bridge_source), dependencies=dependencies,
        derivedInventory=derived, codeFileCount=len(baseline["files"]), changedPaths=changed,
        helperSha256=digest(Path(__file__)), scope="No worlds, candidate mods, server process or gameplay qualification")


def load_plan(path, sha256):
    path = pinned(path, sha256)
    plan = read(path)
    actual = make_plan(plan["seed"], plan["baselineReceiptSha256"], plan["observerJar"],
        plan["observerJarSha256"], plan["buildProof"], plan["buildProofSha256"], plan["bridgeSource"])
    if plan != actual:
        raise AssertionError("Observation plan differs from its pinned derivation")
    return path, plan


def seal_runtime(runtime, plan_path, plan_sha256):
    """Seal only a stopped/disposable staged baseline with the one approved replacement."""
    plan_path, plan = load_plan(plan_path, plan_sha256)
    runtime = Path(runtime).resolve()
    seed = Path(plan["seed"])
    for protected in (seed, plan_path, plan["observerJar"], plan["buildProof"], plan["bridgeSource"]):
        disjoint(runtime, protected)
    current_receipt = runtime / INVENTORY
    if current_receipt.is_symlink() or not current_receipt.resolve().is_relative_to(runtime):
        raise ValueError("Runtime inventory must be a local regular file")
    if os.path.samefile(current_receipt, seed / INVENTORY):
        raise ValueError("Runtime inventory aliases the protected baseline receipt")
    original_bytes = current_receipt.read_bytes()
    if digest(current_receipt) != plan["baselineReceiptSha256"] or read(current_receipt) != plan["baselineInventory"]:
        raise AssertionError("Runtime must still carry the exact original baseline receipt")
    if code_files(runtime) != plan["derivedInventory"]["files"]:
        raise AssertionError("Runtime code is not exactly baseline plus the approved CoreMods replacement")
    try:
        verify_code(runtime)
    except AssertionError as error:
        original_rejection = str(error)
    else:
        raise AssertionError("Original baseline gate unexpectedly accepted the observer replacement")
    custody = runtime / CUSTODY
    custody.mkdir(exist_ok=False)
    (custody / "baseline-inventory.json").write_bytes(original_bytes)
    shutil.copy2(plan_path, custody / "observation-plan.json")
    temporary = custody / "derived-inventory.tmp.json"
    write_json(temporary, plan["derivedInventory"])
    os.replace(temporary, current_receipt)
    verify_code(runtime, plan["derivedInventory"])
    verify_code(seed, plan["baselineInventory"])
    pinned(seed / INVENTORY, plan["baselineReceiptSha256"])
    proof = dict(status="PASS_CODE_CUSTODY_ONLY_NO_MC", planSha256=plan_sha256.upper(),
        baselineReceiptSha256=plan["baselineReceiptSha256"], derivedReceiptSha256=digest(current_receipt),
        baselineGateBeforeReseal="REJECTED_AS_EXPECTED", baselineRejection=original_rejection,
        codeFileCount=plan["codeFileCount"], changedPaths=[COREMODS], observerJarSha256=plan["observerJarSha256"])
    write_json(custody / "receipt.json", proof)
    return audit_runtime(runtime, plan_path, plan_sha256)


def audit_runtime(runtime, plan_path, plan_sha256):
    _, plan = load_plan(plan_path, plan_sha256)
    runtime = Path(runtime).resolve()
    custody = runtime / CUSTODY
    proof = read(custody / "receipt.json")
    pinned(custody / "baseline-inventory.json", plan["baselineReceiptSha256"])
    pinned(custody / "observation-plan.json", plan_sha256)
    pinned(runtime / INVENTORY, proof["derivedReceiptSha256"])
    if read(runtime / INVENTORY) != plan["derivedInventory"]:
        raise AssertionError("Derived inventory differs from the observation plan")
    if (proof.get("status") != "PASS_CODE_CUSTODY_ONLY_NO_MC" or proof.get("changedPaths") != [COREMODS]
            or proof.get("codeFileCount") != plan["codeFileCount"] or proof.get("planSha256") != plan_sha256.upper()
            or proof.get("baselineReceiptSha256") != plan["baselineReceiptSha256"]
            or proof.get("observerJarSha256") != plan["observerJarSha256"]):
        raise AssertionError("Observation custody receipt changed")
    verify_code(runtime, plan["derivedInventory"])
    return proof


def stage_probe(output, plan_path, plan_sha256):
    """Create a NEW code-only copy and exercise the unchanged verifier without booting."""
    plan_path, plan = load_plan(plan_path, plan_sha256)
    output = Path(output).resolve()
    for protected in (plan["seed"], plan_path, plan["observerJar"], plan["buildProof"], plan["bridgeSource"]):
        disjoint(output, protected)
    output.mkdir(parents=True, exist_ok=False)
    for name in plan["baselineInventory"]["files"]:
        destination = output / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(Path(plan["seed"]) / name, destination)
    shutil.copy2(Path(plan["seed"]) / INVENTORY, output / INVENTORY)
    verify_code(output, plan["baselineInventory"])
    shutil.copy2(plan["observerJar"], output / COREMODS)
    return seal_runtime(output, plan_path, plan_sha256)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_subparsers(dest="mode", required=True)
    derive = modes.add_parser("derive")
    for name in ("seed", "observer", "build-proof", "bridge-source", "output"):
        derive.add_argument("--" + name, type=Path, required=True)
    for name in ("inventory-sha256", "observer-sha256", "build-proof-sha256"):
        derive.add_argument("--" + name, required=True)
    for name in ("stage-probe", "seal-runtime", "audit"):
        command = modes.add_parser(name)
        command.add_argument("--plan", type=Path, required=True)
        command.add_argument("--plan-sha256", required=True)
        command.add_argument("--output" if name == "stage-probe" else "--runtime", type=Path, required=True)
    args = parser.parse_args(argv)
    if args.mode == "derive":
        plan = make_plan(args.seed, args.inventory_sha256, args.observer, args.observer_sha256,
                         args.build_proof, args.build_proof_sha256, args.bridge_source)
        output = args.output.resolve()
        for protected in (args.seed, args.observer, args.build_proof, args.bridge_source):
            disjoint(output, protected)
        if output.exists():
            raise FileExistsError(output)
        write_json(output, plan)
        print("DERIVED PLAN ONLY: " + str(plan["codeFileCount"]) + " code files; one replacement; no MC")
    else:
        operation = {"stage-probe": stage_probe, "seal-runtime": seal_runtime, "audit": audit_runtime}[args.mode]
        proof = operation(args.output if args.mode == "stage-probe" else args.runtime, args.plan, args.plan_sha256)
        print(json.dumps(proof, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
