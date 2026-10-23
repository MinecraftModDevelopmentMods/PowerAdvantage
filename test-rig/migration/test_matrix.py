"""The state oracle must detect loss rather than bless it as flattening."""

import unittest
from matrix import differences, normalized


class ComparisonTest(unittest.TestCase):
    def test_nested_inventory_loss_and_duplication_fail(self):
        before = {"Items": [{"id": "steamadvantage:musket", "Count": 1, "tag": {"Damage": 5}}]}
        self.assertTrue(differences(before, {"Items": []}))
        self.assertTrue(differences(before, {"Items": before["Items"] * 2}))
        self.assertTrue(differences(before, {"Items": [{"id": "minecraft:air", "Count": 1}]}))
        self.assertTrue(differences(before, {"Items": [{"id": "steamadvantage:musket", "Count": 2}]}))

    def test_fluid_energy_progress_and_temperature_loss_fail(self):
        for field, value in (("Tank", {"FluidName": "water", "Amount": 750}),
                             ("Energy", [1120403456]), ("progress", 7), ("Temperature", 37.5)):
            self.assertTrue(differences({field: value}, {}), field)
            self.assertTrue(differences({field: value}, {field: 0}), field)

    def test_legacy_enchantments_use_world_registry_not_vanilla_numbers(self):
        old = {"id": "steamadvantage:musket", "Count": 1, "Damage": 5,
               "tag": {"ench": [{"id": 11, "lvl": 2}]}}
        modern = {"id": "steamadvantage:musket", "Count": 1,
                  "tag": {"Damage": 5, "Enchantments": [{"id": "steamadvantage:spreadshot", "lvl": 2}]}}
        self.assertEqual(normalized(old, {11: "steamadvantage:spreadshot"}), modern)
        self.assertTrue(differences(normalized(old, {11: "steamadvantage:spreadshot"}),
                                    normalized(old, {11: "minecraft:binding_curse"})))

    def test_only_complete_registered_oil_buckets_are_normalized(self):
        old = {"id": "forge:bucketfilled", "Count": 1, "Damage": 0,
               "tag": {"FluidName": "crude_oil", "Amount": 1000, "Witness": "keep"}}
        expected = {"id": "poweradvantage:crude_oil_bucket", "Count": 1, "tag": {"Witness": "keep"}}
        self.assertEqual(normalized(old), expected)
        old["tag"]["Amount"] = 750
        self.assertTrue(differences(normalized(old), expected))

    def test_tile_aliases_and_empty_marker_do_not_hide_state_loss(self):
        self.assertEqual(normalized({"id": "minecraft:steamadvantage.steam_pump"})["id"], "steamadvantage:steam_pump")
        self.assertEqual(normalized({"id": "electricadvantage:l_e_d"})["id"], "electricadvantage:led")
        self.assertEqual(normalized({"Tank": {"Empty": 0}}), {"Tank": {"Empty": True}})
        self.assertTrue(differences(normalized({"Tank": {"FluidName": "water", "Amount": 750}}),
                                    normalized({"Tank": {"Empty": 1}})))


if __name__ == "__main__":
    unittest.main()
