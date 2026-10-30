"""Status-writer controls and offline prefix replay; no world or server activity."""

from copy import deepcopy
import unittest

from broad_campaign_status import campaign_document, campaign_status


NAMES = ("packaged-current", "packaged-decoupled")


def passed(name):
    drills = {str(n): {"native": n} for n in range(12)}
    checks = dict(status="PASS", assertions={"PASS": 96, "SKIP": 3}, manualRequired=[str(n) for n in range(21)])
    return dict(profile=name, status="PASS", beforeRestart=drills, afterRestart=deepcopy(drills), phases={
        "initial": dict(status="PASS", drills=deepcopy(drills)), "first": checks, "restarted": deepcopy(checks),
        "operation": dict(status="PASS", drills=12, moved=2), "persistence": dict(status="PASS", drills=12, exactEnergy=True)})


class BroadCampaignStatusTests(unittest.TestCase):
    def test_no_profiles_and_first_only_are_running_not_pass(self):
        self.assertEqual("RUNNING", campaign_status(NAMES, {}))
        self.assertEqual("RUNNING", campaign_status(NAMES, {NAMES[0]: passed(NAMES[0])}))

    def test_all_requested_profiles_required_for_final_pass(self):
        self.assertEqual("PASS", campaign_status(NAMES, {name: passed(name) for name in NAMES}, finished=True))
        self.assertEqual("FAIL", campaign_status(NAMES, {NAMES[0]: passed(NAMES[0])}, finished=True))

    def test_explicit_single_profile_completes_without_inferred_both(self):
        self.assertEqual("PASS", campaign_status((NAMES[0],), {NAMES[0]: passed(NAMES[0])}))

    def test_failed_second_retains_first_evidence(self):
        results = {NAMES[0]: passed(NAMES[0]), NAMES[1]: dict(profile=NAMES[1], status="FAIL", phases={}, error="stage failed")}
        snapshot = deepcopy(results)
        doc = campaign_document(NAMES, results, {"runnerSha256": "old pinned runner"}, finished=True)
        self.assertEqual("FAIL", doc["status"])
        self.assertEqual(snapshot, doc["profiles"])
        self.assertEqual(snapshot, results)

    def test_masked_error_and_incomplete_phases_are_failures(self):
        for mutation in ("error", "shutdown", "phase", "counts", "manual", "cold", "initial", "movement"):
            with self.subTest(mutation=mutation):
                result = passed(NAMES[0])
                if mutation == "error": result["error"] = "failed"
                if mutation == "shutdown": result["shutdownError"] = "failed"
                if mutation == "phase": result["phases"].pop("persistence")
                if mutation == "counts": result["phases"]["first"]["assertions"] = {"PASS": 0}
                if mutation == "manual": result["phases"]["first"]["manualRequired"] = []
                if mutation == "cold": result["afterRestart"] = {}
                if mutation == "initial": result["phases"]["initial"]["drills"] = {}
                if mutation == "movement": result["phases"]["operation"]["moved"] = 0
                self.assertEqual("FAIL", campaign_status((NAMES[0],), {NAMES[0]: result}))

    def test_invalid_or_extra_requested_profiles_are_not_accepted(self):
        for names in ((), (NAMES[0], NAMES[0]), ("unknown",)):
            with self.assertRaises(ValueError):
                campaign_status(names, {})
        self.assertEqual("FAIL", campaign_status((NAMES[0],), {name: passed(name) for name in NAMES}))

    def test_malformed_profile_fails_closed_without_crashing_progress_writer(self):
        for malformed in (None, [], {"status": "PASS"}):
            self.assertEqual("FAIL", campaign_status((NAMES[0],), {NAMES[0]: malformed}))
        result = passed(NAMES[0])
        result["phases"]["operation"]["moved"] = "not a count"
        self.assertEqual("FAIL", campaign_status((NAMES[0],), {NAMES[0]: result}))

    def test_replay_completed_document_changes_no_evidence_or_hashes(self):
        original = dict(status="PASS", runnerSha256="historical-sha", targetManifestSha256="target-sha",
                        profiles={name: passed(name) for name in NAMES})
        metadata = {key: value for key, value in original.items() if key not in ("status", "profiles")}
        self.assertEqual(original, campaign_document(NAMES, original["profiles"], metadata, finished=True))

    def test_partial_replay_diff_is_status_only(self):
        original = dict(status="PASS", runnerSha256="historical-sha", targetManifestSha256="target-sha",
                        profiles={NAMES[0]: passed(NAMES[0])})
        metadata = {key: value for key, value in original.items() if key not in ("status", "profiles")}
        result = campaign_document(NAMES, original["profiles"], metadata)
        self.assertEqual(["status"], [key for key in original if original[key] != result[key]])
        self.assertEqual("RUNNING", result["status"])


if __name__ == "__main__":
    unittest.main()
