import copy
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import MagicMock, patch

import dimensions
from matrix import digest
import native116_actors
import native116_audit_actors
import native116_dimension_assessment as assessment


OWNER = "e036830e-303d-3ac3-bd23-062b72eed895"
PROOF = {"sha256": assessment.CONTROL_LOG_SHA256, "worldDataVersion": 2586,
         "forgeVersion": "36.2.34", "scope": "NATIVE_TAMED_WOLF_LOAD_8_TO_20_NO_POWER"}


def states():
    before = {"dimension": -1, "file": {"Inventory": [{"id": "minecraft:stone", "Count": 3,
               "Slot": 0, "tag": {"Private": "keep"}}]}, "relationships": {}, "entities": {
               "passenger-wolf": {"id": "minecraft:wolf", "UUIDMost": -17, "UUIDLeast": 19,
               "OwnerUUID": OWNER, "Health": 8.0, "Leashed": 0,
               "Attributes": {"generic.maxHealth": {"Base": 20.0},
                              "example:private": {"Base": 3.25, "Private": {"keep": [1, 2]}}}}}}
    after = copy.deepcopy(before)
    after["entities"]["passenger-wolf"]["Health"] = 20.0
    return before, after


def call(before, after, proof=Path("reviewed-control.log"), **kwargs):
    return assessment.assess(before, after, dimensions.verdict(before, after), proof, **kwargs)


def checks_and_assessments():
    before, after = states()
    checks, assessments = {}, {}
    for key in assessment.SEMANTIC_CHECKS:
        old = after if key == "idempotence" else before
        checks[key] = dimensions.verdict(old, after)
        assessments[key] = assessment.assess(old, after, checks[key], Path("control.log"), "survival")
    for key in assessment.DIMENSION_CHECKS - assessment.SEMANTIC_CHECKS:
        checks[key] = {"status": "PASS"}
    checks["backup-stability"]["backups"] = {}
    return checks, assessments


