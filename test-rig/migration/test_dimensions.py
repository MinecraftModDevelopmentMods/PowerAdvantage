import copy
import unittest

from dimensions import WITNESSES, canonical_entities, entity_state, runtime_errors, strict_expected, survival_expected, tick_result, verdict


class DimensionOracleTests(unittest.TestCase):
    def test_runtime_failures_cannot_hide_behind_clean_exit(self):
        content = '[ERROR] Exception stopping the server\n[INFO] Stopping server\n[ERROR] "Silently" catching entity tracking error.'
        self.assertEqual(2, len(runtime_errors(content)))

    def test_known_external_diagnostics_are_not_relabelled_crashes(self):
        self.assertEqual([], runtime_errors('[ERROR] basemetals_forge25_leaves_fixer\n[ERROR] Failed to delete temporary mod-pack directory'))

    def setUp(self):
        self.state = {"dimension": -1, "playerWorldDimension": -1,
                      "file": {"Dimension": -1, "SpawnDimension": 0, "Inventory": [{"id": "steamadvantage:musket", "Count": 1}]},
                      "adapter": {"Dimension": -1},
                      "entities": {"boat": {"id": "minecraft:boat", "UUIDMost": 1, "UUIDLeast": 2},
                                   "horse": {"id": "minecraft:horse", "UUIDMost": 3, "UUIDLeast": 4,
                                             "Leash": {"X": 165, "Y": 100, "Z": 8}},
                                   "wolf": {"id": "minecraft:wolf", "OwnerUUID": "owner", "UUIDMost": 5}},
                      "relationships": {"wolf": {"vehicleUuid": "boat-uuid", "passengerUuids": [], "leashHolderUuid": None}}}

    def changed(self, edit):
        other = copy.deepcopy(self.state)
        edit(other)
        self.assertEqual("FAIL", verdict(self.state, other)["status"])

    def test_identical(self):
        self.assertEqual("PASS", verdict(self.state, self.state)["status"])

    def test_dimension_reset(self):
        self.changed(lambda s: s["file"].update(Dimension=0))

    def test_wrong_live_world(self):
        self.changed(lambda s: s.update(playerWorldDimension=0))

    def test_passenger_detachment(self):
        self.changed(lambda s: s["relationships"]["wolf"].update(vehicleUuid=None))

    def test_leash_loss(self):
        self.changed(lambda s: s["entities"]["horse"].pop("Leash"))

    def test_inventory_loss(self):
        self.changed(lambda s: s["file"].update(Inventory=[]))

    def test_additional_item_tag_is_not_an_exact_item_match(self):
        self.changed(lambda s: s["file"]["Inventory"][0].update(tag={"UnexpectedCapability": 999}))

    def test_unknown_turbine_like_id_is_not_an_alias(self):
        self.assertEqual("foreign:hydroturbine_fake", canonical_entities({"id": "foreign:hydroturbine_fake"})["id"])
        self.assertEqual("electricadvantage:hydroturbine",
                         canonical_entities({"id": "electricadvantage:electricadvantage.Hydroturbine"})["id"])

    def test_every_persistent_witness_needs_tick_evidence(self):
        before = {"gameTime": 0, "entities": [{"witness": "horse", "ticksExisted": 0}]}
        after = {"gameTime": 100, "entities": [{"witness": name, "ticksExisted": 100} for name in sorted(WITNESSES)]}
        self.assertEqual("FAIL", tick_result(before, after)["status"])
        before["entities"] = [{"witness": name, "ticksExisted": 0} for name in sorted(WITNESSES)]
        self.assertEqual("PASS", tick_result(before, after)["status"])

    def test_duplicate_helper_tick_record_fails(self):
        before = {"gameTime": 0, "entities": [{"witness": name, "ticksExisted": 0} for name in sorted(WITNESSES)]}
        after = {"gameTime": 100, "entities": [{"witness": name, "ticksExisted": 100} for name in sorted(WITNESSES)]}
        after["entities"].extend([{"witness": "leash-knot", "ticksExisted": 100}] * 2)
        self.assertEqual("FAIL", tick_result(before, after)["status"])

    def test_owner_loss(self):
        self.changed(lambda s: s["entities"]["wolf"].pop("OwnerUUID"))

    def test_extra_entity(self):
        self.changed(lambda s: s["entities"].update(duplicate=s["entities"]["wolf"]))

    def test_respawn_point_loss(self):
        self.changed(lambda s: s["file"].pop("SpawnDimension"))

    def test_canonicalizes_nested_passenger_ids(self):
        old = {"id": "Boat", "Passengers": [{"id": "Wolf", "OwnerUUID": "owner"}]}
        new = {"id": "minecraft:boat", "Passengers": [{"id": "minecraft:wolf", "OwnerUUID": "owner"}]}
        self.assertEqual(new, canonical_entities(old))
        self.assertEqual("custom:unknown", canonical_entities({"id": "custom:unknown"})["id"])

    def test_pending_links_only_excluded_from_zero_tick_comparison(self):
        self.assertNotIn("relationships", strict_expected(self.state))
        self.assertEqual(self.state["relationships"], survival_expected(self.state)["relationships"])

    def test_movement_is_not_state_loss(self):
        self.state["entities"]["horse"]["Pos"] = [1, 2, 3]
        expected = survival_expected(self.state)
        self.assertNotIn("Pos", expected["entities"]["horse"])
        self.assertIn("Leash", expected["entities"]["horse"])

    def test_legacy_collar_color_keeps_its_meaning(self):
        self.assertEqual(14, canonical_entities({"id": "Wolf", "CollarColor": 1}, legacy=True)["CollarColor"])
        self.assertEqual(14, canonical_entities({"id": "minecraft:wolf", "CollarColor": 14})["CollarColor"])

    def test_passengers_keep_same_semantic_fields_as_roots(self):
        nbt = {"id": "minecraft:boat", "Passengers": [{"id": "minecraft:wolf", "UUIDMost": 5,
               "OwnerUUID": "owner", "Attributes": [{"Name": "generic.maxHealth", "Base": 20}]}]}
        state = entity_state(nbt)
        self.assertEqual(20, state["Passengers"][0]["Attributes"]["generic.maxHealth"]["Base"])
        changed = copy.deepcopy(state)
        changed["Passengers"][0]["OwnerUUID"] = "other"
        self.assertEqual("FAIL", verdict({"entities": state}, {"entities": changed})["status"])

    def test_new_attributes_allowed_but_changed_old_values_fail(self):
        old = entity_state({"id": "minecraft:wolf", "Attributes": [{"Name": "generic.maxHealth", "Base": 20}]})
        new = entity_state({"id": "minecraft:wolf", "Attributes": [{"Name": "forge.swimSpeed", "Base": 1},
                           {"Name": "generic.maxHealth", "Base": 20}]})
        self.assertEqual("PASS", verdict({"entities": {"wolf": old}}, {"entities": {"wolf": new}})["status"])
        new["Attributes"]["generic.maxHealth"]["Base"] = 10
        self.assertEqual("FAIL", verdict({"entities": {"wolf": old}}, {"entities": {"wolf": new}})["status"])


if __name__ == "__main__":
    unittest.main()
