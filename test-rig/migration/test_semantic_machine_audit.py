"""Deliberately corrupt evidence in ways the frozen operating oracle accepted."""

import copy
import json
import unittest
from unittest.mock import patch

from connected import fixture, operation as historical_operation
from semantic_machine_audit import BATCH_MACHINES, connected_interval, endurance_batch
from test_connected import nbt, sample, entries
import test_endurance


def pair():
    before = sample()
    before["gameTime"] = 100
    after = copy.deepcopy(before)
    after.update(gameTime=164, requestedTicks=64, advancedTicks=64)
    rows = entries(after)
    source = ','.join('{Slot:%db,id:"minecraft:dirt",Count:%db}' % (slot, 63 if slot == 26 else 64) for slot in range(27))
    nbt(rows["C-CONVEYOR-input"], '{Items:[' + source + ']}')
    nbt(rows["C-CONVEYOR-output"], '{Items:[{Slot:0b,id:"minecraft:dirt",Count:1b}]}')
    nbt(rows["C-STEAM-furnace"], '{Items:[{Slot:0b,id:"minecraft:coal",Count:32b},'
        '{Slot:1b,id:"minecraft:iron_ore",Count:63b},{Slot:2b,id:"minecraft:iron_ore",Count:64b},'
        '{Slot:3b,id:"minecraft:iron_ore",Count:64b},{Slot:4b,id:"minecraft:iron_ingot",Count:1b}]}')
    for key, raw, product in (("C-ELECTRIC-furnace", "iron_ore", "iron_ingot"), ("C-STEAM-crusher", "cobblestone", "gravel")):
        nbt(rows[key], '{Items:[{Slot:0b,id:"minecraft:%s",Count:63b},{Slot:1b,id:"minecraft:%s",Count:1b}]}' % (raw, product))
    for station, product, count in (("C-SDRILL", "gravel", 1), ("C-EDRILL", "cobblestone", 9)):
        targets = [row for row in after["snapshot"] if row["station"] == station and row["kind"].startswith("target-")]
        for row in targets[:count]: row["actualName"] = "minecraft:air"
        nbt(rows[station + "-drill"], '{Items:[{Slot:0b,id:"minecraft:%s",Count:%db}]}' % (product, count))
    return before, after


class SemanticMachineAuditTest(unittest.TestCase):
    def check(self, before, after):
        return connected_interval(before, after, fixture("test"), 64)

    def assertPreviouslyAccepted(self, before, after):
        self.assertEqual("PASS", historical_operation(before, after, 64)["status"])
        self.assertEqual("FAIL", self.check(before, after)["status"])

    def test_real_material_transformation_and_finite_fixture_pass(self):
        before, after = pair()
        self.assertEqual("PASS", self.check(before, after)["status"])

    def test_unexpected_items_no_longer_hide_behind_expected_counts(self):
        before, after = pair()
        nbt(entries(after)["C-CONVEYOR-output"], '{Items:[{Slot:0b,id:"minecraft:dirt",Count:1b},{Slot:1b,id:"minecraft:diamond",Count:1b}]}')
        self.assertPreviouslyAccepted(before, after)

    def test_replacement_is_not_mining(self):
        before, after = pair()
        target = next(row for row in after["snapshot"] if row["station"] == "C-EDRILL" and row["actualName"] == "minecraft:air")
        target["actualName"] = "minecraft:diamond_block"
        self.assertPreviouslyAccepted(before, after)

    def test_negative_water_is_not_consumed_water(self):
        before, after = pair()
        nbt(entries(after)["C-STEAM-water-0"], '{Tank:{FluidName:"water",Amount:-1}}')
        self.assertPreviouslyAccepted(before, after)

    def test_battery_charge_is_bounded_and_required(self):
        for charge in (-1, 8001):
            with self.subTest(charge=charge):
                before, after = pair()
                nbt(entries(after)["C-ELECTRIC-battery"], '{Items:[{Slot:0b,id:"electricadvantage:lithium_battery",Count:1b,tag:{energy:%df}}]}' % charge)
                self.assertPreviouslyAccepted(before, after)

    def test_self_reported_tick_count_cannot_replace_advancing_world_time(self):
        before, after = pair()
        after["gameTime"] = before["gameTime"]
        self.assertPreviouslyAccepted(before, after)

    def test_invalid_start_state_cannot_be_ignored(self):
        before, after = pair()
        nbt(entries(before)["C-ELECTRIC-furnace"], '{Items:[{Slot:0b,id:"minecraft:iron_ore",Count:63b}]}')
        self.assertPreviouslyAccepted(before, after)

    def test_counter_only_drill_progress_is_incomplete_not_completed_work(self):
        before, after = pair()
        for row in after["snapshot"]:
            if row["station"] == "C-EDRILL" and row["kind"].startswith("target-"): row["actualName"] = row["name"]
        nbt(entries(after)["C-EDRILL-drill"], '{progress:10s,Items:[]}')
        self.assertEqual("PASS", historical_operation(before, after, 64)["status"])
        self.assertEqual("INCOMPLETE", self.check(before, after)["status"])

    def test_no_work_wrong_yield_and_missing_witness_fail(self):
        for mutation in ("idle", "lost", "missing", "duplicate", "fuel", "zero-ticks"):
            with self.subTest(mutation=mutation):
                before, after = pair()
                if mutation == "idle":
                    after = copy.deepcopy(before)
                    after.update(gameTime=164, requestedTicks=64, advancedTicks=64)
                elif mutation == "lost": nbt(entries(after)["C-ELECTRIC-furnace"], '{Items:[]}')
                elif mutation == "missing": after["snapshot"].pop()
                elif mutation == "duplicate": after["snapshot"].append(copy.deepcopy(after["snapshot"][0]))
                elif mutation == "fuel": nbt(entries(after)["C-STEAM-boiler-0"], '{Items:[{Slot:0b,id:"minecraft:coal",Count:33b}]}')
                else: after["advancedTicks"] = 0
                self.assertEqual("FAIL", self.check(before, after)["status"])


