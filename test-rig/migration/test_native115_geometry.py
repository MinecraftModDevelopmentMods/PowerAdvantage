"""Engine geometry and independent historical-oracle preservation contracts."""

import ast
import copy
import hashlib
import json
from pathlib import Path
import unittest
import tempfile

from matrix import digest
from native115 import RIG
from native115_geometry import require_geometry
from native115_campaign import ACCEPTED_POWER114, REQUIRED, require_routes, require_source114_receipt
from native115_harvest import EXPECTED, require_checks, mark_runtime


class GeometryTests(unittest.TestCase):
    def sample(self):
        return dict(ticketGeometry=dict(fullLevel=33, entityTickingLevel=31, maxDistance=11,
                                        lastHolderLevel=44, holderRadius=13, observedForcedLevels=[31]))

    def test_real_forced_ticket_level_and_generation_halo_are_both_required(self):
        require_geometry(self.sample(), forced=True)
        for key in ("fullLevel", "entityTickingLevel", "maxDistance", "lastHolderLevel", "holderRadius"):
            value = self.sample()
            value["ticketGeometry"][key] += 1
            with self.subTest(key=key), self.assertRaises(AssertionError):
                require_geometry(value)

    def test_empty_tickets_only_allowed_after_release(self):
        value = self.sample()
        value["ticketGeometry"]["observedForcedLevels"] = []
        require_geometry(value)
        with self.assertRaises(AssertionError):
            require_geometry(value, forced=True)

    def test_missing_or_historical_assumptions_cannot_count_as_live_geometry(self):
        with self.assertRaises(AssertionError):
            require_geometry({})
        value = self.sample()
        value["ticketGeometry"]["observedForcedLevels"] = [30, 31]
        with self.assertRaises(AssertionError):
            require_geometry(value)


class HarvestTests(unittest.TestCase):
    def test_disposable_harvest_runtime_has_both_required_markers(self):
        with tempfile.TemporaryDirectory() as folder:
            mark_runtime(Path(folder))
            self.assertEqual({p.name for p in Path(folder).iterdir()},
                             {"advantage-native-harvest.allow", "advantage-works.allow"})

    def sample(self):
        rows = []
        for name, (level, allowed, silk, minimum, maximum, trials) in EXPECTED.items():
            rows.append(dict(id=name, status="PASS", historicalHarvestLevel=level, harvestAllowed=allowed,
                             silkTouch=silk, trials=trials, actualDropCounts=[minimum] * trials,
                             actualSolid=name.startswith(("sulfur/", "lithium/", "wire/")),
                             fullCollision=not name.startswith("wire/"),
                             connectionMask=int(name.split("/")[1]) if name.startswith(("wire/", "scaffold/")) else -1))
        rows.append(dict(id="solar/native-solidity", status="PASS", actualSolid=True))
        return dict(label="exercise", minecraft="1.15.2", status="PASS", checks=rows)

    def test_complete_native_survival_witness_set(self):
        require_checks(self.sample(), "exercise")
        self.assertEqual(len(EXPECTED), 270)

    def test_json_presence_or_pass_label_without_real_samples_is_not_enough(self):
        value = self.sample()
        value["checks"][0]["actualDropCounts"] = []
        with self.assertRaises(AssertionError):
            require_checks(value, "exercise")

    def test_failure_tool_drop_silk_drop_quantity_and_level_remain_strict(self):
        for name, field, bad in (("lithium/wood-fails", "harvestAllowed", True),
                                 ("lithium/stone", "historicalHarvestLevel", 0),
                                 ("sulfur/silk", "actualDropCounts", [2]),
                                 ("sulfur/wood", "actualDropCounts", [5] * 8),
                                 ("sulfur/hand-fails", "actualDropCounts", [1])):
            value = self.sample()
            next(row for row in value["checks"] if row["id"] == name)[field] = bad
            with self.subTest(name=name), self.assertRaises(AssertionError):
                require_checks(value, "exercise")

    def test_missing_or_duplicated_witnesses_fail(self):
        value = self.sample()
        value["checks"][-1] = copy.deepcopy(value["checks"][0])
        with self.assertRaises(AssertionError):
            require_checks(value, "exercise")

    def test_all_wire_scaffold_states_and_legacy_solar_solidness_are_strict(self):
        for name, field, bad in (("wire/63/hand", "connectionMask", 0),
                                 ("wire/0/hand", "actualSolid", False),
                                 ("wire/31/silk", "fullCollision", True),
                                 ("scaffold/31/silk", "actualSolid", True),
                                 ("scaffold/0/hand", "fullCollision", False),
                                 ("solar/native-solidity", "actualSolid", False)):
            value = self.sample()
            next(row for row in value["checks"] if row["id"] == name)[field] = bad
            with self.subTest(name=name), self.assertRaises(AssertionError):
                require_checks(value, "exercise")


