import unittest

from native115_player_layout import verdict


class NativeLayoutOracleTest(unittest.TestCase):
    def test_scope_is_exact_player_data_not_unparented_fixture_turbine_operation(self):
        player = {"Inventory": [{"id": "steamadvantage:musket", "Count": 1, "Slot": 35,
                   "tag": {"Nested": {"Items": [{"id": "electricadvantage:lithium_battery", "Count": 3, "tag": {"energy": 123}}]}}}],
                  "EnderItems": [{"id": "poweradvantage:crude_oil_bucket", "Count": 1, "Slot": 26}]}
        expected = {"file": player, "adapter": player, "entities": {"unparented-turbine": {"id": "electricadvantage:hydroturbine"}}}
        self.assertEqual("PASS", verdict(expected, {"file": player, "adapter": player})["status"])

    def test_extra_nested_item_tag_is_rejected(self):
        expected = {"file": {"Inventory": [{"id": "x:y", "Count": 3, "tag": {"energy": 123}}]}, "adapter": {}}
        actual = {"file": {"Inventory": [{"id": "x:y", "Count": 3, "tag": {"energy": 123, "extra": "changed"}}]}, "adapter": {}}
        self.assertEqual("FAIL", verdict(expected, actual)["status"])


if __name__ == "__main__":
    unittest.main()
