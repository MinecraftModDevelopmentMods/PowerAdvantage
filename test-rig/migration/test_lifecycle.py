"""Negative controls for the lifecycle oracle."""
import unittest
from lifecycle import LifecycleRun, changed_target_results, fixtures, move_result, progress_results, require_unloaded


class LifecycleOracleTests(unittest.TestCase):
    def test_all_facings_and_chunk_crossings(self):
        cases = [f for f in fixtures() if "from" in f]
        self.assertEqual(len(cases), 12)
        for f in cases:
            self.assertLessEqual(f["initialEnergy"], 50 if f["energyType"] == "steam" else 500)
            self.assertNotEqual((f["from"][0] >> 4, f["from"][2] >> 4), (f["to"][0] >> 4, f["to"][2] >> 4))
            for p in f["witnesses"]:
                self.assertTrue(all(low <= a <= high for a, low, high in zip(p, f["min"], f["max"])))

    def test_fake_unload_fails(self):
        before = {"chunks": [{"pos": [1, 2], "loaded": True, "unloads": 0}]}
        for loaded, count in ((True, 1), (False, 0)):
            with self.assertRaises(AssertionError):
                require_unloaded(before, {"chunks": [{"pos": [1, 2], "loaded": loaded, "unloads": count}]})
        require_unloaded(before, {"chunks": [{"pos": [1, 2], "loaded": False, "unloads": 1}]})

    def movement(self):
        fixture = next(f for f in fixtures() if f["id"] == "E-MOVE-east")
        nbt = '{x:%d,y:%d,z:%d,Items:[{Slot:0b,id:"minecraft:dirt",Count:7b},{Slot:1b,id:"minecraft:apple",Count:13b}]}'
        before = {"nbt": nbt % tuple(fixture["from"]), "energy": {"electricity": 400}}
        after = {"pos": fixture["to"], "state": "electric_drill[facing=east]", "nbt": nbt % tuple(fixture["to"]),
                 "energy": {"electricity": 150}}
        return fixture, {"before": before, "after": after, "oldPosition": {"block": "electricadvantage:electric_conduit"}}

    def test_valid_move(self):
        fixture, move = self.movement()
        self.assertEqual(move_result(fixture, move)["status"], "PASS")

    def test_unpaid_energy_detected(self):
        fixture, move = self.movement(); move["after"]["energy"]["electricity"] = 400
        self.assertEqual(move_result(fixture, move)["status"], "FAIL")

    def test_inventory_loss_detected(self):
        fixture, move = self.movement(); move["after"]["nbt"] = move["after"]["nbt"].replace("Count:7b", "Count:6b")
        self.assertEqual(move_result(fixture, move)["status"], "FAIL")

    def test_wrong_position_and_facing_detected(self):
        fixture, move = self.movement(); move["after"]["state"] = "electric_drill[facing=up]"
        move["after"]["nbt"] = move["after"]["nbt"].replace("x:" + str(fixture["to"][0]), "x:0")
        self.assertEqual(len(move_result(fixture, move)["failures"]), 2)

    def test_midblock_progress_reset_detected(self):
        fixture, _ = self.movement()
        before = {"cases": {fixture["id"]: [{"pos": fixture["to"], "nbt": "{progress:80s,targetX:3,targetY:4,targetZ:5}"}]}}
        after = {"cases": {fixture["id"]: [{"pos": fixture["to"], "nbt": "{progress:5s,targetX:3,targetY:4,targetZ:5}"}]}}
        self.assertEqual(progress_results([fixture], before, after)[fixture["id"]]["status"], "FAIL")
        after["cases"][fixture["id"]][0]["nbt"] = "{progress:81s,targetX:3,targetY:4,targetZ:5}"
        self.assertEqual(progress_results([fixture], before, after)[fixture["id"]]["status"], "PASS")

    def mining_snapshot(self):
        fixture = fixtures()[0]
        items = ('[{Slot:0b,id:"minecraft:dirt",Count:7b,tag:{AdvWorksWitness:"' + fixture["id"]
                 + '"}},{Slot:1b,id:"minecraft:apple",Count:13b},{Slot:2b,id:"minecraft:gravel",Count:1b}]')
        drill = {"pos": fixture["to"], "state": "steam_drill[facing=down]", "energy": {"steam": 40}, "nbt": "{Items:[]}"}
        chest = {"pos": fixture["rear"], "nbt": "{Items:" + items + "}"}
        return fixture, {"cases": {fixture["id"]: [drill, chest]}}

    def test_changed_target_must_clear_progress_and_coordinates(self):
        fixture, _ = self.movement()
        before = {"cases": {fixture["id"]: [{"pos": fixture["to"], "nbt": "{progress:80s,targetX:3,targetY:4,targetZ:5}"}]}}
        after = {"cases": {fixture["id"]: [{"pos": fixture["to"], "nbt": "{progress:0s}"}]}}
        self.assertEqual(changed_target_results([fixture], before, after)[fixture["id"]]["status"], "PASS")
        for nbt in ("{progress:80s}", "{progress:0s,targetX:3,targetY:4,targetZ:5}"):
            after["cases"][fixture["id"]][0]["nbt"] = nbt
            self.assertEqual(changed_target_results([fixture], before, after)[fixture["id"]]["status"], "FAIL")

    def test_rear_output_passes_with_all_quantities_and_tags(self):
        fixture, snapshot = self.mining_snapshot()
        runner = LifecycleRun(None, None, None, [fixture])
        self.assertEqual(runner.mining_results(snapshot)[fixture["id"]]["status"], "PASS")

    def test_items_retained_in_drill_do_not_pass_rear_output(self):
        fixture, snapshot = self.mining_snapshot()
        drill, chest = snapshot["cases"][fixture["id"]]
        drill["nbt"], chest["nbt"] = chest["nbt"], drill["nbt"]
        runner = LifecycleRun(None, None, None, [fixture])
        self.assertEqual(runner.mining_results(snapshot)[fixture["id"]]["failures"], ["not all output reached rear chest"])


if __name__ == "__main__": unittest.main()
