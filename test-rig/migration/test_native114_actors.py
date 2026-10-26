import unittest
from pathlib import Path
import tempfile
from unittest.mock import Mock, patch

from native114_actors import baseline, expected_state, native_dimension_state, release


class NativeActorOracleTest(unittest.TestCase):
    def test_frozen_comparison_retains_positions_and_nested_items(self):
        old = {"file": {"Inventory": [{"Count": 17, "tag": {"nested": {"keep": 3}}}]},
               "entities": {"cart": {"Pos": [1, 2, 3], "Items": [{"Count": 4}]}}}
        result = expected_state(old, "actors")
        self.assertEqual(old, result)
        result["entities"]["cart"]["Items"][0]["Count"] = 0
        self.assertEqual(4, old["entities"]["cart"]["Items"][0]["Count"])

    def test_survival_only_removes_historical_movement_keys(self):
        old = {"file": {}, "entities": {"wolf": {"Pos": [1, 2, 3], "Motion": [0, 0, 0],
               "Rotation": [0, 0], "OwnerUUID": "owner", "ForgeData": {"keep": 8}}}}
        self.assertEqual({"OwnerUUID": "owner", "ForgeData": {"keep": 8}},
                         expected_state(old, "actors", survival=True)["entities"]["wolf"])

    def test_dimension_oracle_keeps_live_relationships_and_quantities(self):
        old = {"file": {"Inventory": [{"id": "steamadvantage:musket", "Count": 3}]},
               "entities": {"horse": {"Pos": [1, 2, 3], "Health": 20}},
               "relationships": {"horse": {"passengerUuids": ["wolf"]}}, "adapter": {"Dimension": -1}}
        result = expected_state(old, "dimensions", survival=True)
        self.assertEqual(old["relationships"], result["relationships"])
        self.assertEqual(old["file"], result["file"])

    def test_native_leash_byte_is_derived_only_from_actual_saved_compound(self):
        state = {"entities": {"horse": {"id": "minecraft:horse", "Leash": {"X": 1, "Y": 2, "Z": 3}},
                              "wolf": {"id": "minecraft:wolf"}}, "relationships": {"horse": {"leashHolderType": "minecraft:leash_knot"}}}
        result = native_dimension_state(state)
        self.assertEqual(1, result["entities"]["horse"]["Leashed"])
        self.assertEqual(0, result["entities"]["wolf"]["Leashed"])
        self.assertNotIn("Leashed", state["entities"]["horse"])
        del state["entities"]["horse"]["Leash"]
        self.assertEqual(0, native_dimension_state(state)["entities"]["horse"]["Leashed"])

    def test_dimension_release_never_saves_before_native_initialization(self):
        server = Mock()
        release(server, Path("unused"), "dimensions", "released")
        server.send.assert_called_once_with("advdimensions operate released")
        server.wait.assert_called_once_with("ADVDIMENSIONS COMPLETE released", 120)

    def test_actor_release_requests_real_tickets_not_synthetic_ticks(self):
        server = Mock()
        with patch("native114_actors.command", return_value="captured") as command:
            self.assertEqual("captured", release(server, Path("unused"), "actors", "released"))
            self.assertEqual("forceload add 96 -32 223 47", server.send.call_args_list[0].args[0])
            command.assert_called_once_with(server, Path("unused"), "actors", "operate", "released")

    def test_retained_baseline_selection_includes_player_layout_restart(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for selected in ("source", "restarted", "runtime-restart"):
                (root / ("semantic-" + selected + ".json")).write_text("{}")
                with patch("native114_actors.actors.capture", return_value={"retain": True}) as capture:
                    self.assertEqual(({"retain": True}, selected), baseline(root, "actors"))
                    capture.assert_called_once_with(root, selected)


if __name__ == "__main__":
    unittest.main()
