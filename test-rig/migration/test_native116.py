"""Destination-only safety checks; no Minecraft process or source world is opened."""

import json
from pathlib import Path
import tempfile
import unittest
import zipfile

from native116 import LEGACY_POWER, RIG, TEST_FLAT, configure_creation, jar_identity, native_normalized, output_path, pinned_profile, require_clean_log, require_disjoint, stage
from matrix import differences, normalized


class Native116DestinationTest(unittest.TestCase):
    def setUp(self):
        root = RIG / "build/port-evidence"
        root.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=root)
        self.root = Path(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()

    def test_legacy_source_root_is_explicit_pa113(self):
        self.assertEqual(LEGACY_POWER.name, "PowerAdvantage")
        self.assertEqual(LEGACY_POWER.parent.name, "PA113")

    def test_output_must_be_new_and_inside_native_build_scope(self):
        self.assertEqual(output_path(self.root / "fresh"), self.root / "fresh")
        with self.assertRaises(FileExistsError):
            output_path(self.root)
        with self.assertRaises(ValueError):
            output_path(RIG.parent / "world")

    def test_neither_ancestor_nor_descendant_of_source_is_writable(self):
        source = self.root / "source"
        for output in (source, source / "nested", self.root):
            with self.assertRaises(ValueError):
                require_disjoint(output, source)
        require_disjoint(self.root / "destination", source)

    def test_native_identity_and_version_are_both_required(self):
        jar = self.root / "example.jar"
        with zipfile.ZipFile(jar, "w") as archive:
            archive.writestr("META-INF/mods.toml", '[[mods]]\nmodId="poweradvantage"\nversion="2.4.6.116051"\n')
        jar_identity(jar, "poweradvantage", "2.4.6.116051")
        for identity, version in (("steamadvantage", "2.4.6.116051"), ("poweradvantage", "2.4.4.113021")):
            with self.assertRaises(ValueError):
                jar_identity(jar, identity, version)

    def test_pinned_source_hash_failure_is_not_ignored(self):
        artifact = self.root / "input.jar"
        artifact.write_bytes(b"not-the-pinned-artifact")
        manifest = self.root / "source-profiles.json"
        manifest.write_text(json.dumps({"target113": {"seed": ".", "launcher": "forge.jar",
            "jars": [{"path": "input.jar", "sha256": "0" * 64}]}}))
        with self.assertRaises(AssertionError):
            pinned_profile(manifest, "target113")

    def test_native_manifest_rejects_historical_runtime_even_with_no_jars(self):
        manifest = self.root / "target.json"
        manifest.write_text(json.dumps({"target116": {"seed": ".", "launcher": "forge-1.13.2-25.0.223.jar", "jars": []}}))
        with self.assertRaises(AssertionError):
            pinned_profile(manifest, "target116")

    def test_native_omitted_empty_chest_lock_is_semantically_unlocked(self):
        old = {"id": "Chest", "Lock": "", "Items": []}
        new = {"id": "minecraft:chest", "Items": []}
        self.assertEqual(native_normalized(old), native_normalized(new))
        self.assertNotIn("Lock", normalized(new))  # Historical default is unchanged.

    def test_missing_or_changed_real_chest_locks_remain_failures(self):
        unlocked = native_normalized({"id": "minecraft:chest"})
        locked = native_normalized({"id": "minecraft:chest", "Lock": "secret"})
        self.assertTrue(differences(unlocked, locked))
        self.assertTrue(differences(locked, unlocked))
        self.assertTrue(differences(locked, native_normalized({"id": "minecraft:chest", "Lock": "different"})))

    def test_custom_machine_empty_key_is_not_dropped_or_defaulted(self):
        old = native_normalized({"id": "poweradvantage:machine", "Lock": ""})
        new = native_normalized({"id": "poweradvantage:machine"})
        self.assertTrue(differences(old, new))

    def test_startup_errors_cannot_hide_behind_a_successful_snapshot(self):
        log = self.root / "console.log"
        log.write_text("[Server thread/INFO] Done\n[Server thread/ERROR] Failed to reload data packs\n")
        with self.assertRaises(AssertionError):
            require_clean_log(log)
        log.write_text("[Server thread/WARN] No data fixer registered for custom entity\n[Server thread/INFO] Done\n")
        require_clean_log(log)

    def test_fatal_and_uncaught_java_failures_are_rejected_on_the_destination(self):
        log = self.root / "console.log"
        for failure in ("[Server thread/FATAL] [orespawn]: Loading Integration For basemetals",
                        'Exception in thread "Server thread" java.lang.IllegalStateException: broken',
                        "Caused by: java.lang.NoClassDefFoundError: missing/Dependency",
                        "java.lang.VerifyError: invalid bytecode"):
            with self.subTest(failure=failure):
                log.write_text("[Server thread/INFO] Done\n" + failure + "\n")
                with self.assertRaises(AssertionError):
                    require_clean_log(log)

    def test_new_stage_uses_harness_preset_not_vanilla_flat_json(self):
        seed = self.root / "seed"
        (seed / "libraries").mkdir(parents=True)
        (seed / "forge.jar").write_bytes(b"launcher")
        runtime = self.root / "new-runtime"
        stage(runtime, (seed, "forge.jar", []))
        properties = dict(line.split("=", 1) for line in (runtime / "server.properties").read_text().splitlines() if "=" in line)
        self.assertEqual(TEST_FLAT, properties["level-type"])
        self.assertEqual("false", properties["generate-structures"])
        self.assertEqual("", properties["generator-settings"])

    def test_creation_properties_never_rewrite_an_imported_level_dat(self):
        from matrix import WORLD, fingerprint
        runtime = self.root / "disposable-import"
        (runtime / WORLD).mkdir(parents=True)
        (runtime / WORLD / "level.dat").write_bytes(b"immutable imported world data")
        (runtime / "server.properties").write_text("level-type=FLAT\ngenerator-settings={}\ngenerate-structures=true\nmax-tick-time=60000\n")
        before = fingerprint(runtime / WORLD)
        configure_creation(runtime)
        self.assertEqual(before, fingerprint(runtime / WORLD))
        self.assertIn("max-tick-time=60000", (runtime / "server.properties").read_text())

    def test_registry_ops_error_remains_fatal_even_after_done(self):
        log = self.root / "registry-ops-failure.log"
        log.write_text("[main/ERROR] [minecraft/DimensionGeneratorSettings]: Not a registry ops\n[Server thread/INFO] Done\n")
        with self.assertRaisesRegex(AssertionError, "Native runtime errors"):
            require_clean_log(log)


if __name__ == "__main__":
    unittest.main()