class NativeDifferenceAssessmentTest(unittest.TestCase):
    def test_only_reviewed_tamed_wolf_change_has_separate_status_and_keeps_raw_fail(self):
        before, after = states(); original = copy.deepcopy((before, after))
        with patch.object(assessment, "load_native_proof", return_value=PROOF) as proof:
            result = call(before, after)
        self.assertEqual("PASS_WITH_NATIVE_DIFFERENCE", result["status"])
        self.assertEqual("FAIL", result["rawStrictStatus"])
        self.assertEqual(dimensions.verdict(before, after), result["rawStrictResult"])
        self.assertEqual({"UUIDMost": -17, "UUIDLeast": 19}, result["nativeDifferences"][0]["entityUuid"])
        self.assertEqual(original, (before, after))
        proof.assert_called_once_with(Path("reviewed-control.log"))

    def test_missing_uuid_halves_cannot_be_classified(self):
        for key in ("UUIDMost", "UUIDLeast"):
            before, after = states()
            del before["entities"]["passenger-wolf"][key]; del after["entities"]["passenger-wolf"][key]
            self.assertEqual("FAIL", call(before, after)["status"])

    def test_invalid_uuid_halves_nonbool_signed64_are_rejected(self):
        for value in (True, False, 1.0, "17", None, 1 << 63, -(1 << 63) - 1):
            for key in ("UUIDMost", "UUIDLeast"):
                before, after = states()
                before["entities"]["passenger-wolf"][key] = value
                after["entities"]["passenger-wolf"][key] = value
                self.assertEqual("FAIL", call(before, after)["status"])

    def test_mismatching_entity_or_owner_uuid_cannot_be_classified(self):
        for key, value in (("UUIDMost", 27), ("UUIDLeast", 29), ("OwnerUUID", "00000000-0000-0000-0000-000000000001")):
            before, after = states(); after["entities"]["passenger-wolf"][key] = value
            self.assertEqual("FAIL", call(before, after)["status"])

    def test_owner_required_and_must_be_canonical_uuid(self):
        for value in (None, "not-owner", True, ""):
            before, after = states()
            before["entities"]["passenger-wolf"]["OwnerUUID"] = value
            after["entities"]["passenger-wolf"]["OwnerUUID"] = value
            self.assertEqual("FAIL", call(before, after)["status"])

    def test_other_entity_type_or_untamed_max_health_is_not_allowed(self):
        for change in ("type", "maximum"):
            before, after = states()
            for state in (before, after):
                if change == "type": state["entities"]["passenger-wolf"]["id"] = "minecraft:cat"
                else: state["entities"]["passenger-wolf"]["Attributes"]["generic.maxHealth"]["Base"] = 8.0
            self.assertEqual("FAIL", call(before, after)["status"])

    def test_no_generic_health_tolerance(self):
        for old, new in ((7.0, 20.0), (8.0, 19.0), (8.0, 20.00001), (True, 20.0)):
            before, after = states()
            before["entities"]["passenger-wolf"]["Health"] = old
            after["entities"]["passenger-wolf"]["Health"] = new
            self.assertEqual("FAIL", call(before, after)["status"])

    def test_other_raw_field_failures_cannot_be_relabelled(self):
        before, after = states()
        del after["entities"]["passenger-wolf"]["Leashed"]
        self.assertEqual("FAIL", call(before, after)["status"])

    def test_unknown_added_attributes_or_fields_are_not_silently_dropped(self):
        for location in ("attribute", "payload"):
            before, after = states()
            if location == "attribute": after["entities"]["passenger-wolf"]["Attributes"]["example:extra"] = {"Base": 4.0}
            else: after["entities"]["passenger-wolf"]["Attributes"]["generic.maxHealth"]["Extra"] = 1
            self.assertEqual("FAIL", call(before, after)["status"])

    def test_item_quantity_and_unknown_item_payload_remain_exact(self):
        for change in ("count", "payload"):
            before, after = states()
            if change == "count": after["file"]["Inventory"][0]["Count"] = 4
            else: after["file"]["Inventory"][0]["tag"]["Extra"] = True
            self.assertEqual("FAIL", call(before, after)["status"])

    def test_known_empty_modifiers_are_explicit_but_nonempty_changes_fail(self):
        before, after = states()
        after["entities"]["passenger-wolf"]["Attributes"]["generic.maxHealth"]["Modifiers"] = []
        with patch.object(assessment, "load_native_proof", return_value=PROOF):
            self.assertEqual("PASS_WITH_NATIVE_DIFFERENCE", call(before, after)["status"])
        after["entities"]["passenger-wolf"]["Attributes"]["generic.maxHealth"]["Modifiers"] = [{"Amount": 1.0}]
        self.assertEqual("FAIL", call(before, after)["status"])

    def test_unknown_attribute_empty_modifier_fields_are_not_defaulted(self):
        before, after = states()
        after["entities"]["passenger-wolf"]["Attributes"]["example:private"]["Modifiers"] = []
        self.assertEqual("FAIL", call(before, after)["status"])

    def test_reuses_only_the_existing_explicit_historical_survival_scope(self):
        before, after = states()
        after["entities"]["passenger-wolf"]["Pos"] = [10.0, 20.0, 30.0]
        with patch.object(assessment, "load_native_proof", return_value=PROOF):
            self.assertEqual("FAIL", call(before, after)["status"])
            self.assertEqual("PASS_WITH_NATIVE_DIFFERENCE", call(before, after, scope="survival")["status"])
        before["entities"]["passenger-wolf"]["Pos"] = [1.0, 2.0, 3.0]
        self.assertEqual("FAIL", call(before, after, scope="survival")["status"])

    def test_missing_proof_keeps_fail_and_changed_proof_is_rejected(self):
        before, after = states()
        self.assertEqual("FAIL", call(before, after, proof=None)["status"])
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "fake.log"; path.write_text("POWER_LEGACY_CLASS_ABSENT true\n")
            with self.assertRaisesRegex(AssertionError, "reviewed control"):
                call(before, after, proof=path)

    def test_raw_result_tampering_is_rejected(self):
        before, after = states()
        with self.assertRaises(AssertionError): assessment.assess(before, after, {"status": "PASS", "differences": []})

    def test_strict_pass_is_not_reported_as_native_difference(self):
        before, _ = states()
        result = call(before, copy.deepcopy(before), proof=None)
        self.assertEqual("PASS", result["status"])
        self.assertEqual([], result["nativeDifferences"])

    def test_complete_gate_set_and_tick_backup_controls_are_mandatory(self):
        with patch.object(assessment, "load_native_proof", return_value=PROOF):
            checks, values = checks_and_assessments()
        self.assertEqual("PASS_WITH_NATIVE_DIFFERENCE", assessment.summarize_checks(checks, values)["status"])
        self.assertEqual("FAIL", assessment.summarize_checks(checks, values)["rawStrictStatus"])
        for key in assessment.DIMENSION_CHECKS - assessment.SEMANTIC_CHECKS:
            bad = copy.deepcopy(checks); bad[key]["status"] = "FAIL"
            self.assertEqual("FAIL", assessment.summarize_checks(bad, values)["status"])
        incomplete = copy.deepcopy(checks); del incomplete["runtime-ticks"]
        self.assertEqual("FAIL", assessment.summarize_checks(incomplete, values)["status"])
        extra = copy.deepcopy(checks); extra["unreviewed-gate"] = {"status": "FAIL"}
        self.assertEqual("FAIL", assessment.summarize_checks(extra, values)["status"])

    def test_forged_none_uuid_in_route_assessment_is_rejected(self):
        with patch.object(assessment, "load_native_proof", return_value=PROOF):
            checks, values = checks_and_assessments()
        values["conversion"]["nativeDifferences"][0]["entityUuid"]["UUIDMost"] = None
        self.assertEqual("FAIL", assessment.summarize_checks(checks, values)["status"])

    def test_route_assessment_order_is_stable_for_independent_replay(self):
        with patch.object(assessment, "load_native_proof", return_value=PROOF):
            checks, values = checks_and_assessments()
        self.assertEqual(assessment.summarize_checks(checks, values),
                         assessment.summarize_checks(dict(reversed(list(checks.items()))), values))

    def test_invalid_proof_preflight_happens_before_any_boot(self):
        with tempfile.TemporaryDirectory() as temp:
            proof = Path(temp) / "wrong.log"; proof.write_text("wrong")
            with patch.object(native116_actors, "boot") as boot:
                with self.assertRaises(AssertionError): native116_actors.run(SimpleNamespace(kind="dimensions", native_wolf_proof=proof))
                boot.assert_not_called()


