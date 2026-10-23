from collections import Counter
import copy
import unittest
from unittest.mock import patch

from respawn import stacks, respawn_verdict


class RespawnItemOracleTests(unittest.TestCase):
    def test_slots_do_not_change_drop_identity(self):
        item = {"id": "electricadvantage:lithium_battery", "Count": 3, "Slot": 2, "tag": {"energy": 123.0}}
        drop = {key: value for key, value in item.items() if key != "Slot"}
        self.assertEqual(stacks([item]), stacks([drop]))

    def test_combined_stacks_conserve_quantity(self):
        self.assertEqual(stacks([{"id": "minecraft:dirt", "Count": 4}]),
                         stacks([{"id": "minecraft:dirt", "Count": 1}, {"id": "minecraft:dirt", "Count": 3}]))

    def test_battery_discharge_is_not_a_valid_drop(self):
        self.assertNotEqual(stacks([{"id": "electricadvantage:lithium_battery", "Count": 1, "tag": {"energy": 123}}]),
                            stacks([{"id": "electricadvantage:lithium_battery", "Count": 1, "tag": {"energy": 0}}]))

    def test_quantity_loss_and_duplication_fail(self):
        before = stacks([{"id": "minecraft:dirt", "Count": 3}])
        self.assertNotEqual(before, stacks([{"id": "minecraft:dirt", "Count": 2}]))
        self.assertNotEqual(before, stacks([{"id": "minecraft:dirt", "Count": 4}]))
        self.assertNotEqual(before, Counter())


class RespawnOracleTests(unittest.TestCase):
    def setUp(self):
        before = {"Inventory": [{"id": "electricadvantage:lithium_battery", "Count": 3, "Slot": 0, "tag": {"energy": 123.0}}],
                  "EnderItems": [{"id": "steamadvantage:musket", "Count": 1, "Slot": 0}], "Dimension": -1,
                  "Pos": [144.5, 100, 8.5], "Health": 17.0, "UUIDMost": 1, "UUIDLeast": 2,
                  "SpawnX": 160, "SpawnY": 100, "SpawnZ": 24, "SpawnForced": 1,
                  "XpLevel": 23, "XpTotal": 751}
        dead = copy.deepcopy(before)
        dead.update(Health=0.0)
        after = copy.deepcopy(before)
        after.update(Dimension=0, Health=20.0, Pos=[160.5, 100, 24.5])
        self.record = {"dimension": -1, "respawn": {"before": before, "afterDeath": dead, "after": after,
                       "keepInventory": True, "bedMode": "forced", "fromDimension": -1, "toDimension": 0,
                       "handler": "NetHandlerPlayServer.PERFORM_RESPAWN",
                       "drops": [], "scope": "server-only-dummy-network"}}

    def run_oracle(self):
        with patch("respawn.parse_nbt", side_effect=lambda value: value):
            return respawn_verdict(self.record, {}, {"position": [0, 64, 0], "radius": 10})

    def test_kept_inventory(self):
        self.assertEqual("PASS", self.run_oracle()["status"])

    def test_inventory_loss(self):
        self.record["respawn"]["after"]["Inventory"] = []
        self.assertEqual("FAIL", self.run_oracle()["status"])

    def test_extra_battery_payload_fails_exact_item_check(self):
        self.record["respawn"]["after"]["Inventory"][0]["tag"]["extra"] = 999
        self.assertEqual("FAIL", self.run_oracle()["status"])

    def test_missing_bed_must_use_native_world_spawn_area(self):
        data = self.record["respawn"]
        data["bedMode"] = "missing"
        data["after"]["Pos"] = [0.5, 64, 0.5]
        self.assertEqual("PASS", self.run_oracle()["status"])
        data["after"]["Pos"] = [999, 64, 999]
        self.assertEqual("FAIL", self.run_oracle()["status"])

    def test_uuid_replacement(self):
        self.record["respawn"]["after"]["UUIDMost"] = 3
        self.assertEqual("FAIL", self.run_oracle()["status"])

    def test_ender_items_lost(self):
        self.record["respawn"]["after"]["EnderItems"] = []
        self.assertEqual("FAIL", self.run_oracle()["status"])

    def test_false_death_evidence(self):
        self.record["respawn"]["afterDeath"]["Health"] = 17.0
        self.assertEqual("FAIL", self.run_oracle()["status"])

    def test_wrong_dimension(self):
        self.record["respawn"]["after"]["Dimension"] = -1
        self.assertEqual("FAIL", self.run_oracle()["status"])

    def test_saved_spawn_is_not_only_a_position_check(self):
        self.record["respawn"]["after"].pop("SpawnX")
        result = self.run_oracle()
        self.assertEqual("FAIL", result["status"])
        self.assertTrue(any(issue["path"] == "saved-spawn.SpawnX" for issue in result["differences"]))

    def test_bed_failure_is_not_accepted_as_normal_respawn(self):
        self.record["respawn"]["after"]["Pos"] = [0, 64, 0]
        self.assertEqual("FAIL", self.run_oracle()["status"])

    def test_direct_recreation_cannot_claim_native_handler_coverage(self):
        self.record["respawn"].pop("handler")
        self.assertEqual("FAIL", self.run_oracle()["status"])

    def test_drop_items_conserved(self):
        data = self.record["respawn"]
        data["keepInventory"] = False
        data["afterDeath"]["Inventory"] = []
        data["after"].update(Inventory=[], XpLevel=0, XpTotal=0)
        item = copy.deepcopy(data["before"]["Inventory"][0])
        item.pop("Slot")
        data["drops"] = [{"Item": item}]
        self.assertEqual("PASS", self.run_oracle()["status"])
        data["drops"][0]["Item"]["Count"] = 4
        self.assertEqual("FAIL", self.run_oracle()["status"])


if __name__ == "__main__":
    unittest.main()
