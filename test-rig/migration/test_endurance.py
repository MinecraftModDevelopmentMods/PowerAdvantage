"""Negative controls for the endurance oracle; no server or world writes."""

from pathlib import Path
import unittest
import tempfile
from unittest.mock import patch

from endurance import CHECKED, PROCESSING, Rig, parse_snapshot
from audit_endurance import audit, expected_inventory_acknowledgements
from matrix import write_json


class EnduranceOracleTests(unittest.TestCase):
    def test_native_feed_acknowledgement_totals(self):
        self.assertEqual(expected_inventory_acknowledgements(1), (448, 439))
        self.assertEqual(expected_inventory_acknowledgements(30), (13179, 439))

    def test_audit_rejects_old_nbt_fed_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_json(root / "endurance-results.json", {"status": "PASS", "duration_seconds": 1800,
                                                       "interval_seconds": 60})
            with self.assertRaisesRegex(AssertionError, "diagnostics only"): audit(root)

    def setUp(self):
        definitions = {name: {"origin": [0, 0, 0]} for name in CHECKED}
        self.rig = Rig("test", Path("unused"), None, None, definitions)
        self.rig.snapshots = {name: {} for name in CHECKED}
        self.rig.documents = {"W-01": {"worldTime": 1200}}
        self.rig.invariants = lambda: None
        for _, station, pos, neighbours, _, output, _, count in PROCESSING:
            self.put(station, pos, [("minecraft:" + output, count)])
            for neighbour in neighbours: self.put(station, neighbour)
        for x in (2, 3, 4): self.put("W-04", (x, 1, 6))
        self.put("W-04", (5, 1, 6), [("minecraft:dirt", 16)])
        for z in (9, 15, 18):
            self.put("W-04", (2, 1, z))
            self.put("W-04", (3, 1, z))
            forward = [("minecraft:apple", 8)]
            if z == 15: forward.append(("minecraft:dirt", 8))
            self.put("W-04", (4, 1, z), forward)
            self.put("W-04", (3, 0, z), [] if z == 15 else [("minecraft:dirt", 8)])
        self.put("W-03", (4, 1, 8))
        self.put("W-03", (6, 1, 8))
        self.put("W-03", (8, 1, 8))
        self.rig.entry("W-03", (8, 1, 8))["data"]["Tank"] = {
            "FluidName": "poweradvantage:refined_oil", "Amount": 1000}

    def put(self, station, pos, items=()):
        self.rig.snapshots[station][pos] = {"pos": pos, "data": {"Items": [
            {"Slot": slot, "id": name, "Count": count} for slot, (name, count) in enumerate(items)]}}

    def check(self):
        with patch("endurance.write_json"):
            return self.rig.batch("test")

    def test_valid_complete_batch(self):
        self.assertEqual(self.check()["outputs"]["distillation-mB"], 1000)

    def test_missing_item_fails(self):
        self.put("E-03", (6, 1, 6), [("minecraft:iron_ingot", 3)])
        with self.assertRaisesRegex(AssertionError, "incorrect yield"): self.check()

    def test_duplicated_item_fails(self):
        self.put("S-04", (3, 1, 13), [("minecraft:gravel", 5)])
        with self.assertRaisesRegex(AssertionError, "incorrect yield"): self.check()

    def test_unexpected_output_fails(self):
        self.put("E-03", (3, 1, 6), [("minecraft:gravel", 4), ("minecraft:diamond", 1)])
        with self.assertRaisesRegex(AssertionError, "unexpected items"): self.check()

    def test_refined_oil_in_shared_supply_tank_is_counted(self):
        tank = self.rig.entry("W-03", (8, 1, 8))["data"].pop("Tank")
        self.rig.entry("W-03", (4, 1, 8))["data"]["Tank"] = tank
        self.assertEqual(self.check()["outputs"]["distillation-mB"], 1000)

    def test_unprocessed_input_fails(self):
        self.put("E-03", (3, 1, 6), [("minecraft:cobblestone", 1), ("minecraft:gravel", 4)])
        with self.assertRaisesRegex(AssertionError, "input remains"): self.check()

    def test_wrong_filter_branch_fails(self):
        self.put("W-04", (4, 1, 18), [("minecraft:dirt", 8)])
        self.put("W-04", (3, 0, 18), [("minecraft:apple", 8)])
        with self.assertRaisesRegex(AssertionError, "filter 18 forward"): self.check()

    def test_conveyor_duplication_fails(self):
        self.put("W-04", (3, 1, 6), [("minecraft:dirt", 1)])
        with self.assertRaisesRegex(AssertionError, "conveyor conservation"): self.check()

    def test_fluid_loss_fails(self):
        self.rig.entry("W-03", (8, 1, 8))["data"]["Tank"]["Amount"] = 999
        with self.assertRaisesRegex(AssertionError, "oil output"): self.check()

    def test_wrong_fluid_identity_fails(self):
        self.rig.entry("W-03", (8, 1, 8))["data"]["Tank"]["FluidName"] = "minecraft:water"
        with self.assertRaisesRegex(AssertionError, "wrong oil output identity"): self.check()

    def test_pending_oil_fails(self):
        self.rig.entry("W-03", (6, 1, 8))["data"]["InputTank"] = {"Amount": 1, "FluidName": "poweradvantage:crude_oil"}
        with self.assertRaisesRegex(AssertionError, "oil input remaining"): self.check()

    def test_snbt_keeps_quantities_and_float_bits(self):
        snapshot = parse_snapshot({"snapshot": [{"pos": [1, 2, 3], "nbt":
            '{Items:[{Slot:0b,id:"minecraft:dirt",Count:16b}],Energy:[I;1065353216]}'}]})
        self.assertEqual(snapshot[(1, 2, 3)]["data"]["Energy"], [1065353216])
        self.assertEqual(snapshot[(1, 2, 3)]["data"]["Items"][0]["Count"], 16)

    def invariant_fixture(self):
        for station, pos, amount in (("W-01", (13, 1, 7), 2000), ("S-06", (20, 5, 20), 1000),
                                     ("P-API-01", (8, 1, 7), 4000)):
            self.put(station, pos)
            self.rig.entry(station, pos)["data"]["Tank"] = {"FluidName": "minecraft:water", "Amount": amount}
        self.put("S-01", (12, 1, 7))
        self.rig.entry("S-01", (12, 1, 7))["data"]["Temperature"] = 900
        self.put("E-02", (3, 1, 12), [("electricadvantage:lead_acid_battery", 1)])
        self.rig.static_batteries = self.rig.entry("E-02", (3, 1, 12))["data"]["Items"]
        self.put("E-01", (4, 4, 12), [("electricadvantage:lead_acid_battery", 1)])
        self.rig.entry("E-01", (4, 4, 12))["data"]["Items"][0]["tag"] = {"energy": 100}

    def test_closed_tank_loss_fails(self):
        self.invariant_fixture()
        self.rig.entry("S-06", (20, 5, 20))["data"]["Tank"]["Amount"] = 999
        with self.assertRaisesRegex(AssertionError, "closed water witness"): Rig.invariants(self.rig)

    def test_saved_battery_item_loss_fails(self):
        self.invariant_fixture()
        self.put("E-02", (3, 1, 12))
        with self.assertRaisesRegex(AssertionError, "stored battery"): Rig.invariants(self.rig)

    def test_negative_energy_fails(self):
        self.invariant_fixture()
        self.rig.entry("E-03", (6, 1, 6))["data"]["Energy"] = [-1082130432]
        with self.assertRaisesRegex(AssertionError, "invalid energy"): Rig.invariants(self.rig)

    def test_battery_overcharge_fails(self):
        self.invariant_fixture()
        self.rig.entry("E-01", (4, 4, 12))["data"]["Items"][0]["tag"]["energy"] = 1001
        with self.assertRaisesRegex(AssertionError, "out-of-range battery"): Rig.invariants(self.rig)

    def test_inventory_rearming_uses_inventory_commands_not_nbt(self):
        self.rig.entry("E-03", (3, 1, 6))["block"] = "electricadvantage:electric_crusher"
        commands = []
        self.rig.command = lambda command, response=None: commands.append(command)
        self.rig.inventory("E-03", (3, 1, 6), [(0, "minecraft:cobblestone", 4)])
        self.assertEqual(len(commands), 9)
        self.assertTrue(all(c.startswith("replaceitem block ") for c in commands))
        self.assertIn("container.0 minecraft:cobblestone 4", commands[0])
        self.assertTrue(all("minecraft:air 1" in c for c in commands[1:]))


if __name__ == "__main__": unittest.main()