class NativeRunnerAssessmentIntegrationTest(unittest.TestCase):
    def test_route_writes_separate_assessment_without_changing_raw_checks(self):
        before, after = states()
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); source = root / "source"; source.mkdir(); runtime = root / "runtime"
            (source / "dimension-fixture.json").write_text('{"dimension":-1}')
            manifest = root / "manifest.json"; manifest.write_text("{}")

            def boot(runtime, target, java, port):
                runtime.mkdir(); (runtime / "server.properties").write_text("allow-nether=false\n")
                return "unused-no-process.jar"

            args = SimpleNamespace(kind="dimensions", java8=Path("unused"), port=25574,
                                   target_manifest=manifest, native_wolf_proof=Path("reviewed.log"))
            with patch("native116.boot", side_effect=boot), \
                 patch.object(native116_actors, "copy_fixture"), \
                 patch.object(native116_actors, "fingerprint", return_value={}), \
                 patch.object(native116_actors, "baseline", return_value=(before, "source")), \
                 patch.object(native116_actors, "Server") as server, \
                 patch.object(native116_actors, "command", return_value=after), \
                 patch.object(native116_actors, "release"), \
                 patch.object(native116_actors, "raw", return_value={}), \
                 patch.object(native116_actors, "require_clean_log"), \
                 patch.object(native116_actors.time, "sleep"), \
                 patch.object(native116_actors.actors, "backup_state", return_value={}), \
                 patch.object(native116_actors.dimensions, "tick_result", return_value={"status": "PASS"}), \
                 patch.object(assessment, "load_native_proof", return_value=PROOF):
                checks = native116_actors.run_route(source, runtime, None, args)
            self.assertEqual(3, server.call_count)
            self.assertEqual("FAIL", checks["conversion"]["status"])
            sidecar = json.loads((runtime / "native116-platform-assessment.json").read_text())
            self.assertEqual("PASS_WITH_NATIVE_DIFFERENCE", sidecar["assessment"]["status"])
            self.assertEqual("FAIL", sidecar["assessment"]["rawStrictStatus"])
            self.assertEqual(checks, json.loads((runtime / "native116-comparison.json").read_text()))

    def test_raw_campaign_exit_stays_nonzero(self):
        before, after = states()
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            args = SimpleNamespace(kind="dimensions", native_wolf_proof=None, output=root / "run",
                                   fixtures=root / "fixtures", target_manifest=root / "manifest.json", routes=["original110"], port=25574)
            with patch.object(native116_actors, "pinned_profile", return_value=(root / "seed", None, [])), \
                 patch.object(native116_actors, "output_path", side_effect=lambda path: path), \
                 patch.object(native116_actors.socket, "socket", return_value=MagicMock()), \
                 patch.object(native116_actors, "run_route", return_value={"conversion": dimensions.verdict(before, after)}):
                self.assertEqual(1, native116_actors.run(args))


