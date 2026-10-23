import copy
import unittest

from actors import PLAYER_KEYS, compare, semantic, tick_result, freeze_result


class ActorOracleTests(unittest.TestCase):
    def setUp(self):
        self.state = {"file": {"Inventory": [{"Slot": 0, "id": "electricadvantage:lithium_battery", "Count": 3,
                                              "tag": {"energy": 123.0}}], "EnderItems": [], "XpTotal": 751},
                      "embedded": {}, "adapter": {},
                      "entities": {"hydroturbine": {"id": "electricadvantage:hydroturbine", "UUIDMost": 77,
                                                    "parentPos": [164, 81, 8]}}}

    def changed(self, edit):
        value = copy.deepcopy(self.state)
        edit(value)
        self.assertEqual("FAIL", compare(self.state, value)["status"])

    def test_identical(self): self.assertEqual("PASS", compare(self.state, self.state)["status"])
    def test_quantity_loss(self): self.changed(lambda v: v["file"]["Inventory"][0].update(Count=2))
    def test_quantity_duplication(self): self.changed(lambda v: v["file"]["Inventory"][0].update(Count=4))
    def test_extra_slot(self): self.changed(lambda v: v["file"]["Inventory"].append(copy.deepcopy(v["file"]["Inventory"][0])))
    def test_battery_charge_loss(self): self.changed(lambda v: v["file"]["Inventory"][0]["tag"].update(energy=0))
    def test_missing_entity(self): self.changed(lambda v: v["entities"].clear())
    def test_duplicate_entity(self): self.changed(lambda v: v["entities"].update(copy=copy.deepcopy(v["entities"]["hydroturbine"])))
    def test_parent_loss(self): self.changed(lambda v: v["entities"]["hydroturbine"].pop("parentPos"))
    def test_uuid_changed(self): self.changed(lambda v: v["entities"]["hydroturbine"].update(UUIDMost=78))
    def test_experience_loss(self): self.changed(lambda v: v["file"].update(XpTotal=0))
    def test_world_ticks_do_not_prove_entity_ticks(self):
        before = {"gameTime": 100, "entities": [{"witness": "cart", "ticksExisted": 0}]}
        after = {"gameTime": 181, "entities": [{"witness": "cart", "ticksExisted": 0}]}
        self.assertEqual("FAIL", tick_result(before, after)["status"])
        after["entities"][0]["ticksExisted"] = 81
        self.assertEqual("PASS", tick_result(before, after)["status"])

    def test_freeze_requires_zero_measured_ticks(self):
        record = {"frozen": True, "entities": [{"witness": "cart", "ticksExisted": 1}]}
        self.assertEqual("FAIL", freeze_result(record)["status"])
        record["entities"][0]["ticksExisted"] = 0
        self.assertEqual("PASS", freeze_result(record)["status"])
        record["frozen"] = False
        self.assertEqual("FAIL", freeze_result(record)["status"])
    def test_ender_chest_loss(self):
        self.state["file"]["EnderItems"] = copy.deepcopy(self.state["file"]["Inventory"])
        self.changed(lambda v: v["file"].update(EnderItems=[]))

    def test_ownership_changed(self):
        self.state["entities"]["wolf"] = {"OwnerUUID": "original-owner"}
        self.changed(lambda v: v["entities"]["wolf"].update(OwnerUUID="different-owner"))

    def test_custom_nbt_loss(self):
        self.state["file"]["Inventory"][0]["tag"]["Nested"] = {"OwnerNote": "keep"}
        self.changed(lambda v: v["file"]["Inventory"][0]["tag"].pop("Nested"))

    def test_bucket_identity_and_custom_nbt(self):
        old = {"Inventory": [{"Slot": 0, "id": "forge:bucketfilled", "Count": 1, "Damage": 0,
                              "tag": {"FluidName": "crude_oil", "Amount": 1000, "OwnerNote": "keep"}}]}
        modern = {"Inventory": [{"Slot": 0, "id": "poweradvantage:crude_oil_bucket", "Count": 1,
                                 "tag": {"OwnerNote": "keep"}}]}
        self.assertEqual(modern, semantic(old, PLAYER_KEYS, {}))
        modern["Inventory"][0]["id"] = "minecraft:bucket"
        self.assertNotEqual(modern, semantic(old, PLAYER_KEYS, {}))

    def test_enchantment_identity(self):
        old = {"Inventory": [{"Slot": 0, "id": "steamadvantage:musket", "Count": 1, "Damage": 5,
                              "tag": {"ench": [{"id": 81, "lvl": 1}]}}]}
        value = semantic(old, PLAYER_KEYS, {81: "steamadvantage:powder_charge"})
        self.assertEqual("steamadvantage:powder_charge", value["Inventory"][0]["tag"]["Enchantments"][0]["id"])
        self.assertEqual(5, value["Inventory"][0]["tag"]["Damage"])
        wrong = semantic(old, PLAYER_KEYS, {81: "minecraft:sharpness"})
        self.assertNotEqual(value, wrong)


if __name__ == "__main__": unittest.main()
