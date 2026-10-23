"""The offline audit must not promote partial or incomplete evidence to a pass."""
import json
from pathlib import Path
import tempfile
import unittest

from audit_connected import audit, expected_routes
from connected import SOURCES


class ConnectedAuditTest(unittest.TestCase):
    def test_matrix_requires_eight_routes_including_three_bridges(self):
        routes = expected_routes(SOURCES)
        self.assertEqual(len(routes), 8)
        self.assertEqual(sum(r.endswith("-chained") for r in routes), 3)
        self.assertNotIn("legacy112-chained", routes)

    def test_no_evidence_cannot_pass(self):
        self.assertEqual(audit([])["status"], "FAIL")

    def test_empty_success_report_cannot_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base = root / "base.json"
            base.write_text("{}")
            (root / "profiles.json").write_text("{}")
            from matrix import digest
            (root / "input-provenance.json").write_text(json.dumps(dict(base=str(base), sha256=digest(base))))
            (root / "connected-results.json").write_text(json.dumps(dict(status="PASS", sources={}, routes={})))
            result = audit([root])
            self.assertEqual(result["status"], "FAIL")
            self.assertEqual(result["routes"], [])

    def test_unfinished_report_is_a_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base = root / "base.json"
            base.write_text("{}")
            (root / "profiles.json").write_text("{}")
            from matrix import digest
            (root / "input-provenance.json").write_text(json.dumps(dict(base=str(base), sha256=digest(base))))
            (root / "connected-results.json").write_text(json.dumps(dict(status="RUNNING", sources={}, routes={})))
            result = audit([root])
            self.assertTrue(any(c["status"] == "FAIL" and "completed campaign" in c["name"] for c in result["checks"]))


if __name__ == "__main__":
    unittest.main()