class NativeIndependentAssessmentReplayTest(unittest.TestCase):
    def replay(self, root, tamper=False, bad_custody=False, omit_summary=False):
        before, after = states()
        route = root / "route"; route.mkdir()
        source = root / "source"; source.mkdir()
        manifest = root / "manifest.json"; manifest.write_text("{}")
        checks, semantics = checks_and_assessments()
        (root / "results.json").write_text(json.dumps({"route": checks}))
        if not omit_summary:
            (root / "summary.json").write_text(json.dumps({"kind": "dimensions", "routes": ["route"], "status": "FAIL"}))
        (route / "native116-source-provenance.json").write_text(json.dumps({
            "source": str(source), "sourceFingerprint": {"changed": "not-empty"} if bad_custody else {},
            "targetManifest": str(manifest), "targetManifestSha256": digest(manifest)}))
        (route / "input-jars.json").write_text("[]")
        for label in ("converted", "adapter", "restarted", "runtime", "runtime-restart"):
            (route / ("dimension-semantic-" + label + ".json")).write_text(json.dumps(after))
        platform = {"assessment": assessment.summarize_checks(checks, semantics), "semanticAssessments": semantics,
                    "proofPath": "control.log", "assessorSha256": digest(Path(assessment.__file__))}
        if tamper:
            platform["assessment"]["status"] = "PASS"
        (route / "native116-platform-assessment.json").write_text(json.dumps(platform))
        with patch.object(native116_audit_actors, "fingerprint", return_value={}), \
             patch.object(native116_audit_actors, "pinned_profile"), \
             patch.object(native116_audit_actors, "baseline", return_value=(before, "source")), \
             patch.object(native116_audit_actors, "capture_dimensions", return_value=copy.deepcopy(after)), \
             patch.object(native116_audit_actors, "raw", return_value={}), \
             patch.object(native116_audit_actors, "require_clean_log"), \
             patch.object(native116_audit_actors.actors, "backup_state", return_value={}), \
             patch.object(native116_audit_actors.dimensions, "tick_result", return_value={"status": "PASS"}):
            return native116_audit_actors.replay(root, "dimensions")

    def test_audit_replays_separate_assessment_but_keeps_all_five_raw_failures(self):
        with tempfile.TemporaryDirectory() as temp, patch.object(assessment, "load_native_proof", return_value=PROOF):
            result = self.replay(Path(temp))
        self.assertEqual("FAIL", result["status"])
        self.assertEqual(5, len(result["findings"]))
        self.assertEqual("PASS_WITH_NATIVE_DIFFERENCE", result["nativePlatformAssessment"]["status"])
        self.assertEqual([], result["nativePlatformAssessment"]["otherFindings"])

    def test_tampered_sidecar_cannot_relabel_raw_or_separate_qualification(self):
        with tempfile.TemporaryDirectory() as temp, patch.object(assessment, "load_native_proof", return_value=PROOF):
            result = self.replay(Path(temp), tamper=True)
        self.assertEqual("FAIL", result["status"])
        self.assertEqual("FAIL", result["nativePlatformAssessment"]["status"])
        self.assertTrue(any("independent replay" in value for value in result["findings"]))

    def test_source_custody_failure_blocks_native_difference_qualification(self):
        with tempfile.TemporaryDirectory() as temp, patch.object(assessment, "load_native_proof", return_value=PROOF):
            result = self.replay(Path(temp), bad_custody=True)
        self.assertEqual("FAIL", result["nativePlatformAssessment"]["status"])
        self.assertTrue(any("source fingerprint" in value for value in result["findings"]))

    def test_partial_campaign_does_not_gain_completed_native_qualification(self):
        with tempfile.TemporaryDirectory() as temp, patch.object(assessment, "load_native_proof", return_value=PROOF):
            result = self.replay(Path(temp), omit_summary=True)
        self.assertEqual("FAIL", result["nativePlatformAssessment"]["status"])
        self.assertFalse(result["nativePlatformAssessment"]["completeCampaign"])


if __name__ == "__main__":
    unittest.main()
