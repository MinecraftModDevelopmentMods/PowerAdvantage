"""Destination-only safety checks; no Minecraft process or source world is opened."""

import json
from pathlib import Path
import tempfile
import unittest
import zipfile

from native114 import LEGACY_POWER, RIG, jar_identity, native_normalized, output_path, pinned_profile, require_clean_log, require_disjoint
from matrix import differences, normalized


class Native114DestinationTest(unittest.TestCase):
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
            archive.writestr("META-INF/mods.toml", '[[mods]]\nmodId="poweradvantage"\nversion="2.4.5.114041"\n')
        jar_identity(jar, "poweradvantage", "2.4.5.114041")
        for identity, version in (("steamadvantage", "2.4.5.114041"), ("poweradvantage", "2.4.4.113021")):
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
        manifest.write_text(json.dumps({"target114": {"seed": ".", "launcher": "forge-1.13.2-25.0.223.jar", "jars": []}}))
        with self.assertRaises(AssertionError):
            pinned_profile(manifest, "target114")

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


if __name__ == "__main__":
    unittest.main()
