"""Negative controls for the repaired broad drill oracle, not Minecraft passes."""

from copy import deepcopy
import json
from pathlib import Path
import tempfile
import unittest

import nbtlib
import native115_broad as broad


class BroadOracleTests(unittest.TestCase):
    def setUp(self):
        definition = broad.read_json(broad.RIG / "src/main/resources/assets/advantageworkstest/works/works-1.13.2.json")
        self.stations = [s for s in definition["stations"] if s["id"] in broad.DRILL_BAYS]

    def snapshot(self, station, moved=False):
        rows = []
        for case in broad.drill_cases(station):
            position = case["rear"] if moved else case["origin"]
            marker = json.dumps(case["id"])
            data = ('{id:"' + case["block"] + '",x:' + str(position[0]) + ',y:' + str(position[1])
                    + ',z:' + str(position[2]) + ',ForgeData:{AdvBayWitness:' + marker + '},Energy:[I;0],'
                    + 'Items:[{Slot:0b,id:"minecraft:diamond",Count:7b,tag:{AdvBayStock:' + marker + '}}]}')
            row = dict(pos=list(position), block=case["block"], state='facing=' + case["facing"], nbt=data)
            rows.append(row)
            if not moved:
                rows.append(dict(pos=list(case["rear"]), block="steamadvantage:steam_track"
                                 if station["id"] == "S-05" else "electricadvantage:electric_track", state="track"))
            if moved:
                rows.append(dict(pos=list(case["origin"]), block="steamadvantage:steam_pipe"
                                 if station["id"] == "S-05" else "electricadvantage:electric_conduit", state="pipe"))
        return dict(snapshot=rows)

    def states(self, moved=False):
        states = {}
        for station in self.stations:
            states.update(broad.drill_state(station, self.snapshot(station, moved), initial=not moved))
        return states

    def test_all_twelve_initial_then_moved_then_restarted(self):
        initial, moved = self.states(), self.states(True)
        self.assertEqual(12, broad.operating_state(initial, moved)["moved"])
        self.assertTrue(broad.restarted_state(moved, deepcopy(moved))["exactEnergy"])

    def test_movement_cannot_replace_initial_placement_test(self):
        for station in self.stations:
            with self.assertRaisesRegex(AssertionError, "initial placement"):
                broad.drill_state(station, self.snapshot(station, True), initial=True)

    def test_no_movement_cannot_clear_the_original_failure(self):
        with self.assertRaisesRegex(AssertionError, "actual track movement"):
            broad.operating_state(self.states(), self.states())

    def test_initial_track_is_required(self):
        station = self.stations[0]
        doc = self.snapshot(station)
        doc["snapshot"][1]["block"] = "minecraft:air"
        with self.assertRaisesRegex(AssertionError, "initial declared rear track"):
            broad.drill_state(station, doc, initial=True)

    def test_live_steam_bit_may_replace_the_conduit_left_in_the_old_cell(self):
        station = self.stations[0]
        doc = self.snapshot(station, True)
        doc["snapshot"][1]["block"] = "steamadvantage:drillbit"
        self.assertTrue(broad.drill_state(station, doc)["S-05:down-facing"]["moved"])

    def test_missing_duplicate_wrong_facing_and_off_track_fail(self):
        station = self.stations[0]
        for mutation in ("missing", "duplicate", "facing", "off-track"):
            with self.subTest(mutation=mutation):
                doc = self.snapshot(station)
                if mutation == "missing":
                    doc["snapshot"].pop(0)
                elif mutation == "duplicate":
                    clone = deepcopy(doc["snapshot"][0])
                    clone["pos"] = list(broad.drill_cases(station)[0]["rear"])
                    doc["snapshot"].append(clone)
                elif mutation == "facing":
                    doc["snapshot"][0]["state"] = "facing=west"
                else:
                    doc["snapshot"][0]["pos"][0] += 1
                with self.assertRaises(AssertionError):
                    broad.drill_state(station, doc)

    def test_witness_and_inventory_and_coordinate_changes_fail(self):
        station = self.stations[0]
        for mutation in ("marker", "lost-stock", "duplicated-stock", "coordinates", "energy"):
            with self.subTest(mutation=mutation):
                doc = self.snapshot(station)
                data = nbtlib.parse_nbt(doc["snapshot"][0]["nbt"])
                if mutation == "marker":
                    data["ForgeData"]["AdvBayWitness"] = nbtlib.String("replacement")
                elif mutation == "lost-stock":
                    data["Items"][0]["Count"] = nbtlib.Byte(6)
                elif mutation == "duplicated-stock":
                    data["Items"][0]["Count"] = nbtlib.Byte(8)
                elif mutation == "coordinates":
                    data["x"] = nbtlib.Int(999)
                else:
                    data["Energy"] = nbtlib.IntArray([2139095040])
                doc["snapshot"][0]["nbt"] = data.snbt()
                with self.assertRaises(AssertionError):
                    broad.drill_state(station, doc)

    def test_stock_can_export_to_rear_chest_without_loss(self):
        station = self.stations[0]
        doc = self.snapshot(station, True)
        row = doc["snapshot"][0]
        data = nbtlib.parse_nbt(row["nbt"])
        stock = data.pop("Items")
        row["nbt"] = data.snbt()
        doc["snapshot"].append(dict(pos=[-61, 5, 70], block="minecraft:chest", state="chest",
                                    nbt=nbtlib.Compound({"Items": stock}).snbt()))
        self.assertEqual(7, broad.drill_state(station, doc)["S-05:down-facing"]["stock"])

    def test_energy_loss_during_restart_is_not_accepted(self):
        before = self.states(True)
        before["E-06:up-facing"]["energyBits"] = [1132068864]
        before["E-06:up-facing"]["energy"] = 250
        with self.assertRaisesRegex(AssertionError, "Restart changed"):
            broad.restarted_state(before, self.states(True))

    def test_report_cannot_drop_an_assertion_or_station(self):
        station = self.stations[0]
        record = dict(station=station["id"], minecraft="1.15.2", status="MANUAL_REQUIRED",
                      assertions=[dict(id=a["id"], status="PASS") for a in station["assertions"]],
                      manualChecks=station["manualChecks"])
        self.assertEqual(6, broad.assertions_state({station["id"]: record}, [station])["assertions"]["PASS"])
        with self.assertRaisesRegex(AssertionError, "omitted"):
            broad.assertions_state({}, [station])
        record["assertions"].pop()
        with self.assertRaisesRegex(AssertionError, "assertion set changed"):
            broad.assertions_state({station["id"]: record}, [station])

    def test_decoupled_modset_cannot_silently_contain_base_metals(self):
        expected = dict(minecraft="1.15.2", forge="31.2.57", poweradvantage="2.4.6.115021")
        broad.loaded_mods({"S-05": dict(mods=expected)}, expected)
        actual = dict(expected, basemetals="3.0.1.115021")
        with self.assertRaisesRegex(AssertionError, "loaded mod identities"):
            broad.loaded_mods({"S-05": dict(mods=actual)}, expected)

    def test_live_broad_staging_explicitly_disables_frozen_migration_mode(self):
        with tempfile.TemporaryDirectory() as directory:
            runtime = Path(directory)
            (runtime / "advantage-migration.allow").touch()
            broad.authorize_broad(runtime)
            self.assertTrue((runtime / "advantage-works.allow").is_file())
            self.assertTrue((runtime / "migration-operate.allow").is_file())

    def test_works_world_retains_original_no_structure_and_seed_settings(self):
        with tempfile.TemporaryDirectory() as directory:
            runtime = Path(directory)
            properties = runtime / "server.properties"
            properties.write_text("level-name=AdvantageWorksMigration\nserver-port=25584\ngenerate-structures=true\n")
            broad.configure_broad(runtime, 25588)
            result = dict(line.split("=", 1) for line in properties.read_text().splitlines())
            self.assertEqual("false", result["generate-structures"])
            self.assertEqual("advantage-works-1.15.2-v1", result["level-seed"])
            self.assertEqual("25588", result["server-port"])
            self.assertEqual("0", result["difficulty"])
            self.assertEqual("true", result["enable-command-block"])
            self.assertEqual("false", result["spawn-npcs"])
            self.assertEqual("AdvantageWorksMigration", result["level-name"])

    def test_required_integration_skip_is_explained_by_loaded_mods(self):
        station = dict(id="W-03", assertions=[dict(id="mineralogy", requiredMods=["mineralogy"])])
        record = dict(station="W-03", minecraft="1.15.2", status="MANUAL_REQUIRED", mods={}, manualChecks=["GUI"],
                      assertions=[dict(id="mineralogy", status="SKIP")])
        self.assertEqual({"SKIP": 1}, broad.assertions_state({"W-03": record}, [station])["assertions"])
        record["mods"]["mineralogy"] = "6.0.1.115021"
        with self.assertRaisesRegex(AssertionError, "unexplained skip"):
            broad.assertions_state({"W-03": record}, [station])

    def test_duplicate_assertions_cannot_be_counted_as_coverage(self):
        station = dict(id="W-03", assertions=[dict(id="one")])
        record = dict(station="W-03", minecraft="1.15.2", status="MANUAL_REQUIRED", manualChecks=["GUI"],
                      assertions=[dict(id="one", status="PASS"), dict(id="one", status="PASS")])
        with self.assertRaisesRegex(AssertionError, "assertion set changed"):
            broad.assertions_state({"W-03": record}, [station])


if __name__ == "__main__":
    unittest.main()