class PortContracts(unittest.TestCase):
    def test_historical_inputs_remain_exact_except_audited_semantic_corrections(self):
        ledger = json.loads((RIG / "migration/native115-historical-inputs.json").read_text())
        self.assertEqual(ledger["sourceCommit"], ACCEPTED_POWER114)
        self.assertGreater(len(ledger["hashes"]), 100)
        approval = json.loads((RIG / "migration/audited-semantic-corrections.json").read_text())
        self.assertEqual(approval["id"], "EXTENDED-ABSENCE-001")
        records = approval["corrections"]
        self.assertIn("migration/extended_lifecycle.py", records)
        self.assertLessEqual(set(records), {"migration/extended_lifecycle.py", "migration/test_native115_geometry.py"})

        def verify(data, expected, record):
            self.assertEqual(record["historicalSha256"], expected)
            self.assertEqual(hashlib.sha256(data).hexdigest().upper(), record["correctedSha256"])
            tree = ast.parse(data.decode("utf-8-sig"))
            if record["kind"] == "semantic-oracle-correction":
                names = {"partial_result", "continuous_absence"}
                functions = {n.name: hashlib.sha256(ast.dump(n).encode()).hexdigest().upper()
                             for n in tree.body if isinstance(n, ast.FunctionDef) and n.name in names}
                self.assertEqual(functions, record["correctedFunctionAstSha256"])
                tree.body = [n for n in tree.body if not (isinstance(n, ast.FunctionDef) and n.name in names)]
            else:
                self.assertEqual(record["kind"], "historical-guard-reporting-correction")
                for node in tree.body:
                    if isinstance(node, ast.ClassDef) and node.name == "PortContracts":
                        node.body = [n for n in node.body if not (isinstance(n, ast.FunctionDef) and n.name in (
                            "test_historical_adapters_profiles_and_semantic_oracles_are_byte_identical",
                            "test_historical_inputs_remain_exact_except_audited_semantic_corrections"))]
                tree.body = [n for n in tree.body if not (isinstance(n, ast.Import)
                             and all(a.name in ("ast", "hashlib") for a in n.names))]
            self.assertEqual(hashlib.sha256(ast.dump(tree).encode()).hexdigest().upper(),
                             record["unchangedHistoricalAstSha256"])

        for relative, checksum in ledger["hashes"].items():
            with self.subTest(path=relative):
                if relative in records:
                    verify((RIG / relative).read_bytes(), checksum, records[relative])
                else:
                    self.assertEqual(digest(RIG / relative), checksum)

        # Updating only a byte hash must not bless a lower threshold or unrelated oracle edit.
        source = (RIG / "migration/extended_lifecycle.py").read_bytes()
        for old, new in ((b">= 40", b">= 39"),
                         (b"four consecutive moves were not witnessed", b"three consecutive moves were not witnessed")):
            changed = source.replace(old, new, 1)
            self.assertNotEqual(changed, source)
            record = copy.deepcopy(records["migration/extended_lifecycle.py"])
            record["correctedSha256"] = hashlib.sha256(changed).hexdigest().upper()
            with self.subTest(unapproved_change=new), self.assertRaises(AssertionError):
                verify(changed, ledger["hashes"]["migration/extended_lifecycle.py"], record)

    def test_all_thirteen_old_routes_and_comprehensive_native114_source_are_required(self):
        self.assertEqual(len(REQUIRED["frozen"]), 14)
        self.assertEqual(len(REQUIRED["connected"]), 10)
        document = dict(format=1, acceptedPower114=ACCEPTED_POWER114,
                        routes=[dict(kind=kind, id=name) for kind, names in REQUIRED.items() for name in names])
        require_routes(document)
        for kind in REQUIRED:
            value = copy.deepcopy(document)
            index = next(i for i, row in enumerate(value["routes"]) if row["kind"] == kind)
            value["routes"].pop(index)
            with self.subTest(kind=kind), self.assertRaises(AssertionError):
                require_routes(value)

    def test_accepted114_cannot_be_replaced_by_another_passing_legacy_case(self):
        document = dict(format=1, acceptedPower114=ACCEPTED_POWER114,
                        routes=[dict(kind=kind, id=name) for kind, names in REQUIRED.items() for name in names])
        victim = next(row for row in document["routes"] if row["kind"] == "connected" and row["id"].startswith("native114/"))
        victim["id"] = "legacy/original110"
        with self.assertRaises(AssertionError):
            require_routes(document)

    def test_freshly_fingerprinting_an_unverified_old_destination_is_not_approval(self):
        with self.assertRaisesRegex(AssertionError, "not approval"):
            require_source114_receipt(dict(id="native114/full", kind="frozen", sourceStatus="FRESH_HASH_ONLY"))


if __name__ == "__main__":
    unittest.main()
