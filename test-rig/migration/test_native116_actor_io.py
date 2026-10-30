import copy
import unittest

import actors
import dimensions
from native116_actor_io import capture_actors, capture_dimensions, semantic
from native116_format import actor_nbt


class Native116ActorFormatTests(unittest.TestCase):
    def test_capture_algorithms_are_identical_without_mutating_historical_globals(self):
        self.assertIs(capture_actors.__code__, actors.capture.__code__)
        self.assertIs(capture_dimensions.__code__, dimensions.capture.__code__)
        self.assertIsNot(capture_actors.__globals__, actors.capture.__globals__)
        self.assertIsNot(actors.semantic, semantic)

    def test_modern_uuid_dimension_and_inventory_match_the_exact_old_identity(self):
        old = dict(UUIDMost=1, UUIDLeast=2, Dimension=-1, Health=7.0,
                   Inventory=[dict(id="poweradvantage:starch", Count=3, Slot=1)])
        new = dict(UUID=[0, 1, 0, 2], Dimension="minecraft:the_nether", Health=7.0,
                   Inventory=copy.deepcopy(old["Inventory"]))
        before = copy.deepcopy(new)
        keys = tuple(old)
        self.assertEqual(semantic(old, keys, {}), semantic(new, keys, {}))
        self.assertEqual(before, new)
        altered = copy.deepcopy(new)
        altered["Inventory"][0]["Count"] -= 1
        self.assertTrue(actors.compare(dict(file=semantic(old, keys, {}), entities={}),
                                      dict(file=semantic(altered, keys, {}), entities={}))['status'] == 'FAIL')

    def test_changed_uuid_health_and_dimension_are_not_ignored(self):
        original = dict(UUID=[0, 1, 0, 2], Dimension="minecraft:overworld", Health=7.0)
        for field, value in (("UUID", [0, 1, 0, 3]), ("Dimension", "minecraft:the_end"), ("Health", 20.0)):
            altered = dict(original, **{field: value})
            self.assertNotEqual(actor_nbt(original), actor_nbt(altered))

    def test_owner_uuid_is_exact_and_duplicates_are_rejected(self):
        self.assertEqual("00000000-0000-0001-0000-000000000002", actor_nbt(dict(Owner=[0, 1, 0, 2]))['OwnerUUID'])
        with self.assertRaises(ValueError):
            actor_nbt(dict(UUID=[0, 1, 0, 2], UUIDMost=1))
        with self.assertRaises(ValueError):
            actor_nbt(dict(Owner=[0, 1, 0, 2], OwnerUUID="other"))


if __name__ == "__main__":
    unittest.main()
