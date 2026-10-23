import unittest
from collections import Counter

from recipe_unlocks import MATERIALS, fixture


class RecipeUnlockRunnerTest(unittest.TestCase):
    def test_fixture_covers_crafting_and_smelting_but_not_crushing(self):
        recipes = fixture()["recipes"]
        self.assertEqual(Counter(recipe["id"].split(":")[0] for recipe in recipes),
                         {"poweradvantage": 35, "steamadvantage": 24, "electricadvantage": 50})
        self.assertEqual(109, len({recipe["id"] for recipe in recipes}))
        self.assertTrue(all(":crushing_" not in recipe["id"] for recipe in recipes))

    def test_every_case_exercises_inventory_predicates(self):
        for recipe in fixture()["recipes"]:
            self.assertTrue(recipe["ingredients"], recipe["id"])
            self.assertTrue(all(set(predicate) in ({"item"}, {"tag"})
                                for predicate in recipe["ingredients"].values()), recipe["id"])

    def test_standalone_expansions_do_not_require_the_other_expansion(self):
        for extensions, count in (("none", 35), ("steam", 59), ("electric", 85)):
            self.assertEqual(count, len(fixture(extensions)["recipes"]))

    def test_human_kit_is_small_unique_and_uses_registered_id_shapes(self):
        self.assertEqual(27, len(MATERIALS))
        self.assertEqual(len(MATERIALS), len(set(MATERIALS)))
        self.assertTrue(all(":" in item for item in MATERIALS))


if __name__ == "__main__":
    unittest.main()
