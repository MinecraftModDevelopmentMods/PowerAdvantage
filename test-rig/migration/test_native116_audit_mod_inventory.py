"""Exercise the actor auditor with real sealed inventories and worldless oracles."""

from contextlib import ExitStack
import copy
import json
from pathlib import Path
import stat
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

from matrix import digest, fingerprint
import native116_audit_actors as audit
import native116_mod_inventory as inventory


class AuditModInventoryTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="native116 auditor spaces ")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.run = self.root / "evidence"
        self.runtime = self.run / "route"
        self.mods = self.runtime / "mods"
        self.mods.mkdir(parents=True)
        self.source = self.root / "protected source"
        self.source.mkdir()
        (self.source / "original.txt").write_text("unchanged source", encoding="ascii")
        self.manifest = self.root / "sealed manifest.json"
        self.ledger = self.runtime / "input-jars.json"
        self.rows, jars = [], []
        for identity in sorted(inventory.EXPECTED_IDS):
            name = identity + "-1.0.0.116051.jar"
            source = self.root / name
            source.write_bytes(identity.encode("ascii"))
            (self.mods / name).write_bytes(source.read_bytes())
            checksum = digest(source)
            self.rows.append(dict(file=name, source=str(source), sha256=checksum))
            jars.append(dict(id=identity, version="1.0.0.116051", path=str(source), sha256=checksum))
        self.write(self.manifest, {"target116": {"jars": jars}})
        self.write(self.ledger, self.rows)
        self.provenance = dict(source=str(self.source), sourceFingerprint=fingerprint(self.source),
                               targetManifest=str(self.manifest), targetManifestSha256=digest(self.manifest))
        self.write_provenance()
        self.checks = {key: {"status": "PASS"} for key in (
            "conversion", "adapter", "restart", "idempotence", "runtime", "runtime-restart",
            "runtime-ticks", "restart-ticks", "conversion-frozen", "adapter-frozen", "restart-frozen")}
        self.checks["backup-stability"] = {"status": "PASS", "backups": {}}
        for prefix in ("semantic-", "dimension-semantic-"):
            for label in ("converted", "adapter", "restarted", "runtime", "runtime-restart"):
                self.write(self.runtime / (prefix + label + ".json"), {})

    @staticmethod
    def write(path, value):
        path.write_text(json.dumps(value), encoding="utf-8")

    def write_provenance(self):
        self.write(self.runtime / "native116-source-provenance.json", self.provenance)

    def replay(self, native_difference=False):
        kind = "dimensions" if native_difference else "actors"
        checks = copy.deepcopy(self.checks)
        if native_difference:
            for key in ("conversion-frozen", "adapter-frozen", "restart-frozen"):
                del checks[key]
            checks["conversion-ticks"] = {"status": "PASS"}
            keys = ("conversion", "adapter", "restart", "idempotence", "runtime", "runtime-restart")
            for key in keys:
                checks[key] = {"status": "FAIL"}
            platform = dict(assessment={"status": "PASS_WITH_NATIVE_DIFFERENCE"},
                            semanticAssessments={key: {"status": "PASS_WITH_NATIVE_DIFFERENCE"} for key in keys},
                            proofPath=None, assessorSha256=digest(Path(audit.native116_dimension_assessment.__file__)))
            self.write(self.runtime / "native116-platform-assessment.json", platform)
            self.write(self.run / "summary.json", {"kind": kind, "routes": ["route"]})
        self.write(self.run / "results.json", {"route": checks})
        with ExitStack() as stack:
            replacements = ((audit, "pinned_profile", None),
                            (audit, "baseline", ({"file": {"Inventory": [], "EnderItems": []}}, None)),
                            (audit, "expected_state", {}), (audit, "capture_actors", {}),
                            (audit, "capture_dimensions", {}), (audit, "native_dimension_state", {}),
                            (audit, "raw", {}), (audit, "require_clean_log", None),
                            (audit.actors, "backup_state", {}), (audit.actors, "compare", {"status": "PASS"}),
                            (audit.actors, "tick_result", {"status": "PASS"}),
                            (audit.actors, "freeze_result", {"status": "PASS"}),
                            (audit.dimensions, "verdict", {"status": "FAIL"}),
                            (audit.dimensions, "tick_result", {"status": "PASS"}),
                            (audit.dimensions, "exact_item_differences", []),
                            (audit, "assess", {"status": "PASS_WITH_NATIVE_DIFFERENCE"}),
                            (audit, "summarize_checks", {"status": "PASS_WITH_NATIVE_DIFFERENCE"}))
            for owner, name, value in replacements:
                stack.enter_context(patch.object(owner, name, return_value=value))
            return audit.replay(self.run, kind), checks

    def reject(self, native_difference=False):
        result, checks = self.replay(native_difference)
        self.assertEqual("FAIL", result["status"])
        self.assertEqual(checks, result["routes"]["route"]["oracles"])
        self.assertEqual("FAIL", result["routes"]["route"]["modInventory"]["status"])
        self.assertTrue(any("mod inventory" in finding for finding in result["findings"]))
        return result

    def test_exact_six_real_inventory_preserves_existing_strict_checks(self):
        result, checks = self.replay()
        self.assertEqual("PASS", result["status"])
        self.assertEqual(checks, result["routes"]["route"]["oracles"])
        receipt = result["routes"]["route"]["modInventory"]
        self.assertEqual("PASS", receipt["status"])
        self.assertEqual(self.provenance["targetManifestSha256"], receipt["manifestSha256"])
        self.assertEqual(6, len(receipt["jars"]))
        self.assertFalse(receipt["atomicSnapshot"])

    def test_extra_jar_is_an_integrity_failure_with_unchanged_raw_oracles(self):
        (self.mods / "extra.jar").write_bytes(b"extra")
        self.reject()

    def test_missing_physical_jar_is_a_reported_failure_not_a_crash(self):
        (self.mods / self.rows[0]["file"]).unlink()
        self.reject()

    def test_missing_ledger_row_fails(self):
        self.write(self.ledger, self.rows[:-1])
        self.reject()

    def test_nonjar_physical_entry_fails(self):
        (self.mods / "unapproved.txt").write_text("not a mod", encoding="ascii")
        self.reject()

    def test_aborted_route_still_reports_inventory_failure(self):
        self.checks = {"campaign-error": {"status": "FAIL", "error": "incomplete"}}
        (self.mods / "extra.jar").write_bytes(b"extra")
        result, _ = self.replay()
        self.assertEqual("FAIL", result["status"])
        route = result["routes"]["route"]
        self.assertEqual("FAIL", route["modInventory"]["status"])
        self.assertEqual("NOT_POSSIBLE_WITH_INCOMPLETE_SAVED_EVIDENCE", route["semanticReplay"])

    def test_case_changed_ledger_filename_fails(self):
        self.rows[0]["file"] = self.rows[0]["file"].upper().replace(".JAR", ".jar")
        self.write(self.ledger, self.rows)
        self.reject()

    def test_case_colliding_duplicate_ledger_row_fails(self):
        self.rows[1] = dict(self.rows[0], file=self.rows[0]["file"].upper().replace(".JAR", ".jar"))
        self.write(self.ledger, self.rows)
        self.reject()

    def test_altered_disk_bytes_cannot_be_authorized_by_self_declared_ledger_hash(self):
        target = self.mods / self.rows[0]["file"]
        target.write_bytes(b"altered")
        self.rows[0]["sha256"] = digest(target)
        self.write(self.ledger, self.rows)
        self.reject()

    def test_unchanged_ledger_cannot_hide_altered_disk_bytes(self):
        (self.mods / self.rows[0]["file"]).write_bytes(b"altered")
        self.reject()

    def test_missing_manifest_sha_is_not_inferred(self):
        del self.provenance["targetManifestSha256"]
        self.write_provenance()
        self.reject()

    def test_incorrect_manifest_sha_fails(self):
        self.provenance["targetManifestSha256"] = "0" * 64
        self.write_provenance()
        self.reject()

    def test_manifest_bytes_are_bound_to_sealed_provenance_sha(self):
        self.manifest.write_bytes(self.manifest.read_bytes() + b"\n")
        self.reject()

    def test_ledger_source_must_match_the_sealed_manifest(self):
        self.rows[0]["source"] = str(self.root / "elsewhere.jar")
        self.write(self.ledger, self.rows)
        self.reject()

    def test_reparse_physical_jar_fails_through_real_validator(self):
        target = self.mods / self.rows[0]["file"]
        original = Path.lstat

        def reparse(path, *args, **kwargs):
            metadata = original(path, *args, **kwargs)
            if path == target:
                fields = {name: getattr(metadata, name) for name in dir(metadata) if name.startswith("st_")}
                fields["st_file_attributes"] = getattr(metadata, "st_file_attributes", 0) | stat.FILE_ATTRIBUTE_REPARSE_POINT
                return SimpleNamespace(**fields)
            return metadata

        with patch.object(Path, "lstat", reparse):
            self.reject()

    def test_inventory_failure_is_never_waived_by_native_health_classification(self):
        (self.mods / "extra.jar").write_bytes(b"extra")
        result = self.reject(native_difference=True)
        self.assertEqual("FAIL", result["nativePlatformAssessment"]["status"])
        self.assertTrue(any("mod inventory" in value for value in result["nativePlatformAssessment"]["otherFindings"]))

    def test_inventory_success_does_not_turn_raw_native_health_failure_into_pass(self):
        result, checks = self.replay(native_difference=True)
        self.assertEqual("FAIL", result["status"])
        self.assertEqual(checks, result["routes"]["route"]["oracles"])
        self.assertEqual("PASS", result["routes"]["route"]["modInventory"]["status"])
        self.assertEqual("PASS_WITH_NATIVE_DIFFERENCE", result["nativePlatformAssessment"]["status"])


if __name__ == "__main__":
    unittest.main()
