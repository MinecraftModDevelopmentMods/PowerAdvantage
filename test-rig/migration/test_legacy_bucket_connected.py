"""Proof-bound connected permit gates; no servers or Minecraft artifact mutations."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import legacy_bucket_connected as wrapper


def qualified():
    token = "a" * 32
    candidate = dict(codeInventory={"files": {wrapper.proof.LAUNCHER: {"sha256": wrapper.proof.PUBLIC_FORGE_SHA}}},
        mods=[dict(id=mod, version="2.4.0.11" if mod == "basemetals" else "1",
                   runtimeVersion="2.4.0" if mod == "basemetals" else "1",
                   sha256=wrapper.proof.BASE_SHA if mod == "basemetals" else mod, path=mod + ".jar")
              for mod in sorted(wrapper.proof.MOD_IDS)])
    baseline = copy.deepcopy(candidate)
    next(row for row in baseline["mods"] if row["id"] == "poweradvantage")["sha256"] = "older-power"
    inputs = dict(baseline=baseline, candidate=candidate)
    roles, diagnostics = {}, []
    for role in inputs:
        flags = dict(token=token, enabled=True, created=True, registered=True, item="forge:bucketFilled",
                     world="AdvantageWorksMigration", mods={row["id"]: row["runtimeVersion"] for row in inputs[role]["mods"]})
        roles[role] = dict(status="PASS", operationCases=3, exactColdCases=3,
                           startupFlags={phase: copy.deepcopy(flags) for phase in ("operate", "restart")})
        for phase in ("operate", "restart"):
            diagnostics.append(dict(role=role, path="/proof/" + role + "/console-" + phase + ".log",
                originalSeverity="ERROR", message="[12:34:56] [Server thread/ERROR] [FML]: Trying to activate the universal filled bucket too late. Call it statically in your Mods class. Mod: Base Metals"))
    return dict(status="PASS", token=token, inputs=inputs, roles=roles, heldDiagnostics=diagnostics,
        classification=dict(severity="NOTICE", originalSeverity="ERROR", baseSha256=wrapper.proof.BASE_SHA,
                            forgeSha256=wrapper.proof.PUBLIC_FORGE_SHA))


def target(document):
    result = copy.deepcopy(document["inputs"]["candidate"])
    result["mods"] += [dict(id=mod, version="current", runtimeVersion="current", sha256=mod, path=mod + ".jar")
                       for mod in sorted(wrapper.EXTRAS)]
    return result


def primary():
    phases = {phase + "-" + kind: {"status": "PASS"} for phase in ("converted", "restarted")
              for kind in ("preservation", "operation", "completed-work")}
    document = dict(status="PASS", protocolUnchanged=True,
        routes=[dict(sourceKey=source, status="PASS", sourceUnchanged=True, phases=copy.deepcopy(phases))
                for source in sorted(wrapper.SOURCES)])
    processes = {source + phase: dict(sourceKey=source, phase=phase, status="PASS")
                 for source in wrapper.SOURCES for phase in ("converted", "restarted")}
    return document, processes


class LegacyBucketConnectedTest(unittest.TestCase):
    def test_complete_qualified_proof_and_profile_are_accepted(self):
        document = qualified()
        wrapper.check_proof(document)
        wrapper.bind_target(document, target(document))

    def test_pending_failed_partial_or_unregistered_proof_cannot_authorize_notice(self):
        for mutation in ("pending", "cold", "boot", "diagnostic", "registered", "version"):
            document = qualified()
            if mutation == "pending": document["status"] = wrapper.proof.PENDING
            elif mutation == "cold": document["roles"]["baseline"]["exactColdCases"] = 2
            elif mutation == "boot": del document["roles"]["candidate"]["startupFlags"]["restart"]
            elif mutation == "diagnostic": document["heldDiagnostics"].pop()
            elif mutation == "registered": document["roles"]["candidate"]["startupFlags"]["restart"]["registered"] = False
            else: document["roles"]["candidate"]["startupFlags"]["restart"]["mods"]["orespawn"] = "other"
            with self.assertRaises(AssertionError): wrapper.check_proof(document)

    def test_any_changed_core_power_harness_ore_or_base_requires_new_proof(self):
        document = qualified()
        for mod in wrapper.proof.MOD_IDS:
            for field in ("sha256", "version", "runtimeVersion"):
                current = target(document)
                next(row for row in current["mods"] if row["id"] == mod)[field] = "changed"
                with self.assertRaises(AssertionError): wrapper.bind_target(document, current)

    def test_changed_forge_inventory_or_undeclared_extra_is_rejected(self):
        document = qualified()
        current = target(document)
        current["codeInventory"]["files"]["extra.jar"] = {"sha256": "extra"}
        with self.assertRaises(AssertionError): wrapper.bind_target(document, current)
        current = target(document)
        current["mods"].append(dict(id="anothermod"))
        with self.assertRaises(AssertionError): wrapper.bind_target(document, current)

    def test_no_green_subset_of_routes_work_phases_or_processes(self):
        document, processes = primary()
        wrapper.require_primary(document, processes)
        for mutation in ("route", "work", "cold", "source", "process"):
            document, processes = primary()
            if mutation == "route": document["routes"].pop()
            elif mutation == "work": document["routes"][0]["phases"]["converted-completed-work"]["status"] = "INCOMPLETE"
            elif mutation == "cold": del document["routes"][0]["phases"]["restarted-preservation"]
            elif mutation == "source": document["routes"][0]["sourceUnchanged"] = False
            else: processes.pop(next(iter(processes)))
            with self.assertRaises(AssertionError): wrapper.require_primary(document, processes)

    def test_destination_mods_and_injected_probe_are_exactly_bound(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / "mods").mkdir()
            current = target(qualified())
            checksums = {row["path"]: row["sha256"] for row in current["mods"]}
            checksums["LegacyProbe.jar"] = wrapper.PROBE_SHA
            checksums[wrapper.proof.LAUNCHER] = wrapper.proof.PUBLIC_FORGE_SHA
            for name in checksums:
                path = root / (name if name == wrapper.proof.LAUNCHER else "mods/" + name)
                path.write_text("synthetic unit artifact")
            with patch.object(wrapper.proof, "sha", side_effect=lambda path: checksums[Path(path).name]):
                wrapper.verify_destination(root, current)
                for name in ("LegacyProbe.jar", "steamadvantage.jar", "electricadvantage.jar"):
                    old = checksums[name]
                    checksums[name] = "different bytes"
                    with self.assertRaises(AssertionError): wrapper.verify_destination(root, current)
                    checksums[name] = old
                checksums["unexpected.jar"] = "extra"
                (root / "mods/unexpected.jar").write_text("extra mod")
                with self.assertRaises(AssertionError): wrapper.verify_destination(root, current)

    def test_only_complete_post_mod_loading_log_arms_the_witness(self):
        self.assertIsNotNone(wrapper.MODS_LOADED.fullmatch(
            "[12:34:56] [Server thread/INFO] [FML]: Forge Mod Loader has successfully loaded 10 mods\n"))
        for line in ("Starting minecraft server version 1.10.2", "Preparing level", "FMLPreInitializationEvent"):
            self.assertIsNone(wrapper.MODS_LOADED.fullmatch(line))

    def test_explicit_proof_hash_and_all_raw_files_remain_required(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            document = qualified()
            for role in ("baseline", "candidate"):
                for name in ("console-operate.log", "console-restart.log", "bucket-startup-operate.json",
                        "bucket-startup-restart.json", "machine-operation-fixture.json",
                        "advantage-works-results/machine-operation-measured.json",
                        "advantage-works-results/machine-inspection-restarted.json"):
                    path = root / role / name
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_text("sealed unit data")
            document["filesSha256"] = {path.relative_to(root).as_posix(): wrapper.proof.sha(path)
                                       for path in root.rglob("*") if path.is_file()}
            receipt = root / "legacy-bucket-proof.json"
            receipt.write_text(json.dumps(document))
            checksum = wrapper.proof.sha(receipt)
            wrapper.verify_proof(receipt, checksum)
            with self.assertRaises(AssertionError): wrapper.verify_proof(receipt, "0" * 64)
            (root / "baseline/console-operate.log").write_text("changed evidence")
            with self.assertRaises(AssertionError): wrapper.verify_proof(receipt, checksum)


if __name__ == "__main__": unittest.main()
