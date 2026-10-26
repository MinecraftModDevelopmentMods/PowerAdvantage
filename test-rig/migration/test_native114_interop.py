import unittest

from native114_interop import require_checks


class NativeInteropOracleTests(unittest.TestCase):
    def document(self, phase="exercise"):
        return dict(phase=phase, status="PASS", checks=[dict(id=str(i), status="PASS")
                                                      for i in range(20 if phase == "exercise" else 2)])

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
        document["checks"][1]["id"] = "0"
        with self.assertRaisesRegex(AssertionError, "Duplicate"):
            require_checks(document, "exercise")


if __name__ == "__main__":
    unittest.main()
