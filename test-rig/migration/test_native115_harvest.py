"""Harvest fixture isolation and unchanged-oracle tests; never start a server."""

from copy import deepcopy
import hashlib
import json
from pathlib import Path
import unittest
from uuid import UUID

import native115_harvest as native


def sample(label="exercise"):
    rows = []
    identity = 1
    for name, (level, allowed, silk, minimum, maximum, trials) in native.EXPECTED.items():
        evidence = []
        for trial in range(trials):
            drops = []
            if minimum:
                drops.append(dict(entityId=identity, uuid=str(UUID(int=identity)),
                                  item="unit:actual-drop", count=minimum, removed=False))
                identity += 1
            evidence.append(dict(trial=trial, worldTime=1, removedBeforeCleanup=trial,
                                 liveBeforeCleanup=int(trial > 0), liveAfterCleanup=0, actualDrops=drops))
        rows.append(dict(id=name, status="PASS", historicalHarvestLevel=level, harvestAllowed=allowed,
                         silkTouch=silk, trials=trials, actualDropCounts=[minimum] * trials,
                         connectionMask=int(name.split("/")[1]) if name.startswith(("wire/", "scaffold/")) else -1,
                         actualSolid=name.startswith(("sulfur/", "lithium/", "wire/")),
                         fullCollision=not name.startswith("wire/"), dropEvidence=evidence))
    rows.append(dict(id="solar/native-solidity", status="PASS", actualSolid=True))
    return dict(label=label, minecraft="1.15.2", status="PASS", checks=rows,
                fixtureIsolation=native.ISOLATION_PROTOCOL)


def row(document, name="sulfur/wood"):
    return next(entry for entry in document["checks"] if entry["id"] == name)


class HarvestIsolationTests(unittest.TestCase):
    def test_original_270_harvest_oracles_are_exactly_preserved(self):
        checksum = hashlib.sha256(json.dumps(native.EXPECTED, sort_keys=True, separators=(",", ":")).encode()).hexdigest().upper()
        self.assertEqual("A43F316D748DFCCB4DA9B72AE771851FFBD30661C56E5609A0D04E0AC0050D5B", checksum)
        self.assertEqual(270, len(native.EXPECTED))

    def test_java_witness_inputs_masks_trial_counts_and_thresholds_are_unchanged(self):
        source = (Path(__file__).resolve().parents[1] /
                  "src/main/java/zone/moddev/mc/advantageworks/NativeHarvestProbe.java").read_text()
        region = source.split("        String sulfur =", 1)[1].split("        JsonObject solar =", 1)[0]
        self.assertEqual("3FF0AC4C13CA1B737679F67128CE2F41C5A0873E89258D456628BA8A309BAA9A",
                         hashlib.sha256(region.encode()).hexdigest().upper())
        self.assertIn("Actual drop quantity changed: ", source)
        self.assertIn('require(count >= minimum && count <= maximum,', source)
        self.assertIn('equals(dropId), "Wrong actual drop: "', source)
        self.assertIn('bounds, entity -> !entity.removed)', source)

    def test_new_protocol_accepts_all_original_cases_in_each_restart_phase(self):
        for label in ("exercise", "restored"):
            document = sample(label)
            native.require_checks(document, label)
            native.require_isolation(document)

    def test_old_or_missing_fixture_protocol_is_rejected(self):
        for marker in (None, "native114", "native115-default-entity-query"):
            document = sample()
            document["fixtureIsolation"] = marker
            with self.subTest(marker=marker), self.assertRaisesRegex(AssertionError, "isolation protocol"):
                native.require_isolation(document)

    def test_incomplete_trial_audits_cannot_qualify(self):
        document = sample()
        row(document)["dropEvidence"].pop()
        with self.assertRaisesRegex(AssertionError, "per-trial"):
            native.require_isolation(document)

    def test_live_remainder_bad_sequence_or_noninteger_cleanup_is_rejected(self):
        for field, value in (("liveAfterCleanup", 1), ("liveAfterCleanup", False),
                             ("removedBeforeCleanup", -1), ("liveBeforeCleanup", -1),
                             ("trial", 3), ("worldTime", -1)):
            document = sample()
            row(document)["dropEvidence"][0][field] = value
            with self.subTest(field=field, value=value), self.assertRaisesRegex(AssertionError, "not isolated"):
                native.require_isolation(document)

    def test_removed_entities_missing_identity_and_count_lies_are_rejected(self):
        for field, value in (("removed", True), ("removed", None), ("entityId", "1"),
                             ("uuid", ""), ("item", ""), ("count", 0), ("count", True), ("count", 2)):
            document = sample()
            row(document)["dropEvidence"][0]["actualDrops"][0][field] = value
            with self.subTest(field=field, value=value), self.assertRaises(AssertionError):
                native.require_isolation(document)

    def test_same_entity_cannot_be_counted_in_two_trials_or_two_rows(self):
        for across_rows in (False, True):
            for field in ("entityId", "uuid"):
                document = sample()
                first = row(document)["dropEvidence"][0]["actualDrops"][0]
                later = row(document, "sulfur/stone")["dropEvidence"][0] if across_rows else row(document)["dropEvidence"][1]
                later["actualDrops"][0][field] = first[field]
                with self.subTest(across_rows=across_rows, field=field), self.assertRaisesRegex(AssertionError, "repeated"):
                    native.require_isolation(document)

    def test_no_drop_case_cannot_hide_an_unexpected_live_entity(self):
        document = sample()
        row(document, "lithium/wood-fails")["dropEvidence"][0]["actualDrops"] = [
            dict(entityId=99999, uuid=str(UUID(int=99999)), item="electricadvantage:sulfur_powder", count=4, removed=False)]
        with self.assertRaisesRegex(AssertionError, "disagree"):
            native.require_isolation(document)

    def test_failed_identity_witness_reports_actual_failure_not_missing_inventory(self):
        document = sample()
        document["status"] = "FAIL"
        failed = row(document, "sulfur/silk")
        failed.update(status="FAIL", failure="Wrong actual drop: 4 sulfur_powder")
        with self.assertRaisesRegex(AssertionError, "sulfur/silk: Wrong actual drop: 4 sulfur_powder"):
            native.require_checks(document, "exercise")

    def test_complete_inventory_with_failed_aggregate_status_still_fails(self):
        document = sample()
        document["status"] = "FAIL"
        with self.assertRaisesRegex(AssertionError, "witnesses failed"):
            native.require_checks(document, "exercise")

    def test_original_drop_bounds_wrong_tools_masks_shapes_and_solar_remain_strict(self):
        for name, field, value in (("sulfur/wood", "actualDropCounts", [5] * 8),
                                   ("sulfur/silk", "actualDropCounts", [4]),
                                   ("lithium/wood-fails", "actualDropCounts", [1]),
                                   ("lithium/stone", "historicalHarvestLevel", 0),
                                   ("wire/63/hand", "connectionMask", 0),
                                   ("wire/0/hand", "actualSolid", False),
                                   ("wire/0/hand", "fullCollision", True),
                                   ("scaffold/0/hand", "actualSolid", True),
                                   ("scaffold/0/hand", "fullCollision", False),
                                   ("solar/native-solidity", "actualSolid", False)):
            document = sample()
            row(document, name)[field] = deepcopy(value)
            with self.subTest(name=name, field=field), self.assertRaises(AssertionError):
                native.require_checks(document, "exercise")


if __name__ == "__main__":
    unittest.main()
