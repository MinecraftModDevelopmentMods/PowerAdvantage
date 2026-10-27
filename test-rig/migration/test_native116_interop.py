import unittest

from native116_interop import require_checks


class NativeInteropOracleTests(unittest.TestCase):
    ORIGINAL_EXERCISE_IDS = (
        "converter_rf_steam/capability",
        "converter_rf_steam/forge-to-advantage",
        "converter_rf_steam/advantage-to-forge",
        "converter_rf_steam/invalidation",
        "converter_rf_electricity/capability",
        "converter_rf_electricity/forge-to-advantage",
        "converter_rf_electricity/advantage-to-forge",
        "converter_rf_electricity/invalidation",
        "converter_rf_quantum/capability",
        "converter_rf_quantum/forge-to-advantage",
        "converter_rf_quantum/advantage-to-forge",
        "converter_rf_quantum/invalidation",
        "converter_tr_electricity/capability",
        "converter_tr_electricity/forge-to-advantage",
        "converter_tr_electricity/advantage-to-forge",
        "converter_tr_electricity/invalidation",
        "fluid/simulation-conservation-and-identity",
        "portable-tank/native-break-drop-and-replace",
        "locked-chest/conveyor-refuses-extraction",
        "unlocked-chest/conveyor-transfers-exactly",
    )
    CALLBACK_IDS = (
        "capability/native-chunk-unloaded-callback",
        "capability/native-clear-removed-and-revive",
    )
    RESTORED_IDS = (
        "portable-tank/full-process-restart",
        "conveyor/full-process-restart",
    )

    def document(self, phase="exercise"):
        ids = self.ORIGINAL_EXERCISE_IDS + self.CALLBACK_IDS if phase == "exercise" else self.RESTORED_IDS
        return dict(phase=phase, status="PASS", checks=[dict(id=id, status="PASS") for id in ids])

    def test_complete_witness_sets_pass(self):
        for phase in ("exercise", "restored"):
            require_checks(self.document(phase), phase)

    def test_single_conversion_failure_is_not_hidden_by_overall_status(self):
        document = self.document()
        document["checks"][2].update(status="FAIL", failure="energy disappeared")
        with self.assertRaisesRegex(AssertionError, "energy disappeared"):
            require_checks(document, "exercise")

    def test_missing_and_duplicate_witnesses_fail(self):
        document = self.document()
        document["checks"].pop()
        with self.assertRaisesRegex(AssertionError, "Incomplete"):
            require_checks(document, "exercise")
        document = self.document()
        document["checks"][1]["id"] = document["checks"][0]["id"]
        with self.assertRaisesRegex(AssertionError, "Duplicate"):
            require_checks(document, "exercise")

    def test_original_twenty_are_not_native_callback_proof(self):
        document = self.document()
        document["checks"] = document["checks"][:20]
        with self.assertRaisesRegex(AssertionError, "Incomplete"):
            require_checks(document, "exercise")

    def test_each_callback_is_required_and_cannot_be_relabelled(self):
        for id in self.CALLBACK_IDS:
            with self.subTest(id=id):
                document = self.document()
                next(row for row in document["checks"] if row["id"] == id)["id"] = "unrelated/PASS"
                with self.assertRaisesRegex(AssertionError, "Unexpected"):
                    require_checks(document, "exercise")

    def test_callback_failures_are_not_hidden_by_overall_pass(self):
        for id in self.CALLBACK_IDS:
            with self.subTest(id=id):
                document = self.document()
                next(row for row in document["checks"] if row["id"] == id).update(
                    status="FAIL", failure="invalid callback lifecycle")
                with self.assertRaisesRegex(AssertionError, "invalid callback lifecycle"):
                    require_checks(document, "exercise")

    def test_original_and_restoration_names_remain_required(self):
        for phase, ids in (("exercise", self.ORIGINAL_EXERCISE_IDS), ("restored", self.RESTORED_IDS)):
            for id in ids:
                with self.subTest(phase=phase, id=id):
                    document = self.document(phase)
                    next(row for row in document["checks"] if row["id"] == id)["id"] = "unrelated/PASS"
                    with self.assertRaisesRegex(AssertionError, "Unexpected"):
                        require_checks(document, phase)

    def test_unknown_phase_is_not_a_restoration_witness(self):
        document = self.document("restored")
        document["phase"] = "not-a-phase"
        with self.assertRaisesRegex(AssertionError, "Unknown"):
            require_checks(document, "not-a-phase")

    def test_overall_failure_is_not_hidden_by_passing_rows(self):
        document = self.document()
        document["status"] = "FAIL"
        with self.assertRaisesRegex(AssertionError, "defects"):
            require_checks(document, "exercise")

    def test_extra_exercise_or_restoration_rows_fail(self):
        for phase in ("exercise", "restored"):
            with self.subTest(phase=phase):
                document = self.document(phase)
                document["checks"].append(dict(id="unrelated/PASS", status="PASS"))
                with self.assertRaisesRegex(AssertionError, "Incomplete"):
                    require_checks(document, phase)


if __name__ == "__main__":
    unittest.main()
