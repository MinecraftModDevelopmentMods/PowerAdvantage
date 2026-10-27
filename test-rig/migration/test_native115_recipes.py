"""Native recipe runner safety/oracle tests. No Minecraft or protected input writes."""

import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import native115_recipes as recipes


class NativeRecipeTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def packaged(self, counts=None, bad_predicate=False):
        jars = []
        for mod, count in (counts or recipes.MOD_COUNTS).items():
            path = self.root / (mod + ".jar")
            with zipfile.ZipFile(path, "w") as jar:
                for index in range(count):
                    stem = f"case_{index}"
                    jar.writestr(f"data/{mod}/recipes/{stem}.json", json.dumps({"type": "minecraft:crafting_shapeless"}))
                    predicate = {"item": "minecraft:iron_ingot"}
                    if bad_predicate:
                        predicate["count"] = {"min": 1}
                    jar.writestr(f"data/{mod}/advancements/recipes/{stem}.json", json.dumps({"criteria": {
                        "has_ingredient": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [predicate]}}
                    }}))
                jar.writestr(f"data/{mod}/recipes/crushing_test.json", json.dumps({"type": "basemetals:crushing"}))
            jars.append(path)
        return jars

    def test_fixture_uses_packaged_advancements_and_preserves_109_inventory(self):
        definition, sources = recipes.recipe_fixture(self.packaged())
        self.assertEqual(109, len(definition["recipes"]))
        self.assertEqual(109, len(sources))
        self.assertEqual(27, len(definition["materials"]))
        self.assertTrue(all(row["ingredients"] == {"has_ingredient": {"item": "minecraft:iron_ingot"}}
                            for row in definition["recipes"]))
        self.assertTrue(all("crushing" not in identity for identity in sources))

    def test_fixture_inventory_cannot_silently_shrink(self):
        counts = dict(recipes.MOD_COUNTS, poweradvantage=34)
        with self.assertRaises(AssertionError):
            recipes.recipe_fixture(self.packaged(counts))

    def test_fixture_rejects_unsupported_predicates(self):
        with self.assertRaises(AssertionError):
            recipes.recipe_fixture(self.packaged(bad_predicate=True))

    def test_fixture_rejects_duplicate_packaged_recipes(self):
        jars = self.packaged()
        with self.assertRaises(AssertionError):
            recipes.recipe_fixture(jars + jars[:1])

    def test_unlock_schedule_preserves_modes_and_full_process_persistence(self):
        self.assertEqual(5, len(recipes.UNLOCK_BOOTS))
        self.assertEqual(["NORMAL", "NORMAL", "TECH_PROGRESSION", "APOCALYPTIC", "NORMAL"],
                         [row[0] for row in recipes.UNLOCK_BOOTS])
        self.assertEqual(3, sum("sweep" in row[2] for row in recipes.UNLOCK_BOOTS))
        self.assertEqual(3, sum("persist" in row[2] for row in recipes.UNLOCK_BOOTS))
        self.assertEqual(4, sum("restore" in row[2] for row in recipes.UNLOCK_BOOTS))

    def unlock_report(self):
        definition = {"recipes": [{"id": "poweradvantage:case"}]}
        report = {"status": "PASS", "potatoCraftingCases": 59, "bookRecipes": [{"id": "poweradvantage:case"}],
                  "cases": [{"id": "poweradvantage:case", "recipeActive": True, "status": "PASS"}],
                  "additionalMaterials": []}
        return definition, report

    def test_unlock_requires_every_case_and_all_59_native_potato_checks(self):
        definition, report = self.unlock_report()
        self.assertEqual(1, recipes.validate_unlock(report, definition, "sweep")["passed"])
        for key, value in (("cases", []), ("potatoCraftingCases", 58), ("bookRecipes", []), ("status", "FAIL")):
            changed = dict(report, **{key: value})
            with self.subTest(key=key), self.assertRaises(AssertionError):
                recipes.validate_unlock(changed, definition, "sweep")

    def test_unlock_cannot_replace_missing_case_with_duplicate(self):
        definition, report = self.unlock_report()
        definition["recipes"].append({"id": "poweradvantage:other"})
        report["cases"] *= 2
        with self.assertRaises(AssertionError):
            recipes.validate_unlock(report, definition, "sweep")

    def test_human_materials_gaps_remain_failures(self):
        definition, report = self.unlock_report()
        report["additionalMaterials"] = ["electricadvantage:control_circuit"]
        with self.assertRaises(AssertionError):
            recipes.validate_unlock(report, definition, "persist")

    def test_unavailable_ingredient_is_reported_as_skip_not_pass(self):
        definition, report = self.unlock_report()
        report["cases"][0]["status"] = "SKIP_NO_INGREDIENT_PROVIDER"
        counts = recipes.validate_unlock(report, definition, "sweep")
        self.assertEqual((0, 1), (counts["passed"], counts["skipped"]))

    def test_progression_rejects_empty_evidence_and_ignored_configuration(self):
        report = {"status": "PASS", "mode": "NORMAL", "plasticEqualsRubber": True, "blocked": 0,
                  "blockedRecipes": [], "targets": [{"id": "poweradvantage:case", "status": "PASS"}],
                  "reachable": {"minecraft:iron_ingot": {}}, "executedRecipes": ["poweradvantage:case"],
                  "processWitnesses": [{"route": "live_furnace"}], "lootDataPackOverrideVerified": True}
        with patch.object(recipes, "validate_tags"):
            recipes.validate_progression(report, "NORMAL", True)
            for key, value in (("mode", "APOCALYPTIC"), ("plasticEqualsRubber", False), ("blocked", 1),
                               ("blockedRecipes", ["missing"]), ("targets", []), ("processWitnesses", []),
                               ("lootDataPackOverrideVerified", False)):
                with self.subTest(key=key), self.assertRaises(AssertionError):
                    recipes.validate_progression(dict(report, **{key: value}), "NORMAL", True)

    def test_original_natural_basis_and_exclusions_are_not_expanded(self):
        from progression import fixture
        original = fixture()
        self.assertEqual(original, recipes.progression_fixture())
        self.assertNotIn("electricadvantage:psu", original["exclusions"])
        self.assertEqual(["electricadvantage:li_ore", "electricadvantage:sulfur_ore"],
                         [item for item in original["naturalResources"] if item.startswith("electricadvantage:")])

    def test_target_requires_all_five_mods_and_native_harness(self):
        manifest = self.root / "manifest.json"
        manifest.write_text(json.dumps({"target115": {"jars": [{"id": "poweradvantage"}]}}))
        with self.assertRaises(ValueError), patch.object(recipes, "pinned_profile") as resolve:
            recipes.native_target(manifest)
        resolve.assert_not_called()

    def test_missing_and_historical_packet_exports_cannot_claim_native_level(self):
        runtime = self.root / "runtime"
        with self.assertRaises(FileNotFoundError):
            recipes.verify_level(runtime, {"jars": []})
        old_level = {"Data": {"Version": {"Name": "1.13.2"}}}
        with patch.object(recipes.nbtlib, "load", return_value=old_level), self.assertRaises(AssertionError):
            recipes.verify_level(runtime, {"jars": []})

    def test_native_level_requires_nonempty_singular_item_and_block_registries(self):
        level = {"Data": {"Version": {"Name": "1.15.2"}}, "fml": {
            "LoadingModList": [{"ModId": "forge", "ModVersion": "31.2.57"}],
            "Registries": {"minecraft:item": {"ids": [1]}, "minecraft:block": {"ids": [1]}}}}
        with patch.object(recipes.nbtlib, "load", return_value=level), patch.object(recipes, "digest", return_value="test-hash"):
            self.assertEqual("1.15.2", recipes.verify_level(self.root, {"jars": []})["minecraft"])
        for registry in ({"minecraft:items": {"ids": [1]}, "minecraft:blocks": {"ids": [1]}},
                         {"minecraft:item": {"ids": []}, "minecraft:block": {"ids": [1]}}):
            level["fml"]["Registries"] = registry
            with self.subTest(registry=registry), patch.object(recipes.nbtlib, "load", return_value=level), self.assertRaises(AssertionError):
                recipes.verify_level(self.root, {"jars": []})

    def test_native_level_rejects_wrong_forge_or_mod_version(self):
        level = {"Data": {"Version": {"Name": "1.15.2"}}, "fml": {
            "LoadingModList": [{"ModId": "forge", "ModVersion": "25.0.223"}], "Registries": {}}}
        with patch.object(recipes.nbtlib, "load", return_value=level), self.assertRaises(AssertionError):
            recipes.verify_level(self.root, {"jars": []})
        level["fml"]["LoadingModList"][0]["ModVersion"] = "31.2.57"
        with patch.object(recipes.nbtlib, "load", return_value=level), self.assertRaises(AssertionError):
            recipes.verify_level(self.root, {"jars": [{"id": "poweradvantage", "version": "2.4.6.115021"}]})

    def test_staging_only_copies_binaries_and_replaces_legacy_generator(self):
        seed = self.root / "seed"
        seed.mkdir()
        (seed / "libraries").mkdir()
        (seed / "forge-1.15.2-31.2.57.jar").write_bytes(b"test-launcher")
        (seed / WORLD_NAME).mkdir()
        protected = seed / WORLD_NAME / "untouched.dat"
        protected.write_bytes(b"protected-existing-world")
        runtime = self.root / "new-runtime"
        target = seed, "forge-1.15.2-31.2.57.jar", []
        before = recipes.fingerprint(seed)
        with patch.object(recipes, "require_free_port"):
            recipes.stage_native(runtime, target, 25577, "unlock")
        self.assertEqual(before, recipes.fingerprint(seed))
        self.assertFalse((runtime / WORLD_NAME).exists())
        self.assertTrue((runtime / "advantage-recipes.allow").exists())
        props = (runtime / "server.properties").read_text()
        self.assertIn("server-port=25577\n", props)
        self.assertIn("server-ip=127.0.0.1\n", props)
        self.assertIn("generator-settings=\n", props)
        self.assertNotIn("3;minecraft:bedrock", props)
        with patch.object(recipes, "require_free_port"), self.assertRaises(FileExistsError):
            recipes.stage_native(runtime, target, 25577, "unlock")

    def test_mode_pruning_requires_exact_native_packet_difference(self):
        log = self.root / "console-tech.log"
        event = "[Server thread/ERROR] [minecraft/ServerRecipeBook]: Tried to load unrecognized recipe: poweradvantage:normal_only removed now.\n"
        log.write_text(event * 2)
        result = recipes.audit_unlock_log(log, {"poweradvantage:normal_only", "poweradvantage:common"}, {"poweradvantage:common"})
        self.assertEqual(["poweradvantage:normal_only"], result["observed_removed_ids"])
        self.assertEqual(2, len(result["events"]))
        with self.assertRaises(AssertionError):
            recipes.audit_unlock_log(log, {"poweradvantage:normal_only"}, {"poweradvantage:normal_only"})
        log.write_text("")
        with self.assertRaises(AssertionError):
            recipes.audit_unlock_log(log, {"poweradvantage:normal_only"}, set())

    def test_mode_pruning_never_accepts_unrelated_errors_or_bad_recipe_ids(self):
        log = self.root / "console.log"
        for message in ("[Server thread/ERROR] [minecraft/ServerRecipeBook]: Other failure",
                        "[Server thread/ERROR] [minecraft/ServerRecipeBook]: Tried to load improperly formatted recipe: bad removed now.",
                        "[Server thread/ERROR] [minecraft/LootTableManager]: Failed to reload data packs",
                        "NoSuchMethodError: native failure"):
            log.write_text(message)
            with self.subTest(message=message), self.assertRaises(AssertionError):
                recipes.audit_unlock_log(log, set(), set())


WORLD_NAME = recipes.WORLD

if __name__ == "__main__":
    unittest.main()