class IndependentEnduranceTest(unittest.TestCase):
    def setUp(self):
        fixture_test = test_endurance.EnduranceOracleTests()
        fixture_test.setUp()
        self.rig = fixture_test.rig
        # Complete the legacy self-test's deliberately partial fake-world data.
        for pos in ((4, 1, 8), (6, 1, 8)):
            self.rig.entry("W-03", pos)["data"]["Tank"] = {"Empty": 1}
        self.rig.entry("W-03", (6, 1, 8))["data"]["InputTank"] = {"Empty": 1}
        for z, ghosts in ((9, [(1, "dirt"), (2, "cobblestone"), (3, "coal")]), (15, []), (18, [(1, "dirt")])):
            self.rig.entry("W-04", (3, 1, z))["data"]["Items"] = [
                {"Slot": slot, "id": "minecraft:" + name, "Count": 1} for slot, name in ghosts]

    def documents(self):
        docs = {}
        for station in ("S-04", "E-03", "W-04", "W-03"):
            rows = []
            for pos, entry in self.rig.snapshots[station].items():
                block = next((block for s, p, _, block, _, _ in BATCH_MACHINES if s == station and p == pos), "minecraft:chest")
                rows.append(dict(pos=pos, block=block, nbt=json.dumps(entry["data"])))
            docs[station] = dict(station=station, snapshot=rows)
        return docs

    def strict(self):
        return endurance_batch(self.documents(), self.rig.definitions)

    def test_complete_measured_batch_passes(self):
        self.assertEqual("PASS", self.strict()["status"])

    def test_extra_hidden_filter_items_are_not_ignored(self):
        self.rig.entry("W-04", (3, 1, 18))["data"]["Items"].append({"Slot": 4, "id": "minecraft:diamond", "Count": 1})
        with patch("endurance.write_json"): self.rig.batch("test")
        self.assertEqual("FAIL", self.strict()["status"])

    def test_lost_reference_filter_does_not_pass_as_throughput(self):
        self.rig.entry("W-04", (3, 1, 9))["data"]["Items"] = []
        with patch("endurance.write_json"): self.rig.batch("test")
        self.assertEqual("FAIL", self.strict()["status"])

    def test_missing_input_tank_is_not_an_empty_input_tank(self):
        del self.rig.entry("W-03", (6, 1, 8))["data"]["InputTank"]
        with patch("endurance.write_json"): self.rig.batch("test")
        self.assertEqual("FAIL", self.strict()["status"])

    def test_machine_identity_is_required_not_only_seeded_output(self):
        docs = self.documents()
        docs["E-03"]["snapshot"][0]["block"] = "minecraft:chest"
        with patch("endurance.write_json"): self.rig.batch("test")
        self.assertEqual("FAIL", endurance_batch(docs, self.rig.definitions)["status"])


if __name__ == "__main__": unittest.main()
