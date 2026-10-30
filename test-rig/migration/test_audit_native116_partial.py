"""Independent native audit must fail closed on missing or altered engine proof."""

import copy
import json
from pathlib import Path
import tempfile
import unittest

from audit_native116_partial import geometry_evidence, snapshot


GEOMETRY = dict(fullLevel=33, entityTickingLevel=31, maxDistance=11,
                lastHolderLevel=44, holderRadius=13, observedForcedLevels=[31])


class NativeGeometryAuditTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.runtime = Path(self.temp.name)
        self.results = self.runtime / "advantage-works-results"
        self.results.mkdir()

    def observation(self, label, geometry):
        path = self.results / ("lifecycle-" + label + ".json")
        path.write_text(json.dumps(dict(worldTime=42, ticketGeometry=geometry)), encoding="utf-8")
        return path

    def test_raw_geometry_is_checked_without_mutating_evidence(self):
        path = self.observation("valid", GEOMETRY)
        original = path.read_bytes()
        self.assertEqual(snapshot(self.runtime, "valid")["ticketGeometry"], GEOMETRY)
        result = geometry_evidence(self.runtime)
        self.assertEqual(result["snapshots"], 1)
        self.assertEqual(result["forced_ticket_snapshots"], 1)
        self.assertEqual(path.read_bytes(), original)

    def test_an_altered_raw_observation_cannot_hide_behind_valid_canonical(self):
        self.observation("canonical", GEOMETRY)
        altered = copy.deepcopy(GEOMETRY)
        altered["lastHolderLevel"] = 43
        self.observation("pending", altered)
        with self.assertRaisesRegex(AssertionError, "geometry differs"):
            geometry_evidence(self.runtime)

    def test_no_observed_forced_ticket_is_not_runtime_geometry_proof(self):
        missing = copy.deepcopy(GEOMETRY)
        missing["observedForcedLevels"] = []
        self.observation("unforced", missing)
        with self.assertRaisesRegex(AssertionError, "No observed live"):
            geometry_evidence(self.runtime)

    def test_missing_geometry_and_wrong_forced_level_are_rejected(self):
        for value in ({}, dict(GEOMETRY, observedForcedLevels=[30])):
            self.observation("invalid", value)
            with self.assertRaises(AssertionError):
                snapshot(self.runtime, "invalid")

    def test_empty_evidence_is_rejected(self):
        with self.assertRaisesRegex(AssertionError, "No raw native"):
            geometry_evidence(self.runtime)


if __name__ == "__main__":
    unittest.main()
